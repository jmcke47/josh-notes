package com.josh.notes

import android.os.Looper
import android.view.View
import android.widget.Button
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = NotesApp::class)
class UpdateAutoCheckTest {
    private val app get() = ApplicationProvider.getApplicationContext<android.app.Application>()
    private var now = 1_000_000_000L
    private var calls = 0

    @Before fun clean() {
        app.getSharedPreferences("updater", 0).edit().clear().commit()
        app.getSharedPreferences("settings", 0).edit().clear().commit()
    }

    private fun controller(code: Long? = BuildConfig.VERSION_CODE + 1L, fail: Boolean = false, online: Boolean = true): UpdateController {
        val a = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
        return UpdateController(a).apply {
            clock = { now }; this.online = { online }
            fetcher = { calls++; if (fail) throw IOException("boom") else UpdateController.Manifest(code!!, "9.9.$code", "https://x.invalid/a.apk", "n") }
        }
    }

    private fun run(c: UpdateController): UpdateController.Manifest? {
        var got: UpdateController.Manifest? = null
        c.autoCheck { got = it }
        repeat(100) { if (got == null) { Thread.sleep(10); shadowOf(Looper.getMainLooper()).idle() } }
        return got
    }

    @Test fun newerVersionPrompts() = assertEquals(BuildConfig.VERSION_CODE + 1L, run(controller())?.versionCode)

    @Test fun silentWhenUpToDateOfflineOrError() {
        assertNull(run(controller(code = BuildConfig.VERSION_CODE.toLong())))
        app.getSharedPreferences("updater", 0).edit().clear().commit()
        assertNull(run(controller(fail = true)))
        app.getSharedPreferences("updater", 0).edit().clear().commit()
        val before = calls
        assertNull(run(controller(online = false)))
        assertEquals("offline: no network call", before, calls)
    }

    @Test fun atMostEveryTenMinutes() {
        val c = controller()
        assertNotNull(run(c))
        assertFalse(c.autoCheck { })                       // immediately again: skipped
        now += UpdateController.AUTO_INTERVAL_MS - 1000
        assertFalse(c.autoCheck { })
        now += 2000
        assertTrue(c.autoCheck { })
    }

    @Test fun toggleOffDisables() {
        assertTrue(AppSettings.checkUpdatesOnOpen(app))      // on by default
        AppSettings.setCheckUpdatesOnOpen(app, false)
        val before = calls
        assertFalse(controller().autoCheck { })
        assertEquals(before, calls)
    }

    @Test fun promptHasUpdateAndLater() {
        app.getSharedPreferences("settings", 0).edit().putString("theme", "OLDSCHOOL").commit()
        val a = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        var updated = 0
        val m = UpdateController.Manifest(99, "9.9.0", "https://x.invalid/a.apk", "")
        val p = UpdatePrompt.show(a, m) { updated++ }!!
        UpdatePrompt.show(a, m) { updated++ }               // never stacks
        val content = a.findViewById<android.view.ViewGroup>(android.R.id.content)
        assertEquals(1, (0 until content.childCount).count { content.getChildAt(it).tag == UpdatePrompt.TAG })
        fun button(root: View, t: String): Button? = when (root) {
            is Button -> root.takeIf { it.text.toString() == t }
            is android.view.ViewGroup -> (0 until root.childCount).firstNotNullOfOrNull { button(root.getChildAt(it), t) }
            else -> null
        }
        val p2 = content.findViewWithTag<View>(UpdatePrompt.TAG)
        button(p2, "Later")!!.performClick()
        assertNull(content.findViewWithTag<View>(UpdatePrompt.TAG)); assertEquals(0, updated)
        val p3 = UpdatePrompt.show(a, m) { updated++ }!!
        button(p3, "Update")!!.performClick()
        assertEquals(1, updated); assertNull(content.findViewWithTag<View>(UpdatePrompt.TAG))
        assertNotNull(p)
    }
}
