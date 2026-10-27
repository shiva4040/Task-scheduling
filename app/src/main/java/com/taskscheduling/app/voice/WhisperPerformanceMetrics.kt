package com.taskscheduling.app.voice

/**
 * Diagnostics and telemetry metrics for the Whisper speech recognition pipeline.
 * Captures sub-millisecond nanosecond-precision timings for each stage:
 * Microphone -> audio capture -> endpoint detection -> audio preparation ->
 * transcription queue -> JNI mutex -> whisper.cpp inference -> transcript normalization -> UI delivery.
 */
data class WhisperPerformanceMetrics(
    val utteranceId: String = "",
    val selectedModelName: String = "",
    val modelSizeBytes: Long = 0L,
    val threads: Int = 4,
    val languageMode: String = "",
    val modelLoadTimeMs: Long = 0L,
    val endpointDetectionMs: Double = 0.0,
    val audioFinalizationMs: Double = 0.0,
    val submissionToResultMs: Double = 0.0,
    val visibleDisplayMs: Double = 0.0,
    val captureMs: Double = 0.0,
    val audioPreparationMs: Double = 0.0,
    val queueWaitMs: Double = 0.0,
    val nativeMutexWaitMs: Double = 0.0,
    val nativeInferenceMs: Double = 0.0,
    val resultProcessingMs: Double = 0.0,
    val uiDeliveryMs: Double = 0.0,
    val speechEndToResultMs: Double = 0.0,
    val totalMs: Double = 0.0,
    val timeToFirstPartialMs: Long = 0L,
    val timeToFinalMs: Long = 0L,
    val audioCaptureLatencyMs: Long = 0L,
    val decodeTimeMs: Long = 0L,
    val realTimeFactor: Float = 0f,
    val nativeModelMemoryMB: Float = 0f,
    val appMemoryMB: Float = 0f,
    val availableMemoryMB: Float = 0f,
    val rmsLevel: Float = 0f,
    val peakLevel: Float = 0f,
    val minSample: Short = 0,
    val maxSample: Short = 0,
    val zeroPercent: Float = 0f,
    val aecAvailable: Boolean = false,
    val aecEnabled: Boolean = false,
    val nsAvailable: Boolean = false,
    val nsEnabled: Boolean = false,
    val agcAvailable: Boolean = false,
    val agcEnabled: Boolean = false,
    val audioDurationMs: Long = 0L,
    val jniOverheadMs: Double = 0.0,
    val featureExtractionMs: Double = 0.0,
    val encoderMs: Double = 0.0,
    val decoderMs: Double = 0.0,
    val stageTrace: VoiceStageTrace? = null
) {
    val captureDurationMs: Double get() = captureMs
    val endpointingMs: Double get() = endpointDetectionMs
    val audioPreprocessingMs: Double get() = audioPreparationMs
    val modelInitializationMs: Long get() = modelLoadTimeMs
    val modelLoadMs: Long get() = modelLoadTimeMs
    val lockWaitMs: Double get() = nativeMutexWaitMs
    val totalInferenceCallMs: Double get() = nativeInferenceMs + jniOverheadMs
    val totalEndToEndMs: Double get() = totalMs

    fun formatMetricsSummary(): String {
        return buildString {
            append("Utterance: $utteranceId | Model: $selectedModelName (${modelSizeBytes / (1024 * 1024)}MB, $threads thr, $languageMode)\n")
            append("Timing: capture=${"%.1f".format(captureMs)}ms | prep=${"%.1f".format(audioPreparationMs)}ms | queue=${"%.1f".format(queueWaitMs)}ms | endpoint=${"%.1f".format(endpointDetectionMs)}ms\n")
            append("Native: mutex=${"%.1f".format(nativeMutexWaitMs)}ms | inference=${"%.1f".format(nativeInferenceMs)}ms | proc=${"%.1f".format(resultProcessingMs)}ms\n")
            append("Delivery: ui=${"%.1f".format(uiDeliveryMs)}ms | speechEndToResult=${"%.1f".format(speechEndToResultMs)}ms | submissionToResult=${"%.1f".format(submissionToResultMs)}ms | total=${"%.1f".format(totalMs)}ms\n")
            append("Stage 1 Baseline Breakdown:\n")
            append("  capture_duration_ms: ${"%.1f".format(captureMs)}\n")
            append("  endpointing_ms: ${"%.1f".format(endpointDetectionMs)}\n")
            append("  audio_preprocessing_ms: ${"%.1f".format(audioPreparationMs)}\n")
            append("  jni_overhead_ms: ${"%.1f".format(jniOverheadMs)}\n")
            append("  model_initialization_ms: $modelLoadTimeMs\n")
            append("  feature_extraction_ms: ${"%.1f".format(featureExtractionMs)}\n")
            append("  encoder_ms: ${"%.1f".format(encoderMs)}\n")
            append("  decoder_ms: ${"%.1f".format(decoderMs)}\n")
            append("  result_processing_ms: ${"%.1f".format(resultProcessingMs)}\n")
            append("  queue_wait_ms: ${"%.1f".format(queueWaitMs)}\n")
            append("  lock_wait_ms: ${"%.1f".format(nativeMutexWaitMs)}\n")
            append("  total_inference_call_ms: ${"%.1f".format(totalInferenceCallMs)}\n")
            append("  total_end_to_end_ms: ${"%.1f".format(totalMs)}")
        }
    }
}

/**
 * High-precision stage-by-stage boundary trace for real voice tests.
 * Captures monotonic timestamps at every transition boundary and formats
 * standard CSV telemetry:
 * request_id, audio_duration_ms, endpoint_wait_ms, queue_wait_ms, model_load_ms, preprocessing_ms, native_call_ms, result_processing_ms, ui_delivery_ms, total_speech_to_result_ms
 */
data class VoiceStageTrace(
    val requestId: String = "",
    val audioDurationMs: Long = 0L,
    val endpointWaitMs: Double = 0.0,
    val queueWaitMs: Double = 0.0,
    val modelLoadMs: Long = 0L,
    val preprocessingMs: Double = 0.0,
    val nativeCallMs: Double = 0.0,
    val resultProcessingMs: Double = 0.0,
    val uiDeliveryMs: Double = 0.0,
    val totalSpeechToResultMs: Double = 0.0,

    val modelPath: String = "",
    val modelInitCount: Int = 1,
    val inferenceInvocationCount: Int = 1,
    val audioSampleCount: Int = 0,
    val threadCount: Int = 4,
    val isModelWarm: Boolean = true,

    // Monotonic boundary timestamps (System.nanoTime()):
    val tMicCaptureBeginNs: Long = 0L,
    val tLastSpeechSampleNs: Long = 0L,
    val tEndpointDetectionCompleteNs: Long = 0L,
    val tAudioSubmittedNs: Long = 0L,
    val tQueueEntryNs: Long = 0L,
    val tQueueExitNs: Long = 0L,
    val tEngineInitBeginNs: Long = 0L,
    val tEngineInitEndNs: Long = 0L,
    val tAudioPreprocBeginNs: Long = 0L,
    val tAudioPreprocEndNs: Long = 0L,
    val tJniCallBeginNs: Long = 0L,
    val tJniCallEndNs: Long = 0L,
    val tNativeInferenceBeginNs: Long = 0L,
    val tNativeInferenceEndNs: Long = 0L,
    val tNativeResultReturnNs: Long = 0L,
    val tTranscriptCleanupCompleteNs: Long = 0L,
    val tUiReceiveAndRenderNs: Long = 0L,
    val transcript: String = ""
) {
    fun toCsvLine(): String {
        return "$requestId, $audioDurationMs, ${"%.1f".format(endpointWaitMs)}, ${"%.1f".format(queueWaitMs)}, $modelLoadMs, ${"%.1f".format(preprocessingMs)}, ${"%.1f".format(nativeCallMs)}, ${"%.1f".format(resultProcessingMs)}, ${"%.1f".format(uiDeliveryMs)}, ${"%.1f".format(totalSpeechToResultMs)}"
    }
}
