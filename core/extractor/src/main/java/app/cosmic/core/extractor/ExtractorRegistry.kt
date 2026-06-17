package app.cosmic.core.extractor

import app.cosmic.core.extractor.bandcamp.BandcampExtractor
import app.cosmic.core.extractor.newpipe.SoundCloudExtractorImpl
import app.cosmic.core.extractor.ytdlp.YtDlpExtractor
import app.cosmic.core.extractor.ytdlp.YtDlpMusicExtractor
import app.cosmic.core.extractor.ytdlp.YtDlpPlaylistExtractor
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Front door for the download pipeline. Resolves the URL kind and dispatches
 * to the appropriate Extractor.
 *
 * **YouTube routing:** uses yt-dlp (via youtubedl-android) instead of
 * NewPipeExtractor. yt-dlp's actively-updated parser handles YouTube's
 * bot-detection signature/PoToken handshake; NewPipe loses that arms race
 * regularly. SoundCloud is fine on NewPipe — its anonymous-extraction
 * surface is much less hostile.
 */
@Singleton
class ExtractorRegistry @Inject constructor(
    private val resolver: LinkResolver,
    private val direct: DirectUrlExtractor,
    private val ytDlp: YtDlpExtractor,
    private val ytDlpMusic: YtDlpMusicExtractor,
    private val ytDlpPlaylist: YtDlpPlaylistExtractor,
    private val soundcloud: SoundCloudExtractorImpl,
    private val bandcamp: BandcampExtractor,
) {
    suspend fun extract(url: String): ExtractionResult {
        val kind = resolver.resolve(url)
        val extractor = pick(kind) ?: return ExtractionResult.Failure(
            "Unsupported source: $kind. Supported: YouTube, YT Music, SoundCloud, Bandcamp, direct URL.",
        )
        return extractor.extract(url)
    }

    private fun pick(kind: SourceKind): Extractor? = when (kind) {
        SourceKind.DIRECT -> direct
        SourceKind.YOUTUBE -> ytDlp
        SourceKind.YT_MUSIC -> ytDlpMusic
        SourceKind.YOUTUBE_PLAYLIST -> ytDlpPlaylist
        SourceKind.SOUNDCLOUD -> soundcloud
        SourceKind.BANDCAMP -> bandcamp
        SourceKind.UNKNOWN -> null
    }
}
