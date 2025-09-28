package net.canyonwolf.sparklauncher.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Accent and base colors
private val NeonYellow = Color(0xFFCCFF00) // bright neon yellow-green (accent)

// Dark theme palette
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
)

// Light theme palette (whites, light grays, and a contrasting yellow accent)
private val LightBackground = Color(0xFFF5F5F7)
private val LightSurface = Color(0xFFFFFFFF)
private val LightSurfaceVariant = Color(0xFFE9EAEE)
private val OnLight = Color(0xFF222222)
private val OnLightVariant = Color(0xFF2E2E2E)

private val AppLightColors: ColorScheme = lightColorScheme(
    primary = Color(0xFFB59F00), // deeper yellow for contrast on light
    onPrimary = Color(0xFF1B1B1B),
    secondary = NeonYellow,
    onSecondary = Color(0xFF1B1B1B),
    tertiary = Color(0xFFFFD54F),
    onTertiary = Color(0xFF1B1B1B),

    background = LightBackground,
    onBackground = OnLight,

    surface = LightSurface,
    onSurface = OnLight,

    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = OnLightVariant,
)

@Composable
fun AppTheme(themeName: String = "Default", content: @Composable () -> Unit) {
    // Try community theme first (except for built-ins)
    val scheme: ColorScheme = when (themeName) {
        "Light" -> AppLightColors
        "Default" -> AppDarkColors
        else -> {
            val ct = ThemeManager.findByName(themeName)
            val fromMap = ct?.styles?.toColorSchemeOrNull()
            fromMap ?: AppDarkColors
        }
    }
    MaterialTheme(
        colorScheme = scheme,
        content = content
    )
}
