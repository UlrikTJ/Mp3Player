package com.mp3player.ui.screens

import android.content.ComponentName
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

import com.mp3player.R
import com.mp3player.data.entity.*
import com.mp3player.ui.viewmodel.*
import com.mp3player.ui.theme.*
import com.mp3player.ui.components.*
import com.mp3player.ui.player.*
import com.mp3player.ui.screens.*


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailView(
    playlist: com.mp3player.data.entity.PlaylistEntity,
    viewModel: MusicViewModel,
    onBack: () -> Unit
) {
    val songs by viewModel.playlistSongs.collectAsState()
    var displaySongs by remember(songs) { mutableStateOf(songs) }
    val addSongsList by viewModel.songsNotInPlaylist.collectAsState()
    val playerManager by viewModel.playerManager.collectAsState()
    val isPlaying = playerManager?.isPlaying?.collectAsState(false)?.value ?: false
    val currentSong = playerManager?.currentPlayingSong?.collectAsState(null)?.value
    val playlistStatsForCover by viewModel.playlistStats.collectAsState()
    
    var showAddSongsDialog by remember { mutableStateOf(false) }
    var showStatsDialog by remember { mutableStateOf(false) }
    var showPlaylistStats by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var songToRemove by remember { mutableStateOf<SongEntity?>(null) }
    var showWeightEditDialog by remember { mutableStateOf<SongEntity?>(null) }
    var isEditMode by remember { mutableStateOf(false) }
    var deletedSongsHistory by remember { mutableStateOf<List<Pair<Int, SongEntity>>>(emptyList()) }
    
    var draggedIndex by remember { mutableStateOf<Int?>(null) }
    var initialDraggedIndex by remember { mutableStateOf<Int?>(null) }
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
            // Top Action Bar with sticky Play/Pause button or Edit mode controls
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
                
                if (isEditMode) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(
                            onClick = {
                                if (deletedSongsHistory.isNotEmpty()) {
                                    val (origIdx, restoredSong) = deletedSongsHistory.last()
                                    deletedSongsHistory = deletedSongsHistory.dropLast(1)
                                    val updated = displaySongs.toMutableList()
                                    val insertPos = origIdx.coerceIn(0, updated.size)
                                    updated.add(insertPos, restoredSong)
                                    displaySongs = updated
                                }
                            },
                            enabled = deletedSongsHistory.isNotEmpty()
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Undo,
                                contentDescription = "Undo Last Removal",
                                tint = if (deletedSongsHistory.isNotEmpty()) MaterialTheme.colorScheme.primary else Color.Gray
                            )
                        }

                        TextButton(
                            onClick = {
                                if (deletedSongsHistory.isNotEmpty()) {
                                    displaySongs = songs
                                    deletedSongsHistory = emptyList()
                                }
                            },
                            enabled = deletedSongsHistory.isNotEmpty()
                        ) {
                            Text("Revert All", color = if (deletedSongsHistory.isNotEmpty()) MaterialTheme.colorScheme.primary else Color.Gray, fontSize = 12.sp)
                        }

                        Button(
                            onClick = {
                                deletedSongsHistory.forEach { (_, song) ->
                                    viewModel.removeSongFromPlaylist(playlist.id, song.id)
                                }
                                deletedSongsHistory = emptyList()
                                isEditMode = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("Done", color = Color.Black, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        }
                    }
                } else {
                    val showStickyTitle by remember {
                        derivedStateOf {
                            playlistListState.firstVisibleItemIndex > 0 || playlistListState.firstVisibleItemScrollOffset > 40
                        }
                    }

                    val showStickyPlayButton by remember {
                        derivedStateOf {
                            playlistListState.firstVisibleItemIndex > 0 || playlistListState.firstVisibleItemScrollOffset > 130
                        }
                    }

                    if (showStickyTitle) {
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

                    if (showStickyPlayButton && songs.isNotEmpty()) {
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
            }

                val currentDraggedIndex = draggedIndex
                // Mutable ref so onDragEnd/onDragCancel always read the latest value
                var currentTargetIndex by remember { mutableStateOf<Int?>(null) }
                val currentTargetIndexRef = rememberUpdatedState(currentTargetIndex)

                // Recalculate target index reactively without recomposition loops
                LaunchedEffect(currentDraggedIndex) {
                    if (currentDraggedIndex == null) {
                        currentTargetIndex = null
                        return@LaunchedEffect
                    }
                    androidx.compose.runtime.snapshotFlow {
                        // Observe scroll state and drag position
                        playlistListState.firstVisibleItemIndex
                        playlistListState.firstVisibleItemScrollOffset
                        
                        val cardCenterY = targetScreenY + (playlistItemHeightPx / 2f)
                        val visibleItems = playlistListState.layoutInfo.visibleItemsInfo
                        if (visibleItems.isEmpty()) currentDraggedIndex
                        else {
                            val songItems = visibleItems.filter { it.index > 0 }
                            if (songItems.isEmpty()) currentDraggedIndex
                            else {
                                val closestItem = songItems.minByOrNull { item ->
                                    val itemCenter = item.offset + (item.size / 2f)
                                    kotlin.math.abs(cardCenterY - itemCenter)
                                }
                                if (closestItem != null) {
                                    (closestItem.index - 1).coerceIn(0, displaySongs.size - 1)
                                } else currentDraggedIndex
                            }
                        }
                    }.collect { target ->
                        currentTargetIndex = target
                        val from = draggedIndex
                        if (from != null && target != null && from != target) {
                            val updated = displaySongs.toMutableList()
                            if (from in updated.indices && target in updated.indices) {
                                val itemToMove = updated.removeAt(from)
                                updated.add(target, itemToMove)
                                displaySongs = updated
                                draggedIndex = target
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
                                        playlistId = playlist.id,
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
                                            "${displaySongs.size} tracks",
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
                                        val activePlaylistIdState by viewModel.activePlaylistId.collectAsState()
                                        val isThisPlaylistActive = activePlaylistIdState == playlist.id
                                        Button(
                                            onClick = {
                                                val manager = playerManager
                                                if (isThisPlaylistActive && manager != null) {
                                                    if (isPlaying) manager.pause() else manager.resume()
                                                } else {
                                                    viewModel.playPlaylist(playlist.id, shuffle = false)
                                                }
                                            },
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                        ) {
                                            Icon(
                                                imageVector = if (isThisPlaylistActive && isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                contentDescription = "Play/Pause",
                                                tint = Color.Black
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = if (isThisPlaylistActive && isPlaying) "Pause" else if (isThisPlaylistActive) "Resume" else "Play",
                                                color = Color.Black,
                                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                            )
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

                                // Playlist Management Options Row - Responsive compact design with unique icons
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextButton(
                                        onClick = { showRenameDialog = true },
                                        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 0.dp)
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text("Rename", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, maxLines = 1, softWrap = false)
                                    }
                                    TextButton(
                                        onClick = { showAddSongsDialog = true },
                                        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 0.dp)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text("Add", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, maxLines = 1, softWrap = false)
                                    }

                                    TextButton(
                                        onClick = { isEditMode = !isEditMode },
                                        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 0.dp)
                                    ) {
                                        Icon(Icons.Default.DragHandle, contentDescription = null, tint = if (isEditMode) MaterialTheme.colorScheme.primary else Color.LightGray, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text("Reorder", color = if (isEditMode) MaterialTheme.colorScheme.primary else Color.LightGray, fontSize = 11.sp, maxLines = 1, softWrap = false)
                                    }

                                    TextButton(
                                        onClick = { showPlaylistStats = true },
                                        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 0.dp)
                                    ) {
                                        Icon(Icons.Default.BarChart, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text("Stats", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, maxLines = 1, softWrap = false)
                                    }
                                    TextButton(
                                        onClick = { showStatsDialog = true },
                                        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 0.dp)
                                    ) {
                                        Icon(Icons.Default.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text("Options", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, maxLines = 1, softWrap = false)
                                    }
                                }


                                Spacer(modifier = Modifier.height(8.dp))
                                HorizontalDivider(color = Color.DarkGray.copy(alpha = 0.5f), thickness = 1.dp)
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                        }

                        if (displaySongs.isEmpty()) {
                            item {
                                Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                                    Text("This playlist is empty. Tap '+' to add songs!", color = Color.Gray)
                                }
                            }
                        } else {
                            itemsIndexed(displaySongs, key = { _, song -> song.id }) { index, song ->
                                val isDraggedItem = index == currentDraggedIndex

                                val targetTranslationY = run {
                                    val dragIdx = currentDraggedIndex
                                    val targetIdx = currentTargetIndex
                                    when {
                                        dragIdx != null && targetIdx != null -> {
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

                                val isTrackActive = currentSong != null && song.id == currentSong.id

                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .graphicsLayer {
                                            translationY = if (currentDraggedIndex != null) animatedY else 0f
                                            alpha = if (isDraggedItem) 0.25f else 1.0f
                                        },
                                    elevation = CardDefaults.cardElevation(defaultElevation = if (isTrackActive) 2.dp else 0.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isTrackActive)
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                        else
                                            MaterialTheme.colorScheme.surface
                                    ),
                                    border = if (isTrackActive) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { viewModel.playSongFromLibrary(song, playlist.id) }
                                            .padding(vertical = 8.dp, horizontal = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (isEditMode) {
                                            Icon(
                                                imageVector = Icons.Default.DragHandle,
                                                contentDescription = "Drag to reorder",
                                                tint = if (isDraggedItem) MaterialTheme.colorScheme.primary else Color.Gray,
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .padding(4.dp)
                                                    .pointerInput(song.id) {
                                                        detectDragGestures(
                                                            onDragStart = { touchOffset ->
                                                                val itemInfo = playlistListState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == song.id }
                                                                val startY = itemInfo?.offset?.toFloat() ?: (index * playlistItemHeightPx)
                                                                draggedIndex = index
                                                                initialDraggedIndex = index
                                                                targetScreenY = startY + touchOffset.y - (playlistItemHeightPx / 2f)
                                                            },
                                                            onDragEnd = {
                                                                val from = initialDraggedIndex
                                                                val to = draggedIndex
                                                                if (from != null && to != null && from != to) {
                                                                    viewModel.reorderSongInPlaylist(playlist.id, from, to)
                                                                }
                                                                draggedIndex = null
                                                                initialDraggedIndex = null
                                                            },
                                                            onDragCancel = {
                                                                val from = initialDraggedIndex
                                                                val to = draggedIndex
                                                                if (from != null && to != null && from != to) {
                                                                    viewModel.reorderSongInPlaylist(playlist.id, from, to)
                                                                }
                                                                draggedIndex = null
                                                                initialDraggedIndex = null
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
                                                color = if (isTrackActive) MaterialTheme.colorScheme.primary else Color.White, 
                                                fontSize = 15.sp, 
                                                maxLines = 1,
                                                fontWeight = if (isTrackActive) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.SemiBold
                                            )
                                            Text(song.artist, color = Color.Gray, fontSize = 13.sp, maxLines = 1)
                                        }
                                        
                                        if (isTrackActive) {
                                            Text("Playing", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                                            Spacer(modifier = Modifier.width(4.dp))
                                        }
                                        
                                        if (isEditMode) {
                                            IconButton(onClick = {
                                                val removedSong = song
                                                val removedIdx = index
                                                displaySongs = displaySongs.filter { it.id != song.id }
                                                deletedSongsHistory = deletedSongsHistory + (removedIdx to removedSong)
                                            }) {
                                                Icon(
                                                    imageVector = Icons.Default.RemoveCircle,
                                                    contentDescription = "Remove from Playlist",
                                                    tint = Color.Red.copy(alpha = 0.85f)
                                                )
                                            }
                                        } else {
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
                    }

                    // Floating Overlay Card for Dragged Song — matches regular card look
                    val draggedSong = currentDraggedIndex?.let { displaySongs.getOrNull(it) }
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
                    Column(modifier = Modifier.fillMaxSize()) {
                        Column(modifier = Modifier.weight(1f).fillMaxWidth().padding(24.dp)) {
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
                                items(filteredAddSongs, key = { it.id }) { song ->
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

                        val playerManager by viewModel.playerManager.collectAsState()
                        val currentSong = playerManager?.currentPlayingSong?.collectAsState(null)?.value
                        if (currentSong != null) {
                            MiniPlayer(song = currentSong, viewModel = viewModel)
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

        if (showPlaylistStats) {
            val stats by viewModel.playlistStats.collectAsState()
            val keepers by viewModel.playlistKeepers.collectAsState()
            val sortColumn by viewModel.statsSortColumn.collectAsState()
            val sortAscending by viewModel.statsSortAscending.collectAsState()

            StatsScreen(
                stats = stats,
                keepers = keepers,
                sortColumn = sortColumn,
                sortAscending = sortAscending,
                onSortChange = { 
                    if (viewModel.statsSortColumn.value == it) {
                        viewModel.statsSortAscending.value = !viewModel.statsSortAscending.value
                    } else {
                        viewModel.statsSortColumn.value = it
                        viewModel.statsSortAscending.value = false
                    }
                },
                onToggleAscending = { viewModel.statsSortAscending.value = !viewModel.statsSortAscending.value },
                title = "${playlist.name} Stats",
                onDismiss = { showPlaylistStats = false }
            )
        }
    }
