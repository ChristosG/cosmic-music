package app.cosmic.feature.download

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cosmic.core.download.DownloadRepository
import app.cosmic.core.extractor.ytdlp.YtDlpSearchService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class YoutubeSearchViewModel @Inject constructor(
    private val service: YtDlpSearchService,
    private val downloads: DownloadRepository,
) : ViewModel() {

    sealed interface UiState {
        data object Idle : UiState
        data object Loading : UiState
        data class Results(val items: List<YtDlpSearchService.SearchResult>) : UiState
        data class Error(val message: String) : UiState
    }

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _state = MutableStateFlow<UiState>(UiState.Idle)
    val state: StateFlow<UiState> = _state.asStateFlow()

    /**
     * Tracks which result URLs have an enqueue in flight so the UI can
     * show a per-row spinner / "Queued" state without polling the
     * download repository back for status.
     */
    private val _queuedUrls = MutableStateFlow<Set<String>>(emptySet())
    val queuedUrls: StateFlow<Set<String>> = _queuedUrls.asStateFlow()

    private var inflight: Job? = null

    fun setQuery(q: String) {
        _query.value = q
    }

    fun runSearch() {
        val q = _query.value.trim()
        if (q.isEmpty()) return
        inflight?.cancel()
        _state.value = UiState.Loading
        inflight = viewModelScope.launch {
            try {
                val results = service.search(q)
                _state.value = UiState.Results(results)
            } catch (t: Throwable) {
                _state.value = UiState.Error(t.message?.take(180) ?: "Search failed")
            }
        }
    }

    fun download(url: String) {
        if (_queuedUrls.value.contains(url)) return
        _queuedUrls.value = _queuedUrls.value + url
        viewModelScope.launch {
            runCatching { downloads.enqueue(url) }
        }
    }
}
