package com.josh.notes

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import androidx.test.core.app.ApplicationProvider
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders key screens (Old School theme) to PNGs. Skipped unless -PscreensDir=… is given.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w393dp-h852dp-xxhdpi", application = NotesApp::class)
class ScreenshotTest {
    private val dir = System.getProperty("screens.dir").orEmpty()
    private val ctx get() = ApplicationProvider.getApplicationContext<android.app.Application>()

    @Before fun seed() {
        assumeTrue(dir.isNotEmpty())
        runCatching { android.animation.ValueAnimator::class.java.getMethod("setDurationScale", Float::class.javaPrimitiveType).invoke(null, 1f) }
        ctx.getSharedPreferences("settings", 0).edit().putString("theme", "OLDSCHOOL").putInt("xp", 1150).commit()
        val now = System.currentTimeMillis()
        val h = 3600_000L
        NoteStore.replaceAll(ctx, listOf(
            Note(1, "Remind Jess of room change", "Room 4B from Monday — tell her before 9am.", now - h, pinned = true,
                reminderAt = now - 26 * h),
            Note(2, "11:30 sell tsla stock", "Check price first, limit order.", now - 2 * h, reminderAt = now + 5 * h,
                repeat = Repeat(Repeat.Type.WEEKLY, setOf(java.util.Calendar.MONDAY, java.util.Calendar.WEDNESDAY))),
            Note(3, "Shopping", "", now - 3 * h, checklist = true, items = listOf(
                CheckItem("Milk", true), CheckItem("Bread", true), CheckItem("Coffee beans", false), CheckItem("Eggs", false))),
            Note(4, "Gym plan", "Mon: legs\nWed: push\nFri: pull #gym", now - 30 * h, color = 3),
            Note(5, "Voice memo — ideas", "Transcript: build a shed in spring #home", now - 50 * h),
            Note(6, "Garden quest", "", now - 70 * h, checklist = true, quest = true, items = listOf(
                CheckItem("Buy seeds #home", true), CheckItem("Dig the vegetable bed", true), CheckItem("Plant tomatoes", false),
                CheckItem("Build a trellis", false), CheckItem("Water every morning", false), CheckItem("Mulch the paths", false))),
        ))
        NoteStore.all(ctx).filter { it.secret }.forEach { NoteStore.deleteForever(ctx, it.id) }
        SecretCrypto.keyOverride = javax.crypto.KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        AppLock.availableOverride = true
        AppLock.promptOverride = { _, _ -> } // leave secret notes locked
    }

    @org.junit.After fun tearDown() {
        AppLock.availableOverride = null; AppLock.promptOverride = null
        ctx.getSharedPreferences("widget", 0).edit().clear().commit()
    }

    @Test fun checklistLight() {
        ctx.getSharedPreferences("settings", 0).edit().putString("theme", "LIGHT").commit()
        val a = Robolectric.buildActivity(EditNoteActivity::class.java,
            Intent(ctx, EditNoteActivity::class.java).putExtra(EditNoteActivity.EXTRA_NOTE_ID, 6L)).setup().get()
        snap(a, "15-checklist")
    }

    @Test fun secretLocked() {
        val secret = NoteStore.upsert(ctx, Note(0L, "Bank PIN hint", "never shown", System.currentTimeMillis(), secret = true))
        val a = Robolectric.buildActivity(EditNoteActivity::class.java,
            Intent(ctx, EditNoteActivity::class.java).putExtra(EditNoteActivity.EXTRA_NOTE_ID, secret.id)).setup().get()
        idle()
        assertEquals(View.VISIBLE, a.findViewById<View>(R.id.secret_lock).visibility)
        assertEquals("", a.findViewById<android.widget.EditText>(R.id.title).text.toString())
        snap(a, "16-secret-locked")
        // The list shows it as "Secret note" with no preview.
        val m = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        snap(m, "16b-secret-in-list")
        NoteStore.deleteForever(ctx, secret.id)
    }

    @Test fun statsOldSchool() {
        Stats.recordActiveDay(ctx); Stats.recordActiveDay(ctx, System.currentTimeMillis() - 86_400_000L)
        val a = Robolectric.buildActivity(StatsActivity::class.java).setup().get()
        snap(a, "17-stats-oldschool")
    }

    @Test fun widgetConfigure() {
        com.josh.notes.widget.WidgetOptions.set(ctx, 7, com.josh.notes.widget.WidgetOptions(com.josh.notes.widget.WidgetOptions.Mode.CHECKLIST, noteId = 6L))
        val a = Robolectric.buildActivity(com.josh.notes.widget.WidgetConfigActivity::class.java,
            Intent().putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, 7)).setup().get()
        snap(a, "18-widget-configure")
    }

    @Test fun widgetChecklistOldSchool() {
        com.josh.notes.widget.WidgetOptions.set(ctx, 9, com.josh.notes.widget.WidgetOptions(com.josh.notes.widget.WidgetOptions.Mode.CHECKLIST, noteId = 6L))
        renderWidget("OLDSCHOOL", "19-widget-checklist-oldschool", widgetId = 9)
        renderQuick("OLDSCHOOL", "20-widget-quick-oldschool")
        renderQuick("DARK", "21-widget-quick-dark")
    }

    private fun renderQuick(theme: String, name: String) {
        ctx.getSharedPreferences("settings", 0).edit().putString("theme", theme).commit()
        val c = android.view.ContextThemeWrapper(ctx, R.style.Theme_JoshNotes)
        val dm = ctx.resources.displayMetrics
        val w = (170 * dm.density).toInt(); val h = (170 * dm.density).toInt()
        val frame = android.widget.FrameLayout(c).apply { setBackgroundColor(0xFF5C7F9E.toInt()); setPadding(40, 40, 40, 40) }
        com.josh.notes.widget.WidgetTheme.saveWidth(ctx, 0, 170)
        val root = com.josh.notes.widget.QuickWidgetProvider.buildViews(ctx, 0).apply(c, frame)
        frame.addView(root, android.widget.FrameLayout.LayoutParams(w, h))
        frame.measure(View.MeasureSpec.makeMeasureSpec(w + 80, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h + 80, View.MeasureSpec.EXACTLY))
        frame.layout(0, 0, w + 80, h + 80)
        snapView(frame, name)
    }

    private fun idle() { repeat(5) { shadowOf(Looper.getMainLooper()).idle() } }
    private fun idleFor(ms: Long) { shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(ms)) }

    private fun snap(a: Activity, name: String, settle: Boolean = true, extra: List<View> = emptyList()) {
        if (settle) idle()
        snapView(a.window.decorView, name, extra)
    }

    /** [extra]: runtime-added overlay views that Robolectric's software pass skips; drawn on top at their position. */
    private fun snapView(v: View, name: String, extra: List<View> = emptyList()) {
        val w = v.width.takeIf { it > 0 } ?: 1080; val hh = v.height.takeIf { it > 0 } ?: 2340
        v.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(hh, View.MeasureSpec.EXACTLY))
        v.layout(0, 0, w, hh)
        val bmp = Bitmap.createBitmap(w, hh, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        v.draw(canvas)
        extra.forEach { e ->
            val loc = IntArray(2); e.getLocationInWindow(loc)
            canvas.save(); canvas.translate(loc[0] + e.translationX, loc[1] + e.translationY)
            canvas.saveLayerAlpha(0f, 0f, e.width.toFloat(), e.height.toFloat(), (e.alpha * 255).toInt())
            e.draw(canvas); canvas.restore(); canvas.restore()
        }
        File(dir).mkdirs()
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun main() {
        val a = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        snap(a, "01-notes-list")
    }

    @Test fun editorChecklist() {
        val a = Robolectric.buildActivity(EditNoteActivity::class.java,
            Intent(ctx, EditNoteActivity::class.java).putExtra(EditNoteActivity.EXTRA_NOTE_ID, 3L)).setup().get()
        snap(a, "02-editor-checklist")
        val drop = Levels.xpDrop(a, 10)!!
        drop.translationY = -drop.resources.displayMetrics.density * 80
        snap(a, "06-xp-drop", settle = false, extra = listOf(drop))
    }

    @Test fun levelUp() {
        val a = Robolectric.buildActivity(EditNoteActivity::class.java,
            Intent(ctx, EditNoteActivity::class.java).putExtra(EditNoteActivity.EXTRA_NOTE_ID, 3L)).setup().get()
        idle()
        Levels.levelUp(a, 42); idleFor(100); idleFor(100); idleFor(100); idleFor(150)
        val fw = Levels.lastFireworks!!
        fw.prime(frames = 22, fraction = 0.2f)
        snap(a, "07-level-up", settle = false, extra = listOf(fw))
    }

    @Test fun dialog() {
        val a = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        idle()
        val d = osDialog(a).setTitle("Restore which backup?")
            .setMessage("Merge keeps all your current notes and takes the newer version of any note that's in both.")
            .setNegativeButton(android.R.string.cancel, null).setPositiveButton("Merge", null).show()
        idle()
        snapWithDialog(a, d, "08-dialog")
        d.dismiss(); idle()
        val info = osDialog(a).setTitle("Backup saved").setMessage("12 notes were written to your backup folder.")
            .setPositiveButton(android.R.string.ok, null).show()
        idle()
        snapWithDialog(a, info, "08b-dialog-continue")
    }

    @Test fun contextMenu() {
        val a = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        idle()
        val list = a.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.list)
        val dv = a.window.decorView
        dv.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(2340, View.MeasureSpec.EXACTLY))
        dv.layout(0, 0, 1080, 2340); idle()
        list.getChildAt(3).performLongClick(); idle()
        val d = org.robolectric.shadows.ShadowDialog.getLatestDialog()
        snapWithDialog(a, d, "24-context-menu")
    }

    /** Activity + a dialog window composited at the dialog's gravity (Robolectric renders windows separately). */
    private fun snapWithDialog(a: Activity, d: android.app.Dialog, name: String) {
        idle()
        val v = a.window.decorView
        val w = v.width.takeIf { it > 0 } ?: 1080; val hh = v.height.takeIf { it > 0 } ?: 2340
        v.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(hh, View.MeasureSpec.EXACTLY))
        v.layout(0, 0, w, hh)
        val bmp = Bitmap.createBitmap(w, hh, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp); v.draw(c)
        val dv = d.window!!.decorView
        val attrs = d.window!!.attributes
        val full = attrs.width == android.view.ViewGroup.LayoutParams.MATCH_PARENT
        val dw = if (full) w else (w * 0.88f).toInt()
        dv.measure(View.MeasureSpec.makeMeasureSpec(dw, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(hh, View.MeasureSpec.AT_MOST))
        dv.layout(0, 0, dv.measuredWidth, dv.measuredHeight)
        c.drawColor((if (attrs.dimAmount > 0) (attrs.dimAmount * 255).toInt() else 80) shl 24)
        val bottom = (attrs.gravity and android.view.Gravity.VERTICAL_GRAVITY_MASK) == android.view.Gravity.BOTTOM
        val top = if (bottom) hh - dv.measuredHeight - attrs.y else (hh - dv.measuredHeight) / 2
        c.save(); c.translate(((w - dv.measuredWidth) / 2).toFloat(), top.toFloat()); dv.draw(c); c.restore()
        File(dir).mkdirs()
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** [from] = theme the widget showed before; the new look is then *re-applied* onto it, like a launcher does. */
    private fun renderWidget(theme: String, name: String, from: String? = null, widgetId: Int = 0) {
        val c = android.view.ContextThemeWrapper(ctx, R.style.Theme_JoshNotes)
        val dm = ctx.resources.displayMetrics
        val w = (300 * dm.density).toInt(); val h = (380 * dm.density).toInt()
        val frame = android.widget.FrameLayout(c).apply { setBackgroundColor(0xFF5C7F9E.toInt()); setPadding(40, 40, 40, 40) }
        // Same RemoteViews the launcher gets (list adapter attached manually: no launcher host here).
        val prefs = ctx.getSharedPreferences("settings", 0)
        val root = if (from != null) {
            prefs.edit().putString("theme", from).commit()
            val old = com.josh.notes.widget.NotesWidgetProvider.buildViews(ctx, widgetId, withList = false).apply(c, frame)
            prefs.edit().putString("theme", theme).commit()
            val rv = com.josh.notes.widget.NotesWidgetProvider.buildViews(ctx, widgetId, withList = false)
            assertEquals(R.layout.widget_frame, rv.layoutId)
            rv.reapply(c, old); old
        } else {
            prefs.edit().putString("theme", theme).commit()
            // The real update (rows as RemoteCollectionItems), parcelled like the trip to the launcher.
            val rv = com.josh.notes.widget.NotesWidgetProvider.buildViews(ctx, widgetId)
            val p = android.os.Parcel.obtain(); rv.writeToParcel(p, 0); p.setDataPosition(0)
            val sent = android.widget.RemoteViews.CREATOR.createFromParcel(p); p.recycle()
            val hv = android.appwidget.AppWidgetHostView(c)
            sent.apply(c, hv).also { hv.addView(it) }.also { hv.removeView(it) }
        }
        frame.addView(root, android.widget.FrameLayout.LayoutParams(w, h))
        val list = root.findViewById<android.widget.ListView>(R.id.widget_list)
        val f = com.josh.notes.widget.NotesWidgetService.Factory(ctx, widgetId).apply { onDataSetChanged() }
        if (f.count > 0) root.findViewById<View>(R.id.widget_empty).visibility = View.GONE
        if (list.adapter == null) list.adapter = object : android.widget.BaseAdapter() {
            override fun getCount() = f.count
            override fun getItem(p: Int) = p
            override fun getItemId(p: Int) = p.toLong()
            override fun getView(p: Int, cv: View?, parent: android.view.ViewGroup) = f.getViewAt(p).apply(c, parent)
        }
        frame.measure(View.MeasureSpec.makeMeasureSpec(w + 80, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h + 80, View.MeasureSpec.EXACTLY))
        frame.layout(0, 0, w + 80, h + 80)
        snapView(frame, name)
    }

    @Test fun widget() {
        renderWidget("OLDSCHOOL", "04-widget")
        renderWidget("OLDSCHOOL", "10-widget-oldschool")
        renderWidget("DARK", "11-widget-dark")
        renderWidget("LIGHT", "12-widget-light")
        renderWidget("OLDSCHOOL", "13-widget-oldschool-after-system", from = "SYSTEM")
        renderWidget("SYSTEM", "14-widget-system")
        renderWidget("OLDSCHOOL", "27-widget-rows-fixed")
    }

    @Test fun editorText() {
        val a = Robolectric.buildActivity(EditNoteActivity::class.java,
            Intent(ctx, EditNoteActivity::class.java).putExtra(EditNoteActivity.EXTRA_NOTE_ID, 1L)).setup().get()
        snap(a, "03-editor-note")
    }

    @Test fun settings() {
        val a = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
        snap(a, "05-settings")
        val target = a.findViewById<View>(R.id.backup_folder)
        val sv = generateSequence(target.parent as View?) { it.parent as? View }.first { it is android.widget.ScrollView } as android.widget.ScrollView
        var y = 0; var v: View = target
        while (v !== sv) { y += v.top; v = v.parent as View }
        sv.scrollTo(0, y - (60 * a.resources.displayMetrics.density).toInt())
        snap(a, "09-settings-backup-drive")
    }
}
