package app.cosmic.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cosmic.core.db.dao.TrackDao
import app.cosmic.core.db.entity.Track
import app.cosmic.core.player.PlaybackController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import me.xdrop.fuzzywuzzy.FuzzySearch
import javax.inject.Inject

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val trackDao: TrackDao,
    private val playback: PlaybackController,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /**
     * Library size is small enough (typical user libraries < 50k tracks) that
     * pulling the full table once and ranking in-process beats trying to
     * express fuzzy match in SQL. Re-runs whenever query OR library changes.
     */
    val results: StateFlow<List<Track>> = _query
        .debounce(120)
        .combine(trackDao.observeAll()) { q, all -> q to all }
        .flatMapLatest { (q, all) -> kotlinx.coroutines.flow.flowOf(rank(q, all)) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setQuery(q: String) { _query.value = q }

    fun playFromIndex(index: Int) = viewModelScope.launch {
        val list = results.value
        if (list.isNotEmpty() && index in list.indices) {
            playback.playTracks(list, startIndex = index)
        }
    }

    fun playNext(track: app.cosmic.core.db.entity.Track) {
        playback.playNext(track)
    }

    fun addToQueue(track: app.cosmic.core.db.entity.Track) {
        playback.addToQueue(track)
    }

    private fun rank(q: String, all: List<Track>): List<Track> {
        val needle = q.trim()
        if (needle.length < 2) return emptyList()
        // Fuzzy ratio is permissive on typos but score-cap at 50 trims noise.
        return all.asSequence()
            .map { track ->
                val haystack = listOfNotNull(track.title, track.artist, track.album)
                    .joinToString(" ")
                    .lowercase()
                val score = FuzzySearch.partialRatio(needle.lowercase(), haystack)
                track to score
            }
            .filter { it.second >= 60 }
            .sortedByDescending { it.second }
            .take(80)
            .map { it.first }
            .toList()
    }
}
