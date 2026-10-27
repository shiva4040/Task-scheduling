package com.taskscheduling.app

import com.taskscheduling.app.assistant.AssistantUiState
import com.taskscheduling.app.assistant.CommandIntent
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
import com.taskscheduling.app.voice.ContinuousListeningState
import com.taskscheduling.app.voice.ContinuousVoiceController
import com.taskscheduling.app.voice.EnglishSpeechValidator
import com.taskscheduling.app.voice.LanguageMode
import com.taskscheduling.app.voice.SpeechEngineError
import com.taskscheduling.app.voice.SpeechEngineListener
import com.taskscheduling.app.voice.SpeechEngineType
import com.taskscheduling.app.voice.SpeechRecognitionEngine
import com.taskscheduling.app.voice.VoiceInputCallback
import com.taskscheduling.app.voice.VoiceInputManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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

/**
 * Rigorous integration test verifying:
 * 1. Strict English-only speech recognition and rejection of non-English / Hindi input.
 * 2. Thinking status state mapping and lifecycle (UTTERANCE_FINALIZING -> TRANSCRIBING -> COMMAND_PROCESSING -> EXECUTING).
 * 3. Mute/unmute behaviour without canceling in-flight speech processing or workflows.
 * 4. Stop listening without canceling pending workflows.
 * 5. Resume on unmute without restarting explicitly stopped sessions.
 */
class StrictEnglishAndThinkingStatusTest {

    private lateinit var controller: OfflineAssistantController
    private lateinit var fakeEngine: TestAutomationEngine
    private lateinit var fakeSpeechEngine: MockContinuousSpeechEngine
    private lateinit var voiceInputManager: VoiceInputManager
    private lateinit var continuousController: ContinuousVoiceController
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

    private class TestAutomationEngine : AutomationEngine {
        var executeCallCount = 0
        var lastExecutedWorkflow: Workflow? = null

        private val _session = MutableStateFlow(
            ExecutionSession(
                taskId = "",
                taskTitle = "",
                state = ExecutionState.IDLE
            )
        )
        override val executionSession: StateFlow<ExecutionSession> = _session

        override suspend fun executeWorkflow(workflow: Workflow, speedMultiplier: Float) {
            executeCallCount++
            lastExecutedWorkflow = workflow
            _session.value = ExecutionSession(
                taskId = workflow.id,
                taskTitle = workflow.name,
                state = ExecutionState.COMPLETED,
                currentStepIndex = workflow.steps.size,
                totalSteps = workflow.steps.size
            )
        }

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

    class MockContinuousSpeechEngine : SpeechRecognitionEngine {
        override val engineType = SpeechEngineType.WHISPER_OFFLINE
        override var isModelLoaded = true
        override var isRunning = false
        override val configuredSampleRate = 16000
        override val activeAudioSource = "VOICE_RECOGNITION"
        override val configuredBufferSize = 2048
        override var isMuted = false

        var activeListener: SpeechEngineListener? = null
        var isContinuousMode = false

        override suspend fun initialize(): Result<Unit> {
            isModelLoaded = true
            return Result.success(Unit)
        }

        override fun startListening(listener: SpeechEngineListener, continuous: Boolean): Boolean {
            isRunning = true
            isContinuousMode = continuous
            activeListener = listener
            listener.onReady()
            return true
        }

        override fun pauseListening() {}
        override fun resumeListening() {}
        override fun stopListening() {
            isRunning = false
            activeListener?.onStopped()
        }
        override fun cancelListening() {
            isRunning = false
            activeListener?.onStopped()
        }
        override fun release() {
            isRunning = false
            isModelLoaded = false
        }
    }

    @Before
    fun setUp() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "strict_en_test_${UUID.randomUUID()}").apply { mkdirs() }
        val persistenceRepo = WorkflowPersistenceRepository(tempDir)

        val wallpaperWf = Workflow(
            id = "wf_wallpaper_en",
            name = "Change Wallpaper",
            description = "Changes current device wallpaper",
            status = WorkflowStatus.CALIBRATED,
            deviceProfile = testDeviceProfile,
            steps = listOf(
                WorkflowStep(
                    id = "step_1",
                    order = 1,
                    type = ActionType.TAP,
                    label = "Wallpaper & style",
                    startX = 500,
                    startY = 600
                )
            )
        )
        runBlocking {
            persistenceRepo.saveWorkflow(wallpaperWf)
        }

        val workflowResolver = LocalWorkflowResolver(persistenceRepo)
        val commandResolver = LocalCommandResolver()
        val commandProcessor = LocalCommandProcessor(commandResolver, workflowResolver)
        fakeEngine = TestAutomationEngine()

        controller = OfflineAssistantController(
            commandResolver = commandResolver,
            workflowResolver = workflowResolver,
            commandProcessor = commandProcessor,
            confirmationResolver = LocalConfirmationResolver(),
            automationEngine = fakeEngine,
            deviceProfileProvider = { testDeviceProfile },
            coroutineScope = CoroutineScope(Dispatchers.Unconfined)
        )
        controller.autoResumeDelayMs = 0L

        fakeSpeechEngine = MockContinuousSpeechEngine()
        voiceInputManager = VoiceInputManager(
            context = null,
            speechEngine = fakeSpeechEngine,
            scope = CoroutineScope(Dispatchers.Unconfined),
            permissionChecker = { true }
        )

        continuousController = ContinuousVoiceController(
            voiceInputManager = voiceInputManager,
            assistantController = controller,
            coroutineScope = CoroutineScope(Dispatchers.Unconfined)
        )
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    // -------------------------------------------------------------------------
    // PART 1: Strict English-Only Speech Recognition & Non-English Handling
    // -------------------------------------------------------------------------

    @Test
    fun testLanguageModeExplicitlyEnforcesEnglish() {
        assertEquals("English code must be 'en'", "en", LanguageMode.ENGLISH.code)
        assertEquals("VoiceInputManager active mode must be ENGLISH", LanguageMode.ENGLISH, voiceInputManager.activeLanguageMode)

        // Ensure attempting to set other modes is clamped to ENGLISH
        voiceInputManager.setLanguageMode(LanguageMode.HINDI)
        assertEquals("Clamped to ENGLISH", LanguageMode.ENGLISH, voiceInputManager.activeLanguageMode)
    }

    @Test
    fun testNonEnglishHindiUtteranceRejectedWithEnglishMessage() = runBlocking {
        controller.autoResumeDelayMs = 5000L
        continuousController.startContinuousListening()

        // User speaks Hindi: "मेरा वॉलपेपर बदल दो"
        continuousController.handleRecognizedSpeech("मेरा वॉलपेपर बदल दो")

        // Must reject speech and show English rejection message
        val uiState = controller.uiState.value
        assertTrue("UI state must be Error", uiState is AssistantUiState.Error)
        assertEquals(
            "Rejection message must strictly match English-only guidance",
            EnglishSpeechValidator.REJECTION_MESSAGE,
            (uiState as AssistantUiState.Error).message
        )
        // Zero actions or workflows executed
        assertEquals("Zero workflows must be executed from non-English speech", 0, fakeEngine.executeCallCount)
        assertNull("Pending action must not be set", controller.pendingWorkflow)
    }

    @Test
    fun testHinglishUtteranceRejectedAndDoesNotExecuteCommand() = runBlocking {
        controller.autoResumeDelayMs = 5000L
        continuousController.startContinuousListening()

        // User speaks Hinglish: "mera wallpaper badal do"
        continuousController.handleRecognizedSpeech("mera wallpaper badal do")

        // Must reject speech with English rejection message
        val uiState = controller.uiState.value
        assertTrue("UI state must be Error", uiState is AssistantUiState.Error)
        assertEquals(EnglishSpeechValidator.REJECTION_MESSAGE, (uiState as AssistantUiState.Error).message)
        assertEquals("Zero workflows must be executed from Hinglish speech", 0, fakeEngine.executeCallCount)
    }

    @Test
    fun testValidEnglishUtteranceAcceptedAndResolvesCommand() = runBlocking {
        continuousController.startContinuousListening()

        // User speaks valid English: "Change my wallpaper"
        continuousController.handleRecognizedSpeech("Change my wallpaper")

        // Must require confirmation in English
        val uiState = controller.uiState.value
        assertTrue("UI state must be ConfirmationRequired", uiState is AssistantUiState.ConfirmationRequired)
        assertNotNull("Pending workflow must be set", controller.pendingWorkflow)
    }

    // -------------------------------------------------------------------------
    // PART 2: Thinking Status State Mapping & Processing Lifecycle
    // -------------------------------------------------------------------------

    @Test
    fun testThinkingStatusLifecycleTransitions() = runBlocking {
        continuousController.startContinuousListening()
        assertEquals("State must be LISTENING", ContinuousListeningState.LISTENING, continuousController.continuousState.value)
        assertEquals("Status label must be Listening…", "Listening…", continuousController.continuousState.value.statusLabel)

        // 1. Speech detected
        fakeSpeechEngine.activeListener?.onBeginningOfSpeech()
        assertEquals("State must be SPEECH_DETECTED", ContinuousListeningState.SPEECH_DETECTED, continuousController.continuousState.value)
        assertEquals("Status label must be Listening…", "Listening…", continuousController.continuousState.value.statusLabel)

        // 2. Pause detected (Utterance finalizing)
        fakeSpeechEngine.activeListener?.onUtteranceFinalizing()
        assertEquals("State must be UTTERANCE_FINALIZING", ContinuousListeningState.UTTERANCE_FINALIZING, continuousController.continuousState.value)
        assertEquals("Status label must be Processing speech…", "Processing speech…", continuousController.continuousState.value.statusLabel)

        // 3. Whisper Transcribing
        continuousController.handleRecognizedSpeech("Change my wallpaper")
        assertTrue("UI state must be ConfirmationRequired", controller.uiState.value is AssistantUiState.ConfirmationRequired)

        // 4. Confirm with "yes" -> Action executes
        continuousController.handleRecognizedSpeech("yes")
        assertEquals("Workflow must execute", 1, fakeEngine.executeCallCount)
    }

    // -------------------------------------------------------------------------
    // PART 4: Mute/Unmute Without Canceling In-Flight Processing
    // -------------------------------------------------------------------------

    @Test
    fun testMutingDoesNotCancelInFlightSpeechTranscriptionOrWorkflow() = runBlocking {
        continuousController.startContinuousListening()
        assertTrue("Continuous listening active", continuousController.isContinuousListening.value)

        // Utterance finalizing
        fakeSpeechEngine.activeListener?.onUtteranceFinalizing()
        assertEquals(ContinuousListeningState.UTTERANCE_FINALIZING, continuousController.continuousState.value)

        // User mutes microphone while speech is being processed
        continuousController.mute()
        assertTrue("Speech engine is muted", fakeSpeechEngine.isMuted)
        assertTrue("Continuous controller is muted", continuousController.isMuted.value)

        // Already-submitted utterance finishes and dispatches transcript
        continuousController.handleRecognizedSpeech("Change my wallpaper")

        // Confirmation is presented even though mic is now muted
        assertTrue("Confirmation required for already finalized speech", controller.uiState.value is AssistantUiState.ConfirmationRequired)
        assertNotNull("Pending workflow preserved", controller.pendingWorkflow)

        // User confirms (e.g. via tap or action)
        controller.confirmPendingAction()
        assertEquals("Workflow executed successfully even though mic is muted", 1, fakeEngine.executeCallCount)
    }

    @Test
    fun testUnmuteResumesListeningIfContinuousModeEnabled() = runBlocking {
        continuousController.startContinuousListening()
        assertTrue("Continuous listening active", continuousController.isContinuousListening.value)

        // Mute
        continuousController.mute()
        assertTrue("Muted", continuousController.isMuted.value)
        assertEquals(ContinuousListeningState.MUTED, continuousController.continuousState.value)

        // Unmute -> Automatically resumes listening without pressing start again
        continuousController.unmute()
        assertFalse("Unmuted", continuousController.isMuted.value)
        assertEquals("State must return to LISTENING", ContinuousListeningState.LISTENING, continuousController.continuousState.value)
        assertTrue("Speech engine is recording", fakeSpeechEngine.isRunning)
    }

    @Test
    fun testUnmuteDoesNotSilentlyRestartExplicitlyStoppedSession() {
        // Explicitly stopped session
        continuousController.stopContinuousListening()
        assertFalse("Continuous listening inactive", continuousController.isContinuousListening.value)
        assertEquals(ContinuousListeningState.STOPPED, continuousController.continuousState.value)

        // Unmute alone must NOT restart listening session
        continuousController.unmute()
        assertFalse("Continuous listening must NOT start silently on unmute", continuousController.isContinuousListening.value)
        assertEquals("Continuous state must remain STOPPED", ContinuousListeningState.STOPPED, continuousController.continuousState.value)
        assertFalse("Speech engine must remain OFF", fakeSpeechEngine.isRunning)
    }

    @Test
    fun testStoppingListeningPreservesInFlightWorkflowExecution() = runBlocking {
        continuousController.startContinuousListening()

        // Start workflow
        controller.processCommand("Change my wallpaper")
        controller.confirmPendingAction()
        assertEquals(1, fakeEngine.executeCallCount)

        // Tapping stop listening pauses microphone but does not cancel already executing workflow
        continuousController.stopContinuousListening()
        assertFalse("Continuous listening stopped", continuousController.isContinuousListening.value)
        assertEquals("Fake engine workflow call count remains 1", 1, fakeEngine.executeCallCount)
    }
}
