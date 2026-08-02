package com.mp3player.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.mp3player.data.entity.SongEntity
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import android.util.Log

object PlaylistCoverManager {
    private val generationLocks = ConcurrentHashMap<Int, ReentrantLock>()


    fun getCoverFile(context: Context, playlistId: Int): File {
        val dir = File(context.filesDir, "playlist_covers")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "playlist_${playlistId}_cover.png")
    }

    fun clearStaleCovers(context: Context) {
        try {
            val dir = File(context.filesDir, "playlist_covers")
            if (dir.exists()) {
                dir.listFiles()?.forEach { it.delete() }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getOrCreateCover(context: Context, playlistId: Int, songs: List<SongEntity>, stats: List<com.mp3player.data.dao.SongStats> = emptyList()): File? {
        val lock = generationLocks.getOrPut(playlistId) { ReentrantLock() }
        return lock.withLock {
            val coverFile = getCoverFile(context, playlistId)
            if (coverFile.exists() && coverFile.length() > 0) {
                return@withLock coverFile
            }
            generateAndSaveCover(context, playlistId, songs, stats)
        }
    }

    fun generateAndSaveCover(context: Context, playlistId: Int, songs: List<SongEntity>, stats: List<com.mp3player.data.dao.SongStats> = emptyList()): File? {
        val targetFile = getCoverFile(context, playlistId)
        val songsWithArt = songs.filter { !it.artworkPath.isNullOrBlank() }
        if (songsWithArt.isEmpty()) {
            if (targetFile.exists()) targetFile.delete()
            return null
        }

        val sortedByPlays = if (stats.isNotEmpty()) {
            val statsMap = stats.associate { it.songId to it.playCount }
            songsWithArt.sortedWith(
                compareByDescending<SongEntity> { statsMap[it.id] ?: 0 }
                    .thenBy { it.title }
            )
        } else {
            songsWithArt
        }

        val artworkPaths = sortedByPlays.mapNotNull { it.artworkPath }.filter { it.isNotBlank() }.distinct().take(9)
        val loadedBitmaps = artworkPaths.mapNotNull { path ->
            loadScaledBitmap(context, path)?.let { cropToSquare(it) }
        }


        if (loadedBitmaps.isEmpty()) {
            if (targetFile.exists()) targetFile.delete()
            return null
        }

        val top9Bitmaps = List(9) { index -> loadedBitmaps[index % loadedBitmaps.size] }
        val size = 360
        val tileSize = 120

        val collage = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(collage)

        for (i in 0 until 9) {
            val row = i / 3
            val col = i % 3
            val mini = Bitmap.createScaledBitmap(top9Bitmaps[i], tileSize, tileSize, true)
            canvas.drawBitmap(mini, (col * tileSize).toFloat(), (row * tileSize).toFloat(), null)
        }

        val roundedCollage = getRoundedCornerBitmap(collage, 32f)
        
        // Clean up unused bitmaps
        loadedBitmaps.forEach { if (!it.isRecycled) it.recycle() }
        if (!collage.isRecycled) collage.recycle()

        return try {
            FileOutputStream(targetFile).use { out ->
                roundedCollage.compress(Bitmap.CompressFormat.PNG, 90, out)
            }
            targetFile
        } catch (e: Exception) {
            Log.e("PlaylistCoverManager", "Failed to save cover", e)
            null
        }
    }

    fun loadScaledBitmap(context: Context, path: String?): Bitmap? {
        if (path.isNullOrBlank()) return null
        if (path.startsWith("http://") || path.startsWith("https://")) {
            return try {
                val loader = coil.ImageLoader(context)
                val request = coil.request.ImageRequest.Builder(context)
                    .data(path)
                    .allowHardware(false)
                    .size(128, 128)
                    .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                    .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                    .networkCachePolicy(coil.request.CachePolicy.READ_ONLY)
                    .build()
                val snapshot = loader.diskCache?.get(path)
                if (snapshot != null) {
                    val file = snapshot.data.toFile()
                    val bmp = BitmapFactory.decodeFile(file.absolutePath)
                    if (bmp != null && !bmp.isRecycled) return bmp
                }
                null
            } catch (e: Exception) {
                null
            }
        }


        return try {
            val uri = if (path.startsWith("content://") || path.startsWith("file://")) {
                Uri.parse(path)
            } else {
                Uri.fromFile(File(path))
            }



            // Try 1: ContentResolver openInputStream
            val streamBitmap = try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val options = BitmapFactory.Options().apply {
                        inSampleSize = 2
                    }
                    BitmapFactory.decodeStream(stream, null, options)
                }
            } catch (e: Exception) { null }

            if (streamBitmap != null && !streamBitmap.isRecycled) return streamBitmap

            // Try 2: Direct file decode
            val fileBmp = try {
                if (!path.startsWith("content://")) {
                    val options = BitmapFactory.Options().apply {
                        inSampleSize = 2
                    }
                    BitmapFactory.decodeFile(path, options)
                } else null
            } catch (e: Exception) { null }

            if (fileBmp != null && !fileBmp.isRecycled) return fileBmp

            // Try 3: MediaMetadataRetriever embed artwork fallback
            var retriever: MediaMetadataRetriever? = null
            try {
                retriever = MediaMetadataRetriever()
                retriever.setDataSource(context, uri)
                val artBytes = retriever.embeddedPicture
                if (artBytes != null) {
                    return BitmapFactory.decodeByteArray(artBytes, 0, artBytes.size)
                }
            } catch (e: Exception) {
                Log.e("PlaylistCoverManager", "Failed to decode embedded picture", e)
            } finally {
                try {
                    retriever?.release()
                } catch (e: Exception) {}
            }

            null
        } catch (e: Exception) {
            Log.e("PlaylistCoverManager", "Error loading scaled bitmap", e)
            null
        }
    }

    private fun cropToSquare(bitmap: Bitmap): Bitmap {
        val size = minOf(bitmap.width, bitmap.height)
        val x = (bitmap.width - size) / 2
        val y = (bitmap.height - size) / 2
        return Bitmap.createBitmap(bitmap, x, y, size, size)
    }

    private fun getRoundedCornerBitmap(bitmap: Bitmap, cornerRadiusPx: Float): Bitmap {
        val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val rect = Rect(0, 0, bitmap.width, bitmap.height)
        val rectF = RectF(rect)
        canvas.drawRoundRect(rectF, cornerRadiusPx, cornerRadiusPx, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(bitmap, rect, rect, paint)
        return output
    }
}
