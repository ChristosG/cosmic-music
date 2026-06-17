package app.cosmic.core.player

/**
 * Snapshot of the player's user-visible state. Pulled from MediaController
 * + Player.Listener events; emitted as a StateFlow from PlaybackController.
 *
 * positionMs is a snapshot at the time of emission — UI should tick its own
 * 1Hz timer to interpolate while [isPlaying], rather than spamming the
 * controller for fresh positions.
 */
data class PlaybackState(
    val mediaId: String? = null,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val artworkUri: String? = null,
    val durationMs: Long = 0L,
    val positionMs: Long = 0L,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val shuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
    val currentIndex: Int = 0,
    val queueSize: Int = 0,
) {
    val hasTrack: Boolean get() = mediaId != null
}

enum class RepeatMode { OFF, ONE, ALL }

/**
 * One entry in the player's current queue. Reflects what's loaded into the
 * MediaSession's media item list, NOT a database row — for queue display
 * only. mediaId is the original Track.id stringified.
 */
data class QueueItem(
    val index: Int,
    val mediaId: String,
    val title: String,
    val artist: String?,
    val album: String?,
)
