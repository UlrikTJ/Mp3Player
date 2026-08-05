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
import kotlinx.coroutines.launch
import java.io.File

abstract class BaseMusicWidgetProvider(private val layoutResId: Int) : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        com.mp3player.playback.PlaybackStateManager.init(context)
        val savedState = com.mp3player.playback.PlaybackStateManager.getSavedState()
        
        val pendingResult = goAsync()
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                val db = com.mp3player.data.database.AppDatabase.getDatabase(context)
                val musicDao = db.musicDao()
                val playlists = musicDao.getAllPlaylistsSync()
                val targetPlaylistId = savedState?.activePlaylistId ?: playlists.firstOrNull()?.id ?: 1
                val songs = musicDao.getSongsForPlaylistSync(targetPlaylistId)
                val stats = musicDao.getPlaylistSongStatsSync(targetPlaylistId)

                val restoredSong = if (savedState != null && savedState.currentSongId != -1) {
                    com.mp3player.data.entity.SongEntity(
                        id = savedState.currentSongId,
                        title = savedState.title,
                        artist = savedState.artist,
                        album = "",
                        filePath = savedState.filePath,
                        artworkPath = savedState.artworkPath,
                        durationMs = savedState.durationMs,
                        source = if (savedState.filePath.startsWith("http")) "YOUTUBE" else "LOCAL",
                        youtubeVideoId = null
                    )
                } else null

                MusicAppWidgetProvider.updateWidget(
                    context = context,
                    song = restoredSong,
                    isPlaying = false,
                    isShuffleEnabled = savedState?.shuffleOn ?: false,
                    isRepeatEnabled = savedState?.repeatOn ?: false,
                    progressMs = savedState?.seekPositionMs ?: 0L,
                    playlistSongs = songs,
                    activePlaylistId = targetPlaylistId,
                    stats = stats
                )
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                pendingResult.finish()
            }
        }
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
            playlistSongs: List<SongEntity> = emptyList(),
            activePlaylistId: Int? = null,
            stats: List<com.mp3player.data.dao.SongStats> = emptyList()
        ) {
            if (song != null) {
                lastActiveSongId = song.id
            }
            updateProvider(context, MusicAppWidgetProvider::class.java, R.layout.widget_music_4x1, song, isPlaying, isShuffleEnabled, isRepeatEnabled, artworkBitmap, progressMs, recentSongs, playlistSongs, activePlaylistId, stats)
            updateProvider(context, MusicWidget4x2Provider::class.java, R.layout.widget_music_4x2, song, isPlaying, isShuffleEnabled, isRepeatEnabled, artworkBitmap, progressMs, recentSongs, playlistSongs, activePlaylistId, stats)
            updateProvider(context, MusicWidgetSquircleProvider::class.java, R.layout.widget_music_squircle, song, isPlaying, isShuffleEnabled, isRepeatEnabled, artworkBitmap, progressMs, recentSongs, playlistSongs, activePlaylistId, stats)
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
            playlistSongs: List<SongEntity> = emptyList(),
            activePlaylistId: Int? = null,
            stats: List<com.mp3player.data.dao.SongStats> = emptyList()
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
                                val scaled = Bitmap.createScaledBitmap(squared, 128, 128, true)
                                val rounded = getRoundedCornerBitmap(scaled, 16f)
                                remoteViews.setImageViewBitmap(R.id.widget_album_art, rounded)
                                artLoaded = true
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                    if (!artLoaded) {
                        // Song playing but artwork missing or loading: show consistent 1:1 rounded placeholder
                        val defaultBitmap = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
                        defaultBitmap.eraseColor(android.graphics.Color.DKGRAY)
                        remoteViews.setImageViewBitmap(R.id.widget_album_art, getRoundedCornerBitmap(defaultBitmap, 16f))
                    }
                } else {
                    // Idle state (no active song): Use the playlist's 3x3 collage bitmap
                    val collageBmp = createPlaylistCollageBitmap(context, playlistSongs, recentSongs, activePlaylistId, stats)
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
                    val collageBmp = createPlaylistCollageBitmap(context, playlistSongs, recentSongs, activePlaylistId, stats)
                    remoteViews.setImageViewBitmap(R.id.widget_recent_playlist_art, collageBmp)


                    // Upcoming / Top 6 songs thumbnails in bottom row (1:1 square cropped with rounded corners and 1-tap play intent)
                    val slotIds = intArrayOf(
                        R.id.widget_recent_1,
                        R.id.widget_recent_2,
                        R.id.widget_recent_3,
                        R.id.widget_recent_4,
                        R.id.widget_recent_5
                    )
                    for (i in slotIds.indices) {
                        val upcomingSong = recentSongs.getOrNull(i)
                        if (upcomingSong != null) {
                            if (!upcomingSong.artworkPath.isNullOrBlank()) {
                                val bmp = loadScaledBitmap(context, upcomingSong.artworkPath)
                                if (bmp != null && !bmp.isRecycled) {
                                    try {
                                        val squared = cropToSquare(bmp)
                                        val miniScaled = Bitmap.createScaledBitmap(squared, 72, 72, true)
                                        val roundedMini = getRoundedCornerBitmap(miniScaled, 12f)
                                        remoteViews.setImageViewBitmap(slotIds[i], roundedMini)
                                    } catch (e: Exception) {
                                        remoteViews.setImageViewResource(slotIds[i], R.drawable.ic_music_note)
                                    }
                                } else {
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
                            remoteViews.setOnClickPendingIntent(slotIds[i], getServicePendingIntent(context, 1000000 + upcomingSong.id, playSongIntent))
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

        private fun createPlaylistCollageBitmap(
            context: Context,
            playlistSongs: List<SongEntity>,
            fallbackSongs: List<SongEntity> = emptyList(),
            activePlaylistId: Int? = null,
            stats: List<com.mp3player.data.dao.SongStats> = emptyList()
        ): Bitmap {
            val playlistId = activePlaylistId ?: 1
            val songsToUse = if (playlistSongs.isNotEmpty()) playlistSongs else fallbackSongs
            
            val coverFile = com.mp3player.util.PlaylistCoverManager.getOrCreateCover(context, playlistId, songsToUse, stats)
            if (coverFile != null && coverFile.exists() && coverFile.length() > 0) {
                try {
                    val bmp = BitmapFactory.decodeFile(coverFile.absolutePath)
                    if (bmp != null && !bmp.isRecycled) {
                        val scaled = Bitmap.createScaledBitmap(bmp, 128, 128, true)
                        return getRoundedCornerBitmap(scaled, 16f)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // Fallback: Sleek dark card with music note icon
            val size = 128
            val cardBmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(cardBmp)
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.parseColor("#2A2A2A")
            }
            canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)
            
            val iconDrawable = androidx.core.content.ContextCompat.getDrawable(context, R.drawable.ic_music_note)
            iconDrawable?.setBounds(32, 32, 96, 96)
            iconDrawable?.draw(canvas)
            
            return getRoundedCornerBitmap(cardBmp, 16f)
        }

        private fun loadScaledBitmap(context: Context, path: String?): Bitmap? {

            if (path.isNullOrBlank()) return null
            return com.mp3player.util.PlaylistCoverManager.loadScaledBitmap(context, path)
        }

    }
}

class MusicWidget4x2Provider : BaseMusicWidgetProvider(R.layout.widget_music_4x2)
class MusicWidgetSquircleProvider : BaseMusicWidgetProvider(R.layout.widget_music_squircle)
