package app.cosmic.core.download

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.io.InputStream

/**
 * An [InputStream] that reads an HTTP resource as a sequence of short
 * `Range:` requests instead of one long-lived `GET`.
 *
 * WHY THIS EXISTS — the "slow download" fix:
 * YouTube's `googlevideo.com` CDN throttles a *single* connection down to
 * roughly real-time playback speed (and often far slower, ~30–60 KB/s). The
 * throttle is applied progressively *within one response* — the longer a
 * connection streams, the harder it's clamped. By splitting the file into
 * many small ranged requests, each request stays inside the server's initial
 * "burst" allowance, so aggregate throughput jumps back to full line speed.
 * This is the same mechanism behind yt-dlp's `--http-chunk-size`.
 *
 * The stream is strictly sequential (no concurrency) so the downstream
 * [MediaStoreWriter] copy loop and progress reporting are completely
 * unchanged — it just reads bytes in order, faster.
 *
 * Graceful degradation: if the server ignores `Range` and answers `200` with
 * the full body, we transparently stream that single body to completion.
 */
internal class RangedHttpInputStream(
    private val client: OkHttpClient,
    private val url: String,
    private val chunkSize: Long = DEFAULT_CHUNK_SIZE,
) : InputStream() {

    private var current: InputStream? = null
    private var nextOffset: Long = 0L
    /** Total resource length once known (from Content-Range), else -1. */
    private var totalLength: Long = -1L
    /** Set when the server ignored Range and handed us the whole file at once. */
    private var singleShot: Boolean = false
    private var finished: Boolean = false

    override fun read(): Int {
        val one = ByteArray(1)
        val n = read(one, 0, 1)
        return if (n == -1) -1 else one[0].toInt() and 0xFF
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (finished) return -1
        while (true) {
            val stream = current ?: openNextChunk() ?: run { finished = true; return -1 }
            val n = stream.read(b, off, len)
            if (n != -1) return n
            // This chunk is exhausted; advance.
            stream.close()
            current = null
            if (singleShot || (totalLength in 0..nextOffset)) {
                finished = true
                return -1
            }
        }
    }

    /** Opens the next ranged chunk, or returns null if there's nothing left. */
    private fun openNextChunk(): InputStream? {
        if (totalLength in 0..nextOffset) return null

        val end = if (chunkSize > 0) nextOffset + chunkSize - 1 else Long.MAX_VALUE
        val req = Request.Builder()
            .url(url)
            .header("Range", "bytes=$nextOffset-$end")
            .build()

        val res = client.newCall(req).execute()
        if (!res.isSuccessful) {
            res.close()
            throw IOException("Ranged GET failed: HTTP ${res.code} at offset $nextOffset")
        }

        val body = res.body ?: run {
            res.close()
            throw IOException("Empty body on ranged GET at offset $nextOffset")
        }

        when (res.code) {
            206 -> {
                // Partial content — parse total size from "bytes start-end/total".
                if (totalLength < 0) {
                    totalLength = res.header("Content-Range")
                        ?.substringAfter('/', "")
                        ?.toLongOrNull()
                        ?: -1L
                }
                val len = body.contentLength()
                nextOffset += if (len > 0) len else chunkSize
            }
            200 -> {
                // Server ignored Range: this body is the entire file. Stream it
                // once and stop — no point issuing further requests.
                singleShot = true
            }
        }

        current = body.byteStream()
        return current
    }

    override fun close() {
        finished = true
        runCatching { current?.close() }
        current = null
    }

    companion object {
        /**
         * 4 MiB keeps each request short enough to dodge the progressive
         * throttle while large enough that per-request overhead stays
         * negligible. A typical 4-minute song is ~6–10 MiB → 2–3 requests.
         */
        const val DEFAULT_CHUNK_SIZE: Long = 4L * 1024 * 1024
    }
}
