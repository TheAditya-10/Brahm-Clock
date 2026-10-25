package com.brahmclock.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
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
import com.brahmclock.alarm.Scheduler
import com.brahmclock.core.AlarmMath
import com.brahmclock.data.AlarmItem
import com.brahmclock.repo
import java.time.Duration
import java.time.ZonedDateTime

@Composable
fun AlarmsScreen() {
    val context = LocalContext.current
    val repo = context.repo
    val use24h = repo.settings.use24h
    var editing by remember { mutableStateOf<AlarmItem?>(null) }
    val resumed = AppEvents.resumed.intValue

    fun save(alarm: AlarmItem) {
        repo.upsertAlarm(alarm)
        Scheduler.rescheduleAll(context)
    }

    Box(Modifier.fillMaxSize()) {
        Column {
            ScreenHeader("Alarms")
            val next = remember(repo.alarms, resumed) {
                val now = ZonedDateTime.now()
                repo.alarms.filter { it.enabled }.minOfOrNull { AlarmMath.nextOccurrence(now, it.time, it.days) }
                    ?.let { Duration.between(now, it) }
            }
            Text(
                next?.let { "Next alarm in ${Format.until(it)}" } ?: "No alarms on. Your Brahma alarm lives on the first tab.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            LazyColumn(
                contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(repo.alarms, key = { it.id }) { alarm ->
                    SectionCard(Modifier.clickable { editing = alarm }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    Format.time(alarm.time, use24h),
                                    fontSize = 36.sp,
                                    fontWeight = FontWeight.Light,
                                    color = if (alarm.enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    listOf(alarm.label, daysSummary(alarm.days)).filter { it.isNotBlank() }.joinToString(" · "),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(checked = alarm.enabled, onCheckedChange = { save(alarm.copy(enabled = it)) })
                        }
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = { editing = AlarmItem(id = repo.newAlarmId(), hour = 6, minute = 0) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
            containerColor = MaterialTheme.colorScheme.primary,
        ) { Text("+", fontSize = 28.sp) }
    }

    editing?.let { alarm ->
        AlarmEditor(
            alarm = alarm,
            isNew = repo.alarms.none { it.id == alarm.id },
            use24h = use24h,
            onSave = { save(it.copy(enabled = true)); editing = null },
            onDelete = {
                repo.deleteAlarm(alarm.id)
                Scheduler.cancelAlarm(context, alarm.id)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlarmEditor(
    alarm: AlarmItem,
    isNew: Boolean,
    use24h: Boolean,
    onSave: (AlarmItem) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val time = rememberTimePickerState(alarm.hour, alarm.minute, use24h)
    var draft by remember { mutableStateOf(alarm) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "New alarm" else "Edit alarm") },
        confirmButton = {
            TextButton(onClick = { onSave(draft.copy(hour = time.hour, minute = time.minute)) }) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (!isNew) TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TimeInput(state = time)
                OutlinedTextField(
                    value = draft.label,
                    onValueChange = { draft = draft.copy(label = it) },
                    label = { Text("Label") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Repeat: ${daysSummary(draft.days)}")
                DayChips(draft.days) { draft = draft.copy(days = it) }
                SoundPicker(draft.soundUri) { draft = draft.copy(soundUri = it) }
                SwitchRow("Vibrate", checked = draft.vibrate, onChange = { draft = draft.copy(vibrate = it) })
                Spacer(Modifier.height(4.dp))
            }
        },
    )
}
