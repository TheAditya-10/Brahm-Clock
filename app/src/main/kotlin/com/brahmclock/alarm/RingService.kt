package com.brahmclock.alarm

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.mutableStateOf
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.brahmclock.BrahmApp
import com.brahmclock.R
import com.brahmclock.repo
import java.time.LocalDate

/** Plays one alarm at a time in the foreground: fades in, optionally stops on its own (gentle pulses). */
class RingService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var spec: RingSpec? = null

    private val timeout = Runnable { stopRinging() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DISMISS -> dismiss()
            ACTION_SNOOZE -> snooze()
            else -> {
                val s = intent?.let(RingSpec::from)
                if (s == null) stopRinging() else begin(s)
            }
        }
        return START_NOT_STICKY
    }

    private fun begin(s: RingSpec) {
        // startForeground must come first: the system kills services that don't call it within seconds.
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, notification(s),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
        )
        stopPlayback()
        spec = s
        current.value = s
        play(s)
        if (s.vibrate) vibrate()
        // Gentle pulses end on their own; full alarms give up after 10 minutes like stock clocks.
        handler.postDelayed(timeout, if (s.ringSeconds > 0) s.ringSeconds * 1000L else 10 * 60_000L)
    }

    private fun play(s: RingSpec) {
        val candidates = listOfNotNull(
            s.soundUri?.let(Uri::parse),
            RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
        )
        for (uri in candidates) {
            val mp = MediaPlayer()
            try {
                mp.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                mp.setDataSource(this, uri)
                mp.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK)
                mp.isLooping = true
                mp.setVolume(0f, 0f)
                mp.prepare()
                mp.start()
                player = mp
                fadeIn(mp, s)
                return
            } catch (e: Exception) {
                mp.release() // e.g. a library file that was moved or whose permission was revoked
            }
        }
    }

    private fun fadeIn(mp: MediaPlayer, s: RingSpec) {
        val rampMs = if (s.ringSeconds > 0) minOf(10_000L, s.ringSeconds * 1000L / 3) else 15_000L
        val steps = (rampMs / FADE_STEP_MS).toInt().coerceAtLeast(1)
        for (i in 1..steps) {
            handler.postDelayed({
                if (player === mp) mp.setVolume(s.volume * i / steps, s.volume * i / steps)
            }, i * FADE_STEP_MS)
        }
    }

    private fun vibrate() {
        val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java)
        }
        vibrator = v
        v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 600, 900), 0))
    }

    private fun dismiss() {
        val s = spec
        if (s?.kind == RingKind.BRAHMA && s.date != null) {
            val date = LocalDate.parse(s.date)
            repo.markWoke(date)
            repo.updateBrahma(repo.brahma.copy(doneDate = date))
            Scheduler.rescheduleAll(this) // drops the remaining stages for this morning
        }
        stopRinging()
    }

    private fun snooze() {
        val s = spec
        // A gentle stage simply goes quiet until the next stage; final alarms snooze normally.
        if (s != null && s.kind != RingKind.TIMER && (s.kind != RingKind.BRAHMA || s.isFinal)) {
            Scheduler.snooze(this, s, repo.settings.snoozeMinutes)
        }
        stopRinging()
    }

    private fun stopPlayback() {
        handler.removeCallbacksAndMessages(null)
        player?.runCatching { stop(); release() }
        player = null
        vibrator?.cancel()
        vibrator = null
    }

    private fun stopRinging() {
        stopPlayback()
        spec = null
        current.value = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopPlayback()
        current.value = null
        super.onDestroy()
    }

    private fun notification(s: RingSpec) = NotificationCompat.Builder(this, BrahmApp.CHANNEL_RING)
        .setSmallIcon(R.drawable.ic_stat_sun)
        .setContentTitle(s.title)
        .setContentText(s.subtitle)
        .setCategory(NotificationCompat.CATEGORY_ALARM)
        .setPriority(NotificationCompat.PRIORITY_MAX)
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        .setOngoing(true)
        .setFullScreenIntent(activityIntent(), true)
        .setContentIntent(activityIntent())
        .addAction(0, if (s.kind == RingKind.BRAHMA) "I'm awake" else "Dismiss", serviceIntent(ACTION_DISMISS))
        .apply { if (s.kind != RingKind.TIMER) addAction(0, "Snooze", serviceIntent(ACTION_SNOOZE)) }
        .build()

    private fun activityIntent() = PendingIntent.getActivity(
        this, 0,
        Intent(this, RingActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun serviceIntent(action: String) = PendingIntent.getService(
        this, action.hashCode(), Intent(this, RingService::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    companion object {
        const val ACTION_DISMISS = "com.brahmclock.DISMISS"
        const val ACTION_SNOOZE = "com.brahmclock.SNOOZE"
        private const val NOTIFICATION_ID = 1
        private const val FADE_STEP_MS = 500L

        /** What is ringing right now, observed by RingActivity. */
        val current = mutableStateOf<RingSpec?>(null)

        fun start(context: Context, spec: RingSpec) {
            context.startForegroundService(spec.writeTo(Intent(context, RingService::class.java)))
        }

        fun send(context: Context, action: String) {
            context.startService(Intent(context, RingService::class.java).setAction(action))
        }
    }
}
