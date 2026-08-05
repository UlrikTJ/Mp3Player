package com.mp3player.playback

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Singleton that persists and restores playback state across process death.
 *
 * Saves to SharedPreferences:
 *  - Current song ID and metadata (for widget display without DB)
 *  - Full queue (list of song IDs)
 *  - Queue index
 *  - Seek position
 *  - Shuffle / repeat / active playlist state
 *
 * Used by:
 *  - AudioService: to restore state on cold start from widget intents
 *  - MusicViewModel: to save state on every queue/track change
 *  - MusicAppWidgetProvider: to display correct info when service is dead
 */
object PlaybackStateManager {

    private const val PREFS_NAME = "playback_state"
    private const val KEY_CURRENT_SONG_ID = "current_song_id"
    private const val KEY_CURRENT_SONG_TITLE = "current_song_title"
    private const val KEY_CURRENT_SONG_ARTIST = "current_song_artist"
    private const val KEY_CURRENT_SONG_ARTWORK = "current_song_artwork"
    private const val KEY_CURRENT_SONG_DURATION = "current_song_duration"
    private const val KEY_CURRENT_SONG_FILE_PATH = "current_song_file_path"
    private const val KEY_QUEUE_SONG_IDS = "queue_song_ids"
    private const val KEY_QUEUE_INDEX = "queue_index"
    private const val KEY_SEEK_POSITION_MS = "seek_position_ms"
    private const val KEY_SHUFFLE_ON = "shuffle_on"
    private const val KEY_REPEAT_ON = "repeat_on"
    private const val KEY_ACTIVE_PLAYLIST_ID = "active_playlist_id"
    private const val KEY_ACTIVE_PLAYLIST_NAME = "active_playlist_name"
    private const val KEY_LAST_UPDATED = "last_updated"

    private var prefs: SharedPreferences? = null
    private val gson = Gson()

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    private fun getPrefs(): SharedPreferences {
        return prefs ?: throw IllegalStateException("PlaybackStateManager not initialized. Call init(context) first.")
    }

    // --- Save Methods ---

    fun saveCurrentSong(
        songId: Int,
        title: String,
        artist: String,
        artworkPath: String?,
        durationMs: Long,
        filePath: String
    ) {
        getPrefs().edit()
            .putInt(KEY_CURRENT_SONG_ID, songId)
            .putString(KEY_CURRENT_SONG_TITLE, title)
            .putString(KEY_CURRENT_SONG_ARTIST, artist)
            .putString(KEY_CURRENT_SONG_ARTWORK, artworkPath)
            .putLong(KEY_CURRENT_SONG_DURATION, durationMs)
            .putString(KEY_CURRENT_SONG_FILE_PATH, filePath)
            .putLong(KEY_LAST_UPDATED, System.currentTimeMillis())
            .apply()
    }

    fun saveQueue(songIds: List<Int>, currentIndex: Int) {
        val json = gson.toJson(songIds)
        getPrefs().edit()
            .putString(KEY_QUEUE_SONG_IDS, json)
            .putInt(KEY_QUEUE_INDEX, currentIndex)
            .putLong(KEY_LAST_UPDATED, System.currentTimeMillis())
            .apply()
    }

    fun saveSeekPosition(positionMs: Long) {
        getPrefs().edit()
            .putLong(KEY_SEEK_POSITION_MS, positionMs)
            .apply()
    }

    fun saveShuffleRepeatState(shuffleOn: Boolean, repeatOn: Boolean) {
        getPrefs().edit()
            .putBoolean(KEY_SHUFFLE_ON, shuffleOn)
            .putBoolean(KEY_REPEAT_ON, repeatOn)
            .apply()
    }

    fun saveActivePlaylist(playlistId: Int?, playlistName: String?) {
        getPrefs().edit()
            .putInt(KEY_ACTIVE_PLAYLIST_ID, playlistId ?: -1)
            .putString(KEY_ACTIVE_PLAYLIST_NAME, playlistName)
            .apply()
    }

    // --- Read Methods ---

    data class SavedPlaybackState(
        val currentSongId: Int,
        val title: String,
        val artist: String,
        val artworkPath: String?,
        val durationMs: Long,
        val filePath: String,
        val queueSongIds: List<Int>,
        val queueIndex: Int,
        val seekPositionMs: Long,
        val shuffleOn: Boolean,
        val repeatOn: Boolean,
        val activePlaylistId: Int?,
        val activePlaylistName: String?,
        val lastUpdated: Long
    )

    /**
     * Returns the saved playback state, or null if no state has been saved yet.
     */
    fun getSavedState(): SavedPlaybackState? {
        val p = getPrefs()
        val songId = p.getInt(KEY_CURRENT_SONG_ID, -1)
        if (songId == -1) return null

        val title = p.getString(KEY_CURRENT_SONG_TITLE, "") ?: ""
        val artist = p.getString(KEY_CURRENT_SONG_ARTIST, "") ?: ""
        val artworkPath = p.getString(KEY_CURRENT_SONG_ARTWORK, null)
        val durationMs = p.getLong(KEY_CURRENT_SONG_DURATION, 0L)
        val filePath = p.getString(KEY_CURRENT_SONG_FILE_PATH, "") ?: ""

        val queueJson = p.getString(KEY_QUEUE_SONG_IDS, null)
        val queueSongIds: List<Int> = if (queueJson != null) {
            try {
                val type = object : TypeToken<List<Int>>() {}.type
                gson.fromJson(queueJson, type)
            } catch (e: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }

        val queueIndex = p.getInt(KEY_QUEUE_INDEX, 0)
        val seekPositionMs = p.getLong(KEY_SEEK_POSITION_MS, 0L)
        val shuffleOn = p.getBoolean(KEY_SHUFFLE_ON, false)
        val repeatOn = p.getBoolean(KEY_REPEAT_ON, false)
        val activePlaylistId = p.getInt(KEY_ACTIVE_PLAYLIST_ID, -1).let { if (it == -1) null else it }
        val activePlaylistName = p.getString(KEY_ACTIVE_PLAYLIST_NAME, null)
        val lastUpdated = p.getLong(KEY_LAST_UPDATED, 0L)

        return SavedPlaybackState(
            currentSongId = songId,
            title = title,
            artist = artist,
            artworkPath = artworkPath,
            durationMs = durationMs,
            filePath = filePath,
            queueSongIds = queueSongIds,
            queueIndex = queueIndex,
            seekPositionMs = seekPositionMs,
            shuffleOn = shuffleOn,
            repeatOn = repeatOn,
            activePlaylistId = activePlaylistId,
            activePlaylistName = activePlaylistName,
            lastUpdated = lastUpdated
        )
    }

    /**
     * Quick access to just the current song info (for widget display).
     * Returns null if no song has been saved.
     */
    fun getLastSongInfo(): Triple<String, String, String?>? {
        val p = getPrefs()
        val songId = p.getInt(KEY_CURRENT_SONG_ID, -1)
        if (songId == -1) return null
        val title = p.getString(KEY_CURRENT_SONG_TITLE, "") ?: ""
        val artist = p.getString(KEY_CURRENT_SONG_ARTIST, "") ?: ""
        val artworkPath = p.getString(KEY_CURRENT_SONG_ARTWORK, null)
        return Triple(title, artist, artworkPath)
    }

    fun getLastSongId(): Int {
        return getPrefs().getInt(KEY_CURRENT_SONG_ID, -1)
    }

    fun getSeekPosition(): Long {
        return getPrefs().getLong(KEY_SEEK_POSITION_MS, 0L)
    }

    fun clear() {
        getPrefs().edit().clear().apply()
    }
}
