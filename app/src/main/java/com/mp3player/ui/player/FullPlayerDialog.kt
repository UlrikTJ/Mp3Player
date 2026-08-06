package com.mp3player.ui.player

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
import com.mp3player.formatTime


@Composable
fun FullPlayerDialog(song: SongEntity, viewModel: MusicViewModel, onDismiss: () -> Unit) {
    val playerManager = viewModel.playerManager.collectAsState().value ?: return
    val isPlaying by playerManager.isPlaying.collectAsState()
    val progress by playerManager.playbackProgress.collectAsState()
    val playerDuration = playerManager.getDuration()
    val duration = if (playerDuration > 0) playerDuration else (song.durationMs.takeIf { it > 0 } ?: 0L)
    
    val isLooping by viewModel.isLooping.collectAsState()
    val useWeightedShuffle by viewModel.useWeightedShuffle.collectAsState()
    val cooldownFormula by viewModel.cooldownFormula.collectAsState()
    
    var showTuningSheet by remember { mutableStateOf(false) }
    var showQueueDialog by remember { mutableStateOf(false) }

    var optionsMenuExpanded by remember { mutableStateOf(false) }
    var showPlaylistPicker by remember { mutableStateOf(false) }
    var showAppDeleteConfirm by remember { mutableStateOf(false) }
    var showDeviceDeleteConfirm by remember { mutableStateOf(false) }

    val activePlaylistId by viewModel.activePlaylistId.collectAsState()
    val playlists by viewModel.allPlaylists.collectAsState()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            // Blurred background layer
            if (song.artworkPath != null) {
                AsyncImage(
                    model = song.artworkPath,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(100.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                        .graphicsLayer(alpha = 0.5f),
                    contentScale = ContentScale.Crop
                )
            }
            // Gradient Overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.2f),
                                Color.Black.copy(alpha = 0.8f),
                                Color.Black.copy(alpha = 1.0f)
                            )
                        )
                    )
            )

            // Content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 48.dp),
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
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(36.dp))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("PLAYING FROM PLAYLIST", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f), letterSpacing = 1.5.sp)
                        val activePlaylist by viewModel.activePlaylistName.collectAsState()
                        Text(activePlaylist ?: "Library", style = MaterialTheme.typography.bodyMedium, color = Color.White, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    }
                    Box {
                        IconButton(onClick = { optionsMenuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Options", tint = Color.White)
                        }
                        DropdownMenu(
                            expanded = optionsMenuExpanded,
                            onDismissRequest = { optionsMenuExpanded = false }
                        ) {
                            if (activePlaylistId != null) {
                                DropdownMenuItem(
                                    text = { Text("Remove from Playlist", color = Color(0xFFE53935)) },
                                    onClick = {
                                        optionsMenuExpanded = false
                                        viewModel.removeSongFromPlaylist(activePlaylistId!!, song.id)
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Add to Playlist") },
                                onClick = {
                                    optionsMenuExpanded = false
                                    showPlaylistPicker = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete from App (Ignore file)") },
                                onClick = {
                                    optionsMenuExpanded = false
                                    showAppDeleteConfirm = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete from Device", color = Color(0xFFB71C1C)) },
                                onClick = {
                                    optionsMenuExpanded = false
                                    showDeviceDeleteConfirm = true
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // Album Art
                if (song.artworkPath != null) {
                    AsyncImage(
                        model = song.artworkPath,
                        contentDescription = "Album Art",
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .shadow(elevation = 24.dp, shape = RoundedCornerShape(12.dp))
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .shadow(elevation = 24.dp, shape = RoundedCornerShape(12.dp))
                            .background(Color.DarkGray.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(120.dp), tint = Color.White.copy(alpha = 0.3f))
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Song Title & Artist info
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                        Text(song.title, color = Color.White, fontSize = 28.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, maxLines = 1, modifier = Modifier.basicMarquee())
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(song.artist, color = Color.White.copy(alpha = 0.7f), fontSize = 18.sp, maxLines = 1, modifier = Modifier.basicMarquee())
                    }
                    IconButton(onClick = { /* Like feature stub */ }) {
                        Icon(Icons.Default.FavoriteBorder, contentDescription = "Like", tint = Color.White, modifier = Modifier.size(28.dp))
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Progress Bar Slider
                val isSaneDuration = duration > 0 && duration < 24 * 60 * 60 * 1000L
                val progressRatio = if (isSaneDuration) (progress.toFloat() / duration).coerceIn(0f, 1f) else 0f
                Column(modifier = Modifier.fillMaxWidth()) {
                    Slider(
                        value = progressRatio,
                        onValueChange = { if (isSaneDuration) playerManager.seekTo((it * duration).toLong()) },
                        colors = SliderDefaults.colors(
                            activeTrackColor = Color.White,
                            inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                            thumbColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth().height(24.dp)
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(formatTime(progress), color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                        Text(formatTime(duration), color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Playback controls row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { viewModel.toggleShuffle() }) {
                        Icon(
                            imageVector = Icons.Default.Shuffle,
                            contentDescription = "Shuffle",
                            tint = if (useWeightedShuffle) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    IconButton(onClick = { viewModel.playPrevious() }) {
                        Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", tint = Color.White, modifier = Modifier.size(42.dp))
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
                        Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = Color.White, modifier = Modifier.size(42.dp))
                    }

                    IconButton(onClick = { viewModel.toggleLooping() }) {
                        Icon(
                            imageVector = Icons.Default.Repeat,
                            contentDescription = "Loop",
                            tint = if (isLooping) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // Bottom Tuning & Queue buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { showTuningSheet = true }) {
                        Icon(Icons.Default.Tune, contentDescription = "Playback settings", tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(24.dp))
                    }
                    IconButton(onClick = { showQueueDialog = true }) {
                        Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = "Play Queue", tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(24.dp))
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

    if (showAppDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showAppDeleteConfirm = false },
            title = { Text("Remove from App?") },
            text = { Text("This will hide '${song.title}' from the app without deleting the original audio file.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteSongFromApp(song)
                    showAppDeleteConfirm = false
                    onDismiss()
                }) {
                    Text("Remove", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAppDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }

    if (showDeviceDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeviceDeleteConfirm = false },
            title = { Text("Delete from Device?") },
            text = { Text("This will permanently delete '${song.title}' from your storage. This action cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteSongFromDevice(song)
                    showDeviceDeleteConfirm = false
                    onDismiss()
                }) {
                    Text("Delete Permanently", color = Color(0xFFB71C1C))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeviceDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }
}
