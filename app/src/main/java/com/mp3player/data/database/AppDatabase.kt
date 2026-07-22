package com.mp3player.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.mp3player.data.dao.MusicDao
import com.mp3player.data.entity.*

@Database(
    entities = [
        SongEntity::class,
        PlaylistEntity::class,
        PlaylistSongCrossRef::class,
        PlaybackEventEntity::class,
        ChainSkipEventEntity::class,
        ChainSkipDetailEntity::class,
        IgnoredFileEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    
    abstract fun musicDao(): MusicDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "mp3player_database"
                )
                .fallbackToDestructiveMigration() // Destructive migration for simple personal development workflow
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
