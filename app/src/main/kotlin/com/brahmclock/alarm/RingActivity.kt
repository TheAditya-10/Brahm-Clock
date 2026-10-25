package com.brahmclock.alarm

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.brahmclock.repo
import com.brahmclock.ui.Format
import com.brahmclock.ui.theme.BrahmTheme
import com.brahmclock.ui.theme.Dawn
import com.brahmclock.ui.theme.Dusk
import com.brahmclock.ui.theme.Night
import com.brahmclock.ui.theme.Saffron
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant
import java.time.LocalTime

/** Full-screen ringing UI shown over the lock screen. Closes itself when RingService stops. */
class RingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            BrahmTheme(repo.settings.theme) {
                val spec = RingService.current.value
                var seen by remember { mutableStateOf(false) }
                if (spec != null) seen = true
                LaunchedEffect(spec) {
                    // Opened just before the service started (previews): give it a moment to begin.
                    if (spec == null) {
                        if (!seen) delay(3000)
                        if (RingService.current.value == null) finish()
                    }
                }
                if (spec != null) {
                    RingScreen(
                        spec = spec,
                        use24h = repo.settings.use24h,
                        onDismiss = { RingService.send(this, RingService.ACTION_DISMISS) },
                        onSnooze = { RingService.send(this, RingService.ACTION_SNOOZE) },
                    )
                }
            }
        }
    }
}

@Composable
private fun RingScreen(spec: RingSpec, use24h: Boolean, onDismiss: () -> Unit, onSnooze: () -> Unit) {
    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalTime.now()
            delay(1000)
        }
    }
    val gradient = Brush.verticalGradient(listOf(Night, Dusk, Saffron.copy(alpha = 0.85f)))
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(gradient)
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(48.dp))
            Text(Format.time(now, use24h), fontSize = 72.sp, fontWeight = FontWeight.Light, color = Color.White)
            Spacer(Modifier.height(8.dp))
            Text(spec.title, fontSize = 22.sp, color = Dawn)
            if (spec.subtitle.isNotBlank()) Text(spec.subtitle, color = Color.White.copy(alpha = 0.8f))
            if (spec.kind == RingKind.BRAHMA) {
                Spacer(Modifier.height(24.dp))
                Text(
                    "Rise with the sun's first whisper.\nThe mind is calm and clear now.",
                    textAlign = TextAlign.Center,
                    color = Color.White.copy(alpha = 0.85f),
                )
                if (spec.sunriseMillis > 0) {
                    val left = Duration.between(Instant.now(), Instant.ofEpochMilli(spec.sunriseMillis))
                    if (!left.isNegative) {
                        Spacer(Modifier.height(12.dp))
                        Text("Sunrise in ${Format.until(left)}", color = Dawn)
                    }
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().height(64.dp),
                shape = RoundedCornerShape(32.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Dawn, contentColor = Night),
            ) {
                Text(if (spec.kind == RingKind.BRAHMA) "☀  I'm awake" else "Dismiss", fontSize = 20.sp)
            }
            if (spec.kind != RingKind.TIMER) {
                Spacer(Modifier.height(12.dp))
                val label = if (spec.kind == RingKind.BRAHMA && !spec.isFinal) "Quiet until next stage" else "Snooze"
                TextButton(onClick = onSnooze) { Text(label, color = Color.White) }
            }
        }
    }
}
