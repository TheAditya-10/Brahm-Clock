package com.brahmclock.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.brahmclock.repo
import kotlinx.coroutines.delay
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

private val dateFormat = DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.ENGLISH)
private val secondsFormat = DateTimeFormatter.ofPattern(":ss", Locale.ENGLISH)

private fun cityName(zoneId: String) = zoneId.substringAfterLast('/').replace('_', ' ')

@Composable
fun ClockScreen() {
    val context = LocalContext.current
    val repo = context.repo
    val use24h = repo.settings.use24h
    var now by remember { mutableStateOf(ZonedDateTime.now()) }
    var adding by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            now = ZonedDateTime.now()
            delay(1000L - System.currentTimeMillis() % 1000)
        }
    }

    Column {
        ScreenHeader("Clock") { TextButton(onClick = { adding = true }) { Text("+ City") } }
        Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(Format.time(now.toLocalTime(), use24h), fontSize = 56.sp, fontWeight = FontWeight.Light)
                Text(now.format(secondsFormat), fontSize = 22.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(now.format(dateFormat), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HorizontalDivider()
        LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
            items(repo.zones) { id ->
                val zone = runCatching { ZoneId.of(id) }.getOrNull() ?: return@items
                val there = now.withZoneSameInstant(zone)
                val diffMin = (there.offset.totalSeconds - now.offset.totalSeconds) / 60
                Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(cityName(id), style = MaterialTheme.typography.titleMedium)
                        Text(
                            when {
                                diffMin == 0 -> "Same time"
                                else -> (if (diffMin > 0) "+" else "−") + "%d:%02d".format(abs(diffMin) / 60, abs(diffMin) % 60) +
                                    if (there.toLocalDate() != now.toLocalDate()) (if (there.toLocalDate() > now.toLocalDate()) " · tomorrow" else " · yesterday") else ""
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(Format.time(there.toLocalTime(), use24h), fontSize = 26.sp, fontWeight = FontWeight.Light)
                    TextButton(onClick = { repo.setZones(repo.zones - id) }) { Text("✕") }
                }
                HorizontalDivider()
            }
        }
    }

    if (adding) {
        var query by remember { mutableStateOf("") }
        val all = remember {
            ZoneId.getAvailableZoneIds().filter { '/' in it && !it.startsWith("Etc") && !it.startsWith("SystemV") }.sortedBy(::cityName)
        }
        AlertDialog(
            onDismissRequest = { adding = false },
            confirmButton = { TextButton(onClick = { adding = false }) { Text("Close") } },
            title = { Text("Add city") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(query, { query = it }, label = { Text("Search") }, singleLine = true)
                    LazyColumn(Modifier.height(320.dp)) {
                        items(all.filter { query.isBlank() || it.contains(query.trim().replace(' ', '_'), ignoreCase = true) }) { id ->
                            Text(
                                "${cityName(id)}  ·  ${id.substringBefore('/')}",
                                modifier = Modifier.fillMaxWidth().clickable {
                                    if (id !in repo.zones) repo.setZones(repo.zones + id)
                                    adding = false
                                }.padding(vertical = 10.dp),
                            )
                        }
                    }
                }
            },
        )
    }
}
