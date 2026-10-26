package com.josh.oldschool

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/** Tiny chiptune jingles synthesised at runtime (original melodies; no sampled audio). The app decides when it's on. */
object OsChiptune {
    enum class Kind { LEVEL_UP, QUEST, CORRECT, WRONG, TICK }
    const val RATE = 22050

    private fun hz(semitonesFromA4: Int) = 440.0 * Math.pow(2.0, semitonesFromA4 / 12.0)

    /** (semitones from A4, duration seconds); null pitch = rest. */
    private fun melody(kind: Kind): List<Pair<Int?, Double>> = when (kind) {
        Kind.LEVEL_UP -> listOf(3 to 0.09, 7 to 0.09, 10 to 0.09, 15 to 0.12, null to 0.04, 14 to 0.08, 15 to 0.34)
        Kind.QUEST -> listOf(-2 to 0.12, 3 to 0.12, 7 to 0.12, 10 to 0.24, null to 0.06, 7 to 0.10, 10 to 0.10,
            12 to 0.12, 15 to 0.5)
        Kind.CORRECT -> listOf(7 to 0.07, 14 to 0.16)
        Kind.WRONG -> listOf(-5 to 0.10, -8 to 0.22)
        Kind.TICK -> listOf(19 to 0.025)
    }

    /** 16-bit mono PCM: 25% pulse + octave-down triangle, per-note decay envelope. */
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

    /** Plays on a background thread (sonification stream). Check your own "sounds" setting before calling. */
    fun play(kind: Kind) {
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
