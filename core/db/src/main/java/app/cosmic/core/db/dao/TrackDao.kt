package app.cosmic.core.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import app.cosmic.core.db.entity.Track
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {
    @Query("SELECT * FROM tracks ORDER BY title COLLATE NOCASE")
    fun observeAll(): Flow<List<Track>>

    @Query("SELECT * FROM tracks WHERE id = :id")
    suspend fun getById(id: Long): Track?

    @Query("SELECT * FROM tracks WHERE mediaStoreId = :mediaStoreId LIMIT 1")
    suspend fun getByMediaStoreId(mediaStoreId: Long): Track?

    @Query("SELECT mediaStoreId FROM tracks WHERE mediaStoreId IS NOT NULL")
    suspend fun allMediaStoreIds(): List<Long>

    @Upsert
    suspend fun upsert(track: Track): Long

    @Upsert
    suspend fun upsertAll(tracks: List<Track>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(track: Track): Long

    @Update
    suspend fun update(track: Track)

    @Query("DELETE FROM tracks WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("DELETE FROM tracks WHERE mediaStoreId IN (:mediaStoreIds)")
    suspend fun deleteByMediaStoreIds(mediaStoreIds: List<Long>)

    @Query("SELECT COUNT(*) FROM tracks")
    suspend fun count(): Int
}
