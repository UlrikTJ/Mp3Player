package com.mp3player.playback

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.C
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
    private val onTrackStarted: (SongEntity) -> Unit,
    private val onCrossfadeCompleted: ((SongEntity) -> Unit)? = null,
    private val onPrepareNextSong: (() -> SongEntity?)? = null
) {
    // Two players for crossfading
    private var playerA: ExoPlayer = ExoPlayer.Builder(context).build()
    private var playerB: ExoPlayer = ExoPlayer.Builder(context).build()
    
    private var currentPlayer: ExoPlayer = playerA
    private var nextPlayer: ExoPlayer = playerB
    
    private var currentSong: SongEntity? = null
    var nextSong: SongEntity? = null
        private set

    private val MAX_SANE_DURATION_MS = 24 * 60 * 60 * 1000L // 24 hours
    
    private var crossfadeDurationMs: Long = 5000L // 5 seconds default
    var isCrossfading = false
        private set

    private val _isCrossfadingFlow = MutableStateFlow(false)
    val isCrossfadingFlow: StateFlow<Boolean> = _isCrossfadingFlow

    private var handler = Handler(Looper.getMainLooper())
    
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _playbackProgress = MutableStateFlow(0L)
    val playbackProgress: StateFlow<Long> = _playbackProgress

    private val _currentPlayingSong = MutableStateFlow<SongEntity?>(null)
    val currentPlayingSong: StateFlow<SongEntity?> = _currentPlayingSong

    private fun getPlayerOrSongDuration(player: ExoPlayer, song: SongEntity?): Long {
        val dur = player.duration
        if (dur != C.TIME_UNSET && dur > 0 && dur <= MAX_SANE_DURATION_MS) {
            return dur
        }
        val songDur = song?.durationMs ?: 0L
        if (songDur > 0 && songDur <= MAX_SANE_DURATION_MS) {
            return songDur
        }
        return 0L
    }

    private val progressRunnable = object : Runnable {
        override fun run() {
            if (currentPlayer.isPlaying || isCrossfading) {
                // If crossfading, report progress of incoming player (nextPlayer)
                val activePlayer = if (isCrossfading) nextPlayer else currentPlayer
                
                val currentPosition = activePlayer.currentPosition
                
                // Clamp to sane values
                val sanePosition = if (currentPosition < 0 || currentPosition > MAX_SANE_DURATION_MS) 0L else currentPosition
                _playbackProgress.value = sanePosition
                
                // Trigger crossfade logic based on player fading OUT
                val fadeOutPosition = currentPlayer.currentPosition
                val fadeOutDuration = getPlayerOrSongDuration(currentPlayer, currentSong)
                
                if (!isCrossfading && fadeOutDuration > 0 && (fadeOutDuration - fadeOutPosition) <= crossfadeDurationMs && (fadeOutDuration - fadeOutPosition) > 0) {
                    val freshNext = onPrepareNextSong?.invoke() ?: nextSong
                    if (freshNext != null) {
                        nextSong = freshNext
                        startCrossfade()
                    }
                }
                
                // End of track fallback
                if (!currentPlayer.isPlaying && fadeOutPosition >= fadeOutDuration && fadeOutDuration > 0 && !isCrossfading) {
                    onTrackEnded()
                }
            }
            handler.postDelayed(this, 200)
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

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                error.printStackTrace()
                if (player === currentPlayer) {
                    onTrackEnded()
                }
            }
        })
    }

    fun setCrossfadeDuration(seconds: Int) {
        crossfadeDurationMs = seconds * 1000L
    }

    fun play(song: SongEntity, nextSongToPrepare: SongEntity? = null) {
        if (isCrossfading) {
            cancelCrossfade()
        }

        val file = java.io.File(song.filePath)
        if (song.id > 0 && !file.exists()) {
            onTrackEnded()
            return
        }

        currentSong = song
        _currentPlayingSong.value = song
        nextSong = nextSongToPrepare
        _playbackProgress.value = 0L

        currentPlayer.stop()
        val mediaItem = MediaItem.fromUri(Uri.parse(song.filePath))
        currentPlayer.setMediaItem(mediaItem)
        currentPlayer.volume = 1.0f
        currentPlayer.prepare()
        currentPlayer.seekTo(0L)
        currentPlayer.play()
        onTrackStarted(song)

        if (nextSongToPrepare != null) {
            prepareNextPlayer(nextSongToPrepare)
        }
    }

    fun setNextSong(song: SongEntity?) {
        nextSong = song
        if (song != null && !isCrossfading) {
            prepareNextPlayer(song)
        } else if (song == null && !isCrossfading) {
            nextPlayer.stop()
        }
    }

    private fun prepareNextPlayer(song: SongEntity) {
        val file = java.io.File(song.filePath)
        if (song.id > 0 && !file.exists()) return

        nextPlayer.stop()
        val mediaItem = MediaItem.fromUri(Uri.parse(song.filePath))
        nextPlayer.setMediaItem(mediaItem)
        nextPlayer.volume = 0.0f
        nextPlayer.prepare()
    }

    fun pause() {
        if (isCrossfading) {
            nextPlayer.pause()
        }
        currentPlayer.pause()
        _isPlaying.value = false
    }

    fun resume() {
        if (isCrossfading) {
            nextPlayer.play()
        }
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
        val activePlayer = if (isCrossfading) nextPlayer else currentPlayer
        val activeSong = if (isCrossfading) nextSong else currentSong
        return getPlayerOrSongDuration(activePlayer, activeSong)
    }

    private fun startCrossfade() {
        val incomingSong = nextSong ?: return
        
        val file = java.io.File(incomingSong.filePath)
        if (incomingSong.id > 0 && !file.exists()) return

        isCrossfading = true
        _isCrossfadingFlow.value = true
        
        // Prepare & start incoming song at 0:00
        nextPlayer.stop()
        val mediaItem = MediaItem.fromUri(Uri.parse(incomingSong.filePath))
        nextPlayer.setMediaItem(mediaItem)
        nextPlayer.volume = 0.0f
        nextPlayer.prepare()
        nextPlayer.seekTo(0L)
        nextPlayer.play()

        _currentPlayingSong.value = incomingSong
        onTrackStarted(incomingSong)

        val fadeSteps = 50
        val stepDuration = (crossfadeDurationMs / fadeSteps).coerceAtLeast(10L)
        var currentStep = 0

        val crossfadeRunnable = object : Runnable {
            override fun run() {
                if (!isCrossfading) return
                
                currentStep++
                val ratio = (currentStep.toFloat() / fadeSteps).coerceIn(0f, 1f)
                
                val inVolume = sinCurve(ratio)
                val outVolume = sinCurve(1.0f - ratio)

                currentPlayer.volume = outVolume
                nextPlayer.volume = inVolume

                if (currentStep < fadeSteps) {
                    handler.postDelayed(this, stepDuration)
                } else {
                    // Crossfade complete
                    currentPlayer.stop()
                    currentPlayer.volume = 1.0f

                    // Swap player identities
                    val tempPlayer = currentPlayer
                    currentPlayer = nextPlayer
                    nextPlayer = tempPlayer

                    currentSong = incomingSong
                    _currentPlayingSong.value = incomingSong
                    nextSong = null
                    isCrossfading = false
                    _isCrossfadingFlow.value = false

                    if (onCrossfadeCompleted != null) {
                        onCrossfadeCompleted.invoke(incomingSong)
                    } else {
                        onTrackEnded()
                    }
                }
            }
        }
        handler.post(crossfadeRunnable)
    }

    private fun cancelCrossfade() {
        isCrossfading = false
        _isCrossfadingFlow.value = false
        nextPlayer.stop()
        nextPlayer.volume = 0.0f
        currentPlayer.volume = 1.0f
    }

    private fun sinCurve(ratio: Float): Float {
        return (Math.sin((ratio - 0.5) * Math.PI) / 2.0 + 0.5).toFloat()
    }

    fun release() {
        handler.removeCallbacksAndMessages(null)
        playerA.release()
        playerB.release()
    }
}
