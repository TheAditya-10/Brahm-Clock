package com.brahmclock.ui

import java.time.Duration
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object Format {
    private val h12 = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
    private val h24 = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)

    fun time(t: LocalTime, use24h: Boolean): String = (if (use24h) h24 else h12).format(t)

    fun hours(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        return if (m == 0) "$h hours" else "${h}h ${m}m"
    }

    /** "7h 12m", "45m", "under a minute". */
    fun until(d: Duration): String {
        val totalMin = d.toMinutes()
        if (totalMin < 1) return "under a minute"
        val days = totalMin / (24 * 60)
        val h = (totalMin / 60) % 24
        val m = totalMin % 60
        return buildList {
            if (days > 0) add("${days}d")
            if (h > 0) add("${h}h")
            if (m > 0 && days == 0L) add("${m}m")
        }.joinToString(" ")
    }

    /** mm:ss or h:mm:ss for timers. */
    fun clock(millis: Long): String {
        val s = (millis.coerceAtLeast(0) + 999) / 1000
        return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s / 60) % 60, s % 60) else "%02d:%02d".format(s / 60, s % 60)
    }

    /** mm:ss.cc for the stopwatch. */
    fun stopwatch(millis: Long): String {
        val cs = millis / 10
        val text = "%02d:%02d.%02d".format((cs / 6000) % 60, (cs / 100) % 60, cs % 100)
        return if (millis >= 3_600_000) "${millis / 3_600_000}:$text" else text
    }
}
