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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
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
) {
    // Observe metadata loading to recompute groups when rebuild finishes
    val metadataLoading by BoxArtFetcher.metadataLoading.collectAsState()
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

    if (metadataLoading && entries.isNotEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text("Loading game metadata…", color = MaterialTheme.colorScheme.onBackground)
            }
        }
    } else {
        val vScroll = rememberScrollState()
        Box(Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(vScroll)) {
                genreOrder.forEach { genre ->
                    val list = genreGroups[genre].orEmpty()
                    if (list.isEmpty()) return@forEach

                    Text(
                        text = genre,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                    )

                    // Netflix-style carousel with arrow controls
                    val listState = rememberLazyListState()
                    val scope = rememberCoroutineScope()
                    Box(Modifier.fillMaxWidth().height(240.dp)) {
                        LazyRow(
                            state = listState,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize().padding(horizontal = 36.dp)
                        ) {
                            items(list, key = { it.exePath }) { entry ->
                                GameCarouselCard(entry = entry, onClick = { onOpenGame(entry) })
                            }
                        }

                        // Determine when side scrollers are needed
                        val canScrollLeft by remember(list.size) {
                            derivedStateOf {
                                // Show left scroller if we've scrolled past the start of the list
                                // either by moving to a later item or by offsetting the first item.
                                (listState.firstVisibleItemIndex > 0) || (listState.firstVisibleItemScrollOffset > 0)
                            }
                        }
                        val canScrollRight by remember(list.size) {
                            derivedStateOf {
                                val info = listState.layoutInfo
                                val last = info.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf false
                                val viewportEnd = info.viewportEndOffset
                                val lastEnd = last.offset + last.size
                                // Show right scroller if there are more items beyond the last visible index,
                                // or if the last item is only partially visible (its end exceeds the viewport end)
                                (last.index < list.size - 1) || (last.index == list.size - 1 && lastEnd > viewportEnd)
                            }
                        }

                        // Left thin vertical scroller
                        if (canScrollLeft) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterStart)
                                    .width(28.dp)
                                    .fillMaxHeight()
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
                                    .clickable {
                                        if (!listState.isScrollInProgress) {
                                            val idx = listState.firstVisibleItemIndex
                                            val offset = listState.firstVisibleItemScrollOffset
                                            scope.launch {
                                                if (idx == 0 && offset > 0) {
                                                    // If the very first item is partially visible, nudge it fully into view
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
                                Text("❮", color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.alpha(0.95f))
                            }
                        }

                        // Right thin vertical scroller
                        if (canScrollRight) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .width(28.dp)
                                    .fillMaxHeight()
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
                                    .clickable {
                                        if (!listState.isScrollInProgress) {
                                            val target = (listState.firstVisibleItemIndex + 1).coerceAtMost(
                                                (list.size - 1).coerceAtLeast(0)
                                            )
                                            scope.launch { smoothScrollToItem(listState, target) }
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("❯", color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.alpha(0.95f))
                            }
                        }
                    }

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
private fun GameCarouselCard(entry: GameEntry, onClick: () -> Unit) {
    // Load box art asynchronously using cached fetcher
    var image by remember(entry) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    LaunchedEffect(entry) {
        val queryName = entry.name.let { n ->
            val normalized = n.lowercase().trim().replace(Regex("\\s+"), " ")
            if (entry.launcher == LauncherType.BATTLENET && normalized == "call of duty") "Call of Duty: Black Ops 6" else n
        }
        image = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            BoxArtFetcher.getBoxArt(queryName)
        }
    }

    val cardWidth = 180.dp
    val cardHeight = 240.dp

    Surface(
        tonalElevation = 2.dp,
        shadowElevation = 4.dp,
        modifier = Modifier
            .size(cardWidth, cardHeight)
            .clickable(onClick = onClick)
    ) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant)) {
            if (image != null) {
                Image(
                    bitmap = image!!,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // Subtle placeholder
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant))
            }

            // Title overlay at bottom-right
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Text(
                    text = entry.name,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.CenterStart)
                )
            }
        }
    }
}
