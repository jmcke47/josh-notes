package com.josh.notes

import android.os.Build
import android.os.Bundle
import android.speech.SpeechRecognizer
import android.view.View
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.color.DynamicColors
import com.google.android.material.materialswitch.MaterialSwitch
import java.io.File

class SettingsActivity : LockableActivity() {
    private var switchingTheme = false
    private val io = java.util.concurrent.Executors.newSingleThreadExecutor()
    private val pickFolder = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@registerForActivityResult
        runCatching { Backup.setFolder(this, uri) }.onFailure { msg("Couldn't use that folder: ${it.message}") }
        refreshBackup()
        if (Backup.folder(this) != null) backupNow()
    }

    override fun onResume() { super.onResume(); if (::statusView.isInitialized) { refreshBackup(); refreshDrive() } }

    // ---------------------------------------------------------------- Google Drive
    private val driveConsent = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult()) { res ->
        GoogleDrive.handleConsentResult(this, res.data)
            .onSuccess { refreshDrive(); driveBackupNow() }
            .onFailure { showDriveError(it) }
    }

    private fun showDriveError(e: Throwable) {
        osDialog(this).setTitle("Google Drive")
            .setMessage(GoogleDrive.explain(e)).setPositiveButton(android.R.string.ok, null).show()
        refreshDrive()
    }

    private fun refreshDrive() {
        val on = GoogleDrive.enabled(this)
        val status = findViewById<android.widget.TextView>(R.id.gdrive_status)
        val parts = mutableListOf<String>()
        parts += if (on) "Connected" + (GoogleDrive.account(this)?.let { " as $it" } ?: "") else "Not connected"
        val ok = GoogleDrive.lastOk(this); val err = GoogleDrive.lastError(this)
        if (ok > 0) parts += "Last Drive backup: " + ReminderFormat.short(this, ok) + " (${GoogleDrive.lastCount(this)} notes)"
        if (err != null && GoogleDrive.lastErrorAt(this) > ok) parts += "Last attempt failed " + ReminderFormat.short(this, GoogleDrive.lastErrorAt(this)) + ": $err"
        status.text = parts.joinToString("\n")
        findViewById<View>(R.id.gdrive_connect).visibility = if (on) View.GONE else View.VISIBLE
        listOf(R.id.gdrive_now, R.id.gdrive_restore, R.id.gdrive_disconnect, R.id.gdrive_auto).forEach {
            findViewById<View>(it).visibility = if (on) View.VISIBLE else View.GONE
        }
        findViewById<MaterialSwitch>(R.id.gdrive_auto).apply {
            setOnCheckedChangeListener(null); isChecked = GoogleDrive.autoEnabled(this@SettingsActivity)
            setOnCheckedChangeListener { _, v -> GoogleDrive.setAuto(this@SettingsActivity, v) }
        }
    }

    private fun connectDrive() {
        GoogleDrive.connect(this,
            launchConsent = { sender -> driveConsent.launch(androidx.activity.result.IntentSenderRequest.Builder(sender).build()) },
            done = { r -> r.onSuccess { refreshDrive(); driveBackupNow() }.onFailure { showDriveError(it) } })
    }

    private fun driveBackupNow() {
        val d = progress("Backing up to Google Drive…", "Uploading. Large photo/memo backups can take a while on mobile data.")
        io.execute {
            val r = runCatching { GoogleDrive.backupNow(this) }
            runOnUiThread { d.dismiss(); refreshDrive()
                r.onSuccess { msg("Saved to Google Drive: $it") }.onFailure {
                    if (it is GoogleDrive.NeedsConsent) connectDrive() else msg("Drive backup failed: ${it.message}") } }
        }
    }

    private fun driveRestore() {
        val d = progress("Looking for Drive backups…", "Contacting Google Drive…")
        io.execute {
            val r = runCatching { GoogleDrive.list(this) }
            runOnUiThread {
                d.dismiss()
                r.onFailure { if (it is GoogleDrive.NeedsConsent) connectDrive() else msg("Couldn't list Drive backups: ${it.message}") }
                r.onSuccess { list ->
                    if (list.isEmpty()) { msg("No backups in Google Drive yet"); return@onSuccess }
                    val labels = list.map { it.name.removePrefix(Backup.PREFIX).removeSuffix(".zip") + "  ·  " +
                            android.text.format.Formatter.formatShortFileSize(this, it.size) }.toTypedArray()
                    osDialog(this).setTitle("Restore which Drive backup?")
                        .setItems(labels) { _, w -> driveRestoreMode(list[w]) }.setNegativeButton(android.R.string.cancel, null).show()
                }
            }
        }
    }

    private fun driveRestoreMode(item: GoogleDrive.Item) {
        fun run(replace: Boolean) {
            val d = progress("Restoring from Google Drive…", "Downloading ${item.name}")
            io.execute {
                val r = runCatching { if (replace) GoogleDrive.restoreReplace(this, item) else GoogleDrive.restoreMerge(this, item) }
                runOnUiThread { d.dismiss()
                    r.onSuccess { msg(if (replace) "Restored $it notes" else "Merged: $it notes updated or added") }
                        .onFailure { msg("Restore failed (your notes were not changed): ${it.message}") } }
            }
        }
        osDialog(this).setTitle(item.name)
            .setMessage("Merge keeps all your current notes and takes the newer version of any note that's in both.\n\n" +
                    "Replace makes the app exactly match the backup (a safety copy of your current notes is saved first).")
            .setNegativeButton(android.R.string.cancel, null)
            .setNeutralButton("Replace…") { _, _ ->
                osDialog(this).setTitle("Replace all notes?")
                    .setMessage("Notes not in this backup will be removed from the app. A safety copy is kept on this phone.")
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton("Replace") { _, _ -> run(true) }.show()
            }
            .setPositiveButton("Merge") { _, _ -> run(false) }.show()
    }

    private lateinit var statusView: android.widget.TextView
    private lateinit var folderView: android.widget.TextView
    private lateinit var autoSwitch: MaterialSwitch

    private fun msg(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()

    private fun refreshBackup() {
        val label = Backup.folderLabel(this)
        folderView.text = if (label == null) "No backup folder chosen" else "Folder: $label"
        autoSwitch.isEnabled = label != null
        autoSwitch.setOnCheckedChangeListener(null)
        autoSwitch.isChecked = Backup.autoEnabled(this)
        autoSwitch.setOnCheckedChangeListener { _, on -> Backup.setAuto(this, on) }
        findViewById<View>(R.id.backup_now).isEnabled = label != null
        findViewById<View>(R.id.backup_restore).isEnabled = label != null
        val ok = Backup.lastOk(this); val err = Backup.lastError(this); val errAt = Backup.lastErrorAt(this)
        val parts = mutableListOf<String>()
        if (ok > 0) parts += "Last backup: " + ReminderFormat.short(this, ok) + " (${Backup.lastCount(this)} notes)"
        else if (label != null) parts += "No backup yet"
        if (err != null && errAt > ok) parts += "Last attempt failed " + ReminderFormat.short(this, errAt) + ": $err. " +
                "The storage app may be offline or slow — it will retry automatically."
        statusView.text = parts.joinToString("\n")
    }

    private fun progress(title: String, message: String): androidx.appcompat.app.AlertDialog {
        val pad = (24 * resources.displayMetrics.density).toInt()
        return osDialog(this).setTitle(title).setMessage(message)
            .setView(android.widget.FrameLayout(this).apply { setPadding(pad, 0, pad, 0)
                addView(com.google.android.material.progressindicator.LinearProgressIndicator(this@SettingsActivity).apply { isIndeterminate = true }) })
            .setCancelable(false).show()
    }

    private fun backupNow() {
        val d = progress("Backing up…", "Writing to ${Backup.folderLabel(this)}. Cloud folders can take a while on a slow connection.")
        io.execute {
            val r = runCatching { Backup.backupNow(this) }
            runOnUiThread { d.dismiss(); refreshBackup()
                r.onSuccess { msg("Backup saved: $it") }.onFailure { msg("Backup failed: ${it.message}") } }
        }
    }

    private fun restore() {
        val d = progress("Looking for backups…", "Reading ${Backup.folderLabel(this)}…")
        io.execute {
            val r = runCatching { Backup.list(this) }
            runOnUiThread {
                d.dismiss()
                r.onFailure { msg("Couldn't read the backup folder: ${it.message}") }
                r.onSuccess { list ->
                    if (list.isEmpty()) { msg("No Josh Notes backups in that folder yet"); return@onSuccess }
                    val labels = list.map { it.name.removePrefix(Backup.PREFIX).removeSuffix(".zip") + "  ·  " +
                            android.text.format.Formatter.formatShortFileSize(this, it.size) }.toTypedArray()
                    osDialog(this).setTitle("Restore which backup?")
                        .setItems(labels) { _, w -> restoreMode(list[w]) }.setNegativeButton(android.R.string.cancel, null).show()
                }
            }
        }
    }

    private fun restoreMode(info: Backup.Info) {
        osDialog(this).setTitle(info.name)
            .setMessage("Merge keeps all your current notes and takes the newer version of any note that's in both.\n\n" +
                    "Replace makes the app exactly match the backup (a safety copy of your current notes is saved first).")
            .setNegativeButton(android.R.string.cancel, null)
            .setNeutralButton("Replace…") { _, _ ->
                osDialog(this).setTitle("Replace all notes?")
                    .setMessage("Notes not in this backup will be removed from the app. A safety copy is kept on this phone.")
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton("Replace") { _, _ -> runRestore(info, replace = true) }.show()
            }
            .setPositiveButton("Merge") { _, _ -> runRestore(info, replace = false) }.show()
    }

    private fun runRestore(info: Backup.Info, replace: Boolean) {
        val d = progress("Restoring…", "Reading ${info.name}")
        io.execute {
            val r = runCatching { if (replace) Backup.restoreReplace(this, info.uri) else Backup.restoreMerge(this, info.uri) }
            runOnUiThread { d.dismiss()
                r.onSuccess { msg(if (replace) "Restored $it notes" else "Merged: $it notes updated or added") }
                    .onFailure { msg("Restore failed (your notes were not changed): ${it.message}") } }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        if (AppSettings.oldSchool(this)) findViewById<View>(R.id.settings_content).apply {
            val p = paddingLeft
            setBackgroundResource(R.drawable.os_panel); setPadding(p, p, p, p)
            (layoutParams as? android.view.ViewGroup.MarginLayoutParams)?.let { val m = (8 * resources.displayMetrics.density).toInt(); it.setMargins(m, m, m, m) }
        }
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root)) { v, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.updatePadding(left = b.left, top = b.top, right = b.right, bottom = b.bottom)
            WindowInsetsCompat.CONSUMED
        }
        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }

        // App lock
        findViewById<MaterialSwitch>(R.id.app_lock).apply {
            isChecked = AppLock.enabled(this@SettingsActivity)
            if (!AppLock.available(this@SettingsActivity) && !isChecked) {
                isEnabled = false
                findViewById<android.widget.TextView>(R.id.app_lock_desc).text =
                    "Set up a screen lock (PIN, pattern or fingerprint) on this phone to use App lock."
            }
            setOnCheckedChangeListener { sw, on ->
                // Confirm identity both to turn on and to turn off.
                AppLock.prompt(this@SettingsActivity, if (on) "Turn on App lock" else "Turn off App lock",
                    onSuccess = { AppLock.setEnabled(this@SettingsActivity, on) },
                    onFail = { sw.setOnCheckedChangeListener(null); sw.isChecked = !on; recreate() })
            }
        }

        // Theme
        val themeGroup = findViewById<RadioGroup>(R.id.theme_group)
        val theme = AppSettings.theme(this)
        ThemeMode.entries.forEach { t ->
            themeGroup.addView(RadioButton(this).apply {
                id = View.generateViewId(); text = t.label; isChecked = t == theme
                isSaveEnabled = false // state always comes from prefs; restoring must not re-trigger
                setOnClickListener {
                    if (switchingTheme || AppSettings.theme(this@SettingsActivity) == t) return@setOnClickListener
                    switchingTheme = true
                    AppSettings.switchTheme(this@SettingsActivity, t)
                }
            })
        }
        findViewById<MaterialSwitch>(R.id.dynamic_color).apply {
            if (!DynamicColors.isDynamicColorAvailable()) { isEnabled = false; text = "$text — not supported on this phone" }
            if (AppSettings.oldSchool(this@SettingsActivity)) { isEnabled = false; text = "Material You colours (not used by Old School)" }
            isSaveEnabled = false
            isChecked = AppSettings.dynamicColor(this@SettingsActivity)
            setOnCheckedChangeListener { _, on ->
                if (switchingTheme || on == AppSettings.dynamicColor(this@SettingsActivity)) return@setOnCheckedChangeListener
                switchingTheme = true
                AppSettings.setDynamicColor(this@SettingsActivity, on)
                AppSettings.recreateFaded(this@SettingsActivity)
            }
        }

        findViewById<MaterialSwitch>(R.id.level_ups).apply {
            isSaveEnabled = false
            isChecked = AppSettings.levelUps(this@SettingsActivity)
            setOnCheckedChangeListener { _, on -> AppSettings.setLevelUps(this@SettingsActivity, on) }
        }
        findViewById<View>(R.id.font_credit).setOnClickListener {
            val text = runCatching { assets.open("licenses/PixelifySans-OFL.txt").bufferedReader().readText() }.getOrDefault("")
            osDialog(this).setTitle("Pixelify Sans — SIL OFL 1.1")
                .setMessage(text).setPositiveButton(android.R.string.ok, null).show()
        }

        // Backup
        statusView = findViewById(R.id.backup_status); folderView = findViewById(R.id.backup_folder); autoSwitch = findViewById(R.id.backup_auto)
        findViewById<View>(R.id.backup_choose).setOnClickListener { pickFolder.launch(Backup.folder(this)) }
        findViewById<View>(R.id.backup_now).setOnClickListener { backupNow() }
        findViewById<View>(R.id.backup_restore).setOnClickListener { restore() }
        refreshBackup()

        // Google Drive
        findViewById<View>(R.id.gdrive_connect).setOnClickListener { connectDrive() }
        findViewById<View>(R.id.gdrive_now).setOnClickListener { driveBackupNow() }
        findViewById<View>(R.id.gdrive_restore).setOnClickListener { driveRestore() }
        findViewById<View>(R.id.gdrive_disconnect).setOnClickListener {
            osDialog(this).setTitle("Disconnect Google Drive?")
                .setMessage("Automatic Drive backups stop. Existing backups stay in your Drive's app folder.")
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton("Disconnect") { _, _ -> GoogleDrive.disconnect(this); refreshDrive() }.show()
        }
        refreshDrive()

        // Voice action
        val voice = findViewById<RadioGroup>(R.id.voice_group)
        val va = VoiceAction.get(this)
        VoiceAction.entries.forEach { v ->
            voice.addView(RadioButton(this).apply { id = View.generateViewId(); text = v.label; isChecked = v == va
                setOnClickListener { VoiceAction.set(this@SettingsActivity, v) } })
        }

        // Volume trigger
        val triggers = findViewById<RadioGroup>(R.id.trigger_group)
        val current = DictationTrigger.get(this)
        DictationTrigger.entries.forEach { t ->
            triggers.addView(RadioButton(this).apply {
                id = View.generateViewId(); text = t.label; isChecked = t == current
                setOnClickListener { DictationTrigger.set(this@SettingsActivity, t) }
            })
        }

        // Advanced (hidden-ish): update source URL
        val updater = UpdateController(this)
        findViewById<View>(R.id.advanced_toggle).setOnClickListener {
            val box = findViewById<View>(R.id.advanced_box)
            box.visibility = if (box.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
        findViewById<View>(R.id.update_url_btn).setOnClickListener { updater.editUpdateUrl() }
        findViewById<android.widget.TextView>(R.id.version_text).text =
            "Josh Notes v${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})" +
                (com.josh.notes.widget.NotesWidgetProvider.status(this)?.let { "\n$it" } ?: "")

        // Engine
        val dictPrefs = getSharedPreferences("dictation", 0)
        val engine = dictPrefs.getString("engine", null) ?: "AUTO"
        val onDevice = Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(this)
        val modelDir = File(filesDir, DictationController.MODEL_NAME)
        val engines = listOf(
            "AUTO" to "Automatic (Android on-device recognizer if available, else Vosk)",
            "SYSTEM" to "Android on-device recognizer" + if (onDevice) " (available)" else " (not on this phone; offline-preferred)",
            "VOSK" to "Vosk, fully offline (one-time ${DictationController.MODEL_SIZE_MB} MB download)",
        )
        val group = findViewById<RadioGroup>(R.id.engine_group)
        engines.forEach { (key, label) ->
            group.addView(RadioButton(this).apply {
                id = View.generateViewId(); text = label; isChecked = key == engine
                setOnClickListener { dictPrefs.edit { putString("engine", key) } }
            })
        }
        findViewById<View>(R.id.delete_model).apply {
            isEnabled = modelDir.exists()
            setOnClickListener {
                modelDir.deleteRecursively(); isEnabled = false
                Toast.makeText(this@SettingsActivity, "Offline voice model deleted", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
