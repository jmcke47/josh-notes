package com.josh.notes

import android.content.Context
import android.text.format.DateFormat
import android.text.format.DateUtils

object ReminderFormat {
    /** e.g. "Today 11:30", "Tomorrow 9:00", "Sat 24 Oct, 19:05". */
    fun short(ctx: Context, at: Long): String {
        val time = DateFormat.getTimeFormat(ctx).format(at)
        return when {
            DateUtils.isToday(at) -> "Today $time"
            DateUtils.isToday(at - DateUtils.DAY_IN_MILLIS) -> "Tomorrow $time"
            else -> DateUtils.formatDateTime(ctx, at, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_WEEKDAY or
                    DateUtils.FORMAT_ABBREV_ALL or DateUtils.FORMAT_SHOW_TIME)
        }
    }
}
