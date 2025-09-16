package net.canyonwolf.sparklauncher.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Neon yellow accent with gray dark theme
private val NeonYellow = Color(0xFFCCFF00) // bright neon yellow-green
private val DarkerDarkGray = Color(0xFF1E1E1E) // menu bar background
private val LighterDarkGray = Color(0xFF2B2B2B) // app/page background
private val OnDark = Color(0xFFE6E6E6)
private val OnDarker = Color(0xFFF2F2F2)
private val NearBlack = Color(0xFF0A0A0A)

private val AppDarkColors: ColorScheme = darkColorScheme(
    primary = NeonYellow,
    onPrimary = NearBlack,
    secondary = NeonYellow,
    onSecondary = NearBlack,
    tertiary = NeonYellow,
    onTertiary = NearBlack,

    background = LighterDarkGray,
    onBackground = OnDark,

    surface = LighterDarkGray,
    onSurface = OnDark,

    // Use surfaceVariant to represent the top app bar darker background
    surfaceVariant = DarkerDarkGray,
    onSurfaceVariant = OnDarker,

    // Keep error defaults
)

@Composable
fun AppTheme(themeName: String = "Default", content: @Composable () -> Unit) {
    // For now we only have the Default theme, but this allows extending later
    val scheme = when (themeName) {
        else -> AppDarkColors
    }
    MaterialTheme(
        colorScheme = scheme,
        content = content
    )
}
