package com.josh.notes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UrlSanitizeTest {
    private val good = "https://github.com/jmcke47/josh-notes/releases/latest/download/update.json"

    @Test fun exactMalformedStringFromPhone() {
        assertEquals(good, UpdateHttp.sanitizeUrl("https://github . com/jmcke47/jos h-notes/releases/latest /download/update.json."))
    }

    @Test fun whitespaceNewlinesInvisiblesQuotes() {
        assertEquals(good, UpdateHttp.sanitizeUrl("  \"$good\"\n"))
        assertEquals(good, UpdateHttp.sanitizeUrl("https://git\u200Bhub.com/jmcke47/josh-notes/releases/latest/download/update.json\u00A0"))
        assertEquals(good, UpdateHttp.sanitizeUrl("github.com/jmcke47/josh-notes/releases/latest/download/update.json"))
        assertEquals(good, UpdateHttp.sanitizeUrl("HTTPS://GitHub.com/jmcke47/josh-notes/releases/latest/download/update.json,"))
    }

    @Test fun keepsQueryEncoding() {
        val u = "https://release-assets.githubusercontent.com/x/y?sp=r&se=2026-10-20T00%3A00%3A07Z&sig=Ab%3D"
        assertEquals(u, UpdateHttp.sanitizeUrl(u))
    }

    @Test fun rejectsInvalid() {
        assertNull(UpdateHttp.sanitizeUrl(""))
        assertNull(UpdateHttp.sanitizeUrl("   "))
        assertNull(UpdateHttp.sanitizeUrl("http://github.com/x"))
        assertNull(UpdateHttp.sanitizeUrl("https://localhost/x"))
        assertNull(UpdateHttp.sanitizeUrl("https:///nohost"))
        assertNull(UpdateHttp.sanitizeUrl("ftp://github.com/x"))
        assertNull(UpdateHttp.sanitizeUrl("https://user:pw@github.com/x"))
        assertNull(UpdateHttp.sanitizeUrl(null))
    }

    @Test fun builtInDefaultIsValid() {
        assertEquals(BuildConfig.UPDATE_MANIFEST_URL, UpdateHttp.sanitizeUrl(BuildConfig.UPDATE_MANIFEST_URL))
    }
}
