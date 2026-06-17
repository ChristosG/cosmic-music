package app.cosmic.core.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "play_history",
    foreignKeys = [
        ForeignKey(Track::class, ["id"], ["trackId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("trackId"), Index("playedAt")],
)
data class PlayHistory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackId: Long,
    val playedAt: Long,
    val completed: Boolean,
    val msListened: Long,
)
