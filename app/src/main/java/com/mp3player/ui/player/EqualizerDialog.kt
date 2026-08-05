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
fun EqualizerDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var isEnabled by remember { mutableStateOf(com.mp3player.playback.EqualizerManager.isEnabled) }
    var bands by remember { mutableStateOf(com.mp3player.playback.EqualizerManager.getBands()) }
    var presets by remember { mutableStateOf(com.mp3player.playback.EqualizerManager.getPresets()) }
    var selectedPresetIndex by remember { mutableStateOf<Int?>(null) }

    val accentColor = Color(0xFF1DB954) // Vibrant Music Green

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF101012)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                // Top App Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color.White
                            )
                        }
                        Column {
                            Text(
                                "Audio Equalizer",
                                color = Color.White,
                                fontSize = 22.sp,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                            )
                            Text(
                                if (isEnabled) "Hardware DSP Active" else "Bypassed",
                                color = if (isEnabled) accentColor else Color.Gray,
                                fontSize = 12.sp
                            )
                        }
                    }

                    Switch(
                        checked = isEnabled,
                        onCheckedChange = {
                            isEnabled = it
                            com.mp3player.playback.EqualizerManager.setEnabled(it)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = accentColor,
                            uncheckedThumbColor = Color.Gray,
                            uncheckedTrackColor = Color(0xFF2C2C2E)
                        )
                    )
                }

                HorizontalDivider(color = Color(0xFF1E1E22))

                // Body
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    Spacer(modifier = Modifier.height(4.dp))

                    // Presets Section
                    if (presets.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                "PRESETS",
                                color = Color(0xFF8E8E93),
                                fontSize = 12.sp,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                letterSpacing = 1.2.sp
                            )
                            androidx.compose.foundation.lazy.LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                itemsIndexed(presets, key = { index, _ -> index }) { index, presetName ->
                                    val isSelected = selectedPresetIndex == index
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(24.dp))
                                            .background(if (isSelected) accentColor else Color(0xFF1E1E22))
                                            .border(
                                                width = 1.dp,
                                                color = if (isSelected) accentColor else Color(0xFF2C2C32),
                                                shape = RoundedCornerShape(24.dp)
                                            )
                                            .clickable {
                                                selectedPresetIndex = index
                                                com.mp3player.playback.EqualizerManager.usePreset(index.toShort())
                                                bands = com.mp3player.playback.EqualizerManager.getBands()
                                            }
                                            .padding(horizontal = 18.dp, vertical = 10.dp)
                                    ) {
                                        Text(
                                            text = presetName,
                                            color = if (isSelected) Color.Black else Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = if (isSelected) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Bands Section
                    if (bands.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "FREQUENCY BANDS",
                                    color = Color(0xFF8E8E93),
                                    fontSize = 12.sp,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                    letterSpacing = 1.2.sp
                                )
                                TextButton(
                                    onClick = {
                                        selectedPresetIndex = null
                                        bands.forEach {
                                            com.mp3player.playback.EqualizerManager.setBandLevel(it.band, 0)
                                        }
                                        bands = com.mp3player.playback.EqualizerManager.getBands()
                                    }
                                ) {
                                    Text("Reset to Flat", color = accentColor, fontSize = 13.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                                }
                            }

                            bands.forEach { bandInfo ->
                                val freqLabel = if (bandInfo.centerFreqHz >= 1000) {
                                    "${bandInfo.centerFreqHz / 1000} kHz"
                                } else {
                                    "${bandInfo.centerFreqHz} Hz"
                                }
                                val dbValInt = bandInfo.level / 100
                                val dbLabel = if (dbValInt > 0) "+$dbValInt dB" else "$dbValInt dB"

                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    color = Color(0xFF18181B),
                                    border = BorderStroke(1.dp, Color(0xFF26262A))
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                freqLabel,
                                                color = Color.White,
                                                fontSize = 15.sp,
                                                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(
                                                        when {
                                                            dbValInt > 0 -> accentColor.copy(alpha = 0.2f)
                                                            dbValInt < 0 -> Color(0xFFFF5252).copy(alpha = 0.2f)
                                                            else -> Color(0xFF26262A)
                                                        }
                                                    )
                                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                                            ) {
                                                Text(
                                                    dbLabel,
                                                    color = when {
                                                        dbValInt > 0 -> accentColor
                                                        dbValInt < 0 -> Color(0xFFFF5252)
                                                        else -> Color.LightGray
                                                    },
                                                    fontSize = 13.sp,
                                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Slider(
                                            value = bandInfo.level.toFloat(),
                                            onValueChange = { newLevel ->
                                                val levelShort = newLevel.toInt().toShort()
                                                com.mp3player.playback.EqualizerManager.setBandLevel(bandInfo.band, levelShort)
                                                selectedPresetIndex = null
                                                bands = bands.map {
                                                    if (it.band == bandInfo.band) it.copy(level = levelShort) else it
                                                }
                                            },
                                            valueRange = bandInfo.minLevel.toFloat()..bandInfo.maxLevel.toFloat(),
                                            enabled = isEnabled,
                                            colors = SliderDefaults.colors(
                                                thumbColor = if (isEnabled) accentColor else Color.Gray,
                                                activeTrackColor = if (isEnabled) accentColor else Color.Gray,
                                                inactiveTrackColor = Color(0xFF26262A)
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Additional Sound Effects (Bass Boost & Virtualizer)
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(
                            "SOUND EFFECTS",
                            color = Color(0xFF8E8E93),
                            fontSize = 12.sp,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            letterSpacing = 1.2.sp
                        )

                        var bassStrength by remember { mutableStateOf(com.mp3player.playback.EqualizerManager.getBassBoostStrength()) }
                        var virtualizerStrength by remember { mutableStateOf(com.mp3player.playback.EqualizerManager.getVirtualizerStrength()) }

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xFF18181B),
                            border = BorderStroke(1.dp, Color(0xFF26262A))
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Bass Boost", color = Color.White, fontSize = 15.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                                    Text("${(bassStrength / 1000f * 100).toInt()}%", color = accentColor, fontSize = 13.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                                }
                                Slider(
                                    value = bassStrength.toFloat(),
                                    onValueChange = {
                                        bassStrength = it.toInt().toShort()
                                        com.mp3player.playback.EqualizerManager.setBassBoostStrength(bassStrength)
                                    },
                                    valueRange = 0f..1000f,
                                    enabled = isEnabled,
                                    colors = SliderDefaults.colors(
                                        thumbColor = if (isEnabled) accentColor else Color.Gray,
                                        activeTrackColor = if (isEnabled) accentColor else Color.Gray,
                                        inactiveTrackColor = Color(0xFF26262A)
                                    )
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("3D Virtualizer", color = Color.White, fontSize = 15.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                                    Text("${(virtualizerStrength / 1000f * 100).toInt()}%", color = accentColor, fontSize = 13.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                                }
                                Slider(
                                    value = virtualizerStrength.toFloat(),
                                    onValueChange = {
                                        virtualizerStrength = it.toInt().toShort()
                                        com.mp3player.playback.EqualizerManager.setVirtualizerStrength(virtualizerStrength)
                                    },
                                    valueRange = 0f..1000f,
                                    enabled = isEnabled,
                                    colors = SliderDefaults.colors(
                                        thumbColor = if (isEnabled) accentColor else Color.Gray,
                                        activeTrackColor = if (isEnabled) accentColor else Color.Gray,
                                        inactiveTrackColor = Color(0xFF26262A)
                                    )
                                )
                            }
                        }
                    }

                    // Footer system launch option
                    TextButton(
                        onClick = {
                            val intent = Intent(android.media.audiofx.AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
                                putExtra(android.media.audiofx.AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
                                putExtra(android.media.audiofx.AudioEffect.EXTRA_CONTENT_TYPE, android.media.audiofx.AudioEffect.CONTENT_TYPE_MUSIC)
                            }
                            try {
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "System Equalizer activity not found", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Try Opening System EQ Panel", fontSize = 13.sp, color = Color(0xFF8E8E93))
                    }

                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}
