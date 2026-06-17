package app.cosmic.core.download

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import app.cosmic.core.common.CosmicPaths
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Writes a downloaded audio stream into MediaStore under
 * Music/Cosmic/<Artist>/<filename>. Uses the IS_PENDING flow on API 29+ so
 * other apps don't see partial files; on the legacy path falls back to a
 * direct file write inside the public Music directory.
 *
 * The returned Uri is a content:// pointer suitable for MediaItem playback,
 * and the persisted file is visible to any file explorer or other audio app.
 */
@Singleton
class MediaStoreWriter @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    data class Written(val uri: Uri, val mediaStoreId: Long, val displayName: String, val relativePath: String)

    /**
     * @param onProgress fraction in [0..1]; emitted ~10x per second worst case.
     *                   totalBytes may be null (chunked transfer); progress is then 0f.
     */
    suspend fun write(
        input: InputStream,
        filename: String,
        artist: String?,
        mimeType: String?,
        totalBytes: Long?,
        onProgress: suspend (Float) -> Unit,
    ): Written {
        require(filename.isNotBlank()) { "filename must be non-blank" }
        val safeArtist = sanitisePathSegment(artist?.takeIf { it.isNotBlank() } ?: "Unknown Artist")
        val safeName = sanitiseFilename(filename)
        val relPath = "${CosmicPaths.LIBRARY_RELATIVE_PATH}$safeArtist/"

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            @Suppress("DEPRECATION") MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        val resolver = context.contentResolver

        fun buildValues(includeMime: Boolean) = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, safeName)
            if (includeMime) mimeType?.let { put(MediaStore.Audio.Media.MIME_TYPE, it) }
            artist?.let { put(MediaStore.Audio.Media.ARTIST, it) }
            put(MediaStore.Audio.Media.IS_MUSIC, 1)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Audio.Media.RELATIVE_PATH, relPath)
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
        }

        // Some OEM MediaProviders (Samsung notably) reject MIMEs they don't
        // recognize for the Audio collection — e.g. audio/webm. Retry
        // without the MIME column so MediaStore infers from the extension.
        val uri = try {
            resolver.insert(collection, buildValues(includeMime = true))
        } catch (t: IllegalArgumentException) {
            resolver.insert(collection, buildValues(includeMime = false))
        } ?: try {
            resolver.insert(collection, buildValues(includeMime = false))
        } catch (t: IllegalArgumentException) {
            null
        } ?: error("MediaStore refused to allocate a row for $safeName")

        try {
            resolver.openOutputStream(uri, "w")?.use { os ->
                val buf = ByteArray(64 * 1024)
                var read: Int
                var written: Long = 0
                var lastTickPct = -1
                while (input.read(buf).also { read = it } != -1) {
                    os.write(buf, 0, read)
                    written += read
                    if (totalBytes != null && totalBytes > 0) {
                        val pct = ((written * 100) / totalBytes).toInt().coerceIn(0, 100)
                        if (pct != lastTickPct) {
                            lastTickPct = pct
                            onProgress(pct / 100f)
                        }
                    }
                }
                os.flush()
            } ?: error("openOutputStream returned null for $uri")
        } catch (t: Throwable) {
            // Roll back the pending insert so we don't leave zombie rows.
            runCatching { resolver.delete(uri, null, null) }
            throw t
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val finalize = ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }
            resolver.update(uri, finalize, null, null)
        }

        val id = uri.lastPathSegment?.toLongOrNull() ?: -1L
        return Written(uri = uri, mediaStoreId = id, displayName = safeName, relativePath = relPath)
    }

    private fun sanitisePathSegment(s: String): String =
        s.replace(Regex("""[\\/:*?"<>|]"""), "_").trim().take(120)

    private fun sanitiseFilename(s: String): String =
        s.replace(Regex("""[\\/:*?"<>|]"""), "_").trim().take(180)
}
