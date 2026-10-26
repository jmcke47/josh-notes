package com.josh.oldschool

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.annotation.VisibleForTesting
import kotlin.random.Random

/** Square "pixel" particles bursting from a few points and falling with gravity (2.2 s). */
class OsFireworks @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : View(ctx, attrs) {
    @get:VisibleForTesting val count get() = parts.size
    private class P(var x: Float, var y: Float, var vx: Float, var vy: Float, val color: Int, val size: Float)
    private val parts = mutableListOf<P>()
    private val paint = Paint()
    private var t = 0f
    private val colors = intArrayOf(0xFFFFFF00.toInt(), 0xFFFF981F.toInt(), 0xFFFF3B1F.toInt(), 0xFF00FFFF.toInt(),
        Color.WHITE, 0xFF00FF00.toInt())
    private var anim: ValueAnimator? = null
    private val rnd = Random(System.nanoTime())

    /** Test/preview hook: lay out the bursts and advance [frames] frames without animating. */
    @VisibleForTesting fun prime(frames: Int, fraction: Float) {
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

    private fun step() {
        val g = 0.12f * resources.displayMetrics.density
        parts.forEach { it.x += it.vx; it.y += it.vy; it.vy += g; it.vx *= 0.985f }
    }

    override fun onDraw(canvas: Canvas) {
        parts.forEach { p ->
            paint.color = p.color; paint.alpha = (255 * (1f - t)).toInt().coerceIn(0, 255)
            val s = p.size; val x = (p.x / s).toInt() * s; val y = (p.y / s).toInt() * s // coarse grid = pixelated
            canvas.drawRect(x, y, x + s, y + s, paint)
        }
    }

    override fun onDetachedFromWindow() { anim?.cancel(); super.onDetachedFromWindow() }
}
