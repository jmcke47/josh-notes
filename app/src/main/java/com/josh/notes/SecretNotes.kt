package com.josh.notes

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-256-GCM with a non-exportable Android Keystore key. Format: "v1:" + base64(iv[12] || ciphertext+tag).
 * The key never leaves the device's keystore, so secret notes in a Drive backup can only be opened on this install.
 */
object SecretCrypto {
    private const val ALIAS = "josh_notes_secret_v1"
    private const val PREFIX = "v1:"
    private val AAD = "com.josh.notes/secret/v1".toByteArray()

    /** Test hook: Robolectric has no AndroidKeyStore, so tests supply a software key. */
    @androidx.annotation.VisibleForTesting @JvmStatic var keyOverride: SecretKey? = null

    @Synchronized
    private fun key(): SecretKey {
        keyOverride?.let { return it }
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        kg.init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build())
        return kg.generateKey()
    }

    fun encrypt(plain: ByteArray): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key())
        c.updateAAD(AAD)
        val ct = c.doFinal(plain)
        return PREFIX + Base64.encodeToString(c.iv + ct, Base64.NO_WRAP)
    }

    fun decrypt(blob: String): ByteArray {
        require(blob.startsWith(PREFIX)) { "Unknown secret format" }
        val raw = Base64.decode(blob.substring(PREFIX.length), Base64.NO_WRAP)
        require(raw.size > 12 + 16) { "Secret data too short" }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, raw, 0, 12))
        c.updateAAD(AAD)
        return c.doFinal(raw, 12, raw.size - 12)
    }

    /** True if the keystore key can be created/used (checked before a note is made secret). */
    fun works(): Boolean = runCatching { String(decrypt(encrypt("ok".toByteArray()))) == "ok" }.getOrDefault(false)
}

/** Sealing/opening of secret notes. Sealed notes keep only metadata (dates, pin, colour, reminder) in plain text. */
object SecretNotes {
    private fun hasPlain(n: Note) = n.title.isNotEmpty() || n.body.isNotEmpty() || n.items.isNotEmpty() ||
        n.attachments.any { it.name.isNotEmpty() || it.transcript.isNotEmpty() }

    /** Encrypts the content of a secret note (no-op when already sealed); clears [Note.enc] of non-secret notes. */
    fun seal(n: Note): Note {
        if (!n.secret) return if (n.enc.isEmpty()) n else n.copy(enc = "")
        if (n.enc.isNotEmpty() && !hasPlain(n)) return n
        val payload = JSONObject().put("title", n.title).put("body", n.body)
            .put("items", JSONArray().apply { n.items.forEach { put(JSONObject().put("text", it.text).put("checked", it.checked)) } })
            .put("attachments", JSONArray().apply { n.attachments.forEach { put(it.toJson()) } })
        return n.copy(
            title = "", body = "", items = emptyList(),
            // File names on disk stay (needed for backups/cleanup); display names and transcripts are encrypted.
            attachments = n.attachments.map { it.copy(name = "", transcript = "") },
            enc = SecretCrypto.encrypt(payload.toString().toByteArray(Charsets.UTF_8)),
        )
    }

    /** Decrypted copy of a sealed secret note (throws if this device can't decrypt it). */
    fun open(n: Note): Note {
        if (!n.secret || n.enc.isEmpty()) return n
        val o = JSONObject(String(SecretCrypto.decrypt(n.enc), Charsets.UTF_8))
        val items = o.optJSONArray("items")?.let { a ->
            (0 until a.length()).mapNotNull { a.optJSONObject(it) }.map { CheckItem(it.optString("text"), it.optBoolean("checked")) }
        } ?: emptyList()
        val atts = o.optJSONArray("attachments")?.let { a ->
            (0 until a.length()).mapNotNull { a.optJSONObject(it) }.mapNotNull { runCatching { Attachment.fromJson(it) }.getOrNull() }
        } ?: emptyList()
        // Attachment list from the plain record is authoritative for which files exist.
        val byId = atts.associateBy { it.id }
        return n.copy(title = o.optString("title"), body = o.optString("body"), items = items,
            attachments = n.attachments.map { byId[it.id] ?: it }, enc = "")
    }

    // Short in-memory grace so a rotation doesn't ask again (never persisted).
    private val graceUntil = HashMap<Long, Long>()
    fun keepUnlocked(id: Long, ms: Long = 15_000L) = synchronized(graceUntil) { graceUntil[id] = android.os.SystemClock.elapsedRealtime() + ms }
    fun inGrace(id: Long): Boolean = synchronized(graceUntil) {
        (graceUntil.remove(id) ?: 0L) > android.os.SystemClock.elapsedRealtime()
    }
}
