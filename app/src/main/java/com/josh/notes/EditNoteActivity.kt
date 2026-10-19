package com.josh.notes

import android.os.Bundle
import android.view.View
import android.widget.EditText
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class EditNoteActivity : AppCompatActivity() {

    companion object { const val EXTRA_NOTE_ID = "note_id" }

    private var noteId: Long? = null
    private var deleted = false
    private lateinit var titleEt: EditText
    private lateinit var bodyEt: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit)
        val root = findViewById<View>(R.id.root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime()
                    or WindowInsetsCompat.Type.displayCutout())
            v.updatePadding(left = b.left, top = b.top, right = b.right, bottom = b.bottom)
            WindowInsetsCompat.CONSUMED
        }

        titleEt = findViewById(R.id.title)
        bodyEt = findViewById(R.id.body)

        noteId = savedInstanceState?.takeIf { it.containsKey(EXTRA_NOTE_ID) }?.getLong(EXTRA_NOTE_ID)
            ?: intent.takeIf { it.hasExtra(EXTRA_NOTE_ID) }?.getLongExtra(EXTRA_NOTE_ID, -1L)?.takeIf { it >= 0 }

        if (savedInstanceState == null) {
            val note = noteId?.let { NoteStore.get(this, it) }
            if (noteId != null && note == null) noteId = null // note was deleted elsewhere
            note?.let { titleEt.setText(it.title); bodyEt.setText(it.body) }
            if (note == null) titleEt.requestFocus()
        }

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }
        toolbar.setOnMenuItemClickListener {
            if (it.itemId == R.id.action_delete) { confirmDelete(); true } else false
        }
    }

    private fun confirmDelete() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Delete note?")
            .setMessage("This can't be undone.")
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                noteId?.let { NoteStore.delete(this, it) }
                deleted = true
                finish()
            }
            .show()
    }

    /** Auto-save whenever the screen is left. */
    override fun onPause() {
        super.onPause()
        if (deleted) return
        val title = titleEt.text.toString()
        val body = bodyEt.text.toString()
        val existing = noteId?.let { NoteStore.get(this, it) }
        if (existing == null && title.isBlank() && body.isBlank()) return
        if (existing != null && existing.title == title && existing.body == body) return
        noteId = NoteStore.save(this, noteId, title, body).id
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        noteId?.let { outState.putLong(EXTRA_NOTE_ID, it) }
    }
}
