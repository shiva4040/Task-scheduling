package com.taskscheduling.app.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

interface VoiceInputCallback {
    fun onListeningStarted()
    fun onBeginningOfSpeech() {}
    fun onUtteranceFinalizing() {}
    fun onPartialResult(hypothesis: String) {}
    fun onSpeechRecognized(text: String)
    fun onError(message: String)
    fun onListeningStopped()
    fun onAudioLevel(rmsLevel: Float, peakLevel: Float, inputDetected: Boolean) {}
}

/**
 * Manages voice input operations, runtime permissions, and the active [SpeechRecognitionEngine].
 * Defaults to Whisper Small Q5_1 offline recognition.
 * Enforces single Whisper instance in memory.
 */
class VoiceInputManager(
    val context: Context? = null,
    speechEngine: SpeechRecognitionEngine = (if (context != null) WhisperCppSpeechRecognitionEngine.getInstance(context) else DummySpeechEngine()),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main),
    private val permissionChecker: (() -> Boolean)? = null
) {
    var speechEngine: SpeechRecognitionEngine = speechEngine
        private set

    constructor(context: Context) : this(
        context = context,
        speechEngine = WhisperCppSpeechRecognitionEngine.getInstance(context)
    )

    constructor(context: Context, engineType: SpeechEngineType) : this(
        context = context,
        speechEngine = when (engineType) {
            SpeechEngineType.WHISPER_OFFLINE,
            SpeechEngineType.WHISPER_SMALL_Q5_1 -> WhisperCppSpeechRecognitionEngine.getInstance(context)
            @Suppress("DEPRECATION")
            SpeechEngineType.SHERPA_ONNX -> SherpaOnnxSpeechRecognitionEngine(context)
            @Suppress("DEPRECATION")
            SpeechEngineType.VOSK -> VoskOfflineSpeechEngine(context)
        }
    )

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _isEngineReady = MutableStateFlow(false)
    val isEngineReady: StateFlow<Boolean> = _isEngineReady.asStateFlow()

    private val _activeFallbackNotice = MutableStateFlow<String?>(null)
    val activeFallbackNotice: StateFlow<String?> = _activeFallbackNotice.asStateFlow()

    var activeLanguageMode: LanguageMode = LanguageMode.ENGLISH
        private set

    companion object {
        const val MSG_PERMISSION_DENIED =
            "Microphone permission is required for voice commands. You can still type commands."
        const val MSG_MODEL_MISSING = "Offline voice model is missing."
        const val MSG_INIT_FAILED = "Offline voice recognition could not be started."
        const val MSG_COULD_NOT_UNDERSTAND = "Speech captured, but no text was recognized."
    }

    /**
     * Dynamically switches speech engine. Defaults to Whisper Small Q5_1.
     * Enforces strict single-engine-in-RAM policy to protect device memory.
     */
    fun switchEngine(type: SpeechEngineType) {
        val ctx = context ?: return
        if (speechEngine.engineType == type) return

        stopListening()
        // Release previous engine to free native memory immediately
        speechEngine.release()
        System.gc()

        _activeFallbackNotice.value = null
        speechEngine = when (type) {
            SpeechEngineType.WHISPER_OFFLINE,
            SpeechEngineType.WHISPER_SMALL_Q5_1 -> WhisperCppSpeechRecognitionEngine.getInstance(ctx).also {
                it.languageMode = activeLanguageMode
            }
            @Suppress("DEPRECATION")
            SpeechEngineType.SHERPA_ONNX -> SherpaOnnxSpeechRecognitionEngine(ctx)
            @Suppress("DEPRECATION")
            SpeechEngineType.VOSK -> VoskOfflineSpeechEngine(ctx)
        }
        _isEngineReady.value = false
    }

    /**
     * Fallback mechanism: Disabled during diagnosis.
     * Shows explicit WHISPER ERROR rather than silently switching to Sherpa or Vosk.
     */
    fun fallbackToSherpa(reason: String) {
        android.util.Log.e("TaskScheduling-CRASH", "WHISPER ERROR (Fallback disabled for diagnostic build): $reason")
        _activeFallbackNotice.value = "WHISPER ERROR:\n$reason"
    }

    fun setLanguageMode(mode: LanguageMode) {
        // Enforce strict English-only recognition
        activeLanguageMode = LanguageMode.ENGLISH
        (speechEngine as? WhisperCppSpeechRecognitionEngine)?.languageMode = LanguageMode.ENGLISH
    }

    fun setSpeechEngine(engine: SpeechRecognitionEngine) {
        if (speechEngine === engine) return
        stopListening()
        speechEngine.release()
        System.gc()
        speechEngine = engine
        _isEngineReady.value = false
    }

    /**
     * Updates preferred audio source for active engine (MIC, VOICE_RECOGNITION, DEFAULT).
     */
    fun setPreferredAudioSource(source: String) {
        (speechEngine as? WhisperCppSpeechRecognitionEngine)?.preferredAudioSource = source
        (speechEngine as? SherpaOnnxSpeechRecognitionEngine)?.preferredAudioSource = source
    }

    /**
     * Eagerly prepares the embedded model in the background for zero-latency recognition.
     */
    fun prepareEngineAsync(onComplete: ((Boolean) -> Unit)? = null) {
        if (speechEngine.isModelLoaded) {
            _isEngineReady.value = true
            onComplete?.invoke(true)
            return
        }
        scope.launch(Dispatchers.IO) {
            val result = speechEngine.initialize()
            val success = result.isSuccess
            try {
                withContext(Dispatchers.Main) {
                    onComplete?.invoke(success)
                }
            } catch (_: Throwable) {
                onComplete?.invoke(success)
            }
        }
    }

    /**
     * Checks whether RECORD_AUDIO permission has been granted.
     */
    fun hasMicrophonePermission(): Boolean {
        if (permissionChecker != null) return permissionChecker.invoke()
        val ctx = context ?: return false
        return ContextCompat.checkSelfPermission(
            ctx,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Starts listening for voice commands using the active offline speech engine.
     */
    fun startListening(callback: VoiceInputCallback) {
        if (!hasMicrophonePermission()) {
            android.util.Log.e("VoiceInputManager", "Microphone permission RECORD_AUDIO denied")
            callback.onError(MSG_PERMISSION_DENIED)
            return
        }
        android.util.Log.i("VoiceInputManager", "Microphone permission RECORD_AUDIO verified: GRANTED")

        if (!speechEngine.isModelLoaded) {
            scope.launch {
                val initResult = withContext(Dispatchers.IO) {
                    speechEngine.initialize()
                }
                if (initResult.isFailure) {
                    val ex = initResult.exceptionOrNull()
                    val msg = "WHISPER ERROR: ${ex?.message ?: MSG_INIT_FAILED}"
                    callback.onError(msg)
                    return@launch
                }
                _isEngineReady.value = true
                listenWithEngine(callback, continuous = false)
            }
        } else {
            _isEngineReady.value = true
            listenWithEngine(callback, continuous = false)
        }
    }

    /**
     * Starts continuous listening for hands-free voice commands.
     */
    fun startContinuousListening(callback: VoiceInputCallback) {
        if (!hasMicrophonePermission()) {
            android.util.Log.e("VoiceInputManager", "Microphone permission RECORD_AUDIO denied")
            callback.onError(MSG_PERMISSION_DENIED)
            return
        }
        android.util.Log.i("VoiceInputManager", "Microphone permission RECORD_AUDIO verified: GRANTED")

        if (!speechEngine.isModelLoaded) {
            scope.launch {
                val initResult = withContext(Dispatchers.IO) {
                    speechEngine.initialize()
                }
                if (initResult.isFailure) {
                    val ex = initResult.exceptionOrNull()
                    val msg = "WHISPER ERROR: ${ex?.message ?: MSG_INIT_FAILED}"
                    callback.onError(msg)
                    return@launch
                }
                _isEngineReady.value = true
                listenWithEngine(callback, continuous = true)
            }
        } else {
            _isEngineReady.value = true
            listenWithEngine(callback, continuous = true)
        }
    }

    /**
     * Lambda convenience overload for starting voice listening.
     */
    fun startListening(
        onListeningStarted: () -> Unit = {},
        onPartialResult: (String) -> Unit = {},
        onSpeechRecognized: (String) -> Unit,
        onError: (String) -> Unit,
        onListeningStopped: () -> Unit = {}
    ) {
        startListening(object : VoiceInputCallback {
            override fun onListeningStarted() = onListeningStarted()
            override fun onPartialResult(hypothesis: String) = onPartialResult(hypothesis)
            override fun onSpeechRecognized(text: String) = onSpeechRecognized(text)
            override fun onError(message: String) = onError(message)
            override fun onListeningStopped() = onListeningStopped()
        })
    }

    private fun listenWithEngine(callback: VoiceInputCallback, continuous: Boolean = false) {
        val started = speechEngine.startListening(object : SpeechEngineListener {
            override fun onReady() {
                _isListening.value = true
                callback.onListeningStarted()
            }

            override fun onBeginningOfSpeech() {
                callback.onBeginningOfSpeech()
            }

            override fun onUtteranceFinalizing() {
                callback.onUtteranceFinalizing()
            }

            override fun onPartialResult(hypothesis: String) {
                callback.onPartialResult(hypothesis)
            }

            override fun onResult(recognizedText: String) {
                if (!continuous) {
                    _isListening.value = false
                }
                val clean = recognizedText.trim()
                if (clean.isNotBlank()) {
                    callback.onSpeechRecognized(clean)
                } else if (!continuous) {
                    callback.onError(MSG_COULD_NOT_UNDERSTAND)
                }
            }

            override fun onError(error: SpeechEngineError) {
                if (!continuous) {
                    _isListening.value = false
                }
                callback.onError(error.toUserMessage())
            }

            override fun onStopped() {
                _isListening.value = false
                callback.onListeningStopped()
            }

            override fun onAudioLevel(rmsLevel: Float, peakLevel: Float, inputDetected: Boolean) {
                callback.onAudioLevel(rmsLevel, peakLevel, inputDetected)
            }
        }, continuous = continuous)

        if (!started) {
            _isListening.value = false
            callback.onError("Microphone could not be started.")
        }
    }

    fun pauseListening() {
        speechEngine.pauseListening()
    }

    fun resumeListening() {
        speechEngine.resumeListening()
    }

    fun stopListening() {
        _isListening.value = false
        speechEngine.stopListening()
    }

    fun cancelListening() {
        _isListening.value = false
        speechEngine.cancelListening()
    }

    fun release() {
        _isListening.value = false
        stopListening()
    }

    private class DummySpeechEngine : SpeechRecognitionEngine, OfflineSpeechEngine {
        override val engineType: SpeechEngineType = SpeechEngineType.WHISPER_SMALL_Q5_1
        override val isModelLoaded: Boolean = true
        override val isRunning: Boolean = false
        override val configuredSampleRate: Int = 16000
        override val activeAudioSource: String = "VOICE_RECOGNITION"
        override val configuredBufferSize: Int = 2048
        override suspend fun initialize(): Result<Unit> = Result.success(Unit)
        override fun startListening(listener: SpeechEngineListener, continuous: Boolean): Boolean {
            listener.onReady()
            return true
        }
        override fun pauseListening() {}
        override fun resumeListening() {}
        override fun stopListening() {}
        override fun cancelListening() {}
        override fun release() {}
    }
}
