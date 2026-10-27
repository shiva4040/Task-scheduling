package com.taskscheduling.app

import com.taskscheduling.app.assistant.AssistantUiState
import com.taskscheduling.app.assistant.CommandIntent
import com.taskscheduling.app.assistant.CommandProcessor
import com.taskscheduling.app.assistant.CommandResolutionResult
import com.taskscheduling.app.assistant.ConfirmationIntent
import com.taskscheduling.app.assistant.ExecutionPolicy
import com.taskscheduling.app.assistant.DefaultExecutionPolicyResolver
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
import com.taskscheduling.app.assistant.StopListeningResolver
import com.taskscheduling.app.voice.ContinuousVoiceController
import com.taskscheduling.app.voice.EnglishSpeechValidator
import com.taskscheduling.app.voice.OfflineSpeechEngine
import com.taskscheduling.app.voice.SpeechEngineError
import com.taskscheduling.app.voice.SpeechEngineListener
import com.taskscheduling.app.voice.SpeechOutputManager
import com.taskscheduling.app.voice.VoiceInputManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

class OfflineAssistantTest {

    private lateinit var tempDir: File
    private lateinit var persistenceRepo: WorkflowPersistenceRepository
    private lateinit var commandResolver: LocalCommandResolver
    private lateinit var workflowResolver: LocalWorkflowResolver
    private lateinit var commandProcessor: LocalCommandProcessor
    private lateinit var confirmationResolver: LocalConfirmationResolver
    private val testDeviceProfile = DeviceProfile(
        manufacturer = "Google",
        model = "Pixel Tablet",
        androidVersion = "14",
        apiLevel = 34,
        screenWidth = 1600,
        screenHeight = 2560,
        density = 2.0f,
        orientation = "PORTRAIT"
    )

    // Test fake automation engine to track executions and simulate step failures
    private class FakeAutomationEngine : AutomationEngine {
        var executeWorkflowCallCount = 0
        var lastExecutedWorkflow: Workflow? = null
        var lastExecutedStepSequence = mutableListOf<WorkflowStep>()
        var shouldFailAtStep: Int? = null

        private val _session = MutableStateFlow(
            ExecutionSession(
                taskId = "",
                taskTitle = "",
                state = ExecutionState.IDLE
            )
        )
        override val executionSession: StateFlow<ExecutionSession> = _session

        override suspend fun executeWorkflow(workflow: Workflow, speedMultiplier: Float) {
            executeWorkflowCallCount++
            lastExecutedWorkflow = workflow
            lastExecutedStepSequence.clear()
            _session.value = ExecutionSession(
                taskId = workflow.id,
                taskTitle = workflow.name,
                state = ExecutionState.RUNNING,
                totalSteps = workflow.steps.size
            )

            for ((index, step) in workflow.steps.withIndex()) {
                val stepNum = index + 1
                if (shouldFailAtStep == stepNum) {
                    _session.value = ExecutionSession(
                        taskId = workflow.id,
                        taskTitle = workflow.name,
                        state = ExecutionState.FAILED,
                        totalSteps = workflow.steps.size,
                        currentStepIndex = index,
                        errorMessage = "Simulated failure at step $stepNum"
                    )
                    return
                }
                lastExecutedStepSequence.add(step)
            }

            _session.value = ExecutionSession(
                taskId = workflow.id,
                taskTitle = workflow.name,
                state = ExecutionState.COMPLETED,
                totalSteps = workflow.steps.size,
                currentStepIndex = workflow.steps.size
            )
        }

        override suspend fun executeTap(x: Int, y: Int, durationMs: Long): StepExecutionResult =
            StepExecutionResult(StepExecutionStatus.SUCCESS, 0, ActionType.TAP)

        override suspend fun executeLongPress(x: Int, y: Int, durationMs: Long): StepExecutionResult =
            StepExecutionResult(StepExecutionStatus.SUCCESS, 0, ActionType.LONG_PRESS)

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

        override suspend fun execute(task: Task, speedMultiplier: Float) {
            executeWorkflow(task.workflow, speedMultiplier)
        }

        override fun pause() {}
        override fun resume() {}
        override fun stop() {
            _session.value = _session.value.copy(state = ExecutionState.STOPPED)
        }
        override fun restart() {}
        override suspend fun stepNext() {}
    }

    // Fake offline speech engine for deterministic voice testing
    private class FakeOfflineSpeechEngine(
        var shouldFailInit: Boolean = false,
        var initError: SpeechEngineError? = null
    ) : OfflineSpeechEngine {
        override var isModelLoaded: Boolean = false
            private set
        var isListening: Boolean = false
            private set

        var lastListener: SpeechEngineListener? = null
        var releaseCalled = false

        override suspend fun initialize(): Result<Unit> {
            if (shouldFailInit) {
                return Result.failure(IllegalStateException(initError?.toString() ?: "Init failed"))
            }
            isModelLoaded = true
            return Result.success(Unit)
        }

        var isContinuous: Boolean = false
        var isPaused: Boolean = false
        var pauseCount: Int = 0
        var resumeCount: Int = 0

        override fun startListening(listener: SpeechEngineListener, continuous: Boolean): Boolean {
            lastListener = listener
            isContinuous = continuous
            if (!isModelLoaded) {
                listener.onError(SpeechEngineError.InitializationFailed("Engine not initialized"))
                return false
            }
            isListening = true
            listener.onReady()
            return true
        }

        override fun pauseListening() {
            isPaused = true
            pauseCount++
        }

        override fun resumeListening() {
            isPaused = false
            resumeCount++
        }

        override fun stopListening() {
            isListening = false
            lastListener?.onStopped()
        }

        override fun cancelListening() {
            isListening = false
            lastListener?.onStopped()
        }

        override fun release() {
            isListening = false
            isModelLoaded = false
            releaseCalled = true
        }

        fun simulateRecognitionResult(text: String) {
            lastListener?.onResult(text)
            if (!isContinuous) {
                isListening = false
                lastListener?.onStopped()
            }
        }

        fun simulateError(error: SpeechEngineError) {
            lastListener?.onError(error)
            if (!isContinuous) {
                isListening = false
                lastListener?.onStopped()
            }
        }
    }

    private class FakeSpeechOutputManager : SpeechOutputManager {
        private val _isSpeaking = MutableStateFlow(false)
        override val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()
        var speakCount: Int = 0
        var lastSpokenText: String? = null
        var manualFinish: Boolean = false
        var lastOnFinished: (() -> Unit)? = null

        override fun speak(text: String, onSpeechStarted: () -> Unit, onSpeechFinished: () -> Unit) {
            speakCount++
            lastSpokenText = text
            _isSpeaking.value = true
            onSpeechStarted()
            if (manualFinish) {
                lastOnFinished = onSpeechFinished
            } else {
                _isSpeaking.value = false
                onSpeechFinished()
            }
        }

        fun completeSpeech() {
            _isSpeaking.value = false
            lastOnFinished?.invoke()
            lastOnFinished = null
        }

        override fun stop() {
            _isSpeaking.value = false
        }

        override fun release() {
            _isSpeaking.value = false
        }
    }

    private class FakeAssistantActionExecutor : com.taskscheduling.app.assistant.AssistantActionExecutor {
        val executedActions = mutableListOf<CommandIntent>()
        var lastDeviceProfile: DeviceProfile? = null
        var shouldFail: Boolean = false
        var failureMessage: String = "Action failed."

        override suspend fun executeAction(intent: CommandIntent, currentProfile: DeviceProfile): com.taskscheduling.app.assistant.ActionResult {
            if (shouldFail) {
                return com.taskscheduling.app.assistant.ActionResult.Failure(failureMessage)
            }
            executedActions.add(intent)
            lastDeviceProfile = currentProfile
            return com.taskscheduling.app.assistant.ActionResult.Success("Done.")
        }
    }

    private lateinit var fakeEngine: FakeAutomationEngine
    private lateinit var fakeActionExecutor: FakeAssistantActionExecutor
    private lateinit var controller: OfflineAssistantController
    private lateinit var fakeSpeechEngine: FakeOfflineSpeechEngine
    private lateinit var fakeSpeechOutputManager: FakeSpeechOutputManager
    private lateinit var voiceInputManager: VoiceInputManager
    private lateinit var continuousVoiceController: ContinuousVoiceController

    @Before
    fun setup() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "offline_assistant_test_${UUID.randomUUID()}")
        tempDir.mkdirs()
        persistenceRepo = WorkflowPersistenceRepository(tempDir)
        commandResolver = LocalCommandResolver()
        workflowResolver = LocalWorkflowResolver(persistenceRepo)
        commandProcessor = LocalCommandProcessor(commandResolver, workflowResolver)
        confirmationResolver = LocalConfirmationResolver()
        fakeEngine = FakeAutomationEngine()
        fakeActionExecutor = FakeAssistantActionExecutor()
        fakeSpeechEngine = FakeOfflineSpeechEngine()
        controller = OfflineAssistantController(
            commandResolver = commandResolver,
            workflowResolver = workflowResolver,
            commandProcessor = commandProcessor,
            confirmationResolver = confirmationResolver,
            automationEngine = fakeEngine,
            actionExecutor = fakeActionExecutor,
            persistenceRepository = persistenceRepo,
            deviceProfileProvider = { testDeviceProfile },
            coroutineScope = CoroutineScope(Dispatchers.Unconfined)
        ).apply {
            autoResumeDelayMs = 0L
        }
        fakeSpeechOutputManager = FakeSpeechOutputManager()
        voiceInputManager = VoiceInputManager(
            context = null,
            speechEngine = fakeSpeechEngine,
            scope = CoroutineScope(Dispatchers.Unconfined),
            permissionChecker = { true }
        )
        continuousVoiceController = ContinuousVoiceController(
            voiceInputManager = voiceInputManager,
            assistantController = controller,
            speechOutputManager = fakeSpeechOutputManager,
            coroutineScope = CoroutineScope(Dispatchers.Unconfined)
        )
    }

    private fun startTestContinuousListening() = runBlocking {
        fakeSpeechEngine.initialize()
        continuousVoiceController.startContinuousListening()
    }

    @After
    fun tearDown() {
        continuousVoiceController.release()
        tempDir.deleteRecursively()
    }

    private fun createSampleChangeWallpaperWorkflow(): Workflow {
        val steps = listOf(
            WorkflowStep(id = "s1", order = 1, type = ActionType.TAP, startX = 299, startY = 937),
            WorkflowStep(id = "s2", order = 2, type = ActionType.TAP, startX = 350, startY = 400),
            WorkflowStep(id = "s3", order = 3, type = ActionType.SWIPE, startX = 500, startY = 1200, endX = 500, endY = 400),
            WorkflowStep(id = "s4", order = 4, type = ActionType.WAIT, duration = 1000L),
            WorkflowStep(id = "s5", order = 5, type = ActionType.TAP, startX = 600, startY = 800),
            WorkflowStep(id = "s6", order = 6, type = ActionType.BACK)
        )
        return Workflow(
            id = "wf_wallpaper",
            name = "Change Wallpaper",
            description = "Recorded wallpaper workflow",
            status = WorkflowStatus.CALIBRATED,
            deviceProfile = testDeviceProfile,
            steps = steps
        )
    }

    // ------------------------------------------------------------------------
    // SECTION 18 MANDATORY CONFIRMATION FLOW TESTS
    // ------------------------------------------------------------------------

    // Test 1: "change my wallpaper" -> resolves CHANGE_WALLPAPER -> finds workflow -> DOES NOT execute automation
    @Test
    fun test1_CommandResolvesAndFindsWorkflowWithoutExecutingAutomation() = runBlocking {
        val sampleWorkflow = createSampleChangeWallpaperWorkflow()
        persistenceRepo.saveWorkflow(sampleWorkflow)

        controller.processCommand("change my wallpaper")

        // Confirmation required state is displayed
        val state = controller.uiState.value
        assertTrue("State must be ConfirmationRequired", state is AssistantUiState.ConfirmationRequired)
        val conf = state as AssistantUiState.ConfirmationRequired
        assertEquals("Change Wallpaper", conf.workflow?.name)
        assertEquals(6, conf.stepCount)
        assertNotNull("pendingWorkflow must be populated", controller.pendingWorkflow)
        assertFalse("confirmationGranted must be false", controller.confirmationGranted)

        // Safety verification: ZERO calls to AutomationEngine
        assertEquals(0, fakeEngine.executeWorkflowCallCount)
        assertNull(fakeEngine.lastExecutedWorkflow)
    }

    // Test 2: "change my wallpaper" -> confirmation shown -> user says "yes" -> AutomationEngine executes exactly once
    @Test
    fun test2_UserSaysYesExecutesAutomationExactlyOnce() = runBlocking {
        val sampleWorkflow = createSampleChangeWallpaperWorkflow()
        persistenceRepo.saveWorkflow(sampleWorkflow)

        controller.processCommand("change my wallpaper")
        assertEquals(0, fakeEngine.executeWorkflowCallCount)

        // User speaks affirmative voice confirmation
        controller.processCommand("yes")

        // AutomationEngine executed exactly once
        assertEquals(1, fakeEngine.executeWorkflowCallCount)
        assertEquals("Change Wallpaper", fakeEngine.lastExecutedWorkflow?.name)
        assertTrue(controller.uiState.value is AssistantUiState.Completed)
        assertNull("pendingWorkflow must be cleared after execution", controller.pendingWorkflow)
    }

    // Test 3: "change my wallpaper" -> user says "no" -> AutomationEngine is never called
    @Test
    fun test3_UserSaysNoNeverCallsAutomation() = runBlocking {
        val sampleWorkflow = createSampleChangeWallpaperWorkflow()
        persistenceRepo.saveWorkflow(sampleWorkflow)

        controller.processCommand("change my wallpaper")
        assertTrue(controller.uiState.value is AssistantUiState.ConfirmationRequired)

        // User cancels via voice ("no")
        controller.processCommand("no")

        // Returned to Idle, pendingWorkflow cleared, ZERO automation executions
        assertTrue(controller.uiState.value is AssistantUiState.Idle)
        assertNull(controller.pendingWorkflow)
        assertEquals(0, fakeEngine.executeWorkflowCallCount)
        assertNull(fakeEngine.lastExecutedWorkflow)

        // Also test "cancel" keyword
        controller.processCommand("change my wallpaper")
        controller.processCommand("cancel")
        assertTrue(controller.uiState.value is AssistantUiState.Idle)
        assertEquals(0, fakeEngine.executeWorkflowCallCount)
    }

    // Test 4: Original command itself must never count as confirmation
    @Test
    fun test4_OriginalCommandNeverCountsAsConfirmation() = runBlocking {
        val sampleWorkflow = createSampleChangeWallpaperWorkflow()
        persistenceRepo.saveWorkflow(sampleWorkflow)

        controller.processCommand("change my wallpaper")
        assertTrue(controller.uiState.value is AssistantUiState.ConfirmationRequired)

        // User repeats the original command instead of saying yes/no
        controller.processCommand("change my wallpaper")

        // Must NOT execute! Re-enters ConfirmationRequired for the workflow
        assertTrue(controller.uiState.value is AssistantUiState.ConfirmationRequired)
        assertEquals(0, fakeEngine.executeWorkflowCallCount)
        assertNull(fakeEngine.lastExecutedWorkflow)
    }

    // Test 5: Opening the app must never execute a workflow
    @Test
    fun test5_OpeningAppNeverExecutesWorkflow() {
        val freshController = OfflineAssistantController(
            commandResolver = commandResolver,
            workflowResolver = workflowResolver,
            commandProcessor = commandProcessor,
            confirmationResolver = confirmationResolver,
            automationEngine = fakeEngine,
            persistenceRepository = persistenceRepo,
            deviceProfileProvider = { testDeviceProfile }
        )
        assertTrue(freshController.uiState.value is AssistantUiState.Idle)
        assertNull(freshController.pendingWorkflow)
        assertEquals(0, fakeEngine.executeWorkflowCallCount)
    }

    // Test 6: Starting AccessibilityService must never execute a workflow
    @Test
    fun test6_StartingAccessibilityServiceNeverExecutesWorkflow() {
        controller.setListeningState()
        controller.stopListeningState()
        controller.reset()
        assertTrue(controller.uiState.value is AssistantUiState.Idle)
        assertEquals(0, fakeEngine.executeWorkflowCallCount)
    }

    // Test 7: Restoring a saved workflow must never execute a workflow
    @Test
    fun test7_RestoringSavedWorkflowNeverExecutesWorkflow() = runBlocking {
        val sampleWorkflow = createSampleChangeWallpaperWorkflow()
        persistenceRepo.saveWorkflow(sampleWorkflow)

        // Load all saved workflows
        val loaded = persistenceRepo.getAllWorkflows()
        assertEquals(1, loaded.size)
        assertEquals(sampleWorkflow.id, loaded[0].id)

        // Absolutely zero execution
        assertEquals(0, fakeEngine.executeWorkflowCallCount)
    }

    // Test 8: Recording a workflow must never execute a workflow
    @Test
    fun test8_RecordingWorkflowNeverExecutesWorkflow() = runBlocking {
        // Create recorded steps and save workflow into persistence repository
        val recordedSteps = listOf(
            WorkflowStep(id = "rec_1", order = 1, type = ActionType.TAP, startX = 100, startY = 200),
            WorkflowStep(id = "rec_2", order = 2, type = ActionType.SWIPE, startX = 100, startY = 200, endX = 100, endY = 800, duration = 500L)
        )

        val newWf = Workflow(
            id = "recorded_wf",
            name = "Recorded Workflow",
            description = "Test recording",
            status = WorkflowStatus.CALIBRATED,
            deviceProfile = testDeviceProfile,
            steps = recordedSteps
        )
        persistenceRepo.saveWorkflow(newWf)

        // Zero execution
        assertEquals(0, fakeEngine.executeWorkflowCallCount)
        assertNull(fakeEngine.lastExecutedWorkflow)
    }

    // Test 9: Repeated "yes" must not start the same workflow multiple times
    @Test
    fun test9_RepeatedYesDoesNotStartWorkflowMultipleTimes() = runBlocking {
        val sampleWorkflow = createSampleChangeWallpaperWorkflow()
        persistenceRepo.saveWorkflow(sampleWorkflow)

        controller.processCommand("change my wallpaper")
        controller.processCommand("yes")
        assertEquals(1, fakeEngine.executeWorkflowCallCount)

        // Repeated "yes" when not waiting for confirmation
        controller.processCommand("yes")
        controller.processCommand("yes")
        controller.confirmRun()

        // Count remains exactly 1!
        assertEquals(1, fakeEngine.executeWorkflowCallCount)
    }

    // Test 10: If execution fails at step N, stop execution and report the failed step
    @Test
    fun test10_ExecutionFailureAtStepNStopsAndReportsFailedStep() = runBlocking {
        val sampleWorkflow = createSampleChangeWallpaperWorkflow()
        persistenceRepo.saveWorkflow(sampleWorkflow)

        // Configure engine to simulate failure at step 3
        fakeEngine.shouldFailAtStep = 3

        controller.processCommand("change my wallpaper")
        controller.confirmRun()

        assertEquals(1, fakeEngine.executeWorkflowCallCount)
        assertTrue(controller.uiState.value is AssistantUiState.Error)
        val err = controller.uiState.value as AssistantUiState.Error
        assertEquals("Workflow stopped at step 3.", err.message)
        assertEquals(2, fakeEngine.lastExecutedStepSequence.size) // executed steps 1 and 2 only
    }

    // ------------------------------------------------------------------------
    // CONFIRMATION RESOLVER UNIT TESTS
    // ------------------------------------------------------------------------

    @Test
    fun testLocalConfirmationResolverIntents() {
        val affirmative = listOf("yes", "yeah", "yep", "run it", "start", "confirm", "yes run", "run", "do it", "sure")
        for (phrase in affirmative) {
            assertEquals("Phrase '$phrase' must be YES", ConfirmationIntent.YES, confirmationResolver.resolve(phrase))
        }

        val negative = listOf("no", "cancel", "don't run", "dont run", "stop", "never mind", "dismiss")
        for (phrase in negative) {
            assertEquals("Phrase '$phrase' must be NO", ConfirmationIntent.NO, confirmationResolver.resolve(phrase))
        }

        val unknown = listOf("change my wallpaper", "pizza", "what", "maybe", "", "123")
        for (phrase in unknown) {
            assertEquals("Phrase '$phrase' must be UNKNOWN", ConfirmationIntent.UNKNOWN, confirmationResolver.resolve(phrase))
        }
    }

    // ------------------------------------------------------------------------
    // HARD EXECUTION BOUNDARY DIRECT CALL GUARD TEST
    // ------------------------------------------------------------------------

    @Test
    fun testDirectCallToExecuteConfirmedWorkflowWithoutConfirmationIsRejected() = runBlocking {
        val sampleWorkflow = createSampleChangeWallpaperWorkflow()

        // Call executeConfirmedWorkflow directly without setting confirmation
        controller.executeConfirmedWorkflow(sampleWorkflow)

        // Must reject execution
        assertEquals(0, fakeEngine.executeWorkflowCallCount)
        assertTrue(controller.uiState.value is AssistantUiState.Error)
    }

    // ------------------------------------------------------------------------
    // BUTTON CONFIRMATION TEST
    // ------------------------------------------------------------------------

    @Test
    fun testButtonConfirmationUsesSameExecutionPathway() = runBlocking {
        val sampleWorkflow = createSampleChangeWallpaperWorkflow()
        persistenceRepo.saveWorkflow(sampleWorkflow)

        controller.processCommand("change my wallpaper")
        assertEquals(0, fakeEngine.executeWorkflowCallCount)

        // Click visible RUN button
        controller.confirmRun()

        assertEquals(1, fakeEngine.executeWorkflowCallCount)
        assertEquals("Change Wallpaper", fakeEngine.lastExecutedWorkflow?.name)
    }

    @Test
    fun testButtonCancelUsesSameCancellationPathway() = runBlocking {
        val sampleWorkflow = createSampleChangeWallpaperWorkflow()
        persistenceRepo.saveWorkflow(sampleWorkflow)

        controller.processCommand("change my wallpaper")
        assertEquals(0, fakeEngine.executeWorkflowCallCount)

        // Click visible CANCEL button
        controller.cancelConfirmation()

        assertEquals(0, fakeEngine.executeWorkflowCallCount)
        assertTrue(controller.uiState.value is AssistantUiState.Idle)
    }

    // ------------------------------------------------------------------------
    // SECTION 15 TESTS: SPEECH ENGINE & OFFLINE VOICE COMMANDS
    // ------------------------------------------------------------------------

    @Test
    fun testSpeechEngineInitialization() = runBlocking {
        assertFalse(fakeSpeechEngine.isModelLoaded)
        val result = fakeSpeechEngine.initialize()
        assertTrue(result.isSuccess)
        assertTrue(fakeSpeechEngine.isModelLoaded)

        var readyCalled = false
        val started = fakeSpeechEngine.startListening(object : SpeechEngineListener {
            override fun onReady() { readyCalled = true }
            override fun onBeginningOfSpeech() {}
            override fun onPartialResult(hypothesis: String) {}
            override fun onResult(recognizedText: String) {}
            override fun onError(error: SpeechEngineError) {}
            override fun onStopped() {}
        })
        assertTrue(started)
        assertTrue(fakeSpeechEngine.isListening)
        assertTrue(readyCalled)

        fakeSpeechEngine.stopListening()
        assertFalse(fakeSpeechEngine.isListening)

        fakeSpeechEngine.release()
        assertFalse(fakeSpeechEngine.isModelLoaded)
        assertTrue(fakeSpeechEngine.releaseCalled)
    }

    @Test
    fun testMissingModelHandling() {
        var observedError: SpeechEngineError? = null
        fakeSpeechEngine.startListening(object : SpeechEngineListener {
            override fun onReady() {}
            override fun onBeginningOfSpeech() {}
            override fun onPartialResult(hypothesis: String) {}
            override fun onResult(recognizedText: String) {}
            override fun onError(error: SpeechEngineError) { observedError = error }
            override fun onStopped() {}
        })

        fakeSpeechEngine.simulateError(SpeechEngineError.MissingModel)
        assertEquals(SpeechEngineError.MissingModel, observedError)

        controller.setErrorMessage("Offline voice model is missing.")
        val state = controller.uiState.value
        assertTrue(state is AssistantUiState.Error)
        assertEquals("Offline voice model is missing.", (state as AssistantUiState.Error).message)
        assertEquals(0, fakeEngine.executeWorkflowCallCount)
    }

    @Test
    fun testModelLoadingFailureHandling() {
        val loadError = SpeechEngineError.InitializationFailed("Failed to unpack model from assets")
        controller.setErrorMessage(loadError.toUserMessage())

        val state = controller.uiState.value
        assertTrue(state is AssistantUiState.Error)
        assertEquals("Offline voice recognition could not be started.", (state as AssistantUiState.Error).message)
        assertEquals(0, fakeEngine.executeWorkflowCallCount)
    }

    @Test
    fun testMicrophonePermissionDeniedHandling() {
        val permError = SpeechEngineError.MicrophonePermissionDenied
        assertEquals(
            "Microphone permission is required for voice commands. You can still type commands.",
            permError.toUserMessage()
        )

        controller.setErrorMessage(permError.toUserMessage())
        val state = controller.uiState.value
        assertTrue(state is AssistantUiState.Error)
        assertEquals("Microphone permission is required for voice commands. You can still type commands.", (state as AssistantUiState.Error).message)
        assertEquals(0, fakeEngine.executeWorkflowCallCount)
    }

    @Test
    fun testEmptySpeechHandling() {
        controller.processCommand("")
        val stateEmpty = controller.uiState.value
        assertTrue(stateEmpty is AssistantUiState.Idle)
        assertEquals(0, fakeEngine.executeWorkflowCallCount)

        controller.processCommand("   ")
        val stateBlank = controller.uiState.value
        assertTrue(stateBlank is AssistantUiState.Idle)
        assertEquals(0, fakeEngine.executeWorkflowCallCount)
    }

    @Test
    fun testUnknownCommandResolvesToNoIntent() {
        assertNull(commandResolver.resolve("order a pizza"))
        assertNull(commandResolver.resolve("open browser"))
        assertNull(commandResolver.resolve("call mom"))
        assertNull(commandResolver.resolve("random words xyz"))
        assertNull(commandResolver.resolve(""))

        controller.processCommand("order a pizza")
        val state = controller.uiState.value
        assertTrue(state is AssistantUiState.Error)
        assertEquals("I don't know how to do that yet.", (state as AssistantUiState.Error).message)
        assertEquals(0, fakeEngine.executeWorkflowCallCount)
    }

    @Test
    fun testKnownCommandWithoutWorkflowShowsWorkflowNotFound() {
        controller.processCommand("change my wallpaper")
        val state = controller.uiState.value
        assertTrue(state is AssistantUiState.Error)
        assertEquals("I couldn't find a saved workflow for:\n\"change my wallpaper\"", (state as AssistantUiState.Error).message)
        assertEquals(0, fakeEngine.executeWorkflowCallCount)
    }

    @Test
    fun testFarFieldListeningStateMessage() {
        controller.setListeningState()
        val state = controller.uiState.value
        assertTrue(state is AssistantUiState.Listening)
        assertEquals("Listening…", (state as AssistantUiState.Listening).message)
    }

    @Test
    fun testChangeWallpaperRecognitionAllVariations() {
        val requiredPhrases = listOf(
            "change my wallpaper",
            "change wallpaper",
            "change the wallpaper",
            "set my wallpaper",
            "update my wallpaper",
            "change the tablet wallpaper",
            "change the background",
            "set a new wallpaper"
        )

        for (phrase in requiredPhrases) {
            val intent = commandResolver.resolve(phrase)
            assertEquals("Phrase '$phrase' must map to CHANGE_WALLPAPER", CommandIntent.CHANGE_WALLPAPER, intent)
        }
    }

    @Test
    fun testLocalCommandNormalization() {
        assertEquals("change my wallpaper", LocalCommandResolver.normalize("  Change   my   wallpaper!  "))
        assertEquals("change wallpaper", LocalCommandResolver.normalize("Change Wallpaper..."))
        assertEquals("set my wallpaper", LocalCommandResolver.normalize("  SET   MY   WALLPAPER??? "))
        assertEquals("change the tablet wallpaper", LocalCommandResolver.normalize("Change the tablet wallpaper;"))
        assertEquals("", LocalCommandResolver.normalize("   "))
    }

    @Test
    fun testExistingWorkflowStepSequenceIsPassedInOrder() = runBlocking {
        val sampleWorkflow = createSampleChangeWallpaperWorkflow()
        persistenceRepo.saveWorkflow(sampleWorkflow)

        controller.processCommand("change my wallpaper")
        controller.confirmRun()

        val executedSteps = fakeEngine.lastExecutedStepSequence
        assertEquals(6, executedSteps.size)
        assertEquals(1, executedSteps[0].order)
        assertEquals(ActionType.TAP, executedSteps[0].type)
        assertEquals(2, executedSteps[1].order)
        assertEquals(ActionType.TAP, executedSteps[1].type)
        assertEquals(3, executedSteps[2].order)
        assertEquals(ActionType.SWIPE, executedSteps[2].type)
        assertEquals(4, executedSteps[3].order)
        assertEquals(ActionType.WAIT, executedSteps[3].type)
        assertEquals(5, executedSteps[4].order)
        assertEquals(ActionType.TAP, executedSteps[4].type)
        assertEquals(6, executedSteps[5].order)
        assertEquals(ActionType.BACK, executedSteps[5].type)
    }

    // ========================================================================
    // CONTINUOUS HANDS-FREE VOICE ASSISTANT TESTS (SECTION 16)
    // ========================================================================

    // TEST 1: Start Listening -> Say "Change my wallpaper" -> Confirmation appears, NO execution yet
    @Test
    fun testContinuous_Test1_StartListening_CommandShowsConfirmationWithoutExecution() = runBlocking {
        val sampleWorkflow = createSampleChangeWallpaperWorkflow()
        persistenceRepo.saveWorkflow(sampleWorkflow)

        // 1. Activate continuous listening
        startTestContinuousListening()
        assertTrue("Speech engine must be listening continuously", fakeSpeechEngine.isListening)
        assertTrue("Continuous listening must be active", continuousVoiceController.isContinuousListening.value)
        assertTrue("State must be Listening", controller.uiState.value is AssistantUiState.Listening)

        // 2. Continuous voice input captures "Change my wallpaper"
        fakeSpeechEngine.simulateRecognitionResult("Change my wallpaper")

        // 3. Confirmation card appears, pendingWorkflow is saved, NO execution
        val state = controller.uiState.value
        assertTrue("State must be ConfirmationRequired", state is AssistantUiState.ConfirmationRequired)
        val conf = state as AssistantUiState.ConfirmationRequired
        assertEquals("Change Wallpaper", conf.workflow?.name)
        assertNotNull("pendingWorkflow must be set", controller.pendingWorkflow)
        assertFalse("confirmationGranted must remain false", controller.confirmationGranted)

        // Safety verification: ZERO calls to AutomationEngine
        assertEquals(0, fakeEngine.executeWorkflowCallCount)
        assertNull(fakeEngine.lastExecutedWorkflow)

        // Microphone remains continuously listening for confirmation answer
        assertTrue("Speech engine must still be listening for confirmation", fakeSpeechEngine.isListening)
    }

    // TEST 2: Say "Yes" -> Workflow executes exactly once -> After completion, listening automatically resumes
    @Test
    fun testContinuous_Test2_SayYes_ExecutesWorkflowOnce_ListeningResumesAutomatically() = runBlocking {
        val sampleWorkflow = createSampleChangeWallpaperWorkflow()
        persistenceRepo.saveWorkflow(sampleWorkflow)

        startTestContinuousListening()
        fakeSpeechEngine.simulateRecognitionResult("Change my wallpaper")
        assertEquals(0, fakeEngine.executeWorkflowCallCount)

        // User speaks affirmative voice confirmation
        fakeSpeechEngine.simulateRecognitionResult("yes")

        // Automation executes exactly once
        assertEquals(1, fakeEngine.executeWorkflowCallCount)
        assertEquals("Change Wallpaper", fakeEngine.lastExecutedWorkflow?.name)
        assertNull("pendingWorkflow must be cleared", controller.pendingWorkflow)

        // After completion, automatically returns to LISTENING_ACTIVE
        assertTrue("Continuous listening must still be active", continuousVoiceController.isContinuousListening.value)
        assertTrue("State must return to Listening", controller.uiState.value is AssistantUiState.Listening)
        assertTrue("Microphone must remain listening for next command", fakeSpeechEngine.isListening)
    }

    // TEST 3: While still listening say "Change my wallpaper" -> Confirmation appears again without pressing mic
    @Test
    fun testContinuous_Test3_WhileStillListeningSayCommandAgainShowsConfirmationWithoutMicPress() = runBlocking {
        val sampleWorkflow = createSampleChangeWallpaperWorkflow()
        persistenceRepo.saveWorkflow(sampleWorkflow)

        startTestContinuousListening()

        // Command 1
        fakeSpeechEngine.simulateRecognitionResult("Change my wallpaper")
        fakeSpeechEngine.simulateRecognitionResult("yes")
        assertEquals(1, fakeEngine.executeWorkflowCallCount)

        // Assistant is still listening automatically without pressing any button
        assertTrue("Continuous listening must remain active", continuousVoiceController.isContinuousListening.value)
        assertTrue("Microphone is active", fakeSpeechEngine.isListening)

        // Command 2 spoken in the same continuous session
        fakeSpeechEngine.simulateRecognitionResult("Change my wallpaper")

        // Confirmation appears again, NO duplicate execution
        val state = controller.uiState.value
        assertTrue("State must be ConfirmationRequired again", state is AssistantUiState.ConfirmationRequired)
        assertEquals(1, fakeEngine.executeWorkflowCallCount)
    }

    // TEST 4: Say "No" -> Workflow does not execute, listening continues
    @Test
    fun testContinuous_Test4_SayNo_WorkflowDoesNotExecute_ListeningContinues() = runBlocking {
        val sampleWorkflow = createSampleChangeWallpaperWorkflow()
        persistenceRepo.saveWorkflow(sampleWorkflow)

        startTestContinuousListening()
        fakeSpeechEngine.simulateRecognitionResult("Change my wallpaper")
        assertTrue(controller.uiState.value is AssistantUiState.ConfirmationRequired)

        // User declines via voice
        fakeSpeechEngine.simulateRecognitionResult("no")

        // Workflow does NOT execute
        assertEquals(0, fakeEngine.executeWorkflowCallCount)
        assertNull(controller.pendingWorkflow)

        // Assistant continues listening automatically
        assertTrue("Continuous listening must remain active", continuousVoiceController.isContinuousListening.value)
        assertTrue("State must return to Listening", controller.uiState.value is AssistantUiState.Listening)
        assertTrue("Microphone remains active", fakeSpeechEngine.isListening)
    }

    // TEST 5: Say "Stop listening" -> Microphone stops, Vosk stops, state becomes IDLE, no workflow executes
    @Test
    fun testContinuous_Test5_SayStopListening_MicrophoneStops_StateIdle_NoExecution() = runBlocking {
        val sampleWorkflow = createSampleChangeWallpaperWorkflow()
        persistenceRepo.saveWorkflow(sampleWorkflow)

        startTestContinuousListening()
        assertTrue(fakeSpeechEngine.isListening)
        assertTrue(continuousVoiceController.isContinuousListening.value)

        // User gives global stop listening command
        fakeSpeechEngine.simulateRecognitionResult("stop listening")

        // Microphone capture stops
        assertFalse("Continuous listening must be inactive", continuousVoiceController.isContinuousListening.value)
        assertFalse("Microphone must be OFF", fakeSpeechEngine.isListening)
        assertTrue("Assistant state must be Idle", controller.uiState.value is AssistantUiState.Idle)
        assertEquals(0, fakeEngine.executeWorkflowCallCount)
    }

    // TEST 6: Start listening again -> Say "Change my wallpaper" -> Normal confirmation flow works again
    @Test
    fun testContinuous_Test6_StartListeningAgain_ConfirmationFlowWorks() = runBlocking {
        val sampleWorkflow = createSampleChangeWallpaperWorkflow()
        persistenceRepo.saveWorkflow(sampleWorkflow)

        // Start -> Stop
        startTestContinuousListening()
        fakeSpeechEngine.simulateRecognitionResult("stop listening")
        assertFalse(continuousVoiceController.isContinuousListening.value)

        // Start listening again
        continuousVoiceController.startContinuousListening()
        assertTrue(continuousVoiceController.isContinuousListening.value)
        assertTrue(fakeSpeechEngine.isListening)

        // Normal confirmation flow works cleanly
        fakeSpeechEngine.simulateRecognitionResult("Change my wallpaper")
        assertTrue(controller.uiState.value is AssistantUiState.ConfirmationRequired)
        assertEquals(0, fakeEngine.executeWorkflowCallCount)

        fakeSpeechEngine.simulateRecognitionResult("yes")
        assertEquals(1, fakeEngine.executeWorkflowCallCount)
        assertTrue(controller.uiState.value is AssistantUiState.Listening)
    }

    // TEST 7: Say original command but never confirm -> Workflow NEVER executes
    @Test
    fun testContinuous_Test7_SayOriginalCommandNeverConfirm_WorkflowNeverExecutes() = runBlocking {
        val sampleWorkflow = createSampleChangeWallpaperWorkflow()
        persistenceRepo.saveWorkflow(sampleWorkflow)

        startTestContinuousListening()
        fakeSpeechEngine.simulateRecognitionResult("Change my wallpaper")
        assertTrue(controller.uiState.value is AssistantUiState.ConfirmationRequired)

        // User speaks unrecognized utterances or repeats the command itself
        fakeSpeechEngine.simulateRecognitionResult("Change my wallpaper")
        val stateRepeat = controller.uiState.value
        assertTrue(stateRepeat is AssistantUiState.ConfirmationRequired)
        assertEquals("I didn't understand. Please say yes or no.", (stateRepeat as AssistantUiState.ConfirmationRequired).message)
        assertEquals(0, fakeEngine.executeWorkflowCallCount)

        fakeSpeechEngine.simulateRecognitionResult("what time is it")
        val stateUnknown = controller.uiState.value
        assertTrue(stateUnknown is AssistantUiState.ConfirmationRequired)
        assertEquals("I didn't understand. Please say yes or no.", (stateUnknown as AssistantUiState.ConfirmationRequired).message)

        // Workflow NEVER executed
        assertEquals(0, fakeEngine.executeWorkflowCallCount)
        assertNull(fakeEngine.lastExecutedWorkflow)
    }

    // TEST 8: Say "Stop listening" while confirmation is pending -> Pending workflow cancelled, nothing executes
    @Test
    fun testContinuous_Test8_SayStopListeningWhileConfirmationPending_CancelsPendingWorkflow() = runBlocking {
        val sampleWorkflow = createSampleChangeWallpaperWorkflow()
        persistenceRepo.saveWorkflow(sampleWorkflow)

        startTestContinuousListening()
        fakeSpeechEngine.simulateRecognitionResult("Change my wallpaper")
        assertNotNull(controller.pendingWorkflow)
        assertTrue(controller.uiState.value is AssistantUiState.ConfirmationRequired)

        // User says "Stop listening" while confirmation is pending
        fakeSpeechEngine.simulateRecognitionResult("stop listening")

        // Pending workflow must be cancelled, session stopped, ZERO execution
        assertNull("Pending workflow must be cancelled", controller.pendingWorkflow)
        assertFalse(controller.confirmationGranted)
        assertFalse("Continuous listening must be stopped", continuousVoiceController.isContinuousListening.value)
        assertFalse("Microphone must be OFF", fakeSpeechEngine.isListening)
        assertTrue(controller.uiState.value is AssistantUiState.Idle)
        assertEquals(0, fakeEngine.executeWorkflowCallCount)
    }

    // TEST 9: After workflow completion, verify that listening resumes automatically
    @Test
    fun testContinuous_Test9_WorkflowCompletion_AutomaticallyResumesListening() = runBlocking {
        val sampleWorkflow = createSampleChangeWallpaperWorkflow()
        persistenceRepo.saveWorkflow(sampleWorkflow)

        startTestContinuousListening()
        fakeSpeechEngine.simulateRecognitionResult("Change my wallpaper")
        fakeSpeechEngine.simulateRecognitionResult("run it")

        assertEquals(1, fakeEngine.executeWorkflowCallCount)
        assertTrue("Must automatically return to Listening", controller.uiState.value is AssistantUiState.Listening)
        assertTrue("Speech engine must be listening for subsequent commands", fakeSpeechEngine.isListening)
    }

    // TEST 10: AccessibilityService restart/reconnect must NOT automatically start listening
    @Test
    fun testContinuous_Test10_AccessibilityServiceReconnect_NeverStartsListening() {
        // Simulating fresh assistant state or service reconnection
        val serviceController = OfflineAssistantController(
            commandResolver = commandResolver,
            workflowResolver = workflowResolver,
            commandProcessor = commandProcessor,
            confirmationResolver = confirmationResolver,
            automationEngine = fakeEngine,
            persistenceRepository = persistenceRepo,
            deviceProfileProvider = { testDeviceProfile }
        )
        val freshSpeechEngine = FakeOfflineSpeechEngine()
        val freshVoiceInput = VoiceInputManager(
            context = null,
            speechEngine = freshSpeechEngine,
            permissionChecker = { true }
        )
        val freshVoiceController = ContinuousVoiceController(
            voiceInputManager = freshVoiceInput,
            assistantController = serviceController
        )

        // Verification: Continuous listening MUST NOT be active on startup / reconnection
        assertFalse("Continuous listening must NOT start automatically", freshVoiceController.isContinuousListening.value)
        assertFalse("Microphone must NOT be active", freshSpeechEngine.isListening)
        assertTrue("State must be Idle", serviceController.uiState.value is AssistantUiState.Idle)
        assertEquals(0, fakeEngine.executeWorkflowCallCount)
    }

    @Test
    fun testContinuous_StopListeningVariantsRecognized() {
        val stopVariants = listOf(
            "stop listening",
            "stop listening now",
            "stop",
            "turn off listening",
            "stop voice assistant",
            "deactivate listening",
            "stop listening please",
            "please stop listening"
        )
        for (variant in stopVariants) {
            assertTrue("Variant '$variant' must be recognized as stop listening", StopListeningResolver.isStopListening(variant))
        }
        assertFalse(StopListeningResolver.isStopListening("change my wallpaper"))
        assertFalse(StopListeningResolver.isStopListening("yes"))
        assertFalse(StopListeningResolver.isStopListening("no"))
    }

    @Test
    fun testContinuous_ConfirmationVariantsRecognized() {
        assertEquals(ConfirmationIntent.YES, confirmationResolver.resolve("yup"))
        assertEquals(ConfirmationIntent.YES, confirmationResolver.resolve("yeah"))
        assertEquals(ConfirmationIntent.YES, confirmationResolver.resolve("yep"))
        assertEquals(ConfirmationIntent.YES, confirmationResolver.resolve("run it"))
        assertEquals(ConfirmationIntent.YES, confirmationResolver.resolve("do it"))

        assertEquals(ConfirmationIntent.NO, confirmationResolver.resolve("nope"))
        assertEquals(ConfirmationIntent.NO, confirmationResolver.resolve("don't run it"))
        assertEquals(ConfirmationIntent.NO, confirmationResolver.resolve("dont run it"))
        assertEquals(ConfirmationIntent.NO, confirmationResolver.resolve("cancel"))
    }

    @Test
    fun testContinuous_SpeechOutputManager_PausesListeningWhileSpeaking() {
        startTestContinuousListening()
        assertTrue(fakeSpeechEngine.isListening)

        continuousVoiceController.speakWithMute("Do you want me to run Change Wallpaper?")

        // Spoken via TTS coordinator
        assertEquals(1, fakeSpeechOutputManager.speakCount)
        assertEquals("Do you want me to run Change Wallpaper?", fakeSpeechOutputManager.lastSpokenText)

        // Resumed listening after speech finished
        assertTrue(fakeSpeechEngine.isListening)
    }

    // ========================================================================
    // NEW DEVICE CONTROL & APP LAUNCH COMMANDS TESTS (PARTS 28 - 31)
    // ========================================================================

    @Test
    fun testPart28_NewCommandResolutionAndNaturalLanguageVariations() {
        // GO_BACK
        val backPhrases = listOf("go back", "back", "press back", "go backwards", "return back")
        for (phrase in backPhrases) {
            assertEquals("Phrase '$phrase' must map to GO_BACK", CommandIntent.GO_BACK, commandResolver.resolve(phrase))
        }

        // GO_HOME
        val homePhrases = listOf("go home", "home", "press home", "take me home", "go to home screen", "go to the home screen", "home screen")
        for (phrase in homePhrases) {
            assertEquals("Phrase '$phrase' must map to GO_HOME", CommandIntent.GO_HOME, commandResolver.resolve(phrase))
        }

        // SWIPE_LEFT
        val swipeLeftPhrases = listOf("swipe left", "swipe to the left", "move left", "swipe towards the left")
        for (phrase in swipeLeftPhrases) {
            assertEquals("Phrase '$phrase' must map to SWIPE_LEFT", CommandIntent.SWIPE_LEFT, commandResolver.resolve(phrase))
        }

        // SWIPE_RIGHT
        val swipeRightPhrases = listOf("swipe right", "swipe to the right", "move right", "swipe towards the right")
        for (phrase in swipeRightPhrases) {
            assertEquals("Phrase '$phrase' must map to SWIPE_RIGHT", CommandIntent.SWIPE_RIGHT, commandResolver.resolve(phrase))
        }

        // SWIPE_UP
        val swipeUpPhrases = listOf("swipe up", "swipe upwards", "swipe to the top", "move up")
        for (phrase in swipeUpPhrases) {
            assertEquals("Phrase '$phrase' must map to SWIPE_UP", CommandIntent.SWIPE_UP, commandResolver.resolve(phrase))
        }

        // SWIPE_DOWN
        val swipeDownPhrases = listOf("swipe down", "swipe downwards", "swipe to the bottom", "move down")
        for (phrase in swipeDownPhrases) {
            assertEquals("Phrase '$phrase' must map to SWIPE_DOWN", CommandIntent.SWIPE_DOWN, commandResolver.resolve(phrase))
        }

        // SCROLL_UP
        val scrollUpPhrases = listOf("scroll up", "scroll upwards", "scroll to the top", "scroll toward the top", "scroll towards the top")
        for (phrase in scrollUpPhrases) {
            assertEquals("Phrase '$phrase' must map to SCROLL_UP", CommandIntent.SCROLL_UP, commandResolver.resolve(phrase))
        }

        // SCROLL_DOWN
        val scrollDownPhrases = listOf("scroll down", "scroll downwards", "scroll to the bottom", "scroll toward the bottom", "scroll towards the bottom")
        for (phrase in scrollDownPhrases) {
            assertEquals("Phrase '$phrase' must map to SCROLL_DOWN", CommandIntent.SCROLL_DOWN, commandResolver.resolve(phrase))
        }

        // OPEN_YOUTUBE
        val youtubePhrases = listOf("open youtube", "launch youtube", "start youtube", "go to youtube", "open YouTube app")
        for (phrase in youtubePhrases) {
            assertEquals("Phrase '$phrase' must map to OPEN_YOUTUBE", CommandIntent.OPEN_YOUTUBE, commandResolver.resolve(phrase))
        }

        // OPEN_SETTINGS
        val settingsPhrases = listOf("open settings", "open android settings", "launch settings", "go to settings", "open the settings", "open settings app")
        for (phrase in settingsPhrases) {
            assertEquals("Phrase '$phrase' must map to OPEN_SETTINGS", CommandIntent.OPEN_SETTINGS, commandResolver.resolve(phrase))
        }

        // Unknown unsupported commands must not guess
        assertNull(commandResolver.resolve("Turn on Bluetooth"))
        assertNull(commandResolver.resolve("Open Instagram"))
        assertNull(commandResolver.resolve("order coffee"))
    }

    @Test
    fun testPart29_AllDirectCommandsExecuteImmediatelyWithoutConfirmation() = runBlocking {
        val testCases = listOf(
            "go back" to CommandIntent.GO_BACK,
            "go home" to CommandIntent.GO_HOME,
            "swipe left" to CommandIntent.SWIPE_LEFT,
            "swipe right" to CommandIntent.SWIPE_RIGHT,
            "swipe up" to CommandIntent.SWIPE_UP,
            "swipe down" to CommandIntent.SWIPE_DOWN,
            "scroll up" to CommandIntent.SCROLL_UP,
            "scroll down" to CommandIntent.SCROLL_DOWN,
            "open youtube" to CommandIntent.OPEN_YOUTUBE,
            "open settings" to CommandIntent.OPEN_SETTINGS
        )

        for ((phrase, expectedIntent) in testCases) {
            fakeActionExecutor.executedActions.clear()

            // 1. Give command
            controller.processCommand(phrase)

            // 2. Must NOT be in ConfirmationRequired state; executes immediately
            val state = controller.uiState.value
            assertFalse("State for '$phrase' must NOT be ConfirmationRequired", state is AssistantUiState.ConfirmationRequired)
            assertNull("pendingAction must remain null", controller.pendingAction)
            assertEquals("Action for '$phrase' must execute immediately exactly once", 1, fakeActionExecutor.executedActions.size)
            assertEquals(expectedIntent, fakeActionExecutor.executedActions.last())
        }
    }

    @Test
    fun testPart29_WorkflowDeclinedWithNoDoesNotExecute() = runBlocking {
        val sampleWorkflow = createSampleChangeWallpaperWorkflow()
        persistenceRepo.saveWorkflow(sampleWorkflow)
        startTestContinuousListening()

        controller.processCommand("change my wallpaper")
        assertTrue(controller.uiState.value is AssistantUiState.ConfirmationRequired)
        assertEquals(0, fakeEngine.executeWorkflowCallCount)

        // User says "no"
        controller.processCommand("no")

        // Zero execution, pending workflow cleared
        assertEquals(0, fakeEngine.executeWorkflowCallCount)
        assertNull(controller.pendingWorkflow)

        // Resumes listening automatically
        assertTrue(continuousVoiceController.isContinuousListening.value)
        assertTrue(controller.uiState.value is AssistantUiState.Listening)
    }

    @Test
    fun testPart29_StopListeningDuringConfirmationCancelsAndStops() = runBlocking {
        val sampleWorkflow = createSampleChangeWallpaperWorkflow()
        persistenceRepo.saveWorkflow(sampleWorkflow)
        startTestContinuousListening()

        controller.processCommand("change my wallpaper")
        assertTrue(controller.uiState.value is AssistantUiState.ConfirmationRequired)

        // User says "stop listening" while confirmation is pending
        controller.processCommand("stop listening")

        // Must cancel pending action, stop listening, set state to IDLE
        assertEquals(0, fakeEngine.executeWorkflowCallCount)
        assertNull(controller.pendingWorkflow)
        assertFalse("Continuous listening must be stopped", continuousVoiceController.isContinuousListening.value)
        assertFalse("Microphone must be OFF", fakeSpeechEngine.isListening)
        assertTrue("State must be Idle", controller.uiState.value is AssistantUiState.Idle)
    }

    @Test
    fun testPart30_ContinuousListeningBetweenDirectActionsWithoutMicButton() = runBlocking {
        startTestContinuousListening()

        // 1. "Open Settings" -> executes immediately without confirmation
        fakeSpeechEngine.simulateRecognitionResult("open settings")
        assertEquals(1, fakeActionExecutor.executedActions.size)
        assertEquals(CommandIntent.OPEN_SETTINGS, fakeActionExecutor.executedActions[0])

        // Automatically returns to listening
        assertTrue("Continuous listening must remain active", continuousVoiceController.isContinuousListening.value)
        assertTrue("State must return to Listening", controller.uiState.value is AssistantUiState.Listening)
        assertTrue("Microphone must remain active", fakeSpeechEngine.isListening)

        // 2. "Go back" in the same continuous session WITHOUT pressing mic
        fakeSpeechEngine.simulateRecognitionResult("go back")
        assertEquals(2, fakeActionExecutor.executedActions.size)
        assertEquals(CommandIntent.GO_BACK, fakeActionExecutor.executedActions[1])

        // Automatically returns to listening again
        assertTrue("Continuous listening must remain active", continuousVoiceController.isContinuousListening.value)
        assertTrue(controller.uiState.value is AssistantUiState.Listening)
    }

    @Test
    fun testPart31_DuplicateYesDoesNotExecuteWorkflowTwice() = runBlocking {
        val sampleWorkflow = createSampleChangeWallpaperWorkflow()
        persistenceRepo.saveWorkflow(sampleWorkflow)

        controller.processCommand("change my wallpaper")
        assertTrue(controller.uiState.value is AssistantUiState.ConfirmationRequired)

        // First YES
        controller.processCommand("yes")
        assertEquals(1, fakeEngine.executeWorkflowCallCount)

        // Duplicate YES while or after completed
        controller.processCommand("yes")
        assertEquals("Duplicate YES must not execute workflow again", 1, fakeEngine.executeWorkflowCallCount)
    }

    @Test
    fun testDynamicGestureCoordinatesCalculationPortraitAndLandscape() {
        // Portrait tablet (1600 x 2560)
        val pWidth = 1600
        val pHeight = 2560

        val pSwipeLeft = com.taskscheduling.app.assistant.GestureCalculator.calculate(CommandIntent.SWIPE_LEFT, pWidth, pHeight)!!
        assertEquals(1200, pSwipeLeft.startX) // 75%
        assertEquals(1280, pSwipeLeft.startY) // 50%
        assertEquals(400, pSwipeLeft.endX)   // 25%
        assertEquals(1280, pSwipeLeft.endY)   // 50%

        val pSwipeRight = com.taskscheduling.app.assistant.GestureCalculator.calculate(CommandIntent.SWIPE_RIGHT, pWidth, pHeight)!!
        assertEquals(400, pSwipeRight.startX) // 25%
        assertEquals(1200, pSwipeRight.endX)  // 75%

        val pSwipeUp = com.taskscheduling.app.assistant.GestureCalculator.calculate(CommandIntent.SWIPE_UP, pWidth, pHeight)!!
        assertEquals(800, pSwipeUp.startX)   // 50%
        assertEquals(1920, pSwipeUp.startY)  // 75%
        assertEquals(800, pSwipeUp.endX)     // 50%
        assertEquals(640, pSwipeUp.endY)     // 25%

        val pSwipeDown = com.taskscheduling.app.assistant.GestureCalculator.calculate(CommandIntent.SWIPE_DOWN, pWidth, pHeight)!!
        assertEquals(800, pSwipeDown.startX)  // 50%
        assertEquals(640, pSwipeDown.startY)  // 25%
        assertEquals(800, pSwipeDown.endX)    // 50%
        assertEquals(1920, pSwipeDown.endY)   // 75%

        val pScrollUp = com.taskscheduling.app.assistant.GestureCalculator.calculate(CommandIntent.SCROLL_UP, pWidth, pHeight)!!
        assertEquals(800, pScrollUp.startX)   // 50%
        assertEquals(1792, pScrollUp.startY)  // 70%
        assertEquals(800, pScrollUp.endX)     // 50%
        assertEquals(768, pScrollUp.endY)     // 30%

        val pScrollDown = com.taskscheduling.app.assistant.GestureCalculator.calculate(CommandIntent.SCROLL_DOWN, pWidth, pHeight)!!
        assertEquals(800, pScrollDown.startX)  // 50%
        assertEquals(768, pScrollDown.startY)  // 30%
        assertEquals(800, pScrollDown.endX)    // 50%
        assertEquals(1792, pScrollDown.endY)   // 70%

        // Landscape tablet (2560 x 1600)
        val lWidth = 2560
        val lHeight = 1600

        val lSwipeLeft = com.taskscheduling.app.assistant.GestureCalculator.calculate(CommandIntent.SWIPE_LEFT, lWidth, lHeight)!!
        assertEquals(1920, lSwipeLeft.startX) // 75% of 2560
        assertEquals(800, lSwipeLeft.startY)  // 50% of 1600
        assertEquals(640, lSwipeLeft.endX)    // 25% of 2560
        assertEquals(800, lSwipeLeft.endY)    // 50% of 1600

        val lSwipeUp = com.taskscheduling.app.assistant.GestureCalculator.calculate(CommandIntent.SWIPE_UP, lWidth, lHeight)!!
        assertEquals(1280, lSwipeUp.startX)  // 50% of 2560
        assertEquals(1200, lSwipeUp.startY)  // 75% of 1600
        assertEquals(1280, lSwipeUp.endX)    // 50% of 2560
        assertEquals(400, lSwipeUp.endY)     // 25% of 1600
    }

    @Test
    fun testActionExecutionFailureReportedGracefully() = runBlocking {
        fakeActionExecutor.shouldFail = true
        fakeActionExecutor.failureMessage = "I couldn't go back."

        controller.processCommand("go back")

        val state = controller.uiState.value
        assertTrue("Failure must set Error state", state is AssistantUiState.Error)
        assertEquals("I couldn't go back.", (state as AssistantUiState.Error).message)
    }

    @Test
    fun testExistingChangeWallpaperWorkflowStillWorksCompletely() = runBlocking {
        val sampleWorkflow = createSampleChangeWallpaperWorkflow()
        persistenceRepo.saveWorkflow(sampleWorkflow)

        controller.processCommand("change my wallpaper")
        val state = controller.uiState.value
        assertTrue(state is AssistantUiState.ConfirmationRequired)
        val conf = state as AssistantUiState.ConfirmationRequired
        assertEquals("Change Wallpaper", conf.workflow?.name)
        assertEquals(CommandIntent.CHANGE_WALLPAPER, conf.intent)
        assertEquals(0, fakeEngine.executeWorkflowCallCount)

        controller.processCommand("yes")
        assertEquals(1, fakeEngine.executeWorkflowCallCount)
        assertEquals("Change Wallpaper", fakeEngine.lastExecutedWorkflow?.name)
    }

    // =========================================================================
    // EXECUTION POLICY AND IMMEDIATE EXECUTION TESTS (SECTIONS 28-31)
    // =========================================================================

    @Test
    fun testSection28_ExecutionPolicyCategorization() {
        val immediateIntents = listOf(
            CommandIntent.SWIPE_UP,
            CommandIntent.SWIPE_DOWN,
            CommandIntent.SWIPE_LEFT,
            CommandIntent.SWIPE_RIGHT,
            CommandIntent.SCROLL_UP,
            CommandIntent.SCROLL_DOWN,
            CommandIntent.GO_BACK,
            CommandIntent.GO_HOME,
            CommandIntent.OPEN_YOUTUBE,
            CommandIntent.OPEN_SETTINGS
        )

        for (intent in immediateIntents) {
            assertEquals(
                "Intent $intent must have IMMEDIATE execution policy",
                ExecutionPolicy.IMMEDIATE,
                DefaultExecutionPolicyResolver.getExecutionPolicy(intent)
            )
        }

        assertEquals(
            "CHANGE_WALLPAPER must have CONFIRMATION_REQUIRED execution policy",
            ExecutionPolicy.CONFIRMATION_REQUIRED,
            DefaultExecutionPolicyResolver.getExecutionPolicy(CommandIntent.CHANGE_WALLPAPER)
        )

        assertEquals(
            "Any saved workflow must require confirmation",
            ExecutionPolicy.CONFIRMATION_REQUIRED,
            DefaultExecutionPolicyResolver.getWorkflowExecutionPolicy(createSampleChangeWallpaperWorkflow())
        )
    }

    @Test
    fun testSection29_SwipeUp_ExecutesImmediatelyExactlyOnce_NeverEntersConfirmation() = runBlocking {
        controller.reset()
        fakeActionExecutor.executedActions.clear()

        var confirmationStateEncountered = false
        val job = launch {
            controller.uiState.collect { state ->
                if (state is AssistantUiState.ConfirmationRequired) {
                    confirmationStateEncountered = true
                }
            }
        }

        controller.processCommand("swipe up")

        job.cancel()
        assertFalse("Confirmation state must NEVER be entered for 'swipe up'", confirmationStateEncountered)
        assertEquals("Executor must be called exactly once for 'swipe up'", 1, fakeActionExecutor.executedActions.size)
        assertEquals(CommandIntent.SWIPE_UP, fakeActionExecutor.executedActions.first())
        assertNull("Pending action must remain null for immediate commands", controller.pendingAction)
    }

    @Test
    fun testSection29_AllImmediateCommands_ExecuteImmediatelyWithoutConfirmationUi() = runBlocking {
        val commands = listOf(
            "swipe up" to CommandIntent.SWIPE_UP,
            "swipe down" to CommandIntent.SWIPE_DOWN,
            "swipe left" to CommandIntent.SWIPE_LEFT,
            "swipe right" to CommandIntent.SWIPE_RIGHT,
            "scroll up" to CommandIntent.SCROLL_UP,
            "scroll down" to CommandIntent.SCROLL_DOWN,
            "go back" to CommandIntent.GO_BACK,
            "go home" to CommandIntent.GO_HOME,
            "open youtube" to CommandIntent.OPEN_YOUTUBE,
            "open settings" to CommandIntent.OPEN_SETTINGS
        )

        for ((cmd, expectedIntent) in commands) {
            controller.reset()
            fakeActionExecutor.executedActions.clear()

            var sawConfirmation = false
            val job = launch {
                controller.uiState.collect { state ->
                    if (state is AssistantUiState.ConfirmationRequired) {
                        sawConfirmation = true
                    }
                }
            }

            controller.processCommand(cmd)
            job.cancel()

            assertFalse("Command '$cmd' must NOT show confirmation UI", sawConfirmation)
            assertEquals("Command '$cmd' must execute immediately exactly once", 1, fakeActionExecutor.executedActions.size)
            assertEquals(expectedIntent, fakeActionExecutor.executedActions.first())
            assertNull("No pending action should remain", controller.pendingAction)
        }
    }

    @Test
    fun testSection30_ChangeWallpaper_RequiresConfirmationBeforeExecution() = runBlocking {
        val sampleWorkflow = createSampleChangeWallpaperWorkflow()
        persistenceRepo.saveWorkflow(sampleWorkflow)
        controller.reset()
        fakeEngine.executeWorkflowCallCount = 0

        // 1. User says "change my wallpaper"
        controller.processCommand("change my wallpaper")

        // Expected: workflow found and WAITING_FOR_CONFIRMATION
        val state = controller.uiState.value
        assertTrue("Must be in ConfirmationRequired state", state is AssistantUiState.ConfirmationRequired)
        val conf = state as AssistantUiState.ConfirmationRequired
        assertEquals("Change Wallpaper", conf.workflow?.name)
        assertEquals(CommandIntent.CHANGE_WALLPAPER, conf.intent)
        // Expected: executor NOT called yet
        assertEquals("Workflow must NOT execute before confirmation", 0, fakeEngine.executeWorkflowCallCount)

        // 2. User says "yes"
        controller.processCommand("yes")

        // Expected: executor called exactly once
        assertEquals("Workflow must execute exactly once on 'yes'", 1, fakeEngine.executeWorkflowCallCount)
        assertEquals("Change Wallpaper", fakeEngine.lastExecutedWorkflow?.name)
    }

    @Test
    fun testSection30_ChangeWallpaper_DeclinedWithNoDoesNotExecute() = runBlocking {
        val sampleWorkflow = createSampleChangeWallpaperWorkflow()
        persistenceRepo.saveWorkflow(sampleWorkflow)
        startTestContinuousListening()
        fakeEngine.executeWorkflowCallCount = 0

        controller.processCommand("change my wallpaper")
        assertTrue(controller.uiState.value is AssistantUiState.ConfirmationRequired)
        assertEquals(0, fakeEngine.executeWorkflowCallCount)

        controller.processCommand("no")

        assertEquals("Workflow must NOT execute on 'no'", 0, fakeEngine.executeWorkflowCallCount)
        assertNull("Pending workflow must be cleared", controller.pendingWorkflow)
        assertTrue("Continuous listening must remain active", continuousVoiceController.isContinuousListening.value)
        assertTrue("Must return to Listening state", controller.uiState.value is AssistantUiState.Listening)
    }

    @Test
    fun testSection31_ContinuousListening_FullRegressionSequence() = runBlocking {
        val sampleWorkflow = createSampleChangeWallpaperWorkflow()
        persistenceRepo.saveWorkflow(sampleWorkflow)

        startTestContinuousListening()
        fakeActionExecutor.executedActions.clear()
        fakeEngine.executeWorkflowCallCount = 0

        // 1. "Swipe up." -> immediate execution
        fakeSpeechEngine.simulateRecognitionResult("Swipe up.")
        assertEquals("Swipe up executes immediately", 1, fakeActionExecutor.executedActions.size)
        assertEquals(CommandIntent.SWIPE_UP, fakeActionExecutor.executedActions[0])
        assertTrue("Continuous listening remains active", continuousVoiceController.isContinuousListening.value)
        assertTrue("Returns to Listening state", controller.uiState.value is AssistantUiState.Listening)

        // 2. "Swipe down." -> immediate execution
        fakeSpeechEngine.simulateRecognitionResult("Swipe down.")
        assertEquals("Swipe down executes immediately", 2, fakeActionExecutor.executedActions.size)
        assertEquals(CommandIntent.SWIPE_DOWN, fakeActionExecutor.executedActions[1])
        assertTrue("Continuous listening remains active", continuousVoiceController.isContinuousListening.value)
        assertTrue("Returns to Listening state", controller.uiState.value is AssistantUiState.Listening)

        // 3. "Open Settings." -> immediate execution
        fakeSpeechEngine.simulateRecognitionResult("Open Settings.")
        assertEquals("Open Settings executes immediately", 3, fakeActionExecutor.executedActions.size)
        assertEquals(CommandIntent.OPEN_SETTINGS, fakeActionExecutor.executedActions[2])
        assertTrue("Continuous listening remains active", continuousVoiceController.isContinuousListening.value)
        assertTrue("Returns to Listening state", controller.uiState.value is AssistantUiState.Listening)

        // 4. "Change my wallpaper." -> confirmation prompt appears
        fakeSpeechEngine.simulateRecognitionResult("Change my wallpaper.")
        assertTrue("Must require confirmation", controller.uiState.value is AssistantUiState.ConfirmationRequired)
        assertEquals("Workflow not executed yet", 0, fakeEngine.executeWorkflowCallCount)

        // User says "Yes." -> workflow executes
        fakeSpeechEngine.simulateRecognitionResult("Yes.")
        assertEquals("Workflow executes exactly once", 1, fakeEngine.executeWorkflowCallCount)
        assertEquals("Change Wallpaper", fakeEngine.lastExecutedWorkflow?.name)
        assertTrue("Continuous listening remains active", continuousVoiceController.isContinuousListening.value)
        assertTrue("Returns to Listening state", controller.uiState.value is AssistantUiState.Listening)

        // 5. "Swipe left." -> immediate execution
        fakeSpeechEngine.simulateRecognitionResult("Swipe left.")
        assertEquals("Swipe left executes immediately", 4, fakeActionExecutor.executedActions.size)
        assertEquals(CommandIntent.SWIPE_LEFT, fakeActionExecutor.executedActions[3])
        assertTrue("Continuous listening remains active", continuousVoiceController.isContinuousListening.value)
        assertTrue("Returns to Listening state", controller.uiState.value is AssistantUiState.Listening)

        // 6. "Stop listening." -> listening stops
        fakeSpeechEngine.simulateRecognitionResult("Stop listening.")
        assertFalse("Continuous listening must stop", continuousVoiceController.isContinuousListening.value)
        assertFalse("Microphone must be OFF", fakeSpeechEngine.isListening)
        assertTrue("State is IDLE", controller.uiState.value is AssistantUiState.Idle)
    }

    @Test
    fun testTypedAndVoiceCommands_FollowIdenticalExecutionPolicy() = runBlocking {
        // Typed command "swipe up" executes immediately
        controller.reset()
        fakeActionExecutor.executedActions.clear()
        controller.processCommand("swipe up")
        assertEquals(1, fakeActionExecutor.executedActions.size)
        assertEquals(CommandIntent.SWIPE_UP, fakeActionExecutor.executedActions.first())
        assertNull(controller.pendingAction)

        // Typed command "change my wallpaper" requires confirmation
        val sampleWorkflow = createSampleChangeWallpaperWorkflow()
        persistenceRepo.saveWorkflow(sampleWorkflow)
        controller.reset()
        fakeEngine.executeWorkflowCallCount = 0
        controller.processCommand("change my wallpaper")
        assertTrue(controller.uiState.value is AssistantUiState.ConfirmationRequired)
        assertEquals(0, fakeEngine.executeWorkflowCallCount)
        controller.processCommand("yes")
        assertEquals(1, fakeEngine.executeWorkflowCallCount)
    }

    // ========================================================================
    // SECTION 34 TESTS: V1 CUSTOM WORKFLOW NAMING + VOICE INVOCATION
    // ========================================================================

    private fun createCustomWorkflow(
        id: String = UUID.randomUUID().toString(),
        name: String = "YouTube Study",
        stepsCount: Int = 5
    ): Workflow {
        val steps = (1..stepsCount).map { i ->
            WorkflowStep(
                id = "step_$i",
                order = i,
                type = when (i % 5) {
                    1 -> ActionType.TAP
                    2 -> ActionType.SWIPE
                    3 -> ActionType.TAP
                    4 -> ActionType.WAIT
                    else -> ActionType.BACK
                },
                startX = 100 * i,
                startY = 200 * i,
                duration = 1000L
            )
        }
        return Workflow(
            id = id,
            name = name,
            description = "Custom test workflow for $name",
            status = WorkflowStatus.CALIBRATED,
            deviceProfile = testDeviceProfile,
            steps = steps
        )
    }

    // Test 1: Workflow name persistence
    @Test
    fun test1_WorkflowNamePersistence() = runBlocking {
        val id = UUID.randomUUID().toString()
        val customWf = createCustomWorkflow(id = id, name = "YouTube Study")
        persistenceRepo.saveWorkflow(customWf)

        val retrieved = persistenceRepo.getWorkflow(id)
        assertNotNull("Workflow must be persisted", retrieved)
        assertEquals("YouTube Study", retrieved?.name)
        assertEquals(id, retrieved?.id)
    }

    // Test 2: Workflow name retrieval
    @Test
    fun test2_WorkflowNameRetrieval() = runBlocking {
        val wf1 = createCustomWorkflow(name = "Morning Routine")
        val wf2 = createCustomWorkflow(name = "YouTube Study")
        persistenceRepo.saveWorkflow(wf1)
        persistenceRepo.saveWorkflow(wf2)

        val all = persistenceRepo.getAllWorkflows()
        assertTrue(all.any { it.name == "Morning Routine" })
        assertTrue(all.any { it.name == "YouTube Study" })
    }

    // Test 3: Exact workflow matching
    @Test
    fun test3_ExactWorkflowMatching() = runBlocking {
        val wf = createCustomWorkflow(name = "YouTube Study")
        persistenceRepo.saveWorkflow(wf)

        val resolution = workflowResolver.resolveByName("YouTube Study")
        assertTrue("Must be Found", resolution is com.taskscheduling.app.assistant.WorkflowResolution.Found)
        assertEquals("YouTube Study", (resolution as com.taskscheduling.app.assistant.WorkflowResolution.Found).workflow.name)
    }

    // Test 4: Normalized workflow matching
    @Test
    fun test4_NormalizedWorkflowMatching() = runBlocking {
        val wf = createCustomWorkflow(name = "YouTube Study")
        persistenceRepo.saveWorkflow(wf)

        val r1 = workflowResolver.resolveByName("youtube study")
        assertTrue(r1 is com.taskscheduling.app.assistant.WorkflowResolution.Found)
        assertEquals("YouTube Study", (r1 as com.taskscheduling.app.assistant.WorkflowResolution.Found).workflow.name)

        val r2 = workflowResolver.resolveByName("YOUTUBE STUDY")
        assertTrue(r2 is com.taskscheduling.app.assistant.WorkflowResolution.Found)

        val r3 = workflowResolver.resolveByName("   youtube   study   ")
        assertTrue(r3 is com.taskscheduling.app.assistant.WorkflowResolution.Found)
    }

    // Test 5: "run <name>" matching
    @Test
    fun test5_RunPrefixMatching() = runBlocking {
        val wf = createCustomWorkflow(name = "YouTube Study")
        persistenceRepo.saveWorkflow(wf)

        val res = workflowResolver.resolveByName("run youtube study")
        assertTrue(res is com.taskscheduling.app.assistant.WorkflowResolution.Found)
        assertEquals("YouTube Study", (res as com.taskscheduling.app.assistant.WorkflowResolution.Found).workflow.name)
    }

    // Test 6: "start <name>" matching
    @Test
    fun test6_StartPrefixMatching() = runBlocking {
        val wf = createCustomWorkflow(name = "YouTube Study")
        persistenceRepo.saveWorkflow(wf)

        val res = workflowResolver.resolveByName("start youtube study")
        assertTrue(res is com.taskscheduling.app.assistant.WorkflowResolution.Found)
        assertEquals("YouTube Study", (res as com.taskscheduling.app.assistant.WorkflowResolution.Found).workflow.name)
    }

    // Test 7: "execute <name>" matching
    @Test
    fun test7_ExecutePrefixMatching() = runBlocking {
        val wf = createCustomWorkflow(name = "YouTube Study")
        persistenceRepo.saveWorkflow(wf)

        val res = workflowResolver.resolveByName("execute youtube study")
        assertTrue(res is com.taskscheduling.app.assistant.WorkflowResolution.Found)
        assertEquals("YouTube Study", (res as com.taskscheduling.app.assistant.WorkflowResolution.Found).workflow.name)
    }

    // Test 8: "run my <name>" matching
    @Test
    fun test8_RunMyPrefixMatching() = runBlocking {
        val wf = createCustomWorkflow(name = "YouTube Study")
        persistenceRepo.saveWorkflow(wf)

        val res = workflowResolver.resolveByName("run my youtube study")
        assertTrue(res is com.taskscheduling.app.assistant.WorkflowResolution.Found)
        assertEquals("YouTube Study", (res as com.taskscheduling.app.assistant.WorkflowResolution.Found).workflow.name)

        // Also test "start my study workflow"
        val wfStudy = createCustomWorkflow(name = "Study")
        persistenceRepo.saveWorkflow(wfStudy)
        val resSuffix = workflowResolver.resolveByName("start my study workflow")
        assertTrue(resSuffix is com.taskscheduling.app.assistant.WorkflowResolution.Found)
        assertEquals("Study", (resSuffix as com.taskscheduling.app.assistant.WorkflowResolution.Found).workflow.name)
    }

    // Test 9: Workflow not found
    @Test
    fun test9_WorkflowNotFound() = runBlocking {
        val wf = createCustomWorkflow(name = "YouTube Study")
        persistenceRepo.saveWorkflow(wf)

        controller.autoResumeDelayMs = 1000L
        startTestContinuousListening()
        fakeEngine.executeWorkflowCallCount = 0

        controller.processCommand("Run my banking routine")
        assertEquals(0, fakeEngine.executeWorkflowCallCount)
        assertNull("Pending workflow must be null", controller.pendingWorkflow)

        // Verify friendly message
        val uiState = controller.uiState.value
        assertTrue("Must be in Error state before resuming to listening", uiState is AssistantUiState.Error)
        assertEquals("I couldn't find a workflow called Banking Routine.", (uiState as AssistantUiState.Error).message)

        controller.autoResumeDelayMs = 0L
    }

    // Test 10: Ambiguous workflow match
    @Test
    fun test10_AmbiguousWorkflowMatch() = runBlocking {
        val wf1 = createCustomWorkflow(name = "Study Morning")
        val wf2 = createCustomWorkflow(name = "Study Evening")
        persistenceRepo.saveWorkflow(wf1)
        persistenceRepo.saveWorkflow(wf2)

        controller.autoResumeDelayMs = 1000L
        startTestContinuousListening()
        fakeEngine.executeWorkflowCallCount = 0

        // "Run Study" matches both without an exact "Study" workflow
        controller.processCommand("Run Study")
        assertEquals(0, fakeEngine.executeWorkflowCallCount)
        assertNull(controller.pendingWorkflow)

        val state = controller.uiState.value
        assertTrue("Must enter AmbiguousWorkflows state", state is AssistantUiState.AmbiguousWorkflows)
        val ambState = state as AssistantUiState.AmbiguousWorkflows
        assertEquals(2, ambState.candidates.size)
        assertTrue(ambState.message.contains("I found multiple matching workflows"))

        controller.autoResumeDelayMs = 0L
    }

    // Test 11: Confirmation required for workflows
    @Test
    fun test11_ConfirmationRequiredForWorkflows() = runBlocking {
        val wf = createCustomWorkflow(name = "YouTube Study")
        persistenceRepo.saveWorkflow(wf)

        startTestContinuousListening()
        fakeEngine.executeWorkflowCallCount = 0

        controller.processCommand("Run YouTube Study")
        assertEquals("Must NOT execute before confirmation", 0, fakeEngine.executeWorkflowCallCount)

        val state = controller.uiState.value
        assertTrue("Must be in ConfirmationRequired", state is AssistantUiState.ConfirmationRequired)
        val confState = state as AssistantUiState.ConfirmationRequired
        assertEquals("YouTube Study", confState.workflow?.name)
        assertTrue(confState.message.contains("I found your YouTube Study workflow"))
        assertTrue(confState.message.contains("Do you want me to run it?"))
    }

    // Test 12: YES executes exactly once
    @Test
    fun test12_YesExecutesExactlyOnce() = runBlocking {
        val wf = createCustomWorkflow(name = "YouTube Study")
        persistenceRepo.saveWorkflow(wf)

        startTestContinuousListening()
        fakeEngine.executeWorkflowCallCount = 0

        controller.processCommand("Run YouTube Study")
        assertTrue(controller.uiState.value is AssistantUiState.ConfirmationRequired)

        // User confirms "Yes"
        controller.processCommand("Yes")
        assertEquals("Must execute exactly once", 1, fakeEngine.executeWorkflowCallCount)
        assertEquals("YouTube Study", fakeEngine.lastExecutedWorkflow?.name)

        // Duplicate "Yes" speech immediately afterwards must NOT execute again
        controller.processCommand("Yes")
        assertEquals("Duplicate Yes must not execute again", 1, fakeEngine.executeWorkflowCallCount)
    }

    // Test 13: NO cancels
    @Test
    fun test13_NoCancels() = runBlocking {
        val wf = createCustomWorkflow(name = "YouTube Study")
        persistenceRepo.saveWorkflow(wf)

        startTestContinuousListening()
        fakeEngine.executeWorkflowCallCount = 0

        controller.processCommand("Run YouTube Study")
        assertTrue(controller.uiState.value is AssistantUiState.ConfirmationRequired)

        controller.processCommand("No")
        assertEquals("Must not execute on No", 0, fakeEngine.executeWorkflowCallCount)
        assertNull(controller.pendingWorkflow)
        assertTrue("Listening remains active", continuousVoiceController.isContinuousListening.value)
    }

    // Test 14: Stop listening cancels
    @Test
    fun test14_StopListeningCancels() = runBlocking {
        val wf = createCustomWorkflow(name = "YouTube Study")
        persistenceRepo.saveWorkflow(wf)

        startTestContinuousListening()
        fakeEngine.executeWorkflowCallCount = 0

        controller.processCommand("Run YouTube Study")
        assertTrue(controller.uiState.value is AssistantUiState.ConfirmationRequired)

        controller.processCommand("Stop listening")
        assertEquals("Must not execute", 0, fakeEngine.executeWorkflowCallCount)
        assertNull(controller.pendingWorkflow)
        assertFalse("Continuous listening must be stopped", continuousVoiceController.isContinuousListening.value)
        assertFalse("Microphone must be OFF", fakeSpeechEngine.isListening)
        assertTrue("State must be IDLE", controller.uiState.value is AssistantUiState.Idle)
    }

    // Test 15: Direct commands remain immediate
    @Test
    fun test15_DirectCommandsRemainImmediate() = runBlocking {
        startTestContinuousListening()
        fakeActionExecutor.executedActions.clear()

        val directCommands = listOf(
            "Swipe up" to CommandIntent.SWIPE_UP,
            "Swipe down" to CommandIntent.SWIPE_DOWN,
            "Swipe left" to CommandIntent.SWIPE_LEFT,
            "Swipe right" to CommandIntent.SWIPE_RIGHT,
            "Scroll up" to CommandIntent.SCROLL_UP,
            "Scroll down" to CommandIntent.SCROLL_DOWN,
            "Go back" to CommandIntent.GO_BACK,
            "Go home" to CommandIntent.GO_HOME,
            "Open Settings" to CommandIntent.OPEN_SETTINGS,
            "Open YouTube" to CommandIntent.OPEN_YOUTUBE
        )

        for ((index, pair) in directCommands.withIndex()) {
            val (commandText, expectedIntent) = pair
            controller.processCommand(commandText)
            assertEquals("Direct command '$commandText' must execute immediately", index + 1, fakeActionExecutor.executedActions.size)
            assertEquals(expectedIntent, fakeActionExecutor.executedActions.last())
            assertNull("No pending action should exist", controller.pendingAction)
        }
    }

    // Test 16: Workflow execution remains sequential
    @Test
    fun test16_WorkflowExecutionRemainsSequential() = runBlocking {
        val wf = createCustomWorkflow(name = "Sequential Task", stepsCount = 5)
        persistenceRepo.saveWorkflow(wf)

        startTestContinuousListening()
        fakeEngine.executeWorkflowCallCount = 0

        controller.processCommand("Run Sequential Task")
        controller.processCommand("Yes")

        assertEquals(1, fakeEngine.executeWorkflowCallCount)
        val executedOrders = fakeEngine.lastExecutedStepSequence.map { it.order }
        assertEquals("Steps must execute sequentially", listOf(1, 2, 3, 4, 5), executedOrders)
    }

    // Test 17: Workflow failure stops execution
    @Test
    fun test17_WorkflowFailureStopsExecution() = runBlocking {
        val wf = createCustomWorkflow(name = "Failing Workflow", stepsCount = 6)
        persistenceRepo.saveWorkflow(wf)

        fakeEngine.shouldFailAtStep = 4
        controller.autoResumeDelayMs = 1000L

        startTestContinuousListening()
        controller.processCommand("Run Failing Workflow")
        controller.processCommand("Yes")

        // Steps after step 4 should not have been completed
        val executedOrders = fakeEngine.lastExecutedStepSequence.map { it.order }
        assertEquals(listOf(1, 2, 3), executedOrders)

        // Error message in UI state
        val errorState = controller.uiState.value
        assertTrue("State must be Error: $errorState", errorState is AssistantUiState.Error)
        assertEquals("Workflow stopped at step 4.", (errorState as AssistantUiState.Error).message)

        controller.autoResumeDelayMs = 0L
    }

    // Test 18: Workflow remains persisted after reload
    @Test
    fun test18_WorkflowRemainsPersistedAfterReload() = runBlocking {
        val id = UUID.randomUUID().toString()
        val customWf = createCustomWorkflow(id = id, name = "Persisted Across Reboots")
        persistenceRepo.saveWorkflow(customWf)

        // Recreate repository from the same disk baseDir to simulate app/device restart
        val reloadedRepo = WorkflowPersistenceRepository(tempDir)
        val loadedWorkflow = reloadedRepo.getWorkflow(id)

        assertNotNull("Workflow must be loaded after storage reload", loadedWorkflow)
        assertEquals("Persisted Across Reboots", loadedWorkflow?.name)
        assertEquals(id, loadedWorkflow?.id)
        assertEquals(5, loadedWorkflow?.steps?.size)
    }

    // ========================================================================
    // SECTION 35 REGRESSION TEST SUITE
    // ========================================================================
    @Test
    fun testSection35_FullRegressionSuite() = runBlocking {
        val wallpaperWf = createSampleChangeWallpaperWorkflow()
        val studyWf = createCustomWorkflow(name = "YouTube Study")
        persistenceRepo.saveWorkflow(wallpaperWf)
        persistenceRepo.saveWorkflow(studyWf)

        startTestContinuousListening()
        fakeActionExecutor.executedActions.clear()
        fakeEngine.executeWorkflowCallCount = 0

        // 1. "Swipe up" -> immediate
        controller.processCommand("Swipe up")
        assertEquals(1, fakeActionExecutor.executedActions.size)
        assertEquals(CommandIntent.SWIPE_UP, fakeActionExecutor.executedActions.last())

        // 2. "Swipe down" -> immediate
        controller.processCommand("Swipe down")
        assertEquals(2, fakeActionExecutor.executedActions.size)
        assertEquals(CommandIntent.SWIPE_DOWN, fakeActionExecutor.executedActions.last())

        // 3. "Go back" -> immediate
        controller.processCommand("Go back")
        assertEquals(3, fakeActionExecutor.executedActions.size)
        assertEquals(CommandIntent.GO_BACK, fakeActionExecutor.executedActions.last())

        // 4. "Open Settings" -> immediate
        controller.processCommand("Open Settings")
        assertEquals(4, fakeActionExecutor.executedActions.size)
        assertEquals(CommandIntent.OPEN_SETTINGS, fakeActionExecutor.executedActions.last())

        // 5. "Open YouTube" -> immediate
        controller.processCommand("Open YouTube")
        assertEquals(5, fakeActionExecutor.executedActions.size)
        assertEquals(CommandIntent.OPEN_YOUTUBE, fakeActionExecutor.executedActions.last())

        // 6. "Change my wallpaper" -> confirmation
        controller.processCommand("Change my wallpaper")
        assertTrue("Wallpaper requires confirmation", controller.uiState.value is AssistantUiState.ConfirmationRequired)
        assertEquals(0, fakeEngine.executeWorkflowCallCount)
        controller.processCommand("no") // dismiss

        // 7. "Run YouTube Study" -> confirmation
        controller.processCommand("Run YouTube Study")
        assertTrue("Custom workflow requires confirmation", controller.uiState.value is AssistantUiState.ConfirmationRequired)
        assertEquals(0, fakeEngine.executeWorkflowCallCount)

        // 8. "Yes" -> workflow executes
        controller.processCommand("Yes")
        assertEquals("Workflow must execute", 1, fakeEngine.executeWorkflowCallCount)
        assertEquals("YouTube Study", fakeEngine.lastExecutedWorkflow?.name)
        assertTrue("Continuous listening resumes", continuousVoiceController.isContinuousListening.value)

        // 9. "Stop listening" -> assistant stops
        controller.processCommand("Stop listening")
        assertFalse("Continuous listening must be OFF", continuousVoiceController.isContinuousListening.value)
        assertFalse("Microphone must be OFF", fakeSpeechEngine.isListening)
        assertTrue("Assistant state must be IDLE", controller.uiState.value is AssistantUiState.Idle)
    }

    // ========================================================================
    // SECTION 29: GENERAL SPEECH TEST CASES (NON-COMMANDS)
    // ========================================================================
    @Test
    fun testSection29_GeneralSpeechNonCommands() = runBlocking {
        startTestContinuousListening()
        fakeActionExecutor.executedActions.clear()
        fakeEngine.executeWorkflowCallCount = 0

        val generalPhrases = listOf(
            "hello",
            "close",
            "open",
            "please",
            "how are you",
            "this is a test",
            "what are you doing",
            "I was talking about opening Settings yesterday."
        )

        for (phrase in generalPhrases) {
            val resolution = commandProcessor.process(phrase)
            assertTrue("Phrase \"$phrase\" must resolve to UnknownCommand", resolution is CommandResolutionResult.UnknownCommand)

            controller.processCommand(phrase)
            assertEquals("No direct action should execute for \"$phrase\"", 0, fakeActionExecutor.executedActions.size)
            assertEquals("No workflow should execute for \"$phrase\"", 0, fakeEngine.executeWorkflowCallCount)
            assertNull("No pending action should exist for \"$phrase\"", controller.pendingAction)
            assertTrue("Continuous listening must remain ACTIVE after \"$phrase\"", continuousVoiceController.isContinuousListening.value)
        }
    }

    // ========================================================================
    // SECTION 30: COMMANDS INSIDE NATURAL SPEECH
    // ========================================================================
    @Test
    fun testSection30_CommandsInsideNaturalSpeech() = runBlocking {
        val studyWf = createCustomWorkflow(name = "Study")
        val wallpaperWf = createSampleChangeWallpaperWorkflow()
        persistenceRepo.saveWorkflow(studyWf)
        persistenceRepo.saveWorkflow(wallpaperWf)

        // 1. "please swipe up" -> SWIPE_UP
        assertEquals(CommandIntent.SWIPE_UP, commandResolver.resolve("please swipe up"))
        // 2. "can you swipe down" -> SWIPE_DOWN
        assertEquals(CommandIntent.SWIPE_DOWN, commandResolver.resolve("can you swipe down"))
        // 3. "could you go back" -> GO_BACK
        assertEquals(CommandIntent.GO_BACK, commandResolver.resolve("could you go back"))
        // 4. "please go home" -> GO_HOME
        assertEquals(CommandIntent.GO_HOME, commandResolver.resolve("please go home"))
        // 5. "can you open settings" -> OPEN_SETTINGS
        assertEquals(CommandIntent.OPEN_SETTINGS, commandResolver.resolve("can you open settings"))
        // 6. "please open youtube" -> OPEN_YOUTUBE
        assertEquals(CommandIntent.OPEN_YOUTUBE, commandResolver.resolve("please open youtube"))
        // 7. "please change my wallpaper" -> CHANGE_WALLPAPER
        assertEquals(CommandIntent.CHANGE_WALLPAPER, commandResolver.resolve("please change my wallpaper"))
        // 8. "can you swipe up" -> SWIPE_UP
        assertEquals(CommandIntent.SWIPE_UP, commandResolver.resolve("can you swipe up"))
        // 9. "I want you to swipe up" -> SWIPE_UP
        assertEquals(CommandIntent.SWIPE_UP, commandResolver.resolve("I want you to swipe up"))
        // 10. "I want to change my wallpaper" -> CHANGE_WALLPAPER
        assertEquals(CommandIntent.CHANGE_WALLPAPER, commandResolver.resolve("I want to change my wallpaper"))

        // 11. "run my study workflow" -> resolves study workflow
        val res1 = commandProcessor.process("run my study workflow")
        assertTrue("run my study workflow should resolve to Success", res1 is CommandResolutionResult.Success)
        assertEquals("Study", (res1 as CommandResolutionResult.Success).workflow.name)

        // 12. "please run my study workflow" -> resolves study workflow
        val res2 = commandProcessor.process("please run my study workflow")
        assertTrue("please run my study workflow should resolve to Success", res2 is CommandResolutionResult.Success)
        assertEquals("Study", (res2 as CommandResolutionResult.Success).workflow.name)
    }

    // ========================================================================
    // SECTION 31: CONFIRMATION TEST CASES
    // ========================================================================
    @Test
    fun testSection31_ConfirmationVariations() {
        // YES variations
        assertEquals(ConfirmationIntent.YES, confirmationResolver.resolve("yes"))
        assertEquals(ConfirmationIntent.YES, confirmationResolver.resolve("yeah"))
        assertEquals(ConfirmationIntent.YES, confirmationResolver.resolve("yep"))
        assertEquals(ConfirmationIntent.YES, confirmationResolver.resolve("yes please"))
        assertEquals(ConfirmationIntent.YES, confirmationResolver.resolve("run it"))
        assertEquals(ConfirmationIntent.YES, confirmationResolver.resolve("do it"))
        assertEquals(ConfirmationIntent.YES, confirmationResolver.resolve("ok run it"))
        assertEquals(ConfirmationIntent.YES, confirmationResolver.resolve("okay"))

        // NO variations
        assertEquals(ConfirmationIntent.NO, confirmationResolver.resolve("no"))
        assertEquals(ConfirmationIntent.NO, confirmationResolver.resolve("no thanks"))
        assertEquals(ConfirmationIntent.NO, confirmationResolver.resolve("cancel"))

        // STOP LISTENING variations
        assertTrue(StopListeningResolver.isStopListening("stop listening"))
        assertTrue(StopListeningResolver.isStopListening("stop listening now"))
        assertTrue(StopListeningResolver.isStopListening("turn off listening"))
        assertTrue(StopListeningResolver.isStopListening("stop voice assistant"))
        assertTrue(StopListeningResolver.isStopListening("stop the assistant"))
        assertTrue(StopListeningResolver.isStopListening("deactivate listening"))
    }

    // ========================================================================
    // SECTION 32: CONTINUOUS LISTENING SEQUENCE
    // ========================================================================
    @Test
    fun testSection32_ContinuousListeningSequence() = runBlocking {
        startTestContinuousListening()
        fakeActionExecutor.executedActions.clear()
        fakeEngine.executeWorkflowCallCount = 0

        // Step 1: "hello" -> recognized, unknown, continue listening
        controller.processCommand("hello")
        assertEquals(0, fakeActionExecutor.executedActions.size)
        assertTrue("Continuous listening must remain true", continuousVoiceController.isContinuousListening.value)

        // Step 2: "close" -> recognized, unknown, continue listening
        controller.processCommand("close")
        assertEquals(0, fakeActionExecutor.executedActions.size)
        assertTrue("Continuous listening must remain true", continuousVoiceController.isContinuousListening.value)

        // Step 3: "swipe up" -> recognized, SWIPE_UP, execute, continue listening
        controller.processCommand("swipe up")
        assertEquals(1, fakeActionExecutor.executedActions.size)
        assertEquals(CommandIntent.SWIPE_UP, fakeActionExecutor.executedActions.last())
        assertTrue("Continuous listening must remain true", continuousVoiceController.isContinuousListening.value)

        // Step 4: "what is this" -> recognized, unknown, continue listening
        controller.processCommand("what is this")
        assertEquals(1, fakeActionExecutor.executedActions.size)
        assertTrue("Continuous listening must remain true", continuousVoiceController.isContinuousListening.value)

        // Step 5: "open settings" -> recognized, OPEN_SETTINGS, execute, continue listening
        controller.processCommand("open settings")
        assertEquals(2, fakeActionExecutor.executedActions.size)
        assertEquals(CommandIntent.OPEN_SETTINGS, fakeActionExecutor.executedActions.last())
        assertTrue("Continuous listening must remain true", continuousVoiceController.isContinuousListening.value)

        // Step 6: "stop listening" -> STOP_LISTENING, stops microphone, state = IDLE
        controller.processCommand("stop listening")
        assertFalse("Continuous listening must be false", continuousVoiceController.isContinuousListening.value)
        assertFalse("Microphone engine must not be listening", fakeSpeechEngine.isListening)
        assertTrue("Assistant state must be Idle", controller.uiState.value is AssistantUiState.Idle)
    }

    // ========================================================================
    // SECTION 24: VOICE RECOGNITION DEBUG MODE TRACKING
    // ========================================================================
    @Test
    fun testSection24_VoiceDebugModeTracking() {
        controller.processCommand("please open settings", rawVoskJson = "{\"text\":\"please open settings\"}")

        val debug = controller.voiceDebugInfo.value
        assertEquals("{\"text\":\"please open settings\"}", debug.rawVoskResult)
        assertEquals("please open settings", debug.finalText)
        assertEquals("please open settings", debug.normalizedText)
        assertEquals("OPEN_SETTINGS", debug.resolvedCommand)
        assertEquals("IMMEDIATE", debug.executionPolicy)
        assertEquals("DEFAULT", debug.audioSource)
        assertEquals(16000, debug.sampleRate)
        assertEquals(1, debug.channelCount)
    }

    // ========================================================================
    // SECTION 33: FAR-FIELD & CONCURRENCY IMPROVEMENTS
    // ========================================================================
    @Test
    fun testRapidStartStopListeningRaceConditions() {
        for (i in 0 until 10) {
            continuousVoiceController.startContinuousListening()
            continuousVoiceController.stopContinuousListening()
        }
        assertFalse("Continuous listening must be inactive after rapid toggle", continuousVoiceController.isContinuousListening.value)
        assertFalse("Microphone must not be listening after stop", fakeSpeechEngine.isListening)
        assertTrue("Assistant state must be Idle", controller.uiState.value is AssistantUiState.Idle)
    }

    @Test
    fun testConsecutiveCommandsExecutionRaceCondition() = runBlocking {
        startTestContinuousListening()
        fakeActionExecutor.executedActions.clear()

        // 1. Issue first immediate command
        continuousVoiceController.handleRecognizedSpeech("swipe up")
        assertEquals(1, fakeActionExecutor.executedActions.size)
        assertEquals(CommandIntent.SWIPE_UP, fakeActionExecutor.executedActions[0])

        // 2. Immediately issue second command while Completed state is active
        continuousVoiceController.handleRecognizedSpeech("swipe down")
        assertEquals(2, fakeActionExecutor.executedActions.size)
        assertEquals(CommandIntent.SWIPE_DOWN, fakeActionExecutor.executedActions[1])

        assertTrue("Continuous listener must remain active after consecutive commands", continuousVoiceController.isContinuousListening.value)
    }

    @Test
    fun testTtsSuppressionWindowProtectsAgainstSelfTriggering() = runBlocking {
        startTestContinuousListening()
        fakeActionExecutor.executedActions.clear()

        fakeSpeechOutputManager.manualFinish = true

        // Simulate TTS speaking "Do you want me to run it?"
        continuousVoiceController.speakWithMute("Do you want me to run it?")

        // During TTS: input should be ignored
        assertTrue("TTS should be marked active", continuousVoiceController.isTtsSpeaking)
        continuousVoiceController.handleRecognizedSpeech("run it")
        assertEquals("Speech during TTS must be suppressed", 0, fakeActionExecutor.executedActions.size)

        // Simulate TTS completion
        fakeSpeechOutputManager.completeSpeech()

        assertFalse("TTS should be marked finished", continuousVoiceController.isTtsSpeaking)

        // Within 350ms post-TTS suppression window: input should still be ignored
        continuousVoiceController.handleRecognizedSpeech("run it")
        assertEquals("Echo speech immediately post-TTS must be suppressed", 0, fakeActionExecutor.executedActions.size)

        // After post-TTS suppression window passes: input should be processed normally
        Thread.sleep(360L)
        continuousVoiceController.handleRecognizedSpeech("swipe up")
        assertEquals(1, fakeActionExecutor.executedActions.size)
        assertEquals(CommandIntent.SWIPE_UP, fakeActionExecutor.executedActions[0])
    }

    @Test
    fun testAudioDiagnosticsTracking() {
        controller.updateAudioDiagnostics(
            audioSource = "VOICE_RECOGNITION",
            sampleRate = 16000,
            channelCount = 1,
            bufferSize = 4096,
            audioRecordState = "RECORDING",
            isListening = true,
            voskState = "READY"
        )

        val debug = controller.voiceDebugInfo.value
        assertEquals("VOICE_RECOGNITION", debug.audioSource)
        assertEquals(16000, debug.sampleRate)
        assertEquals(1, debug.channelCount)
        assertEquals(4096, debug.bufferSize)
        assertEquals("RECORDING", debug.audioRecordState)
        assertTrue(debug.isListening)
        assertEquals("READY", debug.voskState)
    }

    @Test
    fun testGeneralSpeechEvaluationRemainsListening() = runBlocking {
        startTestContinuousListening()
        fakeActionExecutor.executedActions.clear()

        val unknownUtterances = listOf(
            "hello",
            "close",
            "open",
            "this is a test",
            "how are you",
            "please tell me something",
            "what is this"
        )

        for (utterance in unknownUtterances) {
            continuousVoiceController.handleRecognizedSpeech(utterance)
            assertEquals("Unknown utterance '$utterance' must NOT execute any action", 0, fakeActionExecutor.executedActions.size)
            assertTrue("Continuous listening must remain active after '$utterance'", continuousVoiceController.isContinuousListening.value)
        }

        // Actionable command must execute properly after all unknown speech
        continuousVoiceController.handleRecognizedSpeech("can you open settings")
        assertEquals(1, fakeActionExecutor.executedActions.size)
        assertEquals(CommandIntent.OPEN_SETTINGS, fakeActionExecutor.executedActions[0])
        assertTrue("Continuous listening must remain active after command execution", continuousVoiceController.isContinuousListening.value)
    }

    // =========================================================================
    // SECTION 33: MULTILINGUAL (ENGLISH, HINDI, HINGLISH) COMMAND & CONFIRMATION
    // =========================================================================
    @Test
    fun testMultilingualWallpaperCommandResolution() {
        val englishPhrases = listOf(
            "change my wallpaper" to CommandIntent.CHANGE_WALLPAPER,
            "Hello, change my wallpaper" to CommandIntent.CHANGE_WALLPAPER,
            "Could you please change my wallpaper" to CommandIntent.CHANGE_WALLPAPER
        )

        for ((phrase, expectedIntent) in englishPhrases) {
            val resolved = commandResolver.resolve(phrase)
            assertEquals("Phrase '$phrase' must resolve to $expectedIntent", expectedIntent, resolved)
        }

        val nonEnglishPhrases = listOf(
            "मेरा वॉलपेपर बदल दो",
            "नमस्ते, मेरा वॉलपेपर बदल दो।",
            "mera wallpaper badal do",
            "Hello, mera wallpaper badal do",
            "wallpaper change kar do",
            "bhai wallpaper badal do"
        )

        for (phrase in nonEnglishPhrases) {
            assertTrue("Non-English phrase '$phrase' must be flagged as non-English or not resolve",
                EnglishSpeechValidator.isNonEnglishOrUnreliable(phrase) || commandResolver.resolve(phrase) == null)
        }
    }

    @Test
    fun testMultilingualConfirmationResolution() {
        val yesPhrases = listOf(
            "yes", "yes run it", "run it", "confirm", "proceed"
        )
        for (phrase in yesPhrases) {
            val res = confirmationResolver.resolve(phrase)
            assertEquals("Phrase '$phrase' must resolve to YES", ConfirmationIntent.YES, res)
        }

        val noPhrases = listOf(
            "no", "cancel", "stop", "don't run", "abort"
        )
        for (phrase in noPhrases) {
            val res = confirmationResolver.resolve(phrase)
            assertEquals("Phrase '$phrase' must resolve to NO", ConfirmationIntent.NO, res)
        }

        val nonEnglishConfirmations = listOf(
            "हाँ", "हां", "हाँ चलाओ", "हाँ कर दो", "चलाओ", "कर दो", "शुरू करो",
            "haan", "haan run karo", "chalao", "kar do", "haan please",
            "नहीं", "ना", "मत करो", "कैंसिल", "रद्द करो", "रोको",
            "nahi", "nahin", "mat karo", "cancel karo", "rok do"
        )
        for (phrase in nonEnglishConfirmations) {
            val res = confirmationResolver.resolve(phrase)
            assertTrue("Non-English confirmation '$phrase' must be rejected as UNKNOWN in English-only mode",
                res == ConfirmationIntent.UNKNOWN || EnglishSpeechValidator.isNonEnglishOrUnreliable(phrase))
        }
    }

    @Test
    fun testMultilingualStopListeningCommands() {
        val stopPhrases = listOf(
            "stop listening", "stop voice assistant", "turn off listening"
        )
        for (phrase in stopPhrases) {
            assertTrue("Phrase '$phrase' must be recognized as stop listening", StopListeningResolver.isStopListening(phrase))
        }
    }

    @Test
    fun testFullMultilingualContinuousSessionWithConfirmationAndResume() = runBlocking {
        startTestContinuousListening()
        fakeActionExecutor.executedActions.clear()
        persistenceRepo.saveWorkflow(createSampleChangeWallpaperWorkflow())

        // 1. Say "Hello, change my wallpaper" -> Awaits confirmation
        controller.processCommand("Hello, change my wallpaper")
        assertTrue("UI state must be ConfirmationRequired", controller.uiState.value is AssistantUiState.ConfirmationRequired)

        // 2. Say English confirmation "yes"
        controller.processCommand("yes")
        controller.lastActionJob?.join()
        assertEquals("Wallpaper workflow must be executed", 1, fakeEngine.executeWorkflowCallCount)

        // 3. Must automatically resume continuous listening
        kotlinx.coroutines.delay(60L)
        assertTrue("Continuous listening must resume automatically", continuousVoiceController.isContinuousListening.value)

        // 4. Say next command "Open Settings" without reopening mic
        controller.processCommand("Open Settings")
        controller.lastActionJob?.join()
        assertEquals("Direct action must be executed", 1, fakeActionExecutor.executedActions.size)
        assertEquals(CommandIntent.OPEN_SETTINGS, fakeActionExecutor.executedActions[0])

        // 5. Say "stop listening" (Stop listening)
        continuousVoiceController.handleRecognizedSpeech("stop listening")
        assertFalse("Continuous listening must stop cleanly", continuousVoiceController.isContinuousListening.value)
    }
}

