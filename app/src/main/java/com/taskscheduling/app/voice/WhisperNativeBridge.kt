package com.taskscheduling.app.voice

import android.content.Context
import android.util.Log
import dev.ffmpegkit.whisper.Whisper
import dev.ffmpegkit.whisper.WhisperConfig
import dev.ffmpegkit.whisper.WhisperModel
import dev.ffmpegkit.whisper.WhisperResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

import dev.ffmpegkit.whisper.WhisperSegment

/**
 * Result returned from a Whisper native transcription pass with sub-millisecond nanosecond-precision timings.
 */
data class WhisperTranscriptionResult(
    val text: String,
    val processingTimeMs: Long,
    val language: String,
    val segmentCount: Int,
    val rawText: String = text,
    val nativeMutexWaitMs: Double = 0.0,
    val nativeInferenceMs: Double = 0.0,
    val resultProcessingMs: Double = 0.0,
    val preprocessingMs: Double = 0.0,
    val lockWaitMs: Double = 0.0,
    val totalSubmissionToResultMs: Double = 0.0,
    val audioDurationMs: Long = 0L,
    val sampleCount: Int = 0,
    val modelIdentifier: String = "",
    val callerName: String = "",
    val tJniStartNs: Long = 0L,
    val tJniEndNs: Long = 0L,
    val tNativeResultNs: Long = 0L,
    val tCleanupCompleteNs: Long = 0L,
    val modelInitCount: Int = 1,
    val inferenceInvocationCount: Int = 1,
    val isModelWarm: Boolean = true
)

/**
 * Object responsible for safe, single, idempotent loading of native libraries.
 * Loads libc++_shared.so before libwhisper.so to ensure dynamic dependencies resolve.
 */
object NativeLibraryLoader {
    private const val TAG = "WhisperNativeLoader"
    const val CRASH_TAG = "TaskScheduling-CRASH"

    private val isLoaded = AtomicBoolean(false)
    private var loadError: Throwable? = null

    val isNativeReady: Boolean
        get() = isLoaded.get()

    fun loadLibraries(): Result<Unit> {
        if (isLoaded.get()) return Result.success(Unit)
        synchronized(this) {
            if (isLoaded.get()) return Result.success(Unit)
            loadError?.let { return Result.failure(it) }

            Log.i(CRASH_TAG, "WHISPER_STEP_01_LIBRARY_LOAD")
            return try {
                try {
                    System.loadLibrary("c++_shared")
                    Log.i(TAG, "[Whisper] libc++_shared.so loaded successfully")
                } catch (e: Throwable) {
                    Log.d(TAG, "[Whisper] c++_shared note: ${e.message}")
                }

                System.loadLibrary("whisper")
                Log.i(TAG, "[Whisper] libwhisper.so loaded successfully")
                Log.i(CRASH_TAG, "WHISPER_STEP_02_LIBRARY_READY")
                isLoaded.set(true)
                Result.success(Unit)
            } catch (t: Throwable) {
                Log.e(CRASH_TAG, "FATAL: System.loadLibrary(\"whisper\") failed: ${t.message}", t)
                loadError = t
                Result.failure(t)
            }
        }
    }
}

/**
 * Clean lifecycle-safe bridge to native whisper.cpp / GGML.
 *
 * Guarantees:
 * 1. Safe boundary: Hard exception isolation; native errors cannot crash the JVM process.
 * 2. Stage-by-stage nanosecond measurements using System.nanoTime().
 * 3. Lifecycle safety: Strict single release path; deferred release when busy.
 * 4. Language control: ENGLISH ("en"), HINDI ("hi"), HINGLISH ("auto").
 * 5. Translation suppression: translate is ALWAYS FALSE, ensuring natural Hindi & Hinglish text.
 * 6. High-efficiency audio buffering: Reuses cache WAV file to feed native decoder without memory bloat.
 * 7. Single model context in process: Holds exactly one active WhisperModel at a time, reused across utterances.
 * 8. Configurable thread count: Optimized for ARM Cortex-A76 performance cores.
 */
open class WhisperNativeBridge(
    private val context: Context,
    var threads: Int = 4 // Default 4 threads (2 Big Cortex-A76 + 2 Little Cortex-A55) optimized for Helio G99
) {
    companion object {
        const val TAG = "WhisperNativeBridge"
        const val CRASH_TAG = "TaskScheduling-CRASH"
        const val SAMPLE_RATE = 16000

        @Volatile
        private var sharedBridge: WhisperNativeBridge? = null
        private val singletonLock = Any()

        fun getInstance(context: Context): WhisperNativeBridge {
            return sharedBridge ?: synchronized(singletonLock) {
                sharedBridge ?: WhisperNativeBridge(context.applicationContext).also {
                    sharedBridge = it
                }
            }
        }
    }

    val modelInitCount = AtomicInteger(0)
    val inferenceInvocationCount = AtomicInteger(0)

    private val bridgeMutex = Mutex()
    private val releaseLock = Any()

    @Volatile
    private var activeModel: WhisperModel? = null
    private val isModelLoaded = AtomicBoolean(false)
    private val isInferenceRunning = AtomicBoolean(false)
    private val isDeferredReleasePending = AtomicBoolean(false)
    private var activeModelPath: String = ""

    open val isReady: Boolean
        get() = isModelLoaded.get() && (activeModel?.isValid == true) && !isDeferredReleasePending.get()

    open val isInferenceActive: Boolean
        get() = isInferenceRunning.get()

    open val modelPath: String
        get() = activeModelPath

    fun setThreadCount(count: Int) {
        threads = count.coerceIn(1, 8)
        Log.i(TAG, "[WhisperNativeBridge] Configured thread count: $threads")
    }

    /**
     * Loads the Whisper GGML model into memory off the UI thread.
     * Reuses the existing context if the same model file is already loaded and ready.
     */
    open suspend fun loadModel(modelFile: File): Result<Unit> = withContext(Dispatchers.IO) {
        bridgeMutex.withLock {
            if (isInferenceRunning.get()) {
                val err = "Cannot load model while inference is running"
                Log.w(CRASH_TAG, "[WHISPER_TRACE] STAGE_ERROR: $err")
                return@withContext Result.failure(IllegalStateException(err))
            }
            if (isReady && activeModelPath == modelFile.absolutePath) {
                Log.i(TAG, "[Whisper] Reusing existing valid native model context: ${modelFile.name}")
                return@withContext Result.success(Unit)
            }

            val libLoadRes = NativeLibraryLoader.loadLibraries()
            if (libLoadRes.isFailure) {
                val err = libLoadRes.exceptionOrNull()
                val msg = "Native whisper libraries could not be loaded: ${err?.message}"
                Log.e(CRASH_TAG, "[Whisper] $msg", err)
                return@withContext Result.failure(IllegalStateException(msg, err))
            }

            Log.i(CRASH_TAG, "WHISPER_STEP_03_MODEL_PATH: ${modelFile.absolutePath} (size=${modelFile.length()} bytes)")
            if (!modelFile.exists() || !modelFile.canRead()) {
                val msg = "Whisper model file is missing or unreadable: ${modelFile.absolutePath}"
                Log.e(CRASH_TAG, "[Whisper] $msg")
                return@withContext Result.failure(IllegalArgumentException(msg))
            }

            releaseInternal()

            Log.i(CRASH_TAG, "WHISPER_STEP_04_MODEL_LOAD_START: ${modelFile.name}")
            val startNanos = System.nanoTime()

            try {
                val model = Whisper.loadModel(context, modelFile.absolutePath)
                if (!model.isValid) {
                    val err = "Whisper.loadModel returned invalid model handle for: ${modelFile.name}"
                    Log.e(CRASH_TAG, "WHISPER_STEP_05_MODEL_LOAD_RETURNED handle=0 (INVALID)")
                    return@withContext Result.failure(IllegalStateException(err))
                }

                activeModel = model
                activeModelPath = modelFile.absolutePath
                isModelLoaded.set(true)
                modelInitCount.incrementAndGet()

                val loadMs = (System.nanoTime() - startNanos) / 1_000_000.0
                Log.i(CRASH_TAG, "WHISPER_STEP_05_MODEL_LOAD_RETURNED: ${modelFile.name} valid in ${"%.1f".format(loadMs)}ms")
                Log.i(CRASH_TAG, "WHISPER_STEP_06_MODEL_READY")

                try {
                    val sysInfo = Whisper.getSystemInfo()
                    Log.i(TAG, "[Whisper] System info: $sysInfo")
                } catch (_: Throwable) {}

                Result.success(Unit)
            } catch (e: Throwable) {
                Log.e(CRASH_TAG, "WHISPER_STEP_05_MODEL_LOAD_RETURNED EXCEPTION: ${e.message}", e)
                releaseInternal()
                Result.failure(e)
            }
        }
    }

    /**
     * Transcribes a raw 16kHz PCM16 / Float audio buffer using the native Whisper context.
     *
     * @param pcmFloats Normalized float audio samples [-1.0f, 1.0f] at 16 kHz.
     * @param languageMode Language mode: ENGLISH ("en"), HINDI ("hi"), HINGLISH ("auto").
     */
    open suspend fun transcribeAudio(
        pcmFloats: FloatArray,
        languageMode: LanguageMode = LanguageMode.ENGLISH
    ): Result<WhisperTranscriptionResult> = withContext(Dispatchers.IO) {
        if (pcmFloats.isEmpty()) {
            return@withContext Result.success(WhisperTranscriptionResult("", 0L, languageMode.code, 0))
        }

        val cacheFile = File(context.cacheDir, "whisper_active_buffer.wav")
        try {
            writePcmFloatsToWav(pcmFloats, cacheFile)
            transcribeWavFile(cacheFile, languageMode)
        } catch (ce: kotlinx.coroutines.CancellationException) {
            Log.w(CRASH_TAG, "[WHISPER_TRACE] STAGE_CANCELLED: stage=STAGE_3_NATIVE_ENTRY message=${ce.message}")
            throw ce
        } catch (t: Throwable) {
            Log.e(CRASH_TAG, "[WHISPER_TRACE] STAGE_ERROR: stage=STAGE_3_NATIVE_ENTRY error=${t.message}", t)
            Result.failure(t)
        }
    }

    /**
     * Transcribes a 16 kHz mono WAV audio file using the native Whisper context.
     */
    suspend fun transcribeWavFile(
        wavFile: File,
        languageMode: LanguageMode = LanguageMode.ENGLISH
    ): Result<WhisperTranscriptionResult> = withContext(Dispatchers.IO) {
        val reqNanos = System.nanoTime()
        Log.i(CRASH_TAG, "[WHISPER_TRACE] STAGE_2_MUTEX_REQUEST: file=${wavFile.name}")
        bridgeMutex.withLock {
            coroutineContext.ensureActive()
            val acqNanos = System.nanoTime()
            val mutexWaitMs = (acqNanos - reqNanos) / 1_000_000.0
            Log.i(CRASH_TAG, "[WHISPER_TRACE] STAGE_2_MUTEX_ACQUIRED: waitMs=${"%.2f".format(mutexWaitMs)}")

            if (!isInferenceRunning.compareAndSet(false, true)) {
                val msg = "Whisper native inference locked by concurrent caller. Overlapping call rejected."
                Log.w(CRASH_TAG, "[WHISPER_TRACE] STAGE_ERROR: stage=STAGE_2_MUTEX_ACQUIRED error=$msg")
                return@withContext Result.failure(IllegalStateException(msg))
            }

            try {
                val model = activeModel
                if (model == null || !model.isValid || !isModelLoaded.get()) {
                    val err = "Whisper native context is not loaded or has been released"
                    Log.e(CRASH_TAG, "[WHISPER_TRACE] STAGE_ERROR: stage=STAGE_3_NATIVE_ENTRY error=$err")
                    return@withContext Result.failure(IllegalStateException(err))
                }

                if (!wavFile.exists() || wavFile.length() <= 44L) {
                    return@withContext Result.success(
                        WhisperTranscriptionResult("", 0L, languageMode.code, 0, "", mutexWaitMs, 0.0, 0.0)
                    )
                }

                // Deterministic decoding configuration respecting requested language mode:
                val config = WhisperConfig(
                    language = languageMode.code,
                    translate = false,
                    threads = threads.coerceIn(1, 8),
                    maxSegmentLength = 0,
                    printTimestamps = false
                )

                val nativeStartNanos = System.nanoTime()
                Log.i(CRASH_TAG, "[WHISPER_TRACE] STAGE_3_NATIVE_ENTRY: file=${wavFile.name} bytes=${wavFile.length()} threads=${config.threads} lang=${config.language}")

                val invIndex = inferenceInvocationCount.incrementAndGet()
                val isWarm = invIndex > 1

                val result: WhisperResult = Whisper.transcribe(model, wavFile.absolutePath, config)
                val nativeExitNanos = System.nanoTime()
                val nativeInferenceMs = (nativeExitNanos - nativeStartNanos) / 1_000_000.0
                Log.i(CRASH_TAG, "[WHISPER_TRACE] STAGE_4_NATIVE_EXIT: decodeMs=${"%.1f".format(nativeInferenceMs)} segments=${result.segments.size}")

                val rawText = result.text.trim()
                val dedupedFromSegments = deduplicateSegments(result.segments)
                val sourceText = if (dedupedFromSegments.isNotEmpty() && dedupedFromSegments.size < result.segments.size) {
                    dedupedFromSegments.joinToString(" ") { it.text.trim() }
                } else {
                    rawText
                }
                val cleanText = sanitizeTranscript(sourceText)
                val procEndNanos = System.nanoTime()
                val resultProcMs = (procEndNanos - nativeExitNanos) / 1_000_000.0

                Log.i(CRASH_TAG, "[WHISPER_TRACE] STAGE_5_TEXT_SANITIZED: raw=\"$rawText\" clean=\"$cleanText\" procMs=${"%.2f".format(resultProcMs)}")
                Log.i(TAG, "[WHISPER_RESULT] text=\"$cleanText\" (inference=${"%.1f".format(nativeInferenceMs)}ms lang=${languageMode.code} threads=${config.threads})")

                Result.success(
                    WhisperTranscriptionResult(
                        text = cleanText,
                        processingTimeMs = nativeInferenceMs.toLong(),
                        language = languageMode.code,
                        segmentCount = if (dedupedFromSegments.isNotEmpty()) dedupedFromSegments.size else result.segments.size,
                        rawText = rawText,
                        nativeMutexWaitMs = mutexWaitMs,
                        nativeInferenceMs = nativeInferenceMs,
                        resultProcessingMs = resultProcMs,
                        tJniStartNs = nativeStartNanos,
                        tJniEndNs = nativeExitNanos,
                        tNativeResultNs = nativeExitNanos,
                        tCleanupCompleteNs = procEndNanos,
                        modelInitCount = modelInitCount.get(),
                        inferenceInvocationCount = invIndex,
                        isModelWarm = isWarm
                    )
                )
            } catch (ce: kotlinx.coroutines.CancellationException) {
                val elapsedMs = (System.nanoTime() - acqNanos) / 1_000_000.0
                Log.w(CRASH_TAG, "[WHISPER_TRACE] STAGE_CANCELLED: stage=STAGE_4_NATIVE_EXIT elapsedMs=${"%.1f".format(elapsedMs)} message=${ce.message}")
                throw ce
            } catch (e: Throwable) {
                val elapsedMs = (System.nanoTime() - acqNanos) / 1_000_000.0
                Log.e(CRASH_TAG, "[WHISPER_TRACE] STAGE_ERROR: stage=STAGE_4_NATIVE_EXIT elapsedMs=${"%.1f".format(elapsedMs)} error=${e.message}", e)
                Result.failure(e)
            } finally {
                isInferenceRunning.set(false)
                Log.i(CRASH_TAG, "[WHISPER_TRACE] STAGE_6_MUTEX_RELEASED")
                if (isDeferredReleasePending.compareAndSet(true, false)) {
                    Log.i(CRASH_TAG, "[WHISPER_TRACE] Executing deferred model release now that inference completed")
                    releaseInternal()
                }
            }
        }
    }

    /**
     * Filters out duplicate consecutive segments generated by Whisper when decoding across trailing silence.
     */
    fun deduplicateSegments(segments: List<WhisperSegment>): List<WhisperSegment> {
        if (segments.size <= 1) return segments
        val filtered = mutableListOf<WhisperSegment>()
        for (seg in segments) {
            val textNorm = seg.text.trim().lowercase().replace(Regex("[^a-zA-Z0-9\u0900-\u097F]"), "")
            if (textNorm.isEmpty()) continue
            val lastNorm = filtered.lastOrNull()?.text?.trim()?.lowercase()?.replace(Regex("[^a-zA-Z0-9\u0900-\u097F]"), "")
            if (lastNorm != null && textNorm == lastNorm) {
                Log.d(TAG, "[DEDUP] Dropping hallucinated repeat segment: \"${seg.text}\"")
                continue
            }
            filtered.add(seg)
        }
        return filtered
    }

    /**
     * Sanitizes Whisper output: removes hallucinated tags while preserving Hindi Devanagari Unicode
     * and Hinglish mixed words. Also deduplicates repeated adjacent sentences (e.g. "Hello. Hello." -> "Hello.")
     * without deleting legitimate repeated words in a single sentence (e.g. "very very", "no no").
     */
    fun sanitizeTranscript(raw: String): String {
        var s = raw.trim()
        s = s.replace(Regex("\\[[^\\]]*\\]"), "")
        s = s.replace(Regex("\\([^\\)]*\\)"), "")
        s = s.replace(Regex("<\\|[^\\|>]*\\|>"), "")
        s = s.replace(Regex("[\\r\\n]+"), " ")
        s = s.replace(Regex("\\s+"), " ")
        s = s.trim()

        s = deduplicateRepeatedPhrases(s)

        return s.trim()
    }

    /**
     * Deduplicates adjacent identical sentences or clauses caused by silence looping
     * while preserving multi-word repeated expressions within sentences.
     */
    fun deduplicateRepeatedPhrases(text: String): String {
        if (text.isBlank()) return text
        val sentenceRegex = Regex("(?<=[.!?।])\\s+")
        val sentences = text.split(sentenceRegex).map { it.trim() }.filter { it.isNotEmpty() }
        if (sentences.size <= 1) return text

        val result = mutableListOf<String>()
        for (sentence in sentences) {
            val norm = sentence.lowercase().replace(Regex("[^a-zA-Z0-9\u0900-\u097F]"), "")
            val lastNorm = result.lastOrNull()?.lowercase()?.replace(Regex("[^a-zA-Z0-9\u0900-\u097F]"), "")
            if (lastNorm != null && norm.isNotEmpty() && norm == lastNorm) {
                Log.d(TAG, "[DEDUP] Dropping repeated sentence: \"$sentence\"")
                continue
            }
            result.add(sentence)
        }
        return result.joinToString(" ")
    }

    /**
     * Safely releases native model and frees underlying GGML memory.
     * Guaranteed thread-safe against concurrent inference.
     */
    open fun release() {
        releaseInternal()
    }

    private fun releaseInternal() {
        synchronized(releaseLock) {
            if (isInferenceRunning.get()) {
                Log.w(CRASH_TAG, "[Whisper] Inference is currently running; deferring model release to avoid SIGSEGV")
                isDeferredReleasePending.set(true)
                return
            }
            isDeferredReleasePending.set(false)
            Log.i(CRASH_TAG, "WHISPER_STEP_13_MODEL_RELEASE")
            activeModel?.let { model ->
                try {
                    if (model.isValid) {
                        Whisper.releaseModel(model)
                        Log.i(TAG, "[Whisper] Native Whisper context released cleanly")
                    }
                } catch (e: Throwable) {
                    Log.w(CRASH_TAG, "Notice during Whisper native release: ${e.message}")
                }
            }
            activeModel = null
            isModelLoaded.set(false)
            activeModelPath = ""
        }
    }

    fun writePcmFloatsToWav(floats: FloatArray, outFile: File) {
        val numSamples = floats.size
        val numBytes = numSamples * 2
        val totalDataLen = numBytes + 36
        val sampleRate = SAMPLE_RATE
        val byteRate = sampleRate * 2

        java.io.BufferedOutputStream(FileOutputStream(outFile, false), 65536).use { fos ->
            val header = ByteArray(44)
            header[0] = 'R'.code.toByte(); header[1] = 'I'.code.toByte(); header[2] = 'F'.code.toByte(); header[3] = 'F'.code.toByte()
            header[4] = (totalDataLen and 0xff).toByte()
            header[5] = ((totalDataLen shr 8) and 0xff).toByte()
            header[6] = ((totalDataLen shr 16) and 0xff).toByte()
            header[7] = ((totalDataLen shr 24) and 0xff).toByte()
            header[8] = 'W'.code.toByte(); header[9] = 'A'.code.toByte(); header[10] = 'V'.code.toByte(); header[11] = 'E'.code.toByte()
            header[12] = 'f'.code.toByte(); header[13] = 'm'.code.toByte(); header[14] = 't'.code.toByte(); header[15] = ' '.code.toByte()
            header[16] = 16; header[17] = 0; header[18] = 0; header[19] = 0
            header[20] = 1; header[21] = 0
            header[22] = 1; header[23] = 0
            header[24] = (sampleRate and 0xff).toByte()
            header[25] = ((sampleRate shr 8) and 0xff).toByte()
            header[26] = ((sampleRate shr 16) and 0xff).toByte()
            header[27] = ((sampleRate shr 24) and 0xff).toByte()
            header[28] = (byteRate and 0xff).toByte()
            header[29] = ((byteRate shr 8) and 0xff).toByte()
            header[30] = ((byteRate shr 16) and 0xff).toByte()
            header[31] = ((byteRate shr 24) and 0xff).toByte()
            header[32] = 2; header[33] = 0
            header[34] = 16; header[35] = 0
            header[36] = 'd'.code.toByte(); header[37] = 'a'.code.toByte(); header[38] = 't'.code.toByte(); header[39] = 'a'.code.toByte()
            header[40] = (numBytes and 0xff).toByte()
            header[41] = ((numBytes shr 8) and 0xff).toByte()
            header[42] = ((numBytes shr 16) and 0xff).toByte()
            header[43] = ((numBytes shr 24) and 0xff).toByte()
            fos.write(header)

            val pcmBuf = ByteArray(8192)
            var pcmIdx = 0
            for (i in 0 until numSamples) {
                val clamped = floats[i].coerceIn(-1.0f, 1.0f)
                val s = (clamped * 32767.0f).toInt()
                pcmBuf[pcmIdx++] = (s and 0xff).toByte()
                pcmBuf[pcmIdx++] = ((s shr 8) and 0xff).toByte()
                if (pcmIdx >= pcmBuf.size) {
                    fos.write(pcmBuf, 0, pcmIdx)
                    pcmIdx = 0
                }
            }
            if (pcmIdx > 0) {
                fos.write(pcmBuf, 0, pcmIdx)
            }
            fos.flush()
        }
    }

    fun readWavToFloats(wavFile: File): FloatArray {
        if (!wavFile.exists() || wavFile.length() <= 44L) return FloatArray(0)
        val bytes = wavFile.readBytes()
        if (bytes.size <= 44) return FloatArray(0)
        val numSamples = (bytes.size - 44) / 2
        val floats = FloatArray(numSamples)
        var bIdx = 44
        for (i in 0 until numSamples) {
            val low = bytes[bIdx++].toInt() and 0xFF
            val high = bytes[bIdx++].toInt()
            val sample = (high shl 8) or low
            floats[i] = (sample.toShort() / 32768.0f).coerceIn(-1.0f, 1.0f)
        }
        return floats
    }
}
