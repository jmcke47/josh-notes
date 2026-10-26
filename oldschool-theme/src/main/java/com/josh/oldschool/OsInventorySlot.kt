package com.josh.oldschool

import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.TextView

/**
 * Inventory-slot tile: recessed stone square with a big emoji (or any short text) in the middle, an optional
 * stack count top-left in yellow, and an optional label along the bottom (yellow pixel text, black shadow).
 * Selected = white inner outline (isSelected = true). XML: app:os_emoji, app:os_label, app:os_count.
 * For widgets use @layout/os_rv_inventory_slot (ids os_slot_emoji / os_slot_label / os_slot_count).
 */
class OsInventorySlot @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : FrameLayout(ctx, attrs) {
    val emojiView = TextView(ctx).apply {
        gravity = Gravity.CENTER; setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f); includeFontPadding = false
    }
    val labelView = TextView(ctx).apply {
        gravity = Gravity.CENTER; setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f); typeface = Os.font(ctx)
        setTextColor(ctx.getColor(R.color.os_yellow)); Os.shadow(this, 1f); maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END
    }
    val countView = TextView(ctx).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f); typeface = Os.font(ctx)
        setTextColor(ctx.getColor(R.color.os_yellow)); Os.shadow(this, 1f)
    }

    var emoji: CharSequence? get() = emojiView.text; set(v) { emojiView.text = v; refreshDesc() }
    var label: CharSequence? get() = labelView.text
        set(v) { labelView.text = v; labelView.visibility = if (v.isNullOrEmpty()) GONE else VISIBLE; layoutEmoji(); refreshDesc() }
    var count: Int = 0
        set(v) { field = v; countView.text = if (v > 1) stackText(v) else ""; countView.visibility = if (v > 1) VISIBLE else GONE }

    init {
        setBackgroundResource(R.drawable.os_slot_bg)
        val p = Os.dpi(ctx, 3f); setPadding(p, p, p, p)
        addView(emojiView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(labelView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
        addView(countView, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.START))
        isClickable = true; isFocusable = true
        context.obtainStyledAttributes(attrs, R.styleable.OsInventorySlot).apply {
            emoji = getText(R.styleable.OsInventorySlot_os_emoji)
            label = getText(R.styleable.OsInventorySlot_os_label)
            count = getInt(R.styleable.OsInventorySlot_os_count, 0)
            recycle()
        }
    }

    private fun layoutEmoji() {
        (emojiView.layoutParams as? LayoutParams)?.let {
            it.bottomMargin = if (labelView.visibility == VISIBLE) Os.dpi(context, 12f) else 0; emojiView.layoutParams = it
        }
    }

    private fun refreshDesc() { contentDescription = listOfNotNull(label?.toString()?.ifEmpty { null }, emoji?.toString()).joinToString(" ") }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Square by default (width drives height) unless an exact height is given.
        val hs = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY) heightMeasureSpec
        else MeasureSpec.makeMeasureSpec(
            if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED) Os.dpi(context, 64f) else MeasureSpec.getSize(widthMeasureSpec),
            MeasureSpec.EXACTLY)
        val ws = if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED) MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(hs), MeasureSpec.EXACTLY) else widthMeasureSpec
        super.onMeasure(ws, hs)
    }

    companion object {
        /** Stack-count text: 1-99,999 as is, then "100K", then "10M" (classic stack notation). */
        fun stackText(n: Int): String = when {
            n < 100_000 -> n.toString()
            n < 10_000_000 -> "${n / 1000}K"
            else -> "${n / 1_000_000}M"
        }
    }
}
