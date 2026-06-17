package app.cosmic.feature.nowplaying

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.cosmic.core.lyrics.LyricLine

@Composable
fun LyricsView(
    lines: List<LyricLine>,
    plainTextFallback: String?,
    currentPositionMs: Long,
    modifier: Modifier = Modifier,
    onRefresh: (() -> Unit)? = null,
) {
    when {
        lines.isNotEmpty() -> SyncedLyrics(lines, currentPositionMs, modifier)
        !plainTextFallback.isNullOrBlank() -> PlainLyrics(plainTextFallback, modifier)
        else -> EmptyLyrics(modifier, onRefresh)
    }
}

@Composable
private fun SyncedLyrics(lines: List<LyricLine>, positionMs: Long, modifier: Modifier) {
    val activeIndex = remember(positionMs, lines) {
        // Binary search the largest index whose timestamp <= positionMs.
        var lo = 0; var hi = lines.size - 1; var best = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (lines[mid].timestampMs <= positionMs) { best = mid; lo = mid + 1 } else hi = mid - 1
        }
        best
    }
    val listState = rememberLazyListState()
    LaunchedEffect(activeIndex) {
        if (activeIndex >= 0) {
            // Centre the active line in the viewport.
            listState.animateScrollToItem(
                index = activeIndex.coerceAtLeast(0),
                scrollOffset = -200,
            )
        }
    }
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 80.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        itemsIndexed(lines) { idx, line ->
            val active = idx == activeIndex
            Text(
                text = line.text.ifBlank { "♪" },
                modifier = Modifier.fillMaxWidth(),
                style = if (active) MaterialTheme.typography.titleMedium
                else MaterialTheme.typography.bodyLarge,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                color = if (active) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun PlainLyrics(text: String, modifier: Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
    ) {
        item {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyLyrics(modifier: Modifier, onRefresh: (() -> Unit)?) {
    Box(modifier = modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "No lyrics for this track",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (onRefresh != null) {
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = onRefresh) {
                    Icon(Icons.Filled.Refresh, contentDescription = null)
                    Spacer(Modifier.height(0.dp))
                    Text("  Try again")
                }
            }
        }
    }
}

