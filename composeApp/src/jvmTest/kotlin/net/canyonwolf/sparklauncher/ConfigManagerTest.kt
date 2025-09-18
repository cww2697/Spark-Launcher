package net.canyonwolf.sparklauncher

import net.canyonwolf.sparklauncher.config.AppConfig
import net.canyonwolf.sparklauncher.config.ConfigManager
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*

class ConfigManagerTest {
    private lateinit var tempDir: Path

    @BeforeTest
    fun setup() {
        tempDir = Files.createTempDirectory("sparklauncher-test-config-")
        // Point ConfigManager to temp dir
        ConfigManager.appDataDirOverride = tempDir
    }

    @AfterTest
    fun tearDown() {
        // Clear override and cleanup
        ConfigManager.appDataDirOverride = null
        try {
            Files.walk(tempDir)
                .sorted(Comparator.reverseOrder())
                .forEach { Files.deleteIfExists(it) }
        } catch (_: Throwable) {
        }
    }

    @Test
    fun roundTrip_save_and_load_are_equal() {
        val cfg = AppConfig(
            theme = "Light",
            steamPath = "C:/Games/Steam",
            eaPath = "D:/Games/EA",
            battleNetPath = "E:/Games/Blizzard",
            ubisoftPath = "F:/Games/Ubi",
            steamLibraries = listOf("C:/SteamLib1", "D:/SteamLib2"),
            eaLibraries = listOf("D:/EA Games"),
            battleNetLibraries = listOf("E:/Battle.net"),
            ubisoftLibraries = listOf("F:/Ubisoft"),
            igdbClientId = "abc",
            igdbClientSecret = "xyz",
            windowWidth = 1280,
            windowHeight = 720,
        )
        assertTrue(ConfigManager.save(cfg))
        val loaded = ConfigManager.loadOrCreateDefault()
        assertEquals(cfg, loaded)
    }

    @Test
    fun legacy_single_path_is_mapped_to_libraries_on_load() {
        val cfg = AppConfig(
            theme = "Default",
            steamPath = "C:/Steam",
            // leave arrays empty to simulate legacy
            steamLibraries = emptyList(),
        )
        assertTrue(ConfigManager.save(cfg))
        val loaded = ConfigManager.loadOrCreateDefault()
        assertEquals("C:/Steam", loaded.steamPath)
        assertEquals(listOf("C:/Steam"), loaded.steamLibraries)
    }

    @Test
    fun isEmpty_detects_blank_config() {
        val cfg = AppConfig()
        assertTrue(ConfigManager.isEmpty(cfg))
        val nonEmpty = cfg.copy(steamLibraries = listOf("C:/Steam"))
        assertFalse(ConfigManager.isEmpty(nonEmpty))
    }
}
