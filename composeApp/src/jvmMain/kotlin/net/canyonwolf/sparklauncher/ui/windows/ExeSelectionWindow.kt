package net.canyonwolf.sparklauncher.ui.windows

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import net.canyonwolf.sparklauncher.data.ExeSelectionStore
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.io.FilenameFilter

/**
 * A non-resizable popup window for resolving games needing an executable selection.
 * Instead of listing detected candidates, provides a file selector to browse and pick the correct .exe.
 * Selecting an item will persist the selection immediately via ExeSelectionStore and
 * invoke onSelection(dirPath, exePath).
 */
@Composable
fun ExeSelectionWindow(
    isOpen: Boolean,
    items: List<ExeChoiceItem>,
    onCloseRequest: () -> Unit,
    onSelection: (dirPath: String, exePath: String) -> Unit,
) {
    if (!isOpen) return
    Window(
        onCloseRequest = onCloseRequest,
        title = "Select Executable(s)",
        resizable = false,
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Text(
                    "Some games need help locating the correct executable. Please browse to select the .exe for each game:",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(12.dp))
                Divider()
                Spacer(Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(items) { item ->
                        GameChoiceCard(item = item, onSelection = { chosen ->
                            ExeSelectionStore.put(item.dirPath, chosen)
                            onSelection(item.dirPath, chosen)
                        })
                        Spacer(Modifier.height(12.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun GameChoiceCard(item: ExeChoiceItem, onSelection: (String) -> Unit) {
    Card {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Text(item.gameName, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
            Spacer(Modifier.height(8.dp))
            Text("Game folder: ${item.dirPath}", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "Select the executable (.exe) using the file chooser.",
                    style = MaterialTheme.typography.bodyMedium
                )
                TextButton(onClick = {
                    val startDir = File(item.dirPath)
                    val dirToUse = (startDir.takeIf { it.exists() } ?: startDir.parentFile)
                        ?: File(System.getProperty("user.home"))
                    val dialog = FileDialog(null as Frame?, "Choose executable for ${item.gameName}", FileDialog.LOAD)
                    dialog.directory = dirToUse.absolutePath
                    dialog.filenameFilter = FilenameFilter { _, name -> name.endsWith(".exe", ignoreCase = true) }
                    try {
                        dialog.isVisible = true
                    } catch (_: Exception) {
                        dialog.show()
                    }
                    val file = dialog.file
                    val dir = dialog.directory
                    if (!file.isNullOrBlank() && !dir.isNullOrBlank()) {
                        val chosen = File(dir, file).absolutePath
                        if (chosen.endsWith(".exe", ignoreCase = true)) {
                            onSelection(chosen)
                        }
                    }
                }) {
                    Text("Browse…")
                }
            }
        }
    }
}

/**
 * Data for one game's selection section.
 */
data class ExeChoiceItem(
    val gameName: String,
    val dirPath: String,
    val candidates: List<String>,
)
