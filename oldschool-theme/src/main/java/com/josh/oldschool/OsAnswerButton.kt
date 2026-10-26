package com.josh.oldschool

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import androidx.appcompat.widget.AppCompatButton

/**
 * Quiz answer button: stone (normal), pressed-in (pressed), green (correct) and red (wrong).
 * Background = @drawable/os_answer_button, which reads the custom states app:os_state_correct / app:os_state_wrong.
 * Style: @style/Widget.OldSchool.AnswerButton (applied by default).
 */
class OsAnswerButton @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null,
                                               defStyleAttr: Int = 0)
    : AppCompatButton(ctx, attrs, defStyleAttr) {

    enum class Result { NONE, CORRECT, WRONG }

    var result: Result = Result.NONE
        set(v) { if (field != v) { field = v; refreshDrawableState(); applyTextColor() } }

    init {
        // Always the kit look (a theme's buttonStyle would otherwise supply its own background).
        setBackgroundResource(R.drawable.os_answer_button)
        typeface = Os.font(ctx); isAllCaps = false; gravity = Gravity.CENTER
        minHeight = resources.getDimensionPixelSize(R.dimen.os_answer_min_height); minimumHeight = minHeight
        val h = Os.dpi(ctx, 14f); setPadding(h, Os.dpi(ctx, 8f), h, Os.dpi(ctx, 10f))
        setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.os_text_button))
        Os.shadow(this, 1.5f); stateListAnimator = null
        context.obtainStyledAttributes(attrs, R.styleable.OsAnswerButton, defStyleAttr, R.style.Widget_OldSchool_AnswerButton).apply {
            result = when {
                getBoolean(R.styleable.OsAnswerButton_os_state_correct, false) -> Result.CORRECT
                getBoolean(R.styleable.OsAnswerButton_os_state_wrong, false) -> Result.WRONG
                else -> Result.NONE
            }
            recycle()
        }
        applyTextColor()
    }

    private fun applyTextColor() = setTextColor(context.getColor(when (result) {
        Result.NONE -> R.color.os_orange
        Result.CORRECT, Result.WRONG -> R.color.os_white
    }))

    override fun onCreateDrawableState(extraSpace: Int): IntArray {
        val s = super.onCreateDrawableState(extraSpace + 1)
        @Suppress("KotlinConstantConditions") // result is null during the super-constructor call
        val r: Result? = result
        when (r) {
            Result.CORRECT -> mergeDrawableStates(s, intArrayOf(R.attr.os_state_correct))
            Result.WRONG -> mergeDrawableStates(s, intArrayOf(R.attr.os_state_wrong))
            else -> {}
        }
        return s
    }
}
