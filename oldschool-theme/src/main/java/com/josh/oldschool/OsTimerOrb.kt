package com.josh.oldschool

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.content.ContextCompat
import kotlin.math.ceil
import kotlin.math.min

/**
 * Countdown timer drawn as a stat orb: stone ring (@drawable/os_orb) with a segmented ring that drains anticlockwise
 * from 12 o'clock, and the seconds left in pixel text in the middle. Ring colour goes green > yellow > red.
 *
 *   orb.start(30_000) { /* time's up */ }   orb.pause()   orb.resume()   orb.stop()
 *   orb.setRemaining(fraction)  // drive it yourself (no animation)
 */
class OsTimerOrb @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : View(ctx, attrs) {
    /** 1 = full, 0 = empty. */
    var fraction = 1f; private set
    var durationMs = 30_000L; private set
    /** Number of ring segments (pixel look). */
    var segments = 32
    var showText = true
    var onTick: ((secondsLeft: Int) -> Unit)? = null
    private var onFinished: (() -> Unit)? = null
    private var anim: ValueAnimator? = null
    private var lastSecond = -1

    private val frame = ContextCompat.getDrawable(ctx, R.drawable.os_orb)
    private val seg = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.BUTT }
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0xFF120F0B.toInt() }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = Os.font(ctx) }
    private val oval = RectF()

    init {
        context.obtainStyledAttributes(attrs, R.styleable.OsTimerOrb).apply {
            durationMs = getInt(R.styleable.OsTimerOrb_os_durationMs, 30_000).toLong()
            segments = getInt(R.styleable.OsTimerOrb_os_segments, 32)
            showText = getBoolean(R.styleable.OsTimerOrb_os_showText, true)
            fraction = getFloat(R.styleable.OsTimerOrb_os_fraction, 1f)
            recycle()
        }
        contentDescription = "Timer"
    }

    val secondsLeft get() = ceil(fraction * durationMs / 1000.0).toInt()

    fun start(durationMs: Long, onFinished: (() -> Unit)? = null) {
        this.durationMs = durationMs.coerceAtLeast(1); this.onFinished = onFinished
        runFrom(1f)
    }

    fun pause() { anim?.pause() }
    fun resume() { anim?.resume() }
    fun stop() { anim?.cancel(); anim = null }

    /** Set the remaining fraction directly (stops any running countdown). */
    fun setRemaining(f: Float, durationMs: Long = this.durationMs) {
        stop(); this.durationMs = durationMs; fraction = f.coerceIn(0f, 1f); invalidate()
    }

    private fun runFrom(from: Float) {
        stop()
        fraction = from; lastSecond = -1
        anim = ValueAnimator.ofFloat(from, 0f).setDuration((durationMs * from).toLong()).apply {
            interpolator = LinearInterpolator()
            addUpdateListener {
                fraction = it.animatedValue as Float
                val s = secondsLeft
                if (s != lastSecond) { lastSecond = s; onTick?.invoke(s); contentDescription = "$s seconds left" }
                invalidate()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                var cancelled = false
                override fun onAnimationCancel(animation: android.animation.Animator) { cancelled = true }
                override fun onAnimationEnd(animation: android.animation.Animator) { if (!cancelled) onFinished?.invoke() }
            })
            OsAnimations.play(this)
        }
    }

    fun ringColor(): Int = context.getColor(when {
        fraction > 0.5f -> R.color.os_timer_ok
        fraction > 0.2f -> R.color.os_timer_warn
        else -> R.color.os_timer_low
    })

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val d = Os.dpi(context, 56f)
        val w = resolveSize(d, widthMeasureSpec); val h = resolveSize(d, heightMeasureSpec)
        val s = min(w, h); setMeasuredDimension(s, s)
    }

    override fun onDraw(canvas: Canvas) {
        val s = min(width, height).toFloat()
        frame?.setBounds(0, 0, s.toInt(), s.toInt()); frame?.draw(canvas)
        // ring sits on the stone band (r 12.6..14.6 of 32 in the orb art)
        val stroke = s * 2.6f / 32f; val r = s * 13.6f / 32f; val c = s / 2
        oval.set(c - r, c - r, c + r, c + r)
        seg.strokeWidth = stroke; track.strokeWidth = stroke
        canvas.drawArc(oval, 0f, 360f, false, track)
        seg.color = ringColor()
        val n = segments.coerceAtLeast(4); val step = 360f / n; val gap = if (n >= 8) step * 0.18f else 0f
        val lit = ceil(fraction * n - 1e-4f).toInt()
        for (i in 0 until lit) canvas.drawArc(oval, -90f + i * step + gap / 2, step - gap, false, seg)
        if (showText) {
            text.textSize = s * 0.34f
            val t = secondsLeft.toString(); val y = c - (text.descent() + text.ascent()) / 2
            text.color = 0xFF000000.toInt(); canvas.drawText(t, c + s * 0.03f, y + s * 0.03f, text)
            text.color = seg.color; canvas.drawText(t, c, y, text)
        }
    }

    override fun onDetachedFromWindow() { stop(); super.onDetachedFromWindow() }
}
