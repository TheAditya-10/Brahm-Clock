package com.brahmclock

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.brahmclock.alarm.Scheduler
import com.brahmclock.data.Repo

class BrahmApp : Application() {
    lateinit var repo: Repo
        private set

    override fun onCreate() {
        super.onCreate()
        repo = Repo(this)
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_RING, "Ringing alarms", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null) // RingService plays the sound itself
                enableVibration(false)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_SLEEP, "Sleep reminders", NotificationManager.IMPORTANCE_DEFAULT)
        )
        Scheduler.rescheduleAll(this)
    }

    companion object {
        const val CHANNEL_RING = "ring"
        const val CHANNEL_SLEEP = "sleep"
    }
}

val Context.repo: Repo get() = (applicationContext as BrahmApp).repo
