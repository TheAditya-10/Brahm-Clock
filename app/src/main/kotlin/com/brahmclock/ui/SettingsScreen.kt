package com.brahmclock.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.brahmclock.alarm.Scheduler
import com.brahmclock.data.ThemeMode
import com.brahmclock.repo

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = context.repo
    val s = repo.settings

    fun open(intent: Intent) {
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(Modifier.padding(padding)) {
            ScreenHeader("Settings", onBack)
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SectionCard {
                    Text("Theme", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(ThemeMode.DARK to "Night", ThemeMode.BLACK to "Black", ThemeMode.LIGHT to "Light", ThemeMode.SYSTEM to "System")
                            .forEach { (mode, label) -> Chip(label, s.theme == mode, { repo.updateSettings(s.copy(theme = mode)) }) }
                    }
                    Spacer(Modifier.height(12.dp))
                    SwitchRow("24-hour time", checked = s.use24h, onChange = {
                        repo.updateSettings(s.copy(use24h = it))
                        Scheduler.rescheduleAll(context)
                    })
                }

                SectionCard {
                    Text("Snooze length", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(5, 10, 15, 20).forEach { min ->
                            Chip("$min min", s.snoozeMinutes == min, { repo.updateSettings(s.copy(snoozeMinutes = min)) })
                        }
                    }
                }

                SectionCard {
                    Text("Alarm reliability", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Some phones stop apps in the background. Let Brahm Clock run unrestricted so it never misses a morning.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { open(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }, Modifier.fillMaxWidth()) {
                        Text("Battery optimisation")
                    }
                    OutlinedButton(onClick = {
                        open(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                    }, Modifier.fillMaxWidth()) { Text("Notification settings") }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        OutlinedButton(onClick = {
                            open(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
                        }, Modifier.fillMaxWidth()) { Text("Exact alarms") }
                    }
                }

                SectionCard {
                    Text("About Brahma Muhurta", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Ayurveda divides the day into muhurtas of 48 minutes. Brahma Muhurta is the one that begins " +
                            "96 minutes before sunrise and ends 48 minutes before it, a quiet time considered ideal for waking, " +
                            "meditation and study. Brahm Clock calculates sunrise on your phone from your location every day; " +
                            "no internet connection is needed.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Version 0.1.0", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}
