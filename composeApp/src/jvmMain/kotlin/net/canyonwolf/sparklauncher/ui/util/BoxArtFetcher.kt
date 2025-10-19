@file:Suppress("DEPRECATION")

package net.canyonwolf.sparklauncher.ui.util

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import net.canyonwolf.sparklauncher.config.ConfigManager
import net.canyonwolf.sparklauncher.data.LauncherType
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap
import javax.imageio.ImageIO

/**
 * Helper to fetch a representative box art image for a game using IGDB API.
 * Requires IGDB/Twitch credentials (Client ID and Client Secret) configured in Settings.
 * Flow:
 * 1) Obtain an app access token from Twitch OAuth (client_credentials). Token is cached until expiry.
 * 2) POST /v4/games with a search query; request fields id,name,cover; choose the first result with a cover.
 * 3) POST /v4/covers to retrieve image_id; construct an https URL using images.igdb.com with size t_cover_big.
 */
object BoxArtFetcher {
    data class GameInfo(
        val description: String?,
        val genres: List<String>
    )

    private val imageCache = ConcurrentHashMap<String, ImageBitmap>()
    private val urlCache = ConcurrentHashMap<String, String>()

    // Per-launcher in-memory metadata caches
    private val infoCaches: java.util.EnumMap<LauncherType, ConcurrentHashMap<String, GameInfo>> =
        java.util.EnumMap(LauncherType::class.java)

    // Persistent metadata cache files per launcher under caches/
    private const val APP_FOLDER = "SparkLauncher"
    private const val CACHES_FOLDER = "caches"
    private const val IMAGES_FOLDER = "images"
    private const val METADATA_FILE_PREFIX = "igdb_metadata_"
    private const val METADATA_FILE_SUFFIX = ".json"
    @Volatile
    private var cacheLoadedFromDisk: MutableMap<LauncherType, Boolean> =
        LauncherType.entries.associateWith { false }.toMutableMap()

    // Expose metadata loading state for UI loaders
    private val _metadataLoading = kotlinx.coroutines.flow.MutableStateFlow(false)
    val metadataLoading: kotlinx.coroutines.flow.StateFlow<Boolean> = _metadataLoading

    // Expose image prefetching state to allow UI to wait until images are ready on initial launch
    private val _imagesPrefetching = kotlinx.coroutines.flow.MutableStateFlow(false)
    val imagesPrefetching: kotlinx.coroutines.flow.StateFlow<Boolean> = _imagesPrefetching

    // OAuth token cache
    @Volatile
    private var cachedToken: String? = null
    @Volatile
    private var tokenExpiryEpochMs: Long = 0L

    fun getBoxArt(gameName: String): ImageBitmap? {
        if (gameName.isBlank()) return null
        imageCache[gameName]?.let { return it }

        val newFile = getImageFilePath(gameName)
        val legacyFile = getLegacyImageFilePath(gameName)

        // Try new filename first
        try {
            if (Files.exists(newFile)) {
                val bytes = Files.readAllBytes(newFile)
                if (bytes.isNotEmpty()) {
                    val img: BufferedImage? = ImageIO.read(bytes.inputStream())
                    if (img != null) {
                        val bmp = img.toComposeImageBitmap()
                        imageCache[gameName] = bmp
                        return bmp
                    }
                }
            }
        } catch (_: Throwable) { /* ignore and continue */
        }

        // Try legacy filename and migrate
        try {
            if (!Files.exists(newFile) && Files.exists(legacyFile)) {
                val bytes = Files.readAllBytes(legacyFile)
                if (bytes.isNotEmpty()) {
                    val img: BufferedImage? = ImageIO.read(bytes.inputStream())
                    if (img != null) {
                        // Ensure directory exists for new file then migrate
                        val dir = newFile.parent
                        if (!Files.exists(dir)) Files.createDirectories(dir)
                        try {
                            Files.move(legacyFile, newFile)
                        } catch (_: Throwable) {
                            // If move fails (e.g., cross-device), attempt to write bytes
                            try {
                                Files.write(newFile, bytes)
                            } catch (_: Throwable) {
                            }
                        }
                        val bmp = img.toComposeImageBitmap()
                        imageCache[gameName] = bmp
                        return bmp
                    }
                }
            }
        } catch (_: Throwable) { /* ignore and continue to network */
        }

        val existing = imageCache[gameName]
        if (existing != null) return existing
        val url = findImageUrl(gameName) ?: return null
        val bytes = downloadImageBytes(url) ?: return null
        try {
            val dir = newFile.parent
            if (!Files.exists(dir)) Files.createDirectories(dir)
            Files.write(newFile, bytes)
        } catch (_: Throwable) {
        }
        val img: BufferedImage = ImageIO.read(bytes.inputStream()) ?: return null
        val bmp = img.toComposeImageBitmap()
        imageCache.putIfAbsent(gameName, bmp)
        return imageCache[gameName] ?: bmp
    }

    /** Prefetch and cache an image for a single game name (no-op if already cached or blank). */
    fun prefetch(gameName: String) {
        if (gameName.isBlank()) return
        getBoxArt(gameName)
    }

    /** Prefetch and cache images for a collection of game names. */
    fun prefetchAll(names: Collection<String>) {
        if (names.isEmpty()) return
        _imagesPrefetching.value = true
        try {
            // De-duplicate and run sequentially to avoid IGDB rate-limits on cold start
            names.asSequence()
                .filter { it.isNotBlank() }
                .map { it.trim() }
                .distinct()
                .forEach { prefetch(it) }
        } finally {
            _imagesPrefetching.value = false
        }
    }

    fun getGameInfo(launcher: LauncherType, gameName: String): GameInfo? {
        if (gameName.isBlank()) return null
        ensureInfoCacheLoaded(launcher)
        // Return only cached info here to avoid rate-limiting; network fetch happens only via prefetchMetadataAll (on reindex)
        return infoCaches.getOrPut(launcher) { ConcurrentHashMap() }[gameName]
    }

    /** Returns true if we have a cached record (including a negative cache) for this game. */
    fun hasInfoRecord(launcher: LauncherType, gameName: String): Boolean {
        if (gameName.isBlank()) return false
        ensureInfoCacheLoaded(launcher)
        return infoCaches.getOrPut(launcher) { ConcurrentHashMap() }.containsKey(gameName)
    }

    /**
     * On-demand fetch for a single game. If not present in cache, fetch from IGDB now,
     * update in-memory cache and persist it, then return the result. Returns null on failure.
     */
    fun fetchAndCacheGameInfo(launcher: LauncherType, gameName: String): GameInfo? {
        if (gameName.isBlank()) return null
        ensureInfoCacheLoaded(launcher)
        val cache = infoCaches.getOrPut(launcher) { ConcurrentHashMap() }
        if (cache.containsKey(gameName)) {
            return cache[gameName]
        }
        val fetched = fetchGameInfo(gameName)
        if (fetched != null) {
            cache[gameName] = fetched
            saveInfoCache(launcher)
        }
        return fetched
    }

    fun prefetchMetadataAll(items: Collection<Pair<LauncherType, String>>) {
        if (items.isEmpty()) return
        _metadataLoading.value = true
        try {
            val grouped = items.groupBy({ it.first }, { it.second })
            grouped.forEach { (launcher, names) ->
                ensureInfoCacheLoaded(launcher)
                val cache = infoCaches.getOrPut(launcher) { ConcurrentHashMap() }
                names.forEach { name ->
                    if (name.isBlank()) return@forEach
                    val info = fetchGameInfo(name)
                    if (info != null) cache[name] = info
                }
                saveInfoCache(launcher)
            }
        } finally {
            _metadataLoading.value = false
        }
    }

    /** Returns true if the in-memory cache for this launcher has any metadata loaded (from disk or network). */
    fun isMetadataCachePopulated(launcher: LauncherType): Boolean {
        ensureInfoCacheLoaded(launcher)
        val cache = infoCaches[launcher]
        return cache != null && cache.isNotEmpty()
    }

    // ---- Persistent cache helpers ----
    private fun getAppDataDir(): Path {
        val appDataEnv = System.getenv("APPDATA")
        val base =
            if (!appDataEnv.isNullOrBlank()) Paths.get(appDataEnv) else Paths.get(System.getProperty("user.home"))
        return base.resolve(APP_FOLDER)
    }

    private fun getCachesDir(): Path = getAppDataDir().resolve(CACHES_FOLDER)
    private fun getImagesDir(): Path = getCachesDir().resolve(IMAGES_FOLDER)
    private fun sha1Hex(s: String): String {
        return try {
            val md = java.security.MessageDigest.getInstance("SHA-1")
            val bytes = md.digest(s.toByteArray(StandardCharsets.UTF_8))
            bytes.joinToString(separator = "") { b -> String.format("%02x", b) }
        } catch (_: Throwable) {
            s.hashCode().toUInt().toString(16)
        }
    }

    private fun sanitizeForFilename(name: String): String {
        return try {
            val lower = name.lowercase().trim()
            val replaced = lower.replace(Regex("[^a-z0-9]+"), "_")
            val collapsed = replaced.replace(Regex("_+"), "_").trim('_')
            if (collapsed.isBlank()) "art_" + sha1Hex(lower) else collapsed
        } catch (_: Throwable) {
            "art_" + sha1Hex(name.lowercase())
        }
    }

    private fun getImageFilePath(gameName: String): Path =
        getImagesDir().resolve("${sanitizeForFilename(gameName)}_box_art.jpg")

    // Legacy hashed filename used in previous versions; kept for backward compatibility/migration
    private fun getLegacyImageFilePath(gameName: String): Path =
        getImagesDir().resolve("art_" + sha1Hex(gameName.lowercase()) + ".jpg")

    private fun getMetadataFilePath(launcher: LauncherType): Path =
        getCachesDir().resolve(METADATA_FILE_PREFIX + launcher.name.lowercase() + METADATA_FILE_SUFFIX)

    private fun ensureInfoCacheLoaded(launcher: LauncherType) {
        if (cacheLoadedFromDisk[launcher] == true) return
        synchronized(infoCaches) {
            if (cacheLoadedFromDisk[launcher] == true) return
            try {
                val file = getMetadataFilePath(launcher)
                if (Files.exists(file)) {
                    val bytes = Files.readAllBytes(file)
                    val text = String(bytes, StandardCharsets.UTF_8)
                    // Parse entries: { "entries": [ {"name":"...","description":"...","genres":["..."]}, ... ] }
                    val body = Regex(
                        "\"entries\"\\s*:\\s*\\[(.*)]",
                        RegexOption.DOT_MATCHES_ALL
                    ).find(text)?.groupValues?.getOrNull(1)
                    if (body != null) {
                        val objs =
                            Regex("\\{[^}]*} ", RegexOption.DOT_MATCHES_ALL).findAll(body).map { it.value.trim() }
                                .toList()
                        val objs2 =
                            objs.ifEmpty {
                                Regex("\\{[^}]*}", RegexOption.DOT_MATCHES_ALL).findAll(
                                    body
                                ).map { it.value }.toList()
                            }
                        val cache = infoCaches.getOrPut(launcher) { ConcurrentHashMap() }
                        objs2.forEach { obj ->
                            val name = Regex("\"name\"\\s*:\\s*\"([^\"]*)\"").find(obj)?.groupValues?.getOrNull(1)
                            if (!name.isNullOrBlank()) {
                                val descRaw =
                                    Regex("\"description\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"").find(obj)?.groupValues?.getOrNull(
                                        1
                                    )
                                val desc = if (descRaw != null) decodeText(descRaw) else null
                                val genresBody = Regex(
                                    "\"genres\"\\s*:\\s*\\[([^]]*)]",
                                    RegexOption.DOT_MATCHES_ALL
                                ).find(obj)?.groupValues?.getOrNull(1)
                                val genres = if (genresBody != null && genresBody.isNotBlank()) {
                                    Regex("\"((?:\\\\.|[^\"\\\\])*)\"").findAll(genresBody)
                                        .map { decodeText(it.groupValues[1]) }.toList()
                                } else emptyList()
                                cache[name] = GameInfo(description = desc, genres = genres)
                            }
                        }
                    }
                }
            } catch (_: Throwable) {
                // ignore bad cache files
            } finally {
                cacheLoadedFromDisk[launcher] = true
            }
        }
    }

    private fun hasConfiguredPath(launcher: LauncherType): Boolean {
        val cfg = ConfigManager.loadOrCreateDefault()
        fun any(list: List<String>, legacy: String) = list.any { it.isNotBlank() } || legacy.isNotBlank()
        return when (launcher) {
            LauncherType.STEAM -> any(cfg.steamLibraries, cfg.steamPath)
            LauncherType.EA -> any(cfg.eaLibraries, cfg.eaPath)
            LauncherType.BATTLENET -> any(cfg.battleNetLibraries, cfg.battleNetPath)
            LauncherType.UBISOFT -> any(cfg.ubisoftLibraries, cfg.ubisoftPath)
            LauncherType.CUSTOM -> any(cfg.customLibraries, "")
        }
    }

    private fun saveInfoCache(launcher: LauncherType) {
        try {
            if (!hasConfiguredPath(launcher)) return // Do not create cache file if no path configured
            val cachesDir = getCachesDir()
            if (!Files.exists(cachesDir)) Files.createDirectories(cachesDir)
            val file = getMetadataFilePath(launcher)
            val cache = infoCaches[launcher] ?: return
            fun esc(s: String) = s.replace("\\", "\\\\").replace("\"", "\\\"")
            val entriesJson = cache.entries.joinToString(",\n") { (name, info) ->
                val genresJson = info.genres.joinToString(",") { g -> "\"" + esc(g) + "\"" }
                val descJson = info.description?.let { "\"" + esc(it) + "\"" } ?: "null"
                "  {\n" +
                        "    \"name\": \"${esc(name)}\",\n" +
                        "    \"description\": $descJson,\n" +
                        "    \"genres\": [${genresJson}]\n" +
                        "  }"
            }
            val json = "{\n  \"entries\": [\n$entriesJson\n  ]\n}"
            Files.write(file, json.toByteArray(StandardCharsets.UTF_8))
        } catch (_: Throwable) {
        }
    }

    private fun fetchGameInfo(gameName: String): GameInfo? {
        return try {
            val token = ensureToken() ?: return null
            val clientId = ConfigManager.loadOrCreateDefault().igdbClientId
            if (clientId.isBlank()) return null

            val normalized = gameName.lowercase().replace(Regex("[^a-z0-9 ]"), "").trim()

            // Search game and request summary and genres (IDs)
            val gameQuery = buildString {
                append("search \"")
                append(gameName.replace("\\", " ").replace("\"", " "))
                append("\"; ")
                append("fields id,name,summary,storyline,genres,first_release_date; ")
                if (normalized == "call of duty") {
                    append("sort first_release_date desc; ")
                }
                append("limit 1;")
            }
            val gamesJson = igdbPost("games", gameQuery, token, clientId) ?: return null
            val gameObj = firstJsonObject(gamesJson) ?: return null
            val summary = extractStringField(gameObj, "summary") ?: extractStringField(gameObj, "storyline")
            val genreIds = extractNumberArrayField(gameObj, "genres")

            val genreNames = if (genreIds.isNotEmpty()) {
                val idList = genreIds.joinToString(",")
                val genresQuery = "fields id,name; where id = ($idList); limit 50;"
                val genresJson = igdbPost("genres", genresQuery, token, clientId)
                if (genresJson != null) extractAllStringFieldsFromArray(genresJson, "name") else emptyList()
            } else emptyList()

            GameInfo(description = summary, genres = genreNames)
        } catch (_: Throwable) {
            null
        }
    }

    private fun findImageUrl(gameName: String): String? {
        val cached = urlCache[gameName]
        if (cached != null) return cached
        return try {
            val token = ensureToken() ?: return null
            val clientId = ConfigManager.loadOrCreateDefault().igdbClientId
            if (clientId.isBlank()) return null

            val normalized = gameName.lowercase().replace(Regex("[^a-z0-9 ]"), "").trim()

            // 1) search game
            val gameQuery = buildString {
                append("search \"").append(gameName.replace("\\", " ").replace("\"", " ")).append("\"; ")
                append("fields id,name,cover,first_release_date; ")
                append("where cover != null; ")
                if (normalized == "call of duty") {
                    append("sort first_release_date desc; ")
                }
                append("limit 1;")
            }
            val gamesJson = igdbPost("games", gameQuery, token, clientId) ?: return null
            val gameObj = firstJsonObject(gamesJson) ?: return null
            val coverId = extractNumberField(gameObj, "cover") ?: return null

            // 2) get cover image id
            val coverQuery = "fields image_id,url; where id = $coverId; limit 1;"
            val coversJson = igdbPost("covers", coverQuery, token, clientId) ?: return null
            val coverObj = firstJsonObject(coversJson) ?: return null
            val imageId = extractStringField(coverObj, "image_id")
            val directUrl = extractStringField(coverObj, "url")

            val url = when {
                !imageId.isNullOrBlank() -> "https://images.igdb.com/igdb/image/upload/t_cover_big/$imageId.jpg"
                !directUrl.isNullOrBlank() -> // ensure https and upgrade size to cover_big if possible
                    directUrl.replace("//", "https://").replace("t_thumb", "t_cover_big")

                else -> null
            }
            if (url != null) {
                urlCache.putIfAbsent(gameName, url)
            }
            url
        } catch (_: Throwable) {
            null
        }
    }

    private fun igdbPost(endpoint: String, body: String, token: String, clientId: String): String? {
        val urlStr = "https://api.igdb.com/v4/$endpoint"
        return try {
            val conn = URL(urlStr).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Client-ID", clientId)
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("Content-Type", "text/plain")
            conn.doOutput = true
            conn.connectTimeout = 8000
            conn.readTimeout = 12000
            conn.outputStream.use { os ->
                val bytes = body.toByteArray(StandardCharsets.UTF_8)
                os.write(bytes)
            }
            val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
            stream?.use { input ->
                val bytes = input.readAllBytesCompat()
                String(bytes, StandardCharsets.UTF_8)
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun ensureToken(): String? {
        val now = System.currentTimeMillis()
        val current = cachedToken
        if (!current.isNullOrBlank() && now < tokenExpiryEpochMs - 30_000) return current
        val cfg = ConfigManager.loadOrCreateDefault()
        if (cfg.igdbClientId.isBlank() || cfg.igdbClientSecret.isBlank()) return null
        // Fetch new token
        val tokenResp = fetchTwitchAppToken(cfg.igdbClientId, cfg.igdbClientSecret) ?: return null
        cachedToken = tokenResp.first
        tokenExpiryEpochMs = now + (tokenResp.second * 1000L)
        return cachedToken
    }

    private fun fetchTwitchAppToken(clientId: String, clientSecret: String): Pair<String, Long>? {
        val urlStr = "https://id.twitch.tv/oauth2/token"
        val body = "grant_type=client_credentials&client_id=$clientId&client_secret=$clientSecret"
        return try {
            val conn = URL(urlStr).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            conn.doOutput = true
            conn.connectTimeout = 8000
            conn.readTimeout = 12000
            conn.outputStream.use { os -> os.write(body.toByteArray(StandardCharsets.UTF_8)) }
            val input = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
            input?.use { s ->
                val text = String(s.readAllBytesCompat(), StandardCharsets.UTF_8)
                val token =
                    Regex("\"access_token\"\\s*:\\s*\"([^\"]+)\"").find(text)?.groupValues?.getOrNull(1)
                val expiresIn =
                    Regex("\"expires_in\"\\s*:\\s*(\\d+)").find(text)?.groupValues?.getOrNull(1)?.toLongOrNull()
                if (!token.isNullOrBlank() && expiresIn != null) token to expiresIn else null
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun firstJsonObject(jsonArrayText: String): String? {
        val start = jsonArrayText.indexOf('{')
        if (start < 0) return null
        var level = 0
        for (i in start until jsonArrayText.length) {
            val ch = jsonArrayText[i]
            if (ch == '{') level++
            if (ch == '}') {
                level--
                if (level == 0) {
                    return jsonArrayText.substring(start, i + 1)
                }
            }
        }
        return null
    }

    private fun extractNumberField(obj: String, field: String): Long? {
        val r = Regex("\"" + field + "\"\\s*:\\s*(-?\\d+)")
        val m = r.find(obj) ?: return null
        return m.groupValues.getOrNull(1)?.toLongOrNull()
    }

    private fun extractStringField(obj: String, field: String): String? {
        val r = Regex(""""$field"\s*:\s*"((?:\\.|[^"\\])*)"""")
        val raw = r.find(obj)?.groupValues?.getOrNull(1) ?: return null
        return decodeText(raw)
    }

    private fun extractNumberArrayField(obj: String, field: String): List<Long> {
        val r = Regex(""""$field"\s*:\s*\[([^]]*)]""")
        val body = r.find(obj)?.groupValues?.getOrNull(1) ?: return emptyList()
        return body.split(',').mapNotNull { it.trim().takeIf { it.isNotEmpty() }?.toLongOrNull() }
    }

    private fun extractAllStringFieldsFromArray(jsonArrayText: String, field: String): List<String> {
        val r = Regex(""""$field"\s*:\s*"((?:\\.|[^"\\])*)"""")
        return r.findAll(jsonArrayText)
            .mapNotNull { it.groupValues.getOrNull(1) }
            .map { decodeText(it) }
            .toList()
    }

    private fun decodeText(raw: String): String {
        return htmlEntityDecode(jsonUnescape(raw))
    }

    private fun jsonUnescape(s: String): String {
        val sb = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (val n = s[i + 1]) {
                    '\\' -> {
                        sb.append('\\'); i += 2; continue
                    }

                    '"' -> {
                        sb.append('"'); i += 2; continue
                    }

                    '/' -> {
                        sb.append('/'); i += 2; continue
                    }

                    'b' -> {
                        sb.append('\b'); i += 2; continue
                    }

                    'f' -> {
                        sb.append('\u000C'); i += 2; continue
                    }

                    'n' -> {
                        sb.append('\n'); i += 2; continue
                    }

                    'r' -> {
                        sb.append('\r'); i += 2; continue
                    }

                    't' -> {
                        sb.append('\t'); i += 2; continue
                    }

                    'u' -> {
                        if (i + 6 <= s.length) {
                            val hex = s.substring(i + 2, i + 6)
                            val code = hex.toIntOrNull(16)
                            if (code != null) {
                                sb.append(code.toChar())
                                i += 6
                                continue
                            }
                        }
                        // Fallback: keep as-is if invalid
                        sb.append('u')
                        i += 2
                        continue
                    }

                    else -> { /* unknown escape: keep next char */ sb.append(n); i += 2; continue
                    }
                }
            }
            sb.append(c)
            i++
        }
        return sb.toString()
    }

    private fun htmlEntityDecode(s: String): String {
        if (!s.contains('&')) return s
        val named = mapOf(
            "amp" to "&",
            "lt" to "<",
            "gt" to ">",
            "quot" to "\"",
            "apos" to "'"
        )
        val r = Regex("&(#x[0-9A-Fa-f]+|#\\d+|[A-Za-z]+);")
        return r.replace(s) { m ->
            val ent = m.groupValues[1]
            when {
                ent.startsWith("#x") || ent.startsWith("#X") -> {
                    val hex = ent.drop(2)
                    runCatching { hex.toInt(16).toChar().toString() }.getOrElse { m.value }
                }

                ent.startsWith("#") -> {
                    val dec = ent.drop(1)
                    runCatching { dec.toInt().toChar().toString() }.getOrElse { m.value }
                }

                else -> named[ent] ?: m.value
            }
        }
    }

    private fun downloadImageBytes(urlStr: String): ByteArray? {
        return try {
            val conn = URL(urlStr).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "SparkLauncher/1.0 (+https://github.com/) IGDB client")
            conn.connectTimeout = 8000
            conn.readTimeout = 10000
            conn.instanceFollowRedirects = true
            conn.inputStream.use { input ->
                val bytes = input.readAllBytesCompat()
                if (bytes.isEmpty()) null else bytes
            }
        } catch (_: Throwable) {
            null
        }
    }

    // --- Cache cleanup helpers for removed games ---
    fun removeCachesFor(launcher: LauncherType, gameName: String) {
        if (gameName.isBlank()) return
        try {
            // Remove in-memory caches
            imageCache.remove(gameName)
            urlCache.remove(gameName)
            // Remove image files (new and legacy names)
            try {
                Files.deleteIfExists(getImageFilePath(gameName))
            } catch (_: Throwable) {
            }
            try {
                Files.deleteIfExists(getLegacyImageFilePath(gameName))
            } catch (_: Throwable) {
            }
        } catch (_: Throwable) {
        }
        try {
            // Remove metadata entry and persist
            ensureInfoCacheLoaded(launcher)
            val cache = infoCaches.getOrPut(launcher) { ConcurrentHashMap() }
            if (cache.remove(gameName) != null) {
                saveInfoCache(launcher)
            }
        } catch (_: Throwable) {
        }
    }

    fun removeAllForGames(removed: Collection<Pair<LauncherType, String>>) {
        if (removed.isEmpty()) return
        removed.forEach { (launcher, name) ->
            removeCachesFor(launcher, name)
        }
    }
}

private fun InputStream.readAllBytesCompat(): ByteArray {
    return try {
        // JDK 9+
        this.readAllBytes()
    } catch (_: NoSuchMethodError) {
        val buffer = ByteArray(8 * 1024)
        val baos = ByteArrayOutputStream()
        while (true) {
            val r = this.read(buffer)
            if (r <= 0) break
            baos.write(buffer, 0, r)
        }
        baos.toByteArray()
    }
}

private fun ByteArray.inputStream(): InputStream = java.io.ByteArrayInputStream(this)

