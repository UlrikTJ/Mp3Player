package com.mp3player.data.dao

import androidx.room.*
import com.mp3player.data.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
abstract class MusicDao {

    // --- Song Queries ---
    @Query("SELECT * FROM songs ORDER BY dateAdded DESC")
    abstract fun getAllSongsFlow(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE id = :songId")
    abstract suspend fun getSongById(songId: Int): SongEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertSong(song: SongEntity): Long

    @Update
    abstract suspend fun updateSong(song: SongEntity)

    @Query("UPDATE songs SET baseWeight = :weight WHERE id = :songId")
    abstract suspend fun updateSongWeight(songId: Int, weight: Float)

    @Delete
    abstract suspend fun deleteSong(song: SongEntity)

    // --- Playlist Queries ---
    @Query("SELECT * FROM playlists ORDER BY name ASC")
    abstract fun getAllPlaylistsFlow(): Flow<List<PlaylistEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Delete
    abstract suspend fun deletePlaylist(playlist: PlaylistEntity)

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    abstract suspend fun deletePlaylistById(playlistId: Int)

    @Query("UPDATE playlists SET name = :newName WHERE id = :playlistId")
    abstract suspend fun renamePlaylist(playlistId: Int, newName: String)

    // --- PlaylistSong Junction Queries ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertPlaylistSongCrossRef(crossRef: PlaylistSongCrossRef)

    @Query("""
        SELECT * FROM songs 
        WHERE id NOT IN (SELECT songId FROM playlist_songs WHERE playlistId = :playlistId)
        ORDER BY title ASC
    """)
    abstract fun getSongsNotInPlaylistFlow(playlistId: Int): Flow<List<SongEntity>>

    @Query("""
        SELECT s.* FROM songs s
        INNER JOIN playlist_songs ps ON s.id = ps.songId
        WHERE ps.playlistId = :playlistId
        ORDER BY ps.position ASC
    """)
    abstract fun getSongsForPlaylistFlow(playlistId: Int): Flow<List<SongEntity>>

    @Query("""
        SELECT s.* FROM songs s
        INNER JOIN playlist_songs ps ON s.id = ps.songId
        WHERE ps.playlistId = :playlistId
        ORDER BY ps.position ASC
    """)
    abstract suspend fun getSongsForPlaylist(playlistId: Int): List<SongEntity>

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId AND songId = :songId")
    abstract suspend fun removeSongFromPlaylist(playlistId: Int, songId: Int)

    @Query("DELETE FROM playback_events WHERE playlistId = :playlistId")
    abstract suspend fun deletePlaybackEventsForPlaylist(playlistId: Int)

    // --- Stats & History Logging ---
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertPlaybackEvent(event: PlaybackEventEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertChainSkipEvent(event: ChainSkipEventEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertChainSkipDetails(details: List<ChainSkipDetailEntity>)

    @Transaction
    open suspend fun logChainSkip(keeperEvent: ChainSkipEventEntity, skippedSongs: List<Int>): Long {
        val eventId = insertChainSkipEvent(keeperEvent)
        val details = skippedSongs.mapIndexed { index, skippedId ->
            ChainSkipDetailEntity(
                chainSkipEventId = eventId,
                skippedSongId = skippedId,
                positionInChain = index + 1
            )
        }
        insertChainSkipDetails(details)
        return eventId
    }

    // --- Stats Calculation Models ---
    @Query("""
        SELECT 
            s.id as songId,
            s.title,
            s.artist,
            s.baseWeight,
            (SELECT COUNT(*) FROM playback_events pe WHERE pe.songId = s.id AND pe.outcome = 'COMPLETED') as playCount,
            (SELECT COUNT(*) FROM playback_events pe WHERE pe.songId = s.id AND pe.outcome IN ('SKIPPED', 'CHAIN_SKIPPED')) as skipCount,
            (SELECT COUNT(*) FROM chain_skip_events cse WHERE cse.keeperSongId = s.id) as keeperCount
        FROM songs s
    """)
    abstract fun getSongStatsFlow(): Flow<List<SongStats>>

    @Query("""
        SELECT 
            s.id as songId,
            s.title,
            s.artist,
            s.baseWeight,
            (SELECT COUNT(*) FROM playback_events pe WHERE pe.songId = s.id AND pe.playlistId = :playlistId AND pe.outcome = 'COMPLETED') as playCount,
            (SELECT COUNT(*) FROM playback_events pe WHERE pe.songId = s.id AND pe.playlistId = :playlistId AND pe.outcome IN ('SKIPPED', 'CHAIN_SKIPPED')) as skipCount,
            (SELECT COUNT(*) FROM chain_skip_events cse WHERE cse.keeperSongId = s.id AND cse.sessionId IN (SELECT DISTINCT pe2.sessionId FROM playback_events pe2 WHERE pe2.playlistId = :playlistId)) as keeperCount
        FROM songs s
        INNER JOIN playlist_songs ps ON s.id = ps.songId
        WHERE ps.playlistId = :playlistId
    """)
    abstract fun getPlaylistSongStatsFlow(playlistId: Int): Flow<List<SongStats>>

    @Query("""
        SELECT 
            s.id as songId,
            s.title,
            s.artist,
            COUNT(cse.id) as count
        FROM chain_skip_events cse
        INNER JOIN songs s ON cse.keeperSongId = s.id
        GROUP BY s.id
        ORDER BY count DESC
    """)
    abstract fun getKeepersLeaderboardFlow(): Flow<List<KeeperLeaderboardEntry>>

    @Query("""
        SELECT 
            s.id as songId,
            s.title,
            s.artist,
            COUNT(cse.id) as count
        FROM chain_skip_events cse
        INNER JOIN songs s ON cse.keeperSongId = s.id
        WHERE cse.sessionId IN (SELECT DISTINCT pe.sessionId FROM playback_events pe WHERE pe.playlistId = :playlistId)
        GROUP BY s.id
        ORDER BY count DESC
    """)
    abstract fun getPlaylistKeepersLeaderboardFlow(playlistId: Int): Flow<List<KeeperLeaderboardEntry>>
}

data class SongStats(
    val songId: Int,
    val title: String,
    val artist: String,
    val baseWeight: Float,
    val playCount: Int,
    val skipCount: Int,
    val keeperCount: Int
) {
    val totalPlays: Int get() = playCount + skipCount
    val skipRate: Float get() = if (totalPlays > 0) skipCount.toFloat() / totalPlays else 0f
}

data class KeeperLeaderboardEntry(
    val songId: Int,
    val title: String,
    val artist: String,
    val count: Int
)
