package com.josh.notes.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.RemoteViews
import com.josh.notes.EditNoteActivity
import com.josh.notes.MainActivity
import com.josh.notes.R

class NotesWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { updateWidget(context, manager, it) }
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, newOptions: Bundle) {
        updateWidget(context, manager, id)
    }

    companion object {
        /** Called whenever notes change: rebuilds every widget instance and its list. */
        fun refreshAll(context: Context) {
            val ctx = context.applicationContext
            val manager = AppWidgetManager.getInstance(ctx)
            val ids = manager.getAppWidgetIds(ComponentName(ctx, NotesWidgetProvider::class.java))
            if (ids.isEmpty()) return
            ids.forEach { updateWidget(ctx, manager, it) }
            @Suppress("DEPRECATION")
            manager.notifyAppWidgetViewDataChanged(ids, R.id.widget_list)
        }

        @Suppress("DEPRECATION")
        fun updateWidget(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_notes)

            val serviceIntent = Intent(context, NotesWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }
            views.setRemoteAdapter(R.id.widget_list, serviceIntent)
            views.setEmptyView(R.id.widget_list, R.id.widget_empty)

            // Item tap -> open that note (fill-in intent supplies the note id).
            val itemTemplate = Intent(context, EditNoteActivity::class.java)
            val flagsMutable = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (android.os.Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            views.setPendingIntentTemplate(
                R.id.widget_list,
                PendingIntent.getActivity(context, 1, itemTemplate, flagsMutable)
            )

            val immutable = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            // Header title -> open app.
            views.setOnClickPendingIntent(
                R.id.widget_title,
                PendingIntent.getActivity(context, 2, Intent(context, MainActivity::class.java), immutable)
            )
            // "+" -> new note.
            views.setOnClickPendingIntent(
                R.id.widget_add,
                PendingIntent.getActivity(context, 3,
                    Intent(context, EditNoteActivity::class.java).setAction("com.josh.notes.NEW_NOTE")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), immutable)
            )
            // Mic -> new note with dictation started.
            views.setOnClickPendingIntent(
                R.id.widget_mic,
                PendingIntent.getActivity(context, 5,
                    Intent(context, EditNoteActivity::class.java).setAction(EditNoteActivity.ACTION_DICTATE)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), immutable)
            )
            // Empty state -> new note.
            views.setOnClickPendingIntent(
                R.id.widget_empty,
                PendingIntent.getActivity(context, 4,
                    Intent(context, EditNoteActivity::class.java).setAction("com.josh.notes.NEW_NOTE_EMPTY"), immutable)
            )

            manager.updateAppWidget(widgetId, views)
        }
    }
}
