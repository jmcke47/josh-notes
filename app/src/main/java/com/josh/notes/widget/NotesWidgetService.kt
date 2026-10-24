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
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        Factory(applicationContext, intent.getIntExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, 0))

    @androidx.annotation.VisibleForTesting
    internal class Factory(private val context: Context, private val widgetId: Int = 0) : RemoteViewsFactory {
        private var notes: List<Note> = emptyList()

        override fun onCreate() {}
        override fun onDataSetChanged() { notes = NoteStore.all(context).filter { !it.archived && !it.inTrash }.sortedFor(SortMode.EDITED) }
        override fun onDestroy() {}
        override fun getCount() = notes.size
        override fun getLoadingView(): RemoteViews? = null
        override fun getViewTypeCount() = WidgetTheme.entries.size
        override fun getItemId(position: Int) = notes.getOrNull(position)?.id ?: position.toLong()
        override fun hasStableIds() = true

        override fun getViewAt(position: Int): RemoteViews {
            val theme = WidgetTheme.of(context)
            val os = theme == WidgetTheme.OLDSCHOOL
            val rv = RemoteViews(context.packageName, theme.item)
            val n = notes.getOrNull(position) ?: return rv
            val title = n.displayTitle.ifBlank { context.getString(R.string.untitled) }
            val locked = com.josh.notes.AppLock.enabled(context)
            val preview = if (locked) "" else when {
                n.checklist -> n.contentText.trim()
                n.title.isBlank() -> n.body.lines().drop(1).joinToString("\n").trim()
                else -> n.body.trim()
            }
            val gone = android.view.View.GONE; val visible = android.view.View.VISIBLE
            rv.setViewVisibility(R.id.widget_item_bell, if (n.hasReminder) visible else gone)
            rv.setViewVisibility(R.id.widget_item_clip, if (n.attachments.isNotEmpty()) visible else gone)
            rv.setViewVisibility(R.id.widget_item_pin, if (n.pinned) visible else gone)
            if (n.color == 0) rv.setViewVisibility(R.id.widget_item_color, android.view.View.INVISIBLE)
            else {
                rv.setViewVisibility(R.id.widget_item_color, visible)
                rv.setInt(R.id.widget_item_color, "setColorFilter", NoteColors.palette[n.color])
            }
            if (os) {
                // Chatbox row in the pixel font (pre-rendered; launchers can't load app fonts).
                val dm = context.resources.displayMetrics
                val now = System.currentTimeMillis()
                val overdue = n.reminderAt in 1..now
                val icons = (if (n.pinned) 16 else 0) + (if (n.attachments.isNotEmpty()) 16 else 0) +
                    (if (n.hasReminder && !overdue) 16 else 0) + (if (overdue) 32 else 0)
                val rowW = ((WidgetTheme.widthDp(context, widgetId) - 44) * dm.density).toInt()
                PixelText.render(context, "$title:", 14f, context.getColor(R.color.os_ink), rowW - (icons * dm.density).toInt(), shadow = false)
                    ?.let { rv.setImageViewBitmap(R.id.widget_item_title_img, it) }
                val body = if (preview.isEmpty()) null
                    else PixelText.render(context, preview, 12f, context.getColor(R.color.os_chat_blue), rowW - ((if (overdue) 32 else 0) * dm.density).toInt(),
                        maxLines = 2, shadow = false)
                if (body != null) rv.setImageViewBitmap(R.id.widget_item_body_img, body)
                rv.setViewVisibility(R.id.widget_item_body_img, if (body == null) gone else visible)
                rv.setContentDescription(R.id.widget_item, if (preview.isEmpty()) title else "$title: $preview")
                rv.setViewVisibility(R.id.widget_item_hitsplat, if (overdue) visible else gone)
                if (overdue) {
                    rv.setTextViewText(R.id.widget_item_hitsplat_text, ((now - n.reminderAt) / 86_400_000L + 1).coerceAtMost(99).toString())
                    rv.setViewVisibility(R.id.widget_item_bell, gone)
                }
            } else {
                rv.setTextViewText(R.id.widget_item_title, title)
                rv.setTextViewText(R.id.widget_item_body, preview)
                rv.setViewVisibility(R.id.widget_item_body, if (preview.isEmpty()) gone else visible)
            }
            rv.setOnClickFillInIntent(R.id.widget_item, Intent().putExtra(EditNoteActivity.EXTRA_NOTE_ID, n.id))
            return rv
        }
    }
}
