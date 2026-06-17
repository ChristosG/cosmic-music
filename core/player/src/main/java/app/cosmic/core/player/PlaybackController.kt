package app.cosmic.core.player

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.Tracks
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import app.cosmic.core.db.entity.Track
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Single app-process owner of a MediaController bound to CosmicPlaybackService.
 *
 * Exposes a StateFlow<PlaybackState> for UI to observe — listener callbacks
 * happen on the application main thread, so flow emissions stay on main and
 * Compose recomposes correctly without extra dispatch.
 */
@Singleton
class PlaybackController @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private val _queue = MutableStateFlow<List<QueueItem>>(emptyList())
    val queue: StateFlow<List<QueueItem>> = _queue.asStateFlow()

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var listener: Player.Listener? = null

    suspend fun connect(): MediaController {
        controller?.let { return it }
        val token = SessionToken(context, ComponentName(context, CosmicPlaybackService::class.java))
        return suspendCancellableCoroutine { cont ->
            val future = MediaController.Builder(context, token).buildAsync()
            controllerFuture = future
            future.addListener({
                val c = future.get()
                attach(c)
                cont.resume(c)
            }, MoreExecutors.directExecutor())
            cont.invokeOnCancellation { future.cancel(true) }
        }
    }

    private fun attach(c: MediaController) {
        controller = c
        // Initial snapshot.
        publish(c)
        val l = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                publish(player)
            }
            override fun onTimelineChanged(timeline: Timeline, reason: Int) = publish(c)
            override fun onTracksChanged(tracks: Tracks) = publish(c)
        }
        listener = l
        c.addListener(l)
    }

    private fun publish(p: Player) {
        val mm = p.mediaMetadata
        _state.value = PlaybackState(
            mediaId = p.currentMediaItem?.mediaId,
            title = mm.title?.toString(),
            artist = mm.artist?.toString(),
            album = mm.albumTitle?.toString(),
            artworkUri = mm.artworkUri?.toString(),
            durationMs = p.duration.coerceAtLeast(0L),
            positionMs = p.currentPosition.coerceAtLeast(0L),
            isPlaying = p.isPlaying,
            isBuffering = p.playbackState == Player.STATE_BUFFERING,
            shuffleEnabled = p.shuffleModeEnabled,
            repeatMode = when (p.repeatMode) {
                Player.REPEAT_MODE_ONE -> RepeatMode.ONE
                Player.REPEAT_MODE_ALL -> RepeatMode.ALL
                else -> RepeatMode.OFF
            },
            hasNext = p.hasNextMediaItem(),
            hasPrevious = p.hasPreviousMediaItem(),
            currentIndex = p.currentMediaItemIndex,
            queueSize = p.mediaItemCount,
        )
        publishQueue(p)
    }

    private fun publishQueue(p: Player) {
        val items = ArrayList<QueueItem>(p.mediaItemCount)
        for (i in 0 until p.mediaItemCount) {
            val mi = p.getMediaItemAt(i)
            val md = mi.mediaMetadata
            items += QueueItem(
                index = i,
                mediaId = mi.mediaId,
                title = md.title?.toString().orEmpty(),
                artist = md.artist?.toString(),
                album = md.albumTitle?.toString(),
            )
        }
        _queue.value = items
    }

    suspend fun playTracks(tracks: List<Track>, startIndex: Int = 0) {
        if (tracks.isEmpty()) return
        val c = connect()
        c.setMediaItems(tracks.map { it.toMediaItem() }, startIndex, /* startPositionMs = */ 0L)
        c.prepare()
        c.play()
    }

    /** Inserts a track immediately after the current item so it plays next. */
    fun playNext(track: Track) = scope.launch {
        val c = connect()
        // If nothing is queued yet, just start playback with this single track.
        if (c.mediaItemCount == 0) {
            c.setMediaItem(track.toMediaItem())
            c.prepare()
            c.play()
            return@launch
        }
        c.addMediaItem(c.currentMediaItemIndex + 1, track.toMediaItem())
    }

    /** Appends a track to the end of the queue. */
    fun addToQueue(track: Track) = scope.launch {
        val c = connect()
        if (c.mediaItemCount == 0) {
            c.setMediaItem(track.toMediaItem())
            c.prepare()
            c.play()
            return@launch
        }
        c.addMediaItem(track.toMediaItem())
    }

    /** Jumps to a specific index in the current queue (useful for queue UI). */
    fun jumpTo(index: Int) = scope.launch {
        val c = connect()
        if (index in 0 until c.mediaItemCount) {
            c.seekTo(index, 0L)
            c.play()
        }
    }

    /** Removes the queue entry at [index]. No-op if currently-playing index. */
    fun removeFromQueue(index: Int) = scope.launch {
        val c = connect()
        if (index in 0 until c.mediaItemCount && index != c.currentMediaItemIndex) {
            c.removeMediaItem(index)
        }
    }

    /** Reorders a queue item from one position to another. */
    fun moveQueueItem(from: Int, to: Int) = scope.launch {
        val c = connect()
        if (from !in 0 until c.mediaItemCount || to !in 0 until c.mediaItemCount) return@launch
        if (from == to) return@launch
        c.moveMediaItem(from, to)
    }

    /** Empties the queue. Does NOT pause the playback service automatically. */
    fun clearQueue() = scope.launch {
        connect().clearMediaItems()
    }

    fun playPause() = scope.launch {
        val c = connect()
        if (c.isPlaying) c.pause() else c.play()
    }

    fun next() = scope.launch { connect().seekToNextMediaItem() }
    fun previous() = scope.launch { connect().seekToPreviousMediaItem() }
    fun seekTo(positionMs: Long) = scope.launch { connect().seekTo(positionMs) }

    fun toggleShuffle() = scope.launch {
        val c = connect()
        c.shuffleModeEnabled = !c.shuffleModeEnabled
    }

    fun cycleRepeat() = scope.launch {
        val c = connect()
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    /** Live position from the controller for UI tickers. */
    fun currentPositionMs(): Long = controller?.currentPosition?.coerceAtLeast(0L) ?: 0L

    fun release() {
        listener?.let { controller?.removeListener(it) }
        listener = null
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        controller = null
    }
}

private fun Track.toMediaItem(): MediaItem = MediaItem.Builder()
    .setMediaId(id.toString())
    .setUri(filePath)
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .setIsBrowsable(false)
            .setIsPlayable(true)
            // Setting artworkUri here lets the MediaSession-driven UIs
            // (system lock-screen, Bluetooth car displays, our MiniPlayer)
            // render the cover automatically without having to re-query the
            // Track row by mediaId.
            .apply {
                embeddedArtUri?.takeIf { it.isNotBlank() }
                    ?.let { setArtworkUri(android.net.Uri.parse(it)) }
            }
            .build(),
    )
    .build()
