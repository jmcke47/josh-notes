package com.josh.notes

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RemoteViews
import android.widget.SeekBar
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.josh.oldschool.Os
import com.josh.oldschool.OsAnswerButton
import com.josh.oldschool.OsDialogs
import com.josh.oldschool.OsInventorySlot
import com.josh.oldschool.OsLessonCard
import com.josh.oldschool.OsRemote
import com.josh.oldschool.OsSkillTile
import com.josh.oldschool.OsTimerOrb
import com.josh.oldschool.OsXp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** The shared Old School kit (oldschool-theme module): behaviour + a showcase render (when -PscreensDir is given). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w393dp-h852dp-xxhdpi", application = NotesApp::class)
class OldSchoolKitTest {
    private val app get() = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val ctx get() = ContextThemeWrapper(app, R.style.Theme_JoshNotes_OldSchool)
    private val dir = System.getProperty("screens.dir").orEmpty()

    @Test fun versionMatchesFile() {
        val f = listOf(File("../oldschool-theme/VERSION"), File("oldschool-theme/VERSION")).first { it.exists() }
        assertEquals(f.readText().trim(), Os.VERSION)
    }

    @Test fun xpCurveIsTheKitCurve() {
        assertEquals(83, OsXp.xpForLevel(2)); assertEquals(13_034_431, OsXp.xpForLevel(99))
        assertEquals(Levels.xpForLevel(50), OsXp.xpForLevel(50))
        assertEquals(0f, OsXp.fraction(OsXp.xpForLevel(10)), 1e-6f)
    }

    @Test fun answerButtonStates() {
        val b = OsAnswerButton(ctx)
        assertFalse(b.drawableState.contains(com.josh.oldschool.R.attr.os_state_correct))
        b.result = OsAnswerButton.Result.CORRECT
        assertTrue(b.drawableState.contains(com.josh.oldschool.R.attr.os_state_correct))
        assertFalse(b.drawableState.contains(com.josh.oldschool.R.attr.os_state_wrong))
        b.result = OsAnswerButton.Result.WRONG
        assertTrue(b.drawableState.contains(com.josh.oldschool.R.attr.os_state_wrong))
        assertEquals(app.getColor(R.color.os_white), b.currentTextColor)
        b.result = OsAnswerButton.Result.NONE
        assertEquals(app.getColor(R.color.os_orange), b.currentTextColor)
    }

    @Test fun timerDrainsAndChangesColour() {
        val t = OsTimerOrb(ctx)
        t.setRemaining(1f, 30_000); assertEquals(30, t.secondsLeft); assertEquals(app.getColor(R.color.os_timer_ok), t.ringColor())
        t.setRemaining(0.4f); assertEquals(12, t.secondsLeft); assertEquals(app.getColor(R.color.os_timer_warn), t.ringColor())
        t.setRemaining(0.1f); assertEquals(app.getColor(R.color.os_timer_low), t.ringColor())
        var done = false
        t.start(2_000) { done = true }
        org.robolectric.shadows.ShadowLooper.idleMainLooper(2_100, java.util.concurrent.TimeUnit.MILLISECONDS)
        assertTrue(done); assertEquals(0, t.secondsLeft)
    }

    @Test fun inventorySlotEmojiLabelCount() {
        val s = OsInventorySlot(ctx).apply { emoji = "🦀"; label = "Crabs"; count = 250_000 }
        assertEquals("🦀", s.emojiView.text.toString()); assertEquals("Crabs", s.labelView.text.toString())
        assertEquals("250K", s.countView.text.toString()); assertEquals("Crabs 🦀", s.contentDescription)
        assertEquals("99999", OsInventorySlot.stackText(99_999)); assertEquals("12M", OsInventorySlot.stackText(12_000_000))
        s.count = 1; assertEquals(View.GONE, s.countView.visibility)
    }

    @Test fun remoteViewsLayoutsInflate() {
        val pkg = app.packageName
        for (l in listOf(R.layout.os_rv_frame, R.layout.os_rv_check_row, R.layout.os_rv_inventory_slot, R.layout.os_rv_skill_tile,
            R.layout.os_rv_progress, R.layout.os_rv_progress_xp, R.layout.os_rv_banner, R.layout.os_rv_answer)) {
            val rv = RemoteViews(pkg, l)
            when (l) {
                R.layout.os_rv_inventory_slot -> OsRemote.slot(app, rv, "📘", "Book", 3, 150)
                R.layout.os_rv_skill_tile -> OsRemote.skill(app, rv, R.drawable.os_ic_book, 12, 99, "Lessons", 200)
                R.layout.os_rv_progress -> OsRemote.progress(app, rv, 3, 7, "3/7", 300)
                R.layout.os_rv_answer -> rv.setInt(R.id.os_answer, "setBackgroundResource", OsRemote.answerBackground(OsAnswerButton.Result.CORRECT))
            }
            assertNotNull(rv.apply(app, FrameLayout(app)))
        }
    }

    @Test fun seekBarStyleUsesStoneTrack() {
        val sb = SeekBar(ContextThemeWrapper(ctx, com.josh.oldschool.R.style.Widget_OldSchool_SeekBar), null, 0,
            com.josh.oldschool.R.style.Widget_OldSchool_SeekBar)
        assertTrue(sb.progressDrawable is android.graphics.drawable.LayerDrawable)
        assertNotNull(sb.thumb)
    }

    /** Showcase of the v1.0 kit pieces → screens/22-kit-components.png and 23-kit-widget-pieces.png. */
    @Test fun showcase() {
        org.junit.Assume.assumeTrue(dir.isNotEmpty())
        val c = ctx; val d = { v: Float -> Os.dpi(c, v) }
        fun header(t: String) = TextView(c).apply { text = t; textSize = 15f; setTextColor(c.getColor(R.color.os_orange)); setPadding(0, d(10f), 0, d(4f)) }
        val col = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; setPadding(d(12f), d(8f), d(12f), d(12f)); setBackgroundResource(R.drawable.os_stone_bg) }
        col.addView(header("Lesson card (parchment scroll)"))
        col.addView(OsLessonCard(c).apply {
            addView(TextView(c).apply { setTextAppearance(com.josh.oldschool.R.style.TextAppearance_OldSchool_LessonTitle); text = "Lesson 3: Tides"; gravity = android.view.Gravity.CENTER })
            addView(TextView(c).apply { setTextAppearance(com.josh.oldschool.R.style.TextAppearance_OldSchool_LessonBody)
                text = "Twice a day the sea rises and falls. The Moon's pull makes a bulge of water on the near side of Earth, and another on the far side…" })
        })
        col.addView(header("Quiz answers: normal / pressed / correct / wrong"))
        val grid = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        listOf("A. The Moon" to OsAnswerButton.Result.CORRECT, "B. The wind" to OsAnswerButton.Result.WRONG,
            "C. Earthquakes" to OsAnswerButton.Result.NONE, "D. Rivers" to null).forEach { (t, r) ->
            grid.addView(OsAnswerButton(c).apply { text = t; if (r != null) result = r else { isPressed = true } },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = d(4f) })
        }
        col.addView(grid)
        col.addView(header("Timer orb · inventory slots (emoji + label) · skill tile"))
        col.addView(LinearLayout(c).apply {
            gravity = android.view.Gravity.CENTER_VERTICAL
            addView(OsTimerOrb(c).apply { setRemaining(0.62f, 30_000) }, LinearLayout.LayoutParams(d(64f), d(64f)))
            addView(OsTimerOrb(c).apply { setRemaining(0.15f, 30_000) }, LinearLayout.LayoutParams(d(64f), d(64f)).apply { marginStart = d(6f) })
            listOf(Triple("🌊", "Tides", 0), Triple("🧪", "Atoms", 12), Triple("📜", "Recap", 0)).forEachIndexed { i, (e, l, n) ->
                addView(OsInventorySlot(c).apply { emoji = e; label = l; count = n; isSelected = i == 0 },
                    LinearLayout.LayoutParams(d(60f), d(60f)).apply { marginStart = d(6f) })
            }
        })
        col.addView(OsSkillTile(c).apply { set(R.drawable.os_ic_book, 42, "Lessons", 99) },
            LinearLayout.LayoutParams(d(170f), ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = d(6f) })
        col.addView(header("Depth slider · bars · tabs · checkbox"))
        col.addView(SeekBar(ContextThemeWrapper(c, com.josh.oldschool.R.style.Widget_OldSchool_SeekBar), null, 0,
            com.josh.oldschool.R.style.Widget_OldSchool_SeekBar).apply { max = 5; progress = 3 },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, d(28f)))
        col.addView(android.widget.ProgressBar(c, null, 0, com.josh.oldschool.R.style.Widget_OldSchool_ProgressBar).apply { max = 10; progress = 7 },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, d(12f)).apply { topMargin = d(6f) })
        col.addView(android.widget.ProgressBar(c, null, 0, com.josh.oldschool.R.style.Widget_OldSchool_ProgressBar_Xp).apply { max = 10; progress = 4 },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, d(12f)).apply { topMargin = d(6f) })
        col.addView(LinearLayout(c).apply {
            listOf("Notes", "Quiz", "Stats").forEachIndexed { i, t ->
                addView(TextView(ContextThemeWrapper(c, com.josh.oldschool.R.style.Widget_OldSchool_Tab), null, 0, com.josh.oldschool.R.style.Widget_OldSchool_Tab)
                    .apply { text = t; isSelected = i == 1 }, LinearLayout.LayoutParams(0, d(40f), 1f))
            }
            addView(android.widget.CheckBox(c, null, 0, com.josh.oldschool.R.style.Widget_OldSchool_CheckBox).apply { text = "Done"; isChecked = true },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = d(8f) })
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = d(8f) })
        render(col, "22-kit-components")

        // Context menu + RemoteViews pieces.
        val col2 = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; setPadding(d(12f), d(8f), d(12f), d(12f)); setBackgroundResource(R.drawable.os_stone_bg) }
        col2.addView(header("Context menu"))
        col2.addView(OsDialogs.contextMenuView(c, listOf("Open", "Share", "Delete"), target = "photo.jpg") {},
            LinearLayout.LayoutParams(d(220f), ViewGroup.LayoutParams.WRAP_CONTENT))
        col2.addView(header("Widget (RemoteViews) pieces"))
        val pkg = app.packageName
        fun rv(l: Int, fill: (RemoteViews) -> Unit, w: Float = 360f) =
            col2.addView(RemoteViews(pkg, l).also(fill).apply(c, col2), LinearLayout.LayoutParams(d(w), ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = d(6f) })
        rv(R.layout.os_rv_banner, {
            OsRemote.pixelText(app, it, R.id.os_banner_line1, "Daily lesson ready", 15f, app.getColor(R.color.os_chat_blue_dark), d(280f), shadow = false)
            OsRemote.pixelText(app, it, R.id.os_banner_line2, "Tides · 5 questions", 13f, app.getColor(R.color.os_ink), d(280f), shadow = false)
        })
        rv(R.layout.os_rv_progress, { OsRemote.progress(app, it, 3, 7, "3/7", d(200f)) })
        rv(R.layout.os_rv_progress_xp, { OsRemote.progress(app, it, 40, 100, "1,154 xp", d(200f)) })
        rv(R.layout.os_rv_skill_tile, { OsRemote.skill(app, it, R.drawable.os_ic_flame, 7, 99, "Streak", d(140f)) }, 170f)
        rv(R.layout.os_rv_inventory_slot, { OsRemote.slot(app, it, "🧭", "Maps", 4, d(54f)) }, 60f)
        rv(R.layout.os_rv_answer, {
            it.setInt(R.id.os_answer, "setBackgroundResource", OsRemote.answerBackground(OsAnswerButton.Result.WRONG))
            OsRemote.pixelText(app, it, R.id.os_answer_text, "B. The wind", 16f, app.getColor(R.color.os_white), d(300f))
        })
        rv(R.layout.os_rv_check_row, {
            it.setImageViewResource(R.id.os_rv_check_box, R.drawable.os_ic_check_on)
            OsRemote.pixelText(app, it, R.id.os_rv_check_text, "Read chapter 2", 13f, app.getColor(R.color.os_chat_blue), d(300f), shadow = false)
        })
        render(col2, "23-kit-widget-pieces")
    }

    private fun render(v: View, name: String) {
        val w = Os.dpi(ctx, 393f)
        v.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        v.layout(0, 0, w, v.measuredHeight)
        val bmp = Bitmap.createBitmap(w, v.measuredHeight, Bitmap.Config.ARGB_8888)
        v.draw(Canvas(bmp))
        File(dir).mkdirs(); File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
