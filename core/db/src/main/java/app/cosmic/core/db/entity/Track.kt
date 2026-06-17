package app.cosmic.core.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tracks",
    indices = [
        Index(value = ["mediaStoreId"], unique = true),
        Index(value = ["filePath"], unique = true),
        Index(value = ["artist"]),
        Index(value = ["album"]),
        Index(value = ["title"]),
    ],
)
data class Track(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mediaStoreId: Long?,
    val title: String,
    val artist: String?,
    val album: String?,
    val durationMs: Long,
    val filePath: String,
    val codec: String?,
    val bitrate: Int?,
    val sourceUrl: String?,
    val sourceKind: String?,
    val addedAt: Long,
    val replayGainTrackDb: Float?,
    val replayGainAlbumDb: Float?,
    val embeddedArtUri: String?,
)
