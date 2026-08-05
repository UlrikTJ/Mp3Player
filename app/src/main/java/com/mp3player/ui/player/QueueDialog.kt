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


@Composable
fun QueueDialog(viewModel: MusicViewModel, onDismiss: () -> Unit) {
    val queue by viewModel.currentQueueFlow.collectAsState()
    val rawActiveIndex = viewModel.activeQueueIndex
    val queueOffset = if (rawActiveIndex > 0 && rawActiveIndex < queue.size) rawActiveIndex else 0
    var displayQueue by remember(queue, queueOffset) { mutableStateOf(queue.drop(queueOffset)) }
    val activeIndex = if (rawActiveIndex in queue.indices) 0 else rawActiveIndex
    val playerManager by viewModel.playerManager.collectAsState()
    val currentSong = playerManager?.currentPlayingSong?.collectAsState(null)?.value
    val manualIds by viewModel.manualQueueSongInstanceIds.collectAsState()
    val playlistName by viewModel.activePlaylistName.collectAsState()
    val listState = rememberLazyListState()
    
    var draggedIndex by remember { mutableStateOf<Int?>(null) }
    var initialDraggedIndex by remember { mutableStateOf<Int?>(null) }
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
            Column(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.weight(1f).fillMaxWidth().padding(24.dp)) {
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
                
                if (displayQueue.isEmpty()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("Queue is empty", color = Color.Gray)
                    }
                } else {
                    val currentDraggedIndex = draggedIndex
                    var currentTargetIndex by remember { mutableStateOf<Int?>(null) }
                    val currentTargetIndexRef = rememberUpdatedState(currentTargetIndex)

                    LaunchedEffect(currentDraggedIndex) {
                        if (currentDraggedIndex == null) {
                            currentTargetIndex = null
                            return@LaunchedEffect
                        }
                        androidx.compose.runtime.snapshotFlow {
                            listState.firstVisibleItemIndex
                            listState.firstVisibleItemScrollOffset
                            
                            val cardCenterY = targetScreenY + (itemHeightPx / 2f)
                            val visibleItems = listState.layoutInfo.visibleItemsInfo
                            if (visibleItems.isEmpty()) currentDraggedIndex
                            else {
                                val closestItem = visibleItems.minByOrNull { item ->
                                    val itemCenter = item.offset + (item.size / 2f)
                                    kotlin.math.abs(cardCenterY - itemCenter)
                                }
                                if (closestItem != null) {
                                    closestItem.index.coerceIn(0, displayQueue.size - 1)
                                } else currentDraggedIndex
                            }
                        }.collect { target ->
                            currentTargetIndex = target
                            val from = draggedIndex
                            if (from != null && target != null && from != target) {
                                val updated = displayQueue.toMutableList()
                                if (from in updated.indices && target in updated.indices) {
                                    val itemToMove = updated.removeAt(from)
                                    updated.add(target, itemToMove)
                                    displayQueue = updated
                                    viewModel.reorderQueue(from + queueOffset, target + queueOffset)
                                    draggedIndex = target
                                }
                            }
                        }
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            userScrollEnabled = draggedIndex == null
                        ) {
                            itemsIndexed(displayQueue, key = { _, song -> song.instanceId }) { index, song ->
                                val isActive = (currentSong != null && song.id == currentSong.id) || (index == activeIndex)
                                val isManual = song.instanceId in manualIds
                                val isDraggedItem = index == currentDraggedIndex
                                
                                // Section header determination
                                val sectionHeader = when {
                                    index == activeIndex -> "Now Playing"
                                    isManual && (index == activeIndex + 1 || (index > 0 && displayQueue.getOrNull(index - 1)?.let { prev -> prev.instanceId !in manualIds || displayQueue.indexOf(prev) == activeIndex } == true && manualIds.isNotEmpty())) -> {
                                        val prevIsManual = index > 0 && displayQueue.getOrNull(index - 1)?.instanceId in manualIds
                                        val prevIsNowPlaying = index - 1 == activeIndex
                                        if (!prevIsManual || prevIsNowPlaying) "Added to Queue" else null
                                    }
                                    !isManual && index > activeIndex -> {
                                        val prevIsPlaylist = index > 0 && displayQueue.getOrNull(index - 1)?.let { prev -> prev.instanceId !in manualIds && displayQueue.indexOf(prev) > activeIndex && displayQueue.indexOf(prev) != activeIndex } == true
                                        if (!prevIsPlaylist) {
                                            if (!playlistName.isNullOrBlank()) "Next from $playlistName" else "Next from Playlist"
                                        } else null
                                    }
                                    else -> null
                                }
                                
                                if (sectionHeader != null && sectionHeader != "Now Playing") {
                                    Text(
                                        text = sectionHeader,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontSize = 13.sp,
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                                    )
                                }
                                if (index == activeIndex) {
                                    Text(
                                        text = "Now Playing",
                                        color = MaterialTheme.colorScheme.primary,
                                        fontSize = 13.sp,
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                        modifier = Modifier.padding(bottom = 4.dp)
                                    )
                                }

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
                                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                    label = "reorderTranslation"
                                )

                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .graphicsLayer {
                                            translationY = if (currentDraggedIndex != null) animatedY else 0f
                                            alpha = if (isDraggedItem) 0.25f else 1.0f
                                        },
                                    elevation = CardDefaults.cardElevation(
                                        defaultElevation = if (isActive) 2.dp else 0.dp
                                    ),
                                    colors = CardDefaults.cardColors(
                                        containerColor = when {
                                            isActive -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                            isManual -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.08f)
                                            else -> MaterialTheme.colorScheme.surface
                                        }
                                    ),
                                    border = when {
                                        isActive -> BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                                        isManual -> BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f))
                                        else -> null
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { viewModel.playQueueSongAt(index + queueOffset) }
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
                                                .pointerInput(song.instanceId) {
                                                    detectDragGestures(
                                                        onDragStart = { touchOffset -> 
                                                            val itemInfo = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == song.instanceId }
                                                            val startY = itemInfo?.offset?.toFloat() ?: (index * itemHeightPx)
                                                            draggedIndex = index
                                                            initialDraggedIndex = index
                                                            targetScreenY = startY + touchOffset.y - (itemHeightPx / 2f)
                                                        },
                                                        onDragEnd = {
                                                            val from = initialDraggedIndex
                                                            val to = draggedIndex
                                                            if (from != null && to != null && from != to) {
                                                                viewModel.moveQueueItem(from, to)
                                                            }
                                                            draggedIndex = null
                                                            initialDraggedIndex = null
                                                        },
                                                        onDragCancel = { 
                                                            val from = initialDraggedIndex
                                                            val to = draggedIndex
                                                            if (from != null && to != null && from != to) {
                                                                viewModel.moveQueueItem(from, to)
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
                                        } else if (isManual) {
                                            Text("Queued", color = MaterialTheme.colorScheme.tertiary, fontSize = 11.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
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

                        // Floating Overlay Card for Dragged Queue Song — matches regular card look
                        val draggedSong = currentDraggedIndex?.let { displayQueue.getOrNull(it) }
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

            if (currentSong != null) {
                MiniPlayer(song = currentSong, viewModel = viewModel)
            }
        }
    }
}

}
