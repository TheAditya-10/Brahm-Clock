package com.brahmclock.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.brahmclock.data.ThemeMode

val Saffron = Color(0xFFFFA94D)
val Dawn = Color(0xFFFFD27A)
val Night = Color(0xFF0B1026)
val NightSurface = Color(0xFF161C40)
val Dusk = Color(0xFF3A2E6E)

private val DarkColors = darkColorScheme(
    primary = Saffron,
    onPrimary = Color(0xFF2B1600),
    secondary = Dawn,
    onSecondary = Color(0xFF2B1F00),
    background = Night,
    onBackground = Color(0xFFECE9F5),
    surface = Night,
    onSurface = Color(0xFFECE9F5),
    surfaceVariant = NightSurface,
    onSurfaceVariant = Color(0xFFB9B5CF),
    surfaceContainer = NightSurface,
    surfaceContainerHigh = Color(0xFF1E2550),
    secondaryContainer = Dusk,
    onSecondaryContainer = Dawn,
)

private val BlackColors = DarkColors.copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceVariant = Color(0xFF121212),
    surfaceContainer = Color(0xFF121212),
    surfaceContainerHigh = Color(0xFF1A1A1A),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFFC25E00),
    onPrimary = Color.White,
    secondary = Color(0xFF8A5A00),
    background = Color(0xFFFFF8EE),
    surface = Color(0xFFFFF8EE),
    surfaceVariant = Color(0xFFF7E9D4),
    surfaceContainer = Color(0xFFF7E9D4),
    secondaryContainer = Color(0xFFFFDDB3),
)

@Composable
fun BrahmTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val colors = when (mode) {
        ThemeMode.DARK -> DarkColors
        ThemeMode.BLACK -> BlackColors
        ThemeMode.LIGHT -> LightColors
        ThemeMode.SYSTEM -> if (isSystemInDarkTheme()) DarkColors else LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}
