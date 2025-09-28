package net.canyonwolf.sparklauncher

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.canyonwolf.sparklauncher.data.ExeSelectionStore
import net.canyonwolf.sparklauncher.data.GameIndex
import net.canyonwolf.sparklauncher.data.GameIndexManager
import net.canyonwolf.sparklauncher.ui.components.TopMenuBar
import net.canyonwolf.sparklauncher.ui.screens.HomeScreen
import net.canyonwolf.sparklauncher.ui.screens.LibraryScreen
import net.canyonwolf.sparklauncher.ui.util.BoxArtFetcher
import net.canyonwolf.sparklauncher.ui.windows.ExeChoiceItem
import net.canyonwolf.sparklauncher.ui.windows.ExeSelectionWindow
import net.canyonwolf.sparklauncher.ui.windows.SettingsWindow

private enum class Screen { Home, Library }

@Composable
fun App() {
    // Load configuration once on app startup
    val initialConfig = remember { net.canyonwolf.sparklauncher.config.ConfigManager.loadOrCreateDefault() }
    var themeName by remember { mutableStateOf(initialConfig.theme) }
    val appConfig by remember { mutableStateOf(initialConfig) }
    // Initialize community themes (ensures themes folder/template and loads available themes)
    LaunchedEffect(Unit) {
        net.canyonwolf.sparklauncher.ui.theme.ThemeManager.init()
    }
    val isConfigEmpty by remember(appConfig) {
        mutableStateOf(
            net.canyonwolf.sparklauncher.config.ConfigManager.isEmpty(
                appConfig
            )
        )
    }

    var gameIndex by remember { mutableStateOf(GameIndex()) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(appConfig) {
        scope.launch(Dispatchers.IO) {
            val idx = GameIndexManager.loadOrScan(appConfig)
            withContext(Dispatchers.Main) {
                gameIndex = idx
            }
            BoxArtFetcher.prefetchAll(idx.entries.map { it.toIgdbQueryName() })
            val launchersPresent = idx.entries.map { it.launcher }.toSet()
            val needsBuild = launchersPresent.any { lt ->
                !BoxArtFetcher.isMetadataCachePopulated(lt)
            }
            if (needsBuild) {
                BoxArtFetcher.prefetchMetadataAll(
                    idx.entries.map { it.launcher to it.toIgdbQueryName() }
                )
            }
        }
    }

    net.canyonwolf.sparklauncher.ui.theme.AppTheme(themeName = themeName) {
        var isExeWindowOpen by remember { mutableStateOf(false) }
        var exeChoiceItems by remember { mutableStateOf(listOf<ExeChoiceItem>()) }

        fun refreshExeChoices(idx: GameIndex) {
            fun normalizeNameForMatch(s: String): String = s.lowercase().filter { it.isLetterOrDigit() }

            val autoSelections = mutableListOf<Pair<String, String>>() // dirPath to exePath
            val items = idx.entries.mapNotNull { e ->
                // Battle.net special-case: For MWIII always use battlenet protocol; do not auto-select or show popup
                if (e.launcher == net.canyonwolf.sparklauncher.data.LauncherType.BATTLENET &&
                    e.name.equals("Call of Duty Modern Warfare III", ignoreCase = true)
                ) {
                    return@mapNotNull null
                }

                val selected = ExeSelectionStore.get(e.dirPath)
                val candidates = GameIndexManager.findExeCandidates(e.dirPath)
                if (selected.isNullOrBlank()) {
                    // Battle.net special-case: MW2 Campaign Remastered should always use the launcher exe
                    val mw2Special: String? = if (
                        e.launcher == net.canyonwolf.sparklauncher.data.LauncherType.BATTLENET &&
                        e.name.equals("Call of Duty Modern Warfare 2 Campaign Remastered", ignoreCase = true)
                    ) {
                        candidates.firstOrNull {
                            it.endsWith(
                                "MW2 Campaign Remastered Launcher.exe",
                                ignoreCase = true
                            )
                        }
                    } else null

                    val bestByName: String? = if (mw2Special == null) {
                        val target = normalizeNameForMatch(e.name)
                        candidates.firstOrNull { cand ->
                            val file = java.nio.file.Paths.get(cand).fileName.toString()
                            val base = if (file.endsWith(".exe", ignoreCase = true)) file.substring(
                                0,
                                file.length - 4
                            ) else file
                            normalizeNameForMatch(base) == target
                        }
                    } else null

                    val chosen = mw2Special ?: bestByName
                    if (!chosen.isNullOrBlank()) {
                        ExeSelectionStore.put(e.dirPath, chosen)
                        autoSelections.add(e.dirPath to chosen)
                        null
                    } else if (candidates.size > 1) {
                        ExeChoiceItem(gameName = e.name, dirPath = e.dirPath, candidates = candidates)
                    } else null
                } else null
            }

            // Apply auto-selections to in-memory index and persist, so popup won’t show next time
            if (autoSelections.isNotEmpty()) {
                val updatedEntries = gameIndex.entries.map { ge ->
                    autoSelections.firstOrNull { it.first == ge.dirPath }?.let { (_, exe) -> ge.copy(exePath = exe) }
                        ?: ge
                }
                val updated = gameIndex.copy(entries = updatedEntries)
                gameIndex = updated
                GameIndexManager.save(updated)
            }

            exeChoiceItems = items
            isExeWindowOpen = items.isNotEmpty()
        }
        var currentScreen by remember { mutableStateOf(Screen.Home) }
        var isSettingsOpen by remember { mutableStateOf(false) }
        var settingsWindowRef by remember { mutableStateOf<ComposeWindow?>(null) }

        fun openOrFocusSettings() {
            if (isSettingsOpen) {
                settingsWindowRef?.let { w ->
                    try {
                        w.isVisible = true
                    } catch (_: Exception) {
                    }
                    try {
                        w.extendedState = java.awt.Frame.NORMAL
                    } catch (_: Exception) {
                    }
                    try {
                        w.toFront()
                    } catch (_: Exception) {
                    }
                    try {
                        w.requestFocus()
                    } catch (_: Exception) {
                    }
                }
            } else {
                isSettingsOpen = true
            }
        }

        // Settings window (separate, blank window)
        var configVersion by remember { mutableStateOf(0) }
        fun libraryReload() {
            scope.launch(Dispatchers.IO) {
                // Reset Home screen layout order on library rebuild
                net.canyonwolf.sparklauncher.data.HomeLayoutStore.setOrder(emptyList())
                val idx = GameIndexManager.rescanAndSave(appConfig)
                withContext(Dispatchers.Main) {
                    gameIndex = idx
                    refreshExeChoices(idx)
                }
                BoxArtFetcher.prefetchAll(idx.entries.map { it.toIgdbQueryName() })
                BoxArtFetcher.prefetchMetadataAll(idx.entries.map { it.launcher to it.toIgdbQueryName() })
            }
        }

        SettingsWindow(
            isOpen = isSettingsOpen,
            onCloseRequest = { isSettingsOpen = false; settingsWindowRef = null },
            onReloadLibraries = {
                libraryReload()
            },
            onRebuildCaches = {
                scope.launch(Dispatchers.IO) {
                    BoxArtFetcher.prefetchMetadataAll(
                        gameIndex.entries.map { it.launcher to it.toIgdbQueryName() }
                    )
                }
            },
            onThemeChanged = { newTheme -> themeName = newTheme },
            onConfigChanged = { configVersion++ },
            onWindowCreated = { settingsWindowRef = it }
        )

        // Executable selection popup for ambiguous games
        LaunchedEffect(gameIndex) {
            refreshExeChoices(gameIndex)
        }
        ExeSelectionWindow(
            isOpen = isExeWindowOpen,
            items = exeChoiceItems,
            onCloseRequest = { isExeWindowOpen = false },
            onSelection = { dirPath, exePath ->
                val updatedEntries = gameIndex.entries.map { e ->
                    if (e.dirPath == dirPath) e.copy(exePath = exePath) else e
                }
                val updated = gameIndex.copy(entries = updatedEntries)
                gameIndex = updated
                // Persist updated index
                GameIndexManager.save(updated)
                // Refresh; window auto-closes if nothing pending
                refreshExeChoices(updated)
            }
        )

        Scaffold(
            topBar = {
                TopMenuBar(
                    isHomeSelected = currentScreen == Screen.Home,
                    isLibrarySelected = currentScreen == Screen.Library,
                    onHomeClick = { currentScreen = Screen.Home },
                    onLibraryClick = { currentScreen = Screen.Library },
                    onSettingsClick = { openOrFocusSettings() }
                )
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(innerPadding)
                    .safeContentPadding()
            ) {
                var pendingSelection by remember { mutableStateOf<net.canyonwolf.sparklauncher.data.GameEntry?>(null) }
                when (currentScreen) {
                    Screen.Home -> HomeScreen(
                        entries = gameIndex.entries,
                        onOpenGame = { entry ->
                            pendingSelection = entry
                            currentScreen = Screen.Library
                        },
                        isConfigEmpty = isConfigEmpty,
                        onOpenSettings = { openOrFocusSettings() },
                        configVersion = configVersion
                    )

                    Screen.Library -> {
                        LibraryScreen(
                            entries = gameIndex.entries,
                            preselected = pendingSelection,
                            onReloadLibraries = {
                                libraryReload()
                            },
                            isConfigEmpty = isConfigEmpty,
                            onOpenSettings = { isSettingsOpen = true },
                            configVersion = configVersion
                        )
                        // Clear the pending selection after rendering once
                        if (pendingSelection != null) {
                            LaunchedEffect(Unit) { pendingSelection = null }
                        }
                    }
                }
            }
        }
    }
}

private fun net.canyonwolf.sparklauncher.data.GameEntry.toIgdbQueryName(): String {
    val n = this.name
    val normalized = n.lowercase().trim().replace(Regex("\\s+"), " ")
    return if (
        this.launcher == net.canyonwolf.sparklauncher.data.LauncherType.BATTLENET &&
        normalized == "call of duty"
    ) {
        "Call of Duty: Black Ops 6"
    } else n
}
