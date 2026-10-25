package com.josh.notes

import android.content.Context
import android.media.AudioManager
import android.view.KeyEvent

/** Which hardware gesture starts dictation (Settings). */
enum class DictationTrigger(val label: String) {
    EITHER("Long-press either volume key"),
    UP("Long-press Volume Up"),
    DOWN("Long-press Volume Down"),
    BOTH("Press Volume Up + Down together"),
    OFF("Off");

    companion object {
        fun get(ctx: Context): DictationTrigger = runCatching {
            valueOf(ctx.getSharedPreferences("settings", 0).getString("trigger", null) ?: EITHER.name)
        }.getOrDefault(EITHER)
        fun set(ctx: Context, t: DictationTrigger) =
            ctx.getSharedPreferences("settings", 0).edit().putString("trigger", t.name).apply()
    }
}

/**
 * Volume-key dictation shortcut while the app is in the foreground (fed from [LockableActivity.dispatchKeyEvent]).
 *
 * With "Volume keys don't change volume while using the shortcut" on (default), every down/repeat/up event of the
 * keys the shortcut uses is consumed, so holding a key never nudges the volume. The long-press is detected by our
 * own timer; if the key is released before that (a normal short press), exactly one volume step is applied then via
 * AudioManager, so ordinary volume control keeps working. With the setting off the keys behave as usual (volume
 * changes) and a long-press still starts dictation.
 */
class VolumeKeyDictation(
    private val context: Context,
    private val adjust: (Int) -> Unit,
    private val onTrigger: () -> Unit,
) {
    constructor(context: Context, onTrigger: () -> Unit) : this(context, adjust = defaultAdjust(context), onTrigger = onTrigger)

    companion object {
        const val LONG_PRESS_MS = 500L
        private fun defaultAdjust(context: Context): (Int) -> Unit = { dir ->
            (context.getSystemService(Context.AUDIO_SERVICE) as AudioManager)
                .adjustSuggestedStreamVolume(dir, AudioManager.USE_DEFAULT_STREAM_TYPE, AudioManager.FLAG_SHOW_UI)
        }
        fun silentKeys(ctx: Context) = ctx.getSharedPreferences("settings", 0).getBoolean("volume_keys_silent", true)
        fun setSilentKeys(ctx: Context, on: Boolean) = ctx.getSharedPreferences("settings", 0).edit().putBoolean("volume_keys_silent", on).apply()
    }

    private var fired = false
    private val held = mutableMapOf<Int, Long>()   // keyCode -> downTime
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val longPress = Runnable { if (held.isNotEmpty() && !fired && DictationTrigger.get(context) != DictationTrigger.BOTH) fire() }

    private fun fire() { fired = true; handler.removeCallbacks(longPress); onTrigger() }

    private fun intercepts(code: Int): Boolean = when (DictationTrigger.get(context)) {
        DictationTrigger.EITHER, DictationTrigger.BOTH ->
            code == KeyEvent.KEYCODE_VOLUME_UP || code == KeyEvent.KEYCODE_VOLUME_DOWN
        DictationTrigger.UP -> code == KeyEvent.KEYCODE_VOLUME_UP
        DictationTrigger.DOWN -> code == KeyEvent.KEYCODE_VOLUME_DOWN
        DictationTrigger.OFF -> false
    }

    /** Returns true if the event was consumed (the system must not change the volume for it). */
    fun handle(event: KeyEvent): Boolean {
        val code = event.keyCode
        if (!intercepts(code)) return false
        val silent = silentKeys(context)
        val both = DictationTrigger.get(context) == DictationTrigger.BOTH
        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                if (event.repeatCount == 0) {
                    if (held.isEmpty()) fired = false
                    held[code] = event.downTime
                    if (both && held.size == 2 && !fired) fire()
                    else if (!both) { handler.removeCallbacks(longPress); handler.postDelayed(longPress, LONG_PRESS_MS) }
                } else if (!fired && !both && event.eventTime - event.downTime >= LONG_PRESS_MS) fire()
            }
            KeyEvent.ACTION_UP -> {
                val wasHeld = held.remove(code) != null
                if (held.isEmpty()) handler.removeCallbacks(longPress)
                // A short press with the volume held back: apply the one step it would have made.
                if (silent && wasHeld && !fired && !event.isCanceled)
                    adjust(if (code == KeyEvent.KEYCODE_VOLUME_UP) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER)
                if (held.isEmpty()) fired = false
            }
        }
        return silent
    }
}

/** What the mic button, widget mic and volume-key trigger do. */
enum class VoiceAction(val label: String) {
    DICTATE("Dictate text (speech to text)"),
    MEMO("Record a voice memo"),
    BOTH("Record a voice memo and transcribe it (offline)");

    companion object {
        fun get(ctx: Context): VoiceAction = runCatching {
            valueOf(ctx.getSharedPreferences("settings", 0).getString("voice_action", null) ?: DICTATE.name)
        }.getOrDefault(DICTATE)
        fun set(ctx: Context, v: VoiceAction) = ctx.getSharedPreferences("settings", 0).edit().putString("voice_action", v.name).apply()
    }
}
