package com.josh.oldschool

import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes

/**
 * Skill tile for the classic 3-column skills grid: recessed stone slot, pixel icon on the left, and the level shown
 * twice in yellow ("current" top-left, "base" bottom-right, like a boosted/base pair). [setTotal] turns it into the
 * "Total level:" tile. RemoteViews variant: @layout/os_rv_skill_tile.
 */
class OsSkillTile @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : LinearLayout(ctx, attrs) {
    val icon = ImageView(ctx)
    private fun num(g: Int) = TextView(context).apply {
        typeface = Os.font(context, bold = true); setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f); setTextColor(context.getColor(R.color.os_yellow))
        Os.shadow(this, 1.2f); gravity = g; includeFontPadding = false
    }
    val levelView = num(Gravity.START or Gravity.TOP)
    val baseView = num(Gravity.END or Gravity.BOTTOM)
    private val slash = TextView(ctx).apply {
        text = "/"; typeface = Os.font(ctx, bold = true); setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        setTextColor(ctx.getColor(R.color.os_yellow)); Os.shadow(this, 1f); gravity = Gravity.CENTER
    }
    val labelView = TextView(ctx).apply {
        typeface = Os.font(ctx); setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f); setTextColor(ctx.getColor(R.color.os_cyan)); Os.shadow(this, 1f)
        maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END; gravity = Gravity.CENTER
    }
    private val nums = FrameLayout(ctx).apply {
        addView(levelView, FrameLayout.LayoutParams(-2, -2, Gravity.START or Gravity.TOP))
        addView(slash, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        addView(baseView, FrameLayout.LayoutParams(-2, -2, Gravity.END or Gravity.BOTTOM))
    }
    private val right = LinearLayout(ctx).apply { orientation = VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }

    init {
        orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        setBackgroundResource(R.drawable.os_skill_slot)
        val p = Os.dpi(ctx, 6f); setPadding(p, p, p, p)
        minimumHeight = Os.dpi(ctx, 52f)
        addView(icon, LayoutParams(Os.dpi(ctx, 30f), Os.dpi(ctx, 30f)).apply { marginEnd = Os.dpi(ctx, 4f) })
        right.addView(nums, LayoutParams(Os.dpi(ctx, 46f), Os.dpi(ctx, 36f)))
        right.addView(labelView)
        addView(right, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        context.obtainStyledAttributes(attrs, R.styleable.OsSkillTile).apply {
            val i = getResourceId(R.styleable.OsSkillTile_os_icon, 0)
            set(i, getInt(R.styleable.OsSkillTile_os_level, 1), getText(R.styleable.OsSkillTile_os_label))
            recycle()
        }
    }

    /** [level] top-left, [baseLevel] bottom-right (defaults to the same, i.e. not boosted). */
    fun set(@DrawableRes iconRes: Int, level: Int, label: CharSequence? = null, baseLevel: Int = level) {
        icon.visibility = VISIBLE; slash.visibility = VISIBLE; baseView.visibility = VISIBLE
        if (iconRes != 0) icon.setImageResource(iconRes)
        levelView.text = level.toString(); baseView.text = baseLevel.toString()
        (levelView.layoutParams as FrameLayout.LayoutParams).gravity = Gravity.START or Gravity.TOP
        labelView.text = label; labelView.visibility = if (label.isNullOrEmpty()) GONE else VISIBLE
        contentDescription = listOfNotNull(label, "level $level").joinToString(", ")
    }

    /** "Total level:" tile. */
    fun setTotal(total: Int, caption: String = "Total level:") {
        icon.visibility = GONE; slash.visibility = GONE; baseView.visibility = GONE
        levelView.text = "$caption\n$total"; levelView.gravity = Gravity.CENTER
        levelView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        (levelView.layoutParams as FrameLayout.LayoutParams).gravity = Gravity.CENTER
        labelView.visibility = GONE
        nums.layoutParams = nums.layoutParams.apply { width = LayoutParams.MATCH_PARENT }
        contentDescription = "$caption $total"
    }
}
