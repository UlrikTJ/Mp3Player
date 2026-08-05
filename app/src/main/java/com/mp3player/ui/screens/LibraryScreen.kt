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
import com.mp3player.MainActivity


@Composable
fun LibraryScreen(viewModel: MusicViewModel) {
    val songs by viewModel.allSongs.collectAsState()
    val viewMode by viewModel.libraryViewMode.collectAsState()
    var expandedFolder by remember { mutableStateOf<String?>(null) }
    var filterQuery by remember { mutableStateOf("") }

    var isEditMode by remember { mutableStateOf(false) }
    var displayLibrarySongs by remember(songs) { mutableStateOf(songs) }
    var deletedSongsHistory by remember { mutableStateOf<List<Pair<Int, SongEntity>>>(emptyList()) }

    val filteredSongs = remember(displayLibrarySongs, filterQuery) {
        if (filterQuery.isBlank()) displayLibrarySongs
        else displayLibrarySongs.filter {
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
                    "${filteredSongs.size} tracks available", 
                    style = MaterialTheme.typography.bodySmall, 
                    color = Color.Gray
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (isEditMode) {
                    IconButton(
                        onClick = {
                            if (deletedSongsHistory.isNotEmpty()) {
                                val (origIdx, restoredSong) = deletedSongsHistory.last()
                                deletedSongsHistory = deletedSongsHistory.dropLast(1)
                                val updated = displayLibrarySongs.toMutableList()
                                val insertPos = origIdx.coerceIn(0, updated.size)
                                updated.add(insertPos, restoredSong)
                                displayLibrarySongs = updated
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
                                displayLibrarySongs = songs
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
                                viewModel.deleteSongFromApp(song)
                            }
                            deletedSongsHistory = emptyList()
                            isEditMode = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Done", color = Color.Black, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    }
                } else {
                    IconButton(
                        onClick = { isEditMode = true },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(12.dp))
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Library", tint = MaterialTheme.colorScheme.primary)
                    }

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
                    itemsIndexed(filteredSongs, key = { _, song -> song.id }) { index, song ->
                        SongRow(
                            song = song, 
                            viewModel = viewModel,
                            isEditMode = isEditMode,
                            onDeleteClick = {
                                val removedSong = song
                                val removedIdx = index
                                displayLibrarySongs = displayLibrarySongs.filter { it.id != song.id }
                                deletedSongsHistory = deletedSongsHistory + (removedIdx to removedSong)
                            }
                        )
                    }
                }
            } else {
                if (expandedFolder == null) {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(folders.keys.toList(), key = { it }) { folderName ->
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
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable { expandedFolder = null },
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Folders", color = MaterialTheme.colorScheme.primary, fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
                            Text("  /  ", color = Color.Gray, fontSize = 14.sp)
                            Text(expandedFolder ?: "", color = Color.White, fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val folderSongs = folders[expandedFolder] ?: emptyList()
                        itemsIndexed(folderSongs, key = { _, song -> song.id }) { index, song ->
                            SongRow(
                                song = song, 
                                viewModel = viewModel,
                                isEditMode = isEditMode,
                                onDeleteClick = {
                                    val removedSong = song
                                    val removedIdx = index
                                    displayLibrarySongs = displayLibrarySongs.filter { it.id != song.id }
                                    deletedSongsHistory = deletedSongsHistory + (removedIdx to removedSong)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
