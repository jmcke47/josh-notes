package com.josh.notes

import android.content.Context
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.josh.notes.widget.NotesWidgetProvider
import com.josh.notes.widget.NotesWidgetService
import com.josh.notes.widget.QuickWidgetProvider
import com.josh.notes.widget.WidgetConfigActivity
import com.josh.notes.widget.WidgetOptions
import com.josh.notes.widget.WidgetOptions.Mode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = NotesApp::class)
class WidgetOptionsTest {
    private val ctx get() = ApplicationProvider.getApplicationContext<android.app.Application>()

    @Before fun seed() {
        NoteStore.replaceAll(ctx, listOf(
            Note(1, "Groceries", "", 5000L, checklist = true, items = listOf(CheckItem("Milk", false), CheckItem("Eggs", true), CheckItem("", false))),
            Note(2, "Gym", "legs day #fitness", 4000L, pinned = true),
            Note(3, "Work", "see https://example.com/a#frag and #Work-items", 3000L),
            Note(4, "Old", "#fitness", 2000L, archived = true),
            Note(5, "Binned", "#fitness", 1000L, deletedAt = 1500L)))
        NoteStore.all(ctx).filter { it.secret }.forEach { NoteStore.deleteForever(ctx, it.id) }
        ctx.getSharedPreferences("widget", 0).edit().clear().commit()
        ctx.getSharedPreferences("settings", 0).edit().putString("theme", "LIGHT").putBoolean("app_lock", false).commit()
    }

    @Test fun encodeStoreAndTags() {
        val o = WidgetOptions(Mode.TAG, tag = "fitness")
        assertEquals(o, WidgetOptions.decode(o.encode()))
        assertEquals(WidgetOptions(), WidgetOptions.decode("garbage"))
        WidgetOptions.set(ctx, 11, o); WidgetOptions.set(ctx, 12, WidgetOptions(Mode.CHECKLIST, noteId = 1))
        assertEquals(o, WidgetOptions.get(ctx, 11))
        assertEquals(Mode.CHECKLIST, WidgetOptions.get(ctx, 12).mode)
        WidgetOptions.remove(ctx, 11)
        assertEquals(WidgetOptions(), WidgetOptions.get(ctx, 11))
        assertEquals(setOf("work-items"), NoteStore.get(ctx, 3)!!.tags) // URL fragment isn't a tag
        assertEquals(listOf("fitness", "work-items"), WidgetOptions.allTags(ctx))
        assertEquals(listOf(1L), WidgetOptions.checklists(ctx).map { it.id })
    }

    @Test fun filtersPerMode() {
        val all = NoteStore.all(ctx)
        assertEquals(listOf(2L, 1L, 3L), WidgetOptions(Mode.ALL).filter(all).map { it.id })
        assertEquals(listOf(2L), WidgetOptions(Mode.PINNED).filter(all).map { it.id })
        assertEquals(listOf(2L), WidgetOptions(Mode.TAG, tag = "fitness").filter(all).map { it.id })
        WidgetOptions.set(ctx, 5, WidgetOptions(Mode.CHECKLIST, noteId = 1))
        val rows = NotesWidgetService.load(ctx, 5)
        assertEquals(2, rows.size) // blank item skipped
        assertTrue(rows.all { it is NotesWidgetService.Row.ItemRow })
        // App lock hides checklist items from the home screen.
        ctx.getSharedPreferences("settings", 0).edit().putBoolean("app_lock", true).commit()
        assertTrue(NotesWidgetService.load(ctx, 5).isEmpty())
    }

    private fun title(root: android.view.View, theme: String): String = root.findViewById<TextView>(
        if (theme == "DARK") R.id.widget_title_dark else if (theme == "LIGHT") R.id.widget_title_light else R.id.widget_title).text.toString()

    @Test fun headerFollowsOptionsAndThemeOnReapply() {
        val host = ctx.createPackageContext(ctx.packageName, Context.CONTEXT_RESTRICTED)
        val root = NotesWidgetProvider.buildViews(ctx, 21, withList = false).apply(host, FrameLayout(host))
        assertEquals("Josh Notes", title(root, "LIGHT"))
        val steps = listOf(
            Triple("DARK", WidgetOptions(Mode.PINNED), "Pinned"),
            Triple("OLDSCHOOL", WidgetOptions(Mode.TAG, tag = "fitness"), null),
            Triple("SYSTEM", WidgetOptions(Mode.CHECKLIST, noteId = 1), "Groceries  1/2"),
            Triple("LIGHT", WidgetOptions(Mode.ALL), "Josh Notes"))
        for ((theme, opts, label) in steps) {
            ctx.getSharedPreferences("settings", 0).edit().putString("theme", theme).commit()
            WidgetOptions.set(ctx, 21, opts)
            NotesWidgetProvider.buildViews(ctx, 21, withList = false).reapply(host, root)
            val img = root.findViewById<android.view.View>(R.id.widget_title_img)
            if (label == null) {
                assertEquals(android.view.View.VISIBLE, img.visibility)
                assertEquals("#fitness", img.contentDescription)
            } else {
                assertEquals(android.view.View.GONE, img.visibility)
                assertEquals(label, title(root, theme))
            }
        }
        // Quick widget renders in every theme in a launcher-like context.
        for (t in ThemeMode.entries) {
            ctx.getSharedPreferences("settings", 0).edit().putString("theme", t.name).commit()
            val q = QuickWidgetProvider.buildViews(ctx, 22).apply(host, FrameLayout(host))
            assertTrue(q.findViewById<android.view.View>(R.id.quick_note) != null)
        }
    }

    @Test fun configureScreenSavesPerWidget() {
        val ac = Robolectric.buildActivity(WidgetConfigActivity::class.java,
            android.content.Intent().putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, 33)).setup()
        val a = ac.get()
        a.findViewById<android.widget.RadioButton>(R.id.mode_checklist).performClick()
        a.findViewById<android.view.View>(R.id.config_save).performClick()
        assertEquals(WidgetOptions(Mode.CHECKLIST, noteId = 1), WidgetOptions.get(ctx, 33))
        assertEquals(android.app.Activity.RESULT_OK, shadowOf(a).resultCode)
        assertEquals(WidgetOptions(), WidgetOptions.get(ctx, 34))
    }
}
