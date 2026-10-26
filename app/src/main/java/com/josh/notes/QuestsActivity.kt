package com.josh.notes

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.appbar.MaterialToolbar

/**
 * Quest journal: every checklist marked as a quest, coloured like a classic quest list — red = not started,
 * yellow = in progress, green = complete. Tap to open, long-press for options.
 */
class QuestsActivity : LockableActivity() {
    private val os by lazy { AppSettings.oldSchool(this) }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stats)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root)) { v, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.updatePadding(left = b.left, top = b.top, right = b.right, bottom = b.bottom); WindowInsetsCompat.CONSUMED
        }
        findViewById<MaterialToolbar>(R.id.toolbar).apply { setTitle(R.string.quest_journal); setNavigationOnClickListener { finish() } }
    }

    override fun onResume() { super.onResume(); render() }

    private fun font(bold: Boolean = false) = if (os) com.josh.oldschool.Os.font(this, bold) else null

    fun render() {
        val box = findViewById<LinearLayout>(R.id.stats_content)
        box.removeAllViews()
        val quests = NoteStore.all(this).filter { !it.inTrash && it.checklist && it.quest }
            .sortedWith(compareBy<Note> { if (it.isComplete) 2 else if (it.progress.first > 0) 0 else 1 }.thenBy { it.displayTitle.lowercase() })
        val qp = quests.count { it.isComplete }
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            if (os) { setBackgroundResource(R.drawable.os_widget_bg); setPadding(dp(16), dp(14), dp(16), dp(16)) }
            else setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        panel.addView(TextView(this).apply {
            text = "Quest Points: $qp"; textSize = 18f; gravity = Gravity.CENTER
            font(true)?.let { typeface = it }
            if (os) { setTextColor(getColor(R.color.os_header)); setShadowLayer(0.01f, dp(1).toFloat(), dp(1).toFloat(), Color.BLACK) }
            setPadding(0, 0, 0, dp(8))
        })
        if (quests.isEmpty()) panel.addView(TextView(this).apply {
            text = "No quests yet.\nOpen a checklist, then choose ⋮ › Quest to start one."
            textSize = 15f; gravity = Gravity.CENTER; font()?.let { typeface = it }
            if (os) setTextColor(getColor(R.color.os_orange))
        })
        for (q in quests) {
            val (done, all) = q.progress
            val color = when { q.isComplete -> R.color.os_quest_green; done > 0 -> R.color.os_quest_yellow; else -> R.color.os_quest_red }
            panel.addView(TextView(this).apply {
                text = q.displayTitle.ifBlank { getString(R.string.untitled) } + "   $done/$all"
                textSize = 17f; minHeight = dp(40); gravity = Gravity.CENTER_VERTICAL
                font(true)?.let { typeface = it }
                setTextColor(getColor(color))
                if (os) setShadowLayer(0.01f, dp(1).toFloat(), dp(1).toFloat(), Color.BLACK)
                setBackgroundResource(if (os) com.josh.oldschool.R.drawable.os_menu_row else android.R.drawable.list_selector_background)
                setPadding(dp(6), 0, dp(6), 0)
                setOnClickListener { open(q) }
                setOnLongClickListener { menu(q); true }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        box.addView(panel)
    }

    private fun open(q: Note) = startActivity(Intent(this, EditNoteActivity::class.java).putExtra(EditNoteActivity.EXTRA_NOTE_ID, q.id))

    private fun menu(q: Note) {
        osOptions(this, q.displayTitle.ifBlank { getString(R.string.untitled) }, listOf("Read", "Abandon quest")) { i ->
            when (i) {
                0 -> open(q)
                1 -> { NoteStore.update(this, q.id) { it.copy(quest = false) }; render() }
            }
        }
    }
}
