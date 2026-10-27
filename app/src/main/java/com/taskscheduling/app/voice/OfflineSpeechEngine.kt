package com.taskscheduling.app.voice

/**
 * Interface isolating the offline embedded speech recognition engine.
 * Extends [SpeechRecognitionEngine] for full engine abstraction compatibility.
 */
interface OfflineSpeechEngine : SpeechRecognitionEngine {
    override val engineType: SpeechEngineType
        get() = SpeechEngineType.VOSK

    override val isRunning: Boolean
        get() = false

    override val configuredSampleRate: Int
        get() = 16000

    override val activeAudioSource: String
        get() = "VOICE_RECOGNITION"

    override val configuredBufferSize: Int
        get() = 4096

    override fun pauseListening() {}
    override fun resumeListening() {}
    override fun cancelListening() { stopListening() }
}

/**
 * Callbacks emitted by the offline speech engine.
 */
interface SpeechEngineListener {
    fun onReady()
    fun onBeginningOfSpeech()
    fun onUtteranceFinalizing() {}
    fun onPartialResult(hypothesis: String) {}
    fun onResult(recognizedText: String)
    fun onError(error: SpeechEngineError)
    fun onStopped()
    fun onAudioLevel(rmsLevel: Float, peakLevel: Float, inputDetected: Boolean) {}
}

/**
 * Discrete error categories for embedded offline speech recognition.
 */
sealed class SpeechEngineError {
    object MissingModel : SpeechEngineError()
    data class InitializationFailed(val message: String) : SpeechEngineError()
    data class MicrophoneUnavailable(val message: String) : SpeechEngineError()
    object MicrophonePermissionDenied : SpeechEngineError()
    data class RecognitionFailed(val message: String) : SpeechEngineError()
    object Timeout : SpeechEngineError()
    object EmptySpeech : SpeechEngineError()

    fun toUserMessage(): String = when (this) {
        is MissingModel -> "Offline voice model is missing."
        is InitializationFailed -> "Offline voice recognition could not be started."
        is MicrophonePermissionDenied ->
            "Microphone permission is required for voice commands. You can still type commands."
        is MicrophoneUnavailable -> "Microphone is unavailable. You can still type commands."
        is RecognitionFailed -> if (message.isNotBlank() && !message.contains("Recognition failed", ignoreCase = true)) message else "I couldn't understand that."
        is Timeout -> "No speech detected. Please try again."
        is EmptySpeech -> "Speech captured, but no text was recognized."
    }
}
