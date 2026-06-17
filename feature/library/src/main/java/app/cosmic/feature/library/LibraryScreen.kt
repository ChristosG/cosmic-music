package app.cosmic.feature.library

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.cosmic.feature.common.AddToPlaylistSheet
import app.cosmic.feature.common.TrackContextMenu
import app.cosmic.feature.common.TrackRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    modifier: Modifier = Modifier,
    onSearchClick: () -> Unit = {},
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val tracks by viewModel.tracks.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val audioPermission = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    }
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, audioPermission) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED,
        )
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
        if (granted) viewModel.rescan()
    }
    LaunchedEffect(hasPermission) {
        if (hasPermission && tracks.isEmpty()) viewModel.rescan()
    }

    var ctxIdx by remember { mutableIntStateOf(-1) }
    var addToPlaylistTrackId by remember { mutableStateOf<Long?>(null) }
    var editTrack by remember { mutableStateOf<app.cosmic.core.db.entity.Track?>(null) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text(
                            stringResource(R.string.library_tab),
                            style = MaterialTheme.typography.displaySmall,
                        )
                        if (tracks.isNotEmpty()) {
                            Text(
                                "${tracks.size} tracks",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onSearchClick) {
                        Icon(Icons.Filled.Search, contentDescription = "Search")
                    }
                    IconButton(
                        onClick = { viewModel.smartShuffle() },
                        enabled = tracks.isNotEmpty(),
                    ) {
                        Icon(Icons.Filled.Shuffle, contentDescription = "Smart shuffle")
                    }
                    IconButton(onClick = { if (hasPermission) viewModel.rescan() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.rescan))
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = androidx.compose.ui.graphics.Color.Transparent,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        .copy(alpha = 0.85f),
                ),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        when {
            !hasPermission -> PermissionGate(padding) { launcher.launch(audioPermission) }
            tracks.isEmpty() -> EmptyState(padding)
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(vertical = 4.dp, horizontal = 0.dp),
            ) {
                itemsIndexed(tracks, key = { _, t -> t.id }) { index, track ->
                    TrackRow(
                        track = track,
                        onClick = { viewModel.play(index, tracks) },
                        onLongPress = { ctxIdx = index },
                        onSwipeAddToQueue = { viewModel.addToQueue(track) },
                    )
                }
            }
        }
    }

    if (ctxIdx in tracks.indices) {
        val track = tracks[ctxIdx]
        TrackContextMenu(
            track = track,
            onDismiss = { ctxIdx = -1 },
            onPlayNow = { viewModel.play(ctxIdx, tracks) },
            onPlayNext = { viewModel.playNext(track) },
            onAddToQueue = { viewModel.addToQueue(track) },
            onAddToPlaylist = { addToPlaylistTrackId = track.id },
            onTag = { editTrack = track },
            onDetails = { editTrack = track },
            onDelete = { /* TODO: deleting from MediaStore needs grant flow */ },
        )
    }

    addToPlaylistTrackId?.let { trackId ->
        AddToPlaylistSheet(
            playlists = playlists,
            onDismiss = { addToPlaylistTrackId = null },
            onPick = { playlistId ->
                viewModel.addTrackToPlaylist(playlistId, trackId)
                addToPlaylistTrackId = null
            },
            onCreate = { name ->
                viewModel.createPlaylistWithTrack(name, trackId)
                addToPlaylistTrackId = null
            },
        )
    }

    editTrack?.let { t ->
        app.cosmic.feature.common.EditTrackSheet(
            track = t,
            onDismiss = { editTrack = null },
            onSave = { title, artist, album ->
                viewModel.applyTagEdit(t, title, artist, album)
                editTrack = null
            },
        )
    }
}

@Composable
private fun PermissionGate(padding: PaddingValues, onRequest: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Filled.MusicNote, contentDescription = null, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.permission_audio_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.permission_audio_body),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRequest) { Text(stringResource(R.string.permission_audio_grant)) }
    }
}

@Composable
private fun EmptyState(padding: PaddingValues) {
    Box(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
        ) {
            Icon(Icons.Filled.MusicNote, contentDescription = null, modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.empty_library_title), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.empty_library_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
