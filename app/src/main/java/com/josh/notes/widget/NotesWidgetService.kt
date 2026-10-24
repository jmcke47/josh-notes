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

    private class Factory(private val context: Context) : RemoteViewsFactory {
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
            val rv = RemoteViews(context.packageName,
                if (com.josh.notes.AppSettings.oldSchool(context)) R.layout.widget_item_os else R.layout.widget_item)
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
            rv.setTextViewText(R.id.widget_item_body, preview)
            rv.setViewVisibility(R.id.widget_item_body, if (preview.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE)
            rv.setOnClickFillInIntent(R.id.widget_item, Intent().putExtra(EditNoteActivity.EXTRA_NOTE_ID, n.id))
            return rv
        }
    }
}
