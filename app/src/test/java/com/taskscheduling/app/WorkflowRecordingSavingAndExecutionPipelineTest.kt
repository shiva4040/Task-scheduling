package com.taskscheduling.app

import com.taskscheduling.app.data.repository.TaskPersistenceRepository
import com.taskscheduling.app.data.repository.TaskRepositoryImpl
import com.taskscheduling.app.data.repository.WorkflowPersistenceRepository
import com.taskscheduling.app.domain.model.ActionType
import com.taskscheduling.app.domain.model.CoordinateAction
import com.taskscheduling.app.domain.model.DeviceProfile
import com.taskscheduling.app.domain.model.ExecutionLogEntry
import com.taskscheduling.app.domain.model.ExecutionSession
import com.taskscheduling.app.domain.model.ExecutionState
import com.taskscheduling.app.domain.model.SessionState
import com.taskscheduling.app.domain.model.Task
import com.taskscheduling.app.domain.model.TaskCategory
import com.taskscheduling.app.domain.model.TaskPriority
import com.taskscheduling.app.domain.model.TaskStatus
import com.taskscheduling.app.domain.model.TaskType
import com.taskscheduling.app.domain.model.Workflow
import com.taskscheduling.app.domain.model.WorkflowStep
import com.taskscheduling.app.domain.validation.WorkflowValidationResult
import com.taskscheduling.app.domain.validation.WorkflowValidator
import com.taskscheduling.app.feature.execution.ExecutionViewModel
import com.taskscheduling.app.recording.CoordinateValidator
import com.taskscheduling.app.recording.RecordingManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.UUID

/**
 * End-to-end unit test suite validating the complete workflow pipeline:
 * Recording Actions -> Saving -> Persistence -> Reloading across Restart -> Task Execution.
 */
class WorkflowRecordingSavingAndExecutionPipelineTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var baseDir: File
    private lateinit var persistenceRepo: WorkflowPersistenceRepository
    private lateinit var taskPersistenceRepo: TaskPersistenceRepository
    private lateinit var taskRepo: TaskRepositoryImpl
    private val testProfile = DeviceProfile(screenWidth = 1080, screenHeight = 2400)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        baseDir = tempFolder.newFolder("pipeline_test_dir")
        persistenceRepo = WorkflowPersistenceRepository(baseDir)
        taskPersistenceRepo = TaskPersistenceRepository(baseDir, persistenceRepo)
        taskRepo = TaskRepositoryImpl(persistenceRepo, taskPersistenceRepo)
        RecordingManager.reset()
        RecordingManager.initRepository(persistenceRepo)
    }

    @After
    fun tearDown() {
        RecordingManager.reset()
        Dispatchers.resetMain()
    }

    // =========================================================================
    // 1. Multi-Step Recording & Sequence Preservation
    // =========================================================================

    @Test
    fun testRecordingMultipleStepsInSequence_allFiveActionsRecordedInOrder() {
        val workflowId = "wf_real_device_settings"
        RecordingManager.startRecording(
            workflowId = workflowId,
            workflowName = "Display Timeout Routine",
            deviceProfile = testProfile
        )

        // 1. Open Settings
        RecordingManager.recordTap(500, 1600, label = "Settings")
        // 2. Tap Display
        RecordingManager.recordTap(500, 600, label = "Display")
        // 3. Tap Screen timeout
        RecordingManager.recordTap(500, 1100, label = "Screen timeout")
        // 4. Select a timeout option (e.g. 5 minutes)
        RecordingManager.recordTap(500, 950, label = "5 minutes")
        // 5. Press Back
        RecordingManager.recordBack()

        val steps = RecordingManager.recordedSteps.value
        assertEquals("Must record exactly 5 steps", 5, steps.size)

        // Check step 1
        assertEquals(ActionType.TAP, steps[0].actionType)
        assertEquals(1, steps[0].order)
        assertEquals(500, steps[0].coordinate?.x)
        assertEquals(1600, steps[0].coordinate?.y)
        assertEquals("Settings", steps[0].label)

        // Check step 2
        assertEquals(ActionType.TAP, steps[1].actionType)
        assertEquals(2, steps[1].order)
        assertEquals(500, steps[1].coordinate?.x)
        assertEquals(600, steps[1].coordinate?.y)
        assertEquals("Display", steps[1].label)

        // Check step 3
        assertEquals(ActionType.TAP, steps[2].actionType)
        assertEquals(3, steps[2].order)
        assertEquals(500, steps[2].coordinate?.x)
        assertEquals(1100, steps[2].coordinate?.y)
        assertEquals("Screen timeout", steps[2].label)

        // Check step 4
        assertEquals(ActionType.TAP, steps[3].actionType)
        assertEquals(4, steps[3].order)
        assertEquals(500, steps[3].coordinate?.x)
        assertEquals(950, steps[3].coordinate?.y)
        assertEquals("5 minutes", steps[3].label)

        // Check step 5
        assertEquals(ActionType.BACK, steps[4].actionType)
        assertEquals(5, steps[4].order)

        // Verify all 5 steps have unique non-blank IDs
        val uniqueIds = steps.map { it.id }.toSet()
        assertEquals("Each step must have a unique ID", 5, uniqueIds.size)
        assertTrue(uniqueIds.none { it.isBlank() })
    }

    @Test
    fun testRecordingDragAndScrollAndTextInputAndRecents() {
        RecordingManager.startRecording(
            workflowId = "wf_advanced_actions",
            workflowName = "Advanced Actions Routine",
            deviceProfile = testProfile
        )

        RecordingManager.recordDrag(200, 300, 800, 1200, durationMs = 800L)
        RecordingManager.recordScroll(isUp = true)
        RecordingManager.recordTextInput("Antigravity Search")
        RecordingManager.recordWait(1200L)
        RecordingManager.recordRecents()

        val steps = RecordingManager.recordedSteps.value
        assertEquals(5, steps.size)

        assertEquals(ActionType.DRAG, steps[0].actionType)
        assertEquals(200, steps[0].startX)
        assertEquals(300, steps[0].startY)
        assertEquals(800, steps[0].endX)
        assertEquals(1200, steps[0].endY)

        assertEquals(ActionType.SWIPE, steps[1].actionType)
        assertEquals(ActionType.TEXT_INPUT, steps[2].actionType)
        assertEquals("Antigravity Search", steps[2].textInput)

        assertEquals(ActionType.WAIT, steps[3].actionType)
        assertEquals(1200L, steps[3].duration)

        assertEquals(ActionType.RECENTS, steps[4].actionType)
    }

    @Test
    fun testPreservingStepOrderAndReordering() {
        RecordingManager.startRecording(
            workflowId = "wf_order_test",
            workflowName = "Order Test",
            deviceProfile = testProfile
        )

        RecordingManager.recordTap(100, 100)
        RecordingManager.recordTap(200, 200)
        RecordingManager.recordTap(300, 300)

        val id0 = RecordingManager.recordedSteps.value[0].id
        val id1 = RecordingManager.recordedSteps.value[1].id
        val id2 = RecordingManager.recordedSteps.value[2].id

        // Move first step to the last index (0 -> 2)
        RecordingManager.moveStep(0, 2)

        val steps = RecordingManager.recordedSteps.value
        assertEquals(3, steps.size)
        assertEquals(id1, steps[0].id)
        assertEquals(1, steps[0].order)
        assertEquals(id2, steps[1].id)
        assertEquals(2, steps[1].order)
        assertEquals(id0, steps[2].id)
        assertEquals(3, steps[2].order)
    }

    // =========================================================================
    // 2. Workflow Persistence & Reloading Across App Restart
    // =========================================================================

    @Test
    fun testSavingWorkflow_persistsNameDescriptionAndAllSteps() = runBlocking {
        RecordingManager.startRecording(
            workflowId = "wf_saved_5_steps",
            workflowName = "Saved Routine",
            deviceProfile = testProfile
        )

        RecordingManager.recordTap(100, 200)
        RecordingManager.recordTap(300, 400)
        RecordingManager.recordTap(500, 600)
        RecordingManager.recordTap(700, 800)
        RecordingManager.recordBack()

        val saved = RecordingManager.saveWorkflow(
            name = "Device Automation Routine",
            description = "Runs 5 steps on real device",
            persistenceRepo = persistenceRepo,
            taskRepo = taskRepo
        )

        assertNotNull("Workflow must save successfully", saved)
        assertEquals("Device Automation Routine", saved!!.name)
        assertEquals("Runs 5 steps on real device", saved.description)
        assertEquals(5, saved.steps.size)

        // Verify persistence repository loaded workflow
        val loadedWorkflow = persistenceRepo.getWorkflow(saved.id)
        assertNotNull(loadedWorkflow)
        assertEquals(5, loadedWorkflow!!.steps.size)
        assertEquals(saved.name, loadedWorkflow.name)

        // Verify task repository loaded task
        val loadedTask = taskRepo.getTaskById(saved.id)
        assertNotNull(loadedTask)
        assertEquals(5, loadedTask!!.workflow.steps.size)
        assertEquals(saved.name, loadedTask.title)
    }

    @Test
    fun testUpdatingExistingWorkflow_updatesInsteadOfCreatingDuplicate() = runBlocking {
        val workflowId = "wf_update_test"
        RecordingManager.startRecording(
            workflowId = workflowId,
            workflowName = "Initial Routine",
            deviceProfile = testProfile
        )

        RecordingManager.recordTap(100, 100)
        val initialWorkflow = RecordingManager.saveWorkflow(
            name = "Initial Routine",
            description = "Version 1",
            persistenceRepo = persistenceRepo,
            taskRepo = taskRepo
        )
        assertNotNull(initialWorkflow)

        val countAfterFirstSave = persistenceRepo.getAllWorkflows().size

        // Now resume/edit with an additional step and updated title
        RecordingManager.startRecording(
            workflowId = workflowId,
            workflowName = "Updated Routine",
            deviceProfile = testProfile
        )
        RecordingManager.recordTap(100, 100)
        RecordingManager.recordTap(200, 200)

        val updatedWorkflow = RecordingManager.saveWorkflow(
            name = "Updated Routine",
            description = "Version 2 with 2 steps",
            persistenceRepo = persistenceRepo,
            taskRepo = taskRepo
        )
        assertNotNull(updatedWorkflow)
        assertEquals(workflowId, updatedWorkflow!!.id)

        // Verify no duplicate was created
        val allWorkflows = persistenceRepo.getAllWorkflows()
        assertEquals("Must not create duplicate workflow with same ID", countAfterFirstSave, allWorkflows.size)

        val reloaded = persistenceRepo.getWorkflow(workflowId)
        assertNotNull(reloaded)
        assertEquals("Updated Routine", reloaded!!.name)
        assertEquals(2, reloaded.steps.size)
    }

    @Test
    fun testReopeningSavedWorkflow_afterSimulatedAppRestart() = runBlocking {
        // Phase 1: Record and Save
        val workflowId = "wf_restart_test"
        RecordingManager.startRecording(
            workflowId = workflowId,
            workflowName = "Persistence Across Restart",
            deviceProfile = testProfile
        )

        RecordingManager.recordTap(111, 222, label = "Step 1")
        RecordingManager.recordTap(333, 444, label = "Step 2")
        RecordingManager.recordTap(555, 666, label = "Step 3")
        RecordingManager.recordTap(777, 888, label = "Step 4")
        RecordingManager.recordBack()

        val saved = RecordingManager.saveWorkflow(
            name = "Persistence Across Restart",
            description = "Must survive process death",
            persistenceRepo = persistenceRepo,
            taskRepo = taskRepo
        )
        assertNotNull(saved)

        // Phase 2: Simulate complete process termination & fresh cold startup
        val freshPersistenceRepo = WorkflowPersistenceRepository(baseDir)
        val freshTaskPersistenceRepo = TaskPersistenceRepository(baseDir, freshPersistenceRepo)
        val freshTaskRepo = TaskRepositoryImpl(freshPersistenceRepo, freshTaskPersistenceRepo)

        val reloadedWf = freshPersistenceRepo.getWorkflow(workflowId)
        assertNotNull("Workflow must be loaded from persistent JSON disk", reloadedWf)
        assertEquals("Persistence Across Restart", reloadedWf!!.name)
        assertEquals(5, reloadedWf.steps.size)

        assertEquals("Step 1", reloadedWf.steps[0].label)
        assertEquals(111, reloadedWf.steps[0].coordinate?.x)
        assertEquals(222, reloadedWf.steps[0].coordinate?.y)

        assertEquals("Step 4", reloadedWf.steps[3].label)
        assertEquals(777, reloadedWf.steps[3].coordinate?.x)
        assertEquals(888, reloadedWf.steps[3].coordinate?.y)

        assertEquals(ActionType.BACK, reloadedWf.steps[4].actionType)

        // Check task query
        val reloadedTask = freshTaskRepo.getTaskById(workflowId)
        assertNotNull("Task must also resolve cleanly by workflow ID", reloadedTask)
        assertEquals(5, reloadedTask!!.workflow.steps.size)
    }

    // =========================================================================
    // 3. Execution Pipeline, State Management, and Controls
    // =========================================================================

    /**
     * Test execution engine to verify execution advancement, step count, and playback controls.
     */
    private class TestPipelineEngine : com.taskscheduling.app.automation.AutomationEngine {
        private val _session = MutableStateFlow(
            ExecutionSession(taskId = "", taskTitle = "", state = ExecutionState.IDLE)
        )
        override val executionSession: StateFlow<ExecutionSession> = _session.asStateFlow()

        var executedSteps = mutableListOf<WorkflowStep>()
        var onExecuteWorkflowCalled = false

        override suspend fun executeWorkflow(workflow: Workflow, speedMultiplier: Float) {
            onExecuteWorkflowCalled = true
            val validation = WorkflowValidator.validateWorkflow(workflow)
            if (!validation.isValid) {
                _session.value = _session.value.copy(
                    taskId = workflow.id,
                    taskTitle = workflow.name,
                    totalSteps = 0,
                    state = ExecutionState.FAILED,
                    errorMessage = (validation as WorkflowValidationResult.Invalid).message
                )
                return
            }

            _session.value = _session.value.copy(
                taskId = workflow.id,
                taskTitle = workflow.name,
                totalSteps = workflow.steps.size,
                currentStepIndex = 0,
                currentStep = workflow.steps.firstOrNull(),
                activeCoordinate = workflow.steps.firstOrNull()?.coordinate,
                activeActionType = workflow.steps.firstOrNull()?.actionType,
                progress = 0f,
                state = ExecutionState.RUNNING,
                errorMessage = null
            )

            // Execute each step sequentially
            workflow.steps.forEachIndexed { index, step ->
                executedSteps.add(step)
                _session.value = _session.value.copy(
                    currentStepIndex = index,
                    currentStep = step,
                    activeCoordinate = step.coordinate,
                    activeActionType = step.actionType,
                    progress = (index + 1).toFloat() / workflow.steps.size
                )
            }

            _session.value = _session.value.copy(
                state = ExecutionState.COMPLETED,
                progress = 1.0f
            )
        }

        override fun pause() {
            _session.value = _session.value.copy(state = ExecutionState.PAUSED)
        }

        override fun resume() {
            _session.value = _session.value.copy(state = ExecutionState.RUNNING)
        }

        override fun stop() {
            _session.value = _session.value.copy(state = ExecutionState.STOPPED)
        }

        override suspend fun stepNext() {
            val curr = _session.value.currentStepIndex
            val tot = _session.value.totalSteps
            if (curr + 1 < tot) {
                _session.value = _session.value.copy(
                    currentStepIndex = curr + 1,
                    progress = (curr + 2).toFloat() / tot
                )
            }
        }

        override fun restart() {
            _session.value = _session.value.copy(
                currentStepIndex = 0,
                progress = 0f,
                state = ExecutionState.RUNNING
            )
        }

        override suspend fun executeTap(x: Int, y: Int, durationMs: Long): com.taskscheduling.app.automation.StepExecutionResult =
            com.taskscheduling.app.automation.StepExecutionResult(com.taskscheduling.app.automation.StepExecutionStatus.SUCCESS, 1, ActionType.TAP)
        override suspend fun executeLongPress(x: Int, y: Int, durationMs: Long): com.taskscheduling.app.automation.StepExecutionResult =
            com.taskscheduling.app.automation.StepExecutionResult(com.taskscheduling.app.automation.StepExecutionStatus.SUCCESS, 1, ActionType.LONG_PRESS)
        override suspend fun executeSwipe(startX: Int, startY: Int, endX: Int, endY: Int, durationMs: Long): com.taskscheduling.app.automation.StepExecutionResult =
            com.taskscheduling.app.automation.StepExecutionResult(com.taskscheduling.app.automation.StepExecutionStatus.SUCCESS, 1, ActionType.SWIPE)
        override suspend fun executeWait(durationMs: Long): com.taskscheduling.app.automation.StepExecutionResult =
            com.taskscheduling.app.automation.StepExecutionResult(com.taskscheduling.app.automation.StepExecutionStatus.SUCCESS, 1, ActionType.WAIT)
        override suspend fun executeBack(): com.taskscheduling.app.automation.StepExecutionResult =
            com.taskscheduling.app.automation.StepExecutionResult(com.taskscheduling.app.automation.StepExecutionStatus.SUCCESS, 1, ActionType.BACK)
        override suspend fun executeStep(step: WorkflowStep): com.taskscheduling.app.automation.StepExecutionResult =
            com.taskscheduling.app.automation.StepExecutionResult(com.taskscheduling.app.automation.StepExecutionStatus.SUCCESS, step.order, step.actionType)
        override suspend fun testStep(step: WorkflowStep): com.taskscheduling.app.automation.StepExecutionResult =
            com.taskscheduling.app.automation.StepExecutionResult(com.taskscheduling.app.automation.StepExecutionStatus.SUCCESS, step.order, step.actionType)
        override suspend fun execute(task: Task, speedMultiplier: Float) {
            executeWorkflow(task.workflow, speedMultiplier)
        }
    }

    @Test
    fun testWorkflowExecution_loadsByUniqueIdAndExecutesAllStepsSequentially() = runBlocking {
        // Save a 5-step workflow
        val steps = listOf(
            WorkflowStep(id = "s1", label = "Settings", actionType = ActionType.TAP, coordinate = CoordinateAction(100, 100), startX = 100, startY = 100),
            WorkflowStep(id = "s2", label = "Display", actionType = ActionType.TAP, coordinate = CoordinateAction(200, 200), startX = 200, startY = 200),
            WorkflowStep(id = "s3", label = "Timeout", actionType = ActionType.TAP, coordinate = CoordinateAction(300, 300), startX = 300, startY = 300),
            WorkflowStep(id = "s4", label = "5 Minutes", actionType = ActionType.TAP, coordinate = CoordinateAction(400, 400), startX = 400, startY = 400),
            WorkflowStep(id = "s5", label = "Back", actionType = ActionType.BACK)
        )
        val workflow = Workflow(
            id = "wf_exec_5_steps",
            name = "Settings Display Routine",
            steps = steps
        )
        persistenceRepo.saveWorkflow(workflow)

        val engine = TestPipelineEngine()
        val activityRepo = com.taskscheduling.app.data.repository.ActivityRepositoryImpl()
        val viewModel = ExecutionViewModel(
            automationEngine = engine,
            taskRepository = taskRepo,
            activityRepository = activityRepo,
            workflowPersistenceRepository = persistenceRepo
        )

        // Load and execute
        viewModel.loadAndExecute("wf_exec_5_steps")
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue("Execution engine must be called immediately", engine.onExecuteWorkflowCalled)
        assertEquals(ExecutionState.COMPLETED, engine.executionSession.value.state)
        assertEquals(5, engine.executionSession.value.totalSteps)
        assertEquals(1.0f, engine.executionSession.value.progress, 0.01f)
        assertEquals(5, engine.executedSteps.size)
        assertEquals("s1", engine.executedSteps[0].id)
        assertEquals("s5", engine.executedSteps[4].id)
    }

    @Test
    fun testWorkflowExecution_pauseAndResumeControls() {
        val engine = TestPipelineEngine()
        val activityRepo = com.taskscheduling.app.data.repository.ActivityRepositoryImpl()
        val viewModel = ExecutionViewModel(engine, taskRepo, activityRepo, persistenceRepo)

        viewModel.pause()
        assertEquals(ExecutionState.PAUSED, engine.executionSession.value.state)

        viewModel.resume()
        assertEquals(ExecutionState.RUNNING, engine.executionSession.value.state)
    }

    @Test
    fun testWorkflowExecution_stopAndRestartControls() {
        val engine = TestPipelineEngine()
        val activityRepo = com.taskscheduling.app.data.repository.ActivityRepositoryImpl()
        val viewModel = ExecutionViewModel(engine, taskRepo, activityRepo, persistenceRepo)

        viewModel.stop()
        assertEquals(ExecutionState.STOPPED, engine.executionSession.value.state)

        viewModel.restart()
        assertEquals(ExecutionState.RUNNING, engine.executionSession.value.state)
        assertEquals(0, engine.executionSession.value.currentStepIndex)
        assertEquals(0f, engine.executionSession.value.progress, 0.01f)
    }

    @Test
    fun testWorkflowExecution_stepNextControl() = runBlocking {
        val steps = listOf(
            WorkflowStep(id = "s1", label = "Step 1", actionType = ActionType.TAP, coordinate = CoordinateAction(10, 10), startX = 10, startY = 10),
            WorkflowStep(id = "s2", label = "Step 2", actionType = ActionType.TAP, coordinate = CoordinateAction(20, 20), startX = 20, startY = 20),
            WorkflowStep(id = "s3", label = "Step 3", actionType = ActionType.TAP, coordinate = CoordinateAction(30, 30), startX = 30, startY = 30)
        )
        val workflow = Workflow(id = "wf_step_next", name = "Next Test", steps = steps)
        persistenceRepo.saveWorkflow(workflow)

        val engine = TestPipelineEngine()
        val activityRepo = com.taskscheduling.app.data.repository.ActivityRepositoryImpl()
        val viewModel = ExecutionViewModel(engine, taskRepo, activityRepo, persistenceRepo)

        viewModel.loadTask("wf_step_next")
        viewModel.stepNext()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, engine.executionSession.value.currentStepIndex)
    }

    // =========================================================================
    // 4. Error Handling, Validation, and Edge Cases
    // =========================================================================

    @Test
    fun testEmptyWorkflowHandling_preventsExecutionAndDisplaysClearError() = runBlocking {
        val emptyWf = Workflow(
            id = "wf_empty",
            name = "Empty Workflow",
            steps = emptyList()
        )
        persistenceRepo.saveWorkflow(emptyWf)

        // 1. Validator must reject
        val validation = WorkflowValidator.validateWorkflow(emptyWf)
        assertFalse(validation.isValid)
        val invalid = validation as WorkflowValidationResult.Invalid
        assertTrue(invalid.message.contains("at least one step", ignoreCase = true))

        // 2. Execution must fail gracefully
        val engine = TestPipelineEngine()
        val activityRepo = com.taskscheduling.app.data.repository.ActivityRepositoryImpl()
        val viewModel = ExecutionViewModel(engine, taskRepo, activityRepo, persistenceRepo)
        viewModel.loadAndExecute("wf_empty")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(ExecutionState.FAILED, engine.executionSession.value.state)
        assertEquals(0, engine.executionSession.value.totalSteps)
        assertTrue(engine.executionSession.value.errorMessage!!.contains("at least one step", ignoreCase = true))
    }

    @Test
    fun testInvalidStepHandling_coordinateOutOfBoundsAndMissingEndCoords() {
        // Negative coordinate
        val negativeStep = WorkflowStep(
            id = "bad_1",
            label = "Bad Tap",
            actionType = ActionType.TAP,
            startX = -5,
            startY = 100
        )
        val res1 = WorkflowValidator.validateStep(negativeStep, 0)
        assertFalse("Negative coordinate must be invalid", res1.isValid)

        // Swipe missing end coordinate
        val incompleteSwipe = WorkflowStep(
            id = "bad_2",
            label = "Incomplete Swipe",
            actionType = ActionType.SWIPE,
            startX = 100,
            startY = 200,
            endX = null,
            endY = null
        )
        val res2 = WorkflowValidator.validateStep(incompleteSwipe, 1)
        assertFalse("Swipe missing end coordinates must be invalid", res2.isValid)

        // Wait with 0 duration
        val zeroWait = WorkflowStep(
            id = "bad_3",
            label = "Zero Wait",
            actionType = ActionType.WAIT,
            duration = 0L,
            durationMs = 0L
        )
        val res3 = WorkflowValidator.validateStep(zeroWait, 2)
        assertFalse("Wait with 0ms must be invalid", res3.isValid)

        // Text input with empty text
        val emptyText = WorkflowStep(
            id = "bad_4",
            label = "Empty Text",
            actionType = ActionType.TEXT_INPUT,
            textInput = ""
        )
        val res4 = WorkflowValidator.validateStep(emptyText, 3)
        assertFalse("Empty text input must be invalid", res4.isValid)
    }

    @Test
    fun testFloatingRecorderCancellation_discardsStepsAndResetsState() {
        RecordingManager.startRecording(
            workflowId = "wf_cancel_test",
            workflowName = "Cancel Test",
            deviceProfile = testProfile
        )

        RecordingManager.recordTap(100, 100)
        RecordingManager.recordTap(200, 200)
        assertEquals(2, RecordingManager.recordedSteps.value.size)

        RecordingManager.cancelRecording()

        assertEquals("Recorded steps must be discarded", 0, RecordingManager.recordedSteps.value.size)
        assertEquals(SessionState.IDLE, RecordingManager.sessionState.value)
        assertFalse(RecordingManager.isRecording.value)
    }
}
