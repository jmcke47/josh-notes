package com.josh.notes.widget

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.josh.notes.EditNoteActivity
import com.josh.notes.Note
import com.josh.notes.NoteStore
import com.josh.notes.R

class NotesWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory = Factory(applicationContext)

    private class Factory(private val context: Context) : RemoteViewsFactory {
        private var notes: List<Note> = emptyList()

        override fun onCreate() {}
        override fun onDataSetChanged() { notes = NoteStore.all(context) }
        override fun onDestroy() {}
        override fun getCount() = notes.size
        override fun getLoadingView(): RemoteViews? = null
        override fun getViewTypeCount() = 1
        override fun getItemId(position: Int) = notes.getOrNull(position)?.id ?: position.toLong()
        override fun hasStableIds() = true

        override fun getViewAt(position: Int): RemoteViews {
            val rv = RemoteViews(context.packageName, R.layout.widget_item)
            val n = notes.getOrNull(position) ?: return rv
            rv.setTextViewText(R.id.widget_item_title, n.displayTitle.ifBlank { context.getString(R.string.untitled) })
            val preview = if (n.title.isBlank()) "" else n.body.trim()
            rv.setTextViewText(R.id.widget_item_body, preview)
            rv.setViewVisibility(R.id.widget_item_body, if (preview.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE)
            rv.setOnClickFillInIntent(R.id.widget_item, Intent().putExtra(EditNoteActivity.EXTRA_NOTE_ID, n.id))
            return rv
        }
    }
}
