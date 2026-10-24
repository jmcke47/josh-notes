package com.josh.notes.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import com.josh.notes.AppSettings
import com.josh.notes.R
import com.josh.notes.ThemeMode

/** Widget look that follows the app's theme setting (the launcher's own day/night is only used for "System"). */
enum class WidgetTheme(
    val item: Int,
    val background: Int,
    val mic: Int,
    val add: Int,
    /** Header title view shown for this theme (the others are hidden); Old School uses a pixel-font bitmap. */
    val title: Int,
    val listBackground: Int = 0,
) {
    SYSTEM(R.layout.widget_item, R.drawable.widget_bg, R.drawable.ic_mic_widget, R.drawable.ic_add_widget, R.id.widget_title),
    LIGHT(R.layout.widget_item_light, R.drawable.widget_bg_light, R.drawable.ic_mic_widget_light, R.drawable.ic_add_widget_light, R.id.widget_title_light),
    DARK(R.layout.widget_item_dark, R.drawable.widget_bg_dark, R.drawable.ic_mic_widget_dark, R.drawable.ic_add_widget_dark, R.id.widget_title_dark),
    OLDSCHOOL(R.layout.widget_item_os, R.drawable.os_widget_bg, R.drawable.ic_mic_widget_os, R.drawable.ic_add_widget_os, R.id.widget_title_img, R.drawable.os_chatbox);

    /**
     * Every theme uses the same frame layout, so a theme change is applied by the launcher as a plain re-apply of
     * explicit background/icon/visibility actions instead of swapping layouts (which some launchers kept stale).
     */
    val header: Int get() = R.layout.widget_frame

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
