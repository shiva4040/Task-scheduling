package com.taskscheduling.app.voice

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException

/**
 * Structured transcription result returned by [SpeechRecognitionService].
 */
data class SpeechRecognitionResult(
    val text: String,
    val rawText: String = text,
    val isSuccess: Boolean,
    val processingTimeMs: Long = 0L,
    val language: String = "",
    val segmentCount: Int = 0,
    val jobId: String = "",
    val isCancelled: Boolean = false,
    val error: String? = null,
    val captureMs: Long = 0L,
    val preprocessingMs: Long = 0L,
    val lockWaitMs: Long = 0L,
    val nativeInferenceMs: Long = 0L,
    val resultProcessingMs: Long = 0L,
    val totalSubmissionToResultMs: Long = 0L,
    val audioDurationMs: Long = 0L,
    val sampleCount: Int = 0,
    val modelIdentifier: String = "",
    val callerName: String = "",
    val stageTrace: VoiceStageTrace? = null
) {
    val captureDurationMs: Long get() = captureMs
    val audioPreprocessingMs: Long get() = preprocessingMs
    val totalInferenceCallMs: Long get() = nativeInferenceMs
    val totalEndToEndMs: Long get() = totalSubmissionToResultMs
}

/**
 * Single shared public entry point for speech recognition across the application.
 *
 * Guarantees:
 * 1. Single model instance in RAM shared between diagnostics and floating bot.
 * 2. Shared inference lock, execution state machine, and cancellation lifecycle.
 * 3. Unified audio preprocessing, audible speech validation, and native Whisper execution.
 * 4. Transparent error and status reporting without silent fallback.
 */
class SpeechRecognitionService(
    private val context: Context,
    val engine: WhisperCppSpeechRecognitionEngine = WhisperCppSpeechRecognitionEngine.getInstance(context)
) {
    companion object {
        const val TAG = "SpeechRecognitionService"

        @Volatile
        private var instance: SpeechRecognitionService? = null
        private val lock = Any()

        /**
         * Returns the process-wide singleton provider of [SpeechRecognitionService].
         */
        fun getInstance(context: Context): SpeechRecognitionService {
            return instance ?: synchronized(lock) {
                instance ?: SpeechRecognitionService(context.applicationContext).also {
                    instance = it
                }
            }
        }

        fun setInstanceForTesting(service: SpeechRecognitionService?) {
            synchronized(lock) {
                instance = service
            }
        }
    }

    /**
     * Unified entry point for transcribing audio samples.
     * Both the diagnostic screens and the floating assistant bot use this exact entry point.
     *
     * @param audioSamples Float array of 16kHz mono audio samples normalized to [-1.0f, 1.0f].
     * @param languageMode Language mode: [LanguageMode.ENGLISH], [LanguageMode.HINDI], or [LanguageMode.HINGLISH].
     * @param source Identifier of the caller ("BOT", "DIAGNOSTIC", "PHYSICAL_TEST").
     * @param customJobId Optional unique job identifier for tracking or cancellation.
     */
    suspend fun transcribe(
        audioSamples: FloatArray,
        languageMode: LanguageMode = LanguageMode.ENGLISH,
        source: String = "SHARED",
        customJobId: String? = null
    ): SpeechRecognitionResult {
        val audioDurationMs = if (audioSamples.isNotEmpty()) (audioSamples.size * 1000L) / 16000L else 0L
        if (audioSamples.isEmpty() || !engine.hasAudibleSpeech(audioSamples)) {
            Log.d(TAG, "[$source] Inaudible or empty audio buffer rejected (${audioSamples.size} samples)")
            return SpeechRecognitionResult(
                text = "",
                rawText = "",
                isSuccess = true,
                processingTimeMs = 0L,
                language = languageMode.code,
                segmentCount = 0,
                jobId = customJobId ?: "",
                isCancelled = false,
                error = null,
                captureMs = audioDurationMs,
                audioDurationMs = audioDurationMs,
                sampleCount = audioSamples.size,
                callerName = source
            )
        }

        val result = engine.transcribeDirect(
            audioSamples = audioSamples,
            mode = languageMode,
            source = source,
            customJobId = customJobId
        )

        return if (result.isSuccess) {
            val r = result.getOrThrow()
            SpeechRecognitionResult(
                text = r.text,
                rawText = r.rawText,
                isSuccess = true,
                processingTimeMs = r.nativeInferenceMs.toLong(),
                language = r.language,
                segmentCount = r.segmentCount,
                jobId = customJobId ?: "",
                isCancelled = false,
                error = null,
                captureMs = r.audioDurationMs,
                preprocessingMs = r.preprocessingMs.toLong(),
                lockWaitMs = r.lockWaitMs.toLong(),
                nativeInferenceMs = r.nativeInferenceMs.toLong(),
                resultProcessingMs = r.resultProcessingMs.toLong(),
                totalSubmissionToResultMs = r.totalSubmissionToResultMs.toLong(),
                audioDurationMs = r.audioDurationMs,
                sampleCount = r.sampleCount,
                modelIdentifier = r.modelIdentifier,
                callerName = source,
                stageTrace = VoiceStageTrace(
                    requestId = customJobId ?: "",
                    audioDurationMs = r.audioDurationMs,
                    endpointWaitMs = 0.0,
                    queueWaitMs = r.lockWaitMs,
                    modelLoadMs = engine.modelLoadTimeMs,
                    preprocessingMs = r.preprocessingMs,
                    nativeCallMs = r.nativeInferenceMs,
                    resultProcessingMs = r.resultProcessingMs,
                    uiDeliveryMs = 0.0,
                    totalSpeechToResultMs = r.totalSubmissionToResultMs,
                    modelPath = engine.modelManager.modelFile.absolutePath,
                    modelInitCount = r.modelInitCount,
                    inferenceInvocationCount = r.inferenceInvocationCount,
                    audioSampleCount = r.sampleCount,
                    threadCount = engine.nativeBridge.threads,
                    isModelWarm = r.isModelWarm,
                    tJniCallBeginNs = r.tJniStartNs,
                    tJniCallEndNs = r.tJniEndNs,
                    tNativeInferenceBeginNs = r.tJniStartNs,
                    tNativeInferenceEndNs = r.tJniEndNs,
                    tNativeResultReturnNs = r.tNativeResultNs,
                    tTranscriptCleanupCompleteNs = r.tCleanupCompleteNs,
                    transcript = r.text
                )
            )
        } else {
            val ex = result.exceptionOrNull()
            val isCancelled = ex is CancellationException || ex?.message?.contains("cancelled", ignoreCase = true) == true
            SpeechRecognitionResult(
                text = "",
                rawText = "",
                isSuccess = false,
                processingTimeMs = 0L,
                language = languageMode.code,
                segmentCount = 0,
                jobId = customJobId ?: "",
                isCancelled = isCancelled,
                error = ex?.message ?: "Transcription failed",
                captureMs = audioDurationMs,
                audioDurationMs = audioDurationMs,
                sampleCount = audioSamples.size,
                callerName = source
            )
        }
    }
}
