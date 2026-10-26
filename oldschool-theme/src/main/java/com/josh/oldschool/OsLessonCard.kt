package com.josh.oldschool

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout

/**
 * Lesson card: a vertical parchment scroll panel with rolled wooden ends top and bottom, for long text.
 * Children get black ink text if you wrap them in ThemeOverlay.OldSchool.Parchment (style does this via
 * android:theme) or use TextAppearance.OldSchool.Ink.*. Put it inside a ScrollView for very long lessons.
 */
class OsLessonCard @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null,
                                             defStyleAttr: Int = 0)
    : LinearLayout(ctx, attrs, defStyleAttr, R.style.Widget_OldSchool_LessonCard) {
    init {
        orientation = VERTICAL
        if (background == null) setBackgroundResource(R.drawable.os_lesson_card)
        if (paddingTop == 0 && paddingLeft == 0) {
            val h = Os.dpi(ctx, 20f); val v = Os.dpi(ctx, 26f); setPadding(h, v, h, v)
        }
    }
}
