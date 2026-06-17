package app.cosmic.feature.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.cosmic.core.db.entity.Track
import kotlinx.coroutines.launch

/**
 * Modal bottom sheet of actions for one track. Shown on long-press of a
 * track row anywhere in the app. Callers wire each action to whatever
 * makes sense in their viewmodel — this composable is presentation-only.
 *
 * Design intent: short, one-tap actions live at the top. Slow / destructive
 * actions are below the divider. Add-to-playlist is the most common ask
 * so it's surfaced first.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackContextMenu(
    track: Track,
    onDismiss: () -> Unit,
    onPlayNow: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onTag: () -> Unit,
    onDetails: () -> Unit,
    onDelete: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    fun close(action: () -> Unit) {
        action()
        scope.launch {
            sheetState.hide()
            onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
            // Header with title + artist
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(track.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val sub = listOfNotNull(track.artist, track.album).joinToString(" · ")
                    if (sub.isNotEmpty()) {
                        Text(sub, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            HorizontalDivider()
            ActionRow(Icons.Filled.PlayArrow, "Play now") { close(onPlayNow) }
            ActionRow(Icons.AutoMirrored.Filled.PlaylistPlay, "Play next") { close(onPlayNext) }
            ActionRow(Icons.AutoMirrored.Filled.QueueMusic, "Add to queue") { close(onAddToQueue) }
            ActionRow(Icons.AutoMirrored.Filled.PlaylistAdd, "Add to playlist…") { close(onAddToPlaylist) }
            HorizontalDivider()
            ActionRow(Icons.Filled.Label, "Tag…") { close(onTag) }
            ActionRow(Icons.Filled.Info, "Track details") { close(onDetails) }
            HorizontalDivider()
            ActionRow(
                Icons.Filled.Delete,
                "Remove from device",
                tint = MaterialTheme.colorScheme.error,
            ) { close(onDelete) }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    label: String,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        Icon(icon, contentDescription = null, tint = tint)
        Spacer(Modifier.width(20.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = tint)
    }
}
