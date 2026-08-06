package com.mp3player.playback

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import org.tensorflow.lite.Interpreter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.IntBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/**
 * Offline Keyword Spotting Engine powered by Google LiteRT (TensorFlow Lite successor).
 * Uses Google's Speech Commands conv_actions model recognizing "go", "stop", "right", "left".
 * Features hardware Acoustic Echo Cancellation (AEC) to eliminate speaker feedback.
 */
class LiteRtKeywordEngine(
    private val context: Context,
    private val onCommandDetected: (VoiceCommand) -> Unit
) {
    companion object {
        private const val TAG = "LiteRtKeywordEngine"
        private const val SAMPLE_RATE = 16000
        private const val MODEL_FILENAME = "speech_commands.tflite"
        private const val LABELS_FILENAME = "speech_commands_labels.txt"
        private const val CONFIDENCE_THRESHOLD = 0.65f
        private const val COOLDOWN_MS = 1500L
    }

    private var interpreter: Interpreter? = null
    private var labels = listOf<String>()
    private var isListening = false
    private var audioRecord: AudioRecord? = null
    private var acousticEchoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var workerJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var lastTriggerTime = 0L
    private var lastDebugLogTime = 0L

    init {
        loadModelAndLabels()
    }

    private fun loadModelAndLabels() {
        try {
            val assetManager = context.assets
            assetManager.open(LABELS_FILENAME).bufferedReader().useLines { lines ->
                labels = lines.toList()
            }

            val fileDescriptor = assetManager.openFd(MODEL_FILENAME)
            val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
            val fileChannel = inputStream.channel
            val startOffset = fileDescriptor.startOffset
            val declaredLength = fileDescriptor.declaredLength
            val modelBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)

            interpreter = Interpreter(modelBuffer)
            Log.i(TAG, "LiteRT Speech Commands Model successfully loaded. Labels: $labels")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load LiteRT Speech Commands model: ${e.message}", e)
        }
    }

    fun startListening() {
        if (isListening) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Cannot start listening: RECORD_AUDIO permission not granted.")
            return
        }

        try {
            val minBufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(SAMPLE_RATE * 2)

            // Use VOICE_COMMUNICATION source which engages Android OS VoIP pipeline with hardware Acoustic Echo Cancellation
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                // Fallback to VOICE_RECOGNITION if VOICE_COMMUNICATION fails on specific hardware
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minBufferSize
                )
            }

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord failed to initialize.")
                return
            }

            // Enable Hardware Acoustic Echo Cancellation (AEC) to cancel speaker output from microphone input
            val sessionId = audioRecord?.audioSessionId ?: 0
            if (sessionId != 0) {
                if (AcousticEchoCanceler.isAvailable()) {
                    try {
                        acousticEchoCanceler = AcousticEchoCanceler.create(sessionId)?.apply {
                            enabled = true
                        }
                        Log.d(TAG, "Hardware AcousticEchoCanceler enabled.")
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to enable AcousticEchoCanceler: ${e.message}")
                    }
                }
                if (NoiseSuppressor.isAvailable()) {
                    try {
                        noiseSuppressor = NoiseSuppressor.create(sessionId)?.apply {
                            enabled = true
                        }
                        Log.d(TAG, "Hardware NoiseSuppressor enabled.")
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to enable NoiseSuppressor: ${e.message}")
                    }
                }
            }

            audioRecord?.startRecording()
            isListening = true

            // Acquire a partial wake lock so the CPU stays on and the mic keeps
            // recording even when the screen is off.
            try {
                val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                wakeLock = pm.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "Mp3Player::VoiceCommandWakeLock"
                ).apply { acquire() }
                Log.d(TAG, "PARTIAL_WAKE_LOCK acquired for voice control.")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to acquire wake lock: ${e.message}")
            }

            workerJob = CoroutineScope(Dispatchers.Default).launch {
                processAudioStream()
            }
            Log.d(TAG, "LiteRtKeywordEngine started listening with AEC.")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting LiteRtKeywordEngine: ${e.message}", e)
            stopListening()
        }
    }

    fun stopListening() {
        if (!isListening) return
        isListening = false

        try {
            wakeLock?.let {
                if (it.isHeld) it.release()
            }
            wakeLock = null
            Log.d(TAG, "PARTIAL_WAKE_LOCK released.")
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing wake lock: ${e.message}")
        }
        workerJob?.cancel()
        workerJob = null

        try {
            acousticEchoCanceler?.release()
            acousticEchoCanceler = null
            noiseSuppressor?.release()
            noiseSuppressor = null

            audioRecord?.apply {
                if (recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    stop()
                }
                release()
            }
            audioRecord = null
            Log.d(TAG, "LiteRtKeywordEngine stopped listening.")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping AudioRecord: ${e.message}", e)
        }
    }

    private suspend fun processAudioStream() {
        val samplesPerSec = SAMPLE_RATE
        val audioWindow = ShortArray(samplesPerSec) // 1 second window
        val strideSamples = samplesPerSec / 5 // 200ms stride
        val tempBuffer = ShortArray(strideSamples)

        while (isListening && workerJob?.isActive == true) {
            val readCount = audioRecord?.read(tempBuffer, 0, tempBuffer.size) ?: -1
            if (readCount < strideSamples) continue

            // Shift sliding 1s window
            System.arraycopy(audioWindow, strideSamples, audioWindow, 0, samplesPerSec - strideSamples)
            System.arraycopy(tempBuffer, 0, audioWindow, samplesPerSec - strideSamples, strideSamples)

            val now = SystemClock.elapsedRealtime()
            if (now - lastTriggerTime < COOLDOWN_MS) continue

            // Prepare float buffer for model input (16000 float samples normalized)
            val inputBuffer = ByteBuffer.allocateDirect(16000 * 4).apply {
                order(ByteOrder.nativeOrder())
                asFloatBuffer().apply {
                    for (sample in audioWindow) {
                        put(sample / 32768.0f)
                    }
                }
            }

            // Input 2: Sample Rate (INT32, shape [1])
            val sampleRateBuffer = ByteBuffer.allocateDirect(4).apply {
                order(ByteOrder.nativeOrder())
                asIntBuffer().put(SAMPLE_RATE)
            }

            val inputs = arrayOf<Any>(inputBuffer, sampleRateBuffer)
            val outputs = mutableMapOf<Int, Any>()
            val outputScores = Array(1) { FloatArray(labels.size.coerceAtLeast(12)) }
            outputs[0] = outputScores

            val interp = interpreter ?: continue
            try {
                interp.runForMultipleInputsOutputs(inputs, outputs)
                val probabilities = outputScores[0]

                // ── Diagnostic: dump full probability distribution every ~2s ──
                val debugInterval = 2000L
                if (now - lastDebugLogTime >= debugInterval) {
                    lastDebugLogTime = now
                    val sb = StringBuilder("LiteRT scores (${probabilities.size} outputs, ${labels.size} labels):")
                    for (i in probabilities.indices) {
                        val lbl = if (i < labels.size) labels[i] else "?idx$i"
                        sb.append(" [$lbl=${String.format("%.3f", probabilities[i])}]")
                    }
                    Log.d(TAG, sb.toString())
                }

                var bestLabel: String? = null
                var maxProb = 0.0f

                // Evaluate our 4 target commands + "up" (which the model frequently
                // confuses with "stop" because they share the same plosive ending).
                // "up" is treated as an alias for "stop" → PAUSE.
                for (i in probabilities.indices) {
                    if (i < labels.size) {
                        val label = labels[i]
                        if (label == "go" || label == "stop" || label == "right" || label == "left" || label == "up") {
                            if (probabilities[i] > maxProb) {
                                maxProb = probabilities[i]
                                bestLabel = label
                            }
                        }
                    }
                }

                if (bestLabel != null) {
                    val requiredThreshold = when (bestLabel) {
                        "go", "stop" -> 0.35f // Lower threshold for fast triggers
                        "up" -> 0.55f         // Higher threshold for "up" alias to avoid false positives
                        "right", "left" -> 0.45f
                        else -> CONFIDENCE_THRESHOLD
                    }

                    val silenceIdx = labels.indexOf("_silence_")
                    val silenceScore = if (silenceIdx != -1 && silenceIdx < probabilities.size) probabilities[silenceIdx] else 0f

                    val unknownIdx = labels.indexOf("_unknown_")
                    val unknownScore = if (unknownIdx != -1 && unknownIdx < probabilities.size) probabilities[unknownIdx] else 0f

                    if (maxProb >= requiredThreshold && maxProb > silenceScore && maxProb > unknownScore) {
                        val command = when (bestLabel) {
                            "go" -> VoiceCommand.PLAY
                            "stop", "up" -> VoiceCommand.PAUSE  // "up" is an alias for "stop"
                            "right" -> VoiceCommand.SKIP
                            "left" -> VoiceCommand.PREVIOUS
                            else -> VoiceCommand.UNKNOWN
                        }

                        if (command != VoiceCommand.UNKNOWN) {
                            lastTriggerTime = now
                            Log.i(TAG, "LiteRT Keyword Detected: $bestLabel -> $command (Confidence: ${(maxProb * 100).toInt()}%, threshold: ${(requiredThreshold * 100).toInt()}%)")
                            onCommandDetected(command)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during LiteRT inference: ${e.message}")
            }
        }
    }

    fun release() {
        stopListening()
        interpreter?.close()
        interpreter = null
    }
}
