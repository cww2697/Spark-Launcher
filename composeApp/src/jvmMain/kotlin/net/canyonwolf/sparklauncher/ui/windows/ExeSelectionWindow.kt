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

/**
 * A non-resizable popup window listing games that have multiple .exe candidates.
 * Each section displays the game name and a numbered list of candidate executables.
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
                    "Multiple executables detected. Please choose the correct one for each game:",
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
            item.candidates.forEachIndexed { idx, exe ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("${idx + 1}. ${exe}", style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { onSelection(exe) }) {
                        Text("Select")
                    }
                }
                if (idx != item.candidates.lastIndex) Divider(modifier = Modifier.padding(vertical = 6.dp))
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
