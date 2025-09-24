package net.canyonwolf.sparklauncher.data

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap

/**
 * Persists user-selected executable per game directory under %APPDATA%/SparkLauncher/exe_selections.json
 * JSON structure: { "<dirPath>": "<exePath>", ... }
 */
object ExeSelectionStore {
    private const val APP_FOLDER = "SparkLauncher"
    private const val FILE_NAME = "exe_selections.json"

    // Test hook to override base directory in unit tests (reuse GameIndexManager override if set)
    private val appDataDirOverride: Path?
        get() = GameIndexManager.appDataDirOverride

    private fun getAppDataDir(): Path {
        appDataDirOverride?.let { return it.resolve(APP_FOLDER) }
        val appDataEnv = System.getenv("APPDATA")
        val base =
            if (!appDataEnv.isNullOrBlank()) Paths.get(appDataEnv) else Paths.get(System.getProperty("user.home"))
        return base.resolve(APP_FOLDER)
    }

    private fun getFile(): Path = getAppDataDir().resolve(FILE_NAME)

    private val lock = Any()

    @Volatile
    private var cache: MutableMap<String, String>? = null

    fun get(dirPath: String): String? {
        if (dirPath.isBlank()) return null
        val map = ensureLoaded()
        return map[dirPath]
    }

    fun put(dirPath: String, exePath: String) {
        synchronized(lock) {
            val map = ensureLoadedInternal()
            map[dirPath] = exePath
            writeUnsafe(map)
        }
    }

    fun getAll(): Map<String, String> = HashMap(ensureLoaded())

    private fun ensureLoaded(): MutableMap<String, String> {
        synchronized(lock) {
            return ensureLoadedInternal()
        }
    }

    private fun ensureLoadedInternal(): MutableMap<String, String> {
        cache?.let { return it }
        val dir = getAppDataDir()
        val file = getFile()
        try {
            if (!Files.exists(dir)) Files.createDirectories(dir)
            if (!Files.exists(file)) {
                Files.write(file, "{}".toByteArray(StandardCharsets.UTF_8))
                cache = ConcurrentHashMap()
                return cache as MutableMap<String, String>
            }
            val content = Files.readAllLines(file, StandardCharsets.UTF_8).joinToString("\n")
            val parsed = parseJson(content)
            cache = ConcurrentHashMap(parsed)
            return cache as MutableMap<String, String>
        } catch (_: Throwable) {
            cache = ConcurrentHashMap()
            return cache as MutableMap<String, String>
        }
    }

    private fun writeUnsafe(map: MutableMap<String, String>) {
        try {
            val dir = getAppDataDir()
            val file = getFile()
            if (!Files.exists(dir)) Files.createDirectories(dir)
            val json = toJson(map)
            Files.write(file, json.toByteArray(StandardCharsets.UTF_8))
        } catch (_: Throwable) {
            // ignore write errors
        }
    }

    private fun toJson(map: Map<String, String>): String {
        fun esc(s: String) = s.replace("\\", "\\\\").replace("\"", "\\\"")
        val sb = StringBuilder()
        sb.append("{\n")
        val iter = map.entries.iterator()
        while (iter.hasNext()) {
            val (k, v) = iter.next()
            sb.append("  \"").append(esc(k)).append("\": \"").append(esc(v)).append("\"")
            if (iter.hasNext()) sb.append(",")
            sb.append("\n")
        }
        sb.append("}")
        return sb.toString()
    }

    private fun parseJson(json: String): MutableMap<String, String> {
        val result = mutableMapOf<String, String>()
        val entryRegex = Regex("\"([^\"]+)\"\\s*:\\s*\"([^\"]*)\"")
        for (m in entryRegex.findAll(json)) {
            val k = unesc(m.groupValues[1])
            val v = unesc(m.groupValues[2])
            result[k] = v
        }
        return result
    }

    private fun unesc(s: String): String = s.replace("\\\"", "\"").replace("\\\\", "\\")
}
