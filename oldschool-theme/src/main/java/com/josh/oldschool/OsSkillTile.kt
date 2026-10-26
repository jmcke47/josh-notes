package com.josh.oldschool

import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes

/**
 * Skill tile (as in a skills tab): stone slot with an icon on the left and "level/level" in yellow pixel text on the
 * right; [label] (optional) under it in cyan. RemoteViews variant: @layout/os_rv_skill_tile.
 */
class OsSkillTile @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : LinearLayout(ctx, attrs) {
    val icon = ImageView(ctx)
    val levelView = TextView(ctx).apply {
        typeface = Os.font(ctx); setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f); setTextColor(ctx.getColor(R.color.os_yellow)); Os.shadow(this, 1f)
    }
    val labelView = TextView(ctx).apply {
        typeface = Os.font(ctx); setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f); setTextColor(ctx.getColor(R.color.os_cyan)); Os.shadow(this, 1f)
        maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
    }

    init {
        orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        setBackgroundResource(R.drawable.os_frame_filled)
        addView(icon, LayoutParams(Os.dpi(ctx, 32f), Os.dpi(ctx, 32f)).apply { marginEnd = Os.dpi(ctx, 6f) })
        addView(LinearLayout(ctx).apply {
            orientation = VERTICAL; addView(levelView); addView(labelView)
        }, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        context.obtainStyledAttributes(attrs, R.styleable.OsSkillTile).apply {
            val i = getResourceId(R.styleable.OsSkillTile_os_icon, 0); if (i != 0) icon.setImageResource(i)
            set(i, getInt(R.styleable.OsSkillTile_os_level, 1), getText(R.styleable.OsSkillTile_os_label))
            recycle()
        }
    }

    fun set(@DrawableRes iconRes: Int, level: Int, label: CharSequence? = null, maxLevel: Int = level) {
        if (iconRes != 0) icon.setImageResource(iconRes)
        levelView.text = "$level/$maxLevel"
        labelView.text = label; labelView.visibility = if (label.isNullOrEmpty()) GONE else VISIBLE
        contentDescription = listOfNotNull(label, "level $level").joinToString(", ")
    }
}
