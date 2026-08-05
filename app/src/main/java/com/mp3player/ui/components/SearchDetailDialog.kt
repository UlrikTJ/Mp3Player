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
            Column(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
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

                if (currentSong != null) {
                    MiniPlayer(song = currentSong, viewModel = viewModel)
                }
            }
        }
    }
}
}
