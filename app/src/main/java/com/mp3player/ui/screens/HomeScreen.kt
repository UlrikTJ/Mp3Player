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


@Composable
fun HomeScreen(
    viewModel: MusicViewModel,
    onOpenLibrary: () -> Unit,
    onOpenPlaylists: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val allSongs by viewModel.allSongs.collectAsState()
    val playlists by viewModel.allPlaylists.collectAsState()
    val recentSongs by viewModel.recentlyPlayedSongs.collectAsState()
    val playerManager by viewModel.playerManager.collectAsState()
    val currentSong = playerManager?.currentPlayingSong?.collectAsState(null)?.value
    val isPlaying = playerManager?.isPlaying?.collectAsState(false)?.value ?: false
    val songStats by viewModel.songStats.collectAsState()

    var showQueueDialog by remember { mutableStateOf(false) }
    var showGlobalStats by remember { mutableStateOf(false) }

    val greeting = remember {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        when (hour) {
            in 4..11 -> "Good Morning ☀️"
            in 12..17 -> "Good Afternoon 🌤️"
            else -> "Good Evening 🌙"
        }
    }

    val totalPlays = remember(songStats) { songStats.sumOf { it.totalPlays } }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            // Greeting Banner Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = greeting,
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "🎵 ${allSongs.size} tracks in library • ${playlists.size} playlists • $totalPlays plays",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray
                    )
                }
            }
        }

        // Hero Resume / Now Playing Card
        item {
            val heroSong = currentSong ?: recentSongs.firstOrNull() ?: allSongs.firstOrNull()
            if (heroSong != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (currentSong != null) {
                                if (isPlaying) playerManager?.pause() else playerManager?.resume()
                            } else {
                                viewModel.playSongFromLibrary(heroSong, null)
                            }
                        },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                    ),
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (heroSong.artworkPath != null) {
                            AsyncImage(
                                model = heroSong.artworkPath,
                                contentDescription = "Hero Art",
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(12.dp)),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.DarkGray),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.LightGray)
                            }
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (currentSong != null) "NOW PLAYING" else "QUICK RESUME",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = heroSong.title,
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                maxLines = 1
                            )
                            Text(
                                text = heroSong.artist,
                                color = Color.Gray,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1
                            )
                        }

                        IconButton(
                            onClick = {
                                if (currentSong != null) {
                                    if (isPlaying) playerManager?.pause() else playerManager?.resume()
                                } else {
                                    viewModel.playSongFromLibrary(heroSong, null)
                                }
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape)
                        ) {
                            Icon(
                                imageVector = if (isPlaying && currentSong != null) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play/Pause",
                                tint = Color.Black
                            )
                        }
                    }
                }
            }
        }

        // Quick Action Chips Row
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AssistChip(
                    onClick = { viewModel.playAllShuffled() },
                    label = { Text("🔀 Shuffle All", color = Color.White, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold) },
                    colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surface)
                )
                AssistChip(
                    onClick = { onOpenLibrary() },
                    label = { Text("🎵 All Tracks", color = Color.White, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold) },
                    colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surface)
                )
                AssistChip(
                    onClick = { showGlobalStats = true },
                    label = { Text("📊 Library Stats", color = Color.White, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold) },
                    colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surface)
                )
                AssistChip(
                    onClick = { showQueueDialog = true },
                    label = { Text("⚡ Queue", color = Color.White, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold) },
                    colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surface)
                )
            }
        }

        // Recently Played Section
        if (recentSongs.isNotEmpty()) {
            item {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Recently Played",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        recentSongs.forEach { song ->
                            Card(
                                modifier = Modifier
                                    .width(130.dp)
                                    .clickable { viewModel.playSongFromLibrary(song, null) },
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    if (song.artworkPath != null) {
                                        AsyncImage(
                                            model = song.artworkPath,
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(114.dp)
                                                .clip(RoundedCornerShape(8.dp)),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(114.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color.DarkGray),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.LightGray)
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = song.title,
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = song.artist,
                                        color = Color.Gray,
                                        fontSize = 11.sp,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }

                }
            }
        }

        // Top Playlists Section
        if (playlists.isNotEmpty()) {
            item {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "My Playlists",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                        TextButton(onClick = onOpenPlaylists) {
                            Text("See All", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        playlists.take(4).forEach { playlist ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.selectPlaylist(playlist.id) },
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    PlaylistCardCover(
                                        playlistId = playlist.id,
                                        viewModel = viewModel,
                                        modifier = Modifier.size(50.dp)
                                    )
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = playlist.name,
                                            color = Color.White,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                            maxLines = 1
                                        )
                                    }
                                    IconButton(
                                        onClick = { viewModel.playPlaylist(playlist.id, shuffle = false) }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "Play Playlist",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }

                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showQueueDialog) {
        QueueDialog(viewModel = viewModel, onDismiss = { showQueueDialog = false })
    }

    if (showGlobalStats) {
        val statsSortCol by viewModel.statsSortColumn.collectAsState()
        val statsSortAsc by viewModel.statsSortAscending.collectAsState()
        val keepersLeaderboard by viewModel.keepersLeaderboard.collectAsState()
        val sortedStats = remember(songStats, statsSortCol, statsSortAsc) {
            val comparator = when (statsSortCol) {
                MusicViewModel.StatsSortColumn.TITLE -> compareBy<SongStats> { it.title }
                MusicViewModel.StatsSortColumn.PLAY_COUNT -> compareBy { it.playCount }
                MusicViewModel.StatsSortColumn.SKIP_COUNT -> compareBy { it.skipCount }
                MusicViewModel.StatsSortColumn.SKIP_RATE -> compareBy { it.skipRate }
                MusicViewModel.StatsSortColumn.KEEPER_COUNT -> compareBy { it.keeperCount }
            }
            if (statsSortAsc) songStats.sortedWith(comparator) else songStats.sortedWith(comparator.reversed())
        }
        StatsScreen(
            stats = sortedStats,
            keepers = keepersLeaderboard,
            sortColumn = statsSortCol,
            sortAscending = statsSortAsc,
            onSortChange = { col ->
                if (viewModel.statsSortColumn.value == col) {
                    viewModel.statsSortAscending.value = !viewModel.statsSortAscending.value
                } else {
                    viewModel.statsSortColumn.value = col
                    viewModel.statsSortAscending.value = false
                }
            },
            onToggleAscending = { viewModel.statsSortAscending.value = !viewModel.statsSortAscending.value },
            title = "Global Library Stats",
            onDismiss = { showGlobalStats = false }
        )
    }
}
