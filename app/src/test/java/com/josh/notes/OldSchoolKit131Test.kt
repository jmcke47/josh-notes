package com.josh.notes

import android.view.View
import android.widget.FrameLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import com.josh.oldschool.Os
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Kit v1.3.1: scrollbars stay inside the screen frame; per-view opt-out of the sharpened shadow. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w393dp-h852dp-xxhdpi", application = NotesApp::class)
class OldSchoolKit131Test {
    private val app get() = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val ctx get() = ContextThemeWrapper(app, R.style.Theme_JoshNotes_OldSchool)
    private val band get() = ctx.resources.getDimensionPixelSize(com.josh.oldschool.R.dimen.os_frame_band)

    @Test fun version() = assertEquals("1.3.1", Os.VERSION)

    private fun layout(v: View) {
        v.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1500, View.MeasureSpec.EXACTLY))
        v.layout(0, 0, 1000, 1500)
    }

    @Test fun screenFrameMovesFullWidthScrollbarInside() {
        val root = FrameLayout(ctx)
        val sv = ScrollView(ctx).apply { addView(TextView(ctx).apply { text = "x\n".repeat(200) }) }
        val inset = ScrollView(ctx).apply { isVerticalScrollBarEnabled = true }
        root.addView(sv, FrameLayout.LayoutParams(-1, -1))
        root.addView(inset, FrameLayout.LayoutParams(500, 500))      // far from the right border: untouched
        Os.applyScreenFrame(root)
        layout(root); root.viewTreeObserver.dispatchOnGlobalLayout()
        assertTrue("scrollbar inset ${sv.paddingRight} >= $band", sv.paddingRight >= band)
        assertEquals(0, inset.paddingRight)
        // the visible scrollbar now ends left of the frame border
        assertTrue(sv.width - sv.paddingRight <= root.width - band)
        root.viewTreeObserver.dispatchOnGlobalLayout()
        assertTrue("idempotent", sv.paddingRight in band..band + 1)
    }

    @Test fun publicHelperIsIdempotentAndKeepsBiggerPadding() {
        val sv = ScrollView(ctx)
        Os.keepScrollbarsInsideFrame(sv); Os.keepScrollbarsInsideFrame(sv)
        assertEquals(band, sv.paddingRight)
        val padded = ScrollView(ctx).apply { setPadding(0, 0, band * 3, 0) }
        Os.keepScrollbarsInsideFrame(padded)
        assertEquals(band * 3, padded.paddingRight)
        val nested = FrameLayout(ctx).apply { addView(ScrollView(ctx)) }
        Os.keepScrollbarsInsideFrame(nested, 5)
        assertEquals(5, nested.getChildAt(0).paddingRight)
    }

    @Test fun noSharpShadowOptOut() {
        val keep = TextView(ctx).apply { textSize = 16f; setShadowLayer(0.01f, 9f, 9f, 0xFF000000.toInt()) }
        val xml = TextView(ctx).apply { textSize = 16f; setShadowLayer(0.01f, 9f, 9f, 0xFF000000.toInt()); setTag(com.josh.oldschool.R.id.os_no_sharp_shadow, "true") }
        val inner = TextView(ctx).apply { textSize = 16f; setShadowLayer(0.01f, 9f, 9f, 0xFF000000.toInt()) }
        val box = FrameLayout(ctx).apply { addView(inner) }
        val normal = TextView(ctx).apply { textSize = 16f; setShadowLayer(0.01f, 9f, 9f, 0xFF000000.toInt()) }
        val root = android.widget.LinearLayout(ctx).apply { addView(Os.noSharpShadow(keep)); addView(xml); addView(Os.noSharpShadow(box)); addView(normal) }
        Os.crispShadows(root)
        assertEquals(9f, keep.shadowDx, 0f); assertEquals(9f, xml.shadowDx, 0f); assertEquals(9f, inner.shadowDx, 0f)
        assertEquals(Os.fontPixel(normal.textSize), normal.shadowDx, 0f)
        Os.noSharpShadow(keep, off = false); Os.crispShadows(root)
        assertEquals(Os.fontPixel(keep.textSize), keep.shadowDx, 0f)
    }
}
