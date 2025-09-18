package net.canyonwolf.sparklauncher.config

import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Simple app configuration holder and loader.
 * Currently supports only the base theme name.
 */
data class AppConfig(
    val theme: String = "Default",
    // Legacy single-path fields (kept for backward compatibility)
    val steamPath: String = "",
    val eaPath: String = "",
    val battleNetPath: String = "",
    val ubisoftPath: String = "",
    // New multi-library support (up to 5 per platform)
    val steamLibraries: List<String> = emptyList(),
    val eaLibraries: List<String> = emptyList(),
    val battleNetLibraries: List<String> = emptyList(),
    val ubisoftLibraries: List<String> = emptyList(),
    val igdbClientId: String = "",
    val igdbClientSecret: String = "",
    val windowWidth: Int = 0,
    val windowHeight: Int = 0,
)

object ConfigManager {
    fun isEmpty(cfg: AppConfig): Boolean {
        fun allBlank(list: List<String>) = list.all { it.isBlank() }
        return cfg.steamPath.isBlank() &&
                cfg.eaPath.isBlank() &&
                cfg.battleNetPath.isBlank() &&
                cfg.ubisoftPath.isBlank() &&
                allBlank(cfg.steamLibraries) &&
                allBlank(cfg.eaLibraries) &&
                allBlank(cfg.battleNetLibraries) &&
                allBlank(cfg.ubisoftLibraries) &&
                cfg.igdbClientId.isBlank() &&
                cfg.igdbClientSecret.isBlank()
    }
    private const val APP_FOLDER = "SparkLauncher"
    private const val CONFIG_FILE_NAME = "config.json"

    /** Returns path to %APPDATA%/SparkLauncher on Windows. */
    private fun getAppDataDir(): Path {
        val appDataEnv = System.getenv("APPDATA")
        val base =
            if (!appDataEnv.isNullOrBlank()) Paths.get(appDataEnv) else Paths.get(System.getProperty("user.home"))
        return base.resolve(APP_FOLDER)
    }

    private fun getConfigFilePath(): Path = getAppDataDir().resolve(CONFIG_FILE_NAME)

    /**
     * Load configuration from config.json. If directory or file do not exist, create them with defaults.
     */
    fun loadOrCreateDefault(): AppConfig {
        val dir = getAppDataDir()
        val file = getConfigFilePath()
        try {
            if (!Files.exists(dir)) {
                Files.createDirectories(dir)
            }
            if (!Files.exists(file)) {
                val defaultJson = toJson(AppConfig())
                Files.write(file, defaultJson.toByteArray(StandardCharsets.UTF_8))
                return AppConfig()
            }
            val content = Files.readAllLines(file, StandardCharsets.UTF_8).joinToString("\n")
            return fromJson(content)
        } catch (e: IOException) {
            // On any failure, ensure we at least return defaults
            return AppConfig()
        } catch (e: SecurityException) {
            return AppConfig()
        }
    }

    fun save(config: AppConfig): Boolean {
        return try {
            val dir = getAppDataDir()
            val file = getConfigFilePath()
            if (!Files.exists(dir)) {
                Files.createDirectories(dir)
            }
            Files.write(file, toJson(config).toByteArray(StandardCharsets.UTF_8))
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun toJson(config: AppConfig): String {
        fun esc(s: String) = s.replace("\\", "\\\\").replace("\"", "\\\"")
        fun arr(values: List<String>) = values.joinToString(prefix = "[", postfix = "]") { "\"" + esc(it) + "\"" }
        return buildString {
            append("{\n")
            append("  \"theme\": \"").append(esc(config.theme)).append("\",")
            append("\n  \"steamPath\": \"").append(esc(config.steamPath)).append("\",")
            append("\n  \"eaPath\": \"").append(esc(config.eaPath)).append("\",")
            append("\n  \"battleNetPath\": \"").append(esc(config.battleNetPath)).append("\",")
            append("\n  \"ubisoftPath\": \"").append(esc(config.ubisoftPath)).append("\",")
            append("\n  \"steamLibraries\": ").append(arr(config.steamLibraries)).append(",")
            append("\n  \"eaLibraries\": ").append(arr(config.eaLibraries)).append(",")
            append("\n  \"battleNetLibraries\": ").append(arr(config.battleNetLibraries)).append(",")
            append("\n  \"ubisoftLibraries\": ").append(arr(config.ubisoftLibraries)).append(",")
            append("\n  \"igdbClientId\": \"").append(esc(config.igdbClientId)).append("\",")
            append("\n  \"igdbClientSecret\": \"").append(esc(config.igdbClientSecret)).append("\",")
            append("\n  \"windowWidth\": ").append(config.windowWidth).append(",")
            append("\n  \"windowHeight\": ").append(config.windowHeight).append("\n")
            append("}")
        }
    }

    private fun fromJson(json: String): AppConfig {
        // Naive parser tolerant to missing fields
        fun unesc(s: String): String {
            // Minimal JSON unescape for the fields we emit: handles escaped quotes and backslashes
            return s
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
        }

        fun extractRaw(key: String): String {
            val regex = Regex("\"" + key + "\"\\s*:\\s*\"([^\"]*)\"")
            return regex.find(json)?.groupValues?.get(1) ?: ""
        }
        fun extractArray(key: String): List<String> {
            val r = Regex("\"$key\"\\s*:\\s*\\[(.*?)]", setOf(RegexOption.DOT_MATCHES_ALL))
            val body = r.find(json)?.groupValues?.get(1) ?: return emptyList()
            return if (body.isBlank()) emptyList() else body.split(Regex(",(?=(?:[^\\\"]*\\\"[^\\\"]*\\\")*[^\\\"]*$)"))
                .map { it.trim().trim('"') }.map { unesc(it) }
        }

        val themeRegex = Regex("\"theme\"\\s*:\\s*\"([^\"]+)\"")
        val themeRaw = themeRegex.find(json)?.groupValues?.get(1) ?: "Default"
        val steamRaw = extractRaw("steamPath")
        val eaRaw = extractRaw("eaPath")
        val bnetRaw = extractRaw("battleNetPath")
        val ubiRaw = extractRaw("ubisoftPath")
        val steamLibs = extractArray("steamLibraries")
        val eaLibs = extractArray("eaLibraries")
        val bnetLibs = extractArray("battleNetLibraries")
        val ubiLibs = extractArray("ubisoftLibraries")
        val igdbIdRaw = extractRaw("igdbClientId")
        val igdbSecretRaw = extractRaw("igdbClientSecret")
        fun extractInt(key: String): Int {
            val r = Regex("\"$key\"\\s*:\\s*(-?\\d+)")
            return r.find(json)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        }

        val winW = extractInt("windowWidth")
        val winH = extractInt("windowHeight")
        val steamLibraries =
            if (steamLibs.isNotEmpty()) steamLibs else listOfNotNull(steamRaw.takeIf { it.isNotBlank() })
        val eaLibraries = if (eaLibs.isNotEmpty()) eaLibs else listOfNotNull(eaRaw.takeIf { it.isNotBlank() })
        val battleNetLibraries =
            if (bnetLibs.isNotEmpty()) bnetLibs else listOfNotNull(bnetRaw.takeIf { it.isNotBlank() })
        val ubisoftLibraries = if (ubiLibs.isNotEmpty()) ubiLibs else listOfNotNull(ubiRaw.takeIf { it.isNotBlank() })
        return AppConfig(
            theme = unesc(themeRaw),
            steamPath = unesc(steamRaw),
            eaPath = unesc(eaRaw),
            battleNetPath = unesc(bnetRaw),
            ubisoftPath = unesc(ubiRaw),
            steamLibraries = steamLibraries.map { unesc(it) },
            eaLibraries = eaLibraries.map { unesc(it) },
            battleNetLibraries = battleNetLibraries.map { unesc(it) },
            ubisoftLibraries = ubisoftLibraries.map { unesc(it) },
            igdbClientId = unesc(igdbIdRaw),
            igdbClientSecret = unesc(igdbSecretRaw),
            windowWidth = winW,
            windowHeight = winH,
        )
    }
}