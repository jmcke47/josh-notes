package com.josh.oldschool

import kotlin.math.floor
import kotlin.math.pow

/** The classic RPG experience curve (public formula): level 2 = 83 XP … level 99 = 13,034,431 XP. */
object OsXp {
    const val MAX_LEVEL = 99

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

    /** Progress 0..1 through the current level (1 at 99). */
    fun fraction(xp: Int): Float {
        val l = levelFor(xp); if (l >= MAX_LEVEL) return 1f
        val a = xpForLevel(l); val b = xpForLevel(l + 1)
        return ((xp - a).toFloat() / (b - a)).coerceIn(0f, 1f)
    }
}
