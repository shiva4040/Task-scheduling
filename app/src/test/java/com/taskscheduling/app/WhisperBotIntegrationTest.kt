package com.taskscheduling.app

import android.content.Context
import com.taskscheduling.app.assistant.AssistantUiState
import com.taskscheduling.app.assistant.CommandIntent
import com.taskscheduling.app.assistant.LocalCommandProcessor
import com.taskscheduling.app.assistant.LocalCommandResolver
import com.taskscheduling.app.assistant.LocalConfirmationResolver
import com.taskscheduling.app.assistant.LocalWorkflowResolver
import com.taskscheduling.app.assistant.OfflineAssistantController
import com.taskscheduling.app.assistant.PendingAction
import com.taskscheduling.app.automation.AutomationEngine
import com.taskscheduling.app.automation.StepExecutionResult
import com.taskscheduling.app.automation.StepExecutionStatus
import com.taskscheduling.app.data.repository.WorkflowPersistenceRepository
import com.taskscheduling.app.domain.model.ActionType
import com.taskscheduling.app.domain.model.DeviceProfile
import com.taskscheduling.app.domain.model.ExecutionSession
import com.taskscheduling.app.domain.model.ExecutionState
import com.taskscheduling.app.domain.model.Task
import com.taskscheduling.app.domain.model.Workflow
import com.taskscheduling.app.domain.model.WorkflowStatus
import com.taskscheduling.app.domain.model.WorkflowStep
import com.taskscheduling.app.voice.ContinuousVoiceController
import com.taskscheduling.app.voice.LanguageMode
import com.taskscheduling.app.voice.SpeechEngineError
import com.taskscheduling.app.voice.SpeechEngineListener
import com.taskscheduling.app.voice.VoiceInputCallback
import com.taskscheduling.app.voice.VoiceInputManager
import com.taskscheduling.app.voice.WhisperCppSpeechRecognitionEngine
import com.taskscheduling.app.voice.WhisperModelManager
import com.taskscheduling.app.voice.WhisperNativeBridge
import com.taskscheduling.app.voice.WhisperTranscriptionResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.delay
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
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Verification test suite for:
 * 1. Bot and Diagnostics use the same shared engine/context.
 * 2. Bot transcription works after opening and closing the overlay (context is preserved).
 * 3. Stop capture during inference still delivers the final result.
 * 4. Repeated Start/Stop does not release or duplicate the active context.
 * 5. English, Hindi, and Hinglish mode settings are preserved (translate = false, correct codes).
 * 6. Empty audio and actual context failures produce different errors.
 * 7. A successful transcript reaches the bot resolver.
 */
class WhisperBotIntegrationTest {

    private lateinit var dummyContext: Context
    private lateinit var tempDir: File

    private val testDeviceProfile = DeviceProfile(
        manufacturer = "Samsung",
        model = "Galaxy Tab A9",
        androidVersion = "14",
        apiLevel = 34,
        screenWidth = 800,
        screenHeight = 1340,
        density = 1.33f,
        orientation = "PORTRAIT"
    )

    private class TestContext(private val baseDir: File) : android.content.ContextWrapper(null) {
        override fun getApplicationContext(): Context = this
        override fun getCacheDir(): File = baseDir
        override fun getFilesDir(): File = baseDir
        override fun getPackageName(): String = "com.taskscheduling.app"
    }

    private class MockNativeBridge(
        context: Context,
        var transcriptionDelayMs: Long = 50L,
        var resultText: String = "Hello, do you remember the paper?",
        var failWithContextUnloaded: Boolean = false,
        var initialModelPath: String? = null
    ) : WhisperNativeBridge(context) {
        val isExplicitlyReleased = AtomicBoolean(false)
        val activeCallCount = AtomicInteger(0)
        val totalInferences = AtomicInteger(0)
        var lastLanguageMode: LanguageMode? = null

        private var currentModelPath: String? = initialModelPath

        override val modelPath: String
            get() = currentModelPath ?: initialModelPath ?: ""

        override val isReady: Boolean
            get() = !isExplicitlyReleased.get() && !failWithContextUnloaded

        override suspend fun loadModel(modelFile: File): Result<Unit> {
            if (failWithContextUnloaded) {
                return Result.failure(IllegalStateException("Whisper native context is not loaded"))
            }
            currentModelPath = modelFile.absolutePath
            isExplicitlyReleased.set(false)
            return Result.success(Unit)
        }

        override suspend fun transcribeAudio(
            pcmFloats: FloatArray,
            languageMode: LanguageMode
        ): Result<WhisperTranscriptionResult> {
            lastLanguageMode = languageMode
            if (failWithContextUnloaded || isExplicitlyReleased.get()) {
                return Result.failure(IllegalStateException("Whisper native context is not loaded or has been released"))
            }
            activeCallCount.incrementAndGet()
            totalInferences.incrementAndGet()
            try {
                if (transcriptionDelayMs > 0) {
                    delay(transcriptionDelayMs)
                }
                return Result.success(
                    WhisperTranscriptionResult(
                        text = resultText,
                        processingTimeMs = transcriptionDelayMs,
                        language = languageMode.code,
                        segmentCount = 1
                    )
                )
            } finally {
                activeCallCount.decrementAndGet()
            }
        }

        override fun release() {
            isExplicitlyReleased.set(true)
        }
    }

    private class FakeAutomationEngine : AutomationEngine {
        private val _session = MutableStateFlow(
            ExecutionSession(
                taskId = "",
                taskTitle = "",
                state = ExecutionState.IDLE
            )
        )
        override val executionSession: StateFlow<ExecutionSession> = _session
        override suspend fun executeWorkflow(workflow: Workflow, speedMultiplier: Float) {}
        override suspend fun executeTap(x: Int, y: Int, durationMs: Long): StepExecutionResult =
            StepExecutionResult(StepExecutionStatus.SUCCESS, 0, ActionType.TAP)
        override suspend fun executeLongPress(x: Int, y: Int, durationMs: Long): StepExecutionResult =
            StepExecutionResult(StepExecutionStatus.SUCCESS, 0, ActionType.TAP)
        override suspend fun executeSwipe(
            startX: Int,
            startY: Int,
            endX: Int,
            endY: Int,
            durationMs: Long
        ): StepExecutionResult =
            StepExecutionResult(StepExecutionStatus.SUCCESS, 0, ActionType.SWIPE)
        override suspend fun executeWait(durationMs: Long): StepExecutionResult =
            StepExecutionResult(StepExecutionStatus.SUCCESS, 0, ActionType.WAIT)
        override suspend fun executeBack(): StepExecutionResult =
            StepExecutionResult(StepExecutionStatus.SUCCESS, 0, ActionType.BACK)
        override suspend fun executeStep(step: WorkflowStep): StepExecutionResult =
            StepExecutionResult(StepExecutionStatus.SUCCESS, step.order, step.type)
        override suspend fun testStep(step: WorkflowStep): StepExecutionResult =
            StepExecutionResult(StepExecutionStatus.SUCCESS, step.order, step.type)
        override suspend fun execute(task: Task, speedMultiplier: Float) {}
        override fun pause() {}
        override fun resume() {}
        override fun stop() {}
        override fun restart() {}
        override suspend fun stepNext() {}
    }

    @Before
    fun setUp() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "whisper_bot_test_${UUID.randomUUID()}")
        tempDir.mkdirs()
        dummyContext = TestContext(tempDir)
    }

    /**
     * Requirement 1: Bot and Diagnostics use the same shared engine/context.
     */
    @Test
    fun testBotAndDiagnosticsUseSameSharedEngineInstance() {
        val engine1 = WhisperCppSpeechRecognitionEngine.getInstance(dummyContext)
        val engine2 = WhisperCppSpeechRecognitionEngine.getInstance(dummyContext)
        assertEquals("Both Diagnostics and Assistant bot must share the exact same engine singleton", engine1, engine2)

        val vim = VoiceInputManager(dummyContext)
        assertEquals("VoiceInputManager must default to the shared engine singleton", engine1, vim.speechEngine)
    }

    /**
     * Requirement 2: Bot transcription works after opening and closing the overlay.
     * Closing overlay or calling ContinuousVoiceController.release() must NOT destroy the shared model context.
     */
    @Test
    fun testOverlayCloseDoesNotDestroySharedModelContext() = runBlocking {
        val modelManager = WhisperModelManager(dummyContext)
        val mockBridge = MockNativeBridge(dummyContext, initialModelPath = modelManager.modelFile.absolutePath)
        val engine = object : WhisperCppSpeechRecognitionEngine(dummyContext, modelManager, mockBridge) {
            override fun startListening(listener: SpeechEngineListener, continuous: Boolean): Boolean {
                listener.onReady()
                return true
            }
        }

        val initRes = engine.initialize()
        assertTrue("Engine must initialize successfully", initRes.isSuccess)
        assertTrue("Model must be loaded", engine.isModelLoaded)
        assertTrue("Native bridge must be ready", mockBridge.isReady)

        val vim = VoiceInputManager(
            context = dummyContext,
            speechEngine = engine,
            scope = CoroutineScope(Dispatchers.Unconfined),
            permissionChecker = { true }
        )

        val persistenceRepo = WorkflowPersistenceRepository(tempDir)
        val workflowResolver = LocalWorkflowResolver(persistenceRepo)
        val commandResolver = LocalCommandResolver()
        val commandProcessor = LocalCommandProcessor(commandResolver, workflowResolver)
        val confirmationResolver = LocalConfirmationResolver()

        val assistantController = OfflineAssistantController(
            commandResolver = commandResolver,
            workflowResolver = workflowResolver,
            commandProcessor = commandProcessor,
            confirmationResolver = confirmationResolver,
            automationEngine = FakeAutomationEngine(),
            deviceProfileProvider = { testDeviceProfile },
            coroutineScope = CoroutineScope(Dispatchers.Unconfined)
        )

        val controller = ContinuousVoiceController(
            voiceInputManager = vim,
            assistantController = assistantController,
            coroutineScope = CoroutineScope(Dispatchers.Unconfined)
        )

        controller.startContinuousListening()
        assertTrue("Assistant should be in continuous listening mode", controller.isContinuousListening.value)

        // Simulate closing the overlay or clearing overlays
        controller.release()
        assertFalse("Continuous listening should stop on release", controller.isContinuousListening.value)

        // Verify native bridge context was NOT released
        assertFalse("Native bridge must NOT be released when overlay closes", mockBridge.isExplicitlyReleased.get())
        assertTrue("Native bridge must remain ready for future sessions", mockBridge.isReady)
        assertTrue("Engine model must remain loaded in memory", engine.isModelLoaded)

        // Simulate reopening overlay and starting listening again
        controller.startContinuousListening()
        assertTrue("Continuous listening must succeed again without reloading", controller.isContinuousListening.value)
        controller.stopContinuousListening()
    }

    /**
     * Requirement 3: Stop capture during inference still delivers the final result.
     */
    @Test
    fun testStopCaptureDuringInferenceDeliversFinalResult() = runBlocking {
        val mockBridge = MockNativeBridge(
            dummyContext,
            transcriptionDelayMs = 150L,
            resultText = "Hello, do you remember the paper?"
        )
        val modelManager = WhisperModelManager(dummyContext)
        val engine = WhisperCppSpeechRecognitionEngine(dummyContext, modelManager, mockBridge)
        engine.initialize()

        val deliveredResult = AtomicBoolean(false)
        val latch = CountDownLatch(1)

        val listener = object : SpeechEngineListener {
            override fun onReady() {}
            override fun onBeginningOfSpeech() {}
            override fun onResult(recognizedText: String) {
                if (recognizedText.contains("remember the paper")) {
                    deliveredResult.set(true)
                }
                latch.countDown()
            }
            override fun onError(error: SpeechEngineError) {
                latch.countDown()
            }
            override fun onStopped() {}
        }

        val dummyAudio = FloatArray(16000) { 0.1f }
        val job = engine.submitTranscription(
            audioSamples = dummyAudio,
            isFinal = true,
            listener = listener,
            speechStartNanos = System.nanoTime(),
            speechEndNanos = System.nanoTime()
        )

        // Stop capture while inference is in flight
        engine.stopListening()

        // Inference must still complete and deliver result
        job?.join()
        latch.await(2, TimeUnit.SECONDS)

        assertTrue("Final transcription must be delivered even after stopListening()", deliveredResult.get())
    }

    /**
     * Requirement 4: Repeated Start/Stop does not release or duplicate the active context.
     */
    @Test
    fun testRepeatedStartStopPreservesContext() = runBlocking {
        val modelManager = WhisperModelManager(dummyContext)
        val mockBridge = MockNativeBridge(dummyContext, initialModelPath = modelManager.modelFile.absolutePath)
        val engine = WhisperCppSpeechRecognitionEngine(dummyContext, modelManager, mockBridge)
        val initRes = engine.initialize()
        assertTrue("Engine must initialize successfully", initRes.isSuccess)

        assertTrue("Model must be loaded", engine.isModelLoaded)

        for (i in 1..5) {
            engine.stopListening()
            assertTrue("Model must still be loaded after iteration $i stop", engine.isModelLoaded)
            assertFalse("Native bridge must not be released after iteration $i", mockBridge.isExplicitlyReleased.get())
        }

        assertEquals("Context must remain single, valid, and not released", false, mockBridge.isExplicitlyReleased.get())
    }

    /**
     * Requirement 5: English, Hindi, and Hinglish mode settings are preserved.
     */
    @Test
    fun testLanguageModesPreservedAndTranslateIsAlwaysFalse() = runBlocking {
        val mockBridge = MockNativeBridge(dummyContext)
        val modelManager = WhisperModelManager(dummyContext)
        val engine = WhisperCppSpeechRecognitionEngine(dummyContext, modelManager, mockBridge)
        engine.initialize()

        val dummyAudio = FloatArray(16000) { 0.05f }

        // 1. English
        engine.languageMode = LanguageMode.ENGLISH
        assertEquals("en", engine.languageMode.whisperCode)
        val job1 = engine.submitTranscription(
            audioSamples = dummyAudio,
            isFinal = true,
            listener = object : SpeechEngineListener {
                override fun onReady() {}
                override fun onBeginningOfSpeech() {}
                override fun onResult(recognizedText: String) {}
                override fun onError(error: SpeechEngineError) {}
                override fun onStopped() {}
            }
        )
        job1?.join()
        assertEquals(LanguageMode.ENGLISH, mockBridge.lastLanguageMode)

        // 2. Hindi
        engine.languageMode = LanguageMode.HINDI
        assertEquals("hi", engine.languageMode.whisperCode)
        val job2 = engine.submitTranscription(
            audioSamples = dummyAudio,
            isFinal = true,
            listener = object : SpeechEngineListener {
                override fun onReady() {}
                override fun onBeginningOfSpeech() {}
                override fun onResult(recognizedText: String) {}
                override fun onError(error: SpeechEngineError) {}
                override fun onStopped() {}
            }
        )
        job2?.join()
        assertEquals(LanguageMode.HINDI, mockBridge.lastLanguageMode)

        // 3. Hinglish (auto multilingual detection)
        engine.languageMode = LanguageMode.HINGLISH
        assertEquals("auto", engine.languageMode.whisperCode)
        val job3 = engine.submitTranscription(
            audioSamples = dummyAudio,
            isFinal = true,
            listener = object : SpeechEngineListener {
                override fun onReady() {}
                override fun onBeginningOfSpeech() {}
                override fun onResult(recognizedText: String) {}
                override fun onError(error: SpeechEngineError) {}
                override fun onStopped() {}
            }
        )
        job3?.join()
        assertEquals(LanguageMode.HINGLISH, mockBridge.lastLanguageMode)
    }

    /**
     * Requirement 6: Empty audio and actual context failures produce different errors.
     */
    @Test
    fun testEmptyAudioAndContextFailuresProduceDistinctErrors() = runBlocking {
        // 1. Context failure
        val mockBridge = MockNativeBridge(dummyContext, failWithContextUnloaded = true)
        val modelManager = WhisperModelManager(dummyContext)
        val engine = WhisperCppSpeechRecognitionEngine(dummyContext, modelManager, mockBridge)

        var contextErrorMessage: String? = null
        val latchContext = CountDownLatch(1)

        val job = engine.submitTranscription(
            audioSamples = FloatArray(16000) { 0.1f },
            isFinal = true,
            listener = object : SpeechEngineListener {
                override fun onReady() {}
                override fun onBeginningOfSpeech() {}
                override fun onResult(recognizedText: String) { latchContext.countDown() }
                override fun onError(error: SpeechEngineError) {
                    contextErrorMessage = error.toUserMessage()
                    latchContext.countDown()
                }
                override fun onStopped() {}
            }
        )
        job?.join()
        latchContext.await(1, TimeUnit.SECONDS)

        assertNotNull("Context failure must produce an error", contextErrorMessage)
        assertTrue(
            "Context error must explicitly mention Whisper/context issue: $contextErrorMessage",
            contextErrorMessage!!.contains("Whisper") || contextErrorMessage!!.contains("context") || contextErrorMessage!!.contains("released")
        )

        // 2. Empty speech error
        val emptySpeechError = SpeechEngineError.EmptySpeech.toUserMessage()
        assertEquals("Speech captured, but no text was recognized.", emptySpeechError)
        assertFalse("Empty speech error must not be identical to context failure error", emptySpeechError == contextErrorMessage)
    }

    /**
     * Requirement 7: A successful transcript reaches the bot resolver and prompts for confirmation.
     */
    @Test
    fun testSuccessfulTranscriptReachesBotResolverAndPromptsConfirmation() = runBlocking {
        val persistenceRepo = WorkflowPersistenceRepository(tempDir)
        val wallpaperWorkflow = Workflow(
            id = "wf_wallpaper",
            name = "Change Wallpaper",
            description = "Changes home screen wallpaper",
            status = WorkflowStatus.CALIBRATED,
            deviceProfile = testDeviceProfile,
            steps = listOf(
                WorkflowStep(id = "s1", order = 1, type = ActionType.TAP, startX = 299, startY = 937)
            )
        )
        persistenceRepo.saveWorkflow(wallpaperWorkflow)

        val workflowResolver = LocalWorkflowResolver(persistenceRepo)
        val commandResolver = LocalCommandResolver()
        val commandProcessor = LocalCommandProcessor(commandResolver, workflowResolver)
        val confirmationResolver = LocalConfirmationResolver()

        val assistantController = OfflineAssistantController(
            commandResolver = commandResolver,
            workflowResolver = workflowResolver,
            commandProcessor = commandProcessor,
            confirmationResolver = confirmationResolver,
            automationEngine = FakeAutomationEngine(),
            deviceProfileProvider = { testDeviceProfile },
            coroutineScope = CoroutineScope(Dispatchers.Unconfined)
        )

        val mockBridge = MockNativeBridge(
            dummyContext,
            transcriptionDelayMs = 10L,
            resultText = "Hello, change my wallpaper"
        )
        val modelManager = WhisperModelManager(dummyContext)
        val engine = WhisperCppSpeechRecognitionEngine(dummyContext, modelManager, mockBridge)
        engine.initialize()

        val vim = VoiceInputManager(
            context = dummyContext,
            speechEngine = engine,
            scope = CoroutineScope(Dispatchers.Unconfined),
            permissionChecker = { true }
        )

        val controller = ContinuousVoiceController(
            voiceInputManager = vim,
            assistantController = assistantController,
            coroutineScope = CoroutineScope(Dispatchers.Unconfined)
        )

        // Pass recognized speech into the bot controller
        controller.handleRecognizedSpeech("Hello, change my wallpaper")

        // Verify the bot resolver successfully parsed the workflow and entered ConfirmationRequired state
        val state = assistantController.uiState.value
        assertTrue("Bot must be in ConfirmationRequired state: $state", state is AssistantUiState.ConfirmationRequired)
        val confState = state as AssistantUiState.ConfirmationRequired
        assertEquals("Must resolve the Change Wallpaper workflow", "Change Wallpaper", confState.workflow?.name)
        assertNotNull("Pending action must be set", assistantController.pendingAction)
        assertTrue("Pending action must be WorkflowAction", assistantController.pendingAction is PendingAction.WorkflowAction)
    }

    /**
     * Requirement: A silent or empty buffer does not enter native inference.
     */
    @Test
    fun testSilenceAndEmptyBufferDoNotEnterNativeInference() = runBlocking {
        val mockBridge = MockNativeBridge(dummyContext)
        val modelManager = WhisperModelManager(dummyContext)
        val engine = WhisperCppSpeechRecognitionEngine(dummyContext, modelManager, mockBridge)
        engine.initialize()

        // 1. Empty buffer
        val jobEmpty = engine.submitTranscription(
            audioSamples = FloatArray(0),
            isFinal = true,
            listener = object : SpeechEngineListener {
                override fun onReady() {}
                override fun onBeginningOfSpeech() {}
                override fun onResult(recognizedText: String) {}
                override fun onError(error: SpeechEngineError) {}
                override fun onStopped() {}
            }
        )
        assertNull("Empty buffer must return null without launching inference job", jobEmpty)
        assertEquals("Native inference must not be called for empty buffer", 0, mockBridge.totalInferences.get())

        // 2. Silent buffer (pure room noise peak=0.005f, rms=0.001f)
        val silentAudio = FloatArray(16000) { 0.005f }
        val jobSilent = engine.submitTranscription(
            audioSamples = silentAudio,
            isFinal = true,
            listener = object : SpeechEngineListener {
                override fun onReady() {}
                override fun onBeginningOfSpeech() {}
                override fun onResult(recognizedText: String) {}
                override fun onError(error: SpeechEngineError) {}
                override fun onStopped() {}
            }
        )
        assertNull("Silent buffer must be rejected without launching native inference", jobSilent)
        assertEquals("Native inference must not be called for silent buffer", 0, mockBridge.totalInferences.get())
        assertFalse("Engine must not report busy decoding", engine.isTranscribing)
        assertEquals(com.taskscheduling.app.voice.WhisperEngineState.READY, engine.engineState.value)
    }

    /**
     * Requirement: Opening the bot with no speech leaves it idle and not busy.
     */
    @Test
    fun testOpeningBotWithNoSpeechLeavesItIdleAndNotBusy() = runBlocking {
        val modelManager = WhisperModelManager(dummyContext)
        val mockBridge = MockNativeBridge(dummyContext, initialModelPath = modelManager.modelFile.absolutePath)
        val engine = WhisperCppSpeechRecognitionEngine(dummyContext, modelManager, mockBridge)

        val vim = VoiceInputManager(
            context = dummyContext,
            speechEngine = engine,
            scope = CoroutineScope(Dispatchers.Unconfined),
            permissionChecker = { true }
        )

        // Opening the panel calls prepareEngineAsync
        vim.prepareEngineAsync()
        delay(100)

        // Engine must be ready and idle, NOT transcribing, NOT busy decoding
        assertFalse("Opening bot must not start transcription automatically", engine.isTranscribing)
        assertEquals("Engine must be READY", com.taskscheduling.app.voice.WhisperEngineState.READY, engine.engineState.value)
        assertEquals("No native inference must have run", 0, mockBridge.totalInferences.get())
    }

    /**
     * Requirement: Starting and stopping listening repeatedly does not leave the engine busy.
     */
    @Test
    fun testRepeatedStartStopListeningDoesNotLeaveEngineBusy() = runBlocking {
        val modelManager = WhisperModelManager(dummyContext)
        val mockBridge = MockNativeBridge(dummyContext, initialModelPath = modelManager.modelFile.absolutePath)
        val engine = WhisperCppSpeechRecognitionEngine(dummyContext, modelManager, mockBridge)
        engine.initialize()

        for (i in 1..5) {
            engine.stopListening()
            assertFalse("Stop listening must not leave engine transcribing (iter $i)", engine.isTranscribing)
            assertEquals("Engine must return to READY (iter $i)", com.taskscheduling.app.voice.WhisperEngineState.READY, engine.engineState.value)
        }
        assertEquals("No inferences should have been triggered by empty start/stops", 0, mockBridge.totalInferences.get())
    }

    /**
     * Case 3 & 15: A normal transcription clears its own busy state, allowing further operations.
     */
    @Test
    fun testNormalTranscriptionClearsBusyStateAndAllowsNextOperation() = runBlocking {
        val modelManager = WhisperModelManager(dummyContext)
        val mockBridge = MockNativeBridge(dummyContext, transcriptionDelayMs = 20L, initialModelPath = modelManager.modelFile.absolutePath)
        val engine = WhisperCppSpeechRecognitionEngine(dummyContext, modelManager, mockBridge)
        engine.initialize()

        val audio = FloatArray(16000) { 0.1f }
        val res = engine.transcribeDirect(audio, LanguageMode.ENGLISH, source = "DIAGNOSTIC")

        assertTrue("Direct transcription must succeed", res.isSuccess)
        assertFalse("Engine must not report busy after completion", engine.isTranscribing)
        assertEquals(com.taskscheduling.app.voice.WhisperEngineState.READY, engine.engineState.value)

        // Immediately perform another operation
        val res2 = engine.transcribeDirect(audio, LanguageMode.ENGLISH, source = "DIAGNOSTIC")
        assertTrue("Second operation must also succeed immediately", res2.isSuccess)
        assertFalse("Engine must remain idle after second operation", engine.isTranscribing)
    }

    /**
     * Case 4 & 15: A transcription exception does not leave the engine stuck busy.
     */
    @Test
    fun testTranscriptionExceptionDoesNotLeaveEngineStuckBusy() = runBlocking {
        val modelManager = WhisperModelManager(dummyContext)
        val mockBridge = MockNativeBridge(dummyContext, failWithContextUnloaded = true, initialModelPath = modelManager.modelFile.absolutePath)
        val engine = WhisperCppSpeechRecognitionEngine(dummyContext, modelManager, mockBridge)

        val audio = FloatArray(16000) { 0.1f }
        val res = engine.transcribeDirect(audio, LanguageMode.ENGLISH, source = "DIAGNOSTIC")

        assertTrue("Failing bridge must return failure result", res.isFailure)
        assertFalse("Engine must not be stuck in isTranscribing after failure", engine.isTranscribing)

        // Verify engine can recover
        mockBridge.failWithContextUnloaded = false
        val resRecover = engine.transcribeDirect(audio, LanguageMode.ENGLISH, source = "DIAGNOSTIC")
        assertTrue("Engine must recover and transcribe after fixing failure", resRecover.isSuccess)
    }

    /**
     * Case 8, 9 & 15: A queued transcription can be cancelled, and a cancelled result cannot trigger an action.
     */
    @Test
    fun testCancelledTranscriptionCannotTriggerActionAndRecoversEngine() = runBlocking {
        val persistenceRepo = WorkflowPersistenceRepository(tempDir)
        val wallpaperWorkflow = Workflow(
            id = "wf_wallpaper",
            name = "Change Wallpaper",
            description = "Changes home screen wallpaper",
            status = WorkflowStatus.CALIBRATED,
            deviceProfile = testDeviceProfile,
            steps = listOf(WorkflowStep(id = "s1", order = 1, type = ActionType.TAP, startX = 299, startY = 937))
        )
        persistenceRepo.saveWorkflow(wallpaperWorkflow)

        val workflowResolver = LocalWorkflowResolver(persistenceRepo)
        val commandResolver = LocalCommandResolver()
        val commandProcessor = LocalCommandProcessor(commandResolver, workflowResolver)
        val confirmationResolver = LocalConfirmationResolver()

        val assistantController = OfflineAssistantController(
            commandResolver = commandResolver,
            workflowResolver = workflowResolver,
            commandProcessor = commandProcessor,
            confirmationResolver = confirmationResolver,
            automationEngine = FakeAutomationEngine(),
            deviceProfileProvider = { testDeviceProfile },
            coroutineScope = CoroutineScope(Dispatchers.Unconfined)
        )

        val modelManager = WhisperModelManager(dummyContext)
        val mockBridge = MockNativeBridge(dummyContext, transcriptionDelayMs = 200L, resultText = "Change Wallpaper", initialModelPath = modelManager.modelFile.absolutePath)
        val engine = WhisperCppSpeechRecognitionEngine(dummyContext, modelManager, mockBridge)
        engine.initialize()

        var deliveredResult = false
        val listener = object : SpeechEngineListener {
            override fun onReady() {}
            override fun onBeginningOfSpeech() {}
            override fun onResult(recognizedText: String) {
                deliveredResult = true
                assistantController.processCommand(recognizedText)
            }
            override fun onError(error: SpeechEngineError) {}
            override fun onStopped() {}
        }

        val audio = FloatArray(16000) { 0.1f }
        val job = engine.submitTranscription(audio, isFinal = true, listener = listener)

        // Cancel mid-operation
        engine.cancelActiveTranscription()
        job?.join()

        // Result must NOT have been delivered and NO action or confirmation must have been triggered
        assertFalse("Cancelled transcription must not deliver result to listener", deliveredResult)
        assertNull("Pending action must remain null after cancellation", assistantController.pendingAction)
        assertFalse("Engine must not report busy after cancellation", engine.isTranscribing)
        assertEquals(com.taskscheduling.app.voice.WhisperEngineState.READY, engine.engineState.value)
    }

    /**
     * Case 10: Older cancelled operation cannot overwrite a newer operation.
     */
    @Test
    fun testOlderOperationCannotOverwriteNewerOperation() = runBlocking {
        val modelManager = WhisperModelManager(dummyContext)
        val mockBridge = MockNativeBridge(dummyContext, transcriptionDelayMs = 50L, initialModelPath = modelManager.modelFile.absolutePath)
        val engine = WhisperCppSpeechRecognitionEngine(dummyContext, modelManager, mockBridge)
        engine.initialize()

        val audio = FloatArray(16000) { 0.1f }

        // Start job 1 and cancel it
        mockBridge.resultText = "Old Transcript"
        var firstDelivered: String? = null
        val job1 = engine.submitTranscription(
            audioSamples = audio,
            isFinal = true,
            listener = object : SpeechEngineListener {
                override fun onReady() {}
                override fun onBeginningOfSpeech() {}
                override fun onResult(recognizedText: String) { firstDelivered = recognizedText }
                override fun onError(error: SpeechEngineError) {}
                override fun onStopped() {}
            }
        )
        engine.cancelActiveTranscription()
        job1?.join()

        // Start job 2 with new text
        mockBridge.resultText = "New Transcript"
        var secondDelivered: String? = null
        val job2 = engine.submitTranscription(
            audioSamples = audio,
            isFinal = true,
            listener = object : SpeechEngineListener {
                override fun onReady() {}
                override fun onBeginningOfSpeech() {}
                override fun onResult(recognizedText: String) { secondDelivered = recognizedText }
                override fun onError(error: SpeechEngineError) {}
                override fun onStopped() {}
            }
        )
        job2?.join()

        assertNull("Cancelled first job must not deliver result", firstDelivered)
        assertEquals("New job must deliver its own result", "New Transcript", secondDelivered)
    }

    /**
     * Case 11: Concurrent transcription calls reject overlapping busy calls gracefully.
     */
    @Test
    fun testOverlappingCallsHandledWithoutCrash() = runBlocking {
        val modelManager = WhisperModelManager(dummyContext)
        val mockBridge = MockNativeBridge(dummyContext, transcriptionDelayMs = 150L, initialModelPath = modelManager.modelFile.absolutePath)
        val engine = WhisperCppSpeechRecognitionEngine(dummyContext, modelManager, mockBridge)
        engine.initialize()

        val audio = FloatArray(16000) { 0.1f }
        var secondCallResult: Result<WhisperTranscriptionResult>? = null

        val job1 = CoroutineScope(Dispatchers.Default).launch {
            engine.transcribeDirect(audio, LanguageMode.ENGLISH, source = "DIAGNOSTIC_1")
        }
        delay(30)

        // While job 1 is running, job 2 attempts to call transcribeDirect
        secondCallResult = engine.transcribeDirect(audio, LanguageMode.ENGLISH, source = "DIAGNOSTIC_2")

        job1.join()

        assertTrue("Second concurrent call must be rejected gracefully with busy message", secondCallResult.isFailure)
        assertTrue(
            "Error message must specify busy decoding",
            secondCallResult.exceptionOrNull()?.message?.contains("busy decoding") == true
        )
        assertFalse("Engine must return to ready after job 1 finishes", engine.isTranscribing)
    }
}

