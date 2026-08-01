package com.mp3player.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
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
            isRepeatEnabled: Boolean = false,
            artworkBitmap: Bitmap? = null,
            progressMs: Long = 0L,
            recentSongs: List<SongEntity> = emptyList()
        ) {
            updateProvider(context, MusicAppWidgetProvider::class.java, R.layout.widget_music_4x1, song, isPlaying, isShuffleEnabled, isRepeatEnabled, artworkBitmap, progressMs, recentSongs)
            updateProvider(context, MusicWidget4x2Provider::class.java, R.layout.widget_music_4x2, song, isPlaying, isShuffleEnabled, isRepeatEnabled, artworkBitmap, progressMs, recentSongs)
            updateProvider(context, MusicWidgetSquircleProvider::class.java, R.layout.widget_music_squircle, song, isPlaying, isShuffleEnabled, isRepeatEnabled, artworkBitmap, progressMs, recentSongs)
        }

        private fun getServicePendingIntent(context: Context, requestCode: Int, intent: Intent): PendingIntent {
            val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                PendingIntent.getForegroundService(context, requestCode, intent, flags)
            } else {
                PendingIntent.getService(context, requestCode, intent, flags)
            }
        }

        private fun updateProvider(
            context: Context,
            providerClass: Class<*>,
            layoutResId: Int,
            song: SongEntity?,
            isPlaying: Boolean,
            isShuffleEnabled: Boolean,
            isRepeatEnabled: Boolean,
            artworkBitmap: Bitmap?,
            progressMs: Long,
            recentSongs: List<SongEntity> = emptyList()
        ) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, providerClass)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            if (appWidgetIds.isEmpty()) return

            try {
                val remoteViews = RemoteViews(context.packageName, layoutResId)

                val title = song?.title ?: "Music"
                val artist = song?.artist ?: "Tap to play your music"
                remoteViews.setTextViewText(R.id.widget_title, title)
                remoteViews.setTextViewText(R.id.widget_artist, artist)

                val playPauseIcon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
                remoteViews.setImageViewResource(R.id.widget_btn_play_pause, playPauseIcon)

                // High-Quality Thumbnail Artwork Loading (Cropped to exact 1:1 square to eliminate sidebars)
                val artPath = song?.artworkPath
                var artLoaded = false
                if (artPath != null && artPath.isNotBlank()) {
                    val rawBitmap = artworkBitmap ?: loadScaledBitmap(context, artPath)
                    if (rawBitmap != null && !rawBitmap.isRecycled) {
                        try {
                            val squared = cropToSquare(rawBitmap)
                            val scaled = Bitmap.createScaledBitmap(squared, 360, 360, true)
                            remoteViews.setImageViewBitmap(R.id.widget_album_art, scaled)
                            artLoaded = true
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
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
                    remoteViews.setOnClickPendingIntent(R.id.widget_btn_shuffle, getServicePendingIntent(context, 13, shuffleIntent))

                    val repeatIntent = Intent(context, AudioService::class.java).apply {
                        action = AudioService.ACTION_TOGGLE_REPEAT
                    }
                    remoteViews.setOnClickPendingIntent(R.id.widget_btn_repeat, getServicePendingIntent(context, 14, repeatIntent))

                    // Progress update
                    val duration = song?.durationMs ?: 0L
                    val progress = if (duration > 0) ((progressMs.toFloat() / duration) * 1000).toInt() else 0
                    remoteViews.setProgressBar(R.id.widget_progress_bar, 1000, progress, false)

                    // Active playlist collage thumbnail slot (2x2 grid collage)
                    val collageBmp = createPlaylistCollageBitmap(context, recentSongs, song)
                    remoteViews.setImageViewBitmap(R.id.widget_recent_playlist_art, collageBmp)

                    // Recently played thumbnails in bottom row (1:1 square cropped)
                    val slotIds = intArrayOf(
                        R.id.widget_recent_1,
                        R.id.widget_recent_2,
                        R.id.widget_recent_3,
                        R.id.widget_recent_4
                    )
                    for (i in slotIds.indices) {
                        val recentSong = recentSongs.getOrNull(i)
                        if (recentSong != null && !recentSong.artworkPath.isNullOrBlank()) {
                            val bmp = loadScaledBitmap(context, recentSong.artworkPath)
                            if (bmp != null && !bmp.isRecycled) {
                                try {
                                    val squared = cropToSquare(bmp)
                                    val miniScaled = Bitmap.createScaledBitmap(squared, 140, 140, true)
                                    remoteViews.setImageViewBitmap(slotIds[i], miniScaled)
                                } catch (e: Exception) {
                                    remoteViews.setImageViewResource(slotIds[i], R.drawable.ic_music_note)
                                }
                            } else {
                                remoteViews.setImageViewResource(slotIds[i], R.drawable.ic_music_note)
                            }
                        } else {
                            remoteViews.setImageViewResource(slotIds[i], R.drawable.ic_music_note)
                        }
                    }
                }

                // PendingIntents for standard controls
                val prevIntent = Intent(context, AudioService::class.java).apply {
                    action = AudioService.ACTION_SKIP_PREVIOUS
                }
                remoteViews.setOnClickPendingIntent(R.id.widget_btn_previous, getServicePendingIntent(context, 10, prevIntent))

                val playPauseIntent = Intent(context, AudioService::class.java).apply {
                    action = AudioService.ACTION_PLAY_PAUSE
                }
                remoteViews.setOnClickPendingIntent(R.id.widget_btn_play_pause, getServicePendingIntent(context, 11, playPauseIntent))

                val nextIntent = Intent(context, AudioService::class.java).apply {
                    action = AudioService.ACTION_SKIP_NEXT
                }
                remoteViews.setOnClickPendingIntent(R.id.widget_btn_next, getServicePendingIntent(context, 12, nextIntent))

                val appIntent = Intent(context, MainActivity::class.java)
                val appPendingIntent = PendingIntent.getActivity(
                    context, 0, appIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                remoteViews.setOnClickPendingIntent(R.id.widget_root, appPendingIntent)

                appWidgetManager.updateAppWidget(componentName, remoteViews)
            } catch (e: Exception) {
                // Catch any RemoteViews exception to prevent widget from crashing
                e.printStackTrace()
            }
        }

        private fun cropToSquare(bitmap: Bitmap): Bitmap {
            val size = minOf(bitmap.width, bitmap.height)
            val x = (bitmap.width - size) / 2
            val y = (bitmap.height - size) / 2
            return Bitmap.createBitmap(bitmap, x, y, size, size)
        }

        private fun createPlaylistCollageBitmap(context: Context, recentSongs: List<SongEntity>, currentSong: SongEntity?): Bitmap {
            val allSongs = mutableListOf<SongEntity>()
            currentSong?.let { allSongs.add(it) }
            allSongs.addAll(recentSongs)
            
            val artworkPaths = allSongs.mapNotNull { it.artworkPath }.filter { it.isNotBlank() }.distinct().take(4)
            val bitmaps = artworkPaths.mapNotNull { path ->
                loadScaledBitmap(context, path)?.let { cropToSquare(it) }
            }

            if (bitmaps.size >= 4) {
                val collage = Bitmap.createBitmap(160, 160, Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(collage)
                val mini1 = Bitmap.createScaledBitmap(bitmaps[0], 80, 80, true)
                val mini2 = Bitmap.createScaledBitmap(bitmaps[1], 80, 80, true)
                val mini3 = Bitmap.createScaledBitmap(bitmaps[2], 80, 80, true)
                val mini4 = Bitmap.createScaledBitmap(bitmaps[3], 80, 80, true)
                canvas.drawBitmap(mini1, 0f, 0f, null)
                canvas.drawBitmap(mini2, 80f, 0f, null)
                canvas.drawBitmap(mini3, 0f, 80f, null)
                canvas.drawBitmap(mini4, 80f, 80f, null)
                return collage
            } else if (bitmaps.isNotEmpty()) {
                val square = cropToSquare(bitmaps[0])
                return Bitmap.createScaledBitmap(square, 160, 160, true)
            }

            val defaultBitmap = Bitmap.createBitmap(160, 160, Bitmap.Config.ARGB_8888)
            defaultBitmap.eraseColor(android.graphics.Color.DKGRAY)
            return defaultBitmap
        }

        private fun loadScaledBitmap(context: Context, path: String?): Bitmap? {
            if (path.isNullOrBlank()) return null
            // Only attempt to load image files, not audio files
            val lowerPath = path.lowercase()
            if (lowerPath.endsWith(".mp3") || lowerPath.endsWith(".m4a") ||
                lowerPath.endsWith(".flac") || lowerPath.endsWith(".wav") ||
                lowerPath.endsWith(".ogg") || lowerPath.endsWith(".aac") ||
                lowerPath.endsWith(".wma")) {
                return null
            }
            try {
                if (path.startsWith("http://") || path.startsWith("https://")) {
                    val oldPolicy = android.os.StrictMode.getThreadPolicy()
                    android.os.StrictMode.setThreadPolicy(android.os.StrictMode.ThreadPolicy.Builder().permitAll().build())
                    try {
                        val url = java.net.URL(path)
                        url.openStream().use { stream ->
                            val options = BitmapFactory.Options().apply {
                                inSampleSize = 2
                            }
                            return BitmapFactory.decodeStream(stream, null, options)
                        }
                    } finally {
                        android.os.StrictMode.setThreadPolicy(oldPolicy)
                    }
                } else if (path.startsWith("content://") || path.startsWith("file://")) {
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
