package com.brahmclock.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.brahmclock.data.StopwatchState
import com.brahmclock.repo
import kotlinx.coroutines.delay

@Composable
fun StopwatchScreen() {
    val repo = LocalContext.current.repo
    val state = repo.stopwatch
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state.startedAt) {
        while (state.startedAt != null) {
            now = System.currentTimeMillis()
            delay(33)
        }
    }
    val running = state.startedAt != null
    val elapsed = state.elapsed(if (running) now else 0)
    // Laps are stored as cumulative times; show each split newest first.
    val splits = state.laps.mapIndexed { i, t -> t - (state.laps.getOrNull(i - 1) ?: 0) }
    val best = if (splits.size >= 3) splits.min() else null
    val worst = if (splits.size >= 3) splits.max() else null

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        ScreenHeader("Stopwatch")
        Spacer(Modifier.height(32.dp))
        Text(Format.stopwatch(elapsed), fontSize = 60.sp, fontWeight = FontWeight.Light, fontFamily = FontFamily.Monospace)
        Spacer(Modifier.height(32.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (running) {
                OutlinedButton(
                    onClick = { val t = System.currentTimeMillis(); repo.updateStopwatch(state.copy(laps = state.laps + state.elapsed(t))) },
                    modifier = Modifier.width(130.dp).height(52.dp),
                ) { Text("Lap") }
                Button(
                    onClick = { val t = System.currentTimeMillis(); repo.updateStopwatch(state.copy(startedAt = null, accumulated = state.elapsed(t))) },
                    modifier = Modifier.width(130.dp).height(52.dp),
                ) { Text("Pause") }
            } else {
                OutlinedButton(
                    onClick = { repo.updateStopwatch(StopwatchState()) },
                    enabled = elapsed > 0,
                    modifier = Modifier.width(130.dp).height(52.dp),
                ) { Text("Reset") }
                Button(
                    onClick = { repo.updateStopwatch(state.copy(startedAt = System.currentTimeMillis())) },
                    modifier = Modifier.width(130.dp).height(52.dp),
                ) { Text(if (elapsed > 0) "Resume" else "Start") }
            }
        }
        Spacer(Modifier.height(16.dp))
        LazyColumn(contentPadding = PaddingValues(horizontal = 24.dp)) {
            items(splits.indices.reversed().toList()) { i ->
                val color = when (splits[i]) {
                    best -> MaterialTheme.colorScheme.primary
                    worst -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurface
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                    Text("Lap ${i + 1}", Modifier.weight(1f), color = color)
                    Text(Format.stopwatch(splits[i]), Modifier.weight(1f), color = color, fontFamily = FontFamily.Monospace)
                    Text(Format.stopwatch(state.laps[i]), color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = FontFamily.Monospace)
                }
                HorizontalDivider()
            }
        }
    }
}
