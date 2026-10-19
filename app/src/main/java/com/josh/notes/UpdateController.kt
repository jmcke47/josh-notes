package com.josh.notes

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.content.edit
import androidx.core.content.pm.PackageInfoCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.progressindicator.LinearProgressIndicator
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Self-updater. Manifest JSON:
 *   { "versionCode": 2, "versionName": "1.1.0", "apkUrl": "https://.../notes-app.apk", "notes": "..." }
 * Flow (single status dialog): Checking… -> Up to date | Update found vX — updating… (progress) -> system installer.
 */
class UpdateController(private val activity: AppCompatActivity) {

    data class Manifest(val versionCode: Long, val versionName: String, val apkUrl: String, val notes: String)

    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val prefs = activity.getSharedPreferences("updater", 0)
    private var pendingApk: File? = null
    @Volatile private var cancelled = false

    fun manifestUrl(): String =
        prefs.getString("manifest_url", null)?.takeIf { it.isNotBlank() } ?: BuildConfig.UPDATE_MANIFEST_URL

    private fun isPlaceholder(url: String) = url.isBlank() || url.contains("REPLACE-ME")

    private fun installedVersionCode(): Long {
        val pm = activity.packageManager
        val info = if (Build.VERSION.SDK_INT >= 33)
            pm.getPackageInfo(activity.packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
        else @Suppress("DEPRECATION") pm.getPackageInfo(activity.packageName, 0)
        return PackageInfoCompat.getLongVersionCode(info)
    }

    // ---------------------------------------------------------------- status dialog

    private inner class StatusDialog {
        val view: View = LayoutInflater.from(activity).inflate(R.layout.dialog_update, null)
        val spinner: CircularProgressIndicator = view.findViewById(R.id.spinner)
        val icon: ImageView = view.findViewById(R.id.status_icon)
        val status: TextView = view.findViewById(R.id.status)
        val detail: TextView = view.findViewById(R.id.detail)
        val progress: LinearProgressIndicator = view.findViewById(R.id.progress)
        val notes: TextView = view.findViewById(R.id.notes)
        val dialog: AlertDialog = MaterialAlertDialogBuilder(activity)
            .setView(view).setCancelable(false)
            .setNegativeButton(android.R.string.cancel) { _, _ -> cancelled = true }
            .show()

        fun busy(title: String, sub: String = "") {
            spinner.visibility = View.VISIBLE; icon.visibility = View.GONE
            status.text = title; detail.text = sub; detail.visibility = if (sub.isEmpty()) View.GONE else View.VISIBLE
        }

        fun done(iconRes: Int, title: String, sub: String) {
            spinner.visibility = View.GONE; progress.visibility = View.GONE
            icon.setImageResource(iconRes); icon.visibility = View.VISIBLE
            status.text = title; detail.text = sub; detail.visibility = View.VISIBLE
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).text = activity.getString(android.R.string.ok)
            dialog.setCancelable(true)
        }

        fun dismiss() { if (dialog.isShowing) dialog.dismiss() }
    }

    // ---------------------------------------------------------------- public API

    /** Manual "Check for updates": checks, and if newer, downloads + installs straight away. */
    fun checkForUpdates() {
        val url = manifestUrl()
        cancelled = false
        val ui = StatusDialog()
        if (isPlaceholder(url)) { ui.done(R.drawable.ic_error, "Updates not set up", "No update server is configured."); return }
        ui.busy("Checking for updates…")
        io.execute {
            val result = runCatching { fetchManifest(url) }
            main.post {
                if (activity.isFinishing || cancelled) { ui.dismiss(); return@post }
                result.onFailure { ui.done(R.drawable.ic_error, "Couldn't check for updates", friendly(it)) }
                result.onSuccess { m ->
                    if (m.versionCode <= installedVersionCode()) {
                        ui.done(R.drawable.ic_check_circle, "You're up to date", "Josh Notes v${BuildConfig.VERSION_NAME}")
                    } else download(m, ui)
                }
            }
        }
    }

    /** Quiet check on launch (at most every 6 h). Calls [onAvailable] on the main thread if newer. */
    fun quietCheck(onAvailable: (Manifest) -> Unit) {
        val url = manifestUrl()
        if (isPlaceholder(url)) return
        val now = System.currentTimeMillis()
        if (now - prefs.getLong("last_quiet_check", 0L) < 6 * 3600_000L) {
            // Still surface a known pending update without hitting the network.
            val known = prefs.getLong("known_latest", 0L)
            if (known <= installedVersionCode()) return
        }
        io.execute {
            val m = runCatching { fetchManifest(url) }.getOrNull() ?: return@execute
            prefs.edit { putLong("last_quiet_check", now); putLong("known_latest", m.versionCode) }
            if (m.versionCode > installedVersionCode()) main.post { if (!activity.isFinishing) onAvailable(m) }
        }
    }

    /** Start downloading a known manifest (from the launch banner). */
    fun startUpdate(m: Manifest) { cancelled = false; download(m, StatusDialog()) }

    /** Advanced: change the manifest URL (Settings > Advanced). */
    fun editUpdateUrl() {
        val input = EditText(activity).apply { setText(manifestUrl()); setSingleLine(); selectAll() }
        val box = FrameLayout(activity).apply {
            val p = (20 * resources.displayMetrics.density).toInt(); setPadding(p, p / 2, p, 0); addView(input)
        }
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.update_url)
            .setMessage("Advanced: URL of update.json.")
            .setView(box)
            .setNeutralButton("Reset to default") { _, _ -> prefs.edit { remove("manifest_url") } }
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ -> prefs.edit { putString("manifest_url", input.text.toString().trim()) } }
            .show()
    }

    // ---------------------------------------------------------------- internals

    private fun friendly(t: Throwable): String = when (t) {
        is java.net.UnknownHostException, is java.net.ConnectException -> "No internet connection. Try again later."
        is java.net.SocketTimeoutException -> "The server took too long to respond."
        else -> t.message ?: t.javaClass.simpleName
    }

    private fun fetchManifest(url: String): Manifest {
        val conn = open(url)
        try {
            val o = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val absApk = URL(URL(url), o.getString("apkUrl")).toString()
            return Manifest(o.getLong("versionCode"), o.optString("versionName", ""), absApk, o.optString("notes", ""))
        } finally { conn.disconnect() }
    }

    private fun open(url: String): HttpURLConnection {
        var current = URL(url)
        repeat(6) {
            val c = current.openConnection() as HttpURLConnection
            c.connectTimeout = 15000; c.readTimeout = 30000
            c.instanceFollowRedirects = true
            c.setRequestProperty("Cache-Control", "no-cache")
            c.setRequestProperty("User-Agent", "JoshNotes/${BuildConfig.VERSION_NAME}")
            val code = c.responseCode
            if (code in 300..399) {
                val loc = c.getHeaderField("Location") ?: throw IllegalStateException("Redirect without Location")
                c.disconnect(); current = URL(current, loc); return@repeat
            }
            if (code !in 200..299) { c.disconnect(); throw IllegalStateException("Server answered HTTP $code") }
            return c
        }
        throw IllegalStateException("Too many redirects")
    }

    private fun download(m: Manifest, ui: StatusDialog) {
        ui.busy("Update found: v${m.versionName}", "Updating…")
        if (m.notes.isNotBlank()) { ui.notes.text = m.notes; ui.notes.visibility = View.VISIBLE }
        ui.progress.visibility = View.VISIBLE; ui.progress.isIndeterminate = true
        val dir = File(activity.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val out = File(dir, "notes-${m.versionCode}.apk")
        io.execute {
            val result = runCatching {
                val conn = open(m.apkUrl)
                try {
                    val total = conn.contentLengthLong
                    conn.inputStream.use { input ->
                        out.outputStream().use { output ->
                            val buf = ByteArray(64 * 1024); var done = 0L; var lastPct = -1
                            while (true) {
                                if (cancelled) throw InterruptedException("Cancelled")
                                val n = input.read(buf); if (n < 0) break
                                output.write(buf, 0, n); done += n
                                if (total > 0) {
                                    val pct = (done * 100 / total).toInt()
                                    if (pct != lastPct) {
                                        lastPct = pct
                                        val mb = "%.1f / %.1f MB".format(done / 1e6, total / 1e6)
                                        main.post {
                                            ui.progress.isIndeterminate = false
                                            ui.progress.setProgressCompat(pct, true)
                                            ui.detail.text = "Downloading… $pct%  ($mb)"
                                        }
                                    }
                                }
                            }
                        }
                    }
                } finally { conn.disconnect() }
                verifyApk(out)
                out
            }
            main.post {
                if (activity.isFinishing) return@post
                result.onFailure {
                    out.delete()
                    if (cancelled) ui.dismiss() else ui.done(R.drawable.ic_error, "Update failed", friendly(it))
                }
                result.onSuccess {
                    ui.progress.setProgressCompat(100, true)
                    ui.detail.text = "Opening installer…"
                    ui.dismiss()
                    install(it)
                }
            }
        }
    }

    private fun verifyApk(file: File) {
        @Suppress("DEPRECATION")
        val info = activity.packageManager.getPackageArchiveInfo(file.absolutePath, 0)
            ?: throw IllegalStateException("Downloaded file is not a valid APK")
        if (info.packageName != activity.packageName) throw IllegalStateException("APK is for a different app")
        if (PackageInfoCompat.getLongVersionCode(info) <= installedVersionCode())
            throw IllegalStateException("Downloaded APK is not newer than the installed version")
    }

    private fun install(apk: File) {
        if (!activity.packageManager.canRequestPackageInstalls()) {
            pendingApk = apk
            MaterialAlertDialogBuilder(activity)
                .setTitle("One-time permission")
                .setMessage("Allow Josh Notes to install its own updates. Turn on the switch on the next screen, then come back — the update will continue.")
                .setNegativeButton(android.R.string.cancel) { _, _ -> pendingApk = null }
                .setPositiveButton("Continue") { _, _ ->
                    activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${activity.packageName}")))
                }
                .show()
            return
        }
        pendingApk = null
        val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", apk)
        @Suppress("DEPRECATION")
        val intent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
            putExtra(Intent.EXTRA_INSTALLER_PACKAGE_NAME, activity.packageName)
        }
        try { activity.startActivity(intent) } catch (_: Exception) {
            runCatching {
                activity.startActivity(Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                })
            }.onFailure { StatusDialog().done(R.drawable.ic_error, "Couldn't open installer", friendly(it)) }
        }
    }

    /** Resume an install that was waiting on the "install unknown apps" permission. */
    fun onResume() {
        val apk = pendingApk ?: return
        if (activity.packageManager.canRequestPackageInstalls() && apk.exists()) install(apk)
    }
}
