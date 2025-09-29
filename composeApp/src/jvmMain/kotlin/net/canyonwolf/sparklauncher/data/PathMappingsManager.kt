package net.canyonwolf.sparklauncher.data

import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Manages custom game launch path mappings stored as a JSON array in APPDATA.
 *
 * File location: %APPDATA%/SparkLauncher/PathMappings/game_path_mappings.json
 *
 * JSON format (array of objects):
 * [
 *   { "name": "Call of Duty Modern Warfare III", "path": "battlenet://game/pinta" }
 * ]
 */
object PathMappingsManager {
    private const val APP_FOLDER = "SparkLauncher"
    private const val SUB_FOLDER = "PathMappings"
    private const val FILE_NAME = "game_path_mappings.json"

    // TODO: Replace with GitHub raw URL hosting the latest mappings JSON
    private const val REMOTE_MAPPINGS_URL = "TODO_REPLACE_WITH_GITHUB_RAW_URL"

    // Default content shipped with the app. This will be written on first run if no file exists.
    private val defaultJson: String =
        """[
        { "name": "Call of Duty Modern Warfare III", "path": "battlenet://game/pinta" }
        ]""".trimIndent()

    @Volatile
    internal var appDataDirOverride: Path? = null

    private fun getAppDataDir(): Path {
        appDataDirOverride?.let { return it.resolve(APP_FOLDER) }
        val appDataEnv = System.getenv("APPDATA")
        val base =
            if (!appDataEnv.isNullOrBlank()) Paths.get(appDataEnv) else Paths.get(System.getProperty("user.home"))
        return base.resolve(APP_FOLDER)
    }

    private fun getMappingsFilePath(): Path = getAppDataDir().resolve(SUB_FOLDER).resolve(FILE_NAME)

    fun initOrUpdateOnLaunch() {
        try {
            ensureFileExists()
            // Best-effort update from remote
            updateFromRemoteIfChanged()
        } catch (_: Exception) {
            // swallow any error – mappings are optional
        }
    }

    fun hasMapping(name: String): Boolean = getPathFor(name) != null

    fun getPathFor(name: String): String? {
        return try {
            val path = getMappingsFilePath()
            if (!Files.exists(path)) return null
            val json = Files.readAllBytes(path).toString(StandardCharsets.UTF_8)
            parseJsonArray(json).firstOrNull { it.first.equals(name, ignoreCase = true) }?.second
        } catch (_: Exception) {
            null
        }
    }

    private fun ensureFileExists() {
        val dir = getMappingsFilePath().parent
        if (!Files.exists(dir)) Files.createDirectories(dir)
        val file = getMappingsFilePath()
        if (!Files.exists(file)) {
            Files.write(file, defaultJson.toByteArray(StandardCharsets.UTF_8))
        }
    }

    private fun updateFromRemoteIfChanged() {
        if (REMOTE_MAPPINGS_URL.startsWith("TODO")) return // do nothing until configured
        val connection = URL(REMOTE_MAPPINGS_URL).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 5000
        connection.readTimeout = 5000
        connection.addRequestProperty("Accept", "application/json")
        connection.instanceFollowRedirects = true
        try {
            val code = connection.responseCode
            if (code in 200..299) {
                val remote = connection.inputStream.use { it.readBytes().toString(StandardCharsets.UTF_8) }
                // rudimentary validation – must look like JSON array
                if (remote.trim().startsWith("[") && remote.trim().endsWith("]")) {
                    val localPath = getMappingsFilePath()
                    val local = if (Files.exists(localPath)) Files.readAllBytes(localPath)
                        .toString(StandardCharsets.UTF_8) else ""
                    if (normalizeJson(local) != normalizeJson(remote)) {
                        Files.write(localPath, remote.toByteArray(StandardCharsets.UTF_8))
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun normalizeJson(s: String): String = s.filterNot { it.isWhitespace() }

    // Simple, minimal JSON array parser for our specific shape
    // Returns list of (name, path)
    private fun parseJsonArray(json: String): List<Pair<String, String>> {
        val trimmed = json.trim()
        if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) return emptyList()
        val items = mutableListOf<Pair<String, String>>()
        // Very small, tolerant parsing: split top-level objects by '},{'
        val inner = trimmed.substring(1, trimmed.length - 1).trim()
        if (inner.isEmpty()) return emptyList()
        // Attempt to split objects safely
        val parts = mutableListOf<String>()
        var brace = 0
        var start = 0
        for (i in inner.indices) {
            val c = inner[i]
            if (c == '{') brace++
            if (c == '}') brace--
            if (brace == 0 && i < inner.lastIndex && inner[i + 1] == ',') {
                parts.add(inner.substring(start, i + 1))
                start = i + 2
            }
        }
        parts.add(inner.substring(start))
        parts.forEach { obj ->
            val name = extractStringField(obj, "name")
            val path = extractStringField(obj, "path")
            if (!name.isNullOrBlank() && !path.isNullOrBlank()) {
                items.add(name to path)
            }
        }
        return items
    }

    private fun extractStringField(obj: String, key: String): String? {
        val regex = Regex("\\\"" + Regex.escape(key) + "\\\"\\s*:\\s*\\\"(.*?)\\\"")
        val m = regex.find(obj)
        return m?.groupValues?.getOrNull(1)
    }
}
