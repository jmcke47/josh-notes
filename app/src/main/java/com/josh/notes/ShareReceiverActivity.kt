package com.josh.notes

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/** Receives text shared from other apps (ACTION_SEND text/plain) and opens it as a new note. */
class ShareReceiverActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = intent?.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString().orEmpty()
        val subject = intent?.getStringExtra(Intent.EXTRA_SUBJECT).orEmpty()
        if (intent?.action == Intent.ACTION_SEND && (text.isNotBlank() || subject.isNotBlank())) {
            // Save straight away so nothing is lost even if the editor is dismissed.
            val note = NoteStore.upsert(this, Note(0L, subject.take(200), text, System.currentTimeMillis()))
            startActivity(Intent(this, EditNoteActivity::class.java)
                .putExtra(EditNoteActivity.EXTRA_NOTE_ID, note.id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        finish()
    }
}
