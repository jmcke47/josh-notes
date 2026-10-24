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
 * Volume-key dictation trigger while the app is in the foreground.
 * Intercepted keys still change volume on a short press (re-dispatched to AudioManager on key-up).
 * Keys not used by the current trigger are left completely untouched.
 */
class VolumeKeyDictation(private val context: Context, private val onTrigger: () -> Unit) {
    private var fired = false
    private val held = mutableSetOf<Int>()

    private fun intercepts(code: Int): Boolean = when (DictationTrigger.get(context)) {
        DictationTrigger.EITHER, DictationTrigger.BOTH ->
            code == KeyEvent.KEYCODE_VOLUME_UP || code == KeyEvent.KEYCODE_VOLUME_DOWN
        DictationTrigger.UP -> code == KeyEvent.KEYCODE_VOLUME_UP
        DictationTrigger.DOWN -> code == KeyEvent.KEYCODE_VOLUME_DOWN
        DictationTrigger.OFF -> false
    }

    /** Returns true if consumed. */
    fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (!intercepts(keyCode)) return false
        if (event.repeatCount == 0) {
            if (held.isEmpty()) fired = false
            held += keyCode
            event.startTracking()
            if (DictationTrigger.get(context) == DictationTrigger.BOTH && held.size == 2 && !fired) {
                fired = true; onTrigger()
            }
        }
        return true
    }

    fun onKeyLongPress(keyCode: Int): Boolean {
        if (!intercepts(keyCode)) return false
        if (DictationTrigger.get(context) != DictationTrigger.BOTH && !fired) { fired = true; onTrigger() }
        return true
    }

    fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (!intercepts(keyCode)) return false
        held -= keyCode
        if (!fired && !event.isCanceled) {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.adjustSuggestedStreamVolume(
                if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER,
                AudioManager.USE_DEFAULT_STREAM_TYPE, AudioManager.FLAG_SHOW_UI
            )
        }
        if (held.isEmpty()) fired = false
        return true
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
