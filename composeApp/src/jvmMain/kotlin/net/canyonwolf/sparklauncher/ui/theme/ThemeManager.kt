package net.canyonwolf.sparklauncher.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import net.canyonwolf.sparklauncher.config.ConfigManager
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.name

/**
 * Community theme loader and manager.
 *
 * Responsibilities:
 * - Ensure %APPDATA%/SparkLauncher/themes exists on startup.
 * - Create a template.json with default values and explanatory comments if no theme files exist yet.
 * - Load all .json files (excluding template.json) into CommunityTheme structures.
 */
object ThemeManager {
    data class CommunityTheme(val name: String, val styles: Map<String, String>)

    private var loadedThemes: List<CommunityTheme> = emptyList()

    private fun themesDir(): Path = ConfigManager.getThemesDir()

    fun init() {
        val dir = themesDir()
        try {
            if (!dir.exists()) {
                Files.createDirectories(dir)
            }
        } catch (_: Exception) {
        }

        // If directory exists but has no .json files, create a template
        try {
            val hasAny = Files.list(dir).use { stream ->
                stream.anyMatch { it.toString().endsWith(".json", ignoreCase = true) }
            }
            if (!hasAny) {
                val templatePath = dir.resolve("template.json")
                if (!Files.exists(templatePath)) {
                    Files.write(templatePath, defaultTemplateJson().toByteArray(StandardCharsets.UTF_8))
                }
            }
        } catch (_: Exception) {
        }

        // Load themes
        loadedThemes = loadAll()
    }

    fun listThemeNames(): List<String> = loadedThemes.map { it.name }

    fun findByName(name: String): CommunityTheme? = loadedThemes.firstOrNull { it.name.equals(name, true) }

    private fun loadAll(): List<CommunityTheme> {
        val dir = themesDir()
        if (!dir.exists() || !dir.isDirectory()) return emptyList()
        val list = mutableListOf<CommunityTheme>()
        try {
            Files.list(dir).use { stream ->
                stream.filter { p ->
                    val n = p.name.lowercase()
                    n.endsWith(".json") && n != "template.json"
                }.forEach { p ->
                    parseThemeJson(p)?.let { list += it }
                }
            }
        } catch (_: Exception) {
        }
        return list
    }

    private fun parseThemeJson(path: Path): CommunityTheme? {
        return try {
            val raw = Files.readAllLines(path, StandardCharsets.UTF_8).joinToString("\n")
            // Strip C/C++ style // comments for permissive templates
            val json = raw.lines().joinToString("\n") { line ->
                val i = line.indexOf("//")
                if (i >= 0) line.substring(0, i) else line
            }
            val name = Regex("\"name\"\\s*:\\s*\"([^\"]*)\"").find(json)?.groupValues?.get(1)?.trim()
                ?: return null
            val stylesBlock =
                Regex("\"styles\"\\s*:\\s*\\{(.*)}", setOf(RegexOption.DOT_MATCHES_ALL)).find(json)?.groupValues?.get(1)
                    ?: return CommunityTheme(name, emptyMap())
            val kvRegex = Regex("\"([A-Za-z0-9_]+)\"\\s*:\\s*\"([^\"]*)\"")
            val styles = kvRegex.findAll(stylesBlock).associate { it.groupValues[1] to it.groupValues[2] }
            CommunityTheme(name, styles)
        } catch (_: Exception) {
            null
        }
    }

    private fun defaultTemplateJson(): String {
        // Default (Dark) values taken from Theme.kt
        val defaultStyles = mapOf(
            // accent
            "primary" to "#CCFF00",
            "onPrimary" to "#0A0A0A",
            "secondary" to "#CCFF00",
            "onSecondary" to "#0A0A0A",
            "tertiary" to "#CCFF00",
            "onTertiary" to "#0A0A0A",
            // backgrounds
            "background" to "#2B2B2B",
            "onBackground" to "#E6E6E6",
            "surface" to "#2B2B2B",
            "onSurface" to "#E6E6E6",
            "surfaceVariant" to "#1E1E1E",
            "onSurfaceVariant" to "#F2F2F2"
        )
        val stylesJson = defaultStyles.entries.joinToString(",\n") { (k, v) ->
            "    \"$k\": \"$v\""
        }
        return """
        // SparkLauncher Community Theme Template
        // Copy this file and rename it (e.g., MyCoolTheme.json). Then edit values below.
        // Supported color keys (all optional, hex format #RRGGBB):
        // primary, onPrimary, secondary, onSecondary, tertiary, onTertiary,
        // background, onBackground, surface, onSurface, surfaceVariant, onSurfaceVariant
        // The app will fall back to the default dark theme for any missing keys.
        {
          "name": "My Custom Theme",
          "styles": {
$stylesJson
          }
        }
        """.trimIndent()
    }
}

fun Map<String, String>.toColorSchemeOrNull(): ColorScheme? {
    fun parse(hex: String?): Color? {
        if (hex.isNullOrBlank()) return null
        val s = hex.trim().removePrefix("#")
        return try {
            val v = s.toLong(16).toInt()
            Color(0xFF000000.toInt() or v)
        } catch (_: Exception) {
            null
        }
    }

    val p = parse(this["primary"]) ?: return null
    val onp = parse(this["onPrimary"]) ?: return null
    val s = parse(this["secondary"]) ?: return null
    val ons = parse(this["onSecondary"]) ?: return null
    val t = parse(this["tertiary"]) ?: return null
    val ont = parse(this["onTertiary"]) ?: return null
    val bg = parse(this["background"]) ?: return null
    val onbg = parse(this["onBackground"]) ?: return null
    val surf = parse(this["surface"]) ?: return null
    val onsurf = parse(this["onSurface"]) ?: return null
    val sv = parse(this["surfaceVariant"]) ?: return null
    val onsv = parse(this["onSurfaceVariant"]) ?: return null

    return darkColorScheme(
        primary = p,
        onPrimary = onp,
        secondary = s,
        onSecondary = ons,
        tertiary = t,
        onTertiary = ont,
        background = bg,
        onBackground = onbg,
        surface = surf,
        onSurface = onsurf,
        surfaceVariant = sv,
        onSurfaceVariant = onsv,
    )
}
