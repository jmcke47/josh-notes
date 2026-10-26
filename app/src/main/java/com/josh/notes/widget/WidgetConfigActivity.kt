package com.josh.notes.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.josh.notes.AppSettings
import com.josh.notes.LockableActivity
import com.josh.notes.R

/** Per-widget options (shown when a widget is added, and on long-press › reconfigure on Android 12+). */
class WidgetConfigActivity : LockableActivity() {
    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private lateinit var mode: RadioGroup
    private lateinit var tags: ChipGroup
    private lateinit var lists: RadioGroup
    private var tagNames: List<String> = emptyList()
    private var listIds: List<Long> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        widgetId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID) ?: widgetId
        // Backing out cancels adding the widget (launcher contract).
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }
        setContentView(R.layout.activity_widget_config)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root)) { v, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.updatePadding(left = b.left, top = b.top, right = b.right, bottom = b.bottom); WindowInsetsCompat.CONSUMED
        }
        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }
        if (AppSettings.oldSchool(this)) findViewById<View>(R.id.config_content).setBackgroundResource(R.drawable.os_panel)
        mode = findViewById(R.id.config_mode)
        tags = findViewById(R.id.config_tags)
        lists = findViewById(R.id.config_checklists)

        tagNames = WidgetOptions.allTags(this)
        tagNames.forEachIndexed { i, t ->
            tags.addView(Chip(this).apply { id = View.generateViewId(); text = "#$t"; isCheckable = true; tag = t
                isSaveEnabled = false })
        }
        val checklists = WidgetOptions.checklists(this)
        listIds = checklists.map { it.id }
        checklists.forEach { n ->
            val (d, t) = n.progress
            lists.addView(RadioButton(this).apply { id = View.generateViewId(); tag = n.id; isSaveEnabled = false
                text = n.displayTitle.ifBlank { getString(R.string.untitled) } + "  ($d/$t)" })
        }

        val cur = WidgetOptions.get(this, widgetId)
        mode.check(when (cur.mode) {
            WidgetOptions.Mode.ALL -> R.id.mode_all; WidgetOptions.Mode.PINNED -> R.id.mode_pinned
            WidgetOptions.Mode.TAG -> R.id.mode_tag; WidgetOptions.Mode.CHECKLIST -> R.id.mode_checklist
            WidgetOptions.Mode.CHECKLISTS -> R.id.mode_checklists
        })
        (0 until tags.childCount).map { tags.getChildAt(it) as Chip }.let { chips ->
            (chips.firstOrNull { it.tag == cur.tag } ?: chips.firstOrNull())?.isChecked = true
        }
        (0 until lists.childCount).map { lists.getChildAt(it) as RadioButton }.let { rbs ->
            (rbs.firstOrNull { it.tag == cur.noteId } ?: rbs.firstOrNull())?.let { lists.check(it.id) }
        }
        mode.setOnCheckedChangeListener { _, _ -> sync() }
        tags.setOnCheckedStateChangeListener { _, _ -> sync() }
        lists.setOnCheckedChangeListener { _, _ -> sync() }
        sync()
        findViewById<View>(R.id.config_save).setOnClickListener { save() }
    }

    /** The options currently picked on screen (null if incomplete). */
    fun picked(): WidgetOptions? = when (mode.checkedRadioButtonId) {
        R.id.mode_pinned -> WidgetOptions(WidgetOptions.Mode.PINNED)
        R.id.mode_checklists -> WidgetOptions(WidgetOptions.Mode.CHECKLISTS)
        R.id.mode_tag -> tags.checkedChipId.takeIf { it != View.NO_ID }?.let { findViewById<Chip>(it).tag as String }
            ?.let { WidgetOptions(WidgetOptions.Mode.TAG, tag = it) }
        R.id.mode_checklist -> lists.checkedRadioButtonId.takeIf { it != View.NO_ID }?.let { findViewById<RadioButton>(it).tag as Long }
            ?.let { WidgetOptions(WidgetOptions.Mode.CHECKLIST, noteId = it) }
        else -> WidgetOptions(WidgetOptions.Mode.ALL)
    }

    private fun sync() {
        val sub = findViewById<TextView>(R.id.config_sub_title)
        val hint = findViewById<TextView>(R.id.config_hint)
        val m = mode.checkedRadioButtonId
        tags.visibility = if (m == R.id.mode_tag && tagNames.isNotEmpty()) View.VISIBLE else View.GONE
        lists.visibility = if (m == R.id.mode_checklist && listIds.isNotEmpty()) View.VISIBLE else View.GONE
        sub.visibility = if (m == R.id.mode_tag || m == R.id.mode_checklist) View.VISIBLE else View.GONE
        sub.text = if (m == R.id.mode_tag) "Tag" else "Checklist"
        hint.visibility = View.VISIBLE
        hint.text = when (m) {
            R.id.mode_tag -> if (tagNames.isEmpty()) "No tags yet. Add a #tag anywhere in a note's text, then come back." else
                "Shows notes containing the tag."
            R.id.mode_checklist -> if (listIds.isEmpty()) "No checklists yet. Make a note into a checklist first." else
                "Tick items straight from the home screen. Secret checklists aren't listed."
            R.id.mode_pinned -> "Pin notes from the editor's pin button."
            else -> "Newest edits first. Checklists show their next items, tickable from the widget."
        }
        findViewById<View>(R.id.config_save).isEnabled = picked() != null
    }

    private fun save() {
        val o = picked() ?: return
        WidgetOptions.set(this, widgetId, o)
        NotesWidgetProvider.refreshOne(this, widgetId)
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        finish()
    }
}
