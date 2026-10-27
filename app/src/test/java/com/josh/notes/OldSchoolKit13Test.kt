package com.josh.notes

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import com.josh.oldschool.Os
import com.josh.oldschool.OsDialogs
import com.josh.oldschool.OsPixelText
import com.josh.oldschool.OsTimerOrb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Kit v1.3: the classic-look pass (palette, bevels, shadows, menu, buttons, orbs, scrollbar, chat input, icons). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w393dp-h852dp-xxhdpi", application = NotesApp::class)
class OldSchoolKit13Test {
    private val app get() = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val ctx get() = ContextThemeWrapper(app, R.style.Theme_JoshNotes_OldSchool)
    private val dir = System.getProperty("screens.dir").orEmpty()
    private val KR = com.josh.oldschool.R.drawable::class.java

    @Test fun version() = assertEquals("1.3.0", Os.VERSION)

    @Test fun shadowIsExactlyOneFontPixel() {
        val tv = TextView(ctx).apply { textSize = 16f }          // themed: black shadow from Widget.OldSchool.TextView
        val ink = TextView(ctx).apply { textSize = 16f; setShadowLayer(0f, 0f, 0f, 0) }
        val root = LinearLayout(ctx).apply { addView(tv); addView(ink) }
        Os.crispShadows(root)
        val px = Math.round(tv.textSize / 11f).toFloat()
        assertEquals(px, Os.fontPixel(tv.textSize), 0f)
        assertEquals(px, tv.shadowDx, 0f); assertEquals(px, tv.shadowDy, 0f)
        assertEquals(Color.BLACK, tv.shadowColor)
        assertTrue("no blur", tv.shadowRadius < 0.02f)
        assertEquals("ink text keeps no shadow", 0f, ink.shadowDx, 0f)
        assertEquals(1f, Os.fontPixel(3f), 0f)
    }

    @Test fun screenFrameKeepsLaterRowsCrisp() {
        val root = FrameLayout(ctx)
        Os.applyScreenFrame(root)
        val late = TextView(ctx).apply { textSize = 20f }
        root.addView(late)
        root.viewTreeObserver.dispatchOnGlobalLayout()
        assertEquals(Os.fontPixel(late.textSize), late.shadowDx, 0f)
    }

    @Test fun chooseOptionMenuIsClassic() {
        val menu = OsDialogs.contextMenuView(ctx, listOf("Open", "Pin"), target = "Note") {}
        val header = menu.getChildAt(0) as TextView
        assertEquals("Choose Option", header.text.toString())
        assertEquals(Color.BLACK, (header.background as ColorDrawable).color)
        assertEquals(ctx.getColor(com.josh.oldschool.R.color.os_menu_header_text), header.currentTextColor)
        assertEquals(0xFF5D5447.toInt(), ctx.getColor(com.josh.oldschool.R.color.os_menu_bg))
        val open = menu.getChildAt(1) as TextView
        assertEquals(Color.WHITE, open.textColors.getColorForState(intArrayOf(), 0))
        assertEquals(0xFFFFFF00.toInt(), open.textColors.getColorForState(intArrayOf(android.R.attr.state_pressed), 0))
        assertEquals(Os.fontPixel(open.textSize), open.shadowDx, 0f)
        assertEquals("Cancel", (menu.getChildAt(3) as TextView).text.toString())
    }

    @Test fun buttonStates() {
        val b = com.google.android.material.button.MaterialButton(ctx).apply { text = "Save" }
        val cs = b.textColors
        assertEquals(ctx.getColor(com.josh.oldschool.R.color.os_orange), cs.getColorForState(intArrayOf(android.R.attr.state_enabled), 0))
        assertEquals(0xFFFFFF00.toInt(), cs.getColorForState(intArrayOf(android.R.attr.state_enabled, android.R.attr.state_pressed), 0))
        assertEquals(ctx.getColor(com.josh.oldschool.R.color.os_button_disabled_text), cs.getColorForState(intArrayOf(), 0))
        b.isEnabled = false
        assertEquals(ctx.getColor(com.josh.oldschool.R.color.os_button_disabled_text), b.currentTextColor)
    }

    @Test fun scrollbarIsStoneAndAlwaysShown() {
        val sv = ScrollView(ctx)
        assertFalse("classic scrollbars don't fade", sv.isScrollbarFadingEnabled)
        assertEquals(Os.dpi(ctx, 8f), sv.scrollBarSize)
        assertNotNull(sv.verticalScrollbarThumbDrawable); assertNotNull(sv.verticalScrollbarTrackDrawable)
    }

    @Test fun newPiecesResolve() {
        listOf("os_orb_stat_green", "os_orb_stat_red", "os_orb_stat_cyan", "os_orb_stat_yellow", "os_scrollbar_thumb",
            "os_scrollbar_track", "os_button_focused", "os_button_disabled", "os_button_focused_bg", "os_chat_input",
            "os_menu_panel", "os_ic_pack", "os_ic_star", "os_ic_bell", "os_ic_gear",
            // unchanged names from earlier versions still resolve (backwards compatible)
            "os_frame_carved", "os_frame_carved_filled", "os_rivet", "os_side_tab", "os_tab", "os_chat_tab", "os_orb",
            "os_orb_new", "os_button", "os_button_9", "os_context_menu", "os_ic_stats", "os_ic_quest", "os_ic_scroll").forEach {
            val id = KR.getField(it).getInt(null)
            assertNotNull(it, ctx.getDrawable(id))
        }
        assertTrue(com.josh.oldschool.R.style.Widget_OldSchool_ChatInput != 0 && com.josh.oldschool.R.style.Widget_OldSchool_StatOrb != 0)
    }

    @Test fun boldNNoLongerLooksLikeM() {
        fun img(s: String) = OsPixelText.render(app, s, 20f, Color.WHITE, 400, shadow = false, bold = true)!!
        val n = img("N"); val m = img("M")
        var diff = 0
        for (y in 0 until minOf(n.height, m.height)) for (x in 0 until minOf(n.width, m.width))
            if ((n.getPixel(x, y) ushr 24 > 128) != (m.getPixel(x, y) ushr 24 > 128)) diff++
        assertTrue("bold N and M differ clearly ($diff px)", diff > 40)
        assertNotEquals(0, img("é").width)
    }

    @Test fun showcase13() {
        org.junit.Assume.assumeTrue(dir.isNotEmpty())
        val c = ctx; val d = { v: Float -> Os.dpi(c, v) }
        val K = com.josh.oldschool.R.drawable::class.java
        fun res(n: String) = K.getField(n).getInt(null)
        fun header(t: String) = TextView(c).apply { text = t; setTextAppearance(com.josh.oldschool.R.style.TextAppearance_OldSchool_Header); setPadding(0, d(10f), 0, d(4f)) }
        val col = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; setPadding(d(14f), d(8f), d(14f), d(14f)); setBackgroundResource(R.drawable.os_stone_bg) }
        col.addView(header("Side tabs + new icons"))
        col.addView(LinearLayout(c).apply {
            setBackgroundResource(com.josh.oldschool.R.drawable.os_side_panel); setPadding(d(8f), d(8f), d(8f), d(8f))
            listOf("os_ic_scroll", "os_ic_stats", "os_ic_quest", "os_ic_pack", "os_ic_star", "os_ic_bell").forEachIndexed { i, n ->
                addView(ImageView(c).apply {
                    setBackgroundResource(com.josh.oldschool.R.drawable.os_side_tab); setImageResource(res(n)); isSelected = i == 1
                    setPadding(d(9f), d(9f), d(9f), d(9f)); scaleType = ImageView.ScaleType.FIT_CENTER
                }, LinearLayout.LayoutParams(d(50f), d(50f)))
            }
        })
        col.addView(header("Buttons: normal / pressed / focused / disabled"))
        col.addView(LinearLayout(c).apply {
            listOf("Normal", "Pressed", "Focus", "Off").forEachIndexed { i, t ->
                addView(com.google.android.material.button.MaterialButton(c).apply {
                    text = t; when (i) { 1 -> isPressed = true; 2 -> { isFocusable = true; refreshDrawableState() }; 3 -> isEnabled = false }
                    if (i == 2) { background.state = intArrayOf(android.R.attr.state_enabled, android.R.attr.state_focused); setTextColor(textColors.getColorForState(background.state, 0)) }
                    if (i == 1) { setTextColor(textColors.getColorForState(intArrayOf(android.R.attr.state_enabled, android.R.attr.state_pressed), 0)) }
                }, LinearLayout.LayoutParams(0, d(44f), 1f).apply { marginEnd = d(4f) })
            }
        })
        col.addView(header("Orbs"))
        col.addView(LinearLayout(c).apply {
            gravity = Gravity.CENTER_VERTICAL
            listOf("99" to "os_orb_stat_green", "42" to "os_orb_stat_cyan", "7" to "os_orb_stat_red", "100" to "os_orb_stat_yellow").forEach { (n, o) ->
                addView(TextView(c).apply { text = n; textSize = 16f; setTextColor(c.getColor(com.josh.oldschool.R.color.os_green)); typeface = Os.font(c, true); Os.crispShadow(this); setPadding(d(6f), 0, d(2f), 0) })
                addView(ImageView(c).apply { setImageResource(res(o)) }, LinearLayout.LayoutParams(d(34f), d(34f)))
            }
            addView(OsTimerOrb(c).apply { setRemaining(0.7f, 60_000) }, LinearLayout.LayoutParams(d(48f), d(48f)).apply { marginStart = d(10f) })
        })
        col.addView(header("Choose Option · chatbox + input · scrollbar"))
        val row = LinearLayout(c).apply { gravity = Gravity.TOP }
        row.addView(OsDialogs.contextMenuView(c, listOf("Open", "Pin", "Delete"), target = "Gym plan") {}.apply {
            (getChildAt(2) as TextView).isPressed = true
        }, LinearLayout.LayoutParams(d(170f), -2))
        val chat = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundResource(com.josh.oldschool.R.drawable.os_chatbox_carved); setPadding(d(10f), d(9f), d(10f), d(9f))
            addView(ScrollView(c).apply {
                addView(TextView(c).apply {
                    setTextColor(Color.BLACK); setShadowLayer(0f, 0f, 0f, 0); textSize = 14f; typeface = Os.font(c)
                    text = (1..12).joinToString("\n") { "Welcome to the notes. Line $it" }
                })
            }, LinearLayout.LayoutParams(-1, d(110f)))
            addView(EditText(ContextThemeWrapper(c, 0), null, 0, com.josh.oldschool.R.style.Widget_OldSchool_ChatInput).apply { setText("Josh: hello*"); typeface = Os.font(c) },
                LinearLayout.LayoutParams(-1, -2))
        }
        row.addView(chat, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = d(8f) })
        col.addView(row)
        col.addView(header("Text: yellow · orange · cyan · white"))
        col.addView(LinearLayout(c).apply {
            listOf("Yellow", "Orange", "Cyan", "White").forEach { t ->
                addView(TextView(c).apply {
                    text = "$t  "; setTextAppearance(com.josh.oldschool.R.style::class.java.getField("TextAppearance_OldSchool_$t").getInt(null))
                    typeface = Os.font(c, true); Os.crispShadow(this)
                })
            }
        })
        val w = Os.dpi(c, 393f)
        col.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        col.layout(0, 0, w, col.measuredHeight)
        val bmp = Bitmap.createBitmap(w, col.measuredHeight, Bitmap.Config.ARGB_8888); col.draw(Canvas(bmp))
        File(dir).mkdirs(); File(dir, "28-kit-v13-pieces.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
