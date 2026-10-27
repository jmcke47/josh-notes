package com.josh.notes

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Parcel
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ListView
import android.widget.RemoteViews
import androidx.test.core.app.ApplicationProvider
import com.josh.notes.widget.NotesWidgetProvider
import com.josh.notes.widget.NotesWidgetService
import com.josh.notes.widget.WidgetActionActivity
import com.josh.notes.widget.WidgetOptions
import com.josh.notes.widget.WidgetTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The list widget as the launcher receives it (built, parcelled, un-parcelled, applied in a restricted context):
 * for every theme and filter, row i shows row i's own text/bitmaps and its tap opens (or ticks) row i's note.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w393dp-h852dp-xxhdpi", application = NotesApp::class)
class WidgetRowsTest {
    private val app get() = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val WID = 7

    @Before fun seed() {
        val now = System.currentTimeMillis(); val h = 3_600_000L
        NoteStore.replaceAll(app, listOf(
            Note(1, "Alpha pinned", "First body line #home", now, pinned = true),
            Note(2, "Bravo", "Second note body that is long enough to wrap onto two lines in the widget row easily", now - h),
            Note(3, "Charlie list", "", now - 2 * h, checklist = true, items = listOf(CheckItem("one", false), CheckItem("two", true), CheckItem("three", false))),
            Note(4, "", "Delta untitled first line\nDelta second line", now - 3 * h),
            Note(5, "Echo reminder", "Echo body", now - 4 * h, reminderAt = now + 5 * h, pinned = true),
            Note(6, "Foxtrot list", "", now - 5 * h, checklist = true, items = listOf(CheckItem("buy", false))),
            Note(7, "Golf", "Golf body #home", now - 6 * h, color = 2)))
        WidgetTheme.saveWidth(app, WID, 300)
    }

    private fun parcelled(rv: RemoteViews): RemoteViews {
        val p = Parcel.obtain()
        try { rv.writeToParcel(p, 0); p.setDataPosition(0); return RemoteViews.CREATOR.createFromParcel(p) } finally { p.recycle() }
    }

    private fun bitmaps(v: View): List<Bitmap> = when {
        v is ImageView && v.visibility == View.VISIBLE -> listOfNotNull((v.drawable as? BitmapDrawable)?.bitmap)
        v is ViewGroup && v.visibility != View.GONE -> (0 until v.childCount).flatMap { bitmaps(v.getChildAt(it)) }
        else -> emptyList()
    }
    private fun texts(v: View): List<String> = when {
        v is android.widget.TextView && v.visibility == View.VISIBLE -> listOf(v.text.toString())
        v is ViewGroup && v.visibility != View.GONE -> (0 until v.childCount).flatMap { texts(v.getChildAt(it)) }
        else -> emptyList()
    }

    /** Taps the row like the launcher does; returns the activity intent it launched (template + fill-in). */
    private fun tap(list: ListView, row: View, target: View, i: Int): android.content.Intent? {
        if (target.hasOnClickListeners()) target.performClick() else list.performItemClick(row, i, list.adapter.getItemId(i))
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        return org.robolectric.Shadows.shadowOf(app).nextStartedActivity
    }

    private fun check(theme: String, opts: WidgetOptions) {
        app.getSharedPreferences("settings", 0).edit().putString("theme", theme).commit()
        WidgetOptions.set(app, WID, opts)
        val host = app.createPackageContext(app.packageName, Context.CONTEXT_RESTRICTED)
        val hv = android.appwidget.AppWidgetHostView(host)
        val root = parcelled(NotesWidgetProvider.buildViews(app, WID)).apply(host, hv).also { hv.addView(it) }
        val list = root.findViewById<ListView>(R.id.widget_list)
        val expected = NotesWidgetService.load(app, WID)
        val label = "$theme/${opts.mode}"
        assertEquals("$label row count", expected.size, list.adapter.count)
        list.measure(View.MeasureSpec.makeMeasureSpec(900, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(20000, View.MeasureSpec.EXACTLY))
        list.layout(0, 0, 900, 20000)
        assertEquals("$label all rows laid out", expected.size, list.childCount)
        // Reference: each row built on its own, never parcelled with anything else.
        val ref = NotesWidgetService.Factory(app, WID).apply { onDataSetChanged() }
        for (i in expected.indices) {
            val row = list.getChildAt(i)
            val r = expected[i]
            assertEquals("$label row $i id", NotesWidgetService.rowId(r), list.adapter.getItemId(i))
            val alone = ref.getViewAt(i).apply(host, FrameLayout(host))
            val got = bitmaps(row); val want = bitmaps(alone)
            assertEquals("$label row $i bitmap count", want.size, got.size)
            for (k in want.indices) assertTrue("$label row $i bitmap $k shows another row's text", want[k].sameAs(got[k]))
            assertEquals("$label row $i texts", texts(alone), texts(row))
            // Tap -> fill-in for this same note.
            val clickId = if (r is NotesWidgetService.Row.ItemRow) R.id.widget_check_row else R.id.widget_item
            val target = row.findViewById<View>(clickId) ?: row.takeIf { it.id == clickId }
            assertNotNull("$label row $i clickable view", target)
            val started = tap(list, row, target!!, i)
            assertNotNull("$label row $i tap launches", started); started!!
            assertNotNull("$label row $i click", started)
            val noteId = when (r) { is NotesWidgetService.Row.NoteRow -> r.note.id; is NotesWidgetService.Row.ItemRow -> r.note.id }
            assertEquals("$label row $i opens its own note", noteId, started.getLongExtra(EditNoteActivity.EXTRA_NOTE_ID, -1))
            if (r is NotesWidgetService.Row.ItemRow) assertEquals(r.index, started.getIntExtra(WidgetActionActivity.EXTRA_INDEX, -1))
            else assertEquals(WidgetActionActivity.ACTION_OPEN, started.getStringExtra(WidgetActionActivity.EXTRA_ACTION))
        }
    }

    @Test fun rowsMatchTheirNoteForEveryThemeAndFilter() {
        val filters = listOf(WidgetOptions(WidgetOptions.Mode.ALL), WidgetOptions(WidgetOptions.Mode.PINNED),
            WidgetOptions(WidgetOptions.Mode.CHECKLISTS), WidgetOptions(WidgetOptions.Mode.TAG, tag = "home"),
            WidgetOptions(WidgetOptions.Mode.CHECKLIST, noteId = 3))
        for (t in ThemeMode.entries) for (o in filters) check(t.name, o)
    }

    /** Switching tabs rebuilds the whole list (full update), so nothing from the previous filter survives. */
    @Test fun filterChangeReplacesAllRows() {
        app.getSharedPreferences("settings", 0).edit().putString("theme", "OLDSCHOOL").commit()
        val host = app.createPackageContext(app.packageName, Context.CONTEXT_RESTRICTED)
        WidgetOptions.set(app, WID, WidgetOptions(WidgetOptions.Mode.ALL))
        val hv = android.appwidget.AppWidgetHostView(host)
        val root = parcelled(NotesWidgetProvider.buildViews(app, WID)).apply(host, hv).also { hv.addView(it) }
        assertEquals(7, root.findViewById<ListView>(R.id.widget_list).adapter.count)
        WidgetOptions.set(app, WID, WidgetOptions(WidgetOptions.Mode.PINNED))
        parcelled(NotesWidgetProvider.buildViews(app, WID)).reapply(host, root)
        val list = root.findViewById<ListView>(R.id.widget_list)
        assertEquals(2, list.adapter.count)
        assertEquals(listOf(1L, 5L), (0 until 2).map { list.adapter.getItemId(it) })
    }
}
