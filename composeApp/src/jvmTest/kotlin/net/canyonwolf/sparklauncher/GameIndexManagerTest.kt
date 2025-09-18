package net.canyonwolf.sparklauncher

import net.canyonwolf.sparklauncher.config.AppConfig
import net.canyonwolf.sparklauncher.config.ConfigManager
import net.canyonwolf.sparklauncher.data.GameIndexManager
import net.canyonwolf.sparklauncher.data.LauncherType
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.test.*

class GameIndexManagerTest {
    private lateinit var tempDir: Path

    @BeforeTest
    fun setup() {
        tempDir = Files.createTempDirectory("sparklauncher-test-index-")
        // Override both managers to use temp base dir
        ConfigManager.appDataDirOverride = tempDir
        GameIndexManager.appDataDirOverride = tempDir
    }

    @AfterTest
    fun tearDown() {
        // Clear overrides and cleanup
        ConfigManager.appDataDirOverride = null
        GameIndexManager.appDataDirOverride = null
        try {
            Files.walk(tempDir)
                .sorted(Comparator.reverseOrder())
                .forEach { Files.deleteIfExists(it) }
        } catch (_: Throwable) {
        }
    }

    private fun createExe(path: Path) {
        if (!Files.exists(path.parent)) Files.createDirectories(path.parent)
        // Create a small dummy file to represent an exe
        Files.write(
            path,
            byteArrayOf(0x4D.toByte(), 0x5A.toByte()),
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING
        )
    }

    @Test
    fun rescan_discovers_games_in_known_layouts() {
        // Steam: <steam>/steamapps/common/MyGame/game.exe
        val steamBase = tempDir.resolve("SteamRoot")
        val steamGameExe = steamBase.resolve("steamapps").resolve("common").resolve("MySteamGame").resolve("game.exe")
        createExe(steamGameExe)

        // EA: <ea>/EA Games/EA Title/ea.exe
        val eaBase = tempDir.resolve("EARoot")
        val eaExe = eaBase.resolve("EA Games").resolve("EA Title").resolve("ea.exe")
        createExe(eaExe)

        // Battle.net: <bnet>/Battle.net/BNetTitle/bnet.exe
        val bnetBase = tempDir.resolve("BNetRoot")
        val bnetExe = bnetBase.resolve("Battle.net").resolve("BNetTitle").resolve("bnet.exe")
        createExe(bnetExe)

        // Ubisoft: <ubi>/Ubisoft Game Launcher/games/UbiTitle/ubi.exe
        val ubiBase = tempDir.resolve("UbiRoot")
        val ubiExe = ubiBase.resolve("Ubisoft Game Launcher").resolve("games").resolve("UbiTitle").resolve("ubi.exe")
        createExe(ubiExe)

        val cfg = AppConfig(
            steamLibraries = listOf(steamBase.toString()),
            eaLibraries = listOf(eaBase.toString()),
            battleNetLibraries = listOf(bnetBase.toString()),
            ubisoftLibraries = listOf(ubiBase.toString()),
        )

        val index = GameIndexManager.rescanAndSave(cfg)

        // Expect 4 entries, names equal to directory names
        assertEquals(4, index.entries.size)
        val byName = index.entries.associateBy { it.name }
        assertNotNull(byName["MySteamGame"]); assertEquals(LauncherType.STEAM, byName["MySteamGame"]!!.launcher)
        assertNotNull(byName["EA Title"]); assertEquals(LauncherType.EA, byName["EA Title"]!!.launcher)
        assertNotNull(byName["BNetTitle"]); assertEquals(LauncherType.BATTLENET, byName["BNetTitle"]!!.launcher)
        assertNotNull(byName["UbiTitle"]); assertEquals(LauncherType.UBISOFT, byName["UbiTitle"]!!.launcher)

        // Ensure save() wrote an index file under our temp app data dir
        val indexFile = tempDir.resolve("SparkLauncher").resolve("game_index.json")
        assertTrue(Files.exists(indexFile))
    }
}
