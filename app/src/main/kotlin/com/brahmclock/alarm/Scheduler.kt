package com.brahmclock.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.brahmclock.core.AlarmMath
import com.brahmclock.core.Brahma
import com.brahmclock.data.AlarmItem
import com.brahmclock.repo
import com.brahmclock.ui.Format
import com.brahmclock.ui.MainActivity
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/** Keeps exactly one pending AlarmManager entry per thing that can ring; everything is recomputed from Repo. */
object Scheduler {
    const val ACTION_RING = "com.brahmclock.RING"
    const val ACTION_SLEEP = "com.brahmclock.SLEEP"
    const val EXTRA_AT = "at"

    private const val RC_BRAHMA = 1_000_000
    private const val RC_SLEEP = 1_000_001
    private const val RC_BRAHMA_SNOOZE = 1_000_002
    private const val RC_TIMER = 1_000_003
    private const val RC_ALARM_SNOOZE_BASE = 2_000_000

    fun canScheduleExact(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    /** Recompute every pending alarm. [after] avoids re-picking the alarm that is firing right now. */
    fun rescheduleAll(context: Context, after: Instant? = null) {
        val repo = context.repo
        val zone = ZoneId.systemDefault()
        val nowInstant = Instant.now().let { if (after != null && after.isAfter(it)) after else it }
        val now = ZonedDateTime.ofInstant(nowInstant, zone)

        val plan = Brahma.nextPlan(now, repo.location, repo.brahma)
        if (plan == null) {
            cancel(context, RC_BRAHMA, ACTION_RING)
        } else {
            val stage = plan.next
            val spec = RingSpec(
                kind = RingKind.BRAHMA,
                title = "Brahma Muhurta",
                subtitle = if (stage.total > 1) "Stage ${stage.index + 1} of ${stage.total}" else "Time to rise",
                volume = stage.volume,
                ringSeconds = stage.ringSeconds,
                vibrate = stage.vibrate && repo.sound.vibrate,
                soundUri = repo.sound.uri,
                stageIndex = stage.index,
                stageTotal = stage.total,
                date = plan.window.date.toString(),
                sunriseMillis = plan.window.sunrise?.toInstant()?.toEpochMilli() ?: 0,
            )
            setAlarmClock(context, RC_BRAHMA, stage.time.toInstant(), ringIntent(context, spec))
        }

        val reminder = Brahma.nextSleepReminder(now, repo.location, repo.brahma)
        if (reminder == null) {
            cancel(context, RC_SLEEP, ACTION_SLEEP)
        } else {
            val intent = Intent(context, AlarmReceiver::class.java).setAction(ACTION_SLEEP)
                .putExtra("bedtime", reminder.bedtime.toInstant().toEpochMilli())
                .putExtra("windowStart", reminder.window.start.toInstant().toEpochMilli())
            setReminder(context, RC_SLEEP, reminder.time.toInstant(), intent)
        }

        repo.alarms.forEach { scheduleAlarm(context, it, now) }

        val endAt = repo.timer.endAt
        if (endAt != null && endAt > System.currentTimeMillis()) {
            val spec = RingSpec(RingKind.TIMER, "Timer", "Time's up")
            setAlarmClock(context, RC_TIMER, Instant.ofEpochMilli(endAt), ringIntent(context, spec))
        } else {
            cancel(context, RC_TIMER, ACTION_RING)
        }
    }

    private fun scheduleAlarm(context: Context, alarm: AlarmItem, now: ZonedDateTime) {
        if (!alarm.enabled) {
            cancel(context, alarm.id, ACTION_RING)
            return
        }
        val at = AlarmMath.nextOccurrence(now, alarm.time, alarm.days)
        val spec = RingSpec(
            kind = RingKind.ALARM,
            title = alarm.label.ifBlank { "Alarm" },
            subtitle = Format.time(at.toLocalTime(), context.repo.settings.use24h),
            soundUri = alarm.soundUri,
            vibrate = alarm.vibrate,
            alarmId = alarm.id,
        )
        setAlarmClock(context, alarm.id, at.toInstant(), ringIntent(context, spec))
    }

    fun cancelAlarm(context: Context, id: Int) {
        cancel(context, id, ACTION_RING)
        cancel(context, RC_ALARM_SNOOZE_BASE + id, ACTION_RING)
    }

    fun snooze(context: Context, spec: RingSpec, minutes: Int) {
        val rc = if (spec.kind == RingKind.BRAHMA) RC_BRAHMA_SNOOZE else RC_ALARM_SNOOZE_BASE + spec.alarmId
        val snoozed = spec.copy(volume = 1f, ringSeconds = 0, stageIndex = spec.stageTotal - 1, snoozed = true, subtitle = "Snoozed")
        setAlarmClock(context, rc, Instant.now().plusSeconds(minutes * 60L), ringIntent(context, snoozed))
    }

    private fun ringIntent(context: Context, spec: RingSpec): Intent =
        spec.writeTo(Intent(context, AlarmReceiver::class.java).setAction(ACTION_RING))

    private fun setAlarmClock(context: Context, requestCode: Int, at: Instant, intent: Intent) {
        val am = context.getSystemService(AlarmManager::class.java)
        intent.putExtra(EXTRA_AT, at.toEpochMilli())
        val operation = PendingIntent.getBroadcast(
            context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        if (canScheduleExact(context)) {
            val show = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
            )
            am.setAlarmClock(AlarmManager.AlarmClockInfo(at.toEpochMilli(), show), operation)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), operation)
        }
    }

    private fun setReminder(context: Context, requestCode: Int, at: Instant, intent: Intent) {
        val am = context.getSystemService(AlarmManager::class.java)
        intent.putExtra(EXTRA_AT, at.toEpochMilli())
        val operation = PendingIntent.getBroadcast(
            context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        if (canScheduleExact(context)) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), operation)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), operation)
        }
    }

    private fun cancel(context: Context, requestCode: Int, action: String) {
        val intent = Intent(context, AlarmReceiver::class.java).setAction(action)
        val pi = PendingIntent.getBroadcast(
            context, requestCode, intent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ) ?: return
        context.getSystemService(AlarmManager::class.java).cancel(pi)
        pi.cancel()
    }
}
