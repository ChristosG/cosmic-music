package app.cosmic.core.player

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.util.Log
import app.cosmic.core.prefs.CosmicPrefs
import app.cosmic.core.prefs.EqPreset
import app.cosmic.core.prefs.PreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hardware Equalizer + BassBoost wired to the audio session ids of BOTH
 * underlying ExoPlayers in [CrossfadingMediaPlayer]. We mirror the user's
 * EQ prefs to both so the effect is consistent regardless of which slot
 * is currently audible during a crossfade transition.
 *
 * Each slot gets its own [Equalizer] + [BassBoost] instance because
 * AudioFx effects attach to a SPECIFIC audioSessionId — there's no
 * shared audio bus we could attach a single effect to.
 */
@Singleton
class AudioEffectsController @Inject constructor(
    private val prefs: PreferencesRepository,
) {
    private data class SlotEffects(val equalizer: Equalizer?, val bassBoost: BassBoost?)

    private var slotA: SlotEffects = SlotEffects(null, null)
    private var slotB: SlotEffects = SlotEffects(null, null)
    private var observeJob: Job? = null

    fun bind(scope: CoroutineScope, audioSessionIds: Pair<Int, Int>) {
        release()
        slotA = createSlot(audioSessionIds.first)
        slotB = createSlot(audioSessionIds.second)
        observeJob = scope.launch {
            prefs.prefs.collectLatest { apply(it) }
        }
    }

    fun release() {
        observeJob?.cancel(); observeJob = null
        listOf(slotA, slotB).forEach {
            runCatching { it.equalizer?.release() }
            runCatching { it.bassBoost?.release() }
        }
        slotA = SlotEffects(null, null)
        slotB = SlotEffects(null, null)
    }

    private fun createSlot(audioSessionId: Int): SlotEffects {
        if (audioSessionId == 0) return SlotEffects(null, null)
        val eq = runCatching {
            Equalizer(0, audioSessionId).apply { enabled = false }
        }.onFailure { Log.w(TAG, "Equalizer alloc failed for $audioSessionId: $it") }.getOrNull()
        val bb = runCatching {
            BassBoost(0, audioSessionId).apply { enabled = false }
        }.onFailure { Log.w(TAG, "BassBoost alloc failed for $audioSessionId: $it") }.getOrNull()
        return SlotEffects(eq, bb)
    }

    private fun apply(p: CosmicPrefs) {
        listOf(slotA, slotB).forEach { applyToSlot(it, p) }
    }

    private fun applyToSlot(slot: SlotEffects, p: CosmicPrefs) {
        val eq = slot.equalizer ?: return
        runCatching {
            eq.enabled = p.eqEnabled
            if (p.eqEnabled) {
                val gains = if (p.eqPreset == EqPreset.CUSTOM) p.eqCustomGains else p.eqPreset.gainsMillibels
                applyGains(eq, gains)
            }
        }
        val bb = slot.bassBoost ?: return
        runCatching {
            val targetEnabled = p.eqEnabled && p.bassBoostStrength > 0
            bb.enabled = targetEnabled
            if (targetEnabled && bb.strengthSupported) {
                bb.setStrength(p.bassBoostStrength.coerceIn(0, 1000).toShort())
            }
        }
    }

    private fun applyGains(eq: Equalizer, gainsMillibels: IntArray) {
        val numBands = eq.numberOfBands.toInt().coerceAtMost(gainsMillibels.size)
        val (minGain, maxGain) = eq.bandLevelRange.let { it[0].toInt() to it[1].toInt() }
        for (i in 0 until numBands) {
            val clamped = gainsMillibels[i].coerceIn(minGain, maxGain).toShort()
            runCatching { eq.setBandLevel(i.toShort(), clamped) }
        }
    }

    private companion object { const val TAG = "AudioEffects" }
}
