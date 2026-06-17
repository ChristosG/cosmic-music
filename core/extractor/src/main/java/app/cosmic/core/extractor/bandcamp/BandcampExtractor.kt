package app.cosmic.core.extractor.bandcamp

import app.cosmic.core.extractor.DownloadCandidate
import app.cosmic.core.extractor.ExtractionResult
import app.cosmic.core.extractor.Extractor
import app.cosmic.core.extractor.SourceKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lightweight Bandcamp scraper for free streams. The album/track page embeds
 * a `data-tralbum` attribute on a div containing JSON like:
 *
 *   { "trackinfo": [
 *       { "title": "...", "duration": 207.3,
 *         "file": { "mp3-128": "https://t4.bcbits.com/.../track.mp3?token=..." } } ],
 *     "artist": "...", "current": { "title": "..." } }
 *
 * For paid albums or unauthorized tracks, `file` is null — we surface a
 * Failure so the user gets a clear message rather than a silent empty queue.
 */
@Singleton
class BandcampExtractor @Inject constructor(
    private val client: OkHttpClient,
) : Extractor {
    override val handles: SourceKind = SourceKind.BANDCAMP

    override suspend fun extract(url: String): ExtractionResult = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36")
                .build()
            val html = client.newCall(req).execute().use { res ->
                if (!res.isSuccessful) return@withContext ExtractionResult.Failure("Bandcamp HTTP ${res.code}")
                res.body?.string()
                    ?: return@withContext ExtractionResult.Failure("Bandcamp returned empty body")
            }

            val doc = Jsoup.parse(html, url)
            val tralbumAttr = doc.select("[data-tralbum]").firstOrNull()?.attr("data-tralbum")
                ?: doc.select("script[data-tralbum]").firstOrNull()?.attr("data-tralbum")
                ?: return@withContext ExtractionResult.Failure("Bandcamp page missing data-tralbum")

            val obj = JSONObject(tralbumAttr)
            val artist = obj.optJSONObject("current")?.optString("artist")
                ?: obj.optString("artist", "")
            val albumTitle = obj.optJSONObject("current")?.optString("title")
            val tracks = obj.optJSONArray("trackinfo") ?: JSONArray()

            val candidates = mutableListOf<DownloadCandidate>()
            for (i in 0 until tracks.length()) {
                val t = tracks.getJSONObject(i)
                val title = t.optString("title").ifBlank { "Track ${i + 1}" }
                val duration = t.optDouble("duration", -1.0)
                val file = t.optJSONObject("file")
                val mp3 = file?.optString("mp3-128")
                    ?: file?.keys()?.asSequence()?.firstOrNull()
                        ?.let { file.optString(it) }
                if (mp3.isNullOrBlank()) continue
                candidates += DownloadCandidate(
                    sourceUrl = url,
                    sourceKind = SourceKind.BANDCAMP,
                    streamUrl = mp3,
                    mimeType = "audio/mpeg",
                    codec = "mp3",
                    bitrateKbps = 128,
                    sizeBytes = null,
                    title = title,
                    artist = artist.ifBlank { null },
                    album = albumTitle,
                    durationMs = duration.takeIf { it > 0 }?.let { (it * 1000).toLong() },
                    thumbnailUrl = obj.optString("art_id").takeIf { it.isNotBlank() }
                        ?.let { "https://f4.bcbits.com/img/a${it}_10.jpg" },
                    filename = "${sanitise(title)}.mp3",
                )
            }
            if (candidates.isEmpty()) {
                ExtractionResult.Failure("No streamable tracks on this Bandcamp page (paid only?)")
            } else ExtractionResult.Success(candidates)
        } catch (t: Throwable) {
            ExtractionResult.Failure("Bandcamp extract failed: ${t.message}", t)
        }
    }

    private fun sanitise(s: String): String =
        s.replace(Regex("""[\\/:*?"<>|]"""), "_").trim().take(140).ifBlank { "track" }
}
