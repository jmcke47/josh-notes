package com.josh.notes

import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.Locale

/**
 * Reminder repetition rule.
 * type: NONE, DAILY, WEEKLY (days = Calendar.SUNDAY..SATURDAY), MONTHLY (dayOfMonth anchored), YEARLY, EVERY_N_DAYS (every).
 */
data class Repeat(val type: Type = Type.NONE, val days: Set<Int> = emptySet(), val every: Int = 1, val dayOfMonth: Int = 0) {
    enum class Type { NONE, DAILY, WEEKLY, MONTHLY, YEARLY, EVERY_N_DAYS }

    val isRepeating get() = type != Type.NONE

    fun toJson(): JSONObject = JSONObject().put("type", type.name).put("every", every).put("dayOfMonth", dayOfMonth)
        .put("days", JSONArray().apply { days.sorted().forEach { put(it) } })

    /** First occurrence on/after the picked time (weekly: move to the first selected weekday). */
    fun first(picked: Long): Long {
        if (type != Type.WEEKLY || days.isEmpty()) return picked
        val c = cal(picked)
        repeat(8) { if (c.get(Calendar.DAY_OF_WEEK) in days) return c.timeInMillis; c.add(Calendar.DAY_OF_MONTH, 1) }
        return picked
    }

    /** Next occurrence strictly after both [occurrence] and [now]; 0 if not repeating. */
    fun next(occurrence: Long, now: Long): Long {
        if (!isRepeating) return 0L
        val c = cal(occurrence)
        val anchorDom = if (dayOfMonth in 1..31) dayOfMonth else c.get(Calendar.DAY_OF_MONTH)
        var guard = 0
        do {
            when (type) {
                Type.DAILY -> c.add(Calendar.DAY_OF_MONTH, 1)
                Type.EVERY_N_DAYS -> c.add(Calendar.DAY_OF_MONTH, every.coerceAtLeast(1))
                Type.WEEKLY -> {
                    if (days.isEmpty()) c.add(Calendar.DAY_OF_MONTH, 7)
                    else { var i = 0; do { c.add(Calendar.DAY_OF_MONTH, 1); i++ } while (c.get(Calendar.DAY_OF_WEEK) !in days && i < 8) }
                }
                Type.MONTHLY -> {
                    c.set(Calendar.DAY_OF_MONTH, 1); c.add(Calendar.MONTH, 1)
                    c.set(Calendar.DAY_OF_MONTH, minOf(anchorDom, c.getActualMaximum(Calendar.DAY_OF_MONTH)))
                }
                Type.YEARLY -> c.add(Calendar.YEAR, 1)
                Type.NONE -> return 0L
            }
        } while (c.timeInMillis <= now && ++guard < 100_000)
        return c.timeInMillis
    }

    /** e.g. "Every Mon, Wed 9:00", "Every day 9:00", "Every 3 days 9:00". [time] is a pre-formatted time. */
    fun describe(at: Long, time: String, locale: Locale = Locale.getDefault()): String {
        val c = cal(at)
        return when (type) {
            Type.NONE -> ""
            Type.DAILY -> "Every day $time"
            Type.EVERY_N_DAYS -> if (every <= 1) "Every day $time" else "Every $every days $time"
            Type.WEEKLY -> {
                val names = ORDER.filter { it in days }.map { dayName(it, locale) }
                if (days.size == 7) "Every day $time"
                else if (names.isEmpty()) "Every week $time"
                else "Every ${names.joinToString(", ")} $time"
            }
            Type.MONTHLY -> "Every month on the ${ordinal(if (dayOfMonth in 1..31) dayOfMonth else c.get(Calendar.DAY_OF_MONTH))} $time"
            Type.YEARLY -> "Every year on ${c.get(Calendar.DAY_OF_MONTH)} ${c.getDisplayName(Calendar.MONTH, Calendar.SHORT, locale)} $time"
        }
    }

    companion object {
        val NONE = Repeat()
        /** Monday-first display order. */
        val ORDER = listOf(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY,
            Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY)

        fun dayName(d: Int, locale: Locale = Locale.getDefault()): String =
            Calendar.getInstance(locale).apply { set(Calendar.DAY_OF_WEEK, d) }.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT, locale) ?: "?"

        fun ordinal(n: Int) = n.toString() + when {
            n % 100 in 11..13 -> "th"; n % 10 == 1 -> "st"; n % 10 == 2 -> "nd"; n % 10 == 3 -> "rd"; else -> "th"
        }

        private fun cal(t: Long) = Calendar.getInstance().apply { timeInMillis = t }

        fun fromJson(o: JSONObject): Repeat {
            val type = runCatching { Type.valueOf(o.optString("type", "NONE")) }.getOrDefault(Type.NONE)
            val arr = o.optJSONArray("days")
            val days = buildSet { if (arr != null) for (i in 0 until arr.length()) arr.optInt(i).takeIf { it in 1..7 }?.let { add(it) } }
            return Repeat(type, days, o.optInt("every", 1).coerceIn(1, 3650), o.optInt("dayOfMonth", 0))
        }
    }
}
