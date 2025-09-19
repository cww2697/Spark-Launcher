package net.canyonwolf.sparklauncher.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import net.canyonwolf.sparklauncher.data.GameEntry
import net.canyonwolf.sparklauncher.data.LauncherType
import net.canyonwolf.sparklauncher.ui.util.BoxArtFetcher

private suspend fun smoothScrollToItem(state: LazyListState, targetIndex: Int) {
    // Ensure there is content
    val total = state.layoutInfo.totalItemsCount
    if (total <= 0) return

    // Bound target to available items
    val boundedTarget = targetIndex.coerceIn(0, total - 1)

    // Decide direction based on current first visible index
    var dir = when {
        boundedTarget > state.firstVisibleItemIndex -> 1
        boundedTarget < state.firstVisibleItemIndex -> -1
        else -> 0
    }
    if (dir == 0) return

    // We'll scroll by approximately one card per step using pixel deltas derived from layout info.
    // This avoids internal re-targeting issues in animateScrollToItem on Desktop that can halt mid-way.
    var safety = 0
    while (true) {
        val info = state.layoutInfo
        val firstIndex = info.visibleItemsInfo.firstOrNull()?.index ?: state.firstVisibleItemIndex
        val firstOffset = state.firstVisibleItemScrollOffset

        // Stop if we've reached or passed the target depending on direction
        if ((dir > 0 && firstIndex >= boundedTarget) || (dir < 0 && firstIndex <= boundedTarget)) {
            break
        }

        // Estimate per-item spacing in pixels based on visible items offsets
        val items = info.visibleItemsInfo.sortedBy { it.index }
        val deltas = items.zip(items.drop(1)).map { (a, b) -> (b.offset - a.offset).toFloat() }
        val stepPx = (deltas.takeIf { it.isNotEmpty() }?.average()?.toFloat() ?: 200f) * dir

        // Perform animated scroll by the estimated delta
        state.animateScrollBy(stepPx)

        // If after the animation we didn't move at all, bail out to avoid perceived freeze
        val newInfo = state.layoutInfo
        val newFirstIndex = newInfo.visibleItemsInfo.firstOrNull()?.index ?: state.firstVisibleItemIndex
        val newFirstOffset = state.firstVisibleItemScrollOffset
        if (newFirstIndex == firstIndex && newFirstOffset == firstOffset) {
            // As a fallback, try a small nudge using animateScrollBy again. If still no movement, break.
            state.animateScrollBy(80f * dir)
            val chkIndex = state.layoutInfo.visibleItemsInfo.firstOrNull()?.index ?: state.firstVisibleItemIndex
            val chkOffset = state.firstVisibleItemScrollOffset
            if (chkIndex == firstIndex && chkOffset == firstOffset) break
        }

        // Safety to avoid infinite loops in extreme cases
        safety++
        if (safety > 20) break

        // Recompute direction relative to target after progress
        dir = when {
            boundedTarget > state.firstVisibleItemIndex -> 1
            boundedTarget < state.firstVisibleItemIndex -> -1
            else -> 0
        }
        if (dir == 0) break
    }
}

@Composable
fun HomeScreen(
    entries: List<GameEntry>,
    onOpenGame: (GameEntry) -> Unit,
    isConfigEmpty: Boolean = false,
    onOpenSettings: (() -> Unit)? = null,
) {
    // Observe metadata loading to recompute groups when rebuild finishes
    val metadataLoading by BoxArtFetcher.metadataLoading.collectAsState()
    val imagesPrefetching by BoxArtFetcher.imagesPrefetching.collectAsState()
    // Group entries by genre using cached IGDB metadata (no network). Fallback to "Uncategorized".
    val genreGroups: Map<String, List<GameEntry>> = remember(entries, metadataLoading) {
        val map = linkedMapOf<String, MutableList<GameEntry>>()
        fun add(genre: String, e: GameEntry) {
            map.getOrPut(genre) { mutableListOf() }.add(e)
        }
        entries.forEach { e ->
            val normalized = e.name.lowercase().trim().replace(Regex("\\s+"), " ")
            val queryName = if (e.launcher == LauncherType.BATTLENET && normalized == "call of duty") {
                "Call of Duty: Black Ops 6"
            } else e.name
            val info = BoxArtFetcher.getGameInfo(e.launcher, queryName)
            val genres = info?.genres?.filter { it.isNotBlank() } ?: emptyList()
            if (genres.isEmpty()) {
                add("Uncategorized", e)
            } else {
                genres.forEach { g -> add(g, e) }
            }
        }
        // Convert to immutable lists
        map.mapValues { it.value.toList() }
    }
    val genreOrder = remember(genreGroups) { genreGroups.keys.sorted() }

    if ((metadataLoading || imagesPrefetching) && entries.isNotEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text("Preparing your home screen…", color = MaterialTheme.colorScheme.onBackground)
            }
        }
    } else {
        val vScroll = rememberScrollState()
        Box(Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(vScroll)) {
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
                            androidx.compose.material3.Button(onClick = { onOpenSettings?.invoke() }) {
                                Text("Open Settings")
                            }
                        }
                    }
                }
                genreOrder.forEach { genre ->
                    val list = genreGroups[genre].orEmpty()
                    if (list.isEmpty()) return@forEach
                    CarouselSection(title = genre, items = list, onOpenGame = onOpenGame)
                    Spacer(Modifier.height(24.dp))
                }
            }
            if (vScroll.maxValue > 0) {
                VerticalScrollbar(
                    adapter = rememberScrollbarAdapter(vScroll),
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight()
                )
            }
        }
    }
}

@Composable
private fun CarouselSection(title: String, items: List<GameEntry>, onOpenGame: (GameEntry) -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant, // Darker than background in dark theme
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp, // Avoid yellow tint from surfaceTint (primary)
        shadowElevation = 6.dp,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            )

            val listState = rememberLazyListState()
            val scope = rememberCoroutineScope()
            Box(Modifier.fillMaxWidth().height(240.dp)) {
                LazyRow(
                    state = listState,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    modifier = Modifier.fillMaxSize().padding(horizontal = 48.dp)
                ) {
                    items(items, key = { it.exePath }) { entry ->
                        Box(Modifier.padding(6.dp)) {
                            GameCarouselCard(entry = entry, onClick = { onOpenGame(entry) })
                        }
                    }
                }

                val canScrollLeft by remember(items.size) {
                    derivedStateOf {
                        (listState.firstVisibleItemIndex > 0) || (listState.firstVisibleItemScrollOffset > 0)
                    }
                }
                val canScrollRight by remember(items.size) {
                    derivedStateOf {
                        val info = listState.layoutInfo
                        val last = info.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf false
                        val viewportEnd = info.viewportEndOffset
                        val lastEnd = last.offset + last.size
                        (last.index < items.size - 1) || (last.index == items.size - 1 && lastEnd > viewportEnd)
                    }
                }

                // Left circular arrow with elevation and ripple
                if (canScrollLeft) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .size(40.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape),
                        shape = androidx.compose.foundation.shape.CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        tonalElevation = 2.dp,
                        shadowElevation = 6.dp,
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                        )
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable {
                                    if (!listState.isScrollInProgress) {
                                        val idx = listState.firstVisibleItemIndex
                                        val offset = listState.firstVisibleItemScrollOffset
                                        scope.launch {
                                            if (idx == 0 && offset > 0) {
                                                listState.animateScrollBy(-offset.toFloat())
                                            } else {
                                                val target = (idx - 1).coerceAtLeast(0)
                                                smoothScrollToItem(listState, target)
                                            }
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("❮")
                        }
                    }
                }

                // Right circular arrow with elevation and ripple
                if (canScrollRight) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .size(40.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape),
                        shape = androidx.compose.foundation.shape.CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        tonalElevation = 2.dp,
                        shadowElevation = 6.dp,
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                        )
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable {
                                    if (!listState.isScrollInProgress) {
                                        val target = (listState.firstVisibleItemIndex + 1).coerceAtMost(
                                            (items.size - 1).coerceAtLeast(0)
                                        )
                                        scope.launch { smoothScrollToItem(listState, target) }
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("❯")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GameCarouselCard(entry: GameEntry, onClick: () -> Unit) {
    var image by remember(entry) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var iconBitmap by remember(entry.exePath) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    LaunchedEffect(entry) {
        val queryName = entry.name.let { n ->
            val normalized = n.lowercase().trim().replace(Regex("\\s+"), " ")
            if (entry.launcher == LauncherType.BATTLENET && normalized == "call of duty") "Call of Duty: Black Ops 6" else n
        }
        image = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            BoxArtFetcher.getBoxArt(queryName)
        }
        if (image == null) {
            iconBitmap = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                net.canyonwolf.sparklauncher.ui.util.SystemIconLoader.getIcon(entry.exePath)
            }
        }
    }

    val cardWidth = 180.dp
    val cardHeight = 240.dp

    Surface(
        tonalElevation = 3.dp,
        shadowElevation = 6.dp,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .size(cardWidth, cardHeight)
            .clickable(onClick = onClick)
    ) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant)) {
            when {
                image != null -> {
                    Image(
                        bitmap = image!!,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                iconBitmap != null -> {
                    Image(
                        bitmap = iconBitmap!!,
                        contentDescription = null,
                        modifier = Modifier.align(Alignment.Center).size(72.dp)
                    )
                }

                else -> {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant))
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    tonalElevation = 1.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                ) {
                    EllipsizedTextWithHover(
                        text = entry.name,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        textStyle = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}


@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun EllipsizedTextWithHover(
    text: String,
    modifier: Modifier = Modifier,
    textStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyMedium
) {
    var overflowed by remember(text, textStyle) { mutableStateOf(false) }

    if (!overflowed) {
        Box(modifier = modifier) {
            Text(
                text = text,
                style = textStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { layout -> overflowed = layout.hasVisualOverflow }
            )
        }
    } else {
        val tooltipState = rememberBasicTooltipState()
        val positionProvider = AboveStartPopupPositionProvider
        BasicTooltipBox(
            positionProvider = positionProvider,
            tooltip = {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    tonalElevation = 3.dp,
                    shadowElevation = 10.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                ) {
                    Box(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp).widthIn(max = 420.dp)) {
                        Text(
                            text = text,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = Int.MAX_VALUE,
                            overflow = TextOverflow.Clip
                        )
                    }
                }
            },
            state = tooltipState,
            modifier = modifier
        ) {
            Text(
                text = text,
                style = textStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { layout -> overflowed = layout.hasVisualOverflow }
            )
        }
    }
}

private object AboveStartPopupPositionProvider : androidx.compose.ui.window.PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: androidx.compose.ui.unit.IntRect,
        windowSize: androidx.compose.ui.unit.IntSize,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
        popupContentSize: androidx.compose.ui.unit.IntSize
    ): androidx.compose.ui.unit.IntOffset {
        val x = anchorBounds.left
        val gapPx = 6
        val y = (anchorBounds.top - popupContentSize.height - gapPx).coerceAtLeast(0)
        return androidx.compose.ui.unit.IntOffset(x, y)
    }
}
