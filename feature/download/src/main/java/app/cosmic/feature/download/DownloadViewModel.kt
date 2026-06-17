package app.cosmic.feature.download

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cosmic.core.db.entity.DownloadJob
import app.cosmic.core.download.DownloadRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DownloadViewModel @Inject constructor(
    private val repo: DownloadRepository,
) : ViewModel() {

    val jobs: StateFlow<List<DownloadJob>> = repo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _input = MutableStateFlow("")
    val input: StateFlow<String> = _input.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun onInputChange(value: String) {
        _input.value = value
        if (_error.value != null) _error.value = null
    }

    fun submit() {
        val url = _input.value.trim()
        if (url.isEmpty()) return
        viewModelScope.launch {
            try {
                repo.enqueue(url)
                _input.value = ""
            } catch (t: Throwable) {
                _error.value = t.message ?: "Could not queue download"
            }
        }
    }

    fun cancel(jobId: Long) = viewModelScope.launch { repo.cancel(jobId) }
    fun retry(jobId: Long) = viewModelScope.launch { repo.retry(jobId) }
    fun delete(jobId: Long) = viewModelScope.launch { repo.delete(jobId) }
}
