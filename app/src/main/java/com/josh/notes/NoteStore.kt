package com.josh.notes

import android.content.Context
import android.util.Log
import com.josh.notes.widget.NotesWidgetProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * JSON-file note storage (filesDir/notes.json). Thread-safe.
 *
 * Format v2: {"format":2,"notes":[...]}. v1 (a bare JSON array) is read
 * transparently; before the first v2 write the original v1 file is copied to
 * notes.v1-backup.json. A file that fails to parse is copied aside and never
 * overwritten silently.
 */
object NoteStore {
    private const val FILE = "notes.json"
    private const val FORMAT = 2
    private val lock = Any()
    private var cache: MutableList<Note>? = null
    private var loadFailed = false

    private fun dir(ctx: Context) = ctx.applicationContext.filesDir
    private fun file(ctx: Context) = File(dir(ctx), FILE)

    /** Parses either v1 array or v2 object. Throws on malformed input. */
    fun parse(text: String): List<Note> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return emptyList()
        val arr: JSONArray = if (trimmed.startsWith("[")) JSONArray(trimmed)
        else JSONObject(trimmed).getJSONArray("notes")
        val out = ArrayList<Note>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            runCatching { Note.fromJson(o) }.onSuccess { out += it }
                .onFailure { Log.w("NoteStore", "Skipping bad note #$i", it) }
        }
        return out
    }

    fun serialize(list: List<Note>): String = JSONObject()
        .put("format", FORMAT)
        .put("app", "com.josh.notes")
        .put("exportedAt", System.currentTimeMillis())
        .put("notes", JSONArray().apply { list.forEach { put(it.toJson()) } })
        .toString()

    private fun load(ctx: Context): MutableList<Note> {
        cache?.let { return it }
        val f = file(ctx)
        val list = mutableListOf<Note>()
        if (f.exists()) {
            try {
                val text = f.readText()
                list += parse(text)
                if (text.trim().startsWith("[")) {
                    val backup = File(dir(ctx), "notes.v1-backup.json")
                    if (!backup.exists()) f.copyTo(backup)
                }
            } catch (e: Exception) {
                Log.e("NoteStore", "notes.json unreadable; preserving copy", e)
                f.copyTo(File(dir(ctx), "notes.corrupt-${System.currentTimeMillis()}.json"), overwrite = true)
                loadFailed = true
            }
        }
        cache = list
        // Permanently remove notes that have been in the trash for > 30 days.
        val cutoff = System.currentTimeMillis() - TRASH_MS
        val expired = list.filter { it.inTrash && it.deletedAt < cutoff }
        if (!loadFailed && expired.isNotEmpty()) {
            list.removeAll(expired.toSet()); persist(ctx, list)
            expired.forEach { Attachments.deleteAll(ctx, it.id) }
        }
        return list
    }

    private fun persist(ctx: Context, list: List<Note>) {
        val f = file(ctx)
        val tmp = File(f.parentFile, "$FILE.tmp")
        val data = serialize(list)
        tmp.writeText(data)
        if (!tmp.renameTo(f)) { f.writeText(data); tmp.delete() }
        loadFailed = false
    }

    const val TRASH_DAYS = 30
    private const val TRASH_MS = TRASH_DAYS * 24L * 3600_000L

    private fun changed(ctx: Context) {
        NotesWidgetProvider.refreshAll(ctx)
        Backup.onLocalChange(ctx)
    }

    fun all(ctx: Context): List<Note> = synchronized(lock) { load(ctx).toList() }

    fun get(ctx: Context, id: Long): Note? = synchronized(lock) { load(ctx).find { it.id == id } }

    private fun newId(list: List<Note>): Long = maxOf(System.currentTimeMillis(), (list.maxOfOrNull { it.id } ?: 0L) + 1)

    /** Insert (note.id == 0) or replace by id. Returns the stored note. */
    fun upsert(ctx: Context, note0: Note, touch: Boolean = true): Note {
        // Secret notes are encrypted before they ever reach the file.
        val note = SecretNotes.seal(note0)
        if (touch) Stats.recordActiveDay(ctx)
        val saved = synchronized(lock) {
            val list = load(ctx)
            val now = System.currentTimeMillis()
            val idx = if (note.id != 0L) list.indexOfFirst { it.id == note.id } else -1
            val n = when {
                idx >= 0 -> note.copy(updatedAt = if (touch) now else note.updatedAt).also { list[idx] = it }
                else -> {
                    val id = if (note.id != 0L) note.id else newId(list)
                    note.copy(id = id, createdAt = if (note.id == 0L) now else note.createdAt,
                        updatedAt = if (touch || note.id == 0L) now else note.updatedAt).also { list += it }
                }
            }
            persist(ctx, list)
            n
        }
        Reminders.sync(ctx, saved)
        changed(ctx)
        return saved
    }

    /** Update without bumping "edited" time (pin, colour, archive, checkbox toggles from list). */
    fun update(ctx: Context, id: Long, touch: Boolean = false, f: (Note) -> Note): Note? {
        val n = get(ctx, id) ?: return null
        return upsert(ctx, f(n), touch)
    }

    /** Move to trash (kept $TRASH_DAYS days). Returns the note as it was before, for Undo. */
    fun delete(ctx: Context, id: Long): Note? {
        val before = get(ctx, id) ?: return null
        upsert(ctx, before.copy(deletedAt = System.currentTimeMillis()), touch = false)
        return before
    }

    /** Restore from trash. */
    fun untrash(ctx: Context, id: Long) = update(ctx, id) { it.copy(deletedAt = 0L) }

    /** Permanently delete. */
    fun deleteForever(ctx: Context, id: Long): Note? {
        val removed = synchronized(lock) {
            val list = load(ctx)
            val idx = list.indexOfFirst { it.id == id }
            if (idx < 0) null else list.removeAt(idx).also { persist(ctx, list) }
        }
        removed?.let { Reminders.cancel(ctx, it.id); Attachments.deleteAll(ctx, it.id) }
        changed(ctx)
        return removed
    }

    fun emptyTrash(ctx: Context): Int {
        val ids = all(ctx).filter { it.inTrash }.map { it.id }
        ids.forEach { deleteForever(ctx, it) }
        return ids.size
    }

    /** Put a previously deleted note back exactly as it was (Undo). */
    fun restore(ctx: Context, note: Note) = upsert(ctx, note, touch = false)

    /** Plain JSON export: secret notes are left out. */
    fun exportJson(ctx: Context): String = synchronized(lock) { serialize(load(ctx).filter { !it.secret }) }

    /** Merge: same id -> keep the newer edit; new ids added. Returns ids taken from [incoming]. */
    fun mergeIncoming(ctx: Context, incoming: List<Note>): Set<Long> {
        val taken = mutableSetOf<Long>()
        synchronized(lock) {
            val list = load(ctx)
            for (n in incoming) {
                val idx = list.indexOfFirst { it.id == n.id }
                if (idx < 0) { list += n; taken += n.id }
                else if (n.updatedAt > list[idx].updatedAt ||
                    (n.updatedAt == list[idx].updatedAt && n.deletedAt != list[idx].deletedAt && n.deletedAt == 0L)) {
                    list[idx] = n; taken += n.id
                }
            }
            if (taken.isNotEmpty()) persist(ctx, list)
        }
        Reminders.rescheduleAll(ctx)
        changed(ctx)
        return taken
    }

    /**
     * Replace every note (restore-replace). Caller must have made a safety backup first.
     * Secret notes that aren't in [notes] (plain backups never contain them) are kept. Returns their ids.
     */
    fun replaceAll(ctx: Context, notes: List<Note>): Set<Long> {
        val kept: Set<Long>
        synchronized(lock) {
            val list = load(ctx)
            list.forEach { Reminders.cancel(ctx, it.id) }
            val incoming = notes.map { it.id }.toSet()
            val keep = list.filter { it.secret && it.id !in incoming }
            kept = keep.map { it.id }.toSet()
            list.clear(); list += notes; list += keep
            persist(ctx, list)
        }
        Reminders.rescheduleAll(ctx)
        changed(ctx)
        return kept
    }

    fun maxUpdatedAt(ctx: Context): Long = all(ctx).maxOfOrNull { maxOf(it.updatedAt, it.deletedAt) } ?: 0L

    /** Merge imported notes (JSON backup, no attachments). Returns (added, updated). */
    fun importJson(ctx: Context, text: String): Pair<Int, Int> {
        val incoming = parse(text)
        val before = all(ctx).map { it.id }.toSet()
        val taken = mergeIncoming(ctx, incoming)
        val added = taken.count { it !in before }
        return added to (taken.size - added)
    }
}
