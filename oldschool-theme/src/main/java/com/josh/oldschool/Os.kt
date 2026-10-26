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
    @Volatile private var bold: Typeface? = null

    /**
     * The kit's self-drawn pixel font "OS Small" (SIL OFL 1.1): regular for body text, [bold] = true for headers.
     * Falls back to monospace if it can't load. (Pixelify Sans is still bundled as @font/os_pixelify_sans.)
     */
    fun font(ctx: Context, bold: Boolean = false): Typeface = if (bold)
        this.bold ?: load(ctx, R.font.os_font_small_bold).also { this.bold = it }
    else font ?: load(ctx, R.font.os_font_small).also { font = it }

    private fun load(ctx: Context, id: Int): Typeface =
        runCatching { ResourcesCompat.getFont(ctx.applicationContext ?: ctx, id) }.getOrNull() ?: Typeface.MONOSPACE

    /**
     * Carved, riveted wood frame drawn over a whole screen ([root] = the activity's root view). The frame follows
     * [root]'s padding (e.g. system-bar insets) so it never sits under the status/navigation bar.
     */
    fun applyScreenFrame(root: android.view.View) {
        val frame = androidx.core.content.ContextCompat.getDrawable(root.context, R.drawable.os_screen_frame) ?: return
        fun apply() {
            root.foreground = android.graphics.drawable.InsetDrawable(frame, root.paddingLeft, root.paddingTop, root.paddingRight, root.paddingBottom)
        }
        apply()
        var last = listOf(root.paddingLeft, root.paddingTop, root.paddingRight, root.paddingBottom)
        root.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            val now = listOf(v.paddingLeft, v.paddingTop, v.paddingRight, v.paddingBottom)
            if (now != last) { last = now; apply() }
        }
    }

    fun dp(ctx: Context, v: Float): Float = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, ctx.resources.displayMetrics)
    fun dpi(ctx: Context, v: Float): Int = dp(ctx, v).toInt()

    /** The theme's hard black drop shadow (no blur) for a TextView. */
    fun shadow(tv: android.widget.TextView, dxDp: Float = 1.5f) {
        tv.setShadowLayer(0.01f, dp(tv.context, dxDp), dp(tv.context, dxDp), 0xFF000000.toInt())
    }
}
