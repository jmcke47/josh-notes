package com.josh.oldschool

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ValueAnimator
import java.util.Collections
import java.util.WeakHashMap

/**
 * Global switch for every kit animation (XP drops, banners/quest scroll fades, fireworks, timer orb).
 *
 * Tests (e.g. Robolectric screenshots):
 * ```
 * OsAnimations.freeze(0.5f)      // kit animations are built, positioned at 50% and held there (never finish)
 * OsXpDrops.rightSide(activity, drops); snap(...)
 * OsAnimations.seek(0.8f)        // move every held animation to 80%
 * OsAnimations.reset()           // back to normal (held animations are released and finish)
 * OsAnimations.scale = 0f        // or: run instantly (0) / slow-motion (e.g. 10f)
 * ```
 * While frozen, entrance/exit fades complete instantly (so overlays are fully visible) and banners don't auto-dismiss.
 * [Os.animationScale] is an alias for [scale].
 */
object OsAnimations {
    /** Multiplies every kit animation's duration and start delay. 0 = jump to the end. Default 1. */
    @JvmStatic @Volatile var scale: Float = 1f
        set(v) { field = v.coerceAtLeast(0f) }

    /** When non-null (0..1), kit animations are seeked to this fraction of their total time and held. */
    @JvmStatic @Volatile var freezeAt: Float? = null
        private set

    /** True when either hook is active. */
    @JvmStatic val testMode: Boolean get() = scale != 1f || freezeAt != null

    private val held: MutableSet<Animator> = Collections.newSetFromMap(WeakHashMap())

    @JvmStatic @JvmOverloads fun freeze(at: Float = 0.5f) { freezeAt = at.coerceIn(0f, 1f) }

    /** Move every held animation to [at] (0..1). */
    @JvmStatic fun seek(at: Float) {
        val f = at.coerceIn(0f, 1f); if (freezeAt != null) freezeAt = f
        synchronized(held) { held.toList() }.forEach { seekTo(it, f) }
    }

    /** Let held animations play on from where they are (they then finish and clean up normally). */
    @JvmStatic fun release() {
        val list = synchronized(held) { held.toList().also { held.clear() } }
        list.forEach { it.start() }
    }

    /** Stop freezing, release held animations, scale back to 1. */
    @JvmStatic fun reset() { freezeAt = null; scale = 1f; release() }

    /** Number of animations currently held by [freeze]. */
    @JvmStatic val heldCount: Int get() = synchronized(held) { held.size }

    /** Scaled duration helper for app code that wants to follow the same switch. */
    @JvmStatic fun duration(ms: Long): Long = (ms * scale).toLong()

    /**
     * Start [a] honouring the hooks. [freezable] = false for entrance/exit fades: under [freeze] they complete
     * instantly instead of being held.
     */
    @JvmStatic @JvmOverloads fun <T : Animator> play(a: T, freezable: Boolean = true): T {
        if (scale != 1f) scaleTree(a, scale)
        val f = freezeAt
        when {
            f == null -> a.start()
            !freezable -> a.end()
            else -> { seekTo(a, f); synchronized(held) { held += a } }
        }
        return a
    }

    private fun scaleTree(a: Animator, s: Float) {
        a.startDelay = (a.startDelay * s).toLong()
        when (a) {
            is AnimatorSet -> a.childAnimations.forEach { scaleTree(it, s) }
            is ValueAnimator -> a.duration = (a.duration * s).toLong()
        }
    }

    private fun seekTo(a: Animator, f: Float) {
        runCatching {
            val total = a.totalDuration
            if (total == Animator.DURATION_INFINITE) return
            val t = (total * f).toLong()
            when (a) {
                is AnimatorSet -> a.currentPlayTime = (t - a.startDelay).coerceIn(0L, (total - a.startDelay).coerceAtLeast(0L))
                is ValueAnimator -> a.currentPlayTime = (t - a.startDelay).coerceIn(0L, a.duration)
            }
        }
    }
}
