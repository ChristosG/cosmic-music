package app.cosmic.core.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import app.cosmic.core.db.entity.LyricsCache

@Dao
interface LyricsDao {
    @Query("SELECT * FROM lyrics_cache WHERE trackId = :trackId")
    suspend fun get(trackId: Long): LyricsCache?

    @Upsert
    suspend fun upsert(entry: LyricsCache)
}
