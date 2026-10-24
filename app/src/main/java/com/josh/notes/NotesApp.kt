package com.josh.notes

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.DynamicColorsOptions

enum class ThemeMode(val label: String, val night: Int) {
    SYSTEM("System default", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM),
    LIGHT("Light", AppCompatDelegate.MODE_NIGHT_NO),
    DARK("Dark", AppCompatDelegate.MODE_NIGHT_YES),
    OLDSCHOOL("Old School (pixel adventure style)", AppCompatDelegate.MODE_NIGHT_YES);
}

object AppSettings {
    private fun p(ctx: Context) = ctx.getSharedPreferences("settings", 0)

    fun theme(ctx: Context) = runCatching { ThemeMode.valueOf(p(ctx).getString("theme", null) ?: "SYSTEM") }
        .getOrDefault(ThemeMode.SYSTEM)

    /** Saves the choice only; [switchTheme] applies it with exactly one recreate. */
    fun setTheme(ctx: Context, t: ThemeMode) { p(ctx).edit().putString("theme", t.name).commit() }

    /** Everything that affects how an activity is themed; activities recreate (once) when it differs. */
    fun signature(ctx: Context) = theme(ctx).name + "|" + dynamicColor(ctx)

    private fun isNight(uiMode: Int) = uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
        android.content.res.Configuration.UI_MODE_NIGHT_YES

    /**
     * Switch theme from [a] with a single, cross-faded recreate. If the day/night value actually changes,
     * AppCompat recreates the activity itself (and later any in the back stack) — calling recreate() as well caused a
     * double recreate (the flicker). Otherwise (e.g. Dark ↔ Old School) we recreate manually.
     */
    fun switchTheme(a: android.app.Activity, t: ThemeMode) {
        if (theme(a) == t) return
        setTheme(a, t)
        val currentNight = isNight(a.resources.configuration.uiMode)
        val targetNight = when (t.night) {
            AppCompatDelegate.MODE_NIGHT_YES -> true
            AppCompatDelegate.MODE_NIGHT_NO -> false
            // Same rule AppCompat uses for FOLLOW_SYSTEM: the application configuration.
            else -> isNight(a.applicationContext.resources.configuration.uiMode)
        }
        a.window.setWindowAnimations(R.style.ThemeSwitchFade)
        AppCompatDelegate.setDefaultNightMode(t.night)
        if (currentNight == targetNight) a.recreate()
        // Safety net if AppCompat didn't relaunch after all: still exactly one recreate, never a loop.
        else (a as? LockableActivity)?.let { la -> la.window.decorView.postDelayed({ la.recreateIfThemeChanged() }, 700) }
    }

    fun recreateFaded(a: android.app.Activity) { a.window.setWindowAnimations(R.style.ThemeSwitchFade); a.recreate() }

    fun dynamicColor(ctx: Context) = p(ctx).getBoolean("dynamic_color", false) && theme(ctx) != ThemeMode.OLDSCHOOL
    fun oldSchool(ctx: Context) = theme(ctx) == ThemeMode.OLDSCHOOL
    /** "Level up" banner when finishing a checklist / reminder (Old School theme only). */
    fun levelUps(ctx: Context) = p(ctx).getBoolean("level_ups", true)
    fun setLevelUps(ctx: Context, on: Boolean) = p(ctx).edit().putBoolean("level_ups", on).apply()
    fun setDynamicColor(ctx: Context, on: Boolean) { p(ctx).edit().putBoolean("dynamic_color", on).commit() }
}

class NotesApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppCompatDelegate.setDefaultNightMode(AppSettings.theme(this).night)
        // Material You colours (Android 12+), evaluated per activity creation so the toggle applies on recreate.
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(a: android.app.Activity) = AppLock.onActivityStarted()
            override fun onActivityStopped(a: android.app.Activity) = AppLock.onActivityStopped()
            override fun onActivityCreated(a: android.app.Activity, b: android.os.Bundle?) {}
            override fun onActivityResumed(a: android.app.Activity) {}
            override fun onActivityPaused(a: android.app.Activity) {}
            override fun onActivitySaveInstanceState(a: android.app.Activity, b: android.os.Bundle) {}
            override fun onActivityDestroyed(a: android.app.Activity) {}
        })
        // Re-arm reminders (covers app updates and force-stops); cheap, runs off the main thread.
        Thread { runCatching { Reminders.rescheduleAll(this) }; runCatching { Attachments.cleanupOrphans(this, NoteStore.all(this)) } }.start()
        runCatching { Backup.schedule(this) }
        DynamicColors.applyToActivitiesIfAvailable(this,
            DynamicColorsOptions.Builder().setPrecondition { a, _ -> AppSettings.dynamicColor(a) }.build())
    }
}
