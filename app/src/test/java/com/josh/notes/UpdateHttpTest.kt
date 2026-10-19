package com.josh.notes

import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test

/** Live network test: GitHub latest/download -> 302 -> 302 -> release-assets.githubusercontent.com. */
class UpdateHttpTest {
    @Test fun followsGithubRedirectsAcrossHosts() {
        val o = JSONObject(UpdateHttp.readText(BuildConfig.UPDATE_MANIFEST_URL))
        assertTrue(o.getLong("versionCode") >= 1)
        val c = UpdateHttp.open(o.getString("apkUrl"))
        try {
            assertTrue(c.url.host.endsWith("githubusercontent.com"))
            assertTrue(c.contentLengthLong > 1_000_000)
        } finally { c.disconnect() }
    }
}
