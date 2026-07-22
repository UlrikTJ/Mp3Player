package com.mp3player.playback

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import android.content.pm.ServiceInfo
import com.mp3player.MainActivity
import com.mp3player.data.entity.SongEntity

class AudioService : Service() {

    private val binder = AudioBinder()
    private lateinit var playerManager: CrossfadePlayerManager
    
    private val NOTIFICATION_ID = 1001
    private val CHANNEL_ID = "mp3player_playback_channel"

    var onTrackEndedListener: (() -> Unit)? = null
    var onTrackStartedListener: ((SongEntity) -> Unit)? = null

    inner class AudioBinder : Binder() {
        fun getService(): AudioService = this@AudioService
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        
        // Android 12+ (API 31+) requirement: startForeground must be called within 5 seconds.
        // We show a placeholder notification immediately.
        showPlaceholderNotification()
        
        playerManager = CrossfadePlayerManager(
            context = this,
            onTrackEnded = { onTrackEndedListener?.invoke() },
            onTrackStarted = { song ->
                onTrackStartedListener?.invoke(song)
                updateNotification(song, playerManager.isPlaying.value)
            }
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY_PAUSE -> {
                if (playerManager.isPlaying.value) {
                    playerManager.pause()
                } else {
                    playerManager.resume()
                }
                playerManager.currentPlayingSong.value?.let {
                    updateNotification(it, playerManager.isPlaying.value)
                }
            }
            ACTION_SKIP_NEXT -> {
                onTrackEndedListener?.invoke()
            }
        }
        return Service.START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    fun getPlayerManager(): CrossfadePlayerManager = playerManager

    private fun showPlaceholderNotification() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Audio Service")
            .setContentText("Initializing...")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .build()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Playback Control",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows music notification controls"
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(channel)
    }

    private fun updateNotification(song: SongEntity, isPlaying: Boolean) {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Actions
        val playPauseActionIntent = Intent(this, AudioService::class.java).apply {
            action = ACTION_PLAY_PAUSE
        }
        val playPausePendingIntent = PendingIntent.getService(
            this, 1, playPauseActionIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val nextActionIntent = Intent(this, AudioService::class.java).apply {
            action = ACTION_SKIP_NEXT
        }
        val nextPendingIntent = PendingIntent.getService(
            this, 2, nextActionIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val playPauseIcon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        val playPauseText = if (isPlaying) "Pause" else "Play"

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(song.title)
            .setContentText(song.artist)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(playPauseIcon, playPauseText, playPausePendingIntent)
            .addAction(android.R.drawable.ic_media_next, "Next", nextPendingIntent)
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setShowActionsInCompactView(0, 1)
            )
            .setOngoing(isPlaying)
            .build()

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
    }

    override fun onDestroy() {
        playerManager.release()
        super.onDestroy()
    }

    companion object {
        const val ACTION_PLAY_PAUSE = "com.mp3player.ACTION_PLAY_PAUSE"
        const val ACTION_SKIP_NEXT = "com.mp3player.ACTION_SKIP_NEXT"
    }
}
