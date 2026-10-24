package com.josh.notes

import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Status
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LevelsDriveTest {
    @Test fun experienceCurve() {
        assertEquals(0, Levels.xpForLevel(1))
        assertEquals(83, Levels.xpForLevel(2))
        assertEquals(1154, Levels.xpForLevel(10))
        assertEquals(13_034_431, Levels.xpForLevel(99))
        assertEquals(1, Levels.levelFor(0))
        assertEquals(1, Levels.levelFor(82))
        assertEquals(2, Levels.levelFor(83))
        assertEquals(9, Levels.levelFor(1153))
        assertEquals(10, Levels.levelFor(1154))
        assertEquals(99, Levels.levelFor(200_000_000))
    }

    @Test fun developerErrorIsExplained() {
        val msg = GoogleDrive.explain(ApiException(Status(CommonStatusCodes.DEVELOPER_ERROR)))
        assertTrue(msg, msg.contains("DEVELOPER_ERROR") && msg.contains("OAuth client") && msg.contains("SHA-1"))
        assertTrue(GoogleDrive.explain(RuntimeException(ApiException(Status(CommonStatusCodes.DEVELOPER_ERROR)))).contains("isn't set up"))
    }
}
