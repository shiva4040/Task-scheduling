package com.taskscheduling.app.feature.diagnostics

import android.content.Context
import android.os.Build
import com.taskscheduling.app.voice.LanguageMode as VoiceLanguageMode
import com.taskscheduling.app.voice.WhisperEngineState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Diagnostic event severity levels.
 */
enum class DiagnosticSeverity {
    DEBUG,
    INFO,
    WARNING,
    ERROR
}

/**
 * System components monitored by the Voice & Bot Diagnostic Center.
 */
enum class DiagnosticComponent {
    MICROPHONE,
    AUDIO_CAPTURE,
    ENDPOINTING,
    MODEL_LOADING,
    WHISPER_INFERENCE,
    LANGUAGE_CONFIG,
    FLOATING_BOT,
    COMMAND_RESOLVER,
    ACCESSIBILITY_SERVICE,
    WORKFLOW_EXECUTION,
    CANCELLATION
}

/**
 * Diagnostic failure classification when voice is not recognized.
 */
enum class AudioFailureCause(val title: String, val explanation: String) {
    NONE("None", "Speech recognized successfully."),
    PERMISSION_DENIED("Microphone Permission Denied", "Android RECORD_AUDIO permission was not granted."),
    RECORDER_INIT_FAILED("AudioRecord Init Failed", "AudioRecord could not initialize with requested sample rate/source."),
    NO_BUFFERS_RECEIVED("No Audio Buffers Received", "Microphone read returned 0 bytes or timed out immediately."),
    BUFFER_EMPTY("Audio Buffer Empty", "The captured audio array had zero samples when finalized."),
    AUDIO_NEARLY_SILENT("Captured Audio is Nearly Silent", "Max audio peak was <= 0.005f. Microphone input was too quiet or muted."),
    AUDIO_TRUNCATED("Captured Audio is Truncated", "Recording length was < 3200 samples (200ms)."),
    REJECTED_BY_VAD("Rejected by Speech Detector", "Audio RMS level was below 0.003f and peak was below 0.015f."),
    TRANSCRIPTION_RETURNED_EMPTY("Transcription Returned Empty", "Whisper processed the audio but returned no words."),
    PRE_ROLL_NOT_FINALIZED("Audio Buffer Prematurely Cleared", "Buffer was cleared before dispatch or during rapid state change."),
    TIMED_OUT("Inference or Read Timed Out", "Operation did not complete within the watchdog timeout."),
    AUDIO_PROCESSING_FAILED("Audio Preprocessing Failed", "Normalization or PCM float conversion encountered an error."),
    NATIVE_INFERENCE_FAILED("Native Whisper Inference Failed", "Whisper JNI bridge returned a failure code or threw an exception."),
    CAUSE_UNKNOWN("Actual Cause Not Determined", "Telemetry inconclusive; check raw audio stats and timeline.")
}

/**
 * Test outcome classifications for the English, Hindi, and Hinglish Test Lab.
 */
enum class TestOutcomeClassification(val displayName: String) {
    NO_AUDIO_CAPTURED("No Audio Captured"),
    AUDIO_NEARLY_SILENT("Audio Captured But Nearly Silent"),
    AUDIO_CAPTURED_EMPTY_TRANSCRIPT("Audio Captured, Empty Transcript"),
    STOPPED_PREMATURELY("Recording Stopped Prematurely (<200ms)"),
    AUDIO_FINALIZED_NOT_SUBMITTED("Audio Finalized But Not Submitted"),
    ENGINE_BUSY("Inference Rejected: Engine Busy"),
    CANCELLED("Test Cancelled by User"),
    PERMISSION_DENIED("Microphone Permission Denied"),
    MIC_IN_USE_BY_ANOTHER_COMPONENT("Microphone In Use by Another Component"),
    INCORRECT_WORDS("Incorrect Words"),
    MISSING_BEGINNING_OR_ENDING("Missing Beginning or Ending"),
    LANGUAGE_MISMATCH("Hindi/Hinglish Language Mismatch"),
    SUCCESSFUL_TRANSCRIPT("Successful Transcript"),
    INFERENCE_TIMEOUT_OR_FAILURE("Inference Timeout or Failure"),
    INCONCLUSIVE("Inconclusive")
}

/**
 * Immutable diagnostic event entry.
 */
data class DiagnosticEvent(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val timestampFormatted: String = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(timestamp)),
    val severity: DiagnosticSeverity = DiagnosticSeverity.INFO,
    val component: DiagnosticComponent,
    val sessionId: String = "",
    val jobId: String = "",
    val eventName: String,
    val description: String,
    val errorCode: String? = null,
    val metadata: Map<String, String> = emptyMap(),
    val suggestedCheck: String? = null
)

/**
 * Live System Overview state.
 */
data class SystemOverviewState(
    val micPermissionGranted: Boolean = false,
    val micCaptureState: String = "IDLE", // IDLE, RECORDING, STOPPED, ERROR
    val currentRecordingDurationMs: Long = 0L,
    val hasMeasurableAudio: Boolean = false,
    val audioCaptureEngine: String = "AudioRecord (16kHz PCM16)",
    val requestedAudioSource: String = "DEFAULT",
    val actualAudioSource: String = "DEFAULT",
    val sampleRate: Int = 16000,
    val channelCount: Int = 1,
    val pcmFormat: String = "16-bit Signed PCM",
    val speechRecognitionEngine: String = "WhisperCppSpeechRecognitionEngine",
    val loadedModelName: String = "ggml-tiny.en-q5_1.bin",
    val loadedModelPath: String = "assets/whisper-models/ggml-tiny.en-q5_1.bin",
    val isModelLoaded: Boolean = false,
    val selectedLanguage: String = "ENGLISH (en)",
    val inferenceState: String = "IDLE", // IDLE, QUEUED, DECODING, SUCCEEDED, FAILED, CANCELLED
    val activeInferenceJobId: String = "",
    val queuedJobCount: Int = 0,
    val lastSuccessfulTranscriptionTime: String = "None",
    val lastTranscriptionDurationMs: Long = 0L,
    val lastRecognitionResult: String = "",
    val lastFailureReason: String = "None",
    val floatingBotState: String = "HIDDEN", // VISIBLE, HIDDEN, LISTENING, PROCESSING
    val isAccessibilityConnected: Boolean = false,
    val isAccessibilityEnabled: Boolean = false,
    val commandProcessingState: String = "IDLE",
    val lastWorkflowStatus: String = "NONE",
    val lastCheckedTimestamp: Long = System.currentTimeMillis(),
    val isStale: Boolean = false
)

/**
 * Live Microphone and Audio Capture telemetry.
 */
data class AudioCaptureTelemetry(
    val requestedSource: String = "DEFAULT",
    val actualSource: String = "DEFAULT",
    val recorderState: String = "RECORDSTATE_STOPPED",
    val isInitialized: Boolean = false,
    val initError: String? = null,
    val sampleRate: Int = 16000,
    val channelCount: Int = 1,
    val pcmEncoding: String = "ENCODING_PCM_16BIT",
    val bufferSizeBytes: Int = 4096,
    val totalSamplesCaptured: Long = 0L,
    val totalCapturedBytes: Long = 0L,
    val actualRecordingDurationMs: Long = 0L,
    val effectiveAudioDurationMs: Long = 0L,
    val framesReceivedCount: Long = 0L,
    val emptyFramesCount: Long = 0L,
    val bufferOverrunsCount: Long = 0L,
    val readErrorsCount: Long = 0L,
    val lastReadErrorCode: Int = 0,
    val stoppedNormally: Boolean = true,
    val bufferStatus: String = "IDLE", // IDLE, ACCUMULATING, SUBMITTED, REJECTED, EMPTY, TRUNCATED
    val stoppedBeforeSpeechFinished: Boolean = false,
    val recordingStartTimeMs: Long = 0L,
    val recordingStopTimeMs: Long = 0L,
    val samplesPerRead: Int = 1024,
    val bytesPerRead: Int = 2048,
    val stopReason: String = "UNKNOWN", // USER_STOP, SILENCE_ENDPOINT, DURATION_LIMIT, CANCELLED, ERROR, UNKNOWN
    val samplesDiscarded: Long = 0L,
    val finalBufferFlushed: Boolean = false,
    val submittedToInference: Boolean = false,
    // Real Audio Signal Statistics
    val rmsLevel: Float = 0f,
    val peakAmplitude: Float = 0f,
    val minSampleValue: Float = 0f,
    val maxSampleValue: Float = 0f,
    val nearZeroSamplesPercentage: Float = 0f,
    val clippedSamplesPercentage: Float = 0f,
    val estimatedSilencePercentage: Float = 0f,
    val estimatedSpeechDurationMs: Long = 0L,
    // Failure stage diagnosis
    val failureCause: AudioFailureCause = AudioFailureCause.NONE,
    val failureDetails: String = ""
)

/**
 * Live Speech Model and Whisper JNI Bridge telemetry.
 */
data class SpeechModelTelemetry(
    val engineImplementation: String = "WhisperCppSpeechRecognitionEngine (Native GGML)",
    val nativeLibraryLoaded: Boolean = false,
    val nativeInitError: String? = null,
    val modelFilename: String = "ggml-tiny.en-q5_1.bin",
    val modelSizeBytes: Long = 31200000L,
    val modelFileExists: Boolean = true,
    val modelFileReadable: Boolean = true,
    val modelState: String = "UNINITIALIZED",
    val modelLoadDurationMs: Long = 0L,
    val quantization: String = "Q5_1 (5-bit quantized)",
    val configuredThreadCount: Int = 2,
    val inferenceParameters: String = "beam_size=1, greedy=true, temp=0.0, no_context=true",
    val languageDetectionMode: String = "Manual",
    val requestedLanguageCode: String = "en",
    val detectedLanguageCode: String = "en",
    val isAutoLanguageDetection: Boolean = false,
    val isTranslationEnabled: Boolean = false, // Always false
    val decodingDurationMs: Long = 0L,
    val preprocessingDurationMs: Long = 0L,
    val totalEndToEndDurationMs: Long = 0L,
    val currentJobId: String = "",
    val queueSize: Int = 0,
    val activeInferenceOwner: String = "NONE", // DIAGNOSTIC, FLOATING_BOT, TEST_LAB, NONE
    val cancellationState: String = "NONE", // NONE, PENDING, COMPLETED
    val nativeError: String? = null,
    val lastSessionId: String = "",
    val lastAudioSampleCount: Int = 0,
    val lastAudioDurationMs: Long = 0L,
    val sampleFormat: String = "16kHz Mono 32-bit Float",
    val lastRms: Float = 0f,
    val lastPeak: Float = 0f,
    val preprocessingSuccess: Boolean = true,
    val bufferQuality: String = "VALID", // VALID, EMPTY, TRUNCATED, SILENT
    val inferenceStarted: Boolean = false,
    val inferenceCompleted: Boolean = false,
    val lastReturnedTranscript: String = ""
)

/**
 * Long-Speech and Endpointing telemetry.
 */
data class LongSpeechTelemetry(
    val recordingStartNanos: Long = 0L,
    val recordingStopNanos: Long = 0L,
    val totalDurationMs: Long = 0L,
    val finalBufferDurationMs: Long = 0L,
    val buffersCollected: Int = 0,
    val finalBufferFlushed: Boolean = true,
    val isTruncatedByLimit: Boolean = false,
    val configuredMaxRecordingMs: Long = 10000L, // 10s ceiling in AudioCaptureWorker
    val configuredEndpointSilenceMs: Long = 1200L, // 1200ms EN / 1600ms HI
    val lastNonSilentTimeMs: Long = 0L,
    val stoppedBySilenceDetection: Boolean = false,
    val stoppedByWordPause: Boolean = false,
    val bufferClearedPrematurely: Boolean = false,
    val priorJobPreventedRecording: Boolean = false,
    val interruptedByTimeoutOrCancel: Boolean = false,
    val transcriptionReceivedFullBuffer: Boolean = true,
    val stageTimeline: List<TimelineStage> = emptyList()
)

data class TimelineStage(
    val stageName: String,
    val timestamp: Long = System.currentTimeMillis(),
    val timestampFormatted: String = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(timestamp)),
    val durationMs: Long? = null,
    val status: String = "SUCCESS", // SUCCESS, FAILED, SKIPPED, PENDING
    val details: String = ""
)

/**
 * Floating Bot Pipeline Telemetry.
 */
data class FloatingBotPipelineTelemetry(
    val sessionId: String = "",
    val botActivated: Boolean = false,
    val botOverlayVisible: Boolean = false,
    val isListening: Boolean = false,
    val currentStage: String = "IDLE",
    val stages: List<PipelineStageRecord> = emptyList(),
    val failureStage: String? = null,
    val failureReason: String? = null,
    // Matrix of shared implementation checks
    val sharedRecorderImplementation: Boolean = true,
    val sharedAudioSource: Boolean = true,
    val sharedPcmFormat: Boolean = true,
    val sharedPreprocessing: Boolean = true,
    val sharedModelInstance: Boolean = true,
    val sharedTranscriptionEntryPoint: Boolean = true,
    val sharedInferenceLock: Boolean = true,
    val sharedLanguageConfig: Boolean = true
)

data class PipelineStageRecord(
    val stageIndex: Int,
    val stageName: String,
    val timestamp: Long = System.currentTimeMillis(),
    val timestampFormatted: String = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(timestamp)),
    val durationMs: Long? = null,
    val status: String = "SUCCESS", // SUCCESS, EMPTY_TRANSCRIPT, FAILED, SKIPPED, PENDING, CANCELLED
    val details: String = "",
    val sessionId: String = "",
    val jobId: String = ""
)

/**
 * Command and Workflow Pipeline Telemetry.
 */
data class CommandPipelineTelemetry(
    val exactRecognizedText: String = "",
    val normalizedText: String = "",
    val resolverLanguageMode: String = "ENGLISH",
    val resolverEntryTimeFormatted: String = "",
    val resolverResult: String = "",
    val matchedCommandOrWorkflowId: String = "",
    val noMatchReason: String? = null,
    val isConfirmationRequired: Boolean = false,
    val isConfirmationDisplayed: Boolean = false,
    val userConfirmationAction: String = "NONE", // NONE, CONFIRMED, CANCELLED
    val accessibilityReady: Boolean = false,
    val workflowExecutionStatus: String = "IDLE", // IDLE, RUNNING, COMPLETED, FAILED
    val failedWorkflowStep: Int? = null,
    val workflowError: String? = null
)

/**
 * Test Lab Run State.
 */
data class TestLabRunState(
    val testId: String = "",
    val languageMode: VoiceLanguageMode = VoiceLanguageMode.ENGLISH,
    val callerLabel: String = "DIAGNOSTIC_PATH", // DIAGNOSTIC_PATH vs FLOATING_BOT_PATH
    val expectedText: String = "",
    val promptPhrase: String = "",
    val isRecording: Boolean = false,
    val isTranscribing: Boolean = false,
    val recordingDurationMs: Long = 0L,
    val rmsLevel: Float = 0f,
    val peakAmplitude: Float = 0f,
    val silencePercentage: Float = 0f,
    val recognizedText: String = "",
    val detectedLanguage: String = "",
    val inferenceTimeMs: Long = 0L,
    val totalTimeMs: Long = 0L,
    val outcomeClassification: TestOutcomeClassification = TestOutcomeClassification.INCONCLUSIVE,
    val errorDetails: String? = null,
    val isOutputTruncatedOrEmpty: Boolean = false,
    val lifecycleStage: String = "IDLE", // IDLE, RECORDING, FINALIZING, READY_TO_TRANSCRIBE, TRANSCRIBING, RESULT, CANCELLED, FAILED, PERMISSION_DENIED
    val capturedSampleCount: Int = 0
)

/**
 * Central singleton for telemetry, event recording, and diagnostic observation.
 * Follows the strict rule: OBSERVABILITY ONLY, NO SILENT REWRITES.
 */
class VoiceBotDiagnosticTracker private constructor(private val context: Context) {

    companion object {
        const val MAX_EVENT_LOG_SIZE = 250

        @Volatile
        private var instance: VoiceBotDiagnosticTracker? = null

        fun getInstance(context: Context? = null): VoiceBotDiagnosticTracker {
            return instance ?: synchronized(this) {
                instance ?: run {
                    val ctx = context?.applicationContext
                        ?: com.taskscheduling.app.accessibility.TaskSchedulingAccessibilityService.instance?.applicationContext
                        ?: throw IllegalStateException("VoiceBotDiagnosticTracker requires a Context for initialization")
                    VoiceBotDiagnosticTracker(ctx).also {
                        instance = it
                    }
                }
            }
        }

        fun getInstanceOrNull(): VoiceBotDiagnosticTracker? = instance
    }

    private val eventQueue = ConcurrentLinkedDeque<DiagnosticEvent>()

    private val _systemOverview = MutableStateFlow(SystemOverviewState())
    val systemOverview: StateFlow<SystemOverviewState> = _systemOverview.asStateFlow()

    private val _audioCapture = MutableStateFlow(AudioCaptureTelemetry())
    val audioCapture: StateFlow<AudioCaptureTelemetry> = _audioCapture.asStateFlow()

    private val _speechModel = MutableStateFlow(SpeechModelTelemetry())
    val speechModel: StateFlow<SpeechModelTelemetry> = _speechModel.asStateFlow()

    private val _longSpeech = MutableStateFlow(LongSpeechTelemetry())
    val longSpeech: StateFlow<LongSpeechTelemetry> = _longSpeech.asStateFlow()

    private val _floatingBotPipeline = MutableStateFlow(FloatingBotPipelineTelemetry())
    val floatingBotPipeline: StateFlow<FloatingBotPipelineTelemetry> = _floatingBotPipeline.asStateFlow()

    private val _commandPipeline = MutableStateFlow(CommandPipelineTelemetry())
    val commandPipeline: StateFlow<CommandPipelineTelemetry> = _commandPipeline.asStateFlow()

    private val _testLabState = MutableStateFlow(TestLabRunState())
    val testLabState: StateFlow<TestLabRunState> = _testLabState.asStateFlow()

    private val _eventLogs = MutableStateFlow<List<DiagnosticEvent>>(emptyList())
    val eventLogs: StateFlow<List<DiagnosticEvent>> = _eventLogs.asStateFlow()

    // Active session and job tracking
    private var currentSessionId: String = "sess_init"
    private var currentBotJobId: String = ""

    init {
        logEvent(
            severity = DiagnosticSeverity.INFO,
            component = DiagnosticComponent.MODEL_LOADING,
            eventName = "TRACKER_INITIALIZED",
            description = "Voice & Bot Diagnostic Center tracker initialized."
        )
    }

    /**
     * Bounded log append with automatic UI StateFlow refresh.
     */
    fun logEvent(
        severity: DiagnosticSeverity = DiagnosticSeverity.INFO,
        component: DiagnosticComponent,
        eventName: String,
        description: String,
        errorCode: String? = null,
        metadata: Map<String, String> = emptyMap(),
        suggestedCheck: String? = null,
        sessionId: String = currentSessionId,
        jobId: String = currentBotJobId
    ) {
        val event = DiagnosticEvent(
            severity = severity,
            component = component,
            sessionId = sessionId,
            jobId = jobId,
            eventName = eventName,
            description = description,
            errorCode = errorCode,
            metadata = metadata,
            suggestedCheck = suggestedCheck
        )
        eventQueue.addFirst(event)
        while (eventQueue.size > MAX_EVENT_LOG_SIZE) {
            eventQueue.pollLast()
        }
        _eventLogs.value = eventQueue.toList()
    }

    fun clearEventLogs(recordClearEvent: Boolean = true) {
        eventQueue.clear()
        _eventLogs.value = emptyList()
        if (recordClearEvent) {
            logEvent(
                severity = DiagnosticSeverity.INFO,
                component = DiagnosticComponent.MODEL_LOADING,
                eventName = "LOGS_CLEARED",
                description = "Diagnostic event log was cleared by user."
            )
        }
    }

    /**
     * Records audio statistics computed from raw float samples.
     */
    fun recordCapturedAudioStats(
        samples: FloatArray,
        source: String = "DEFAULT",
        isSubmitted: Boolean = true,
        rejectionReason: String? = null,
        owner: String = "FLOATING_BOT"
    ) {
        if (samples.isEmpty()) {
            _audioCapture.value = _audioCapture.value.copy(
                totalSamplesCaptured = 0L,
                totalCapturedBytes = 0L,
                actualRecordingDurationMs = 0L,
                bufferStatus = "EMPTY",
                failureCause = AudioFailureCause.BUFFER_EMPTY,
                failureDetails = "Captured audio buffer has 0 samples."
            )
            logEvent(
                severity = DiagnosticSeverity.WARNING,
                component = DiagnosticComponent.AUDIO_CAPTURE,
                eventName = "EMPTY_AUDIO_BUFFER",
                description = "Audio buffer has 0 samples.",
                suggestedCheck = "Check AudioRecord read loop and microphone permission."
            )
            return
        }

        var minVal = Float.MAX_VALUE
        var maxVal = -Float.MAX_VALUE
        var sumSquares = 0.0
        var peak = 0f
        var nearZeroCount = 0
        var clippedCount = 0
        var silentCount = 0

        for (s in samples) {
            val a = kotlin.math.abs(s)
            if (s < minVal) minVal = s
            if (s > maxVal) maxVal = s
            if (a > peak) peak = a
            sumSquares += (s * s).toDouble()
            if (a < 0.001f) nearZeroCount++
            if (a >= 0.99f) clippedCount++
            if (a < 0.005f) silentCount++
        }

        val n = samples.size
        val rms = kotlin.math.sqrt(sumSquares / n).toFloat()
        val nearZeroPct = (nearZeroCount.toFloat() / n.toFloat()) * 100f
        val clippedPct = (clippedCount.toFloat() / n.toFloat()) * 100f
        val silentPct = (silentCount.toFloat() / n.toFloat()) * 100f
        val durationMs = (n * 1000L) / 16000L
        val speechDurationMs = ((n - silentCount) * 1000L) / 16000L

        val failureCause = when {
            samples.size < 3200 -> AudioFailureCause.AUDIO_TRUNCATED
            peak <= 0.005f -> AudioFailureCause.AUDIO_NEARLY_SILENT
            rejectionReason != null -> AudioFailureCause.REJECTED_BY_VAD
            else -> AudioFailureCause.NONE
        }

        _audioCapture.value = _audioCapture.value.copy(
            requestedSource = source,
            actualSource = source,
            totalSamplesCaptured = n.toLong(),
            totalCapturedBytes = (n * 2).toLong(),
            actualRecordingDurationMs = durationMs,
            effectiveAudioDurationMs = speechDurationMs,
            rmsLevel = rms,
            peakAmplitude = peak,
            minSampleValue = minVal,
            maxSampleValue = maxVal,
            nearZeroSamplesPercentage = nearZeroPct,
            clippedSamplesPercentage = clippedPct,
            estimatedSilencePercentage = silentPct,
            estimatedSpeechDurationMs = speechDurationMs,
            bufferStatus = if (isSubmitted) "SUBMITTED" else "REJECTED ($rejectionReason)",
            failureCause = failureCause,
            failureDetails = rejectionReason ?: if (failureCause != AudioFailureCause.NONE) failureCause.explanation else ""
        )

        _systemOverview.value = _systemOverview.value.copy(
            hasMeasurableAudio = peak > 0.005f,
            currentRecordingDurationMs = durationMs
        )

        logEvent(
            severity = if (failureCause != AudioFailureCause.NONE) DiagnosticSeverity.WARNING else DiagnosticSeverity.INFO,
            component = DiagnosticComponent.AUDIO_CAPTURE,
            eventName = "AUDIO_STATS_COMPUTED",
            description = "Captured $n samples (${durationMs}ms) peak=${"%.4f".format(peak)} rms=${"%.4f".format(rms)} silent=${"%.1f".format(silentPct)}% status=${if (isSubmitted) "SUBMITTED" else "REJECTED"}",
            metadata = mapOf(
                "samples" to "$n",
                "durationMs" to "$durationMs",
                "peak" to "%.4f".format(peak),
                "rms" to "%.4f".format(rms),
                "rejection" to (rejectionReason ?: "None")
            ),
            suggestedCheck = if (peak <= 0.005f) "Increase microphone gain or speak closer to the tablet mic." else null
        )
    }

    /**
     * Updates model telemetry when loaded or rechecked.
     */
    fun updateModelTelemetry(
        modelName: String,
        modelPath: String,
        sizeBytes: Long,
        isLoaded: Boolean,
        state: WhisperEngineState,
        loadTimeMs: Long = 0L,
        threads: Int = 2,
        language: VoiceLanguageMode = VoiceLanguageMode.ENGLISH,
        error: String? = null
    ) {
        _speechModel.value = _speechModel.value.copy(
            modelFilename = modelName,
            modelSizeBytes = sizeBytes,
            modelFileExists = true,
            modelFileReadable = true,
            modelState = state.name,
            modelLoadDurationMs = loadTimeMs,
            configuredThreadCount = threads,
            requestedLanguageCode = language.code,
            isAutoLanguageDetection = language == VoiceLanguageMode.HINGLISH,
            nativeLibraryLoaded = true,
            nativeError = error
        )

        _systemOverview.value = _systemOverview.value.copy(
            loadedModelName = modelName,
            loadedModelPath = modelPath,
            isModelLoaded = isLoaded,
            selectedLanguage = "${language.displayName} (${language.code})",
            lastCheckedTimestamp = System.currentTimeMillis()
        )
    }

    /**
     * Updates live floating bot pipeline stage.
     */
    fun recordBotPipelineStage(
        stageName: String,
        status: String = "SUCCESS",
        details: String = "",
        durationMs: Long? = null,
        sessionId: String = currentSessionId,
        jobId: String = currentBotJobId
    ) {
        val currentStages = _floatingBotPipeline.value.stages.toMutableList()
        val existingIndex = currentStages.indexOfFirst { it.stageName == stageName }
        val nextIdx = if (existingIndex >= 0) currentStages[existingIndex].stageIndex else (currentStages.size + 1)
        val stageRecord = PipelineStageRecord(
            stageIndex = nextIdx,
            stageName = stageName,
            durationMs = durationMs,
            status = status,
            details = details,
            sessionId = sessionId,
            jobId = jobId
        )
        if (existingIndex >= 0) {
            currentStages[existingIndex] = stageRecord
        } else {
            currentStages.add(stageRecord)
        }

        _floatingBotPipeline.value = _floatingBotPipeline.value.copy(
            currentStage = stageName,
            stages = currentStages,
            failureStage = if (status == "FAILED" || status == "EMPTY_TRANSCRIPT") stageName else _floatingBotPipeline.value.failureStage,
            failureReason = if (status == "FAILED" || status == "EMPTY_TRANSCRIPT") details else _floatingBotPipeline.value.failureReason
        )

        logEvent(
            severity = if (status == "FAILED") DiagnosticSeverity.ERROR else if (status == "EMPTY_TRANSCRIPT") DiagnosticSeverity.WARNING else DiagnosticSeverity.INFO,
            component = DiagnosticComponent.FLOATING_BOT,
            eventName = "PIPELINE_STAGE_$stageName",
            description = "Stage $nextIdx: $stageName -> $status. $details",
            metadata = mapOf(
                "stage" to stageName,
                "status" to status,
                "durationMs" to (durationMs?.toString() ?: "N/A"),
                "jobId" to jobId
            ),
            sessionId = sessionId,
            jobId = jobId
        )
    }

    /**
     * Resets the floating bot pipeline for a fresh listening attempt.
     */
    fun startNewBotSession(sessionId: String = "bot_sess_${System.currentTimeMillis()}") {
        currentSessionId = sessionId
        currentBotJobId = ""
        _floatingBotPipeline.value = FloatingBotPipelineTelemetry(
            sessionId = sessionId,
            botActivated = true,
            botOverlayVisible = true,
            isListening = true,
            currentStage = "Bot Activated"
        )
        recordBotPipelineStage(
            stageName = "Bot Activated",
            status = "SUCCESS",
            details = "User activated listening control.",
            sessionId = sessionId
        )
    }

    /**
     * Records exact audio capture session lifecycle metrics.
     */
    fun recordAudioSessionLifecycle(
        startTimeMs: Long,
        stopTimeMs: Long,
        actualElapsedMs: Long,
        successfulReads: Long,
        samplesPerRead: Int,
        bytesPerRead: Int,
        totalSamplesCaptured: Long,
        totalBytesCaptured: Long,
        finalizedAudioDurationMs: Long,
        stopReason: String,
        samplesDiscarded: Long = 0L,
        finalBufferFlushed: Boolean = true,
        submittedToInference: Boolean = true
    ) {
        _audioCapture.value = _audioCapture.value.copy(
            recordingStartTimeMs = startTimeMs,
            recordingStopTimeMs = stopTimeMs,
            actualRecordingDurationMs = actualElapsedMs,
            effectiveAudioDurationMs = finalizedAudioDurationMs,
            framesReceivedCount = successfulReads,
            samplesPerRead = samplesPerRead,
            bytesPerRead = bytesPerRead,
            totalSamplesCaptured = totalSamplesCaptured,
            totalCapturedBytes = totalBytesCaptured,
            stopReason = stopReason,
            samplesDiscarded = samplesDiscarded,
            finalBufferFlushed = finalBufferFlushed,
            submittedToInference = submittedToInference,
            stoppedNormally = stopReason == "SILENCE_ENDPOINT" || stopReason == "USER_STOP" || stopReason == "DURATION_LIMIT"
        )
        logEvent(
            severity = DiagnosticSeverity.INFO,
            component = DiagnosticComponent.AUDIO_CAPTURE,
            eventName = "AUDIO_SESSION_LIFECYCLE",
            description = "Audio session: elapsed=${actualElapsedMs}ms, finalized=${finalizedAudioDurationMs}ms, samples=$totalSamplesCaptured, reads=$successfulReads, stopReason=$stopReason, submitted=$submittedToInference",
            metadata = mapOf(
                "startMs" to "$startTimeMs",
                "stopMs" to "$stopTimeMs",
                "elapsedMs" to "$actualElapsedMs",
                "finalDurationMs" to "$finalizedAudioDurationMs",
                "reads" to "$successfulReads",
                "totalSamples" to "$totalSamplesCaptured",
                "stopReason" to stopReason,
                "submitted" to "$submittedToInference"
            )
        )
    }

    /**
     * Records audio verification at the inference entry point.
     */
    fun recordInferenceEntryPoint(
        sessionId: String,
        jobId: String,
        sampleCount: Int,
        durationMs: Long,
        sampleFormat: String = "16kHz Mono 32-bit Float",
        rms: Float,
        peak: Float,
        bufferQuality: String,
        preprocessingSuccess: Boolean
    ) {
        _speechModel.value = _speechModel.value.copy(
            currentJobId = jobId,
            lastSessionId = sessionId,
            lastAudioSampleCount = sampleCount,
            lastAudioDurationMs = durationMs,
            sampleFormat = sampleFormat,
            lastRms = rms,
            lastPeak = peak,
            bufferQuality = bufferQuality,
            preprocessingSuccess = preprocessingSuccess,
            inferenceStarted = false,
            inferenceCompleted = false
        )
        logEvent(
            severity = DiagnosticSeverity.INFO,
            component = DiagnosticComponent.WHISPER_INFERENCE,
            eventName = "INFERENCE_ENTRY_POINT",
            description = "Inference entry: job=$jobId samples=$sampleCount (${durationMs}ms) rms=${"%.4f".format(rms)} peak=${"%.4f".format(peak)} quality=$bufferQuality preprocOk=$preprocessingSuccess",
            sessionId = sessionId,
            jobId = jobId
        )
    }

    /**
     * Records inference execution outcome.
     */
    fun recordInferenceExecution(
        jobId: String,
        started: Boolean,
        completed: Boolean,
        decodingMs: Long,
        transcript: String,
        nativeError: String? = null
    ) {
        _speechModel.value = _speechModel.value.copy(
            inferenceStarted = started,
            inferenceCompleted = completed,
            decodingDurationMs = decodingMs,
            lastReturnedTranscript = transcript,
            nativeError = nativeError
        )
        logEvent(
            severity = if (nativeError != null) DiagnosticSeverity.ERROR else DiagnosticSeverity.INFO,
            component = DiagnosticComponent.WHISPER_INFERENCE,
            eventName = "INFERENCE_EXECUTION_RESULT",
            description = "Inference for $jobId: started=$started, completed=$completed, time=${decodingMs}ms, transcript=\"$transcript\"",
            jobId = jobId
        )
    }

    /**
     * Records long-speech and endpointing information.
     */
    fun recordLongSpeechTelemetry(
        totalDurationMs: Long,
        finalBufferDurationMs: Long = 0L,
        buffersCount: Int,
        isTruncatedByLimit: Boolean,
        stoppedBySilence: Boolean,
        stoppedByWordPause: Boolean,
        endpointSilenceMs: Long,
        timeline: List<TimelineStage>
    ) {
        _longSpeech.value = _longSpeech.value.copy(
            totalDurationMs = totalDurationMs,
            finalBufferDurationMs = finalBufferDurationMs,
            buffersCollected = buffersCount,
            isTruncatedByLimit = isTruncatedByLimit,
            stoppedBySilenceDetection = stoppedBySilence,
            stoppedByWordPause = stoppedByWordPause,
            configuredEndpointSilenceMs = endpointSilenceMs,
            stageTimeline = timeline
        )

        logEvent(
            severity = if (isTruncatedByLimit) DiagnosticSeverity.WARNING else DiagnosticSeverity.INFO,
            component = DiagnosticComponent.ENDPOINTING,
            eventName = "ENDPOINT_RECORDED",
            description = "Speech stopped: ${totalDurationMs}ms (audio: ${finalBufferDurationMs}ms) in $buffersCount buffers. SilencePause=$stoppedByWordPause Capped10s=$isTruncatedByLimit",
            suggestedCheck = if (isTruncatedByLimit) "Long utterance exceeded 10-second buffer cap." else if (stoppedByWordPause) "Pause between words exceeded ${endpointSilenceMs}ms timeout." else null
        )
    }

    /**
     * Records command resolution and workflow safety pipeline.
     */
    fun recordCommandResolution(
        rawText: String,
        normalizedText: String,
        language: String,
        resultIntent: String,
        isConfirmationRequired: Boolean,
        workflowId: String = ""
    ) {
        val nowFormatted = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
        _commandPipeline.value = _commandPipeline.value.copy(
            exactRecognizedText = rawText,
            normalizedText = normalizedText,
            resolverLanguageMode = language,
            resolverEntryTimeFormatted = nowFormatted,
            resolverResult = resultIntent,
            matchedCommandOrWorkflowId = if (workflowId.isNotBlank()) workflowId else resultIntent,
            isConfirmationRequired = isConfirmationRequired,
            isConfirmationDisplayed = isConfirmationRequired,
            userConfirmationAction = if (isConfirmationRequired) "AWAITING CONFIRMATION" else "NONE"
        )

        _systemOverview.value = _systemOverview.value.copy(
            lastRecognitionResult = rawText,
            commandProcessingState = if (isConfirmationRequired) "AWAITING_CONFIRMATION" else "RESOLVED"
        )

        logEvent(
            severity = DiagnosticSeverity.INFO,
            component = DiagnosticComponent.COMMAND_RESOLVER,
            eventName = "COMMAND_RESOLVED",
            description = "Resolved \"$rawText\" -> $resultIntent (Requires confirmation: $isConfirmationRequired)",
            metadata = mapOf("raw" to rawText, "intent" to resultIntent, "confirm" to "$isConfirmationRequired")
        )
    }

    /**
     * Updates test lab state for English, Hindi, and Hinglish tests.
     */
    fun updateTestLabState(update: TestLabRunState) {
        _testLabState.value = update
    }

    /**
     * Refreshes live system state from active Android environment.
     */
    fun refreshSystemStatus(
        isMicPermissionGranted: Boolean,
        isAccessibilityServiceEnabled: Boolean,
        isAccessibilityServiceConnected: Boolean,
        botOverlayVisible: Boolean,
        botIsListening: Boolean
    ) {
        _systemOverview.value = _systemOverview.value.copy(
            micPermissionGranted = isMicPermissionGranted,
            isAccessibilityEnabled = isAccessibilityServiceEnabled,
            isAccessibilityConnected = isAccessibilityServiceConnected,
            floatingBotState = if (botIsListening) "LISTENING" else if (botOverlayVisible) "VISIBLE" else "HIDDEN",
            lastCheckedTimestamp = System.currentTimeMillis(),
            isStale = false
        )
    }
}
