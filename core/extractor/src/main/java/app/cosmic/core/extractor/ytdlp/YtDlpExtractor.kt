package app.cosmic.core.extractor.ytdlp

import android.util.Log
import app.cosmic.core.extractor.DownloadCandidate
import app.cosmic.core.extractor.ExtractionResult
import app.cosmic.core.extractor.Extractor
import app.cosmic.core.extractor.SourceKind
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "YtDlpExtract"

/**
 * yt-dlp powered extractor. Replaces NewPipe for YouTube / YT Music: where
 * NewPipe loses the cat-and-mouse with YT bot detection, yt-dlp wins thanks
 * to its actively-maintained Python parser that mimics a real web client
 * including signature-timestamp + PoToken handshake.
 *
 * We only use yt-dlp to RESOLVE the stream URL — the actual byte download
 * still flows through OkHttp + MediaStoreWriter, so the existing progress
 * reporting and atomic write semantics are unchanged.
 */
@Singleton
class YtDlpExtractor @Inject constructor(
    private val initializer: YtDlpInitializer,
) : Extractor {

    override val handles: SourceKind = SourceKind.YOUTUBE

    override suspend fun extract(url: String): ExtractionResult = withContext(Dispatchers.IO) {
        try {
            initializer.ensureReady()
            val info = fetchInfo(url, flatPlaylist = false)
            ExtractionResult.Success(listOf(toCandidate(url, info)))
        } catch (t: Throwable) {
            // Log everything we can so logcat captures the full Python traceback
            // (it's nested inside YoutubeDLException.message). Without this the
            // user only sees a 180-char preview in the Downloads UI.
            Log.e(TAG, "extract failed for $url", t)
            if (t is YoutubeDLException) {
                Log.e(TAG, "yt-dlp stderr/stdout follows:")
                t.message?.lineSequence()?.forEach { Log.e(TAG, it) }
            }
            ExtractionResult.Failure(
                "yt-dlp extract failed: ${t.message?.take(400) ?: t::class.java.simpleName}",
                t,
            )
        }
    }

    private fun toCandidate(originalUrl: String, info: JSONObject): DownloadCandidate {
        val rawTitle = info.optString("title").ifBlank { "Track" }
        val uploader = info.optString("uploader").ifBlank { info.optString("channel") }.takeIf { it.isNotBlank() }
        // Try yt-dlp's own metadata fields first (some videos expose them
        // via Music-on-YouTube tagging), then parse "Artist - Title" style
        // titles. Falls back to uploader/channel name as a last resort.
        val ytMetaArtist = info.optString("artist").ifBlank { info.optString("creator") }.takeIf { it.isNotBlank() }
        val ytMetaTrack = info.optString("track").takeIf { it.isNotBlank() }
        val (title, artist) = when {
            ytMetaArtist != null && ytMetaTrack != null -> ytMetaTrack to ytMetaArtist
            else -> parseArtistAndTitle(rawTitle, uploader)
        }
        val duration = info.optDouble("duration", -1.0).takeIf { it > 0 }?.let { (it * 1000).toLong() }
        val thumbnail = info.optString("thumbnail").takeIf { it.isNotBlank() }

        val format = pickBestAudioFormat(info.optJSONArray("formats"))
            ?: error("No audio formats in yt-dlp output")

        val streamUrl = format.optString("url").takeIf { it.isNotBlank() }
            ?: error("Best format has no url")
        val ext = format.optString("ext").takeIf { it.isNotBlank() } ?: "audio"
        val codec = format.optString("acodec").takeIf { it.isNotBlank() && it != "none" }
        val bitrate = format.optDouble("abr", -1.0).takeIf { it > 0 }?.toInt()
        val mime = mimeFromExt(ext)
        val filesize = format.optLong("filesize", -1L).takeIf { it > 0 }
            ?: format.optLong("filesize_approx", -1L).takeIf { it > 0 }

        return DownloadCandidate(
            sourceUrl = originalUrl,
            sourceKind = SourceKind.YOUTUBE,
            streamUrl = streamUrl,
            mimeType = mime,
            codec = codec,
            bitrateKbps = bitrate,
            sizeBytes = filesize,
            title = title,
            artist = artist,
            album = null,
            durationMs = duration,
            thumbnailUrl = thumbnail,
            filename = "${sanitiseFilename(title)}.$ext",
        )
    }
}

@Singleton
class YtDlpMusicExtractor @Inject constructor(
    private val ytDlp: YtDlpExtractor,
) : Extractor {
    override val handles: SourceKind = SourceKind.YT_MUSIC
    override suspend fun extract(url: String): ExtractionResult {
        // YT Music URLs are accepted by yt-dlp; just delegate.
        val result = ytDlp.extract(url)
        return when (result) {
            is ExtractionResult.Failure -> result
            is ExtractionResult.Success -> ExtractionResult.Success(
                result.candidates.map { it.copy(sourceKind = SourceKind.YT_MUSIC) },
            )
        }
    }
}

@Singleton
class YtDlpPlaylistExtractor @Inject constructor(
    private val initializer: YtDlpInitializer,
) : Extractor {
    override val handles: SourceKind = SourceKind.YOUTUBE_PLAYLIST

    override suspend fun extract(url: String): ExtractionResult = withContext(Dispatchers.IO) {
        try {
            initializer.ensureReady()
            // --flat-playlist makes yt-dlp dump *items* without resolving each.
            // We then enqueue one DownloadJob per item; the worker re-runs yt-dlp
            // per item to resolve the actual stream URL (which has a TTL).
            val info = fetchInfo(url, flatPlaylist = true)
            val entries = info.optJSONArray("entries") ?: JSONArray()
            val candidates = mutableListOf<DownloadCandidate>()
            for (i in 0 until entries.length()) {
                val item = entries.optJSONObject(i) ?: continue
                val itemUrl = item.optString("url").takeIf { it.startsWith("http") }
                    ?: item.optString("webpage_url").takeIf { it.startsWith("http") }
                    ?: item.optString("id").takeIf { it.isNotBlank() }
                        ?.let { "https://www.youtube.com/watch?v=$it" }
                    ?: continue
                val title = item.optString("title").ifBlank { "Track ${i + 1}" }
                val uploader = item.optString("uploader").ifBlank { null }
                candidates += DownloadCandidate(
                    sourceUrl = itemUrl,
                    sourceKind = SourceKind.YOUTUBE,
                    streamUrl = itemUrl, // worker re-resolves via yt-dlp
                    mimeType = null,
                    codec = null,
                    bitrateKbps = null,
                    sizeBytes = null,
                    title = title,
                    artist = uploader,
                    album = info.optString("title").takeIf { it.isNotBlank() },
                    durationMs = item.optDouble("duration", -1.0).takeIf { it > 0 }
                        ?.let { (it * 1000).toLong() },
                    thumbnailUrl = item.optString("thumbnail").takeIf { it.isNotBlank() },
                    filename = "${sanitiseFilename(title)}.audio",
                )
            }
            if (candidates.isEmpty()) ExtractionResult.Failure("Playlist returned no items")
            else ExtractionResult.Success(candidates)
        } catch (t: Throwable) {
            Log.e(TAG, "playlist extract failed for $url", t)
            ExtractionResult.Failure("yt-dlp playlist failed: ${t.message?.take(400)}", t)
        }
    }
}

internal fun fetchInfo(url: String, flatPlaylist: Boolean): JSONObject {
    val req = YoutubeDLRequest(url).apply {
        addOption("--dump-single-json")
        addOption("--no-warnings")
        // Bypass YT's web-client bandwidth throttling by including the
        // android_music client (which returns un-throttled URLs and exposes
        // m4a). Keep `default` at the head so yt-dlp uses its built-in
        // priority and falls through to mobile clients only when needed.
        // ios is last because it tends to expose only opus/webm.
        addOption("--extractor-args", "youtube:player_client=default,android_music,ios")
        if (flatPlaylist) {
            addOption("--flat-playlist")
            addOption("--yes-playlist")
        } else {
            // Permissive selector. Prefer m4a (Samsung MediaStore happy path)
            // but cascade through mp3 → any audio-only → best combined so
            // *something* always matches even when the chosen client doesn't
            // expose m4a.
            addOption(
                "--format",
                "bestaudio[ext=m4a]/bestaudio[ext=mp3]/bestaudio[acodec^=mp4a]/bestaudio/best",
            )
            addOption("--no-playlist")
        }
    }
    val response = YoutubeDL.getInstance().execute(req)
    val out = response.out.trim()
    require(out.isNotEmpty()) { "yt-dlp produced no output" }
    return JSONObject(out)
}

/**
 * Parses common video-title patterns into (title, artist):
 *   "Artist - Song"            → ("Song", "Artist")
 *   "Artist – Song"            → ditto, em-dash
 *   "Artist: Song"             → ditto
 *   "Song (feat. X) - Artist"  → falls back to uploader heuristic
 *   "Song"                     → (title, uploader)
 *
 * Strips trailing junk like "[Official Audio]", "(Lyric Video)", "HD",
 * "Official Music Video" etc. so LRCLIB lookups have a clean track name.
 */
internal fun parseArtistAndTitle(rawTitle: String, uploader: String?): Pair<String, String?> {
    val cleanedTitle = rawTitle
        .replace(Regex("""\s*[\[(](?:official\s*(?:music\s*)?(?:video|audio|lyric(?:s)?\s*video|visualizer)|hd|hq|4k|lyric(?:s)?|audio|m/v|mv)[\])]\s*""", RegexOption.IGNORE_CASE), " ")
        .replace(Regex("""\s+"""), " ")
        .trim()
    val sep = Regex("""\s+[-–—:]\s+""")
    val parts = cleanedTitle.split(sep, limit = 2)
    return if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
        // Heuristic: if the LEFT side equals the uploader, assume "Channel - Song"
        // (LEFT is artist). Otherwise we still go LEFT=artist as the convention,
        // because most music videos are titled "Artist - Title".
        parts[1].trim() to parts[0].trim()
    } else {
        cleanedTitle to uploader
    }
}

internal fun pickBestAudioFormat(formats: JSONArray?): JSONObject? {
    if (formats == null || formats.length() == 0) return null
    var best: JSONObject? = null
    var bestScore = Double.NEGATIVE_INFINITY
    for (i in 0 until formats.length()) {
        val f = formats.optJSONObject(i) ?: continue
        val acodec = f.optString("acodec").lowercase()
        val vcodec = f.optString("vcodec").lowercase()
        if (acodec.isBlank() || acodec == "none") continue
        // Skip combined video+audio when an audio-only format is available
        // (handled by score: audio-only gets +1.0 boost so it wins ties).
        val abr = f.optDouble("abr", 0.0)
        val ext = f.optString("ext").lowercase()
        // Mirror yt-dlp's format-selector preference: m4a (AAC) wins over
        // opus/webm because of MediaStore compat on Samsung et al. The
        // bitrate weight in `score` still picks the highest-quality m4a.
        val codecPriority = when {
            acodec.startsWith("aac") || ext == "m4a" || acodec.startsWith("mp4a") -> 3.0
            acodec.startsWith("mp3") -> 2.5
            acodec.startsWith("opus") && ext != "webm" -> 2.0
            acodec.startsWith("opus") -> 1.5  // opus-in-webm — lowest pref
            else -> 1.0
        }
        val audioOnlyBoost = if (vcodec == "none") 1.0 else 0.0
        val score = abr + 100 * codecPriority + 1000 * audioOnlyBoost
        if (score > bestScore) {
            bestScore = score
            best = f
        }
    }
    return best
}

internal fun mimeFromExt(ext: String): String? = when (ext.lowercase()) {
    "m4a", "mp4" -> "audio/mp4"
    "mp3" -> "audio/mpeg"
    "opus" -> "audio/opus"
    "webm" -> "audio/webm"
    "ogg" -> "audio/ogg"
    "flac" -> "audio/flac"
    "wav" -> "audio/wav"
    "aac" -> "audio/aac"
    else -> null
}

internal fun sanitiseFilename(s: String): String =
    s.replace(Regex("""[\\/:*?"<>|]"""), "_").trim().take(140).ifBlank { "track" }
