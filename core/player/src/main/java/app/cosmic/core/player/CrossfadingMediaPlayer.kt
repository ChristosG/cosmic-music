package app.cosmic.core.player

import android.content.Context
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.exoplayer.ExoPlayer
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * **Dual-ExoPlayer crossfading Player** exposed as a single [Player] to
 * MediaSession. This is the architecture serious Android music players
 * (Outertune, InnerTune, Auxio with crossfade) all use.
 *
 * MediaSession sees ONE Player. Internally we hold two `ExoPlayer` instances
 * ("slots"). At any moment one slot is "current" — its position is what
 * we report. The other is either idle or rendering the next track during
 * a crossfade overlap.
 *
 * Lifecycle:
 *
 *   IDLE ──► FADING ──► IDLE (with new "current" slot)
 *
 * - **IDLE**: only `current` plays. Other slot has no media item.
 * - **FADING**: enters when `current` has < `crossfadeMs` left AND there's
 *   a next item queued. We load the next item on the *other* slot, start
 *   it from position 0, and ramp `current.volume` 1→0 + `other.volume`
 *   0→1 in lockstep. **Two audible streams, true overlap.**
 * - **Boundary**: when current's track ends, we stop `current`, swap which
 *   slot is `current`, advance `currentIndex`. The new current keeps
 *   playing seamlessly from where the fade brought it. MediaSession sees
 *   one continuous playback — the swap is invisible because both slots
 *   are owned by us, not by MediaSession.
 *
 * Why this beats single-ExoPlayer + post-transition seek-hop: there's no
 * "primary auto-transitioned to position 0 of the next track and we have
 * to hop forward" moment. Both slots already know their roles; the swap
 * is at the State level, not the audio renderer level.
 *
 * ReplayGain composition: the RG controller calls
 * [setReplayGainFactor] which the fade engine multiplies into its
 * volume calculations, so RG and crossfade compose without fighting.
 *
 * EQ/BassBoost: both slots have separate audio session ids. The host
 * service should bind [AudioEffectsController] to BOTH so user prefs
 * apply regardless of which slot is currently audible.
 */
class CrossfadingMediaPlayer(
    context: Context,
    private val mainLooper: Looper,
    /** Live-read crossfade duration. 0 disables crossfade entirely. */
    private val crossfadeMsProvider: () -> Int,
) : SimpleBasePlayer(mainLooper) {

    private val playerA: ExoPlayer = createInternalPlayer(context)
    private val playerB: ExoPlayer = createInternalPlayer(context)

    /** Audio session ids for both slots. EQ binders should attach to both. */
    val audioSessionIds: Pair<Int, Int>
        get() = playerA.audioSessionId to playerB.audioSessionId

    @Volatile private var currentSlot: ExoPlayer = playerA
    @Volatile private var otherSlot: ExoPlayer = playerB

    private var queue: MutableList<MediaItem> = mutableListOf()
    private var currentIndex: Int = 0
    private var requestedPlayWhenReady: Boolean = false
    private var requestedShuffle: Boolean = false
    private var requestedRepeatMode: Int = Player.REPEAT_MODE_OFF

    private enum class FadePhase { IDLE, FADING }
    @Volatile private var phase: FadePhase = FadePhase.IDLE
    @Volatile private var replayGainFactor: Float = 1f

    private val handler = android.os.Handler(mainLooper)
    private val tick = object : Runnable {
        override fun run() {
            // Adaptive cadence: fast (50ms) only when actively fading or
            // close to a fade boundary; slow (500ms) when idle. 10× fewer
            // wake-ups when the user is just listening to a single track —
            // cuts background CPU + battery cost dramatically.
            val cadence = nextTickDelayMs()
            try { onTick() } finally { handler.postDelayed(this, cadence) }
        }
    }

    init {
        val invalidatingListener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                invalidateState()
            }
        }
        playerA.addListener(invalidatingListener)
        playerB.addListener(invalidatingListener)
        handler.postDelayed(tick, IDLE_TICK_MS)
    }

    /**
     * Picks the tick cadence based on how close we are to needing a fade.
     * - In the fade window: fast tick for smooth volume ramp.
     * - Within `crossfadeMs * 2` of the fade window starting: fast tick so
     *   we don't miss the entry edge.
     * - Otherwise: slow tick — we're nowhere near a boundary.
     */
    private fun nextTickDelayMs(): Long {
        if (phase == FadePhase.FADING) return FADE_TICK_MS
        val cur = currentSlot
        val xfade = crossfadeMsProvider()
        if (xfade <= 0) return IDLE_TICK_MS
        val pos = cur.currentPosition
        val dur = cur.duration
        if (dur <= 0 || pos < 0) return IDLE_TICK_MS
        val timeToEnd = dur - pos
        // Within 2× the fade window of entering it → keep checking quickly.
        return if (timeToEnd <= xfade * 2) FADE_TICK_MS else IDLE_TICK_MS
    }

    /**
     * Set by [ReplayGainController] when the active track's RG values are
     * known. Multiplied into the fade engine's volume math, so RG + fade
     * compose without overwriting each other.
     */
    fun setReplayGainFactor(factor: Float) {
        replayGainFactor = factor.coerceIn(0f, 1f)
        // If not fading, push directly to current slot. During fade the
        // tick will pick it up next iteration.
        if (phase == FadePhase.IDLE) {
            currentSlot.volume = replayGainFactor
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    //                          State exposure
    // ─────────────────────────────────────────────────────────────────────

    override fun getState(): State {
        val cur = currentSlot
        val items = queue.mapIndexed { i, m ->
            MediaItemData.Builder(/* uid = */ i.toLong())
                .setMediaItem(m)
                .setDurationUs(
                    if (i == currentIndex && cur.duration > 0) cur.duration * 1000L
                    else C.TIME_UNSET,
                )
                .build()
        }
        return State.Builder()
            .setAvailableCommands(AVAILABLE_COMMANDS)
            .setPlaylist(items)
            .setCurrentMediaItemIndex(currentIndex.coerceAtMost((items.size - 1).coerceAtLeast(0)))
            .setContentPositionMs { cur.currentPosition.coerceAtLeast(0L) }
            .setContentBufferedPositionMs { cur.bufferedPosition.coerceAtLeast(0L) }
            .setPlayWhenReady(requestedPlayWhenReady, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setPlaybackState(
                when {
                    queue.isEmpty() -> Player.STATE_IDLE
                    cur.playbackState == Player.STATE_ENDED && currentIndex >= queue.size - 1 -> Player.STATE_ENDED
                    else -> cur.playbackState
                },
            )
            .setIsLoading(cur.isLoading)
            .setShuffleModeEnabled(requestedShuffle)
            .setRepeatMode(requestedRepeatMode)
            .setPlaybackParameters(cur.playbackParameters)
            .setVolume(cur.volume)
            .build()
    }

    // ─────────────────────────────────────────────────────────────────────
    //                       Command handlers
    // ─────────────────────────────────────────────────────────────────────

    override fun handleSetMediaItems(
        mediaItems: List<MediaItem>,
        startIndex: Int,
        startPositionMs: Long,
    ): ListenableFuture<*> {
        cancelFade()
        queue = mediaItems.toMutableList()
        currentIndex = startIndex.coerceIn(0, (queue.size - 1).coerceAtLeast(0))
        loadCurrentSlotItem(positionMs = startPositionMs.takeIf { it > 0 } ?: 0L)
        return Futures.immediateVoidFuture()
    }

    override fun handleAddMediaItems(index: Int, mediaItems: List<MediaItem>): ListenableFuture<*> {
        val safeIndex = index.coerceIn(0, queue.size)
        queue.addAll(safeIndex, mediaItems)
        if (safeIndex <= currentIndex) currentIndex += mediaItems.size
        return Futures.immediateVoidFuture()
    }

    override fun handleRemoveMediaItems(fromIndex: Int, toIndex: Int): ListenableFuture<*> {
        val from = fromIndex.coerceIn(0, queue.size)
        val to = toIndex.coerceIn(from, queue.size)
        if (from == to) return Futures.immediateVoidFuture()

        val removingCurrent = currentIndex in from until to
        val removed = queue.subList(from, to).toList()
        queue.subList(from, to).clear()

        when {
            removingCurrent -> {
                cancelFade()
                if (queue.isEmpty()) {
                    currentIndex = 0
                    currentSlot.stop()
                    currentSlot.clearMediaItems()
                } else {
                    currentIndex = from.coerceAtMost(queue.size - 1)
                    loadCurrentSlotItem(positionMs = 0L)
                }
            }
            from < currentIndex -> currentIndex -= removed.size
        }
        return Futures.immediateVoidFuture()
    }

    override fun handleMoveMediaItems(fromIndex: Int, toIndex: Int, newIndex: Int): ListenableFuture<*> {
        val from = fromIndex.coerceIn(0, queue.size)
        val to = toIndex.coerceIn(from, queue.size)
        val newPos = newIndex.coerceIn(0, queue.size - (to - from))
        if (from == to || from == newPos) return Futures.immediateVoidFuture()

        val moving = queue.subList(from, to).toList()
        queue.subList(from, to).clear()
        queue.addAll(newPos, moving)

        currentIndex = when {
            currentIndex in from until to -> newPos + (currentIndex - from)
            currentIndex >= to -> currentIndex - moving.size
            currentIndex >= newPos -> currentIndex + moving.size
            else -> currentIndex
        }.coerceIn(0, (queue.size - 1).coerceAtLeast(0))
        return Futures.immediateVoidFuture()
    }

    override fun handleSeek(
        mediaItemIndex: Int,
        positionMs: Long,
        seekCommand: Int,
    ): ListenableFuture<*> {
        if (queue.isEmpty()) return Futures.immediateVoidFuture()
        val targetIndex = mediaItemIndex.coerceIn(0, queue.size - 1)
        if (targetIndex != currentIndex) {
            cancelFade()
            currentIndex = targetIndex
            loadCurrentSlotItem(positionMs = positionMs.coerceAtLeast(0L))
        } else {
            currentSlot.seekTo(positionMs.coerceAtLeast(0L))
        }
        return Futures.immediateVoidFuture()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        requestedPlayWhenReady = playWhenReady
        currentSlot.playWhenReady = playWhenReady
        if (phase == FadePhase.FADING) otherSlot.playWhenReady = playWhenReady
        return Futures.immediateVoidFuture()
    }

    override fun handlePrepare(): ListenableFuture<*> {
        currentSlot.prepare()
        return Futures.immediateVoidFuture()
    }

    override fun handleStop(): ListenableFuture<*> {
        cancelFade()
        currentSlot.stop()
        return Futures.immediateVoidFuture()
    }

    override fun handleRelease(): ListenableFuture<*> {
        handler.removeCallbacks(tick)
        playerA.release()
        playerB.release()
        return Futures.immediateVoidFuture()
    }

    override fun handleSetShuffleModeEnabled(shuffleModeEnabled: Boolean): ListenableFuture<*> {
        requestedShuffle = shuffleModeEnabled
        invalidateState()
        return Futures.immediateVoidFuture()
    }

    override fun handleSetRepeatMode(repeatMode: Int): ListenableFuture<*> {
        requestedRepeatMode = repeatMode
        invalidateState()
        return Futures.immediateVoidFuture()
    }

    override fun handleSetVolume(volume: Float): ListenableFuture<*> {
        // User-issued volume command (rare — usually via system volume keys).
        // Forward to both slots; the fade engine's per-slot writes during a
        // fade will override transiently, then settle here when fade ends.
        playerA.volume = volume
        playerB.volume = volume
        return Futures.immediateVoidFuture()
    }

    override fun handleSetPlaybackParameters(playbackParameters: PlaybackParameters): ListenableFuture<*> {
        playerA.playbackParameters = playbackParameters
        playerB.playbackParameters = playbackParameters
        return Futures.immediateVoidFuture()
    }

    // ─────────────────────────────────────────────────────────────────────
    //                       Fade orchestration
    // ─────────────────────────────────────────────────────────────────────

    private fun onTick() {
        if (queue.isEmpty()) return
        val cur = currentSlot
        val xfade = crossfadeMsProvider()

        val pos = cur.currentPosition.coerceAtLeast(0L)
        val dur = cur.duration

        val ended = cur.playbackState == Player.STATE_ENDED ||
            (dur > 0 && pos >= dur - END_EPSILON_MS && cur.playWhenReady)

        if (ended) {
            advanceToNext()
            return
        }

        if (xfade <= 0) {
            if (phase == FadePhase.FADING) cancelFade()
            return
        }

        if (dur <= 0) return

        val timeToEnd = dur - pos
        val nextIndex = currentIndex + 1
        val hasNext = nextIndex < queue.size

        if (timeToEnd in 0..xfade && hasNext) {
            if (phase != FadePhase.FADING) {
                phase = FadePhase.FADING
                otherSlot.setMediaItem(queue[nextIndex])
                otherSlot.volume = 0f
                otherSlot.playWhenReady = requestedPlayWhenReady
                otherSlot.prepare()
            }
            val frac = (timeToEnd.toFloat() / xfade.toFloat()).coerceIn(0f, 1f)
            cur.volume = frac * replayGainFactor
            otherSlot.volume = (1f - frac).coerceIn(0f, 1f) * replayGainFactor
        } else if (phase == FadePhase.FADING) {
            cancelFade()
        }
    }

    private fun advanceToNext() {
        // REPEAT_ONE wins regardless of position: any track end re-plays the
        // current track from zero. Without this guard, a mid-playlist track
        // end would advance to the next item even though the user asked to
        // loop just this one.
        if (requestedRepeatMode == Player.REPEAT_MODE_ONE) {
            cancelFade()
            currentSlot.seekTo(0L)
            currentSlot.playWhenReady = requestedPlayWhenReady
            invalidateState()
            return
        }
        if (currentIndex >= queue.size - 1) {
            when (requestedRepeatMode) {
                Player.REPEAT_MODE_ALL -> {
                    currentIndex = 0
                    cancelFade()
                    loadCurrentSlotItem(positionMs = 0L)
                    currentSlot.playWhenReady = requestedPlayWhenReady
                }
                else -> {
                    // Stay at last item; STATE_ENDED reported.
                }
            }
            invalidateState()
            return
        }

        if (phase == FadePhase.FADING) {
            // Other slot already has the next track playing — swap.
            val oldCurrent = currentSlot
            currentSlot = otherSlot
            otherSlot = oldCurrent
            currentIndex += 1
            currentSlot.volume = replayGainFactor
            oldCurrent.stop()
            oldCurrent.clearMediaItems()
            oldCurrent.volume = replayGainFactor
            phase = FadePhase.IDLE
        } else {
            currentIndex += 1
            loadCurrentSlotItem(positionMs = 0L)
        }
        invalidateState()
    }

    private fun loadCurrentSlotItem(positionMs: Long) {
        val item = queue.getOrNull(currentIndex) ?: run {
            currentSlot.clearMediaItems()
            return
        }
        currentSlot.setMediaItem(item)
        currentSlot.seekTo(positionMs)
        currentSlot.volume = replayGainFactor
        currentSlot.playWhenReady = requestedPlayWhenReady
        currentSlot.prepare()
    }

    private fun cancelFade() {
        if (phase == FadePhase.IDLE) return
        otherSlot.stop()
        otherSlot.clearMediaItems()
        otherSlot.volume = 0f
        currentSlot.volume = replayGainFactor
        phase = FadePhase.IDLE
    }

    private fun createInternalPlayer(context: Context): ExoPlayer = ExoPlayer.Builder(context)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .setUsage(C.USAGE_MEDIA)
                .build(),
            // Wrapper handles focus at the Media3 level; slots are silent
            // proxies underneath.
            /* handleAudioFocus = */ false,
        )
        .setHandleAudioBecomingNoisy(false)
        .setLooper(mainLooper)
        .build()

    private companion object {
        const val FADE_TICK_MS = 50L
        const val IDLE_TICK_MS = 500L
        const val END_EPSILON_MS = 30L

        val AVAILABLE_COMMANDS: Player.Commands = Player.Commands.Builder()
            .addAll(
                Player.COMMAND_PLAY_PAUSE,
                Player.COMMAND_PREPARE,
                Player.COMMAND_STOP,
                Player.COMMAND_SEEK_TO_DEFAULT_POSITION,
                Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_PREVIOUS,
                Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_NEXT,
                Player.COMMAND_SEEK_TO_MEDIA_ITEM,
                Player.COMMAND_SET_SPEED_AND_PITCH,
                Player.COMMAND_SET_SHUFFLE_MODE,
                Player.COMMAND_SET_REPEAT_MODE,
                Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
                Player.COMMAND_GET_TIMELINE,
                Player.COMMAND_GET_METADATA,
                Player.COMMAND_SET_PLAYLIST_METADATA,
                Player.COMMAND_SET_MEDIA_ITEM,
                Player.COMMAND_CHANGE_MEDIA_ITEMS,
                Player.COMMAND_GET_AUDIO_ATTRIBUTES,
                Player.COMMAND_GET_VOLUME,
                Player.COMMAND_SET_VOLUME,
                Player.COMMAND_RELEASE,
            )
            .build()
    }
}
