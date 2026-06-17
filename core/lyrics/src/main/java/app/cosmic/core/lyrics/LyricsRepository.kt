package app.cosmic.core.lyrics

import app.cosmic.core.common.TitleCleaner
import app.cosmic.core.db.dao.LyricsDao
import app.cosmic.core.db.dao.TrackDao
import app.cosmic.core.db.entity.LyricsCache
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cached lyric lookups keyed by trackId.
 *
 * Concurrency: a per-trackId Mutex coalesces simultaneous calls. The first
 * caller does the network hop and writes the lyrics_cache row; everyone
 * else waits on the mutex and then reads the freshly-written row. This
 * kills the "8 identical LRCLIB GETs in 8 seconds" pattern that happens
 * when MiniPlayer + Now Playing + recompose triggers all observe the
 * same trackId change at once.
 *
 * Cache policy:
 * - Positive cache (we have lyrics) is permanent.
 * - Negative cache (no match) re-tries after [NEGATIVE_TTL_MS] so the
 *   user isn't permanently stuck on "No lyrics" if LRCLIB indexes the
 *   song later.
 *
 * Metadata fix-up: before lookup we run [TitleCleaner.normaliseForLookup]
 * so an "<unknown>" artist with a YT-style "Artist - Song (Official Audio)"
 * title still produces usable lookup parameters.
 */
@Singleton
class LyricsRepository @Inject constructor(
    private val lyricsDao: LyricsDao,
    private val trackDao: TrackDao,
    private val client: LrcLibClient,
) {
    data class Lyrics(val lrcText: String?, val plainText: String?, val source: String, val parsed: List<LyricLine>)

    private val locks = ConcurrentHashMap<Long, Mutex>()

    suspend fun getOrFetch(trackId: Long): Lyrics? = serialised(trackId) {
        val cached = lyricsDao.get(trackId)
        if (cached != null && (cached.lrcText != null || cached.plainText != null)) {
            cached.toLyrics()
        } else if (cached != null && System.currentTimeMillis() - cached.fetchedAt < NEGATIVE_TTL_MS) {
            cached.toLyrics()
        } else {
            fetchAndCache(trackId)
        }
    }

    /** Bypass cache and force a fresh lookup (the "Try again" button). */
    suspend fun refetch(trackId: Long): Lyrics? = serialised(trackId) { fetchAndCache(trackId) }

    private suspend fun fetchAndCache(trackId: Long): Lyrics? {
        val track = trackDao.getById(trackId) ?: return null
        val (artist, title) = TitleCleaner.normaliseForLookup(track.artist, track.title)
        val cleanAlbum = track.album?.takeUnless { TitleCleaner.isUnknownArtist(it) }
        val res = client.lookup(
            artist = artist,
            track = title,
            album = cleanAlbum,
            durationSec = track.durationMs.takeIf { it > 0 }?.let { it / 1000 },
        )
        val entry = LyricsCache(
            trackId = trackId,
            lrcText = res?.lrcText,
            plainText = res?.plainText,
            source = res?.source ?: "lrclib",
            fetchedAt = System.currentTimeMillis(),
        )
        lyricsDao.upsert(entry)
        return entry.toLyrics()
    }

    /**
     * Serialises operations on a given trackId. We don't `.remove()` after —
     * the map grows by one Mutex per ever-played track, bounded by library
     * size and negligible memory.
     */
    private suspend inline fun <T> serialised(trackId: Long, crossinline block: suspend () -> T): T {
        val mutex = locks.computeIfAbsent(trackId) { Mutex() }
        return mutex.withLock { block() }
    }

    private fun LyricsCache.toLyrics(): Lyrics = Lyrics(
        lrcText = lrcText,
        plainText = plainText,
        source = source,
        parsed = lrcText?.let { LrcParser.parse(it) }.orEmpty(),
    )

    private companion object {
        const val NEGATIVE_TTL_MS = 24L * 60L * 60L * 1000L  // 24 hours
    }
}
