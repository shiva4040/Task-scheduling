package com.taskscheduling.app.voice

import com.taskscheduling.app.assistant.AssistantLogger
import com.taskscheduling.app.assistant.AssistantUiState
import com.taskscheduling.app.assistant.OfflineAssistantController
import com.taskscheduling.app.assistant.StopListeningResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Explicit state machine for continuous voice listening session.
 */
enum class ContinuousListeningState(val displayName: String, val statusLabel: String) {
    IDLE("Idle", "Ready"),
    LISTENING("Listening", "Listening…"),
    MUTED("Muted", "Microphone muted"),
    SPEECH_DETECTED("Speech Detected", "Listening…"),
    UTTERANCE_FINALIZING("Finalizing Utterance", "Processing speech…"),
    TRANSCRIBING("Transcribing", "Thinking…"),
    COMMAND_PROCESSING("Processing Command", "Thinking…"),
    EXECUTING("Executing Action", "Running action…"),
    LISTENING_AGAIN("Listening Again", "Listening…"),
    ERROR("Error", "Error"),
    STOPPED("Stopped", "Listening paused")
}

/**
 * Controller responsible for the lifecycle of continuous hands-free voice interaction.
 * Coordinates the offline speech engine (Whisper / Sherpa-ONNX / Vosk), assistant state machine,
 * TTS coordination, raw speech testing, pause-based endpointing, and global stop-listening commands.
 */
class ContinuousVoiceController(
    val voiceInputManager: VoiceInputManager,
    val assistantController: OfflineAssistantController,
    val speechOutputManager: SpeechOutputManager? = null,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Main)
) {
    private val _isContinuousListening = MutableStateFlow(false)
    val isContinuousListening: StateFlow<Boolean> = _isContinuousListening.asStateFlow()

    private val _continuousState = MutableStateFlow(ContinuousListeningState.IDLE)
    val continuousState: StateFlow<ContinuousListeningState> = _continuousState.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _partialHypothesis = MutableStateFlow("")
    val partialHypothesis: StateFlow<String> = _partialHypothesis.asStateFlow()

    var isRawSpeechTestMode: Boolean = false
        set(value) {
            field = value
            assistantController.setRawSpeechTestMode(value)
        }

    private var assistantStateJob: Job? = null
    @Volatile
    var isTtsSpeaking: Boolean = false
        private set
    @Volatile
    var lastTtsFinishedTimeMs: Long = 0L
        private set

    init {
        // Wire callbacks from OfflineAssistantController
        assistantController.onStopListeningRequested = {
            stopContinuousListening()
        }
        assistantController.onContinuousListeningResumed = {
            if (_isContinuousListening.value) {
                if (_isMuted.value) {
                    _continuousState.value = ContinuousListeningState.MUTED
                } else {
                    _continuousState.value = ContinuousListeningState.LISTENING
                    voiceInputManager.resumeListening()
                }
                syncAudioDiagnostics()
            }
        }
    }

    /**
     * Toggles microphone mute state.
     */
    fun toggleMute() {
        if (_isMuted.value) unmute() else mute()
    }

    /**
     * Mutes microphone input. Suppresses speech inference and drops newly captured audio.
     * Preserves continuous listening session and existing conversation history.
     */
    fun mute() {
        if (_isMuted.value) return
        _isMuted.value = true
        voiceInputManager.speechEngine.isMuted = true
        AssistantLogger.i("ContinuousVoiceController", "[CONTINUOUS_TRACE] [MUTE_TRACE] Microphone input MUTED by user")
        if (_isContinuousListening.value) {
            val currentState = _continuousState.value
            val isProcessingOrExecuting = currentState == ContinuousListeningState.UTTERANCE_FINALIZING ||
                currentState == ContinuousListeningState.TRANSCRIBING ||
                currentState == ContinuousListeningState.COMMAND_PROCESSING ||
                currentState == ContinuousListeningState.EXECUTING
            if (!isProcessingOrExecuting) {
                _continuousState.value = ContinuousListeningState.MUTED
                assistantController.setMutedState("Microphone muted")
            }
            syncAudioDiagnostics()
        }
    }

    /**
     * Unmutes microphone input. Resumes audio capture and continuous listening.
     */
    fun unmute() {
        if (!_isMuted.value) return
        _isMuted.value = false
        voiceInputManager.speechEngine.isMuted = false
        AssistantLogger.i("ContinuousVoiceController", "[CONTINUOUS_TRACE] [MUTE_TRACE] Microphone input UNMUTED by user")
        if (_isContinuousListening.value) {
            val currentState = _continuousState.value
            val isProcessingOrExecuting = currentState == ContinuousListeningState.UTTERANCE_FINALIZING ||
                currentState == ContinuousListeningState.TRANSCRIBING ||
                currentState == ContinuousListeningState.COMMAND_PROCESSING ||
                currentState == ContinuousListeningState.EXECUTING
            if (!isProcessingOrExecuting) {
                _continuousState.value = ContinuousListeningState.LISTENING
                voiceInputManager.resumeListening()
                assistantController.setListeningState()
            }
            syncAudioDiagnostics()
        }
    }

    /**
     * Starts continuous hands-free listening.
     * Ensures Whisper model is loaded and warm before reporting listening state.
     */
    fun startContinuousListening() {
        if (_isContinuousListening.value) return

        if (!voiceInputManager.speechEngine.isModelLoaded) {
            assistantController.setLoadingState("Loading Whisper model...")
            voiceInputManager.prepareEngineAsync { success ->
                if (success) {
                    startContinuousListeningInternal()
                } else {
                    assistantController.setErrorMessage("Whisper model loading failed. Tap to retry.")
                    syncAudioDiagnostics()
                }
            }
            return
        }

        startContinuousListeningInternal()
    }

    private fun startContinuousListeningInternal() {
        if (_isContinuousListening.value) return

        _isContinuousListening.value = true
        _continuousState.value = ContinuousListeningState.LISTENING
        assistantController.isContinuousListening = true
        assistantController.setListeningState()

        try {
            com.taskscheduling.app.feature.diagnostics.VoiceBotDiagnosticTracker.getInstance(voiceInputManager.context)
                .startNewBotSession()
            com.taskscheduling.app.feature.diagnostics.VoiceBotDiagnosticTracker.getInstance(voiceInputManager.context)
                .recordBotPipelineStage("Microphone Started", "SUCCESS")
        } catch (_: Throwable) {}

        // Start observing assistant states
        observeAssistantStates()

        voiceInputManager.startContinuousListening(object : VoiceInputCallback {
            override fun onListeningStarted() {
                _partialHypothesis.value = ""
                _continuousState.value = ContinuousListeningState.LISTENING
                AssistantLogger.i("ContinuousVoiceController", "[CONTINUOUS_TRACE] Continuous listening started. Recorder active and awaiting speech.")
                syncAudioDiagnostics()
            }

            override fun onBeginningOfSpeech() {
                _continuousState.value = ContinuousListeningState.SPEECH_DETECTED
                assistantController.updateEndpointStatus(false)
                AssistantLogger.i("ContinuousVoiceController", "[CONTINUOUS_TRACE] Beginning of speech detected by VAD")
                try {
                    com.taskscheduling.app.feature.diagnostics.VoiceBotDiagnosticTracker.getInstance(voiceInputManager.context)
                        .recordBotPipelineStage("Audio Buffers Received", "SUCCESS", "Speech active")
                } catch (_: Throwable) {}
            }

            override fun onPartialResult(hypothesis: String) {
                _partialHypothesis.value = hypothesis
                assistantController.updateDebugPartial(hypothesis)
            }

            override fun onUtteranceFinalizing() {
                _continuousState.value = ContinuousListeningState.UTTERANCE_FINALIZING
                assistantController.setLoadingState("Processing speech…")
                AssistantLogger.i("ContinuousVoiceController", "[CONTINUOUS_TRACE] Utterance finalizing (pause detected). Processing speech…")
            }

            override fun onSpeechRecognized(text: String) {
                _partialHypothesis.value = ""
                _continuousState.value = ContinuousListeningState.TRANSCRIBING
                assistantController.setLoadingState("Thinking…")
                assistantController.updateEndpointStatus(true)
                AssistantLogger.i("ContinuousVoiceController", "[CONTINUOUS_TRACE] Speech utterance recognized: \"$text\" -> dispatching to command handler")
                handleRecognizedSpeech(text)
            }

            override fun onError(message: String) {
                _partialHypothesis.value = ""
                syncAudioDiagnostics()
                try {
                    com.taskscheduling.app.feature.diagnostics.VoiceBotDiagnosticTracker.getInstance(voiceInputManager.context)
                        .recordBotPipelineStage("Recognition Requested", "FAILED", message)
                } catch (_: Throwable) {}

                val isFatalHardwareOrPermissionError = message.contains("permission", ignoreCase = true) ||
                        message.contains("unavailable", ignoreCase = true) ||
                        message.contains("failed to initialize", ignoreCase = true) ||
                        message.contains("could not be started", ignoreCase = true) ||
                        message.contains("Mic failed", ignoreCase = true)

                if (isFatalHardwareOrPermissionError || !_isContinuousListening.value) {
                    _isContinuousListening.value = false
                    _continuousState.value = ContinuousListeningState.ERROR
                    assistantController.isContinuousListening = false
                    assistantController.setErrorMessage(message)
                } else {
                    // A transient utterance transcription error must NOT permanently disable continuous listening
                    _continuousState.value = ContinuousListeningState.ERROR
                    assistantController.setErrorMessage(message)
                    coroutineScope.launch {
                        val delayMs = assistantController.autoResumeDelayMs
                        if (delayMs > 0L) {
                            kotlinx.coroutines.delay(delayMs)
                        }
                        if (_isContinuousListening.value) {
                            _continuousState.value = ContinuousListeningState.LISTENING_AGAIN
                            if (delayMs > 0L) {
                                kotlinx.coroutines.delay(200L)
                            }
                            if (_isContinuousListening.value) {
                                _continuousState.value = ContinuousListeningState.LISTENING
                                voiceInputManager.resumeListening()
                                assistantController.setListeningState()
                                syncAudioDiagnostics()
                            }
                        }
                    }
                }
            }

            override fun onListeningStopped() {
                _partialHypothesis.value = ""
                syncAudioDiagnostics()
                if (_isContinuousListening.value) {
                    voiceInputManager.resumeListening()
                } else {
                    _continuousState.value = ContinuousListeningState.IDLE
                }
            }

            override fun onAudioLevel(rmsLevel: Float, peakLevel: Float, inputDetected: Boolean) {
                assistantController.updateAudioLevel(rmsLevel, peakLevel, inputDetected)
            }
        })
        syncAudioDiagnostics()
    }

    /**
     * Stops continuous hands-free listening.
     * Cancels any pending workflows and resets assistant state to IDLE.
     */
    fun stopContinuousListening() {
        _isContinuousListening.value = false
        _continuousState.value = ContinuousListeningState.STOPPED
        assistantController.isContinuousListening = false
        assistantStateJob?.cancel()
        assistantStateJob = null

        voiceInputManager.stopListening()
        assistantController.stopListeningState()
        // Do NOT cancel pending workflow: stopping mic capture must not cancel in-flight action
        speechOutputManager?.stop()
        _partialHypothesis.value = ""
        syncAudioDiagnostics()
    }

    /**
     * Handles recognized user speech.
     * In raw speech test mode, updates UI diagnostics without executing commands.
     * In regular mode, checks for dedicated "Stop listening" commands and forwards to the assistant controller.
     */
    fun handleRecognizedSpeech(text: String) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) {
            try {
                com.taskscheduling.app.feature.diagnostics.VoiceBotDiagnosticTracker.getInstance(voiceInputManager.context)
                    .recordBotPipelineStage(
                        stageName = "Command Resolver Called",
                        status = "SKIPPED",
                        details = "Empty transcript received"
                    )
            } catch (_: Throwable) {}
            return
        }

        // Robust TTS echo / self-recognition suppression window (350ms post-TTS buffer)
        val now = System.currentTimeMillis()
        if (isTtsSpeaking || (now - lastTtsFinishedTimeMs) < 350L) {
            AssistantLogger.w(
                "ContinuousVoiceController",
                "Ignoring voice input captured during/immediately after TTS: \"$trimmed\""
            )
            return
        }

        AssistantLogger.i("ContinuousVoiceController", "[VOICE_TEXT] \"$trimmed\"")

        // 1. Raw speech test mode: Display recognized speech but NEVER execute commands
        if (isRawSpeechTestMode) {
            assistantController.updateDebugFinal(trimmed)
            AssistantLogger.i(
                "ContinuousVoiceController",
                "[RAW_SPEECH_TEST] FINAL: \"$trimmed\" (Command execution bypassed)"
            )
            return
        }

        // 2. Global stop-listening command check
        if (StopListeningResolver.isStopListening(trimmed)) {
            assistantController.cancelPendingWorkflow()
            stopContinuousListening()
            return
        }

        _continuousState.value = ContinuousListeningState.COMMAND_PROCESSING
        assistantController.setLoadingState("Thinking…")

        // 3. Forward to the assistant controller for command resolution & execution
        try {
            AssistantLogger.i("ContinuousVoiceController", "[CONTINUOUS_TRACE] Forwarding command to AssistantController: \"$trimmed\"")
            assistantController.processCommand(trimmed)
        } catch (t: Throwable) {
            AssistantLogger.e("ContinuousVoiceController", "[CONTINUOUS_TRACE] Command processing error for \"$trimmed\": ${t.message}")
            if (_isContinuousListening.value) {
                _continuousState.value = ContinuousListeningState.LISTENING
                voiceInputManager.resumeListening()
                assistantController.setListeningState()
            }
        }
    }

    private fun observeAssistantStates() {
        assistantStateJob?.cancel()
        assistantStateJob = coroutineScope.launch {
            assistantController.uiState.collect { state ->
                AssistantLogger.i("ContinuousVoiceController", "[CONTINUOUS_TRACE] Assistant UI state changed to: ${state::class.simpleName}")
                when (state) {
                    is AssistantUiState.Running -> {
                        _continuousState.value = ContinuousListeningState.EXECUTING
                        // Pause microphone during physical gesture execution
                        voiceInputManager.pauseListening()
                    }
                    is AssistantUiState.Speaking -> {
                        _continuousState.value = ContinuousListeningState.EXECUTING
                        voiceInputManager.pauseListening()
                        if (_isContinuousListening.value) {
                            coroutineScope.launch {
                                val delayMs = maxOf(assistantController.autoResumeDelayMs, 1000L)
                                if (delayMs > 0L) {
                                    kotlinx.coroutines.delay(delayMs)
                                }
                                if (_isContinuousListening.value && !isTtsSpeaking) {
                                    _continuousState.value = ContinuousListeningState.LISTENING
                                    voiceInputManager.resumeListening()
                                    assistantController.setListeningState()
                                    AssistantLogger.i("ContinuousVoiceController", "[CONTINUOUS_TRACE] Auto-resumed listening after speaking")
                                    syncAudioDiagnostics()
                                }
                            }
                        }
                    }
                    is AssistantUiState.Processing -> {
                        if (_continuousState.value != ContinuousListeningState.UTTERANCE_FINALIZING &&
                            _continuousState.value != ContinuousListeningState.TRANSCRIBING) {
                            _continuousState.value = ContinuousListeningState.COMMAND_PROCESSING
                        }
                    }
                    is AssistantUiState.ConfirmationRequired,
                    is AssistantUiState.TaskDraftReady -> {
                        if (_isContinuousListening.value) {
                            _continuousState.value = ContinuousListeningState.LISTENING
                            voiceInputManager.resumeListening()
                            AssistantLogger.i("ContinuousVoiceController", "[CONTINUOUS_TRACE] Microphone active and listening for user confirmation/details")
                            syncAudioDiagnostics()
                        }
                    }
                    is AssistantUiState.Completed,
                    is AssistantUiState.TaskActionCompleted -> {
                        if (_isContinuousListening.value) {
                            if (_isMuted.value) {
                                _continuousState.value = ContinuousListeningState.MUTED
                                assistantController.setMutedState("Microphone muted")
                                syncAudioDiagnostics()
                            } else {
                                _continuousState.value = ContinuousListeningState.LISTENING_AGAIN
                                coroutineScope.launch {
                                    val delayMs = assistantController.autoResumeDelayMs
                                    if (delayMs > 0L) {
                                        kotlinx.coroutines.delay(delayMs)
                                    }
                                    if (_isContinuousListening.value && !_isMuted.value) {
                                        _continuousState.value = ContinuousListeningState.LISTENING
                                        voiceInputManager.resumeListening()
                                        assistantController.setListeningState()
                                        AssistantLogger.i("ContinuousVoiceController", "[CONTINUOUS_TRACE] Action completed. Continuous listening resumed (state=LISTENING)")
                                        syncAudioDiagnostics()
                                    }
                                }
                            }
                        }
                    }
                    is AssistantUiState.Error,
                    is AssistantUiState.Cancelled -> {
                        if (_isContinuousListening.value) {
                            _continuousState.value = ContinuousListeningState.ERROR
                            coroutineScope.launch {
                                val delayMs = assistantController.autoResumeDelayMs
                                if (delayMs > 0L) {
                                    kotlinx.coroutines.delay(delayMs)
                                }
                                if (_isContinuousListening.value) {
                                    _continuousState.value = ContinuousListeningState.LISTENING_AGAIN
                                    if (delayMs > 0L) {
                                        kotlinx.coroutines.delay(200L)
                                    }
                                    if (_isContinuousListening.value) {
                                        _continuousState.value = ContinuousListeningState.LISTENING
                                        voiceInputManager.resumeListening()
                                        assistantController.setListeningState()
                                        AssistantLogger.i("ContinuousVoiceController", "[CONTINUOUS_TRACE] Resumed listening after transient error/cancellation")
                                        syncAudioDiagnostics()
                                    }
                                }
                            }
                        }
                    }
                    is AssistantUiState.Listening -> {
                        _continuousState.value = ContinuousListeningState.LISTENING
                        voiceInputManager.resumeListening()
                        syncAudioDiagnostics()
                    }
                    is AssistantUiState.Idle -> {
                        if (!_isContinuousListening.value) {
                            _continuousState.value = ContinuousListeningState.IDLE
                            voiceInputManager.stopListening()
                            syncAudioDiagnostics()
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    fun syncAudioDiagnostics() {
        val engine = voiceInputManager.speechEngine
        val isLoaded = engine.isModelLoaded
        val isRec = engine.isRunning
        val whisperEngine = engine as? WhisperCppSpeechRecognitionEngine
        val modelStatusStr = whisperEngine?.engineState?.value?.name ?: if (isLoaded) "READY" else "NOT_LOADED"
        val lastErr = whisperEngine?.lastErrorMessage?.value ?: ""
        val perf = whisperEngine?.performanceMetrics?.value
        val memStr = if (perf != null && perf.availableMemoryMB > 0f) {
            "Avail: ${perf.availableMemoryMB.toInt()}MB | App: ${perf.appMemoryMB.toInt()}MB"
        } else "OK"

        val hasSpeech = assistantController.voiceDebugInfo.value.inputDetected || assistantController.voiceDebugInfo.value.rmsLevel >= 0.005f
        assistantController.updateAudioDiagnostics(
            engineType = engine.engineType.displayName,
            audioSource = engine.activeAudioSource,
            sampleRate = engine.configuredSampleRate,
            channelCount = 1,
            bufferSize = engine.configuredBufferSize,
            audioRecordState = if (isRec) "RECORDING" else "IDLE",
            isListening = _isContinuousListening.value,
            voskState = if (isLoaded) "READY" else "NOT_LOADED",
            modelState = modelStatusStr,
            recognizerState = if (isLoaded) "READY" else "NOT_INITIALIZED",
            streamState = if (isRec) "ACTIVE" else "IDLE",
            micPipelineOk = hasSpeech || assistantController.voiceDebugInfo.value.micPipelineOk,
            pcmPipelineOk = hasSpeech || assistantController.voiceDebugInfo.value.pcmPipelineOk,
            enginePipelineOk = isLoaded,
            memoryStatus = memStr,
            errorMessage = lastErr
        )
    }

    fun speakWithMute(text: String, onComplete: () -> Unit = {}) {
        val tts = speechOutputManager
        if (tts == null) {
            onComplete()
            return
        }

        isTtsSpeaking = true
        voiceInputManager.pauseListening()
        tts.speak(
            text = text,
            onSpeechStarted = {
                isTtsSpeaking = true
                voiceInputManager.pauseListening()
            },
            onSpeechFinished = {
                isTtsSpeaking = false
                lastTtsFinishedTimeMs = System.currentTimeMillis()
                if (_isContinuousListening.value) {
                    voiceInputManager.resumeListening()
                }
                onComplete()
            }
        )
    }

    fun release() {
        stopContinuousListening()
        voiceInputManager.stopListening()
        speechOutputManager?.stop()
    }
}
