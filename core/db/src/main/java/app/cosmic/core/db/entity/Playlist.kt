package app.cosmic.core.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "playlists")
data class Playlist(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
    val sortMode: String = "manual",
)

@Entity(
    tableName = "playlist_tracks",
    primaryKeys = ["playlistId", "trackId"],
    foreignKeys = [
        ForeignKey(Playlist::class, ["id"], ["playlistId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(Track::class, ["id"], ["trackId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("trackId")],
)
data class PlaylistTrack(
    val playlistId: Long,
    val trackId: Long,
    val position: Int,
)
