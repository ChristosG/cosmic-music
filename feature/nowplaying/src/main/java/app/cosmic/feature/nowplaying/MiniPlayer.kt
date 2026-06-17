package app.cosmic.feature.nowplaying

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay

@Composable
fun MiniPlayer(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    viewModel: NowPlayingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AnimatedVisibility(
        visible = state.hasTrack,
        enter = slideInVertically { it },
        exit = slideOutVertically { it },
        modifier = modifier,
    ) {
        Surface(
            tonalElevation = 3.dp,
            shadowElevation = 6.dp,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth().clickable { onClick() },
        ) {
            Column {
                // Tick a 1Hz local timer so the progress bar moves without churning the controller.
                var ticked by remember { mutableLongStateOf(state.positionMs) }
                LaunchedEffect(state.isPlaying, state.mediaId) {
                    while (state.isPlaying) {
                        ticked = viewModel.currentPositionMs()
                        delay(500)
                    }
                    ticked = state.positionMs
                }
                val fraction = if (state.durationMs > 0) {
                    (ticked.coerceAtLeast(0L).toFloat() / state.durationMs).coerceIn(0f, 1f)
                } else 0f
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.Transparent,
                    drawStopIndicator = {},
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val gradient = remember(state.mediaId) {
                        app.cosmic.feature.common.CosmicGradient.fromString(
                            state.mediaId ?: "default", dark = true,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                androidx.compose.ui.graphics.Brush.linearGradient(
                                    listOf(gradient.top, gradient.bottom),
                                ),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        val art = state.artworkUri?.takeIf { it.isNotBlank() }
                        if (art != null) {
                            coil3.compose.AsyncImage(
                                model = art,
                                contentDescription = stringResource(R.string.album_art),
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                            )
                        } else {
                            Icon(
                                Icons.Filled.MusicNote,
                                contentDescription = stringResource(R.string.album_art),
                                tint = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.85f),
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            state.title ?: stringResource(R.string.nothing_playing),
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (state.artist != null) {
                            Text(
                                state.artist!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    IconButton(onClick = { viewModel.playPause() }) {
                        Icon(
                            if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (state.isPlaying)
                                stringResource(R.string.pause) else stringResource(R.string.play),
                        )
                    }
                    IconButton(onClick = { viewModel.next() }, enabled = state.hasNext) {
                        Icon(Icons.Filled.SkipNext, contentDescription = stringResource(R.string.next))
                    }
                }
            }
        }
    }
}
