package app.cosmic.core.extractor.newpipe

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request as NewPipeRequest
import org.schabi.newpipe.extractor.downloader.Response as NewPipeResponse

/**
 * NewPipeExtractor calls into this to make HTTP requests. We bridge to the
 * shared OkHttpClient so headers, redirects, and timeouts are consistent
 * across the app (and we don't pull in the legacy java.net stack).
 *
 * NewPipe sets a User-Agent on each request via headers; we honour it. If
 * none is provided, we set a Firefox-like UA that YouTube treats as a real
 * browser — without it the JS player config endpoint occasionally 403s.
 */
class CosmicNewPipeDownloader(private val client: OkHttpClient) : Downloader() {

    override fun execute(req: NewPipeRequest): NewPipeResponse {
        val builder = Request.Builder().url(req.url())
        var hasUa = false
        for ((name, values) in req.headers()) {
            if (name.equals("User-Agent", ignoreCase = true)) hasUa = true
            for (v in values) builder.addHeader(name, v)
        }
        if (!hasUa) builder.header("User-Agent", DEFAULT_UA)

        val method = req.httpMethod()
        val body = req.dataToSend()
        val rb = if (body != null) {
            val ct = req.headers()["Content-Type"]?.firstOrNull()?.toMediaTypeOrNull()
            body.toRequestBody(ct)
        } else null
        builder.method(method, rb)

        val res = client.newCall(builder.build()).execute()
        val responseHeaders = res.headers.toMultimap()
        val responseBody = res.body?.string() ?: ""
        return NewPipeResponse(
            res.code,
            res.message,
            responseHeaders,
            responseBody,
            res.request.url.toString(),
        )
    }

    companion object {
        const val DEFAULT_UA =
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36"
    }
}
