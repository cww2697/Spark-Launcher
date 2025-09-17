package net.canyonwolf.sparklauncher

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

fun main() = application {
    // Load config to get previous window size (if any)
    val cfg = net.canyonwolf.sparklauncher.config.ConfigManager.loadOrCreateDefault()
    val startW = maxOf(cfg.windowWidth.takeIf { it > 0 } ?: 1280, 600)
    val startH = maxOf(cfg.windowHeight.takeIf { it > 0 } ?: 720, 600)

    val state = rememberWindowState(
        width = startW.dp,
        height = startH.dp
    )

    Window(
        onCloseRequest = {
            try {
                // Reload latest config to merge with any changes made in Settings
                val latest = net.canyonwolf.sparklauncher.config.ConfigManager.loadOrCreateDefault()
                val newCfg = latest.copy(
                    windowWidth = state.size.width.value.toInt(),
                    windowHeight = state.size.height.value.toInt()
                )
                net.canyonwolf.sparklauncher.config.ConfigManager.save(newCfg)
            } catch (_: Throwable) {
            } finally {
                exitApplication()
            }
        },
        title = "SparkLauncher",
        state = state,
    ) {
        // Enforce minimum size via AWT window
        androidx.compose.runtime.LaunchedEffect(Unit) {
            try {
                this@Window.window.minimumSize = java.awt.Dimension(600, 600)
            } catch (_: Throwable) {
            }
        }
        App()
    }
}