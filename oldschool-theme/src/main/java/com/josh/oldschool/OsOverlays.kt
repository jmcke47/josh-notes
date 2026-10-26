package com.josh.oldschool

import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.app.Activity
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.VisibleForTesting
import androidx.core.content.res.ResourcesCompat

private fun Activity.root() = window.decorView as? ViewGroup

/** Floating "+XP" drop: white pixel number with an icon, rises and fades (1.4 s). */
object OsXpDrop {
    fun show(a: Activity, amount: Int, @DrawableRes icon: Int = R.drawable.os_ic_quill, topDp: Float = 130f, endDp: Float = 110f): View? {
        val root = a.root() ?: return null
        val tv = TextView(a).apply {
            text = "+$amount"; setTextColor(Color.WHITE); textSize = 20f; typeface = Os.font(a, bold = true)
            setShadowLayer(0.01f, Os.dp(a, 1f), Os.dp(a, 1f), Color.BLACK)
            val d = ResourcesCompat.getDrawable(resources, icon, null)?.mutate()
            d?.setBounds(0, 0, Os.dpi(a, 22f), Os.dpi(a, 22f))
            setCompoundDrawables(d, null, null, null)
            compoundDrawablePadding = Os.dpi(a, 4f); gravity = Gravity.CENTER_VERTICAL
            tag = "os_xp_drop"
        }
        root.addView(tv, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.END).apply { topMargin = Os.dpi(a, topDp); marginEnd = Os.dpi(a, endDp) })
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(tv, View.TRANSLATION_Y, 0f, -Os.dp(a, 95f)).setDuration(1400).apply { interpolator = DecelerateInterpolator() },
                ObjectAnimator.ofFloat(tv, View.ALPHA, 1f, 0f).setDuration(500).apply { startDelay = 900 })
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) { root.removeView(tv) }
            })
            start()
        }
        return tv
    }
}

/**
 * XP drops floating up the right-hand side (2007 style): each (icon, amount) pair rises from mid-screen and fades;
 * several drops are staggered. Returns the views (already attached).
 */
object OsXpDrops {
    fun rightSide(a: Activity, drops: List<Pair<Int, Int>>, staggerMs: Long = 320): List<View> {
        val root = a.root() ?: return emptyList()
        return drops.filter { it.second > 0 }.mapIndexed { i, (icon, amount) ->
            val tv = TextView(a).apply {
                text = "+$amount"; setTextColor(Color.WHITE); textSize = 18f; typeface = Os.font(a, bold = true)
                setShadowLayer(0.01f, Os.dp(a, 1.2f), Os.dp(a, 1.2f), Color.BLACK)
                val d = ResourcesCompat.getDrawable(resources, icon, null)?.mutate()
                d?.setBounds(0, 0, Os.dpi(a, 20f), Os.dpi(a, 20f))
                setCompoundDrawables(d, null, null, null); compoundDrawablePadding = Os.dpi(a, 3f)
                gravity = Gravity.CENTER_VERTICAL; alpha = 0f; tag = "os_xp_drop"
            }
            root.addView(tv, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.END).apply { topMargin = (root.height.takeIf { it > 0 } ?: Os.dpi(a, 800f)) * 45 / 100; marginEnd = Os.dpi(a, 18f) })
            AnimatorSet().apply {
                startDelay = i * staggerMs
                playTogether(
                    ObjectAnimator.ofFloat(tv, View.ALPHA, 0f, 1f).setDuration(120),
                    ObjectAnimator.ofFloat(tv, View.TRANSLATION_Y, 0f, -Os.dp(a, 220f)).setDuration(1700),
                    ObjectAnimator.ofFloat(tv, View.ALPHA, 1f, 0f).setDuration(400).apply { startDelay = 1300 })
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) { root.removeView(tv) }
                })
                start()
            }
            tv
        }
    }
}

/** Chatbox banners (parchment box at the bottom), e.g. the level-up message with pixel fireworks. Tap to continue. */
object OsBanner {
    @VisibleForTesting var lastFireworks: OsFireworks? = null

    class Line(val text: String, val color: Int, val sizeSp: Float)

    /** Generic banner. Returns the overlay (already attached). Auto-dismisses after [autoDismissMs] (0 = never). */
    fun show(a: Activity, lines: List<Line>, @DrawableRes icon: Int = 0, fireworks: Boolean = false,
             autoDismissMs: Long = 8000, onDismiss: (() -> Unit)? = null): View? {
        val root = a.root() ?: return null
        val overlay = FrameLayout(a).apply { isClickable = true; setBackgroundColor(0x33000000) }
        val fw = if (fireworks) OsFireworks(a).also {
            overlay.addView(it, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        } else null
        val pad = Os.dpi(a, 14f)
        val box = LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setBackgroundResource(R.drawable.os_chatbox_carved); setPadding(pad, pad, pad, pad)
            if (icon != 0) addView(ImageView(a).apply { setImageResource(icon) },
                LinearLayout.LayoutParams(Os.dpi(a, 44f), Os.dpi(a, 44f)).apply { marginEnd = pad })
            addView(LinearLayout(a).apply {
                orientation = LinearLayout.VERTICAL
                lines.forEachIndexed { i, l ->
                    addView(TextView(a).apply {
                        text = l.text; setTextColor(l.color); textSize = l.sizeSp; typeface = Os.font(a, bold = i == 0); gravity = Gravity.CENTER
                        if (i in 1 until lines.size - 1) setPadding(0, pad / 3, 0, pad / 3)
                    })
                }
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        overlay.addView(box, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM).apply { val m = Os.dpi(a, 10f); setMargins(m, m, m, Os.dpi(a, 96f)) })
        var gone = false
        val dismiss = {
            if (!gone) { gone = true; onDismiss?.invoke(); overlay.animate().alpha(0f).setDuration(200).withEndAction { root.removeView(overlay) }.start() }
        }
        overlay.setOnClickListener { dismiss() }
        root.addView(overlay, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        overlay.alpha = 0f; overlay.animate().alpha(1f).setDuration(150).start()
        fw?.start(); if (fw != null) lastFireworks = fw
        if (autoDismissMs > 0) overlay.postDelayed({ dismiss() }, autoDismissMs)
        return overlay
    }

    /** "Congratulations, you just advanced a <skill> level." */
    fun levelUp(a: Activity, skill: String, level: Int, @DrawableRes icon: Int = R.drawable.os_ic_quill): View? {
        val article = if (skill.firstOrNull()?.lowercaseChar() in setOf('a', 'e', 'i', 'o', 'u')) "an" else "a"
        return show(a, listOf(
            Line("Congratulations, you just advanced $article $skill level.", a.getColor(R.color.os_chat_blue_dark), 17f),
            Line("Your $skill level is now $level.", Color.BLACK, 16f),
            Line("Click here to continue", a.getColor(R.color.os_link_ink), 15f)), icon, fireworks = true)
    }
}

/** Centre-screen parchment scroll with rolled ends, e.g. "Quest complete!". Tap to continue. */
object OsQuestScroll {
    fun show(a: Activity, heading: String, subtitle: String, title: String, rewards: List<String>,
             @DrawableRes icon: Int = R.drawable.os_ic_tick_scroll, onDismiss: (() -> Unit)? = null): View? {
        val root = a.root() ?: return null
        val font = Os.font(a)
        val overlay = FrameLayout(a).apply { isClickable = true; setBackgroundColor(0x88000000.toInt()) }
        val pad = Os.dpi(a, 22f)
        fun line(t: String, color: Int, size: Float, shadow: Boolean = false) = TextView(a).apply {
            text = t; setTextColor(color); textSize = size; typeface = font; gravity = Gravity.CENTER
            if (shadow) Os.shadow(this)
        }
        val box = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundResource(R.drawable.os_scroll); setPadding(pad, (pad * 1.6f).toInt(), pad, (pad * 1.4f).toInt())
            addView(line(heading, a.getColor(R.color.os_orange), 30f, shadow = true).apply { typeface = Os.font(a, bold = true) })
            if (icon != 0) addView(ImageView(a).apply { setImageResource(icon) },
                LinearLayout.LayoutParams(Os.dpi(a, 56f), Os.dpi(a, 56f)).apply { topMargin = pad / 2; bottomMargin = pad / 2 })
            addView(line(subtitle, Color.BLACK, 17f))
            addView(line(title, a.getColor(R.color.os_chat_blue_dark), 20f).apply { setPadding(0, pad / 4, 0, pad / 2) })
            if (rewards.isNotEmpty()) addView(line("Reward:", a.getColor(R.color.os_ink_brown), 16f))
            rewards.forEach { addView(line(it, Color.BLACK, 16f)) }
            addView(line("Click here to continue", a.getColor(R.color.os_link_ink), 15f).apply { setPadding(0, pad / 2, 0, 0) })
        }
        overlay.addView(box, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER).apply { val m = Os.dpi(a, 18f); setMargins(m, m, m, m) })
        var gone = false
        overlay.setOnClickListener {
            if (!gone) { gone = true; onDismiss?.invoke(); overlay.animate().alpha(0f).setDuration(200).withEndAction { root.removeView(overlay) }.start() }
        }
        root.addView(overlay, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        overlay.alpha = 0f; overlay.animate().alpha(1f).setDuration(180).start()
        return overlay
    }
}
