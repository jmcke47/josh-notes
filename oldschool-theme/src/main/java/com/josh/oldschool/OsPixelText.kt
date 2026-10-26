package com.josh.oldschool

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.util.TypedValue

/**
 * Pixel-text-to-bitmap helper for RemoteViews (home-screen widgets).
 * Launchers inflate home-screen widgets in a context that can't load an app's font files, so pixel-font text for
 * RemoteViews is pre-rendered into bitmaps (with the theme's hard black drop shadow) and shown in ImageViews.
 * Bitmaps are rendered at device density so they display 1:1.
 */
object OsPixelText {
    /** Largest size in [minSp]..[sizeSp] at which [text] fits on one line in [maxWidthPx]. */
    fun fitSize(ctx: Context, text: CharSequence, sizeSp: Float, minSp: Float, maxWidthPx: Int, bold: Boolean = false): Float {
        val dm = ctx.resources.displayMetrics
        val paint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply { typeface = Os.font(ctx, bold) }
        var sp = sizeSp
        while (sp > minSp) {
            paint.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, dm)
            if (paint.measureText(text, 0, text.length) + 2 * dm.density <= maxWidthPx) return sp
            sp -= 1f
        }
        return minSp
    }

    /** @param maxLines 1 = single ellipsized line. Returns null for blank text. */
    fun render(ctx: Context, text: CharSequence, sizeSp: Float, color: Int, maxWidthPx: Int, maxLines: Int = 1,
               shadow: Boolean = true, strike: Boolean = false, bold: Boolean = false): Bitmap? {
        if (text.isBlank()) return null
        val dm = ctx.resources.displayMetrics
        val paint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
            typeface = Os.font(ctx, bold); textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sizeSp, dm); this.color = color
            isStrikeThruText = strike
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
