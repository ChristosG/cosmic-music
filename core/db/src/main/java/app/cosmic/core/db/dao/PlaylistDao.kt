package app.cosmic.core.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import app.cosmic.core.db.entity.Playlist
import app.cosmic.core.db.entity.PlaylistTrack
import app.cosmic.core.db.entity.Track
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<Playlist>>

    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun get(id: Long): Playlist?

    @Insert
    suspend fun insertPlaylist(playlist: Playlist): Long

    @Update
    suspend fun updatePlaylist(playlist: Playlist)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylist(id: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addPlaylistTrack(playlistTrack: PlaylistTrack)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun removeTrack(playlistId: Long, trackId: Long)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun clearPlaylist(playlistId: Long)

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun nextPositionFor(playlistId: Long): Int

    @Query("SELECT COUNT(*) FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun trackCount(playlistId: Long): Int

    @Transaction
    suspend fun appendTrack(playlistId: Long, trackId: Long) {
        val pos = nextPositionFor(playlistId)
        addPlaylistTrack(PlaylistTrack(playlistId = playlistId, trackId = trackId, position = pos))
    }

    @Transaction
    @Query("""
        SELECT t.* FROM tracks t
        INNER JOIN playlist_tracks pt ON pt.trackId = t.id
        WHERE pt.playlistId = :playlistId
        ORDER BY pt.position
    """)
    fun observeTracks(playlistId: Long): Flow<List<Track>>

    @Query("""
        SELECT COUNT(*) FROM playlist_tracks WHERE playlistId IN (:playlistIds)
    """)
    suspend fun totalTracksAcross(playlistIds: List<Long>): Int
}
