package com.brahmclock.core

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BrahmaTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val delhi = GeoPoint(28.6139, 77.2090, "New Delhi")
    private val config = BrahmaConfig()
    private val date = LocalDate.of(2026, 10, 26)

    private fun at(d: LocalDate, h: Int, m: Int): ZonedDateTime = d.atTime(h, m).atZone(zone)

    @Test
    fun windowIsNinetySixToFortyEightMinutesBeforeSunrise() {
        val w = Brahma.window(date, delhi, zone, config)
        val sunrise = assertNotNull(w.sunrise)
        assertEquals(96, Duration.between(w.start, sunrise).toMinutes())
        assertEquals(48, Duration.between(w.end, sunrise).toMinutes())
    }

    @Test
    fun missingLocationFallsBackToBackupTime() {
        val w = Brahma.window(date, null, zone, config)
        assertNull(w.sunrise)
        assertEquals(LocalTime.of(4, 30), w.start.toLocalTime())
        assertEquals(LocalTime.of(5, 18), w.end.toLocalTime())
    }

    @Test
    fun gentleStagesSpanWindowAndGetLouder() {
        val w = Brahma.window(date, delhi, zone, config)
        val stages = Brahma.stages(w, config)
        assertEquals(7, stages.size)
        assertEquals(w.start, stages.first().time)
        assertEquals(w.end, stages.last().time)
        assertEquals(8, Duration.between(stages[0].time, stages[1].time).toMinutes())
        assertTrue(stages.zipWithNext().all { (a, b) -> b.volume > a.volume })
        assertEquals(0.1f, stages.first().volume)
        assertTrue(stages.last().isFinal && stages.last().ringSeconds == 0 && stages.last().volume == 1f)
        assertTrue(stages.dropLast(1).all { it.ringSeconds in 15..60 })
    }

    @Test
    fun standardModeIsOneAlarmAtWindowStart() {
        val cfg = config.copy(gentle = false)
        val w = Brahma.window(date, delhi, zone, cfg)
        val stages = Brahma.stages(w, cfg)
        assertEquals(1, stages.size)
        assertEquals(w.start, stages.single().time)
        assertTrue(stages.single().isFinal)
    }

    @Test
    fun nextPlanPicksUpcomingStage() {
        val plan = assertNotNull(Brahma.nextPlan(at(date.minusDays(1), 22, 0), delhi, config))
        assertEquals(date, plan.window.date)
        assertEquals(0, plan.next.index)

        val during = plan.stages[2].time.plusSeconds(1)
        assertEquals(3, Brahma.nextPlan(during, delhi, config)?.next?.index)
    }

    @Test
    fun nextPlanSkipsDoneSkippedAndInactiveDays() {
        val evening = at(date.minusDays(1), 22, 0)
        assertEquals(date.plusDays(1), Brahma.nextPlan(evening, delhi, config.copy(skipDate = date))?.window?.date)

        val early = at(date, 5, 0)
        assertEquals(date.plusDays(1), Brahma.nextPlan(early, delhi, config.copy(doneDate = date))?.window?.date)

        // 2026-10-26 is a Monday.
        val weekendsOff = config.copy(days = DayOfWeek.entries.toSet() - DayOfWeek.MONDAY)
        assertEquals(date.plusDays(1), Brahma.nextPlan(evening, delhi, weekendsOff)?.window?.date)

        assertNull(Brahma.nextPlan(evening, delhi, config.copy(enabled = false)))
    }

    @Test
    fun sleepReminderIsEightHoursBeforeWindow() {
        val reminder = assertNotNull(Brahma.nextSleepReminder(at(date.minusDays(1), 12, 0), delhi, config))
        assertEquals(date, reminder.window.date)
        assertEquals(8 * 60, Duration.between(reminder.bedtime, reminder.window.start).toMinutes())
        assertEquals(reminder.bedtime, reminder.time)

        val early = config.copy(reminderAdvanceMinutes = 30)
        val r2 = assertNotNull(Brahma.nextSleepReminder(at(date.minusDays(1), 12, 0), delhi, early))
        assertEquals(30, Duration.between(r2.time, r2.bedtime).toMinutes())

        // Once tonight's reminder has passed, the next one is for the following morning.
        val late = reminder.time.plusMinutes(1)
        assertEquals(date.plusDays(1), Brahma.nextSleepReminder(late, delhi, config)?.window?.date)
        assertNull(Brahma.nextSleepReminder(late, delhi, config.copy(sleepReminder = false)))
    }

    @Test
    fun streakCountsOnlyRepeatDays() {
        val today = LocalDate.of(2026, 10, 26) // Monday
        val all = DayOfWeek.entries.toSet()
        val woke = setOf(today, today.minusDays(1), today.minusDays(2))
        assertEquals(3, Brahma.streak(woke, today, all))
        assertEquals(2, Brahma.streak(woke - today, today, all))
        assertEquals(0, Brahma.streak(setOf(today.minusDays(3)), today, all))

        val noSunday = all - DayOfWeek.SUNDAY
        assertEquals(3, Brahma.streak(setOf(today, today.minusDays(2), today.minusDays(3)), today, noSunday))
    }
}
