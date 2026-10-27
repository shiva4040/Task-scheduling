package com.taskscheduling.app.voice

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Diagnostic metrics snapshot for real-time microphone analysis (Step 1).
 */
data class MicTestMetrics(
    val isRunning: Boolean = false,
    val source: String = "DEFAULT",
    val audioSourceId: Int = MediaRecorder.AudioSource.DEFAULT,
    val isInitialized: Boolean = false,
    val recordingState: String = "STOPPED",
    val sampleRate: Int = 16000,
    val channels: String = "1 (CHANNEL_IN_MONO)",
    val encoding: String = "PCM 16-bit (ENCODING_PCM_16BIT)",
    val bufferSize: Int = 2048,
    val samplesRead: Int = 0,
    val bytesReadPerCycle: Int = 0,
    val totalSamples: Long = 0L,
    val nonZeroSamples: Long = 0L,
    val rms: Float = 0f,
    val peak: Float = 0f,
    val minSample: Short = 0,
    val maxSample: Short = 0,
    val zeroCrossings: Long = 0L,
    val zeroCrossingRate: Float = 0f,
    val zeroSamplePercent: Float = 0f,
    val noiseFloorRms: Float = 0.001f,
    val speechThreshold: Float = 0.005f,
    val speechRms: Float = 0f,
    val snrDb: Float = 0f,
    val isSpeechDetected: Boolean = false,
    val statusLabel: String = "SILENCE",
    val levelCategory: String = "SILENCE", // SILENCE, LOW AUDIO, NORMAL SPEECH, CLIPPING
    val readResultCode: Int = 0,
    val errorMessage: String? = null
)

/**
 * Structured result of a 3-second isolated microphone diagnostic test (Step 8).
 */
data class RawMicDiagnosticSummary(
    val source: String,
    val sampleRate: Int,
    val channels: String = "1 (CHANNEL_IN_MONO)",
    val encoding: String = "ENCODING_PCM_16BIT",
    val averageRms: Float,
    val peak: Float,
    val minSample: Short,
    val maxSample: Short,
    val totalSamples: Long,
    val nonZeroSamples: Long,
    val zeroCrossingRate: Float,
    val result: String, // NO SIGNAL, VERY LOW SIGNAL, VALID SPEECH SIGNAL, CLIPPING
    val pcmFloats: FloatArray? = null,
    val durationMs: Long = 0L,
    val readErrors: Int = 0
) {
    fun formatDisplay(): String = buildString {
        appendLine("MIC TEST")
        appendLine("Source: $source")
        appendLine("Sample rate: $sampleRate Hz")
        appendLine("Channels: $channels")
        appendLine("Encoding: $encoding")
        appendLine("Average RMS: ${"%.4f".format(averageRms)}")
        appendLine("Peak: ${"%.4f".format(peak)}")
        appendLine("Minimum: $minSample")
        appendLine("Maximum: $maxSample")
        appendLine("Samples: $totalSamples")
        appendLine("Result: $result")
    }
}

/**
 * Robust Raw Microphone Diagnostic Engine.
 *
 * Capabilities:
 * 1. Step 1: Real-time measurements of RMS, Peak, min, max, zero crossing rate, non-zero samples, read result.
 * 2. Step 2: Controlled source tests (VOICE_RECOGNITION, MIC, DEFAULT).
 * 3. Step 3: CHANNEL_IN_MONO validation and 16000Hz / 48000Hz support.
 * 4. Step 4: Pure PCM16 signed conversion (`short / 32768.0f`), with all optional hardware effects
 *    and DC filters completely disabled during raw diagnostic capture.
 * 5. Step 5: Android RECORD_AUDIO permission verification.
 * 6. Step 6: Safe AudioRecord buffer sizing and read error handling.
 * 7. Step 8: Dedicated 3-second isolated microphone diagnostic test without Whisper.
 * 8. Step 9: High-fidelity PCM buffer capture for single-shot Whisper transcription.
 */
class RawMicrophoneTester(private val context: Context) {

    companion object {
        const val TAG = "RawMicTester"
        const val DEFAULT_SAMPLE_RATE = 16000
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        const val SAMPLES_PER_READ = 1024

        fun classifyLevel(rms: Float, peak: Float, minVal: Short, maxVal: Short): String {
            return when {
                peak >= 0.95f || maxVal >= 32700 || minVal <= -32700 -> "CLIPPING"
                rms >= 0.020f -> "NORMAL SPEECH"
                rms >= 0.005f -> "LOW AUDIO"
                else -> "SILENCE"
            }
        }

        fun getAudioSourceId(name: String): Int {
            return when (name.uppercase()) {
                "VOICE_RECOGNITION" -> MediaRecorder.AudioSource.VOICE_RECOGNITION
                "MIC" -> MediaRecorder.AudioSource.MIC
                "DEFAULT" -> MediaRecorder.AudioSource.DEFAULT
                else -> MediaRecorder.AudioSource.DEFAULT
            }
        }

        /**
         * Creates and initializes an AudioRecord instance using the verified diagnostic audio pipeline parameters:
         * 16kHz mono, 16-bit PCM, double buffer size, and hardware audio effects (AEC, NS, AGC) disabled.
         */
        @SuppressLint("MissingPermission")
        fun createConfiguredAudioRecord(source: String, sampleRate: Int = DEFAULT_SAMPLE_RATE): AudioRecord {
            val srcId = getAudioSourceId(source)
            val minBufSize = AudioRecord.getMinBufferSize(sampleRate, CHANNEL_CONFIG, AUDIO_FORMAT)
            if (minBufSize <= 0) {
                throw IllegalStateException("getMinBufferSize returned $minBufSize for $sampleRate Hz")
            }
            val bufSize = maxOf(minBufSize * 2, SAMPLES_PER_READ * 4)
            val record = AudioRecord(srcId, sampleRate, CHANNEL_CONFIG, AUDIO_FORMAT, bufSize)
            if (record.state != AudioRecord.STATE_INITIALIZED) {
                record.release()
                throw IllegalStateException("AudioRecord failed to initialize for source $source (id=$srcId)")
            }
            disableHardwareAudioEffects(record.audioSessionId)
            return record
        }

        /**
         * Disables hardware audio effects (AEC, NS, AGC) to prevent Samsung/MediaTek
         * audio HAL from zeroing out or distorting microphone signals.
         */
        fun disableHardwareAudioEffects(audioSessionId: Int) {
            try {
                if (AcousticEchoCanceler.isAvailable()) {
                    AcousticEchoCanceler.create(audioSessionId)?.apply {
                        enabled = false
                        release()
                    }
                }
            } catch (e: Throwable) {
                Log.w(TAG, "AEC disable exception: ${e.message}")
            }
            try {
                if (NoiseSuppressor.isAvailable()) {
                    NoiseSuppressor.create(audioSessionId)?.apply {
                        enabled = false
                        release()
                    }
                }
            } catch (e: Throwable) {
                Log.w(TAG, "NS disable exception: ${e.message}")
            }
            try {
                if (AutomaticGainControl.isAvailable()) {
                    AutomaticGainControl.create(audioSessionId)?.apply {
                        enabled = false
                        release()
                    }
                }
            } catch (e: Throwable) {
                Log.w(TAG, "AGC disable exception: ${e.message}")
            }
        }
    }

    private val _metrics = MutableStateFlow(MicTestMetrics())
    val metrics: StateFlow<MicTestMetrics> = _metrics.asStateFlow()

    private val isRunning = AtomicBoolean(false)
    private var workerThread: Thread? = null
    private var audioRecord: AudioRecord? = null

    /**
     * Checks RECORD_AUDIO runtime permission (Step 5).
     */
    fun hasRecordPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Continuous raw microphone streaming test (Step 1).
     */
    @SuppressLint("MissingPermission")
    fun start(requestedSource: String = "DEFAULT", sampleRate: Int = DEFAULT_SAMPLE_RATE): Boolean {
        if (isRunning.get()) return true

        if (!hasRecordPermission()) {
            val err = "Microphone permission RECORD_AUDIO denied"
            Log.e(TAG, err)
            _metrics.value = _metrics.value.copy(errorMessage = err)
            return false
        }
        Log.i(TAG, "Microphone permission RECORD_AUDIO verified: GRANTED")

        val minBufSize = AudioRecord.getMinBufferSize(sampleRate, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (minBufSize <= 0) {
            val err = "AudioRecord.getMinBufferSize failed for $sampleRate Hz: $minBufSize"
            Log.e(TAG, err)
            _metrics.value = _metrics.value.copy(errorMessage = err)
            return false
        }
        val bufSize = maxOf(minBufSize * 2, SAMPLES_PER_READ * 4)

        val srcId = getAudioSourceId(requestedSource)
        val record = try {
            val r = AudioRecord(srcId, sampleRate, CHANNEL_CONFIG, AUDIO_FORMAT, bufSize)
            if (r.state != AudioRecord.STATE_INITIALIZED) {
                r.release()
                null
            } else {
                r
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to initialize AudioRecord for $requestedSource: ${e.message}")
            null
        }

        if (record == null) {
            val err = "Failed to initialize AudioRecord for source $requestedSource ($srcId)"
            Log.e(TAG, err)
            _metrics.value = _metrics.value.copy(errorMessage = err)
            return false
        }

        audioRecord = record

        // Temporarily disable hardware acoustic effects (Step 4)
        disableHardwareAudioEffects(record.audioSessionId)

        try {
            record.startRecording()
        } catch (e: Throwable) {
            Log.e(TAG, "startRecording failed: ${e.message}", e)
            record.release()
            audioRecord = null
            _metrics.value = _metrics.value.copy(errorMessage = "startRecording error: ${e.message}")
            return false
        }

        isRunning.set(true)
        _metrics.value = MicTestMetrics(
            isRunning = true,
            source = requestedSource,
            audioSourceId = srcId,
            isInitialized = true,
            recordingState = "RECORDING",
            sampleRate = sampleRate,
            channels = "1 (CHANNEL_IN_MONO)",
            encoding = "ENCODING_PCM_16BIT",
            bufferSize = bufSize,
            statusLabel = "STREAMING",
            levelCategory = "SILENCE"
        )

        val thread = Thread({
            val buffer = ShortArray(SAMPLES_PER_READ)
            var totalRead = 0L
            var nonZeroCount = 0L
            var totalZeroCrossings = 0L
            var prevSample: Short = 0

            Log.i(TAG, "[MIC_STREAM_START] source=$requestedSource sampleRate=$sampleRate bufSize=$bufSize effects=DISABLED")

            while (isRunning.get()) {
                val readCount = record.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
                if (readCount <= 0) {
                    val errName = when (readCount) {
                        AudioRecord.ERROR_INVALID_OPERATION -> "ERROR_INVALID_OPERATION"
                        AudioRecord.ERROR_BAD_VALUE -> "ERROR_BAD_VALUE"
                        AudioRecord.ERROR_DEAD_OBJECT -> "ERROR_DEAD_OBJECT"
                        else -> "READ_CODE_$readCount"
                    }
                    Log.w(TAG, "[MIC_STREAM_READ_ERROR] $errName")
                    continue
                }

                totalRead += readCount
                var sumSq = 0.0
                var peak = 0f
                var minVal = Short.MAX_VALUE
                var maxVal = Short.MIN_VALUE
                var zeroCount = 0
                var localZc = 0L

                for (i in 0 until readCount) {
                    val s = buffer[i]
                    if (s == 0.toShort()) {
                        zeroCount++
                    } else {
                        nonZeroCount++
                    }

                    if (s < minVal) minVal = s
                    if (s > maxVal) maxVal = s

                    if ((s > 0 && prevSample <= 0) || (s < 0 && prevSample >= 0)) {
                        localZc++
                    }
                    prevSample = s

                    // Step 4: Correct signed PCM16 conversion
                    val f = s.toFloat() / 32768.0f
                    sumSq += (f * f).toDouble()
                    val absF = abs(f)
                    if (absF > peak) peak = absF
                }

                totalZeroCrossings += localZc
                val rms = sqrt(sumSq / readCount).toFloat()
                val zeroPercent = (zeroCount * 100f) / readCount
                val zcRate = localZc.toFloat() / readCount
                val category = classifyLevel(rms, peak, minVal, maxVal)

                _metrics.value = MicTestMetrics(
                    isRunning = true,
                    source = requestedSource,
                    audioSourceId = srcId,
                    isInitialized = true,
                    recordingState = "RECORDING",
                    sampleRate = sampleRate,
                    channels = "1 (CHANNEL_IN_MONO)",
                    encoding = "ENCODING_PCM_16BIT",
                    bufferSize = bufSize,
                    samplesRead = readCount,
                    bytesReadPerCycle = readCount * 2,
                    totalSamples = totalRead,
                    nonZeroSamples = nonZeroCount,
                    rms = rms,
                    peak = peak,
                    minSample = minVal,
                    maxSample = maxVal,
                    zeroCrossings = totalZeroCrossings,
                    zeroCrossingRate = zcRate,
                    zeroSamplePercent = zeroPercent,
                    statusLabel = category,
                    levelCategory = category,
                    readResultCode = readCount
                )
            }

            try { record.stop() } catch (_: Throwable) {}
            try { record.release() } catch (_: Throwable) {}
            audioRecord = null

            _metrics.value = _metrics.value.copy(
                isRunning = false,
                recordingState = "STOPPED",
                statusLabel = "STOPPED"
            )
            Log.i(TAG, "[MIC_STREAM_STOP]")
        }, "RawMicStreamingThread")

        workerThread = thread
        thread.start()
        return true
    }

    fun stop() {
        isRunning.set(false)
        workerThread?.interrupt()
        workerThread = null
    }

    /**
     * Executes a dedicated 3-second isolated microphone diagnostic test (Step 8).
     * Does NOT invoke Whisper.
     */
    @SuppressLint("MissingPermission")
    suspend fun recordTimedDiagnostic(
        source: String = "DEFAULT",
        sampleRate: Int = DEFAULT_SAMPLE_RATE,
        durationMs: Long = 3000L,
        stopSignal: (() -> Boolean)? = null,
        onTick: ((remainingSeconds: Int, currentRms: Float) -> Unit)? = null,
        autoEndpointOnPause: Boolean = false,
        endpointSilenceMs: Long = 1000L
    ): Result<RawMicDiagnosticSummary> = withContext(Dispatchers.IO) {
        if (!hasRecordPermission()) {
            return@withContext Result.failure(IllegalStateException("RECORD_AUDIO permission not granted"))
        }

        val record = try {
            val r = createConfiguredAudioRecord(source, sampleRate)
            r.startRecording()
            r
        } catch (e: Throwable) {
            return@withContext Result.failure(e)
        }

        Log.i(TAG, "[TIMED_DIAGNOSTIC_START] source=$source duration=${durationMs}ms sampleRate=$sampleRate autoEndpoint=$autoEndpointOnPause")

        val targetSamples = (sampleRate * (durationMs / 1000.0)).toLong()
        val capturedFloats = ArrayList<Float>((sampleRate * 4))
        val buffer = ShortArray(SAMPLES_PER_READ)

        var totalSamplesRead = 0L
        var totalSumSquares = 0.0
        var maxPeak = 0f
        var overallMin = Short.MAX_VALUE
        var overallMax = Short.MIN_VALUE
        var nonZeroCount = 0L
        var zeroCrossings = 0L
        var prevSample: Short = 0
        var readErrors = 0

        var isSpeechDetected = false
        var speechStartTime = 0L
        var lastSpeechTime = 0L

        val startTime = System.currentTimeMillis()
        var lastTickSecond = (durationMs / 1000).toInt()

        try {
            while (totalSamplesRead < targetSamples && stopSignal?.invoke() != true) {
                val n = record.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
                if (n <= 0) {
                    readErrors++
                    if (readErrors > 50) {
                        Log.e(TAG, "Too many read errors ($readErrors), aborting test")
                        break
                    }
                    continue
                }

                totalSamplesRead += n
                var chunkSumSq = 0.0

                for (i in 0 until n) {
                    val s = buffer[i]
                    if (s != 0.toShort()) nonZeroCount++
                    if (s < overallMin) overallMin = s
                    if (s > overallMax) overallMax = s

                    if ((s > 0 && prevSample <= 0) || (s < 0 && prevSample >= 0)) {
                        zeroCrossings++
                    }
                    prevSample = s

                    val f = s.toFloat() / 32768.0f
                    capturedFloats.add(f)
                    chunkSumSq += (f * f).toDouble()
                    val absF = abs(f)
                    if (absF > maxPeak) maxPeak = absF
                }

                totalSumSquares += chunkSumSq

                val elapsed = System.currentTimeMillis() - startTime
                val remainingSec = max(0, ((durationMs - elapsed) / 1000).toInt())
                val instantRms = if (n > 0) sqrt(chunkSumSq / n).toFloat() else 0f
                if (remainingSec != lastTickSecond) {
                    lastTickSecond = remainingSec
                    onTick?.invoke(remainingSec, instantRms)
                }

                // Automatic pause-based utterance endpointing
                if (autoEndpointOnPause) {
                    val now = System.currentTimeMillis()
                    val isChunkSpeech = instantRms >= 0.004f || maxPeak >= 0.016f
                    if (isChunkSpeech) {
                        if (!isSpeechDetected) {
                            isSpeechDetected = true
                            speechStartTime = now
                        }
                        lastSpeechTime = now
                    } else if (isSpeechDetected) {
                        val speechDur = now - speechStartTime
                        val silenceDur = now - lastSpeechTime
                        if (speechDur >= 300L && silenceDur >= endpointSilenceMs) {
                            Log.i(TAG, "[AUTO_ENDPOINT] Utterance detected ($speechDur ms) followed by pause ($silenceDur ms). Ending recording.")
                            break
                        }
                    }
                }
            }
        } finally {
            try { record.stop() } catch (_: Throwable) {}
            try { record.release() } catch (_: Throwable) {}
        }

        val elapsed = System.currentTimeMillis() - startTime
        val avgRms = if (totalSamplesRead > 0) sqrt(totalSumSquares / totalSamplesRead).toFloat() else 0f
        val zcRate = if (totalSamplesRead > 0) zeroCrossings.toFloat() / totalSamplesRead else 0f

        val resultCategory = when {
            maxPeak >= 0.95f || overallMax >= 32700 || overallMin <= -32700 -> "CLIPPING"
            avgRms >= 0.015f && nonZeroCount > totalSamplesRead * 0.1 -> "VALID SPEECH SIGNAL"
            avgRms >= 0.003f && nonZeroCount > 0 -> "VERY LOW SIGNAL"
            else -> "NO SIGNAL"
        }

        val summary = RawMicDiagnosticSummary(
            source = source,
            sampleRate = sampleRate,
            channels = "1 (CHANNEL_IN_MONO)",
            encoding = "ENCODING_PCM_16BIT",
            averageRms = avgRms,
            peak = maxPeak,
            minSample = if (totalSamplesRead > 0) overallMin else 0,
            maxSample = if (totalSamplesRead > 0) overallMax else 0,
            totalSamples = totalSamplesRead,
            nonZeroSamples = nonZeroCount,
            zeroCrossingRate = zcRate,
            result = resultCategory,
            pcmFloats = capturedFloats.toFloatArray(),
            durationMs = elapsed,
            readErrors = readErrors
        )

        Log.i(TAG, "[TIMED_DIAGNOSTIC_COMPLETE]\n${summary.formatDisplay()}")
        Result.success(summary)
    }

    /**
     * Controlled multi-source comparison test (Step 2).
     * Tests VOICE_RECOGNITION, MIC, and DEFAULT sequentially.
     */
    suspend fun testAllSources(durationPerSourceMs: Long = 2500L): List<RawMicDiagnosticSummary> = withContext(Dispatchers.IO) {
        val sources = listOf("VOICE_RECOGNITION", "MIC", "DEFAULT")
        val results = mutableListOf<RawMicDiagnosticSummary>()
        for (src in sources) {
            val res = recordTimedDiagnostic(source = src, durationMs = durationPerSourceMs)
            if (res.isSuccess) {
                results.add(res.getOrThrow())
            }
        }
        results
    }
}

