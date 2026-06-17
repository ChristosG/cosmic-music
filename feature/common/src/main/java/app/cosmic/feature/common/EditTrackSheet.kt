package app.cosmic.feature.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cosmic.core.db.entity.Track

/**
 * Inline metadata editor — title / artist / album. Used to fix tracks
 * whose imported metadata is bad (most commonly `<unknown>` artists from
 * pre-fix YT downloads).
 *
 * Confirms via [onSave] with the new values; caller is responsible for
 * pushing them through [TagWriter].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTrackSheet(
    track: Track,
    onDismiss: () -> Unit,
    onSave: (title: String, artist: String, album: String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var title by remember(track.id) { mutableStateOf(track.title) }
    var artist by remember(track.id) {
        mutableStateOf(track.artist?.takeUnless { app.cosmic.core.common.TitleCleaner.isUnknownArtist(it) }.orEmpty())
    }
    var album by remember(track.id) { mutableStateOf(track.album.orEmpty()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Edit metadata", style = MaterialTheme.typography.titleLarge)
            Text(
                "Updates the file's tags + Cosmic's library. Other music apps see the new values too.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = artist,
                onValueChange = { artist = it },
                label = { Text("Artist") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = album,
                onValueChange = { album = it },
                label = { Text("Album (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Spacer(Modifier.padding(horizontal = 4.dp))
                Button(
                    onClick = { onSave(title.trim(), artist.trim(), album.trim()) },
                    enabled = title.isNotBlank(),
                ) {
                    Icon(Icons.Filled.Save, contentDescription = null)
                    Spacer(Modifier.padding(horizontal = 4.dp))
                    Text("Save")
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
