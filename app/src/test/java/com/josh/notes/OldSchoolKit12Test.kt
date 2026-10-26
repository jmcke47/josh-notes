package com.josh.notes

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RemoteViews
import android.widget.TextView
import androidx.appcompat.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.josh.oldschool.Os
import com.josh.oldschool.OsAnimations
import com.josh.oldschool.OsBanner
import com.josh.oldschool.OsChat
import com.josh.oldschool.OsChips
import com.josh.oldschool.OsRemote
import com.josh.oldschool.OsTimerOrb
import com.josh.oldschool.OsXpDrop
import com.josh.oldschool.OsXpDrops
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.io.File

/** Kit v1.2 additions requested by Josh Learn. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w393dp-h852dp-xxhdpi", application = NotesApp::class)
class OldSchoolKit12Test {
    private val app get() = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val ctx get() = ContextThemeWrapper(app, R.style.Theme_JoshNotes_OldSchool)
    private val dir = System.getProperty("screens.dir").orEmpty()
    private val K = com.josh.oldschool.R::class.java

    @After fun tidy() = OsAnimations.reset()

    private fun apply(rv: RemoteViews, widthPx: Int): View {
        val parent = FrameLayout(ctx)
        val v = rv.apply(ctx, parent)
        parent.addView(v)
        parent.measure(View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        parent.layout(0, 0, widthPx, parent.measuredHeight)
        return v
    }
    private fun bmpOf(v: View, id: Int) = (v.findViewById<ImageView>(id).drawable as BitmapDrawable).bitmap

    // 1. two-line slot labels
    @Test fun slotLabelWrapsToTwoLines() {
        val w = Os.dpi(ctx, 60f)
        val one = apply(RemoteViews(app.packageName, com.josh.oldschool.R.layout.os_rv_inventory_slot).also {
            OsRemote.slot(app, it, "🌊", "Ocean currents", widthPx = w) }, w)
        val two = apply(RemoteViews(app.packageName, com.josh.oldschool.R.layout.os_rv_inventory_slot).also {
            OsRemote.slot(app, it, "🌊", "Ocean currents", widthPx = w, labelMaxLines = 2) }, w)
        val b1 = bmpOf(one, com.josh.oldschool.R.id.os_slot_label); val b2 = bmpOf(two, com.josh.oldschool.R.id.os_slot_label)
        assertTrue("2-line label is taller (${b1.height} vs ${b2.height})", b2.height > b1.height * 1.4f)
        assertTrue(b2.width <= w)
    }

    // 2. stretchable slot fills its column
    @Test fun wideSlotFillsColumn() {
        val w = Os.dpi(ctx, 120f)
        val v = apply(RemoteViews(app.packageName, com.josh.oldschool.R.layout.os_rv_inventory_slot_wide).also {
            OsRemote.slot(app, it, "🧪", "Atoms and molecules", 3, widthPx = w, labelMaxLines = 2) }, w)
        assertEquals(w, v.width)
        assertTrue(v.height >= Os.dpi(ctx, 56f) - 1)
        assertEquals(View.VISIBLE, v.findViewById<View>(com.josh.oldschool.R.id.os_slot_count).visibility)
    }

    // 3. chat line helper
    @Test fun chatLineColoursAndRemote() {
        val t = OsChat.text(ctx, "Guide", "Welcome back!") as Spanned
        assertEquals("Guide: Welcome back!", t.toString())
        val spans = t.getSpans(0, t.length, ForegroundColorSpan::class.java)
        assertEquals(listOf(Color.BLACK, ctx.getColor(com.josh.oldschool.R.color.os_chat_blue)), spans.map { it.foregroundColor })
        val tv = OsChat.view(ctx, "Guide", "Welcome back!", speakerColor = ctx.getColor(com.josh.oldschool.R.color.os_chat_red))
        assertEquals(0f, tv.shadowRadius, 0f); assertNotNull(tv.background)
        val w = Os.dpi(ctx, 300f)
        val v = apply(RemoteViews(app.packageName, com.josh.oldschool.R.layout.os_rv_chat_line).also {
            OsRemote.chatLine(app, it, "Guide", "Today's lesson is about tides and the Moon, and it has five questions.", w) }, w)
        val b = bmpOf(v, com.josh.oldschool.R.id.os_chat_line_text)
        assertTrue(b.width <= w)
        // speaker (black) and message (blue) pixels both present
        var black = false; var blue = false
        for (x in 0 until b.width step 1) for (y in 0 until b.height step 1) {
            val p = b.getPixel(x, y); if (Color.alpha(p) < 200) continue
            if (Color.red(p) < 40 && Color.blue(p) < 40) black = true
            if (Color.blue(p) > 200 && Color.red(p) < 60) blue = true
        }
        assertTrue(black && blue)
    }

    // 4. parchment
    @Test fun parchmentizeAndOverlay() {
        val box = LinearLayout(ctx).apply {
            addView(TextView(ctx).apply { text = "Title"; setTextColor(ctx.getColor(com.josh.oldschool.R.color.os_yellow)); setShadowLayer(1f, 3f, 3f, Color.BLACK) })
            addView(TextView(ctx).apply { text = "link"; setTextColor(ctx.getColor(com.josh.oldschool.R.color.os_orange)) })
            addView(Button(ctx).apply { text = "Next" })
        }
        Os.parchmentize(box)
        val (t, l, b) = (0..2).map { box.getChildAt(it) as TextView }
        assertEquals(Color.BLACK, t.currentTextColor); assertEquals(0f, t.shadowRadius, 0f)
        assertEquals(ctx.getColor(com.josh.oldschool.R.color.os_link_ink), l.currentTextColor)
        assertEquals(ctx.getColor(com.josh.oldschool.R.color.os_link_ink), b.currentTextColor); assertEquals(0f, b.shadowRadius, 0f)
        // overlay: buttons inflated under it are link-ink with no shadow
        val pc = ContextThemeWrapper(ctx, com.josh.oldschool.R.style.ThemeOverlay_OldSchool_Parchment)
        val mb = com.google.android.material.button.MaterialButton(pc)
        assertEquals(ctx.getColor(com.josh.oldschool.R.color.os_link_ink), mb.currentTextColor)
        assertEquals(0f, mb.shadowRadius, 0f)
    }

    private fun nav(style: Int): BottomNavigationView {
        val c = ContextThemeWrapper(ctx, 0)
        val nav = if (style == com.josh.oldschool.R.style.Widget_OldSchool_BottomNav) com.josh.oldschool.OsBottomNav(c)
            else com.josh.oldschool.OsBottomNav.style(BottomNavigationView(c, null, 0, style), labeled = true)
        return nav.apply {
            listOf(R.drawable.os_ic_scroll, com.josh.oldschool.R.drawable.os_ic_stats, com.josh.oldschool.R.drawable.os_ic_quest, R.drawable.os_ic_wrench)
                .forEachIndexed { i, ic -> menu.add(0, 100 + i, i, listOf("Notes", "Stats", "Quests", "Settings")[i]).setIcon(ic) }
            selectedItemId = 101
        }
    }

    // 5. bottom nav
    @Test fun bottomNavMatchesSideTabs() {
        val n = nav(com.josh.oldschool.R.style.Widget_OldSchool_BottomNav)
        assertNull("pixel icons untinted", n.itemIconTintList)
        assertNotNull(n.itemBackground)
        assertFalse(n.isItemActiveIndicatorEnabled)
        assertEquals(BottomNavigationView.LABEL_VISIBILITY_UNLABELED, n.labelVisibilityMode)
        assertEquals(Os.dpi(ctx, 32f), n.itemIconSize)
        val l = nav(com.josh.oldschool.R.style.Widget_OldSchool_BottomNav_Labeled)
        assertEquals(BottomNavigationView.LABEL_VISIBILITY_LABELED, l.labelVisibilityMode)
        l.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(Os.dpi(ctx, 80f), View.MeasureSpec.EXACTLY)); l.layout(0, 0, 1000, Os.dpi(ctx, 80f))
        fun labels(v: View): List<TextView> = when (v) { is TextView -> listOf(v); is ViewGroup -> (0 until v.childCount).flatMap { labels(v.getChildAt(it)) }; else -> emptyList() }
        assertTrue("labels have room", labels(l).filter { it.visibility == View.VISIBLE }.all { it.height > Os.dpi(ctx, 10f) })
    }

    // 6. chips
    @Test fun chipStyles() {
        val choice = OsChips.choice(ctx, "Tides")
        assertTrue(choice.isCheckable); assertFalse(choice.isCheckedIconVisible)
        val filter = OsChips.filter(ctx, "Atoms")
        assertTrue(filter.isCheckable); assertTrue(filter.isCheckedIconVisible); assertNotNull(filter.checkedIcon)
        filter.isChecked = true
        assertEquals(Color.WHITE, filter.textColors.getColorForState(filter.drawableState, 0))
        assertEquals(0f, OsChips.assist(ctx, "A").chipCornerRadius, 0f)
    }

    // 7. animation hooks
    @Test fun freezeHoldsXpDropsMidway() {
        val a = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        OsAnimations.freeze(0.5f)
        val tv = OsXpDrop.show(a, 25)!!
        ShadowLooper.idleMainLooper(5, java.util.concurrent.TimeUnit.SECONDS)
        assertTrue("held mid-rise", tv.translationY < -Os.dp(a, 30f) && tv.translationY > -Os.dp(a, 95f))
        assertTrue(tv.parent != null)
        val drops = OsXpDrops.rightSide(a, listOf(R.drawable.os_ic_quill to 40, R.drawable.os_ic_book to 120))
        assertTrue("staggered", drops[0].translationY < drops[1].translationY)
        val before = drops[1].translationY
        OsAnimations.seek(1f); assertTrue("seek moves held drops", drops[1].translationY < before && drops[1].translationY <= -Os.dp(a, 200f))
        assertTrue(OsAnimations.heldCount >= 3)
        OsAnimations.reset()
        ShadowLooper.idleMainLooper(5, java.util.concurrent.TimeUnit.SECONDS)
        assertNull("finished and removed after release", tv.parent)
    }

    @Test fun scaleZeroIsInstantAndTimerFreezes() {
        val a = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        Os.animationScale = 0f
        assertEquals(0f, OsAnimations.scale, 0f)
        val tv = OsXpDrop.show(a, 5)!!
        ShadowLooper.idleMainLooper(100, java.util.concurrent.TimeUnit.MILLISECONDS)
        assertNull(tv.parent)
        OsAnimations.reset(); OsAnimations.freeze(0.25f)
        var done = false
        val orb = OsTimerOrb(ctx); orb.start(20_000) { done = true }
        assertEquals(0.75f, orb.fraction, 0.02f)
        ShadowLooper.idleMainLooper(30, java.util.concurrent.TimeUnit.SECONDS); assertFalse(done)
        val banner = OsBanner.show(a, listOf(OsBanner.Line("Hello", Color.BLACK, 16f)), autoDismissMs = 100)!!
        assertEquals("fades complete instantly while frozen", 1f, banner.alpha, 0f)
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS); assertNotNull("no auto-dismiss while frozen", banner.parent)
    }

    /** → screens/26-kit-v12-pieces.png */
    @Test fun showcase12() {
        org.junit.Assume.assumeTrue(dir.isNotEmpty())
        val c = ctx; val d = { v: Float -> Os.dpi(c, v) }
        fun header(t: String) = TextView(c).apply { text = t; setTextAppearance(com.josh.oldschool.R.style.TextAppearance_OldSchool_Header); setPadding(0, d(10f), 0, d(4f)) }
        val col = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; setPadding(d(14f), d(8f), d(14f), d(14f)); setBackgroundResource(R.drawable.os_stone_bg) }
        col.addView(header("BottomNav (unlabelled / labelled)"))
        col.addView(nav(com.josh.oldschool.R.style.Widget_OldSchool_BottomNav), LinearLayout.LayoutParams(-1, d(66f)))
        col.addView(nav(com.josh.oldschool.R.style.Widget_OldSchool_BottomNav_Labeled), LinearLayout.LayoutParams(-1, d(80f)).apply { topMargin = d(6f) })
        col.addView(header("Chips: assist · choice · filter"))
        col.addView(ChipGroup(c).apply {
            addView(OsChips.assist(c, "Assist"))
            addView(OsChips.choice(c, "Easy").apply { isChecked = true })
            addView(OsChips.choice(c, "Hard"))
            addView(OsChips.filter(c, "Tides").apply { isChecked = true })
            addView(OsChips.filter(c, "Atoms"))
        })
        col.addView(header("Chat lines (View · RemoteViews)"))
        val chat = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; setBackgroundResource(com.josh.oldschool.R.drawable.os_chatbox); setPadding(d(8f), d(8f), d(8f), d(8f)) }
        chat.addView(OsChat.view(c, "Guide", "Welcome back! Ready for lesson 4?"))
        chat.addView(OsChat.view(c, "Josh", "Yes, let's go.", speakerColor = c.getColor(com.josh.oldschool.R.color.os_chat_red), messageColor = Color.BLACK))
        val cw = d(340f)
        chat.addView(RemoteViews(app.packageName, com.josh.oldschool.R.layout.os_rv_chat_line).also {
            OsRemote.chatLine(app, it, "Guide", "Widgets get the same line as a pixel bitmap, wrapped onto two lines when it's long.", cw) }.apply(c, chat))
        col.addView(chat)
        col.addView(header("Stretch slots, 2-line labels (RemoteViews)"))
        col.addView(LinearLayout(c).apply {
            listOf(Triple("🌊", "Ocean currents", 0), Triple("🧪", "Atoms and molecules", 12), Triple("📜", "Recap", 0)).forEach { (e, l, n) ->
                val w = d(110f)
                addView(RemoteViews(app.packageName, com.josh.oldschool.R.layout.os_rv_inventory_slot_wide).also {
                    OsRemote.slot(app, it, e, l, n, widthPx = w, labelMaxLines = 2) }.apply(c, this),
                    LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = d(4f) })
            }
        })
        col.addView(header("Parchmentized views"))
        col.addView(Os.parchmentize(LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL; setPadding(d(12f), d(10f), d(12f), d(10f))
            addView(TextView(c).apply { text = "Lesson 4: The Moon"; typeface = Os.font(c, true); textSize = 18f; setTextColor(c.getColor(com.josh.oldschool.R.color.os_yellow)); Os.shadow(this) })
            addView(TextView(c).apply { text = "Body text that was yellow is now ink."; typeface = Os.font(c); textSize = 15f; setTextColor(c.getColor(com.josh.oldschool.R.color.os_white)); Os.shadow(this) })
            addView(com.google.android.material.button.MaterialButton(c).apply { text = "Next page" })
        }).apply { setBackgroundResource(com.josh.oldschool.R.drawable.os_scroll) })
        val w = Os.dpi(c, 393f)
        col.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        col.layout(0, 0, w, col.measuredHeight)
        val bmp = Bitmap.createBitmap(w, col.measuredHeight, Bitmap.Config.ARGB_8888); col.draw(Canvas(bmp))
        File(dir).mkdirs(); File(dir, "26-kit-v12-pieces.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
