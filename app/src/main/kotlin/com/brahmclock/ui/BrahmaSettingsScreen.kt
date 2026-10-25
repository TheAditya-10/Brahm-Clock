package com.brahmclock.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.content.Intent
import com.brahmclock.alarm.RingActivity
import com.brahmclock.alarm.RingKind
import com.brahmclock.alarm.RingService
import com.brahmclock.alarm.RingSpec
import com.brahmclock.alarm.Scheduler
import com.brahmclock.core.Brahma
import com.brahmclock.repo
import java.time.DayOfWeek
import java.time.ZonedDateTime
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BrahmaSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = context.repo
    val cfg = repo.brahma
    val sound = repo.sound
    val use24h = repo.settings.use24h
    var editBackup by remember { mutableStateOf(false) }

    val preview = remember(cfg, repo.location) {
        Brahma.nextPlan(
            ZonedDateTime.now(), repo.location,
            cfg.copy(enabled = true, days = DayOfWeek.entries.toSet(), skipDate = null, doneDate = null),
        )
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(Modifier.padding(padding)) {
            ScreenHeader("Brahma alarm", onBack)
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SectionCard {
                    Text("Wake style", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Chip("Gentle", cfg.gentle, { context.saveBrahma(cfg.copy(gentle = true)) })
                        Chip("Standard", !cfg.gentle, { context.saveBrahma(cfg.copy(gentle = false)) })
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (cfg.gentle) "Soft pulses through the 48 minutes, each a little longer and louder, ending in a full alarm."
                        else "A single full alarm when Brahma Muhurta begins.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (cfg.gentle) {
                        Spacer(Modifier.height(12.dp))
                        Text("Stages: ${cfg.stageCount}")
                        SettledSlider(
                            value = cfg.stageCount.toFloat(),
                            onSettled = { context.saveBrahma(cfg.copy(stageCount = it.roundToInt())) },
                            valueRange = 3f..10f,
                            steps = 6,
                        )
                    }
                }

                if (preview != null) {
                    SectionCard {
                        Text("Timeline", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        preview.stages.forEach { stage ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(Format.time(stage.time.toLocalTime(), use24h), Modifier.width(84.dp))
                                LinearProgressIndicator(
                                    progress = { stage.volume },
                                    modifier = Modifier.weight(1f).height(6.dp),
                                )
                                Text(
                                    if (stage.isFinal) "  until dismissed" else "  ${stage.ringSeconds}s",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.width(110.dp),
                                )
                            }
                        }
                    }
                }

                SectionCard {
                    Text("Window starts ${cfg.offsetMinutes} min before sunrise", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Traditional: 96 minutes (two muhurtas of 48 minutes).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SettledSlider(
                        value = cfg.offsetMinutes.toFloat(),
                        onSettled = { context.saveBrahma(cfg.copy(offsetMinutes = (it / 6).roundToInt() * 6)) },
                        valueRange = 72f..120f,
                        steps = 7,
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Backup start time")
                            Text(
                                "Used when there's no location or no sunrise",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        OutlinedButton(onClick = { editBackup = true }) { Text(Format.time(cfg.backupStart, use24h)) }
                    }
                }

                SectionCard {
                    SoundPicker(sound.uri) { repo.updateSound(sound.copy(uri = it)); Scheduler.rescheduleAll(context) }
                    Spacer(Modifier.height(12.dp))
                    SwitchRow("Vibrate", "From the third stage onward", sound.vibrate) {
                        repo.updateSound(sound.copy(vibrate = it))
                        Scheduler.rescheduleAll(context)
                    }
                    Spacer(Modifier.height(12.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            RingService.start(
                                context,
                                RingSpec(RingKind.BRAHMA, "Brahma Muhurta", "Preview · gentle stage", 0.3f, 15, false, sound.uri, stageIndex = 0, stageTotal = 2),
                            )
                            context.startActivity(Intent(context, RingActivity::class.java))
                        }) { Text("Preview gentle") }
                        OutlinedButton(onClick = {
                            RingService.start(context, RingSpec(RingKind.BRAHMA, "Brahma Muhurta", "Preview · full alarm", soundUri = sound.uri, vibrate = sound.vibrate))
                            context.startActivity(Intent(context, RingActivity::class.java))
                        }) { Text("Preview full") }
                    }
                }

                SectionCard {
                    Text("Repeat", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    DayChips(cfg.days) { context.saveBrahma(cfg.copy(days = it)) }
                }

                SectionCard {
                    SwitchRow(
                        "🌙 Sleep reminder",
                        "A notification at bedtime so you get enough sleep",
                        cfg.sleepReminder,
                    ) { context.saveBrahma(cfg.copy(sleepReminder = it)) }
                    if (cfg.sleepReminder) {
                        Spacer(Modifier.height(12.dp))
                        Text("Sleep goal: ${Format.hours(cfg.sleepMinutes)}")
                        SettledSlider(
                            value = cfg.sleepMinutes / 60f,
                            onSettled = { context.saveBrahma(cfg.copy(sleepMinutes = ((it * 2).roundToInt() * 30))) },
                            valueRange = 5f..10f,
                            steps = 9,
                        )
                        Text("Remind me")
                        Spacer(Modifier.height(6.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(0 to "On time", 15 to "15m early", 30 to "30m", 60 to "1h").forEach { (min, label) ->
                                Chip(label, cfg.reminderAdvanceMinutes == min, { context.saveBrahma(cfg.copy(reminderAdvanceMinutes = min)) })
                            }
                        }
                        preview?.let {
                            Spacer(Modifier.height(8.dp))
                            val bedtime = it.window.start.minusMinutes(cfg.sleepMinutes.toLong())
                            Text(
                                "Next bedtime: ${Format.time(bedtime.toLocalTime(), use24h)}",
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    if (editBackup) {
        TimeDialog(
            initial = cfg.backupStart,
            use24h = use24h,
            onConfirm = { context.saveBrahma(cfg.copy(backupStart = it)); editBackup = false },
            onDismiss = { editBackup = false },
        )
    }
}
