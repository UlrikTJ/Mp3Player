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
        fun updateWidget(
            context: Context,
            song: SongEntity?,
            isPlaying: Boolean,
            isShuffleEnabled: Boolean = false,
            isRepeatEnabled: Boolean = false
        ) {
            updateProvider(context, MusicAppWidgetProvider::class.java, R.layout.widget_music_4x1, song, isPlaying, isShuffleEnabled, isRepeatEnabled)
            updateProvider(context, MusicWidget4x2Provider::class.java, R.layout.widget_music_4x2, song, isPlaying, isShuffleEnabled, isRepeatEnabled)
            updateProvider(context, MusicWidgetSquircleProvider::class.java, R.layout.widget_music_squircle, song, isPlaying, isShuffleEnabled, isRepeatEnabled)
        }

        private fun updateProvider(
            context: Context,
            providerClass: Class<*>,
            layoutResId: Int,
            song: SongEntity?,
            isPlaying: Boolean,
            isShuffleEnabled: Boolean,
            isRepeatEnabled: Boolean
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

            // Enable marquee scrolling on title and artist
            try {
                remoteViews.setBoolean(R.id.widget_title, "setSelected", true)
                remoteViews.setBoolean(R.id.widget_artist, "setSelected", true)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            val playPauseIcon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
            remoteViews.setImageViewResource(R.id.widget_btn_play_pause, playPauseIcon)

            val artPath = song?.artworkPath
            var artLoaded = false
            val bitmap = loadScaledBitmap(context, artPath)
            if (bitmap != null) {
                remoteViews.setImageViewBitmap(R.id.widget_album_art, bitmap)
                artLoaded = true
            }
            if (!artLoaded) {
                remoteViews.setImageViewResource(R.id.widget_album_art, R.drawable.ic_music_note)
            }

            // Shuffle & Repeat active vector icons & intents for 4x2 widget
            if (layoutResId == R.layout.widget_music_4x2) {
                val shuffleRes = if (isShuffleEnabled) R.drawable.ic_widget_shuffle_on else R.drawable.ic_widget_shuffle_off
                remoteViews.setImageViewResource(R.id.widget_btn_shuffle, shuffleRes)

                val repeatRes = if (isRepeatEnabled) R.drawable.ic_widget_repeat_on else R.drawable.ic_widget_repeat_off
                remoteViews.setImageViewResource(R.id.widget_btn_repeat, repeatRes)

                val shuffleIntent = Intent(context, AudioService::class.java).apply {
                    action = AudioService.ACTION_TOGGLE_SHUFFLE
                }
                val shufflePendingIntent = PendingIntent.getService(
                    context, 13, shuffleIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                remoteViews.setOnClickPendingIntent(R.id.widget_btn_shuffle, shufflePendingIntent)

                val repeatIntent = Intent(context, AudioService::class.java).apply {
                    action = AudioService.ACTION_TOGGLE_REPEAT
                }
                val repeatPendingIntent = PendingIntent.getService(
                    context, 14, repeatIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                remoteViews.setOnClickPendingIntent(R.id.widget_btn_repeat, repeatPendingIntent)
            }

            // Pending Intents for standard controls
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

        private fun loadScaledBitmap(context: Context, path: String?): android.graphics.Bitmap? {
            if (path.isNullOrBlank()) return null
            try {
                if (path.startsWith("content://") || path.startsWith("file://")) {
                    val uri = android.net.Uri.parse(path)
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        val options = BitmapFactory.Options().apply {
                            inSampleSize = 2
                        }
                        return BitmapFactory.decodeStream(stream, null, options)
                    }
                } else {
                    val file = File(path)
                    if (file.exists()) {
                        val options = BitmapFactory.Options().apply {
                            inSampleSize = 2
                        }
                        return BitmapFactory.decodeFile(file.absolutePath, options)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return null
        }
    }
}

class MusicWidget4x2Provider : BaseMusicWidgetProvider(R.layout.widget_music_4x2)
class MusicWidgetSquircleProvider : BaseMusicWidgetProvider(R.layout.widget_music_squircle)
