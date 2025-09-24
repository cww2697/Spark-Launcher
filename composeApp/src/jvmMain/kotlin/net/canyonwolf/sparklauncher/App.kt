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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.canyonwolf.sparklauncher.data.GameIndex
import net.canyonwolf.sparklauncher.data.GameIndexManager
import net.canyonwolf.sparklauncher.ui.components.TopMenuBar
import net.canyonwolf.sparklauncher.ui.screens.HomeScreen
import net.canyonwolf.sparklauncher.ui.screens.LibraryScreen
import net.canyonwolf.sparklauncher.ui.util.BoxArtFetcher
import net.canyonwolf.sparklauncher.ui.windows.SettingsWindow

private enum class Screen { Home, Library }

@Composable
fun App() {
    // Load configuration once on app startup
    val initialConfig = remember { net.canyonwolf.sparklauncher.config.ConfigManager.loadOrCreateDefault() }
    var themeName by remember { mutableStateOf(initialConfig.theme) }
    val appConfig by remember { mutableStateOf(initialConfig) }
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
            withContext(Dispatchers.Main) { gameIndex = idx }
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
        var currentScreen by remember { mutableStateOf(Screen.Home) }
        var isSettingsOpen by remember { mutableStateOf(false) }
        val homeScrollState = remember { androidx.compose.foundation.ScrollState(0) }

        // Settings window (separate, blank window)
        var configVersion by remember { mutableStateOf(0) }
        fun libraryReload() {
            scope.launch(Dispatchers.IO) {
                val idx = GameIndexManager.rescanAndSave(appConfig)
                withContext(Dispatchers.Main) { gameIndex = idx }
                BoxArtFetcher.prefetchAll(idx.entries.map { it.toIgdbQueryName() })
                BoxArtFetcher.prefetchMetadataAll(idx.entries.map { it.launcher to it.toIgdbQueryName() })
            }
        }

        SettingsWindow(
            isOpen = isSettingsOpen,
            onCloseRequest = { isSettingsOpen = false },
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
            onConfigChanged = { configVersion++ }
        )

        Scaffold(
            topBar = {
                TopMenuBar(
                    isHomeSelected = currentScreen == Screen.Home,
                    isLibrarySelected = currentScreen == Screen.Library,
                    onHomeClick = { currentScreen = Screen.Home },
                    onLibraryClick = { currentScreen = Screen.Library },
                    onSettingsClick = { isSettingsOpen = true }
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
                        onOpenSettings = { isSettingsOpen = true },
                        configVersion = configVersion,
                        scrollState = homeScrollState
                    )

                    Screen.Library -> {
                        LibraryScreen(
                            entries = gameIndex.entries,
                            preselected = pendingSelection,
                            onReloadLibraries = {
                                libraryReload()
                            },
                            isConfigEmpty = isConfigEmpty,
                            onOpenSettings = { isSettingsOpen = true }
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
