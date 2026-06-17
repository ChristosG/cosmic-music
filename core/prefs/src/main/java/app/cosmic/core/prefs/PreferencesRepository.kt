package app.cosmic.core.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "cosmic_prefs")

/**
 * One async source of truth for all user-tunable settings. UI calls into the
 * setters; everything observing [prefs] re-emits when any value changes.
 *
 * Why DataStore not SharedPreferences: SP is sync (blocks the main thread on
 * first access) and the Compose-friendly migration is non-trivial. DataStore
 * exposes Flow natively and serialises writes via a coroutine actor, so we
 * never hit a write-during-read race.
 */
@Singleton
class PreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val THEME = stringPreferencesKey("theme")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val EQ_ENABLED = booleanPreferencesKey("eq_enabled")
        val EQ_PRESET = stringPreferencesKey("eq_preset")
        val EQ_CUSTOM = stringPreferencesKey("eq_custom_csv")
        val BASS_STRENGTH = intPreferencesKey("bass_boost_strength")
        val CROSSFADE = intPreferencesKey("crossfade_ms")
        val REPLAY_GAIN = stringPreferencesKey("replay_gain")
        val SMART_SHUFFLE = booleanPreferencesKey("smart_shuffle")
        val SCAN_WHOLE_MUSIC = booleanPreferencesKey("scan_whole_music_dir")
    }

    val prefs: Flow<CosmicPrefs> = context.dataStore.data.map { p ->
        CosmicPrefs(
            theme = p[Keys.THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.AMOLED,
            dynamicColor = p[Keys.DYNAMIC_COLOR] ?: true,
            eqEnabled = p[Keys.EQ_ENABLED] ?: false,
            eqPreset = p[Keys.EQ_PRESET]?.let { runCatching { EqPreset.valueOf(it) }.getOrNull() } ?: EqPreset.FLAT,
            eqCustomGains = p[Keys.EQ_CUSTOM]?.split(',')?.mapNotNull { it.toIntOrNull() }?.toIntArray()
                ?: IntArray(5),
            bassBoostStrength = p[Keys.BASS_STRENGTH] ?: 0,
            crossfadeMs = p[Keys.CROSSFADE] ?: 0,
            replayGain = p[Keys.REPLAY_GAIN]?.let { runCatching { ReplayGainMode.valueOf(it) }.getOrNull() }
                ?: ReplayGainMode.OFF,
            smartShuffleEnabled = p[Keys.SMART_SHUFFLE] ?: false,
            scanWholeMusicDir = p[Keys.SCAN_WHOLE_MUSIC] ?: false,
        )
    }

    suspend fun setTheme(mode: ThemeMode) = edit { it[Keys.THEME] = mode.name }
    suspend fun setDynamicColor(enabled: Boolean) = edit { it[Keys.DYNAMIC_COLOR] = enabled }
    suspend fun setEqEnabled(enabled: Boolean) = edit { it[Keys.EQ_ENABLED] = enabled }
    suspend fun setEqPreset(preset: EqPreset) = edit { it[Keys.EQ_PRESET] = preset.name }
    suspend fun setEqCustomGains(gains: IntArray) = edit { it[Keys.EQ_CUSTOM] = gains.joinToString(",") }
    suspend fun setBassBoostStrength(strength: Int) = edit {
        it[Keys.BASS_STRENGTH] = strength.coerceIn(0, 1000)
    }
    suspend fun setCrossfadeMs(ms: Int) = edit {
        it[Keys.CROSSFADE] = ms.coerceIn(0, 12_000)
    }
    suspend fun setReplayGain(mode: ReplayGainMode) = edit { it[Keys.REPLAY_GAIN] = mode.name }
    suspend fun setSmartShuffle(enabled: Boolean) = edit { it[Keys.SMART_SHUFFLE] = enabled }
    suspend fun setScanWholeMusicDir(enabled: Boolean) = edit { it[Keys.SCAN_WHOLE_MUSIC] = enabled }

    private suspend inline fun edit(crossinline block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit { block(it) }
    }
}
