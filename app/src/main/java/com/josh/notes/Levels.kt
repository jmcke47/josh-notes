package com.josh.notes

import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import kotlin.math.floor
import kotlin.math.pow
import kotlin.random.Random

/**
 * Playful "Organising" skill for the Old School theme: XP for ticking checklist items and completing reminders,
 * floating "+XP" drops, and a level-up banner with pixel fireworks. Uses the classic RPG experience curve
 * (a public formula); all visuals are original.
 */
object Levels {
    const val SKILL = "Organising"
    const val XP_ITEM = 10
    const val XP_CHECKLIST_DONE = 40
    const val XP_REMINDER = 25
    const val MAX_LEVEL = 99

    private fun p(ctx: Context) = ctx.getSharedPreferences("settings", 0)

    /** XP needed to reach [level] (level 1 = 0 XP, level 2 = 83, … level 99 = 13,034,431). */
    fun xpForLevel(level: Int): Int {
        var points = 0.0
        for (n in 1 until level.coerceIn(1, MAX_LEVEL)) points += floor(n + 300.0 * 2.0.pow(n / 7.0))
        return floor(points / 4).toInt()
    }

    fun levelFor(xp: Int): Int {
        var l = 1
        while (l < MAX_LEVEL && xp >= xpForLevel(l + 1)) l++
        return l
    }

    fun xp(ctx: Context): Int {
        val prefs = p(ctx)
        if (!prefs.contains("xp")) {
            // v1.3 counted completions; carry them over.
            val legacy = prefs.getInt("completions", 0) * 50
            prefs.edit().putInt("xp", legacy).apply()
            return legacy
        }
        return prefs.getInt("xp", 0)
    }

    data class Gain(val amount: Int, val oldLevel: Int, val newLevel: Int) { val leveledUp get() = newLevel > oldLevel }

    /** Adds XP; if [pendingIfLevel] (no UI, e.g. a notification action) a level-up is shown next time the app opens. */
    fun addXp(ctx: Context, amount: Int, pendingIfLevel: Boolean = false): Gain {
        val before = xp(ctx)
        val after = (before.toLong() + amount).coerceAtMost(200_000_000L).toInt()
        val g = Gain(amount, levelFor(before), levelFor(after))
        p(ctx).edit().putInt("xp", after).apply {
            if (pendingIfLevel && g.leveledUp) putInt("pending_level", g.newLevel)
        }.apply()
        return g
    }

    fun consumePendingLevel(ctx: Context): Int {
        val l = p(ctx).getInt("pending_level", 0)
        if (l > 0) p(ctx).edit().remove("pending_level").apply()
        return l
    }

    private fun enabled(a: Activity) = AppSettings.oldSchool(a) && AppSettings.levelUps(a)

    /** Award + show the XP drop (and the level-up banner when a level is gained). */
    fun reward(a: Activity, amount: Int) {
        val g = addXp(a, amount)
        if (!enabled(a)) return
        xpDrop(a, amount)
        if (g.leveledUp) a.window.decorView.postDelayed({ if (!a.isFinishing) levelUp(a, g.newLevel) }, 450)
    }

    private fun dp(a: Context, v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, a.resources.displayMetrics)
    private fun pixelFont(a: Context): Typeface? = runCatching { ResourcesCompat.getFont(a, R.font.pixelify_sans) }.getOrNull()

    /** Floating "+10" with a quill icon that rises from below the toolbar and fades out. */
    fun xpDrop(a: Activity, amount: Int): View? {
        if (!enabled(a)) return null
        val root = a.window.decorView as? ViewGroup ?: return null
        val tv = TextView(a).apply {
            text = "+$amount"
            setTextColor(Color.WHITE)
            textSize = 20f
            typeface = pixelFont(a)
            setShadowLayer(0.01f, dp(a, 1f), dp(a, 1f), Color.BLACK)
            val icon = ResourcesCompat.getDrawable(resources, R.drawable.ic_os_xp, null)?.mutate()
            icon?.setBounds(0, 0, dp(a, 22f).toInt(), dp(a, 22f).toInt())
            setCompoundDrawables(icon, null, null, null)
            compoundDrawablePadding = dp(a, 4f).toInt()
            gravity = Gravity.CENTER_VERTICAL
        }
        val top = dp(a, 130f)
        root.addView(tv, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.END).apply { topMargin = top.toInt(); marginEnd = dp(a, 110f).toInt() })
        android.animation.AnimatorSet().apply {
            playTogether(
                android.animation.ObjectAnimator.ofFloat(tv, View.TRANSLATION_Y, 0f, -dp(a, 95f)).setDuration(1400)
                    .apply { interpolator = DecelerateInterpolator() },
                android.animation.ObjectAnimator.ofFloat(tv, View.ALPHA, 1f, 0f).setDuration(500).apply { startDelay = 900 })
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) { root.removeView(tv) }
            })
            start()
        }
        return tv
    }

    /** Level-up chatbox banner with pixel fireworks; tap to continue. */
    fun levelUp(a: Activity, level: Int) {
        if (!enabled(a)) return
        val root = a.window.decorView as? ViewGroup ?: return
        val overlay = FrameLayout(a).apply { isClickable = true; setBackgroundColor(0x33000000) }
        val fireworks = PixelFireworks(a)
        overlay.addView(fireworks, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        val font = pixelFont(a)
        fun line(text: String, color: Int, size: Float) = TextView(a).apply {
            this.text = text; setTextColor(color); textSize = size; typeface = font; gravity = Gravity.CENTER
        }
        val pad = dp(a, 14f).toInt()
        val box = LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setBackgroundResource(R.drawable.os_chatbox); setPadding(pad, pad, pad, pad)
            addView(ImageView(a).apply { setImageResource(R.drawable.ic_os_xp) },
                LinearLayout.LayoutParams(dp(a, 44f).toInt(), dp(a, 44f).toInt()).apply { marginEnd = pad })
            addView(LinearLayout(a).apply {
                orientation = LinearLayout.VERTICAL
                addView(line("Congratulations, you just advanced an $SKILL level.", 0xFF000080.toInt(), 17f))
                addView(line("Your $SKILL level is now $level.", Color.BLACK, 16f).apply { setPadding(0, pad / 3, 0, pad / 3) })
                addView(line("Click here to continue", a.getColor(R.color.os_link_ink), 15f))
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        overlay.addView(box, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM).apply { val m = dp(a, 10f).toInt(); setMargins(m, m, m, dp(a, 96f).toInt()) })
        var gone = false
        val dismiss = { if (!gone) { gone = true; overlay.animate().alpha(0f).setDuration(200).withEndAction { root.removeView(overlay) }.start() } }
        overlay.setOnClickListener { dismiss() }
        root.addView(overlay, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        overlay.alpha = 0f; overlay.animate().alpha(1f).setDuration(150).start()
        fireworks.start()
        SoundFx.play(a, SoundFx.Kind.LEVEL_UP)
        lastFireworks = fireworks
        overlay.postDelayed({ dismiss() }, 8000)
    }

    /** Square "pixel" particles bursting from a few points, falling with gravity. */
    @androidx.annotation.VisibleForTesting internal var lastFireworks: PixelFireworks? = null

    class PixelFireworks(ctx: Context) : View(ctx) {
        @androidx.annotation.VisibleForTesting internal val count get() = parts.size
        private class P(var x: Float, var y: Float, var vx: Float, var vy: Float, val color: Int, val size: Float)
        private val parts = mutableListOf<P>()
        private val paint = Paint()
        private var t = 0f
        private val colors = intArrayOf(0xFFFFFF00.toInt(), 0xFFFF981F.toInt(), 0xFFFF3B1F.toInt(), 0xFF00FFFF.toInt(),
            Color.WHITE, 0xFF00FF00.toInt())
        private var anim: ValueAnimator? = null
        private val rnd = Random(System.nanoTime())

        /** Test hook: lay out the bursts and advance [frames] frames without animating. */
        @androidx.annotation.VisibleForTesting internal fun prime(frames: Int, fraction: Float) {
            anim?.cancel(); parts.clear(); spawn(); repeat(frames) { step() }; t = fraction; invalidate()
        }

        fun start(tries: Int = 0) {
            post {
                if (width == 0) { if (tries < 20) postDelayed({ start(tries + 1) }, 16); return@post }
                spawn()
                anim = ValueAnimator.ofFloat(0f, 1f).setDuration(2200).apply {
                    var frames = 0
                    addUpdateListener { va ->
                        t = va.animatedValue as Float
                        val target = (va.currentPlayTime / 16L).toInt() // frame-rate independent
                        while (frames < target) { step(); frames++ }
                        invalidate()
                    }
                    start()
                }
            }
        }

        private fun spawn() {
            run {
                val d = resources.displayMetrics.density
                repeat(4) { burst ->
                    val cx = width * (0.2f + 0.6f * rnd.nextFloat()); val cy = height * (0.15f + 0.35f * rnd.nextFloat())
                    val c1 = colors[rnd.nextInt(colors.size)]; val c2 = colors[rnd.nextInt(colors.size)]
                    repeat(36) { i ->
                        val ang = (i / 36f) * 2 * Math.PI + rnd.nextFloat() * 0.2
                        val sp = (2.5f + rnd.nextFloat() * 3.5f) * d
                        parts += P(cx, cy, (Math.cos(ang) * sp).toFloat(), (Math.sin(ang) * sp).toFloat() - burst * 0.2f * d,
                            if (i % 2 == 0) c1 else c2, (3 + rnd.nextInt(3)) * d)
                    }
                }
            }
        }

        private fun step() {
            val g = 0.12f * resources.displayMetrics.density
            parts.forEach { it.x += it.vx; it.y += it.vy; it.vy += g; it.vx *= 0.985f }
        }

        override fun onDraw(canvas: Canvas) {
            paint.alpha = (255 * (1f - t).coerceIn(0f, 1f)).toInt()
            parts.forEach { p ->
                paint.color = p.color; paint.alpha = (255 * (1f - t)).toInt().coerceIn(0, 255)
                // snap to a coarse grid so it looks pixelated
                val s = p.size; val x = (p.x / s).toInt() * s; val y = (p.y / s).toInt() * s
                canvas.drawRect(x, y, x + s, y + s, paint)
            }
        }

        override fun onDetachedFromWindow() { anim?.cancel(); super.onDetachedFromWindow() }
    }
}
