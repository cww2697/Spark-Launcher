package net.canyonwolf.sparklauncher.data

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap

/**
 * Persists simple per-game play stats under %APPDATA%/SparkLauncher/userdata/gamestats.json
 * JSON structure:
 * {
 *   "Game Name": { "plays": 3, "lastplayed": "2025-09-17T21:00:00" },
 *   ...
 * }
 */
object UserDataStore {
    private const val APP_FOLDER = "SparkLauncher"
    private const val USERDATA_FOLDER = "userdata"
    private const val FILE_NAME = "gamestats.json"

    data class PlayStat(var plays: Int = 0, var lastplayed: String = "")

    private fun getAppDataDir(): Path {
        val appDataEnv = System.getenv("APPDATA")
        val base =
            if (!appDataEnv.isNullOrBlank()) Paths.get(appDataEnv) else Paths.get(System.getProperty("user.home"))
        return base.resolve(APP_FOLDER).resolve(USERDATA_FOLDER)
    }

    private fun getStatsFile(): Path = getAppDataDir().resolve(FILE_NAME)

    private val lock = Any()
    @Volatile
    private var cache: MutableMap<String, PlayStat>? = null

    fun get(gameName: String): PlayStat? {
        if (gameName.isBlank()) return null
        val map = ensureLoaded()
        return map[gameName]
    }

    fun getAll(): Map<String, PlayStat> = HashMap(ensureLoaded())

    fun recordPlay(gameName: String, playedAt: LocalDateTime = LocalDateTime.now()): PlayStat {
        val whenStr = playedAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        val updated: PlayStat
        synchronized(lock) {
            val map = ensureLoadedInternal()
            val stat = map.getOrPut(gameName) { PlayStat(plays = 0, lastplayed = whenStr) }
            stat.plays += 1
            stat.lastplayed = whenStr
            writeUnsafe(map)
            updated = PlayStat(stat.plays, stat.lastplayed)
        }
        return updated
    }

    // --- internals ---

    private fun ensureLoaded(): MutableMap<String, PlayStat> {
        synchronized(lock) {
            return ensureLoadedInternal()
        }
    }

    private fun ensureLoadedInternal(): MutableMap<String, PlayStat> {
        cache?.let { return it }
        val dir = getAppDataDir()
        val file = getStatsFile()
        try {
            if (!Files.exists(dir)) Files.createDirectories(dir)
            if (!Files.exists(file)) {
                // initialize empty JSON file
                Files.write(file, "{}".toByteArray(StandardCharsets.UTF_8))
                cache = ConcurrentHashMap()
                return cache as MutableMap<String, PlayStat>
            }
            val content = Files.readAllLines(file, StandardCharsets.UTF_8).joinToString("\n")
            val parsed = parseJson(content)
            cache = ConcurrentHashMap(parsed)
            return cache as MutableMap<String, PlayStat>
        } catch (_: Throwable) {
            cache = ConcurrentHashMap()
            return cache as MutableMap<String, PlayStat>
        }
    }

    private fun writeUnsafe(map: MutableMap<String, PlayStat>) {
        try {
            val dir = getAppDataDir()
            val file = getStatsFile()
            if (!Files.exists(dir)) Files.createDirectories(dir)
            val json = toJson(map)
            Files.write(file, json.toByteArray(StandardCharsets.UTF_8))
        } catch (_: Throwable) {
            // ignore write errors
        }
    }

    private fun toJson(map: Map<String, PlayStat>): String {
        fun esc(s: String) = s.replace("\\", "\\\\").replace("\"", "\\\"")
        val sb = StringBuilder()
        sb.append("{\n")
        val iter = map.entries.iterator()
        while (iter.hasNext()) {
            val (name, stat) = iter.next()
            sb.append("  \"").append(esc(name)).append("\": { \"plays\": ")
                .append(stat.plays)
                .append(", \"lastplayed\": \"").append(esc(stat.lastplayed)).append("\" }")
            if (iter.hasNext()) sb.append(",")
            sb.append("\n")
        }
        sb.append("}")
        return sb.toString()
    }

    private fun parseJson(json: String): MutableMap<String, PlayStat> {
        val result = mutableMapOf<String, PlayStat>()
        // Very naive top-level object parser: matches "Key": { ... }
        val entryRegex = Regex("\"([^\"]+)\"\\s*:\\s*\\{([^}]*)\\}")
        val playsRegex = Regex("\"plays\"\\s*:\\s*(-?\\d+)")
        val lastRegex = Regex("\"lastplayed\"\\s*:\\s*\"([^\"]*)\"")
        for (m in entryRegex.findAll(json)) {
            val name = unesc(m.groupValues[1])
            val body = m.groupValues[2]
            val plays = playsRegex.find(body)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val last = lastRegex.find(body)?.groupValues?.get(1) ?: ""
            result[name] = PlayStat(plays, unesc(last))
        }
        return result
    }

    private fun unesc(s: String): String {
        return s.replace("\\\"", "\"").replace("\\\\", "\\")
    }
}
