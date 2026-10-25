package com.josh.notes

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.IntentCompat

/**
 * "Share to Josh Notes": text, links and images (ACTION_SEND / ACTION_SEND_MULTIPLE). Creates a new note or appends
 * to a chosen one; images are copied in as attachments (the share grant only lasts while this screen is open).
 */
class ShareReceiverActivity : AppCompatActivity() {

    data class Shared(val subject: String, val text: String, val streams: List<Uri>) {
        val isEmpty get() = subject.isBlank() && text.isBlank() && streams.isEmpty()
    }

    companion object {
        fun parse(intent: Intent?): Shared {
            if (intent == null || (intent.action != Intent.ACTION_SEND && intent.action != Intent.ACTION_SEND_MULTIPLE)) return Shared("", "", emptyList())
            val texts = buildList {
                intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.let { add(it.toString()) }
                intent.getCharSequenceArrayListExtra(Intent.EXTRA_TEXT)?.forEach { add(it.toString()) }
            }.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
            val streams = buildList {
                IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.let { add(it) }
                IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.let { addAll(it) }
                intent.clipData?.let { c -> for (i in 0 until c.itemCount) c.getItemAt(i).uri?.let { add(it) } }
            }.distinct().filter { it.scheme == "content" }
            return Shared(intent.getStringExtra(Intent.EXTRA_SUBJECT).orEmpty().trim().take(200), texts.joinToString("\n\n"), streams)
        }

        /** Text appended to an existing note. */
        fun appendTo(n: Note, s: Shared): Note {
            val add = listOf(s.subject, s.text).filter { it.isNotBlank() }.joinToString("\n")
            if (add.isBlank()) return n
            return if (n.checklist) n.copy(items = n.items + add.lines().filter { it.isNotBlank() }.map { CheckItem(it.trim(), false) })
            else n.copy(body = if (n.body.isBlank()) add else n.body.trimEnd() + "\n\n" + add)
        }
    }

    private lateinit var shared: Shared

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(if (AppSettings.oldSchool(this)) R.style.Theme_JoshNotes_OldSchool_Translucent else R.style.Theme_JoshNotes_Translucent)
        super.onCreate(savedInstanceState)
        shared = parse(intent)
        if (shared.isEmpty) { finish(); return }
        if (savedInstanceState != null) return
        val what = when {
            shared.streams.isNotEmpty() && shared.text.isNotBlank() -> "text and ${shared.streams.size} image(s)"
            shared.streams.isNotEmpty() -> "${shared.streams.size} image(s)"
            else -> "text"
        }
        osDialog(this).setTitle("Save to Josh Notes")
            .setMessage("Save the shared $what as:")
            .setPositiveButton("New note") { _, _ -> save(null) }
            .setNeutralButton("Add to a note…") { _, _ -> pickNote() }
            .setNegativeButton(android.R.string.cancel) { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun pickNote() {
        if (AppLock.needsUnlock(this)) {
            AppLock.prompt(this, "Unlock Josh Notes", onSuccess = { AppLock.markUnlocked(); pickNote() }, onFail = { finish() })
            return
        }
        // Secret notes are not offered (their content is encrypted until opened in the editor).
        val notes = NoteStore.all(this).filter { !it.inTrash && !it.secret }.sortedFor(SortMode.EDITED).take(50)
        if (notes.isEmpty()) { save(null); return }
        osDialog(this).setTitle("Add to which note?")
            .setItems(notes.map { it.displayTitle.ifBlank { getString(R.string.untitled) } }.toTypedArray()) { _, i -> save(notes[i].id) }
            .setNegativeButton(android.R.string.cancel) { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun save(targetId: Long?) {
        val app = applicationContext
        Thread {
            val r = runCatching {
                val base = targetId?.let { NoteStore.get(app, it) }
                val note = if (base != null) NoteStore.upsert(app, appendTo(base, shared))
                    else NoteStore.upsert(app, Note(0L, shared.subject, shared.text, System.currentTimeMillis()))
                var failed = 0
                val added = shared.streams.mapNotNull { u ->
                    runCatching { Attachments.importUri(this, note.id, u) }.onFailure { failed++ }.getOrNull()
                }
                if (added.isNotEmpty()) NoteStore.update(app, note.id) { it.copy(attachments = it.attachments + added) }
                note.id to failed
            }
            runOnUiThread {
                r.onSuccess { (id, failed) ->
                    if (failed > 0) Toast.makeText(app, "$failed attachment(s) couldn't be copied", Toast.LENGTH_LONG).show()
                    startActivity(Intent(this, EditNoteActivity::class.java).putExtra(EditNoteActivity.EXTRA_NOTE_ID, id)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }.onFailure { Toast.makeText(app, "Couldn't save: ${it.message}", Toast.LENGTH_LONG).show() }
                finish()
            }
        }.start()
    }
}
