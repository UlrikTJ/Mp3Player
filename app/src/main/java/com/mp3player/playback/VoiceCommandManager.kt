package com.mp3player.playback

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.content.ContextCompat

enum class VoiceCommand {
    PLAY,
    PAUSE,
    SKIP,
    PREVIOUS,
    UNKNOWN
}

enum class VoiceEngineMode {
    SPEECH_RECOGNIZER, // Native Android speech-to-text ("play", "pause", "skip", "previous")
    LITERT_KEYWORD_SPOTTING // Offline TFLite / LiteRT model ("go", "stop", "right", "left")
}

/**
 * Unified Voice Command Manager supporting both Android SpeechRecognizer and LiteRT keyword engines.
 */
class VoiceCommandManager(
    private val context: Context,
    private val onCommandDetected: (VoiceCommand) -> Unit
) {
    companion object {
        private const val TAG = "VoiceCommandManager"
        private const val COOLDOWN_MS = 1500L
        private const val RESTART_DELAY_MS = 750L
    }

    private var engineMode: VoiceEngineMode = VoiceEngineMode.SPEECH_RECOGNIZER
    private var liteRtEngine: LiteRtKeywordEngine? = null

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private var shouldBeListening = false
    private var lastTriggerTime = 0L
    private val mainHandler = Handler(Looper.getMainLooper())

    val isActive: Boolean get() = shouldBeListening && engineMode == VoiceEngineMode.SPEECH_RECOGNIZER

    private val commandKeywords = mapOf(
        "skip" to VoiceCommand.SKIP,
        "next" to VoiceCommand.SKIP,
        "play" to VoiceCommand.PLAY,
        "resume" to VoiceCommand.PLAY,
        "start" to VoiceCommand.PLAY,
        "pause" to VoiceCommand.PAUSE,
        "stop" to VoiceCommand.PAUSE,
        "previous" to VoiceCommand.PREVIOUS,
        "back" to VoiceCommand.PREVIOUS,
    )

    init {
        updateModeFromPrefs()
    }

    fun updateModeFromPrefs() {
        val sharedPrefs = context.getSharedPreferences("Mp3PlayerPrefs", Context.MODE_PRIVATE)
        val modeStr = sharedPrefs.getString("voice_engine_mode", VoiceEngineMode.SPEECH_RECOGNIZER.name)
        val newMode = try {
            VoiceEngineMode.valueOf(modeStr ?: VoiceEngineMode.SPEECH_RECOGNIZER.name)
        } catch (e: Exception) {
            VoiceEngineMode.SPEECH_RECOGNIZER
        }

        if (newMode != engineMode) {
            val wasListening = shouldBeListening
            stopListening()
            engineMode = newMode
            if (wasListening) {
                startListening()
            }
        }
    }

    fun startListening() {
        shouldBeListening = true
        updateModeFromPrefs()

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Cannot start listening: RECORD_AUDIO permission not granted.")
            return
        }

        if (engineMode == VoiceEngineMode.LITERT_KEYWORD_SPOTTING) {
            if (liteRtEngine == null) {
                liteRtEngine = LiteRtKeywordEngine(context, onCommandDetected)
            }
            liteRtEngine?.startListening()
        } else {
            if (isListening) return
            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                Log.w(TAG, "Speech recognition is not available on this device.")
                return
            }
            mainHandler.post { startRecognizer() }
        }
    }

    fun stopListening() {
        shouldBeListening = false
        mainHandler.removeCallbacksAndMessages(null)

        if (engineMode == VoiceEngineMode.LITERT_KEYWORD_SPOTTING) {
            liteRtEngine?.stopListening()
        } else {
            mainHandler.post { destroyRecognizer() }
        }
    }

    fun release() {
        stopListening()
        liteRtEngine?.release()
        liteRtEngine = null
    }

    // ── SpeechRecognizer implementation ───────────────────────────────────────

    private fun startRecognizer() {
        if (!shouldBeListening || engineMode != VoiceEngineMode.SPEECH_RECOGNIZER) return
        destroyRecognizer()

        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).also {
                it.setRecognitionListener(recognitionListener)
            }
            speechRecognizer?.startListening(createRecognitionIntent())
            isListening = true
            Log.d(TAG, "SpeechRecognizer started listening.")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting SpeechRecognizer: ${e.message}", e)
            scheduleRestart()
        }
    }

    private fun destroyRecognizer() {
        isListening = false
        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.e(TAG, "Error destroying SpeechRecognizer: ${e.message}", e)
        }
        speechRecognizer = null
    }

    private fun scheduleRestart() {
        if (!shouldBeListening || engineMode != VoiceEngineMode.SPEECH_RECOGNIZER) return
        mainHandler.postDelayed({
            if (shouldBeListening && engineMode == VoiceEngineMode.SPEECH_RECOGNIZER) startRecognizer()
        }, RESTART_DELAY_MS)
    }

    private fun createRecognitionIntent(): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
        }
    }

    private fun parseCommand(spokenText: String): VoiceCommand {
        val words = spokenText.lowercase().trim().split("\\s+".toRegex())
        for (word in words) {
            commandKeywords[word]?.let { return it }
        }
        return VoiceCommand.UNKNOWN
    }

    private fun handleRecognizedText(results: List<String>) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastTriggerTime < COOLDOWN_MS) return

        for (result in results) {
            val command = parseCommand(result)
            if (command != VoiceCommand.UNKNOWN) {
                lastTriggerTime = now
                Log.i(TAG, "Voice command detected: $command (from: \"$result\")")
                onCommandDetected(command)
                return
            }
        }
    }

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}

        override fun onError(error: Int) {
            isListening = false
            if (error != SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                scheduleRestart()
            }
        }

        override fun onResults(results: Bundle?) {
            isListening = false
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                handleRecognizedText(matches)
            }
            scheduleRestart()
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                handleRecognizedText(matches)
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }
}
