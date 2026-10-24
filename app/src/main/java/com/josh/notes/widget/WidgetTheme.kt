package com.josh.notes.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import com.josh.notes.AppSettings
import com.josh.notes.R
import com.josh.notes.ThemeMode

/** Widget look that follows the app's theme setting (the launcher's own day/night is only used for "System"). */
enum class WidgetTheme(val header: Int, val item: Int) {
    SYSTEM(R.layout.widget_notes, R.layout.widget_item),
    LIGHT(R.layout.widget_notes_light, R.layout.widget_item_light),
    DARK(R.layout.widget_notes_dark, R.layout.widget_item_dark),
    OLDSCHOOL(R.layout.widget_notes_os, R.layout.widget_item_os);

    companion object {
        fun of(ctx: Context): WidgetTheme = when (AppSettings.theme(ctx)) {
            ThemeMode.SYSTEM -> SYSTEM
            ThemeMode.LIGHT -> LIGHT
            ThemeMode.DARK -> DARK
            ThemeMode.OLDSCHOOL -> OLDSCHOOL
        }

        private fun p(ctx: Context) = ctx.applicationContext.getSharedPreferences("widget", 0)

        /** Current widget width in dp (from the launcher's size options), default for a 3-cell widget. */
        fun widthDp(ctx: Context, widgetId: Int): Int = p(ctx).getInt("w_$widgetId", 0).takeIf { it > 0 }
            ?: runCatching {
                AppWidgetManager.getInstance(ctx).getAppWidgetOptions(widgetId).getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
            }.getOrDefault(0).takeIf { it > 0 } ?: 250

        fun saveWidth(ctx: Context, widgetId: Int, dp: Int) { if (dp > 0) p(ctx).edit().putInt("w_$widgetId", dp).apply() }
    }
}
