package com.mp3player.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import com.mp3player.data.entity.SongEntity
import java.io.File
import java.io.FileOutputStream

object PlaylistCoverManager {

    fun getCoverFile(context: Context, playlistId: Int): File {
        val dir = File(context.filesDir, "playlist_covers")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "playlist_${playlistId}_cover.png")
    }

    fun getOrCreateCover(context: Context, playlistId: Int, songs: List<SongEntity>): File? {
        val coverFile = getCoverFile(context, playlistId)
        if (coverFile.exists() && coverFile.length() > 0) {
            return coverFile
        }
        return generateAndSaveCover(context, playlistId, songs)
    }

    fun generateAndSaveCover(context: Context, playlistId: Int, songs: List<SongEntity>): File? {
        val targetFile = getCoverFile(context, playlistId)
        val songsWithArt = songs.filter { !it.artworkPath.isNullOrBlank() }
        if (songsWithArt.isEmpty()) {
            if (targetFile.exists()) targetFile.delete()
            return null
        }

        val artworkPaths = songsWithArt.mapNotNull { it.artworkPath }.filter { it.isNotBlank() }.distinct().take(9)
        if (artworkPaths.isEmpty()) return null

        val top9Paths = List(9) { index -> artworkPaths[index % artworkPaths.size] }
        val size = 360
        val tileSize = 120

        val collage = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(collage)

        for (i in 0 until 9) {
            val row = i / 3
            val col = i % 3
            val path = top9Paths[i]
            val bmp = loadScaledBitmap(context, path)
            val squared = if (bmp != null) cropToSquare(bmp) else createPlaceholder(tileSize)
            val mini = Bitmap.createScaledBitmap(squared, tileSize, tileSize, true)
            canvas.drawBitmap(mini, (col * tileSize).toFloat(), (row * tileSize).toFloat(), null)
        }

        val roundedCollage = getRoundedCornerBitmap(collage, 32f)

        return try {
            FileOutputStream(targetFile).use { out ->
                roundedCollage.compress(Bitmap.CompressFormat.PNG, 90, out)
            }
            targetFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun loadScaledBitmap(context: Context, path: String): Bitmap? {
        return try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            if (path.startsWith("content://")) {
                context.contentResolver.openInputStream(android.net.Uri.parse(path))?.use {
                    BitmapFactory.decodeStream(it, null, options)
                }
            } else {
                BitmapFactory.decodeFile(path, options)
            }

            options.inSampleSize = calculateInSampleSize(options, 180, 180)
            options.inJustDecodeBounds = false

            if (path.startsWith("content://")) {
                context.contentResolver.openInputStream(android.net.Uri.parse(path))?.use {
                    BitmapFactory.decodeStream(it, null, options)
                }
            } else {
                BitmapFactory.decodeFile(path, options)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }

    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.run { outHeight to outWidth }
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    private fun cropToSquare(bitmap: Bitmap): Bitmap {
        val size = minOf(bitmap.width, bitmap.height)
        val x = (bitmap.width - size) / 2
        val y = (bitmap.height - size) / 2
        return Bitmap.createBitmap(bitmap, x, y, size, size)
    }

    private fun createPlaceholder(size: Int): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.DKGRAY)
        return bmp
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
