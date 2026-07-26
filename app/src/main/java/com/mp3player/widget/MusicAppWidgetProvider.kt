package com.mp3player.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.widget.RemoteViews
import com.mp3player.MainActivity
import com.mp3player.R
import com.mp3player.data.entity.SongEntity
import com.mp3player.playback.AudioService
import java.io.File

abstract class BaseMusicWidgetProvider(private val layoutResId: Int) : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        MusicAppWidgetProvider.updateWidget(context, null, false)
    }
}

class MusicAppWidgetProvider : BaseMusicWidgetProvider(R.layout.widget_music_4x1) {
    companion object {
        fun updateWidget(context: Context, song: SongEntity?, isPlaying: Boolean) {
            updateProvider(context, MusicAppWidgetProvider::class.java, R.layout.widget_music_4x1, song, isPlaying)
            updateProvider(context, MusicWidget4x2Provider::class.java, R.layout.widget_music_4x2, song, isPlaying)
            updateProvider(context, MusicWidgetSquircleProvider::class.java, R.layout.widget_music_squircle, song, isPlaying)
        }

        private fun updateProvider(
            context: Context,
            providerClass: Class<*>,
            layoutResId: Int,
            song: SongEntity?,
            isPlaying: Boolean
        ) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, providerClass)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            if (appWidgetIds.isEmpty()) return

            val remoteViews = RemoteViews(context.packageName, layoutResId)

            val title = song?.title ?: "Music"
            val artist = song?.artist ?: "Select a song to play"
            remoteViews.setTextViewText(R.id.widget_title, title)
            remoteViews.setTextViewText(R.id.widget_artist, artist)

            val playPauseIcon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
            remoteViews.setImageViewResource(R.id.widget_btn_play_pause, playPauseIcon)

            val artPath = song?.artworkPath
            var artLoaded = false
            if (!artPath.isNullOrEmpty()) {
                val file = File(artPath)
                if (file.exists()) {
                    try {
                        val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                        if (bitmap != null) {
                            remoteViews.setImageViewBitmap(R.id.widget_album_art, bitmap)
                            artLoaded = true
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
            if (!artLoaded) {
                remoteViews.setImageViewResource(R.id.widget_album_art, R.drawable.ic_music_note)
            }

            // Pending Intents for controls
            val prevIntent = Intent(context, AudioService::class.java).apply {
                action = AudioService.ACTION_SKIP_PREVIOUS
            }
            val prevPendingIntent = PendingIntent.getService(
                context, 10, prevIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            remoteViews.setOnClickPendingIntent(R.id.widget_btn_previous, prevPendingIntent)

            val playPauseIntent = Intent(context, AudioService::class.java).apply {
                action = AudioService.ACTION_PLAY_PAUSE
            }
            val playPausePendingIntent = PendingIntent.getService(
                context, 11, playPauseIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            remoteViews.setOnClickPendingIntent(R.id.widget_btn_play_pause, playPausePendingIntent)

            val nextIntent = Intent(context, AudioService::class.java).apply {
                action = AudioService.ACTION_SKIP_NEXT
            }
            val nextPendingIntent = PendingIntent.getService(
                context, 12, nextIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            remoteViews.setOnClickPendingIntent(R.id.widget_btn_next, nextPendingIntent)

            val appIntent = Intent(context, MainActivity::class.java)
            val appPendingIntent = PendingIntent.getActivity(
                context, 0, appIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            remoteViews.setOnClickPendingIntent(R.id.widget_root, appPendingIntent)

            appWidgetManager.updateAppWidget(componentName, remoteViews)
        }
    }
}

class MusicWidget4x2Provider : BaseMusicWidgetProvider(R.layout.widget_music_4x2)
class MusicWidgetSquircleProvider : BaseMusicWidgetProvider(R.layout.widget_music_squircle)
