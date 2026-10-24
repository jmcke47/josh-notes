package com.josh.notes

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.SpeechService
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.Executors
import java.util.zip.ZipInputStream

/**
 * Offline voice dictation.
 *
 * Engines:
 *  - SYSTEM: Android's on-device recognizer (createOnDeviceSpeechRecognizer, API 31+),
 *    or on older devices the default recognizer with EXTRA_PREFER_OFFLINE.
 *  - VOSK: fully offline, bundled engine; English model (~40 MB) downloaded once on first use.
 * AUTO picks SYSTEM when an on-device recognizer exists, else VOSK.
 * Must be constructed during Activity onCreate (registers a permission launcher).
 */
class DictationController(
    private val activity: AppCompatActivity,
    private val onText: (String) -> Unit,
) {
    enum class Engine { AUTO, SYSTEM, VOSK }

    private val prefs = activity.getSharedPreferences("dictation", 0)
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private var pendingStart = false

    private var sysRecognizer: SpeechRecognizer? = null
    private var vosk: SpeechService? = null
    private var voskModel: Model? = null
    private var dialog: AlertDialog? = null
    private var liveText: TextView? = null
    private val voskFinals = StringBuilder()
    private var voskPartial = ""

    private val permission: ActivityResultLauncher<String> =
        activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted && pendingStart) start()
            else if (!granted) toast("Microphone permission is needed for dictation")
            pendingStart = false
        }

    var engine: Engine
        get() = runCatching { Engine.valueOf(prefs.getString("engine", null) ?: "AUTO") }.getOrDefault(Engine.AUTO)
        set(v) = prefs.edit { putString("engine", v.name) }

    private val modelDir get() = File(activity.filesDir, MODEL_NAME)
    fun modelReady() = File(modelDir, "am/final.mdl").exists() || File(modelDir, "conf/model.conf").exists()

    private fun onDeviceSystemAvailable() =
        Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(activity)

    fun start() {
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            pendingStart = true
            permission.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        when (engine) {
            Engine.VOSK -> startVoskOrDownload()
            Engine.SYSTEM -> startSystem()
            Engine.AUTO -> when {
                onDeviceSystemAvailable() -> startSystem()
                modelReady() -> startVosk()
                else -> offerChoice()
            }
        }
    }

    private fun offerChoice() {
        val b = MaterialAlertDialogBuilder(activity)
            .setTitle("Offline dictation")
            .setMessage("No on-device speech recognizer was found on this phone.\n\nDownload the offline English voice model (about $MODEL_SIZE_MB MB, one time)? Speech never leaves your phone.")
            .setPositiveButton("Download") { _, _ -> downloadModel { startVosk() } }
            .setNegativeButton(android.R.string.cancel, null)
        if (SpeechRecognizer.isRecognitionAvailable(activity))
            b.setNeutralButton("Use system (may be online)") { _, _ -> startSystem(forceStandard = true) }
        b.show()
    }

    // ---------------------------------------------------------------- System recognizer

    private fun startSystem(forceStandard: Boolean = false) {
        val onDevice = !forceStandard && onDeviceSystemAvailable()
        if (!onDevice && !SpeechRecognizer.isRecognitionAvailable(activity)) {
            toast("No speech recognizer on this phone; using Vosk"); startVoskOrDownload(); return
        }
        stopAll()
        val rec = if (onDevice && Build.VERSION.SDK_INT >= 31) SpeechRecognizer.createOnDeviceSpeechRecognizer(activity)
        else SpeechRecognizer.createSpeechRecognizer(activity)
        sysRecognizer = rec
        showDialog(if (onDevice) "Listening (on-device)…" else "Listening (offline preferred)…") { rec.stopListening() }
        rec.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { setLive("Speak now…") }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
            override fun onPartialResults(partial: Bundle?) {
                partial?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { setLive(it) }
            }
            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                finish(text)
            }
            override fun onError(error: Int) {
                val langMissing = error == 12 /*LANGUAGE_NOT_SUPPORTED*/ || error == 13 /*LANGUAGE_UNAVAILABLE*/
                stopAll()
                when {
                    error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> toast("Didn't catch that")
                    langMissing || error == SpeechRecognizer.ERROR_NETWORK || error == SpeechRecognizer.ERROR_SERVER ||
                            error == SpeechRecognizer.ERROR_CLIENT || error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                        MaterialAlertDialogBuilder(activity)
                            .setTitle("System recognizer unavailable")
                            .setMessage("Android's recognizer couldn't run offline (error $error). Use the fully offline Vosk engine instead?")
                            .setPositiveButton("Use Vosk") { _, _ -> engine = Engine.VOSK; startVoskOrDownload() }
                            .setNegativeButton(android.R.string.cancel, null).show()
                    else -> toast("Dictation error ($error)")
                }
            }
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
        }
        rec.startListening(intent)
    }

    // ---------------------------------------------------------------- Vosk

    private fun startVoskOrDownload() {
        if (modelReady()) startVosk()
        else MaterialAlertDialogBuilder(activity)
            .setTitle("Download offline voice model?")
            .setMessage("Vosk needs a one-time download of the English model (about $MODEL_SIZE_MB MB). After that dictation works with no internet.")
            .setPositiveButton("Download") { _, _ -> downloadModel { startVosk() } }
            .setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun startVosk() {
        stopAll()
        voskFinals.clear(); voskPartial = ""
        showDialog("Listening (offline)…") { vosk?.stop() }
        setLive("Loading model…")
        io.execute {
            val r = runCatching {
                val model = voskModel ?: Model(modelDir.absolutePath).also { voskModel = it }
                SpeechService(Recognizer(model, 16000f), 16000f)
            }
            main.post {
                r.onFailure { stopAll(); toast("Couldn't start offline dictation: ${it.message}") }
                r.onSuccess { svc ->
                    if (dialog == null) { svc.shutdown(); return@post }
                    vosk = svc
                    setLive("Speak now… tap Done when finished")
                    svc.startListening(object : org.vosk.android.RecognitionListener {
                        override fun onPartialResult(hypothesis: String?) {
                            voskPartial = hypothesis?.let { JSONObject(it).optString("partial") }.orEmpty()
                            setLive(liveVosk())
                        }
                        override fun onResult(hypothesis: String?) {
                            val t = hypothesis?.let { JSONObject(it).optString("text") }.orEmpty()
                            if (t.isNotBlank()) voskFinals.append(if (voskFinals.isEmpty()) "" else " ").append(t)
                            voskPartial = ""; setLive(liveVosk())
                        }
                        override fun onFinalResult(hypothesis: String?) {
                            val t = hypothesis?.let { JSONObject(it).optString("text") }.orEmpty()
                            if (t.isNotBlank()) voskFinals.append(if (voskFinals.isEmpty()) "" else " ").append(t)
                            finish(voskFinals.toString())
                        }
                        override fun onError(e: Exception?) { stopAll(); toast("Dictation error: ${e?.message}") }
                        override fun onTimeout() { finish(voskFinals.toString()) }
                    })
                }
            }
        }
    }

    /** Load (or reuse) the Vosk model; call off the main thread. */
    fun loadModel(): Model = voskModel ?: Model(modelDir.absolutePath).also { voskModel = it }

    /** Ensure the offline model exists, offering the one-time download; then runs [then] on the main thread. */
    fun withVoskModel(reason: String, then: () -> Unit) {
        if (modelReady()) { then(); return }
        MaterialAlertDialogBuilder(activity)
            .setTitle("Download offline voice model?")
            .setMessage("$reason needs the offline English model (about $MODEL_SIZE_MB MB, one time). Speech never leaves your phone.")
            .setPositiveButton("Download") { _, _ -> downloadModel(then) }
            .setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun liveVosk() = (voskFinals.toString() + " " + voskPartial).trim().ifEmpty { "Speak now…" }

    fun downloadModel(then: () -> Unit) {
        val bar = LinearProgressIndicator(activity).apply { max = 100; isIndeterminate = true }
        val box = LinearLayout(activity).apply {
            val p = (24 * resources.displayMetrics.density).toInt(); setPadding(p, p / 2, p, p / 2); addView(bar)
        }
        var cancelled = false
        val d = MaterialAlertDialogBuilder(activity).setTitle("Downloading voice model…").setView(box)
            .setCancelable(false).setNegativeButton(android.R.string.cancel) { _, _ -> cancelled = true }.show()
        io.execute {
            val tmpZip = File(activity.cacheDir, "vosk-model.zip")
            val tmpDir = File(activity.filesDir, "$MODEL_NAME.tmp")
            val r = runCatching {
                val c = URL(MODEL_URL).openConnection() as HttpURLConnection
                c.connectTimeout = 15000; c.readTimeout = 30000
                if (c.responseCode !in 200..299) throw IllegalStateException("HTTP ${c.responseCode}")
                val total = c.contentLengthLong
                c.inputStream.use { inp -> tmpZip.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024); var done = 0L; var last = -1
                    while (true) {
                        if (cancelled) throw InterruptedException("Cancelled")
                        val n = inp.read(buf); if (n < 0) break
                        out.write(buf, 0, n); done += n
                        if (total > 0) { val pct = (done * 100 / total).toInt()
                            if (pct != last) { last = pct; main.post { bar.isIndeterminate = false; bar.setProgressCompat(pct, true) } } }
                    }
                } }
                c.disconnect()
                main.post { bar.isIndeterminate = true }
                tmpDir.deleteRecursively(); tmpDir.mkdirs()
                val root = tmpDir.canonicalPath + File.separator
                ZipInputStream(tmpZip.inputStream().buffered()).use { zip ->
                    while (true) {
                        val e = zip.nextEntry ?: break
                        // strip the top-level folder inside the zip
                        val rel = e.name.substringAfter('/', "")
                        if (rel.isEmpty()) continue
                        val f = File(tmpDir, rel)
                        if (!f.canonicalPath.startsWith(root)) throw SecurityException("Bad zip entry")
                        if (e.isDirectory) f.mkdirs() else { f.parentFile?.mkdirs(); f.outputStream().use { zip.copyTo(it) } }
                    }
                }
                modelDir.deleteRecursively()
                if (!tmpDir.renameTo(modelDir)) throw IllegalStateException("Couldn't install model")
                if (!modelReady()) throw IllegalStateException("Model files missing after unzip")
            }
            tmpZip.delete(); if (r.isFailure) tmpDir.deleteRecursively()
            main.post {
                d.dismiss()
                r.onSuccess { toast("Offline voice model installed"); then() }
                r.onFailure { if (!cancelled) toast("Model download failed: ${it.message}") }
            }
        }
    }

    // ---------------------------------------------------------------- UI helpers

    private fun showDialog(title: String, onDone: () -> Unit) {
        val pad = (24 * activity.resources.displayMetrics.density).toInt()
        val text = TextView(activity).apply {
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge)
            minLines = 3
        }
        val bar = LinearProgressIndicator(activity).apply { isIndeterminate = true }
        val box = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL; setPadding(pad, pad / 2, pad, 0); addView(text); addView(bar)
        }
        liveText = text
        dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(title).setView(box).setCancelable(false)
            .setPositiveButton("Done", null)
            .setNegativeButton(android.R.string.cancel) { _, _ -> cancel() }
            .show().also { d ->
                // Keep dialog open on Done until the final result arrives.
                d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    it.isEnabled = false; setLive((liveText?.text ?: "").toString() + "\n(finishing…)"); onDone()
                }
            }
    }

    private fun setLive(s: String) { liveText?.text = s }

    private fun finish(text: String) {
        stopAll()
        val t = text.trim()
        if (t.isNotEmpty()) onText(t.replaceFirstChar { it.titlecase(Locale.getDefault()) })
    }

    private fun cancel() {
        sysRecognizer?.cancel(); vosk?.cancel(); stopAll()
    }

    private fun stopAll() {
        sysRecognizer?.destroy(); sysRecognizer = null
        vosk?.shutdown(); vosk = null
        dialog?.dismiss(); dialog = null; liveText = null
    }

    fun release() {
        stopAll()
        io.execute { voskModel?.close(); voskModel = null }
        io.shutdown()
    }

    private fun toast(s: String) = android.widget.Toast.makeText(activity, s, android.widget.Toast.LENGTH_LONG).show()

    companion object {
        const val MODEL_NAME = "vosk-model-small-en-us-0.15"
        const val MODEL_URL = "https://alphacephei.com/vosk/models/$MODEL_NAME.zip"
        const val MODEL_SIZE_MB = 40
    }
}
