package net.canyonwolf.sparklauncher.ui.windows

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import net.canyonwolf.sparklauncher.config.AppConfig
import net.canyonwolf.sparklauncher.config.ConfigManager
import java.awt.Desktop
import java.net.URI
import javax.swing.JFileChooser

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsWindow(
    isOpen: Boolean,
    onCloseRequest: () -> Unit,
    onReloadLibraries: () -> Unit,
    onRebuildCaches: () -> Unit,
    onThemeChanged: (String) -> Unit = {},
    onConfigChanged: () -> Unit = {},
    onWindowCreated: (ComposeWindow) -> Unit = {},
) {
    if (isOpen) {
        Window(
            onCloseRequest = onCloseRequest,
            title = "Settings",
            resizable = false,
        ) {
            LaunchedEffect(Unit) { onWindowCreated(window) }

            // Load current config
            val currentConfig = remember { ConfigManager.loadOrCreateDefault() }

            // Local state for form fields
            var selectedTheme by remember { mutableStateOf(currentConfig.theme) }
            // Multi-library per platform (keep legacy single fields for migration)
            val steamLibs = remember {
                mutableStateListOf<String>().apply {
                    val src =
                        if (currentConfig.steamLibraries.isNotEmpty()) currentConfig.steamLibraries else listOfNotNull(
                            currentConfig.steamPath.takeIf { it.isNotBlank() })
                    addAll(src.ifEmpty { listOf("") })
                }
            }
            val eaLibs = remember {
                mutableStateListOf<String>().apply {
                    val src = if (currentConfig.eaLibraries.isNotEmpty()) currentConfig.eaLibraries else listOfNotNull(
                        currentConfig.eaPath.takeIf { it.isNotBlank() })
                    addAll(src.ifEmpty { listOf("") })
                }
            }
            val bnetLibs = remember {
                mutableStateListOf<String>().apply {
                    val src =
                        if (currentConfig.battleNetLibraries.isNotEmpty()) currentConfig.battleNetLibraries else listOfNotNull(
                            currentConfig.battleNetPath.takeIf { it.isNotBlank() })
                    addAll(src.ifEmpty { listOf("") })
                }
            }
            val ubiLibs = remember {
                mutableStateListOf<String>().apply {
                    val src =
                        if (currentConfig.ubisoftLibraries.isNotEmpty()) currentConfig.ubisoftLibraries else listOfNotNull(
                            currentConfig.ubisoftPath.takeIf { it.isNotBlank() })
                    addAll(src.ifEmpty { listOf("") })
                }
            }
            val customLibs = remember {
                mutableStateListOf<String>().apply {
                    val src = currentConfig.customLibraries
                    addAll((if (src.isNotEmpty()) src else listOf("")))
                }
            }
            var igdbClientId by remember { mutableStateOf(currentConfig.igdbClientId) }
            var igdbClientSecret by remember { mutableStateOf(currentConfig.igdbClientSecret) }

            // Home settings
            var showUncategorizedTitles by remember { mutableStateOf(currentConfig.showUncategorizedTitles) }
            var showGamesInMultipleCategories by remember { mutableStateOf(currentConfig.showGamesInMultipleCategories) }

            // Play Statistics settings
            var dateTimeFormatPattern by remember { mutableStateOf(currentConfig.dateTimeFormatPattern) }
            val dateTimeFormatOptions = remember {
                // Keep internal patterns but present examples to the user (including date-only formats)
                listOf(
                    "MMM d, yyyy h:mm a",      // e.g., Sep 27, 2025 5:45 PM
                    "yyyy-MM-dd HH:mm",        // e.g., 2025-09-27 17:45
                    "dd/MM/yyyy HH:mm",        // e.g., 27/09/2025 17:45
                    "EEE, MMM d, yyyy h:mm a", // e.g., Sat, Sep 27, 2025 5:45 PM
                    "MMM d h:mm a",            // e.g., Sep 27 5:45 PM
                    // Date-only options (no time)
                    "MMM d, yyyy",             // e.g., Sep 27, 2025
                    "yyyy-MM-dd",              // e.g., 2025-09-27
                    "dd/MM/yyyy",              // e.g., 27/09/2025
                    "EEE, MMM d, yyyy"         // e.g., Sat, Sep 27, 2025
                )
            }
            val dateExampleForPattern: (String) -> String = remember {
                { pattern ->
                    try {
                        val now = java.time.LocalDateTime.now()
                        val fmt = java.time.format.DateTimeFormatter.ofPattern(pattern)
                        now.format(fmt)
                    } catch (_: Throwable) {
                        pattern
                    }
                }
            }
            val currentFormatExample = remember(dateTimeFormatPattern) { dateExampleForPattern(dateTimeFormatPattern) }

            // Available themes; refresh when window opens
            var themes by remember {
                mutableStateOf(
                    listOf(
                        "Default",
                        "Light"
                    ) + net.canyonwolf.sparklauncher.ui.theme.ThemeManager.listThemeNames()
                )
            }
            LaunchedEffect(isOpen) {
                if (isOpen) {
                    net.canyonwolf.sparklauncher.ui.theme.ThemeManager.init()
                    themes =
                        listOf("Default", "Light") + net.canyonwolf.sparklauncher.ui.theme.ThemeManager.listThemeNames()
                }
            }
            var themeMenuExpanded by remember { mutableStateOf(false) }

            // Using ambient MaterialTheme from the app; no additional theme wrapper here
            run {
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
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                                    border = BorderStroke(
                                        1.dp,
                                        MaterialTheme.colorScheme.primary
                                    )
                                ) {
                                    Text("Cancel")
                                }
                                Spacer(Modifier.width(16.dp))
                                Button(onClick = {
                                    // Normalize lists: trim blanks, cap to 5
                                    fun norm(list: List<String>) =
                                        list.map { it.trim() }.filter { it.isNotBlank() }.take(5)

                                    val steamList = norm(steamLibs)
                                    val eaList = norm(eaLibs)
                                    val bnetList = norm(bnetLibs)
                                    val ubiList = norm(ubiLibs)
                                    val customList = norm(customLibs)
                                    // Save to config (keep legacy single fields from the first element, or empty)
                                    val newConfig = AppConfig(
                                        theme = selectedTheme,
                                        steamPath = steamList.firstOrNull() ?: "",
                                        eaPath = eaList.firstOrNull() ?: "",
                                        battleNetPath = bnetList.firstOrNull() ?: "",
                                        ubisoftPath = ubiList.firstOrNull() ?: "",
                                        steamLibraries = steamList,
                                        eaLibraries = eaList,
                                        battleNetLibraries = bnetList,
                                        ubisoftLibraries = ubiList,
                                        customLibraries = customList,
                                        igdbClientId = igdbClientId,
                                        igdbClientSecret = igdbClientSecret,
                                        windowWidth = currentConfig.windowWidth,
                                        windowHeight = currentConfig.windowHeight,
                                        showUncategorizedTitles = showUncategorizedTitles,
                                        showGamesInMultipleCategories = showGamesInMultipleCategories,
                                        dateTimeFormatPattern = dateTimeFormatPattern,
                                    )
                                    ConfigManager.save(newConfig)
                                    onThemeChanged(selectedTheme)
                                    onConfigChanged()
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
                                            ExposedDropdownMenuBox(
                                                expanded = themeMenuExpanded,
                                                onExpandedChange = { themeMenuExpanded = !themeMenuExpanded }
                                            ) {
                                                OutlinedTextField(
                                                    value = selectedTheme,
                                                    onValueChange = {},
                                                    modifier = Modifier
                                                        .menuAnchor()
                                                        .fillMaxWidth(),
                                                    label = { Text("Theme") },
                                                    readOnly = true,
                                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = themeMenuExpanded) },
                                                    enabled = true
                                                )
                                                ExposedDropdownMenu(
                                                    expanded = themeMenuExpanded,
                                                    onDismissRequest = { themeMenuExpanded = false },
                                                    modifier = Modifier.exposedDropdownSize(matchTextFieldWidth = true)
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

                                    // Integrations Section
                                    Text(
                                        text = "Integrations",
                                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    SectionCard {
                                        Column(
                                            Modifier.fillMaxWidth().padding(16.dp),
                                            verticalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            OutlinedTextField(
                                                value = igdbClientId,
                                                onValueChange = { igdbClientId = it },
                                                modifier = Modifier.fillMaxWidth(),
                                                label = { Text("IGDB Client ID") },
                                                singleLine = true
                                            )
                                            OutlinedTextField(
                                                value = igdbClientSecret,
                                                onValueChange = { igdbClientSecret = it },
                                                modifier = Modifier.fillMaxWidth(),
                                                label = { Text("IGDB Client Secret") },
                                                singleLine = true
                                            )
                                        }
                                    }

                                    Spacer(Modifier.height(24.dp))

                                    // Home Section
                                    Text(
                                        text = "Home",
                                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    SectionCard {
                                        Column(
                                            Modifier.fillMaxWidth().padding(16.dp),
                                            verticalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Checkbox(
                                                    checked = showUncategorizedTitles,
                                                    onCheckedChange = { showUncategorizedTitles = it }
                                                )
                                                Spacer(Modifier.width(8.dp))
                                                Text("Show Uncategorized Titles")
                                            }
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Checkbox(
                                                    checked = showGamesInMultipleCategories,
                                                    onCheckedChange = { showGamesInMultipleCategories = it }
                                                )
                                                Spacer(Modifier.width(8.dp))
                                                Text("Show Games in Multiple Categories")
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
                                            MultiLibraryPicker(label = "Steam", libraries = steamLibs)
                                            MultiLibraryPicker(label = "EA", libraries = eaLibs)
                                            MultiLibraryPicker(label = "Battle.Net", libraries = bnetLibs)
                                            MultiLibraryPicker(label = "Ubisoft", libraries = ubiLibs)
                                            MultiLibraryPicker(label = "Custom", libraries = customLibs)

                                            // Bottom actions inside Libraries section
                                            Spacer(Modifier.height(8.dp))
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.Center,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Button(
                                                    onClick = { onReloadLibraries() },
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
                                                Spacer(Modifier.width(12.dp))
                                                Button(
                                                    onClick = { onRebuildCaches() },
                                                    modifier = Modifier.height(40.dp),
                                                    contentPadding = PaddingValues(horizontal = 12.dp),
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                ) {
                                                    Text("⟲")
                                                    Spacer(Modifier.width(8.dp))
                                                    Text("Rebuild Caches")
                                                }
                                            }
                                        }
                                    }

                                    Spacer(Modifier.height(24.dp))

                                    // Play Statistics Section
                                    Text(
                                        text = "Play Statistics",
                                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    SectionCard {
                                        Column(
                                            Modifier.fillMaxWidth().padding(16.dp),
                                            verticalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            var dtMenuExpanded by remember { mutableStateOf(false) }
                                            Text(
                                                text = "Last Played date format",
                                                style = MaterialTheme.typography.titleSmall,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            ExposedDropdownMenuBox(
                                                expanded = dtMenuExpanded,
                                                onExpandedChange = { dtMenuExpanded = !dtMenuExpanded }
                                            ) {
                                                OutlinedTextField(
                                                    value = currentFormatExample,
                                                    onValueChange = {},
                                                    modifier = Modifier
                                                        .menuAnchor()
                                                        .fillMaxWidth(),
                                                    label = { Text("Date-Time Format") },
                                                    readOnly = true,
                                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dtMenuExpanded) },
                                                    enabled = true
                                                )
                                                ExposedDropdownMenu(
                                                    expanded = dtMenuExpanded,
                                                    onDismissRequest = { dtMenuExpanded = false },
                                                    modifier = Modifier.exposedDropdownSize(matchTextFieldWidth = true)
                                                ) {
                                                    dateTimeFormatOptions.forEach { option ->
                                                        DropdownMenuItem(
                                                            text = { Text(dateExampleForPattern(option)) },
                                                            onClick = {
                                                                dateTimeFormatPattern = option
                                                                dtMenuExpanded = false
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    Spacer(Modifier.height(24.dp))

                                    // About Section
                                    Text(
                                        text = "About",
                                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    SectionCard {
                                        Column(
                                            Modifier.fillMaxWidth().padding(16.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            val appVersion = run {
                                                val v =
                                                    Package.getPackage("net.canyonwolf.sparklauncher")?.implementationVersion
                                                if (v.isNullOrBlank()) "dev" else v
                                            }
                                            Text(
                                                text = "Version: $appVersion",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )

                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = "Repository:",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Spacer(Modifier.width(8.dp))
                                                val url = "https://github.com/cww2697/Spark-Launcher"
                                                Text(
                                                    text = url,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    textDecoration = TextDecoration.Underline,
                                                    modifier = Modifier.clickable {
                                                        runCatching {
                                                            val desktop =
                                                                if (Desktop.isDesktopSupported()) Desktop.getDesktop() else null
                                                            desktop?.browse(URI(url))
                                                        }
                                                    }
                                                )
                                            }
                                        }
                                    }
                                    Spacer(Modifier.height(24.dp))
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
                try {
                    val currentPath = path
                    if (currentPath.isNotBlank()) {
                        val f = java.io.File(currentPath)
                        val dir = if (f.exists() && f.isDirectory) f else f.parentFile
                        if (dir != null && dir.exists() && dir.isDirectory) {
                            chooser.currentDirectory = dir
                        }
                    }
                } catch (_: Throwable) {
                }
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


@Composable
private fun MultiLibraryPicker(label: String, libraries: MutableList<String>) {
    Column(Modifier.fillMaxWidth()) {
        Text(text = label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(6.dp))
        if (libraries.isEmpty()) libraries.add("")
        // Fixed sizes to keep rows aligned regardless of which buttons are visible
        val controlHeight = 36.dp
        val browseWidth = 110.dp
        val iconBtnSize = controlHeight
        for (index in libraries.indices.toList()) {
            val value = libraries[index]
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = value,
                    onValueChange = { newVal: String -> libraries[index] = newVal },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(if (index == 0) "Choose folder or leave empty" else "Choose additional folder") },
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
                // Browse button (fixed width and height for alignment)
                FilledTonalButton(
                    onClick = {
                        val chooser = JFileChooser()
                        chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                        try {
                            val currentPath = value
                            if (currentPath.isNotBlank()) {
                                val f = java.io.File(currentPath)
                                val dir = if (f.exists() && f.isDirectory) f else f.parentFile
                                if (dir != null && dir.exists() && dir.isDirectory) {
                                    chooser.currentDirectory = dir
                                }
                            }
                        } catch (_: Throwable) {
                        }
                        val result = chooser.showOpenDialog(null)
                        if (result == JFileChooser.APPROVE_OPTION) {
                            val selected = chooser.selectedFile?.absolutePath ?: ""
                            libraries[index] = selected
                        }
                    },
                    modifier = Modifier
                        .width(browseWidth)
                        .height(controlHeight)
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Browse")
                    }
                }
                Spacer(Modifier.width(8.dp))
                // Single trailing icon slot: shows + on first row when populated and can add; otherwise shows trash on rows > 0; otherwise placeholder
                val canAddMore = libraries.count() < 5
                val showPlus = index == 0 && value.isNotBlank() && canAddMore
                val showTrash = index > 0
                when {
                    showPlus -> {
                        FilledTonalButton(
                            onClick = { if (libraries.count() < 5) libraries.add("") },
                            modifier = Modifier.size(iconBtnSize),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("+")
                            }
                        }
                    }

                    showTrash -> {
                        FilledTonalButton(
                            onClick = { libraries.removeAt(index) },
                            modifier = Modifier.size(iconBtnSize),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("🗑")
                            }
                        }
                    }

                    else -> {
                        Box(modifier = Modifier.size(iconBtnSize)) {}
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
