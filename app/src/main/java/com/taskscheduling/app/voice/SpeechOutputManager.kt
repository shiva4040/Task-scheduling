package com.taskscheduling.app.voice

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Coordinates spoken text-to-speech output with microphone listening.
 * Ensures the assistant never captures or interprets its own voice as user speech.
 */
interface SpeechOutputManager {
    val isSpeaking: StateFlow<Boolean>
    fun speak(text: String, onSpeechStarted: () -> Unit = {}, onSpeechFinished: () -> Unit = {})
    fun stop()
    fun release()
}

class AndroidSpeechOutputManager(
    private val context: Context
) : SpeechOutputManager {

    private val _isSpeaking = MutableStateFlow(false)
    override val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val mainHandler = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var isInitialized = false

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                isInitialized = true
            }
        }
    }

    override fun speak(text: String, onSpeechStarted: () -> Unit, onSpeechFinished: () -> Unit) {
        val activeTts = tts
        if (!isInitialized || activeTts == null) {
            onSpeechStarted()
            onSpeechFinished()
            return
        }

        val utteranceId = "utterance_${System.currentTimeMillis()}"
        _isSpeaking.value = true
        onSpeechStarted()

        activeTts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                _isSpeaking.value = true
            }

            override fun onDone(utteranceId: String?) {
                mainHandler.post {
                    _isSpeaking.value = false
                    onSpeechFinished()
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                mainHandler.post {
                    _isSpeaking.value = false
                    onSpeechFinished()
                }
            }
        })

        activeTts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    override fun stop() {
        _isSpeaking.value = false
        try {
            tts?.stop()
        } catch (_: Throwable) {}
    }

    override fun release() {
        stop()
        try {
            tts?.shutdown()
        } catch (_: Throwable) {}
        tts = null
        isInitialized = false
    }
}
