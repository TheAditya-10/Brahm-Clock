package com.brahmclock.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** AlarmManager forgets everything on reboot, and times shift on clock/zone changes. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Scheduler.rescheduleAll(context)
    }
}
