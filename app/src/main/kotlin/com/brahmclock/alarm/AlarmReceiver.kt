package com.brahmclock.alarm

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.brahmclock.BrahmApp
import com.brahmclock.R
import com.brahmclock.repo
import com.brahmclock.ui.Format
import com.brahmclock.ui.MainActivity
import java.time.Instant
import java.time.ZoneId

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val at = Instant.ofEpochMilli(intent.getLongExtra(Scheduler.EXTRA_AT, System.currentTimeMillis()))
        when (intent.action) {
            Scheduler.ACTION_SLEEP -> showSleepReminder(context, intent)
            Scheduler.ACTION_RING -> RingSpec.from(intent)?.let { ring(context, it) }
        }
        Scheduler.rescheduleAll(context, after = at.plusSeconds(1))
    }

    private fun ring(context: Context, spec: RingSpec) {
        val repo = context.repo
        when (spec.kind) {
            RingKind.BRAHMA ->
                // Already up this morning: the remaining stages stay silent.
                if (!spec.snoozed && spec.date != null && spec.date == repo.brahma.doneDate?.toString()) return
            RingKind.ALARM -> {
                val alarm = repo.alarms.firstOrNull { it.id == spec.alarmId }
                if (!spec.snoozed && alarm != null && alarm.days.isEmpty()) repo.upsertAlarm(alarm.copy(enabled = false))
            }
            RingKind.TIMER -> repo.updateTimer(repo.timer.copy(endAt = null, pausedRemaining = null))
        }
        RingService.start(context, spec)
    }

    @SuppressLint("MissingPermission")
    private fun showSleepReminder(context: Context, intent: Intent) {
        val nm = NotificationManagerCompat.from(context)
        if (!nm.areNotificationsEnabled()) return
        val zone = ZoneId.systemDefault()
        val use24h = context.repo.settings.use24h
        val bedtime = Instant.ofEpochMilli(intent.getLongExtra("bedtime", 0)).atZone(zone).toLocalTime()
        val start = Instant.ofEpochMilli(intent.getLongExtra("windowStart", 0)).atZone(zone).toLocalTime()
        val hours = Format.hours(context.repo.brahma.sleepMinutes)
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, BrahmApp.CHANNEL_SLEEP)
            .setSmallIcon(R.drawable.ic_stat_sun)
            .setContentTitle("Time to wind down 🌙")
            .setContentText("Sleep by ${Format.time(bedtime, use24h)} for $hours before Brahma Muhurta at ${Format.time(start, use24h)}.")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "Sleep by ${Format.time(bedtime, use24h)} to get $hours of rest. " +
                        "Your gentle alarm begins at ${Format.time(start, use24h)}."
                )
            )
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        nm.notify(SLEEP_NOTIFICATION_ID, notification)
    }

    companion object {
        const val SLEEP_NOTIFICATION_ID = 2
    }
}
