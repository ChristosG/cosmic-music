package app.cosmic.core.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "lyrics_cache",
    foreignKeys = [
        ForeignKey(Track::class, ["id"], ["trackId"], onDelete = ForeignKey.CASCADE),
    ],
)
data class LyricsCache(
    @PrimaryKey val trackId: Long,
    val lrcText: String?,
    val plainText: String?,
    val source: String,
    val fetchedAt: Long,
)
