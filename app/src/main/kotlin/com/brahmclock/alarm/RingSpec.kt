package com.brahmclock.alarm

import android.content.Intent

enum class RingKind { BRAHMA, ALARM, TIMER }

/** Everything RingService needs to play one alarm; travels as Intent extras. */
data class RingSpec(
    val kind: RingKind,
    val title: String,
    val subtitle: String = "",
    val volume: Float = 1f,
    /** 0 = ring until dismissed. */
    val ringSeconds: Int = 0,
    val vibrate: Boolean = true,
    val soundUri: String? = null,
    val alarmId: Int = 0,
    val stageIndex: Int = 0,
    val stageTotal: Int = 1,
    /** Brahma morning as LocalDate string, so "I'm awake" can mark it done. */
    val date: String? = null,
    val sunriseMillis: Long = 0,
    val snoozed: Boolean = false,
) {
    val isFinal: Boolean get() = stageIndex == stageTotal - 1

    fun writeTo(intent: Intent): Intent = intent
        .putExtra("kind", kind.name)
        .putExtra("title", title)
        .putExtra("subtitle", subtitle)
        .putExtra("volume", volume)
        .putExtra("ringSeconds", ringSeconds)
        .putExtra("vibrate", vibrate)
        .putExtra("sound", soundUri)
        .putExtra("alarmId", alarmId)
        .putExtra("stageIndex", stageIndex)
        .putExtra("stageTotal", stageTotal)
        .putExtra("date", date)
        .putExtra("sunrise", sunriseMillis)
        .putExtra("snoozed", snoozed)

    companion object {
        fun from(intent: Intent): RingSpec? {
            val kind = intent.getStringExtra("kind")?.let { runCatching { RingKind.valueOf(it) }.getOrNull() } ?: return null
            return RingSpec(
                kind = kind,
                title = intent.getStringExtra("title") ?: "Alarm",
                subtitle = intent.getStringExtra("subtitle") ?: "",
                volume = intent.getFloatExtra("volume", 1f),
                ringSeconds = intent.getIntExtra("ringSeconds", 0),
                vibrate = intent.getBooleanExtra("vibrate", true),
                soundUri = intent.getStringExtra("sound"),
                alarmId = intent.getIntExtra("alarmId", 0),
                stageIndex = intent.getIntExtra("stageIndex", 0),
                stageTotal = intent.getIntExtra("stageTotal", 1),
                date = intent.getStringExtra("date"),
                sunriseMillis = intent.getLongExtra("sunrise", 0),
                snoozed = intent.getBooleanExtra("snoozed", false),
            )
        }
    }
}
