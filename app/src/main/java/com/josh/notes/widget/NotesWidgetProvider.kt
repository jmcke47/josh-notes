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
        WidgetTheme.saveWidth(context, id, newOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH))
        updateWidget(context, manager, id)
        @Suppress("DEPRECATION") manager.notifyAppWidgetViewDataChanged(intArrayOf(id), R.id.widget_list)
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

        fun updateWidget(context: Context, manager: AppWidgetManager, widgetId: Int) {
            manager.updateAppWidget(widgetId, buildViews(context, widgetId))
        }

        /** The header RemoteViews in the app's current theme. [withList] = false for previews/tests. */
        @Suppress("DEPRECATION")
        fun buildViews(context: Context, widgetId: Int, withList: Boolean = true): RemoteViews {
            val theme = WidgetTheme.of(context)
            val views = RemoteViews(context.packageName, theme.header)
            if (theme == WidgetTheme.OLDSCHOOL) {
                val dm = context.resources.displayMetrics
                val maxW = ((WidgetTheme.widthDp(context, widgetId) - 110) * dm.density).toInt()
                PixelText.render(context, context.getString(R.string.app_name), 20f, context.getColor(R.color.os_orange), maxW)
                    ?.let { views.setImageViewBitmap(R.id.widget_title, it) }
            }

            if (withList) {
                val serviceIntent = Intent(context, NotesWidgetService::class.java).apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                    // Theme in the data URI so the launcher re-binds a fresh factory/view cache when the theme changes.
                    putExtra("theme", theme.name)
                    data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
                }
                views.setRemoteAdapter(R.id.widget_list, serviceIntent)
            }
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

            return views
        }
    }
}
