package com.josh.notes

import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException

/** Plain java.net HTTP helper (no Android deps, unit-testable). Always call off the main thread. */
object UpdateHttp {
    var userAgent = "JoshNotes"

    /**
     * Opens [url], following up to 8 redirects manually (also across hosts, e.g. github.com ->
     * release-assets.githubusercontent.com). Refuses https->http downgrades. Retries transient
     * DNS/timeout failures twice with a short backoff.
     */
    fun open(url: String): HttpURLConnection {
        var last: IOException? = null
        for (attempt in 0 until 3) {
            try { return openOnce(url) } catch (e: UnknownHostException) { last = e }
            catch (e: SocketTimeoutException) { last = e }
            Thread.sleep(800L * (attempt + 1))
        }
        throw last!!
    }

    private fun openOnce(url: String): HttpURLConnection {
        var current = URL(sanitizeUrl(url) ?: throw java.net.MalformedURLException("Invalid update URL: \"$url\""))
        for (hop in 0..8) {
            val c = current.openConnection() as HttpURLConnection
            c.connectTimeout = 20000; c.readTimeout = 30000
            c.instanceFollowRedirects = false
            c.setRequestProperty("Cache-Control", "no-cache")
            c.setRequestProperty("User-Agent", userAgent)
            c.setRequestProperty("Accept", "*/*")
            val code = c.responseCode
            if (code in 300..399) {
                val loc = c.getHeaderField("Location") ?: throw IOException("HTTP $code redirect without Location")
                c.disconnect()
                val next = URL(current, loc)
                if (current.protocol == "https" && next.protocol != "https")
                    throw IOException("Refusing insecure redirect to ${next.host}")
                current = next
                continue
            }
            if (code !in 200..299) { c.disconnect(); throw HttpStatusException(code, current.host) }
            return c
        }
        throw IOException("Too many redirects")
    }

    fun readText(url: String): String {
        val c = open(url)
        try { return c.inputStream.bufferedReader().use { it.readText() } } finally { c.disconnect() }
    }

    /**
     * Cleans a user-entered / stored URL: removes all whitespace and invisible characters
     * (phone keyboards/autocorrect insert spaces like "github . com"), strips surrounding quotes/brackets
     * and trailing punctuation, then validates it is an absolute https URL with a real host.
     * Returns the normalized URL, or null if it isn't usable.
     */
    fun sanitizeUrl(raw: String?): String? {
        if (raw == null) return null
        var s = raw.replace(Regex("[\\s\u00A0\u1680\u2000-\u200F\u2028-\u202F\u205F\u2060\u3000\uFEFF]"), "")
        s = s.trim('"', '\'', '<', '>', '(', ')', '[', ']', '`')
        s = s.trimEnd('.', ',', ';', ':', '!', '?')
        if (s.isEmpty()) return null
        if (!s.contains("://")) s = "https://$s"
        return try {
            val u = java.net.URI(s)
            val host = u.host
            if (!u.scheme.equals("https", ignoreCase = true)) return null
            if (host.isNullOrEmpty() || !host.contains('.') || host.startsWith('.') || host.endsWith('.')) return null
            if (!host.all { it.isLetterOrDigit() || it == '.' || it == '-' }) return null
            if (u.rawUserInfo != null) return null
            "https://" + host.lowercase() + (if (u.port > 0) ":${u.port}" else "") +
                (u.rawPath?.ifEmpty { "/" } ?: "/") + (u.rawQuery?.let { "?$it" } ?: "")
        } catch (_: Exception) { null }
    }

    class HttpStatusException(val code: Int, val host: String) : IOException("HTTP $code from $host")
}
