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
import net.canyonwolf.sparklauncher.ui.components.TopMenuBar
import net.canyonwolf.sparklauncher.ui.screens.HomeScreen
import net.canyonwolf.sparklauncher.ui.screens.LibraryScreen
import net.canyonwolf.sparklauncher.ui.windows.SettingsWindow

private enum class Screen { Home, Library }

@Composable
fun App() {
    // Load configuration once on app startup
    val appConfig by remember { mutableStateOf(net.canyonwolf.sparklauncher.config.ConfigManager.loadOrCreateDefault()) }

    net.canyonwolf.sparklauncher.ui.theme.AppTheme(themeName = appConfig.theme) {
        var currentScreen by remember { mutableStateOf(Screen.Home) }
        var isSettingsOpen by remember { mutableStateOf(false) }

        // Settings window (separate, blank window)
        SettingsWindow(
            isOpen = isSettingsOpen,
            onCloseRequest = { isSettingsOpen = false }
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
                when (currentScreen) {
                    Screen.Home -> HomeScreen()
                    Screen.Library -> LibraryScreen()
                }
            }
        }
    }
}