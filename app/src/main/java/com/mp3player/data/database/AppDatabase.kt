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
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    
    abstract fun musicDao(): MusicDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_playback_events_playlistId` ON `playback_events` (`playlistId`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_playback_events_sessionId` ON `playback_events` (`sessionId`)")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "mp3player_database"
                )
                .addMigrations(MIGRATION_2_3)
                .fallbackToDestructiveMigration(false) // Changed from true to false
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
