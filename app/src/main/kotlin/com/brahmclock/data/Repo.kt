package com.brahmclock.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import com.brahmclock.core.BrahmaConfig
import com.brahmclock.core.GeoPoint
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

data class AlarmItem(
    val id: Int,
    val hour: Int,
    val minute: Int,
    val days: Set<DayOfWeek> = emptySet(),
    val label: String = "",
    val enabled: Boolean = true,
    val soundUri: String? = null,
    val vibrate: Boolean = true,
) {
    val time: LocalTime get() = LocalTime.of(hour, minute)
}

enum class ThemeMode { SYSTEM, DARK, BLACK, LIGHT }

data class GeneralSettings(
    val use24h: Boolean = false,
    val snoozeMinutes: Int = 10,
    val theme: ThemeMode = ThemeMode.DARK,
)

data class BrahmaSound(val uri: String? = null, val vibrate: Boolean = true)

/** Countdown timer: running when [endAt] is set, paused when [pausedRemaining] is set. */
data class TimerState(val totalMillis: Long = 5 * 60_000L, val endAt: Long? = null, val pausedRemaining: Long? = null)

data class StopwatchState(val startedAt: Long? = null, val accumulated: Long = 0, val laps: List<Long> = emptyList()) {
    fun elapsed(now: Long): Long = accumulated + (startedAt?.let { now - it } ?: 0)
}

/**
 * Single source of truth backed by SharedPreferences (light: no database needed for a few dozen rows).
 * Fields are Compose state so screens recompose when receivers/services change them.
 */
class Repo(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("brahm", Context.MODE_PRIVATE)

    var brahma by mutableStateOf(loadBrahma())
        private set
    var sound by mutableStateOf(BrahmaSound(prefs.getString("b_sound", null), prefs.getBoolean("b_vibrate", true)))
        private set
    var location by mutableStateOf(loadLocation())
        private set
    var alarms by mutableStateOf(loadAlarms())
        private set
    var zones by mutableStateOf(prefs.getString("zones", null)?.split(",")?.filter { it.isNotBlank() } ?: emptyList())
        private set
    var settings by mutableStateOf(loadSettings())
        private set
    var wokeDates by mutableStateOf(prefs.getStringSet("woke", emptySet())!!.map(LocalDate::parse).toSet())
        private set
    var timer by mutableStateOf(loadTimer())
        private set
    var stopwatch by mutableStateOf(loadStopwatch())
        private set

    val onboarded: Boolean get() = prefs.getBoolean("onboarded", false)
    fun setOnboarded() = prefs.edit { putBoolean("onboarded", true) }

    fun updateBrahma(value: BrahmaConfig) {
        brahma = value
        prefs.edit {
            putBoolean("b_enabled", value.enabled)
            putBoolean("b_gentle", value.gentle)
            putInt("b_offset", value.offsetMinutes)
            putInt("b_stages", value.stageCount)
            putString("b_days", value.days.joinToString(",") { it.name })
            putString("b_backup", value.backupStart.toString())
            putString("b_skip", value.skipDate?.toString())
            putString("b_done", value.doneDate?.toString())
            putBoolean("b_sleep", value.sleepReminder)
            putInt("b_sleep_min", value.sleepMinutes)
            putInt("b_sleep_adv", value.reminderAdvanceMinutes)
        }
    }

    fun updateSound(value: BrahmaSound) {
        sound = value
        prefs.edit { putString("b_sound", value.uri); putBoolean("b_vibrate", value.vibrate) }
    }

    fun updateLocation(value: GeoPoint) {
        location = value
        prefs.edit {
            putString("loc_lat", value.lat.toString())
            putString("loc_lon", value.lon.toString())
            putString("loc_label", value.label)
        }
    }

    fun upsertAlarm(alarm: AlarmItem) {
        val list = alarms.filter { it.id != alarm.id } + alarm
        saveAlarms(list.sortedBy { it.hour * 60 + it.minute })
    }

    fun deleteAlarm(id: Int) = saveAlarms(alarms.filter { it.id != id })

    fun newAlarmId(): Int = (alarms.maxOfOrNull { it.id } ?: 0) + 1

    fun setZones(value: List<String>) {
        zones = value
        prefs.edit { putString("zones", value.joinToString(",")) }
    }

    fun updateSettings(value: GeneralSettings) {
        settings = value
        prefs.edit {
            putBoolean("use24h", value.use24h)
            putInt("snooze", value.snoozeMinutes)
            putString("theme", value.theme.name)
        }
    }

    fun markWoke(date: LocalDate) {
        // Keep a year of history; enough for streaks and the weekly dots.
        wokeDates = (wokeDates + date).filter { it.isAfter(date.minusDays(366)) }.toSet()
        prefs.edit { putStringSet("woke", wokeDates.map { it.toString() }.toSet()) }
    }

    fun updateTimer(value: TimerState) {
        timer = value
        prefs.edit {
            putLong("t_total", value.totalMillis)
            putLong("t_end", value.endAt ?: -1)
            putLong("t_paused", value.pausedRemaining ?: -1)
        }
    }

    fun updateStopwatch(value: StopwatchState) {
        stopwatch = value
        prefs.edit {
            putLong("sw_start", value.startedAt ?: -1)
            putLong("sw_acc", value.accumulated)
            putString("sw_laps", value.laps.joinToString(","))
        }
    }

    private fun loadBrahma(): BrahmaConfig {
        val d = BrahmaConfig()
        return BrahmaConfig(
            enabled = prefs.getBoolean("b_enabled", d.enabled),
            gentle = prefs.getBoolean("b_gentle", d.gentle),
            offsetMinutes = prefs.getInt("b_offset", d.offsetMinutes),
            stageCount = prefs.getInt("b_stages", d.stageCount),
            days = prefs.getString("b_days", null)?.let(::parseDays) ?: d.days,
            backupStart = prefs.getString("b_backup", null)?.let(LocalTime::parse) ?: d.backupStart,
            skipDate = prefs.getString("b_skip", null)?.let(LocalDate::parse),
            doneDate = prefs.getString("b_done", null)?.let(LocalDate::parse),
            sleepReminder = prefs.getBoolean("b_sleep", d.sleepReminder),
            sleepMinutes = prefs.getInt("b_sleep_min", d.sleepMinutes),
            reminderAdvanceMinutes = prefs.getInt("b_sleep_adv", d.reminderAdvanceMinutes),
        )
    }

    private fun loadLocation(): GeoPoint? {
        val lat = prefs.getString("loc_lat", null)?.toDoubleOrNull() ?: return null
        val lon = prefs.getString("loc_lon", null)?.toDoubleOrNull() ?: return null
        return GeoPoint(lat, lon, prefs.getString("loc_label", null) ?: "")
    }

    private fun loadSettings() = GeneralSettings(
        use24h = prefs.getBoolean("use24h", false),
        snoozeMinutes = prefs.getInt("snooze", 10),
        theme = runCatching { ThemeMode.valueOf(prefs.getString("theme", null)!!) }.getOrDefault(ThemeMode.DARK),
    )

    private fun loadTimer() = TimerState(
        totalMillis = prefs.getLong("t_total", 5 * 60_000L),
        endAt = prefs.getLong("t_end", -1).takeIf { it >= 0 },
        pausedRemaining = prefs.getLong("t_paused", -1).takeIf { it >= 0 },
    )

    private fun loadStopwatch() = StopwatchState(
        startedAt = prefs.getLong("sw_start", -1).takeIf { it >= 0 },
        accumulated = prefs.getLong("sw_acc", 0),
        laps = prefs.getString("sw_laps", "")!!.split(",").mapNotNull { it.toLongOrNull() },
    )

    private fun loadAlarms(): List<AlarmItem> {
        val json = prefs.getString("alarms", null) ?: return emptyList()
        val array = JSONArray(json)
        return (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            AlarmItem(
                id = o.getInt("id"),
                hour = o.getInt("hour"),
                minute = o.getInt("minute"),
                days = parseDays(o.optString("days")),
                label = o.optString("label"),
                enabled = o.optBoolean("enabled", true),
                soundUri = o.optString("sound").ifBlank { null },
                vibrate = o.optBoolean("vibrate", true),
            )
        }
    }

    private fun saveAlarms(list: List<AlarmItem>) {
        alarms = list
        val array = JSONArray()
        list.forEach {
            array.put(
                JSONObject()
                    .put("id", it.id)
                    .put("hour", it.hour)
                    .put("minute", it.minute)
                    .put("days", it.days.joinToString(",") { d -> d.name })
                    .put("label", it.label)
                    .put("enabled", it.enabled)
                    .put("sound", it.soundUri ?: "")
                    .put("vibrate", it.vibrate)
            )
        }
        prefs.edit { putString("alarms", array.toString()) }
    }

    private fun parseDays(value: String): Set<DayOfWeek> =
        value.split(",").filter { it.isNotBlank() }.mapNotNull { runCatching { DayOfWeek.valueOf(it) }.getOrNull() }.toSet()
}
