package com.brahmclock.core

import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

class SunCalculatorTest {
    // Reference times from the astral (NOAA) library.
    private val cases = listOf(
        Case(28.6139, 77.2090, "Asia/Kolkata", "2024-01-01", "07:13:58"),
        Case(28.6139, 77.2090, "Asia/Kolkata", "2026-10-26", "06:28:56"),
        Case(22.7196, 75.8577, "Asia/Kolkata", "2026-06-21", "05:42:39"),
        Case(51.5074, -0.1278, "Europe/London", "2024-06-21", "04:43:33"),
        Case(40.7128, -74.0060, "America/New_York", "2024-12-21", "07:17:06"),
        Case(-33.8688, 151.2093, "Australia/Sydney", "2025-03-15", "06:54:28"),
        Case(35.6762, 139.6503, "Asia/Tokyo", "2025-09-01", "05:13:29"),
        Case(64.1466, -21.9426, "Atlantic/Reykjavik", "2025-06-10", "03:04:27"),
    )

    @Test
    fun sunriseMatchesReferenceWithinTwoMinutes() {
        for (c in cases) {
            val date = LocalDate.parse(c.date)
            val zone = ZoneId.of(c.zone)
            val rise = SunCalculator.sunrise(date, c.lat, c.lon, zone) ?: fail("no sunrise for $c")
            val expected = date.atTime(LocalTime.parse(c.expected)).atZone(zone)
            val diff = Duration.between(expected, rise).abs()
            assertTrue(diff.seconds <= 120, "$c: got ${rise.toLocalTime()}, off by ${diff.seconds}s")
        }
    }

    @Test
    fun noSunriseInPolarNight() {
        assertNull(SunCalculator.sunrise(LocalDate.of(2024, 12, 21), 69.6496, 18.9560, ZoneId.of("Europe/Oslo")))
    }

    private data class Case(val lat: Double, val lon: Double, val zone: String, val date: String, val expected: String)
}
