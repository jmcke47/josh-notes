package com.josh.notes

import android.media.AudioManager
import android.view.KeyEvent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = NotesApp::class)
class VolumeKeyTest {
    private val ctx get() = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val adjusts = mutableListOf<Int>()
    private var triggers = 0
    private lateinit var keys: VolumeKeyDictation

    @Before fun setUp() {
        ctx.getSharedPreferences("settings", 0).edit().remove("volume_keys_silent").putString("trigger", "EITHER").commit()
        keys = VolumeKeyDictation(ctx, { adjusts += it }) { triggers++ }
    }

    private fun ev(action: Int, code: Int, down: Long, at: Long, repeat: Int = 0) = KeyEvent(down, at, action, code, repeat)
    private val UP = KeyEvent.KEYCODE_VOLUME_UP
    private val DOWN = KeyEvent.KEYCODE_VOLUME_DOWN

    @Test fun shortPressConsumedThenOneVolumeStep() {
        assertTrue(VolumeKeyDictation.silentKeys(ctx)) // on by default
        assertTrue(keys.handle(ev(KeyEvent.ACTION_DOWN, UP, 0, 0)))
        assertTrue(adjusts.isEmpty()) // nothing before we know it's a short press
        assertTrue(keys.handle(ev(KeyEvent.ACTION_UP, UP, 0, 120)))
        assertEquals(listOf(AudioManager.ADJUST_RAISE), adjusts)
        assertEquals(0, triggers)
    }

    @Test fun longPressStartsDictationWithoutVolumeChange() {
        assertTrue(keys.handle(ev(KeyEvent.ACTION_DOWN, DOWN, 0, 0)))
        for (t in listOf(100L, 200L, 300L, 400L)) assertTrue(keys.handle(ev(KeyEvent.ACTION_DOWN, DOWN, 0, t, repeat = (t / 100).toInt())))
        assertEquals(0, triggers)
        assertTrue(keys.handle(ev(KeyEvent.ACTION_DOWN, DOWN, 0, 550, repeat = 5)))
        assertEquals(1, triggers)
        assertTrue(keys.handle(ev(KeyEvent.ACTION_DOWN, DOWN, 0, 650, repeat = 6)))
        assertTrue(keys.handle(ev(KeyEvent.ACTION_UP, DOWN, 0, 700)))
        assertEquals(1, triggers)
        assertTrue(adjusts.isEmpty())
    }

    @Test fun bothKeysAndUnusedKeys() {
        DictationTrigger.set(ctx, DictationTrigger.BOTH)
        ctx.getSharedPreferences("settings", 0).edit().putString("trigger", "BOTH").commit()
        assertTrue(keys.handle(ev(KeyEvent.ACTION_DOWN, UP, 0, 0)))
        assertTrue(keys.handle(ev(KeyEvent.ACTION_DOWN, DOWN, 50, 50)))
        assertEquals(1, triggers)
        keys.handle(ev(KeyEvent.ACTION_UP, UP, 0, 200)); keys.handle(ev(KeyEvent.ACTION_UP, DOWN, 50, 210))
        assertTrue(adjusts.isEmpty())
        // Trigger on Volume Up only: Volume Down is left completely alone.
        ctx.getSharedPreferences("settings", 0).edit().putString("trigger", "UP").commit()
        assertFalse(keys.handle(ev(KeyEvent.ACTION_DOWN, DOWN, 0, 0)))
        ctx.getSharedPreferences("settings", 0).edit().putString("trigger", "OFF").commit()
        assertFalse(keys.handle(ev(KeyEvent.ACTION_DOWN, UP, 0, 0)))
    }

    @Test fun settingOffKeepsNormalVolumeButLongPressStillWorks() {
        VolumeKeyDictation.setSilentKeys(ctx, false)
        assertFalse(keys.handle(ev(KeyEvent.ACTION_DOWN, UP, 0, 0)))
        assertFalse(keys.handle(ev(KeyEvent.ACTION_DOWN, UP, 0, 600, repeat = 6)))
        assertEquals(1, triggers)
        assertFalse(keys.handle(ev(KeyEvent.ACTION_UP, UP, 0, 650)))
        assertTrue(adjusts.isEmpty()) // the system already changed the volume itself
    }
}
