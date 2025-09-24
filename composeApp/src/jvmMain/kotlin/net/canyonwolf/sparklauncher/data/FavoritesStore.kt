package net.canyonwolf.sparklauncher.data

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap

/**
 * Persists user favorites under %APPDATA%/SparkLauncher/favorites.json
 * JSON structure: [ "<dirPath>", ... ]
 */
object FavoritesStore {
    private const val APP_FOLDER = "SparkLauncher"
    private const val FILE_NAME = "favorites.json"

    // Reuse appData override if tests set it
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
    private var cache: MutableSet<String>? = null

    fun isFavorite(dirPath: String): Boolean {
        if (dirPath.isBlank()) return false
        return ensureLoaded().contains(dirPath)
    }

    fun getAll(): Set<String> = HashSet(ensureLoaded())

    fun setFavorite(dirPath: String, favored: Boolean) {
        if (dirPath.isBlank()) return
        synchronized(lock) {
            val set = ensureLoadedInternal()
            if (favored) set.add(dirPath) else set.remove(dirPath)
            writeUnsafe(set)
        }
    }

    fun toggle(dirPath: String): Boolean {
        if (dirPath.isBlank()) return false
        synchronized(lock) {
            val set = ensureLoadedInternal()
            val nowFav = if (set.contains(dirPath)) {
                set.remove(dirPath)
                false
            } else {
                set.add(dirPath)
                true
            }
            writeUnsafe(set)
            return nowFav
        }
    }

    // --- internals ---

    private fun ensureLoaded(): MutableSet<String> {
        synchronized(lock) { return ensureLoadedInternal() }
    }

    private fun ensureLoadedInternal(): MutableSet<String> {
        cache?.let { return it }
        val dir = getAppDataDir()
        val file = getFile()
        try {
            if (!Files.exists(dir)) Files.createDirectories(dir)
            if (!Files.exists(file)) {
                Files.write(file, "[]".toByteArray(StandardCharsets.UTF_8))
                cache = ConcurrentHashMap.newKeySet()
                return cache as MutableSet<String>
            }
            val content = Files.readAllLines(file, StandardCharsets.UTF_8).joinToString("\n")
            val parsed = parseJsonArray(content)
            cache = ConcurrentHashMap.newKeySet<String>().apply { addAll(parsed) }
            return cache as MutableSet<String>
        } catch (_: Throwable) {
            cache = ConcurrentHashMap.newKeySet()
            return cache as MutableSet<String>
        }
    }

    private fun writeUnsafe(set: MutableSet<String>) {
        try {
            val dir = getAppDataDir()
            val file = getFile()
            if (!Files.exists(dir)) Files.createDirectories(dir)
            val json = toJsonArray(set)
            Files.write(file, json.toByteArray(StandardCharsets.UTF_8))
        } catch (_: Throwable) {
            // ignore
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
