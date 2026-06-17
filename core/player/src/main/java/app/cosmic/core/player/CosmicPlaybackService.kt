package app.cosmic.core.player

import android.content.Intent
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import app.cosmic.core.prefs.PreferencesRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

/**
 * Foreground media service. Media3 publishes lock-screen + notification
 * + Bluetooth controls once a MediaSession is bound.
 *
 * Player architecture: a single [CrossfadingMediaPlayer] is what
 * MediaSession sees. Internally it manages two ExoPlayer slots for true
 * overlap crossfade. EQ + BassBoost are bound to BOTH internal audio
 * session ids so effects apply regardless of which slot is currently
 * audible. ReplayGain factor is pushed into the wrapper and composes with
 * the fade math — no race at the volume layer.
 */
@AndroidEntryPoint
class CosmicPlaybackService : MediaLibraryService() {

    @Inject lateinit var effects: AudioEffectsController
    @Inject lateinit var replayGain: ReplayGainController
    @Inject lateinit var prefs: PreferencesRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Volatile private var crossfadeMs: Int = 0

    private var player: CrossfadingMediaPlayer? = null
    private var session: MediaLibrarySession? = null

    override fun onCreate() {
        super.onCreate()

        // Live-reflect crossfade pref into a volatile field that the
        // wrapper polls each tick.
        prefs.prefs
            .map { it.crossfadeMs }
            .distinctUntilChanged()
            .onEach { crossfadeMs = it }
            .launchIn(serviceScope)

        val wrapper = CrossfadingMediaPlayer(
            context = this,
            mainLooper = mainLooper,
            crossfadeMsProvider = { crossfadeMs },
        )

        // EQ + BassBoost attach to each slot's audio session id so the
        // effect is consistent across crossfade boundaries.
        effects.bind(serviceScope, wrapper.audioSessionIds)

        // RG factor pushed into the wrapper, composed with the fade math.
        replayGain.bind(serviceScope, wrapper)

        player = wrapper
        session = MediaLibrarySession.Builder(this, wrapper, EmptyLibraryCallback).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = player ?: return super.onTaskRemoved(rootIntent)
        if (!p.playWhenReady || p.mediaItemCount == 0) {
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        effects.release()
        replayGain.unbind()
        serviceScope.cancel()
        session?.run {
            player.release()
            release()
        }
        session = null
        player = null
        super.onDestroy()
    }

    private object EmptyLibraryCallback : MediaLibrarySession.Callback
}
