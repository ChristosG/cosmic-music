package app.cosmic.feature.nowplaying

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cosmic.core.lyrics.LyricsRepository
import app.cosmic.core.player.PlaybackController
import app.cosmic.core.player.PlaybackState
import app.cosmic.core.player.QueueItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NowPlayingViewModel @Inject constructor(
    private val playback: PlaybackController,
    private val lyricsRepo: LyricsRepository,
) : ViewModel() {

    val state: StateFlow<PlaybackState> = playback.state
    val queue: StateFlow<List<QueueItem>> = playback.queue

    private val _userScrubMs = MutableStateFlow<Long?>(null)
    val userScrubMs: StateFlow<Long?> = _userScrubMs.asStateFlow()

    private val _lyrics = MutableStateFlow<LyricsRepository.Lyrics?>(null)
    val lyrics: StateFlow<LyricsRepository.Lyrics?> = _lyrics.asStateFlow()

    init {
        viewModelScope.launch { playback.connect() }
        // Re-fetch lyrics whenever the current trackId changes.
        playback.state
            .map { it.mediaId?.toLongOrNull() }
            .distinctUntilChanged()
            .onEach { id ->
                _lyrics.value = null
                if (id != null) {
                    runCatching { _lyrics.value = lyricsRepo.getOrFetch(id) }
                }
            }
            .launchIn(viewModelScope)
    }

    fun playPause() = playback.playPause()
    fun next() = playback.next()
    fun previous() = playback.previous()
    fun toggleShuffle() = playback.toggleShuffle()
    fun cycleRepeat() = playback.cycleRepeat()

    fun jumpTo(index: Int) = playback.jumpTo(index)
    fun removeFromQueue(index: Int) = playback.removeFromQueue(index)
    fun moveQueueItem(from: Int, to: Int) = playback.moveQueueItem(from, to)

    fun beginScrub(ms: Long) { _userScrubMs.value = ms }
    fun updateScrub(ms: Long) { _userScrubMs.value = ms }
    fun commitScrub(ms: Long) {
        playback.seekTo(ms)
        _userScrubMs.value = null
    }

    /**
     * Manual re-lookup. Bypasses the cache so the user can retry after
     * editing track metadata or just to re-check LRCLIB.
     */
    fun refreshLyrics() {
        val id = state.value.mediaId?.toLongOrNull() ?: return
        viewModelScope.launch {
            _lyrics.value = null
            runCatching { _lyrics.value = lyricsRepo.refetch(id) }
        }
    }

    fun currentPositionMs(): Long = playback.currentPositionMs()
}
