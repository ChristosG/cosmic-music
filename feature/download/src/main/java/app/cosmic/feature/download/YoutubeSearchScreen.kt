package app.cosmic.feature.download

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.cosmic.core.extractor.ytdlp.YtDlpSearchService
import coil3.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YoutubeSearchScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: YoutubeSearchViewModel = hiltViewModel(),
) {
    val q by viewModel.query.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val queued by viewModel.queuedUrls.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Search YouTube") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = q,
                onValueChange = viewModel::setQuery,
                singleLine = true,
                placeholder = { Text("Song, artist, album…") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = if (q.isNotBlank()) {
                    {
                        IconButton(onClick = { viewModel.setQuery("") }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear")
                        }
                    }
                } else null,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { viewModel.runSearch() }),
            )
            HorizontalDivider()
            when (val s = state) {
                YoutubeSearchViewModel.UiState.Idle -> Hint(
                    "Type a song name and tap search.\n" +
                        "Each result has a Download button — tap it to add to your library.",
                )
                YoutubeSearchViewModel.UiState.Loading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
                is YoutubeSearchViewModel.UiState.Error -> Hint("Search failed: ${s.message}")
                is YoutubeSearchViewModel.UiState.Results -> {
                    if (s.items.isEmpty()) {
                        Hint("No matches.")
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = 8.dp),
                        ) {
                            items(s.items, key = { it.url }) { result ->
                                ResultRow(
                                    result = result,
                                    isQueued = queued.contains(result.url),
                                    onDownload = { viewModel.download(result.url) },
                                )
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultRow(
    result: YtDlpSearchService.SearchResult,
    isQueued: Boolean,
    onDownload: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(96.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (result.thumbnailUrl != null) {
                AsyncImage(
                    model = result.thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    Icons.Filled.PlayCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                result.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                buildString {
                    if (!result.uploader.isNullOrBlank()) append(result.uploader)
                    if (result.durationSec > 0) {
                        if (isNotEmpty()) append("  ·  ")
                        append(formatDuration(result.durationSec))
                    }
                    result.viewCount?.let {
                        if (isNotEmpty()) append("  ·  ")
                        append(formatViews(it))
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        if (isQueued) {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = "Queued",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp),
            )
        } else {
            FilledTonalIconButton(onClick = onDownload) {
                Icon(Icons.Filled.Download, contentDescription = "Download")
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

private fun formatDuration(sec: Long): String {
    val s = sec.coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val ss = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, ss) else "%d:%02d".format(m, ss)
}

private fun formatViews(count: Long): String = when {
    count >= 1_000_000_000 -> "%.1fB views".format(count / 1_000_000_000.0)
    count >= 1_000_000 -> "%.1fM views".format(count / 1_000_000.0)
    count >= 1_000 -> "%.1fK views".format(count / 1_000.0)
    else -> "$count views"
}
