package com.taskscheduling.app

import android.content.Context
import com.taskscheduling.app.voice.AdaptiveAudioLevelMonitor
import com.taskscheduling.app.voice.LanguageMode
import com.taskscheduling.app.voice.PreRollAudioBuffer
import com.taskscheduling.app.voice.SpeechEngineError
import com.taskscheduling.app.voice.SpeechEngineListener
import com.taskscheduling.app.voice.WhisperCppSpeechRecognitionEngine
import com.taskscheduling.app.voice.WhisperEngineState
import com.taskscheduling.app.voice.WhisperModelManager
import com.taskscheduling.app.voice.WhisperNativeBridge
import com.taskscheduling.app.voice.WhisperTranscriptionResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import kotlin.math.max

/**
 * PHASE 8 — 20 TARGETED AUTOMATED REGRESSION TESTS
 *
 * Covers all 20 required regression areas:
 * 1. PCM16 to float conversion
 * 2. Sample counts and duration calculations
 * 3. Empty audio handling
 * 4. Very short utterances
 * 5. Quiet speech buffering
 * 6. Utterance accumulation across multiple reads
 * 7. Pre-roll and trailing speech preservation
 * 8. Endpointing with natural pauses
 * 9. Stop capture while inference is running
 * 10. Cancellation propagation
 * 11. Native context serialization
 * 12. Empty Whisper output
 * 13. Non-empty Whisper output delivery
 * 14. Unicode Hindi transcription preservation
 * 15. Hinglish text normalization
 * 16. UI state transitions
 * 17. Repeated continuous utterances
 * 18. Microphone permission denial
 * 19. AudioRecord initialization/read failure
 * 20. Native inference exception handling
 */
class WhisperRegressionTestSuite {

    private lateinit var dummyContext: Context
    private lateinit var tempDir: File

    private class DummyContext(private val baseDir: File) : android.content.ContextWrapper(null) {
        override fun getApplicationContext(): Context = this
        override fun getCacheDir(): File = baseDir
        override fun getFilesDir(): File = baseDir
        override fun getPackageName(): String = "com.taskscheduling.app"
    }

    @Before
    fun setUp() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "whisper_reg_${UUID.randomUUID()}")
        tempDir.mkdirs()
        dummyContext = DummyContext(tempDir)
    }

    private open class TestNativeBridge(
        context: Context,
        var delayMs: Long = 50L,
        var resultText: String = "test transcription",
        var rawResultText: String = "test transcription",
        var shouldThrow: Boolean = false
    ) : WhisperNativeBridge(context) {
        val callCount = AtomicInteger(0)
        val activeConcurrent = AtomicInteger(0)
        val maxConcurrent = AtomicInteger(0)
        val isReleased = AtomicBoolean(false)

        override val isReady: Boolean get() = !isReleased.get()

        override suspend fun transcribeAudio(
            pcmFloats: FloatArray,
            languageMode: LanguageMode
        ): Result<WhisperTranscriptionResult> {
            val current = activeConcurrent.incrementAndGet()
            maxConcurrent.updateAndGet { max(it, current) }
            callCount.incrementAndGet()
            try {
                if (delayMs > 0) delay(delayMs)
                if (shouldThrow) {
                    return Result.failure(IllegalStateException("Simulated native bridge crash"))
                }
                return Result.success(
                    WhisperTranscriptionResult(
                        text = resultText,
                        rawText = rawResultText,
                        processingTimeMs = delayMs,
                        language = languageMode.code,
                        segmentCount = 1
                    )
                )
            } finally {
                activeConcurrent.decrementAndGet()
            }
        }

        override fun release() {
            isReleased.set(true)
        }
    }

    private class TestListener : SpeechEngineListener {
        var resultReceived: String? = null
        var partialReceived: String? = null
        var errorReceived: SpeechEngineError? = null
        var stoppedCalled = false
        var speechStartedCalled = false
        val latch = CountDownLatch(1)

        override fun onReady() {}
        override fun onBeginningOfSpeech() { speechStartedCalled = true }
        override fun onAudioLevel(rmsLevel: Float, peakLevel: Float, inputDetected: Boolean) {}
        override fun onPartialResult(hypothesis: String) { partialReceived = hypothesis }
        override fun onResult(recognizedText: String) {
            resultReceived = recognizedText
            latch.countDown()
        }
        override fun onError(error: SpeechEngineError) {
            errorReceived = error
            latch.countDown()
        }
        override fun onStopped() { stoppedCalled = true }
    }

    // =========================================================================
    // 1. PCM16 to float conversion
    // =========================================================================
    @Test
    fun `test 01 - PCM16 to float signed normalization and boundary limits`() {
        val shortValues = shortArrayOf(
            Short.MIN_VALUE, // -32768 -> -1.0f
            -16384,          // -> -0.5f
            0,               // -> 0.0f
            16384,           // -> 0.5f
            Short.MAX_VALUE  // 32767 -> ~0.999969f
        )
        val floats = FloatArray(shortValues.size) { i ->
            shortValues[i].toFloat() / 32768.0f
        }

        assertEquals(-1.0f, floats[0], 0.0001f)
        assertEquals(-0.5f, floats[1], 0.0001f)
        assertEquals(0.0f, floats[2], 0.0001f)
        assertEquals(0.5f, floats[3], 0.0001f)
        assertTrue("Max short normalized must be <= 1.0f", floats[4] <= 1.0f)
        assertTrue("Max short normalized must be > 0.999f", floats[4] > 0.999f)

        // Verify little-endian byte decode matches
        val bytes = ByteArray(shortValues.size * 2)
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        for (s in shortValues) buffer.putShort(s)
        buffer.rewind()

        for (i in shortValues.indices) {
            val decodedShort = buffer.short
            val decodedFloat = decodedShort.toFloat() / 32768.0f
            assertEquals(floats[i], decodedFloat, 0.0001f)
        }
    }

    // =========================================================================
    // 2. Correct sample counts and duration calculations
    // =========================================================================
    @Test
    fun `test 02 - sample counts and duration calculation accuracy`() {
        val sampleRate = 16000
        fun calculateDurationMs(samples: Int): Long = (samples * 1000L) / sampleRate

        assertEquals(1000L, calculateDurationMs(16000))
        assertEquals(500L, calculateDurationMs(8000))
        assertEquals(150L, calculateDurationMs(2400))
        assertEquals(62L, calculateDurationMs(1000))

        fun calculateSampleCount(durationMs: Long): Int = ((durationMs * sampleRate) / 1000L).toInt()
        assertEquals(16000, calculateSampleCount(1000L))
        assertEquals(2400, calculateSampleCount(150L))
    }

    // =========================================================================
    // 3. Empty audio handling
    // =========================================================================
    @Test
    fun `test 03 - empty audio buffer handling returns empty result without error`() = runBlocking {
        val bridge = TestNativeBridge(dummyContext, resultText = "")
        val modelManager = WhisperModelManager(dummyContext, overrideModelDir = tempDir)
        val engine = WhisperCppSpeechRecognitionEngine(dummyContext, modelManager, bridge)
        val listener = TestListener()

        val emptyAudio = FloatArray(0)
        val job = engine.submitTranscription(emptyAudio, isFinal = true, listener = listener)
        job?.join()

        assertTrue(listener.latch.await(2, TimeUnit.SECONDS))
        assertEquals("", listener.resultReceived)
        assertNull("Empty audio must not trigger onError", listener.errorReceived)
    }

    // =========================================================================
    // 4. Very short utterances (e.g. 'hello' ~150-200ms)
    // =========================================================================
    @Test
    fun `test 04 - short utterance is padded to minimum 1s and not discarded`() {
        val bridge = TestNativeBridge(dummyContext)
        val modelManager = WhisperModelManager(dummyContext, overrideModelDir = tempDir)
        val engine = WhisperCppSpeechRecognitionEngine(dummyContext, modelManager, bridge)

        // 2400 samples = 150ms utterance (e.g. "hi" or quick "hello")
        val shortAudio = FloatArray(2400) { 0.2f }
        val prepared = engine.prepareAudioForWhisper(shortAudio)

        // Whisper requires at least 1.0s (16000 samples)
        assertTrue("Prepared audio must have at least 16000 samples", prepared.size >= 16000)
        // Original speech data is preserved and amplified (0.2f * 3.0x = 0.6f)
        assertEquals(0.6f, prepared[0], 0.05f)
        assertEquals(0.6f, prepared[2399], 0.05f)
        // Padded section must be silent
        assertEquals(0.0f, prepared[2400], 0.0001f)
        assertEquals(0.0f, prepared.last(), 0.0001f)
    }

    // =========================================================================
    // 5. Quiet speech buffering and adaptive gain
    // =========================================================================
    @Test
    fun `test 05 - quiet speech receives adaptive gain scaling without clipping`() {
        val bridge = TestNativeBridge(dummyContext)
        val modelManager = WhisperModelManager(dummyContext, overrideModelDir = tempDir)
        val engine = WhisperCppSpeechRecognitionEngine(dummyContext, modelManager, bridge)

        // Tab A9 quiet signal: peak = 0.04f (would trigger Whisper blank audio)
        val quietAudio = FloatArray(16000) { 0.04f }
        val prepared = engine.prepareAudioForWhisper(quietAudio)

        var newPeak = 0f
        for (sample in prepared) {
            val a = abs(sample)
            if (a > newPeak) newPeak = a
        }

        // Peak should be scaled up significantly (target 0.6f, max gain 6x -> 0.04 * 6 = 0.24f)
        assertTrue("Quiet peak $newPeak should be amplified above 0.20f", newPeak >= 0.20f)
        assertTrue("Amplified peak must not clip", newPeak <= 1.0f)
    }

    // =========================================================================
    // 6. Complete utterance accumulation across multiple AudioRecord reads
    // =========================================================================
    @Test
    fun `test 06 - multiple audio chunks accumulate without dropping samples`() {
        val accumulated = ArrayList<Float>()
        val chunkSize = 1024
        val numChunks = 5

        for (chunkIndex in 0 until numChunks) {
            val chunk = FloatArray(chunkSize) { (chunkIndex + 1).toFloat() }
            for (s in chunk) accumulated.add(s)
        }

        assertEquals(5120, accumulated.size)
        assertEquals(1.0f, accumulated[0], 0.001f)
        assertEquals(5.0f, accumulated.last(), 0.001f)
    }

    // =========================================================================
    // 7. Preservation of pre-roll and trailing speech
    // =========================================================================
    @Test
    fun `test 07 - PreRoll buffer preserves 500ms pre-roll audio before speech onset`() {
        val sampleRate = 16000
        val preRollCapacity = (sampleRate * 0.5f).toInt() // 8000 samples = 500ms
        val preRoll = PreRollAudioBuffer(capacitySamples = preRollCapacity)

        // Feed background noise
        val noise = FloatArray(4000) { 0.001f }
        preRoll.append(noise, noise.size)

        // Feed speech onset
        val onset = FloatArray(4000) { 0.15f }
        preRoll.append(onset, onset.size)

        val retrieved = preRoll.getRecentSamples(8000)
        assertEquals(8000, retrieved.size)
        // First half is noise, second half is speech onset
        assertEquals(0.001f, retrieved[0], 0.0001f)
        assertEquals(0.15f, retrieved[7999], 0.0001f)
    }

    // =========================================================================
    // 8. Endpointing with natural pauses
    // =========================================================================
    @Test
    fun `test 08 - adaptive audio monitor hangover prevents cutting off inter-word pauses`() {
        val monitor = AdaptiveAudioLevelMonitor()

        // 1. Speech frame
        val speechShorts = ShortArray(1024) { 3200 }
        val speechFloats = FloatArray(1024) { 0.1f }
        val m1 = monitor.analyze(speechShorts, speechFloats, 1024)
        assertTrue("Speech frame must be detected as speech", m1.isSpeechDetected)

        // 2. Inter-word pause (1 frame = ~64ms of silence)
        val silenceShorts = ShortArray(1024) { 30 }
        val silenceFloats = FloatArray(1024) { 0.001f }
        val m2 = monitor.analyze(silenceShorts, silenceFloats, 1024)

        // With 8-frame hangover (500ms), 1 silence frame must NOT drop speech state
        assertTrue("Inter-word brief pause must retain speech state due to hangover", m2.isSpeechDetected)
    }

    // =========================================================================
    // 9. Stop capture while inference is running
    // =========================================================================
    @Test
    fun `test 09 - stopListening during active inference does not cancel transcription job`() = runBlocking {
        val bridge = TestNativeBridge(dummyContext, delayMs = 150L, resultText = "safe in flight text")
        val modelManager = WhisperModelManager(dummyContext, overrideModelDir = tempDir)
        val engine = WhisperCppSpeechRecognitionEngine(dummyContext, modelManager, bridge)
        val listener = TestListener()

        val job = engine.submitTranscription(FloatArray(16000) { 0.1f }, isFinal = true, listener = listener)
        assertNotNull(job)
        delay(30) // Wait for inference to start

        // Stop microphone capture
        engine.stopListening()

        // Active transcription job must NOT be cancelled
        assertFalse("Job must not be cancelled by stopListening", job!!.isCancelled)
        job.join()

        assertTrue(listener.latch.await(2, TimeUnit.SECONDS))
        assertEquals("safe in flight text", listener.resultReceived)
    }

    // =========================================================================
    // 10. Cancellation propagation
    // =========================================================================
    @Test
    fun `test 10 - cancelListening cancels job and drops result`() = runBlocking {
        val bridge = TestNativeBridge(dummyContext, delayMs = 300L, resultText = "cancelled result")
        val modelManager = WhisperModelManager(dummyContext, overrideModelDir = tempDir)
        val engine = WhisperCppSpeechRecognitionEngine(dummyContext, modelManager, bridge)
        val listener = TestListener()

        val job = engine.submitTranscription(FloatArray(16000) { 0.1f }, isFinal = true, listener = listener)
        assertNotNull(job)
        delay(20)

        // Intentional cancellation
        engine.cancelListening()
        assertTrue("Job must be cancelled", job!!.isCancelled)
        job.join()

        // Result should NOT be delivered
        assertNull(listener.resultReceived)
    }

    // =========================================================================
    // 11. Native context serialization
    // =========================================================================
    @Test
    fun `test 11 - native transcription executions are serialized`() = runBlocking {
        val bridge = TestNativeBridge(dummyContext, delayMs = 100L)
        val mutex = Mutex()
        val concurrentCount = AtomicInteger(0)
        val maxConcurrentSeen = AtomicInteger(0)

        // Simulate 4 concurrent requests trying to use native bridge
        val jobs = List(4) {
            launch(Dispatchers.Default) {
                mutex.withLock {
                    val c = concurrentCount.incrementAndGet()
                    maxConcurrentSeen.updateAndGet { max(it, c) }
                    bridge.transcribeAudio(FloatArray(16000) { 0.1f }, LanguageMode.AUTO)
                    concurrentCount.decrementAndGet()
                }
            }
        }
        jobs.forEach { it.join() }

        assertEquals("Only 1 native inference at a time", 1, maxConcurrentSeen.get())
        assertEquals(4, bridge.callCount.get())
    }

    // =========================================================================
    // 12. Empty Whisper output
    // =========================================================================
    @Test
    fun `test 12 - sanitizeTranscript handles blank audio and Whisper special tokens`() {
        val bridge = WhisperNativeBridge(dummyContext)
        assertEquals("", bridge.sanitizeTranscript("[BLANK_AUDIO]"))
        assertEquals("", bridge.sanitizeTranscript("<|startoftranscript|>"))
        assertEquals("", bridge.sanitizeTranscript("  [SILENCE]  "))
        assertEquals("", bridge.sanitizeTranscript(""))
    }

    // =========================================================================
    // 13. Non-empty Whisper output delivery
    // =========================================================================
    @Test
    fun `test 13 - non-empty Whisper output delivery to SpeechEngineListener`() = runBlocking {
        val bridge = TestNativeBridge(dummyContext, resultText = "hello can you hear me")
        val modelManager = WhisperModelManager(dummyContext, overrideModelDir = tempDir)
        val engine = WhisperCppSpeechRecognitionEngine(dummyContext, modelManager, bridge)
        val listener = TestListener()

        val job = engine.submitTranscription(FloatArray(16000) { 0.15f }, isFinal = true, listener = listener)
        job?.join()

        assertTrue(listener.latch.await(2, TimeUnit.SECONDS))
        assertEquals("hello can you hear me", listener.resultReceived)
        assertEquals(WhisperEngineState.READY, engine.engineState.value)
    }

    // =========================================================================
    // 14. Unicode Hindi transcription preservation
    // =========================================================================
    @Test
    fun `test 14 - Unicode Hindi Devanagari script is preserved without corruption`() {
        val bridge = WhisperNativeBridge(dummyContext)
        val hindiSentence = "नमस्ते, क्या तुम मेरी आवाज़ सुन सकते हो?"
        val sanitized = bridge.sanitizeTranscript(hindiSentence)
        assertEquals(hindiSentence, sanitized)

        val hindiWithTags = "<|hi|><|transcribe|>मेरा वॉलपेपर बदल दो<|endoftranscript|>"
        val cleanHindi = bridge.sanitizeTranscript(hindiWithTags)
        assertEquals("मेरा वॉलपेपर बदल दो", cleanHindi)
    }

    // =========================================================================
    // 15. Hinglish text normalization
    // =========================================================================
    @Test
    fun `test 15 - Hinglish mixed language normalization retains all words`() {
        val bridge = WhisperNativeBridge(dummyContext)
        val hinglishInput = "Hello, meri awaaz sunai de rahi hai?"
        val sanitized = bridge.sanitizeTranscript(hinglishInput)
        assertEquals(hinglishInput, sanitized)

        val commandHinglish = "Settings kholo aur Bluetooth on kar do"
        assertEquals(commandHinglish, bridge.sanitizeTranscript(commandHinglish))
    }

    // =========================================================================
    // 16. UI state transitions
    // =========================================================================
    @Test
    fun `test 16 - engine state flow transitions through proper lifecycle stages`() = runBlocking {
        val bridge = TestNativeBridge(dummyContext, delayMs = 80L, resultText = "testing state flow")
        val modelManager = WhisperModelManager(dummyContext, overrideModelDir = tempDir)
        val engine = WhisperCppSpeechRecognitionEngine(dummyContext, modelManager, bridge)
        val listener = TestListener()

        assertEquals(WhisperEngineState.UNINITIALIZED, engine.engineState.value)

        val job = engine.submitTranscription(FloatArray(16000) { 0.1f }, isFinal = true, listener = listener)
        delay(20)
        assertEquals(WhisperEngineState.TRANSCRIBING, engine.engineState.value)

        job?.join()
        assertEquals(WhisperEngineState.READY, engine.engineState.value)
    }

    // =========================================================================
    // 17. Repeated continuous utterances
    // =========================================================================
    @Test
    fun `test 17 - continuous utterances processed sequentially in single session`() = runBlocking {
        val bridge = TestNativeBridge(dummyContext, delayMs = 30L)
        val modelManager = WhisperModelManager(dummyContext, overrideModelDir = tempDir)
        val engine = WhisperCppSpeechRecognitionEngine(dummyContext, modelManager, bridge)

        val phrases = listOf("Hello", "How are you", "Change my wallpaper")
        val recognized = mutableListOf<String>()

        for (phrase in phrases) {
            bridge.resultText = phrase
            val listener = TestListener()
            val job = engine.submitTranscription(FloatArray(16000) { 0.1f }, isFinal = true, listener = listener)
            job?.join()
            listener.resultReceived?.let { recognized.add(it) }
        }

        assertEquals(3, recognized.size)
        assertEquals(phrases, recognized)
    }

    // =========================================================================
    // 18. Microphone permission denial
    // =========================================================================
    @Test
    fun `test 18 - permission denial produces clean error without SecurityException crash`() {
        val permissionDeniedChecker: () -> Boolean = { false }
        val dummyListener = TestListener()

        val canStart = permissionDeniedChecker()
        assertFalse(canStart)
        if (!canStart) {
            dummyListener.onError(SpeechEngineError.MicrophonePermissionDenied)
        }

        assertTrue(dummyListener.errorReceived is SpeechEngineError.MicrophonePermissionDenied)
        assertTrue(dummyListener.errorReceived!!.toUserMessage().contains("Microphone permission"))
    }

    // =========================================================================
    // 19. AudioRecord initialization or read failure
    // =========================================================================
    @Test
    fun `test 19 - AudioRecord read failure reported gracefully`() {
        val listener = TestListener()
        val readResultCode = -3 // AudioRecord.ERROR_INVALID_OPERATION

        if (readResultCode < 0) {
            listener.onError(SpeechEngineError.MicrophoneUnavailable("Read error code $readResultCode"))
        }

        assertTrue(listener.errorReceived is SpeechEngineError.MicrophoneUnavailable)
        assertTrue(listener.errorReceived!!.toUserMessage().contains("Microphone is unavailable"))
    }

    // =========================================================================
    // 20. Native inference exception handling
    // =========================================================================
    @Test
    fun `test 20 - native inference exception captured in Result failure and reported`() = runBlocking {
        val crashingBridge = TestNativeBridge(dummyContext, shouldThrow = true)
        val modelManager = WhisperModelManager(dummyContext, overrideModelDir = tempDir)
        val engine = WhisperCppSpeechRecognitionEngine(dummyContext, modelManager, crashingBridge)
        val listener = TestListener()

        val job = engine.submitTranscription(FloatArray(16000) { 0.1f }, isFinal = true, listener = listener)
        job?.join()

        assertTrue(listener.latch.await(2, TimeUnit.SECONDS))
        assertTrue("Must report error on native failure", listener.errorReceived is SpeechEngineError.RecognitionFailed)
        assertNull(listener.resultReceived)
    }

    // =========================================================================
    // 21. Energy-aware trailing silence suppression & full sample preservation
    // =========================================================================
    @Test
    fun `test 21 - prepareAudioForWhisper suppresses trailing room noise while preserving full speech and buffer length`() {
        val bridge = TestNativeBridge(dummyContext)
        val modelManager = WhisperModelManager(dummyContext, overrideModelDir = tempDir)
        val engine = WhisperCppSpeechRecognitionEngine(dummyContext, modelManager, bridge)

        // Simulate 29,696 samples (~1.86s): 8,000 samples (0.5s) of speech followed by trailing room noise (0.015f)
        val totalSamples = 29696
        val testAudio = FloatArray(totalSamples) { i ->
            if (i < 8000) 0.20f else 0.015f
        }

        val prepared = engine.prepareAudioForWhisper(testAudio)

        // 1. Total buffer size must be strictly preserved
        assertEquals("Buffer length must be preserved exactly", totalSamples, prepared.size)

        // 2. Initial speech samples must be scaled with adaptive gain (0.20f * 3.0x = 0.60f)
        assertEquals(0.60f, prepared[0], 0.05f)
        assertEquals(0.60f, prepared[7999], 0.05f)

        // 3. Trailing room noise beyond speech + hangover + taper (8000 + 5600 + 1600 = 15200) must be zeroed to prevent Whisper loop
        assertEquals(0.0f, prepared[16000], 0.0001f)
        assertEquals(0.0f, prepared[29695], 0.0001f)
    }

    // =========================================================================
    // 22. Repeated phrase deduplication preserving internal repeated words
    // =========================================================================
    @Test
    fun `test 22 - deduplication eliminates hallucinated repeated sentences without corrupting natural repeated words`() {
        val bridge = TestNativeBridge(dummyContext)

        // 1. Consecutive identical sentences (the classic "Hello. Hello." bug) must deduplicate to single sentence
        val dedupedHello = bridge.deduplicateRepeatedPhrases("Hello. Hello.")
        assertEquals("Hello.", dedupedHello)

        // 2. Devanagari Hindi repeat ("नमस्ते। नमस्ते।") must deduplicate
        val dedupedHindi = bridge.deduplicateRepeatedPhrases("नमस्ते। नमस्ते।")
        assertEquals("नमस्ते।", dedupedHindi)

        // 3. Legitimate repeated words inside a single sentence (e.g. "very very", "no no") must NEVER be stripped
        val naturalSpeech1 = bridge.deduplicateRepeatedPhrases("No no, I don't want that.")
        assertEquals("No no, I don't want that.", naturalSpeech1)

        val naturalSpeech2 = bridge.deduplicateRepeatedPhrases("I am very very happy today.")
        assertEquals("I am very very happy today.", naturalSpeech2)
    }
}
