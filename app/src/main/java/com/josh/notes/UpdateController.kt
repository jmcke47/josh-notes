package com.josh.notes

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Self-updater.
 *
 * Fetches a manifest JSON like:
 *   { "versionCode": 2, "versionName": "1.0.1",
 *     "apkUrl": "https://.../notes-app.apk", "notes": "What's new" }
 * from [manifestUrl] (BuildConfig.UPDATE_MANIFEST_URL, overridable in-app),
 * compares versionCode with the installed one, downloads the APK into
 * cacheDir/updates/ and hands it to the system package installer via FileProvider.
 */
class UpdateController(private val activity: AppCompatActivity) {

    data class Manifest(val versionCode: Long, val versionName: String, val apkUrl: String, val notes: String)

    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val prefs = activity.getSharedPreferences("updater", 0)
    private var pendingApk: File? = null

    fun manifestUrl(): String =
        prefs.getString("manifest_url", null)?.takeIf { it.isNotBlank() } ?: BuildConfig.UPDATE_MANIFEST_URL

    private fun isPlaceholder(url: String) = url.isBlank() || url.contains("REPLACE-ME")

    private fun installedVersionCode(): Long {
        val pm = activity.packageManager
        val info = if (Build.VERSION.SDK_INT >= 33)
            pm.getPackageInfo(activity.packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
        else @Suppress("DEPRECATION") pm.getPackageInfo(activity.packageName, 0)
        return androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(info)
    }

    fun editUpdateUrl() {
        val input = EditText(activity).apply {
            setText(manifestUrl()); setSingleLine(); selectAll()
        }
        val box = FrameLayout(activity).apply {
            val p = (20 * resources.displayMetrics.density).toInt()
            setPadding(p, p / 2, p, 0); addView(input)
        }
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.update_url)
            .setMessage("URL of update.json. Leave empty to use the built-in default.")
            .setView(box)
            .setNeutralButton("Reset") { _, _ -> prefs.edit().remove("manifest_url").apply() }
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                prefs.edit().putString("manifest_url", input.text.toString().trim()).apply()
            }
            .show()
    }

    fun checkForUpdates() {
        val url = manifestUrl()
        if (isPlaceholder(url)) {
            MaterialAlertDialogBuilder(activity)
                .setTitle("Updates not configured")
                .setMessage("No update server has been set up yet.\n\nSet one via menu → ${activity.getString(R.string.update_url)}.")
                .setPositiveButton(android.R.string.ok, null)
                .setNeutralButton(R.string.update_url) { _, _ -> editUpdateUrl() }
                .show()
            return
        }
        val dialog = progressDialog("Checking for updates…", indeterminate = true)
        io.execute {
            val result = runCatching { fetchManifest(url) }
            main.post {
                if (activity.isFinishing) return@post
                dialog.dismiss()
                result.onFailure { showError("Couldn't check for updates", it) }
                result.onSuccess { m ->
                    val current = installedVersionCode()
                    if (m.versionCode <= current) {
                        Toast.makeText(activity, "You're up to date (v${BuildConfig.VERSION_NAME})", Toast.LENGTH_LONG).show()
                    } else {
                        MaterialAlertDialogBuilder(activity)
                            .setTitle("Update available: v${m.versionName}")
                            .setMessage("Installed: v${BuildConfig.VERSION_NAME} (build $current)\nNew: v${m.versionName} (build ${m.versionCode})" +
                                    if (m.notes.isNotBlank()) "\n\n${m.notes}" else "")
                            .setNegativeButton("Later", null)
                            .setPositiveButton("Update") { _, _ -> download(m) }
                            .show()
                    }
                }
            }
        }
    }

    private fun fetchManifest(url: String): Manifest {
        val conn = open(url)
        try {
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            val o = JSONObject(text)
            val apk = o.getString("apkUrl")
            // apkUrl may be relative to the manifest URL.
            val absApk = URL(URL(url), apk).toString()
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
            if (code in 300..399) { // cross-protocol/host redirects
                val loc = c.getHeaderField("Location") ?: throw IllegalStateException("Redirect without Location")
                c.disconnect(); current = URL(current, loc); return@repeat
            }
            if (code !in 200..299) { c.disconnect(); throw IllegalStateException("HTTP $code from ${current.host}") }
            return c
        }
        throw IllegalStateException("Too many redirects")
    }

    private fun download(m: Manifest) {
        val dir = File(activity.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val out = File(dir, "notes-${m.versionCode}.apk")
        val dialog = progressDialog("Downloading v${m.versionName}…", indeterminate = false)
        val bar = dialog.findViewById<LinearProgressIndicator>(PROGRESS_ID)
        io.execute {
            val result = runCatching {
                val conn = open(m.apkUrl)
                try {
                    val total = conn.contentLengthLong
                    conn.inputStream.use { input ->
                        out.outputStream().use { output ->
                            val buf = ByteArray(64 * 1024); var done = 0L; var lastPct = -1
                            while (true) {
                                val n = input.read(buf); if (n < 0) break
                                output.write(buf, 0, n); done += n
                                if (total > 0) {
                                    val pct = (done * 100 / total).toInt()
                                    if (pct != lastPct) { lastPct = pct; main.post { bar?.setProgressCompat(pct, true) } }
                                }
                            }
                        }
                    }
                } finally { conn.disconnect() }
                verifyApk(out, m)
                out
            }
            main.post {
                if (activity.isFinishing) return@post
                dialog.dismiss()
                result.onFailure { out.delete(); showError("Download failed", it) }
                result.onSuccess { install(it) }
            }
        }
    }

    /** Sanity-check the download is our package and actually newer. Signature is enforced by Android itself. */
    private fun verifyApk(file: File, m: Manifest) {
        val pm = activity.packageManager
        @Suppress("DEPRECATION")
        val info = pm.getPackageArchiveInfo(file.absolutePath, 0)
            ?: throw IllegalStateException("Downloaded file is not a valid APK")
        if (info.packageName != activity.packageName)
            throw IllegalStateException("APK is for a different app (${info.packageName})")
        if (androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(info) <= installedVersionCode())
            throw IllegalStateException("Downloaded APK (build ${androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(info)}) is not newer than installed")
    }

    private fun install(apk: File) {
        if (!activity.packageManager.canRequestPackageInstalls()) {
            pendingApk = apk
            MaterialAlertDialogBuilder(activity)
                .setTitle("Allow updates")
                .setMessage("To install updates, allow Josh Notes to install apps. You'll be sent to Settings — enable the toggle, then come back.")
                .setNegativeButton(android.R.string.cancel) { _, _ -> pendingApk = null }
                .setPositiveButton("Open Settings") { _, _ ->
                    activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${activity.packageName}")))
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
        try { activity.startActivity(intent) } catch (e: Exception) {
            val view = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            runCatching { activity.startActivity(view) }.onFailure { showError("Couldn't open installer", it) }
        }
    }

    /** Resume an install that was waiting on the "install unknown apps" permission. */
    fun onResume() {
        val apk = pendingApk ?: return
        if (activity.packageManager.canRequestPackageInstalls() && apk.exists()) install(apk)
    }

    private fun progressDialog(title: String, indeterminate: Boolean): AlertDialog {
        val bar = LinearProgressIndicator(activity).apply {
            id = PROGRESS_ID; isIndeterminate = indeterminate; max = 100
        }
        val box = FrameLayout(activity).apply {
            val p = (24 * resources.displayMetrics.density).toInt()
            setPadding(p, p / 2, p, p / 2); addView(bar)
        }
        return MaterialAlertDialogBuilder(activity).setTitle(title).setView(box).setCancelable(false).show()
    }

    private fun showError(title: String, t: Throwable) {
        MaterialAlertDialogBuilder(activity)
            .setTitle(title)
            .setMessage(t.message ?: t.javaClass.simpleName)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    companion object { private val PROGRESS_ID = android.view.View.generateViewId() }
}
