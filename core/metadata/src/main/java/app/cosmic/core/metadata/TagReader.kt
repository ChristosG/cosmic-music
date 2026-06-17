package app.cosmic.core.metadata

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import java.io.File
import java.util.logging.Level
import java.util.logging.Logger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads file-side metadata that MediaStore doesn't surface — primarily
 * ReplayGain tags. Used at scan time to enrich Track rows.
 *
 * RG tags live as text fields named `REPLAYGAIN_TRACK_GAIN` /
 * `REPLAYGAIN_ALBUM_GAIN` in ID3v2 (TXXX), Vorbis comments (Opus/Ogg/FLAC),
 * and MP4/M4A user-data atoms. JAudioTagger normalises across containers
 * via `AudioFile.tag.getFirst(...)` — but tag names aren't in `FieldKey`,
 * so we read by raw key.
 */
@Singleton
class TagReader @Inject constructor() {

    init {
        // JAudioTagger is INFO-spammy; quiet it down on Android logcat.
        runCatching { Logger.getLogger("org.jaudiotagger").level = Level.WARNING }
    }

    data class FileMetadata(
        val replayGainTrackDb: Float?,
        val replayGainAlbumDb: Float?,
    )

    suspend fun read(path: String): FileMetadata = withContext(Dispatchers.IO) {
        try {
            val file = File(path)
            if (!file.exists() || !file.canRead()) return@withContext EMPTY
            val audio = AudioFileIO.read(file)
            val tag = audio.tag ?: return@withContext EMPTY
            FileMetadata(
                replayGainTrackDb = parseDb(rgValue(tag, "REPLAYGAIN_TRACK_GAIN")),
                replayGainAlbumDb = parseDb(rgValue(tag, "REPLAYGAIN_ALBUM_GAIN")),
            )
        } catch (t: Throwable) {
            Log.v(TAG, "tag read failed for $path: ${t.message}")
            EMPTY
        }
    }

    private fun rgValue(tag: org.jaudiotagger.tag.Tag, key: String): String? {
        // Try direct lookup first; falls back to FieldKey enums where the
        // mapping is supported (rare for RG but harmless).
        return runCatching { tag.getFirst(key) }.getOrNull()?.takeIf { it.isNotBlank() }
            ?: runCatching { tag.getFirst(FieldKey.valueOf(key)) }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    /** Parses values like "+3.45 dB", "-2 dB", "1.50". Returns null on bogus input. */
    private fun parseDb(raw: String?): Float? {
        if (raw.isNullOrBlank()) return null
        val cleaned = raw.replace("dB", "", ignoreCase = true).trim()
        return cleaned.toFloatOrNull()
    }

    private companion object {
        const val TAG = "TagReader"
        val EMPTY = FileMetadata(null, null)
    }
}
