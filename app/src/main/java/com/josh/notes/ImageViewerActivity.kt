package com.josh.notes

import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import java.io.File
import java.util.concurrent.Executors

/** Full-screen image viewer (tap to close). */
class ImageViewerActivity : LockableActivity() {
    override val screenFrame get() = false
    companion object { const val EXTRA_PATH = "path"; const val EXTRA_NAME = "name" }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val iv = ImageView(this).apply { scaleType = ImageView.ScaleType.FIT_CENTER; setBackgroundColor(0xFF000000.toInt())
            contentDescription = intent.getStringExtra(EXTRA_NAME); setOnClickListener { finish() } }
        setContentView(FrameLayout(this).apply { setBackgroundColor(0xFF000000.toInt()); addView(iv, ViewGroup.LayoutParams(-1, -1)) })
        val path = intent.getStringExtra(EXTRA_PATH) ?: return finish()
        // Only allow files inside our own attachments folder.
        val f = File(path)
        if (!f.canonicalPath.startsWith(Attachments.root(this).canonicalPath)) return finish()
        val dm = resources.displayMetrics
        Executors.newSingleThreadExecutor().execute {
            val b = BitmapFactory.Options().apply { inJustDecodeBounds = true }; BitmapFactory.decodeFile(f.path, b)
            var s = 1; while (b.outWidth / (s * 2) >= dm.widthPixels && b.outHeight / (s * 2) >= dm.heightPixels) s *= 2
            val bmp = BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = s })
            runOnUiThread { if (bmp != null) iv.setImageBitmap(bmp) }
        }
    }
}
