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
        private val TITLE_IDS = intArrayOf(R.id.widget_title, R.id.widget_title_light, R.id.widget_title_dark, R.id.widget_title_img)

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
            val status = context.applicationContext.getSharedPreferences("widget", 0)
            try {
                manager.updateAppWidget(widgetId, buildViews(context, widgetId))
                status.edit().putString("last_ok", WidgetTheme.of(context).name + "@" + System.currentTimeMillis())
                    .remove("last_error").apply()
            } catch (e: Exception) {
                android.util.Log.e("NotesWidget", "widget update failed", e)
                status.edit().putString("last_error", e.toString().take(300)).apply()
                // Never leave the launcher on an old look: retry without the pre-rendered title bitmap.
                runCatching { manager.updateAppWidget(widgetId, buildViews(context, widgetId, pixelTitle = false)) }
            }
        }

        /** "Old School · updated 00:42" style status for Settings (or the last error). */
        fun status(context: Context): String? {
            val p = context.applicationContext.getSharedPreferences("widget", 0)
            p.getString("last_error", null)?.let { return "Widget update failed: $it" }
            val ok = p.getString("last_ok", null) ?: return null
            val theme = ok.substringBefore('@')
            val at = ok.substringAfter('@').toLongOrNull() ?: return null
            val time = android.text.format.DateFormat.getTimeFormat(context).format(java.util.Date(at))
            return "Widget: ${theme.lowercase().replace("oldschool", "old school").replaceFirstChar { it.uppercase() }} look · updated $time"
        }

        /** The header RemoteViews in the app's current theme. [withList] = false for previews/tests. */
        @Suppress("DEPRECATION")
        fun buildViews(context: Context, widgetId: Int, withList: Boolean = true, pixelTitle: Boolean = true): RemoteViews {
            val theme = WidgetTheme.of(context)
            val views = RemoteViews(context.packageName, theme.header)
            val dm = context.resources.displayMetrics
            fun px(dp: Int) = (dp * dm.density).toInt()

            // Frame, buttons and list panel: set explicitly every time (re-applies never inherit an older theme).
            views.setInt(android.R.id.background, "setBackgroundResource", theme.background)
            val pad = px(if (theme == WidgetTheme.OLDSCHOOL) 8 else 10)
            views.setViewPadding(android.R.id.background, pad, pad, pad, pad)
            views.setImageViewResource(R.id.widget_mic, theme.mic)
            views.setImageViewResource(R.id.widget_add, theme.add)
            views.setInt(R.id.widget_list_frame, "setBackgroundResource", theme.listBackground)
            val lp = if (theme == WidgetTheme.OLDSCHOOL) px(4) else 0
            views.setViewPadding(R.id.widget_list_frame, lp, lp, lp, lp)

            // Header title: exactly one of the title views is visible.
            var title = theme.title
            var titleBitmap: android.graphics.Bitmap? = null
            if (theme == WidgetTheme.OLDSCHOOL) {
                // Small bitmap (title only, ~20sp tall) so launchers never reject it for size.
                val maxW = px((WidgetTheme.widthDp(context, widgetId) - 110).coerceIn(80, 420))
                titleBitmap = if (pixelTitle) runCatching {
                    PixelText.render(context, context.getString(R.string.app_name), 20f, context.getColor(R.color.os_orange), maxW)
                }.getOrNull() else null
                if (titleBitmap == null) title = R.id.widget_title_dark  // fallback: plain orange text on stone
            }
            for (id in TITLE_IDS) views.setViewVisibility(id, if (id == title) android.view.View.VISIBLE else android.view.View.GONE)
            views.setTextColor(R.id.widget_title_light, context.getColor(R.color.widget_text_light))
            views.setTextColor(R.id.widget_title_dark,
                context.getColor(if (theme == WidgetTheme.OLDSCHOOL) R.color.os_orange else R.color.widget_text_dark))
            titleBitmap?.let { views.setImageViewBitmap(R.id.widget_title_img, it) }

            // Empty-state text colour.
            when (theme) {
                WidgetTheme.SYSTEM ->
                    if (android.os.Build.VERSION.SDK_INT >= 31) views.setColorStateList(R.id.widget_empty, "setTextColor", R.color.widget_subtext)
                    else views.setTextColor(R.id.widget_empty, context.getColor(R.color.widget_subtext))
                WidgetTheme.LIGHT -> views.setTextColor(R.id.widget_empty, context.getColor(R.color.widget_subtext_light))
                WidgetTheme.DARK -> views.setTextColor(R.id.widget_empty, context.getColor(R.color.widget_subtext_dark))
                WidgetTheme.OLDSCHOOL -> views.setTextColor(R.id.widget_empty, context.getColor(R.color.os_ink))
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
            val openApp = PendingIntent.getActivity(context, 2, Intent(context, MainActivity::class.java), immutable)
            for (id in TITLE_IDS) views.setOnClickPendingIntent(id, openApp)
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
