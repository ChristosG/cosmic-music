package app.cosmic.core.extractor.newpipe

import app.cosmic.core.extractor.DownloadCandidate
import app.cosmic.core.extractor.ExtractionResult
import app.cosmic.core.extractor.Extractor
import app.cosmic.core.extractor.SourceKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamExtractor
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves a single track URL on YouTube / YT Music / SoundCloud into a
 * DownloadCandidate. Pick rule: highest-bitrate audio-only stream, preferring
 * Opus (YT) > AAC (m4a) > MP3 — this gives us the best quality the source
 * actually serves, with no transcode at our end.
 */
@Singleton
class YouTubeStreamExtractor @Inject constructor(
    private val initializer: NewPipeInitializer,
) : Extractor {
    override val handles: SourceKind = SourceKind.YOUTUBE
    override suspend fun extract(url: String): ExtractionResult =
        extractSingleStream(url, SourceKind.YOUTUBE, ServiceList.YouTube.serviceId, initializer)
}

@Singleton
class YouTubeMusicExtractor @Inject constructor(
    private val initializer: NewPipeInitializer,
) : Extractor {
    override val handles: SourceKind = SourceKind.YT_MUSIC
    // YouTube Music URLs route through the YouTube service — NewPipe handles
    // the canonicalisation internally.
    override suspend fun extract(url: String): ExtractionResult =
        extractSingleStream(url, SourceKind.YT_MUSIC, ServiceList.YouTube.serviceId, initializer)
}

@Singleton
class SoundCloudExtractorImpl @Inject constructor(
    private val initializer: NewPipeInitializer,
) : Extractor {
    override val handles: SourceKind = SourceKind.SOUNDCLOUD
    override suspend fun extract(url: String): ExtractionResult =
        extractSingleStream(url, SourceKind.SOUNDCLOUD, ServiceList.SoundCloud.serviceId, initializer)
}

@Singleton
class YouTubePlaylistExtractor @Inject constructor(
    private val initializer: NewPipeInitializer,
) : Extractor {
    override val handles: SourceKind = SourceKind.YOUTUBE_PLAYLIST

    override suspend fun extract(url: String): ExtractionResult = withContext(Dispatchers.IO) {
        try {
            initializer.ensureInitialized()
            val service = org.schabi.newpipe.extractor.NewPipe.getService(ServiceList.YouTube.serviceId)
            val playlistExtractor = service.getPlaylistExtractor(url)
            playlistExtractor.fetchPage()
            val items = mutableListOf<StreamInfoItem>()
            items += playlistExtractor.initialPage.items
            // Walk further pages if present (cap to keep enqueue snappy).
            var page = playlistExtractor.initialPage.nextPage
            var pages = 0
            while (page != null && pages < 10 && items.size < 200) {
                val next = playlistExtractor.getPage(page)
                items += next.items
                page = next.nextPage
                pages++
            }
            // For each playlist item we record the source URL only — the
            // actual stream URL gets resolved per-item by the worker, because
            // YouTube stream URLs expire and shouldn't be cached at queue time.
            val candidates = items.mapNotNull { item ->
                val itemUrl = item.url ?: return@mapNotNull null
                DownloadCandidate(
                    sourceUrl = itemUrl,
                    sourceKind = SourceKind.YOUTUBE,
                    streamUrl = itemUrl,                       // worker will re-resolve
                    mimeType = null,
                    codec = null,
                    bitrateKbps = null,
                    sizeBytes = null,
                    title = item.name ?: "Track",
                    artist = item.uploaderName,
                    album = playlistExtractor.name,
                    durationMs = item.duration.takeIf { it > 0 }?.let { it * 1000L },
                    thumbnailUrl = item.thumbnails?.firstOrNull()?.url,
                    filename = sanitiseTitleAsFilename(item.name) + ".webm",
                )
            }
            if (candidates.isEmpty())
                ExtractionResult.Failure("Playlist returned no items")
            else ExtractionResult.Success(candidates)
        } catch (t: Throwable) {
            ExtractionResult.Failure("Playlist extract failed: ${t.message}", t)
        }
    }
}

internal suspend fun extractSingleStream(
    url: String,
    kind: SourceKind,
    serviceId: Int,
    initializer: NewPipeInitializer,
): ExtractionResult = withContext(Dispatchers.IO) {
    try {
        initializer.ensureInitialized()
        val service = org.schabi.newpipe.extractor.NewPipe.getService(serviceId)
        val extractor: StreamExtractor = service.getStreamExtractor(url)
        extractor.fetchPage()

        val audio = pickBestAudioStream(extractor.audioStreams)
            ?: return@withContext ExtractionResult.Failure("No audio-only stream available")

        val title = extractor.name?.takeIf { it.isNotBlank() } ?: "Track"
        val artist = extractor.uploaderName
        val ext = audioStreamExtension(audio)
        val codec = audioStreamCodec(audio)
        val mime = audioStreamMime(audio)

        ExtractionResult.Success(
            listOf(
                DownloadCandidate(
                    sourceUrl = url,
                    sourceKind = kind,
                    streamUrl = audio.content,                  // direct CDN URL
                    mimeType = mime,
                    codec = codec,
                    bitrateKbps = audio.averageBitrate.takeIf { it > 0 },
                    sizeBytes = null,                           // YT chunked
                    title = title,
                    artist = artist,
                    album = null,
                    durationMs = extractor.length.takeIf { it > 0 }?.let { it * 1000L },
                    thumbnailUrl = extractor.thumbnails?.firstOrNull()?.url,
                    filename = "${sanitiseTitleAsFilename(title)}$ext",
                ),
            ),
        )
    } catch (t: Throwable) {
        ExtractionResult.Failure("$kind extract failed: ${t.message}", t)
    }
}

private fun pickBestAudioStream(streams: List<AudioStream>?): AudioStream? {
    if (streams.isNullOrEmpty()) return null
    // Sort: prefer opus > m4a > mp3, then highest bitrate.
    val priority = mapOf("opus" to 3, "m4a" to 2, "aac" to 2, "mp3" to 1)
    return streams
        .sortedWith(
            compareByDescending<AudioStream> { priority[it.format?.suffix?.lowercase()] ?: 0 }
                .thenByDescending { it.averageBitrate.takeIf { b -> b > 0 } ?: 0 },
        )
        .firstOrNull()
}

private fun audioStreamExtension(s: AudioStream): String {
    val suffix = s.format?.suffix?.lowercase()
    return when (suffix) {
        "m4a" -> ".m4a"
        "opus" -> ".opus"
        "mp3" -> ".mp3"
        "webm" -> ".webm"
        "ogg" -> ".ogg"
        null -> ".audio"
        else -> ".$suffix"
    }
}

private fun audioStreamCodec(s: AudioStream): String? = s.format?.suffix?.lowercase()

private fun audioStreamMime(s: AudioStream): String? = s.format?.mimeType

internal fun sanitiseTitleAsFilename(title: String?): String =
    (title ?: "track").replace(Regex("""[\\/:*?"<>|]"""), "_").trim().take(140).ifBlank { "track" }
