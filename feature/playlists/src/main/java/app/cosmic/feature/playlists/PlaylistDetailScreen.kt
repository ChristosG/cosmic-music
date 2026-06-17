package app.cosmic.feature.playlists

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.cosmic.feature.common.TrackContextMenu
import app.cosmic.feature.common.TrackRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlaylistDetailViewModel = hiltViewModel(),
) {
    val playlist by viewModel.playlist.collectAsStateWithLifecycle()
    val tracks by viewModel.tracks.collectAsStateWithLifecycle()
    var showRename by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var contextTrackIdx by remember { mutableStateOf(-1) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(playlist?.name ?: "Playlist") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showRename = true }) {
                        Icon(Icons.Filled.Edit, contentDescription = "Rename")
                    }
                    IconButton(onClick = { showDelete = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete playlist")
                    }
                },
            )
        },
    ) { padding ->
        if (tracks.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null)
                    Spacer(Modifier.height(8.dp))
                    Text("No tracks yet", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Long-press any song in the library to add it here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
                    ) {
                        Button(
                            onClick = { viewModel.playFrom(0) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Play")
                        }
                        FilledTonalButton(
                            onClick = { viewModel.shuffle() },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Filled.Shuffle, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Shuffle")
                        }
                    }
                    Text(
                        "${tracks.size} tracks",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                    HorizontalDivider()
                }
                itemsIndexed(tracks, key = { _, t -> t.id }) { index, track ->
                    TrackRow(
                        track = track,
                        onClick = { viewModel.playFrom(index) },
                        onLongPress = { contextTrackIdx = index },
                        onSwipeAddToQueue = { viewModel.addToQueue(track) },
                    )
                }
            }
        }
    }

    if (contextTrackIdx in tracks.indices) {
        val t = tracks[contextTrackIdx]
        TrackContextMenu(
            track = t,
            onDismiss = { contextTrackIdx = -1 },
            onPlayNow = { viewModel.playFrom(contextTrackIdx) },
            onPlayNext = { viewModel.playNext(t) },
            onAddToQueue = { viewModel.addToQueue(t) },
            onAddToPlaylist = { /* TODO: "move to other playlist" picker */ },
            onTag = { /* TODO */ },
            onDetails = { /* TODO */ },
            onDelete = { viewModel.removeTrack(t.id) },
        )
    }

    if (showRename && playlist != null) {
        var name by remember(playlist?.id) { mutableStateOf(playlist?.name.orEmpty()) }
        AlertDialog(
            onDismissRequest = { showRename = false },
            title = { Text("Rename playlist") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.rename(name); showRename = false },
                    enabled = name.isNotBlank(),
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showRename = false }) { Text("Cancel") }
            },
        )
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("Delete playlist?") },
            text = { Text("The tracks themselves stay on your device.") },
            confirmButton = {
                Button(
                    onClick = { viewModel.deleteSelf(); showDelete = false; onBack() },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text("Cancel") } },
        )
    }
}
