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

    /**
     * @param maxLines 1 = single ellipsized line; >1 wraps at word boundaries inside [maxWidthPx] (last line ellipsized).
     * [text] may be a Spanned with ForegroundColorSpans (see [OsChat.text]); the shadow stays black.
     * [center] centres wrapped lines on each other (for slot labels). Returns null for blank text.
     */
    fun render(ctx: Context, text: CharSequence, sizeSp: Float, color: Int, maxWidthPx: Int, maxLines: Int = 1,
               shadow: Boolean = true, strike: Boolean = false, bold: Boolean = false, center: Boolean = false): Bitmap? {
        if (text.isBlank()) return null
        val dm = ctx.resources.displayMetrics
        val paint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
            typeface = Os.font(ctx, bold); textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sizeSp, dm); this.color = color
            isStrikeThruText = strike
        }
        val shadowPx = if (shadow) Os.fontPixel(paint.textSize) else 0f   // v1.3: exactly one font pixel
        val width = (maxWidthPx - shadowPx).toInt().coerceAtLeast(1)
        val align = if (center) Layout.Alignment.ALIGN_CENTER else Layout.Alignment.ALIGN_NORMAL
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(align).setIncludePad(false)
            .setMaxLines(maxLines).setEllipsize(TextUtils.TruncateAt.END).build()
        var left = Float.MAX_VALUE; var right = 0f
        for (i in 0 until layout.lineCount) { left = minOf(left, layout.getLineLeft(i)); right = maxOf(right, layout.getLineRight(i)) }
        if (left == Float.MAX_VALUE) left = 0f
        val w = right - left
        val bw = (w + shadowPx).toInt().coerceIn(1, maxWidthPx.coerceAtLeast(1))
        val bh = (layout.height + shadowPx).toInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        bmp.density = dm.densityDpi
        val c = Canvas(bmp)
        c.translate(-left, 0f)
        if (shadow) {
            // Coloured spans (e.g. OsChat speaker names) must not colour the shadow: draw a span-free black copy.
            val plain: CharSequence = if (text is android.text.Spanned) android.text.SpannableString(text).also { sp ->
                sp.getSpans(0, sp.length, android.text.style.CharacterStyle::class.java)
                    .filter { it !is android.text.style.MetricAffectingSpan }.forEach { sp.removeSpan(it) }
            } else text
            val sPaint = TextPaint(paint).apply { this.color = Color.BLACK }
            val sl = if (plain === text) null else StaticLayout.Builder.obtain(plain, 0, plain.length, sPaint, width)
                .setAlignment(align).setIncludePad(false)
                .setMaxLines(maxLines).setEllipsize(TextUtils.TruncateAt.END).build()
            val oldColor = paint.color
            if (sl == null) paint.color = Color.BLACK
            c.save(); c.translate(shadowPx, shadowPx); (sl ?: layout).draw(c); c.restore()
            paint.color = oldColor
        }
        layout.draw(c)
        return bmp
    }
}
