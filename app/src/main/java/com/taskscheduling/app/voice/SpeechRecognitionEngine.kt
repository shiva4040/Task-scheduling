package com.taskscheduling.app.voice

/**
 * Supported offline speech recognition engines.
 */
enum class SpeechEngineType(val displayName: String) {
    WHISPER_OFFLINE("Whisper Offline (Fast Multilingual)"),
    WHISPER_SMALL_Q5_1("Whisper Small Q5_1 (Offline Multilingual)"),
    @Deprecated("Retired in favor of Whisper")
    SHERPA_ONNX("Sherpa-ONNX (Streaming Zipformer)"),
    @Deprecated("Retired in favor of Whisper")
    VOSK("Vosk (Legacy Offline)")
}

/**
 * Strictly supported user-facing language selection modes for speech recognition:
 * 1. English - Whisper "en" token, no redundant detection pass
 * 2. Hindi - Whisper "hi" token, preserves Devanagari Unicode
 * 3. Hinglish - Natural code-switching mode using multilingual engine, translate=false
 */
enum class LanguageMode(val code: String, val displayName: String) {
    ENGLISH("en", "English"),
    HINDI("hi", "Hindi"),
    HINGLISH("auto", "Hinglish");

    val whisperCode: String get() = code

    companion object {
        @JvmField
        val AUTO = HINGLISH // Direct alias ensuring Hinglish maps to auto language code-switching
    }
}

/**
 * Unified clean interface for offline on-device speech recognition engines.
 * Isolates speech-to-text processing from assistant command logic and automation workflows.
 */
interface SpeechRecognitionEngine {
    val engineType: SpeechEngineType
    val isModelLoaded: Boolean
    val isRunning: Boolean
    val configuredSampleRate: Int
    val activeAudioSource: String
    val configuredBufferSize: Int
    var isMuted: Boolean
        get() = false
        set(_) {}

    /**
     * Initializes the engine and loads the local on-device acoustic and language models.
     */
    suspend fun initialize(): Result<Unit>

    /**
     * Starts listening for audio input from the device microphone.
     * @param listener Callbacks for speech events (ready, partial, final result, error, audio level).
     * @param continuous If true, keeps listening after an utterance completes without tearing down the microphone.
     */
    fun startListening(listener: SpeechEngineListener, continuous: Boolean = false): Boolean

    /**
     * Temporarily pauses audio processing (e.g. during assistant TTS speech output or physical gesture execution).
     */
    fun pauseListening()

    /**
     * Resumes audio processing after a pause.
     */
    fun resumeListening()

    /**
     * Stops listening and processes any remaining speech buffer.
     */
    fun stopListening()

    /**
     * Cancels active recognition immediately without processing.
     */
    fun cancelListening()

    /**
     * Releases native, memory, and audio capture resources.
     */
    fun release()
}
