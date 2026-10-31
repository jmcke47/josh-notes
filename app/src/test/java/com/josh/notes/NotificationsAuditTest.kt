package com.josh.notes

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

/** Josh Notes posts no notifications except note reminders. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = NotesApp::class)
class NotificationsAuditTest {
    private val app get() = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val nm get() = app.getSystemService(NotificationManager::class.java)

    @Test fun upgradeDeletesEveryChannelExceptReminders() {
        listOf("updates", "backup", "recording").forEach { nm.createNotificationChannel(NotificationChannel(it, it, NotificationManager.IMPORTANCE_LOW)) }
        nm.createNotificationChannel(NotificationChannel(Reminders.CHANNEL, "Reminders", NotificationManager.IMPORTANCE_HIGH))
        nm.notify(7, NotificationCompat.Builder(app, "updates").setSmallIcon(android.R.drawable.stat_sys_download).setContentTitle("x").build())
        nm.notify(8, NotificationCompat.Builder(app, Reminders.CHANNEL).setSmallIcon(android.R.drawable.ic_popup_reminder).setContentTitle("r").build())
        val gone = Reminders.removeNonReminderNotifications(app, force = true)
        assertEquals(setOf("updates", "backup", "recording"), gone.toSet())
        assertEquals(listOf(Reminders.CHANNEL), nm.notificationChannels.map { it.id })
        assertEquals("only the reminder notification is left", listOf(Reminders.CHANNEL), shadowOf(nm).allNotifications.map { it.channelId })
        // runs once per version
        nm.createNotificationChannel(NotificationChannel("stray", "s", NotificationManager.IMPORTANCE_LOW))
        assertTrue(Reminders.removeNonReminderNotifications(app).isEmpty())
    }

    @Test fun noForegroundServiceInMergedManifest() {
        val pi = app.packageManager.getPackageInfo(app.packageName, PackageManager.GET_SERVICES or PackageManager.GET_PERMISSIONS)
        assertFalse(pi.services.orEmpty().any { it.name.contains("SystemForegroundService") })
        assertFalse(pi.requestedPermissions.orEmpty().any { it.startsWith("android.permission.FOREGROUND_SERVICE") })
    }

    @Test fun launchingDoesNotAskForNotificationPermission() {
        Robolectric.buildActivity(MainActivity::class.java).setup()
        Robolectric.buildActivity(SettingsActivity::class.java).setup()
        assertEquals(0, shadowOf(nm).size())
    }

    /** Source audit: only Reminders.kt posts notifications; POST_NOTIFICATIONS is requested only from setReminder(). */
    @Test fun onlyRemindersPostNotifications() {
        val src = generateSequence(File("").absoluteFile) { it.parentFile }.map { File(it, "app/src/main/java") }.firstOrNull { it.isDirectory }
            ?: File("src/main/java")
        assertTrue(src.isDirectory)
        val posting = Regex("""NotificationCompat\.Builder|Notification\.Builder|\.notify\(|startForeground\(|setForeground\(|createNotificationChannel""")
        val offenders = src.walk().filter { it.extension == "kt" && it.name != "Reminders.kt" && posting.containsMatchIn(it.readText()) }.map { it.name }.toList()
        assertEquals(emptyList<String>(), offenders)
        val askers = src.walk().filter { it.extension == "kt" && it.readText().contains("launch(android.Manifest.permission.POST_NOTIFICATIONS") }.map { it.name }.toList()
        assertEquals(listOf("EditNoteActivity.kt"), askers)
        val edit = File(src, "com/josh/notes/EditNoteActivity.kt").readText()
        val at = edit.indexOf("launch(android.Manifest.permission.POST_NOTIFICATIONS")
        assertEquals("requested inside setReminder()", edit.lastIndexOf("private fun setReminder()", at), edit.lastIndexOf("fun ", at) - "private ".length)
        assertNotNull(Reminders.CHANNEL)
    }
}
