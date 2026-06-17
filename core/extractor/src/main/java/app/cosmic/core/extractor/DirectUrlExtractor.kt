package app.cosmic.core.extractor

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves an arbitrary http(s) URL into a DownloadCandidate by issuing a
 * HEAD (falling back to range-GET if HEAD is rejected) and reading
 * Content-Type / Content-Length. The actual byte-streaming happens in the
 * download worker, not here — keeping resolution cheap and cancelable.
 */
@Singleton
class DirectUrlExtractor @Inject constructor(
    private val client: OkHttpClient,
) : Extractor {

    override val handles: SourceKind = SourceKind.DIRECT

    override suspend fun extract(url: String): ExtractionResult = withContext(Dispatchers.IO) {
        try {
            val resolved = head(url) ?: rangeGet(url)
                ?: return@withContext ExtractionResult.Failure("Could not reach $url")

            val mime = resolved.contentType?.lowercase()
            if (mime != null && !mime.startsWith("audio/") && !mime.startsWith("application/octet-stream")) {
                return@withContext ExtractionResult.Failure("URL is not audio: content-type=$mime")
            }
            val codec = mime?.codecHint()
            val filename = filenameFrom(resolved.finalUrl, mime)

            ExtractionResult.Success(
                listOf(
                    DownloadCandidate(
                        sourceUrl = url,
                        sourceKind = SourceKind.DIRECT,
                        streamUrl = resolved.finalUrl,
                        mimeType = mime,
                        codec = codec,
                        bitrateKbps = null,
                        sizeBytes = resolved.contentLength,
                        title = filename.substringBeforeLast('.').ifBlank { "Imported track" },
                        artist = null,
                        album = null,
                        durationMs = null,
                        thumbnailUrl = null,
                        filename = filename,
                    ),
                ),
            )
        } catch (t: Throwable) {
            ExtractionResult.Failure("Direct URL extract failed: ${t.message}", t)
        }
    }

    private data class Resolved(val finalUrl: String, val contentType: String?, val contentLength: Long?)

    private fun head(url: String): Resolved? {
        val req = Request.Builder().url(url).head().build()
        return client.newCall(req).execute().use { res ->
            if (!res.isSuccessful) return null
            Resolved(
                finalUrl = res.request.url.toString(),
                contentType = res.header("Content-Type"),
                contentLength = res.header("Content-Length")?.toLongOrNull(),
            )
        }
    }

    private fun rangeGet(url: String): Resolved? {
        val req = Request.Builder().url(url).header("Range", "bytes=0-0").build()
        return client.newCall(req).execute().use { res ->
            if (!res.isSuccessful && res.code != 206) return null
            // For 206, the full size is in Content-Range "bytes 0-0/<total>"; otherwise null.
            val total = res.header("Content-Range")?.substringAfterLast('/')?.toLongOrNull()
            Resolved(
                finalUrl = res.request.url.toString(),
                contentType = res.header("Content-Type"),
                contentLength = total,
            )
        }
    }

    private fun filenameFrom(url: String, mime: String?): String {
        val raw = url.substringAfterLast('/').substringBefore('?').ifBlank { "track" }
        val hasExt = raw.contains('.')
        return if (hasExt) raw else raw + (mime?.toExt() ?: ".mp3")
    }

    private fun String.codecHint(): String? = when {
        startsWith("audio/mpeg") -> "mp3"
        startsWith("audio/mp4") || startsWith("audio/aac") || startsWith("audio/x-m4a") -> "aac"
        startsWith("audio/ogg") || startsWith("audio/opus") || startsWith("audio/webm") -> "opus"
        startsWith("audio/flac") -> "flac"
        startsWith("audio/wav") || startsWith("audio/x-wav") -> "wav"
        else -> null
    }

    private fun String.toExt(): String = when {
        startsWith("audio/mpeg") -> ".mp3"
        startsWith("audio/mp4") || startsWith("audio/x-m4a") -> ".m4a"
        startsWith("audio/aac") -> ".aac"
        startsWith("audio/ogg") || startsWith("audio/opus") -> ".opus"
        startsWith("audio/webm") -> ".webm"
        startsWith("audio/flac") -> ".flac"
        startsWith("audio/wav") || startsWith("audio/x-wav") -> ".wav"
        else -> ".bin"
    }
}
