package com.brahmclock.core

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.roundToInt

data class GeoPoint(val lat: Double, val lon: Double, val label: String)

/** The Brahma Muhurta window for one morning. [sunrise] is null when it had to be estimated from the backup time. */
data class BrahmaWindow(
    val date: LocalDate,
    val start: ZonedDateTime,
    val end: ZonedDateTime,
    val sunrise: ZonedDateTime?,
)

data class BrahmaConfig(
    val enabled: Boolean = true,
    /** Gentle = staged pulses across the window; otherwise one alarm at the window start. */
    val gentle: Boolean = true,
    val offsetMinutes: Int = 96,
    val lengthMinutes: Int = 48,
    val stageCount: Int = 7,
    val days: Set<DayOfWeek> = DayOfWeek.entries.toSet(),
    /** Window start used when there is no location or no sunrise (polar regions). */
    val backupStart: LocalTime = LocalTime.of(4, 30),
    val skipDate: LocalDate? = null,
    /** The morning the user already tapped "I'm awake"; remaining stages are skipped. */
    val doneDate: LocalDate? = null,
    val sleepReminder: Boolean = true,
    val sleepMinutes: Int = 8 * 60,
    val reminderAdvanceMinutes: Int = 0,
)

data class Stage(
    val index: Int,
    val total: Int,
    val time: ZonedDateTime,
    /** 0..1, relative to the alarm stream volume. */
    val volume: Float,
    /** How long this pulse plays; 0 = rings until dismissed. */
    val ringSeconds: Int,
    val vibrate: Boolean,
) {
    val isFinal: Boolean get() = index == total - 1
}

data class BrahmaPlan(val window: BrahmaWindow, val stages: List<Stage>, val next: Stage)

data class SleepReminder(val time: ZonedDateTime, val bedtime: ZonedDateTime, val window: BrahmaWindow)

object Brahma {
    const val MIN_STAGES = 2
    const val MAX_STAGES = 12

    fun window(date: LocalDate, location: GeoPoint?, zone: ZoneId, config: BrahmaConfig): BrahmaWindow {
        val sunrise = location?.let { SunCalculator.sunrise(date, it.lat, it.lon, zone) }
        val start = sunrise?.minusMinutes(config.offsetMinutes.toLong())
            ?: date.atTime(config.backupStart).atZone(zone)
        return BrahmaWindow(date, start, start.plusMinutes(config.lengthMinutes.toLong()), sunrise)
    }

    fun stages(window: BrahmaWindow, config: BrahmaConfig): List<Stage> {
        if (!config.gentle) return listOf(Stage(0, 1, window.start, 1f, 0, vibrate = true))
        val n = config.stageCount.coerceIn(MIN_STAGES, MAX_STAGES)
        val step = Duration.between(window.start, window.end).seconds / (n - 1)
        return (0 until n).map { i ->
            val last = i == n - 1
            val progress = i.toFloat() / (n - 1)
            Stage(
                index = i,
                total = n,
                time = if (last) window.end else window.start.plusSeconds(step * i),
                volume = if (last) 1f else ((0.1f + 0.75f * progress) * 100).roundToInt() / 100f,
                ringSeconds = if (last) 0 else (15 + 45 * progress).roundToInt(),
                vibrate = last || progress >= 0.3f,
            )
        }
    }

    fun isActiveDay(date: LocalDate, config: BrahmaConfig): Boolean =
        date.dayOfWeek in config.days && date != config.skipDate

    /** The next morning that still has a stage after [now], with that stage. */
    fun nextPlan(now: ZonedDateTime, location: GeoPoint?, config: BrahmaConfig): BrahmaPlan? {
        if (!config.enabled || config.days.isEmpty()) return null
        for (i in 0L..8L) {
            val date = now.toLocalDate().plusDays(i)
            if (!isActiveDay(date, config) || date == config.doneDate) continue
            val window = window(date, location, now.zone, config)
            val stages = stages(window, config)
            val next = stages.firstOrNull { it.time.isAfter(now) } ?: continue
            return BrahmaPlan(window, stages, next)
        }
        return null
    }

    /** Next bedtime reminder: [BrahmaConfig.sleepMinutes] before the window starts, minus the advance. */
    fun nextSleepReminder(now: ZonedDateTime, location: GeoPoint?, config: BrahmaConfig): SleepReminder? {
        if (!config.enabled || !config.sleepReminder || config.days.isEmpty()) return null
        for (i in 0L..9L) {
            val date = now.toLocalDate().plusDays(i)
            if (!isActiveDay(date, config)) continue
            val window = window(date, location, now.zone, config)
            val bedtime = window.start.minusMinutes(config.sleepMinutes.toLong())
            val time = bedtime.minusMinutes(config.reminderAdvanceMinutes.toLong())
            if (time.isAfter(now)) return SleepReminder(time, bedtime, window)
        }
        return null
    }

    /** Consecutive wake-ups, counting only the alarm's repeat days. Today counts once you're up. */
    fun streak(woke: Set<LocalDate>, today: LocalDate, days: Set<DayOfWeek>): Int {
        if (days.isEmpty()) return 0
        var date = if (today in woke) today else today.minusDays(1)
        var count = 0
        repeat(400) {
            when {
                date in woke -> count++
                date.dayOfWeek in days -> return count
            }
            date = date.minusDays(1)
        }
        return count
    }
}
