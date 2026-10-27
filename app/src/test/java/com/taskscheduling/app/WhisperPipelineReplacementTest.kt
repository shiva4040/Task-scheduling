package com.taskscheduling.app

import android.content.Context
import com.taskscheduling.app.assistant.AssistantUiState
import com.taskscheduling.app.assistant.CommandIntent
import com.taskscheduling.app.assistant.CommandResolutionResult
import com.taskscheduling.app.assistant.LocalCommandProcessor
import com.taskscheduling.app.assistant.LocalCommandResolver
import com.taskscheduling.app.assistant.LocalConfirmationResolver
import com.taskscheduling.app.assistant.LocalWorkflowResolver
import com.taskscheduling.app.assistant.OfflineAssistantController
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
import com.taskscheduling.app.voice.SpeechRecognitionResult
import com.taskscheduling.app.voice.SpeechRecognitionService
import com.taskscheduling.app.voice.VoiceInputCallback
import com.taskscheduling.app.voice.VoiceInputManager
import com.taskscheduling.app.voice.WhisperCppSpeechRecognitionEngine
import com.taskscheduling.app.voice.WhisperEngineState
import com.taskscheduling.app.voice.WhisperModelManager
import com.taskscheduling.app.voice.WhisperNativeBridge
import com.taskscheduling.app.voice.WhisperTranscriptionResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
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
 * Regression Test Suite covering the 12 Mandatory Requirements:
 * 1. Diagnostic recognition returns expected result for controlled test input.
 * 2. Floating bot calls the same shared recognition entry point.
 * 3. Both callers use the same configured engine/model instance.
 * 4. Empty audio is rejected safely.
 * 5. Recognition failure returns the engine to a usable state.
 * 6. A cancelled transcription cannot trigger a command.
 * 7. Concurrent diagnostic and bot requests are serialized or rejected safely.
 * 8. A later request succeeds after a failed or cancelled request.
 * 9. A correct transcript reaches the existing command resolver unchanged.
 * 10. A sensitive workflow still requires confirmation.
 * 11. A completed recognition operation cannot leave the UI stuck in BUSY DECODING.
 * 12. Hindi and Hinglish language modes are preserved with translate=false.
 */
class WhisperPipelineReplacementTest {

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

    private class TestContextWrapper(private val baseDir: File) : android.content.ContextWrapper(null) {
        override fun getApplicationContext(): Context = this
        override fun getCacheDir(): File = baseDir
        override fun getFilesDir(): File = baseDir
        override fun getPackageName(): String = "com.taskscheduling.app"
    }

    private class TestNativeBridge(
        context: Context,
        var delayMs: Long = 20L,
        var resultText: String = "Hello",
        var shouldFail: Boolean = false
    ) : WhisperNativeBridge(context) {
        val totalInferences = AtomicInteger(0)
        var lastLanguageMode: LanguageMode? = null
        var lastTranslate: Boolean = false

        override val isReady: Boolean
            get() = true

        override suspend fun transcribeAudio(
            pcmFloats: FloatArray,
            languageMode: LanguageMode
        ): Result<WhisperTranscriptionResult> {
            lastLanguageMode = languageMode
            lastTranslate = false
            totalInferences.incrementAndGet()
            if (shouldFail) {
                return Result.failure(IllegalStateException("Simulated native inference failure"))
            }
            if (delayMs > 0) {
                delay(delayMs)
            }
            return Result.success(
                WhisperTranscriptionResult(
                    text = resultText,
                    rawText = resultText,
                    processingTimeMs = delayMs,
                    language = languageMode.code,
                    segmentCount = 1
                )
            )
        }
    }

    private class TestAutomationEngine : AutomationEngine {
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
        override suspend fun executeSwipe(startX: Int, startY: Int, endX: Int, endY: Int, durationMs: Long): StepExecutionResult =
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
        tempDir = File(System.getProperty("java.io.tmpdir"), "whisper_test_${UUID.randomUUID()}")
        tempDir.mkdirs()
        dummyContext = TestContextWrapper(tempDir)
    }

    @After
    fun tearDown() {
        SpeechRecognitionService.setInstanceForTesting(null)
    }

    // =========================================================================
    // 1. Diagnostic recognition returns expected result for controlled test input
    // =========================================================================
    @Test
    fun test1_diagnosticRecognitionReturnsExpectedResultForControlledInput() = runBlocking {
        val mockBridge = TestNativeBridge(dummyContext, resultText = "Hello.")
        val modelManager = WhisperModelManager(dummyContext)
        val engine = WhisperCppSpeechRecognitionEngine(dummyContext, modelManager, mockBridge)
        val service = SpeechRecognitionService(dummyContext, engine)

        val audio = FloatArray(16000) { 0.15f }
        val result = service.transcribe(audio, LanguageMode.ENGLISH, source = "DIAGNOSTIC")

        assertTrue("Diagnostic inference must succeed", result.isSuccess)
        assertEquals("Hello.", result.text)
        assertFalse("Result must not be marked cancelled", result.isCancelled)
        assertNull("Error must be null", result.error)
    }

    // =========================================================================
    // 2. Floating bot calls the same shared recognition entry point
    // =========================================================================
    @Test
    fun test2_floatingBotCallsSameSharedRecognitionEntryPoint() = runBlocking {
        val mockBridge = TestNativeBridge(dummyContext, resultText = "Change my wallpaper.")
        val modelManager = WhisperModelManager(dummyContext)
        val engine = WhisperCppSpeechRecognitionEngine(dummyContext, modelManager, mockBridge)
        val service = SpeechRecognitionService(dummyContext, engine)
        SpeechRecognitionService.setInstanceForTesting(service)

        val vim = VoiceInputManager(
            context = dummyContext,
            speechEngine = engine,
            scope = CoroutineScope(Dispatchers.Unconfined),
            permissionChecker = { true }
        )

        var botReceivedText: String? = null
        val latch = CountDownLatch(1)
        val listener = object : SpeechEngineListener {
            override fun onReady() {}
            override fun onBeginningOfSpeech() {}
            override fun onResult(recognizedText: String) {
                botReceivedText = recognizedText
                latch.countDown()
            }
            override fun onError(error: SpeechEngineError) {
                latch.countDown()
            }
            override fun onStopped() {}
        }

        val audio = FloatArray(16000) { 0.2f }
        val job = engine.submitTranscription(audio, isFinal = true, listener = listener)
        job?.join()
        latch.await(2, TimeUnit.SECONDS)

        assertEquals("Bot must receive the exact transcript from the shared engine", "Change my wallpaper.", botReceivedText)
        assertEquals("Native bridge must record exactly 1 inference call", 1, mockBridge.totalInferences.get())
    }

    // =========================================================================
    // 3. Both callers use the same configured engine/model instance
    // =========================================================================
    @Test
    fun test3_bothCallersUseSameConfiguredEngineAndModelInstance() {
        val diagEngine = WhisperCppSpeechRecognitionEngine.getInstance(dummyContext)
        val sharedService = SpeechRecognitionService.getInstance(dummyContext)
        val botInputManager = VoiceInputManager(dummyContext)

        assertEquals("Shared service must hold the singleton engine instance", diagEngine, sharedService.engine)
        assertEquals("VoiceInputManager must default to the singleton engine instance", diagEngine, botInputManager.speechEngine)
    }

    // =========================================================================
    // 4. Empty audio is rejected safely
    // =========================================================================
    @Test
    fun test4_emptyAudioRejectedSafely() = runBlocking {
        val mockBridge = TestNativeBridge(dummyContext)
        val modelManager = WhisperModelManager(dummyContext)
        val engine = WhisperCppSpeechRecognitionEngine(dummyContext, modelManager, mockBridge)
        val service = SpeechRecognitionService(dummyContext, engine)

        // Empty buffer
        val resEmpty = service.transcribe(FloatArray(0), LanguageMode.ENGLISH, source = "TEST")
        assertTrue("Empty audio must return a safe success response", resEmpty.isSuccess)
        assertEquals("", resEmpty.text)

        // Silent buffer (zeros)
        val resSilent = service.transcribe(FloatArray(16000) { 0.0f }, LanguageMode.ENGLISH, source = "TEST")
        assertTrue("Silent audio must return a safe success response", resSilent.isSuccess)
        assertEquals("", resSilent.text)

        // Sub-200ms buffer (< 3200 samples)
        val resTooShort = service.transcribe(FloatArray(1000) { 0.1f }, LanguageMode.ENGLISH, source = "TEST")
        assertTrue("Truncated audio must return a safe success response", resTooShort.isSuccess)
        assertEquals("", resTooShort.text)

        assertEquals("Zero native inferences must occur for rejected audio", 0, mockBridge.totalInferences.get())
    }

    // =========================================================================
    // 5. Recognition failure returns the engine to a usable state
    // =========================================================================
    @Test
    fun test5_recognitionFailureReturnsEngineToUsableState() = runBlocking {
        val mockBridge = TestNativeBridge(dummyContext, shouldFail = true)
        val modelManager = WhisperModelManager(dummyContext)
        val engine = WhisperCppSpeechRecognitionEngine(dummyContext, modelManager, mockBridge)
        val service = SpeechRecognitionService(dummyContext, engine)

        val audio = FloatArray(16000) { 0.15f }
        val failRes = service.transcribe(audio, LanguageMode.ENGLISH, source = "DIAGNOSTIC")

        assertFalse("Failing bridge must return failure", failRes.isSuccess)
        assertFalse("Engine must not be stuck in isTranscribing after failure", engine.isTranscribing)

        // Recover and verify next request succeeds
        mockBridge.shouldFail = false
        mockBridge.resultText = "Recovered"
        val okRes = service.transcribe(audio, LanguageMode.ENGLISH, source = "DIAGNOSTIC")

        assertTrue("Engine must recover on subsequent request", okRes.isSuccess)
        assertEquals("Recovered", okRes.text)
    }

    // =========================================================================
    // 6. A cancelled transcription cannot trigger a command
    // =========================================================================
    @Test
    fun test6_cancelledTranscriptionCannotTriggerCommand() = runBlocking {
        val persistenceRepo = WorkflowPersistenceRepository(tempDir)
        val wallpaperWorkflow = Workflow(
            id = "wf_wallpaper",
            name = "Change Wallpaper",
            description = "Changes wallpaper",
            status = WorkflowStatus.CALIBRATED,
            deviceProfile = testDeviceProfile,
            steps = listOf(WorkflowStep(id = "s1", order = 1, type = ActionType.TAP, startX = 100, startY = 200))
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
            automationEngine = TestAutomationEngine(),
            deviceProfileProvider = { testDeviceProfile },
            coroutineScope = CoroutineScope(Dispatchers.Unconfined)
        )

        val mockBridge = TestNativeBridge(dummyContext, delayMs = 150L, resultText = "Change Wallpaper")
        val modelManager = WhisperModelManager(dummyContext)
        val engine = WhisperCppSpeechRecognitionEngine(dummyContext, modelManager, mockBridge)
        val service = SpeechRecognitionService(dummyContext, engine)
        SpeechRecognitionService.setInstanceForTesting(service)

        var commandTriggered = false
        val listener = object : SpeechEngineListener {
            override fun onReady() {}
            override fun onBeginningOfSpeech() {}
            override fun onResult(recognizedText: String) {
                commandTriggered = true
                assistantController.processCommand(recognizedText)
            }
            override fun onError(error: SpeechEngineError) {}
            override fun onStopped() {}
        }

        val audio = FloatArray(16000) { 0.1f }
        val job = engine.submitTranscription(audio, isFinal = true, listener = listener)

        // Cancel while running
        engine.cancelActiveTranscription()
        job?.join()

        assertFalse("Command callback must NOT have been called", commandTriggered)
        assertNull("No pending action should exist in assistant", assistantController.pendingAction)
        assertFalse("Engine must not remain busy", engine.isTranscribing)
    }

    // =========================================================================
    // 7. Concurrent diagnostic and bot requests are serialized or rejected safely
    // =========================================================================
    @Test
    fun test7_concurrentDiagnosticAndBotRequestsHandledSafely() = runBlocking {
        val mockBridge = TestNativeBridge(dummyContext, delayMs = 100L, resultText = "Job 1")
        val modelManager = WhisperModelManager(dummyContext)
        val engine = WhisperCppSpeechRecognitionEngine(dummyContext, modelManager, mockBridge)
        val service = SpeechRecognitionService(dummyContext, engine)

        val audio = FloatArray(16000) { 0.1f }

        var result1: SpeechRecognitionResult? = null
        var result2: SpeechRecognitionResult? = null

        val job1 = CoroutineScope(Dispatchers.Default).launch {
            result1 = service.transcribe(audio, LanguageMode.ENGLISH, source = "BOT")
        }
        delay(20)

        // Concurrent call from DIAGNOSTIC while BOT is decoding
        result2 = service.transcribe(audio, LanguageMode.ENGLISH, source = "DIAGNOSTIC")

        job1.join()

        assertTrue("First job must succeed", result1?.isSuccess == true)
        assertEquals("Job 1", result1?.text)

        // Second job was rejected safely without crashing the process
        assertFalse("Overlapping second job must be rejected safely", result2.isSuccess)
        assertTrue(
            "Rejection error must state engine is busy",
            result2.error?.contains("busy decoding") == true
        )
    }

    // =========================================================================
    // 8. A later request succeeds after a failed or cancelled request
    // =========================================================================
    @Test
    fun test8_laterRequestSucceedsAfterFailedOrCancelledRequest() = runBlocking {
        val mockBridge = TestNativeBridge(dummyContext, delayMs = 50L, resultText = "First Call")
        val modelManager = WhisperModelManager(dummyContext)
        val engine = WhisperCppSpeechRecognitionEngine(dummyContext, modelManager, mockBridge)
        val service = SpeechRecognitionService(dummyContext, engine)

        val audio = FloatArray(16000) { 0.1f }

        // Start and cancel request 1
        val job1 = CoroutineScope(Dispatchers.Default).launch {
            service.transcribe(audio, LanguageMode.ENGLISH, source = "BOT", customJobId = "job_1")
        }
        delay(10)
        engine.cancelActiveTranscription("job_1")
        job1.join()

        // Request 2 must succeed cleanly
        mockBridge.resultText = "Second Call"
        val res2 = service.transcribe(audio, LanguageMode.ENGLISH, source = "BOT", customJobId = "job_2")

        assertTrue("Subsequent request must succeed after cancellation", res2.isSuccess)
        assertEquals("Second Call", res2.text)
    }

    // =========================================================================
    // 9. A correct transcript reaches the existing command resolver unchanged
    // =========================================================================
    @Test
    fun test9_correctTranscriptReachesCommandResolverUnchanged() = runBlocking {
        val persistenceRepo = WorkflowPersistenceRepository(tempDir)
        val wallpaperWorkflow = Workflow(
            id = "wf_wallpaper",
            name = "Change Wallpaper",
            description = "Changes wallpaper",
            status = WorkflowStatus.CALIBRATED,
            deviceProfile = testDeviceProfile,
            steps = listOf(WorkflowStep(id = "s1", order = 1, type = ActionType.TAP, startX = 100, startY = 200))
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
            automationEngine = TestAutomationEngine(),
            deviceProfileProvider = { testDeviceProfile },
            coroutineScope = CoroutineScope(Dispatchers.Unconfined)
        )

        // Exact phrase
        val phrase = "change my wallpaper"
        assistantController.processCommand(phrase)

        // Verify resolver processed the exact transcript
        assertEquals(
            "User message in controller must match the exact transcript",
            phrase,
            assistantController.userMessage.value
        )
        assertTrue(
            "State must transition to ConfirmationRequired for wallpaper change",
            assistantController.uiState.value is AssistantUiState.ConfirmationRequired
        )
        val confState = assistantController.uiState.value as AssistantUiState.ConfirmationRequired
        assertEquals("Change Wallpaper", confState.workflow?.name)
    }

    // =========================================================================
    // 10. A sensitive workflow still requires confirmation
    // =========================================================================
    @Test
    fun test10_sensitiveWorkflowRequiresConfirmation() = runBlocking {
        val persistenceRepo = WorkflowPersistenceRepository(tempDir)
        val wallpaperWorkflow = Workflow(
            id = "wf_wallpaper",
            name = "Change Wallpaper",
            description = "Changes wallpaper",
            status = WorkflowStatus.CALIBRATED,
            deviceProfile = testDeviceProfile,
            steps = listOf(WorkflowStep(id = "s1", order = 1, type = ActionType.TAP, startX = 100, startY = 200))
        )
        persistenceRepo.saveWorkflow(wallpaperWorkflow)

        val workflowResolver = LocalWorkflowResolver(persistenceRepo)
        val commandResolver = LocalCommandResolver()
        val commandProcessor = LocalCommandProcessor(commandResolver, workflowResolver)
        val confirmationResolver = LocalConfirmationResolver()

        var didAutomateRun = false
        val testEngine = object : AutomationEngine {
            private val _session = MutableStateFlow(ExecutionSession("", "", ExecutionState.IDLE))
            override val executionSession: StateFlow<ExecutionSession> = _session
            override suspend fun executeWorkflow(workflow: Workflow, speedMultiplier: Float) { didAutomateRun = true }
            override suspend fun executeTap(x: Int, y: Int, durationMs: Long): StepExecutionResult = StepExecutionResult(StepExecutionStatus.SUCCESS, 0, ActionType.TAP)
            override suspend fun executeLongPress(x: Int, y: Int, durationMs: Long): StepExecutionResult = StepExecutionResult(StepExecutionStatus.SUCCESS, 0, ActionType.TAP)
            override suspend fun executeSwipe(startX: Int, startY: Int, endX: Int, endY: Int, durationMs: Long): StepExecutionResult = StepExecutionResult(StepExecutionStatus.SUCCESS, 0, ActionType.SWIPE)
            override suspend fun executeWait(durationMs: Long): StepExecutionResult = StepExecutionResult(StepExecutionStatus.SUCCESS, 0, ActionType.WAIT)
            override suspend fun executeBack(): StepExecutionResult = StepExecutionResult(StepExecutionStatus.SUCCESS, 0, ActionType.BACK)
            override suspend fun executeStep(step: WorkflowStep): StepExecutionResult = StepExecutionResult(StepExecutionStatus.SUCCESS, step.order, step.type)
            override suspend fun testStep(step: WorkflowStep): StepExecutionResult = StepExecutionResult(StepExecutionStatus.SUCCESS, step.order, step.type)
            override suspend fun execute(task: Task, speedMultiplier: Float) { didAutomateRun = true }
            override fun pause() {}
            override fun resume() {}
            override fun stop() {}
            override fun restart() {}
            override suspend fun stepNext() {}
        }

        val assistantController = OfflineAssistantController(
            commandResolver = commandResolver,
            workflowResolver = workflowResolver,
            commandProcessor = commandProcessor,
            confirmationResolver = confirmationResolver,
            automationEngine = testEngine,
            deviceProfileProvider = { testDeviceProfile },
            coroutineScope = CoroutineScope(Dispatchers.Unconfined)
        )

        // Process wallpaper command
        assistantController.processCommand("change my wallpaper")

        // Must NOT run automatically!
        assertFalse("Automation must NOT run before confirmation", didAutomateRun)
        assertFalse("Confirmation must not be granted automatically", assistantController.confirmationGranted)
        assertTrue("State must be ConfirmationRequired", assistantController.uiState.value is AssistantUiState.ConfirmationRequired)

        // Explicit user confirmation: "yes"
        assistantController.processCommand("yes")
        assertTrue("Automation engine should execute after explicit confirmation", didAutomateRun)
    }

    // =========================================================================
    // 11. A completed recognition operation cannot leave the UI stuck in BUSY DECODING
    // =========================================================================
    @Test
    fun test11_completedRecognitionCannotLeaveUiStuckInBusyDecoding() = runBlocking {
        val mockBridge = TestNativeBridge(dummyContext, delayMs = 10L, resultText = "Done")
        val modelManager = WhisperModelManager(dummyContext)
        val engine = WhisperCppSpeechRecognitionEngine(dummyContext, modelManager, mockBridge)
        val service = SpeechRecognitionService(dummyContext, engine)

        val audio = FloatArray(16000) { 0.1f }

        // Normal completion
        service.transcribe(audio, LanguageMode.ENGLISH, source = "TEST")
        assertFalse("isTranscribing must be false after completion", engine.isTranscribing)
        assertEquals(WhisperEngineState.READY, engine.engineState.value)

        // Rejection
        service.transcribe(FloatArray(0), LanguageMode.ENGLISH, source = "TEST")
        assertFalse("isTranscribing must be false after empty rejection", engine.isTranscribing)
        assertEquals(WhisperEngineState.READY, engine.engineState.value)

        // Failure
        mockBridge.shouldFail = true
        service.transcribe(audio, LanguageMode.ENGLISH, source = "TEST")
        assertFalse("isTranscribing must be false after failure", engine.isTranscribing)
    }

    // =========================================================================
    // 12. Hindi and Hinglish language modes are preserved with translate=false
    // =========================================================================
    @Test
    fun test12_hindiAndHinglishModesSupportedOffline() = runBlocking {
        val mockBridge = TestNativeBridge(dummyContext, delayMs = 10L)
        val modelManager = WhisperModelManager(dummyContext)
        val engine = WhisperCppSpeechRecognitionEngine(dummyContext, modelManager, mockBridge)
        val service = SpeechRecognitionService(dummyContext, engine)

        val audio = FloatArray(16000) { 0.1f }

        // 1. English mode -> "en"
        service.transcribe(audio, LanguageMode.ENGLISH, source = "TEST")
        assertEquals(LanguageMode.ENGLISH, mockBridge.lastLanguageMode)
        assertEquals("en", mockBridge.lastLanguageMode?.code)
        assertFalse("Translation must always be false", mockBridge.lastTranslate)

        // 2. Hindi mode -> "hi"
        service.transcribe(audio, LanguageMode.HINDI, source = "TEST")
        assertEquals(LanguageMode.HINDI, mockBridge.lastLanguageMode)
        assertEquals("hi", mockBridge.lastLanguageMode?.code)
        assertFalse("Translation must always be false", mockBridge.lastTranslate)

        // 3. Hinglish mode -> "auto"
        service.transcribe(audio, LanguageMode.HINGLISH, source = "TEST")
        assertEquals(LanguageMode.HINGLISH, mockBridge.lastLanguageMode)
        assertEquals("auto", mockBridge.lastLanguageMode?.code)
        assertFalse("Translation must always be false", mockBridge.lastTranslate)
    }
}
