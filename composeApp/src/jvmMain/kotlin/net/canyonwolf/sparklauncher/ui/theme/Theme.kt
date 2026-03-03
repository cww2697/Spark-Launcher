package net.canyonwolf.sparklauncher.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

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
    primary = Color(0xFF00B8D4), // adjusted Cyan for light mode
    onPrimary = Color.White,
    secondary = Color(0xFF00B8D4),
    onSecondary = Color.White,
    tertiary = Color(0xFF64DD17),
    onTertiary = Color.White,

    background = Color(0xFFF1F5F9),
    onBackground = Color(0xFF0F172A),

    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1E293B),

    surfaceVariant = Color(0xFFE2E8F0),
    onSurfaceVariant = Color(0xFF475569),
    outline = Color(0xFFCBD5E1),
    outlineVariant = Color(0xFFF1F5F9)
)

@Composable
fun AppTheme(themeName: String = "Default", content: @Composable () -> Unit) {
    // Liquid Glass is now the base for all themes
    val isLight = themeName == "Light"
    val scheme: ColorScheme = if (isLight) AppLightColors else AppLiquidGlassColors
    
    MaterialTheme(
        colorScheme = scheme,
        shapes = LiquidGlassShapes,
        content = content
    )
}

private val LiquidGlassShapes = androidx.compose.material3.Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)
)

private val AppLiquidGlassColors: ColorScheme = darkColorScheme(
    primary = Color(0xFF00E5FF),
    onPrimary = Color(0xFF001A1A),
    secondary = Color(0xFF00E5FF),
    onSecondary = Color(0xFF001A1A),
    tertiary = Color(0xFF76FF03),
    onTertiary = Color(0xFF0A1A00),

    background = Color(0xFF0F172A),
    onBackground = Color(0xFFE2E8F0),

    surface = Color(0xFF1E293B),
    onSurface = Color(0xFFF1F5F9),

    surfaceVariant = Color(0xFF334155),
    onSurfaceVariant = Color(0xFFCBD5E1),
    outline = Color(0xFF475569),
    outlineVariant = Color(0xFF1E293B)
)

@Composable
fun Modifier.liquidGlass(
    color: Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f),
    borderColor: Color = Color.White.copy(alpha = 0.15f),
    shape: androidx.compose.ui.graphics.Shape = MaterialTheme.shapes.medium,
    borderWidth: Dp = 1.dp
): Modifier = this.then(
    Modifier
        .clip(shape)
        .background(color)
        .border(borderWidth, borderColor, shape)
)
