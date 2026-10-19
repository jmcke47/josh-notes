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

    private fun changed(ctx: Context) = NotesWidgetProvider.refreshAll(ctx)

    fun all(ctx: Context): List<Note> = synchronized(lock) { load(ctx).toList() }

    fun get(ctx: Context, id: Long): Note? = synchronized(lock) { load(ctx).find { it.id == id } }

    private fun newId(list: List<Note>): Long = maxOf(System.currentTimeMillis(), (list.maxOfOrNull { it.id } ?: 0L) + 1)

    /** Insert (note.id == 0) or replace by id. Returns the stored note. */
    fun upsert(ctx: Context, note: Note, touch: Boolean = true): Note {
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
        changed(ctx)
        return saved
    }

    /** Update without bumping "edited" time (pin, colour, archive, checkbox toggles from list). */
    fun update(ctx: Context, id: Long, touch: Boolean = false, f: (Note) -> Note): Note? {
        val n = get(ctx, id) ?: return null
        return upsert(ctx, f(n), touch)
    }

    fun delete(ctx: Context, id: Long): Note? {
        val removed = synchronized(lock) {
            val list = load(ctx)
            val idx = list.indexOfFirst { it.id == id }
            if (idx < 0) null else list.removeAt(idx).also { persist(ctx, list) }
        }
        changed(ctx)
        return removed
    }

    /** Put a previously deleted note back exactly as it was (Undo). */
    fun restore(ctx: Context, note: Note) = upsert(ctx, note, touch = false)

    fun exportJson(ctx: Context): String = synchronized(lock) { serialize(load(ctx)) }

    /** Merge imported notes: same id -> keep the newer edit; new ids added. Returns (added, updated). */
    fun importJson(ctx: Context, text: String): Pair<Int, Int> {
        val incoming = parse(text)
        var added = 0; var updated = 0
        synchronized(lock) {
            val list = load(ctx)
            for (n in incoming) {
                val idx = list.indexOfFirst { it.id == n.id }
                if (idx < 0) { list += n; added++ }
                else if (n.updatedAt > list[idx].updatedAt) { list[idx] = n; updated++ }
            }
            if (added + updated > 0) persist(ctx, list)
        }
        changed(ctx)
        return added to updated
    }
}
