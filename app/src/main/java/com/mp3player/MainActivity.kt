package com.mp3player

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import com.mp3player.data.network.SearchTrackDto
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.aspectRatio
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive



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
            boundService.onSkipPreviousListener = {
                viewModel.playPreviousSong()
            }
            boundService.onToggleShuffleListener = {
                viewModel.toggleShuffleMode()
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            audioService = null
            isBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Start and Bind Playback Service
        val intent = Intent(this, AudioService::class.java)
        startService(intent)
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
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF1DB954), // Spotify green style
            background = Color(0xFF121212),
            surface = Color(0xFF1E1E1E),
            onPrimary = Color.Black,
            onBackground = Color.White,
            onSurface = Color.White
        ),
        content = content
    )
}

@Composable
fun MainScreen(viewModel: MusicViewModel) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val activePlaylistId by viewModel.selectedPlaylistId.collectAsState()
    val allPlaylists by viewModel.allPlaylists.collectAsState()
    val activePlaylist = remember(activePlaylistId, allPlaylists) {
        allPlaylists.firstOrNull { it.id == activePlaylistId }
    }
    
    val playerManager by viewModel.playerManager.collectAsState()
    val currentSong = playerManager?.currentPlayingSong?.collectAsState(null)?.value

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
                        icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Library") },
                        label = { Text("Library") },
                        selected = selectedTab == 0 && activePlaylist == null,
                        onClick = { 
                            selectedTab = 0 
                            viewModel.selectPlaylist(null)
                        }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                        label = { Text("Search") },
                        selected = selectedTab == 1 && activePlaylist == null,
                        onClick = { 
                            selectedTab = 1 
                            viewModel.selectPlaylist(null)
                        }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Playlists") },
                        label = { Text("Playlists") },
                        selected = selectedTab == 2 || activePlaylist != null,
                        onClick = { 
                            selectedTab = 2 
                        }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                        label = { Text("Settings") },
                        selected = selectedTab == 3 && activePlaylist == null,
                        onClick = { 
                            selectedTab = 3 
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
            } else {
                when (selectedTab) {
                    0 -> LibraryScreen(viewModel)
                    1 -> SearchScreen(viewModel)
                    2 -> PlaylistsScreen(viewModel)
                    3 -> SettingsScreen(viewModel)
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

@Composable
fun MiniPlayer(song: SongEntity, viewModel: MusicViewModel) {
    val playerManager = viewModel.playerManager.collectAsState().value ?: return
    val isPlaying by playerManager.isPlaying.collectAsState()
    var showFullPlayer by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .clickable { showFullPlayer = true }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Thumbnail icon
        if (song.artworkPath != null) {
            AsyncImage(
                model = song.artworkPath,
                contentDescription = "Album Art",
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(4.dp)),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Gray),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.LightGray)
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(song.title, color = Color.White, fontSize = 14.sp, maxLines = 1)
            Text(song.artist, color = Color.Gray, fontSize = 12.sp, maxLines = 1)
        }

        IconButton(onClick = {
            if (isPlaying) playerManager.pause() else playerManager.resume()
        }) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = "Play/Pause",
                tint = Color.White
            )
        }
        
        IconButton(onClick = { viewModel.playNext() }) {
            Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = Color.White)
        }

        IconButton(onClick = { showQueue = true }) {
            Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = "Queue", tint = Color.White)
        }
    }

    if (showFullPlayer) {
        FullPlayerDialog(song = song, viewModel = viewModel, onDismiss = { showFullPlayer = false })
    }

    if (showQueue) {
        QueueDialog(viewModel = viewModel, onDismiss = { showQueue = false })
    }
}

@Composable
fun LibraryScreen(viewModel: MusicViewModel) {
    val songs by viewModel.allSongs.collectAsState()
    val viewMode by viewModel.libraryViewMode.collectAsState()
    var expandedFolder by remember { mutableStateOf<String?>(null) }
    var filterQuery by remember { mutableStateOf("") }

    val filteredSongs = remember(songs, filterQuery) {
        if (filterQuery.isBlank()) songs
        else songs.filter {
            it.title.contains(filterQuery, ignoreCase = true) ||
            it.artist.contains(filterQuery, ignoreCase = true)
        }
    }

    val folders = remember(filteredSongs) {
        filteredSongs.groupBy { 
            val parent = File(it.filePath).parentFile?.name
            if (parent.isNullOrEmpty() || parent == "Music" || parent == "mp3player_downloads") "Downloads" else parent
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // Header Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "My Library", 
                    style = MaterialTheme.typography.headlineMedium, 
                    color = Color.White,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
                Text(
                    "${songs.size} tracks available", 
                    style = MaterialTheme.typography.bodySmall, 
                    color = Color.Gray
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (songs.isNotEmpty()) {
                    IconButton(
                        onClick = { viewModel.playAllShuffled() },
                        modifier = Modifier.background(MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(12.dp))
                    ) {
                        Icon(Icons.Default.Shuffle, contentDescription = "Shuffle All", tint = Color.Black)
                    }
                }
                val context = LocalContext.current
                val activity = context as? MainActivity
                IconButton(
                    onClick = { activity?.checkAndRequestScanPermission() },
                    modifier = Modifier.background(MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(12.dp))
                ) {
                    Icon(Icons.Default.Search, contentDescription = "Scan Storage", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        // Search Filter TextField
        OutlinedTextField(
            value = filterQuery,
            onValueChange = { filterQuery = it },
            placeholder = { Text("Filter track or artist...", color = Color.Gray) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray) },
            trailingIcon = {
                if (filterQuery.isNotEmpty()) {
                    IconButton(onClick = { filterQuery = "" }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color.Gray)
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = Color.Transparent
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Tab Selection Pill Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Button(
                onClick = { viewModel.updateLibraryViewMode("ALL"); expandedFolder = null },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (viewMode == "ALL") MaterialTheme.colorScheme.primary else Color.Transparent,
                    contentColor = if (viewMode == "ALL") Color.Black else Color.Gray
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text("All Tracks", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            }
            Button(
                onClick = { viewModel.updateLibraryViewMode("FOLDERS") },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (viewMode == "FOLDERS") MaterialTheme.colorScheme.primary else Color.Transparent,
                    contentColor = if (viewMode == "FOLDERS") Color.Black else Color.Gray
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text("Folders", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        
        if (filteredSongs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (filterQuery.isBlank()) "No music saved yet. Scan storage or download tracks!" else "No songs match '${filterQuery}'", 
                    color = Color.Gray
                )
            }
        } else {
            if (viewMode == "ALL") {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(filteredSongs) { song ->
                        SongRow(song = song, viewModel = viewModel)
                    }
                }
            } else {
                if (expandedFolder == null) {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(folders.keys.toList()) { folderName ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { expandedFolder = folderName },
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(folderName, color = Color.White, fontSize = 16.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                                        Text("${folders[folderName]?.size ?: 0} songs", color = Color.Gray, fontSize = 13.sp)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { expandedFolder = null }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Back to Folders (${expandedFolder})", color = Color.White, fontSize = 16.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(folders[expandedFolder] ?: emptyList()) { song ->
                            SongRow(song = song, viewModel = viewModel)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SongRow(song: SongEntity, viewModel: MusicViewModel) {
    var menuExpanded by remember { mutableStateOf(false) }
    var showAppDeleteConfirm by remember { mutableStateOf(false) }
    var showDeviceDeleteConfirm by remember { mutableStateOf(false) }
    var showPlaylistPicker by remember { mutableStateOf(false) }
    
    val playlists by viewModel.allPlaylists.collectAsState()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { viewModel.playSongFromLibrary(song) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (song.artworkPath != null) {
                AsyncImage(
                    model = song.artworkPath,
                    contentDescription = "Album Art",
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Gray),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.LightGray)
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(song.title, color = Color.White, fontSize = 16.sp, maxLines = 1)
                Text(song.artist, color = Color.Gray, fontSize = 14.sp, maxLines = 1)
            }
            
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Options", tint = Color.LightGray)
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Add to Playlist") },
                        onClick = {
                            menuExpanded = false
                            showPlaylistPicker = true
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete from App (Ignore file)") },
                        onClick = {
                            menuExpanded = false
                            showAppDeleteConfirm = true
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete from Device", color = Color(0xFFB71C1C)) },
                        onClick = {
                            menuExpanded = false
                            showDeviceDeleteConfirm = true
                        }
                    )
                }
            }
        }
    }

    if (showAppDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showAppDeleteConfirm = false },
            title = { Text("Remove from App") },
            text = { Text("Remove '${song.title}' from the app library? The file will remain on your device, but future scans will skip it unless restored.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteSongFromApp(song)
                        showAppDeleteConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB71C1C))
                ) { Text("Remove", color = Color.White) }
            },
            dismissButton = {
                TextButton(onClick = { showAppDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }

    if (showDeviceDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeviceDeleteConfirm = false },
            title = { Text("Delete from Device") },
            text = { Text("Permanently delete '${song.title}' from device storage? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteSongFromDevice(song)
                        showDeviceDeleteConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB71C1C))
                ) { Text("Delete Permanently", color = Color.White) }
            },
            dismissButton = {
                TextButton(onClick = { showDeviceDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }

    if (showPlaylistPicker) {
        AlertDialog(
            onDismissRequest = { showPlaylistPicker = false },
            title = { Text("Add to Playlist") },
            text = {
                Column {
                    if (playlists.isEmpty()) {
                        Text("No playlists available", color = Color.Gray)
                    } else {
                        playlists.forEach { playlist ->
                            TextButton(
                                onClick = {
                                    viewModel.addSongToPlaylist(playlist.id, song.id)
                                    showPlaylistPicker = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(playlist.name, color = Color.White, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPlaylistPicker = false }) { Text("Close") }
            }
        )
    }
}

@Composable
fun SearchScreen(viewModel: MusicViewModel) {
    var searchQuery by remember { mutableStateOf("") }
    val results by viewModel.searchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val downloads by viewModel.downloadProgress.collectAsState()
    val suggestions by viewModel.suggestions.collectAsState()

    LaunchedEffect(searchQuery) {
        viewModel.fetchSearchSuggestions(searchQuery)
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Search YouTube Music") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { viewModel.searchYouTube(searchQuery) }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                focusedLabelColor = MaterialTheme.colorScheme.primary
            ),
            trailingIcon = {
                IconButton(onClick = { viewModel.searchYouTube(searchQuery) }) {
                    Icon(Icons.Default.Search, contentDescription = "Search")
                }
            }
        )
        
        if (suggestions.isNotEmpty() && results.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    suggestions.take(5).forEach { suggestion ->
                        Text(
                            text = suggestion,
                            color = Color.White,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    searchQuery = suggestion
                                    viewModel.searchYouTube(suggestion)
                                }
                                .padding(12.dp),
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        if (isSearching) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(results) { track ->
                    var showDetailDialog by remember { mutableStateOf(false) }
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { 
                                showDetailDialog = true 
                                viewModel.preplaySearchTrack(track)
                                viewModel.clearSuggestions()
                            },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (track.thumbnail.isNotEmpty()) {
                                AsyncImage(
                                    model = track.thumbnail,
                                    contentDescription = "Thumbnail",
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                            }
                            
                            Column(modifier = Modifier.weight(1f)) {
                                Text(track.title, color = Color.White, fontSize = 14.sp, maxLines = 2)
                                Text(track.uploader, color = Color.Gray, fontSize = 12.sp)
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Stream Preview
                                val playerManager by viewModel.playerManager.collectAsState()
                                val currentSong = playerManager?.currentPlayingSong?.collectAsState(null)?.value
                                val isPlaying = playerManager?.isPlaying?.collectAsState(false)?.value ?: false
                                
                                val isThisTrackPlaying = currentSong?.youtubeVideoId == track.id
                                
                                IconButton(onClick = { 
                                    viewModel.clearSuggestions()
                                    val manager = playerManager
                                    if (isThisTrackPlaying && manager != null) {
                                        if (isPlaying) manager.pause() else manager.resume()
                                    } else {
                                        viewModel.playOrStreamSearchTrack(track)
                                    }
                                }) {
                                    Icon(
                                        imageVector = if (isThisTrackPlaying && isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = "Preview/Stream",
                                        tint = if (isThisTrackPlaying) MaterialTheme.colorScheme.primary else Color.LightGray
                                    )
                                }
                                
                                // Download to Local MP3
                                val isDownloading = downloads.containsKey(track.id)
                                IconButton(onClick = { viewModel.downloadYouTubeTrack(track) }, enabled = !isDownloading) {
                                    if (isDownloading) {
                                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                    } else {
                                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Download", tint = MaterialTheme.colorScheme.primary)
                                    }
                                }

                                // Add to Playlist
                                val playlists by viewModel.allPlaylists.collectAsState()
                                var dropdownExpanded by remember { mutableStateOf(false) }
                                Box {
                                    IconButton(onClick = { dropdownExpanded = true }) {
                                        Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = "Add to Playlist", tint = Color.White)
                                    }
                                    DropdownMenu(
                                        expanded = dropdownExpanded,
                                        onDismissRequest = { dropdownExpanded = false }
                                    ) {
                                        if (playlists.isEmpty()) {
                                            DropdownMenuItem(
                                                text = { Text("No Playlists (Create one first)") },
                                                onClick = { dropdownExpanded = false }
                                            )
                                        } else {
                                            playlists.forEach { playlist ->
                                                DropdownMenuItem(
                                                    text = { Text(playlist.name) },
                                                    onClick = {
                                                        viewModel.addSearchTrackToPlaylist(track, playlist.id)
                                                        dropdownExpanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (showDetailDialog) {
                        SearchDetailDialog(
                            track = track,
                            viewModel = viewModel,
                            onDismiss = { showDetailDialog = false }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PlaylistCollageCover(
    songs: List<SongEntity>,
    stats: List<com.mp3player.data.dao.SongStats> = emptyList(),
    modifier: Modifier = Modifier
) {
    val gridArtworks = remember(songs, stats) {
        val songsWithArt = songs.filter { !it.artworkPath.isNullOrBlank() }
        if (songsWithArt.isEmpty()) return@remember emptyList<String>()
        
        val statsMap = stats.associate { it.songId to it.playCount }
        
        // Sort by play count descending, with unplayed fallback
        val sortedByViews = songsWithArt.sortedWith(
            compareByDescending<SongEntity> { statsMap[it.id] ?: 0 }
                .thenBy { it.title }
        )
        
        // Select top 9 songs (or cycle if total songs with art < 9)
        val top9 = sortedByViews.take(9)
        val nineArtworks = List(9) { index -> top9[index % top9.size].artworkPath!! }
        
        // Randomize placement in the 3x3 grid
        nineArtworks.shuffled()
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.linearGradient(
                    listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f), MaterialTheme.colorScheme.surface)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        if (gridArtworks.isEmpty()) {
            Icon(
                Icons.AutoMirrored.Filled.PlaylistPlay,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                for (row in 0..2) {
                    Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        for (col in 0..2) {
                            val artPath = gridArtworks[row * 3 + col]
                            AsyncImage(
                                model = artPath,
                                contentDescription = null,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PlaylistCardCover(
    playlistId: Int,
    viewModel: MusicViewModel,
    modifier: Modifier = Modifier
) {
    val songs by remember(playlistId) {
        viewModel.getSongsForPlaylistFlow(playlistId)
    }.collectAsState(initial = emptyList())
    
    val stats by remember(playlistId) {
        viewModel.getPlaylistSongStatsFlow(playlistId)
    }.collectAsState(initial = emptyList())

    PlaylistCollageCover(songs = songs, stats = stats, modifier = modifier)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistsScreen(viewModel: MusicViewModel) {
    val playlists by viewModel.allPlaylists.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    var playlistNameInput by remember { mutableStateOf("") }
    var playlistToDelete by remember { mutableStateOf<com.mp3player.data.entity.PlaylistEntity?>(null) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "My Playlists", 
                style = MaterialTheme.typography.headlineMedium, 
                color = Color.White,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
            )
            Button(
                onClick = { showCreateDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Create Playlist")
                Spacer(modifier = Modifier.width(4.dp))
                Text("Create")
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        if (playlists.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Create your first playlist to organize your music!", color = Color.Gray)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(playlists) { playlist ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { 
                                viewModel.selectPlaylist(playlist.id)
                            },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            PlaylistCardCover(
                                playlistId = playlist.id,
                                viewModel = viewModel,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                            )
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    playlist.name, 
                                    color = Color.White, 
                                    fontSize = 15.sp,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1
                                )
                                IconButton(
                                    onClick = { playlistToDelete = playlist },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Delete, 
                                        contentDescription = "Delete", 
                                        tint = Color.Red.copy(alpha = 0.6f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    playlistToDelete?.let { playlist ->
        AlertDialog(
            onDismissRequest = { playlistToDelete = null },
            title = { Text("Delete Playlist") },
            text = { Text("Are you sure you want to permanently delete the playlist '${playlist.name}'?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deletePlaylist(playlist.id)
                        playlistToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete", color = Color.White) }
            },
            dismissButton = {
                TextButton(onClick = { playlistToDelete = null }) { Text("Cancel") }
            }
        )
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("New Playlist") },
            text = {
                OutlinedTextField(
                    value = playlistNameInput,
                    onValueChange = { playlistNameInput = it },
                    label = { Text("Playlist Name") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (playlistNameInput.isNotBlank()) {
                        viewModel.createPlaylist(playlistNameInput)
                        playlistNameInput = ""
                        showCreateDialog = false
                    }
                }) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailView(
    playlist: com.mp3player.data.entity.PlaylistEntity,
    viewModel: MusicViewModel,
    onBack: () -> Unit
) {
    val songs by viewModel.playlistSongs.collectAsState()
    val addSongsList by viewModel.songsNotInPlaylist.collectAsState()
    val playerManager by viewModel.playerManager.collectAsState()
    val isPlaying = playerManager?.isPlaying?.collectAsState(false)?.value ?: false
    val playlistStatsForCover by viewModel.playlistStats.collectAsState()
    
    var showAddSongsDialog by remember { mutableStateOf(false) }
    var showStatsDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var songToRemove by remember { mutableStateOf<SongEntity?>(null) }
    var showWeightEditDialog by remember { mutableStateOf<SongEntity?>(null) }
    var isReorderMode by remember { mutableStateOf(false) }
    
    var draggedIndex by remember { mutableStateOf<Int?>(null) }
    var targetScreenY by remember { mutableFloatStateOf(0f) }
    val playlistListState = rememberLazyListState()
    val density = LocalDensity.current
    val playlistItemHeightPx = with(density) { 68.dp.toPx() }

    val draggedIndexRef = rememberUpdatedState(draggedIndex)
    val targetScreenYRef = rememberUpdatedState(targetScreenY)

    LaunchedEffect(draggedIndex != null) {
        if (draggedIndex == null) return@LaunchedEffect
        while (isActive && draggedIndexRef.value != null) {
            val layoutInfo = playlistListState.layoutInfo
            val viewportHeight = layoutInfo.viewportSize.height.toFloat()
            if (viewportHeight > 0f) {
                val cardCenterY = targetScreenYRef.value + (playlistItemHeightPx / 2f)
                val topThreshold = 140f
                val bottomThreshold = viewportHeight - 140f
                
                var scrollDelta = 0f
                if (cardCenterY > bottomThreshold) {
                    val overflow = cardCenterY - bottomThreshold
                    scrollDelta = (overflow * 0.35f).coerceIn(8f, 50f)
                } else if (cardCenterY < topThreshold) {
                    val overflow = topThreshold - cardCenterY
                    scrollDelta = -(overflow * 0.35f).coerceIn(8f, 50f)
                }
                
                if (scrollDelta != 0f) {
                    playlistListState.scrollBy(scrollDelta)
                }
            }
            delay(16)
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Action Bar with sticky Play/Pause button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                    if (playlistListState.firstVisibleItemIndex > 0) {
                        Text(
                            playlist.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            maxLines = 1
                        )
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    if (songs.isNotEmpty()) {
                        IconButton(onClick = {
                            val manager = playerManager
                            if (manager != null && isPlaying) {
                                manager.pause()
                            } else {
                                viewModel.playPlaylist(playlist.id, shuffle = false)
                            }
                        }) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play/Pause",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else {
                        Box(modifier = Modifier.size(48.dp))
                    }
                }

                val currentDraggedIndex = draggedIndex
                // Mutable ref so onDragEnd/onDragCancel always read the latest value
                var currentTargetIndex by remember { mutableStateOf<Int?>(null) }
                val currentTargetIndexRef = rememberUpdatedState(currentTargetIndex)

                // Recalculate target index reactively whenever drag position or scroll changes
                LaunchedEffect(currentDraggedIndex, targetScreenY, playlistListState.firstVisibleItemIndex, playlistListState.firstVisibleItemScrollOffset) {
                    currentTargetIndex = if (currentDraggedIndex == null) null
                    else {
                        val visibleItems = playlistListState.layoutInfo.visibleItemsInfo
                        if (visibleItems.isEmpty()) currentDraggedIndex
                        else {
                            // Only consider song items (skip header at index 0)
                            val songItems = visibleItems.filter { it.index > 0 }
                            if (songItems.isEmpty()) currentDraggedIndex
                            else {
                                val cardCenterY = targetScreenY + (playlistItemHeightPx / 2f)
                                val closestItem = songItems.minByOrNull { item ->
                                    val itemCenter = item.offset + (item.size / 2f)
                                    kotlin.math.abs(cardCenterY - itemCenter)
                                }
                                if (closestItem != null) {
                                    (closestItem.index - 1).coerceIn(0, songs.size - 1)
                                } else currentDraggedIndex
                            }
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    LazyColumn(
                        state = playlistListState,
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        userScrollEnabled = draggedIndex == null
                    ) {
                        item {
                            Column {
                                Spacer(modifier = Modifier.height(4.dp))
                                // Banner Layout
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    PlaylistCollageCover(
                                        songs = songs,
                                        stats = playlistStatsForCover,
                                        modifier = Modifier.size(90.dp)
                                    )

                                    Spacer(modifier = Modifier.width(16.dp))

                                    Column {
                                        Text(
                                            playlist.name,
                                            style = MaterialTheme.typography.titleLarge,
                                            color = Color.White,
                                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                            maxLines = 2
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            "${songs.size} tracks",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = Color.Gray
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                // Play / Shuffle Buttons
                                if (songs.isNotEmpty()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Button(
                                            onClick = { viewModel.playPlaylist(playlist.id, shuffle = false) },
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                        ) {
                                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Play", color = Color.Black, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                                        }
                                        Button(
                                            onClick = { viewModel.playPlaylist(playlist.id, shuffle = true) },
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface)
                                        ) {
                                            Icon(Icons.Default.Shuffle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Shuffle", color = MaterialTheme.colorScheme.primary, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                // Playlist Management Options Row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextButton(onClick = { showRenameDialog = true }) {
                                        Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Rename", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                                    }
                                    TextButton(onClick = { showAddSongsDialog = true }) {
                                        Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Add Track", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                                    }
                                    TextButton(onClick = { isReorderMode = !isReorderMode }) {
                                        Icon(Icons.Default.DragHandle, contentDescription = null, tint = if (isReorderMode) MaterialTheme.colorScheme.primary else Color.LightGray, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Reorder", color = if (isReorderMode) MaterialTheme.colorScheme.primary else Color.LightGray, fontSize = 12.sp)
                                    }
                                    TextButton(onClick = { showStatsDialog = true }) {
                                        Icon(Icons.Default.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Shuffle Options", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                HorizontalDivider(color = Color.DarkGray.copy(alpha = 0.5f), thickness = 1.dp)
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                        }

                        if (songs.isEmpty()) {
                            item {
                                Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                                    Text("This playlist is empty. Tap '+' to add songs!", color = Color.Gray)
                                }
                            }
                        } else {
                            itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
                                val isDraggedItem = index == currentDraggedIndex
                                
                                val targetTranslationY = run {
                                    val dragIdx = currentDraggedIndex
                                    val targetIdx = currentTargetIndex
                                    when {
                                        dragIdx != null && targetIdx != null -> {
                                            // Get actual item size from layout info
                                            val actualItemSize = playlistListState.layoutInfo.visibleItemsInfo
                                                .firstOrNull { it.index > 0 }?.size?.toFloat() ?: playlistItemHeightPx
                                            val spacing = with(density) { 4.dp.toPx() }
                                            val totalItemHeight = actualItemSize + spacing
                                            if (dragIdx < targetIdx && index > dragIdx && index <= targetIdx) {
                                                -totalItemHeight
                                            } else if (dragIdx > targetIdx && index < dragIdx && index >= targetIdx) {
                                                totalItemHeight
                                            } else 0f
                                        }
                                        else -> 0f
                                    }
                                }
                                
                            val animatedY by animateFloatAsState(
                                targetValue = targetTranslationY,
                                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                label = "playlistReorderTranslation"
                            )

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .graphicsLayer {
                                        translationY = animatedY
                                        alpha = if (isDraggedItem) 0.25f else 1.0f
                                    },
                                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { viewModel.playSongFromLibrary(song, playlist.id) }
                                            .padding(vertical = 8.dp, horizontal = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (isReorderMode) {
                                            Icon(
                                                imageVector = Icons.Default.DragHandle,
                                                contentDescription = "Drag to reorder",
                                                tint = if (isDraggedItem) MaterialTheme.colorScheme.primary else Color.Gray,
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .padding(4.dp)
                                                    .pointerInput(index) {
                                                        detectDragGestures(
                                                            onDragStart = { touchOffset ->
                                                                val itemInfo = playlistListState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == song.id }
                                                                val startY = itemInfo?.offset?.toFloat() ?: (index * playlistItemHeightPx)
                                                                draggedIndex = index
                                                                targetScreenY = startY + touchOffset.y - (playlistItemHeightPx / 2f)
                                                            },
                                                            onDragEnd = {
                                                                val from = draggedIndex
                                                                val to = currentTargetIndexRef.value
                                                                if (from != null && to != null && from != to) {
                                                                    viewModel.reorderSongInPlaylist(playlist.id, from, to)
                                                                }
                                                                draggedIndex = null
                                                            },
                                                            onDragCancel = {
                                                                val from = draggedIndex
                                                                val to = currentTargetIndexRef.value
                                                                if (from != null && to != null && from != to) {
                                                                    viewModel.reorderSongInPlaylist(playlist.id, from, to)
                                                                }
                                                                draggedIndex = null
                                                            },
                                                            onDrag = { change, dragAmount ->
                                                                change.consume()
                                                                targetScreenY += dragAmount.y
                                                            }
                                                        )
                                                    }
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                        }
                                        
                                        if (song.artworkPath != null) {
                                            AsyncImage(
                                                model = song.artworkPath,
                                                contentDescription = "Album Art",
                                                modifier = Modifier
                                                    .size(52.dp)
                                                    .clip(RoundedCornerShape(8.dp)),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .size(52.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(Color.DarkGray),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.LightGray)
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                song.title, 
                                                color = Color.White, 
                                                fontSize = 15.sp, 
                                                maxLines = 1,
                                                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                                            )
                                            Text(song.artist, color = Color.Gray, fontSize = 13.sp, maxLines = 1)
                                        }
                                        
                                        // Options three dots button
                                        var menuExpanded by remember { mutableStateOf(false) }
                                        Box {
                                            IconButton(onClick = { menuExpanded = true }) {
                                                Icon(Icons.Default.MoreVert, contentDescription = "Options", tint = Color.LightGray)
                                            }
                                            DropdownMenu(
                                                expanded = menuExpanded,
                                                onDismissRequest = { menuExpanded = false }
                                            ) {
                                                DropdownMenuItem(
                                                    text = { Text("Add to Queue") },
                                                    onClick = {
                                                        viewModel.addToQueue(song)
                                                        menuExpanded = false
                                                    }
                                                )
                                                DropdownMenuItem(
                                                    text = { Text("Change Weight") },
                                                    onClick = {
                                                        showWeightEditDialog = song
                                                        menuExpanded = false
                                                    }
                                                )
                                                DropdownMenuItem(
                                                    text = { Text("Remove from Playlist") },
                                                    onClick = {
                                                        songToRemove = song
                                                        menuExpanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Transparent Parent Gesture Interceptor while dragging
                    if (draggedIndex != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(Unit) {
                                    detectDragGestures(
                                        onDragEnd = {
                                            val from = draggedIndex
                                            val to = currentTargetIndexRef.value
                                            if (from != null && to != null && from != to) {
                                                viewModel.reorderSongInPlaylist(playlist.id, from, to)
                                            }
                                            draggedIndex = null
                                        },
                                        onDragCancel = {
                                            val from = draggedIndex
                                            val to = currentTargetIndexRef.value
                                            if (from != null && to != null && from != to) {
                                                viewModel.reorderSongInPlaylist(playlist.id, from, to)
                                            }
                                            draggedIndex = null
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            targetScreenY += dragAmount.y
                                        }
                                    )
                                }
                        )
                    }

                    // Floating Overlay Card for Dragged Song — matches regular card look
                    val draggedSong = currentDraggedIndex?.let { songs.getOrNull(it) }
                    if (draggedSong != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .graphicsLayer {
                                    translationY = targetScreenY
                                    scaleX = 1.03f
                                    scaleY = 1.03f
                                    shadowElevation = 16f
                                }
                        ) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DragHandle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(36.dp).padding(4.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    if (draggedSong.artworkPath != null) {
                                        AsyncImage(
                                            model = draggedSong.artworkPath,
                                            contentDescription = "Album Art",
                                            modifier = Modifier
                                                .size(52.dp)
                                                .clip(RoundedCornerShape(8.dp)),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(52.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color.DarkGray),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.LightGray)
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            draggedSong.title,
                                            color = Color.White,
                                            fontSize = 15.sp,
                                            maxLines = 1,
                                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                                        )
                                        Text(draggedSong.artist, color = Color.Gray, fontSize = 13.sp, maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                }

                }
        }

        songToRemove?.let { songToDelete ->
            AlertDialog(
                onDismissRequest = { songToRemove = null },
                title = { Text("Remove Song") },
                text = { Text("Are you sure you want to remove '${songToDelete.title}' from this playlist?") },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.removeSongFromPlaylist(playlist.id, songToDelete.id)
                            songToRemove = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB71C1C))
                    ) { Text("Remove", color = Color.White) }
                },
                dismissButton = {
                    TextButton(onClick = { songToRemove = null }) { Text("Cancel") }
                }
            )
        }

        showWeightEditDialog?.let { song ->
            var weightInput by remember { mutableFloatStateOf(song.baseWeight) }
            AlertDialog(
                onDismissRequest = { showWeightEditDialog = null },
                title = { Text("Edit Song Weight") },
                text = {
                    Column {
                        Text("Song: ${song.title}", color = Color.White, style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Weight Multiplier: %.2fx".format(Locale.US, weightInput), color = Color.LightGray)
                        Slider(
                            value = weightInput,
                            onValueChange = { weightInput = it },
                            valueRange = 0.1f..5.0f
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.updateSongWeight(song.id, weightInput)
                            showWeightEditDialog = null
                        }
                    ) { Text("Save") }
                },
                dismissButton = {
                    TextButton(onClick = { showWeightEditDialog = null }) { Text("Cancel") }
                }
            )
        }

        if (showRenameDialog) {
            var newNameInput by remember { mutableStateOf(playlist.name) }
            AlertDialog(
                onDismissRequest = { showRenameDialog = false },
                title = { Text("Rename Playlist") },
                text = {
                    OutlinedTextField(
                        value = newNameInput,
                        onValueChange = { newNameInput = it },
                        label = { Text("New Name") },
                        singleLine = true
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (newNameInput.isNotBlank()) {
                                viewModel.renamePlaylist(playlist.id, newNameInput)
                                showRenameDialog = false
                            }
                        }
                    ) { Text("Save") }
                },
                dismissButton = {
                    TextButton(onClick = { showRenameDialog = false }) { Text("Cancel") }
                }
            )
        }

        if (showAddSongsDialog) {
            var addSearchQuery by remember { mutableStateOf("") }
            val filteredAddSongs = remember(addSongsList, addSearchQuery) {
                addSongsList.filter {
                    it.title.contains(addSearchQuery, ignoreCase = true) ||
                    it.artist.contains(addSearchQuery, ignoreCase = true)
                }
            }

            Dialog(
                onDismissRequest = { showAddSongsDialog = false },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Add Songs to Playlist", style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                            IconButton(onClick = { showAddSongsDialog = false }) {
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        OutlinedTextField(
                            value = addSearchQuery,
                            onValueChange = { addSearchQuery = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Search library songs...") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                focusedLabelColor = MaterialTheme.colorScheme.primary
                            )
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        if (filteredAddSongs.isEmpty()) {
                            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Text("No matching songs found.", color = Color.Gray)
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(filteredAddSongs) { song ->
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { viewModel.addSongToPlaylist(playlist.id, song.id) },
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                    ) {
                                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Text(song.title, color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
                                            Icon(Icons.Default.Add, contentDescription = "Add", tint = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showStatsDialog) {
            PlaylistStatsDialog(
                playlistName = playlist.name,
                playlistId = playlist.id,
                viewModel = viewModel,
                onDismiss = { showStatsDialog = false }
            )
        }
    }

@Composable
fun PlaylistStatsDialog(
    playlistName: String,
    playlistId: Int,
    viewModel: MusicViewModel,
    onDismiss: () -> Unit
) {
    val stats by viewModel.playlistStats.collectAsState()
    val keepers by viewModel.playlistKeepers.collectAsState()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("$playlistName Stats", style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                        }
                    }
                }

                item {
                    Text("Skipped to", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                }

                if (keepers.isEmpty()) {
                    item {
                        Text("No skip-over destination events logged for this playlist.", color = Color.Gray, fontSize = 14.sp)
                    }
                } else {
                    items(keepers) { entry ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(entry.title, color = Color.White, fontSize = 14.sp, maxLines = 1)
                                Text(entry.artist, color = Color.Gray, fontSize = 12.sp, maxLines = 1)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("${entry.count} Skips To", color = MaterialTheme.colorScheme.primary, fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        }
                    }
                }

                item {
                    Text("Track Weights & Skips", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                }

                if (stats.isEmpty()) {
                    item {
                        Text("No tracks inside this playlist.", color = Color.Gray, fontSize = 14.sp)
                    }
                } else {
                    items(stats) { stat ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(stat.title, color = Color.White, fontSize = 14.sp, maxLines = 1, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                                        Text(stat.artist, color = Color.Gray, fontSize = 12.sp, maxLines = 1)
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Skip: %.0f%%".format(Locale.US, stat.skipRate * 100), color = if (stat.skipRate > 0.5f) Color.Red else Color.Gray, fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                                }
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                // Direct adjust slider
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Prob: %.2fx".format(Locale.US, stat.baseWeight), color = Color.LightGray, fontSize = 12.sp, modifier = Modifier.width(68.dp))
                                    Slider(
                                        value = stat.baseWeight,
                                        onValueChange = { viewModel.updateSongWeight(stat.songId, it) },
                                        valueRange = 0.1f..5.0f,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            viewModel.resetPlaylistStats(playlistId)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB71C1C)),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Reset Playlist Stats & Weights", color = Color.White, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(viewModel: MusicViewModel) {
    val serverIp by viewModel.serverIp.collectAsState()
    val crossfade by viewModel.crossfadeSeconds.collectAsState()
    val useWeightedShuffle by viewModel.useWeightedShuffle.collectAsState()
    val useSkipPenalty by viewModel.useSkipPenalty.collectAsState()
    val useKeeperBonus by viewModel.useKeeperBonus.collectAsState()
    
    var ipInput by remember { mutableStateOf(serverIp) }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("App Settings", style = MaterialTheme.typography.headlineMedium, color = Color.White)
        
        OutlinedTextField(
            value = ipInput,
            onValueChange = { ipInput = it; viewModel.updateServerIp(it) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Tailscale Server IP Address") }
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Crossfade Duration (${crossfade}s)", color = Color.White)
            Slider(
                value = crossfade.toFloat(),
                onValueChange = { viewModel.updateCrossfadeSeconds(it.toInt()) },
                valueRange = 0f..15f,
                modifier = Modifier.width(180.dp)
            )
        }

        HorizontalDivider()

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Weighted Shuffle", color = Color.White)
                Text("Increase/decrease selection odds", color = Color.Gray, fontSize = 12.sp)
            }
            Switch(checked = useWeightedShuffle, onCheckedChange = { viewModel.updateWeightedShuffle(it) })
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Auto Skip Penalty", color = Color.White)
                Text("Lowers odds for highly skipped tracks", color = Color.Gray, fontSize = 12.sp)
            }
            Switch(checked = useSkipPenalty, onCheckedChange = { viewModel.updateSkipPenalty(it) })
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Auto Keeper Bonus", color = Color.White)
                Text("Boosts landing target tracks", color = Color.Gray, fontSize = 12.sp)
            }
            Switch(checked = useKeeperBonus, onCheckedChange = { viewModel.updateKeeperBonus(it) })
        }
    }
}

@Composable
fun FullPlayerDialog(song: SongEntity, viewModel: MusicViewModel, onDismiss: () -> Unit) {
    val playerManager = viewModel.playerManager.collectAsState().value ?: return
    val isPlaying by playerManager.isPlaying.collectAsState()
    val progress by playerManager.playbackProgress.collectAsState()
    val duration = playerManager.getDuration()
    
    val isLooping by viewModel.isLooping.collectAsState()
    val useWeightedShuffle by viewModel.useWeightedShuffle.collectAsState()
    val cooldownFormula by viewModel.cooldownFormula.collectAsState()
    
    var showTuningSheet by remember { mutableStateOf(false) }
    var showQueueDialog by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(32.dp))
                    }
                    Text("Now Playing", style = MaterialTheme.typography.titleMedium, color = Color.White)
                    IconButton(onClick = { /* More options */ }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Options", tint = Color.White)
                    }
                }

                // Album Art
                Spacer(modifier = Modifier.height(16.dp))
                if (song.artworkPath != null) {
                    AsyncImage(
                        model = song.artworkPath,
                        contentDescription = "Album Art",
                        modifier = Modifier
                            .size(300.dp)
                            .clip(RoundedCornerShape(16.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(300.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.DarkGray),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(80.dp), tint = Color.LightGray)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Song Title & Artist info
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                    Text(song.title, color = Color.White, fontSize = 24.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, maxLines = 1)
                    Text(song.artist, color = Color.Gray, fontSize = 16.sp, maxLines = 1)
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Progress Bar Slider
                val progressRatio = if (duration > 0) progress.toFloat() / duration else 0f
                Column(modifier = Modifier.fillMaxWidth()) {
                    Slider(
                        value = progressRatio,
                        onValueChange = { playerManager.seekTo((it * duration).toLong()) },
                        colors = SliderDefaults.colors(
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            thumbColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(formatTime(progress), color = Color.Gray, fontSize = 12.sp)
                        Text(formatTime(duration), color = Color.Gray, fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Playback controls row (Shuffle, Prev, Play, Next, Repeat)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { viewModel.toggleShuffle() }) {
                        Icon(
                            imageVector = Icons.Default.Shuffle,
                            contentDescription = "Shuffle",
                            tint = if (useWeightedShuffle) MaterialTheme.colorScheme.primary else Color.Gray,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    IconButton(onClick = { viewModel.playPrevious() }) {
                        Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", tint = Color.White, modifier = Modifier.size(36.dp))
                    }

                    IconButton(
                        onClick = { if (isPlaying) playerManager.pause() else playerManager.resume() },
                        modifier = Modifier
                            .size(72.dp)
                            .background(Color.White, RoundedCornerShape(36.dp))
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Play/Pause",
                            tint = Color.Black,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    IconButton(onClick = { viewModel.playNext() }) {
                        Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = Color.White, modifier = Modifier.size(36.dp))
                    }

                    IconButton(onClick = { viewModel.toggleLooping() }) {
                        Icon(
                            imageVector = Icons.Default.Repeat,
                            contentDescription = "Loop",
                            tint = if (isLooping) MaterialTheme.colorScheme.primary else Color.Gray,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Bottom Tuning & Queue buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { showTuningSheet = true }) {
                        Icon(Icons.Default.Tune, contentDescription = "Playback settings & weights", tint = Color.LightGray, modifier = Modifier.size(28.dp))
                    }
                    IconButton(onClick = { showQueueDialog = true }) {
                        Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = "Play Queue", tint = Color.LightGray, modifier = Modifier.size(28.dp))
                    }
                }
            }
        }
    }

    if (showQueueDialog) {
        QueueDialog(viewModel = viewModel, onDismiss = { showQueueDialog = false })
    }

    if (showTuningSheet) {
        var weightInput by remember { mutableFloatStateOf(song.baseWeight) }
        var formulaInput by remember { mutableStateOf(cooldownFormula) }
        
        AlertDialog(
            onDismissRequest = { showTuningSheet = false },
            title = { Text("Playback Customization") },
            text = {
                Column {
                    Text("Dynamic Weight Settings", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    if (song.id > 0) {
                        Text("Current Song Weight: %.2fx".format(Locale.US, weightInput), color = Color.White)
                        Slider(
                            value = weightInput,
                            onValueChange = { 
                                weightInput = it
                                viewModel.updateSongWeight(song.id, it)
                            },
                            valueRange = 0.1f..5.0f,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    
                    Text("Cooldown Decay Formula", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Text("Defines played tracks lockout size. Rounds up (Ceiling). E.g. n/3, 3*log(n), n-1", color = Color.Gray, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    OutlinedTextField(
                        value = formulaInput,
                        onValueChange = { formulaInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("e.g. n/3") }
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateCooldownFormula(formulaInput)
                        showTuningSheet = false
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showTuningSheet = false }) { Text("Cancel") }
            }
        )
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}

@Composable
fun SearchDetailDialog(
    track: SearchTrackDto,
    viewModel: MusicViewModel,
    onDismiss: () -> Unit
) {
    val downloads by viewModel.downloadProgress.collectAsState()
    val localSongs by viewModel.allSongs.collectAsState()
    val playlists by viewModel.allPlaylists.collectAsState()
    val playerManager by viewModel.playerManager.collectAsState()
    val currentSong = playerManager?.currentPlayingSong?.collectAsState(null)?.value
    val isPlaying = playerManager?.isPlaying?.collectAsState(false)?.value ?: false
    val isThisTrackPlaying = currentSong?.youtubeVideoId == track.id
    
    val isDownloaded = localSongs.any { it.youtubeVideoId == track.id }
    val isDownloading = downloads.containsKey(track.id)
    val downloadPercent = downloads[track.id] ?: 0f

    var playlistExpanded by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(32.dp))
                    }
                    Text("Track Details", style = MaterialTheme.typography.titleMedium, color = Color.White)
                    Box(modifier = Modifier.size(48.dp))
                }

                // Artwork (Centered)
                Spacer(modifier = Modifier.height(16.dp))
                if (track.thumbnail.isNotEmpty()) {
                    AsyncImage(
                        model = track.thumbnail,
                        contentDescription = "Thumbnail",
                        modifier = Modifier
                            .size(300.dp)
                            .clip(RoundedCornerShape(16.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(300.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.DarkGray),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(80.dp), tint = Color.LightGray)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Song Title & Artist info
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        track.title, 
                        color = Color.White, 
                        fontSize = 24.sp, 
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, 
                        maxLines = 2,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(track.uploader, color = Color.Gray, fontSize = 16.sp, maxLines = 1, textAlign = TextAlign.Center)
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Status tag
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(Color.DarkGray.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    if (isDownloaded) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Downloaded & Offline", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
                    } else if (isDownloading) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Pre-downloading... %.0f%%".format(Locale.US, downloadPercent * 100), color = Color.Yellow, fontSize = 13.sp)
                    } else {
                        Text("Streaming Available", color = Color.White, fontSize = 13.sp)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Actions Column (Play, Download, Add to Playlist)
                Column(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { 
                            val manager = playerManager
                            if (isThisTrackPlaying && manager != null) {
                                if (isPlaying) manager.pause() else manager.resume()
                            } else {
                                viewModel.playOrStreamSearchTrack(track)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(
                            imageVector = if (isThisTrackPlaying && isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.Black
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isThisTrackPlaying && isPlaying) "Pause Song" else "Play Song",
                            color = Color.Black,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                    }

                    if (!isDownloaded) {
                        OutlinedButton(
                            onClick = { viewModel.downloadYouTubeTrack(track) },
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isDownloading) "Downloading..." else "Download to Library",
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { playlistExpanded = true },
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            border = BorderStroke(1.dp, Color.Gray)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Add to Playlist", color = Color.White)
                        }
                        
                        DropdownMenu(
                            expanded = playlistExpanded,
                            onDismissRequest = { playlistExpanded = false }
                        ) {
                            if (playlists.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("No playlists available") },
                                    onClick = { playlistExpanded = false }
                                )
                            } else {
                                playlists.forEach { playlist ->
                                    DropdownMenuItem(
                                        text = { Text(playlist.name) },
                                        onClick = {
                                            viewModel.addSearchTrackToPlaylist(track, playlist.id)
                                            playlistExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun QueueDialog(viewModel: MusicViewModel, onDismiss: () -> Unit) {
    val queue by viewModel.currentQueueFlow.collectAsState()
    val activeIndex = viewModel.activeQueueIndex
    val listState = rememberLazyListState()
    
    var draggedIndex by remember { mutableStateOf<Int?>(null) }
    var targetScreenY by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val itemHeightPx = with(density) { 64.dp.toPx() }

    LaunchedEffect(draggedIndex) {
        if (draggedIndex == null) return@LaunchedEffect
        while (isActive && draggedIndex != null) {
            val layoutInfo = listState.layoutInfo
            val viewportHeight = layoutInfo.viewportSize.height.toFloat()
            if (viewportHeight > 0f) {
                val cardCenterY = targetScreenY + (itemHeightPx / 2f)
                val topThreshold = 140f
                val bottomThreshold = viewportHeight - 140f
                
                var scrollDelta = 0f
                if (cardCenterY > bottomThreshold) {
                    val overflow = cardCenterY - bottomThreshold
                    scrollDelta = (overflow * 0.35f).coerceIn(8f, 50f)
                } else if (cardCenterY < topThreshold) {
                    val overflow = topThreshold - cardCenterY
                    scrollDelta = -(overflow * 0.35f).coerceIn(8f, 50f)
                }
                
                if (scrollDelta != 0f) {
                    listState.scrollBy(scrollDelta)
                }
            }
            delay(16)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Play Queue", style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                
                if (queue.isEmpty()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("Queue is empty", color = Color.Gray)
                    }
                } else {
                    val currentDraggedIndex = draggedIndex
                    // Mutable ref so onDragEnd/onDragCancel always read the latest value
                    var currentTargetIndex by remember { mutableStateOf<Int?>(null) }
                    val currentTargetIndexRef = rememberUpdatedState(currentTargetIndex)

                    // Recalculate target index reactively whenever drag position or scroll changes
                    LaunchedEffect(currentDraggedIndex, targetScreenY, listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) {
                        currentTargetIndex = if (currentDraggedIndex == null) null
                        else {
                            val visibleItems = listState.layoutInfo.visibleItemsInfo
                            if (visibleItems.isEmpty()) currentDraggedIndex
                            else {
                                val cardCenterY = targetScreenY + (itemHeightPx / 2f)
                                val closestItem = visibleItems.minByOrNull { item ->
                                    val itemCenter = item.offset + (item.size / 2f)
                                    kotlin.math.abs(cardCenterY - itemCenter)
                                }
                                if (closestItem != null) {
                                    closestItem.index.coerceIn(0, queue.size - 1)
                                } else currentDraggedIndex
                            }
                        }
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            userScrollEnabled = draggedIndex == null
                        ) {
                            itemsIndexed(queue, key = { _, song -> song.instanceId }) { index, song ->
                                val isActive = index == activeIndex
                                val isDraggedItem = index == currentDraggedIndex
                                
                                val targetTranslationY = run {
                                    val dragIdx = currentDraggedIndex
                                    val targetIdx = currentTargetIndex
                                    when {
                                        dragIdx != null && targetIdx != null -> {
                                            if (dragIdx < targetIdx && index > dragIdx && index <= targetIdx) {
                                                -itemHeightPx
                                            } else if (dragIdx > targetIdx && index < dragIdx && index >= targetIdx) {
                                                itemHeightPx
                                            } else 0f
                                        }
                                        else -> 0f
                                    }
                                }
                                
                            val animatedY by animateFloatAsState(
                                targetValue = targetTranslationY,
                                animationSpec = if (currentDraggedIndex != null) spring(stiffness = Spring.StiffnessMediumLow) else snap(),
                                label = "reorderTranslation"
                            )

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .animateItem()
                                    .graphicsLayer {
                                        translationY = animatedY
                                        alpha = if (isDraggedItem) 0.25f else 1.0f
                                    },
                                    elevation = CardDefaults.cardElevation(
                                        defaultElevation = if (isActive) 2.dp else 0.dp
                                    ),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isActive) 
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) 
                                        else 
                                            MaterialTheme.colorScheme.surface
                                    ),
                                    border = if (isActive) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { viewModel.playQueueSongAt(index) }
                                            .padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DragHandle,
                                            contentDescription = "Reorder Queue",
                                            tint = if (isDraggedItem) MaterialTheme.colorScheme.primary else Color.Gray,
                                            modifier = Modifier
                                                .size(36.dp)
                                                .padding(4.dp)
                                                .pointerInput(index) {
                                                    detectDragGestures(
                                                        onDragStart = { touchOffset -> 
                                                            val itemInfo = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == song.instanceId }
                                                            val startY = itemInfo?.offset?.toFloat() ?: (index * itemHeightPx)
                                                            draggedIndex = index
                                                            targetScreenY = startY + touchOffset.y - (itemHeightPx / 2f)
                                                        },
                                                        onDragEnd = {
                                                            val from = draggedIndex
                                                            val to = currentTargetIndexRef.value
                                                            if (from != null && to != null && from != to) {
                                                                viewModel.moveQueueItem(from, to)
                                                            }
                                                            draggedIndex = null
                                                        },
                                                        onDragCancel = { 
                                                            val from = draggedIndex
                                                            val to = currentTargetIndexRef.value
                                                            if (from != null && to != null && from != to) {
                                                                viewModel.moveQueueItem(from, to)
                                                            }
                                                            draggedIndex = null
                                                        },
                                                        onDrag = { change, dragAmount ->
                                                            change.consume()
                                                            targetScreenY += dragAmount.y
                                                        }
                                                    )
                                                }
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))

                                        if (song.artworkPath != null) {
                                            AsyncImage(
                                                model = song.artworkPath,
                                                contentDescription = "Album Art",
                                                modifier = Modifier
                                                    .size(48.dp)
                                                    .clip(RoundedCornerShape(8.dp)),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .size(48.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(Color.DarkGray),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.LightGray)
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(12.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = song.title,
                                                color = if (isActive) MaterialTheme.colorScheme.primary else Color.White,
                                                fontSize = 14.sp,
                                                fontWeight = if (isActive) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.SemiBold,
                                                maxLines = 1
                                            )
                                            Text(
                                                text = song.artist,
                                                color = Color.Gray,
                                                fontSize = 12.sp,
                                                maxLines = 1
                                            )
                                        }

                                        if (isActive) {
                                            Text("Playing", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                                            Spacer(modifier = Modifier.width(4.dp))
                                        }

                                        var queueMenuExpanded by remember { mutableStateOf(false) }
                                        Box {
                                            IconButton(onClick = { queueMenuExpanded = true }) {
                                                Icon(Icons.Default.MoreVert, contentDescription = "Options", tint = Color.LightGray)
                                            }
                                            DropdownMenu(
                                                expanded = queueMenuExpanded,
                                                onDismissRequest = { queueMenuExpanded = false }
                                            ) {
                                                DropdownMenuItem(
                                                    text = { Text("Remove from Queue") },
                                                    onClick = {
                                                        viewModel.removeFromQueueAt(index)
                                                        queueMenuExpanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Transparent Parent Gesture Interceptor while dragging
                        if (draggedIndex != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pointerInput(Unit) {
                                        detectDragGestures(
                                            onDragEnd = {
                                                val from = draggedIndex
                                                val to = currentTargetIndexRef.value
                                                if (from != null && to != null && from != to) {
                                                    viewModel.moveQueueItem(from, to)
                                                }
                                                draggedIndex = null
                                            },
                                            onDragCancel = {
                                                val from = draggedIndex
                                                val to = currentTargetIndexRef.value
                                                if (from != null && to != null && from != to) {
                                                    viewModel.moveQueueItem(from, to)
                                                }
                                                draggedIndex = null
                                            },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                targetScreenY += dragAmount.y
                                            }
                                        )
                                    }
                            )
                        }

                        // Floating Overlay Card for Dragged Queue Song — matches regular card look
                        val draggedSong = currentDraggedIndex?.let { queue.getOrNull(it) }
                        val isActiveDragged = currentDraggedIndex == activeIndex
                        if (draggedSong != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .graphicsLayer {
                                        translationY = targetScreenY
                                        scaleX = 1.03f
                                        scaleY = 1.03f
                                        shadowElevation = 16f
                                    }
                            ) {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    elevation = CardDefaults.cardElevation(
                                        defaultElevation = if (isActiveDragged) 2.dp else 0.dp
                                    ),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isActiveDragged)
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                        else
                                            MaterialTheme.colorScheme.surface
                                    ),
                                    border = if (isActiveDragged) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DragHandle,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(36.dp).padding(4.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        if (draggedSong.artworkPath != null) {
                                            AsyncImage(
                                                model = draggedSong.artworkPath,
                                                contentDescription = "Album Art",
                                                modifier = Modifier
                                                    .size(48.dp)
                                                    .clip(RoundedCornerShape(8.dp)),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .size(48.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(Color.DarkGray),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.LightGray)
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = draggedSong.title,
                                                color = if (isActiveDragged) MaterialTheme.colorScheme.primary else Color.White,
                                                fontSize = 14.sp,
                                                fontWeight = if (isActiveDragged) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.SemiBold,
                                                maxLines = 1
                                            )
                                            Text(
                                                text = draggedSong.artist,
                                                color = Color.Gray,
                                                fontSize = 12.sp,
                                                maxLines = 1
                                            )
                                        }
                                        if (isActiveDragged) {
                                            Text("Playing", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
