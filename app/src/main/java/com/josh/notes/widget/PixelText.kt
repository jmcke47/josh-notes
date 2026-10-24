package com.josh.notes.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.util.TypedValue
import androidx.core.content.res.ResourcesCompat
import com.josh.notes.R

/**
 * Home-screen widgets are inflated by the launcher in a restricted context, which can't load an app's font files,
 * so Old School widget text is pre-rendered here with the bundled Pixelify Sans (SIL OFL) into bitmaps — with the
 * theme's hard 1px-style black drop shadow — and shown in ImageViews. Bitmaps are rendered at the device density so
 * they display 1:1.
 */
object PixelText {
    @Volatile private var font: Typeface? = null
    private fun font(ctx: Context): Typeface =
        font ?: (runCatching { ResourcesCompat.getFont(ctx, R.font.pixelify_sans) }.getOrNull() ?: Typeface.MONOSPACE).also { font = it }

    /**
     * @param maxWidthPx text is ellipsized/wrapped to this width.
     * @param maxLines 1 = single ellipsized line.
     */
    fun render(ctx: Context, text: CharSequence, sizeSp: Float, color: Int, maxWidthPx: Int, maxLines: Int = 1,
               shadow: Boolean = true): Bitmap? {
        if (text.isBlank()) return null
        val dm = ctx.resources.displayMetrics
        val paint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
            typeface = font(ctx); textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sizeSp, dm); this.color = color
        }
        val shadowPx = if (shadow) (1.5f * dm.density).coerceAtLeast(1f) else 0f
        val width = (maxWidthPx - shadowPx).toInt().coerceAtLeast(1)
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(false)
            .setMaxLines(maxLines).setEllipsize(TextUtils.TruncateAt.END).build()
        var w = 0f
        for (i in 0 until layout.lineCount) w = maxOf(w, layout.getLineWidth(i))
        val bw = (w + shadowPx).toInt().coerceIn(1, maxWidthPx.coerceAtLeast(1))
        val bh = (layout.height + shadowPx).toInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        bmp.density = dm.densityDpi
        val c = Canvas(bmp)
        if (shadow) {
            val oldColor = paint.color
            paint.color = Color.BLACK
            c.save(); c.translate(shadowPx, shadowPx); layout.draw(c); c.restore()
            paint.color = oldColor
        }
        layout.draw(c)
        return bmp
    }
}
