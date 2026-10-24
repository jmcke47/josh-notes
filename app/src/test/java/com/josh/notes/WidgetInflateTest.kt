package com.josh.notes

import android.content.Context
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Inflate every widget layout the way a launcher does (restricted package context, no app theme). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = NotesApp::class)
class WidgetInflateTest {
    /** Launchers re-apply a same-layout update onto the existing views: no state may leak from the old theme. */
    @Test fun reapplyAcrossThemesHasNoStaleState() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val host = app.createPackageContext(app.packageName, Context.CONTEXT_RESTRICTED)
        val prefs = app.getSharedPreferences("settings", 0)
        prefs.edit().putString("theme", "SYSTEM").commit()
        val root = com.josh.notes.widget.NotesWidgetProvider.buildViews(app, 1, withList = false).apply(host, FrameLayout(host))
        val titles = mapOf(
            ThemeMode.SYSTEM to R.id.widget_title, ThemeMode.LIGHT to R.id.widget_title_light,
            ThemeMode.DARK to R.id.widget_title_dark, ThemeMode.OLDSCHOOL to R.id.widget_title_img)
        for (t in listOf(ThemeMode.OLDSCHOOL, ThemeMode.LIGHT, ThemeMode.OLDSCHOOL, ThemeMode.DARK, ThemeMode.SYSTEM, ThemeMode.OLDSCHOOL)) {
            prefs.edit().putString("theme", t.name).commit()
            val rv = com.josh.notes.widget.NotesWidgetProvider.buildViews(app, 1, withList = false)
            org.junit.Assert.assertEquals(R.layout.widget_frame, rv.layoutId)
            rv.reapply(host, root)
            for ((mode, id) in titles) org.junit.Assert.assertEquals("$t title $mode",
                if (mode == t) android.view.View.VISIBLE else android.view.View.GONE, root.findViewById<android.view.View>(id).visibility)
            val listFrame = root.findViewById<android.view.View>(R.id.widget_list_frame)
            org.junit.Assert.assertEquals("$t list panel", t == ThemeMode.OLDSCHOOL, listFrame.background != null)
            org.junit.Assert.assertNotNull(root.background)
        }
    }

    @Test fun allThemesInflateInRestrictedContext() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val host = app.createPackageContext(app.packageName, Context.CONTEXT_RESTRICTED)
        for (t in ThemeMode.entries) {
            app.getSharedPreferences("settings", 0).edit().putString("theme", t.name).commit()
            val rv = com.josh.notes.widget.NotesWidgetProvider.buildViews(app, 1, withList = false)
            try { rv.apply(host, FrameLayout(host)) } catch (e: Throwable) { throw AssertionError("header $t: $e", e) }
            val f = com.josh.notes.widget.NotesWidgetService.Factory(app, 1)
            NoteStore.replaceAll(app, listOf(Note(1, "T", "b", System.currentTimeMillis())))
            f.onDataSetChanged()
            try { f.getViewAt(0).apply(host, FrameLayout(host)) } catch (e: Throwable) { throw AssertionError("item $t: $e", e) }
        }
    }
}
