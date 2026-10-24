package com.josh.notes.widget

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.josh.notes.EditNoteActivity
import com.josh.notes.Note
import com.josh.notes.NoteStore
import com.josh.notes.NoteColors
import com.josh.notes.R
import com.josh.notes.SortMode
import com.josh.notes.sortedFor

class NotesWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory = Factory(applicationContext)

    @androidx.annotation.VisibleForTesting
    internal class Factory(private val context: Context) : RemoteViewsFactory {
        private var notes: List<Note> = emptyList()

        override fun onCreate() {}
        override fun onDataSetChanged() { notes = NoteStore.all(context).filter { !it.archived && !it.inTrash }.sortedFor(SortMode.EDITED) }
        override fun onDestroy() {}
        override fun getCount() = notes.size
        override fun getLoadingView(): RemoteViews? = null
        override fun getViewTypeCount() = 2
        override fun getItemId(position: Int) = notes.getOrNull(position)?.id ?: position.toLong()
        override fun hasStableIds() = true

        override fun getViewAt(position: Int): RemoteViews {
            val os = com.josh.notes.AppSettings.oldSchool(context)
            val rv = RemoteViews(context.packageName, if (os) R.layout.widget_item_os else R.layout.widget_item)
            val n = notes.getOrNull(position) ?: return rv
            rv.setTextViewText(R.id.widget_item_title, n.displayTitle.ifBlank { context.getString(R.string.untitled) })
            val locked = com.josh.notes.AppLock.enabled(context)
            val preview = if (locked) "" else when {
                n.checklist -> n.contentText.trim()
                n.title.isBlank() -> n.body.lines().drop(1).joinToString("\n").trim()
                else -> n.body.trim()
            }
            rv.setViewVisibility(R.id.widget_item_bell, if (n.hasReminder) android.view.View.VISIBLE else android.view.View.GONE)
            rv.setViewVisibility(R.id.widget_item_clip, if (n.attachments.isNotEmpty()) android.view.View.VISIBLE else android.view.View.GONE)
            rv.setViewVisibility(R.id.widget_item_pin, if (n.pinned) android.view.View.VISIBLE else android.view.View.GONE)
            if (n.color == 0) rv.setViewVisibility(R.id.widget_item_color, android.view.View.INVISIBLE)
            else {
                rv.setViewVisibility(R.id.widget_item_color, android.view.View.VISIBLE)
                rv.setInt(R.id.widget_item_color, "setColorFilter", NoteColors.palette[n.color])
            }
            if (os) {
                rv.setTextViewText(R.id.widget_item_title, n.displayTitle.ifBlank { context.getString(R.string.untitled) } + ":")
                val now = System.currentTimeMillis()
                val overdue = n.reminderAt in 1..now
                rv.setViewVisibility(R.id.widget_item_hitsplat, if (overdue) android.view.View.VISIBLE else android.view.View.GONE)
                if (overdue) rv.setTextViewText(R.id.widget_item_hitsplat_text, ((now - n.reminderAt) / 86_400_000L + 1).coerceAtMost(99).toString())
                if (overdue) rv.setViewVisibility(R.id.widget_item_bell, android.view.View.GONE)
            }
            rv.setTextViewText(R.id.widget_item_body, preview)
            rv.setViewVisibility(R.id.widget_item_body, if (preview.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE)
            rv.setOnClickFillInIntent(R.id.widget_item, Intent().putExtra(EditNoteActivity.EXTRA_NOTE_ID, n.id))
            return rv
        }
    }
}
