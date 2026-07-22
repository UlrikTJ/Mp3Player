package com.mp3player.playback

import com.mp3player.data.dao.MusicDao
import com.mp3player.data.entity.ChainSkipEventEntity
import com.mp3player.data.entity.PlaybackEventEntity
import com.mp3player.data.entity.SongEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

class PlaybackStatsTracker(
    private val musicDao: MusicDao,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    private var currentSong: SongEntity? = null
    private var startTimeMs: Long = 0L
    private var currentPlaylistId: Int? = null
    private var sessionId: String = UUID.randomUUID().toString()

    // Chain skip tracker state
    private val skipBuffer = mutableListOf<Int>() // List of skipped song IDs
    private var chainActive = false

    private val SKIP_THRESHOLD_MS = 10000L // 10 seconds
    private val MIN_CHAIN_LENGTH = 2

    fun onSessionStarted() {
        sessionId = UUID.randomUUID().toString()
        skipBuffer.clear()
        chainActive = false
    }

    fun onTrackStarted(song: SongEntity, playlistId: Int?) {
        currentSong = song
        currentPlaylistId = playlistId
        startTimeMs = System.currentTimeMillis()
    }

    fun onTrackEnded(completed: Boolean) {
        val song = currentSong ?: return
        if (song.id <= 0) return // Don't log stats for temporary YouTube streams
        
        // Reset state immediately to prevent double processing if called again quickly
        currentSong = null
        
        val endTimeMs = System.currentTimeMillis()
        val durationPlayed = endTimeMs - startTimeMs
        
        val totalDuration = song.durationMs
        val isSkip = !completed && (durationPlayed < SKIP_THRESHOLD_MS)

        scope.launch {
            try {
                if (isSkip) {
                    // Add to skip buffer and keep chain active
                    skipBuffer.add(song.id)
                    chainActive = true

                    // Log a regular skip event for now (it might get grouped into a chain-skip later)
                    musicDao.insertPlaybackEvent(
                        PlaybackEventEntity(
                            songId = song.id,
                            playlistId = currentPlaylistId,
                            durationPlayedMs = durationPlayed,
                            totalDurationMs = totalDuration,
                            outcome = "SKIPPED",
                            sessionId = sessionId
                        )
                    )
                } else {
                    // User stayed on the song! Check if we just completed a chain-skip
                    if (chainActive && skipBuffer.size >= MIN_CHAIN_LENGTH) {
                        // This song is the KEEPER
                        val keeperEvent = ChainSkipEventEntity(
                            keeperSongId = song.id,
                            chainLength = skipBuffer.size,
                            timestamp = endTimeMs,
                            sessionId = sessionId
                        )
                        
                        // Save the keeper chain transaction
                        musicDao.logChainSkip(keeperEvent, skipBuffer)
                        
                        // Mark outcomes in history
                        musicDao.insertPlaybackEvent(
                            PlaybackEventEntity(
                                songId = song.id,
                                playlistId = currentPlaylistId,
                                durationPlayedMs = durationPlayed,
                                totalDurationMs = totalDuration,
                                outcome = "COMPLETED",
                                sessionId = sessionId
                            )
                        )
                    } else {
                        // Simple completion / standard non-chain playback
                        musicDao.insertPlaybackEvent(
                            PlaybackEventEntity(
                                songId = song.id,
                                playlistId = currentPlaylistId,
                                durationPlayedMs = durationPlayed,
                                totalDurationMs = totalDuration,
                                outcome = if (completed) "COMPLETED" else "PARTIAL",
                                sessionId = sessionId
                            )
                        )
                    }

                    // Reset skip buffer state
                    skipBuffer.clear()
                    chainActive = false
                }
            } catch (e: Exception) {
                // If the song was deleted from DB while playing, the Foreign Key constraint will fail.
                // We catch this and ignore to avoid a crash.
                e.printStackTrace()
            }
        }
    }
}
