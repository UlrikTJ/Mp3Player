package com.mp3player.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(tableName = "songs")
data class SongEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val artist: String,
    val album: String,
    val filePath: String,
    val artworkPath: String?,
    val durationMs: Long,
    val baseWeight: Float = 1.0f,
    val source: String, // "LOCAL" or "YOUTUBE"
    val youtubeVideoId: String?,
    val dateAdded: Long = System.currentTimeMillis()
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "playlist_songs",
    primaryKeys = ["playlistId", "songId"],
    foreignKeys = [
        ForeignKey(entity = PlaylistEntity::class, parentColumns = ["id"], childColumns = ["playlistId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = SongEntity::class, parentColumns = ["id"], childColumns = ["songId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("songId")]
)
data class PlaylistSongCrossRef(
    val playlistId: Int,
    val songId: Int,
    val position: Int
)

@Entity(
    tableName = "playback_events",
    foreignKeys = [
        ForeignKey(entity = SongEntity::class, parentColumns = ["id"], childColumns = ["songId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("songId")]
)
data class PlaybackEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val songId: Int,
    val playlistId: Int?,
    val timestamp: Long = System.currentTimeMillis(),
    val durationPlayedMs: Long,
    val totalDurationMs: Long,
    val outcome: String, // "COMPLETED", "SKIPPED", "CHAIN_SKIPPED"
    val sessionId: String
)

@Entity(
    tableName = "chain_skip_events",
    foreignKeys = [
        ForeignKey(entity = SongEntity::class, parentColumns = ["id"], childColumns = ["keeperSongId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("keeperSongId")]
)
data class ChainSkipEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val keeperSongId: Int,
    val chainLength: Int,
    val timestamp: Long = System.currentTimeMillis(),
    val sessionId: String
)

@Entity(
    tableName = "chain_skip_details",
    foreignKeys = [
        ForeignKey(entity = ChainSkipEventEntity::class, parentColumns = ["id"], childColumns = ["chainSkipEventId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = SongEntity::class, parentColumns = ["id"], childColumns = ["skippedSongId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("chainSkipEventId"), Index("skippedSongId")]
)
data class ChainSkipDetailEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val chainSkipEventId: Long,
    val skippedSongId: Int,
    val positionInChain: Int
)

@Entity(tableName = "ignored_files")
data class IgnoredFileEntity(
    @PrimaryKey val filePath: String,
    val dateIgnored: Long = System.currentTimeMillis()
)
