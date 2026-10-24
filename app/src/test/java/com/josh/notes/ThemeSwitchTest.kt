package com.josh.notes

import android.os.Looper
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.appcompat.app.AppCompatDelegate
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** v1.4.1: a theme change must recreate the screen exactly once (no flicker loop). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = NotesApp::class)
class ThemeSwitchTest {
    private val ctx get() = ApplicationProvider.getApplicationContext<android.app.Application>()

    @Before fun reset() {
        ctx.getSharedPreferences("settings", 0).edit().clear().commit()
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
    }

    private fun idle() = repeat(4) { shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(400)) }

    private fun switchTo(scenario: ActivityScenario<SettingsActivity>, t: ThemeMode): Int {
        idle()
        val before = LockableActivity.createCount
        scenario.onActivity { a ->
            val g = a.findViewById<RadioGroup>(R.id.theme_group)
            (0 until g.childCount).map { g.getChildAt(it) as RadioButton }.first { it.text == t.label }.performClick()
        }
        idle()
        scenario.onActivity { }  // let any pending relaunch finish
        idle()
        assertEquals(t, AppSettings.theme(ctx))
        return LockableActivity.createCount - before
    }

    @Test fun eachSwitchRecreatesOnce() {
        ActivityScenario.launch(SettingsActivity::class.java).use { s ->
            assertEquals("System -> Old School", 1, switchTo(s, ThemeMode.OLDSCHOOL))
            assertEquals("Old School -> Dark (same night mode)", 1, switchTo(s, ThemeMode.DARK))
            assertEquals("Dark -> Light (night mode changes)", 1, switchTo(s, ThemeMode.LIGHT))
            assertEquals("Light -> Old School (night + theme)", 1, switchTo(s, ThemeMode.OLDSCHOOL))
            assertEquals("Old School -> System", 1, switchTo(s, ThemeMode.SYSTEM))
            // Settling longer must not recreate again.
            val n = LockableActivity.createCount; idle(); assertEquals(n, LockableActivity.createCount)
        }
    }
}
