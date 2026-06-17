package app.cosmic.core.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import app.cosmic.core.db.entity.DownloadJob
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    @Query("SELECT * FROM download_jobs ORDER BY queuedAt DESC")
    fun observeAll(): Flow<List<DownloadJob>>

    @Insert
    suspend fun insert(job: DownloadJob): Long

    @Update
    suspend fun update(job: DownloadJob)

    @Query("DELETE FROM download_jobs WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM download_jobs WHERE id = :id")
    suspend fun get(id: Long): DownloadJob?
}
