package app.cosmic.core.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import app.cosmic.core.db.dao.DownloadDao
import app.cosmic.core.db.dao.LyricsDao
import app.cosmic.core.db.dao.PlayHistoryDao
import app.cosmic.core.db.dao.PlaylistDao
import app.cosmic.core.db.dao.TagDao
import app.cosmic.core.db.dao.TrackDao
import app.cosmic.core.db.entity.DownloadJob
import app.cosmic.core.db.entity.LyricsCache
import app.cosmic.core.db.entity.PlayHistory
import app.cosmic.core.db.entity.Playlist
import app.cosmic.core.db.entity.PlaylistTrack
import app.cosmic.core.db.entity.Tag
import app.cosmic.core.db.entity.Track
import app.cosmic.core.db.entity.TrackTag

@Database(
    entities = [
        Track::class,
        Playlist::class,
        PlaylistTrack::class,
        PlayHistory::class,
        Tag::class,
        TrackTag::class,
        DownloadJob::class,
        LyricsCache::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class CosmicDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun playHistoryDao(): PlayHistoryDao
    abstract fun tagDao(): TagDao
    abstract fun downloadDao(): DownloadDao
    abstract fun lyricsDao(): LyricsDao

    companion object {
        const val DB_NAME = "cosmic.db"

        /**
         * v1 → v2: adds `download_jobs.title` so the Downloads screen can
         * show the resolved track title instead of the raw source URL.
         * Nullable column → no data migration needed; rows without a title
         * keep falling back to sourceUrl in the UI.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE download_jobs ADD COLUMN title TEXT DEFAULT NULL")
            }
        }
    }
}
