package com.brahmclock.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.roundToLong
import kotlin.math.sin

/**
 * Offline sunrise calculation (NOAA / "sunrise equation" approximation).
 * Accurate to about a minute for inhabited latitudes; no network needed.
 */
object SunCalculator {
    private const val J2000 = 2451545.0
    private const val UNIX_EPOCH_JD = 2440587.5
    private const val J2000_EPOCH_DAY = 10957L // LocalDate.of(2000, 1, 1).toEpochDay()

    /** Sunrise on [date] (local to [zone]), or null when the sun doesn't rise that day (polar day/night). */
    fun sunrise(date: LocalDate, lat: Double, lon: Double, zone: ZoneId): ZonedDateTime? {
        var n = date.toEpochDay() - J2000_EPOCH_DAY
        repeat(3) {
            val rise = riseForDay(n, lat, lon)?.atZone(zone) ?: return null
            val riseDate = rise.toLocalDate()
            when {
                riseDate == date -> return rise
                riseDate < date -> n++
                else -> n--
            }
        }
        return null
    }

    private fun riseForDay(n: Long, lat: Double, lon: Double): Instant? {
        val meanNoon = n - lon / 360.0
        val anomaly = Math.toRadians(normalize(357.5291 + 0.98560028 * meanNoon))
        val center = 1.9148 * sin(anomaly) + 0.0200 * sin(2 * anomaly) + 0.0003 * sin(3 * anomaly)
        val eclipticLon = Math.toRadians(normalize(Math.toDegrees(anomaly) + center + 180.0 + 102.9372))
        val transit = J2000 + meanNoon + 0.0053 * sin(anomaly) - 0.0069 * sin(2 * eclipticLon)
        val sinDecl = sin(eclipticLon) * sin(Math.toRadians(23.4397))
        val cosDecl = cos(asin(sinDecl))
        val phi = Math.toRadians(lat)
        val cosHourAngle = (sin(Math.toRadians(-0.833)) - sin(phi) * sinDecl) / (cos(phi) * cosDecl)
        if (cosHourAngle < -1.0 || cosHourAngle > 1.0) return null
        val riseJd = transit - Math.toDegrees(acos(cosHourAngle)) / 360.0
        return Instant.ofEpochMilli(((riseJd - UNIX_EPOCH_JD) * 86_400_000.0).roundToLong())
    }

    private fun normalize(deg: Double): Double = ((deg % 360.0) + 360.0) % 360.0
}
