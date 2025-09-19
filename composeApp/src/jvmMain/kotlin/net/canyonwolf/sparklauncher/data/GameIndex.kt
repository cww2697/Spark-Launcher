package net.canyonwolf.sparklauncher.data

import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Instant
import kotlin.io.path.isDirectory
import kotlin.io.path.name

enum class LauncherType { STEAM, EA, BATTLENET, UBISOFT }

data class GameEntry(
    val launcher: LauncherType,
    val name: String,
    val exePath: String,
    val dirPath: String,
)

data class GameIndex(
    val lastUpdatedEpochSeconds: Long = Instant.now().epochSecond,
    val entries: List<GameEntry> = emptyList()
)

object GameIndexManager {
    private const val APP_FOLDER = "SparkLauncher"
    private const val INDEX_FILE_NAME = "game_index.json"

    // Test hook to override base directory in unit tests
    @Volatile
    internal var appDataDirOverride: Path? = null

    private fun getAppDataDir(): Path {
        appDataDirOverride?.let { return it.resolve(APP_FOLDER) }
        val appDataEnv = System.getenv("APPDATA")
        val base =
            if (!appDataEnv.isNullOrBlank()) Paths.get(appDataEnv) else Paths.get(System.getProperty("user.home"))
        return base.resolve(APP_FOLDER)
    }

    private fun getIndexFilePath(): Path = getAppDataDir().resolve(INDEX_FILE_NAME)

    fun load(): GameIndex {
        return try {
            val file = getIndexFilePath()
            if (!Files.exists(file)) return GameIndex()
            val content = Files.readAllBytes(file).toString(StandardCharsets.UTF_8)
            fromJson(content)
        } catch (_: Exception) {
            GameIndex()
        }
    }

    fun save(index: GameIndex) {
        try {
            val dir = getAppDataDir()
            if (!Files.exists(dir)) Files.createDirectories(dir)
            val file = getIndexFilePath()
            Files.write(file, toJson(index).toByteArray(StandardCharsets.UTF_8))
        } catch (_: Exception) {
            // ignore
        }
    }

    /**
     * Scan based on provided config values. Returns the current index (existing if unchanged, rescanned if changed).
     */
    fun loadOrScan(config: net.canyonwolf.sparklauncher.config.AppConfig): GameIndex {
        val existing = load()
        val scanned = scan(config)
        val existingSet = existing.entries.map { it.exePath }.toSet()
        val scannedSet = scanned.entries.map { it.exePath }.toSet()
        return if (existingSet == scannedSet) {
            // Keep existing (no rewrite) to preserve lastUpdated
            existing
        } else {
            val updated = scanned.copy(lastUpdatedEpochSeconds = Instant.now().epochSecond)
            save(updated)
            updated
        }
    }

    /**
     * Force a complete rescan and overwrite the index regardless of changes.
     */
    fun rescanAndSave(config: net.canyonwolf.sparklauncher.config.AppConfig): GameIndex {
        val scanned = scan(config)
        val updated = scanned.copy(lastUpdatedEpochSeconds = Instant.now().epochSecond)
        save(updated)
        return updated
    }

    private fun scan(config: net.canyonwolf.sparklauncher.config.AppConfig): GameIndex {
        val result = mutableListOf<GameEntry>()

        fun addFromBase(base: Path?, launcher: LauncherType, restrictToSteamCommon: Boolean = false) {
            if (base == null) return
            if (!Files.exists(base)) return
            val scanRoot = if (restrictToSteamCommon) base.resolve("steamapps").resolve("common") else base
            if (!Files.exists(scanRoot)) return
            try {
                Files.list(scanRoot).use { stream ->
                    stream.filter { it.isDirectory() }.forEach { gameDir ->
                        // Inside each gameDir, find a .exe (first match)
                        val exe = findFirstExe(gameDir)
                        if (exe != null) {
                            val name = gameDir.name
                            // Special-case: For Battle.net title "Call of Duty Modern Warfare III",
                            // use a custom protocol path to ensure proper launching via Battle.net.
                            val exePathStr =
                                if (launcher == LauncherType.BATTLENET && name == "Call of Duty Modern Warfare III") {
                                    "battlenet://game/pinta"
                                } else {
                                    exe.toString()
                                }
                            result.add(
                                GameEntry(
                                    launcher = launcher,
                                    name = name,
                                    exePath = exePathStr,
                                    dirPath = gameDir.toString(),
                                )
                            )
                        }
                    }
                }
            } catch (_: IOException) {
                // ignore this launcher
            }
        }

        // Build lists from new fields with legacy fallback
        val steamBases =
            (if (config.steamLibraries.isNotEmpty()) config.steamLibraries else listOfNotNull(config.steamPath.takeIf { it.isNotBlank() }))
                .mapNotNull { it.takeIf { it.isNotBlank() }?.let { Paths.get(it) } }
        val eaBases =
            (if (config.eaLibraries.isNotEmpty()) config.eaLibraries else listOfNotNull(config.eaPath.takeIf { it.isNotBlank() }))
                .mapNotNull { it.takeIf { it.isNotBlank() }?.let { Paths.get(it) } }
        val bnetBases =
            (if (config.battleNetLibraries.isNotEmpty()) config.battleNetLibraries else listOfNotNull(config.battleNetPath.takeIf { it.isNotBlank() }))
                .mapNotNull { it.takeIf { it.isNotBlank() }?.let { Paths.get(it) } }
        val ubiBases =
            (if (config.ubisoftLibraries.isNotEmpty()) config.ubisoftLibraries else listOfNotNull(config.ubisoftPath.takeIf { it.isNotBlank() }))
                .mapNotNull { it.takeIf { it.isNotBlank() }?.let { Paths.get(it) } }

        steamBases.forEach { base ->
            addFromBase(base, LauncherType.STEAM, restrictToSteamCommon = true)
        }

        // EA: common subfolders are "EA Games" or legacy "Origin Games" under the selected base
        eaBases.forEach { base ->
            val eaPath = run {
                val eaGames = base.resolve("EA Games")
                val originGames = base.resolve("Origin Games")
                when {
                    Files.exists(eaGames) -> eaGames
                    Files.exists(originGames) -> originGames
                    else -> base
                }
            }
            addFromBase(eaPath, LauncherType.EA)
        }

        // Battle.net games are stored under a "Battle.net" subfolder: <base>/Battle.net/{game}
        // If the user points to the parent folder, scan the Battle.net subfolder if it exists; otherwise scan the base.
        bnetBases.forEach { base ->
            val bnetPath = run {
                val sub = base.resolve("Battle.net")
                if (Files.exists(sub)) sub else base
            }
            addFromBase(bnetPath, LauncherType.BATTLENET)
        }

        // Ubisoft: common layout is "Ubisoft Game Launcher/games" under the selected base
        ubiBases.forEach { base ->
            val ubiPath = run {
                val uplayGames = base.resolve("Ubisoft Game Launcher").resolve("games")
                val games = base.resolve("games")
                when {
                    Files.exists(uplayGames) -> uplayGames
                    Files.exists(games) -> games
                    else -> base
                }
            }
            addFromBase(ubiPath, LauncherType.UBISOFT)
        }

        // Sort entries by launcher then name for consistency
        val sorted = result.sortedWith(compareBy<GameEntry> { it.launcher.name }.thenBy { it.name.lowercase() })
        return GameIndex(entries = sorted)
    }

    private fun findFirstExe(dir: Path): Path? {
        // Prefer exe file matching directory name; otherwise first .exe found in depth 1
        var first: Path? = null
        try {
            Files.list(dir).use { s ->
                s.filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".exe", ignoreCase = true) }
                    .forEach { p ->
                        if (first == null) first = p
                    }
            }
            if (first != null) return first
            // Also check one level deeper (common for some launchers)
            Files.list(dir).use { s ->
                s.filter { it.isDirectory() }.forEach { sub ->
                    val found = findFirstExe(sub)
                    if (found != null && first == null) first = found
                }
            }
        } catch (_: IOException) {
        }
        return first
    }

    private fun esc(s: String) = s.replace("\\", "\\\\").replace("\"", "\\\"")

    private fun toJson(index: GameIndex): String {
        val entriesJson = index.entries.joinToString(separator = ",\n") { e ->
            "  {\n" +
                    "    \"launcher\": \"${e.launcher.name}\",\n" +
                    "    \"name\": \"${esc(e.name)}\",\n" +
                    "    \"exePath\": \"${esc(e.exePath)}\",\n" +
                    "    \"dirPath\": \"${esc(e.dirPath)}\"\n" +
                    "  }"
        }
        return "{" +
                "\n  \"lastUpdatedEpochSeconds\": ${index.lastUpdatedEpochSeconds},\n" +
                "  \"entries\": [\n$entriesJson\n  ]\n" +
                "}"
    }

    private fun fromJson(json: String): GameIndex {
        fun extractLong(key: String, default: Long): Long {
            val r = Regex("\"$key\"\\s*:\\s*(\\d+)")
            val v = r.find(json)?.groupValues?.get(1)
            return v?.toLongOrNull() ?: default
        }

        fun extractArrayObjects(key: String): List<String> {
            val r = Regex("\"$key\"\\s*:\\s*\\[(.*)]", RegexOption.DOT_MATCHES_ALL)
            val body = r.find(json)?.groupValues?.get(1) ?: return emptyList()
            // Split by objects roughly
            val objs = Regex("\\{[^}]*} ", RegexOption.DOT_MATCHES_ALL).findAll(body)
            return if (objs.any()) objs.map { it.value.trim() }.toList() else Regex(
                "\\{[^}]*}",
                RegexOption.DOT_MATCHES_ALL
            ).findAll(body).map { it.value }.toList()
        }

        fun extract(field: String, src: String): String {
            val r = Regex("\"$field\"\\s*:\\s*\"([^\"]*)\"")
            return r.find(src)?.groupValues?.get(1) ?: ""
        }

        val last = extractLong("lastUpdatedEpochSeconds", Instant.now().epochSecond)
        val entries = extractArrayObjects("entries").map { obj ->
            GameEntry(
                launcher = runCatching {
                    LauncherType.valueOf(
                        extract(
                            "launcher",
                            obj
                        )
                    )
                }.getOrDefault(LauncherType.STEAM),
                name = extract("name", obj),
                exePath = extract("exePath", obj),
                dirPath = extract("dirPath", obj),
            )
        }
        return GameIndex(lastUpdatedEpochSeconds = last, entries = entries)
    }
}
