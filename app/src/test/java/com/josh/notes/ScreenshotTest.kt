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
            Note(4, "Gym plan", "Mon: legs\nWed: push\nFri: pull", now - 30 * h, color = 3),
            Note(5, "Voice memo — ideas", "Transcript: build a shed in spring", now - 50 * h),
        ))
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
        snapView(d.window!!.decorView, "08-dialog")
    }

    /** [from] = theme the widget showed before; the new look is then *re-applied* onto it, like a launcher does. */
    private fun renderWidget(theme: String, name: String, from: String? = null) {
        val c = android.view.ContextThemeWrapper(ctx, R.style.Theme_JoshNotes)
        val dm = ctx.resources.displayMetrics
        val w = (300 * dm.density).toInt(); val h = (380 * dm.density).toInt()
        val frame = android.widget.FrameLayout(c).apply { setBackgroundColor(0xFF5C7F9E.toInt()); setPadding(40, 40, 40, 40) }
        // Same RemoteViews the launcher gets (list adapter attached manually: no launcher host here).
        val prefs = ctx.getSharedPreferences("settings", 0)
        val root = if (from != null) {
            prefs.edit().putString("theme", from).commit()
            val old = com.josh.notes.widget.NotesWidgetProvider.buildViews(ctx, 0, withList = false).apply(c, frame)
            prefs.edit().putString("theme", theme).commit()
            val rv = com.josh.notes.widget.NotesWidgetProvider.buildViews(ctx, 0, withList = false)
            assertEquals(R.layout.widget_frame, rv.layoutId)
            rv.reapply(c, old); old
        } else {
            prefs.edit().putString("theme", theme).commit()
            com.josh.notes.widget.NotesWidgetProvider.buildViews(ctx, 0, withList = false).apply(c, frame)
        }
        frame.addView(root, android.widget.FrameLayout.LayoutParams(w, h))
        val list = root.findViewById<android.widget.ListView>(R.id.widget_list)
        val f = com.josh.notes.widget.NotesWidgetService.Factory(ctx).apply { onDataSetChanged() }
        root.findViewById<View>(R.id.widget_empty).visibility = View.GONE
        list.adapter = object : android.widget.BaseAdapter() {
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
