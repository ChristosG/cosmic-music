package app.cosmic.core.metadata

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import app.cosmic.core.db.dao.TrackDao
import app.cosmic.core.db.entity.Track
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads the system MediaStore and reconciles the Track table with what's
 * actually on disk. We trust MediaStore as the source of truth for "what
 * audio files exist" — files added via download, file explorer, ADB, or
 * any other app all flow through it.
 *
 * Pass [scopeRelativePath] = null to scan all audio; pass "Music/Cosmic/" to
 * scope to the app's directory. Default is null so user-imported tracks
 * outside the Cosmic folder also show up.
 */
@Singleton
class MediaStoreScanner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val trackDao: TrackDao,
    private val tagReader: TagReader,
) {

    data class ScanResult(val added: Int, val updated: Int, val removed: Int)

    suspend fun scan(scopeRelativePath: String? = null): ScanResult = withContext(Dispatchers.IO) {
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.BITRATE.takeIf { Build.VERSION.SDK_INT >= Build.VERSION_CODES.R },
            MediaStore.Audio.Media.RELATIVE_PATH.takeIf { Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q },
        ).filterNotNull().toTypedArray()

        // Filter: must be marked as music, optionally constrained to a relative path prefix.
        val (selection, args) = buildSelection(scopeRelativePath)

        val seenIds = mutableSetOf<Long>()
        var added = 0
        var updated = 0

        context.contentResolver.query(collection, projection, selection, args, null)?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dataCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val mimeCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
            val bitrateCol = c.getColumnIndex(MediaStore.Audio.Media.BITRATE) // -1 if not present

            val now = System.currentTimeMillis()
            val toUpsert = mutableListOf<Track>()

            while (c.moveToNext()) {
                val msId = c.getLong(idCol)
                seenIds += msId
                val existing = trackDao.getByMediaStoreId(msId)
                val rawPath = c.getString(dataCol)
                // Read RG tags only when we don't have them cached (first
                // import of a track). Re-reading on every scan would slow it
                // down without giving new info — RG tags don't change at rest.
                val rg = if (
                    existing?.replayGainTrackDb == null &&
                    existing?.replayGainAlbumDb == null &&
                    rawPath != null
                ) {
                    tagReader.read(rawPath)
                } else {
                    TagReader.FileMetadata(existing?.replayGainTrackDb, existing?.replayGainAlbumDb)
                }
                val track = Track(
                    id = existing?.id ?: 0L,
                    mediaStoreId = msId,
                    title = c.getString(titleCol) ?: "Unknown",
                    artist = c.getString(artistCol),
                    album = c.getString(albumCol),
                    durationMs = c.getLong(durationCol),
                    filePath = rawPath ?: ContentUris.withAppendedId(collection, msId).toString(),
                    codec = c.getString(mimeCol),
                    bitrate = if (bitrateCol >= 0) c.getInt(bitrateCol).takeIf { it > 0 } else null,
                    sourceUrl = existing?.sourceUrl,
                    sourceKind = existing?.sourceKind,
                    addedAt = existing?.addedAt ?: now,
                    replayGainTrackDb = rg.replayGainTrackDb,
                    replayGainAlbumDb = rg.replayGainAlbumDb,
                    embeddedArtUri = existing?.embeddedArtUri,
                )
                toUpsert += track
                if (existing == null) added++ else updated++
            }
            if (toUpsert.isNotEmpty()) trackDao.upsertAll(toUpsert)
        }

        // Drop tracks whose mediaStoreId no longer exists.
        val knownIds = trackDao.allMediaStoreIds()
        val staleIds = knownIds.filterNot { it in seenIds }
        if (staleIds.isNotEmpty()) trackDao.deleteByMediaStoreIds(staleIds)

        ScanResult(added = added, updated = updated, removed = staleIds.size)
    }

    private fun buildSelection(scopeRelativePath: String?): Pair<String, Array<String>?> {
        val baseSel = "${MediaStore.Audio.Media.IS_MUSIC} = 1"
        return if (scopeRelativePath != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "$baseSel AND ${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ?" to arrayOf("$scopeRelativePath%")
        } else {
            baseSel to null
        }
    }
}
