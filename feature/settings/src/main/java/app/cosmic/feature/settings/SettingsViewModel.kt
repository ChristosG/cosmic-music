package app.cosmic.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cosmic.core.prefs.CosmicPrefs
import app.cosmic.core.prefs.EqPreset
import app.cosmic.core.prefs.PreferencesRepository
import app.cosmic.core.prefs.ReplayGainMode
import app.cosmic.core.prefs.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repo: PreferencesRepository,
) : ViewModel() {

    val state: StateFlow<CosmicPrefs> = repo.prefs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CosmicPrefs())

    fun setTheme(t: ThemeMode) = viewModelScope.launch { repo.setTheme(t) }
    fun setDynamicColor(b: Boolean) = viewModelScope.launch { repo.setDynamicColor(b) }
    fun setEqEnabled(b: Boolean) = viewModelScope.launch { repo.setEqEnabled(b) }
    fun setEqPreset(p: EqPreset) = viewModelScope.launch { repo.setEqPreset(p) }
    fun setBassStrength(strength: Int) = viewModelScope.launch { repo.setBassBoostStrength(strength) }
    fun setCrossfadeMs(ms: Int) = viewModelScope.launch { repo.setCrossfadeMs(ms) }
    fun setReplayGain(mode: ReplayGainMode) = viewModelScope.launch { repo.setReplayGain(mode) }
    fun setSmartShuffle(b: Boolean) = viewModelScope.launch { repo.setSmartShuffle(b) }
    fun setScanWholeMusic(b: Boolean) = viewModelScope.launch { repo.setScanWholeMusicDir(b) }
}
