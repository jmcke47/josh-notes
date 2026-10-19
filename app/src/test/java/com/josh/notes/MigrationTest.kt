package com.josh.notes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MigrationTest {
    private val v1 = """[{"id":1760870000000,"title":"Shopping","body":"milk\neggs","updatedAt":1760870500000},
        {"id":1760871000000,"title":"","body":"just body","updatedAt":1760871000000}]"""

    @Test fun readsV1Array() {
        val notes = NoteStore.parse(v1)
        assertEquals(2, notes.size)
        val a = notes[0]
        assertEquals("Shopping", a.title); assertEquals("milk\neggs", a.body)
        assertEquals(1760870000000, a.createdAt); assertEquals(1760870500000, a.updatedAt)
        assertFalse(a.pinned); assertFalse(a.archived); assertFalse(a.checklist); assertEquals(0, a.color)
        assertEquals("just body", notes[1].displayTitle)
    }

    @Test fun v2RoundTrip() {
        val n = Note(5, "T", "", 200, 100, pinned = true, color = 3, archived = true, checklist = true,
            items = listOf(CheckItem("a", true), CheckItem("b", false)))
        val back = NoteStore.parse(NoteStore.serialize(listOf(n)))
        assertEquals(listOf(n), back)
        assertEquals("☑ a\n☐ b", back[0].contentText)
    }

    @Test fun v1ThenV2PreservesEverything() {
        val migrated = NoteStore.parse(NoteStore.serialize(NoteStore.parse(v1)))
        assertEquals(NoteStore.parse(v1), migrated)
    }

    @Test fun pinnedFirstSorting() {
        val list = listOf(Note(1, "b", "", 10, 1), Note(2, "a", "", 5, 2, pinned = true), Note(3, "c", "", 20, 3))
        assertEquals(listOf(2L, 3L, 1L), list.sortedFor(SortMode.EDITED).map { it.id })
        assertEquals(listOf(2L, 1L, 3L), list.sortedFor(SortMode.TITLE).map { it.id })
        assertEquals(listOf(2L, 3L, 1L), list.sortedFor(SortMode.CREATED).map { it.id })
        assertTrue(list.sortedFor(SortMode.EDITED).first().pinned)
    }
}
