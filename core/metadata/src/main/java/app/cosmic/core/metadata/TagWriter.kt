package app.cosmic.core.metadata

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import app.cosmic.core.db.dao.TrackDao
import app.cosmic.core.db.entity.Track
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Writes title / artist / album back into the audio file's tag and
 * propagates the change to MediaStore + the Cosmic Track row.
 *
 * Why this exists: tracks downloaded with `<unknown>` artist (e.g. before
 * the title-parser fix landed) are stuck displaying that bad metadata until
 * the underlying file is re-tagged. Re-tagging is what file-explorer-style
 * apps do — JAudioTagger handles ID3v2 / Vorbis comments / MP4 atoms via
 * one API and writes in place.
 *
 * The MediaStore re-scan is critical: without it, MediaStore caches the
 * old metadata and our scanner picks up the stale values.
 */
@Singleton
class TagWriter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val trackDao: TrackDao,
) {

    data class Edit(
        val title: String?,
        val artist: String?,
        val album: String?,
    )

    /**
     * Writes the edit to the file referenced by [track], updates the Track
     * row, and pings MediaScannerConnection so the system MediaStore
     * picks up the new tags.
     *
     * Returns true on success. Common failure modes: file not writable
     * (scoped storage), file format JAudioTagger can't tag (rare for the
     * codecs we ingest).
     */
    suspend fun apply(track: Track, edit: Edit): Boolean = withContext(Dispatchers.IO) {
        val path = track.filePath.takeIf { it.isNotBlank() && !it.startsWith("content://") }
        if (path == null) {
            Log.w(TAG, "no writable file path for track ${track.id} (${track.filePath})")
            return@withContext false
        }
        val file = File(path)
        if (!file.exists() || !file.canWrite()) {
            Log.w(TAG, "${file.absolutePath} not writable")
            return@withContext false
        }

        val ok = runCatching {
            val audio = AudioFileIO.read(file)
            val tag = audio.tagOrCreateAndSetDefault
            edit.title?.takeIf { it.isNotBlank() }?.let { tag.setField(FieldKey.TITLE, it) }
            edit.artist?.takeIf { it.isNotBlank() }?.let {
                tag.setField(FieldKey.ARTIST, it)
                // ALBUM_ARTIST mirrors ARTIST when not explicitly different.
                runCatching { tag.setField(FieldKey.ALBUM_ARTIST, it) }
            }
            edit.album?.takeIf { it.isNotBlank() }?.let { tag.setField(FieldKey.ALBUM, it) }
            audio.commit()
            true
        }.onFailure { Log.e(TAG, "tag write failed", it) }.getOrDefault(false)

        if (!ok) return@withContext false

        // Update the DB row immediately so the UI reflects new values
        // without waiting for the MediaStore round-trip.
        val updated = track.copy(
            title = edit.title?.takeIf { it.isNotBlank() } ?: track.title,
            artist = edit.artist?.takeIf { it.isNotBlank() } ?: track.artist,
            album = edit.album?.takeIf { it.isNotBlank() } ?: track.album,
        )
        runCatching { trackDao.update(updated) }

        // Update MediaStore via ContentResolver (immediate, no scanner wait).
        track.mediaStoreId?.let { msId ->
            runCatching {
                val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                } else {
                    @Suppress("DEPRECATION") MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                }
                val values = ContentValues().apply {
                    edit.title?.takeIf { it.isNotBlank() }?.let { put(MediaStore.Audio.Media.TITLE, it) }
                    edit.artist?.takeIf { it.isNotBlank() }?.let { put(MediaStore.Audio.Media.ARTIST, it) }
                    edit.album?.takeIf { it.isNotBlank() }?.let { put(MediaStore.Audio.Media.ALBUM, it) }
                }
                if (values.size() > 0) {
                    context.contentResolver.update(
                        ContentUris.withAppendedId(collection, msId),
                        values,
                        null,
                        null,
                    )
                }
            }
        }

        // Belt-and-suspenders: ask the system to re-scan the file so other
        // music apps notice the change too.
        runCatching {
            MediaScannerConnection.scanFile(
                context,
                arrayOf(file.absolutePath),
                null,
                null,
            )
        }
        true
    }

    private companion object { const val TAG = "TagWriter" }
}
