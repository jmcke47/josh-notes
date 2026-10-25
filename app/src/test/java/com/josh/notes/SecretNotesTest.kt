package com.josh.notes

import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.zip.ZipFile
import javax.crypto.KeyGenerator

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = NotesApp::class)
class SecretNotesTest {
    private val ctx get() = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val SECRET_TITLE = "Locker code"
    private val SECRET_BODY = "4-7-1-9 behind the poster"

    @Before fun setUp() {
        SecretCrypto.keyOverride = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        NoteStore.replaceAll(ctx, listOf(Note(1, "Plain", "visible text", 1000L)))
        NoteStore.all(ctx).filter { it.secret }.forEach { NoteStore.deleteForever(ctx, it.id) }
    }

    @After fun tearDown() { SecretCrypto.keyOverride = null }

    @Test fun aesGcmRoundTripAndTamperDetection() {
        val a = SecretCrypto.encrypt("hello".toByteArray())
        val b = SecretCrypto.encrypt("hello".toByteArray())
        assertTrue(a.startsWith("v1:"))
        assertNotEquals("fresh IV every time", a, b)
        assertEquals("hello", String(SecretCrypto.decrypt(a)))
        val raw = android.util.Base64.decode(a.removePrefix("v1:"), android.util.Base64.NO_WRAP)
        raw[raw.size - 1] = (raw[raw.size - 1].toInt() xor 1).toByte()
        try {
            SecretCrypto.decrypt("v1:" + android.util.Base64.encodeToString(raw, android.util.Base64.NO_WRAP))
            fail("tampered data must not decrypt")
        } catch (e: javax.crypto.AEADBadTagException) { /* expected */ }
        // A different key can't open it.
        SecretCrypto.keyOverride = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        assertTrue(runCatching { SecretCrypto.decrypt(a) }.isFailure)
    }

    private fun saveSecret(): Note = NoteStore.upsert(ctx, Note(0L, SECRET_TITLE, SECRET_BODY, System.currentTimeMillis(), secret = true,
        checklist = false, attachments = listOf(Attachment("a1", "f1.jpg", "holiday-receipt.jpg", "image/jpeg", 10, "image", transcript = "spoken words"))))

    @Test fun encryptedAtRestAndHiddenEverywhere() {
        val saved = saveSecret()
        val onDisk = File(ctx.filesDir, "notes.json").readText()
        for (plain in listOf(SECRET_TITLE, SECRET_BODY, "holiday-receipt", "spoken words")) assertFalse("'$plain' leaked to disk", onDisk.contains(plain))
        assertTrue(onDisk.contains("f1.jpg")) // the file reference stays so backups/cleanup keep the file

        val stored = NoteStore.get(ctx, saved.id)!!
        assertEquals("Secret note", stored.displayTitle)
        assertEquals("", stored.contentText)
        assertEquals("", stored.shareText())
        assertTrue(stored.tags.isEmpty())
        assertFalse(stored.isEmpty)

        val open = SecretNotes.open(stored)
        assertEquals(SECRET_TITLE, open.title); assertEquals(SECRET_BODY, open.body)
        assertEquals("holiday-receipt.jpg", open.attachments.single().name)

        // Metadata-only change (pin from the list) keeps the encrypted content.
        val pinned = NoteStore.update(ctx, saved.id) { it.copy(pinned = true) }!!
        assertEquals(stored.enc, pinned.enc)
        assertEquals(SECRET_BODY, SecretNotes.open(pinned).body)

        // Removing "secret" stores plain text again and drops the blob.
        val plain = NoteStore.upsert(ctx, open.copy(secret = false))
        assertEquals("", plain.enc); assertEquals(SECRET_BODY, NoteStore.get(ctx, plain.id)!!.body)
    }

    @Test fun backupsLeaveSecretsOutOrKeepThemEncrypted() {
        val s = saveSecret()
        assertFalse(NoteStore.exportJson(ctx).contains("\"secret\":true"))
        val folder = File(ctx.cacheDir, "f.zip"); Backup.writeArchive(ctx, folder, includeSecret = false)
        val drive = File(ctx.cacheDir, "d.zip"); Backup.writeArchive(ctx, drive, includeSecret = true)
        fun notesJson(f: File) = ZipFile(f).use { z -> z.getInputStream(z.getEntry("notes.json")).readBytes().toString(Charsets.UTF_8) }
        assertEquals(1, NoteStore.parse(notesJson(folder)).size)
        val d = notesJson(drive)
        assertEquals(2, NoteStore.parse(d).size)
        assertFalse(d.contains(SECRET_BODY)); assertTrue(d.contains("\"enc\":\"v1:"))

        // Restore-replace from a plain backup keeps the local secret note.
        NoteStore.replaceAll(ctx, NoteStore.parse(notesJson(folder)))
        assertTrue(NoteStore.get(ctx, s.id)!!.secret)
    }

    @Test fun editorStaysLockedUntilAuthenticated() {
        val s = saveSecret()
        AppLock.availableOverride = true
        var approve: (() -> Unit)? = null
        AppLock.promptOverride = { ok, _ -> approve = ok }
        try {
            val ac = org.robolectric.Robolectric.buildActivity(EditNoteActivity::class.java,
                android.content.Intent(ctx, EditNoteActivity::class.java).putExtra(EditNoteActivity.EXTRA_NOTE_ID, s.id)).setup()
            val a = ac.get()
            val title = a.findViewById<android.widget.EditText>(R.id.title)
            assertEquals("", title.text.toString())
            assertTrue(a.window.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_SECURE != 0)
            // Leaving while locked must not overwrite the encrypted content.
            ac.pause(); ac.resume()
            assertEquals(SECRET_BODY, SecretNotes.open(NoteStore.get(ctx, s.id)!!).body)
            approve!!.invoke()
            assertEquals(SECRET_TITLE, title.text.toString())
            title.setText("$SECRET_TITLE (edited)")
            ac.pause()
            val stored = NoteStore.get(ctx, s.id)!!
            assertEquals("", stored.title)
            assertEquals("$SECRET_TITLE (edited)", SecretNotes.open(stored).title)
            ac.stop().destroy()
        } finally { AppLock.availableOverride = null; AppLock.promptOverride = null }
    }

    @Test fun widgetRowShowsOnlySecretNote() {
        ctx.getSharedPreferences("settings", 0).edit().putString("theme", "LIGHT").commit()
        saveSecret()
        val f = com.josh.notes.widget.NotesWidgetService.Factory(ctx, 1).apply { onDataSetChanged() }
        val idx = f.rows.indexOfFirst { (it as com.josh.notes.widget.NotesWidgetService.Row.NoteRow).note.secret }
        val v = f.getViewAt(idx).apply(ctx, FrameLayout(ctx))
        assertEquals("Secret note", v.findViewById<TextView>(R.id.widget_item_title).text.toString())
        assertEquals(android.view.View.GONE, v.findViewById<TextView>(R.id.widget_item_body).visibility)
    }
}
