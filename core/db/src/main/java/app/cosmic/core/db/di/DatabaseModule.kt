package app.cosmic.core.db.di

import android.content.Context
import androidx.room.Room
import app.cosmic.core.db.CosmicDatabase
import app.cosmic.core.db.dao.DownloadDao
import app.cosmic.core.db.dao.LyricsDao
import app.cosmic.core.db.dao.PlayHistoryDao
import app.cosmic.core.db.dao.PlaylistDao
import app.cosmic.core.db.dao.TagDao
import app.cosmic.core.db.dao.TrackDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): CosmicDatabase =
        Room.databaseBuilder(context, CosmicDatabase::class.java, CosmicDatabase.DB_NAME)
            .addMigrations(CosmicDatabase.MIGRATION_1_2)
            // Library + playlists are user-generated content. If a schema
            // mismatch ever escapes the migration list, prefer crashing
            // loudly to silently nuking the user's data.
            .build()

    @Provides fun trackDao(db: CosmicDatabase): TrackDao = db.trackDao()
    @Provides fun playlistDao(db: CosmicDatabase): PlaylistDao = db.playlistDao()
    @Provides fun playHistoryDao(db: CosmicDatabase): PlayHistoryDao = db.playHistoryDao()
    @Provides fun tagDao(db: CosmicDatabase): TagDao = db.tagDao()
    @Provides fun downloadDao(db: CosmicDatabase): DownloadDao = db.downloadDao()
    @Provides fun lyricsDao(db: CosmicDatabase): LyricsDao = db.lyricsDao()
}
