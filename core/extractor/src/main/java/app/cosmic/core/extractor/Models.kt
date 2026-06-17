package app.cosmic.core.extractor

/** Where a download is sourced from. Drives which Extractor handles it. */
enum class SourceKind { DIRECT, YOUTUBE, YOUTUBE_PLAYLIST, YT_MUSIC, SOUNDCLOUD, BANDCAMP, UNKNOWN }

/**
 * Resolved metadata + stream URL ready to feed to the download worker.
 * One source URL may produce multiple candidates (e.g. a YT playlist) — the
 * download repository will fan out into one DownloadJob per candidate.
 */
data class DownloadCandidate(
    val sourceUrl: String,
    val sourceKind: SourceKind,
    val streamUrl: String,
    val mimeType: String?,           // e.g. "audio/mpeg", "audio/webm" — used for filename + tagging
    val codec: String?,              // human-readable: "mp3", "opus", "aac"
    val bitrateKbps: Int?,           // null if unknown
    val sizeBytes: Long?,            // null if unknown (chunked transfer)
    val title: String,
    val artist: String?,
    val album: String?,
    val durationMs: Long?,
    val thumbnailUrl: String?,
    /** Suggested filename (without directory). Caller may sanitise further. */
    val filename: String,
)

sealed class ExtractionResult {
    data class Success(val candidates: List<DownloadCandidate>) : ExtractionResult()
    data class Failure(val message: String, val cause: Throwable? = null) : ExtractionResult()
}

/** A source-specific resolver. Implementations live in this module. */
interface Extractor {
    val handles: SourceKind
    suspend fun extract(url: String): ExtractionResult
}
