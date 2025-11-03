package net.canyonwolf.sparklauncher.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.canyonwolf.sparklauncher.data.*
import net.canyonwolf.sparklauncher.ui.util.BoxArtFetcher

@Composable
fun LibraryScreen(
    entries: List<GameEntry>,
    preselected: GameEntry? = null,
    onReloadLibraries: (() -> Unit)? = null,
    isConfigEmpty: Boolean = false,
    onOpenSettings: (() -> Unit)? = null,
    configVersion: Int = 0,
) {
    val grouped: Map<LauncherType, List<GameEntry>> = remember(entries) {
        entries.groupBy { it.launcher }
    }

    if (entries.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                if (isConfigEmpty) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        tonalElevation = 2.dp,
                        shadowElevation = 0.dp,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("No games indexed yet", style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "It looks like you haven't configured your launchers. Open Settings to add paths and scan your library.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                                )
                            }
                            Button(onClick = { onOpenSettings?.invoke() }) { Text("Open Settings") }
                        }
                    }
                }
                Text(
                    "Your library is empty",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Try reloading your libraries.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { onReloadLibraries?.invoke() }) {
                        Text("Reload Library")
                    }
                }
            }
        }
        return
    }

    // Expand/collapse states per launcher
    val launchersInOrder =
        listOf(LauncherType.STEAM, LauncherType.EA, LauncherType.BATTLENET, LauncherType.UBISOFT, LauncherType.CUSTOM)
    val expandedState = remember { mutableStateMapOf<LauncherType, Boolean>() }
    launchersInOrder.forEach { lt -> if (expandedState[lt] == null) expandedState[lt] = true }

    var searchQuery by rememberSaveable { mutableStateOf("") }
    val filteredGrouped: Map<LauncherType, List<GameEntry>> = remember(entries, searchQuery) {
        val q = searchQuery.trim()
        val filtered = if (q.isBlank()) entries else entries.filter { it.name.contains(q, ignoreCase = true) }
        filtered.groupBy { it.launcher }
    }

    var selected by remember(entries) { mutableStateOf<GameEntry?>(preselected) }
    var boxArt by remember(selected) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var gameInfo by remember(selected) { mutableStateOf<BoxArtFetcher.GameInfo?>(null) }
    // Local loader state for on-demand fetch when metadata is missing
    var isInfoLoading by remember(selected) { mutableStateOf(false) }
    // System app icon as fallback when no box art exists
    var appIcon by remember(selected) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    // Play stats for the selected game
    var playStats by remember(selected) { mutableStateOf<UserDataStore.PlayStat?>(null) }
    // Track if the selected game's process is currently running
    var isRunning by remember(selected) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    // Favorites state
    var favorites by remember { mutableStateOf(FavoritesStore.getAll()) }
    var showRunConfigDialog by remember { mutableStateOf(false) }

    // Apply preselected only when provided; do not reset selection on later recompositions
    LaunchedEffect(preselected) {
        if (preselected != null) {
            selected = preselected
        }
    }

    LaunchedEffect(selected) {
        try {
            val sel = selected
            if (sel == null) {
                boxArt = null
                gameInfo = null
                isInfoLoading = false
                appIcon = null
                isRunning = false
            } else {
                val normalized = sel.name.lowercase().trim().replace(Regex("\\s+"), " ")
                val queryName = if (sel.launcher == LauncherType.BATTLENET && normalized == "call of duty") {
                    "Call of Duty: Black Ops 6"
                } else sel.name
                if (queryName.isBlank()) {
                    boxArt = null
                    gameInfo = null
                    isInfoLoading = false
                    appIcon = null
                } else {
                    // Fetch on background to avoid blocking UI
                    val art = withContext(Dispatchers.IO) {
                        BoxArtFetcher.getBoxArt(queryName)
                    }
                    boxArt = art
                    if (art == null) {
                        // Load system app icon as fallback
                        appIcon = withContext(Dispatchers.IO) {
                            net.canyonwolf.sparklauncher.ui.util.SystemIconLoader.getIcon(sel.exePath)
                        }
                    } else {
                        appIcon = null
                    }
                    // First try to get cached metadata
                    val cached = withContext(Dispatchers.IO) {
                        BoxArtFetcher.getGameInfo(sel.launcher, queryName)
                    }
                    val hasRecord = withContext(Dispatchers.IO) {
                        BoxArtFetcher.hasInfoRecord(sel.launcher, queryName)
                    }
                    if (cached != null) {
                        gameInfo = cached
                        isInfoLoading = false
                    } else if (!hasRecord) {
                        // Only fetch on-demand if no record exists yet
                        isInfoLoading = true
                        val fetched = withContext(Dispatchers.IO) {
                            BoxArtFetcher.fetchAndCacheGameInfo(sel.launcher, queryName)
                        }
                        // Only apply if selection hasn't changed
                        if (selected?.exePath == sel.exePath) {
                            gameInfo = fetched
                            isInfoLoading = false
                        }
                    } else {
                        // We have a negative cache; do not try again
                        gameInfo = null
                        isInfoLoading = false
                    }
                }
                // Load play stats for the selected game
                playStats = withContext(Dispatchers.IO) {
                    UserDataStore.get(sel.name)
                }
            }
        } catch (_: Throwable) {
            // On any unexpected error (e.g., network/parse), ensure loader is hidden and no crash occurs
            isInfoLoading = false
            gameInfo = null
            // Do not change boxArt/appIcon here; leave any already-loaded visual as-is
        }
    }

    // Poll whether the selected game's process is running and update UI state
    LaunchedEffect(selected) {
        val sel = selected
        if (sel == null) {
            isRunning = false
        } else {
            try {
                while (selected?.exePath == sel.exePath) {
                    isRunning = isProcessRunning(sel.exePath)
                    kotlinx.coroutines.delay(1000)
                }
            } catch (_: Throwable) {
                isRunning = false
            }
        }
    }

    val config = remember(configVersion) { net.canyonwolf.sparklauncher.config.ConfigManager.loadOrCreateDefault() }
    val datePattern = config.dateTimeFormatPattern.ifBlank { "MMM d, yyyy h:mm a" }

    Row(Modifier.fillMaxSize()) {
        // Left navigation list
        Surface(tonalElevation = 1.dp, modifier = Modifier.width(300.dp).fillMaxHeight()) {
            // Compute favorites shown set based on current filter
            val favEntries = entries.filter { favorites.contains(it.dirPath) }
            val favShown = if (searchQuery.isBlank()) favEntries else favEntries.filter {
                it.name.contains(
                    searchQuery,
                    ignoreCase = true
                )
            }
            val favShownDirSet = favShown.map { it.dirPath }.toSet()
            LazyColumn(Modifier.fillMaxSize().padding(vertical = 8.dp)) {
                item(key = "search-bar") {
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp)) {
                        val fieldTextStyle = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeightStyle = LineHeightStyle(
                                alignment = LineHeightStyle.Alignment.Center,
                                trim = LineHeightStyle.Trim.Both
                            )
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(28.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                                    if (searchQuery.isBlank()) {
                                        Text(
                                            text = "Search library…",
                                            style = fieldTextStyle.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    androidx.compose.foundation.text.BasicTextField(
                                        value = searchQuery,
                                        onValueChange = { searchQuery = it },
                                        singleLine = true,
                                        textStyle = fieldTextStyle,
                                        cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                if (searchQuery.isNotBlank()) {
                                    Surface(
                                        shape = androidx.compose.foundation.shape.CircleShape,
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        tonalElevation = 0.dp,
                                        modifier = Modifier.size(20.dp).clickable { searchQuery = "" }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Close,
                                            contentDescription = "Clear search",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(3.dp).size(12.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Favorites section (if any)
                run {
                    if (favShown.isNotEmpty()) {
                        item(key = "header-favorites") {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("★", modifier = Modifier.width(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "Favorites (" + favShown.size + ")",
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                        }
                        items(favShown, key = { "FAV:${it.launcher}:${it.exePath}" }) { e ->
                            GameListItem(
                                entry = e,
                                selected = selected?.exePath == e.exePath,
                                onClick = { selected = e }
                            )
                        }
                    }
                }
                
                launchersInOrder.forEach { launcher ->
                    val originalList = grouped[launcher].orEmpty()
                    if (originalList.isEmpty()) return@forEach
                    val shownList = filteredGrouped[launcher].orEmpty().filter { !favShownDirSet.contains(it.dirPath) }

                    item(key = "header-${launcher.name}") {
                        LauncherHeader(
                            launcher = launcher,
                            count = shownList.size,
                            expanded = expandedState[launcher] == true,
                            onToggle = { expandedState[launcher] = !(expandedState[launcher] ?: true) }
                        )
                    }
                    if (expandedState[launcher] == true) {
                        if (shownList.isEmpty() && searchQuery.isNotBlank()) {
                            item(key = "nores-${launcher.name}") {
                                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                                    val lname = when (launcher) {
                                        LauncherType.STEAM -> "Steam"
                                        LauncherType.EA -> "EA App"
                                        LauncherType.BATTLENET -> "Battle.net"
                                        LauncherType.UBISOFT -> "Ubisoft Connect"
                                        LauncherType.CUSTOM -> "Custom"
                                    }
                                    Text(
                                        text = "No results found under $lname",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                        fontStyle = FontStyle.Italic
                                    )
                                }
                            }
                        } else {
                            items(shownList, key = { "${it.launcher}:${it.exePath}" }) { e ->
                                GameListItem(
                                    entry = e,
                                    selected = selected?.exePath == e.exePath,
                                    onClick = { selected = e }
                                )
                            }
                        }
                    }
                    item(key = "divider-${launcher.name}") { HorizontalDivider(thickness = 1.dp) }
                }
            }
        }
        // Right content: fixed background box art with a full-pane opaque panel
        Box(Modifier.weight(1f).fillMaxHeight()) {
            // Background image stays fixed
            val bgAlpha = 0.25f
            if (boxArt != null) {
                Image(
                    bitmap = boxArt!!,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().alpha(bgAlpha)
                )
            } else if (appIcon != null) {
                // Show app icon as a fallback in the visible top area (avoid being covered by the bottom panel)
                Box(modifier = Modifier.fillMaxSize()) {
                    Image(
                        bitmap = appIcon!!,
                        contentDescription = null,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 48.dp)
                            .size(96.dp)
                            .alpha(0.5f)
                    )
                }
            }

            // Opaque foreground panel occupying the bottom third of the pane (starting ~2/3 up)
            Column(modifier = Modifier.fillMaxSize()) {
                // Config-empty notice at the top of the page (like Home)
                if (isConfigEmpty) {
                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            tonalElevation = 2.dp,
                            shadowElevation = 0.dp,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "Welcome!",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        "Your configuration looks empty. Open Settings to connect your launchers and set IGDB credentials.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                                    )
                                }
                                Button(onClick = { onOpenSettings?.invoke() }) {
                                    Text("Open Settings")
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                Surface(
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth().weight(2f)
                ) {
                    val detailsScroll = rememberScrollState()
                    if (selected == null) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "Select a game to see details",
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = "Choose a game from the list on the left. If something is missing try reloading your libraries.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                                )
                                Spacer(Modifier.height(16.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Button(onClick = { onReloadLibraries?.invoke() }) {
                                        Text("Reload Library")
                                    }
                                }
                            }
                        }
                    } else {
                        Row(modifier = Modifier.fillMaxSize().padding(24.dp)) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .verticalScroll(detailsScroll)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    val name = selected?.name ?: "Select a game"
                                    Text(
                                        text = name,
                                        style = MaterialTheme.typography.headlineSmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f)
                                    )
                                    // Favorite star toggle
                                    val dirPath = selected?.dirPath ?: ""
                                    val isFav = favorites.contains(dirPath)
                                    IconButton(
                                        onClick = {
                                            if (dirPath.isNotBlank()) {
                                                val nowFav = FavoritesStore.toggle(dirPath)
                                                favorites = if (nowFav) favorites + dirPath else favorites - dirPath
                                            }
                                        },
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Text(text = if (isFav) "★" else "☆", fontSize = 28.sp)
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Button(
                                        onClick = {
                                            val e = selected
                                            if (e != null) {
                                                scope.launch {
                                                    val updated = withContext(Dispatchers.IO) {
                                                        UserDataStore.recordPlay(e.name)
                                                    }
                                                    playStats = updated
                                                    launchGame(e)
                                                }
                                            }
                                        },
                                        enabled = !isRunning
                                    ) {
                                        Text(if (isRunning) "Running" else "Play")
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                if (isInfoLoading) {
                                    ScrollingBoxesLoader()
                                } else {
                                    // Genres chips row
                                    val genres = gameInfo?.genres.orEmpty()
                                    if (genres.isNotEmpty()) {
                                        GenreChipsRow(genres = genres)
                                        Spacer(Modifier.height(10.dp))
                                    }
                                    // Game description from IGDB
                                    val desc = gameInfo?.description.orEmpty()
                                    if (desc.isNotBlank()) {
                                        Text(
                                            text = desc,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)
                                        )
                                    }
                                }
                            }
                            if (detailsScroll.maxValue > 0) {
                                Spacer(Modifier.width(8.dp))
                                VerticalScrollbar(
                                    adapter = rememberScrollbarAdapter(detailsScroll),
                                    modifier = Modifier.fillMaxHeight()
                                )
                            }
                            Spacer(Modifier.width(16.dp))
                            // Right-side summary column (Play Stats + actions)
                            Column(modifier = Modifier.width(260.dp).fillMaxHeight()) {
                                PlayStatsCard(stats = playStats, dateTimeFormatPattern = datePattern)
                                Spacer(Modifier.weight(1f))
                                val sel = selected
                                // Show Run Configuration for all titles; disable options for Battle.net
                                if (sel != null) {
                                    OutlinedButton(
                                        onClick = { showRunConfigDialog = true },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Run Configuration")
                                    }
                                    if (showRunConfigDialog) {
                                        val current = sel
                                        if (current != null) {
                                            val disabled = current.launcher == LauncherType.BATTLENET
                                            var exeText by remember(current.dirPath) {
                                                mutableStateOf(
                                                    ExeSelectionStore.get(current.dirPath) ?: current.exePath
                                                )
                                            }
                                            var argsText by remember(current.dirPath) {
                                                mutableStateOf(RunArgsStore.get(current.dirPath) ?: "")
                                            }
                                            var useArgs by remember(current.dirPath) {
                                                mutableStateOf(!RunArgsStore.get(current.dirPath).isNullOrBlank())
                                            }
                                            AlertDialog(
                                                onDismissRequest = { showRunConfigDialog = false },
                                                title = {
                                                    Text("Run Configuration")
                                                },
                                                text = {
                                                    Column(modifier = Modifier.fillMaxWidth()) {
                                                        if (disabled) {
                                                            WarningBanner(
                                                                message = "Editing run options is not available for Battle.net games.",
                                                                modifier = Modifier.fillMaxWidth()
                                                            )
                                                            Spacer(Modifier.height(12.dp))
                                                        }
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            OutlinedTextField(
                                                                value = exeText,
                                                                onValueChange = { exeText = it },
                                                                label = { Text("Executable Path") },
                                                                singleLine = true,
                                                                enabled = !disabled,
                                                                modifier = Modifier.weight(1f)
                                                            )
                                                            Spacer(Modifier.width(8.dp))
                                                            Button(
                                                                onClick = {
                                                                    try {
                                                                        val chooser = javax.swing.JFileChooser()
                                                                        chooser.fileSelectionMode =
                                                                            javax.swing.JFileChooser.FILES_ONLY
                                                                        chooser.fileFilter =
                                                                            javax.swing.filechooser.FileNameExtensionFilter(
                                                                                "Executable files (*.exe)",
                                                                                "exe"
                                                                            )
                                                                        // Preselect to current exe or its directory
                                                                        try {
                                                                            val currentPath = exeText
                                                                            val currentFile =
                                                                                if (currentPath.isNotBlank()) java.io.File(
                                                                                    currentPath
                                                                                ) else null
                                                                            val initialDir = when {
                                                                                currentFile != null && currentFile.exists() && currentFile.isDirectory -> currentFile
                                                                                currentFile != null && currentFile.exists() && currentFile.isFile -> currentFile.parentFile
                                                                                else -> java.io.File(current.dirPath)
                                                                            }
                                                                            if (initialDir != null && initialDir.exists() && initialDir.isDirectory) {
                                                                                chooser.currentDirectory = initialDir
                                                                            }
                                                                            if (currentFile != null && currentFile.exists() && currentFile.isFile) {
                                                                                chooser.selectedFile = currentFile
                                                                            }
                                                                        } catch (_: Throwable) {
                                                                        }
                                                                        val ret = chooser.showOpenDialog(null)
                                                                        if (ret == javax.swing.JFileChooser.APPROVE_OPTION) {
                                                                            exeText = chooser.selectedFile.absolutePath
                                                                        }
                                                                    } catch (_: Throwable) {
                                                                    }
                                                                },
                                                                enabled = !disabled
                                                            ) {
                                                                Text("Browse…")
                                                            }
                                                        }
                                                        Spacer(Modifier.height(10.dp))
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            Checkbox(
                                                                checked = useArgs,
                                                                onCheckedChange = { useArgs = it },
                                                                enabled = !disabled
                                                            )
                                                            Spacer(Modifier.width(6.dp))
                                                            Text(
                                                                "Use Custom Launch Options",
                                                                style = MaterialTheme.typography.labelLarge,
                                                                color = if (disabled) MaterialTheme.colorScheme.onSurface.copy(
                                                                    alpha = 0.38f
                                                                ) else MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                        }
                                                        if (useArgs) {
                                                            Spacer(Modifier.height(12.dp))
                                                            OutlinedTextField(
                                                                value = argsText,
                                                                onValueChange = { argsText = it },
                                                                label = { Text("Command-line arguments") },
                                                                singleLine = false,
                                                                enabled = !disabled,
                                                                modifier = Modifier.fillMaxWidth()
                                                            )
                                                        }
                                                    }
                                                },
                                                confirmButton = {
                                                    TextButton(
                                                        onClick = {
                                                            if (exeText.isNotBlank()) {
                                                                ExeSelectionStore.put(current.dirPath, exeText)
                                                            }
                                                            if (useArgs) {
                                                                RunArgsStore.put(current.dirPath, argsText)
                                                            } else {
                                                                // Clear saved args when disabled
                                                                RunArgsStore.put(current.dirPath, "")
                                                            }
                                                            showRunConfigDialog = false
                                                        },
                                                        enabled = !disabled
                                                    ) { Text("Save") }
                                                },
                                                dismissButton = {
                                                    TextButton(onClick = {
                                                        showRunConfigDialog = false
                                                    }) { Text("Cancel") }
                                                }
                                            )
                                        } else {
                                            showRunConfigDialog = false
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LauncherHeader(launcher: LauncherType, count: Int, expanded: Boolean, onToggle: () -> Unit) {
    val icon = when (launcher) {
        LauncherType.STEAM -> "⚙"
        LauncherType.EA -> "EA"
        LauncherType.BATTLENET -> "BN"
        LauncherType.UBISOFT -> "Ubi"
        LauncherType.CUSTOM -> "★"
    }
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onToggle() }.padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(if (expanded) "▾" else "▸", modifier = Modifier.width(18.dp))
        Spacer(Modifier.width(4.dp))
        Text(icon)
        Spacer(Modifier.width(8.dp))
        Text(
            text = when (launcher) {
                LauncherType.STEAM -> "Steam"
                LauncherType.EA -> "EA App"
                LauncherType.BATTLENET -> "Battle.net"
                LauncherType.UBISOFT -> "Ubisoft Connect"
                LauncherType.CUSTOM -> "Custom"
            } + " (" + count + ")",
            style = MaterialTheme.typography.titleMedium
        )
    }
}

@Composable
private fun GameListItem(entry: GameEntry, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent
    Row(
        modifier = Modifier.fillMaxWidth().background(bg).clickable { onClick() }
            .padding(start = 34.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Load system icon off the UI thread to avoid stutter
        var iconBitmap by remember(entry.exePath) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
        LaunchedEffect(entry.exePath) {
            iconBitmap = withContext(Dispatchers.IO) {
                net.canyonwolf.sparklauncher.ui.util.SystemIconLoader.getIcon(entry.exePath)
            }
        }
        if (iconBitmap != null) {
            Image(
                bitmap = iconBitmap!!,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
        } else {
            Text("■")
        }
        Spacer(Modifier.width(8.dp))
        // Show directory name as game name (already in entry.name)
        Text(entry.name, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}


private fun launchGame(entry: GameEntry) {
    try {
        // Use per-game override if present
        val exePath = ExeSelectionStore.get(entry.dirPath)?.takeIf { it.isNotBlank() } ?: entry.exePath
        val args = RunArgsStore.get(entry.dirPath).orEmpty()

        // If exePath is a protocol URL (e.g., battlenet://game/pinta), open via Desktop to invoke handler
        if (exePath.contains("://")) {
            try {
                val uri = java.net.URI(exePath)
                if (java.awt.Desktop.isDesktopSupported()) {
                    val d = java.awt.Desktop.getDesktop()
                    if (d.isSupported(java.awt.Desktop.Action.BROWSE)) {
                        d.browse(uri)
                        return
                    }
                }
            } catch (_: Throwable) {
                // fall through to process builder attempt below
            }
        }
        val exe = java.io.File(exePath)
        val dir = java.io.File(entry.dirPath)
        val command = mutableListOf(exe.absolutePath)
        command.addAll(parseArgsString(args))
        val pb = ProcessBuilder(command)
        if (dir.exists()) pb.directory(dir)
        pb.start()
    } catch (_: Throwable) {
        // ignore launch errors for now
    }
}

private fun parseArgsString(s: String): List<String> {
    if (s.isBlank()) return emptyList()
    val tokens = mutableListOf<String>()
    val sb = StringBuilder()
    var inQuotes = false
    var i = 0
    while (i < s.length) {
        when (val c = s[i]) {
            '"' -> {
                inQuotes = !inQuotes
            }

            ' ' -> {
                if (inQuotes) sb.append(c) else {
                    if (sb.isNotEmpty()) {
                        tokens.add(sb.toString())
                        sb.setLength(0)
                    }
                }
            }

            else -> sb.append(c)
        }
        i++
    }
    if (sb.isNotEmpty()) tokens.add(sb.toString())
    return tokens
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GenreChipsRow(genres: List<String>) {
    if (genres.isEmpty()) return
    androidx.compose.foundation.layout.FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        genres.forEach { g ->
            GenreChip(g)
        }
    }
}

@Composable
private fun ScrollingBoxesLoader() {
    val scrollState = rememberScrollState()
    LaunchedEffect(Unit) {
        while (true) {
            // auto-scroll back and forth
            val max = scrollState.maxValue
            var i = 0
            while (i <= max) {
                scrollState.scrollTo(i)
                kotlinx.coroutines.delay(12)
                i += 8
            }
            var j = max
            while (j >= 0) {
                scrollState.scrollTo(j)
                kotlinx.coroutines.delay(12)
                j -= 8
            }
        }
    }
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .horizontalScroll(scrollState),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(24) { idx ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier
                        .width((90 + (idx % 4) * 20).dp)
                        .height(40.dp)
                ) { }
                Spacer(Modifier.width(12.dp))
            }
        }
    }
}

@Composable
private fun GenreChip(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun PlayStatsCard(stats: UserDataStore.PlayStat?, dateTimeFormatPattern: String = "MMM d, yyyy h:mm a") {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 2.dp,
        shadowElevation = 0.dp,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text("Your Play Stats", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            // Plays count
            Text(
                "Plays",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            Text((stats?.plays ?: 0).toString(), style = MaterialTheme.typography.displaySmall)
            Spacer(Modifier.height(12.dp))
            // Last played
            Text(
                "Last Played",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            val last = stats?.lastplayed.orEmpty()
            val shown = remember(last, dateTimeFormatPattern) {
                try {
                    if (last.isBlank()) {
                        "Never"
                    } else {
                        val dt = java.time.LocalDateTime.parse(last)
                        val fmt = try {
                            java.time.format.DateTimeFormatter.ofPattern(dateTimeFormatPattern)
                        } catch (_: Throwable) {
                            java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a")
                        }
                        dt.format(fmt)
                    }
                } catch (_: Throwable) {
                    last
                }
            }
            Text(shown, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(8.dp))
            Text(
                "Tip: Stats update when you click Play.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}


private fun isProcessRunning(exePath: String): Boolean {
    try {
        val targetPath = exePath.trim().trim('"')
        val targetFile = try {
            java.nio.file.Paths.get(targetPath).fileName.toString()
        } catch (_: Throwable) {
            java.io.File(targetPath).name
        }
        val it = ProcessHandle.allProcesses().iterator()
        while (it.hasNext()) {
            val ph = it.next()
            try {
                val cmdOpt = ph.info().command()
                if (cmdOpt.isPresent) {
                    var cmd = cmdOpt.get()
                    cmd = cmd.trim().trim('"')
                    if (cmd.equals(targetPath, ignoreCase = true)) return true
                    val cmdFile = try {
                        java.nio.file.Paths.get(cmd).fileName.toString()
                    } catch (_: Throwable) {
                        java.io.File(cmd).name
                    }
                    if (cmdFile.equals(targetFile, ignoreCase = true)) return true
                }
            } catch (_: Throwable) {
                // ignore per-process access issues
            }
        }
    } catch (_: Throwable) {
        // ignore
    }
    return false
}


@Composable
private fun WarningBanner(message: String, modifier: Modifier = Modifier) {
    // Theme-adaptive, semi-transparent banner that remains visible on light and dark themes.
    val onSurface = MaterialTheme.colorScheme.onSurface
    val bg = onSurface.copy(alpha = 0.12f)        // subtle, transparent tint
    val border = onSurface.copy(alpha = 0.36f)    // clearer delineation
    val content = onSurface                       // high-contrast text/icon

    Surface(
        color = bg,
        tonalElevation = 0.dp,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
    ) {
        Box(
            Modifier
                .border(1.dp, border, RoundedCornerShape(8.dp))
                .padding(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = null,
                    tint = content.copy(alpha = 0.9f)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = message,
                    color = content,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
