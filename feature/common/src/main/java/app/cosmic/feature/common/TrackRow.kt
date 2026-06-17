package app.cosmic.feature.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.cosmic.core.db.entity.Track
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade

/**
 * Reusable row for tracks. Tap to play, long-press for context menu,
 * **swipe left to add to queue**, press-scale animation for tactile feedback.
 *
 * Album-art tile is a deterministic gradient (per-track-id seed) — gives
 * library lists more atmosphere without needing actual cover art loaded.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TrackRow(
    track: Track,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    onSwipeAddToQueue: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = tween(140),
        label = "rowScale",
    )

    val gradient = remember(track.id) { CosmicGradient.fromSeed(track.id, dark = true) }

    val content: @Composable () -> Unit = {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .combinedClickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = onClick,
                    onLongClick = onLongPress,
                )
                .scale(scale)
                .background(
                    if (highlighted) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.32f)
                    else Color.Transparent,
                )
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Brush.linearGradient(listOf(gradient.top, gradient.bottom))),
                contentAlignment = Alignment.Center,
            ) {
                val art = track.embeddedArtUri?.takeIf { it.isNotBlank() }
                if (art != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                            .data(art)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        Icons.Filled.MusicNote,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.85f),
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    track.title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (highlighted) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface,
                    modifier = if (highlighted) Modifier.basicMarquee(iterations = Int.MAX_VALUE) else Modifier,
                )
                val subtitle = listOfNotNull(track.artist, track.album).joinToString(" · ")
                if (subtitle.isNotEmpty()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (highlighted) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (trailing != null) {
                Spacer(Modifier.width(8.dp))
                trailing()
            } else {
                Spacer(Modifier.width(8.dp))
                Text(
                    formatDuration(track.durationMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (onSwipeAddToQueue == null) {
        content()
    } else {
        SwipeRowQueueable(
            onAddToQueue = onSwipeAddToQueue,
            modifier = modifier,
        ) { content() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeRowQueueable(
    onAddToQueue: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    // We watch `currentValue` instead of returning false from
    // `confirmValueChange`. The previous approach silently rejected the
    // state transition (false = "stay where you are"), and on some Material 3
    // builds that meant the callback fired without the queue add ever
    // running because the box was snapping back mid-frame.
    //
    // Cleaner pattern: let the box settle on EndToStart, react to that
    // value via LaunchedEffect, run the action, then reset() back to the
    // settled state. The user sees a clean "swipe → snap back" with the
    // action firing exactly once.
    val state = rememberSwipeToDismissBoxState(
        positionalThreshold = { totalDistance -> totalDistance * 0.4f },
    )
    LaunchedEffect(state.currentValue) {
        if (state.currentValue == SwipeToDismissBoxValue.EndToStart) {
            onAddToQueue()
            state.reset()
        }
    }
    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            // Reveal behind the row: a tonal background with "Add to queue"
            // label + icon, aligned right (the side the user is swiping toward).
            val progress = state.progress
            val visible = state.dismissDirection == SwipeToDismissBoxValue.EndToStart && progress > 0.05f
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        if (visible) MaterialTheme.colorScheme.primaryContainer
                        else Color.Transparent,
                    )
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                if (visible) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End,
                    ) {
                        Text(
                            "Add to queue",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Spacer(Modifier.width(10.dp))
                        Icon(
                            Icons.AutoMirrored.Filled.QueueMusic,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }
        },
        content = { content() },
    )
}

internal fun formatDuration(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}
