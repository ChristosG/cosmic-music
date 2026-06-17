package app.cosmic.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cosmic.core.prefs.CosmicPrefs
import app.cosmic.core.prefs.PreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Top-level VM that exposes prefs to the theme provider. Sits in MainActivity
 * scope so theme changes recompose the entire app instantly when the user
 * tweaks them in Settings.
 */
@HiltViewModel
class ThemeViewModel @Inject constructor(
    repo: PreferencesRepository,
) : ViewModel() {
    val prefs: StateFlow<CosmicPrefs> = repo.prefs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CosmicPrefs())
}
