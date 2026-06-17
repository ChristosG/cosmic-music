package app.cosmic.core.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import app.cosmic.core.db.entity.PlayHistory

data class TrackPlayStat(
    val trackId: Long,
    val playCount: Int,
    val skipCount: Int,
    val lastPlayedAt: Long?,
)

@Dao
interface PlayHistoryDao {
    @Insert
    suspend fun log(entry: PlayHistory)

    @Query("""
        SELECT trackId,
               SUM(CASE WHEN completed = 1 THEN 1 ELSE 0 END) AS playCount,
               SUM(CASE WHEN completed = 0 THEN 1 ELSE 0 END) AS skipCount,
               MAX(playedAt) AS lastPlayedAt
        FROM play_history
        GROUP BY trackId
    """)
    suspend fun stats(): List<TrackPlayStat>
}
