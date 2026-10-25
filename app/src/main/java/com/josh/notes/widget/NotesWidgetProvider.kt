package com.josh.notes.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import com.josh.notes.AppLock
import com.josh.notes.EditNoteActivity
import com.josh.notes.MainActivity
import com.josh.notes.NoteStore
import com.josh.notes.R

/** Resizable list widget (all notes, pinned, a #tag, or one checklist — chosen per widget). */
class NotesWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { updateWidget(context, manager, it) }
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, newOptions: Bundle) {
        WidgetTheme.saveWidth(context, id, newOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH))
        updateWidget(context, manager, id)
        @Suppress("DEPRECATION") manager.notifyAppWidgetViewDataChanged(intArrayOf(id), R.id.widget_list)
    }

    override fun onDeleted(context: Context, ids: IntArray) { ids.forEach { WidgetOptions.remove(context, it) } }

    companion object {
        private val TITLE_IDS = intArrayOf(R.id.widget_title, R.id.widget_title_light, R.id.widget_title_dark, R.id.widget_title_img)

        /** Called whenever notes or the theme change: rebuilds every widget instance (list and quick) and its list. */
        fun refreshAll(context: Context) {
            val ctx = context.applicationContext
            val manager = AppWidgetManager.getInstance(ctx)
            val ids = manager.getAppWidgetIds(ComponentName(ctx, NotesWidgetProvider::class.java))
            ids.forEach { updateWidget(ctx, manager, it) }
            @Suppress("DEPRECATION")
            if (ids.isNotEmpty()) manager.notifyAppWidgetViewDataChanged(ids, R.id.widget_list)
            QuickWidgetProvider.refreshAll(ctx)
        }

        /** Rebuild one widget, whichever provider it belongs to. */
        fun refreshOne(context: Context, widgetId: Int) {
            val ctx = context.applicationContext
            val manager = AppWidgetManager.getInstance(ctx)
            val provider = manager.getAppWidgetInfo(widgetId)?.provider?.className
            if (provider == QuickWidgetProvider::class.java.name) QuickWidgetProvider.update(ctx, manager, widgetId)
            else {
                updateWidget(ctx, manager, widgetId)
                @Suppress("DEPRECATION") manager.notifyAppWidgetViewDataChanged(intArrayOf(widgetId), R.id.widget_list)
            }
        }

        fun updateWidget(context: Context, manager: AppWidgetManager, widgetId: Int) {
            safeUpdate(context, manager, widgetId) { pixel -> buildViews(context, widgetId, pixelTitle = pixel) }
        }

        /** Push an update; if building/sending fails, record it and retry without pixel-font bitmaps. */
        internal fun safeUpdate(context: Context, manager: AppWidgetManager, widgetId: Int, build: (Boolean) -> RemoteViews) {
            val status = context.applicationContext.getSharedPreferences("widget", 0)
            try {
                manager.updateAppWidget(widgetId, build(true))
                status.edit().putString("last_ok", WidgetTheme.of(context).name + "@" + System.currentTimeMillis())
                    .remove("last_error").apply()
            } catch (e: Exception) {
                android.util.Log.e("NotesWidget", "widget update failed", e)
                status.edit().putString("last_error", e.toString().take(300)).apply()
                runCatching { manager.updateAppWidget(widgetId, build(false)) }
            }
        }

        /** "Old school look · updated 00:42" style status for Settings (or the last error). */
        fun status(context: Context): String? {
            val p = context.applicationContext.getSharedPreferences("widget", 0)
            p.getString("last_error", null)?.let { return "Widget update failed: $it" }
            val ok = p.getString("last_ok", null) ?: return null
            val theme = ok.substringBefore('@')
            val at = ok.substringAfter('@').toLongOrNull() ?: return null
            val time = android.text.format.DateFormat.getTimeFormat(context).format(java.util.Date(at))
            return "Widget: ${theme.lowercase().replace("oldschool", "old school").replaceFirstChar { it.uppercase() }} look · updated $time"
        }

        private fun px(context: Context, dp: Int) = (dp * context.resources.displayMetrics.density).toInt()

        /**
         * Theme the shared frame (background, padding, buttons, list panel, title) of either widget layout. Every
         * property is set explicitly so a re-apply never keeps anything from a previous theme or option.
         */
        internal fun styleFrame(context: Context, views: RemoteViews, theme: WidgetTheme, widgetId: Int, label: String,
                                pixelTitle: Boolean, titleSp: Float = 20f, panel: Int = theme.listBackground, buttonsDp: Int = 104) {
            val os = theme == WidgetTheme.OLDSCHOOL
            views.setInt(android.R.id.background, "setBackgroundResource", theme.background)
            val pad = px(context, if (os) 8 else 10)
            views.setViewPadding(android.R.id.background, pad, pad, pad, pad)
            views.setImageViewResource(R.id.widget_mic, theme.mic)
            views.setImageViewResource(R.id.widget_add, theme.add)
            views.setInt(R.id.widget_list_frame, "setBackgroundResource", panel)
            val lp = if (os) px(context, 4) else 0
            views.setViewPadding(R.id.widget_list_frame, lp, lp, lp, lp)

            var title = theme.title
            var titleBitmap: Bitmap? = null
            if (os) {
                val maxW = px(context, (WidgetTheme.widthDp(context, widgetId) - buttonsDp).coerceIn(60, 420))
                titleBitmap = if (pixelTitle) runCatching {
                    // Shrink long labels (e.g. "Garden quest 2/6") a little before ellipsizing.
                    val sp = PixelText.fitSize(context, label, titleSp, titleSp * 0.7f, maxW)
                    PixelText.render(context, label, sp, context.getColor(R.color.os_orange), maxW)
                }.getOrNull() else null
                if (titleBitmap == null) title = R.id.widget_title_dark // fallback: plain orange text on stone
            }
            for (id in TITLE_IDS) {
                views.setViewVisibility(id, if (id == title) View.VISIBLE else View.GONE)
                if (id != R.id.widget_title_img) views.setTextViewText(id, label)
            }
            views.setTextColor(R.id.widget_title_light, context.getColor(R.color.widget_text_light))
            views.setTextColor(R.id.widget_title_dark, context.getColor(if (os) R.color.os_orange else R.color.widget_text_dark))
            titleBitmap?.let { views.setImageViewBitmap(R.id.widget_title_img, it) }
            views.setContentDescription(R.id.widget_title_img, label)

            val immutable = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            val openApp = PendingIntent.getActivity(context, 2, Intent(context, MainActivity::class.java), immutable)
            for (id in TITLE_IDS) views.setOnClickPendingIntent(id, openApp)
            views.setOnClickPendingIntent(R.id.widget_mic,
                PendingIntent.getActivity(context, 5,
                    Intent(context, EditNoteActivity::class.java).setAction(EditNoteActivity.ACTION_DICTATE)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), immutable))
        }

        /** Mutable template for collection items: routed through [WidgetActionActivity] (open note / tick item). */
        internal fun itemTemplate(context: Context): PendingIntent = PendingIntent.getActivity(context, 1,
            Intent(context, WidgetActionActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or (if (android.os.Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0))

        /** The list widget's RemoteViews in the app's current theme. [withList] = false for previews/tests. */
        @Suppress("DEPRECATION")
        fun buildViews(context: Context, widgetId: Int, withList: Boolean = true, pixelTitle: Boolean = true): RemoteViews {
            val theme = WidgetTheme.of(context)
            val opts = WidgetOptions.get(context, widgetId)
            val views = RemoteViews(context.packageName, theme.header)
            val checklist = opts.checklist(context)
            val label = if (AppLock.enabled(context) && opts.mode == WidgetOptions.Mode.CHECKLIST) "Checklist"
                else opts.label(context, context.getString(R.string.app_name))
            styleFrame(context, views, theme, widgetId, label, pixelTitle)

            // Empty-state text colour + wording.
            when (theme) {
                WidgetTheme.SYSTEM ->
                    if (android.os.Build.VERSION.SDK_INT >= 31) views.setColorStateList(R.id.widget_empty, "setTextColor", R.color.widget_subtext)
                    else views.setTextColor(R.id.widget_empty, context.getColor(R.color.widget_subtext))
                WidgetTheme.LIGHT -> views.setTextColor(R.id.widget_empty, context.getColor(R.color.widget_subtext_light))
                WidgetTheme.DARK -> views.setTextColor(R.id.widget_empty, context.getColor(R.color.widget_subtext_dark))
                WidgetTheme.OLDSCHOOL -> views.setTextColor(R.id.widget_empty, context.getColor(R.color.os_ink))
            }
            views.setTextViewText(R.id.widget_empty, when (opts.mode) {
                WidgetOptions.Mode.ALL -> context.getString(R.string.empty_notes)
                WidgetOptions.Mode.PINNED -> "No pinned notes"
                WidgetOptions.Mode.TAG -> "No notes tagged #${opts.tag}"
                WidgetOptions.Mode.CHECKLIST -> when {
                    checklist == null -> "Checklist not found — long-press the widget to choose another"
                    checklist.secret -> "This checklist is secret — tap to open it"
                    AppLock.enabled(context) -> "App lock is on — tap to open the checklist"
                    else -> "No items yet — tap + to add some"
                }
            })

            if (withList) {
                val serviceIntent = Intent(context, NotesWidgetService::class.java).apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                    // Theme + options in the data URI so the launcher re-binds a fresh factory/view cache when they change.
                    putExtra("theme", theme.name)
                    putExtra("opts", opts.encode())
                    data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
                }
                views.setRemoteAdapter(R.id.widget_list, serviceIntent)
            }
            views.setEmptyView(R.id.widget_list, R.id.widget_empty)
            views.setPendingIntentTemplate(R.id.widget_list, itemTemplate(context))

            val immutable = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            // "+" -> new note (single-checklist widgets: open that checklist to add items).
            val add = if (checklist != null) Intent(context, EditNoteActivity::class.java)
                    .putExtra(EditNoteActivity.EXTRA_NOTE_ID, checklist.id).setAction("com.josh.notes.OPEN_CHECKLIST_$widgetId")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                else Intent(context, EditNoteActivity::class.java).setAction("com.josh.notes.NEW_NOTE").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            views.setOnClickPendingIntent(R.id.widget_add, PendingIntent.getActivity(context, 3 + widgetId * 16, add, immutable))
            // Empty state -> new note / the checklist.
            views.setOnClickPendingIntent(R.id.widget_empty, PendingIntent.getActivity(context, 4 + widgetId * 16,
                if (checklist != null) add else Intent(context, EditNoteActivity::class.java).setAction("com.josh.notes.NEW_NOTE_EMPTY"), immutable))
            return views
        }
    }
}
