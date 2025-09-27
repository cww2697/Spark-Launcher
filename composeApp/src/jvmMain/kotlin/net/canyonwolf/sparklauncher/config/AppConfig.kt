package net.canyonwolf.sparklauncher.config

import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * App configuration
 */
data class AppConfig(
    val theme: String = "Default",
    val steamPath: String = "",
    val eaPath: String = "",
    val battleNetPath: String = "",
    val ubisoftPath: String = "",
    val steamLibraries: List<String> = emptyList(),
    val eaLibraries: List<String> = emptyList(),
    val battleNetLibraries: List<String> = emptyList(),
    val ubisoftLibraries: List<String> = emptyList(),
    val customLibraries: List<String> = emptyList(),
    val igdbClientId: String = "",
    val igdbClientSecret: String = "",
    val windowWidth: Int = 0,
    val windowHeight: Int = 0,
    val showUncategorizedTitles: Boolean = true,
    val showGamesInMultipleCategories: Boolean = true,
    val dateTimeFormatPattern: String = "MMM d, yyyy h:mm a",
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
                allBlank(cfg.customLibraries) &&
                cfg.igdbClientId.isBlank() &&
                cfg.igdbClientSecret.isBlank()
    }
    private const val APP_FOLDER = "SparkLauncher"
    private const val CONFIG_FILE_NAME = "config.json"

    @Volatile
    internal var appDataDirOverride: Path? = null

    /** Returns a path to %APPDATA%/SparkLauncher on Windows. */
    private fun getAppDataDir(): Path {
        appDataDirOverride?.let { return it.resolve(APP_FOLDER) }
        val appDataEnv = System.getenv("APPDATA")
        val base =
            if (!appDataEnv.isNullOrBlank()) Paths.get(appDataEnv) else Paths.get(System.getProperty("user.home"))
        return base.resolve(APP_FOLDER)
    }

    private fun getConfigFilePath(): Path = getAppDataDir().resolve(CONFIG_FILE_NAME)

    /**
     * Load configuration from config.json. If a directory or file does not exist, create them with default values.
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
            append("\n  \"customLibraries\": ").append(arr(config.customLibraries)).append(",")
            append("\n  \"igdbClientId\": \"").append(esc(config.igdbClientId)).append("\",")
            append("\n  \"igdbClientSecret\": \"").append(esc(config.igdbClientSecret)).append("\",")
            append("\n  \"windowWidth\": ").append(config.windowWidth).append(",")
            append("\n  \"windowHeight\": ").append(config.windowHeight).append(",")
            append("\n  \"showUncategorizedTitles\": ").append(config.showUncategorizedTitles).append(",")
            append("\n  \"showGamesInMultipleCategories\": ").append(config.showGamesInMultipleCategories).append(",")
            append("\n  \"dateTimeFormatPattern\": \"").append(esc(config.dateTimeFormatPattern)).append("\"\n")
            append("}")
        }
    }

    private fun fromJson(json: String): AppConfig {
        fun unesc(s: String): String {
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
            return if (body.isBlank()) emptyList() else body.split(Regex(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)"))
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
        val customLibs = extractArray("customLibraries")
        val igdbIdRaw = extractRaw("igdbClientId")
        val igdbSecretRaw = extractRaw("igdbClientSecret")
        fun extractInt(key: String): Int {
            val r = Regex("\"$key\"\\s*:\\s*(-?\\d+)")
            return r.find(json)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        }
        fun extractBool(key: String, default: Boolean): Boolean {
            val r = Regex("\"$key\"\\s*:\\s*(true|false)", setOf())
            val m = r.find(json)?.groupValues?.get(1)
            return when (m) {
                "true" -> true
                "false" -> false
                else -> default
            }
        }

        val winW = extractInt("windowWidth")
        val winH = extractInt("windowHeight")
        val steamLibraries =
            if (steamLibs.isNotEmpty()) steamLibs else listOfNotNull(steamRaw.takeIf { it.isNotBlank() })
        val eaLibraries = if (eaLibs.isNotEmpty()) eaLibs else listOfNotNull(eaRaw.takeIf { it.isNotBlank() })
        val battleNetLibraries =
            if (bnetLibs.isNotEmpty()) bnetLibs else listOfNotNull(bnetRaw.takeIf { it.isNotBlank() })
        val ubisoftLibraries = if (ubiLibs.isNotEmpty()) ubiLibs else listOfNotNull(ubiRaw.takeIf { it.isNotBlank() })
        val dtPatternRaw = extractRaw("dateTimeFormatPattern")
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
            customLibraries = customLibs.map { unesc(it) },
            igdbClientId = unesc(igdbIdRaw),
            igdbClientSecret = unesc(igdbSecretRaw),
            windowWidth = winW,
            windowHeight = winH,
            showUncategorizedTitles = extractBool("showUncategorizedTitles", true),
            showGamesInMultipleCategories = extractBool("showGamesInMultipleCategories", false),
            dateTimeFormatPattern = if (dtPatternRaw.isBlank()) "MMM d, yyyy h:mm a" else unesc(dtPatternRaw),
        )
    }
}