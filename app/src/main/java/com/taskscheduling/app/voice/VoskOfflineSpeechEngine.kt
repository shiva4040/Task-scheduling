package com.taskscheduling.app.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.AssetManager
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Embedded offline speech recognition engine powered by Vosk.
 * Completely self-contained with bundled local acoustic and language models.
 * Optimized for far-field capture, device hardware speech processing (AEC, NS, AGC),
 * and sensible natural-cadence silence detection.
 */
class VoskOfflineSpeechEngine(
    private val context: Context,
    private val modelAssetFolder: String = "model-en-us"
) : OfflineSpeechEngine {

    private var model: Model? = null
    private var recognizer: Recognizer? = null
    private val recognizerLock = Any()
    private val workerLock = Any()
    private var audioRecordWorker: AudioRecordWorker? = null

    override val engineType: SpeechEngineType
        get() = SpeechEngineType.VOSK

    override val isModelLoaded: Boolean
        get() = model != null

    override val isRunning: Boolean
        get() = synchronized(workerLock) { audioRecordWorker?.isRecording ?: false }

    override val activeAudioSource: String
        get() = synchronized(workerLock) { audioRecordWorker?.activeAudioSource ?: "VOICE_RECOGNITION" }

    override val configuredSampleRate: Int
        get() = SAMPLE_RATE

    override val configuredBufferSize: Int
        get() = synchronized(workerLock) { audioRecordWorker?.bufferSizeBytes ?: 4096 }

    val audioSourceUsed: String
        get() = activeAudioSource

    val isAudioRecording: Boolean
        get() = isRunning

    val configuredBufferSizeBytes: Int
        get() = configuredBufferSize

    companion object {
        const val TAG = "TaskSchedulingAudio"
        const val SAMPLE_RATE = 16000
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

        // Listening windows for far-field capture and natural speech cadence
        const val INITIAL_SPEECH_TIMEOUT_MS = 8000L   // Time allowed to begin speaking after mic tap
        const val SILENCE_AFTER_SPEECH_MS = 1200L     // Trailing silence before finalizing command
        const val MAX_SESSION_TIMEOUT_MS = 12000L     // Absolute maximum listening duration
    }

    override suspend fun initialize(): Result<Unit> = withContext(Dispatchers.IO) {
        if (model != null) return@withContext Result.success(Unit)

        try {
            val targetDir = File(context.filesDir, "vosk-model-en-us")
            val isModelReady = File(targetDir, "am/final.mdl").exists() &&
                    File(targetDir, "conf/model.conf").exists()

            if (!isModelReady) {
                val assetList = context.assets.list(modelAssetFolder)
                if (assetList.isNullOrEmpty()) {
                    return@withContext Result.failure(
                        IllegalStateException("Offline voice model is missing from application assets: $modelAssetFolder")
                    )
                }

                targetDir.mkdirs()
                copyAssetFolder(context.assets, modelAssetFolder, targetDir)
            } else {
                // Ensure configuration files (conf/model.conf, conf/mfcc.conf) stay updated from assets
                try {
                    copyAssetFile(context.assets, "$modelAssetFolder/conf/model.conf", File(targetDir, "conf/model.conf"))
                    copyAssetFile(context.assets, "$modelAssetFolder/conf/mfcc.conf", File(targetDir, "conf/mfcc.conf"))
                } catch (e: Exception) {
                    Log.w(TAG, "Could not refresh conf files from assets: ${e.message}")
                }
            }

            if (!File(targetDir, "am/final.mdl").exists()) {
                return@withContext Result.failure(
                    IllegalStateException("Model files failed extraction to: ${targetDir.absolutePath}")
                )
            }

            val loadedModel = Model(targetDir.absolutePath)
            model = loadedModel

            // Initialize recognizer in general unrestricted speech recognition mode (no grammar limitation)
            synchronized(recognizerLock) {
                recognizer = Recognizer(loadedModel, SAMPLE_RATE.toFloat())
            }
            Result.success(Unit)
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    override fun startListening(listener: SpeechEngineListener, continuous: Boolean): Boolean {
        // 1. Runtime permission check
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            listener.onError(SpeechEngineError.MicrophonePermissionDenied)
            return false
        }

        // 2. Ensure model is loaded
        val activeModel = model
        if (activeModel == null) {
            listener.onError(SpeechEngineError.MissingModel)
            return false
        }

        // Cleanly terminate any active recording session under workerLock to prevent race conditions
        synchronized(workerLock) {
            stopListeningInternal()

            val activeRecognizer = synchronized(recognizerLock) {
                val rec = recognizer ?: Recognizer(activeModel, SAMPLE_RATE.toFloat()).also {
                    recognizer = it
                }
                try {
                    rec.reset()
                } catch (_: Throwable) {}
                rec
            }

            val worker = AudioRecordWorker(activeRecognizer, listener, continuous = continuous)
            val started = worker.start()
            if (started) {
                audioRecordWorker = worker
                return true
            } else {
                audioRecordWorker = null
                return false
            }
        }
    }

    override fun pauseListening() {
        synchronized(workerLock) {
            audioRecordWorker?.pause()
        }
    }

    override fun resumeListening() {
        synchronized(workerLock) {
            audioRecordWorker?.resume()
        }
    }

    override fun stopListening() {
        synchronized(workerLock) {
            stopListeningInternal()
        }
    }

    private fun stopListeningInternal() {
        audioRecordWorker?.stop()
        audioRecordWorker = null
    }

    override fun cancelListening() {
        synchronized(workerLock) {
            audioRecordWorker?.cancel()
            audioRecordWorker = null
        }
    }

    override fun release() {
        stopListening()
        synchronized(recognizerLock) {
            try {
                recognizer?.close()
            } catch (_: Throwable) {}
            recognizer = null

            try {
                model?.close()
            } catch (_: Throwable) {}
            model = null
        }
    }

    /**
     * Dedicated background audio capture worker.
     * Uses VOICE_RECOGNITION audio source, attaches hardware AEC / NS / AGC effects when available,
     * and manages speech endpointing and silence detection.
     */
    private inner class AudioRecordWorker(
        private val recognizer: Recognizer,
        private val listener: SpeechEngineListener,
        private val continuous: Boolean = false
    ) {
        private val isRunning = AtomicBoolean(false)
        private val isPaused = AtomicBoolean(false)
        private val mainHandler = Handler(Looper.getMainLooper())
        private var audioRecord: AudioRecord? = null
        private var noiseSuppressor: NoiseSuppressor? = null
        private var echoCanceler: AcousticEchoCanceler? = null
        private var gainControl: AutomaticGainControl? = null
        private var workerThread: Thread? = null

        var activeAudioSource: String = "VOICE_RECOGNITION"
            private set

        val isRecording: Boolean
            get() = isRunning.get()

        var bufferSizeBytes: Int = 4096
            private set

        fun pause() {
            isPaused.set(true)
        }

        fun resume() {
            isPaused.set(false)
        }

        fun start(): Boolean {
            val minBufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT
            ).coerceAtLeast(4096)
            bufferSizeBytes = minBufferSize

            var recorder: AudioRecord? = null
            var sourceUsed = MediaRecorder.AudioSource.VOICE_RECOGNITION

            // 1. Prefer VOICE_RECOGNITION for directional voice tuning and hardware preprocessing
            try {
                val rec = AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    minBufferSize * 4
                )
                if (rec.state == AudioRecord.STATE_INITIALIZED) {
                    recorder = rec
                    sourceUsed = MediaRecorder.AudioSource.VOICE_RECOGNITION
                } else {
                    rec.release()
                }
            } catch (e: Exception) {
                Log.w(TAG, "VOICE_RECOGNITION source unavailable, falling back to MIC: ${e.message}")
            }

            // Fallback to standard MIC if VOICE_RECOGNITION is unsupported by hardware
            if (recorder == null) {
                try {
                    val rec = AudioRecord(
                        MediaRecorder.AudioSource.MIC,
                        SAMPLE_RATE,
                        CHANNEL_CONFIG,
                        AUDIO_FORMAT,
                        minBufferSize * 4
                    )
                    if (rec.state == AudioRecord.STATE_INITIALIZED) {
                        recorder = rec
                        sourceUsed = MediaRecorder.AudioSource.MIC
                    } else {
                        rec.release()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to initialize AudioRecord with MIC fallback: ${e.message}")
                }
            }

            if (recorder == null) {
                mainHandler.post {
                    listener.onError(SpeechEngineError.MicrophoneUnavailable("Failed to initialize AudioRecord"))
                }
                return false
            }

            audioRecord = recorder
            activeAudioSource = if (sourceUsed == MediaRecorder.AudioSource.VOICE_RECOGNITION) "VOICE_RECOGNITION" else "MIC"

            // 2. Attach available hardware audio effects (AEC, NS, AGC) to improve far-field speech
            val sessionId = recorder.audioSessionId
            if (sessionId != 0) {
                try {
                    if (NoiseSuppressor.isAvailable()) {
                        noiseSuppressor = NoiseSuppressor.create(sessionId)?.apply { enabled = true }
                    }
                } catch (t: Throwable) {
                    Log.w(TAG, "NoiseSuppressor init error: ${t.message}")
                }

                try {
                    if (AcousticEchoCanceler.isAvailable()) {
                        echoCanceler = AcousticEchoCanceler.create(sessionId)?.apply { enabled = true }
                    }
                } catch (t: Throwable) {
                    Log.w(TAG, "AcousticEchoCanceler init error: ${t.message}")
                }

                try {
                    if (AutomaticGainControl.isAvailable()) {
                        gainControl = AutomaticGainControl.create(sessionId)?.apply { enabled = true }
                    }
                } catch (t: Throwable) {
                    Log.w(TAG, "AutomaticGainControl init error: ${t.message}")
                }
            }

            // 3. Log actual hardware device configuration as requested in Section 13.3
            val sourceName = activeAudioSource
            Log.d(TAG, """
                [AUDIO]
                source=$sourceName
                sampleRate=$SAMPLE_RATE
                channels=MONO
                bufferSizeBytes=$minBufferSize
                noiseSuppression=${noiseSuppressor?.enabled ?: false}
                echoCancellation=${echoCanceler?.enabled ?: false}
                automaticGainControl=${gainControl?.enabled ?: false}
            """.trimIndent())

            isRunning.set(true)

            val thread = Thread({
                runAudioLoop(recorder, minBufferSize)
            }, "TaskScheduling-VoskAudioWorker")

            workerThread = thread
            thread.start()
            return true
        }

        private fun runAudioLoop(recorder: AudioRecord, minBufferSize: Int) {
            // Read in 50ms - 100ms chunks (800 - 1600 samples @ 16kHz) for low-latency streaming
            val readChunkShorts = (minBufferSize / 4).coerceIn(800, 1600)
            val buffer = ShortArray(readChunkShorts)
            var hasDetectedSpeech = false
            var lastSpeechTime = 0L
            var lastPartialText = ""
            var sessionStartTime = System.currentTimeMillis()
            var wasPaused = false
            var consecutiveReadErrors = 0
            val maxConsecutiveErrors = 5

            try {
                recorder.startRecording()
                mainHandler.post {
                    listener.onReady()
                    listener.onBeginningOfSpeech()
                }

                while (isRunning.get()) {
                    val now = System.currentTimeMillis()

                    if (isPaused.get()) {
                        wasPaused = true
                        recorder.read(buffer, 0, buffer.size)
                        try {
                            Thread.sleep(20)
                        } catch (_: InterruptedException) {}
                        continue
                    }

                    // Resuming from pause: reset speech state and recognizer safely on the audio thread
                    if (wasPaused) {
                        wasPaused = false
                        hasDetectedSpeech = false
                        lastSpeechTime = 0L
                        lastPartialText = ""
                        sessionStartTime = now
                        consecutiveReadErrors = 0
                        synchronized(recognizerLock) {
                            try { recognizer.reset() } catch (_: Throwable) {}
                        }
                    }

                    if (!continuous) {
                        // Check total session timeout
                        if (now - sessionStartTime > MAX_SESSION_TIMEOUT_MS) {
                            finalizeRecognition()
                            break
                        }

                        // Check initial silence timeout (no speech begun after 8 seconds)
                        if (!hasDetectedSpeech && (now - sessionStartTime > INITIAL_SPEECH_TIMEOUT_MS)) {
                            isRunning.set(false)
                            mainHandler.post {
                                listener.onError(SpeechEngineError.Timeout)
                                listener.onStopped()
                            }
                            break
                        }
                    } else {
                        // In continuous mode, silence does not stop listening
                        if (!hasDetectedSpeech && (now - sessionStartTime > INITIAL_SPEECH_TIMEOUT_MS)) {
                            sessionStartTime = now
                        }
                    }

                    val nread = recorder.read(buffer, 0, buffer.size)
                    if (nread > 0 && isRunning.get() && !isPaused.get()) {
                        consecutiveReadErrors = 0
                        val isEndpoint = synchronized(recognizerLock) {
                            recognizer.acceptWaveForm(buffer, nread)
                        }

                        // Inspect partial hypothesis for live UI feedback
                        val partialRaw = synchronized(recognizerLock) {
                            recognizer.partialResult
                        }
                        val partialText = cleanRecognizedText(parseHypothesisText(partialRaw))

                        if (partialText.isNotBlank() && partialText != "[unk]") {
                            Log.d(TAG, "[VOSK_PARTIAL] $partialText")
                            hasDetectedSpeech = true
                            lastSpeechTime = now
                            if (partialText != lastPartialText) {
                                lastPartialText = partialText
                                mainHandler.post {
                                    listener.onPartialResult(partialText)
                                }
                            }
                        }

                        // If phrase endpoint boundary detected by Vosk
                        if (isEndpoint) {
                            val resultRaw = synchronized(recognizerLock) {
                                recognizer.result
                            }
                            Log.d(TAG, "[VOSK_FINAL] $resultRaw")
                            val recognized = cleanRecognizedText(parseHypothesisText(resultRaw))
                            if (recognized.isNotBlank() && recognized != "[unk]") {
                                if (continuous) {
                                    mainHandler.post {
                                        listener.onResult(recognized)
                                    }
                                    hasDetectedSpeech = false
                                    lastSpeechTime = 0L
                                    lastPartialText = ""
                                    sessionStartTime = now
                                    // In Vosk C++, Recognizer::Result() already re-initializes decoding for the next utterance.
                                    // Do NOT call recognizer.reset() here to avoid asserting num_toks_ == 0.
                                } else {
                                    isRunning.set(false)
                                    mainHandler.post {
                                        listener.onResult(recognized)
                                        listener.onStopped()
                                    }
                                    break
                                }
                            }
                        }

                        // Inter-speech trailing silence detection (user completed command naturally)
                        if (hasDetectedSpeech && (now - lastSpeechTime > SILENCE_AFTER_SPEECH_MS)) {
                            if (continuous) {
                                val finalRaw = synchronized(recognizerLock) {
                                    recognizer.finalResult
                                }
                                Log.d(TAG, "[VOSK_FINAL] $finalRaw")
                                val recognized = cleanRecognizedText(parseHypothesisText(finalRaw))
                                if (recognized.isNotBlank() && recognized != "[unk]") {
                                    mainHandler.post {
                                        listener.onResult(recognized)
                                    }
                                }
                                hasDetectedSpeech = false
                                lastSpeechTime = 0L
                                lastPartialText = ""
                                sessionStartTime = now
                                // In Vosk, FinalResult() finalizes decoding, so we MUST call reset() before accepting new waveform
                                synchronized(recognizerLock) {
                                    try { recognizer.reset() } catch (_: Throwable) {}
                                }
                            } else {
                                finalizeRecognition()
                                break
                            }
                        }
                    } else if (nread < 0) {
                        consecutiveReadErrors++
                        Log.e(TAG, "AudioRecord read returned error code: $nread (consecutive: $consecutiveReadErrors)")
                        if (consecutiveReadErrors >= maxConsecutiveErrors) {
                            if (isRunning.get()) {
                                mainHandler.post {
                                    listener.onError(
                                        SpeechEngineError.RecognitionFailed("Audio capture failed with error code $nread")
                                    )
                                    listener.onStopped()
                                }
                            }
                            break
                        } else {
                            try { Thread.sleep(30) } catch (_: InterruptedException) {}
                        }
                    } else {
                        // nread == 0
                        consecutiveReadErrors = 0
                    }
                }
            } catch (e: Exception) {
                if (isRunning.get()) {
                    mainHandler.post {
                        listener.onError(
                            SpeechEngineError.RecognitionFailed(e.message ?: "Audio capture error")
                        )
                        listener.onStopped()
                    }
                }
            } finally {
                cleanupAudioResources()
            }
        }

        private fun finalizeRecognition() {
            if (!isRunning.getAndSet(false)) return
            val finalRaw = synchronized(recognizerLock) {
                try {
                    recognizer.finalResult
                } catch (_: Throwable) {
                    ""
                }
            }
            Log.d(TAG, "[VOSK_FINAL] $finalRaw")
            val recognized = cleanRecognizedText(parseHypothesisText(finalRaw))
            mainHandler.post {
                if (recognized.isNotBlank() && recognized != "[unk]") {
                    listener.onResult(recognized)
                } else {
                    listener.onError(SpeechEngineError.EmptySpeech)
                }
                listener.onStopped()
            }
        }

        fun stop() {
            isRunning.set(false)
            try {
                audioRecord?.stop()
            } catch (_: Throwable) {}
            try {
                workerThread?.join(1000)
            } catch (_: InterruptedException) {}
            workerThread = null
            cleanupAudioResources()
        }

        fun cancel() {
            isRunning.set(false)
            try {
                audioRecord?.stop()
            } catch (_: Throwable) {}
            try {
                workerThread?.join(1000)
            } catch (_: InterruptedException) {}
            workerThread = null
            cleanupAudioResources()
            mainHandler.post {
                listener.onStopped()
            }
        }

        private fun cleanupAudioResources() {
            try {
                audioRecord?.stop()
            } catch (_: Throwable) {}
            try {
                audioRecord?.release()
            } catch (_: Throwable) {}
            audioRecord = null

            try {
                noiseSuppressor?.release()
            } catch (_: Throwable) {}
            noiseSuppressor = null

            try {
                echoCanceler?.release()
            } catch (_: Throwable) {}
            echoCanceler = null

            try {
                gainControl?.release()
            } catch (_: Throwable) {}
            gainControl = null
        }
    }

    private fun parseHypothesisText(json: String): String {
        return try {
            val obj = JSONObject(json)
            obj.optString("text", obj.optString("partial", "")).trim()
        } catch (_: Exception) {
            ""
        }
    }

    private fun cleanRecognizedText(raw: String): String {
        return raw
            .lowercase()
            .replace(Regex("[^a-z0-9\\s\\[\\]]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun copyAssetFolder(assets: AssetManager, sourcePath: String, targetDir: File) {
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }
        val items = assets.list(sourcePath) ?: return
        for (item in items) {
            val childSource = if (sourcePath.isEmpty()) item else "$sourcePath/$item"
            val childTarget = File(targetDir, item)
            val subItems = assets.list(childSource)
            if (subItems != null && subItems.isNotEmpty()) {
                copyAssetFolder(assets, childSource, childTarget)
            } else {
                assets.open(childSource).use { input ->
                    childTarget.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }
    }

    private fun copyAssetFile(assets: AssetManager, sourcePath: String, targetFile: File) {
        targetFile.parentFile?.mkdirs()
        assets.open(sourcePath).use { input ->
            targetFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    }
}
