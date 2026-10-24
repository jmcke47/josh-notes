package com.josh.notes

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class RepeatTest {
    init { TimeZone.setDefault(TimeZone.getTimeZone("Australia/Brisbane")); Locale.setDefault(Locale.UK) }

    private fun at(y: Int, m: Int, d: Int, h: Int = 9, min: Int = 0) =
        Calendar.getInstance().apply { clear(); set(y, m - 1, d, h, min, 0) }.timeInMillis

    @Test fun daily() {
        val r = Repeat(Repeat.Type.DAILY)
        assertEquals(at(2026, 10, 25), r.next(at(2026, 10, 24), at(2026, 10, 24, 9, 0)))
        // missed several days -> next future occurrence keeps the time of day
        assertEquals(at(2026, 10, 30), r.next(at(2026, 10, 24), at(2026, 10, 29, 12, 0)))
    }

    @Test fun weeklyMonWed() {
        val r = Repeat(Repeat.Type.WEEKLY, setOf(Calendar.MONDAY, Calendar.WEDNESDAY))
        // Sat 24 Oct 2026 picked -> first is Mon 26
        assertEquals(at(2026, 10, 26), r.first(at(2026, 10, 24)))
        assertEquals(at(2026, 10, 28), r.next(at(2026, 10, 26), at(2026, 10, 26, 9, 0)))
        assertEquals(at(2026, 11, 2), r.next(at(2026, 10, 28), at(2026, 10, 28, 9, 0)))
        assertEquals("Every Mon, Wed 9:00", r.describe(at(2026, 10, 26), "9:00", Locale.UK))
    }

    @Test fun monthlyClampsAndReanchors() {
        val r = Repeat(Repeat.Type.MONTHLY, dayOfMonth = 31)
        assertEquals(at(2026, 2, 28), r.next(at(2026, 1, 31), at(2026, 1, 31)))
        assertEquals(at(2026, 3, 31), r.next(at(2026, 2, 28), at(2026, 2, 28)))
        assertEquals("Every month on the 31st 9:00", r.describe(at(2026, 1, 31), "9:00"))
    }

    @Test fun yearlyAndEveryN() {
        assertEquals(at(2027, 10, 24), Repeat(Repeat.Type.YEARLY).next(at(2026, 10, 24), at(2026, 10, 24)))
        assertEquals(at(2026, 10, 27), Repeat(Repeat.Type.EVERY_N_DAYS, every = 3).next(at(2026, 10, 24), at(2026, 10, 24)))
        assertEquals("Every 3 days 9:00", Repeat(Repeat.Type.EVERY_N_DAYS, every = 3).describe(0, "9:00"))
    }

    @Test fun noneAndJsonRoundTrip() {
        assertEquals(0L, Repeat.NONE.next(at(2026, 10, 24), 0))
        val r = Repeat(Repeat.Type.WEEKLY, setOf(2, 4), 1, 0)
        assertEquals(r, Repeat.fromJson(r.toJson()))
        val n = Note(1, "Bins", "", 2, 1, reminderAt = 5, repeat = r, snoozeAt = 9,
            attachments = listOf(Attachment("a1", "a1.jpg", "photo.jpg", "image/jpeg", 1234, "image", createdAt = 3)))
        assertEquals(n, NoteStore.parse(NoteStore.serialize(listOf(n))).single())
    }
}
