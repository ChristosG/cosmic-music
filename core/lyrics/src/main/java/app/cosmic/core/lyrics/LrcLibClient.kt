package app.cosmic.core.lyrics

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Free, no-key public lyric lookup against https://lrclib.net.
 * Tries `/api/get` (exact match) first, then `/api/search` with a relaxed
 * query when the exact match misses — covers the common case where the
 * artist field on a YT-downloaded track is the channel name, not the
 * actual song artist.
 */
@Singleton
class LrcLibClient @Inject constructor(
    private val client: OkHttpClient,
) {
    data class Result(val lrcText: String?, val plainText: String?, val source: String = "lrclib")

    suspend fun lookup(
        artist: String?,
        track: String?,
        album: String? = null,
        durationSec: Long? = null,
    ): Result? = withContext(Dispatchers.IO) {
        if (track.isNullOrBlank()) {
            Log.d(TAG, "lookup skipped: blank track")
            return@withContext null
        }
        // Exact match path — works when artist + title are clean.
        val exact = if (!artist.isNullOrBlank()) {
            getExact(artist, track, album, durationSec)
        } else null
        // Only short-circuit if exact actually has lyric content. LRCLIB
        // returns 200 with empty syncedLyrics/plainLyrics when it has the
        // track's metadata but no transcribed lyrics — fall through to the
        // search endpoint, which often finds an alternate (different
        // recording / cover / re-uploaded) entry that does have lyrics.
        if (exact != null && (exact.lrcText != null || exact.plainText != null)) {
            return@withContext exact
        }

        // Fallback: relaxed search by track title (+ artist if we have it).
        // Useful for YT downloads where `artist` is often the channel name.
        val searched = searchByTrack(track, artist)
        if (searched != null && (searched.lrcText != null || searched.plainText != null)) {
            return@withContext searched
        }
        // Truly nothing — return whichever non-null we got (so caller knows
        // we tried), or null if both endpoints failed/404'd.
        exact ?: searched
    }

    private fun getExact(artist: String, track: String, album: String?, durationSec: Long?): Result? {
        val url = "https://lrclib.net/api/get".toHttpUrl().newBuilder()
            .addQueryParameter("artist_name", artist)
            .addQueryParameter("track_name", track)
            .apply {
                if (!album.isNullOrBlank()) addQueryParameter("album_name", album)
                if (durationSec != null && durationSec > 0)
                    addQueryParameter("duration", durationSec.toString())
            }
            .build()
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "Cosmic/0.1 (android; lyrics-lookup)")
            .build()
        return try {
            client.newCall(req).execute().use { res ->
                Log.i(TAG, "GET $url → ${res.code}")
                when {
                    res.code == 404 -> null
                    !res.isSuccessful -> null
                    else -> parseObject(res.body?.string())
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "exact lookup threw: ${t.message}")
            null
        }
    }

    private fun searchByTrack(track: String, artist: String?): Result? {
        val q = if (!artist.isNullOrBlank()) "$artist $track" else track
        val url = "https://lrclib.net/api/search".toHttpUrl().newBuilder()
            .addQueryParameter("q", q)
            .build()
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "Cosmic/0.1 (android; lyrics-lookup)")
            .build()
        return try {
            client.newCall(req).execute().use { res ->
                Log.i(TAG, "SEARCH $url → ${res.code}")
                if (!res.isSuccessful) return null
                val body = res.body?.string() ?: return null
                val arr = org.json.JSONArray(body)
                if (arr.length() == 0) {
                    Log.i(TAG, "  no search hits for q=$q")
                    return null
                }
                // Take the first hit. Could rank by duration/artist similarity later.
                val first = arr.optJSONObject(0) ?: return null
                Log.i(TAG, "  best hit: ${first.optString("artistName")} - ${first.optString("trackName")}")
                Result(
                    lrcText = first.optString("syncedLyrics").takeIf { it.isNotBlank() },
                    plainText = first.optString("plainLyrics").takeIf { it.isNotBlank() },
                )
            }
        } catch (t: Throwable) {
            Log.w(TAG, "search lookup threw: ${t.message}")
            null
        }
    }

    private fun parseObject(body: String?): Result? {
        if (body.isNullOrBlank()) return null
        val obj = JSONObject(body)
        return Result(
            lrcText = obj.optString("syncedLyrics").takeIf { it.isNotBlank() },
            plainText = obj.optString("plainLyrics").takeIf { it.isNotBlank() },
        )
    }

    private companion object { const val TAG = "LrcLib" }
}
