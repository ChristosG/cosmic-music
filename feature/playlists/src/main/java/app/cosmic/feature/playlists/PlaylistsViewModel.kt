package app.cosmic.feature.playlists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cosmic.core.db.dao.PlaylistDao
import app.cosmic.core.db.entity.Playlist
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlaylistsViewModel @Inject constructor(
    private val dao: PlaylistDao,
) : ViewModel() {

    val playlists: StateFlow<List<Playlist>> = dao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun create(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            dao.insertPlaylist(
                Playlist(name = name.trim(), createdAt = System.currentTimeMillis()),
            )
        }
    }

    fun rename(playlist: Playlist, newName: String) {
        if (newName.isBlank()) return
        viewModelScope.launch {
            dao.updatePlaylist(playlist.copy(name = newName.trim()))
        }
    }

    fun delete(id: Long) = viewModelScope.launch { dao.deletePlaylist(id) }
}
