package com.josh.notes

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * Note reminders via AlarmManager.
 * Exact alarms: USE_EXACT_ALARM (API 33+, auto-granted) / SCHEDULE_EXACT_ALARM (API 31-32, user-grantable).
 * If exact alarms aren't allowed, falls back to an inexact allow-while-idle alarm (may be a few minutes late).
 */
object Reminders {
    const val CHANNEL = "reminders"
    const val ACTION_FIRE = "com.josh.notes.REMINDER_FIRE"
    const val ACTION_DONE = "com.josh.notes.REMINDER_DONE"
    const val ACTION_SNOOZE = "com.josh.notes.REMINDER_SNOOZE"
    const val EXTRA_ID = "note_id"
    const val SNOOZE_MS = 10 * 60_000L

    private fun am(ctx: Context) = ctx.getSystemService(AlarmManager::class.java)

    fun canExact(ctx: Context) = Build.VERSION.SDK_INT < 31 || am(ctx).canScheduleExactAlarms()

    fun canNotify(ctx: Context) = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun firePi(ctx: Context, id: Long): PendingIntent = PendingIntent.getBroadcast(
        ctx, id.hashCode(),
        Intent(ctx, ReminderReceiver::class.java).setAction(ACTION_FIRE).putExtra(EXTRA_ID, id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    /** Bring the alarm in line with the note's state (called after every save). */
    fun sync(ctx: Context, n: Note) {
        val c = ctx.applicationContext
        if (!n.hasReminder || n.reminderFired || n.inTrash) { cancelAlarm(c, n.id) ; return }
        if (n.reminderAt <= System.currentTimeMillis()) { fire(c, n.id); return }
        schedule(c, n.id, n.reminderAt)
    }

    private fun schedule(ctx: Context, id: Long, at: Long) {
        val pi = firePi(ctx, id)
        try {
            if (canExact(ctx)) am(ctx).setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            else am(ctx).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } catch (_: SecurityException) {
            am(ctx).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    private fun cancelAlarm(ctx: Context, id: Long) = am(ctx).cancel(firePi(ctx, id))

    fun cancel(ctx: Context, id: Long) {
        cancelAlarm(ctx.applicationContext, id)
        NotificationManagerCompat.from(ctx).cancel(id.hashCode())
    }

    /** Re-arm everything (boot, app update, time change, app start). Missed reminders fire now. */
    fun rescheduleAll(ctx: Context) {
        NoteStore.all(ctx).forEach { sync(ctx, it) }
    }

    fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Note reminders"
                enableVibration(true)
            })
        }
    }

    /** Show the notification and mark the reminder as fired. */
    fun fire(ctx: Context, id: Long) {
        val n = NoteStore.get(ctx, id) ?: return
        if (!n.hasReminder || n.inTrash) return
        NoteStore.upsert(ctx, n.copy(reminderFired = true), touch = false)
        if (!canNotify(ctx)) return
        ensureChannel(ctx)
        val open = PendingIntent.getActivity(ctx, id.hashCode(),
            Intent(ctx, EditNoteActivity::class.java).putExtra(EditNoteActivity.EXTRA_NOTE_ID, id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        fun action(a: String, req: Int) = PendingIntent.getBroadcast(ctx, id.hashCode() * 31 + req,
            Intent(ctx, ReminderReceiver::class.java).setAction(a).putExtra(EXTRA_ID, id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val locked = AppLock.enabled(ctx)
        val title = n.displayTitle.ifBlank { ctx.getString(R.string.untitled) }
        val text = if (locked) "Tap to open" else n.contentText.trim().take(300)
        val notif = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_bell)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(if (locked) NotificationCompat.VISIBILITY_PRIVATE else NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(open)
            .addAction(0, "Done", action(ACTION_DONE, 1))
            .addAction(0, "Snooze 10 min", action(ACTION_SNOOZE, 2))
            .build()
        try { NotificationManagerCompat.from(ctx).notify(id.hashCode(), notif) } catch (_: SecurityException) {}
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(Reminders.EXTRA_ID, 0L)
        if (id == 0L) return
        val pending = goAsync()
        Thread {
            try {
                when (intent.action) {
                    Reminders.ACTION_FIRE -> Reminders.fire(context, id)
                    Reminders.ACTION_DONE -> {
                        NotificationManagerCompat.from(context).cancel(id.hashCode())
                        NoteStore.update(context, id) { it.copy(reminderAt = 0L, reminderFired = false) }
                    }
                    Reminders.ACTION_SNOOZE -> {
                        NotificationManagerCompat.from(context).cancel(id.hashCode())
                        NoteStore.update(context, id) {
                            it.copy(reminderAt = System.currentTimeMillis() + Reminders.SNOOZE_MS, reminderFired = false)
                        }
                    }
                }
            } finally { pending.finish() }
        }.start()
    }
}

/** Re-arms reminders after reboot, app update, or clock/time-zone changes. */
class ReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        Thread { try { Reminders.rescheduleAll(context) } finally { pending.finish() } }.start()
    }
}
