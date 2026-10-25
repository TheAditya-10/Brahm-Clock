package com.brahmclock.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import com.brahmclock.alarm.Scheduler
import com.brahmclock.core.Cities
import com.brahmclock.location.LocationHelper
import com.brahmclock.repo
import com.brahmclock.ui.theme.BrahmTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            BrahmTheme(repo.settings.theme) { App() }
        }
    }

    override fun onResume() {
        super.onResume()
        AppEvents.resumed.intValue++
        refreshLocation()
        Scheduler.rescheduleAll(this)
    }

    /** Sunrise follows you: re-locate quietly when you've moved more than a few km. */
    private fun refreshLocation() {
        if (!LocationHelper.hasPermission(this)) return
        LocationHelper.locate(this) { point ->
            val saved = repo.location
            if (point != null && (saved == null || Cities.distanceKm(saved.lat, saved.lon, point.lat, point.lon) > 5)) {
                repo.updateLocation(point)
                Scheduler.rescheduleAll(this)
            }
        }
    }
}

private enum class Tab(val emoji: String, val label: String) {
    BRAHMA("🌅", "Brahma"),
    ALARM("⏰", "Alarm"),
    CLOCK("🕐", "Clock"),
    TIMER("⏳", "Timer"),
    STOPWATCH("⏱️", "Stopwatch"),
}

private enum class Page { BRAHMA_SETTINGS, SETTINGS }

@Composable
private fun App() {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var page by rememberSaveable { mutableStateOf<Page?>(null) }
    BackHandler(enabled = page != null) { page = null }

    when (page) {
        Page.BRAHMA_SETTINGS -> BrahmaSettingsScreen(onBack = { page = null })
        Page.SETTINGS -> SettingsScreen(onBack = { page = null })
        null -> Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    Tab.entries.forEachIndexed { i, t ->
                        NavigationBarItem(
                            selected = tab == i,
                            onClick = { tab = i },
                            icon = { Text(t.emoji, fontSize = 20.sp) },
                            label = { Text(t.label, maxLines = 1) },
                        )
                    }
                }
            },
        ) { padding ->
            Box(Modifier.padding(padding)) {
                when (Tab.entries[tab]) {
                    Tab.BRAHMA -> BrahmaScreen(
                        onEdit = { page = Page.BRAHMA_SETTINGS },
                        onSettings = { page = Page.SETTINGS },
                    )
                    Tab.ALARM -> AlarmsScreen()
                    Tab.CLOCK -> ClockScreen()
                    Tab.TIMER -> TimerScreen()
                    Tab.STOPWATCH -> StopwatchScreen()
                }
            }
        }
    }
}
