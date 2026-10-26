package com.josh.notes.widget

import android.content.Context
import android.graphics.Bitmap
import com.josh.oldschool.OsPixelText

/** Pixel-font text for RemoteViews; implemented by the shared Old School kit (OsPixelText). */
object PixelText {
    fun fitSize(ctx: Context, text: CharSequence, sizeSp: Float, minSp: Float, maxWidthPx: Int, bold: Boolean = false): Float =
        OsPixelText.fitSize(ctx, text, sizeSp, minSp, maxWidthPx, bold)

    fun render(ctx: Context, text: CharSequence, sizeSp: Float, color: Int, maxWidthPx: Int, maxLines: Int = 1,
               shadow: Boolean = true, strike: Boolean = false, bold: Boolean = false): Bitmap? =
        OsPixelText.render(ctx, text, sizeSp, color, maxWidthPx, maxLines, shadow, strike, bold)
}
