package com.josh.notes

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/** Quest checklists: finishing one shows a "Quest complete!" scroll (Old School) and awards XP. */
object Quests {
    const val XP_QUEST = 250
    private fun p(ctx: Context) = ctx.applicationContext.getSharedPreferences("settings", 0)

    /** Completed outside the app (widget): shown next time a screen opens. */
    fun setPending(ctx: Context, noteId: Long) = p(ctx).edit().putLong("pending_quest", noteId).apply()
    fun consumePending(ctx: Context): Long {
        val id = p(ctx).getLong("pending_quest", 0L)
        if (id != 0L) p(ctx).edit().remove("pending_quest").apply()
        return id
    }

    fun completed(a: Activity, note: Note, awardXp: Boolean = true) {
        if (awardXp) Levels.addXp(a, XP_QUEST, pendingIfLevel = false)
        val title = note.displayTitle.ifBlank { a.getString(R.string.untitled) }
        if (!AppSettings.oldSchool(a)) {
            com.google.android.material.snackbar.Snackbar.make(a.findViewById(android.R.id.content),
                "Quest complete: $title (+$XP_QUEST XP)", com.google.android.material.snackbar.Snackbar.LENGTH_LONG).show()
            SoundFx.play(a, SoundFx.Kind.QUEST)
            return
        }
        scroll(a, title)
    }

    private fun dp(a: Context, v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, a.resources.displayMetrics)

    /** Parchment scroll overlay; tap to close. Returns the overlay (tests snapshot it). */
    fun scroll(a: Activity, questTitle: String): View? {
        val root = a.window.decorView as? ViewGroup ?: return null
        val font = runCatching { ResourcesCompat.getFont(a, R.font.pixelify_sans) }.getOrNull()
        val overlay = FrameLayout(a).apply { isClickable = true; setBackgroundColor(0x88000000.toInt()) }
        val pad = dp(a, 22f).toInt()
        fun line(t: String, color: Int, size: Float, shadow: Boolean = false) = TextView(a).apply {
            text = t; setTextColor(color); textSize = size; typeface = font; gravity = Gravity.CENTER
            if (shadow) setShadowLayer(0.01f, dp(a, 1.5f), dp(a, 1.5f), Color.BLACK)
        }
        val box = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundResource(R.drawable.os_scroll); setPadding(pad, (pad * 1.6f).toInt(), pad, (pad * 1.4f).toInt())
            addView(line("Quest complete!", a.getColor(R.color.os_orange), 30f, shadow = true))
            addView(ImageView(a).apply { setImageResource(R.drawable.ic_os_skill_tasks) },
                LinearLayout.LayoutParams(dp(a, 56f).toInt(), dp(a, 56f).toInt()).apply { topMargin = pad / 2; bottomMargin = pad / 2 })
            addView(line("You have completed", Color.BLACK, 17f))
            addView(line(questTitle, 0xFF000080.toInt(), 20f).apply { setPadding(0, pad / 4, 0, pad / 2) })
            addView(line("Reward:", 0xFF5A2E0C.toInt(), 16f))
            addView(line("1 Quest point", Color.BLACK, 16f))
            addView(line("$XP_QUEST ${Levels.SKILL} XP", Color.BLACK, 16f))
            addView(line("Click here to continue", a.getColor(R.color.os_link_ink), 15f).apply { setPadding(0, pad / 2, 0, 0) })
        }
        overlay.addView(box, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER).apply { val m = dp(a, 18f).toInt(); setMargins(m, m, m, m) })
        var gone = false
        overlay.setOnClickListener {
            if (!gone) { gone = true; overlay.animate().alpha(0f).setDuration(200).withEndAction { root.removeView(overlay) }.start() }
        }
        root.addView(overlay, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        overlay.alpha = 0f; overlay.animate().alpha(1f).setDuration(180).start()
        SoundFx.play(a, SoundFx.Kind.QUEST)
        return overlay
    }
}

/** Tiny chiptune jingles synthesised at runtime (original melodies; no sampled audio). Off unless enabled in Settings. */
object SoundFx {
    enum class Kind { LEVEL_UP, QUEST }
    private const val RATE = 22050

    fun enabled(ctx: Context) = ctx.getSharedPreferences("settings", 0).getBoolean("sounds", false)
    fun setEnabled(ctx: Context, on: Boolean) = ctx.getSharedPreferences("settings", 0).edit().putBoolean("sounds", on).apply()

    private fun hz(semitonesFromA4: Int) = 440.0 * Math.pow(2.0, semitonesFromA4 / 12.0)

    /** (semitones from A4, duration seconds) — null pitch = rest. */
    private fun melody(kind: Kind): List<Pair<Int?, Double>> = when (kind) {
        // Rising arpeggio with a little turn at the top.
        Kind.LEVEL_UP -> listOf(3 to 0.09, 7 to 0.09, 10 to 0.09, 15 to 0.12, null to 0.04, 14 to 0.08, 15 to 0.34)
        // Short fanfare: call, answer, held resolution.
        Kind.QUEST -> listOf(-2 to 0.12, 3 to 0.12, 7 to 0.12, 10 to 0.24, null to 0.06, 7 to 0.10, 10 to 0.10,
            12 to 0.12, 15 to 0.5)
    }

    /** 16-bit mono PCM; pulse wave (25% duty) + a softer octave-down triangle, with a per-note decay envelope. */
    fun synth(kind: Kind): ShortArray {
        val out = ArrayList<Short>()
        for ((pitch, dur) in melody(kind)) {
            val n = (dur * RATE).toInt()
            val f = pitch?.let { hz(it) }
            for (i in 0 until n) {
                val t = i.toDouble() / RATE
                val v = if (f == null) 0.0 else {
                    val phase = (t * f) % 1.0
                    val pulse = if (phase < 0.25) 1.0 else -1.0
                    val tri = 2.0 * kotlin.math.abs(2.0 * ((t * f / 2) % 1.0) - 1.0) - 1.0
                    val env = minOf(1.0, i / (RATE * 0.004)) * exp(-3.2 * t / dur) * (if (i > n - RATE * 0.004) (n - i) / (RATE * 0.004) else 1.0)
                    (0.55 * pulse + 0.35 * tri + 0.1 * sin(2 * PI * f * t)) * env
                }
                out += (v * 0.32 * Short.MAX_VALUE).toInt().coerceIn(-32767, 32767).toShort()
            }
        }
        return out.toShortArray()
    }

    fun play(ctx: Context, kind: Kind) {
        if (!enabled(ctx)) return
        Thread {
            runCatching {
                val pcm = synth(kind)
                val track = AudioTrack.Builder()
                    .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                    .setAudioFormat(AudioFormat.Builder().setSampleRate(RATE).setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setTransferMode(AudioTrack.MODE_STATIC).setBufferSizeInBytes(pcm.size * 2).build()
                track.write(pcm, 0, pcm.size)
                track.play()
                Thread.sleep(pcm.size * 1000L / RATE + 150)
                track.release()
            }
        }.start()
    }
}
