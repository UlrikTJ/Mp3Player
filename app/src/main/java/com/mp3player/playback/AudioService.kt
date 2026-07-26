package com.mp3player.playback

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import androidx.core.app.NotificationCompat
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.mp3player.MainActivity
import com.mp3player.R
import com.mp3player.data.entity.SongEntity
import com.mp3player.widget.MusicAppWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AudioService : Service() {

    private val binder = AudioBinder()
    private lateinit var playerManager: CrossfadePlayerManager
    private lateinit var mediaSession: MediaSessionCompat

    private val NOTIFICATION_ID = 1001
    private val CHANNEL_ID = "mp3player_playback_channel"

    var onTrackEndedListener: (() -> Unit)? = null
    var onTrackStartedListener: ((SongEntity) -> Unit)? = null
    var onCrossfadeCompletedListener: ((SongEntity) -> Unit)? = null
    var onPrepareNextSongListener: (() -> SongEntity?)? = null
    var onSkipPreviousListener: (() -> Unit)? = null
    var onToggleShuffleListener: (() -> Unit)? = null

    inner class AudioBinder : Binder() {
        fun getService(): AudioService = this@AudioService
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        mediaSession = MediaSessionCompat(this, "Mp3PlayerAudioService").apply {
            isActive = true
        }

        playerManager = CrossfadePlayerManager(
            context = this,
            onTrackEnded = { onTrackEndedListener?.invoke() },
            onTrackStarted = { song ->
                onTrackStartedListener?.invoke(song)
                updateNotification(song, playerManager.isPlaying.value)
                MusicAppWidgetProvider.updateWidget(this, song, playerManager.isPlaying.value)
            },
            onCrossfadeCompleted = { song ->
                onCrossfadeCompletedListener?.invoke(song)
            },
            onPrepareNextSong = {
                onPrepareNextSongListener?.invoke()
            }
        )

        CoroutineScope(Dispatchers.Main).launch {
            playerManager.isPlaying.collect { isPlaying ->
                playerManager.currentPlayingSong.value?.let { song ->
                    updateNotification(song, isPlaying)
                    MusicAppWidgetProvider.updateWidget(this@AudioService, song, isPlaying)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY_PAUSE -> {
                val nextState = !playerManager.isPlaying.value
                if (playerManager.isPlaying.value) {
                    playerManager.pause()
                } else {
                    playerManager.resume()
                }
                playerManager.currentPlayingSong.value?.let { song ->
                    updateNotification(song, nextState)
                    MusicAppWidgetProvider.updateWidget(this, song, nextState)
                }
            }
            ACTION_SKIP_NEXT -> {
                onTrackEndedListener?.invoke()
            }
            ACTION_SKIP_PREVIOUS -> {
                onSkipPreviousListener?.invoke()
            }
            ACTION_TOGGLE_SHUFFLE -> {
                onToggleShuffleListener?.invoke()
            }
        }
        return Service.START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    fun getPlayerManager(): CrossfadePlayerManager = playerManager

    private fun showPlaceholderNotification() {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Music")
            .setSmallIcon(R.drawable.ic_music_note)
            .setContentIntent(pendingIntent)
            .build()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            // Android 12+ (S) / 14+ (U) restriction: Background apps cannot start foreground services.
            e.printStackTrace()
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Playback Control",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Music playback notification controls"
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(channel)
    }

    private fun updateNotification(song: SongEntity, isPlaying: Boolean) {
        CoroutineScope(Dispatchers.IO).launch {
            val notificationIntent = Intent(this@AudioService, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                this@AudioService, 0, notificationIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val prevPendingIntent = PendingIntent.getService(
                this@AudioService, 10,
                Intent(this@AudioService, AudioService::class.java).apply { action = ACTION_SKIP_PREVIOUS },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val playPausePendingIntent = PendingIntent.getService(
                this@AudioService, 11,
                Intent(this@AudioService, AudioService::class.java).apply { action = ACTION_PLAY_PAUSE },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val nextPendingIntent = PendingIntent.getService(
                this@AudioService, 12,
                Intent(this@AudioService, AudioService::class.java).apply { action = ACTION_SKIP_NEXT },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val shufflePendingIntent = PendingIntent.getService(
                this@AudioService, 13,
                Intent(this@AudioService, AudioService::class.java).apply { action = ACTION_TOGGLE_SHUFFLE },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val playPauseIcon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
            val playPauseText = if (isPlaying) "Pause" else "Play"

            var largeIconBitmap: Bitmap? = null
            if (!song.artworkPath.isNullOrEmpty()) {
                try {
                    val loader = ImageLoader(this@AudioService)
                    val highResArtPath = if (song.artworkPath.contains("i.ytimg.com") || song.artworkPath.contains("ytimg.com")) {
                        song.artworkPath.replace("/hqdefault.jpg", "/maxresdefault.jpg")
                            .replace("/sddefault.jpg", "/maxresdefault.jpg")
                            .replace("/mqdefault.jpg", "/maxresdefault.jpg")
                            .replace("/default.jpg", "/maxresdefault.jpg")
                    } else {
                        song.artworkPath
                    }

                    var request = ImageRequest.Builder(this@AudioService)
                        .data(highResArtPath)
                        .size(1024, 1024)
                        .allowHardware(false)
                        .build()
                    var result = loader.execute(request)
                    var drawable = (result as? SuccessResult)?.drawable

                    if (drawable == null && highResArtPath != song.artworkPath) {
                        val sdPath = song.artworkPath.replace("/hqdefault.jpg", "/sddefault.jpg")
                        request = ImageRequest.Builder(this@AudioService)
                            .data(sdPath)
                            .size(1024, 1024)
                            .allowHardware(false)
                            .build()
                        result = loader.execute(request)
                        drawable = (result as? SuccessResult)?.drawable

                        if (drawable == null) {
                            request = ImageRequest.Builder(this@AudioService)
                                .data(song.artworkPath)
                                .size(1024, 1024)
                                .allowHardware(false)
                                .build()
                            result = loader.execute(request)
                            drawable = (result as? SuccessResult)?.drawable
                        }
                    }

                    val original = (drawable as? BitmapDrawable)?.bitmap
                    
                    if (original != null && original.width > 0 && original.height > 0) {
                        val targetHeight = (original.height * 0.70).toInt().coerceAtLeast(1)
                        val topOffset = (original.height - targetHeight) / 2
                        largeIconBitmap = Bitmap.createBitmap(original, 0, topOffset, original.width, targetHeight)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // Update MediaSession Metadata so System UI Media Controls use full-res HD artwork
            val metadataBuilder = MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, song.title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, song.artist)
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, song.album)
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, song.durationMs)

            if (largeIconBitmap != null) {
                metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, largeIconBitmap)
                metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, largeIconBitmap)
            }
            mediaSession.setMetadata(metadataBuilder.build())

            val builder = NotificationCompat.Builder(this@AudioService, CHANNEL_ID)
                .setContentTitle(song.title)
                .setContentText(song.artist)
                .setSmallIcon(R.drawable.ic_music_note)
                .setContentIntent(pendingIntent)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(isPlaying)
                .addAction(android.R.drawable.ic_menu_rotate, "Shuffle", shufflePendingIntent)
                .addAction(android.R.drawable.ic_media_previous, "Previous", prevPendingIntent)
                .addAction(playPauseIcon, playPauseText, playPausePendingIntent)
                .addAction(android.R.drawable.ic_media_next, "Next", nextPendingIntent)
                .setStyle(
                    androidx.media.app.NotificationCompat.MediaStyle()
                        .setMediaSession(mediaSession.sessionToken)
                        .setShowActionsInCompactView(1, 2, 3)
                )

            if (largeIconBitmap != null) {
                builder.setLargeIcon(largeIconBitmap)
            }

            val notification = builder.build()

            withContext(Dispatchers.Main) {
                try {
                    if (isPlaying) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
                        } else {
                            startForeground(NOTIFICATION_ID, notification)
                        }
                    } else {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            stopForeground(STOP_FOREGROUND_DETACH)
                        } else {
                            @Suppress("DEPRECATION")
                            stopForeground(false)
                        }
                        val manager = getSystemService(NotificationManager::class.java)
                        manager?.notify(NOTIFICATION_ID, notification)
                    }
                } catch (e: Exception) {
                    // Handle ForegroundServiceStartNotAllowedException on Android 12+
                    e.printStackTrace()
                }
            }
        }
    }

    override fun onDestroy() {
        mediaSession.release()
        playerManager.release()
        super.onDestroy()
    }

    companion object {
        const val ACTION_PLAY_PAUSE = "com.mp3player.ACTION_PLAY_PAUSE"
        const val ACTION_SKIP_NEXT = "com.mp3player.ACTION_SKIP_NEXT"
        const val ACTION_SKIP_PREVIOUS = "com.mp3player.ACTION_SKIP_PREVIOUS"
        const val ACTION_TOGGLE_SHUFFLE = "com.mp3player.ACTION_TOGGLE_SHUFFLE"
    }
}
