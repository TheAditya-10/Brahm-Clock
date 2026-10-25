package com.brahmclock.ui

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.brahmclock.alarm.Scheduler
import com.brahmclock.core.Brahma
import com.brahmclock.core.BrahmaConfig
import com.brahmclock.core.BrahmaWindow
import com.brahmclock.core.Cities
import com.brahmclock.core.GeoPoint
import com.brahmclock.location.LocationHelper
import com.brahmclock.repo
import com.brahmclock.ui.theme.Dawn
import com.brahmclock.ui.theme.Saffron
import kotlinx.coroutines.delay
import java.time.DayOfWeek
import java.time.Duration
import java.time.ZonedDateTime
import kotlin.math.cos
import kotlin.math.sin

fun Context.saveBrahma(config: BrahmaConfig) {
    repo.updateBrahma(config)
    Scheduler.rescheduleAll(this)
}

@Composable
fun BrahmaScreen(onEdit: () -> Unit, onSettings: () -> Unit) {
    val context = LocalContext.current
    val repo = context.repo
    val cfg = repo.brahma
    val location = repo.location
    val use24h = repo.settings.use24h
    var now by remember { mutableStateOf(ZonedDateTime.now()) }
    val resumed = AppEvents.resumed.intValue
    LaunchedEffect(resumed) {
        while (true) {
            now = ZonedDateTime.now()
            delay(30_000)
        }
    }
    var showLocation by remember { mutableStateOf(false) }

    val plan = remember(cfg, location, now) { Brahma.nextPlan(now, location, cfg) }
    // Even when the alarm is off, show the upcoming window.
    val preview = remember(cfg, location, now) {
        Brahma.nextPlan(now, location, cfg.copy(enabled = true, days = DayOfWeek.entries.toSet(), skipDate = null, doneDate = null))
    }
    val window = plan?.window ?: preview?.window

    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Brahm Clock", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "📍 ${location?.label?.ifBlank { null } ?: "Set location"}  ▾",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable { showLocation = true }.padding(vertical = 4.dp),
                )
            }
            TextButton(onClick = onSettings) { Text("⚙️", fontSize = 22.sp) }
        }

        PermissionCards(resumed, needsLocation = location == null, onPickLocation = { showLocation = true })

        if (window != null) {
            DawnArc(window, now)
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "BRAHMA MUHURTA · ${dayLabel(window, now)}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary,
                    letterSpacing = 2.sp,
                )
                Text(
                    "${Format.time(window.start.toLocalTime(), use24h)} – ${Format.time(window.end.toLocalTime(), use24h)}",
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Light,
                )
                val sunrise = window.sunrise
                Text(
                    if (sunrise != null) "Sunrise ${Format.time(sunrise.toLocalTime(), use24h)}"
                    else "No sunrise data — using backup time",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (plan != null) {
                    Text(
                        "Next ring in ${Format.until(Duration.between(now, plan.next.time))}",
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        SectionCard {
            SwitchRow(
                title = "Adaptive alarm",
                subtitle = if (cfg.gentle) "Gentle · ${cfg.stageCount} rising stages" else "Standard · one alarm at the window start",
                checked = cfg.enabled,
                onChange = { context.saveBrahma(cfg.copy(enabled = it)) },
            )
            Spacer(Modifier.height(12.dp))
            DayChips(cfg.days) { context.saveBrahma(cfg.copy(days = it)) }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val skipped = cfg.skipDate
                if (skipped != null && !skipped.isBefore(now.toLocalDate())) {
                    OutlinedButton(onClick = { context.saveBrahma(cfg.copy(skipDate = null)) }) {
                        Text("Undo skip (${skipped.dayOfWeek.name.take(3).lowercase().replaceFirstChar(Char::uppercase)})")
                    }
                } else if (plan != null) {
                    OutlinedButton(onClick = { context.saveBrahma(cfg.copy(skipDate = plan.window.date)) }) {
                        Text("Skip ${dayLabel(plan.window, now).lowercase()}")
                    }
                }
                Button(onClick = onEdit) { Text("Edit") }
            }
        }

        SleepCard(cfg, location, now, use24h)
        StreakCard(cfg, repo.wokeDates, now)
        Spacer(Modifier.height(8.dp))
    }

    if (showLocation) {
        LocationDialog(
            onPick = {
                repo.updateLocation(it)
                Scheduler.rescheduleAll(context)
                showLocation = false
            },
            onDismiss = { showLocation = false },
        )
    }
}

private fun dayLabel(window: BrahmaWindow, now: ZonedDateTime): String {
    val today = now.toLocalDate()
    return when (window.date) {
        today -> "TODAY"
        today.plusDays(1) -> "TOMORROW"
        else -> window.date.dayOfWeek.name
    }
}

/** Half-circle sky: night on the left, the Brahma window in saffron, the sun at the right; dot = now. */
@Composable
private fun DawnArc(window: BrahmaWindow, now: ZonedDateTime) {
    val sunrise = window.sunrise ?: window.end.plusMinutes(48)
    val from = window.start.minusMinutes(90)
    val to = sunrise.plusMinutes(15)
    val total = Duration.between(from, to).seconds.toFloat()
    fun frac(t: ZonedDateTime) = (Duration.between(from, t).seconds / total).coerceIn(0f, 1f)
    val track = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
    val nowFrac = Duration.between(from, now).seconds / total

    Canvas(Modifier.fillMaxWidth().height(150.dp).padding(horizontal = 24.dp)) {
        val stroke = 10.dp.toPx()
        val radius = minOf(size.width / 2, size.height) - stroke
        val center = Offset(size.width / 2, size.height - stroke / 2)
        val topLeft = Offset(center.x - radius, center.y - radius)
        val arcSize = Size(radius * 2, radius * 2)
        fun point(f: Float): Offset {
            val angle = Math.toRadians(180.0 + 180.0 * f)
            return Offset(center.x + radius * cos(angle).toFloat(), center.y + radius * sin(angle).toFloat())
        }
        drawLine(track, Offset(0f, center.y), Offset(size.width, center.y), strokeWidth = 2.dp.toPx())
        drawArc(track, 180f, 180f, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        val s = frac(window.start)
        val e = frac(window.end)
        drawArc(Saffron, 180f + 180f * s, 180f * (e - s), false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        drawCircle(Dawn, radius = 14.dp.toPx(), center = point(frac(sunrise)))
        if (nowFrac in 0f..1f) {
            drawCircle(Color.White, radius = 7.dp.toPx(), center = point(nowFrac))
        }
    }
}

@Composable
private fun SleepCard(cfg: BrahmaConfig, location: GeoPoint?, now: ZonedDateTime, use24h: Boolean) {
    val context = LocalContext.current
    val next = remember(cfg, location, now) {
        Brahma.nextSleepReminder(now, location, cfg.copy(enabled = true, sleepReminder = true))
    }
    SectionCard {
        SwitchRow(
            title = "🌙 Sleep reminder",
            subtitle = next?.let {
                "Bedtime ${Format.time(it.bedtime.toLocalTime(), use24h)} · ${Format.hours(cfg.sleepMinutes)} before Brahma Muhurta"
            } ?: "Reminds you to sleep ${Format.hours(cfg.sleepMinutes)} before Brahma Muhurta",
            checked = cfg.sleepReminder && cfg.enabled,
            onChange = { context.saveBrahma(cfg.copy(sleepReminder = it, enabled = cfg.enabled || it)) },
        )
    }
}

@Composable
private fun StreakCard(cfg: BrahmaConfig, woke: Set<java.time.LocalDate>, now: ZonedDateTime) {
    val today = now.toLocalDate()
    val streak = Brahma.streak(woke, today, cfg.days)
    SectionCard {
        Text("🔥 Wake streak: $streak ${if (streak == 1) "day" else "days"}", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            (6 downTo 0).forEach { back ->
                val d = today.minusDays(back.toLong())
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(d.dayOfWeek.name.take(1), style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.height(4.dp))
                    val active = d.dayOfWeek in cfg.days
                    Box(
                        Modifier
                            .size(16.dp)
                            .then(
                                when {
                                    d in woke -> Modifier.background(Saffron, CircleShape)
                                    active -> Modifier.border(1.5.dp, MaterialTheme.colorScheme.onSurfaceVariant, CircleShape)
                                    else -> Modifier
                                }
                            ),
                        contentAlignment = Alignment.Center,
                    ) { if (d !in woke && !active) Text("–", fontSize = 10.sp) }
                }
            }
        }
    }
}

@Composable
private fun PermissionCards(resumed: Int, needsLocation: Boolean, onPickLocation: () -> Unit) {
    val context = LocalContext.current
    var tick by remember { mutableStateOf(0) }
    val key = resumed + tick

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }
    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            LocationHelper.locate(context) { point ->
                if (point != null) {
                    context.repo.updateLocation(point)
                    Scheduler.rescheduleAll(context)
                } else {
                    onPickLocation()
                }
            }
        }
        tick++
    }

    val needsNotifications = remember(key) {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    }
    val needsExact = remember(key) { !Scheduler.canScheduleExact(context) }
    val needsFullScreen = remember(key) {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            !context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
    }

    if (needsLocation) {
        SectionCard {
            Text("Where do you wake up?", style = MaterialTheme.typography.titleMedium)
            Text(
                "Brahma Muhurta follows your local sunrise. Your location never leaves the phone.",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { locationLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION) }) { Text("Use my location") }
                OutlinedButton(onClick = onPickLocation) { Text("Choose city") }
            }
        }
    }
    if (needsNotifications) {
        PermissionCard("Allow notifications so alarms and bedtime reminders can appear.") {
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    if (needsExact && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        PermissionCard("Allow exact alarms so the alarm rings on the minute.") {
            context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
        }
    }
    if (needsFullScreen && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        PermissionCard("Allow full-screen alarms so the alarm shows over the lock screen.") {
            context.startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${context.packageName}")))
        }
    }
}

@Composable
private fun PermissionCard(text: String, onAllow: () -> Unit) {
    SectionCard {
        Text(text, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        Button(onClick = onAllow) { Text("Allow") }
    }
}

@Composable
fun LocationDialog(onPick: (GeoPoint) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var manual by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            status = "Locating…"
            LocationHelper.locate(context) { p -> if (p != null) onPick(p) else status = "Location unavailable. Pick a city instead." }
        } else {
            status = "Permission denied. Pick a city instead."
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text("Location") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { launcher.launch(Manifest.permission.ACCESS_COARSE_LOCATION) }, modifier = Modifier.fillMaxWidth()) {
                    Text("📍 Use my location")
                }
                status?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                OutlinedTextField(query, { query = it }, label = { Text("Search city") }, singleLine = true)
                LazyColumn(Modifier.height(220.dp)) {
                    items(Cities.search(query)) { city ->
                        Text(
                            city.label,
                            modifier = Modifier.fillMaxWidth().clickable { onPick(city) }.padding(vertical = 10.dp),
                        )
                        HorizontalDivider()
                    }
                }
                OutlinedTextField(
                    manual, { manual = it },
                    label = { Text("Or coordinates: lat, lon") },
                    singleLine = true,
                    trailingIcon = {
                        TextButton(onClick = {
                            val parts = manual.split(",").map { it.trim().toDoubleOrNull() }
                            val lat = parts.getOrNull(0)
                            val lon = parts.getOrNull(1)
                            if (lat != null && lon != null && lat in -90.0..90.0 && lon in -180.0..180.0) {
                                onPick(GeoPoint(lat, lon, Cities.nearest(lat, lon)?.label ?: "%.2f, %.2f".format(lat, lon)))
                            } else {
                                status = "Enter coordinates like 28.61, 77.21"
                            }
                        }) { Text("Set") }
                    },
                )
            }
        },
    )
}
