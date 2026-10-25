package com.josh.notes

import org.json.JSONArray
import org.json.JSONObject

data class CheckItem(val text: String, val checked: Boolean)

/** A file attached to a note, stored at filesDir/attachments/<noteId>/<file>. */
data class Attachment(
    val id: String,
    val file: String,          // file name inside the note's attachment dir
    val name: String,          // display name
    val mime: String,
    val size: Long,
    val kind: String,          // "image" | "audio" | "file"
    val durationMs: Long = 0L,
    val createdAt: Long = 0L,
    val transcript: String = "",
) {
    val isImage get() = kind == "image"
    val isAudio get() = kind == "audio"

    fun toJson(): JSONObject = JSONObject().put("id", id).put("file", file).put("name", name).put("mime", mime)
        .put("size", size).put("kind", kind).put("durationMs", durationMs).put("createdAt", createdAt)
        .put("transcript", transcript)

    companion object {
        fun fromJson(o: JSONObject) = Attachment(
            o.getString("id"), o.getString("file"), o.optString("name", o.getString("file")),
            o.optString("mime", "application/octet-stream"), o.optLong("size"), o.optString("kind", "file"),
            o.optLong("durationMs"), o.optLong("createdAt"), o.optString("transcript"))
    }
}

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
    val reminderAt: Long = 0L,       // epoch ms, 0 = no reminder
    val reminderFired: Boolean = false,
    val deletedAt: Long = 0L,        // epoch ms, 0 = not in trash
    val repeat: Repeat = Repeat.NONE,
    val snoozeAt: Long = 0L,         // pending snooze alarm (doesn't change the series)
    val attachments: List<Attachment> = emptyList(),
    /** Secret note: content (title, body, items, attachment names/transcripts) lives only in [enc] (Keystore AES-GCM). */
    val secret: Boolean = false,
    val enc: String = "",
    /** Checklist marked as a quest: finishing it shows the "Quest complete!" scroll. */
    val quest: Boolean = false,
) {
    val hasImages get() = attachments.any { it.isImage }
    val hasAudio get() = attachments.any { it.isAudio }
    val hasFiles get() = attachments.any { !it.isAudio }
    val inTrash: Boolean get() = deletedAt > 0
    val hasReminder: Boolean get() = reminderAt > 0

    val displayTitle: String
        get() = if (secret) SECRET_TITLE else title.ifBlank {
            (if (checklist) items.firstOrNull { it.text.isNotBlank() }?.text
            else body.lineSequence().firstOrNull { it.isNotBlank() })?.trim() ?: ""
        }

    /** Plain-text preview / share text of the content. */
    val contentText: String
        get() = if (secret) "" else if (checklist) items.joinToString("\n") { (if (it.checked) "☑ " else "☐ ") + it.text } else body

    val isEmpty: Boolean
        get() = !(secret && enc.isNotEmpty()) && title.isBlank() && body.isBlank() && items.all { it.text.isBlank() } && attachments.isEmpty()

    /** Non-blank checklist items: (done, total). */
    val progress: Pair<Int, Int>
        get() = items.filter { it.text.isNotBlank() }.let { l -> l.count { it.checked } to l.size }
    val isComplete: Boolean get() = checklist && progress.let { (d, t) -> t > 0 && d == t }

    /** #hashtags in the note (lower-case, without '#'); none for secret notes. */
    val tags: Set<String>
        get() = if (secret) emptySet() else TAG_RE.findAll(title + "\n" + body + "\n" + items.joinToString("\n") { it.text })
            .map { it.groupValues[1].lowercase() }.toSet()

    fun shareText(): String = if (secret) "" else listOf(title.trim(), contentText.trim()).filter { it.isNotEmpty() }.joinToString("\n\n")

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("title", title).put("body", body)
        .put("createdAt", createdAt).put("updatedAt", updatedAt)
        .put("pinned", pinned).put("color", color).put("archived", archived)
        .put("checklist", checklist)
        .put("reminderAt", reminderAt).put("reminderFired", reminderFired).put("deletedAt", deletedAt)
        .put("repeat", repeat.toJson()).put("snoozeAt", snoozeAt)
        .put("secret", secret).put("enc", enc).put("quest", quest)
        .put("attachments", JSONArray().apply { attachments.forEach { put(it.toJson()) } })
        .put("items", JSONArray().apply {
            items.forEach { put(JSONObject().put("text", it.text).put("checked", it.checked)) }
        })

    companion object {
        const val SECRET_TITLE = "Secret note"
        private val TAG_RE = Regex("(?<![\\w#&])#([\\p{L}\\p{N}_-]{1,40})")

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
                reminderAt = o.optLong("reminderAt", 0L).coerceAtLeast(0L),
                reminderFired = o.optBoolean("reminderFired"),
                deletedAt = o.optLong("deletedAt", 0L).coerceAtLeast(0L),
                repeat = o.optJSONObject("repeat")?.let { Repeat.fromJson(it) } ?: Repeat.NONE,
                snoozeAt = o.optLong("snoozeAt", 0L).coerceAtLeast(0L),
                secret = o.optBoolean("secret"),
                enc = o.optString("enc"),
                quest = o.optBoolean("quest"),
                attachments = buildList {
                    val arr = o.optJSONArray("attachments")
                    if (arr != null) for (i in 0 until arr.length()) {
                        arr.optJSONObject(i)?.let { a -> runCatching { Attachment.fromJson(a) }.onSuccess { add(it) } }
                    }
                },
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
