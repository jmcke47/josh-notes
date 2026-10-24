package com.josh.notes

import android.app.Activity
import android.content.Context
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView

/** Playful "level up" counter for the Old School theme (completed checklists / reminders). */
object Levels {
    private fun p(ctx: Context) = ctx.getSharedPreferences("settings", 0)

    /** +1 completion; returns the new level (1 level per 3 completions, min 1). */
    fun award(ctx: Context): Int {
        val n = p(ctx).getInt("completions", 0) + 1
        p(ctx).edit().putInt("completions", n).apply()
        return level(n)
    }

    fun level(completions: Int) = 1 + completions / 3

    /** Banner at the top of the screen, only in the Old School theme. */
    fun celebrate(a: Activity, level: Int, leveledUp: Boolean) {
        if (AppSettings.theme(a) != ThemeMode.OLDSCHOOL || !AppSettings.levelUps(a)) return
        val root = a.window.decorView as ViewGroup
        val d = a.resources.displayMetrics.density
        val tv = TextView(a).apply {
            text = if (leveledUp) "Level up! Your Organising level is now $level." else "Task complete! Organising XP +1"
            setBackgroundResource(R.drawable.os_panel)
            setTextColor(0xFFFFFF00.toInt())
            textSize = 15f
            gravity = Gravity.CENTER
            val p = (14 * d).toInt(); setPadding(p, p, p, p)
            alpha = 0f
        }
        root.addView(tv, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.TOP).apply { val m = (16 * d).toInt(); setMargins(m, (72 * d).toInt(), m, 0) })
        tv.animate().alpha(1f).setDuration(200).withEndAction {
            tv.animate().alpha(0f).setStartDelay(2200).setDuration(400).withEndAction { root.removeView(tv) }.start()
        }.start()
    }
}
