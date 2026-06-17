package app.cosmic.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cosmic.core.db.dao.PlaylistDao
import app.cosmic.core.db.dao.TrackDao
import app.cosmic.core.db.entity.Playlist
import app.cosmic.core.db.entity.Track
import app.cosmic.core.metadata.MediaStoreScanner
import app.cosmic.core.metadata.TagWriter
import app.cosmic.core.player.PlaybackController
import app.cosmic.core.shuffle.SmartShuffle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val trackDao: TrackDao,
    private val playlistDao: PlaylistDao,
    private val scanner: MediaStoreScanner,
    private val playback: PlaybackController,
    private val smartShuffle: SmartShuffle,
    private val tagWriter: TagWriter,
) : ViewModel() {

    val tracks: StateFlow<List<Track>> = trackDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val playlists: StateFlow<List<Playlist>> = playlistDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun rescan() = viewModelScope.launch { scanner.scan() }

    fun play(index: Int, list: List<Track>) = viewModelScope.launch {
        playback.playTracks(list, startIndex = index)
    }

    fun playNext(track: Track) {
        playback.playNext(track)
    }

    fun addToQueue(track: Track) {
        playback.addToQueue(track)
    }

    /**
     * Generates a smart-shuffle queue weighted by play history + recency +
     * tag affinity, seeded by the most-recently-played track (or a random
     * one if no history exists yet).
     */
    fun smartShuffle() = viewModelScope.launch {
        val list = tracks.value
        if (list.isEmpty()) return@launch
        val seed = list.random()
        val queue = smartShuffle.buildQueue(seed = seed, library = list, count = 60)
        playback.playTracks(queue, startIndex = 0)
    }

    fun addTrackToPlaylist(playlistId: Long, trackId: Long) = viewModelScope.launch {
        playlistDao.appendTrack(playlistId, trackId)
    }

    fun createPlaylistWithTrack(name: String, trackId: Long) = viewModelScope.launch {
        val newId = playlistDao.insertPlaylist(
            Playlist(name = name.trim(), createdAt = System.currentTimeMillis()),
        )
        playlistDao.appendTrack(newId, trackId)
    }

    fun applyTagEdit(track: Track, title: String, artist: String, album: String) {
        viewModelScope.launch {
            tagWriter.apply(
                track = track,
                edit = TagWriter.Edit(
                    title = title.takeIf { it.isNotBlank() },
                    artist = artist.takeIf { it.isNotBlank() },
                    album = album.takeIf { it.isNotBlank() },
                ),
            )
        }
    }
}
