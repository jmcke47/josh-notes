package com.josh.notes

import android.app.Activity
import android.content.Context
import android.view.View
import com.josh.oldschool.OsChiptune
import com.josh.oldschool.OsQuestScroll

/** Quest checklists: finishing one shows a "Quest complete!" scroll (Old School) and awards XP. */
object Quests {
    const val XP_QUEST = 250
    private fun p(ctx: Context) = ctx.applicationContext.getSharedPreferences("settings", 0)

    /** Completed outside the app (widget): shown next time a screen opens. */
    fun setPending(ctx: Context, noteId: Long) = p(ctx).edit().putLong("pending_quest", noteId).apply()
    fun consumePending(ctx: Context): Long {
        val id = p(ctx).getLong("pending_quest", 0L)
        if (id != 0L) p(ctx).edit().remove("pending_quest").apply()
        return id
    }

    fun completed(a: Activity, note: Note, awardXp: Boolean = true) {
        if (awardXp) Levels.addXp(a, XP_QUEST, pendingIfLevel = false)
        val title = note.displayTitle.ifBlank { a.getString(R.string.untitled) }
        if (!AppSettings.oldSchool(a)) {
            com.google.android.material.snackbar.Snackbar.make(a.findViewById(android.R.id.content),
                "Quest complete: $title (+$XP_QUEST XP)", com.google.android.material.snackbar.Snackbar.LENGTH_LONG).show()
            SoundFx.play(a, SoundFx.Kind.QUEST)
            return
        }
        scroll(a, title)
    }

    /** "Quest complete!" parchment scroll (kit: OsQuestScroll). */
    fun scroll(a: Activity, questTitle: String): View? {
        val v = OsQuestScroll.show(a, "Quest complete!", "You have completed", questTitle,
            listOf("1 Quest point", "$XP_QUEST ${Levels.SKILL} XP"))
        SoundFx.play(a, SoundFx.Kind.QUEST)
        return v
    }
}

/** Level-up / quest jingles (synthesised by the kit's OsChiptune). Off unless enabled in Settings. */
object SoundFx {
    enum class Kind(val chip: OsChiptune.Kind) { LEVEL_UP(OsChiptune.Kind.LEVEL_UP), QUEST(OsChiptune.Kind.QUEST) }

    fun enabled(ctx: Context) = ctx.getSharedPreferences("settings", 0).getBoolean("sounds", false)
    fun setEnabled(ctx: Context, on: Boolean) = ctx.getSharedPreferences("settings", 0).edit().putBoolean("sounds", on).apply()
    fun synth(kind: Kind): ShortArray = OsChiptune.synth(kind.chip)
    fun play(ctx: Context, kind: Kind) { if (enabled(ctx)) OsChiptune.play(kind.chip) }
}
