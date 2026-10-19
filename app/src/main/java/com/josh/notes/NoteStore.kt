package com.josh.notes

import android.content.Context
import com.josh.notes.widget.NotesWidgetProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Simple JSON-file note storage (filesDir/notes.json). Thread-safe. */
object NoteStore {
    private const val FILE = "notes.json"
    private val lock = Any()
    private var cache: MutableList<Note>? = null

    private fun file(ctx: Context) = File(ctx.applicationContext.filesDir, FILE)

    private fun load(ctx: Context): MutableList<Note> {
        cache?.let { return it }
        val f = file(ctx)
        val list = mutableListOf<Note>()
        if (f.exists()) {
            runCatching {
                val arr = JSONArray(f.readText())
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    list += Note(
                        o.getLong("id"), o.optString("title"), o.optString("body"),
                        o.optLong("updatedAt", System.currentTimeMillis())
                    )
                }
            }
        }
        cache = list
        return list
    }

    private fun persist(ctx: Context, list: List<Note>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("id", it.id).put("title", it.title)
                .put("body", it.body).put("updatedAt", it.updatedAt))
        }
        val f = file(ctx)
        val tmp = File(f.parentFile, "$FILE.tmp")
        tmp.writeText(arr.toString())
        if (!tmp.renameTo(f)) { f.writeText(arr.toString()); tmp.delete() }
    }

    /** All notes, most recently edited first. */
    fun all(ctx: Context): List<Note> = synchronized(lock) {
        load(ctx).sortedByDescending { it.updatedAt }
    }

    fun get(ctx: Context, id: Long): Note? = synchronized(lock) { load(ctx).find { it.id == id } }

    /** Insert or update. Returns the saved note (with id). */
    fun save(ctx: Context, id: Long?, title: String, body: String): Note {
        val saved = synchronized(lock) {
            val list = load(ctx)
            val now = System.currentTimeMillis()
            val idx = if (id != null) list.indexOfFirst { it.id == id } else -1
            val note = if (idx >= 0) {
                list[idx].copy(title = title, body = body, updatedAt = now).also { list[idx] = it }
            } else {
                val newId = maxOf(now, (list.maxOfOrNull { it.id } ?: 0L) + 1)
                Note(newId, title, body, now).also { list += it }
            }
            persist(ctx, list)
            note
        }
        NotesWidgetProvider.refreshAll(ctx)
        return saved
    }

    fun delete(ctx: Context, id: Long) {
        synchronized(lock) {
            val list = load(ctx)
            if (list.removeAll { it.id == id }) persist(ctx, list)
        }
        NotesWidgetProvider.refreshAll(ctx)
    }
}
