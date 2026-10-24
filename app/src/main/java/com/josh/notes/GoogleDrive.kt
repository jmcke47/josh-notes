package com.josh.notes

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.ClearTokenRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Tasks
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * Backups straight into the app's private Google Drive "appDataFolder" (hidden from the Drive UI, only this app can
 * read it). Auth: Google Identity Services AuthorizationClient with the drive.appdata scope. Needs an Android OAuth
 * client (package + signing SHA-1) in the user's Google Cloud project; no client secret is embedded.
 */
object GoogleDrive {
    const val SCOPE = "https://www.googleapis.com/auth/drive.appdata"
    private const val API = "https://www.googleapis.com/drive/v3"
    private const val UPLOAD = "https://www.googleapis.com/upload/drive/v3/files?uploadType=resumable&fields=id,name,size,modifiedTime"

    data class Item(val id: String, val name: String, val size: Long, val modified: Long, val device: String, val maxUpdatedAt: Long)

    /** Thrown when the user has to grant access interactively (open Settings → Google Drive). */
    class NeedsConsent(val result: AuthorizationResult?) : Exception("Google Drive needs you to sign in again (open Settings → Google Drive)")

    private fun p(ctx: Context) = ctx.applicationContext.getSharedPreferences("gdrive", 0)
    fun enabled(ctx: Context) = p(ctx).getBoolean("enabled", false)
    fun setEnabled(ctx: Context, on: Boolean) { p(ctx).edit().putBoolean("enabled", on).apply(); Backup.schedule(ctx) }
    fun autoEnabled(ctx: Context) = enabled(ctx) && p(ctx).getBoolean("auto", true)
    fun setAuto(ctx: Context, on: Boolean) { p(ctx).edit().putBoolean("auto", on).apply(); Backup.schedule(ctx) }
    fun account(ctx: Context) = p(ctx).getString("account", null)
    fun lastOk(ctx: Context) = p(ctx).getLong("last_ok", 0L)
    fun lastName(ctx: Context) = p(ctx).getString("last_name", null)
    fun lastCount(ctx: Context) = p(ctx).getInt("last_count", 0)
    fun lastError(ctx: Context) = p(ctx).getString("last_error", null)
    fun lastErrorAt(ctx: Context) = p(ctx).getLong("last_error_at", 0L)

    private val http by lazy {
        OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS).retryOnConnectionFailure(true).build()
    }

    private fun request() = AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(SCOPE))).build()

    /** Human-readable explanation for auth failures, especially "OAuth client not set up yet". */
    fun explain(e: Throwable): String {
        val api = e as? ApiException ?: e.cause as? ApiException
        return when (api?.statusCode) {
            CommonStatusCodes.DEVELOPER_ERROR ->
                "Google sign-in isn't set up for this app yet (DEVELOPER_ERROR). An Android OAuth client for " +
                    "com.josh.notes with this app's SHA-1 must exist in Google Cloud, with the Drive API enabled and " +
                    "your Gmail added as a test user."
            CommonStatusCodes.NETWORK_ERROR -> "No connection to Google — check Wi-Fi/mobile data and try again."
            CommonStatusCodes.CANCELED, 12501 -> "Google sign-in was cancelled."
            CommonStatusCodes.SIGN_IN_REQUIRED -> "Sign in to a Google account on this phone first."
            null -> e.message ?: e.javaClass.simpleName
            else -> "Google sign-in failed (code ${api.statusCode}: ${CommonStatusCodes.getStatusCodeString(api.statusCode)})"
        }
    }

    /**
     * Interactive connect from an Activity. [launchConsent] is called with the PendingIntent's IntentSender when Google
     * needs the user to pick an account / approve access; feed the activity result to [handleConsentResult].
     */
    fun connect(a: Activity, launchConsent: (android.content.IntentSender) -> Unit, done: (Result<String?>) -> Unit) {
        Identity.getAuthorizationClient(a).authorize(request())
            .addOnSuccessListener { r ->
                val pi = r.pendingIntent
                if (r.hasResolution() && pi != null) launchConsent(pi.intentSender)
                else { onAuthorized(a, r); done(Result.success(account(a))) }
            }
            .addOnFailureListener { done(Result.failure(it)) }
    }

    fun handleConsentResult(a: Activity, data: Intent?): Result<String?> = runCatching {
        val r = Identity.getAuthorizationClient(a).getAuthorizationResultFromIntent(data)
        if (r.accessToken == null) throw IllegalStateException("Google didn't grant Drive access")
        onAuthorized(a, r)
        account(a)
    }

    private fun onAuthorized(ctx: Context, r: AuthorizationResult) {
        val email = runCatching { r.toGoogleSignInAccount()?.email }.getOrNull()
        p(ctx).edit().putBoolean("enabled", true).apply { if (email != null) putString("account", email) }.apply()
        Backup.schedule(ctx)
    }

    fun disconnect(ctx: Context) {
        p(ctx).edit().clear().apply()
        Backup.schedule(ctx)
    }

    /** Blocking (worker/background thread). Silent when access was granted before; otherwise [NeedsConsent]. */
    private fun token(ctx: Context): String {
        val r = try { Tasks.await(Identity.getAuthorizationClient(ctx).authorize(request()), 30, TimeUnit.SECONDS) }
        catch (e: java.util.concurrent.ExecutionException) { throw IOException(explain(e.cause ?: e), e.cause) }
        if (r.hasResolution()) throw NeedsConsent(r)
        return r.accessToken ?: throw NeedsConsent(r)
    }

    private fun clearToken(ctx: Context, t: String) {
        runCatching { Tasks.await(Identity.getAuthorizationClient(ctx).clearToken(ClearTokenRequest.builder().setToken(t).build()), 15, TimeUnit.SECONDS) }
    }

    /** Runs [call] with a token, refreshing once on 401. */
    private fun <T> withToken(ctx: Context, call: (String) -> T): T {
        val t = token(ctx)
        return try { call(t) } catch (e: Unauthorized) { clearToken(ctx, t); call(token(ctx)) }
    }

    private class Unauthorized : IOException("Google rejected the access token")

    private fun check(r: Response): Response {
        if (r.code == 401) { r.close(); throw Unauthorized() }
        if (!r.isSuccessful) {
            val body = runCatching { r.body.string() }.getOrDefault("")
            r.close()
            val msg = runCatching { JSONObject(body).getJSONObject("error").getString("message") }.getOrNull() ?: body.take(200)
            throw IOException(when {
                r.code == 403 && body.contains("accessNotConfigured", true) || body.contains("has not been used", true) ->
                    "The Google Drive API isn't enabled in your Google Cloud project yet ($msg)"
                r.code == 403 && body.contains("storageQuotaExceeded", true) -> "Your Google Drive is full"
                else -> "Google Drive error ${r.code}: $msg"
            })
        }
        return r
    }

    private fun rfc3339(s: String): Long = runCatching {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(s)!!.time
    }.getOrDefault(0L)

    fun list(ctx: Context): List<Item> = withToken(ctx) { t ->
        val url = "$API/files?spaces=appDataFolder&pageSize=100&orderBy=modifiedTime%20desc" +
            "&fields=files(id,name,size,modifiedTime,appProperties)&q=" + Uri.encode("trashed=false and name contains '${Backup.PREFIX}'")
        check(http.newCall(Request.Builder().url(url).header("Authorization", "Bearer $t").build()).execute()).use { r ->
            val arr = JSONObject(r.body.string()).optJSONArray("files") ?: return@use emptyList()
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i); val ap = o.optJSONObject("appProperties") ?: JSONObject()
                Item(o.getString("id"), o.getString("name"), o.optString("size", "0").toLongOrNull() ?: 0L, rfc3339(o.optString("modifiedTime")),
                    ap.optString("device"), ap.optString("maxUpdatedAt", "0").toLongOrNull() ?: 0L)
            }.sortedByDescending { it.name }
        }
    }

    /** Builds the same archive as folder backups and uploads it (resumable), then keeps the newest [Backup.KEEP]. */
    @Synchronized
    fun backupNow(ctx: Context): String {
        val name = Backup.PREFIX + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()) + ".zip"
        val tmp = File(ctx.cacheDir, "gd-$name")
        try {
            val count = Backup.writeArchive(ctx, tmp)
            val meta = JSONObject().put("name", name).put("parents", org.json.JSONArray().put("appDataFolder"))
                .put("mimeType", "application/zip")
                .put("appProperties", JSONObject().put("device", Backup.deviceId(ctx))
                    .put("maxUpdatedAt", NoteStore.maxUpdatedAt(ctx).toString()).put("noteCount", count.toString())
                    .put("appVersion", BuildConfig.VERSION_NAME))
            withToken(ctx) { t ->
                val start = check(http.newCall(Request.Builder().url(UPLOAD).header("Authorization", "Bearer $t")
                    .header("X-Upload-Content-Type", "application/zip").header("X-Upload-Content-Length", tmp.length().toString())
                    .post(meta.toString().toRequestBody("application/json; charset=UTF-8".toMediaType())).build()).execute())
                val location = start.use { it.header("Location") } ?: throw IOException("Google Drive didn't return an upload URL")
                check(http.newCall(Request.Builder().url(location).header("Authorization", "Bearer $t")
                    .put(tmp.asRequestBody("application/zip".toMediaType())).build()).execute()).close()
            }
            runCatching { rotate(ctx) }
            p(ctx).edit().putLong("last_ok", System.currentTimeMillis()).putString("last_name", name).putInt("last_count", count)
                .remove("last_error").apply()
            return name
        } catch (e: Exception) {
            p(ctx).edit().putString("last_error", if (e is IOException || e is NeedsConsent) e.message else explain(e))
                .putLong("last_error_at", System.currentTimeMillis()).apply()
            throw e
        } finally { tmp.delete() }
    }

    private fun rotate(ctx: Context) {
        val old = list(ctx).drop(Backup.KEEP)
        if (old.isEmpty()) return
        withToken(ctx) { t ->
            old.forEach { f ->
                http.newCall(Request.Builder().url("$API/files/${f.id}").header("Authorization", "Bearer $t").delete().build()).execute().close()
            }
        }
    }

    private fun download(ctx: Context, item: Item): File {
        val f = File(ctx.cacheDir, "gd-restore-${item.id}.zip")
        withToken(ctx) { t ->
            check(http.newCall(Request.Builder().url("$API/files/${item.id}?alt=media").header("Authorization", "Bearer $t").build()).execute())
                .use { r -> f.outputStream().use { out -> r.body.byteStream().copyTo(out) } }
        }
        return f
    }

    fun restoreMerge(ctx: Context, item: Item): Int { val f = download(ctx, item); try { return Backup.restoreMerge(ctx, Uri.fromFile(f)) } finally { f.delete() } }
    fun restoreReplace(ctx: Context, item: Item): Int { val f = download(ctx, item); try { return Backup.restoreReplace(ctx, Uri.fromFile(f)) } finally { f.delete() } }

    /** Sync offer: newest Drive backup came from another install and has newer edits. Silent (no UI) or null. */
    fun newerBackup(ctx: Context): Item? {
        if (!enabled(ctx)) return null
        val latest = runCatching { list(ctx).firstOrNull() }.getOrNull() ?: return null
        if (latest.name == lastName(ctx) || latest.name == p(ctx).getString("dismissed", null)) return null
        if (latest.device == Backup.deviceId(ctx)) return null
        return if (latest.maxUpdatedAt > NoteStore.maxUpdatedAt(ctx)) latest else null
    }

    fun dismiss(ctx: Context, name: String) = p(ctx).edit().putString("dismissed", name).apply()
}
