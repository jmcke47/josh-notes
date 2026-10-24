package com.josh.notes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class V12FieldsTest {
    // A note exactly as written by v1.1.x (no reminder / trash fields).
    private val v11 = """{"format":2,"app":"com.josh.notes","notes":[{"id":1761300000000,"title":"11:30 sell tsla stock from Chinese app",
        "body":"","createdAt":1761290000000,"updatedAt":1761300000000,"pinned":false,"color":0,"archived":false,"checklist":false,"items":[]}]}"""

    @Test fun v11NotesGetSafeDefaults() {
        val n = NoteStore.parse(v11).single()
        assertEquals("11:30 sell tsla stock from Chinese app", n.title)
        assertEquals(0L, n.reminderAt); assertFalse(n.reminderFired); assertEquals(0L, n.deletedAt)
        assertFalse(n.inTrash); assertFalse(n.hasReminder)
    }

    @Test fun reminderAndTrashRoundTrip() {
        val n = Note(7, "Remind Jess of room change", "", 2, 1, reminderAt = 1761350000000, reminderFired = true, deletedAt = 1761360000000)
        val back = NoteStore.parse(NoteStore.serialize(listOf(n))).single()
        assertEquals(n, back)
        assertTrue(back.inTrash); assertTrue(back.hasReminder)
    }
}
