package com.brahmclock.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

class AlarmMathTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val monday = LocalDate.of(2026, 10, 26)

    @Test
    fun oneTimeAlarmRollsToTomorrowWhenPassed() {
        val now = monday.atTime(8, 0).atZone(zone)
        assertEquals(monday.atTime(9, 0).atZone(zone), AlarmMath.nextOccurrence(now, LocalTime.of(9, 0), emptySet()))
        assertEquals(monday.plusDays(1).atTime(7, 0).atZone(zone), AlarmMath.nextOccurrence(now, LocalTime.of(7, 0), emptySet()))
    }

    @Test
    fun repeatingAlarmFindsNextSelectedDay() {
        val now = monday.atTime(8, 0).atZone(zone)
        val next = AlarmMath.nextOccurrence(now, LocalTime.of(7, 0), setOf(DayOfWeek.MONDAY))
        assertEquals(monday.plusDays(7).atTime(7, 0).atZone(zone), next)
        val sat = AlarmMath.nextOccurrence(now, LocalTime.of(7, 0), setOf(DayOfWeek.SATURDAY))
        assertEquals(LocalDate.of(2026, 10, 31), sat.toLocalDate())
    }
}
