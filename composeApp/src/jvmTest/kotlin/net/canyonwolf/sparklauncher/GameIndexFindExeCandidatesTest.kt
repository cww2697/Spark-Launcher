package net.canyonwolf.sparklauncher

import net.canyonwolf.sparklauncher.data.GameIndexManager
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.test.*

class GameIndexFindExeCandidatesTest {
    private lateinit var tempDir: Path

    @BeforeTest
    fun setup() {
        tempDir = Files.createTempDirectory("sparklauncher-test-candidates-")
    }

    @AfterTest
    fun tearDown() {
        try {
            Files.walk(tempDir)
                .sorted(Comparator.reverseOrder())
                .forEach { Files.deleteIfExists(it) }
        } catch (_: Throwable) {
        }
    }

    private fun createExe(path: Path) {
        if (!Files.exists(path.parent)) Files.createDirectories(path.parent)
        Files.write(path, byteArrayOf(0x4D, 0x5A), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
    }

    @Test
    fun returns_empty_list_for_missing_directory() {
        val bogus = tempDir.resolve("does-not-exist")
        val list = GameIndexManager.findExeCandidates(bogus.toString())
        assertTrue(list.isEmpty(), "Expected empty list for missing directory")
    }

    @Test
    fun finds_executables_in_root_and_one_level_deep() {
        val rootExe = tempDir.resolve("game.exe")
        val deepExe = tempDir.resolve("bin").resolve("launcher.exe")
        val otherFile = tempDir.resolve("readme.txt")
        createExe(rootExe)
        createExe(deepExe)
        Files.write(otherFile, byteArrayOf(1, 2, 3), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)

        val list = GameIndexManager.findExeCandidates(tempDir.toString())
        // Expect both exe files, but not the txt
        assertEquals(2, list.size)
        assertTrue(list.any { it.endsWith("game.exe") })
        assertTrue(list.any { it.endsWith("launcher.exe") })
    }
}
