package com.taskscheduling.app

import android.content.Context
import com.taskscheduling.app.voice.LanguageMode
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
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.lang.reflect.Proxy
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Targeted tests for Whisper Small Q5_1 transcription coroutine lifecycle,
 * isolating microphone capture lifecycle from Whisper inference lifecycle.
 */
class WhisperTranscriptionLifecycleTest {

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
        tempDir = File(System.getProperty("java.io.tmpdir"), "whisper_lifecycle_${UUID.randomUUID()}")
        tempDir.mkdirs()
        dummyContext = DummyContext(tempDir)
    }

    private open class FakeNativeBridge(
        context: Context,
        var transcriptionDelayMs: Long = 100L,
        var transcriptionResultText: String = "hello hello",
        var shouldFail: Boolean = false
    ) : WhisperNativeBridge(context) {
        val isReleased = AtomicBoolean(false)
        val inferenceRunning = AtomicBoolean(false)
        val inferenceCount = AtomicInteger(0)
        val releaseWhileInferenceCount = AtomicInteger(0)

        override val isReady: Boolean
            get() = !isReleased.get()

        override suspend fun transcribeAudio(
            pcmFloats: FloatArray,
            languageMode: LanguageMode
        ): Result<WhisperTranscriptionResult> {
            if (isReleased.get()) {
                return Result.failure(IllegalStateException("Native bridge already released"))
            }
            if (inferenceRunning.get()) {
                // Detected overlapping native transcription!
                releaseWhileInferenceCount.incrementAndGet()
            }
            inferenceRunning.set(true)
            inferenceCount.incrementAndGet()
            try {
                if (transcriptionDelayMs > 0) {
                    delay(transcriptionDelayMs)
                }
                if (shouldFail) {
                    return Result.failure(RuntimeException("Simulated native failure"))
                }
                return Result.success(
                    WhisperTranscriptionResult(
                        text = transcriptionResultText,
                        processingTimeMs = transcriptionDelayMs,
                        language = "en",
                        segmentCount = 1
                    )
                )
            } finally {
                inferenceRunning.set(false)
            }
        }

        override fun release() {
            if (inferenceRunning.get()) {
                releaseWhileInferenceCount.incrementAndGet()
            }
            isReleased.set(true)
        }
    }

    private class RecordingListener : SpeechEngineListener {
        var readyCalled = false
        var stoppedCalled = false
        var resultText: String? = null
        var partialText: String? = null
        var errorOccurred: String? = null
        val resultLatch = CountDownLatch(1)

        override fun onReady() { readyCalled = true }
        override fun onBeginningOfSpeech() {}
        override fun onAudioLevel(rmsLevel: Float, peakLevel: Float, inputDetected: Boolean) {}
        override fun onPartialResult(hypothesis: String) { this.partialText = hypothesis }
        override fun onResult(recognizedText: String) {
            this.resultText = recognizedText
            resultLatch.countDown()
        }
        override fun onError(error: com.taskscheduling.app.voice.SpeechEngineError) {
            this.errorOccurred = error.toUserMessage()
            resultLatch.countDown()
        }
        override fun onStopped() { stoppedCalled = true }
    }

    // =========================================================================
    // 1. stopListening() DOES NOT CANCEL ACTIVE TRANSCRIPTION
    // =========================================================================
    @Test
    fun `test stopListening does not cancel active transcription`() = runBlocking {
        val fakeBridge = FakeNativeBridge(dummyContext, transcriptionDelayMs = 200L, transcriptionResultText = "hello hello")
        val modelManager = WhisperModelManager(dummyContext, overrideModelDir = tempDir)
        val engine = WhisperCppSpeechRecognitionEngine(dummyContext, modelManager, fakeBridge)
        val listener = RecordingListener()

        val sampleAudio = FloatArray(16000) { 0.1f }
        val job = engine.submitTranscription(sampleAudio, isFinal = true, listener = listener)
        assertNotNull("Transcription job must be created", job)
        assertTrue("Transcription job must be active", job!!.isActive)

        // Give coroutine brief moment to enter transcribeAudio
        delay(30)
        assertEquals(WhisperEngineState.TRANSCRIBING, engine.engineState.value)

        // Call stopListening while transcription is active
        engine.stopListening()

        // CRITICAL CHECK: In-flight transcription job MUST NOT be cancelled by stopListening
        assertFalse("Job must not be cancelled by stopListening", job.isCancelled)
        assertTrue("Job must still be active to finish inference", job.isActive)

        // Wait for inference to finish
        job.join()
        assertTrue("Result latch must trigger", listener.resultLatch.await(2, TimeUnit.SECONDS))
        assertEquals("hello hello", listener.resultText)
        assertEquals(WhisperEngineState.READY, engine.engineState.value)
    }

    // =========================================================================
    // 2. AUDIO CAPTURE JOB CAN FINISH WHILE TRANSCRIPTION CONTINUES
    // =========================================================================
    @Test
    fun `test audio capture job can finish while transcription continues`() = runBlocking {
        val fakeBridge = FakeNativeBridge(dummyContext, transcriptionDelayMs = 250L, transcriptionResultText = "capture finished test")
        val modelManager = WhisperModelManager(dummyContext, overrideModelDir = tempDir)
        val engine = WhisperCppSpeechRecognitionEngine(dummyContext, modelManager, fakeBridge)
        val listener = RecordingListener()

        val captureScope = CoroutineScope(Dispatchers.Default + Job())
        val sampleAudio = FloatArray(16000) { 0.2f }

        // Start capture simulation
        var transcriptionJob: Job? = null
        val captureJob = captureScope.launch {
            // Audio recording completes, buffer extracted
            val immutableAudio = sampleAudio.copyOf()
            // Submit to independent engine transcription scope
            transcriptionJob = engine.submitTranscription(immutableAudio, isFinal = true, listener = listener)
        }
        captureJob.join()

        // Capture coroutine is finished, captureScope children cancelled
        captureScope.coroutineContext.cancelChildren()

        // Whisper transcription continues unimpeded
        assertNotNull(transcriptionJob)
        assertTrue("Transcription must still be active after capture scope finished", transcriptionJob!!.isActive)
        assertFalse("Transcription must not be cancelled", transcriptionJob!!.isCancelled)

        transcriptionJob!!.join()
        assertTrue(listener.resultLatch.await(5, TimeUnit.SECONDS))
        assertEquals("capture finished test", listener.resultText)
    }

    // =========================================================================
    // 3. transcriptionScope SURVIVES CAPTURE COROUTINE CANCELLATION
    // =========================================================================
    @Test
    fun `test transcriptionScope survives capture coroutine cancellation`() = runBlocking {
        val fakeBridge = FakeNativeBridge(dummyContext, transcriptionDelayMs = 200L, transcriptionResultText = "scope survival test")
        val modelManager = WhisperModelManager(dummyContext, overrideModelDir = tempDir)
        val engine = WhisperCppSpeechRecognitionEngine(dummyContext, modelManager, fakeBridge)
        val listener = RecordingListener()

        val captureScope = CoroutineScope(Dispatchers.Default + Job())
        val jobSubmittedLatch = CountDownLatch(1)
        var transcriptionJob: Job? = null

        captureScope.launch {
            transcriptionJob = engine.submitTranscription(FloatArray(16000) { 0.15f }, isFinal = true, listener = listener)
            jobSubmittedLatch.countDown()
            // Abrupt capture cancellation occurs (e.g. mic error or timeout)
            throw CancellationException("Audio capture aborted")
        }

        assertTrue(jobSubmittedLatch.await(5, TimeUnit.SECONDS))
        // Capture scope cancelled abruptly
        captureScope.cancel()

        // Engine transcription scope must remain completely intact
        val engineScope = engine.getOrCreateTranscriptionScope()
        assertTrue("Engine transcriptionScope must remain active", engineScope.coroutineContext[Job]?.isActive == true)
        assertNotNull(transcriptionJob)
        assertFalse("Inference job must not be cancelled", transcriptionJob!!.isCancelled)

        transcriptionJob!!.join()
        assertTrue(listener.resultLatch.await(5, TimeUnit.SECONDS))
        assertEquals("scope survival test", listener.resultText)
    }

    // =========================================================================
    // 4. release() SAFELY WAITS FOR ACTIVE INFERENCE
    // =========================================================================
    @Test
    fun `test release safely waits for active inference`() = runBlocking {
        val fakeBridge = FakeNativeBridge(dummyContext, transcriptionDelayMs = 300L, transcriptionResultText = "release safe")
        val modelManager = WhisperModelManager(dummyContext, overrideModelDir = tempDir)
        val engine = WhisperCppSpeechRecognitionEngine(dummyContext, modelManager, fakeBridge)
        val listener = RecordingListener()

        val job = engine.submitTranscription(FloatArray(16000) { 0.05f }, isFinal = true, listener = listener)
        assertNotNull(job)
        delay(50) // Ensure inference has entered native bridge

        // Call release on engine while inference is running
        val releaseStart = System.currentTimeMillis()
        engine.release()
        val releaseDuration = System.currentTimeMillis() - releaseStart

        // release() must wait for the inference job to complete before destroying native context
        assertTrue("release must wait for active inference (~200-300ms)", releaseDuration >= 150L)
        assertEquals(0, fakeBridge.releaseWhileInferenceCount.get())
        assertTrue("Native bridge must be released after wait", fakeBridge.isReleased.get())
        assertEquals(WhisperEngineState.RELEASED, engine.engineState.value)
    }

    // =========================================================================
    // 5. SUCCESSFUL WHISPER RESULT REACHES CONTROLLER / LISTENER
    // =========================================================================
    @Test
    fun `test successful Whisper result reaches controller`() = runBlocking {
        val fakeBridge = FakeNativeBridge(dummyContext, transcriptionDelayMs = 50L, transcriptionResultText = "turn on bluetooth")
        val modelManager = WhisperModelManager(dummyContext, overrideModelDir = tempDir)
        val engine = WhisperCppSpeechRecognitionEngine(dummyContext, modelManager, fakeBridge)
        val listener = RecordingListener()

        val job = engine.submitTranscription(FloatArray(16000) { 0.2f }, isFinal = true, listener = listener)
        job?.join()

        assertTrue(listener.resultLatch.await(5, TimeUnit.SECONDS))
        assertEquals("turn on bluetooth", listener.resultText)
        assertNull("No error should be dispatched", listener.errorOccurred)
    }

    // =========================================================================
    // 6. EMPTY WHISPER RESULT IS HANDLED CORRECTLY
    // =========================================================================
    @Test
    fun `test empty Whisper result is handled correctly without crash`() = runBlocking {
        val fakeBridge = FakeNativeBridge(dummyContext, transcriptionDelayMs = 50L, transcriptionResultText = "")
        val modelManager = WhisperModelManager(dummyContext, overrideModelDir = tempDir)
        val engine = WhisperCppSpeechRecognitionEngine(dummyContext, modelManager, fakeBridge)
        val listener = RecordingListener()

        val job = engine.submitTranscription(FloatArray(16000) { 0.0f }, isFinal = true, listener = listener)
        job?.join()

        assertTrue(listener.resultLatch.await(5, TimeUnit.SECONDS))
        assertEquals("", listener.resultText)
        assertNull("Empty text must not trigger error", listener.errorOccurred)
        assertEquals(WhisperEngineState.READY, engine.engineState.value)
    }

    // =========================================================================
    // 7. EXPLICIT ENGINE DESTRUCTION CANCELS TRANSCRIPTION SAFELY
    // =========================================================================
    @Test
    fun `test explicit engine destruction cancels transcription safely`() = runBlocking {
        val fakeBridge = FakeNativeBridge(dummyContext, transcriptionDelayMs = 500L, transcriptionResultText = "cancelled text")
        val modelManager = WhisperModelManager(dummyContext, overrideModelDir = tempDir)
        val engine = WhisperCppSpeechRecognitionEngine(dummyContext, modelManager, fakeBridge)
        val listener = RecordingListener()

        val job = engine.submitTranscription(FloatArray(16000) { 0.1f }, isFinal = true, listener = listener)
        assertNotNull(job)
        delay(30)

        // Explicit user cancellation
        engine.cancelListening()

        assertTrue("Job must be explicitly cancelled", job!!.isCancelled)
        job.join()
        // Result must NOT have been delivered
        assertNull(listener.resultText)
        assertEquals(WhisperEngineState.READY, engine.engineState.value)
    }

    // =========================================================================
    // 8. MULTIPLE SIMULTANEOUS TRANSCRIPTION JOBS ARE PREVENTED
    // =========================================================================
    @Test
    fun `test multiple simultaneous transcription jobs are prevented`() = runBlocking {
        val fakeBridge = FakeNativeBridge(dummyContext, transcriptionDelayMs = 300L, transcriptionResultText = "final text")
        val modelManager = WhisperModelManager(dummyContext, overrideModelDir = tempDir)
        val engine = WhisperCppSpeechRecognitionEngine(dummyContext, modelManager, fakeBridge)
        val listener = RecordingListener()

        // 1. Submit intermediate partial chunk
        val partialJob = engine.submitTranscription(FloatArray(16000) { 0.1f }, isFinal = false, listener = listener)
        assertNotNull("First partial job accepted", partialJob)

        // 2. Submit second intermediate partial chunk while first is active -> Should be dropped (returns null)
        val droppedPartialJob = engine.submitTranscription(FloatArray(16000) { 0.1f }, isFinal = false, listener = listener)
        assertNull("Secondary partial job must be dropped when engine is busy", droppedPartialJob)

        // 3. Submit final transcription -> Must cancel/supersede partial chunk
        val finalJob = engine.submitTranscription(FloatArray(16000) { 0.2f }, isFinal = true, listener = listener)
        assertNotNull("Final job accepted", finalJob)
        assertTrue("Partial job must be cancelled by final job", partialJob!!.isCancelled)

        finalJob!!.join()
        assertTrue(listener.resultLatch.await(5, TimeUnit.SECONDS))
        assertEquals("final text", listener.resultText)
        assertEquals("Zero overlapping native inferences", 0, fakeBridge.releaseWhileInferenceCount.get())
    }

    // =========================================================================
    // 9. CANCELLED QUEUED JOB DOES NOT RUN NATIVE TRANSCRIPTION ON RESUME
    // =========================================================================
    @Test
    fun `test cancelled queued job does not run native transcription after prior job completes`() = runBlocking {
        val fakeBridge = FakeNativeBridge(dummyContext, transcriptionDelayMs = 200L, transcriptionResultText = "first")
        val modelManager = WhisperModelManager(dummyContext, overrideModelDir = tempDir)
        val engine = WhisperCppSpeechRecognitionEngine(dummyContext, modelManager, fakeBridge)
        val listener1 = RecordingListener()
        val listener2 = RecordingListener()

        // Job 1 runs for 200ms
        val job1 = engine.submitTranscription(FloatArray(16000) { 0.1f }, isFinal = true, listener = listener1)
        assertNotNull(job1)

        // Job 2 is submitted, cancelling Job 1 and queuing behind Job 1's join
        val job2 = engine.submitTranscription(FloatArray(16000) { 0.2f }, isFinal = true, listener = listener2)
        assertNotNull(job2)

        // Cancel Job 2 while it is waiting for Job 1 to finish
        job2!!.cancel()

        job1!!.join()
        job2.join()

        // Only Job 1 ran inference. Job 2 was cancelled while waiting and ensureActive() prevented it from running inference
        assertEquals("Only Job 1 should have run native inference", 1, fakeBridge.inferenceCount.get())
        assertNull("Cancelled Job 2 must not deliver result", listener2.resultText)
    }

    // =========================================================================
    // 10. TRANSCRIBE AUDIO PROPAGATES COROUTINE CANCELLATION
    // =========================================================================
    @Test
    fun `test transcribeAudio propagates CancellationException without swallowing`() = runBlocking {
        val bridge = object : WhisperNativeBridge(dummyContext) {
            override val isReady: Boolean = true
            override suspend fun transcribeAudio(pcmFloats: FloatArray, languageMode: LanguageMode): Result<WhisperTranscriptionResult> {
                // Check cooperative cancellation
                coroutineContext.ensureActive()
                return Result.success(WhisperTranscriptionResult("ok", 10L, "en", 1))
            }
        }

        val job = launch {
            try {
                // Cancel before running
                kotlin.coroutines.coroutineContext[Job]?.cancel()
                bridge.transcribeAudio(FloatArray(100), LanguageMode.AUTO)
            } catch (ce: CancellationException) {
                // Propagated properly
                throw ce
            }
        }
        job.join()
        assertTrue("Job must be marked cancelled", job.isCancelled)
    }
}

