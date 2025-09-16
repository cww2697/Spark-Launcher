package net.canyonwolf.sparklauncher.ui.windows

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import net.canyonwolf.sparklauncher.config.AppConfig
import net.canyonwolf.sparklauncher.config.ConfigManager
import net.canyonwolf.sparklauncher.ui.theme.AppTheme
import javax.swing.JFileChooser

@Composable
fun SettingsWindow(
    isOpen: Boolean,
    onCloseRequest: () -> Unit,
) {
    if (isOpen) {
        Window(
            onCloseRequest = onCloseRequest,
            title = "Settings",
            resizable = false,
        ) {
            // Load current config
            val currentConfig = remember { ConfigManager.loadOrCreateDefault() }

            // Local state for form fields
            var selectedTheme by remember { mutableStateOf(currentConfig.theme) }
            var steamPath by remember { mutableStateOf(currentConfig.steamPath) }
            var eaPath by remember { mutableStateOf(currentConfig.eaPath) }
            var battleNetPath by remember { mutableStateOf(currentConfig.battleNetPath) }
            var ubisoftPath by remember { mutableStateOf(currentConfig.ubisoftPath) }

            // Available themes (currently only Default)
            val themes = remember { listOf("Default") }
            var themeMenuExpanded by remember { mutableStateOf(false) }

            AppTheme(themeName = selectedTheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Scaffold(
                        bottomBar = {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = onCloseRequest,
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.secondary),
                                    border = BorderStroke(
                                        1.dp,
                                        MaterialTheme.colorScheme.secondary
                                    )
                                ) {
                                    Text("Cancel")
                                }
                                Spacer(Modifier.width(16.dp))
                                Button(onClick = {
                                    // Save to config
                                    val newConfig = AppConfig(
                                        theme = selectedTheme,
                                        steamPath = steamPath,
                                        eaPath = eaPath,
                                        battleNetPath = battleNetPath,
                                        ubisoftPath = ubisoftPath,
                                    )
                                    ConfigManager.save(newConfig)
                                    onCloseRequest()
                                }) {
                                    Text("Save")
                                }
                            }
                        }
                    ) { innerPadding ->
                        val scrollState = rememberScrollState()
                        val focusManager = LocalFocusManager.current
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.background)
                                .padding(24.dp)
                                .padding(bottom = innerPadding.calculateBottomPadding())
                                .pointerInput(Unit) { detectTapGestures(onTap = { focusManager.clearFocus() }) }
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(scrollState)
                                ) {
                                    // Theme Section
                                    Text(
                                        text = "Theme",
                                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    SectionCard {
                                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                                            Box {
                                                OutlinedTextField(
                                                    value = selectedTheme,
                                                    onValueChange = {},
                                                    modifier = Modifier.fillMaxWidth(),
                                                    label = { Text("Theme") },
                                                    readOnly = true,
                                                    trailingIcon = {
                                                        Text("▾", color = MaterialTheme.colorScheme.onSurface)
                                                    },
                                                    enabled = true
                                                )
                                                DropdownMenu(
                                                    expanded = themeMenuExpanded,
                                                    onDismissRequest = { themeMenuExpanded = false },
                                                ) {
                                                    themes.forEach { option ->
                                                        DropdownMenuItem(
                                                            text = { Text(option) },
                                                            onClick = {
                                                                selectedTheme = option
                                                                themeMenuExpanded = false
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    Spacer(Modifier.height(24.dp))

                                    // Libraries Section
                                    Text(
                                        text = "Libraries",
                                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    SectionCard {
                                        Column(
                                            Modifier.fillMaxWidth().padding(16.dp),
                                            verticalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            FilePathPicker(
                                                label = "Steam",
                                                path = steamPath,
                                                onPathChange = { steamPath = it }
                                            )
                                            FilePathPicker(
                                                label = "EA",
                                                path = eaPath,
                                                onPathChange = { eaPath = it }
                                            )
                                            FilePathPicker(
                                                label = "Battle.Net",
                                                path = battleNetPath,
                                                onPathChange = { battleNetPath = it }
                                            )
                                            FilePathPicker(
                                                label = "Ubisoft",
                                                path = ubisoftPath,
                                                onPathChange = { ubisoftPath = it }
                                            )

                                            // Bottom actions inside Libraries section
                                            Spacer(Modifier.height(8.dp))
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.Center,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Button(
                                                    onClick = { /* no-op for now */ },
                                                    modifier = Modifier.height(40.dp),
                                                    contentPadding = PaddingValues(horizontal = 12.dp),
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                ) {
                                                    Text("↻")
                                                    Spacer(Modifier.width(8.dp))
                                                    Text("Reload Libraries")
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            if (scrollState.maxValue > 0) {
                                Spacer(Modifier.width(8.dp))
                                VerticalScrollbar(
                                    adapter = rememberScrollbarAdapter(scrollState),
                                    modifier = Modifier
                                        .fillMaxHeight()
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionCard(content: @Composable () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        tonalElevation = 2.dp,
        shadowElevation = 2.dp,
        shape = MaterialTheme.shapes.medium
    ) {
        content()
    }
}

@Composable
private fun FilePathPicker(label: String, path: String, onPathChange: (String) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(text = label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = path,
                onValueChange = { onPathChange(it) },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Choose folder or leave empty") },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    disabledTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    focusedPlaceholderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    disabledPlaceholderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    cursorColor = MaterialTheme.colorScheme.primary,
                    focusedIndicatorColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f),
                    unfocusedIndicatorColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f),
                    disabledIndicatorColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                )
            )
            Spacer(Modifier.width(8.dp))
            FilledTonalButton(onClick = {
                val chooser = JFileChooser()
                chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                val result = chooser.showOpenDialog(null)
                if (result == JFileChooser.APPROVE_OPTION) {
                    onPathChange(chooser.selectedFile?.absolutePath ?: "")
                }
            }) {
                Text("Browse")
            }
        }
    }
}
