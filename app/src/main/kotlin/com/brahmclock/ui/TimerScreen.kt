package com.brahmclock.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.brahmclock.alarm.Scheduler
import com.brahmclock.data.TimerState
import com.brahmclock.repo
import kotlinx.coroutines.delay

@Composable
fun TimerScreen() {
    val context = LocalContext.current
    val repo = context.repo
    val state = repo.timer
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state.endAt) {
        while (state.endAt != null) {
            now = System.currentTimeMillis()
            delay(200)
        }
    }

    fun save(s: TimerState) {
        repo.updateTimer(s)
        Scheduler.rescheduleAll(context)
    }

    val running = state.endAt != null
    val paused = state.pausedRemaining != null
    val remaining = state.endAt?.let { it - now } ?: state.pausedRemaining ?: state.totalMillis
    val progress = if (running || paused) (remaining.toFloat() / state.totalMillis).coerceIn(0f, 1f) else 1f
    val primary = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceVariant

    Column(
        Modifier.verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ScreenHeader("Timer")
        Box(Modifier.size(260.dp).padding(8.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxWidth().height(244.dp)) {
                val stroke = Stroke(12.dp.toPx(), cap = StrokeCap.Round)
                drawArc(track, 0f, 360f, false, style = stroke)
                drawArc(primary, -90f, 360f * progress, false, style = stroke)
            }
            Text(Format.clock(remaining), fontSize = 52.sp, fontWeight = FontWeight.Light)
        }
        Spacer(Modifier.height(16.dp))

        if (!running && !paused) {
            val presets = listOf(1, 3, 5, 10, 15, 25, 30, 60)
            presets.chunked(4).forEach { row ->
                Row(Modifier.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { min ->
                        Chip("$min min", state.totalMillis == min * 60_000L, { save(state.copy(totalMillis = min * 60_000L)) }, Modifier.width(76.dp))
                    }
                }
            }
            var custom by remember { mutableStateOf("") }
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    custom, { custom = it.filter(Char::isDigit).take(3) },
                    label = { Text("Minutes") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(140.dp),
                )
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = {
                    custom.toIntOrNull()?.takeIf { it > 0 }?.let { save(state.copy(totalMillis = it * 60_000L)) }
                    custom = ""
                }) { Text("Set") }
            }
            Button(
                onClick = { save(state.copy(endAt = System.currentTimeMillis() + state.totalMillis, pausedRemaining = null)) },
                modifier = Modifier.width(200.dp).height(56.dp),
            ) { Text("Start", fontSize = 18.sp) }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { save(TimerState(totalMillis = state.totalMillis)) }) { Text("Reset") }
                if (running) {
                    Button(onClick = { save(state.copy(endAt = null, pausedRemaining = remaining.coerceAtLeast(0))) }) { Text("Pause") }
                    OutlinedButton(onClick = {
                        save(state.copy(endAt = state.endAt!! + 60_000L, totalMillis = state.totalMillis + 60_000L))
                    }) { Text("+1 min") }
                } else {
                    Button(onClick = {
                        save(state.copy(endAt = System.currentTimeMillis() + state.pausedRemaining!!, pausedRemaining = null))
                    }) { Text("Resume") }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
