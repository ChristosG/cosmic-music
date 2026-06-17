package app.cosmic.core.extractor.ytdlp

import android.util.Log
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * YouTube search via yt-dlp's `ytsearch:` URL prefix. Used in place of
 * NewPipeExtractor's search because YouTube's anti-extraction layer now
 * affects search responses too (the same `ytInitialData` parse failure
 * we hit on stream extraction).
 *
 * Output is one JSON object per line (NDJSON) when `--flat-playlist` is
 * combined with `--print` — much lighter than `--dump-single-json` which
 * resolves every hit. Each line carries:
 *   id, title, uploader, duration, view_count, thumbnail (sometimes), url
 */
@Singleton
class YtDlpSearchService @Inject constructor(
    private val initializer: YtDlpInitializer,
) {

    data class SearchResult(
        val title: String,
        val uploader: String?,
        val durationSec: Long,
        val viewCount: Long?,
        val thumbnailUrl: String?,
        val url: String,
    )

    private data class CacheEntry(val results: List<SearchResult>, val storedAt: Long)

    /** Tiny LRU + TTL cache so repeated searches don't pay the yt-dlp cost. */
    private val cache = object : LinkedHashMap<String, CacheEntry>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CacheEntry>): Boolean =
            size > 20
    }
    private val cacheLock = Any()

    suspend fun search(query: String, count: Int = 20): List<SearchResult> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.isEmpty()) return@withContext emptyList()
        val cacheKey = "$q::$count"
        synchronized(cacheLock) {
            val hit = cache[cacheKey]
            if (hit != null && System.currentTimeMillis() - hit.storedAt < CACHE_TTL_MS) {
                return@withContext hit.results
            }
        }
        initializer.ensureReady()

        val req = YoutubeDLRequest("ytsearch${count}:$q").apply {
            addOption("--flat-playlist")
            addOption("--no-warnings")
            // --dump-json emits one full JSON info-dict per result line.
            // Combined with --flat-playlist, items aren't fully resolved
            // (no per-item HTTP), so search stays fast — we only need
            // title/duration/thumbnail to render the row, and actual
            // stream URLs resolve later when the user taps Download.
            addOption("--dump-json")
            addOption("--no-playlist")
            // Same player-client trick as the extractor — keeps search
            // responses fresh and bypasses some throttle paths.
            addOption("--extractor-args", "youtube:player_client=default,android_music")
        }

        val response = try {
            YoutubeDL.getInstance().execute(req)
        } catch (t: Throwable) {
            Log.e(TAG, "yt-dlp search failed: ${t.message}", t)
            throw t
        }

        val results = mutableListOf<SearchResult>()
        for (line in response.out.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || !trimmed.startsWith("{")) continue
            try {
                val obj = JSONObject(trimmed)
                val id = obj.optString("id").takeIf { it.isNotBlank() }
                val watchUrl = obj.optString("webpage_url").takeIf { it.startsWith("http") }
                    ?: obj.optString("url").takeIf { it.startsWith("http") }
                    ?: id?.let { "https://www.youtube.com/watch?v=$it" }
                    ?: continue
                results += SearchResult(
                    title = obj.optString("title").ifBlank { "Untitled" },
                    uploader = obj.optString("uploader").ifBlank { obj.optString("channel") }
                        .takeIf { it.isNotBlank() },
                    durationSec = obj.optDouble("duration", -1.0).takeIf { it > 0 }?.toLong() ?: 0L,
                    viewCount = obj.optLong("view_count", -1L).takeIf { it >= 0 },
                    thumbnailUrl = obj.optString("thumbnail").takeIf { it.startsWith("http") }
                        ?: id?.let { "https://i.ytimg.com/vi/$it/mqdefault.jpg" },
                    url = watchUrl,
                )
            } catch (t: Throwable) {
                Log.w(TAG, "skipping malformed search row: ${t.message}")
            }
        }
        Log.i(TAG, "ytsearch:'$q' returned ${results.size} hits")
        synchronized(cacheLock) {
            cache[cacheKey] = CacheEntry(results, System.currentTimeMillis())
        }
        results
    }

    private companion object {
        const val TAG = "YtDlpSearch"
        const val CACHE_TTL_MS = 5L * 60_000L  // 5 minutes
    }
}
