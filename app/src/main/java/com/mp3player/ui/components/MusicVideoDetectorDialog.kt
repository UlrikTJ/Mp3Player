package com.mp3player.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.mp3player.data.entity.SongEntity
import com.mp3player.data.network.SearchTrackDto
import com.mp3player.ui.viewmodel.MusicViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicVideoDetectorDialog(
    viewModel: MusicViewModel,
    playlistId: Int?,
    playlistName: String?,
    onDismiss: () -> Unit
) {
    val scanResults by viewModel.musicVideoScanResults.collectAsState()
    val isScanning by viewModel.isScanningMusicVideos.collectAsState()
    val replacementResults by viewModel.replacementSearchResults.collectAsState()
    val isReplacing by viewModel.isReplacingAudio.collectAsState()
    val playerManager by viewModel.playerManager.collectAsState()
    val isPlaying = playerManager?.isPlaying?.collectAsState(false)?.value ?: false
    val currentSong = playerManager?.currentPlayingSong?.collectAsState(null)?.value

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Videos, 1: Rename
    var renameInput by remember { mutableStateOf(playlistName ?: "") }
    var selectedCandidateToPreview by remember { mutableStateOf<Pair<SongEntity, SearchTrackDto>?>(null) }

    LaunchedEffect(playlistId) {
        viewModel.scanPlaylistForMusicVideos(playlistId)
    }

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
                    .padding(16.dp)
            ) {
                // Fullscreen Header with Navigation & Tabs underneath
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Edit Playlist",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            label = { Text("Music Videos", fontSize = 13.sp) },
                            leadingIcon = { Icon(Icons.Default.Videocam, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        )
                        FilterChip(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            label = { Text("Rename", fontSize = 13.sp) },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (selectedTab == 1) {
                    // Rename Tab
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text("Rename Playlist", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = renameInput,
                            onValueChange = { renameInput = it },
                            label = { Text("Playlist Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = {
                                if (renameInput.isNotBlank() && playlistId != null) {
                                    viewModel.renamePlaylist(playlistId, renameInput)
                                    onDismiss()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("Save Name", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    // Videos Tab - Fullscreen Video Inspector & Batch Replacer
                    val flaggedCount = scanResults.count { it.isLikelyMusicVideo }
                    val replaceAllProgress by viewModel.replaceAllProgress.collectAsState()

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (isScanning) "Scanning playlist tracks..." else "$flaggedCount music video tracks flagged",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                    Text(
                                        text = "Click any song to open preview player & check audio",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    OutlinedButton(
                                        onClick = { viewModel.cleanPlaylistSongTitles(playlistId) },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        shape = RoundedCornerShape(20.dp)
                                    ) {
                                        Text("Clean Titles", fontSize = 11.sp)
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    if (flaggedCount > 0 && replaceAllProgress == null) {
                                        Button(
                                            onClick = { viewModel.replaceAllMusicVideoAudios() },
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                            shape = RoundedCornerShape(20.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                        ) {
                                            Icon(Icons.Default.AutoFixHigh, contentDescription = null, tint = Color.Black, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text("Replace All", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                        Spacer(modifier = Modifier.width(6.dp))
                                    }
                                    IconButton(
                                        onClick = { viewModel.scanPlaylistForMusicVideos(playlistId) },
                                        enabled = !isScanning && replaceAllProgress == null
                                    ) {
                                        if (isScanning) {
                                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                        } else {
                                            Icon(Icons.Default.Refresh, contentDescription = "Re-scan")
                                        }
                                    }
                                }
                            }

                            // Progress Bar for Replace All
                            if (replaceAllProgress != null) {
                                val (current, total) = replaceAllProgress!!
                                val progressPercent = if (total > 0) current.toFloat() / total.toFloat() else 0f
                                Spacer(modifier = Modifier.height(12.dp))
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Replacing audio in background...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                        Text("$current / $total", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    LinearProgressIndicator(
                                        progress = { progressPercent },
                                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (scanResults.isEmpty() && !isScanning) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No tracks found in playlist", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(scanResults) { item ->
                                ScanItemRow(
                                    item = item,
                                    isPlayingThisSong = currentSong?.id == item.song.id && isPlaying,
                                    replacementCandidates = replacementResults[item.song.id] ?: emptyList(),
                                    isReplacing = isReplacing[item.song.id] == true,
                                    onPlaySong = {
                                        val searchDto = SearchTrackDto(
                                            id = item.song.youtubeVideoId ?: "",
                                            title = item.song.title,
                                            uploader = item.song.artist,
                                            duration = (item.song.durationMs / 1000L).toInt(),
                                            thumbnail = item.song.artworkPath ?: ""
                                        )
                                        selectedCandidateToPreview = Pair(item.song, searchDto)
                                    },
                                    onFindReplacement = { viewModel.findAudioReplacements(item.song) },
                                    onOpenPreviewModal = { candidate ->
                                        selectedCandidateToPreview = Pair(item.song, candidate)
                                    },
                                    onCleanTitle = { viewModel.cleanSongTitle(item.song) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Song Preview Modal (for inspecting track or testing replacement candidates)
    selectedCandidateToPreview?.let { (oldSong, candidate) ->
        val isReplacingCandidate = candidate.id.isNotEmpty() && candidate.id != oldSong.youtubeVideoId
        SearchDetailDialog(
            track = candidate,
            viewModel = viewModel,
            onDismiss = { selectedCandidateToPreview = null },
            actionButtonText = if (isReplacingCandidate) "Replace Song Audio" else null,
            onActionClick = if (isReplacingCandidate) {
                {
                    viewModel.replaceSongAudio(oldSong, candidate)
                    selectedCandidateToPreview = null
                }
            } else null
        )
    }
}

@Composable
private fun ScanItemRow(
    item: MusicViewModel.MusicVideoScanItem,
    isPlayingThisSong: Boolean,
    replacementCandidates: List<SearchTrackDto>,
    isReplacing: Boolean,
    onPlaySong: () -> Unit,
    onFindReplacement: () -> Unit,
    onOpenPreviewModal: (SearchTrackDto) -> Unit,
    onCleanTitle: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (item.isLikelyMusicVideo) {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPlaySong() }
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Interactive Play/Pause Cover Artwork
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (item.isLikelyMusicVideo) MaterialTheme.colorScheme.error.copy(alpha = 0.3f)
                            else MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (item.song.artworkPath != null) {
                        AsyncImage(
                            model = item.song.artworkPath,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = if (item.isLikelyMusicVideo) Icons.Default.Videocam else Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = if (item.isLikelyMusicVideo) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    }

                    // Play/Pause Overlay
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.4f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPlayingThisSong) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Play/Pause",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.song.title,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 15.sp
                    )
                    Text(
                        text = "${item.song.artist} • ${item.reason}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (item.isLikelyMusicVideo) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = onCleanTitle,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("Clean Title", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                    }

                    if (item.isLikelyMusicVideo) {
                        Button(
                            onClick = {
                                onFindReplacement()
                                expanded = !expanded
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            if (isReplacing) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Replace", fontSize = 12.sp)
                            }
                        }
                    } else {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "Audio OK",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            }

            AnimatedVisibility(visible = expanded && item.isLikelyMusicVideo) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    Text(
                        text = "Clean Audio Candidates (Click candidate to preview & skip):",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    if (replacementCandidates.isEmpty()) {
                        Text(
                            text = "Searching YouTube for clean audio...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        replacementCandidates.take(4).forEach { candidate ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onOpenPreviewModal(candidate) }
                                    .padding(vertical = 6.dp, horizontal = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = "Preview", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column {
                                        Text(
                                            text = candidate.title,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${candidate.uploader} • ${candidate.duration}s",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Button(
                                    onClick = { onOpenPreviewModal(candidate) },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text("Preview & Swap", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
