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
import net.canyonwolf.sparklauncher.ui.theme.liquidGlass
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
                HorizontalDivider(thickness = 1.dp, color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.1f))
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
    Surface(
        modifier = Modifier.fillMaxWidth().liquidGlass(),
        color = androidx.compose.ui.graphics.Color.Transparent,
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Text(item.gameName, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
            Spacer(Modifier.height(8.dp))
            item.candidates.forEachIndexed { idx, exe ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("${idx + 1}. ${exe}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { onSelection(exe) },
                        modifier = Modifier.height(32.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                    ) {
                        Text("Select", style = MaterialTheme.typography.labelMedium)
                    }
                }
                if (idx != item.candidates.lastIndex) HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), thickness = 1.dp, color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.1f))
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
