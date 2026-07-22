package com.mp3player.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mp3player.data.database.AppDatabase
import com.mp3player.data.dao.MusicDao
import com.mp3player.data.dao.SongStats
import com.mp3player.data.dao.KeeperLeaderboardEntry
import com.mp3player.data.entity.SongEntity
import com.mp3player.data.network.*
import com.mp3player.playback.ShuffleEngine
import com.mp3player.playback.PlaybackStatsTracker
import com.mp3player.playback.CrossfadePlayerManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import android.provider.MediaStore
import com.mp3player.data.entity.PlaylistEntity
import com.mp3player.data.entity.PlaylistSongCrossRef
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import com.google.gson.JsonParser
import com.google.gson.JsonArray



class MusicViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val musicDao: MusicDao = db.musicDao()
    private val statsTracker = PlaybackStatsTracker(musicDao, viewModelScope)
    private val streamUrlCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    private val sharedPrefs: SharedPreferences = application.getSharedPreferences("Mp3PlayerPrefs", Context.MODE_PRIVATE)

    // Player manager instance (injected from service binding, or created locally for testing)
    private var _playerManager = MutableStateFlow<CrossfadePlayerManager?>(null)
    val playerManager: StateFlow<CrossfadePlayerManager?> = _playerManager

    // UI Configuration States
    private val _serverIp = MutableStateFlow(sharedPrefs.getString("server_ip", "100.100.100.100") ?: "100.100.100.100")
    val serverIp: StateFlow<String> = _serverIp

    private val _crossfadeSeconds = MutableStateFlow(sharedPrefs.getInt("crossfade_seconds", 5))
    val crossfadeSeconds: StateFlow<Int> = _crossfadeSeconds

    private val _useWeightedShuffle = MutableStateFlow(sharedPrefs.getBoolean("weighted_shuffle", true))
    val useWeightedShuffle: StateFlow<Boolean> = _useWeightedShuffle

    private val _useSkipPenalty = MutableStateFlow(sharedPrefs.getBoolean("skip_penalty", true))
    val useSkipPenalty: StateFlow<Boolean> = _useSkipPenalty

    private val _useKeeperBonus = MutableStateFlow(sharedPrefs.getBoolean("keeper_bonus", true))
    val useKeeperBonus: StateFlow<Boolean> = _useKeeperBonus

    private val _isLooping = MutableStateFlow(sharedPrefs.getBoolean("is_looping", false))
    val isLooping: StateFlow<Boolean> = _isLooping

    private val _cooldownFormula = MutableStateFlow(sharedPrefs.getString("cooldown_formula", "n/3") ?: "n/3")
    val cooldownFormula: StateFlow<String> = _cooldownFormula

    private val _libraryViewMode = MutableStateFlow(sharedPrefs.getString("library_view_mode", "ALL") ?: "ALL")
    val libraryViewMode: StateFlow<String> = _libraryViewMode

    // API Instance
    private var apiService: ApiService? = ApiService.getInstance("http://${_serverIp.value}:8000")

    // Data streams
    val allSongs: StateFlow<List<SongEntity>> = musicDao.getAllSongsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Selected Playlist ID flow for details and stats
    val selectedPlaylistId = MutableStateFlow<Int?>(null)

    val allPlaylists: StateFlow<List<PlaylistEntity>> = musicDao.getAllPlaylistsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val playlistSongs: StateFlow<List<SongEntity>> = selectedPlaylistId.flatMapLatest { id ->
        id?.let { musicDao.getSongsForPlaylistFlow(it) } ?: flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val playlistStats: StateFlow<List<SongStats>> = selectedPlaylistId.flatMapLatest { id ->
        id?.let { musicDao.getPlaylistSongStatsFlow(it) } ?: flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val playlistKeepers: StateFlow<List<KeeperLeaderboardEntry>> = selectedPlaylistId.flatMapLatest { id ->
        id?.let { musicDao.getPlaylistKeepersLeaderboardFlow(it) } ?: flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val songsNotInPlaylist: StateFlow<List<SongEntity>> = selectedPlaylistId.flatMapLatest { id ->
        id?.let { musicDao.getSongsNotInPlaylistFlow(it) } ?: flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())


    val songStats: StateFlow<List<SongStats>> = musicDao.getSongStatsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val keepersLeaderboard: StateFlow<List<KeeperLeaderboardEntry>> = musicDao.getKeepersLeaderboardFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // UI Search & Download States
    private val _searchResults = MutableStateFlow<List<SearchTrackDto>>(emptyList())
    val searchResults: StateFlow<List<SearchTrackDto>> = _searchResults

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching

    private val _downloadProgress = MutableStateFlow<Map<String, Float>>(emptyMap()) // videoId -> progress percentage
    val downloadProgress: StateFlow<Map<String, Float>> = _downloadProgress

    // History and Queue
    private val playbackHistory = mutableListOf<Int>() // List of song IDs
    private val currentQueue = mutableListOf<SongEntity>()
    private var currentQueueIndex = -1
    private val playedSongIds = mutableListOf<Int>()

    val activeQueue: List<SongEntity> get() = currentQueue
    val activeQueueIndex: Int get() = currentQueueIndex

    init {
        statsTracker.onSessionStarted()
    }

    fun setPlayerManager(manager: CrossfadePlayerManager) {
        _playerManager.value = manager
        manager.setCrossfadeDuration(_crossfadeSeconds.value)
    }

    fun updateServerIp(ip: String) {
        if (ip.isBlank()) return
        _serverIp.value = ip
        sharedPrefs.edit().putString("server_ip", ip).apply()
        val newService = ApiService.getInstance("http://$ip:8000")
        if (newService != null) {
            apiService = newService
        }
    }

    fun updateCrossfadeSeconds(seconds: Int) {
        _crossfadeSeconds.value = seconds
        sharedPrefs.edit().putInt("crossfade_seconds", seconds).apply()
        _playerManager.value?.setCrossfadeDuration(seconds)
    }

    fun updateWeightedShuffle(enabled: Boolean) {
        _useWeightedShuffle.value = enabled
        sharedPrefs.edit().putBoolean("weighted_shuffle", enabled).apply()
    }

    fun updateSkipPenalty(enabled: Boolean) {
        _useSkipPenalty.value = enabled
        sharedPrefs.edit().putBoolean("skip_penalty", enabled).apply()
    }

    fun updateKeeperBonus(enabled: Boolean) {
        _useKeeperBonus.value = enabled
        sharedPrefs.edit().putBoolean("keeper_bonus", enabled).apply()
    }

    fun toggleLooping() {
        val newValue = !_isLooping.value
        _isLooping.value = newValue
        sharedPrefs.edit().putBoolean("is_looping", newValue).apply()
    }

    fun toggleShuffle() {
        val newValue = !_useWeightedShuffle.value
        _useWeightedShuffle.value = newValue
        sharedPrefs.edit().putBoolean("weighted_shuffle", newValue).apply()
        playedSongIds.clear()
    }

    fun updateCooldownFormula(formula: String) {
        if (formula.isNotBlank()) {
            _cooldownFormula.value = formula
            sharedPrefs.edit().putString("cooldown_formula", formula).apply()
        }
    }

    fun updateLibraryViewMode(mode: String) {
        _libraryViewMode.value = mode
        sharedPrefs.edit().putString("library_view_mode", mode).apply()
    }

    fun updateSongWeight(songId: Int, weight: Float) {
        viewModelScope.launch(Dispatchers.IO) {
            musicDao.updateSongWeight(songId, weight)
        }
    }

    // --- Search & Streaming ---
    fun searchYouTube(query: String) {
        _suggestions.value = emptyList()
        viewModelScope.launch {
            val service = apiService ?: return@launch
            _isSearching.value = true
            try {
                val results = withContext(Dispatchers.IO) {
                    service.search(query)
                }
                _searchResults.value = results
                if (results.isNotEmpty()) {
                    val topTrack = results.first()
                    preplaySearchTrack(topTrack)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isSearching.value = false
            }
        }
    }

    fun preplaySearchTrack(dto: SearchTrackDto) {
        val videoId = dto.id
        if (streamUrlCache.containsKey(videoId)) return
        viewModelScope.launch {
            val service = apiService ?: return@launch
            try {
                val response = withContext(Dispatchers.IO) {
                    service.getStreamUrl(StreamRequestDto(video_id = videoId))
                }
                streamUrlCache[videoId] = response.stream_url
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun ensureTrackDownloaded(dto: SearchTrackDto) {
        val exists = allSongs.value.any { it.youtubeVideoId == dto.id }
        if (!exists) {
            downloadYouTubeTrack(dto)
        }
    }

    fun playOrStreamSearchTrack(dto: SearchTrackDto) {
        val existing = allSongs.value.find { it.youtubeVideoId == dto.id }
        if (existing != null) {
            playSongFromLibrary(existing)
        } else {
            streamYouTubeTrack(dto)
        }
    }

    fun streamYouTubeTrack(dto: SearchTrackDto) {
        val manager = _playerManager.value
        val currentPlaying = manager?.currentPlayingSong?.value

        if (currentPlaying?.youtubeVideoId == dto.id && manager != null) {
            if (manager.isPlaying.value) manager.pause() else manager.resume()
            return
        }

        val cachedUrl = streamUrlCache[dto.id]
        if (cachedUrl != null) {
            val streamSong = SongEntity(
                id = -1,
                title = dto.title,
                artist = dto.uploader,
                album = "YouTube Stream",
                filePath = cachedUrl,
                artworkPath = dto.thumbnail,
                durationMs = 0L,
                source = "YOUTUBE",
                youtubeVideoId = dto.id
            )
            playSongDirectly(streamSong)
            return
        }

        viewModelScope.launch {
            val service = apiService ?: return@launch
            try {
                val response = withContext(Dispatchers.IO) {
                    service.getStreamUrl(StreamRequestDto(video_id = dto.id))
                }
                streamUrlCache[dto.id] = response.stream_url
                
                val streamSong = SongEntity(
                    id = -1,
                    title = dto.title,
                    artist = dto.uploader,
                    album = "YouTube Stream",
                    filePath = response.stream_url,
                    artworkPath = dto.thumbnail,
                    durationMs = response.duration * 1000L,
                    source = "YOUTUBE",
                    youtubeVideoId = dto.id
                )
                playSongDirectly(streamSong)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // --- Core Playback flow ---
    fun playSongFromLibrary(song: SongEntity, playlistId: Int? = null) {
        playedSongIds.clear()
        currentQueue.clear()
        if (playlistId != null) {
            viewModelScope.launch {
                val playlistSongs = withContext(Dispatchers.IO) {
                    musicDao.getSongsForPlaylist(playlistId)
                }
                currentQueue.addAll(playlistSongs)
                currentQueueIndex = currentQueue.indexOfFirst { it.id == song.id }
                playCurrentQueueIndex(playlistId)
            }
        } else {
            currentQueue.addAll(allSongs.value)
            currentQueueIndex = currentQueue.indexOfFirst { it.id == song.id }
            playCurrentQueueIndex(playlistId)
        }
    }

    fun addToQueue(song: SongEntity) {
        currentQueue.add(song)
    }

    private fun playCurrentQueueIndex(playlistId: Int?) {
        if (currentQueueIndex < 0 || currentQueueIndex >= currentQueue.size) return
        val song = currentQueue[currentQueueIndex]
        
        statsTracker.onTrackEnded(completed = false)
        statsTracker.onTrackStarted(song, playlistId)
        
        if (song.id > 0) {
            playbackHistory.add(song.id)
        }

        val nextSong = getNextSongForQueue()
        _playerManager.value?.play(song, nextSong)
    }

    fun onTrackEndedEvent() {
        statsTracker.onTrackEnded(completed = true)
        selectNextTrack(completed = true)
    }

    private fun getNextSongForQueue(): SongEntity? {
        if (currentQueue.isEmpty()) return null
        
        if (_useWeightedShuffle.value) {
            val statsMap = songStats.value.associateBy { it.songId }
            if (_isLooping.value) {
                return ShuffleEngine.selectNextSong(
                    songs = currentQueue,
                    statsMap = statsMap,
                    history = playbackHistory,
                    cooldownFormula = _cooldownFormula.value,
                    useSkipPenalty = _useSkipPenalty.value,
                    useKeeperBonus = _useKeeperBonus.value
                )
            } else {
                val currentSong = currentQueue.getOrNull(currentQueueIndex)
                val tempPlayed = if (currentSong != null) playedSongIds + currentSong.id else playedSongIds
                val pool = currentQueue.filter { it.id !in tempPlayed }
                if (pool.isEmpty()) return null
                return ShuffleEngine.selectNextSong(
                    songs = pool,
                    statsMap = statsMap,
                    history = playbackHistory,
                    cooldownFormula = _cooldownFormula.value,
                    useSkipPenalty = _useSkipPenalty.value,
                    useKeeperBonus = _useKeeperBonus.value
                )
            }
        } else {
            val nextIndex = currentQueueIndex + 1
            return if (nextIndex < currentQueue.size) {
                currentQueue[nextIndex]
            } else if (_isLooping.value) {
                currentQueue.firstOrNull()
            } else {
                null
            }
        }
    }

    private fun selectNextTrack(completed: Boolean) {
        if (currentQueue.isEmpty()) return

        val currentSong = currentQueue.getOrNull(currentQueueIndex)
        if (currentSong != null && completed) {
            playedSongIds.add(currentSong.id)
        }

        if (_useWeightedShuffle.value) {
            if (_isLooping.value) {
                val statsMap = songStats.value.associateBy { it.songId }
                val nextSong = ShuffleEngine.selectNextSong(
                    songs = currentQueue,
                    statsMap = statsMap,
                    history = playbackHistory,
                    cooldownFormula = _cooldownFormula.value,
                    useSkipPenalty = _useSkipPenalty.value,
                    useKeeperBonus = _useKeeperBonus.value
                )
                if (nextSong != null) {
                    currentQueueIndex = currentQueue.indexOfFirst { it.id == nextSong.id }
                    playCurrentQueueIndex(selectedPlaylistId.value)
                }
            } else {
                val pool = currentQueue.filter { it.id !in playedSongIds }
                if (pool.isNotEmpty()) {
                    val statsMap = songStats.value.associateBy { it.songId }
                    val nextSong = ShuffleEngine.selectNextSong(
                        songs = pool,
                        statsMap = statsMap,
                        history = playbackHistory,
                        cooldownFormula = _cooldownFormula.value,
                        useSkipPenalty = _useSkipPenalty.value,
                        useKeeperBonus = _useKeeperBonus.value
                    )
                    if (nextSong != null) {
                        currentQueueIndex = currentQueue.indexOfFirst { it.id == nextSong.id }
                        playCurrentQueueIndex(selectedPlaylistId.value)
                    }
                } else {
                    _playerManager.value?.pause()
                    playedSongIds.clear()
                }
            }
        } else {
            val nextIndex = currentQueueIndex + 1
            if (nextIndex < currentQueue.size) {
                currentQueueIndex = nextIndex
                playCurrentQueueIndex(selectedPlaylistId.value)
            } else {
                if (_isLooping.value) {
                    currentQueueIndex = 0
                    playCurrentQueueIndex(selectedPlaylistId.value)
                } else {
                    _playerManager.value?.pause()
                }
            }
        }
    }

    fun playNext() {
        selectNextTrack(completed = false)
    }

    fun playPrevious() {
        if (currentQueue.isNotEmpty()) {
            val prevIndex = currentQueueIndex - 1
            currentQueueIndex = if (prevIndex < 0) currentQueue.size - 1 else prevIndex
            playCurrentQueueIndex(selectedPlaylistId.value)
        }
    }

    private fun playSongDirectly(song: SongEntity) {
        playedSongIds.clear()
        statsTracker.onTrackEnded(completed = false)
        statsTracker.onTrackStarted(song, null)
        _playerManager.value?.play(song, null)
    }

    // --- Downloading & Local Storage ---
    fun downloadYouTubeTrack(dto: SearchTrackDto) {
        viewModelScope.launch {
            val service = apiService ?: return@launch
            val videoId = dto.id
            if (_downloadProgress.value.containsKey(videoId)) return@launch // already downloading
            
            _downloadProgress.update { it + (videoId to 0.05f) }
            
            try {
                val responseBody = withContext(Dispatchers.IO) {
                    service.downloadTrack(
                        DownloadRequestDto(
                            video_id = videoId,
                            title = dto.title,
                            artist = dto.uploader,
                            thumbnail_url = dto.thumbnail
                        )
                    )
                }

                _downloadProgress.update { it + (videoId to 0.50f) }

                // Save file locally
                val localSong = saveTrackToLocalStorage(dto.title, dto.uploader, dto.thumbnail, responseBody.byteStream())
                
                if (localSong != null) {
                    // Save to Room DB
                    withContext(Dispatchers.IO) {
                        musicDao.insertSong(localSong)
                    }
                    _downloadProgress.update { it.toMutableMap().apply { remove(videoId) } } // completed
                } else {
                    _downloadProgress.update { it.toMutableMap().apply { remove(videoId) } } // error
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _downloadProgress.update { it.toMutableMap().apply { remove(videoId) } } // error
            }
        }
    }

    fun addSearchTrackToPlaylist(dto: SearchTrackDto, playlistId: Int) {
        val existingSong = allSongs.value.find { it.youtubeVideoId == dto.id || (it.title == dto.title && it.artist == dto.uploader) }
        if (existingSong != null) {
            addSongToPlaylist(playlistId, existingSong.id)
        } else {
            viewModelScope.launch {
                val service = apiService ?: return@launch
                val videoId = dto.id
                if (_downloadProgress.value.containsKey(videoId)) return@launch // already downloading
                
                _downloadProgress.update { it + (videoId to 0.05f) }
                
                try {
                    val responseBody = withContext(Dispatchers.IO) {
                        service.downloadTrack(
                            DownloadRequestDto(
                                video_id = videoId,
                                title = dto.title,
                                artist = dto.uploader,
                                thumbnail_url = dto.thumbnail
                            )
                        )
                    }

                    _downloadProgress.update { it + (videoId to 0.50f) }

                    val localSong = saveTrackToLocalStorage(dto.title, dto.uploader, dto.thumbnail, responseBody.byteStream())
                    
                    if (localSong != null) {
                        withContext(Dispatchers.IO) {
                            val insertedId = musicDao.insertSong(localSong)
                            val songs = musicDao.getSongsForPlaylist(playlistId)
                            val nextPosition = songs.size + 1
                            musicDao.insertPlaylistSongCrossRef(
                                PlaylistSongCrossRef(playlistId = playlistId, songId = insertedId.toInt(), position = nextPosition)
                            )
                        }
                        _downloadProgress.update { it.toMutableMap().apply { remove(videoId) } } // completed
                    } else {
                        _downloadProgress.update { it.toMutableMap().apply { remove(videoId) } } // error
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    _downloadProgress.update { it.toMutableMap().apply { remove(videoId) } } // error
                }
            }
        }
    }

    fun playPlaylist(playlistId: Int, shuffle: Boolean = false) {
        viewModelScope.launch {
            val songs = withContext(Dispatchers.IO) {
                musicDao.getSongsForPlaylist(playlistId)
            }
            if (songs.isNotEmpty()) {
                playedSongIds.clear()
                currentQueue.clear()
                currentQueue.addAll(songs)
                
                if (shuffle) {
                    _useWeightedShuffle.value = true
                    sharedPrefs.edit().putBoolean("weighted_shuffle", true).apply()
                    
                    val statsMap = songStats.value.associateBy { it.songId }
                    val nextSong = ShuffleEngine.selectNextSong(
                        songs = songs,
                        statsMap = statsMap,
                        history = playbackHistory,
                        cooldownFormula = _cooldownFormula.value,
                        useSkipPenalty = _useSkipPenalty.value,
                        useKeeperBonus = _useKeeperBonus.value
                    )
                    currentQueueIndex = songs.indexOfFirst { it.id == nextSong?.id }
                    if (currentQueueIndex < 0) currentQueueIndex = 0
                } else {
                    _useWeightedShuffle.value = false
                    sharedPrefs.edit().putBoolean("weighted_shuffle", false).apply()
                    currentQueueIndex = 0
                }
                
                playCurrentQueueIndex(playlistId)
            }
        }
    }

    private suspend fun saveTrackToLocalStorage(
        title: String,
        artist: String,
        thumbnailUrl: String,
        inputStream: InputStream
    ): SongEntity? = withContext(Dispatchers.IO) {
        try {
            val context = getApplication<Application>().applicationContext
            val musicDir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: context.filesDir
            
            val safeName = title.replace(Regex("[^a-zA-Z0-9\\-_ ]"), "").trim().replace(" ", "_")
            val mp3File = File(musicDir, "${safeName}_${System.currentTimeMillis()}.mp3")
            
            // Write stream to file
            FileOutputStream(mp3File).use { outputStream ->
                val buffer = ByteArray(4096)
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                }
            }

            // Estimate duration (usually server handles metadata tagging, so we read it back or guess)
            val durationMs = 240000L // 4-minute fallback or read via media extractor
            
            SongEntity(
                title = title,
                artist = artist,
                album = "YouTube Downloads",
                filePath = mp3File.absolutePath,
                artworkPath = thumbnailUrl, // Store thumbnail URL or path
                durationMs = durationMs,
                source = "LOCAL",
                youtubeVideoId = null
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun scanLocalStorage() {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>().applicationContext
            val contentResolver = context.contentResolver
            val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.DATA
            )
            
            // Only fetch real music files (exclude notification sounds, podcasts, etc.)
            val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
            
            val cursor = contentResolver.query(uri, projection, selection, null, null)
            
            cursor?.use { c ->
                val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durationCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val dataCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                
                val existingPaths = allSongs.value.map { it.filePath }.toSet()
                
                while (c.moveToNext()) {
                    val dataPath = c.getString(dataCol)
                    if (existingPaths.contains(dataPath)) continue // skip already added songs
                    
                    val title = c.getString(titleCol) ?: "Unknown Title"
                    val artist = c.getString(artistCol) ?: "Unknown Artist"
                    val album = c.getString(albumCol) ?: "Unknown Album"
                    val duration = c.getLong(durationCol)
                    
                    val song = SongEntity(
                        title = title,
                        artist = artist,
                        album = album,
                        filePath = dataPath,
                        artworkPath = null,
                        durationMs = if (duration > 0) duration else 240000L,
                        source = "LOCAL",
                        youtubeVideoId = null
                    )
                    musicDao.insertSong(song)
                }
            }
            
            // Auto trigger artwork fetcher for local songs
            triggerAutoArtworkFetcher()
        }
    }

    private val _isFetchingArtwork = MutableStateFlow(false)
    val isFetchingArtwork: StateFlow<Boolean> = _isFetchingArtwork

    fun triggerAutoArtworkFetcher() {
        viewModelScope.launch(Dispatchers.IO) {
            if (_isFetchingArtwork.value) return@launch
            val service = apiService ?: return@launch
            _isFetchingArtwork.value = true
            
            try {
                val songsToFetch = allSongs.value.filter { it.source == "LOCAL" && it.artworkPath.isNullOrBlank() }
                for (song in songsToFetch) {
                    try {
                        val query = "${song.title} ${song.artist}".trim()
                        val results = service.search(query)
                        if (results.isNotEmpty()) {
                            val bestMatch = results.first()
                            if (bestMatch.thumbnail.isNotEmpty()) {
                                val updatedSong = song.copy(artworkPath = bestMatch.thumbnail)
                                musicDao.updateSong(updatedSong)
                            }
                        }
                        // Sleep to limit YouTube API hits
                        Thread.sleep(1000)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            } finally {
                _isFetchingArtwork.value = false
            }
        }
    }

    fun selectPlaylist(playlistId: Int?) {
        selectedPlaylistId.value = playlistId
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            musicDao.insertPlaylist(PlaylistEntity(name = name))
        }
    }

    fun deletePlaylist(playlistId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            musicDao.deletePlaylistById(playlistId)
            if (selectedPlaylistId.value == playlistId) {
                selectedPlaylistId.value = null
            }
        }
    }

    fun addSongToPlaylist(playlistId: Int, songId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val songs = musicDao.getSongsForPlaylist(playlistId)
            val nextPosition = songs.size + 1
            musicDao.insertPlaylistSongCrossRef(
                PlaylistSongCrossRef(playlistId = playlistId, songId = songId, position = nextPosition)
            )
        }
    }

    fun removeSongFromPlaylist(playlistId: Int, songId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            musicDao.removeSongFromPlaylist(playlistId, songId)
        }
    }

    fun deleteSong(song: SongEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            musicDao.deleteSong(song)
            val file = File(song.filePath)
            if (file.exists() && song.source == "YOUTUBE") {
                try {
                    file.delete()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private val _suggestions = MutableStateFlow<List<String>>(emptyList())
    val suggestions: StateFlow<List<String>> = _suggestions

    fun fetchSearchSuggestions(query: String) {
        if (query.isBlank()) {
            _suggestions.value = emptyList()
            return
        }
        viewModelScope.launch {
            try {
                val client = okhttp3.OkHttpClient()
                val request = okhttp3.Request.Builder()
                    .url("https://suggestqueries.google.com/complete/search?client=firefox&ds=yt&q=${java.net.URLEncoder.encode(query, "UTF-8")}")
                    .build()
                val response = withContext(Dispatchers.IO) {
                    client.newCall(request).execute()
                }
                val body = response.body?.string()
                if (body != null) {
                    val rootElement = JsonParser.parseString(body)
                    if (rootElement is com.google.gson.JsonArray && rootElement.size() > 1) {
                        val suggestionsArray = rootElement.get(1).asJsonArray
                        val suggestionsList = mutableListOf<String>()
                        for (i in 0 until suggestionsArray.size()) {
                            suggestionsList.add(suggestionsArray.get(i).asString)
                        }
                        _suggestions.value = suggestionsList
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun clearSuggestions() {
        _suggestions.value = emptyList()
    }


    fun resetPlaylistStats(playlistId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val songs = musicDao.getSongsForPlaylist(playlistId)
            for (song in songs) {
                musicDao.updateSongWeight(song.id, 1.0f)
            }
            musicDao.deletePlaybackEventsForPlaylist(playlistId)
        }
    }

    fun renamePlaylist(playlistId: Int, newName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            musicDao.renamePlaylist(playlistId, newName)
        }
    }

    fun moveSongInPlaylist(playlistId: Int, songId: Int, moveUp: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val songs = musicDao.getSongsForPlaylist(playlistId)
            val index = songs.indexOfFirst { it.id == songId }
            if (index == -1) return@launch
            
            val newIndex = if (moveUp) index - 1 else index + 1
            if (newIndex < 0 || newIndex >= songs.size) return@launch
            
            val song1 = songs[index]
            val song2 = songs[newIndex]
            
            musicDao.insertPlaylistSongCrossRef(PlaylistSongCrossRef(playlistId, song1.id, newIndex + 1))
            musicDao.insertPlaylistSongCrossRef(PlaylistSongCrossRef(playlistId, song2.id, index + 1))
        }
    }
}
