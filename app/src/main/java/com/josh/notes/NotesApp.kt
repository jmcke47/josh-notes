package com.josh.notes

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.DynamicColorsOptions

enum class ThemeMode(val label: String, val night: Int) {
    SYSTEM("System default", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM),
    LIGHT("Light", AppCompatDelegate.MODE_NIGHT_NO),
    DARK("Dark", AppCompatDelegate.MODE_NIGHT_YES);
}

object AppSettings {
    private fun p(ctx: Context) = ctx.getSharedPreferences("settings", 0)

    fun theme(ctx: Context) = runCatching { ThemeMode.valueOf(p(ctx).getString("theme", null) ?: "SYSTEM") }
        .getOrDefault(ThemeMode.SYSTEM)

    fun setTheme(ctx: Context, t: ThemeMode) {
        p(ctx).edit().putString("theme", t.name).apply()
        AppCompatDelegate.setDefaultNightMode(t.night) // recreates open activities immediately
    }

    fun dynamicColor(ctx: Context) = p(ctx).getBoolean("dynamic_color", false)
    fun setDynamicColor(ctx: Context, on: Boolean) = p(ctx).edit().putBoolean("dynamic_color", on).apply()
}

class NotesApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppCompatDelegate.setDefaultNightMode(AppSettings.theme(this).night)
        // Material You colours (Android 12+), evaluated per activity creation so the toggle applies on recreate.
        DynamicColors.applyToActivitiesIfAvailable(this,
            DynamicColorsOptions.Builder().setPrecondition { a, _ -> AppSettings.dynamicColor(a) }.build())
    }
}
