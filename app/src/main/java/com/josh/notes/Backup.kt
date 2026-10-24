package com.josh.notes

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.documentfile.provider.DocumentFile
import androidx.work.BackoffPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Backup & sync to a user-chosen folder via the Storage Access Framework
 * (local storage, or any document provider that supports folders: OneDrive, Dropbox, Nextcloud, …).
 * Archive: josh-notes-backup-YYYYMMDD-HHMMSS.zip = meta.json + notes.json + attachments/<noteId>/<file>.
 */
object Backup {
    const val PREFIX = "josh-notes-backup-"
    const val KEEP = 10
    private const val WORK_CHANGE = "backup-on-change"
    private const val WORK_DAILY = "backup-daily"

    data class Info(val uri: Uri, val name: String, val modified: Long, val size: Long)
    data class Parsed(val notes: List<Note>, val meta: JSONObject, val dir: File)

    private fun p(ctx: Context) = ctx.applicationContext.getSharedPreferences("backup", 0)

    fun folder(ctx: Context): Uri? = p(ctx).getString("tree", null)?.let(Uri::parse)
    fun autoEnabled(ctx: Context) = folder(ctx) != null && p(ctx).getBoolean("auto", true)
    fun setAuto(ctx: Context, on: Boolean) { p(ctx).edit().putBoolean("auto", on).apply(); schedule(ctx) }

    fun deviceId(ctx: Context): String = p(ctx).getString("device", null)
        ?: UUID.randomUUID().toString().also { p(ctx).edit().putString("device", it).apply() }

    fun setFolder(ctx: Context, tree: Uri) {
        val cr = ctx.contentResolver
        folder(ctx)?.let { old -> if (old != tree) runCatching { cr.releasePersistableUriPermission(old, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) } }
        cr.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        p(ctx).edit().putString("tree", tree.toString()).putBoolean("auto", true).apply()
        schedule(ctx)
    }

    fun clearFolder(ctx: Context) {
        folder(ctx)?.let { runCatching { ctx.contentResolver.releasePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) } }
        p(ctx).edit().remove("tree").apply()
        schedule(ctx)
    }

    /** Human label, e.g. "OneDrive › Notes". */
    fun folderLabel(ctx: Context): String? {
        val t = folder(ctx) ?: return null
        val provider = when {
            t.authority?.contains("externalstorage") == true -> "This phone"
            t.authority?.contains("skydrive") == true || t.authority?.contains("onedrive") == true -> "OneDrive"
            t.authority?.contains("dropbox") == true -> "Dropbox"
            t.authority?.contains("google.android.apps.docs") == true -> "Google Drive"
            t.authority?.contains("nextcloud") == true -> "Nextcloud"
            else -> t.authority ?: "Folder"
        }
        val name = runCatching { DocumentFile.fromTreeUri(ctx, t)?.name }.getOrNull()
        return if (name.isNullOrBlank()) provider else "$provider › $name"
    }

    // ------------------------------------------------------------------ status

    fun lastOk(ctx: Context) = p(ctx).getLong("last_ok", 0L)
    fun lastName(ctx: Context) = p(ctx).getString("last_name", null)
    fun lastError(ctx: Context) = p(ctx).getString("last_error", null)
    fun lastErrorAt(ctx: Context) = p(ctx).getLong("last_error_at", 0L)
    fun lastCount(ctx: Context) = p(ctx).getInt("last_count", 0)

    // ------------------------------------------------------------------ scheduling

    /** Folder and/or Google Drive automatic backups. */
    fun anyAuto(ctx: Context) = autoEnabled(ctx) || GoogleDrive.autoEnabled(ctx)

    fun schedule(ctx: Context) {
        val wm = WorkManager.getInstance(ctx.applicationContext)
        if (!anyAuto(ctx)) { wm.cancelUniqueWork(WORK_DAILY); wm.cancelUniqueWork(WORK_CHANGE); return }
        wm.enqueueUniquePeriodicWork(WORK_DAILY, ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<BackupWorker>(24, TimeUnit.HOURS)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES).build())
    }

    /** Debounced: a backup runs ~2 minutes after the last change. */
    fun onLocalChange(ctx: Context) {
        if (!anyAuto(ctx)) return
        runCatching {
            WorkManager.getInstance(ctx.applicationContext).enqueueUniqueWork(WORK_CHANGE, ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<BackupWorker>().setInitialDelay(2, TimeUnit.MINUTES)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES).build())
        }
    }

    // ------------------------------------------------------------------ write

    /** Build the archive locally (fast), then copy it to the provider (may be slow). Returns the file name. */
    @Synchronized
    fun backupNow(ctx: Context): String {
        val tree = folder(ctx) ?: throw IllegalStateException("No backup folder chosen")
        val dir = DocumentFile.fromTreeUri(ctx, tree) ?: throw IllegalStateException("Backup folder unavailable")
        if (!dir.canWrite()) throw IllegalStateException("No permission to write to the backup folder — choose it again in Settings")
        val name = PREFIX + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()) + ".zip"
        val tmp = File(ctx.cacheDir, name)
        try {
            val count = writeArchive(ctx, tmp)
            val doc = dir.createFile("application/zip", name) ?: throw IllegalStateException("Couldn't create a file in the backup folder (provider offline?)")
            ctx.contentResolver.openOutputStream(doc.uri, "w")?.use { out -> tmp.inputStream().use { it.copyTo(out) } }
                ?: throw IllegalStateException("Couldn't write to the backup folder")
            rotate(ctx)
            p(ctx).edit().putLong("last_ok", System.currentTimeMillis()).putString("last_name", doc.name ?: name)
                .putInt("last_count", count).remove("last_error").apply()
            return doc.name ?: name
        } catch (e: Exception) {
            p(ctx).edit().putString("last_error", e.message ?: e.javaClass.simpleName)
                .putLong("last_error_at", System.currentTimeMillis()).apply()
            throw e
        } finally { tmp.delete() }
    }

    /** Writes a full archive to [out]. Returns the note count. Also used for local safety copies. */
    fun writeArchive(ctx: Context, out: File): Int {
        val notes = NoteStore.all(ctx)
        ZipOutputStream(out.outputStream().buffered()).use { z ->
            val meta = JSONObject().put("format", 1).put("app", "com.josh.notes")
                .put("appVersion", BuildConfig.VERSION_NAME).put("createdAt", System.currentTimeMillis())
                .put("device", deviceId(ctx)).put("deviceModel", Build.MODEL)
                .put("noteCount", notes.size).put("maxUpdatedAt", notes.maxOfOrNull { maxOf(it.updatedAt, it.deletedAt) } ?: 0L)
            z.putNextEntry(ZipEntry("meta.json")); z.write(meta.toString(2).toByteArray()); z.closeEntry()
            z.putNextEntry(ZipEntry("notes.json")); z.write(NoteStore.serialize(notes).toByteArray()); z.closeEntry()
            for (n in notes) for (a in n.attachments) {
                val f = Attachments.file(ctx, n.id, a)
                if (!f.exists()) continue
                z.putNextEntry(ZipEntry("attachments/${n.id}/${a.file}"))
                f.inputStream().use { it.copyTo(z) }
                z.closeEntry()
            }
        }
        return notes.size
    }

    private fun rotate(ctx: Context) {
        val all = list(ctx)
        all.drop(KEEP).forEach { runCatching { DocumentFile.fromSingleUri(ctx, it.uri)?.delete() } }
    }

    // ------------------------------------------------------------------ read / restore

    fun list(ctx: Context): List<Info> {
        val dir = folder(ctx)?.let { DocumentFile.fromTreeUri(ctx, it) } ?: return emptyList()
        return dir.listFiles().filter { it.isFile && (it.name ?: "").startsWith(PREFIX) && (it.name ?: "").endsWith(".zip") }
            .map { Info(it.uri, it.name!!, it.lastModified(), it.length()) }
            .sortedByDescending { it.name } // timestamped names sort chronologically
    }

    fun readMeta(ctx: Context, uri: Uri): JSONObject? = runCatching {
        ZipInputStream(ctx.contentResolver.openInputStream(uri)!!.buffered()).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                if (e.name == "meta.json") return JSONObject(z.readBytes().toString(Charsets.UTF_8))
            }
            null
        }
    }.getOrNull()

    /** Extract to a temp dir (zip-slip safe). */
    fun read(ctx: Context, uri: Uri): Parsed {
        val dir = File(ctx.cacheDir, "restore-${System.currentTimeMillis()}").apply { mkdirs() }
        val root = dir.canonicalPath + File.separator
        var notesJson: String? = null; var meta = JSONObject()
        (ctx.contentResolver.openInputStream(uri) ?: throw IllegalStateException("Can't open backup")).use { input ->
            ZipInputStream(input.buffered()).use { z ->
                while (true) {
                    val e = z.nextEntry ?: break
                    when {
                        e.name == "notes.json" -> notesJson = z.readBytes().toString(Charsets.UTF_8)
                        e.name == "meta.json" -> meta = JSONObject(z.readBytes().toString(Charsets.UTF_8))
                        e.name.startsWith("attachments/") && !e.isDirectory -> {
                            val f = File(dir, e.name)
                            if (!f.canonicalPath.startsWith(root)) throw SecurityException("Bad entry in backup")
                            f.parentFile?.mkdirs(); f.outputStream().use { z.copyTo(it) }
                        }
                    }
                }
            }
        }
        val notes = NoteStore.parse(notesJson ?: throw IllegalStateException("Not a Josh Notes backup (no notes.json)"))
        return Parsed(notes, meta, dir)
    }

    private fun copyAttachmentsFor(ctx: Context, parsed: Parsed, ids: Collection<Long>) {
        for (id in ids) {
            val src = File(parsed.dir, "attachments/$id")
            if (!src.isDirectory) continue
            val dst = Attachments.dir(ctx, id).apply { mkdirs() }
            src.listFiles()?.forEach { f -> f.copyTo(File(dst, f.name), overwrite = true) }
        }
    }

    /** Merge: newer edit wins per note. Returns how many notes were taken from the backup. */
    fun restoreMerge(ctx: Context, uri: Uri): Int {
        val parsed = read(ctx, uri)
        try {
            val taken = NoteStore.mergeIncoming(ctx, parsed.notes)
            copyAttachmentsFor(ctx, parsed, taken)
            Attachments.cleanupOrphans(ctx, NoteStore.all(ctx))
            return taken.size
        } finally { parsed.dir.deleteRecursively() }
    }

    /** Replace everything. A local safety archive is written first (filesDir/safety, last 3 kept). */
    fun restoreReplace(ctx: Context, uri: Uri): Int {
        val parsed = read(ctx, uri)
        try {
            safetyCopy(ctx)
            NoteStore.replaceAll(ctx, parsed.notes)
            Attachments.root(ctx).deleteRecursively()
            copyAttachmentsFor(ctx, parsed, parsed.notes.map { it.id })
            return parsed.notes.size
        } finally { parsed.dir.deleteRecursively() }
    }

    fun safetyCopy(ctx: Context): File {
        val d = File(ctx.filesDir, "safety").apply { mkdirs() }
        val f = File(d, "pre-restore-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()) + ".zip")
        writeArchive(ctx, f)
        d.listFiles()?.sortedByDescending { it.name }?.drop(3)?.forEach { it.delete() }
        return f
    }

    /** "Sync": the newest backup in the folder came from another device/install and has newer edits. */
    fun newerBackup(ctx: Context): Pair<Info, JSONObject>? {
        val latest = list(ctx).firstOrNull() ?: return null
        if (latest.name == lastName(ctx) || latest.name == p(ctx).getString("dismissed", null)) return null
        val meta = readMeta(ctx, latest.uri) ?: return null
        if (meta.optString("device") == deviceId(ctx)) return null
        return if (meta.optLong("maxUpdatedAt") > NoteStore.maxUpdatedAt(ctx)) latest to meta else null
    }

    fun dismiss(ctx: Context, name: String) = p(ctx).edit().putString("dismissed", name).apply()
}

class BackupWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
    override fun doWork(): Result {
        val ctx = applicationContext
        var retry = false
        if (Backup.autoEnabled(ctx)) try { Backup.backupNow(ctx) } catch (_: Exception) { retry = true }
        if (GoogleDrive.autoEnabled(ctx)) try { GoogleDrive.backupNow(ctx) }
            catch (_: GoogleDrive.NeedsConsent) { /* needs the user; shown in Settings */ }
            catch (_: Exception) { retry = true }
        return if (!retry) Result.success() else if (runAttemptCount < 4) Result.retry() else Result.failure()
    }
}
