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
fun StatsScreen(
    stats: List<SongStats>,
    keepers: List<KeeperLeaderboardEntry>,
    sortColumn: StatsSortColumn,
    sortAscending: Boolean,
    onSortChange: (StatsSortColumn) -> Unit,
    onToggleAscending: () -> Unit,
    title: String,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) } // 0: All Track Stats, 1: Keepers Leaderboard

    val sortedStats = remember(stats, sortColumn, sortAscending) {
        val sorted = when (sortColumn) {
            StatsSortColumn.TITLE -> stats.sortedBy { it.title }
            StatsSortColumn.PLAY_COUNT -> stats.sortedBy { it.playCount }
            StatsSortColumn.SKIP_COUNT -> stats.sortedBy { it.skipCount }
            StatsSortColumn.SKIP_RATE -> stats.sortedBy { it.skipRate }
            StatsSortColumn.KEEPER_COUNT -> stats.sortedBy { it.keeperCount }
        }
        if (sortAscending) sorted else sorted.reversed()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(title, style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))

                // Tab Row for Track Stats vs Keepers Leaderboard
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Track Stats (${stats.size})", color = if (selectedTab == 0) MaterialTheme.colorScheme.primary else Color.Gray) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Keepers Leaderboard 🏆", color = if (selectedTab == 1) MaterialTheme.colorScheme.primary else Color.Gray) }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (selectedTab == 0) {
                    // Sorting chips
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val arrow = if (sortAscending) "↑" else "↓"
                        FilterChip(
                            selected = sortColumn == StatsSortColumn.TITLE,
                            onClick = { onSortChange(StatsSortColumn.TITLE) },
                            label = { Text("Title ${if (sortColumn == StatsSortColumn.TITLE) arrow else ""}", fontSize = 12.sp) }
                        )
                        FilterChip(
                            selected = sortColumn == StatsSortColumn.PLAY_COUNT,
                            onClick = { onSortChange(StatsSortColumn.PLAY_COUNT) },
                            label = { Text("Plays ${if (sortColumn == StatsSortColumn.PLAY_COUNT) arrow else ""}", fontSize = 12.sp) }
                        )
                        FilterChip(
                            selected = sortColumn == StatsSortColumn.SKIP_COUNT,
                            onClick = { onSortChange(StatsSortColumn.SKIP_COUNT) },
                            label = { Text("Skips ${if (sortColumn == StatsSortColumn.SKIP_COUNT) arrow else ""}", fontSize = 12.sp) }
                        )
                        FilterChip(
                            selected = sortColumn == StatsSortColumn.SKIP_RATE,
                            onClick = { onSortChange(StatsSortColumn.SKIP_RATE) },
                            label = { Text("Skip Rate ${if (sortColumn == StatsSortColumn.SKIP_RATE) arrow else ""}", fontSize = 12.sp) }
                        )
                        FilterChip(
                            selected = sortColumn == StatsSortColumn.KEEPER_COUNT,
                            onClick = { onSortChange(StatsSortColumn.KEEPER_COUNT) },
                            label = { Text("Keepers ${if (sortColumn == StatsSortColumn.KEEPER_COUNT) arrow else ""}", fontSize = 12.sp) }
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    if (sortedStats.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No stats available.", color = Color.Gray)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(sortedStats, key = { it.songId }) { stat ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp).fillMaxWidth()) {
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
                                            Column(horizontalAlignment = Alignment.End) {
                                                Text("P: ${stat.playCount} | S: ${stat.skipCount}", color = Color.LightGray, fontSize = 12.sp)
                                                Text("Skip: %.0f%% | K: ${stat.keeperCount}".format(Locale.US, stat.skipRate * 100), color = if (stat.skipRate > 0.5f) Color(0xFFFF5252) else Color(0xFF66BB6A), fontSize = 12.sp)
                                            }
                                        }
                                        
                                        Spacer(modifier = Modifier.height(8.dp))
                                        
                                        // Visual progress bar for Skip Rate (Green to Red gradient/color)
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            LinearProgressIndicator(
                                                progress = { stat.skipRate.coerceIn(0f, 1f) },
                                                modifier = Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)),
                                                color = if (stat.skipRate > 0.5f) Color(0xFFFF5252) else Color(0xFF66BB6A),
                                                trackColor = Color.DarkGray
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Keepers Leaderboard
                    if (keepers.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No Keepers yet! Listen to full tracks to earn Keeper badges.", color = Color.Gray, textAlign = TextAlign.Center)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            itemsIndexed(keepers, key = { _, entry -> entry.songId }) { index, entry ->
                                val (badgeText, badgeColor) = when (index) {
                                    0 -> "🥇 1st" to Color(0xFFFFD700)
                                    1 -> "🥈 2nd" to Color(0xFFC0C0C0)
                                    2 -> "🥉 3rd" to Color(0xFFCD7F32)
                                    else -> "#${index + 1}" to Color.Gray
                                }

                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = badgeColor.copy(alpha = 0.2f),
                                            modifier = Modifier.size(44.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(badgeText, color = badgeColor, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, fontSize = 13.sp)
                                            }
                                        }
                                        
                                        Spacer(modifier = Modifier.width(12.dp))
                                        
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(entry.title, color = Color.White, fontSize = 14.sp, maxLines = 1, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                                            Text(entry.artist, color = Color.Gray, fontSize = 12.sp, maxLines = 1)
                                        }
                                        
                                        Spacer(modifier = Modifier.width(8.dp))
                                        
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text("${entry.count} Keepers", color = Color(0xFFFFD700), fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, fontSize = 13.sp)
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

