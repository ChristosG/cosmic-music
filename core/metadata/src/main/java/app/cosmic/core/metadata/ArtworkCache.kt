package app.cosmic.core.metadata

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Downloads remote thumbnail images (YouTube, SoundCloud, Bandcamp covers)
 * into the app's private cache directory and returns a stable file:// URI
 * that Coil can render.
 *
 * Why a separate cache instead of writing only into the audio file:
 *   - Coil decodes a small JPEG much faster than parsing an Opus / m4a tag
 *     to extract embedded art.
 *   - The cache is content-addressed by URL hash, so two tracks sharing a
 *     thumbnail (album re-uses) only store it once.
 *   - The audio-file embed is still desirable for portability to other
 *     players, but lives separately in TagWriter — see writeArtwork there
 *     when we add it.
 */
@Singleton
class ArtworkCache @Inject constructor(
    @ApplicationContext private val context: Context,
    private val httpClient: OkHttpClient,
) {

    /**
     * Downloads [url] into the cache and returns a `file://` URI for it,
     * or null if the URL is blank or the fetch fails. Idempotent: a second
     * call with the same URL returns the existing cached file without
     * re-downloading.
     */
    suspend fun cacheRemote(url: String?): String? = withContext(Dispatchers.IO) {
        val src = url?.trim()?.takeIf { it.isNotBlank() } ?: return@withContext null
        val target = fileFor(src)
        if (target.exists() && target.length() > 0) return@withContext target.toUri()

        runCatching {
            val req = Request.Builder().url(src).build()
            httpClient.newCall(req).execute().use { res ->
                if (!res.isSuccessful) return@runCatching null
                val body = res.body ?: return@runCatching null
                target.parentFile?.mkdirs()
                target.outputStream().use { out -> body.byteStream().copyTo(out) }
                target.toUri()
            }
        }.onFailure {
            Log.w(TAG, "thumbnail cache failed for $src", it)
        }.getOrNull()
    }

    private fun fileFor(url: String): File {
        // SHA-1 is fine here — we're keying a cache, not a security boundary.
        val hash = MessageDigest.getInstance("SHA-1")
            .digest(url.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return File(artDir(), "$hash.jpg")
    }

    private fun artDir(): File = File(context.cacheDir, "artwork").apply { mkdirs() }

    private fun File.toUri(): String = "file://${absolutePath}"

    private companion object { const val TAG = "ArtworkCache" }
}
