package com.josh.oldschool

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import androidx.core.content.res.ResourcesCompat

/** Kit entry points shared by every component. */
object Os {
    /** Kit version (mirrors the VERSION file at the kit root). */
    const val VERSION: String = BuildConfig.KIT_VERSION

    @Volatile private var font: Typeface? = null

    /** The bundled Pixelify Sans (SIL OFL 1.1), falling back to monospace if it can't load. */
    fun font(ctx: Context): Typeface =
        font ?: (runCatching { ResourcesCompat.getFont(ctx.applicationContext ?: ctx, R.font.os_pixelify_sans) }.getOrNull()
            ?: Typeface.MONOSPACE).also { font = it }

    fun dp(ctx: Context, v: Float): Float = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, ctx.resources.displayMetrics)
    fun dpi(ctx: Context, v: Float): Int = dp(ctx, v).toInt()

    /** The theme's hard black drop shadow (no blur) for a TextView. */
    fun shadow(tv: android.widget.TextView, dxDp: Float = 1.5f) {
        tv.setShadowLayer(0.01f, dp(tv.context, dxDp), dp(tv.context, dxDp), 0xFF000000.toInt())
    }
}
