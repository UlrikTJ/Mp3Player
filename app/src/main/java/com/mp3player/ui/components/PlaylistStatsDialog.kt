package com.mp3player.ui.components

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
fun PlaylistStatsDialog(
    playlistName: String,
    playlistId: Int,
    viewModel: MusicViewModel,
    onDismiss: () -> Unit
) {
    val stats by viewModel.playlistStats.collectAsState()
    val keepers by viewModel.playlistKeepers.collectAsState()

    var isSkippedToExpanded by remember { mutableStateOf(true) }
    var isWeightsExpanded by remember { mutableStateOf(true) }

    // Sort Category: 0 = Playlist Order, 1 = Skipped, 2 = Weight
    var activeSortCategory by remember { mutableIntStateOf(0) }
    var sortAscending by remember { mutableStateOf(true) }

    val sortedStats = remember(stats, activeSortCategory, sortAscending) {
        when (activeSortCategory) {
            0 -> if (sortAscending) stats else stats.reversed()
            1 -> if (sortAscending) {
                stats.sortedByDescending { it.skipRate * 1000f + it.skipCount }
            } else {
                stats.sortedBy { it.skipRate * 1000f + it.skipCount }
            }
            2 -> if (sortAscending) {
                stats.sortedByDescending { it.baseWeight }
            } else {
                stats.sortedBy { it.baseWeight }
            }
            else -> stats
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
            Column(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
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

                    // --- Section 1: Skipped To (Expandable Header) ---
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable { isSkippedToExpanded = !isSkippedToExpanded },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Skipped To (${keepers.size})", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                                Icon(
                                    imageVector = if (isSkippedToExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Toggle Skipped To",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    if (isSkippedToExpanded) {
                        if (keepers.isEmpty()) {
                            item {
                                Text("No skip-over destination events logged for this playlist.", color = Color.Gray, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 8.dp))
                            }
                        } else {
                            items(keepers, key = { it.songId }) { entry ->
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
                    }

                    // --- Section 2: Track Weights & Skips (Expandable Header & Sorting) ---
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable { isWeightsExpanded = !isWeightsExpanded },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Track Weights & Skips (${stats.size})", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                                Icon(
                                    imageVector = if (isWeightsExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Toggle Track Weights",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    if (isWeightsExpanded) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = activeSortCategory == 0,
                                    onClick = {
                                        if (activeSortCategory == 0) sortAscending = !sortAscending
                                        else { activeSortCategory = 0; sortAscending = true }
                                    },
                                    label = {
                                        Text("Order ${if (activeSortCategory == 0) (if (sortAscending) "↑" else "↓") else ""}", fontSize = 12.sp)
                                    }
                                )
                                FilterChip(
                                    selected = activeSortCategory == 1,
                                    onClick = {
                                        if (activeSortCategory == 1) sortAscending = !sortAscending
                                        else { activeSortCategory = 1; sortAscending = true }
                                    },
                                    label = {
                                        Text("Skipped ${if (activeSortCategory == 1) (if (sortAscending) "↓ (Most)" else "↑ (Least)") else ""}", fontSize = 12.sp)
                                    }
                                )
                                FilterChip(
                                    selected = activeSortCategory == 2,
                                    onClick = {
                                        if (activeSortCategory == 2) sortAscending = !sortAscending
                                        else { activeSortCategory = 2; sortAscending = true }
                                    },
                                    label = {
                                        Text("Weight ${if (activeSortCategory == 2) (if (sortAscending) "↓ (High)" else "↑ (Low)") else ""}", fontSize = 12.sp)
                                    }
                                )
                            }
                        }

                        if (sortedStats.isEmpty()) {
                            item {
                                Text("No tracks inside this playlist.", color = Color.Gray, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 8.dp))
                            }
                        } else {
                            items(sortedStats, key = { it.songId }) { stat ->
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
                                            Text("Skip: %.0f%% (%d)".format(Locale.US, stat.skipRate * 100, stat.skipCount), color = if (stat.skipRate > 0.5f) Color.Red else Color.Gray, fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
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
                    }

                    item {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Reset Options", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { viewModel.resetPlaylistWeights(playlistId) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB71C1C)),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Reset Weights (1.0x)", color = Color.White, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                            }
                            Button(
                                onClick = { viewModel.resetPlaylistSkips(playlistId) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB71C1C)),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Reset Skips Stats", color = Color.White, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                            }
                            Button(
                                onClick = { viewModel.resetPlaylistSkippedTo(playlistId) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB71C1C)),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Reset Skipped To History", color = Color.White, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
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
