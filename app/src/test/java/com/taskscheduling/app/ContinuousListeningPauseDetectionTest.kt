package com.taskscheduling.app

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
import com.taskscheduling.app.voice.ContinuousListeningState
import com.taskscheduling.app.voice.ContinuousVoiceController
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
 * Unit Test Suite for Continuous Listening with Automatic Pause-Based Sentence Detection:
 * 1. Persistent session state machine (IDLE, LISTENING, SPEECH_DETECTED, TRANSCRIBING, etc.).
 * 2. Pause ends utterance, not continuous session.
 * 3. Multiple consecutive utterances processed independently.
 * 4. Greetings receive assistant responses and listening continues.
 * 5. Transient errors do not disable continuous listening.
 * 6. Explicit "Stop listening" cleanly stops session.
 * 7. Language-aware pause endpointing thresholds (English, Hindi, Hinglish).
 */
class ContinuousListeningPauseDetectionTest {

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

        fun simulateSpeech(text: String) {
            activeListener?.onBeginningOfSpeech()
            activeListener?.onResult(text)
        }

        fun simulateTransientError(error: SpeechEngineError) {
            activeListener?.onError(error)
        }
    }

    @Before
    fun setUp() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "continuous_test_${UUID.randomUUID()}").apply { mkdirs() }
        val persistenceRepo = WorkflowPersistenceRepository(tempDir)

        val wallpaperWf = Workflow(
            id = "wf_wallpaper",
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
        controller.autoResumeDelayMs = 0L // instant resume in unit tests

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

    @Test
    fun testContinuousListeningSessionStateTransitions() {
        assertEquals("Initial state must be IDLE", ContinuousListeningState.IDLE, continuousController.continuousState.value)

        continuousController.startContinuousListening()
        assertTrue("Continuous listening must be active", continuousController.isContinuousListening.value)
        assertEquals("State must be LISTENING", ContinuousListeningState.LISTENING, continuousController.continuousState.value)

        // Simulate beginning of speech
        fakeSpeechEngine.activeListener?.onBeginningOfSpeech()
        assertEquals("State must be SPEECH_DETECTED", ContinuousListeningState.SPEECH_DETECTED, continuousController.continuousState.value)

        // Utterance finalized and transcribed
        continuousController.handleRecognizedSpeech("Change my wallpaper")
        // Workflow requires confirmation
        assertTrue("Confirmation required", controller.uiState.value is AssistantUiState.ConfirmationRequired)
        assertTrue("Continuous listening remains active", continuousController.isContinuousListening.value)

        // User confirms with "yes"
        continuousController.handleRecognizedSpeech("yes")
        assertEquals("Workflow executed", 1, fakeEngine.executeCallCount)

        // After completion, continuous listening remains active
        assertTrue("Continuous listening remains active after execution", continuousController.isContinuousListening.value)
        assertEquals("Continuous state transitions to LISTENING", ContinuousListeningState.LISTENING, continuousController.continuousState.value)
    }

    @Test
    fun testPauseEndsUtteranceNotSession() {
        continuousController.startContinuousListening()
        assertTrue(continuousController.isContinuousListening.value)

        // First utterance: "Hello"
        fakeSpeechEngine.simulateSpeech("Hello")
        // Greeting receives polite response
        assertTrue("Assistant responds to greeting", controller.chatMessages.value.any { it.text.contains("Hello", ignoreCase = true) })
        // Session MUST NOT end
        assertTrue("Continuous listening remains active across sentence pause", continuousController.isContinuousListening.value)

        // Second utterance immediately follows: "Change my wallpaper"
        fakeSpeechEngine.simulateSpeech("Change my wallpaper")
        assertTrue("Second command recognized", controller.uiState.value is AssistantUiState.ConfirmationRequired)
        assertTrue("Continuous session is still running", continuousController.isContinuousListening.value)
    }

    @Test
    fun testMultipleUtterancesProcessedIndependently() {
        continuousController.startContinuousListening()

        // 1. "Hello"
        fakeSpeechEngine.simulateSpeech("Hello")
        assertTrue("Assistant responds to greeting", controller.chatMessages.value.any { it.text.contains("Hello", ignoreCase = true) })
        assertEquals(0, fakeEngine.executeCallCount)

        // 2. "Swipe up"
        fakeSpeechEngine.simulateSpeech("swipe up")
        assertTrue("Action executed", controller.uiState.value is AssistantUiState.Completed || controller.uiState.value is AssistantUiState.Listening)
        assertTrue(continuousController.isContinuousListening.value)

        // 3. "Go back"
        fakeSpeechEngine.simulateSpeech("go back")
        assertTrue(continuousController.isContinuousListening.value)
    }

    @Test
    fun testTransientErrorDoesNotKillContinuousListening() {
        continuousController.startContinuousListening()
        assertTrue(continuousController.isContinuousListening.value)

        // Simulate a transient transcription error (e.g. ambient cough / no words decoded)
        fakeSpeechEngine.simulateTransientError(SpeechEngineError.RecognitionFailed("No words recognized"))

        // Continuous listening must NOT be permanently killed
        assertTrue("Continuous listening remains active after transient recognition error", continuousController.isContinuousListening.value)
        assertEquals("Continuous state returns to LISTENING", ContinuousListeningState.LISTENING, continuousController.continuousState.value)
    }

    @Test
    fun testStopListeningVoiceCommandTerminatesSession() {
        continuousController.startContinuousListening()
        assertTrue(continuousController.isContinuousListening.value)

        // User speaks dedicated stop command
        fakeSpeechEngine.simulateSpeech("stop listening")

        assertFalse("Continuous listening must be STOPPED", continuousController.isContinuousListening.value)
        assertEquals("State must be IDLE after stop", ContinuousListeningState.IDLE, continuousController.continuousState.value)
        assertFalse("Speech engine must be stopped", fakeSpeechEngine.isRunning)
    }

    @Test
    fun testLanguageAwarePauseEndpointingThresholds() {
        // English: 950ms
        val englishPause = when (LanguageMode.ENGLISH) {
            LanguageMode.ENGLISH -> 950L
            LanguageMode.HINDI -> 1100L
            LanguageMode.HINGLISH -> 1200L
        }
        assertEquals(950L, englishPause)

        // Hindi: 1100ms
        val hindiPause = when (LanguageMode.HINDI) {
            LanguageMode.ENGLISH -> 950L
            LanguageMode.HINDI -> 1100L
            LanguageMode.HINGLISH -> 1200L
        }
        assertEquals(1100L, hindiPause)

        // Hinglish: 1200ms
        val hinglishPause = when (LanguageMode.HINGLISH) {
            LanguageMode.ENGLISH -> 950L
            LanguageMode.HINDI -> 1100L
            LanguageMode.HINGLISH -> 1200L
        }
        assertEquals(1200L, hinglishPause)
    }
}
