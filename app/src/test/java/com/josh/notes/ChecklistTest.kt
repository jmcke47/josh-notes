package com.josh.notes

import android.content.Intent
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.test.core.app.ApplicationProvider
import com.josh.notes.widget.NotesWidgetService
import com.josh.notes.widget.WidgetActionActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = NotesApp::class)
class ChecklistTest {
    private val ctx get() = ApplicationProvider.getApplicationContext<android.app.Application>()
    private fun items(vararg s: Pair<String, Boolean>) = s.map { CheckItem(it.first, it.second) }

    @Before fun seed() {
        NoteStore.replaceAll(ctx, listOf(
            Note(10, "Trip", "", 1000L, checklist = true, quest = true, items = items("Passport" to true, "Tickets" to false, "Charger" to false)),
            Note(11, "Plain", "body", 900L)))
        NoteStore.all(ctx).filter { it.secret }.forEach { NoteStore.deleteForever(ctx, it.id) }
        ctx.getSharedPreferences("settings", 0).edit().remove("pending_quest").putString("theme", "LIGHT").commit()
        ctx.getSharedPreferences("stats", 0).edit().clear().commit()
    }

    @Test fun progressAndMove() {
        val n = NoteStore.get(ctx, 10)!!
        assertEquals(1 to 3, n.progress)
        assertFalse(n.isComplete)
        val moved = Checklists.move(n.items, 2, 0)
        assertEquals(listOf("Charger", "Passport", "Tickets"), moved.map { it.text })
        assertEquals(n.items, Checklists.move(n.items, 5, 0))
    }

    @Test fun toggleFromWidgetCompletesQuest() {
        val before = Stats.checklistsDone(ctx)
        var t = Checklists.toggle(ctx, 10, 1, "Tickets")!!
        assertTrue(t.note.items[1].checked); assertFalse(t.completedNow)
        // Stale widget: index 0 drawn with "Charger" text -> resolved by text.
        t = Checklists.toggle(ctx, 10, 0, "Charger")!!
        assertTrue(t.completedNow)
        assertTrue(NoteStore.get(ctx, 10)!!.isComplete)
        assertEquals(before + 1, Stats.checklistsDone(ctx))
        assertEquals(10L, Quests.consumePending(ctx))
        // Untick again via the widget trampoline.
        assertTrue(WidgetActionActivity.handle(ctx, Intent()
            .putExtra(WidgetActionActivity.EXTRA_ACTION, WidgetActionActivity.ACTION_TOGGLE)
            .putExtra(EditNoteActivity.EXTRA_NOTE_ID, 10L).putExtra(WidgetActionActivity.EXTRA_INDEX, 2).putExtra(WidgetActionActivity.EXTRA_TEXT, "Charger")))
        assertFalse(NoteStore.get(ctx, 10)!!.items[2].checked)
        // Open action isn't handled in place.
        assertFalse(WidgetActionActivity.handle(ctx, Intent().putExtra(EditNoteActivity.EXTRA_NOTE_ID, 10L)
            .putExtra(WidgetActionActivity.EXTRA_ACTION, WidgetActionActivity.ACTION_OPEN)))
        // Missing note / non-checklist.
        assertNull(Checklists.toggle(ctx, 99, 0, null))
        assertNull(Checklists.toggle(ctx, 11, 0, null))
    }

    @Test fun widgetRowHasTickableItems() {
        val f = NotesWidgetService.Factory(ctx, 3).apply { onDataSetChanged() }
        val idx = f.rows.indexOfFirst { it is NotesWidgetService.Row.NoteRow && it.note.id == 10L }
        val v = f.getViewAt(idx).apply(ctx, FrameLayout(ctx))
        assertEquals(2, v.findViewById<LinearLayout>(R.id.widget_item_checks).childCount) // the two open items
        assertTrue(v.findViewById<android.widget.TextView>(R.id.widget_item_title).text.endsWith("1/3"))
        // Re-applying (recycled row) must not duplicate the item rows.
        f.getViewAt(idx).reapply(ctx, v)
        assertEquals(2, v.findViewById<LinearLayout>(R.id.widget_item_checks).childCount)
    }

    @Test fun streaksAndSounds() {
        val cal = java.util.Calendar.getInstance().apply { set(2026, 9, 25) }
        assertEquals(3 to 4, Stats.streaks(setOf("20261023", "20261024", "20261025", "20261001", "20261002", "20261003", "20261004"), cal))
        assertEquals(0 to 1, Stats.streaks(setOf("20261020"), cal))
        for (k in SoundFx.Kind.entries) {
            val pcm = SoundFx.synth(k)
            assertTrue(pcm.size in 10_000..60_000)
            assertTrue(pcm.any { it > 3000 })
        }
        assertTrue(Stats.skills(ctx).all { it.level in 1..99 })
    }
}
