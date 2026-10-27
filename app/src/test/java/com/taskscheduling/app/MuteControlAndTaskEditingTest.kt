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
import com.taskscheduling.app.data.repository.TaskRepositoryImpl
import com.taskscheduling.app.data.repository.WorkflowPersistenceRepository
import com.taskscheduling.app.domain.model.ActionType
import com.taskscheduling.app.domain.model.CoordinateAction
import com.taskscheduling.app.domain.model.DeviceProfile
import com.taskscheduling.app.domain.model.ExecutionSession
import com.taskscheduling.app.domain.model.ExecutionState
import com.taskscheduling.app.domain.model.Task
import com.taskscheduling.app.domain.model.TaskCategory
import com.taskscheduling.app.domain.model.Workflow
import com.taskscheduling.app.domain.model.WorkflowStatus
import com.taskscheduling.app.domain.model.WorkflowStep
import com.taskscheduling.app.feature.builder.BuilderViewModel
import com.taskscheduling.app.voice.ContinuousListeningState
import com.taskscheduling.app.voice.ContinuousVoiceController
import com.taskscheduling.app.voice.SpeechEngineError
import com.taskscheduling.app.voice.SpeechEngineListener
import com.taskscheduling.app.voice.SpeechEngineType
import com.taskscheduling.app.voice.SpeechRecognitionEngine
import com.taskscheduling.app.voice.VoiceInputManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.UUID

/**
 * Test Suite verifying:
 * 1. PART 1: Floating Assistant Mute/Unmute state machine, speech suppression, and resume behavior.
 * 2. PART 3: BuilderViewModel editing of task metadata, action addition, deletion, duplication, and reordering.
 * 3. PART 4: Coordinate recalibration and validation for single actions on real device screen coordinates.
 * 4. PART 6: Workflow persistence and task sync across Featured, Favorite, and All tasks.
 */
class MuteControlAndTaskEditingTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var controller: OfflineAssistantController
    private lateinit var fakeSpeechEngine: MockContinuousSpeechEngine
    private lateinit var voiceInputManager: VoiceInputManager
    private lateinit var continuousController: ContinuousVoiceController
    private lateinit var taskRepository: TaskRepositoryImpl
    private lateinit var workflowRepository: WorkflowPersistenceRepository
    private lateinit var builderViewModel: BuilderViewModel

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
        private val _session = MutableStateFlow(
            ExecutionSession(
                taskId = "",
                taskTitle = "",
                state = ExecutionState.IDLE
            )
        )
        override val executionSession: StateFlow<ExecutionSession> = _session

        override suspend fun executeWorkflow(workflow: Workflow, speedMultiplier: Float) {
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
        var transcribedCount = 0

        override suspend fun initialize(): Result<Unit> {
            isModelLoaded = true
            return Result.success(Unit)
        }

        override fun startListening(listener: SpeechEngineListener, continuous: Boolean): Boolean {
            isRunning = true
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
            if (isMuted) return
            transcribedCount++
            activeListener?.onBeginningOfSpeech()
            activeListener?.onResult(text)
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Before
    fun setUp() {
        kotlinx.coroutines.Dispatchers.setMain(kotlinx.coroutines.test.UnconfinedTestDispatcher())
        val baseDir = tempFolder.newFolder("test_wf_${UUID.randomUUID()}")
        workflowRepository = WorkflowPersistenceRepository(baseDir)
        taskRepository = TaskRepositoryImpl(workflowPersistence = workflowRepository)

        val workflowResolver = LocalWorkflowResolver(workflowRepository)
        val commandResolver = LocalCommandResolver()
        val commandProcessor = LocalCommandProcessor(commandResolver, workflowResolver)
        val fakeEngine = TestAutomationEngine()

        controller = OfflineAssistantController(
            commandResolver = commandResolver,
            workflowResolver = workflowResolver,
            commandProcessor = commandProcessor,
            confirmationResolver = LocalConfirmationResolver(),
            automationEngine = fakeEngine,
            taskRepository = taskRepository,
            persistenceRepository = workflowRepository,
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

        builderViewModel = BuilderViewModel(
            taskRepository = taskRepository
        )
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @After
    fun tearDown() {
        continuousController.stopContinuousListening()
        voiceInputManager.release()
        try {
            kotlinx.coroutines.Dispatchers.resetMain()
        } catch (_: Throwable) {}
    }

    // =========================================================================
    // PART 1: MUTE / UNMUTE TESTS
    // =========================================================================

    @Test
    fun testMuteStateToggling() {
        assertFalse("Initially should not be muted", continuousController.isMuted.value)

        continuousController.mute()
        assertTrue("isMuted should be true after mute()", continuousController.isMuted.value)
        assertTrue("Speech engine should reflect muted state", fakeSpeechEngine.isMuted)

        continuousController.unmute()
        assertFalse("isMuted should be false after unmute()", continuousController.isMuted.value)
        assertFalse("Speech engine should reflect unmuted state", fakeSpeechEngine.isMuted)

        continuousController.toggleMute()
        assertTrue("toggleMute should switch to muted", continuousController.isMuted.value)

        continuousController.toggleMute()
        assertFalse("toggleMute should switch back to unmuted", continuousController.isMuted.value)
    }

    @Test
    fun testMuteSuppressesAudioAndSpeechProcessingDuringContinuousSession() {
        continuousController.startContinuousListening()
        assertEquals(ContinuousListeningState.LISTENING, continuousController.continuousState.value)

        // Mute
        continuousController.mute()
        assertEquals(ContinuousListeningState.MUTED, continuousController.continuousState.value)
        assertTrue(continuousController.isContinuousListening.value)

        // Attempt speech while muted
        fakeSpeechEngine.simulateSpeech("Change my wallpaper")

        // Speech must be completely dropped
        assertEquals("No transcription should have completed while muted", 0, fakeSpeechEngine.transcribedCount)
        assertFalse(controller.uiState.value is AssistantUiState.ConfirmationRequired)

        // Unmute
        continuousController.unmute()
        assertEquals(ContinuousListeningState.LISTENING, continuousController.continuousState.value)

        // Now speech should be processed normally
        fakeSpeechEngine.simulateSpeech("Change my wallpaper")
        assertEquals(1, fakeSpeechEngine.transcribedCount)
    }

    @Test
    fun testStoppingContinuousListeningIsDistinctFromMuting() {
        continuousController.startContinuousListening()
        continuousController.mute()

        assertTrue("Still in continuous listening session", continuousController.isContinuousListening.value)
        assertTrue("Muted is true", continuousController.isMuted.value)

        // Stop session explicitly
        continuousController.stopContinuousListening()
        assertFalse("Continuous listening should be stopped", continuousController.isContinuousListening.value)
        assertEquals(ContinuousListeningState.IDLE, continuousController.continuousState.value)
    }

    // =========================================================================
    // PART 3 & 4: TASK & ACTION EDITING + COORDINATE RECALIBRATION
    // =========================================================================

    @Test
    fun testCreateAndEditTaskMetadataAndActions() = runBlocking {
        // Create an initial task in repository
        val taskId = "task_test_edit"
        val initialWorkflow = Workflow(
            id = "wf_$taskId",
            name = "Initial Task",
            description = "Initial Description",
            steps = listOf(
                WorkflowStep(
                    id = "step_1",
                    stepNumber = 1,
                    label = "Initial Step",
                    actionType = ActionType.TAP,
                    coordinate = CoordinateAction(100, 200),
                    startX = 100,
                    startY = 200,
                    durationMs = 400L,
                    explanation = "Initial step"
                )
            ),
            status = WorkflowStatus.NOT_CALIBRATED
        )
        val initialTask = Task(
            id = taskId,
            title = "Initial Task",
            description = "Initial Description",
            category = TaskCategory.CUSTOM,
            workflow = initialWorkflow
        )
        taskRepository.saveTask(initialTask)

        // Load into BuilderViewModel
        builderViewModel.loadTask(taskId)
        assertEquals("Initial Task", builderViewModel.uiState.value.task?.title)

        // Edit metadata
        builderViewModel.updateTaskDetails("Open YouTube and Search", "Automated YouTube navigation")
        assertEquals("Open YouTube and Search", builderViewModel.uiState.value.task?.title)
        assertEquals("Automated YouTube navigation", builderViewModel.uiState.value.task?.description)

        // Add actions
        builderViewModel.addNewStepWithType(ActionType.TAP)
        builderViewModel.addNewStepWithType(ActionType.WAIT)
        builderViewModel.addNewStepWithType(ActionType.SWIPE)
        builderViewModel.addNewStepWithType(ActionType.BACK)

        val steps = builderViewModel.uiState.value.task?.workflow?.steps ?: emptyList()
        assertEquals(5, steps.size)
        assertEquals(ActionType.TAP, steps[0].actionType)
        assertEquals(ActionType.TAP, steps[1].actionType)
        assertEquals(ActionType.WAIT, steps[2].actionType)
        assertEquals(ActionType.SWIPE, steps[3].actionType)
        assertEquals(ActionType.BACK, steps[4].actionType)

        // Recalibrate coordinates for Tap action (Step 1)
        builderViewModel.recalibrateStepCoordinates(
            index = 1,
            startX = 540,
            startY = 960,
            endX = null,
            endY = null,
            durationMs = 400L
        )

        val updatedTapStep = builderViewModel.uiState.value.task!!.workflow.steps[1]
        assertEquals(540, updatedTapStep.startX)
        assertEquals(960, updatedTapStep.startY)
        assertEquals(540, updatedTapStep.coordinate?.x)
        assertEquals(960, updatedTapStep.coordinate?.y)

        // Recalibrate coordinates for Swipe action (Step 3)
        builderViewModel.recalibrateStepCoordinates(
            index = 3,
            startX = 600,
            startY = 1500,
            endX = 600,
            endY = 400,
            durationMs = 450L
        )

        val updatedSwipeStep = builderViewModel.uiState.value.task!!.workflow.steps[3]
        assertEquals(600, updatedSwipeStep.startX)
        assertEquals(1500, updatedSwipeStep.startY)
        assertEquals(600, updatedSwipeStep.endX)
        assertEquals(400, updatedSwipeStep.endY)
        assertEquals(450L, updatedSwipeStep.durationMs)

        // Reorder actions: Move Wait (index 2) down
        builderViewModel.moveStepDown(2)
        val reorderedSteps = builderViewModel.uiState.value.task!!.workflow.steps
        assertEquals(ActionType.SWIPE, reorderedSteps[2].actionType)
        assertEquals(ActionType.WAIT, reorderedSteps[3].actionType)

        // Select and duplicate Tap step (index 1)
        builderViewModel.selectStep(1)
        builderViewModel.duplicateSelectedStep()
        assertEquals(6, builderViewModel.uiState.value.task!!.workflow.steps.size)
        assertTrue(builderViewModel.uiState.value.task!!.workflow.steps[2].label.contains("Copy"))

        // Delete duplicated step
        builderViewModel.selectStep(2)
        builderViewModel.deleteSelectedStep()
        assertEquals(5, builderViewModel.uiState.value.task!!.workflow.steps.size)

        // Save explicitly
        var savedSuccessfully = false
        val latch = java.util.concurrent.CountDownLatch(1)
        builderViewModel.saveTaskExplicitly {
            savedSuccessfully = true
            latch.countDown()
        }
        latch.await(3, java.util.concurrent.TimeUnit.SECONDS)
        assertTrue("Task should be saved successfully", savedSuccessfully)

        // Verify task repository sync
        val savedTask = taskRepository.getTaskById(taskId)
        assertNotNull(savedTask)
        assertEquals("Open YouTube and Search", savedTask?.title)
        assertEquals(5, savedTask?.workflow?.steps?.size)
    }

    @Test
    fun testExistingWorkflowCanBeEditedFromAnyCategory() = runBlocking {
        // Pre-populate a saved task and workflow
        val taskId = "featured_task_123"
        val originalWorkflow = Workflow(
            id = "wf_$taskId",
            name = "Original Task",
            description = "Original Description",
            steps = listOf(
                WorkflowStep(
                    id = "step-1",
                    stepNumber = 1,
                    label = "Tap Settings",
                    actionType = ActionType.TAP,
                    coordinate = CoordinateAction(100, 200),
                    startX = 100,
                    startY = 200,
                    durationMs = 400L,
                    explanation = "Tap Settings"
                )
            ),
            status = WorkflowStatus.CALIBRATED
        )
        val task = Task(
            id = taskId,
            title = "Original Task",
            description = "Original Description",
            category = TaskCategory.SETTINGS,
            isFavorite = true,
            workflow = originalWorkflow
        )
        taskRepository.saveTask(task)

        // Load into BuilderViewModel as if opened from Featured or Favorite
        builderViewModel.loadTask(taskId)
        assertEquals("Original Task", builderViewModel.uiState.value.task?.title)

        // Edit task name and calibrate new coordinates
        builderViewModel.updateTaskDetails("Modified Task", "Modified Description")
        builderViewModel.recalibrateStepCoordinates(
            index = 0,
            startX = 800,
            startY = 1200,
            endX = null,
            endY = null,
            durationMs = 500L
        )

        val latch = java.util.concurrent.CountDownLatch(1)
        builderViewModel.saveTaskExplicitly {
            latch.countDown()
        }
        latch.await(3, java.util.concurrent.TimeUnit.SECONDS)

        // Verify that task repository is updated with the EXACT same ID and data
        val persistedTask = taskRepository.getTaskById(taskId)

        assertNotNull(persistedTask)
        assertEquals("Modified Task", persistedTask?.title)
        assertEquals("Modified Description", persistedTask?.description)
        assertEquals(800, persistedTask?.workflow?.steps?.first()?.startX)
        assertEquals(1200, persistedTask?.workflow?.steps?.first()?.startY)
        assertEquals(800, persistedTask?.workflow?.steps?.first()?.coordinate?.x)
        assertEquals(1200, persistedTask?.workflow?.steps?.first()?.coordinate?.y)
        assertTrue(persistedTask?.isFavorite == true) // Preserved category/favorite flags
    }
}
