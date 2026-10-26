package com.josh.notes

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.view.ContextThemeWrapper
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import com.josh.notes.widget.WidgetActionActivity
import com.josh.notes.widget.WidgetOptions
import com.josh.oldschool.Os
import com.josh.oldschool.OsDialogs
import com.josh.oldschool.OsSkillTile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowLooper
import java.io.File

/** Kit v1.1 "2007-era" redesign: tabs that filter, chat dialogs, Choose Option menus, XP drops, widget tabs. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w393dp-h852dp-xxhdpi", application = NotesApp::class)
class OldSchool11Test {
    private val app get() = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val ctx get() = ContextThemeWrapper(app, R.style.Theme_JoshNotes_OldSchool)
    private val dir = System.getProperty("screens.dir").orEmpty()
    private fun idle() = ShadowLooper.idleMainLooper()

    @Before fun seed() {
        app.getSharedPreferences("settings", 0).edit().putString("theme", "OLDSCHOOL").commit()
        val now = System.currentTimeMillis()
        NoteStore.replaceAll(app, listOf(
            Note(1, "Pinned one", "a", now, pinned = true),
            Note(2, "List", "", now - 1, checklist = true, items = listOf(CheckItem("x", false))),
            Note(3, "Plain", "words here", now - 2)))
    }

    private fun count(a: MainActivity) = a.findViewById<RecyclerView>(R.id.list).adapter!!.itemCount

    @Test fun chatTabsFilterTheList() {
        val a = Robolectric.buildActivity(MainActivity::class.java).setup().get(); idle()
        assertEquals(3, count(a))
        a.findViewById<View>(R.id.tab_pinned).performClick(); idle()
        assertEquals(1, count(a))
        a.findViewById<View>(R.id.tab_lists).performClick(); idle()
        assertEquals(1, count(a))
        a.findViewById<View>(R.id.tab_all).performClick(); idle()
        assertEquals(3, count(a))
        assertTrue(a.findViewById<View>(R.id.tab_all).isSelected)
    }

    @Test fun longPressShowsChooseOption() {
        val a = Robolectric.buildActivity(MainActivity::class.java).setup().get(); idle()
        val dv = a.window.decorView
        dv.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(2340, View.MeasureSpec.EXACTLY))
        dv.layout(0, 0, 1080, 2340); idle()
        assertTrue(a.findViewById<RecyclerView>(R.id.list).getChildAt(0).performLongClick()); idle()
        val d = ShadowDialog.getLatestDialog(); assertNotNull(d)
        assertTrue(texts(d.window!!.decorView).any { it == "Choose Option" })
    }

    @Test fun alertBuilderBecomesChatOrOptions() {
        val one = OsDialogs.alert(ctx).setTitle("Saved").setMessage("Done.").setPositiveButton("OK", null).show(); idle()
        assertTrue(texts(one.window!!.decorView).contains("Click here to continue"))
        one.dismiss()
        var picked = 0
        val two = OsDialogs.alert(ctx).setMessage("Delete it?").setPositiveButton("Delete") { _, w -> picked = w }
            .setNegativeButton("Cancel", null).show(); idle()
        val t = texts(two.window!!.decorView)
        assertTrue(t.contains("Select an Option")); assertTrue(t.contains("Delete"))
        click(two.window!!.decorView, "Delete"); idle()
        assertEquals(android.content.DialogInterface.BUTTON_POSITIVE, picked)
    }

    @Test fun savingQueuesRightSideXpDrops() {
        val n = Note(10, "New", "one two three", System.currentTimeMillis())
        Stats.recordSaveXp(app, null, n)
        val drops = Stats.consumeSaveXp(app)
        assertEquals(listOf(120, 16), drops.map { it.second })
        assertTrue(Stats.consumeSaveXp(app).isEmpty())
    }

    @Test fun widgetChecklistsModeAndTabSwitch() {
        val o = WidgetOptions(WidgetOptions.Mode.CHECKLISTS)
        assertEquals(listOf(2L), o.filter(NoteStore.all(app)).map { it.id })
        assertEquals(o, WidgetOptions.decode(o.encode()))
        val i = Intent().putExtra(WidgetActionActivity.EXTRA_ACTION, WidgetActionActivity.ACTION_MODE)
            .putExtra(WidgetActionActivity.EXTRA_MODE, "PINNED").putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, 7)
        assertTrue(WidgetActionActivity.handle(app, i))
        assertEquals(WidgetOptions.Mode.PINNED, WidgetOptions.get(app, 7).mode)
    }

    /** v1.1 pieces → screens/25-kit-v11-pieces.png */
    @Test fun showcase11() {
        org.junit.Assume.assumeTrue(dir.isNotEmpty())
        val c = ctx; val d = { v: Float -> Os.dpi(c, v) }
        fun header(t: String) = TextView(c).apply { text = t; setTextAppearance(com.josh.oldschool.R.style.TextAppearance_OldSchool_Header); setPadding(0, d(10f), 0, d(4f)) }
        val col = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; setPadding(d(14f), d(10f), d(14f), d(14f)); setBackgroundResource(R.drawable.os_stone_bg) }
        col.addView(header("Side tabs · chat tabs"))
        col.addView(LinearLayout(c).apply {
            setBackgroundResource(com.josh.oldschool.R.drawable.os_side_panel); setPadding(d(8f), d(8f), d(8f), d(8f))
            listOf(R.drawable.os_ic_scroll, com.josh.oldschool.R.drawable.os_ic_stats, com.josh.oldschool.R.drawable.os_ic_quest, R.drawable.os_ic_wrench).forEachIndexed { i, ic ->
                addView(android.widget.ImageButton(c, null, 0, com.josh.oldschool.R.style.Widget_OldSchool_SideTab).apply { setImageResource(ic); isSelected = i == 1 },
                    LinearLayout.LayoutParams(d(50f), d(50f)).apply { marginEnd = d(4f) })
            }
        })
        col.addView(LinearLayout(c).apply {
            listOf("All", "Pinned", "Checklists", "Secret").forEachIndexed { i, t ->
                addView(TextView(c, null, 0, com.josh.oldschool.R.style.Widget_OldSchool_ChatTab).apply { text = t; isSelected = i == 0 },
                    LinearLayout.LayoutParams(0, d(30f), 1f).apply { marginEnd = d(2f) })
            }
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = d(6f) })
        col.addView(header("Stone buttons: normal / pressed"))
        col.addView(LinearLayout(c).apply {
            listOf(false, true).forEach { p -> addView(android.widget.Button(c, null, 0, com.josh.oldschool.R.style.Widget_OldSchool_Button_Stone).apply { text = if (p) "Pressed" else "Normal"; isPressed = p },
                LinearLayout.LayoutParams(0, d(44f), 1f).apply { marginEnd = d(6f) }) }
        })
        col.addView(header("Skills grid tiles"))
        col.addView(LinearLayout(c).apply {
            addView(OsSkillTile(c).apply { set(com.josh.oldschool.R.drawable.os_ic_flame, 12) }, LinearLayout.LayoutParams(0, -2, 1f))
            addView(OsSkillTile(c).apply { set(com.josh.oldschool.R.drawable.os_ic_quest, 5, baseLevel = 7) }, LinearLayout.LayoutParams(0, -2, 1f))
            addView(OsSkillTile(c).apply { setTotal(41) }, LinearLayout.LayoutParams(0, -2, 1f))
        })
        col.addView(header("Chatbox dialogs"))
        col.addView(OsDialogs.chatPanel(c, 0) {
            addView(TextView(c).apply { text = "Backup saved"; typeface = Os.font(c, true); setTextColor(c.getColor(com.josh.oldschool.R.color.os_chat_blue_dark)); gravity = android.view.Gravity.CENTER })
            addView(TextView(c).apply { text = "Click here to continue"; typeface = Os.font(c, true); setTextColor(c.getColor(com.josh.oldschool.R.color.os_dialog_blue)); gravity = android.view.Gravity.CENTER })
        })
        col.addView(header("Carved frame"))
        col.addView(View(c).apply { setBackgroundResource(com.josh.oldschool.R.drawable.os_frame_carved_filled) }, LinearLayout.LayoutParams(-1, d(70f)))
        val w = Os.dpi(c, 393f)
        col.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        col.layout(0, 0, w, col.measuredHeight)
        val bmp = Bitmap.createBitmap(w, col.measuredHeight, Bitmap.Config.ARGB_8888); col.draw(Canvas(bmp))
        File(dir).mkdirs(); File(dir, "25-kit-v11-pieces.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun texts(v: View): List<String> = when (v) {
        is TextView -> listOf(v.text.toString())
        is ViewGroup -> (0 until v.childCount).flatMap { texts(v.getChildAt(it)) }
        else -> emptyList()
    }
    private fun click(v: View, t: String): Boolean = when (v) {
        is TextView -> if (v.text.toString() == t) { v.performClick(); true } else false
        is ViewGroup -> (0 until v.childCount).any { click(v.getChildAt(it), t) }
        else -> false
    }
}
