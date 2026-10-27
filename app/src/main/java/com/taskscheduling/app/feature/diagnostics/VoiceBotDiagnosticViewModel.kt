package com.taskscheduling.app.feature.diagnostics

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.taskscheduling.app.accessibility.AccessibilityUtils
import com.taskscheduling.app.accessibility.TaskSchedulingAccessibilityService
import com.taskscheduling.app.voice.LanguageMode
import com.taskscheduling.app.voice.RawMicrophoneTester
import com.taskscheduling.app.voice.SpeechRecognitionService
import com.taskscheduling.app.voice.WhisperCppSpeechRecognitionEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * ViewModel orchestrating data and interactions for the Voice & Bot Diagnostic Center.
 */
class VoiceBotDiagnosticViewModel(
    private val context: Context,
    private val tracker: VoiceBotDiagnosticTracker = VoiceBotDiagnosticTracker.getInstance(context)
) : ViewModel() {

    val systemOverview: StateFlow<SystemOverviewState> = tracker.systemOverview
    val audioCapture: StateFlow<AudioCaptureTelemetry> = tracker.audioCapture
    val speechModel: StateFlow<SpeechModelTelemetry> = tracker.speechModel
    val longSpeech: StateFlow<LongSpeechTelemetry> = tracker.longSpeech
    val floatingBotPipeline: StateFlow<FloatingBotPipelineTelemetry> = tracker.floatingBotPipeline
    val commandPipeline: StateFlow<CommandPipelineTelemetry> = tracker.commandPipeline
    val testLabState: StateFlow<TestLabRunState> = tracker.testLabState

    // Event filter state
    val selectedComponentFilter = MutableStateFlow<DiagnosticComponent?>(null)
    val selectedSeverityFilter = MutableStateFlow<DiagnosticSeverity?>(null)
    val searchQuery = MutableStateFlow("")

    val filteredEvents: StateFlow<List<DiagnosticEvent>> = combine(
        tracker.eventLogs,
        selectedComponentFilter,
        selectedSeverityFilter,
        searchQuery
    ) { logs, comp, sev, query ->
        logs.filter { event ->
            val matchComp = comp == null || event.component == comp
            val matchSev = sev == null || event.severity == sev
            val matchQuery = query.isBlank() ||
                    event.eventName.contains(query, ignoreCase = true) ||
                    event.description.contains(query, ignoreCase = true) ||
                    event.metadata.values.any { it.contains(query, ignoreCase = true) }
            matchComp && matchSev && matchQuery
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Lab test recording state
    private var labRecorder: RawMicrophoneTester? = null
    private var labRecordingJob: Job? = null
    private val isLabRecording = AtomicBoolean(false)
    private var testAudioSamples: FloatArray? = null
    private var testStartTimeMs: Long = 0L

    // Exported report preview state
    private val _reportPreviewText = MutableStateFlow<String?>(null)
    val reportPreviewText: StateFlow<String?> = _reportPreviewText.asStateFlow()

    private val _lastExportedFile = MutableStateFlow<File?>(null)
    val lastExportedFile: StateFlow<File?> = _lastExportedFile.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _actionConfirmation = MutableStateFlow<String?>(null)
    val actionConfirmation: StateFlow<String?> = _actionConfirmation.asStateFlow()

    fun clearActionConfirmation() {
        _actionConfirmation.value = null
    }

    init {
        refreshDiagnostics()
    }

    /**
     * Refreshes environment and status from live components.
     */
    fun refreshDiagnostics() {
        val hasMic = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        val isA11yEnabled = AccessibilityUtils.isAccessibilityServiceEnabled(context)
        val a11yService = TaskSchedulingAccessibilityService.instance
        val isA11yConnected = a11yService != null
        val botVisible = a11yService?.overlayManager?.isFloatingShortcutVisible == true
        val isListening = a11yService?.assistantController?.isContinuousListening == true

        tracker.refreshSystemStatus(
            isMicPermissionGranted = hasMic,
            isAccessibilityServiceEnabled = isA11yEnabled,
            isAccessibilityServiceConnected = isA11yConnected,
            botOverlayVisible = botVisible,
            botIsListening = isListening
        )

        // Sync model state
        val engine = WhisperCppSpeechRecognitionEngine.getInstance(context)
        tracker.updateModelTelemetry(
            modelName = engine.modelManager.selectedModel.filename,
            modelPath = engine.modelManager.getModelPath(),
            sizeBytes = engine.modelManager.selectedModel.sizeBytes,
            isLoaded = engine.isModelLoaded,
            state = engine.engineState.value,
            threads = 2,
            language = engine.languageMode,
            error = engine.lastErrorMessage.value
        )
    }

    /**
     * Releases the microphone from the floating bot or continuous listening session.
     */
    fun releaseAssistantMic() {
        val a11yService = TaskSchedulingAccessibilityService.instance
        a11yService?.overlayManager?.continuousVoiceController?.stopContinuousListening()
        val engine = WhisperCppSpeechRecognitionEngine.getInstance(context)
        engine.stopListening()
        refreshDiagnostics()
        _actionConfirmation.value = "Microphone released from Floating Assistant."
    }

    /**
     * Starts an isolated manual test in the Test Lab.
     * Enforces the recording lifecycle: IDLE -> RECORDING -> FINALIZING -> READY_TO_TRANSCRIBE -> TRANSCRIBING -> RESULT
     */
    fun startLabTest(
        language: LanguageMode,
        callerLabel: String = "DIAGNOSTIC_PATH",
        promptPhrase: String = "",
        expectedText: String = ""
    ) {
        val hasMic = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasMic) {
            tracker.updateTestLabState(
                TestLabRunState(
                    testId = "lab_${System.currentTimeMillis()}",
                    languageMode = language,
                    callerLabel = callerLabel,
                    promptPhrase = promptPhrase,
                    expectedText = expectedText,
                    isRecording = false,
                    isTranscribing = false,
                    lifecycleStage = "PERMISSION_DENIED",
                    outcomeClassification = TestOutcomeClassification.PERMISSION_DENIED,
                    errorDetails = "RECORD_AUDIO permission is not granted."
                )
            )
            return
        }

        // Check for mic conflict
        val a11y = TaskSchedulingAccessibilityService.instance
        val isBotListening = a11y?.assistantController?.isContinuousListening == true
        val engine = WhisperCppSpeechRecognitionEngine.getInstance(context)
        val isEngineBusy = engine.engineState.value == com.taskscheduling.app.voice.WhisperEngineState.CAPTURING

        if (isBotListening && callerLabel == "DIAGNOSTIC_PATH") {
            tracker.updateTestLabState(
                TestLabRunState(
                    testId = "lab_${System.currentTimeMillis()}",
                    languageMode = language,
                    callerLabel = callerLabel,
                    promptPhrase = promptPhrase,
                    expectedText = expectedText,
                    isRecording = false,
                    isTranscribing = false,
                    lifecycleStage = "FAILED",
                    outcomeClassification = TestOutcomeClassification.MIC_IN_USE_BY_ANOTHER_COMPONENT,
                    errorDetails = "Microphone is in use by Floating Assistant. Stop or release it before starting."
                )
            )
            return
        }

        val testId = "lab_${System.currentTimeMillis()}"
        testAudioSamples = null
        testStartTimeMs = System.currentTimeMillis()

        tracker.updateTestLabState(
            TestLabRunState(
                testId = testId,
                languageMode = language,
                callerLabel = callerLabel,
                promptPhrase = promptPhrase,
                expectedText = expectedText,
                isRecording = true,
                isTranscribing = false,
                lifecycleStage = "RECORDING",
                outcomeClassification = TestOutcomeClassification.INCONCLUSIVE
            )
        )

        tracker.logEvent(
            severity = DiagnosticSeverity.INFO,
            component = DiagnosticComponent.LANGUAGE_CONFIG,
            eventName = "LAB_TEST_STARTED",
            description = "Started Lab Test ($callerLabel) in ${language.displayName} (${language.code}). Prompt: \"$promptPhrase\"",
            metadata = mapOf("lang" to language.code, "expected" to expectedText, "caller" to callerLabel)
        )

        labRecorder = RawMicrophoneTester(context)
        isLabRecording.set(true)

        val audioSource = if (callerLabel == "FLOATING_BOT_PATH") "VOICE_RECOGNITION" else "DEFAULT"

        labRecordingJob = viewModelScope.launch(Dispatchers.IO) {
            val endpointMs = if (language == LanguageMode.ENGLISH) 950L else 1200L
            val recordRes = labRecorder?.recordTimedDiagnostic(
                source = audioSource,
                durationMs = 15000L,
                stopSignal = { !isLabRecording.get() },
                autoEndpointOnPause = true,
                endpointSilenceMs = endpointMs
            )

            // When recorder exits (either by timeout or stopSignal), enter FINALIZING
            withContext(Dispatchers.Main) {
                tracker.updateTestLabState(
                    tracker.testLabState.value.copy(
                        isRecording = false,
                        lifecycleStage = "FINALIZING"
                    )
                )
            }

            if (recordRes != null && recordRes.isSuccess) {
                val summary = recordRes.getOrThrow()
                val samples = summary.pcmFloats ?: FloatArray(0)
                testAudioSamples = samples
                // PCM16 mono at 16kHz: duration in ms = (samples * 1000) / 16000
                val actualDurationMs = if (samples.isNotEmpty()) (samples.size * 1000L) / 16000L else 0L

                withContext(Dispatchers.Main) {
                    tracker.recordCapturedAudioStats(
                        samples = samples,
                        source = summary.source,
                        isSubmitted = samples.isNotEmpty(),
                        owner = callerLabel
                    )
                    tracker.updateTestLabState(
                        tracker.testLabState.value.copy(
                            isRecording = false,
                            lifecycleStage = "READY_TO_TRANSCRIBE",
                            capturedSampleCount = samples.size,
                            recordingDurationMs = actualDurationMs,
                            rmsLevel = summary.averageRms,
                            peakAmplitude = summary.peak,
                            silencePercentage = tracker.audioCapture.value.estimatedSilencePercentage
                        )
                    )

                    when {
                        samples.isEmpty() -> {
                            tracker.updateTestLabState(
                                tracker.testLabState.value.copy(
                                    lifecycleStage = "FAILED",
                                    outcomeClassification = TestOutcomeClassification.NO_AUDIO_CAPTURED,
                                    errorDetails = "No audio samples were recorded."
                                )
                            )
                        }
                        actualDurationMs < 200L -> {
                            tracker.updateTestLabState(
                                tracker.testLabState.value.copy(
                                    lifecycleStage = "FAILED",
                                    outcomeClassification = TestOutcomeClassification.STOPPED_PREMATURELY,
                                    errorDetails = "Recording stopped prematurely ($actualDurationMs ms, minimum 200ms required)."
                                )
                            )
                        }
                        else -> {
                            // Auto-transcribe upon clean capture completion
                            transcribeLabAudio(samples, language, expectedText, callerLabel)
                        }
                    }
                }
            } else {
                withContext(Dispatchers.Main) {
                    val err = recordRes?.exceptionOrNull()?.message ?: "Recording failed"
                    tracker.updateTestLabState(
                        tracker.testLabState.value.copy(
                            isRecording = false,
                            lifecycleStage = "FAILED",
                            errorDetails = err,
                            outcomeClassification = TestOutcomeClassification.NO_AUDIO_CAPTURED
                        )
                    )
                }
            }
        }
    }

    /**
     * Stops the lab test manually and flushes audio for Whisper transcription.
     * Does NOT cancel the recording job prematurely, allowing the recorder to flush cleanly.
     */
    fun stopLabTestAndTranscribe() {
        if (!isLabRecording.get()) return

        tracker.updateTestLabState(
            tracker.testLabState.value.copy(
                isRecording = false,
                lifecycleStage = "FINALIZING"
            )
        )

        isLabRecording.set(false)
        labRecorder?.stop()
    }

    /**
     * Transcribes test audio using the shared diagnostic recognition service without executing commands.
     */
    private fun transcribeLabAudio(
        samples: FloatArray,
        language: LanguageMode,
        expectedText: String,
        callerLabel: String
    ) {
        tracker.updateTestLabState(
            tracker.testLabState.value.copy(
                isRecording = false,
                isTranscribing = true,
                lifecycleStage = "TRANSCRIBING"
            )
        )

        viewModelScope.launch(Dispatchers.IO) {
            val t0 = System.currentTimeMillis()
            val service = SpeechRecognitionService.getInstance(context)
            val result = service.transcribe(
                audioSamples = samples,
                languageMode = language,
                source = if (callerLabel == "FLOATING_BOT_PATH") "FLOATING_BOT_TEST" else "TEST_LAB",
                customJobId = "lab_${System.currentTimeMillis()}"
            )
            val inferenceTimeMs = System.currentTimeMillis() - t0

            withContext(Dispatchers.Main) {
                if (result.isSuccess) {
                    val transcript = result.text.trim()
                    val actualDurMs = (samples.size * 1000L) / 16000L
                    val totalLatency = (System.currentTimeMillis() - testStartTimeMs).coerceAtLeast(inferenceTimeMs)

                    // Classify test outcome honestly
                    val outcome = when {
                        samples.isEmpty() -> TestOutcomeClassification.NO_AUDIO_CAPTURED
                        tracker.audioCapture.value.peakAmplitude <= 0.005f -> TestOutcomeClassification.AUDIO_NEARLY_SILENT
                        transcript.isBlank() -> TestOutcomeClassification.AUDIO_CAPTURED_EMPTY_TRANSCRIPT
                        expectedText.isNotBlank() && transcript.equals(expectedText, ignoreCase = true) -> TestOutcomeClassification.SUCCESSFUL_TRANSCRIPT
                        expectedText.isNotBlank() && !transcript.equals(expectedText, ignoreCase = true) -> TestOutcomeClassification.INCORRECT_WORDS
                        else -> TestOutcomeClassification.SUCCESSFUL_TRANSCRIPT
                    }

                    tracker.updateTestLabState(
                        tracker.testLabState.value.copy(
                            isTranscribing = false,
                            lifecycleStage = "RESULT",
                            recognizedText = transcript,
                            detectedLanguage = result.language.ifBlank { language.code },
                            inferenceTimeMs = result.processingTimeMs,
                            totalTimeMs = result.totalSubmissionToResultMs,
                            outcomeClassification = outcome,
                            isOutputTruncatedOrEmpty = transcript.isBlank()
                        )
                    )

                    tracker.logEvent(
                        severity = DiagnosticSeverity.INFO,
                        component = DiagnosticComponent.WHISPER_INFERENCE,
                        eventName = "LAB_TRANSCRIPTION_RESULT",
                        description = "Transcript: \"$transcript\" in ${inferenceTimeMs}ms (Outcome: ${outcome.displayName})",
                        metadata = mapOf("transcript" to transcript, "outcome" to outcome.name, "caller" to callerLabel)
                    )
                } else {
                    val err = result.error ?: "Transcription failed"
                    val isBusy = err.contains("busy", ignoreCase = true)
                    val outcome = if (isBusy) TestOutcomeClassification.ENGINE_BUSY else TestOutcomeClassification.INFERENCE_TIMEOUT_OR_FAILURE

                    tracker.updateTestLabState(
                        tracker.testLabState.value.copy(
                            isTranscribing = false,
                            lifecycleStage = "FAILED",
                            errorDetails = err,
                            outcomeClassification = outcome
                        )
                    )
                    tracker.logEvent(
                        severity = DiagnosticSeverity.ERROR,
                        component = DiagnosticComponent.WHISPER_INFERENCE,
                        eventName = "LAB_TRANSCRIPTION_ERROR",
                        description = "Lab test inference failed: $err",
                        errorCode = err
                    )
                }
            }
        }
    }

    /**
     * Cancels the active lab test.
     */
    fun cancelLabTest() {
        isLabRecording.set(false)
        labRecorder?.stop()
        labRecordingJob?.cancel()
        testAudioSamples = null

        tracker.updateTestLabState(
            tracker.testLabState.value.copy(
                isRecording = false,
                isTranscribing = false,
                lifecycleStage = "CANCELLED",
                outcomeClassification = TestOutcomeClassification.CANCELLED,
                errorDetails = "Test was cancelled by user."
            )
        )
        tracker.logEvent(
            severity = DiagnosticSeverity.WARNING,
            component = DiagnosticComponent.CANCELLATION,
            eventName = "LAB_TEST_CANCELLED",
            description = "Lab test was explicitly cancelled."
        )
    }

    /**
     * Cancels active engine recording.
     */
    fun cancelEngineRecording() {
        val engine = WhisperCppSpeechRecognitionEngine.getInstance(context)
        engine.stopListening()
        tracker.logEvent(
            severity = DiagnosticSeverity.INFO,
            component = DiagnosticComponent.CANCELLATION,
            eventName = "ENGINE_RECORDING_CANCELLED",
            description = "User requested cancellation of active microphone recording."
        )
        refreshDiagnostics()
    }

    /**
     * Cancels active Whisper inference without tearing down native context.
     */
    fun cancelEngineInference() {
        val engine = WhisperCppSpeechRecognitionEngine.getInstance(context)
        engine.cancelActiveTranscription()
        tracker.logEvent(
            severity = DiagnosticSeverity.INFO,
            component = DiagnosticComponent.CANCELLATION,
            eventName = "ENGINE_INFERENCE_CANCELLED",
            description = "User requested cancellation of active Whisper inference."
        )
        refreshDiagnostics()
    }

    /**
     * Generates and prepares the diagnostic report for user preview and export.
     */
    fun prepareReportPreview() {
        viewModelScope.launch(Dispatchers.IO) {
            val preview = VoiceBotDiagnosticReportExporter.buildTextReport(context, tracker)
            _reportPreviewText.value = preview
        }
    }

    fun dismissReportPreview() {
        _reportPreviewText.value = null
    }

    /**
     * Copies the full plain-text diagnostic report to the system clipboard.
     */
    fun copyReportToClipboard() {
        val ok = VoiceBotDiagnosticReportExporter.copyReportToClipboard(context, tracker)
        _actionConfirmation.value = if (ok) "Report copied to clipboard." else "Failed to copy report to clipboard."
    }

    /**
     * Saves the diagnostic report directly to the chosen SAF Uri.
     */
    fun saveReportToUri(uri: android.net.Uri, isJson: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            val content = if (isJson) {
                VoiceBotDiagnosticReportExporter.buildJsonReport(context, tracker).toString(2)
            } else {
                VoiceBotDiagnosticReportExporter.buildTextReport(context, tracker)
            }
            val ok = VoiceBotDiagnosticReportExporter.writeReportToUri(context, uri, content)
            withContext(Dispatchers.Main) {
                _actionConfirmation.value = if (ok) "Report successfully saved to chosen file." else "Failed to write report to storage."
            }
        }
    }

    /**
     * Exports TXT and JSON reports and creates share Intent.
     */
    fun exportReport(onReadyToShare: (android.content.Intent) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val (txtFile, _) = VoiceBotDiagnosticReportExporter.exportReportFiles(context, tracker)
            _lastExportedFile.value = txtFile
            val shareIntent = VoiceBotDiagnosticReportExporter.createShareIntent(context, txtFile)
            withContext(Dispatchers.Main) {
                onReadyToShare(shareIntent)
            }
        }
    }

    /**
     * Creates a share Intent directly from the latest text report file.
     */
    fun getShareIntent(onReadyToShare: (android.content.Intent) -> Unit) {
        exportReport(onReadyToShare)
    }

    fun clearLogs() {
        tracker.clearEventLogs()
    }
}
