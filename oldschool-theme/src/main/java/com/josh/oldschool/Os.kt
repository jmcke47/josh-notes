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
        crispShadows(root, keep = true)
        var last = listOf(root.paddingLeft, root.paddingTop, root.paddingRight, root.paddingBottom)
        root.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            val now = listOf(v.paddingLeft, v.paddingTop, v.paddingRight, v.paddingBottom)
            if (now != last) { last = now; apply() }
        }
    }

    fun dp(ctx: Context, v: Float): Float = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, ctx.resources.displayMetrics)
    fun dpi(ctx: Context, v: Float): Int = dp(ctx, v).toInt()

    /**
     * Size in px of one pixel of the OS Small font at [textSizePx] (11 font pixels per em), snapped to whole device
     * pixels. v1.3: the classic "1px" black drop shadow is exactly this far right and down.
     */
    @JvmStatic fun fontPixel(textSizePx: Float): Float = Math.round(textSizePx / 11f).coerceAtLeast(1).toFloat()

    /** v1.3: give [tv] the classic hard shadow: black, no blur, offset by exactly one font pixel. */
    @JvmStatic fun crispShadow(tv: android.widget.TextView) {
        val d = fontPixel(tv.textSize)
        if (tv.shadowDx != d || tv.shadowDy != d || tv.shadowRadius > 0.02f || tv.shadowColor != 0xFF000000.toInt())
            tv.setShadowLayer(0.01f, d, d, 0xFF000000.toInt())
    }

    /**
     * v1.3: walk [root] and snap every TextView that already has an opaque black shadow (i.e. the theme's shadowed
     * text; parchment/ink text without a shadow is left alone) to [crispShadow]. With [keep] = true it also re-runs
     * after every layout, so rows added later (lists, dialogs) are snapped too. [applyScreenFrame] calls this.
     */
    @JvmStatic @JvmOverloads fun crispShadows(root: android.view.View, keep: Boolean = false) {
        fun walk(v: android.view.View) {
            if (v is android.widget.TextView && v.shadowColor == 0xFF000000.toInt() && v.shadowDx > 0f) crispShadow(v)
            if (v is android.view.ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i))
        }
        walk(root)
        if (keep && root.getTag(R.id.os_crisp_shadows) == null) {
            root.setTag(R.id.os_crisp_shadows, true)
            root.viewTreeObserver.addOnGlobalLayoutListener { walk(root) }
        }
    }

    /** The theme's hard black drop shadow (no blur) for a TextView. */
    fun shadow(tv: android.widget.TextView, dxDp: Float = 1.5f) {
        tv.setShadowLayer(0.01f, dp(tv.context, dxDp), dp(tv.context, dxDp), 0xFF000000.toInt())
    }

    /** Alias for [OsAnimations.scale] (test hook: 0 = instant, >1 = slow motion). See [OsAnimations.freeze]. */
    @JvmStatic var animationScale: Float
        get() = OsAnimations.scale
        set(v) { OsAnimations.scale = v }

    /**
     * Restyle an already-inflated view tree for parchment (use when you can't wrap it in
     * `ThemeOverlay.OldSchool.Parchment`, e.g. views built in code or inflated with the dark theme):
     *  - TextViews: no shadow; light kit colours mapped to ink (yellow/white -> black, orange/link -> link ink,
     *    cyan -> chat blue, green -> dark green, red -> dark red); link colour = link ink.
     *  - EditTexts: ink text, brown hint.
     *  - Buttons (incl. MaterialButton): flat "link-ink" text buttons (os_link_ink, black while pressed) on the
     *    os_chat_row press highlight, no stone background/tint/stroke.
     *  - CheckBox/RadioButton/Switch: ink text, ink-soft button tint.
     * Recurses into ViewGroups. Returns [view].
     */
    @JvmStatic fun <T : android.view.View> parchmentize(view: T): T {
        val c = view.context
        if (view is android.view.ViewGroup) for (i in 0 until view.childCount) parchmentize(view.getChildAt(i))
        if (view !is android.widget.TextView) return view
        view.setShadowLayer(0f, 0f, 0f, 0)
        view.setLinkTextColor(c.getColor(R.color.os_link_ink))
        when (view) {
            is android.widget.CompoundButton -> {
                view.setTextColor(c.getColor(R.color.os_ink))
                view.buttonTintList = android.content.res.ColorStateList.valueOf(c.getColor(R.color.os_ink_soft))
            }
            is android.widget.Button -> {
                view.setTextColor(android.content.res.ColorStateList(
                    arrayOf(intArrayOf(android.R.attr.state_pressed), intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
                    intArrayOf(c.getColor(R.color.os_ink), 0x80B32400.toInt(), c.getColor(R.color.os_link_ink))))
                if (view is com.google.android.material.button.MaterialButton) {
                    view.strokeWidth = 0; view.elevation = 0f; view.stateListAnimator = null
                    view.iconTint = android.content.res.ColorStateList.valueOf(c.getColor(R.color.os_link_ink))
                }
                view.setBackgroundResource(R.drawable.os_chat_row)
                view.backgroundTintList = null
            }
            is android.widget.EditText -> {
                view.setTextColor(c.getColor(R.color.os_ink)); view.setHintTextColor(0xFF8A7346.toInt())
            }
            else -> view.setTextColor(inkFor(c, view.currentTextColor))
        }
        return view
    }

    /** Parchment equivalent of a dark-theme text colour (see [parchmentize]). */
    @JvmStatic fun inkFor(ctx: Context, color: Int): Int {
        val rgb = color or 0xFF000000.toInt()
        fun k(id: Int) = ctx.getColor(id) or 0xFF000000.toInt()
        return when (rgb) {
            k(R.color.os_yellow), k(R.color.os_white), k(R.color.os_header), k(R.color.os_text), k(R.color.os_gold) -> ctx.getColor(R.color.os_ink)
            k(R.color.os_orange), k(R.color.os_link) -> ctx.getColor(R.color.os_link_ink)
            k(R.color.os_cyan) -> ctx.getColor(R.color.os_chat_blue)
            k(R.color.os_green) -> ctx.getColor(R.color.os_answer_correct)
            k(R.color.os_red) -> ctx.getColor(R.color.os_chat_red)
            else -> {
                val l = (0.299 * android.graphics.Color.red(rgb) + 0.587 * android.graphics.Color.green(rgb) + 0.114 * android.graphics.Color.blue(rgb)) / 255
                if (l > 0.55) ctx.getColor(R.color.os_ink) else color
            }
        }
    }
}
