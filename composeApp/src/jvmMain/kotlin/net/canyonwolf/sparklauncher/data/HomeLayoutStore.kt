package net.canyonwolf.sparklauncher.data

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Persists Home page section order under %APPDATA%/SparkLauncher/home_layout.json
 * JSON structure: [ "Genre A", "Genre B", ... ]
 */
object HomeLayoutStore {
    private const val APP_FOLDER = "SparkLauncher"
    private const val FILE_NAME = "home_layout.json"

    // Reuse appData override if tests set it via GameIndexManager
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
    private var cache: MutableList<String>? = null

    fun getOrder(): List<String> {
        synchronized(lock) { return ensureLoadedInternal().toList() }
    }

    fun setOrder(order: List<String>) {
        synchronized(lock) {
            val normalized = order.distinct() // ensure uniqueness
            val list = ensureLoadedInternal()
            list.clear()
            list.addAll(normalized)
            writeUnsafe(list)
        }
    }

    private fun ensureLoadedInternal(): MutableList<String> {
        cache?.let { return it }
        val dir = getAppDataDir()
        val file = getFile()
        try {
            if (!Files.exists(dir)) Files.createDirectories(dir)
            if (!Files.exists(file)) {
                Files.write(file, "[]".toByteArray(StandardCharsets.UTF_8))
                cache = CopyOnWriteArrayList()
                return cache as MutableList<String>
            }
            val content = Files.readAllLines(file, StandardCharsets.UTF_8).joinToString("\n")
            val parsed = parseJsonArray(content)
            cache = CopyOnWriteArrayList(parsed)
            return cache as MutableList<String>
        } catch (_: Throwable) {
            cache = CopyOnWriteArrayList()
            return cache as MutableList<String>
        }
    }

    private fun writeUnsafe(list: MutableList<String>) {
        try {
            val dir = getAppDataDir()
            val file = getFile()
            if (!Files.exists(dir)) Files.createDirectories(dir)
            val json = toJsonArray(list)
            Files.write(file, json.toByteArray(StandardCharsets.UTF_8))
        } catch (_: Throwable) { /* ignore */
        }
    }

    private fun esc(s: String) = s.replace("\\", "\\\\").replace("\"", "\\\"")

    private fun toJsonArray(items: Collection<String>): String {
        val sb = StringBuilder()
        sb.append("[\n")
        val iter = items.iterator()
        while (iter.hasNext()) {
            val v = iter.next()
            sb.append("  \"").append(esc(v)).append("\"")
            if (iter.hasNext()) sb.append(",")
            sb.append("\n")
        }
        sb.append("]")
        return sb.toString()
    }

    private fun parseJsonArray(json: String): List<String> {
        val result = mutableListOf<String>()
        val strRegex = Regex("\"([^\\\"]*(?:\\\\.[^\\\"]*)*)\"")
        for (m in strRegex.findAll(json)) {
            result.add(m.groupValues[1].replace("\\\"", "\"").replace("\\\\", "\\"))
        }
        return result
    }
}
