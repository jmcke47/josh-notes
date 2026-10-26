package com.josh.notes.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import com.josh.notes.AppLock
import com.josh.notes.EditNoteActivity
import com.josh.notes.NoteStore
import com.josh.notes.R

/** Small 2x2 widget: quick add / dictate plus the most recent note (filtered by the widget's options). */
class QuickWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) { ids.forEach { update(context, manager, it) } }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, newOptions: Bundle) {
        WidgetTheme.saveWidth(context, id, newOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH))
        update(context, manager, id)
    }

    override fun onDeleted(context: Context, ids: IntArray) { ids.forEach { WidgetOptions.remove(context, it) } }

    companion object {
        private val TITLES = intArrayOf(R.id.quick_title, R.id.quick_title_light, R.id.quick_title_dark, R.id.quick_title_img)
        private val BODIES = intArrayOf(R.id.quick_body, R.id.quick_body_light, R.id.quick_body_dark, R.id.quick_body_img)

        fun refreshAll(context: Context) {
            val ctx = context.applicationContext
            val manager = AppWidgetManager.getInstance(ctx)
            manager.getAppWidgetIds(ComponentName(ctx, QuickWidgetProvider::class.java)).forEach { update(ctx, manager, it) }
        }

        fun update(context: Context, manager: AppWidgetManager, widgetId: Int) {
            NotesWidgetProvider.safeUpdate(context, manager, widgetId) { pixel -> buildViews(context, widgetId, pixel) }
        }

        private fun variant(theme: WidgetTheme, ids: IntArray) = when (theme) {
            WidgetTheme.SYSTEM -> ids[0]; WidgetTheme.LIGHT -> ids[1]; WidgetTheme.DARK -> ids[2]; WidgetTheme.OLDSCHOOL -> ids[3]
        }

        fun buildViews(context: Context, widgetId: Int, pixel: Boolean = true): RemoteViews {
            val theme = WidgetTheme.of(context)
            val os = theme == WidgetTheme.OLDSCHOOL
            val opts = WidgetOptions.get(context, widgetId)
            val views = RemoteViews(context.packageName, R.layout.widget_quick)
            val label = when (opts.mode) {
                WidgetOptions.Mode.ALL -> context.getString(R.string.app_name)
                WidgetOptions.Mode.CHECKLIST -> "Checklist"
                else -> opts.label(context, context.getString(R.string.app_name))
            }
            NotesWidgetProvider.styleFrame(context, views, theme, widgetId, label, pixel, titleSp = 15f, panel = theme.card, buttonsDp = 86)

            val n = opts.filter(NoteStore.all(context)).firstOrNull()
            val locked = AppLock.enabled(context)
            val title = n?.let { it.displayTitle.ifBlank { context.getString(R.string.untitled) } } ?: "No notes yet"
            val (done, total) = n?.progress ?: (0 to 0)
            val body = when {
                n == null -> "Tap + to write one"
                n.secret || locked -> ""
                n.checklist -> "$done/$total done" + n.items.filter { !it.checked && it.text.isNotBlank() }.take(4)
                    .joinToString("") { "\n☐ " + it.text }
                n.title.isBlank() -> n.body.lines().drop(1).joinToString("\n").trim()
                else -> n.body.trim()
            }
            val titleId = variant(theme, TITLES); val bodyId = variant(theme, BODIES)
            for (id in TITLES) views.setViewVisibility(id, if (id == titleId) View.VISIBLE else View.GONE)
            for (id in BODIES) views.setViewVisibility(id, if (id == bodyId && body.isNotEmpty()) View.VISIBLE else View.GONE)
            if (os) {
                val w = ((WidgetTheme.widthDp(context, widgetId).coerceAtMost(260) - 40) * context.resources.displayMetrics.density).toInt()
                val t = if (pixel) runCatching { PixelText.render(context, "$title:", 14f, context.getColor(R.color.os_ink), w, shadow = false, bold = true) }.getOrNull() else null
                val b = if (pixel && body.isNotEmpty()) runCatching {
                    PixelText.render(context, body, 12f, context.getColor(R.color.os_chat_blue), w, maxLines = 6, shadow = false)
                }.getOrNull() else null
                if (t != null) views.setImageViewBitmap(R.id.quick_title_img, t)
                else { views.setViewVisibility(R.id.quick_title_img, View.GONE); views.setViewVisibility(R.id.quick_title_dark, View.VISIBLE) }
                if (b != null) views.setImageViewBitmap(R.id.quick_body_img, b)
                else if (body.isNotEmpty()) { views.setViewVisibility(R.id.quick_body_img, View.GONE); views.setViewVisibility(R.id.quick_body_dark, View.VISIBLE) }
            }
            for (id in TITLES) if (id != R.id.quick_title_img) views.setTextViewText(id, title)
            for (id in BODIES) if (id != R.id.quick_body_img) views.setTextViewText(id, body)
            views.setContentDescription(R.id.quick_note, if (body.isEmpty()) title else "$title. $body")

            val immutable = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            val open = Intent(context, EditNoteActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .setAction("com.josh.notes.QUICK_OPEN_$widgetId")
            if (n != null) open.putExtra(EditNoteActivity.EXTRA_NOTE_ID, n.id)
            views.setOnClickPendingIntent(R.id.quick_note, PendingIntent.getActivity(context, 100 + widgetId * 16, open, immutable))
            views.setOnClickPendingIntent(R.id.widget_add, PendingIntent.getActivity(context, 101 + widgetId * 16,
                Intent(context, EditNoteActivity::class.java).setAction("com.josh.notes.NEW_NOTE").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), immutable))
            return views
        }
    }
}
