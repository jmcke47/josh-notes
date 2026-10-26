package com.josh.notes

import android.app.Activity
import android.content.Context
import android.view.View
import com.josh.oldschool.OsBanner
import com.josh.oldschool.OsFireworks
import com.josh.oldschool.OsXp
import com.josh.oldschool.OsXpDrop

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
    const val MAX_LEVEL = OsXp.MAX_LEVEL

    private fun p(ctx: Context) = ctx.getSharedPreferences("settings", 0)

    /** XP needed to reach [level] (level 1 = 0 XP, level 2 = 83, … level 99 = 13,034,431). Curve lives in the kit. */
    fun xpForLevel(level: Int): Int = OsXp.xpForLevel(level)
    fun levelFor(xp: Int): Int = OsXp.levelFor(xp)

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

    /** Floating "+10" with a quill icon that rises from below the toolbar and fades out (kit: OsXpDrop). */
    fun xpDrop(a: Activity, amount: Int): View? = if (!enabled(a)) null else OsXpDrop.show(a, amount)

    /** Level-up chatbox banner with pixel fireworks; tap to continue (kit: OsBanner). */
    fun levelUp(a: Activity, level: Int) {
        if (!enabled(a)) return
        OsBanner.levelUp(a, SKILL, level) ?: return
        SoundFx.play(a, SoundFx.Kind.LEVEL_UP)
    }

    @androidx.annotation.VisibleForTesting internal val lastFireworks: OsFireworks? get() = OsBanner.lastFireworks
}
