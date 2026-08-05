package com.mp3player

import android.content.ComponentName
import com.mp3player.ui.theme.*
import com.mp3player.ui.components.*
import com.mp3player.ui.player.*
import com.mp3player.ui.screens.*
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import kotlinx.coroutines.delay
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import android.provider.OpenableColumns
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mp3player.data.entity.SongEntity
import com.mp3player.playback.AudioService
import com.mp3player.ui.viewmodel.MusicViewModel
import java.util.Locale
import kotlin.math.roundToInt
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import java.io.File
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import com.mp3player.data.network.SearchTrackDto
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.shadow
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.aspectRatio
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

import android.media.audiofx.AudioEffect
import android.widget.Toast
import com.mp3player.ui.viewmodel.MusicViewModel.StatsSortColumn
import com.mp3player.data.dao.SongStats
import com.mp3player.data.dao.KeeperLeaderboardEntry
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
class MainActivity : ComponentActivity() {

    private val viewModel: MusicViewModel by viewModels()
    private var audioService: AudioService? = null
    private var isBound = false

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            viewModel.scanLocalStorage()
        }
    }

    fun checkAndRequestScanPermission() {
        val permissions = mutableListOf<String>()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isEmpty()) {
            viewModel.scanLocalStorage()
        } else {
            requestPermissionLauncher.launch(missingPermissions.first())
            // In a real app, we'd handle multiple permissions better, but this is a start
        }
    }


    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as AudioService.AudioBinder
            val boundService = binder.getService()
            audioService = boundService
            isBound = true
            
            // Connect player manager and viewmodel callbacks
            val manager = boundService.getPlayerManager()
            viewModel.setPlayerManager(manager)

            boundService.onTrackEndedListener = {
                viewModel.onTrackEndedEvent()
            }
            boundService.onCrossfadeCompletedListener = { song ->
                viewModel.onCrossfadeCompletedEvent(song)
            }
            boundService.onPrepareNextSongListener = {
                viewModel.getNextSongForQueuePublic()
            }
            boundService.onSkipPreviousListener = {
                viewModel.playPreviousSong()
            }
            boundService.onToggleShuffleListener = {
                viewModel.toggleShuffle()
            }
            boundService.onToggleRepeatListener = {
                viewModel.toggleRepeatMode()
            }
            boundService.onRecentlyPlayedListener = {
                viewModel.recentlyPlayedSongs.value
            }
            boundService.onActivePlaylistSongsListener = {
                viewModel.activePlaylistSongs.value
            }
            boundService.onUpcomingOrTopSongsListener = {
                viewModel.upcomingOrTopSongs.value
            }
            boundService.onPlaySpecificSongListener = { songId ->
                viewModel.playSongById(songId)
            }
            boundService.onPlayFirstPlaylistListener = {
                viewModel.playFirstPlaylistOrDefault()
            }
            boundService.onActivePlaylistIdListener = {
                viewModel.activePlaylistId.value ?: viewModel.allPlaylists.value.firstOrNull()?.id
            }
            boundService.onPlaylistStatsListener = {
                viewModel.playlistStats.value
            }
        }



        override fun onServiceDisconnected(name: ComponentName?) {
            audioService = null
            isBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Bind Playback Service
        val intent = Intent(this, AudioService::class.java)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)

        setContent {
            AppTheme {
                MainScreen(viewModel)
            }
        }
    }

    override fun onDestroy() {
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
        super.onDestroy()
    }
}

@Composable
fun SplashScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0D12)),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.splash_art),
            contentDescription = "Splash Art",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.app_icon),
                contentDescription = "App Logo",
                modifier = Modifier
                    .size(110.dp)
                    .clip(CircleShape)
            )
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "Music",
                color = Color.White,
                fontSize = 36.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Your Ultimate Audio Player",
                color = Color.Gray,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(32.dp))
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 3.dp,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
fun MainScreen(viewModel: MusicViewModel) {
    val allSongs by viewModel.allSongs.collectAsState()
    val allPlaylists by viewModel.allPlaylists.collectAsState()
    var isLoaded by remember { mutableStateOf(false) }

    LaunchedEffect(allSongs, allPlaylists) {
        if (!isLoaded) {
            isLoaded = true
        }
    }

    if (!isLoaded) {
        SplashScreen()
        return
    }

    var selectedTab by remember { mutableIntStateOf(0) }
    val activePlaylistId by viewModel.selectedPlaylistId.collectAsState()
    val activePlaylist = remember(activePlaylistId, allPlaylists) {
        allPlaylists.firstOrNull { it.id == activePlaylistId }
    }
    
    val playerManager by viewModel.playerManager.collectAsState()
    val currentSong = playerManager?.currentPlayingSong?.collectAsState(null)?.value

    var showLibraryScreen by remember { mutableStateOf(false) }

    Scaffold(
        bottomBar = {
            Column {
                // Mini Player
                currentSong?.let { song ->
                    MiniPlayer(song = song, viewModel = viewModel)
                }
                
                // Navigation
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp
                ) {
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                        label = { Text("Home") },
                        selected = selectedTab == 0 && activePlaylist == null && !showLibraryScreen,
                        onClick = { 
                            selectedTab = 0 
                            showLibraryScreen = false
                            viewModel.selectPlaylist(null)
                        }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                        label = { Text("Search") },
                        selected = selectedTab == 1 && activePlaylist == null && !showLibraryScreen,
                        onClick = { 
                            selectedTab = 1 
                            showLibraryScreen = false
                            viewModel.selectPlaylist(null)
                        }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Playlists") },
                        label = { Text("Playlists") },
                        selected = (selectedTab == 2 || activePlaylist != null) && !showLibraryScreen,
                        onClick = { 
                            selectedTab = 2 
                            showLibraryScreen = false
                        }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                        label = { Text("Settings") },
                        selected = (selectedTab == 3 || showLibraryScreen) && activePlaylist == null,
                        onClick = { 
                            selectedTab = 3 
                            showLibraryScreen = false
                            viewModel.selectPlaylist(null)
                        }
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues)
        ) {
            if (activePlaylist != null) {
                PlaylistDetailView(
                    playlist = activePlaylist,
                    viewModel = viewModel,
                    onBack = { viewModel.selectPlaylist(null) }
                )
            } else if (showLibraryScreen) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { showLibraryScreen = false }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                        Text("Music Library", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        LibraryScreen(viewModel)
                    }
                }
            } else {
                when (selectedTab) {
                    0 -> HomeScreen(
                        viewModel = viewModel, 
                        onOpenLibrary = { showLibraryScreen = true },
                        onOpenPlaylists = { selectedTab = 2 },
                        onOpenSettings = { selectedTab = 3 }
                    )
                    1 -> SearchScreen(viewModel)
                    2 -> PlaylistsScreen(viewModel)
                    3 -> SettingsScreen(viewModel, onOpenLibrary = { showLibraryScreen = true })
                }
            }
        }
    }


    if (viewModel.showRestorePrompt) {
        val context = LocalContext.current
        AlertDialog(
            onDismissRequest = { viewModel.dismissRestorePrompt() },
            title = { Text("Restore Deleted Songs?") },
            text = { Text("You have ${viewModel.pendingIgnoredCount} previously deleted or ignored songs. Would you like to re-add them to your library during this scan?") },
            confirmButton = {
                Button(
                    onClick = { viewModel.confirmScanStorage(context, restoreIgnored = true) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) { Text("Re-add & Scan", color = Color.Black) }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.confirmScanStorage(context, restoreIgnored = false) }) {
                    Text("Keep Hidden & Scan")
                }
            }
        )
    }
}

fun getFileNameFromUri(context: Context, uri: android.net.Uri): String? {
    var result: String? = null
    if (uri.scheme == "content") {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val nameIdx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIdx >= 0) result = it.getString(nameIdx)
            }
        }
    }
    if (result == null) {
        result = uri.path
        val cut = result?.lastIndexOf('/') ?: -1
        if (cut != -1) {
            result = result?.substring(cut + 1)
        }
    }
    return result
}

fun formatTime(ms: Long): String {
    if (ms < 0) return "00:00"
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}

