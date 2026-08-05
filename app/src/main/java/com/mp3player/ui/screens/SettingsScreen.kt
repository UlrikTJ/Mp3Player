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
fun SettingsScreen(viewModel: MusicViewModel, onOpenLibrary: () -> Unit = {}) {
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

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpenLibrary() },
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Music Library", tint = MaterialTheme.colorScheme.primary)
                    Column {
                        Text("Music Library", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        Text("View all scanned local audio files & downloads", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                }
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.graphicsLayer { rotationZ = 180f })
            }
        }
        
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
                Text("Auto Keeper Bonus", color = Color.White)
                Text("Boosts landing target tracks", color = Color.Gray, fontSize = 12.sp)
            }
            Switch(checked = useKeeperBonus, onCheckedChange = { viewModel.updateKeeperBonus(it) })
        }

        HorizontalDivider()

        var showEqualizerDialog by remember { mutableStateOf(false) }

        Button(
            onClick = { showEqualizerDialog = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Equalizer")
        }

        if (showEqualizerDialog) {
            EqualizerDialog(onDismiss = { showEqualizerDialog = false })
        }

        var showGlobalStats by remember { mutableStateOf(false) }

        Button(
            onClick = { showGlobalStats = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Library Stats")
        }

        if (showGlobalStats) {
            val stats by viewModel.songStats.collectAsState()
            val keepers by viewModel.keepersLeaderboard.collectAsState()
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
                title = "Global Stats",
                onDismiss = { showGlobalStats = false }
            )
        }
    }
}
