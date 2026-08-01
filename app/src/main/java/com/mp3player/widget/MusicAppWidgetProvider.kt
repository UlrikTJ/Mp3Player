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
        @Volatile
        private var lastActiveSongId: Int? = null

        fun updateWidget(
            context: Context,
            song: SongEntity?,
            isPlaying: Boolean,
            isShuffleEnabled: Boolean = false,
            isRepeatEnabled: Boolean = false,
            artworkBitmap: Bitmap? = null,
            progressMs: Long = 0L,
            recentSongs: List<SongEntity> = emptyList(),
            playlistSongs: List<SongEntity> = emptyList()
        ) {
            if (song != null) {
                lastActiveSongId = song.id
            }
            updateProvider(context, MusicAppWidgetProvider::class.java, R.layout.widget_music_4x1, song, isPlaying, isShuffleEnabled, isRepeatEnabled, artworkBitmap, progressMs, recentSongs, playlistSongs)
            updateProvider(context, MusicWidget4x2Provider::class.java, R.layout.widget_music_4x2, song, isPlaying, isShuffleEnabled, isRepeatEnabled, artworkBitmap, progressMs, recentSongs, playlistSongs)
            updateProvider(context, MusicWidgetSquircleProvider::class.java, R.layout.widget_music_squircle, song, isPlaying, isShuffleEnabled, isRepeatEnabled, artworkBitmap, progressMs, recentSongs, playlistSongs)
        }

        fun updateProgressOnly(
            context: Context,
            isPlaying: Boolean,
            progressMs: Long,
            durationMs: Long
        ) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val providers = arrayOf(
                ComponentName(context, MusicAppWidgetProvider::class.java) to R.layout.widget_music_4x1,
                ComponentName(context, MusicWidget4x2Provider::class.java) to R.layout.widget_music_4x2,
                ComponentName(context, MusicWidgetSquircleProvider::class.java) to R.layout.widget_music_squircle
            )
            for ((comp, layoutId) in providers) {
                val ids = appWidgetManager.getAppWidgetIds(comp)
                if (ids.isNotEmpty()) {
                    val remoteViews = RemoteViews(context.packageName, layoutId)
                    val playPauseIcon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
                    remoteViews.setImageViewResource(R.id.widget_btn_play_pause, playPauseIcon)
                    val progress = if (durationMs > 0) ((progressMs.toFloat() / durationMs) * 1000).toInt() else 0
                    remoteViews.setProgressBar(R.id.widget_progress_bar, 1000, progress, false)
                    appWidgetManager.partiallyUpdateAppWidget(ids, remoteViews)
                }
            }
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
            recentSongs: List<SongEntity> = emptyList(),
            playlistSongs: List<SongEntity> = emptyList()
        ) {
            // Reject stale updates referencing a different song than the currently active one
            if (song != null && lastActiveSongId != null && song.id != lastActiveSongId) {
                return
            }

            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, providerClass)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            if (appWidgetIds.isEmpty()) return

            try {
                val remoteViews = RemoteViews(context.packageName, layoutResId)

                val title = song?.title ?: (playlistSongs.firstOrNull()?.title ?: "My Playlist")
                val artist = song?.artist ?: "${playlistSongs.size} tracks • Tap ▶ to play"
                remoteViews.setTextViewText(R.id.widget_title, title)
                remoteViews.setTextViewText(R.id.widget_artist, artist)

                val playPauseIcon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
                remoteViews.setImageViewResource(R.id.widget_btn_play_pause, playPauseIcon)

                // High-Quality Thumbnail Artwork Loading (Cropped to exact 1:1 square with rounded corners)
                val artPath = song?.artworkPath
                var artLoaded = false
                if (song != null) {
                    if (artPath != null && artPath.isNotBlank()) {
                        val rawBitmap = artworkBitmap ?: loadScaledBitmap(context, artPath)
                        if (rawBitmap != null && !rawBitmap.isRecycled) {
                            try {
                                val squared = cropToSquare(rawBitmap)
                                val scaled = Bitmap.createScaledBitmap(squared, 160, 160, true)
                                val rounded = getRoundedCornerBitmap(scaled, 24f)
                                remoteViews.setImageViewBitmap(R.id.widget_album_art, rounded)
                                artLoaded = true
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                    if (!artLoaded) {
                        // Song playing but artwork missing or loading: show consistent 1:1 rounded placeholder (never flash 3x3 collage)
                        val defaultBitmap = Bitmap.createBitmap(160, 160, Bitmap.Config.ARGB_8888)
                        defaultBitmap.eraseColor(android.graphics.Color.DKGRAY)
                        remoteViews.setImageViewBitmap(R.id.widget_album_art, getRoundedCornerBitmap(defaultBitmap, 24f))
                    }
                } else {
                    // Idle state (no active song): Use the playlist's 3x3 collage bitmap
                    val collageBmp = createPlaylistCollageBitmap(context, playlistSongs, recentSongs)
                    remoteViews.setImageViewBitmap(R.id.widget_album_art, collageBmp)
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

                    // Active playlist collage thumbnail slot (pure 3x3 grid collage of playlist songs)
                    val collageBmp = createPlaylistCollageBitmap(context, playlistSongs, recentSongs)
                    remoteViews.setImageViewBitmap(R.id.widget_recent_playlist_art, collageBmp)


                    // Upcoming / Top 6 songs thumbnails in bottom row (1:1 square cropped with rounded corners and 1-tap play intent)
                    val slotIds = intArrayOf(
                        R.id.widget_recent_1,
                        R.id.widget_recent_2,
                        R.id.widget_recent_3,
                        R.id.widget_recent_4,
                        R.id.widget_recent_5,
                        R.id.widget_recent_6
                    )
                    for (i in slotIds.indices) {
                        val upcomingSong = recentSongs.getOrNull(i)
                        if (upcomingSong != null && !upcomingSong.artworkPath.isNullOrBlank()) {
                            val bmp = loadScaledBitmap(context, upcomingSong.artworkPath)
                            if (bmp != null && !bmp.isRecycled) {
                                try {
                                    val squared = cropToSquare(bmp)
                                    val miniScaled = Bitmap.createScaledBitmap(squared, 72, 72, true)
                                    val roundedMini = getRoundedCornerBitmap(miniScaled, 14f)
                                    remoteViews.setImageViewBitmap(slotIds[i], roundedMini)

                                } catch (e: Exception) {
                                    remoteViews.setImageViewResource(slotIds[i], R.drawable.ic_music_note)
                                }
                            } else {
                                remoteViews.setImageViewResource(slotIds[i], R.drawable.ic_music_note)
                            }

                            // 1-Tap Play Intent for upcoming/top song
                            val playSongIntent = Intent(context, AudioService::class.java).apply {
                                action = AudioService.ACTION_PLAY_SPECIFIC_SONG
                                putExtra(AudioService.EXTRA_SONG_ID, upcomingSong.id)
                            }
                            remoteViews.setOnClickPendingIntent(slotIds[i], getServicePendingIntent(context, 100 + i, playSongIntent))
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

                val appIntent = Intent(context, MainActivity::class.java).apply {
                    action = Intent.ACTION_MAIN
                    addCategory(Intent.CATEGORY_LAUNCHER)
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                }
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

        private fun getRoundedCornerBitmap(bitmap: Bitmap, cornerRadiusPx: Float = 24f): Bitmap {
            val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(output)
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
            val rect = android.graphics.Rect(0, 0, bitmap.width, bitmap.height)
                    val rectF = android.graphics.RectF(rect)
            canvas.drawRoundRect(rectF, cornerRadiusPx, cornerRadiusPx, paint)
            paint.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN)
            canvas.drawBitmap(bitmap, rect, rect, paint)
            return output
        }

        private fun createPlaylistCollageBitmap(context: Context, playlistSongs: List<SongEntity>, fallbackSongs: List<SongEntity> = emptyList()): Bitmap {
            val sourceSongs = if (playlistSongs.any { !it.artworkPath.isNullOrBlank() }) playlistSongs else fallbackSongs
            val songsWithArt = sourceSongs.filter { !it.artworkPath.isNullOrBlank() }
            
            val loadedBitmaps = songsWithArt.mapNotNull { song ->
                loadScaledBitmap(context, song.artworkPath)?.let { cropToSquare(it) }
            }.distinct().take(9)

            val size = 120
            if (loadedBitmaps.isNotEmpty()) {
                val top9Bitmaps = List(9) { index -> loadedBitmaps[index % loadedBitmaps.size] }
                val collage = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(collage)
                val tileSize = 40
                for (i in 0 until 9) {
                    val row = i / 3
                    val col = i % 3
                    val mini = Bitmap.createScaledBitmap(top9Bitmaps[i], tileSize, tileSize, true)
                    canvas.drawBitmap(mini, (col * tileSize).toFloat(), (row * tileSize).toFloat(), null)
                }
                return getRoundedCornerBitmap(collage, 20f)
            }


            // Fallback: Sleek dark card with music note icon (never a plain gray box)
            val cardBmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(cardBmp)
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.parseColor("#2A2A2A")
            }
            canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)
            
            val iconDrawable = androidx.core.content.ContextCompat.getDrawable(context, R.drawable.ic_music_note)
            iconDrawable?.setBounds(45, 45, 135, 135)
            iconDrawable?.draw(canvas)
            
            return getRoundedCornerBitmap(cardBmp, 24f)
        }


        private fun loadScaledBitmap(context: Context, path: String?): Bitmap? {
            if (path.isNullOrBlank()) return null
            // Skip audio files and remote HTTP URLs (network loading must not block main thread)
            val lowerPath = path.lowercase()
            if (lowerPath.endsWith(".mp3") || lowerPath.endsWith(".m4a") ||
                lowerPath.endsWith(".flac") || lowerPath.endsWith(".wav") ||
                lowerPath.endsWith(".ogg") || lowerPath.endsWith(".aac") ||
                lowerPath.endsWith(".wma") ||
                lowerPath.startsWith("http://") || lowerPath.startsWith("https://")) {
                return null
            }
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
