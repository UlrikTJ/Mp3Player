package com.mp3player.playback

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.mp3player.data.entity.SongEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.max
import kotlin.math.min

class CrossfadePlayerManager(
    private val context: Context,
    private val onTrackEnded: () -> Unit,
    private val onTrackStarted: (SongEntity) -> Unit
) {
    // Two players for crossfading
    private var playerA: ExoPlayer = ExoPlayer.Builder(context).build()
    private var playerB: ExoPlayer = ExoPlayer.Builder(context).build()
    
    private var currentPlayer: ExoPlayer = playerA
    private var nextPlayer: ExoPlayer = playerB
    
    private var currentSong: SongEntity? = null
    private var nextSong: SongEntity? = null

    private var crossfadeDurationMs: Long = 5000L // 5 seconds default
    private var isCrossfading = false
    private var handler = Handler(Looper.getMainLooper())
    
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _playbackProgress = MutableStateFlow(0L)
    val playbackProgress: StateFlow<Long> = _playbackProgress

    private val _currentPlayingSong = MutableStateFlow<SongEntity?>(null)
    val currentPlayingSong: StateFlow<SongEntity?> = _currentPlayingSong

    private val progressRunnable = object : Runnable {
        override fun run() {
            if (currentPlayer.isPlaying) {
                val currentPosition = currentPlayer.currentPosition
                val duration = currentPlayer.duration
                _playbackProgress.value = currentPosition
                
                // Trigger crossfade if we have a next song, aren't crossfading yet, 
                // and the remaining time is less than the crossfade duration.
                if (nextSong != null && !isCrossfading && duration > 0 && (duration - currentPosition) <= crossfadeDurationMs) {
                    startCrossfade()
                }
                
                // End of track fallback (if crossfade is off or didn't trigger)
                if (!currentPlayer.isPlaying && currentPosition >= duration && duration > 0) {
                    onTrackEnded()
                }
            }
            handler.postDelayed(this, 100)
        }
    }

    init {
        setupPlayerListeners(playerA)
        setupPlayerListeners(playerB)
        handler.post(progressRunnable)
    }

    private fun setupPlayerListeners(player: ExoPlayer) {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (player === currentPlayer) {
                    _isPlaying.value = isPlaying
                }
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED && player === currentPlayer && !isCrossfading) {
                    onTrackEnded()
                }
            }
        })
    }

    fun setCrossfadeDuration(seconds: Int) {
        crossfadeDurationMs = seconds * 1000L
    }

    fun play(song: SongEntity, nextSongToPrepare: SongEntity? = null) {
        // If we are crossfading or already playing, we stop player operations gently
        if (isCrossfading) {
            cancelCrossfade()
        }

        currentSong = song
        _currentPlayingSong.value = song
        nextSong = nextSongToPrepare

        currentPlayer.stop()
        val mediaItem = MediaItem.fromUri(Uri.parse(song.filePath))
        currentPlayer.setMediaItem(mediaItem)
        currentPlayer.volume = 1.0f
        currentPlayer.prepare()
        currentPlayer.play()
        onTrackStarted(song)

        // Pre-buffer the next song on the backup player
        if (nextSongToPrepare != null) {
            prepareNextPlayer(nextSongToPrepare)
        }
    }

    fun setNextSong(song: SongEntity?) {
        nextSong = song
        if (song != null && !isCrossfading) {
            prepareNextPlayer(song)
        } else if (song == null) {
            nextPlayer.stop()
        }
    }

    private fun prepareNextPlayer(song: SongEntity) {
        nextPlayer.stop()
        val mediaItem = MediaItem.fromUri(Uri.parse(song.filePath))
        nextPlayer.setMediaItem(mediaItem)
        nextPlayer.volume = 0.0f
        nextPlayer.prepare()
    }

    fun pause() {
        currentPlayer.pause()
        _isPlaying.value = false
    }

    fun resume() {
        currentPlayer.play()
        _isPlaying.value = true
    }

    fun seekTo(positionMs: Long) {
        if (!isCrossfading) {
            currentPlayer.seekTo(positionMs)
            _playbackProgress.value = positionMs
        }
    }

    fun getDuration(): Long {
        return currentPlayer.duration
    }

    private fun startCrossfade() {
        val incomingSong = nextSong ?: return
        isCrossfading = true
        
        // Start playing the next song silently
        nextPlayer.volume = 0.0f
        nextPlayer.play()
        onTrackStarted(incomingSong)

        val fadeSteps = 50
        val stepDuration = crossfadeDurationMs / fadeSteps
        var currentStep = 0

        val crossfadeRunnable = object : Runnable {
            override fun run() {
                if (!isCrossfading) return
                
                currentStep++
                val ratio = currentStep.toFloat() / fadeSteps
                
                // Volume curve (S-Curve shape)
                val inVolume = sinCurve(ratio)
                val outVolume = sinCurve(1.0f - ratio)

                currentPlayer.volume = outVolume
                nextPlayer.volume = inVolume

                if (currentStep < fadeSteps) {
                    handler.postDelayed(this, stepDuration)
                } else {
                    // Crossfade complete
                    currentPlayer.stop()
                    currentPlayer.volume = 1.0f // Reset volume to normal for next use

                    // Swap player identities
                    val tempPlayer = currentPlayer
                    currentPlayer = nextPlayer
                    nextPlayer = tempPlayer

                    currentSong = incomingSong
                    _currentPlayingSong.value = incomingSong
                    nextSong = null
                    isCrossfading = false
                    
                    // Callback to request the next track in queue and prepare it
                    onTrackEnded()
                }
            }
        }
        handler.post(crossfadeRunnable)
    }

    private fun cancelCrossfade() {
        isCrossfading = false
        nextPlayer.stop()
        nextPlayer.volume = 0.0f
        currentPlayer.volume = 1.0f
    }

    private fun sinCurve(ratio: Float): Float {
        // Approximate an S-curve for smoother audio transitions
        return (Math.sin((ratio - 0.5) * Math.PI) / 2.0 + 0.5).toFloat()
    }

    fun release() {
        handler.removeCallbacksAndMessages(null)
        playerA.release()
        playerB.release()
    }
}
