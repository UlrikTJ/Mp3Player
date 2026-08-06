package com.mp3player.playback

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
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
    private var workerJob: Job? = null
    private var lastTriggerTime = 0L

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

            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord failed to initialize.")
                return
            }

            audioRecord?.startRecording()
            isListening = true

            workerJob = CoroutineScope(Dispatchers.Default).launch {
                processAudioStream()
            }
            Log.d(TAG, "LiteRtKeywordEngine started listening.")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting LiteRtKeywordEngine: ${e.message}", e)
            stopListening()
        }
    }

    fun stopListening() {
        if (!isListening) return
        isListening = false
        workerJob?.cancel()
        workerJob = null

        try {
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

                var maxIdx = -1
                var maxProb = 0.0f
                for (i in probabilities.indices) {
                    if (probabilities[i] > maxProb) {
                        maxProb = probabilities[i]
                        maxIdx = i
                    }
                }

                if (maxIdx in labels.indices) {
                    val label = labels[maxIdx]
                    val requiredThreshold = when (label) {
                        "go", "stop" -> 0.45f // Lower threshold so "go" and "stop" trigger much more easily
                        "right", "left" -> 0.55f
                        else -> CONFIDENCE_THRESHOLD
                    }

                    if (maxProb >= requiredThreshold) {
                        val command = when (label) {
                            "go" -> VoiceCommand.PLAY
                            "stop" -> VoiceCommand.PAUSE
                            "right" -> VoiceCommand.SKIP
                            "left" -> VoiceCommand.PREVIOUS
                            else -> VoiceCommand.UNKNOWN
                        }

                        if (command != VoiceCommand.UNKNOWN) {
                            lastTriggerTime = now
                            Log.i(TAG, "LiteRT Keyword Detected: $label -> $command (Confidence: ${(maxProb * 100).toInt()}%, threshold: ${(requiredThreshold * 100).toInt()}%)")
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
