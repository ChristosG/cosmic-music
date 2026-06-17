package app.cosmic.feature.search

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.cosmic.feature.common.TrackContextMenu
import app.cosmic.feature.common.TrackRow

@Composable
fun SearchScreen(
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val q by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    var ctxIdx by remember { mutableIntStateOf(-1) }

    Scaffold(modifier = modifier.fillMaxSize()) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = q,
                onValueChange = viewModel::setQuery,
                singleLine = true,
                placeholder = { Text("Search title, artist, album…") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = if (q.isNotEmpty()) {
                    {
                        IconButton(onClick = { viewModel.setQuery("") }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear")
                        }
                    }
                } else null,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            )
            HorizontalDivider()
            when {
                q.length < 2 -> Hint("Type at least 2 characters to search your library.")
                results.isEmpty() -> Hint("No matches.")
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 4.dp),
                ) {
                    itemsIndexed(results, key = { _, t -> t.id }) { idx, track ->
                        TrackRow(
                            track = track,
                            onClick = { viewModel.playFromIndex(idx) },
                            onLongPress = { ctxIdx = idx },
                            onSwipeAddToQueue = { viewModel.addToQueue(track) },
                        )
                    }
                }
            }
        }
    }

    if (ctxIdx in results.indices) {
        val t = results[ctxIdx]
        TrackContextMenu(
            track = t,
            onDismiss = { ctxIdx = -1 },
            onPlayNow = { viewModel.playFromIndex(ctxIdx) },
            onPlayNext = { viewModel.playNext(t) },
            onAddToQueue = { viewModel.addToQueue(t) },
            onAddToPlaylist = { /* TODO: hoist to Library-level since playlists list lives there */ },
            onTag = { /* TODO */ },
            onDetails = { /* TODO */ },
            onDelete = { /* TODO */ },
        )
    }
}

@Composable
private fun Hint(text: String) {
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
