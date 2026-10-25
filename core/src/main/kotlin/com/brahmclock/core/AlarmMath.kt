package com.brahmclock.core

import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZonedDateTime

object AlarmMath {
    /** Next time [time] occurs after [now] on one of [days]; empty [days] means a one-time alarm. */
    fun nextOccurrence(now: ZonedDateTime, time: LocalTime, days: Set<DayOfWeek>): ZonedDateTime {
        for (i in 0L..7L) {
            val date = now.toLocalDate().plusDays(i)
            if (days.isNotEmpty() && date.dayOfWeek !in days) continue
            val candidate = date.atTime(time).atZone(now.zone)
            if (candidate.isAfter(now)) return candidate
        }
        return now.toLocalDate().plusDays(1).atTime(time).atZone(now.zone)
    }
}
