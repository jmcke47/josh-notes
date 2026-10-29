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

    /**
     * Effective manifest URL. A stored override (from 1.0.0's "Update source URL" or Settings > Advanced)
     * is sanitized; if it's unusable or just the default, it's discarded and the built-in default is used.
     */
    fun manifestUrl(): String {
        val default = UpdateHttp.sanitizeUrl(BuildConfig.UPDATE_MANIFEST_URL) ?: BuildConfig.UPDATE_MANIFEST_URL
        val raw = prefs.getString("manifest_url", null) ?: return default
        val clean = UpdateHttp.sanitizeUrl(raw)
        if (clean == null || clean == default || clean.contains("REPLACE-ME")) {
            prefs.edit { remove("manifest_url") }
            return default
        }
        if (clean != raw) prefs.edit { putString("manifest_url", clean) }
        return clean
    }

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
        val dialog: AlertDialog = osDialog(activity)
            .setView(view).setCancelable(false)
            .setNegativeButton(android.R.string.cancel) { _, _ -> cancelled = true }
            .show()

        fun busy(title: String, sub: String = "") {
            spinner.visibility = View.VISIBLE; icon.visibility = View.GONE
            status.text = title; detail.text = sub; detail.visibility = if (sub.isEmpty()) View.GONE else View.VISIBLE
        }

        fun fail(title: String, t: Throwable) {
            done(R.drawable.ic_error, title, friendly(t))
            notes.text = "Details: " + details(t); notes.visibility = View.VISIBLE
            notes.setTextIsSelectable(true)
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
                result.onFailure { ui.fail("Couldn't check for updates", it) }
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

    /**
     * Automatic check when the app opens or comes back from the background (Settings > "Check for updates on open").
     * Network at most once per [AUTO_INTERVAL_MS]; silent when offline, on any error, or when up to date.
     * Calls [onAvailable] on the main thread only when a newer version exists. Returns true if a check was started.
     */
    fun autoCheck(onAvailable: (Manifest) -> Unit): Boolean {
        if (!AppSettings.checkUpdatesOnOpen(activity)) return false
        val url = manifestUrl()
        if (isPlaceholder(url)) return false
        val now = clock()
        val last = prefs.getLong("last_auto_check", 0L)
        if (last in (now - AUTO_INTERVAL_MS + 1)..now) return false
        if (!online()) return false          // offline: stay quiet, try again next time
        prefs.edit { putLong("last_auto_check", now) }
        io.execute {
            val m = runCatching { fetcher(url) }.getOrNull() ?: return@execute   // any error: silent
            prefs.edit { putLong("known_latest", m.versionCode) }
            if (m.versionCode > installedVersionCode()) main.post { if (!activity.isFinishing && !activity.isDestroyed) onAvailable(m) }
        }
        return true
    }

    /** Test hooks (manifest fetcher and clock). */
    internal var fetcher: (String) -> Manifest = { fetchManifest(it) }
    internal var clock: () -> Long = { System.currentTimeMillis() }
    internal var online: () -> Boolean = { hasNetwork() }

    companion object { const val AUTO_INTERVAL_MS = 10 * 60_000L }

    /** Start downloading a known manifest (from the launch banner). */
    fun startUpdate(m: Manifest) { cancelled = false; download(m, StatusDialog()) }

    /** Advanced: change the manifest URL (Settings > Advanced). */
    fun editUpdateUrl() {
        val input = EditText(activity).apply { setText(manifestUrl()); setSingleLine(); selectAll() }
        val box = FrameLayout(activity).apply {
            val p = (20 * resources.displayMetrics.density).toInt(); setPadding(p, p / 2, p, 0); addView(input)
        }
        osDialog(activity)
            .setTitle(R.string.update_url)
            .setMessage("Advanced: URL of update.json.")
            .setView(box)
            .setNeutralButton("Reset to default") { _, _ -> prefs.edit { remove("manifest_url") } }
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val typed = input.text.toString()
                val clean = UpdateHttp.sanitizeUrl(typed)
                when {
                    typed.isBlank() -> prefs.edit { remove("manifest_url") }
                    clean == null -> android.widget.Toast.makeText(activity,
                        "That isn't a valid https:// address — kept the previous one.", android.widget.Toast.LENGTH_LONG).show()
                    else -> prefs.edit { putString("manifest_url", clean) }
                }
            }
            .show()
    }

    // ---------------------------------------------------------------- internals

    /** Short human message; only claims "offline" when Android says there's no usable network. */
    private fun friendly(t: Throwable): String {
        val online = hasNetwork()
        return when {
            t is UpdateHttp.HttpStatusException && t.code == 404 -> "No published update was found on the server."
            t is UpdateHttp.HttpStatusException -> "The update server returned an error (HTTP ${t.code})."
            !online && (t is java.net.UnknownHostException || t is java.net.ConnectException ||
                    t is java.net.SocketTimeoutException) -> "You appear to be offline. Check your connection and try again."
            t is java.net.UnknownHostException -> "Couldn't reach the update server (DNS lookup failed). If other apps work, check that Josh Notes is allowed to use mobile data / background data in Android settings, or Private DNS."
            t is java.net.SocketTimeoutException -> "The update server took too long to respond."
            t is javax.net.ssl.SSLException -> "Secure connection to the update server failed. Check the phone's date & time."
            t is java.net.ConnectException -> "Couldn't connect to the update server."
            else -> "Something went wrong while checking for updates."
        }
    }

    /** Technical one-liner for the "Details" line. */
    private fun details(t: Throwable): String {
        val root = generateSequence(t) { it.cause }.last()
        val chain = if (root !== t) " ← ${root.javaClass.simpleName}: ${root.message}" else ""
        return "${t.javaClass.simpleName}: ${t.message}$chain · network: ${networkSummary()} · app v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
    }

    private fun cm() = activity.getSystemService(android.net.ConnectivityManager::class.java)

    private fun hasNetwork(): Boolean = runCatching {
        val caps = cm().getNetworkCapabilities(cm().activeNetwork) ?: return false
        caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }.getOrDefault(true) // if we can't tell, don't claim offline

    private fun networkSummary(): String = runCatching {
        val c = cm()
        val caps = c.getNetworkCapabilities(c.activeNetwork) ?: return "none"
        val t = buildList {
            if (caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)) add("wifi")
            if (caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR)) add("cellular")
            if (caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN)) add("vpn")
            if (caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET)) add("ethernet")
        }.joinToString("+").ifEmpty { "other" }
        val validated = caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        val bg = when (c.restrictBackgroundStatus) {
            android.net.ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED -> ", data saver on"
            else -> ""
        }
        "$t${if (validated) "" else " (not validated)"}$bg"
    }.getOrElse { "unknown" }

    private fun fetchManifest(url: String): Manifest {
        UpdateHttp.userAgent = "JoshNotes/${BuildConfig.VERSION_NAME} (Android ${Build.VERSION.RELEASE})"
        val o = JSONObject(UpdateHttp.readText(url))
        val absApk = UpdateHttp.sanitizeUrl(URL(URL(url), o.getString("apkUrl").trim()).toString())
            ?: throw java.io.IOException("Manifest has an invalid apkUrl")
        return Manifest(o.getLong("versionCode"), o.optString("versionName", ""), absApk, o.optString("notes", ""))
    }

    private fun open(url: String): HttpURLConnection {
        UpdateHttp.userAgent = "JoshNotes/${BuildConfig.VERSION_NAME} (Android ${Build.VERSION.RELEASE})"
        return UpdateHttp.open(url)
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
                    if (cancelled) ui.dismiss() else ui.fail("Update failed", it)
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
            osDialog(activity)
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
            }.onFailure { StatusDialog().fail("Couldn't open installer", it) }
        }
    }

    /** Resume an install that was waiting on the "install unknown apps" permission. */
    fun onResume() {
        val apk = pendingApk ?: return
        if (activity.packageManager.canRequestPackageInstalls() && apk.exists()) install(apk)
    }
}
