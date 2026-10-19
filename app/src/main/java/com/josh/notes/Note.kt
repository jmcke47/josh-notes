package com.josh.notes

import org.json.JSONArray
import org.json.JSONObject

data class CheckItem(val text: String, val checked: Boolean)

data class Note(
    val id: Long,
    val title: String,
    val body: String,
    val updatedAt: Long,
    val createdAt: Long = updatedAt,
    val pinned: Boolean = false,
    val color: Int = 0,            // index into NoteColors.palette, 0 = none
    val archived: Boolean = false,
    val checklist: Boolean = false,
    val items: List<CheckItem> = emptyList(),
) {
    val displayTitle: String
        get() = title.ifBlank {
            (if (checklist) items.firstOrNull { it.text.isNotBlank() }?.text
            else body.lineSequence().firstOrNull { it.isNotBlank() })?.trim() ?: ""
        }

    /** Plain-text preview / share text of the content. */
    val contentText: String
        get() = if (checklist) items.joinToString("\n") { (if (it.checked) "☑ " else "☐ ") + it.text } else body

    val isEmpty: Boolean
        get() = title.isBlank() && body.isBlank() && items.all { it.text.isBlank() }

    fun shareText(): String = listOf(title.trim(), contentText.trim()).filter { it.isNotEmpty() }.joinToString("\n\n")

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("title", title).put("body", body)
        .put("createdAt", createdAt).put("updatedAt", updatedAt)
        .put("pinned", pinned).put("color", color).put("archived", archived)
        .put("checklist", checklist)
        .put("items", JSONArray().apply {
            items.forEach { put(JSONObject().put("text", it.text).put("checked", it.checked)) }
        })

    companion object {
        /** Tolerant parser: accepts v1 notes (id/title/body/updatedAt only) and v2 notes. */
        fun fromJson(o: JSONObject): Note {
            val updated = o.optLong("updatedAt", 0L).takeIf { it > 0 } ?: o.optLong("id", System.currentTimeMillis())
            val itemsArr = o.optJSONArray("items")
            val items = buildList {
                if (itemsArr != null) for (i in 0 until itemsArr.length()) {
                    val it = itemsArr.optJSONObject(i) ?: continue
                    add(CheckItem(it.optString("text"), it.optBoolean("checked")))
                }
            }
            return Note(
                id = o.getLong("id"),
                title = o.optString("title"),
                body = o.optString("body"),
                updatedAt = updated,
                // v1 ids were creation timestamps, so they're a good createdAt fallback.
                createdAt = o.optLong("createdAt", 0L).takeIf { it > 0 } ?: minOf(o.getLong("id"), updated),
                pinned = o.optBoolean("pinned"),
                color = o.optInt("color", 0).coerceIn(0, NoteColors.palette.size - 1),
                archived = o.optBoolean("archived"),
                checklist = o.optBoolean("checklist"),
                items = items,
            )
        }
    }
}

object NoteColors {
    /** 0 = no colour. ARGB. */
    val palette = intArrayOf(
        0x00000000, 0xFFE53935.toInt(), 0xFFFB8C00.toInt(), 0xFFFDD835.toInt(), 0xFF43A047.toInt(),
        0xFF00ACC1.toInt(), 0xFF1E88E5.toInt(), 0xFF8E24AA.toInt(), 0xFFD81B60.toInt(),
    )
    val names = arrayOf("None", "Red", "Orange", "Yellow", "Green", "Teal", "Blue", "Purple", "Pink")
}

enum class SortMode { EDITED, CREATED, TITLE }

fun List<Note>.sortedFor(mode: SortMode): List<Note> {
    val base = when (mode) {
        SortMode.EDITED -> sortedByDescending { it.updatedAt }
        SortMode.CREATED -> sortedByDescending { it.createdAt }
        SortMode.TITLE -> sortedBy { it.displayTitle.lowercase() }
    }
    return base.sortedByDescending { it.pinned } // stable: pinned first, order kept within groups
}
