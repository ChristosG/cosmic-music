package app.cosmic.feature.playlists

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cosmic.core.db.dao.PlaylistDao
import app.cosmic.core.db.entity.Playlist
import app.cosmic.core.db.entity.Track
import app.cosmic.core.player.PlaybackController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlaylistDetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val dao: PlaylistDao,
    private val playback: PlaybackController,
) : ViewModel() {
    val playlistId: Long = savedState["playlistId"] ?: -1L

    private val _playlist = MutableStateFlow<Playlist?>(null)
    val playlist: StateFlow<Playlist?> = _playlist.asStateFlow()

    val tracks: StateFlow<List<Track>> = MutableStateFlow(playlistId)
        .flatMapLatest { id -> dao.observeTracks(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch { _playlist.value = dao.get(playlistId) }
    }

    fun playFrom(index: Int) = viewModelScope.launch {
        val list = tracks.value
        if (list.isNotEmpty() && index in list.indices) {
            playback.playTracks(list, startIndex = index)
        }
    }

    fun shuffle() = viewModelScope.launch {
        val list = tracks.value
        if (list.isNotEmpty()) playback.playTracks(list.shuffled(), startIndex = 0)
    }

    fun playNext(track: Track) {
        playback.playNext(track)
    }

    fun addToQueue(track: Track) {
        playback.addToQueue(track)
    }

    fun removeTrack(trackId: Long) = viewModelScope.launch {
        dao.removeTrack(playlistId, trackId)
    }

    fun rename(newName: String) {
        if (newName.isBlank()) return
        viewModelScope.launch {
            val current = _playlist.value ?: return@launch
            val updated = current.copy(name = newName.trim())
            dao.updatePlaylist(updated)
            _playlist.value = updated
        }
    }

    fun deleteSelf() = viewModelScope.launch { dao.deletePlaylist(playlistId) }
}
