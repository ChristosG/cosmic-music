package app.cosmic.core.player

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import app.cosmic.core.db.dao.TrackDao
import app.cosmic.core.prefs.PreferencesRepository
import app.cosmic.core.prefs.ReplayGainMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.pow

/**
 * Per-track ReplayGain compensation. Computes a linear-amplitude factor
 * from stored RG dB values and pushes it to [CrossfadingMediaPlayer]'s RG
 * slot. The wrapper composes RG factor with the crossfade fraction
 * internally — no race, no fight at the volume layer.
 */
@Singleton
class ReplayGainController @Inject constructor(
    private val trackDao: TrackDao,
    private val prefs: PreferencesRepository,
) {
    private var observeJob: Job? = null
    private var player: CrossfadingMediaPlayer? = null
    private var listener: Player.Listener? = null
    private var currentMode: ReplayGainMode = ReplayGainMode.OFF

    fun bind(scope: CoroutineScope, wrapper: CrossfadingMediaPlayer) {
        if (player === wrapper) return
        unbind()
        player = wrapper

        val l = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                applyForCurrent(scope)
            }
            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                applyForCurrent(scope)
            }
        }
        listener = l
        wrapper.addListener(l)

        observeJob?.cancel()
        observeJob = scope.launch {
            prefs.prefs.collectLatest { p ->
                currentMode = p.replayGain
                applyForCurrent(scope)
            }
        }
    }

    fun unbind() {
        observeJob?.cancel(); observeJob = null
        listener?.let { player?.removeListener(it) }
        listener = null
        player?.setReplayGainFactor(1f)
        player = null
    }

    private fun applyForCurrent(scope: CoroutineScope) {
        val p = player ?: return
        val mediaIdStr = p.currentMediaItem?.mediaId ?: run {
            p.setReplayGainFactor(1f)
            return
        }
        val trackId = mediaIdStr.toLongOrNull() ?: run {
            p.setReplayGainFactor(1f)
            return
        }
        scope.launch(Dispatchers.IO) {
            val track = trackDao.getById(trackId)
            val gainDb: Float? = when (currentMode) {
                ReplayGainMode.OFF -> null
                ReplayGainMode.TRACK -> track?.replayGainTrackDb ?: track?.replayGainAlbumDb
                ReplayGainMode.ALBUM -> track?.replayGainAlbumDb ?: track?.replayGainTrackDb
            }
            val factor = gainDb?.let { db ->
                10.0.pow((db.toDouble() / 20.0)).coerceIn(0.0, 1.0).toFloat()
            } ?: 1.0f
            withContext(Dispatchers.Main) { player?.setReplayGainFactor(factor) }
        }
    }
}
