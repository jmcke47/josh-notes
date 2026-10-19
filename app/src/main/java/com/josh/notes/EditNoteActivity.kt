package com.josh.notes

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Paint
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar

class EditNoteActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_NOTE_ID = "note_id"
        const val EXTRA_DICTATE = "dictate"
        const val EXTRA_TITLE = "init_title"
        const val EXTRA_BODY = "init_body"
        const val ACTION_DICTATE = "com.josh.notes.DICTATE"
    }

    private var noteId: Long = 0L
    private var deleted = false
    private var pinned = false
    private var color = 0
    private var archived = false
    private var checklist = false
    private var createdAt = 0L

    private lateinit var toolbar: MaterialToolbar
    private lateinit var titleEt: EditText
    private lateinit var bodyEt: EditText
    private lateinit var listBox: LinearLayout
    private lateinit var addItem: View
    private lateinit var colorBar: View
    private var lastFocused: EditText? = null
    private lateinit var dictation: DictationController
    private val volumeKeys by lazy { VolumeKeyDictation(this) { dictation.start() } }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root)) { v, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime()
                    or WindowInsetsCompat.Type.displayCutout())
            v.updatePadding(left = b.left, top = b.top, right = b.right, bottom = b.bottom)
            WindowInsetsCompat.CONSUMED
        }
        toolbar = findViewById(R.id.toolbar)
        titleEt = findViewById(R.id.title)
        bodyEt = findViewById(R.id.body)
        listBox = findViewById(R.id.checklist)
        addItem = findViewById(R.id.add_item)
        colorBar = findViewById(R.id.color_bar)
        dictation = DictationController(this) { insertAtCursor(it) }

        trackFocus(titleEt); trackFocus(bodyEt)
        addItem.setOnClickListener { addRow(CheckItem("", false), focus = true) }

        val restoredId = savedInstanceState?.getLong(EXTRA_NOTE_ID, 0L) ?: 0L
        noteId = if (restoredId != 0L) restoredId else intent.getLongExtra(EXTRA_NOTE_ID, 0L)
        val note = noteId.takeIf { it != 0L }?.let { NoteStore.get(this, it) }
        if (note == null) noteId = 0L

        if (savedInstanceState != null && savedInstanceState.containsKey("checklist")) {
            // Rebuild from instance state (rotation etc.).
            pinned = savedInstanceState.getBoolean("pinned"); color = savedInstanceState.getInt("color")
            archived = savedInstanceState.getBoolean("archived"); checklist = savedInstanceState.getBoolean("checklist")
            createdAt = savedInstanceState.getLong("createdAt")
            val texts = savedInstanceState.getStringArrayList("itemTexts") ?: arrayListOf()
            val checks = savedInstanceState.getBooleanArray("itemChecks") ?: BooleanArray(0)
            setChecklistItems(texts.mapIndexed { i, t -> CheckItem(t, checks.getOrElse(i) { false }) })
        } else if (note != null) {
            titleEt.setText(note.title); bodyEt.setText(note.body)
            pinned = note.pinned; color = note.color; archived = note.archived
            checklist = note.checklist; createdAt = note.createdAt
            setChecklistItems(note.items)
        } else {
            intent.getStringExtra(EXTRA_TITLE)?.let { titleEt.setText(it) }
            intent.getStringExtra(EXTRA_BODY)?.let { bodyEt.setText(it) }
            titleEt.requestFocus()
        }
        applyChecklistMode()
        updateMenu(); updateColor()

        toolbar.setNavigationOnClickListener { finish() }
        toolbar.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.action_mic -> dictation.start()
                R.id.action_pin -> { pinned = !pinned; updateMenu()
                    Snackbar.make(toolbar, if (pinned) "Pinned" else "Unpinned", Snackbar.LENGTH_SHORT).show() }
                R.id.action_color -> pickColor()
                R.id.action_checklist -> toggleChecklist()
                R.id.action_share -> share()
                R.id.action_archive -> { archived = !archived; save(); finish() }
                R.id.action_delete -> confirmDelete()
                else -> return@setOnMenuItemClickListener false
            }
            true
        }

        val wantsDictation = savedInstanceState == null &&
                (intent.getBooleanExtra(EXTRA_DICTATE, false) || intent.action == ACTION_DICTATE)
        if (wantsDictation) {
            (if (checklist) null else bodyEt)?.requestFocus()
            bodyEt.post { dictation.start() }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent) = volumeKeys.onKeyDown(keyCode, event) || super.onKeyDown(keyCode, event)
    override fun onKeyLongPress(keyCode: Int, event: KeyEvent) = volumeKeys.onKeyLongPress(keyCode) || super.onKeyLongPress(keyCode, event)
    override fun onKeyUp(keyCode: Int, event: KeyEvent) = volumeKeys.onKeyUp(keyCode, event) || super.onKeyUp(keyCode, event)

    override fun onDestroy() { dictation.release(); super.onDestroy() }

    private fun trackFocus(et: EditText) {
        et.setOnFocusChangeListener { v, has -> if (has) lastFocused = v as EditText }
    }

    /** Insert dictated text at the cursor of the last focused field (body / item by default). */
    private fun insertAtCursor(text: String) {
        val target = lastFocused?.takeIf { it.isAttachedToWindow }
            ?: if (checklist) addRow(CheckItem("", false), focus = true) else bodyEt
        val e = target.text
        val start = target.selectionStart.coerceAtLeast(0).coerceAtMost(e.length)
        val end = target.selectionEnd.coerceAtLeast(start).coerceAtMost(e.length)
        val before = if (start > 0 && !e[start - 1].isWhitespace()) " " else ""
        val after = if (end < e.length && !e[end].isWhitespace()) " " else ""
        val ins = before + (if (start > 0 && e.substring(0, start).trimEnd().let { it.isNotEmpty() && it.last() !in ".!?\n" })
            (if (text.startsWith("I ") || text.startsWith("I'") || text == "I") text else text.replaceFirstChar { it.lowercase() }) else text) + after
        e.replace(start, end, ins)
        target.requestFocus()
        target.setSelection((start + ins.length - after.length).coerceAtMost(target.text.length))
    }

    // ---------------------------------------------------------------- checklist

    private fun setChecklistItems(items: List<CheckItem>) {
        listBox.removeAllViews()
        items.forEach { addRow(it, focus = false) }
    }

    private fun addRow(item: CheckItem, focus: Boolean, index: Int = -1): EditText {
        val row = LayoutInflater.from(this).inflate(R.layout.item_check, listBox, false)
        val cb = row.findViewById<MaterialCheckBox>(R.id.check)
        val et = row.findViewById<EditText>(R.id.text)
        et.setText(item.text)
        cb.isChecked = item.checked
        strike(et, item.checked)
        cb.setOnCheckedChangeListener { _, c -> strike(et, c) }
        trackFocus(et)
        et.imeOptions = EditorInfo.IME_ACTION_NEXT
        et.setRawInputType(EditorInfo.TYPE_CLASS_TEXT or EditorInfo.TYPE_TEXT_FLAG_CAP_SENTENCES)
        et.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_NEXT || action == EditorInfo.IME_ACTION_DONE) {
                addRow(CheckItem("", false), focus = true, index = listBox.indexOfChild(row) + 1); true
            } else false
        }
        row.findViewById<ImageButton>(R.id.remove).setOnClickListener {
            if (lastFocused == et) lastFocused = null
            listBox.removeView(row)
        }
        if (index in 0..listBox.childCount) listBox.addView(row, index) else listBox.addView(row)
        if (focus) et.requestFocus()
        return et
    }

    private fun strike(et: EditText, on: Boolean) {
        et.paintFlags = if (on) et.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG else et.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
        et.alpha = if (on) 0.6f else 1f
    }

    private fun currentItems(): List<CheckItem> = (0 until listBox.childCount).map {
        val row = listBox.getChildAt(it)
        CheckItem(row.findViewById<EditText>(R.id.text).text.toString(), row.findViewById<MaterialCheckBox>(R.id.check).isChecked)
    }

    private fun toggleChecklist() {
        if (!checklist) {
            val lines = bodyEt.text.toString().lines().map { it.trim().removePrefix("☐").removePrefix("☑").removePrefix("-").trim() }
                .filter { it.isNotEmpty() }
            setChecklistItems(lines.map { CheckItem(it, false) })
            if (lines.isEmpty()) addRow(CheckItem("", false), focus = true)
            bodyEt.setText("")
            checklist = true
        } else {
            bodyEt.setText(currentItems().filter { it.text.isNotBlank() }.joinToString("\n") { it.text })
            listBox.removeAllViews()
            checklist = false
        }
        lastFocused = null
        applyChecklistMode(); updateMenu()
    }

    private fun applyChecklistMode() {
        bodyEt.visibility = if (checklist) View.GONE else View.VISIBLE
        listBox.visibility = if (checklist) View.VISIBLE else View.GONE
        addItem.visibility = listBox.visibility
    }

    // ---------------------------------------------------------------- pin / colour / share

    private fun updateMenu() {
        toolbar.menu.findItem(R.id.action_pin)?.apply {
            title = getString(if (pinned) R.string.unpin else R.string.pin)
            icon?.mutate()?.alpha = if (pinned) 255 else 110
        }
        toolbar.menu.findItem(R.id.action_checklist)?.title = getString(if (checklist) R.string.to_text else R.string.to_checklist)
        toolbar.menu.findItem(R.id.action_archive)?.title = getString(if (archived) R.string.unarchive else R.string.archive)
    }

    private fun updateColor() {
        colorBar.visibility = if (color == 0) View.GONE else View.VISIBLE
        colorBar.setBackgroundColor(NoteColors.palette[color])
    }

    private fun pickColor() {
        val pad = (12 * resources.displayMetrics.density).toInt()
        val row = LinearLayout(this).apply { setPadding(pad * 2, pad, pad * 2, 0); orientation = LinearLayout.HORIZONTAL }
        val scroll = android.widget.HorizontalScrollView(this).apply { addView(row) }
        val dialog = MaterialAlertDialogBuilder(this).setTitle(R.string.colour).setView(scroll)
            .setNegativeButton(android.R.string.cancel, null).create()
        NoteColors.palette.forEachIndexed { i, c ->
            row.addView(ImageView(this).apply {
                val size = (40 * resources.displayMetrics.density).toInt()
                layoutParams = LinearLayout.LayoutParams(size, size).apply { marginEnd = pad }
                setImageResource(if (i == 0) R.drawable.ic_close else R.drawable.color_dot)
                if (i != 0) imageTintList = ColorStateList.valueOf(c)
                contentDescription = NoteColors.names[i]
                alpha = if (i == color) 1f else 0.75f
                scaleX = if (i == color) 1.15f else 1f; scaleY = scaleX
                setOnClickListener { color = i; updateColor(); dialog.dismiss() }
            })
        }
        dialog.show()
    }

    private fun buildNote() = Note(
        id = noteId, title = titleEt.text.toString(),
        body = if (checklist) "" else bodyEt.text.toString(),
        updatedAt = System.currentTimeMillis(), createdAt = createdAt,
        pinned = pinned, color = color, archived = archived, checklist = checklist,
        items = if (checklist) currentItems() else emptyList(),
    )

    private fun share() {
        val text = buildNote().shareText()
        if (text.isBlank()) return
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            titleEt.text.toString().takeIf { it.isNotBlank() }?.let { putExtra(Intent.EXTRA_SUBJECT, it) }
        }, getString(R.string.share)))
    }

    private fun confirmDelete() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Delete note?")
            .setMessage("This can't be undone.")
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                if (noteId != 0L) NoteStore.delete(this, noteId)
                deleted = true
                finish()
            }
            .show()
    }

    // ---------------------------------------------------------------- save

    private fun save() {
        if (deleted) return
        val n = buildNote()
        val existing = noteId.takeIf { it != 0L }?.let { NoteStore.get(this, it) }
        if (existing == null && n.isEmpty) return
        if (existing != null) {
            val contentSame = existing.title == n.title && existing.body == n.body && existing.items == n.items &&
                    existing.checklist == n.checklist
            val metaSame = existing.pinned == n.pinned && existing.color == n.color && existing.archived == n.archived
            if (contentSame && metaSame) return
            // Only bump "edited" time when the content changed.
            noteId = NoteStore.upsert(this, n.copy(createdAt = existing.createdAt, updatedAt = existing.updatedAt), touch = !contentSame).id
        } else {
            noteId = NoteStore.upsert(this, n.copy(id = 0L)).id
        }
        createdAt = NoteStore.get(this, noteId)?.createdAt ?: createdAt
    }

    override fun onPause() {
        super.onPause()
        save()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong(EXTRA_NOTE_ID, noteId)
        outState.putBoolean("pinned", pinned); outState.putInt("color", color)
        outState.putBoolean("archived", archived); outState.putBoolean("checklist", checklist)
        outState.putLong("createdAt", createdAt)
        val items = currentItems()
        outState.putStringArrayList("itemTexts", ArrayList(items.map { it.text }))
        outState.putBooleanArray("itemChecks", items.map { it.checked }.toBooleanArray())
    }
}
