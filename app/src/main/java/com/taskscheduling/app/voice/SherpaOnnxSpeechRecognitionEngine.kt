package com.taskscheduling.app.voice

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Production-grade embedded offline streaming speech recognition engine powered by Sherpa-ONNX.
 *
 * Upgrades:
 * 1. Robust AudioRecord capture with automatic error recovery (recreates record on hardware fault).
 * 2. Unattenuated audio source selection with candidate fallback (MIC, VOICE_RECOGNITION, DEFAULT).
 * 3. Hardware audio effects suppression (AEC=OFF, NS=OFF, AGC=OFF) to prevent Samsung One UI speech gating.
 * 4. Safe DC-blocking high-pass filter (SafeAudioPreprocessor) removing DC bias and sub-15Hz rumble.
 * 5. Adaptive audio level monitor & dynamic noise-floor VAD (SILENCE, QUIET, NORMAL, LOUD, CLIPPING).
 * 6. Pre-roll circular audio buffer (~200ms) to prevent first-word/first-syllable loss (e.g. 'h' in "hello").
 * 7. Trailing audio window (~250ms) to ensure final phonemes/consonants are not cut off.
 * 8. Partial hypothesis stabilization (PartialStabilizer) to eliminate transient acoustic flicker.
 * 9. Silence-safe endpointing: never resets stream on room silence; only finalizes real utterances.
 */
class SherpaOnnxSpeechRecognitionEngine(
    private val context: Context,
    private val modelManager: SherpaOnnxModelManager = SherpaOnnxModelManager(context)
) : SpeechRecognitionEngine, OfflineSpeechEngine {

    companion object {
        const val TAG = "SherpaOnnxSpeechEngine"
        const val SAMPLE_RATE = 16000
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        const val SAMPLES_PER_READ = 1024
        private val sessionCounter = AtomicInteger(100)
    }

    private var recognizer: OnlineRecognizer? = null
    private val recognizerLock = Any()
    private val workerLock = Any()
    private var activeWorker: AudioRecordWorker? = null

    /**
     * Preferred audio source. Can be configured to "DEFAULT", "VOICE_RECOGNITION", or "MIC".
     * Default is "DEFAULT" which provides reliable, unattenuated input on Samsung tablets.
     */
    var preferredAudioSource: String = "DEFAULT"

    override val engineType: SpeechEngineType
        get() = SpeechEngineType.SHERPA_ONNX

    override val isModelLoaded: Boolean
        get() = synchronized(recognizerLock) { recognizer != null }

    override val isRunning: Boolean
        get() = synchronized(workerLock) { activeWorker?.isRecording ?: false }

    override val configuredSampleRate: Int
        get() = SAMPLE_RATE

    override val activeAudioSource: String
        get() = synchronized(workerLock) { activeWorker?.activeAudioSource ?: preferredAudioSource }

    override val configuredBufferSize: Int
        get() = synchronized(workerLock) { activeWorker?.bufferSize ?: (SAMPLES_PER_READ * 2) }

    override suspend fun initialize(): Result<Unit> = withContext(Dispatchers.IO) {
        if (isModelLoaded) return@withContext Result.success(Unit)

        val recResult = modelManager.loadRecognizer()
        if (recResult.isFailure) {
            val ex = recResult.exceptionOrNull() ?: IllegalStateException("Sherpa-ONNX model initialization failed")
            Log.e(TAG, "[SHERPA_ERROR] type=init message=${ex.message}", ex)
            return@withContext Result.failure(ex)
        }

        synchronized(recognizerLock) {
            recognizer = recResult.getOrThrow()
        }
        Result.success(Unit)
    }

    /**
     * Runs direct audio file test (WAV -> FloatArray -> Sherpa -> Text) bypassing AudioRecord.
     */
    suspend fun testAudioFile(assetPath: String = "sherpa/test_hello.wav"): Result<String> {
        return modelManager.testSherpaAudioFile(assetPath)
    }

    @SuppressLint("MissingPermission")
    override fun startListening(listener: SpeechEngineListener, continuous: Boolean): Boolean {
        val sessionId = sessionCounter.incrementAndGet()
        Log.i(TAG, "[VOICE_START] sessionId=$sessionId source=$preferredAudioSource continuous=$continuous")

        // 1. Permission check
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "[SHERPA_ERROR] sessionId=$sessionId type=permission message=Microphone permission denied")
            listener.onError(SpeechEngineError.MicrophonePermissionDenied)
            return false
        }

        // 2. Model check
        var activeRecognizer = synchronized(recognizerLock) { recognizer }
        if (activeRecognizer == null) {
            Log.i(TAG, "[VOICE_START] sessionId=$sessionId model not loaded yet, loading now...")
            val loadRes = kotlinx.coroutines.runBlocking(Dispatchers.IO) {
                modelManager.loadRecognizer()
            }
            if (loadRes.isSuccess) {
                synchronized(recognizerLock) {
                    recognizer = loadRes.getOrNull()
                    activeRecognizer = recognizer
                }
            } else {
                val ex = loadRes.exceptionOrNull()
                Log.e(TAG, "[SHERPA_ERROR] sessionId=$sessionId type=model message=Recognizer load failed: ${ex?.message}", ex)
                listener.onError(SpeechEngineError.InitializationFailed("Sherpa model load failed: ${ex?.message}"))
                return false
            }
        }
        val safeRecognizer = activeRecognizer ?: run {
            listener.onError(SpeechEngineError.MissingModel)
            return false
        }

        // 3. Cleanly terminate previous session under workerLock to prevent duplicate capture loops
        synchronized(workerLock) {
            stopListeningInternal()

            val stream: OnlineStream
            try {
                stream = safeRecognizer.createStream()
                Log.i(TAG, "[VOICE_STREAM_CREATED] sessionId=$sessionId")
            } catch (e: Throwable) {
                Log.e(TAG, "[SHERPA_ERROR] sessionId=$sessionId type=createStream message=${e.message}", e)
                listener.onError(SpeechEngineError.InitializationFailed("Could not create Sherpa OnlineStream: ${e.message}"))
                return false
            }

            val worker = AudioRecordWorker(
                sessionId = sessionId,
                recognizer = safeRecognizer,
                stream = stream,
                listener = listener,
                continuous = continuous,
                desiredSource = preferredAudioSource
            )

            val started = worker.startRecording()
            if (started) {
                activeWorker = worker
                listener.onReady()
                return true
            } else {
                try {
                    stream.release()
                } catch (_: Throwable) {}
                listener.onError(SpeechEngineError.MicrophoneUnavailable("Could not start AudioRecord"))
                return false
            }
        }
    }

    override fun pauseListening() {
        synchronized(workerLock) {
            activeWorker?.pause()
        }
    }

    override fun resumeListening() {
        synchronized(workerLock) {
            activeWorker?.resume()
        }
    }

    override fun stopListening() {
        synchronized(workerLock) {
            stopListeningInternal()
        }
    }

    override fun cancelListening() {
        synchronized(workerLock) {
            activeWorker?.cancel()
            stopListeningInternal()
        }
    }

    override fun release() {
        stopListening()
        synchronized(recognizerLock) {
            try {
                recognizer?.release()
            } catch (e: Throwable) {
                Log.w(TAG, "Recognizer release notice: ${e.message}")
            }
            recognizer = null
        }
        modelManager.release()
    }

    private fun stopListeningInternal() {
        activeWorker?.let {
            it.stopRecording()
            activeWorker = null
        }
    }

    /**
     * Dedicated background worker capturing PCM16 audio, applying DC-blocking preprocessing,
     * tracking adaptive noise floor, managing pre-roll/trailing buffers, and executing streaming decoding.
     */
    private inner class AudioRecordWorker(
        val sessionId: Int,
        private val recognizer: OnlineRecognizer,
        private var stream: OnlineStream,
        private val listener: SpeechEngineListener,
        private val continuous: Boolean,
        private val desiredSource: String
    ) : Runnable {

        private var workerThread: Thread? = null
        private var audioRecord: AudioRecord? = null
        private val isRunning = AtomicBoolean(false)
        private val isPaused = AtomicBoolean(false)
        private val mainHandler = Handler(Looper.getMainLooper())

        var activeAudioSource: String = desiredSource
            private set
        var bufferSize: Int = SAMPLES_PER_READ * 2
            private set
        val isRecording: Boolean
            get() = isRunning.get()

        private val preprocessor = SafeAudioPreprocessor(pole = 0.995f)
        private val levelMonitor = AdaptiveAudioLevelMonitor()
        private val preRollBuffer = PreRollAudioBuffer(capacitySamples = 4000) // ~250ms at 16kHz
        private val partialStabilizer = PartialStabilizer()

        @SuppressLint("MissingPermission")
        fun startRecording(): Boolean {
            val (record, sourceUsed) = createAudioRecord(desiredSource)
            if (record == null) {
                Log.e(TAG, "[SHERPA_ERROR] sessionId=$sessionId No AudioRecord candidate entered RECORDSTATE_RECORDING")
                return false
            }

            audioRecord = record
            activeAudioSource = sourceUsed

            Log.i(
                TAG,
                "[VOICE_AUDIORECORD_CONFIG] sessionId=$sessionId source=$activeAudioSource sampleRate=$SAMPLE_RATE channel=MONO encoding=PCM_16BIT bufferSize=$bufferSize effects=[AEC=OFF, NS=OFF, AGC=OFF]"
            )

            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            val isMuted = audioManager?.isMicrophoneMute ?: false
            Log.i(TAG, "[VOICE_ROUTING] sessionId=$sessionId micMute=$isMuted source=$activeAudioSource sampleRate=$SAMPLE_RATE bufferSize=$bufferSize")

            isRunning.set(true)
            val thread = Thread(this, "SherpaOnnxAudioWorker-$sessionId")
            workerThread = thread
            thread.start()
            return true
        }

        override fun run() {
            val shortBuffer = ShortArray(SAMPLES_PER_READ)
            var record = audioRecord ?: return

            var wasSpeech = false
            var trailingFramesRemaining = 0
            var chunkIndex = 0L
            var recoveryAttempts = 0

            while (isRunning.get()) {
                val shortsRead = record.read(shortBuffer, 0, shortBuffer.size, AudioRecord.READ_BLOCKING)

                // 1. Microphone Recovery: Handle hardware read errors without silently dropping into fake listening state
                if (shortsRead == AudioRecord.ERROR_INVALID_OPERATION ||
                    shortsRead == AudioRecord.ERROR_BAD_VALUE ||
                    shortsRead == AudioRecord.ERROR_DEAD_OBJECT ||
                    shortsRead < 0
                ) {
                    Log.e(TAG, "[SHERPA_ERROR] sessionId=$sessionId AudioRecord read error: $shortsRead. Attempting recovery...")
                    if (recoveryAttempts < 3) {
                        recoveryAttempts++
                        try {
                            record.stop()
                            record.release()
                        } catch (_: Exception) {}
                        try {
                            Thread.sleep(120)
                        } catch (_: InterruptedException) {}

                        val (newRecord, newSource) = createAudioRecord(desiredSource)
                        if (newRecord != null) {
                            audioRecord = newRecord
                            record = newRecord
                            activeAudioSource = newSource
                            Log.i(TAG, "[SHERPA_RECOVERY] AudioRecord recovered successfully with source $newSource")
                            continue
                        }
                    }
                    Log.e(TAG, "[SHERPA_ERROR] AudioRecord recovery failed after $recoveryAttempts attempts")
                    mainHandler.post {
                        listener.onError(SpeechEngineError.MicrophoneUnavailable("AudioRecord hardware fault: error $shortsRead"))
                    }
                    break
                }

                if (shortsRead == 0) {
                    continue
                }

                recoveryAttempts = 0 // Reset on successful read

                if (isPaused.get()) {
                    continue
                }

                chunkIndex++

                // 2. Normalization: PCM16 -> Float32 [-1.0f, 1.0f]
                val rawFloats = FloatArray(shortsRead)
                for (i in 0 until shortsRead) {
                    rawFloats[i] = shortBuffer[i].toFloat() / 32768.0f
                }

                // 3. Preprocessing: Gentle DC-offset removal and sub-15Hz rumble rejection
                val cleanFloats = preprocessor.process(rawFloats)

                // 4. Real-time audio level monitoring & dynamic noise-floor estimation
                val metrics = levelMonitor.analyze(shortBuffer, cleanFloats, shortsRead)

                if (metrics.isSpeechDetected || chunkIndex % 20 == 0L) {
                    Log.i(
                        TAG,
                        "[AUDIO_DIAGNOSTIC] sampleRate=16000 channels=1 encoding=PCM_16 bytesRead=${shortsRead * 2} rms=${"%.4f".format(metrics.rms)} peak=${"%.4f".format(metrics.peak)} min=${metrics.minSample} max=${metrics.maxSample} zeroPercent=${"%.2f".format(metrics.zeroPercent)}% state=${record.state} recState=${record.recordingState} category=${metrics.levelCategory} floor=${"%.4f".format(metrics.noiseFloorRms)} snr=${"%.1f".format(metrics.snrDb)}dB"
                    )
                }

                mainHandler.post {
                    listener.onAudioLevel(metrics.rms, metrics.peak, metrics.isSpeechDetected)
                }

                // 5. Pre-roll Buffer & Speech Transition Handling
                if (metrics.isSpeechDetected && !wasSpeech) {
                    wasSpeech = true
                    trailingFramesRemaining = 4 // ~256ms trailing audio window
                    mainHandler.post {
                        listener.onBeginningOfSpeech()
                    }

                    // Prepend pre-roll buffer (~200ms) to prevent first syllable/word loss ('h' in "hello")
                    val preRollSamples = preRollBuffer.getRecentSamples(3200)
                    if (preRollSamples.isNotEmpty()) {
                        try {
                            Log.i(TAG, "[SHERPA_AUDIO] PRE_ROLL prepended: samples=${preRollSamples.size} (~${(preRollSamples.size * 1000) / SAMPLE_RATE}ms)")
                            stream.acceptWaveform(preRollSamples, SAMPLE_RATE)
                        } catch (e: Throwable) {
                            Log.e(TAG, "[SHERPA_ERROR] preRoll acceptWaveform error: ${e.message}", e)
                        }
                    }
                } else if (!metrics.isSpeechDetected && wasSpeech) {
                    if (trailingFramesRemaining > 0) {
                        trailingFramesRemaining--
                    } else {
                        wasSpeech = false
                    }
                }

                // Keep pre-roll ring buffer continuously updated
                preRollBuffer.append(cleanFloats)

                // 6. Feed audio to Sherpa-ONNX streaming session
                // Feed during speech OR trailing audio window
                val shouldFeedSherpa = metrics.isSpeechDetected || trailingFramesRemaining > 0 || wasSpeech
                if (shouldFeedSherpa) {
                    if (chunkIndex % 15 == 0L || metrics.isSpeechDetected) {
                        Log.i(TAG, "[SHERPA_AUDIO] samples=$shortsRead sampleRate=16000 rms=${"%.4f".format(metrics.rms)} peak=${"%.4f".format(metrics.peak)}")
                    }

                    try {
                        stream.acceptWaveform(cleanFloats, SAMPLE_RATE)
                    } catch (e: Throwable) {
                        Log.e(TAG, "[SHERPA_ERROR] sessionId=$sessionId acceptWaveform error: ${e.message}", e)
                    }

                    // 7. Streaming Decoding Loop
                    val readyBefore = recognizer.isReady(stream)
                    try {
                        while (recognizer.isReady(stream)) {
                            recognizer.decode(stream)
                        }
                    } catch (e: Throwable) {
                        Log.e(TAG, "[SHERPA_ERROR] sessionId=$sessionId decode error: ${e.message}", e)
                    }

                    // 8. Partial Hypothesis Stabilization
                    var rawHypothesis = ""
                    try {
                        rawHypothesis = recognizer.getResult(stream).text.trim()
                        if (rawHypothesis.isNotBlank()) {
                            val changed = partialStabilizer.update(rawHypothesis)
                            val stable = partialStabilizer.stablePartial

                            Log.i(TAG, "[SHERPA_DECODE] samples=$shortsRead ready=$readyBefore raw=\"$rawHypothesis\" stable=\"$stable\"")

                            if (changed && stable.isNotBlank()) {
                                Log.i(TAG, "[VOICE_SHERPA_PARTIAL] sessionId=$sessionId text=\"$stable\"")
                                mainHandler.post {
                                    listener.onPartialResult(stable)
                                }
                            }
                        }
                    } catch (e: Throwable) {
                        Log.e(TAG, "[SHERPA_ERROR] sessionId=$sessionId getResult error: ${e.message}", e)
                    }

                    // 9. Endpoint Detection: Never reset stream before speech begins or when hypothesis is empty!
                    try {
                        val isEndpoint = recognizer.isEndpoint(stream)
                        if (isEndpoint && rawHypothesis.isNotBlank()) {
                            try {
                                stream.inputFinished()
                                while (recognizer.isReady(stream)) {
                                    recognizer.decode(stream)
                                }
                            } catch (e: Throwable) {
                                Log.w(TAG, "inputFinished note: ${e.message}")
                            }

                            val finalResult = recognizer.getResult(stream).text.trim()
                            if (finalResult.isNotBlank()) {
                                Log.i(TAG, "[SHERPA_FINAL] $finalResult")
                                mainHandler.post {
                                    listener.onResult(finalResult)
                                }
                            }

                            if (continuous) {
                                // Reset stream for next utterance in continuous mode
                                recognizer.reset(stream)
                                preprocessor.reset()
                                levelMonitor.reset()
                                preRollBuffer.clear()
                                partialStabilizer.reset()
                                wasSpeech = false
                                trailingFramesRemaining = 0
                            } else {
                                break
                            }
                        }
                    } catch (e: Throwable) {
                        Log.e(TAG, "[SHERPA_ERROR] sessionId=$sessionId isEndpoint error: ${e.message}", e)
                    }
                }
            }

            cleanup()
            mainHandler.post {
                listener.onStopped()
            }
        }

        fun pause() {
            isPaused.set(true)
        }

        fun resume() {
            isPaused.set(false)
        }

        fun cancel() {
            isRunning.set(false)
        }

        fun stopRecording() {
            isRunning.set(false)
            workerThread?.interrupt()
        }

        private fun cleanup() {
            isRunning.set(false)
            try {
                audioRecord?.stop()
            } catch (_: Exception) {}
            try {
                audioRecord?.release()
            } catch (_: Exception) {}
            audioRecord = null

            val finalHypothesis = partialStabilizer.stablePartial.ifBlank {
                try { recognizer.getResult(stream).text.trim() } catch (_: Throwable) { "" }
            }

            if (finalHypothesis.isNotBlank()) {
                try {
                    stream.inputFinished()
                    while (recognizer.isReady(stream)) {
                        recognizer.decode(stream)
                    }
                    val finalResult = recognizer.getResult(stream).text.trim()
                    if (finalResult.isNotBlank()) {
                        Log.i(TAG, "[SHERPA_FINAL] $finalResult")
                        mainHandler.post {
                            listener.onResult(finalResult)
                        }
                    }
                } catch (_: Throwable) {}
                partialStabilizer.reset()
            }

            try {
                stream.release()
            } catch (e: Throwable) {
                Log.w(TAG, "Stream release notice: ${e.message}")
            }
        }

        @SuppressLint("MissingPermission")
        private fun createAudioRecord(desiredSource: String): Pair<AudioRecord?, String> {
            val minBufSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT
            )
            if (minBufSize <= 0) {
                Log.e(TAG, "[SHERPA_ERROR] sessionId=$sessionId AudioRecord minBufferSize error: $minBufSize")
                return Pair(null, desiredSource)
            }

            bufferSize = maxOf(minBufSize * 2, SAMPLES_PER_READ * 2)

            val sourceMap = mapOf(
                "MIC" to MediaRecorder.AudioSource.MIC,
                "VOICE_RECOGNITION" to MediaRecorder.AudioSource.VOICE_RECOGNITION,
                "DEFAULT" to MediaRecorder.AudioSource.DEFAULT
            )

            val candidateSources = mutableListOf<Pair<Int, String>>()
            val primaryId = sourceMap[desiredSource] ?: MediaRecorder.AudioSource.MIC
            candidateSources.add(Pair(primaryId, desiredSource))
            for ((label, id) in sourceMap) {
                if (label != desiredSource) {
                    candidateSources.add(Pair(id, label))
                }
            }

            for ((sourceId, label) in candidateSources) {
                try {
                    val candidate = AudioRecord(
                        sourceId,
                        SAMPLE_RATE,
                        CHANNEL_CONFIG,
                        AUDIO_FORMAT,
                        bufferSize
                    )
                    if (candidate.state == AudioRecord.STATE_INITIALIZED) {
                        disableAudioEffects(candidate.audioSessionId)
                        candidate.startRecording()
                        if (candidate.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                            return Pair(candidate, label)
                        } else {
                            candidate.release()
                        }
                    } else {
                        candidate.release()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "AudioRecord candidate $label error: ${e.message}")
                }
            }
            return Pair(null, desiredSource)
        }

        private fun disableAudioEffects(audioSessionId: Int) {
            try {
                if (AcousticEchoCanceler.isAvailable()) {
                    AcousticEchoCanceler.create(audioSessionId)?.apply {
                        enabled = false
                        release()
                    }
                }
            } catch (e: Throwable) {
                Log.w(TAG, "AEC disable notice: ${e.message}")
            }
            try {
                if (NoiseSuppressor.isAvailable()) {
                    NoiseSuppressor.create(audioSessionId)?.apply {
                        enabled = false
                        release()
                    }
                }
            } catch (e: Throwable) {
                Log.w(TAG, "NS disable notice: ${e.message}")
            }
            try {
                if (AutomaticGainControl.isAvailable()) {
                    AutomaticGainControl.create(audioSessionId)?.apply {
                        enabled = false
                        release()
                    }
                }
            } catch (e: Throwable) {
                Log.w(TAG, "AGC disable notice: ${e.message}")
            }
        }
    }
}
