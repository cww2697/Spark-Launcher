package net.canyonwolf.sparklauncher.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.canyonwolf.sparklauncher.data.GameEntry
import net.canyonwolf.sparklauncher.data.LauncherType
import net.canyonwolf.sparklauncher.data.UserDataStore
import net.canyonwolf.sparklauncher.ui.util.BoxArtFetcher

@Composable
fun LibraryScreen(
    entries: List<GameEntry>,
    preselected: GameEntry? = null,
    onReloadLibraries: (() -> Unit)? = null,
    onRebuildCaches: (() -> Unit)? = null
) {
    val grouped: Map<LauncherType, List<GameEntry>> = remember(entries) {
        entries.groupBy { it.launcher }
    }

    if (entries.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Your library is empty",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Try reloading your libraries or rebuilding caches.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    androidx.compose.material3.Button(onClick = { onReloadLibraries?.invoke() }) {
                        Text("Reload Library")
                    }
                }
            }
        }
        return
    }

    // Expand/collapse states per launcher
    val launchersInOrder = listOf(LauncherType.STEAM, LauncherType.EA, LauncherType.BATTLENET, LauncherType.UBISOFT)
    val expandedState = remember { mutableStateMapOf<LauncherType, Boolean>() }
    launchersInOrder.forEach { lt -> if (expandedState[lt] == null) expandedState[lt] = true }

    var selected by remember(entries) { mutableStateOf<GameEntry?>(preselected) }
    var boxArt by remember(selected) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var gameInfo by remember(selected) { mutableStateOf<BoxArtFetcher.GameInfo?>(null) }
    // Local loader state for on-demand fetch when metadata is missing
    var isInfoLoading by remember(selected) { mutableStateOf(false) }
    // System app icon as fallback when no box art exists
    var appIcon by remember(selected) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    // Play stats for the selected game
    var playStats by remember(selected) { mutableStateOf<UserDataStore.PlayStat?>(null) }
    val scope = rememberCoroutineScope()

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
                        if (selected === sel) {
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

    Row(Modifier.fillMaxSize()) {
        // Left navigation list
        Surface(tonalElevation = 1.dp, modifier = Modifier.width(300.dp).fillMaxHeight()) {
            LazyColumn(Modifier.fillMaxSize().padding(vertical = 8.dp)) {
                launchersInOrder.forEach { launcher ->
                    val list = grouped[launcher].orEmpty()
                    if (list.isEmpty()) return@forEach

                    item(key = "header-${launcher.name}") {
                        LauncherHeader(
                            launcher = launcher,
                            count = list.size,
                            expanded = expandedState[launcher] == true,
                            onToggle = { expandedState[launcher] = !(expandedState[launcher] ?: true) }
                        )
                    }
                    if (expandedState[launcher] == true) {
                        items(list, key = { "${it.launcher}:${it.exePath}" }) { e ->
                            GameListItem(
                                entry = e,
                                selected = selected?.exePath == e.exePath,
                                onClick = { selected = e }
                            )
                        }
                    }
                    item(key = "divider-${launcher.name}") { Divider(thickness = 1.dp) }
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
                                    text = "Choose a game from the list on the left. If something is missing, try reloading your libraries or rebuilding caches.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                                )
                                Spacer(Modifier.height(16.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    androidx.compose.material3.Button(onClick = { onReloadLibraries?.invoke() }) {
                                        Text("Reload Library")
                                    }
                                    androidx.compose.material3.OutlinedButton(onClick = { onRebuildCaches?.invoke() }) {
                                        Text("Rebuild Caches")
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
                                    androidx.compose.material3.Button(
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
                                        }
                                    ) {
                                        Text("Play")
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
                            // Right-side stats card
                            Box(modifier = Modifier.width(260.dp).fillMaxHeight()) {
                                PlayStatsCard(playStats)
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
        val exe = java.io.File(entry.exePath)
        val dir = java.io.File(entry.dirPath)
        val pb = ProcessBuilder(exe.absolutePath)
        if (dir.exists()) pb.directory(dir)
        pb.start()
    } catch (_: Throwable) {
        // ignore launch errors for now
    }
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
private fun PlayStatsCard(stats: UserDataStore.PlayStat?) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 2.dp,
        shadowElevation = 0.dp,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
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
            val shown = remember(last) {
                try {
                    if (last.isBlank()) {
                        "Never"
                    } else {
                        val dt = java.time.LocalDateTime.parse(last)
                        dt.format(java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a"))
                    }
                } catch (_: Throwable) {
                    last
                }
            }
            Text(shown, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.weight(1f))
            Text(
                "Tip: Stats update when you click Play.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

