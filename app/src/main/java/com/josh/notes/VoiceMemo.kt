package com.josh.notes

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaRecorder
import org.json.JSONObject
import org.vosk.Recognizer
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

/**
 * Voice memo recorder: one AudioRecord stream (16 kHz mono) is encoded to AAC (.m4a, MediaCodec+MediaMuxer)
 * and, optionally, fed to an offline Vosk recognizer at the same time (record + transcribe).
 */
class VoiceMemoRecorder(private val out: File, private val recognizer: Recognizer?, private val listener: Listener) {
    interface Listener {
        fun onLevel(level: Float) {}
        fun onPartial(text: String) {}
        fun onText(text: String) {}
        fun onError(e: Exception) {}
    }

    companion object { const val RATE = 16000 }

    @Volatile private var running = false
    @Volatile var elapsedMs = 0L; private set
    private var thread: Thread? = null
    var ok = false; private set

    fun start() { running = true; thread = Thread({ loop() }, "memo-rec").also { it.start() } }

    /** Stops and finalizes the file; returns duration in ms (0 if nothing recorded). */
    fun stop(): Long { running = false; thread?.join(8000); return if (ok) elapsedMs else 0L }

    @SuppressLint("MissingPermission")
    private fun loop() {
        var rec: AudioRecord? = null; var codec: MediaCodec? = null; var muxer: MediaMuxer? = null
        var track = -1; var muxStarted = false
        val info = MediaCodec.BufferInfo()
        fun drain(untilEos: Boolean) {
            val c = codec ?: return
            while (true) {
                val idx = c.dequeueOutputBuffer(info, if (untilEos) 10_000 else 0)
                when {
                    idx == MediaCodec.INFO_TRY_AGAIN_LATER -> if (!untilEos) return
                    idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> { track = muxer!!.addTrack(c.outputFormat); muxer!!.start(); muxStarted = true }
                    idx >= 0 -> {
                        val buf = c.getOutputBuffer(idx)!!
                        if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                        if (info.size > 0 && muxStarted) {
                            buf.position(info.offset); buf.limit(info.offset + info.size)
                            muxer!!.writeSampleData(track, buf, info)
                        }
                        c.releaseOutputBuffer(idx, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                    }
                }
            }
        }
        fun feed(bytes: ByteArray, len: Int, ptsUs: Long, eos: Boolean) {
            val c = codec!!
            while (true) {
                val i = c.dequeueInputBuffer(10_000)
                if (i >= 0) {
                    val ib = c.getInputBuffer(i)!!; ib.clear(); ib.put(bytes, 0, len)
                    c.queueInputBuffer(i, 0, len, ptsUs, if (eos) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0)
                    return
                }
                drain(false)
            }
        }
        try {
            val minBuf = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            rec = AudioRecord(MediaRecorder.AudioSource.MIC, RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuf, 8192) * 2)
            if (rec.state != AudioRecord.STATE_INITIALIZED) throw IllegalStateException("Microphone is busy or unavailable")
            val fmt = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, RATE, 1).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, 48_000)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16_384)
            }
            codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC).apply {
                configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE); start()
            }
            muxer = MediaMuxer(out.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            rec.startRecording()
            val shorts = ShortArray(RATE / 10) // 100 ms
            val bytes = ByteArray(shorts.size * 2)
            var samples = 0L
            while (running) {
                val n = rec.read(shorts, 0, shorts.size)
                if (n <= 0) continue
                var peak = 0
                for (i in 0 until n) { val v = abs(shorts[i].toInt()); if (v > peak) peak = v }
                listener.onLevel(peak / 32768f)
                recognizer?.let { r ->
                    if (r.acceptWaveForm(shorts, n)) JSONObject(r.result).optString("text").takeIf { it.isNotBlank() }?.let(listener::onText)
                    else listener.onPartial(JSONObject(r.partialResult).optString("partial"))
                }
                ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(shorts, 0, n)
                feed(bytes, n * 2, samples * 1_000_000L / RATE, false)
                samples += n
                elapsedMs = samples * 1000L / RATE
                drain(false)
            }
            feed(ByteArray(0), 0, samples * 1_000_000L / RATE, true)
            drain(true)
            recognizer?.let { r -> JSONObject(r.finalResult).optString("text").takeIf { it.isNotBlank() }?.let(listener::onText) }
            ok = muxStarted && samples > RATE / 4
        } catch (e: Exception) {
            listener.onError(e)
        } finally {
            runCatching { rec?.stop() }; rec?.release()
            runCatching { codec?.stop() }; codec?.release()
            runCatching { if (muxStarted) muxer?.stop() }; runCatching { muxer?.release() }
            if (!ok) out.delete()
        }
    }
}

/** Decodes any audio file to 16 kHz mono PCM16 chunks (for transcribing a saved memo with Vosk). */
object AudioDecoder {
    fun decode(file: File, onProgress: (Float) -> Unit, onChunk: (ShortArray, Int) -> Unit) {
        val ex = MediaExtractor(); ex.setDataSource(file.path)
        val trackIdx = (0 until ex.trackCount).firstOrNull { ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
            ?: throw IllegalStateException("No audio track")
        ex.selectTrack(trackIdx)
        val fmt = ex.getTrackFormat(trackIdx)
        val durUs = if (fmt.containsKey(MediaFormat.KEY_DURATION)) fmt.getLong(MediaFormat.KEY_DURATION) else 0L
        var srcRate = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var channels = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        val dec = MediaCodec.createDecoderByType(fmt.getString(MediaFormat.KEY_MIME)!!)
        dec.configure(fmt, null, null, 0); dec.start()
        val info = MediaCodec.BufferInfo()
        var inEos = false; var outEos = false
        var pos = 0.0 // resampler position in source samples
        val outBuf = ShortArray(8192); var outN = 0
        var prev = 0f
        try {
            while (!outEos) {
                if (!inEos) {
                    val i = dec.dequeueInputBuffer(10_000)
                    if (i >= 0) {
                        val n = ex.readSampleData(dec.getInputBuffer(i)!!, 0)
                        if (n < 0) { dec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inEos = true }
                        else { dec.queueInputBuffer(i, 0, n, ex.sampleTime, 0); ex.advance() }
                    }
                }
                val o = dec.dequeueOutputBuffer(info, 10_000)
                if (o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    srcRate = dec.outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    channels = dec.outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                } else if (o >= 0) {
                    val b = dec.getOutputBuffer(o)!!.order(ByteOrder.LITTLE_ENDIAN)
                    b.position(info.offset); b.limit(info.offset + info.size)
                    val sb = b.asShortBuffer()
                    val frames = sb.remaining() / channels
                    val step = srcRate / 16000.0
                    // downmix to mono floats
                    val mono = FloatArray(frames)
                    for (f in 0 until frames) { var acc = 0; for (ch in 0 until channels) acc += sb.get(f * channels + ch); mono[f] = acc.toFloat() / channels }
                    // linear resample
                    while (pos < frames) {
                        val i0 = pos.toInt(); val frac = (pos - i0).toFloat()
                        val a = if (i0 - 1 >= 0) mono[i0 - 1] else prev
                        val s = if (i0 < frames) a + (mono[i0] - a) * frac else a
                        outBuf[outN++] = s.toInt().coerceIn(-32768, 32767).toShort()
                        if (outN == outBuf.size) { onChunk(outBuf, outN); outN = 0 }
                        pos += step
                    }
                    pos -= frames
                    if (frames > 0) prev = mono[frames - 1]
                    if (durUs > 0) onProgress((info.presentationTimeUs.toFloat() / durUs).coerceIn(0f, 1f))
                    dec.releaseOutputBuffer(o, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outEos = true
                }
            }
            if (outN > 0) onChunk(outBuf, outN)
        } finally { runCatching { dec.stop() }; dec.release(); ex.release() }
    }
}
