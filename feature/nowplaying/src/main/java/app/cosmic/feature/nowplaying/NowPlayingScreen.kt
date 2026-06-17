package app.cosmic.feature.nowplaying

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.Lyrics
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.cosmic.core.player.QueueItem
import app.cosmic.core.player.RepeatMode
import app.cosmic.feature.common.CosmicGradient
import kotlinx.coroutines.delay

private enum class NowPlayingTab(val label: String) {
    TRACK("Track"),
    QUEUE("Queue"),
    LYRICS("Lyrics"),
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun NowPlayingScreen(
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NowPlayingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scrubOverride by viewModel.userScrubMs.collectAsStateWithLifecycle()
    val lyrics by viewModel.lyrics.collectAsStateWithLifecycle()
    val queue by viewModel.queue.collectAsStateWithLifecycle()
    var tab by remember { mutableStateOf(NowPlayingTab.TRACK) }

    // Local 1Hz tick for the scrubber while playing.
    var tickedPos by remember { mutableLongStateOf(state.positionMs) }
    LaunchedEffect(state.isPlaying, state.mediaId) {
        while (state.isPlaying) {
            tickedPos = viewModel.currentPositionMs()
            delay(500)
        }
        tickedPos = state.positionMs
    }
    val displayPos = scrubOverride ?: tickedPos

    // Per-track gradient backdrop seeded by mediaId. Recomputed only when
    // the track changes — `derivedStateOf` so we don't re-hash every frame.
    val gradient by remember(state.mediaId) {
        derivedStateOf {
            CosmicGradient.fromString(
                seed = state.mediaId ?: "default",
                dark = true,
            )
        }
    }

    // Track cumulative vertical drag on the screen surface. When the user
    // drags down past the threshold we collapse — same effect as tapping the
    // down-arrow at top-left, but feels native to the slide-up presentation.
    val dragY = remember { mutableFloatStateOf(0f) }
    val dragThreshold = 220f
    Surface(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = { dragY.floatValue = 0f },
                    onDragCancel = { dragY.floatValue = 0f },
                ) { _, dy ->
                    if (dy > 0) {
                        dragY.floatValue += dy
                        if (dragY.floatValue >= dragThreshold) {
                            dragY.floatValue = 0f
                            onCollapse()
                        }
                    } else {
                        // Upward drag resets accumulator; we only react to
                        // sustained downward gestures.
                        dragY.floatValue = (dragY.floatValue + dy).coerceAtLeast(0f)
                    }
                }
            },
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Backdrop: vertical gradient → fade to surface.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                gradient.top,
                                gradient.bottom,
                                MaterialTheme.colorScheme.background,
                            ),
                            startY = 0f,
                            endY = Float.POSITIVE_INFINITY,
                        ),
                    ),
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Top bar
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onCollapse) {
                        Icon(Icons.Filled.ArrowDownward, contentDescription = "Collapse")
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        when (tab) {
                            NowPlayingTab.TRACK -> "NOW PLAYING"
                            NowPlayingTab.QUEUE -> "UP NEXT"
                            NowPlayingTab.LYRICS -> "LYRICS"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                    Spacer(Modifier.weight(1f))
                    Spacer(Modifier.size(48.dp))
                }

                Spacer(Modifier.height(8.dp))

                // Hero area: art OR queue OR lyrics. Smooth crossfade between.
                AnimatedContent(
                    targetState = tab,
                    transitionSpec = {
                        fadeIn(tween(220)) togetherWith fadeOut(tween(180))
                    },
                    modifier = Modifier.weight(1f, fill = true),
                    label = "nowPlayingTab",
                ) { current ->
                    when (current) {
                        NowPlayingTab.TRACK -> AlbumArtHero(
                            artworkUri = state.artworkUri,
                            modifier = Modifier.fillMaxSize(),
                        )
                        NowPlayingTab.QUEUE -> QueuePane(
                            queue = queue,
                            currentIndex = state.currentIndex,
                            onJumpTo = { viewModel.jumpTo(it) },
                            onRemove = { viewModel.removeFromQueue(it) },
                            onMove = { from, to -> viewModel.moveQueueItem(from, to) },
                            modifier = Modifier.fillMaxSize(),
                        )
                        NowPlayingTab.LYRICS -> LyricsView(
                            lines = lyrics?.parsed ?: emptyList(),
                            plainTextFallback = lyrics?.plainText,
                            currentPositionMs = displayPos,
                            modifier = Modifier.fillMaxSize(),
                            onRefresh = { viewModel.refreshLyrics() },
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                // Track title + artist + album, marquee for long titles.
                TrackHeader(
                    title = state.title ?: "Nothing playing",
                    artist = state.artist,
                    album = state.album,
                )

                Spacer(Modifier.height(16.dp))

                // Scrubber
                ScrubberRow(
                    durationMs = state.durationMs.coerceAtLeast(1L),
                    displayPos = displayPos,
                    enabled = state.hasTrack,
                    onScrub = { viewModel.updateScrub(it) },
                    onCommit = { viewModel.commitScrub(it) },
                )

                Spacer(Modifier.height(8.dp))

                TransportRow(
                    state = state,
                    onShuffle = { viewModel.toggleShuffle() },
                    onPrev = { viewModel.previous() },
                    onPlayPause = { viewModel.playPause() },
                    onNext = { viewModel.next() },
                    onRepeat = { viewModel.cycleRepeat() },
                )

                Spacer(Modifier.height(16.dp))

                // Tab switcher
                TabSwitcher(
                    selected = tab,
                    onSelect = { tab = it },
                )
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun AlbumArtHero(artworkUri: String?, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .aspectRatio(1f)
                .shadow(
                    elevation = 32.dp,
                    shape = RoundedCornerShape(28.dp),
                    ambientColor = MaterialTheme.colorScheme.primary,
                    spotColor = MaterialTheme.colorScheme.primary,
                )
                .clip(RoundedCornerShape(28.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.surfaceContainerHigh,
                            MaterialTheme.colorScheme.surfaceContainerHighest,
                        ),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            val art = artworkUri?.takeIf { it.isNotBlank() }
            if (art != null) {
                coil3.compose.AsyncImage(
                    model = art,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    Icons.Filled.MusicNote,
                    contentDescription = null,
                    modifier = Modifier.size(108.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrackHeader(title: String, artist: String?, album: String?) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().basicMarquee(iterations = Int.MAX_VALUE),
        )
        val sub = listOfNotNull(artist, album).joinToString(" · ")
        if (sub.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = sub,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().basicMarquee(iterations = Int.MAX_VALUE),
            )
        }
    }
}

@Composable
private fun ScrubberRow(
    durationMs: Long,
    displayPos: Long,
    enabled: Boolean,
    onScrub: (Long) -> Unit,
    onCommit: (Long) -> Unit,
) {
    val pendingSeekMs = remember { mutableLongStateOf(0L) }
    Column(modifier = Modifier.fillMaxWidth()) {
        app.cosmic.feature.common.CosmicSlider(
            value = displayPos.coerceIn(0L, durationMs).toFloat(),
            onValueChange = {
                val ms = it.toLong()
                pendingSeekMs.longValue = ms
                onScrub(ms)
            },
            onValueChangeFinished = { onCommit(pendingSeekMs.longValue) },
            valueRange = 0f..durationMs.toFloat(),
            enabled = enabled,
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                formatDuration(displayPos),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                formatDuration(durationMs),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TransportRow(
    state: app.cosmic.core.player.PlaybackState,
    onShuffle: () -> Unit,
    onPrev: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onRepeat: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Shuffle / repeat are "ghost" buttons — bare icon, no chrome. Tinted
        // primary when active, dim onSurface when off. Reads as a state toggle
        // rather than a chunky tappable rectangle.
        IconButton(onClick = onShuffle) {
            Icon(
                Icons.Filled.Shuffle,
                contentDescription = "Shuffle",
                tint = if (state.shuffleEnabled) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            )
        }
        // Prev / next are soft tonal circles — they have weight (you can land
        // on them in the dark) but step back from the play button.
        FilledTonalIconButton(
            onClick = onPrev,
            enabled = state.hasPrevious,
            modifier = Modifier.size(56.dp),
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
        ) {
            Icon(
                Icons.Filled.SkipPrevious,
                contentDescription = "Previous",
                modifier = Modifier.size(30.dp),
            )
        }
        // Play/pause is the hero — primary-filled, larger, soft glow shadow.
        FilledTonalIconButton(
            onClick = onPlayPause,
            enabled = state.hasTrack,
            modifier = Modifier
                .size(80.dp)
                .shadow(
                    elevation = 18.dp,
                    shape = androidx.compose.foundation.shape.CircleShape,
                    ambientColor = MaterialTheme.colorScheme.primary,
                    spotColor = MaterialTheme.colorScheme.primary,
                ),
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            Icon(
                if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (state.isPlaying) "Pause" else "Play",
                modifier = Modifier.size(44.dp),
            )
        }
        FilledTonalIconButton(
            onClick = onNext,
            enabled = state.hasNext,
            modifier = Modifier.size(56.dp),
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
        ) {
            Icon(
                Icons.Filled.SkipNext,
                contentDescription = "Next",
                modifier = Modifier.size(30.dp),
            )
        }
        IconButton(onClick = onRepeat) {
            val (icon, active) = when (state.repeatMode) {
                RepeatMode.OFF -> Icons.Filled.Repeat to false
                RepeatMode.ALL -> Icons.Filled.Repeat to true
                RepeatMode.ONE -> Icons.Filled.RepeatOne to true
            }
            Icon(
                icon,
                contentDescription = "Repeat",
                tint = if (active) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            )
        }
    }
}

@Composable
private fun TabSwitcher(selected: NowPlayingTab, onSelect: (NowPlayingTab) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.6f),
        shape = RoundedCornerShape(28.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            NowPlayingTab.entries.forEach { t ->
                val active = t == selected
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(24.dp))
                        .clickable { onSelect(t) },
                    color = if (active) MaterialTheme.colorScheme.primary
                    else Color.Transparent,
                    shape = RoundedCornerShape(24.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            when (t) {
                                NowPlayingTab.TRACK -> Icons.Filled.MusicNote
                                NowPlayingTab.QUEUE -> Icons.AutoMirrored.Filled.QueueMusic
                                NowPlayingTab.LYRICS -> Icons.Filled.Lyrics
                            },
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = if (active) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QueuePane(
    queue: List<QueueItem>,
    currentIndex: Int,
    onJumpTo: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onMove: (from: Int, to: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (queue.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(
                "Queue is empty",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(lazyListState = listState) { from, to ->
        onMove(from.index, to.index)
    }
    LazyColumn(state = listState, modifier = modifier) {
        itemsIndexed(
            queue,
            key = { _, item -> item.mediaId.ifBlank { "i${item.index}" } },
        ) { _, item ->
            ReorderableItem(
                state = reorderState,
                key = item.mediaId.ifBlank { "i${item.index}" },
            ) { _ ->
                QueueRow(
                    item = item,
                    isCurrent = item.index == currentIndex,
                    onClick = { onJumpTo(item.index) },
                    onRemove = { onRemove(item.index) },
                    dragHandleModifier = Modifier.draggableHandle(),
                )
            }
        }
    }
}

@Composable
private fun QueueRow(
    item: QueueItem,
    isCurrent: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    dragHandleModifier: Modifier = Modifier,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isCurrent) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                else Color.Transparent,
            )
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Drag handle on the left (long-press to grab + drag to reorder).
        Icon(
            Icons.Filled.DragIndicator,
            contentDescription = "Drag to reorder",
            modifier = dragHandleModifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
        )
        Spacer(Modifier.size(8.dp))
        Icon(
            if (isCurrent) Icons.Filled.PlayArrow else Icons.Filled.MusicNote,
            contentDescription = null,
            tint = if (isCurrent) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
        Spacer(Modifier.size(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                item.title.ifBlank { "Track" },
                style = MaterialTheme.typography.bodyLarge,
                color = if (isCurrent) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val sub = listOfNotNull(item.artist, item.album).joinToString(" · ")
            if (sub.isNotEmpty()) {
                Text(
                    sub,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (!isCurrent) {
            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.Close, contentDescription = "Remove from queue")
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}
