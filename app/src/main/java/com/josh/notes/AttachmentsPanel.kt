package com.josh.notes

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import org.json.JSONObject
import org.vosk.Recognizer
import java.io.File
import java.util.concurrent.Executors

/**
 * Editor attachments: photos (camera / photo picker), files (OPEN_DOCUMENT), voice memos
 * (record, record+transcribe with Vosk, play/pause/seek, transcribe later).
 */
class AttachmentsPanel(
    private val activity: AppCompatActivity,
    private val box: LinearLayout,
    private val dictation: DictationController,
    private val ensureNoteId: () -> Long,
    private val current: () -> List<Attachment>,
    private val onChanged: (List<Attachment>) -> Unit,
    private val insertText: (String) -> Unit,
) {
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val d = activity.resources.displayMetrics.density
    private var captureFile: File? = null
    private var pendingTranscribe: Boolean? = null

    private val takePicture = activity.registerForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val f = captureFile ?: return@registerForActivityResult
        if (ok && f.length() > 0) importAsync { Attachments.importFile(activity, ensureNoteId(), f, "image/jpeg", "Photo.jpg").also { f.delete() } }
        else f.delete()
    }
    private val pickMedia = activity.registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { uris ->
        uris.forEach { u -> importAsync { Attachments.importUri(activity, ensureNoteId(), u) } }
    }
    private val pickFile = activity.registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.forEach { u -> importAsync { Attachments.importUri(activity, ensureNoteId(), u) } }
    }
    private val micPermission = activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        val t = pendingTranscribe; pendingTranscribe = null
        if (ok && t != null) recordMemo(t) else if (!ok) toast("Microphone permission is needed to record")
    }

    // ------------------------------------------------------------------ add

    fun showAddMenu() {
        val items = arrayOf("Take photo", "Photo or video from gallery", "File", "Record voice memo", "Record + transcribe (offline)")
        osDialog(activity).setTitle(R.string.attach)
            .setItems(items) { _, w ->
                when (w) {
                    0 -> capturePhoto()
                    1 -> pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                    2 -> pickFile.launch(arrayOf("*/*"))
                    3 -> recordMemo(false)
                    4 -> recordMemo(true)
                }
            }.show()
    }

    private fun capturePhoto() {
        val dir = File(activity.cacheDir, "capture").apply { mkdirs() }
        val f = File(dir, "cap-${System.currentTimeMillis()}.jpg"); captureFile = f
        val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", f)
        try { takePicture.launch(uri) } catch (_: ActivityNotFoundException) { toast("No camera app found") }
    }

    private fun importAsync(block: () -> Attachment) {
        val bar = toast("Adding attachment…")
        io.execute {
            val r = runCatching(block)
            main.post {
                bar.cancel()
                r.onSuccess { add(it) }.onFailure { toast("Couldn't add attachment: ${it.message}") }
            }
        }
    }

    private fun add(a: Attachment) { onChanged(current() + a); render() }

    private fun remove(a: Attachment) {
        if (playing?.first == a.id) stopPlayer()
        onChanged(current().filter { it.id != a.id })
        Attachments.delete(activity, ensureNoteId(), a)
        render()
    }

    private fun replace(a: Attachment) { onChanged(current().map { if (it.id == a.id) a else it }); render() }

    // ------------------------------------------------------------------ render

    fun render() {
        box.removeAllViews()
        val list = current()
        if (list.isEmpty()) return
        val noteId = ensureNoteIdIfExisting() ?: return
        val tiles = list.filter { !it.isAudio }
        if (tiles.isNotEmpty()) {
            val row = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; setPadding((16 * d).toInt(), (4 * d).toInt(), (16 * d).toInt(), (4 * d).toInt()) }
            tiles.forEach { row.addView(tile(noteId, it)) }
            box.addView(HorizontalScrollView(activity).apply { isHorizontalScrollBarEnabled = false; addView(row) })
        }
        list.filter { it.isAudio }.forEach { box.addView(audioRow(noteId, it)) }
    }

    private fun ensureNoteIdIfExisting(): Long? = runCatching { ensureNoteId() }.getOrNull()

    private fun tile(noteId: Long, a: Attachment): View {
        val size = (96 * d).toInt()
        val card = MaterialCardView(activity).apply {
            layoutParams = LinearLayout.LayoutParams(size, size).apply { marginEnd = (8 * d).toInt() }
            radius = 12 * d
            setOnClickListener { open(noteId, a) }
            setOnLongClickListener { tileMenu(noteId, a); true }
            contentDescription = a.name
        }
        if (a.isImage) {
            val iv = ImageView(activity).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
            card.addView(iv)
            io.execute {
                val bmp = runCatching { Attachments.thumbnail(activity, noteId, a, size) }.getOrNull()
                main.post { if (bmp != null) iv.setImageBitmap(bmp) else iv.setImageResource(R.drawable.ic_file) }
            }
        } else {
            card.addView(LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL; gravity = android.view.Gravity.CENTER
                setPadding((6 * d).toInt(), (6 * d).toInt(), (6 * d).toInt(), (6 * d).toInt())
                addView(ImageView(activity).apply { setImageResource(R.drawable.ic_file); layoutParams = LinearLayout.LayoutParams((32 * d).toInt(), (32 * d).toInt()) })
                addView(TextView(box.context).apply { text = a.name; maxLines = 2; textSize = 11f; gravity = android.view.Gravity.CENTER
                    ellipsize = android.text.TextUtils.TruncateAt.MIDDLE })
            })
        }
        return card
    }

    private fun tileMenu(noteId: Long, a: Attachment) {
        osDialog(activity).setTitle(a.name)
            .setItems(arrayOf("Open", "Share", "Delete")) { _, w ->
                when (w) { 0 -> open(noteId, a); 1 -> share(noteId, a); 2 -> confirmRemove(a) }
            }.show()
    }

    private fun confirmRemove(a: Attachment) {
        osDialog(activity).setTitle("Delete attachment?").setMessage(a.name)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ -> remove(a) }.show()
    }

    private fun open(noteId: Long, a: Attachment) {
        if (a.isImage) {
            activity.startActivity(Intent(activity, ImageViewerActivity::class.java)
                .putExtra(ImageViewerActivity.EXTRA_PATH, Attachments.file(activity, noteId, a).path)
                .putExtra(ImageViewerActivity.EXTRA_NAME, a.name))
            return
        }
        val i = Intent(Intent.ACTION_VIEW).setDataAndType(Attachments.uri(activity, noteId, a), a.mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try { activity.startActivity(i) } catch (_: ActivityNotFoundException) { toast("No app can open ${a.mime}") }
    }

    private fun share(noteId: Long, a: Attachment) {
        activity.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType(a.mime)
            .putExtra(Intent.EXTRA_STREAM, Attachments.uri(activity, noteId, a))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), a.name))
    }

    // ------------------------------------------------------------------ audio playback

    private var player: MediaPlayer? = null
    private var playing: Pair<String, View>? = null
    private val tick = object : Runnable {
        override fun run() {
            val p = player ?: return; val v = playing?.second ?: return
            v.findViewById<SeekBar>(R.id.seek).progress = p.currentPosition
            v.findViewById<TextView>(R.id.time).text = "${fmt(p.currentPosition.toLong())} / ${fmt(p.duration.toLong())}"
            main.postDelayed(this, 250)
        }
    }

    private fun audioRow(noteId: Long, a: Attachment): View {
        val v = LayoutInflater.from(box.context).inflate(R.layout.item_audio, box, false)
        val play = v.findViewById<ImageButton>(R.id.play)
        val seek = v.findViewById<SeekBar>(R.id.seek)
        v.findViewById<TextView>(R.id.time).text = fmt(a.durationMs)
        seek.max = a.durationMs.toInt().coerceAtLeast(1)
        v.findViewById<TextView>(R.id.transcript).apply {
            if (a.transcript.isNotBlank()) { visibility = View.VISIBLE; text = "“${a.transcript}”" }
        }
        play.setOnClickListener {
            if (playing?.first == a.id && player != null) {
                val p = player!!
                if (p.isPlaying) { p.pause(); play.setImageResource(R.drawable.ic_play) }
                else { p.start(); play.setImageResource(R.drawable.ic_pause); main.post(tick) }
            } else startPlayer(noteId, a, v)
        }
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar, p: Int, fromUser: Boolean) { if (fromUser && playing?.first == a.id) player?.seekTo(p) }
            override fun onStartTrackingTouch(s: SeekBar) {}
            override fun onStopTrackingTouch(s: SeekBar) {}
        })
        v.findViewById<ImageButton>(R.id.more).setOnClickListener {
            val opts = arrayOf(if (a.transcript.isBlank()) "Transcribe (offline)" else "Transcribe again", "Insert transcript into note", "Share", "Delete")
            osDialog(activity).setTitle(a.name).setItems(opts) { _, w ->
                when (w) {
                    0 -> dictation.withVoskModel("Transcribing") { transcribe(noteId, a) }
                    1 -> if (a.transcript.isNotBlank()) insertText(a.transcript) else toast("Transcribe it first")
                    2 -> share(noteId, a)
                    3 -> confirmRemove(a)
                }
            }.show()
        }
        return v
    }

    private fun startPlayer(noteId: Long, a: Attachment, v: View) {
        stopPlayer()
        val p = runCatching {
            MediaPlayer().apply { setDataSource(Attachments.file(activity, noteId, a).path); prepare() }
        }.getOrElse { toast("Can't play this memo: ${it.message}"); return }
        player = p; playing = a.id to v
        v.findViewById<SeekBar>(R.id.seek).max = p.duration.coerceAtLeast(1)
        v.findViewById<ImageButton>(R.id.play).setImageResource(R.drawable.ic_pause)
        p.setOnCompletionListener {
            v.findViewById<ImageButton>(R.id.play).setImageResource(R.drawable.ic_play)
            v.findViewById<SeekBar>(R.id.seek).progress = 0
            stopPlayer()
        }
        p.start(); main.post(tick)
    }

    fun stopPlayer() {
        main.removeCallbacks(tick)
        playing?.second?.findViewById<ImageButton>(R.id.play)?.setImageResource(R.drawable.ic_play)
        player?.release(); player = null; playing = null
    }

    // ------------------------------------------------------------------ recording

    private var recorder: VoiceMemoRecorder? = null
    private var recDialog: AlertDialog? = null

    /** Record a memo; with [transcribe] the same audio is transcribed offline (Vosk) live. */
    fun recordMemo(transcribe: Boolean) {
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            pendingTranscribe = transcribe; micPermission.launch(Manifest.permission.RECORD_AUDIO); return
        }
        if (transcribe) dictation.withVoskModel("Record + transcribe") { startRecording(true) } else startRecording(false)
    }

    private fun startRecording(transcribe: Boolean) {
        if (recorder != null) return
        stopPlayer()
        val noteId = ensureNoteId()
        val out = File(activity.cacheDir, "memo-${System.currentTimeMillis()}.m4a")
        val finals = StringBuilder()
        val pad = (24 * d).toInt()
        val timer = TextView(activity).apply { textSize = 34f; gravity = android.view.Gravity.CENTER; text = "0:00" }
        val level = LinearProgressIndicator(activity).apply { max = 100; isIndeterminate = false }
        val live = TextView(activity).apply { setPadding(0, pad / 2, 0, 0); visibility = if (transcribe) View.VISIBLE else View.GONE; text = "Loading offline model…" }
        val content = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad / 2, pad, 0)
            addView(timer); addView(level); addView(live) }
        var cancelled = false
        recDialog = osDialog(activity)
            .setTitle(if (transcribe) "Recording + transcribing" else "Recording voice memo")
            .setView(content).setCancelable(false)
            .setPositiveButton("Stop & save") { _, _ -> finishRecording(noteId, out, finals, transcribe, cancelled) }
            .setNegativeButton(android.R.string.cancel) { _, _ -> cancelled = true; finishRecording(noteId, out, finals, transcribe, true) }
            .show()
        io.execute {
            val rec: Recognizer? = if (transcribe) runCatching { Recognizer(dictation.loadModel(), VoiceMemoRecorder.RATE.toFloat()) }.getOrNull() else null
            main.post {
                if (recDialog == null) { rec?.close(); return@post }
                if (transcribe && rec == null) live.text = "(Transcription unavailable — recording audio only)"
                else if (transcribe) live.text = "Speak now…"
                val r = VoiceMemoRecorder(out, rec, object : VoiceMemoRecorder.Listener {
                    override fun onLevel(l: Float) { main.post { level.setProgressCompat((l * 140).toInt().coerceAtMost(100), false) } }
                    override fun onPartial(text: String) { main.post { live.text = (finals.toString() + " " + text).trim().ifEmpty { "Speak now…" } } }
                    override fun onText(text: String) { main.post { if (finals.isNotEmpty()) finals.append(' '); finals.append(text); live.text = finals } }
                    override fun onError(e: Exception) { main.post { toast("Recording problem: ${e.message}") } }
                })
                recorder = r; recognizer = rec
                r.start()
                main.post(object : Runnable { override fun run() {
                    val rr = recorder ?: return
                    timer.text = fmt(rr.elapsedMs); main.postDelayed(this, 200)
                } })
            }
        }
    }

    private var recognizer: Recognizer? = null

    private fun finishRecording(noteId: Long, out: File, finals: StringBuilder, transcribe: Boolean, cancelled: Boolean) {
        val r = recorder; recorder = null; recDialog = null
        val rec = recognizer; recognizer = null
        if (r == null) { rec?.close(); out.delete(); return }
        io.execute {
            val dur = r.stop()
            rec?.close()
            val result = if (cancelled || dur <= 0) null else runCatching {
                val name = "Voice memo " + java.text.SimpleDateFormat("d MMM HH:mm", java.util.Locale.getDefault()).format(java.util.Date()) + ".m4a"
                Attachments.importFile(activity, noteId, out, "audio/mp4", name, dur, finals.toString().trim())
            }
            out.delete()
            main.post {
                result?.onSuccess { a ->
                    add(a)
                    if (transcribe && a.transcript.isNotBlank()) insertText(a.transcript.replaceFirstChar { it.titlecase() })
                }?.onFailure { toast("Couldn't save memo: ${it.message}") }
                if (!cancelled && dur <= 0) toast("Nothing was recorded")
            }
        }
    }

    /** Stop any recording (e.g. leaving the screen) and keep what was captured. */
    fun stopRecordingIfAny() {
        recDialog?.let { it.getButton(AlertDialog.BUTTON_POSITIVE)?.performClick() }
    }

    // ------------------------------------------------------------------ transcribe saved memo

    private fun transcribe(noteId: Long, a: Attachment) {
        val bar = LinearProgressIndicator(activity).apply { max = 100 }
        val pad = (24 * d).toInt()
        val dlg = osDialog(activity).setTitle("Transcribing offline…")
            .setView(LinearLayout(activity).apply { setPadding(pad, pad / 2, pad, 0); addView(bar) })
            .setCancelable(false).show()
        io.execute {
            val r = runCatching {
                Recognizer(dictation.loadModel(), 16000f).use { rec ->
                    val sb = StringBuilder()
                    AudioDecoder.decode(Attachments.file(activity, noteId, a), { p -> main.post { bar.setProgressCompat((p * 100).toInt(), true) } }) { buf, n ->
                        if (rec.acceptWaveForm(buf, n)) JSONObject(rec.result).optString("text").takeIf { it.isNotBlank() }?.let { if (sb.isNotEmpty()) sb.append(' '); sb.append(it) }
                    }
                    JSONObject(rec.finalResult).optString("text").takeIf { it.isNotBlank() }?.let { if (sb.isNotEmpty()) sb.append(' '); sb.append(it) }
                    sb.toString()
                }
            }
            main.post {
                dlg.dismiss()
                r.onSuccess { t ->
                    if (t.isBlank()) toast("No speech found in this memo")
                    else {
                        replace(a.copy(transcript = t))
                        osDialog(activity).setTitle("Transcript").setMessage(t)
                            .setNegativeButton("Close", null)
                            .setPositiveButton("Insert into note") { _, _ -> insertText(t.replaceFirstChar { it.titlecase() }) }.show()
                    }
                }.onFailure { toast("Transcription failed: ${it.message}") }
            }
        }
    }

    fun release() { stopRecordingIfAny(); stopPlayer(); io.shutdown() }

    private fun toast(s: String) = Toast.makeText(activity, s, Toast.LENGTH_LONG).also { it.show() }

    companion object {
        fun fmt(ms: Long): String { val s = ms / 1000; return "%d:%02d".format(s / 60, s % 60) }
    }
}
