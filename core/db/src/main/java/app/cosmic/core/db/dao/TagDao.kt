package app.cosmic.core.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.cosmic.core.db.entity.Tag
import app.cosmic.core.db.entity.TrackTag
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {
    @Query("SELECT * FROM tags ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<Tag>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTag(tag: Tag): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun tagTrack(link: TrackTag)

    @Query("DELETE FROM track_tags WHERE trackId = :trackId AND tagId = :tagId")
    suspend fun untagTrack(trackId: Long, tagId: Long)

    @Query("SELECT tagId FROM track_tags WHERE trackId = :trackId")
    suspend fun tagsFor(trackId: Long): List<Long>
}
