package com.josh.notes

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.provider.OpenableColumns
import android.util.LruCache
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.util.UUID

/** App-private attachment files: filesDir/attachments/<noteId>/<file>. */
object Attachments {
    private const val MAX_SIDE = 2048
    private const val JPEG_QUALITY = 85
    private const val RECOMPRESS_OVER_BYTES = 1_500_000L

    fun root(ctx: Context) = File(ctx.applicationContext.filesDir, "attachments")
    fun dir(ctx: Context, noteId: Long) = File(root(ctx), noteId.toString())
    fun file(ctx: Context, noteId: Long, a: Attachment) = File(dir(ctx, noteId), a.file)

    fun uri(ctx: Context, noteId: Long, a: Attachment): Uri =
        FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file(ctx, noteId, a))

    private fun newId() = UUID.randomUUID().toString().replace("-", "").take(16)

    private fun kindOf(mime: String) = when {
        mime.startsWith("image/") -> "image"
        mime.startsWith("audio/") -> "audio"
        else -> "file"
    }

    private fun safeExt(name: String, mime: String): String {
        val fromName = name.substringAfterLast('.', "").lowercase().filter { it.isLetterOrDigit() }.take(8)
        return fromName.ifEmpty { MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: "bin" }
    }

    /** Copy a picked/shared document into the note's private folder (photos are downscaled if large). */
    fun importUri(ctx: Context, noteId: Long, uri: Uri): Attachment {
        val cr = ctx.contentResolver
        var name = "attachment"; var size = -1L
        cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                c.getString(0)?.let { name = it }
                if (!c.isNull(1)) size = c.getLong(1)
            }
        }
        val mime = cr.getType(uri) ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "")) ?: "application/octet-stream"
        val tmp = File(ctx.cacheDir, "import-${newId()}")
        try {
            (cr.openInputStream(uri) ?: throw IllegalStateException("Can't read that file")).use { i -> tmp.outputStream().use { i.copyTo(it) } }
            return importFile(ctx, noteId, tmp, mime, name)
        } finally { tmp.delete() }
    }

    /** Move/copy a local file (camera capture, recording, import temp) into the note's folder. */
    fun importFile(ctx: Context, noteId: Long, src: File, mime: String, name: String,
                   durationMs: Long = 0L, transcript: String = ""): Attachment {
        val id = newId()
        val kind = kindOf(mime)
        val d = dir(ctx, noteId).apply { mkdirs() }
        if (kind == "image" && mime != "image/gif") {
            compressImage(src, File(d, "$id.jpg"))?.let { out ->
                return Attachment(id, out.name, name.substringBeforeLast('.') + ".jpg", "image/jpeg", out.length(), "image",
                    createdAt = System.currentTimeMillis())
            }
        }
        val out = File(d, "$id.${safeExt(name, mime)}")
        src.copyTo(out, overwrite = true)
        return Attachment(id, out.name, name, mime, out.length(), kind, durationMs, System.currentTimeMillis(), transcript)
    }

    /** Downscale to MAX_SIDE, honour EXIF rotation, re-encode JPEG. Returns null to keep the original as-is. */
    private fun compressImage(src: File, out: File): File? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(src.path, bounds)
        if (bounds.outWidth <= 0) return null
        val rotation = runCatching { ExifInterface(src.path).rotationDegrees }.getOrDefault(0)
        val big = maxOf(bounds.outWidth, bounds.outHeight)
        if (big <= MAX_SIDE && src.length() <= RECOMPRESS_OVER_BYTES && rotation == 0 &&
            bounds.outMimeType == "image/jpeg") { src.copyTo(out, overwrite = true); return out }
        var sample = 1
        while (big / (sample * 2) >= MAX_SIDE) sample *= 2
        var bmp = BitmapFactory.decodeFile(src.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val scale = MAX_SIDE.toFloat() / maxOf(bmp.width, bmp.height)
        val m = Matrix()
        if (scale < 1f) m.postScale(scale, scale)
        if (rotation != 0) m.postRotate(rotation.toFloat())
        if (!m.isIdentity) bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        out.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
        out
    }.getOrNull()

    fun delete(ctx: Context, noteId: Long, a: Attachment) {
        file(ctx, noteId, a).delete()
        thumbs.remove("$noteId/${a.file}")
    }

    fun deleteAll(ctx: Context, noteId: Long) { dir(ctx, noteId).deleteRecursively() }

    /** Remove files no note references (after restores / crashes). */
    fun cleanupOrphans(ctx: Context, notes: List<Note>) {
        // Never act on an empty/unreadable store, and leave anything recent alone (an editor may be mid-import).
        if (notes.isEmpty()) return
        val cutoff = System.currentTimeMillis() - 24 * 3600_000L
        val keep = notes.associate { it.id.toString() to it.attachments.map { a -> a.file }.toSet() }
        root(ctx).listFiles()?.forEach { d ->
            val wanted = keep[d.name]
            d.listFiles()?.forEach { f -> if ((wanted == null || f.name !in wanted) && f.lastModified() < cutoff) f.delete() }
            if (d.listFiles().isNullOrEmpty()) d.delete()
        }
    }

    private val thumbs = object : LruCache<String, Bitmap>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    /** Sampled thumbnail (call off the main thread). */
    fun thumbnail(ctx: Context, noteId: Long, a: Attachment, px: Int): Bitmap? {
        val key = "$noteId/${a.file}@$px"
        thumbs.get(key)?.let { return it }
        val f = file(ctx, noteId, a)
        val b = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, b)
        if (b.outWidth <= 0) return null
        var s = 1
        while (minOf(b.outWidth, b.outHeight) / (s * 2) >= px) s *= 2
        val bmp = BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = s }) ?: return null
        thumbs.put(key, bmp)
        return bmp
    }
}
