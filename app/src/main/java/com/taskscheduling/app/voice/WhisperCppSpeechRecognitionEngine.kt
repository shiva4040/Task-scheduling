package com.taskscheduling.app.voice

import android.Manifest
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Explicit states representing the Whisper speech recognition engine lifecycle.
 */
enum class WhisperEngineState {
    UNINITIALIZED,
    IDLE,
    LOADING,
    READY,
    RUNNING,
    LISTENING,
    SPEECH_DETECTED,
    CAPTURING,
    TRANSCRIBING,
    RESULT,
    EMPTY_RESULT,
    STOPPING,
    RELEASED,
    ERROR
}

/**
 * Primary offline speech recognition engine powered by configurable multilingual OpenAI Whisper GGML models.
 *
 * Guarantees:
 * 1. Hard Safety Boundary: Native Whisper is isolated; no JNI/OOM fault can crash the Android process.
 * 2. Stage-by-Stage Precision Telemetry: Measures capture, audioPrep, queueWait, nativeMutex,
 *    nativeInference, resultProcessing, and uiDelivery using System.nanoTime().
 * 3. Pre-flight RAM Protection: Measures available RAM before native allocation to prevent kernel SIGKILL.
 * 4. Model Reuse: Loads model once into native context and reuses across consecutive utterances without reloads.
 * 5. Exactly 3 Language Modes: English ("en"), Hindi ("hi"), Hinglish ("auto"), with translate=false.
 * 6. Thread-Optimized: Calibrated for ARM big cores (2 threads on Helio G99 Cortex-A76).
 */
open class WhisperCppSpeechRecognitionEngine(
    private val context: Context,
    val modelManager: WhisperModelManager = WhisperModelManager(context),
    val nativeBridge: WhisperNativeBridge = WhisperNativeBridge.getInstance(context)
) : SpeechRecognitionEngine, OfflineSpeechEngine {

    companion object {
        const val TAG = "WhisperCppEngine"
        const val CRASH_TAG = "TaskScheduling-CRASH"
        const val SAMPLE_RATE = 16000
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        const val SAMPLES_PER_READ = 1024
        const val STALL_TIMEOUT_MS = 15_000L // 15s bounded watchdog (3x normal inference)
        private val sessionCounter = AtomicInteger(200)
        private val utteranceCounter = AtomicInteger(1000)

        @Volatile
        private var sharedEngine: WhisperCppSpeechRecognitionEngine? = null
        private val singletonLock = Any()

        /**
         * Process-wide singleton provider ensuring only ONE Whisper model instance exists in RAM.
         */
        fun getInstance(context: Context): WhisperCppSpeechRecognitionEngine {
            return sharedEngine ?: synchronized(singletonLock) {
                sharedEngine ?: WhisperCppSpeechRecognitionEngine(context.applicationContext).also {
                    sharedEngine = it
                }
            }
        }
    }

    private val initMutex = Mutex()
    private val inferenceMutex = Mutex()
    enum class InferenceJobState { QUEUED, DECODING, SUCCEEDED, FAILED, CANCELLED }

    data class InferenceJob(
        val id: String,
        val source: String,
        val languageMode: LanguageMode,
        val submissionNanos: Long = System.nanoTime(),
        val cancellationSignal: AtomicBoolean = AtomicBoolean(false)
    ) {
        @Volatile var state: InferenceJobState = InferenceJobState.QUEUED
    }

    private val inferenceLock = Any()
    @Volatile
    private var currentInferenceJob: InferenceJob? = null

    private val workerLock = Any()
    private var activeWorker: AudioCaptureWorker? = null

    private val transcriptionLock = Any()
    private var transcriptionJob = SupervisorJob()
    private var transcriptionScope = CoroutineScope(transcriptionJob + Dispatchers.Default)
    private var activeTranscriptionJob: Job? = null
    @Volatile
    private var activeUtteranceId: String? = null
    private var lastJobIsFinal: Boolean = false
    private val pendingUtterancesCount = AtomicInteger(0)

    private fun dispatchToMain(action: () -> Unit) {
        try {
            val looper = Looper.getMainLooper()
            if (looper != null && Looper.myLooper() != looper) {
                Handler(looper).post(action)
                return
            }
        } catch (_: Throwable) {}
        action()
    }

    private val _engineState = MutableStateFlow(WhisperEngineState.UNINITIALIZED)
    val engineState: StateFlow<WhisperEngineState> = _engineState.asStateFlow()

    private val _lastErrorMessage = MutableStateFlow<String?>(null)
    val lastErrorMessage: StateFlow<String?> = _lastErrorMessage.asStateFlow()
    var languageMode: LanguageMode = LanguageMode.ENGLISH
    var preferredAudioSource: String = "DEFAULT"
    var enablePreprocessing: Boolean = false
    var customEndpointSilenceMs: Long? = null

    private val _performanceMetrics = MutableStateFlow(WhisperPerformanceMetrics())
    val performanceMetrics: StateFlow<WhisperPerformanceMetrics> = _performanceMetrics.asStateFlow()

    var modelLoadTimeMs: Long = 0L

    override val engineType: SpeechEngineType
        get() = SpeechEngineType.WHISPER_OFFLINE

    override val isModelLoaded: Boolean
        get() = (_engineState.value == WhisperEngineState.READY ||
                _engineState.value == WhisperEngineState.RUNNING ||
                _engineState.value == WhisperEngineState.LISTENING ||
                _engineState.value == WhisperEngineState.SPEECH_DETECTED ||
                _engineState.value == WhisperEngineState.CAPTURING ||
                _engineState.value == WhisperEngineState.TRANSCRIBING ||
                _engineState.value == WhisperEngineState.RESULT ||
                _engineState.value == WhisperEngineState.EMPTY_RESULT ||
                _engineState.value == WhisperEngineState.STOPPING) &&
                nativeBridge.isReady

    override val isRunning: Boolean
        get() = _engineState.value == WhisperEngineState.RUNNING ||
                _engineState.value == WhisperEngineState.LISTENING ||
                _engineState.value == WhisperEngineState.SPEECH_DETECTED ||
                _engineState.value == WhisperEngineState.CAPTURING ||
                _engineState.value == WhisperEngineState.TRANSCRIBING

    override val configuredSampleRate: Int
        get() = SAMPLE_RATE

    override val activeAudioSource: String
        get() = synchronized(workerLock) { activeWorker?.activeAudioSource ?: preferredAudioSource }

    override val configuredBufferSize: Int
        get() = synchronized(workerLock) { activeWorker?.bufferSize ?: (SAMPLES_PER_READ * 2) }

    @Volatile
    override var isMuted: Boolean = false

    val isTranscribing: Boolean
        get() {
            val directJob = synchronized(inferenceLock) { currentInferenceJob }
            val directActive = directJob != null && (directJob.state == InferenceJobState.DECODING || directJob.state == InferenceJobState.QUEUED) && !directJob.cancellationSignal.get()
            val jobActive = synchronized(transcriptionLock) { activeTranscriptionJob?.isActive == true }
            val nativeActive = nativeBridge.isInferenceActive
            val stateTranscribing = _engineState.value == WhisperEngineState.TRANSCRIBING

            if (!jobActive && !directActive && !nativeActive && stateTranscribing) {
                Log.w(CRASH_TAG, "[Whisper] Stale TRANSCRIBING state detected without active job or native inference. Resetting to READY.")
                val isRec = synchronized(workerLock) { activeWorker?.isCapturing == true }
                _engineState.value = if (isRec) WhisperEngineState.CAPTURING else if (nativeBridge.isReady) WhisperEngineState.READY else WhisperEngineState.UNINITIALIZED
                pendingUtterancesCount.set(0)
                return false
            }
            return nativeActive || jobActive || directActive || stateTranscribing
        }

    fun getOrCreateTranscriptionScope(): CoroutineScope {
        synchronized(transcriptionLock) {
            if (!transcriptionJob.isActive) {
                transcriptionJob = SupervisorJob()
                transcriptionScope = CoroutineScope(transcriptionJob + Dispatchers.Default)
            }
            return transcriptionScope
        }
    }

    /**
     * Cancels any active or queued transcription job.
     * Restores engine state to READY or CAPTURING immediately.
     */
    fun cancelActiveTranscription(jobId: String? = null) {
        synchronized(inferenceLock) {
            val job = currentInferenceJob
            if (job != null && (jobId == null || job.id == jobId)) {
                job.cancellationSignal.set(true)
                job.state = InferenceJobState.CANCELLED
                currentInferenceJob = null
                Log.i(CRASH_TAG, "[Whisper] Cancelled active inference job: ${job.id}")
            }
        }
        synchronized(transcriptionLock) {
            activeTranscriptionJob?.cancel()
            activeTranscriptionJob = null
            activeUtteranceId = null
            pendingUtterancesCount.set(0)
        }
        if (inferenceMutex.isLocked) {
            try { inferenceMutex.unlock() } catch (_: Throwable) {}
        }
        if (_engineState.value == WhisperEngineState.TRANSCRIBING) {
            val isRec = synchronized(workerLock) { activeWorker?.isCapturing == true }
            _engineState.value = if (isRec) WhisperEngineState.CAPTURING else if (nativeBridge.isReady) WhisperEngineState.READY else WhisperEngineState.UNINITIALIZED
        }
    }

    /**
     * Rejects empty, silent, or background-noise-only buffers before native inference.
     * Genuine speech requires at least 200ms (3200 samples) and audible peak above ambient noise floor (0.005f).
     */
    fun hasAudibleSpeech(samples: FloatArray): Boolean {
        if (samples.size < 3200) return false
        var peak = 0f
        for (s in samples) {
            val a = kotlin.math.abs(s)
            if (a > peak) peak = a
        }
        return peak > 0.005f
    }

    /**
     * Normalizes audio amplitude, gates trailing silence to prevent Whisper hallucinations,
     * and ensures minimum length padding for Whisper while strictly preserving all speech.
     */
    fun prepareAudioForWhisper(rawFloats: FloatArray): FloatArray {
        val t0 = System.currentTimeMillis()
        if (rawFloats.isEmpty()) return rawFloats

        var peak = 0f
        for (f in rawFloats) {
            val a = kotlin.math.abs(f)
            if (a > peak) peak = a
        }

        val gain = when {
            peak < 0.005f -> 1.0f // pure noise, don't amplify
            peak >= 0.6f -> 1.0f  // already healthy level
            else -> (0.6f / peak).coerceIn(1.0f, 6.0f) // boost quiet audio up to 6x
        }

        // Whisper needs at least 1.0s of audio for reliable attention decoding
        val targetLength = maxOf(rawFloats.size, SAMPLE_RATE)
        val processed = FloatArray(targetLength)

        // Find last active speech boundary using 20ms frames (320 samples @ 16kHz)
        // to prevent trailing silence/room noise from being amplified into hallucinated repeat loops.
        val frameSize = 320
        val speechThreshold = maxOf(0.005f, peak * 0.10f)
        var lastSpeechSample = 0

        var frameStart = 0
        while (frameStart < rawFloats.size) {
            val frameEnd = minOf(rawFloats.size, frameStart + frameSize)
            var sumSq = 0.0
            for (j in frameStart until frameEnd) {
                val sample = rawFloats[j]
                sumSq += (sample * sample).toDouble()
            }
            val frameRms = kotlin.math.sqrt(sumSq / (frameEnd - frameStart)).toFloat()
            if (frameRms >= speechThreshold) {
                lastSpeechSample = frameEnd
            }
            frameStart += frameSize
        }

        // Allow generous 350ms hangover (5,600 samples) to ensure unvoiced trailing phonemes/fricatives are preserved
        val hangoverSamples = (SAMPLE_RATE * 0.35f).toInt()
        val speechEnd = if (lastSpeechSample > 0) minOf(rawFloats.size, lastSpeechSample + hangoverSamples) else rawFloats.size
        // 100ms smooth taper into comfort silence (1,600 samples)
        val taperSamples = (SAMPLE_RATE * 0.10f).toInt()
        val fadeEnd = minOf(rawFloats.size, speechEnd + taperSamples)

        for (i in 0 until speechEnd) {
            processed[i] = (rawFloats[i] * gain).coerceIn(-1.0f, 1.0f)
        }

        if (fadeEnd > speechEnd) {
            val fadeLen = (fadeEnd - speechEnd).toFloat()
            for (i in speechEnd until fadeEnd) {
                val taperFactor = (fadeEnd - i) / fadeLen
                processed[i] = (rawFloats[i] * gain * taperFactor).coerceIn(-1.0f, 1.0f)
            }
        }

        // Trailing silence beyond fadeEnd remains 0.0f (clean silence), preventing Whisper VAD from looping

        val t1 = System.currentTimeMillis()
        Log.i(CRASH_TAG, "[WHISPER_TRACE] STAGE_1_PREPROCESS_DONE: samples=$targetLength speechEnd=$speechEnd peak=${"%.4f".format(peak * gain)} elapsedMs=${t1 - t0}")
        return processed
    }

    /**
     * Submits an immutable audio buffer for Whisper transcription on the independent transcriptionScope.
     * Records stage-by-stage timings with System.nanoTime() and generates a unique utteranceId.
     */
    fun submitTranscription(
        audioSamples: FloatArray,
        isFinal: Boolean,
        listener: SpeechEngineListener,
        speechStartNanos: Long = 0L,
        speechEndNanos: Long = 0L,
        customJobId: String? = null,
        onTranscriptionFinished: (() -> Unit)? = null
    ): Job? {
        if (audioSamples.isEmpty() || !hasAudibleSpeech(audioSamples)) {
            Log.d(TAG, "[WHISPER_TRACE] Rejected empty or silent audio buffer (samples=${audioSamples.size})")
            val reason = if (audioSamples.isEmpty()) "Buffer empty" else if (audioSamples.size < 3200) "Truncated (<200ms)" else "Peak <= 0.005f"
            val durMs = (audioSamples.size * 1000L) / SAMPLE_RATE
            try {
                val tracker = com.taskscheduling.app.feature.diagnostics.VoiceBotDiagnosticTracker.getInstance(context)
                tracker.recordCapturedAudioStats(
                    samples = audioSamples,
                    source = activeAudioSource,
                    isSubmitted = false,
                    rejectionReason = reason,
                    owner = "BOT"
                )
                tracker.recordBotPipelineStage(
                    stageName = "Audio Finalized",
                    status = "FAILED",
                    details = "Rejected: $reason ($durMs ms, ${audioSamples.size} samples)",
                    durationMs = durMs
                )
                tracker.recordBotPipelineStage(
                    stageName = "Recognition Requested",
                    status = "SKIPPED",
                    details = "Bypassed due to rejected audio"
                )
                tracker.recordBotPipelineStage(
                    stageName = "Engine Accepted Job",
                    status = "SKIPPED",
                    details = "Bypassed due to rejected audio"
                )
                tracker.recordBotPipelineStage(
                    stageName = "Inference Started",
                    status = "SKIPPED",
                    details = "Bypassed due to rejected audio"
                )
                tracker.recordBotPipelineStage(
                    stageName = "Transcript Returned",
                    status = "EMPTY_TRANSCRIPT",
                    details = "Audio was inaudible or empty"
                )
            } catch (_: Throwable) {}
            if (isFinal) {
                if (_engineState.value != WhisperEngineState.CAPTURING) {
                    _engineState.value = if (nativeBridge.isReady) WhisperEngineState.READY else WhisperEngineState.UNINITIALIZED
                }
                dispatchToMain {
                    try { listener.onResult("") } catch (_: Throwable) {}
                }
            }
            onTranscriptionFinished?.invoke()
            return null
        }

        val submissionNanos = System.nanoTime()
        val audioDurMs = (audioSamples.size * 1000L) / SAMPLE_RATE
        val utteranceId = customJobId ?: "utt_${System.currentTimeMillis()}_${utteranceCounter.incrementAndGet()}"

        try {
            val tracker = com.taskscheduling.app.feature.diagnostics.VoiceBotDiagnosticTracker.getInstance(context)
            tracker.recordCapturedAudioStats(
                samples = audioSamples,
                source = activeAudioSource,
                isSubmitted = true,
                owner = "BOT"
            )
            tracker.recordBotPipelineStage(
                stageName = "Audio Finalized",
                status = "SUCCESS",
                details = "Captured ${audioSamples.size} samples",
                durationMs = audioDurMs,
                jobId = utteranceId
            )
            tracker.recordBotPipelineStage(
                stageName = "Recognition Requested",
                status = "SUCCESS",
                details = "Requesting recognition for $utteranceId (${audioDurMs}ms)",
                durationMs = null,
                jobId = utteranceId
            )
        } catch (_: Throwable) {}

        synchronized(transcriptionLock) {
            val priorJob = activeTranscriptionJob
            if (priorJob != null && priorJob.isActive) {
                if (!isFinal) {
                    Log.d(TAG, "[WHISPER_TRACE] Drop secondary partial job while prior transcription is active")
                    return null
                } else if (!lastJobIsFinal) {
                    Log.d(TAG, "[WHISPER_TRACE] Cancelling previous partial job in favor of final utterance")
                    synchronized(inferenceLock) {
                        currentInferenceJob?.cancellationSignal?.set(true)
                        currentInferenceJob?.state = InferenceJobState.CANCELLED
                    }
                    priorJob.cancel()
                }
            }

            pendingUtterancesCount.incrementAndGet()
            lastJobIsFinal = isFinal
            activeUtteranceId = utteranceId
            _engineState.value = WhisperEngineState.TRANSCRIBING

            val scope = getOrCreateTranscriptionScope()
            val job = scope.launch {
                val queueWaitNanos = System.nanoTime()
                if (priorJob != null && priorJob.isActive) {
                    try { priorJob.join() } catch (_: Throwable) {}
                }
                val queueWaitMs = (System.nanoTime() - queueWaitNanos) / 1_000_000.0
                if (!coroutineContext.isActive) {
                    return@launch
                }
                coroutineContext.ensureActive()

                try {
                    val service = if (this@WhisperCppSpeechRecognitionEngine === getInstance(context)) {
                        SpeechRecognitionService.getInstance(context)
                    } else {
                        SpeechRecognitionService(context, this@WhisperCppSpeechRecognitionEngine)
                    }
                    val transResult = service.transcribe(
                        audioSamples = audioSamples,
                        languageMode = languageMode,
                        source = "BOT",
                        customJobId = utteranceId
                    )

                    if (!coroutineContext.isActive || transResult.isCancelled) {
                        try {
                            com.taskscheduling.app.feature.diagnostics.VoiceBotDiagnosticTracker.getInstance(context).recordBotPipelineStage(
                                stageName = "Transcript Returned",
                                status = "CANCELLED",
                                details = "Transcription cancelled",
                                jobId = utteranceId
                            )
                        } catch (_: Throwable) {}
                        return@launch
                    }

                    val resultNanos = System.nanoTime()
                    val submissionToResultMs = (resultNanos - submissionNanos) / 1_000_000.0
                    val endpointDetectionMs = if (speechEndNanos > speechStartNanos) (speechEndNanos - speechStartNanos) / 1_000_000.0 else 0.0

                    if (transResult.isSuccess) {
                        val text = transResult.text.trim()
                        val isBlank = text.isBlank()
                        Log.i(CRASH_TAG, "[CONTINUOUS_TRACE] Utterance $utteranceId transcription result: \"$text\" (latency=${transResult.processingTimeMs}ms, subToResult=${"%.1f".format(submissionToResultMs)}ms)")
                        try {
                            com.taskscheduling.app.feature.diagnostics.VoiceBotDiagnosticTracker.getInstance(context).recordBotPipelineStage(
                                stageName = "Transcript Returned",
                                status = if (isBlank) "EMPTY_TRANSCRIPT" else "SUCCESS",
                                details = if (isBlank) "Returned empty string" else "\"$text\"",
                                durationMs = transResult.processingTimeMs,
                                jobId = utteranceId
                            )
                        } catch (_: Throwable) {}

                        val uiDeliveryStartNanos = System.nanoTime()
                        if (isFinal) {
                            dispatchToMain {
                                val tUiReceiveAndRenderNs = System.nanoTime()
                                val uiDeliveryMs = (tUiReceiveAndRenderNs - uiDeliveryStartNanos) / 1_000_000.0
                                val visibleDisplayMs = (tUiReceiveAndRenderNs - submissionNanos) / 1_000_000.0
                                val totalSpeechToResultMs = if (speechStartNanos > 0L) (tUiReceiveAndRenderNs - speechStartNanos) / 1_000_000.0 else visibleDisplayMs
                                val endpointWaitMs = if (endpointDetectionMs > 0.0) endpointDetectionMs else 0.0

                                val fullTrace = VoiceStageTrace(
                                    requestId = utteranceId,
                                    audioDurationMs = audioDurMs,
                                    endpointWaitMs = endpointWaitMs,
                                    queueWaitMs = queueWaitMs,
                                    modelLoadMs = modelLoadTimeMs,
                                    preprocessingMs = transResult.preprocessingMs.toDouble(),
                                    nativeCallMs = transResult.nativeInferenceMs.toDouble(),
                                    resultProcessingMs = transResult.resultProcessingMs.toDouble(),
                                    uiDeliveryMs = uiDeliveryMs,
                                    totalSpeechToResultMs = totalSpeechToResultMs,
                                    modelPath = modelManager.modelFile.absolutePath,
                                    modelInitCount = nativeBridge.modelInitCount.get().coerceAtLeast(1),
                                    inferenceInvocationCount = nativeBridge.inferenceInvocationCount.get().coerceAtLeast(1),
                                    audioSampleCount = audioSamples.size,
                                    threadCount = nativeBridge.threads,
                                    isModelWarm = nativeBridge.isReady,
                                    tMicCaptureBeginNs = speechStartNanos,
                                    tLastSpeechSampleNs = if (speechEndNanos > 0L) speechEndNanos - (endpointWaitMs * 1_000_000.0).toLong() else speechStartNanos + (audioDurMs * 1_000_000L),
                                    tEndpointDetectionCompleteNs = speechEndNanos,
                                    tAudioSubmittedNs = submissionNanos,
                                    tQueueEntryNs = submissionNanos,
                                    tQueueExitNs = submissionNanos + (queueWaitMs * 1_000_000.0).toLong(),
                                    tEngineInitBeginNs = 0L,
                                    tEngineInitEndNs = 0L,
                                    tAudioPreprocBeginNs = transResult.stageTrace?.tAudioPreprocBeginNs ?: 0L,
                                    tAudioPreprocEndNs = transResult.stageTrace?.tAudioPreprocEndNs ?: 0L,
                                    tJniCallBeginNs = transResult.stageTrace?.tJniCallBeginNs ?: 0L,
                                    tJniCallEndNs = transResult.stageTrace?.tJniCallEndNs ?: 0L,
                                    tNativeInferenceBeginNs = transResult.stageTrace?.tNativeInferenceBeginNs ?: 0L,
                                    tNativeInferenceEndNs = transResult.stageTrace?.tNativeInferenceEndNs ?: 0L,
                                    tNativeResultReturnNs = transResult.stageTrace?.tNativeResultReturnNs ?: 0L,
                                    tTranscriptCleanupCompleteNs = transResult.stageTrace?.tTranscriptCleanupCompleteNs ?: 0L,
                                    tUiReceiveAndRenderNs = tUiReceiveAndRenderNs,
                                    transcript = text
                                )

                                Log.i(CRASH_TAG, "[VOICE_STAGE_CSV] ${fullTrace.toCsvLine()}")
                                Log.i(CRASH_TAG, "[VOICE_STAGE_META] modelPath=${fullTrace.modelPath} initCount=${fullTrace.modelInitCount} infCount=${fullTrace.inferenceInvocationCount} sampleCount=${fullTrace.audioSampleCount} threadCount=${fullTrace.threadCount} isWarm=${fullTrace.isModelWarm}")
                                Log.i(CRASH_TAG, "[VOICE_STAGE_TRACE] id=$utteranceId audioDur=${fullTrace.audioDurationMs}ms endpointWait=${"%.1f".format(fullTrace.endpointWaitMs)}ms queueWait=${"%.1f".format(fullTrace.queueWaitMs)}ms modelLoad=${fullTrace.modelLoadMs}ms prep=${"%.1f".format(fullTrace.preprocessingMs)}ms nativeCall=${"%.1f".format(fullTrace.nativeCallMs)}ms resultProc=${"%.1f".format(fullTrace.resultProcessingMs)}ms uiDelivery=${"%.1f".format(fullTrace.uiDeliveryMs)}ms totalSpeechToResult=${"%.1f".format(fullTrace.totalSpeechToResultMs)}ms text=\"$text\"")

                                _performanceMetrics.value = _performanceMetrics.value.copy(
                                    utteranceId = utteranceId,
                                    selectedModelName = modelManager.selectedModel.displayName,
                                    modelSizeBytes = modelManager.selectedModel.sizeBytes,
                                    threads = nativeBridge.threads,
                                    languageMode = languageMode.displayName,
                                    endpointDetectionMs = endpointDetectionMs,
                                    audioFinalizationMs = audioDurMs.toDouble(),
                                    submissionToResultMs = submissionToResultMs,
                                    visibleDisplayMs = visibleDisplayMs,
                                    queueWaitMs = queueWaitMs,
                                    nativeInferenceMs = transResult.processingTimeMs.toDouble(),
                                    uiDeliveryMs = uiDeliveryMs,
                                    speechEndToResultMs = submissionToResultMs,
                                    totalMs = visibleDisplayMs,
                                    stageTrace = fullTrace
                                )
                                try { listener.onResult(text) } catch (_: Throwable) {}
                            }
                        } else {
                            dispatchToMain {
                                try { listener.onPartialResult(text) } catch (_: Throwable) {}
                            }
                        }
                    } else {
                        val err = transResult.error ?: "Transcription failed"
                        val status = if (transResult.isCancelled) "CANCELLED" else "FAILED"
                        try {
                            com.taskscheduling.app.feature.diagnostics.VoiceBotDiagnosticTracker.getInstance(context).recordBotPipelineStage(
                                stageName = "Transcript Returned",
                                status = status,
                                details = err,
                                jobId = utteranceId
                            )
                        } catch (_: Throwable) {}
                        dispatchToMain {
                            try { listener.onError(SpeechEngineError.RecognitionFailed(err)) } catch (_: Throwable) {}
                        }
                    }
                } catch (ce: CancellationException) {
                    Log.w(CRASH_TAG, "[Whisper] submitTranscription cancelled for $utteranceId")
                    try {
                        com.taskscheduling.app.feature.diagnostics.VoiceBotDiagnosticTracker.getInstance(context).recordBotPipelineStage(
                            stageName = "Transcript Returned",
                            status = "CANCELLED",
                            details = "Cancelled before completion",
                            jobId = utteranceId
                        )
                    } catch (_: Throwable) {}
                    throw ce
                } catch (t: Throwable) {
                    try {
                        com.taskscheduling.app.feature.diagnostics.VoiceBotDiagnosticTracker.getInstance(context).recordBotPipelineStage(
                            stageName = "Transcript Returned",
                            status = "FAILED",
                            details = t.message ?: "Execution error",
                            jobId = utteranceId
                        )
                    } catch (_: Throwable) {}
                    dispatchToMain {
                        try { listener.onError(SpeechEngineError.RecognitionFailed(t.message ?: "Error")) } catch (_: Throwable) {}
                    }
                } finally {
                    val remaining = pendingUtterancesCount.decrementAndGet()
                    synchronized(transcriptionLock) {
                        if (activeTranscriptionJob === coroutineContext[Job]) {
                            activeTranscriptionJob = null
                            if (activeUtteranceId == utteranceId) {
                                activeUtteranceId = null
                            }
                        }
                    }
                    if (_engineState.value != WhisperEngineState.RELEASED) {
                        if (remaining <= 0 || synchronized(transcriptionLock) { activeTranscriptionJob == null }) {
                            pendingUtterancesCount.set(0)
                            val isRec = synchronized(workerLock) { activeWorker?.isCapturing == true }
                            _engineState.value = if (isRec) WhisperEngineState.CAPTURING else if (nativeBridge.isReady) WhisperEngineState.READY else WhisperEngineState.UNINITIALIZED
                        } else {
                            _engineState.value = WhisperEngineState.TRANSCRIBING
                        }
                    }
                    try {
                        onTranscriptionFinished?.invoke()
                    } catch (_: Throwable) {}
                }
            }
            activeTranscriptionJob = job
            return job
        }
    }

    /**
     * Unified, direct synchronous transcription for diagnostic screens and direct callers.
     * Shares the exact same inference coordination, preprocessing, and lifecycle as continuous bot listening.
     */
    suspend fun transcribeDirect(
        audioSamples: FloatArray,
        mode: LanguageMode = languageMode,
        source: String = "DIAGNOSTIC",
        customJobId: String? = null
    ): Result<WhisperTranscriptionResult> = withContext(Dispatchers.IO) {
        if (audioSamples.isEmpty() || !hasAudibleSpeech(audioSamples)) {
            Log.d(TAG, "[WHISPER_TRACE] Direct inference rejected empty/silent audio (samples=${audioSamples.size})")
            return@withContext Result.success(WhisperTranscriptionResult("", 0L, mode.code, 0))
        }

        val jobId = customJobId ?: "${source.lowercase()}_${System.currentTimeMillis()}_${utteranceCounter.incrementAndGet()}"
        val job = InferenceJob(id = jobId, source = source, languageMode = mode)

        var peakVal = 0f
        var sumSq = 0.0
        for (s in audioSamples) {
            val a = kotlin.math.abs(s)
            if (a > peakVal) peakVal = a
            sumSq += (s * s).toDouble()
        }
        val rmsVal = if (audioSamples.isNotEmpty()) kotlin.math.sqrt(sumSq / audioSamples.size).toFloat() else 0f
        val audioDurationMs = (audioSamples.size * 1000L) / SAMPLE_RATE
        val bufferQualityStr = when {
            audioSamples.isEmpty() -> "EMPTY"
            audioSamples.size < 3200 -> "TRUNCATED"
            peakVal <= 0.005f -> "SILENT"
            else -> "VALID"
        }

        if (source == "BOT") {
            try {
                com.taskscheduling.app.feature.diagnostics.VoiceBotDiagnosticTracker.getInstance(context).recordInferenceEntryPoint(
                    sessionId = "bot_sess",
                    jobId = jobId,
                    sampleCount = audioSamples.size,
                    durationMs = audioDurationMs,
                    rms = rmsVal,
                    peak = peakVal,
                    bufferQuality = bufferQualityStr,
                    preprocessingSuccess = true
                )
            } catch (_: Throwable) {}
        }

        if (source != "BOT") {
            val isBusy = synchronized(inferenceLock) {
                val active = currentInferenceJob
                active != null && active.id != jobId && (active.state == InferenceJobState.DECODING || active.state == InferenceJobState.QUEUED) && !active.cancellationSignal.get()
            } || inferenceMutex.isLocked
            if (isBusy) {
                val msg = "Engine is currently busy decoding. Please wait."
                Log.w(CRASH_TAG, "[Whisper] Direct inference rejected: $msg (active: ${currentInferenceJob?.id})")
                return@withContext Result.failure(IllegalStateException(msg))
            }
        }

        val submissionNanos = System.nanoTime()
        val lockReqNanos = System.nanoTime()
        val lockAcquired = try {
            withTimeoutOrNull(STALL_TIMEOUT_MS) {
                inferenceMutex.lock()
                true
            } ?: false
        } catch (_: CancellationException) {
            false
        }
        val lockWaitMs = (System.nanoTime() - lockReqNanos) / 1_000_000.0

        if (!lockAcquired) {
            val msg = "Engine is currently busy decoding. Timed out waiting for inference lock."
            Log.w(CRASH_TAG, "[Whisper] Direct inference timed out: $msg (active: ${currentInferenceJob?.id})")
            if (source == "BOT") {
                try {
                    com.taskscheduling.app.feature.diagnostics.VoiceBotDiagnosticTracker.getInstance(context).recordBotPipelineStage(
                        stageName = "Engine Accepted Job",
                        status = "FAILED",
                        details = msg,
                        jobId = jobId
                    )
                } catch (_: Throwable) {}
            }
            return@withContext Result.failure(IllegalStateException(msg))
        }

        synchronized(inferenceLock) {
            currentInferenceJob = job
        }

        if (source == "BOT") {
            try {
                com.taskscheduling.app.feature.diagnostics.VoiceBotDiagnosticTracker.getInstance(context).recordBotPipelineStage(
                    stageName = "Engine Accepted Job",
                    status = "SUCCESS",
                    details = "Job accepted by Whisper engine",
                    jobId = jobId
                )
            } catch (_: Throwable) {}
        }

        val preprocStartNanos = System.nanoTime()
        val processedAudio = prepareAudioForWhisper(audioSamples)
        val preprocMs = (System.nanoTime() - preprocStartNanos) / 1_000_000.0
        _engineState.value = WhisperEngineState.TRANSCRIBING
        job.state = InferenceJobState.DECODING

        if (source == "BOT") {
            try {
                com.taskscheduling.app.feature.diagnostics.VoiceBotDiagnosticTracker.getInstance(context).recordBotPipelineStage(
                    stageName = "Inference Started",
                    status = "SUCCESS",
                    details = "Whisper decoding started (${processedAudio.size} samples, mode=${mode.code})",
                    jobId = jobId
                )
            } catch (_: Throwable) {}
        }

        try {
            if (!nativeBridge.isReady) {
                Log.w(CRASH_TAG, "[Whisper] Native bridge not ready before direct transcription. Initializing...")
                val initRes = initialize()
                if (initRes.isFailure) {
                    val initEx = initRes.exceptionOrNull()
                    val errMsg = "Whisper native context is not loaded: ${initEx?.message ?: "Model not ready"}"
                    job.state = InferenceJobState.FAILED
                    _engineState.value = WhisperEngineState.ERROR
                    _lastErrorMessage.value = errMsg
                    if (source == "BOT") {
                        try {
                            com.taskscheduling.app.feature.diagnostics.VoiceBotDiagnosticTracker.getInstance(context).recordBotPipelineStage(
                                stageName = "Inference Started",
                                status = "FAILED",
                                details = errMsg,
                                jobId = jobId
                            )
                        } catch (_: Throwable) {}
                    }
                    return@withContext Result.failure(IllegalStateException(errMsg, initEx))
                }
            }

            if (job.cancellationSignal.get() || !coroutineContext.isActive) {
                job.state = InferenceJobState.CANCELLED
                throw CancellationException("Direct inference cancelled before execution")
            }

            val infStartNanos = System.nanoTime()
            val res = nativeBridge.transcribeAudio(processedAudio, mode)
            val infMs = (System.nanoTime() - infStartNanos) / 1_000_000L
            val totalSubmissionToResultMs = (System.nanoTime() - submissionNanos) / 1_000_000.0

            if (source == "BOT") {
                try {
                    val transcriptStr = if (res.isSuccess) res.getOrThrow().text else ""
                    com.taskscheduling.app.feature.diagnostics.VoiceBotDiagnosticTracker.getInstance(context).recordInferenceExecution(
                        jobId = jobId,
                        started = true,
                        completed = res.isSuccess,
                        decodingMs = infMs,
                        transcript = transcriptStr,
                        nativeError = res.exceptionOrNull()?.message
                    )
                } catch (_: Throwable) {}
            }

            if (job.cancellationSignal.get() || !coroutineContext.isActive) {
                job.state = InferenceJobState.CANCELLED
                throw CancellationException("Direct inference cancelled during execution")
            }

            if (res.isSuccess) {
                job.state = InferenceJobState.SUCCEEDED
                val r = res.getOrThrow()
                _engineState.value = if (r.text.isNotBlank()) WhisperEngineState.RESULT else WhisperEngineState.EMPTY_RESULT

                val enriched = r.copy(
                    preprocessingMs = preprocMs,
                    lockWaitMs = lockWaitMs,
                    totalSubmissionToResultMs = totalSubmissionToResultMs,
                    audioDurationMs = audioDurationMs,
                    sampleCount = audioSamples.size,
                    modelIdentifier = modelManager.selectedModel.displayName,
                    callerName = source
                )

                _performanceMetrics.value = _performanceMetrics.value.copy(
                    utteranceId = jobId,
                    selectedModelName = modelManager.selectedModel.displayName,
                    threads = nativeBridge.threads,
                    languageMode = mode.displayName,
                    captureMs = audioDurationMs.toDouble(),
                    audioPreparationMs = preprocMs,
                    queueWaitMs = lockWaitMs,
                    nativeMutexWaitMs = r.nativeMutexWaitMs,
                    nativeInferenceMs = r.nativeInferenceMs,
                    resultProcessingMs = r.resultProcessingMs,
                    submissionToResultMs = totalSubmissionToResultMs,
                    speechEndToResultMs = totalSubmissionToResultMs,
                    totalMs = totalSubmissionToResultMs,
                    audioDurationMs = audioDurationMs
                )

                Log.i(
                    CRASH_TAG,
                    "[WHISPER_PERF] id=$jobId caller=$source model=${modelManager.selectedModel.displayName} " +
                    "audioDur=${audioDurationMs}ms samples=${audioSamples.size} " +
                    "lockWait=${"%.1f".format(lockWaitMs)}ms prep=${"%.1f".format(preprocMs)}ms " +
                    "nativeDecode=${"%.1f".format(r.nativeInferenceMs)}ms resultProc=${"%.1f".format(r.resultProcessingMs)}ms " +
                    "totalSubmissionToResult=${"%.1f".format(totalSubmissionToResultMs)}ms " +
                    "text=\"${r.text}\""
                )

                Result.success(enriched)
            } else {
                job.state = InferenceJobState.FAILED
                _engineState.value = WhisperEngineState.ERROR
                _lastErrorMessage.value = res.exceptionOrNull()?.message
                res
            }
        } catch (ce: CancellationException) {
            job.state = InferenceJobState.CANCELLED
            Log.w(CRASH_TAG, "[Whisper] Direct inference cancelled for $jobId")
            throw ce
        } catch (t: Throwable) {
            job.state = InferenceJobState.FAILED
            _engineState.value = WhisperEngineState.ERROR
            _lastErrorMessage.value = t.message
            Result.failure(t)
        } finally {
            synchronized(inferenceLock) {
                if (currentInferenceJob?.id == jobId) {
                    currentInferenceJob = null
                }
            }
            if (inferenceMutex.isLocked) {
                try { inferenceMutex.unlock() } catch (_: Throwable) {}
            }
            if (_engineState.value != WhisperEngineState.RELEASED) {
                val isRec = synchronized(workerLock) { activeWorker?.isCapturing == true }
                _engineState.value = if (isRec) WhisperEngineState.CAPTURING else if (nativeBridge.isReady) WhisperEngineState.READY else WhisperEngineState.UNINITIALIZED
            }
        }
    }

    /**
     * Initializes the selected Whisper offline model.
     * Reuses existing READY model context if already initialized with the same model.
     */
    override suspend fun initialize(): Result<Unit> = withContext(Dispatchers.IO) {
        initMutex.withLock {
            val targetModel = modelManager.selectedModel
            val targetFile = modelManager.modelFile

            if (nativeBridge.isReady && nativeBridge.modelPath == targetFile.absolutePath) {
                _engineState.value = WhisperEngineState.READY
                _lastErrorMessage.value = null
                getOrCreateTranscriptionScope()
                Log.i(CRASH_TAG, "[Whisper] Reusing existing valid native model context: ${targetModel.displayName}")
                return@withContext Result.success(Unit)
            }

            _engineState.value = WhisperEngineState.LOADING
            _lastErrorMessage.value = null
            Log.i(CRASH_TAG, "[Whisper] Initialization started for model: ${targetModel.displayName} (${targetModel.sizeBytes / (1024 * 1024)}MB)")

            try {
                val modelStatus = modelManager.checkStatus(targetModel)
                val modelFile = when (modelStatus) {
                    is WhisperModelStatus.Ready -> modelStatus.file
                    is WhisperModelStatus.Corrupted -> {
                        val err = "Model corrupted: ${modelStatus.message}"
                        _engineState.value = WhisperEngineState.ERROR
                        _lastErrorMessage.value = err
                        return@withContext Result.failure(IllegalStateException(err))
                    }
                    is WhisperModelStatus.InsufficientStorage -> {
                        val err = "Not enough storage for ${targetModel.displayName}."
                        _engineState.value = WhisperEngineState.ERROR
                        _lastErrorMessage.value = err
                        return@withContext Result.failure(IllegalStateException(err))
                    }
                    else -> {
                        val err = "Speech model required: ${targetModel.filename} not found."
                        _engineState.value = WhisperEngineState.ERROR
                        _lastErrorMessage.value = err
                        return@withContext Result.failure(IllegalStateException(err))
                    }
                }

                // Pre-flight RAM protection
                val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                val memInfo = ActivityManager.MemoryInfo()
                actManager?.getMemoryInfo(memInfo)
                val availMemBytes = memInfo.availMem
                val requiredMemBytes = modelFile.length() + (40L * 1024L * 1024L)

                if (availMemBytes < requiredMemBytes || memInfo.lowMemory) {
                    val errMsg = "Insufficient RAM: ${availMemBytes / (1024 * 1024)}MB available, ~${requiredMemBytes / (1024 * 1024)}MB required."
                    _engineState.value = WhisperEngineState.ERROR
                    _lastErrorMessage.value = errMsg
                    updateMemoryDiagnostics()
                    return@withContext Result.failure(IllegalStateException(errMsg))
                }

                nativeBridge.release()
                System.gc()

                val loadStartNanos = System.nanoTime()
                val loadRes = nativeBridge.loadModel(modelFile)
                if (loadRes.isSuccess) {
                    modelLoadTimeMs = ((System.nanoTime() - loadStartNanos) / 1_000_000L)
                    _engineState.value = WhisperEngineState.READY
                    _lastErrorMessage.value = null
                    getOrCreateTranscriptionScope()
                    updateMemoryDiagnostics()
                    Log.i(CRASH_TAG, "[Whisper] MODEL READY: ${targetModel.filename} (Load time: ${modelLoadTimeMs}ms)")
                    Result.success(Unit)
                } else {
                    val ex = loadRes.exceptionOrNull() ?: IllegalStateException("Native model load failed")
                    _engineState.value = WhisperEngineState.ERROR
                    _lastErrorMessage.value = ex.message
                    updateMemoryDiagnostics()
                    Result.failure(ex)
                }
            } catch (t: Throwable) {
                _engineState.value = WhisperEngineState.ERROR
                _lastErrorMessage.value = t.message
                updateMemoryDiagnostics()
                Result.failure(t)
            }
        }
    }

    /**
     * Diagnostic: Isolated model load test.
     */
    suspend fun testWhisperModelLoadOnly(): Result<String> = withContext(Dispatchers.IO) {
        Log.i(CRASH_TAG, "[DIAGNOSTIC] === START testWhisperModelLoadOnly ===")
        try {
            val initRes = initialize()
            if (initRes.isFailure) return@withContext Result.failure(initRes.exceptionOrNull()!!)
            val modelName = modelManager.selectedModel.displayName
            Log.i(CRASH_TAG, "[DIAGNOSTIC] === SUCCESS testWhisperModelLoadOnly: $modelName ready ===")
            Result.success("PASS: Whisper model $modelName loaded and verified ready in memory.")
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    /**
     * Diagnostic: Isolated microphone test.
     */
    suspend fun testMicrophoneOnly(source: String = preferredAudioSource): Result<String> = withContext(Dispatchers.IO) {
        try {
            val tester = RawMicrophoneTester(context)
            val res = tester.recordTimedDiagnostic(source = source, durationMs = 3000L)
            if (res.isSuccess) {
                Result.success(res.getOrThrow().formatDisplay())
            } else {
                Result.failure(res.exceptionOrNull() ?: Exception("Microphone test failed"))
            }
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    /**
     * Diagnostic: Single shot audio transcription.
     */
    suspend fun testSingleShotAudioTranscription(source: String = preferredAudioSource): Result<String> = withContext(Dispatchers.IO) {
        try {
            val initRes = initialize()
            if (initRes.isFailure) return@withContext Result.failure(initRes.exceptionOrNull()!!)

            val tester = RawMicrophoneTester(context)
            val recRes = tester.recordTimedDiagnostic(source = source, durationMs = 2500L)
            if (recRes.isFailure) return@withContext Result.failure(recRes.exceptionOrNull()!!)

            val summary = recRes.getOrThrow()
            val pcmFloats = summary.pcmFloats ?: FloatArray(0)
            val t0 = System.currentTimeMillis()
            val result = transcribeDirect(pcmFloats, languageMode, source = "1SHOT_TEST")
            val elapsed = System.currentTimeMillis() - t0

            if (result.isSuccess) {
                val text = result.getOrThrow().text
                val display = "MIC SIGNAL (RMS=${"%.4f".format(summary.averageRms)}, Peak=${"%.4f".format(summary.peak)}) -> PCM (${summary.totalSamples} samples) -> WHISPER -> TEXT: \"$text\""
                Result.success(display)
            } else {
                Result.failure(result.exceptionOrNull() ?: Exception("Transcription failed"))
            }
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    /**
     * Diagnostic: Controlled sequential test of VOICE_RECOGNITION, MIC, and DEFAULT audio sources.
     */
    suspend fun testAllAudioSources(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val tester = RawMicrophoneTester(context)
            val list = tester.testAllSources(durationPerSourceMs = 2500L)
            val report = buildString {
                appendLine("AUDIO SOURCES COMPARISON (Samsung Tab A9)")
                for (item in list) {
                    appendLine("----------------------------------------")
                    appendLine("Source: ${item.source} -> Result: ${item.result}")
                    appendLine("Average RMS: ${"%.4f".format(item.averageRms)} | Peak: ${"%.4f".format(item.peak)}")
                    appendLine("Min: ${item.minSample} | Max: ${item.maxSample} | Samples: ${item.totalSamples}")
                }
            }
            Result.success(report)
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    /**
     * Diagnostic: Direct WAV file transcription test.
     */
    suspend fun testAudioFile(wavFile: File, lang: LanguageMode = languageMode): Result<String> = withContext(Dispatchers.IO) {
        if (!isModelLoaded) {
            val initRes = initialize()
            if (initRes.isFailure) return@withContext Result.failure(initRes.exceptionOrNull()!!)
        }
        val t0 = System.currentTimeMillis()
        val res = nativeBridge.transcribeWavFile(wavFile, lang)
        val elapsed = System.currentTimeMillis() - t0
        if (res.isSuccess) {
            val transcript = res.getOrThrow().text
            Log.i(TAG, "[WHISPER_FINAL] WAV test result: \"$transcript\" in ${elapsed}ms")
            Result.success(transcript)
        } else {
            Result.failure(res.exceptionOrNull() ?: IllegalStateException("Transcription failed"))
        }
    }

    /**
     * Benchmark a specific model with a given thread count on a target WAV file.
     */
    suspend fun benchmarkModel(
        modelType: WhisperModelType,
        threads: Int,
        wavFile: File,
        lang: LanguageMode
    ): Result<WhisperPerformanceMetrics> = withContext(Dispatchers.IO) {
        try {
            modelManager.selectModel(modelType)
            nativeBridge.setThreadCount(threads)
            val initRes = initialize()
            if (initRes.isFailure) return@withContext Result.failure(initRes.exceptionOrNull()!!)

            val startNanos = System.nanoTime()
            val transRes = nativeBridge.transcribeWavFile(wavFile, lang)
            val endNanos = System.nanoTime()

            if (transRes.isSuccess) {
                val r = transRes.getOrThrow()
                val totalMs = (endNanos - startNanos) / 1_000_000.0
                val metrics = WhisperPerformanceMetrics(
                    utteranceId = "bench_${modelType.id}_${threads}t",
                    selectedModelName = modelType.filename,
                    modelSizeBytes = modelType.sizeBytes,
                    threads = threads,
                    languageMode = lang.displayName,
                    modelLoadTimeMs = modelLoadTimeMs,
                    nativeMutexWaitMs = r.nativeMutexWaitMs,
                    nativeInferenceMs = r.nativeInferenceMs,
                    resultProcessingMs = r.resultProcessingMs,
                    totalMs = totalMs,
                    decodeTimeMs = r.processingTimeMs
                )
                Log.i(CRASH_TAG, "[BENCHMARK] model=${modelType.id} thr=$threads lang=${lang.code} inferenceMs=${"%.1f".format(r.nativeInferenceMs)} text=\"${r.text}\"")
                Result.success(metrics)
            } else {
                Result.failure(transRes.exceptionOrNull() ?: Exception("Benchmark failed"))
            }
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    @SuppressLint("MissingPermission")
    open override fun startListening(listener: SpeechEngineListener, continuous: Boolean): Boolean {
        val sessionId = sessionCounter.incrementAndGet()
        Log.i(CRASH_TAG, "[Whisper] Start listening request: sessionId=$sessionId continuous=$continuous")

        try {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                Log.e(CRASH_TAG, "[Whisper] RECORD_AUDIO permission denied")
                listener.onError(SpeechEngineError.MicrophonePermissionDenied)
                return false
            }

            if (!NativeLibraryLoader.isNativeReady) {
                val libRes = NativeLibraryLoader.loadLibraries()
                if (libRes.isFailure) {
                    val msg = "Native whisper library not loaded: ${libRes.exceptionOrNull()?.message}"
                    listener.onError(SpeechEngineError.InitializationFailed(msg))
                    return false
                }
            }

            if (!isModelLoaded || !nativeBridge.isReady) {
                val lastErr = _lastErrorMessage.value ?: "Whisper model loading. Please wait."
                listener.onError(SpeechEngineError.InitializationFailed(lastErr))
                return false
            }

            synchronized(workerLock) {
                stopListeningInternal()

                val worker = AudioCaptureWorker(
                    sessionId = sessionId,
                    listener = listener,
                    continuous = continuous,
                    desiredSource = preferredAudioSource
                )

                val started = worker.start()
                if (started) {
                    activeWorker = worker
                    _engineState.value = WhisperEngineState.CAPTURING
                    listener.onReady()
                    return true
                } else {
                    val err = "Failed to initialize AudioRecord for Whisper"
                    _lastErrorMessage.value = err
                    listener.onError(SpeechEngineError.MicrophoneUnavailable(err))
                    return false
                }
            }
        } catch (t: Throwable) {
            listener.onError(SpeechEngineError.InitializationFailed("startListening error: ${t.message}"))
            return false
        }
    }

    override fun pauseListening() {
        synchronized(workerLock) { activeWorker?.pause() }
    }

    override fun resumeListening() {
        synchronized(workerLock) { activeWorker?.resume() }
    }

    override fun stopListening() {
        synchronized(workerLock) { stopListeningInternal() }
    }

    override fun cancelListening() {
        synchronized(workerLock) {
            activeWorker?.cancel()
            cancelActiveTranscription()
            stopListeningInternal()
            _engineState.value = if (nativeBridge.isReady) WhisperEngineState.READY else WhisperEngineState.UNINITIALIZED
        }
    }

    override fun release() {
        stopListening()
        val jobToWait: Job?
        synchronized(transcriptionLock) { jobToWait = activeTranscriptionJob }
        if (jobToWait != null && jobToWait.isActive) {
            try {
                runBlocking {
                    withTimeoutOrNull(2500L) {
                        jobToWait.join()
                    }
                }
            } catch (_: Throwable) {}
        }

        synchronized(transcriptionLock) {
            transcriptionJob.cancel()
            activeTranscriptionJob = null
            activeUtteranceId = null
            pendingUtterancesCount.set(0)
        }

        _engineState.value = WhisperEngineState.RELEASED
        nativeBridge.release()
        Log.i(CRASH_TAG, "[Whisper] WhisperCppSpeechRecognitionEngine released cleanly")
    }

    private fun stopListeningInternal() {
        val worker = synchronized(workerLock) {
            val w = activeWorker
            activeWorker = null
            w
        }
        worker?.stop()
        if (_engineState.value != WhisperEngineState.RELEASED) {
            val isTranscribingNow = synchronized(transcriptionLock) { activeTranscriptionJob?.isActive == true } || nativeBridge.isInferenceActive
            _engineState.value = if (isTranscribingNow) {
                WhisperEngineState.TRANSCRIBING
            } else if (nativeBridge.isReady) {
                WhisperEngineState.READY
            } else {
                WhisperEngineState.UNINITIALIZED
            }
        }
    }

    fun updateMemoryDiagnostics() {
        try {
            val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            actManager?.getMemoryInfo(memInfo)
            val runtime = Runtime.getRuntime()
            val appUsedMB = (runtime.totalMemory() - runtime.freeMemory()) / (1024f * 1024f)
            val availMB = memInfo.availMem / (1024f * 1024f)

            _performanceMetrics.value = _performanceMetrics.value.copy(
                modelLoadTimeMs = modelLoadTimeMs,
                nativeModelMemoryMB = (modelManager.selectedModel.sizeBytes / (1024f * 1024f)),
                appMemoryMB = appUsedMB,
                availableMemoryMB = availMB
            )
        } catch (_: Throwable) {}
    }

    private inner class AudioCaptureWorker(
        val sessionId: Int,
        private val listener: SpeechEngineListener,
        private val continuous: Boolean,
        private val desiredSource: String
    ) : Runnable {

        private var workerThread: Thread? = null
        private var audioRecord: AudioRecord? = null
        private val isRunning = AtomicBoolean(false)
        private val isPaused = AtomicBoolean(false)
        private val isCancelled = AtomicBoolean(false)

        val isCapturing: Boolean
            get() = isRunning.get()

        var activeAudioSource: String = desiredSource
            private set
        var bufferSize: Int = SAMPLES_PER_READ * 2
            private set

        private val preRollBuffer = PreRollAudioBuffer(capacitySamples = 4800)
        private val utteranceBuffer = ArrayList<Float>(SAMPLE_RATE * 15)
        private val utteranceLock = Any()

        @Volatile
        private var isSpeechActive = false
        @Volatile
        private var speechStartNanos = 0L
        @Volatile
        private var speechEndNanos = 0L
        @Volatile
        private var recordingStartRealtimeMs = 0L
        @Volatile
        private var speechStartRealtimeMs = 0L
        @Volatile
        private var speechEndRealtimeMs = 0L
        @Volatile
        private var lastSpeechDetectedRealtimeMs = 0L
        @Volatile
        private var totalSuccessfulReads = 0L
        @Volatile
        private var totalSamplesCaptured = 0L
        private var dynamicNoiseFloor = 0.002f

        @SuppressLint("MissingPermission")
        fun start(): Boolean {
            val record = try {
                RawMicrophoneTester.createConfiguredAudioRecord(desiredSource, SAMPLE_RATE)
            } catch (t: Throwable) {
                Log.e(CRASH_TAG, "[Whisper] sessionId=$sessionId AudioRecord init error: ${t.message}", t)
                return false
            }

            audioRecord = record
            activeAudioSource = desiredSource
            val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
            bufferSize = maxOf(minBuf * 2, SAMPLES_PER_READ * 4)

            try {
                record.startRecording()
            } catch (t: Throwable) {
                try { record.release() } catch (_: Throwable) {}
                audioRecord = null
                return false
            }

            recordingStartRealtimeMs = android.os.SystemClock.elapsedRealtime()
            totalSuccessfulReads = 0L
            totalSamplesCaptured = 0L

            isRunning.set(true)
            val thread = Thread(this, "WhisperAudioWorker-$sessionId")
            workerThread = thread
            thread.start()
            return true
        }

        fun pause() { isPaused.set(true) }
        fun resume() { isPaused.set(false) }
        fun cancel() {
            isCancelled.set(true)
            stop()
        }

        fun stop() {
            if (!isRunning.getAndSet(false)) return
            val stopRealtimeMs = android.os.SystemClock.elapsedRealtime()
            val totalElapsedMs = if (recordingStartRealtimeMs > 0L) (stopRealtimeMs - recordingStartRealtimeMs) else 0L
            val stopReasonStr = if (isCancelled.get()) "CANCELLED" else "USER_STOP"
            Log.i(CRASH_TAG, "WHISPER_STEP_12_AUDIO_STOP sessionId=$sessionId stopReason=$stopReasonStr elapsedMs=$totalElapsedMs")

            try { audioRecord?.stop() } catch (_: Throwable) {}

            val thread = workerThread
            if (thread != null && thread != Thread.currentThread()) {
                try { thread.join(1000) } catch (_: InterruptedException) {}
            }
            workerThread = null

            try { audioRecord?.release() } catch (_: Throwable) {}
            audioRecord = null

            val shouldTranscribe: Boolean
            val finalFloats: FloatArray
            val candidateSize: Int
            val sStartNanos = speechStartNanos
            val sEndNanos = if (speechEndNanos > 0L) speechEndNanos else System.nanoTime()

            synchronized(utteranceLock) {
                val candidateFloats = utteranceBuffer.toFloatArray()
                utteranceBuffer.clear()
                candidateSize = candidateFloats.size
                shouldTranscribe = !isCancelled.get() && hasAudibleSpeech(candidateFloats)
                finalFloats = if (shouldTranscribe) candidateFloats else FloatArray(0)
            }

            val finalDurMs = (candidateSize * 1000L) / SAMPLE_RATE
            try {
                val tracker = com.taskscheduling.app.feature.diagnostics.VoiceBotDiagnosticTracker.getInstance(context)
                tracker.recordAudioSessionLifecycle(
                    startTimeMs = System.currentTimeMillis() - totalElapsedMs,
                    stopTimeMs = System.currentTimeMillis(),
                    actualElapsedMs = totalElapsedMs,
                    successfulReads = totalSuccessfulReads,
                    samplesPerRead = SAMPLES_PER_READ,
                    bytesPerRead = SAMPLES_PER_READ * 2,
                    totalSamplesCaptured = totalSamplesCaptured,
                    totalBytesCaptured = totalSamplesCaptured * 2,
                    finalizedAudioDurationMs = finalDurMs,
                    stopReason = stopReasonStr,
                    samplesDiscarded = if (shouldTranscribe) 0L else candidateSize.toLong(),
                    finalBufferFlushed = true,
                    submittedToInference = shouldTranscribe
                )
            } catch (_: Throwable) {}

            if (shouldTranscribe) {
                isSpeechActive = false
                dispatchUtterance(finalFloats, sStartNanos, sEndNanos)
            } else {
                dispatchToMain {
                    try { listener.onStopped() } catch (_: Throwable) {}
                }
            }
        }

        override fun run() {
            val shortBuffer = ShortArray(SAMPLES_PER_READ)
            val record = audioRecord ?: return
            var chunkIndex = 0L

            try {
                while (isRunning.get()) {
                    if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) break

                    val shortsRead = record.read(shortBuffer, 0, shortBuffer.size, AudioRecord.READ_BLOCKING)
                    if (!isRunning.get()) break
                    if (shortsRead <= 0) continue
                    if (isPaused.get()) continue
                    if (isMuted) {
                        var wasSpeakingWithAudio = false
                        var candidateFloats: FloatArray? = null
                        synchronized(utteranceLock) {
                            if (isSpeechActive && utteranceBuffer.size >= (SAMPLE_RATE * 0.25f)) {
                                wasSpeakingWithAudio = true
                                candidateFloats = utteranceBuffer.toFloatArray()
                                utteranceBuffer.clear()
                            } else {
                                utteranceBuffer.clear()
                            }
                        }
                        isSpeechActive = false
                        lastSpeechDetectedRealtimeMs = 0L
                        if (wasSpeakingWithAudio && candidateFloats != null && hasAudibleSpeech(candidateFloats!!)) {
                            val sEnd = System.nanoTime()
                            dispatchUtterance(candidateFloats!!, speechStartNanos, sEnd, ++utteranceIndex)
                        }
                        continue
                    }

                    chunkIndex++
                    totalSuccessfulReads++
                    totalSamplesCaptured += shortsRead

                    val rawFloats = FloatArray(shortsRead)
                    var chunkSumSq = 0.0
                    var chunkPeak = 0f
                    for (i in 0 until shortsRead) {
                        val s = shortBuffer[i]
                        val f = s.toFloat() / 32768.0f
                        rawFloats[i] = f
                        chunkSumSq += (f * f).toDouble()
                        val absF = kotlin.math.abs(f)
                        if (absF > chunkPeak) chunkPeak = absF
                    }

                    if (isMuted) {
                        synchronized(utteranceLock) {
                            utteranceBuffer.clear()
                        }
                        isSpeechActive = false
                        lastSpeechDetectedRealtimeMs = 0L
                        continue
                    }

                    val rms = kotlin.math.sqrt(chunkSumSq / shortsRead).toFloat()
                    if (rms in 0.0005f..0.02f) {
                        dynamicNoiseFloor = minOf(dynamicNoiseFloor, 0.95f * dynamicNoiseFloor + 0.05f * rms)
                    }
                    val speechRmsThreshold = maxOf(0.0035f, dynamicNoiseFloor * 2.2f)
                    val speechPeakThreshold = maxOf(0.016f, speechRmsThreshold * 3.5f)
                    val isCandidateSpeech = rms >= speechRmsThreshold || chunkPeak >= speechPeakThreshold

                    if (chunkIndex % 3 == 0L) {
                        _performanceMetrics.value = _performanceMetrics.value.copy(
                            rmsLevel = rms,
                            peakLevel = chunkPeak
                        )
                        dispatchToMain {
                            try { listener.onAudioLevel(rms, chunkPeak, isCandidateSpeech) } catch (_: Throwable) {}
                        }
                    }

                    val nowRealtime = android.os.SystemClock.elapsedRealtime()
                    val endpointSilenceMs = customEndpointSilenceMs ?: when (languageMode) {
                        LanguageMode.ENGLISH -> 500L   // 500ms: crisp responsive pause detection
                        LanguageMode.HINDI -> 650L    // 650ms: natural Hindi pause
                        LanguageMode.HINGLISH -> 700L // 700ms: code-switching buffer
                    }

                    if (isCandidateSpeech) {
                        val isNewUtterance = !isSpeechActive && (lastSpeechDetectedRealtimeMs == 0L || (nowRealtime - lastSpeechDetectedRealtimeMs) >= endpointSilenceMs)
                        lastSpeechDetectedRealtimeMs = nowRealtime
                        if (isNewUtterance) {
                            isSpeechActive = true
                            speechStartRealtimeMs = nowRealtime
                            speechStartNanos = System.nanoTime()
                            _engineState.value = WhisperEngineState.SPEECH_DETECTED
                            dispatchToMain {
                                try { listener.onBeginningOfSpeech() } catch (_: Throwable) {}
                            }

                            synchronized(utteranceLock) {
                                utteranceBuffer.clear()
                                val preRoll = preRollBuffer.getRecentSamples(4800)
                                for (s in preRoll) { utteranceBuffer.add(s) }
                            }
                        } else {
                            isSpeechActive = true
                        }
                    }

                    preRollBuffer.append(rawFloats)

                    val isRecentSpeech = (nowRealtime - lastSpeechDetectedRealtimeMs) < endpointSilenceMs
                    if (isSpeechActive || isRecentSpeech) {
                        synchronized(utteranceLock) {
                            for (f in rawFloats) { utteranceBuffer.add(f) }
                        }
                    }

                    val silenceDurationMs = if (isSpeechActive && lastSpeechDetectedRealtimeMs > 0L) (nowRealtime - lastSpeechDetectedRealtimeMs) else 0L
                    val actualElapsedSpeechMs = if (isSpeechActive && speechStartRealtimeMs > 0L) (nowRealtime - speechStartRealtimeMs) else 0L
                    val totalSpeechAccumulated = synchronized(utteranceLock) { utteranceBuffer.size }
                    val hasSubstantialAudio = totalSpeechAccumulated >= (SAMPLE_RATE * 0.25f).toInt()
                    val reachedMaxDuration = isSpeechActive && actualElapsedSpeechMs >= 15000L

                    if (isSpeechActive && hasSubstantialAudio && (silenceDurationMs >= endpointSilenceMs || reachedMaxDuration)) {
                        isSpeechActive = false
                        speechEndRealtimeMs = nowRealtime
                        speechEndNanos = System.nanoTime()
                        dispatchToMain {
                            try { listener.onUtteranceFinalizing() } catch (_: Throwable) {}
                        }
                        val finalFloats: FloatArray
                        val sStartNanos = speechStartNanos
                        val sEndNanos = speechEndNanos

                        synchronized(utteranceLock) {
                            val total = utteranceBuffer.size
                            // Preserve full speech and natural 300ms decay, trimming excess silence from pause detector
                            val excessSilenceSamples = ((silenceDurationMs - 300L).coerceAtLeast(0L) * SAMPLE_RATE / 1000L).toInt()
                            val keepSamples = (total - excessSilenceSamples).coerceIn((SAMPLE_RATE * 0.25f).toInt(), total)
                            finalFloats = FloatArray(keepSamples)
                            for (i in 0 until keepSamples) {
                                finalFloats[i] = utteranceBuffer[i]
                            }
                            utteranceBuffer.clear()
                        }

                        val finalBufferDurationMs = (finalFloats.size * 1000L) / SAMPLE_RATE
                        val stopReasonStr = if (reachedMaxDuration) "DURATION_LIMIT" else "SILENCE_ENDPOINT"
                        val stopDetail = if (reachedMaxDuration) "15s limit" else "${silenceDurationMs}ms pause"

                        try {
                            val tracker = com.taskscheduling.app.feature.diagnostics.VoiceBotDiagnosticTracker.getInstance(context)
                            tracker.recordLongSpeechTelemetry(
                                totalDurationMs = actualElapsedSpeechMs,
                                finalBufferDurationMs = finalBufferDurationMs,
                                buffersCount = (finalFloats.size / SAMPLES_PER_READ).coerceAtLeast(1),
                                isTruncatedByLimit = reachedMaxDuration,
                                stoppedBySilence = silenceDurationMs >= endpointSilenceMs,
                                stoppedByWordPause = silenceDurationMs >= endpointSilenceMs,
                                endpointSilenceMs = endpointSilenceMs,
                                timeline = listOf(
                                    com.taskscheduling.app.feature.diagnostics.TimelineStage("Recording Started", System.currentTimeMillis() - actualElapsedSpeechMs, details = "Speech detected"),
                                    com.taskscheduling.app.feature.diagnostics.TimelineStage("Speech Endpoint", System.currentTimeMillis(), durationMs = silenceDurationMs, details = stopDetail)
                                )
                            )
                            tracker.recordBotPipelineStage(
                                stageName = "Speech Endpoint Reached",
                                status = "SUCCESS",
                                details = stopDetail,
                                durationMs = silenceDurationMs,
                                sessionId = "sess_$sessionId"
                            )
                            tracker.recordAudioSessionLifecycle(
                                startTimeMs = System.currentTimeMillis() - actualElapsedSpeechMs,
                                stopTimeMs = System.currentTimeMillis(),
                                actualElapsedMs = actualElapsedSpeechMs,
                                successfulReads = totalSuccessfulReads,
                                samplesPerRead = SAMPLES_PER_READ,
                                bytesPerRead = SAMPLES_PER_READ * 2,
                                totalSamplesCaptured = totalSamplesCaptured,
                                totalBytesCaptured = totalSamplesCaptured * 2,
                                finalizedAudioDurationMs = finalBufferDurationMs,
                                stopReason = stopReasonStr,
                                samplesDiscarded = 0L,
                                finalBufferFlushed = true,
                                submittedToInference = hasAudibleSpeech(finalFloats)
                            )
                        } catch (_: Throwable) {}

                        utteranceIndex++
                        Log.i(CRASH_TAG, "[CONTINUOUS_TRACE] Utterance #$utteranceIndex finalized (${finalBufferDurationMs}ms, ${finalFloats.size} samples, reason=$stopReasonStr). Audio capture continues active (totalSamplesCaptured=$totalSamplesCaptured)")

                        if (hasAudibleSpeech(finalFloats)) {
                            dispatchUtterance(finalFloats, sStartNanos, sEndNanos, utteranceIndex)
                        } else {
                            Log.d(CRASH_TAG, "[CONTINUOUS_TRACE] Utterance #$utteranceIndex skipped: inaudible / background noise only (${finalFloats.size} samples)")
                        }
                    }
                }
            } catch (t: Throwable) {
                Log.e(CRASH_TAG, "[Whisper] Capture worker fault: ${t.message}", t)
                dispatchToMain {
                    try { listener.onError(SpeechEngineError.MicrophoneUnavailable("Capture error: ${t.message}")) } catch (_: Throwable) {}
                }
            }
        }

        private var utteranceIndex = 0

        private fun dispatchUtterance(audioSamples: FloatArray, startNanos: Long, endNanos: Long, index: Int = 0) {
            val uttId = "utt_${sessionId}_${index}_${System.currentTimeMillis()}"
            Log.i(CRASH_TAG, "[CONTINUOUS_TRACE] Dispatching utterance $uttId (${audioSamples.size} samples) to Whisper transcription service")
            submitTranscription(
                audioSamples = audioSamples,
                isFinal = true,
                listener = listener,
                speechStartNanos = startNanos,
                speechEndNanos = endNanos,
                customJobId = uttId,
                onTranscriptionFinished = {
                    Log.i(CRASH_TAG, "[CONTINUOUS_TRACE] Utterance $uttId transcription cycle finished. continuous=$continuous, recorderRunning=${isRunning.get()}")
                    if (!continuous && isRunning.get()) {
                        stop()
                    }
                }
            )
        }
    }
}
