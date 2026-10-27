package com.taskscheduling.app.voice

/**
 * Callbacks for offline speech recognition events.
 */
interface SpeechRecognitionCallback {
    fun onReadyForSpeech()
    fun onBeginningOfSpeech()
    fun onRmsChanged(rmsdB: Float) {}
    fun onSpeechRecognized(text: String)
    fun onError(errorMessage: String)
    fun onStopped()
}
