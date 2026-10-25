package com.josh.notes.widget

import android.content.Context
import com.josh.notes.Note
import com.josh.notes.NoteStore
import com.josh.notes.SortMode
import com.josh.notes.sortedFor

/** What one widget instance shows (kept per widget id). */
data class WidgetOptions(val mode: Mode = Mode.ALL, val tag: String = "", val noteId: Long = 0L) {
    enum class Mode { ALL, PINNED, TAG, CHECKLIST }

    fun encode() = "${mode.name}|$tag|$noteId"

    /** Notes for a list widget (or the newest one for the quick widget). */
    fun filter(notes: List<Note>): List<Note> {
        val live = notes.filter { !it.archived && !it.inTrash }
        return when (mode) {
            Mode.ALL -> live
            Mode.PINNED -> live.filter { it.pinned }
            Mode.TAG -> live.filter { tag in it.tags }
            Mode.CHECKLIST -> notes.filter { it.id == noteId && !it.inTrash }
        }.sortedFor(SortMode.EDITED)
    }

    /** The checklist shown in [Mode.CHECKLIST] (null if deleted or no longer a checklist). */
    fun checklist(ctx: Context): Note? =
        if (mode != Mode.CHECKLIST) null else NoteStore.get(ctx, noteId)?.takeIf { it.checklist && !it.inTrash }

    /** Header label for this widget. */
    fun label(ctx: Context, appName: String): String = when (mode) {
        Mode.ALL -> appName
        Mode.PINNED -> "Pinned"
        Mode.TAG -> "#$tag"
        Mode.CHECKLIST -> checklist(ctx)?.let { n ->
            val (d, t) = n.progress
            n.displayTitle.ifBlank { "Checklist" } + if (n.secret) "" else "  $d/$t"
        } ?: "Checklist"
    }

    companion object {
        private fun p(ctx: Context) = ctx.applicationContext.getSharedPreferences("widget", 0)

        fun decode(s: String?): WidgetOptions {
            if (s.isNullOrBlank()) return WidgetOptions()
            val parts = s.split('|')
            val mode = runCatching { Mode.valueOf(parts[0]) }.getOrDefault(Mode.ALL)
            return WidgetOptions(mode, parts.getOrElse(1) { "" }, parts.getOrElse(2) { "0" }.toLongOrNull() ?: 0L)
        }

        fun get(ctx: Context, widgetId: Int): WidgetOptions = decode(p(ctx).getString("opt_$widgetId", null))
        fun set(ctx: Context, widgetId: Int, o: WidgetOptions) { p(ctx).edit().putString("opt_$widgetId", o.encode()).commit() }
        fun remove(ctx: Context, widgetId: Int) { p(ctx).edit().remove("opt_$widgetId").remove("w_$widgetId").apply() }

        /** All #tags used in (non-secret, live) notes, most used first. */
        fun allTags(ctx: Context): List<String> = NoteStore.all(ctx).filter { !it.inTrash && !it.archived }
            .flatMap { it.tags }.groupingBy { it }.eachCount().entries.sortedWith(compareBy({ -it.value }, { it.key })).map { it.key }

        /** Checklists that can be shown on their own (secret ones aren't offered). */
        fun checklists(ctx: Context): List<Note> = NoteStore.all(ctx).filter { it.checklist && !it.inTrash && !it.secret }
            .sortedFor(SortMode.EDITED)
    }
}
