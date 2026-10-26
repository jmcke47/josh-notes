package com.josh.notes

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Old School "skills" computed from your notes. Each skill's XP maps to a level with the same experience curve as
 * [Levels]. Active days and completed checklists are counted as they happen (seeded once from existing notes).
 */
object Stats {
    private fun p(ctx: Context) = ctx.applicationContext.getSharedPreferences("stats", 0)
    private val dayFmt get() = SimpleDateFormat("yyyyMMdd", Locale.US)
    private const val MAX_DAYS = 800

    data class Skill(val key: String, val name: String, val icon: Int, val amount: Int, val unit: String, val xp: Int, val note: String = "") {
        val level get() = Levels.levelFor(xp)
        val nextXp get() = if (level >= Levels.MAX_LEVEL) xp else Levels.xpForLevel(level + 1)
    }

    fun recordActiveDay(ctx: Context, at: Long = System.currentTimeMillis()) {
        val d = dayFmt.format(Date(at))
        val prefs = p(ctx)
        val set = prefs.getStringSet("days", null) ?: emptySet()
        if (d in set) return
        val next = (set + d).sorted().takeLast(MAX_DAYS).toSet()
        prefs.edit().putStringSet("days", next).apply()
    }

    private fun seed(ctx: Context) {
        val prefs = p(ctx)
        if (prefs.getBoolean("seeded", false)) return
        val notes = NoteStore.all(ctx)
        val days = (prefs.getStringSet("days", null) ?: emptySet()).toMutableSet()
        notes.forEach { days += dayFmt.format(Date(it.createdAt)); days += dayFmt.format(Date(it.updatedAt)) }
        prefs.edit().putStringSet("days", days.sorted().takeLast(MAX_DAYS).toSet())
            .putInt("checklists_done", prefs.getInt("checklists_done", 0) + notes.count { !it.inTrash && it.isComplete })
            .putBoolean("seeded", true).apply()
    }

    fun checklistCompleted(ctx: Context) {
        seed(ctx)
        p(ctx).edit().putInt("checklists_done", p(ctx).getInt("checklists_done", 0) + 1).apply()
    }

    fun checklistsDone(ctx: Context): Int { seed(ctx); return p(ctx).getInt("checklists_done", 0) }

    fun activeDays(ctx: Context): Set<String> { seed(ctx); return p(ctx).getStringSet("days", null) ?: emptySet() }

    /** (current streak ending today or yesterday, best streak) in days. */
    fun streaks(days: Set<String>, today: Calendar = Calendar.getInstance()): Pair<Int, Int> {
        val fmt = dayFmt
        val sorted = days.mapNotNull { runCatching { fmt.parse(it) }.getOrNull() }.map { dayIndex(it.time) }.toSortedSet()
        var best = 0; var run = 0; var prev = Long.MIN_VALUE
        for (d in sorted) { run = if (d == prev + 1) run + 1 else 1; best = maxOf(best, run); prev = d }
        val t = dayIndex(today.timeInMillis)
        var cur = 0
        var start = if (t in sorted) t else if (t - 1 in sorted) t - 1 else return 0 to best
        while (start in sorted) { cur++; start-- }
        return cur to best
    }

    private fun dayIndex(ms: Long): Long {
        val c = Calendar.getInstance().apply { timeInMillis = ms }
        val utc = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
            clear(); set(c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH))
        }
        return utc.timeInMillis / 86_400_000L
    }

    fun words(n: Note): Int = if (n.secret) 0 else Regex("\\S+").findAll(n.title + " " + n.contentText).count()

    fun skills(ctx: Context): List<Skill> {
        val notes = NoteStore.all(ctx).filter { !it.inTrash }
        val words = notes.sumOf { words(it) }
        val days = activeDays(ctx)
        val (cur, best) = streaks(days)
        val done = checklistsDone(ctx)
        val quests = notes.count { it.quest && it.isComplete }
        val tags = notes.flatMap { it.tags }.toSet().size
        val files = notes.filter { !it.secret }.sumOf { it.attachments.size }
        return listOf(
            Skill("notes", "Scribing", R.drawable.os_ic_book, notes.size, "notes written", notes.size * 120),
            Skill("words", "Wordsmithing", R.drawable.os_ic_inkpot, words, "words", words * 4),
            Skill("streak", "Dedication", R.drawable.os_ic_flame, cur, "day streak", days.size * 350,
                "${days.size} active days · best streak $best"),
            Skill("checklists", "Tasking", R.drawable.os_ic_tick_scroll, done, "checklists completed", done * 300),
            Skill("organising", Levels.SKILL, R.drawable.os_ic_quill, Levels.xp(ctx), "XP from ticks & reminders", Levels.xp(ctx)),
            Skill("quests", "Questing", R.drawable.os_ic_quest, quests, "quests complete", quests * 1_500,
                "${notes.count { it.quest }} quests started"),
            Skill("tags", "Tagging", R.drawable.os_ic_tag, tags, "different #tags", tags * 400),
            Skill("files", "Recording", R.drawable.os_ic_memo, files, "voice memos & attachments", files * 250),
        )
    }

    private val ICONS = mapOf("notes" to R.drawable.os_ic_book, "words" to R.drawable.os_ic_inkpot)

    /** XP drops to show after a save (Old School): new note = Scribing XP, new words = Wordsmithing XP. */
    fun recordSaveXp(ctx: Context, before: Note?, after: Note) {
        if (after.secret) return
        val drops = mutableListOf<String>()
        if (before == null) drops += "notes:120"
        val dw = words(after) - (before?.let { words(it) } ?: 0)
        if (dw > 0) drops += "words:${dw * 4}"
        if (drops.isEmpty()) return
        val prev = p(ctx).getString("pending_drops", "").orEmpty()
        p(ctx).edit().putString("pending_drops", (prev.split(',').filter { it.isNotBlank() } + drops).takeLast(6).joinToString(",")).apply()
    }

    /** (icon, xp) pairs saved by [recordSaveXp] since the last call. */
    fun consumeSaveXp(ctx: Context): List<Pair<Int, Int>> {
        val raw = p(ctx).getString("pending_drops", "").orEmpty()
        if (raw.isEmpty()) return emptyList()
        p(ctx).edit().remove("pending_drops").apply()
        return raw.split(',').mapNotNull { e ->
            val (k, v) = e.split(':').takeIf { it.size == 2 } ?: return@mapNotNull null
            (ICONS[k] ?: return@mapNotNull null) to (v.toIntOrNull() ?: return@mapNotNull null)
        }
    }
}

/** Checklist operations shared by the editor, the widget and tests. */
object Checklists {
    data class Toggle(val note: Note, val completedNow: Boolean)

    /**
     * Toggle item [index] of note [noteId]. [expectText] guards against a stale widget (the item list changed since it
     * was drawn): if the text at [index] differs, the first item with that text is used instead. Returns null if the
     * note/item is gone or the note is secret.
     */
    fun toggle(ctx: Context, noteId: Long, index: Int, expectText: String?): Toggle? {
        val n = NoteStore.get(ctx, noteId) ?: return null
        if (n.secret || !n.checklist) return null
        val i = if (expectText == null || n.items.getOrNull(index)?.text == expectText) index
            else n.items.indexOfFirst { it.text == expectText }
        if (i !in n.items.indices) return null
        val wasComplete = n.isComplete
        val items = n.items.toMutableList().also { it[i] = it[i].copy(checked = !it[i].checked) }
        val saved = NoteStore.upsert(ctx, n.copy(items = items), touch = true)
        val completed = !wasComplete && saved.isComplete
        if (items[i].checked) Levels.addXp(ctx, Levels.XP_ITEM + if (completed) Levels.XP_CHECKLIST_DONE else 0, pendingIfLevel = true)
        if (completed) {
            Stats.checklistCompleted(ctx)
            if (saved.quest) Quests.setPending(ctx, saved.id)
        }
        return Toggle(saved, completed)
    }

    /** Move an item (drag reorder). */
    fun move(items: List<CheckItem>, from: Int, to: Int): List<CheckItem> {
        if (from !in items.indices || to !in items.indices || from == to) return items
        return items.toMutableList().also { val x = it.removeAt(from); it.add(to, x) }
    }
}
