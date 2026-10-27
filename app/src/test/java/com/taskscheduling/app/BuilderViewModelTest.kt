package com.taskscheduling.app

import com.taskscheduling.app.data.repository.TaskPersistenceRepository
import com.taskscheduling.app.data.repository.TaskRepositoryImpl
import com.taskscheduling.app.data.repository.WorkflowPersistenceRepository
import com.taskscheduling.app.domain.model.ActionType
import com.taskscheduling.app.domain.model.CoordinateAction
import com.taskscheduling.app.domain.model.Task
import com.taskscheduling.app.domain.model.TaskCategory
import com.taskscheduling.app.domain.model.TaskPriority
import com.taskscheduling.app.domain.model.TaskStatus
import com.taskscheduling.app.domain.model.Workflow
import com.taskscheduling.app.domain.model.WorkflowStep
import com.taskscheduling.app.feature.builder.BuilderViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class BuilderViewModelTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var baseDir: File
    private lateinit var workflowRepo: WorkflowPersistenceRepository
    private lateinit var taskPersistenceRepo: TaskPersistenceRepository
    private lateinit var taskRepository: TaskRepositoryImpl
    private lateinit var viewModel: BuilderViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        baseDir = tempFolder.newFolder("builder_vm_test")
        workflowRepo = WorkflowPersistenceRepository(baseDir)
        taskPersistenceRepo = TaskPersistenceRepository(baseDir, workflowRepo)
        taskRepository = TaskRepositoryImpl(workflowRepo, taskPersistenceRepo)
        viewModel = BuilderViewModel(taskRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testLoadTask_newWorkflow_createsUniqueIdAndDefaultStep() = runTest {
        viewModel.loadTask(null)
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNotNull(state.task)
        assertFalse("New workflow should NOT have hardcoded 'task_wallpaper' ID", state.task!!.id == "task_wallpaper")
        assertTrue("Task ID should start with workflow_", state.task!!.id.startsWith("workflow_"))
        assertFalse("isDirty should be false initially", state.isDirty)
        assertEquals(0, state.selectedStepIndex)
        assertNotNull("Should select first step ID", state.selectedStepId)
    }

    @Test
    fun testLoadTask_existingWorkflow_loadsFromRepository() = runTest {
        val existingStep = WorkflowStep(
            id = "step_existing_1",
            label = "Open App",
            actionType = ActionType.TAP,
            startX = 300,
            startY = 600,
            coordinate = CoordinateAction(300, 600)
        )
        val existingWf = Workflow(
            id = "task_custom_100",
            name = "My Existing Workflow",
            steps = listOf(existingStep)
        )
        val existingTask = Task(
            id = "task_custom_100",
            title = "My Existing Workflow",
            description = "Custom test task",
            priority = TaskPriority.HIGH,
            category = TaskCategory.CUSTOM,
            status = TaskStatus.TODO,
            workflow = existingWf
        )
        taskRepository.saveTask(existingTask)

        viewModel.loadTask("task_custom_100")
        var attempts = 0
        while (viewModel.uiState.value.task == null && attempts < 50) {
            Thread.sleep(20)
            attempts++
        }

        val state = viewModel.uiState.value
        assertNotNull(state.task)
        assertEquals("task_custom_100", state.task!!.id)
        assertEquals("My Existing Workflow", state.task!!.title)
        assertEquals(1, state.task!!.workflow.steps.size)
        assertEquals("step_existing_1", state.selectedStepId)
    }

    @Test
    fun testSelectStepById_selectsCorrectStepAndIndex() = runTest {
        viewModel.loadTask(null)
        testScheduler.advanceUntilIdle()

        viewModel.addNewStepWithType(ActionType.TAP)
        viewModel.addNewStepWithType(ActionType.SWIPE)
        viewModel.addNewStepWithType(ActionType.TEXT_INPUT)

        val steps = viewModel.uiState.value.task!!.workflow.steps
        assertTrue("Should have at least 4 steps", steps.size >= 4)

        val targetStep = steps[2]
        viewModel.selectStepById(targetStep.id)

        val state = viewModel.uiState.value
        assertEquals(targetStep.id, state.selectedStepId)
        assertEquals(2, state.selectedStepIndex)
    }

    @Test
    fun testUpdateStepDetails_updatesSynchronizedFieldsAndMarksDirty() = runTest {
        viewModel.loadTask(null)
        testScheduler.advanceUntilIdle()

        val firstStepId = viewModel.uiState.value.selectedStepId!!

        viewModel.updateSelectedStep(
            label = "Calibrated Button Tap",
            actionType = ActionType.TAP,
            coordinate = CoordinateAction(720, 1280),
            targetDescription = "Submit button",
            textInput = null,
            waitDurationMs = 1500L,
            timeoutMs = 6000L,
            expectedScreenState = "SUBMITTED",
            verificationRule = "TOAST_APPEARS",
            retryLimit = 3,
            recoveryBehavior = "RETRY"
        )

        val state = viewModel.uiState.value
        assertTrue("Updating step should mark workflow as dirty", state.isDirty)
        val updatedStep = state.task!!.workflow.steps.find { it.id == firstStepId }!!
        assertEquals("Calibrated Button Tap", updatedStep.label)
        assertEquals("Calibrated Button Tap", updatedStep.description)
        assertEquals(720, updatedStep.coordinate!!.x)
        assertEquals(720, updatedStep.startX)
        assertEquals(1280, updatedStep.coordinate!!.y)
        assertEquals(1280, updatedStep.startY)
        assertEquals(1500L, updatedStep.waitDurationMs)
        assertEquals(6000L, updatedStep.timeoutMs)
        assertEquals("SUBMITTED", updatedStep.expectedScreenState)
    }

    @Test
    fun testStepReorderingAndInsertion() = runTest {
        viewModel.loadTask(null)
        testScheduler.advanceUntilIdle()

        val step1 = viewModel.uiState.value.task!!.workflow.steps[0]

        // Add 2 steps
        viewModel.addNewStepWithType(ActionType.SWIPE)
        val step2 = viewModel.uiState.value.task!!.workflow.steps.last()
        viewModel.addNewStepWithType(ActionType.TEXT_INPUT)
        val step3 = viewModel.uiState.value.task!!.workflow.steps.last()

        val initialIds = viewModel.uiState.value.task!!.workflow.steps.map { it.id }
        assertTrue(initialIds.contains(step1.id))
        assertTrue(initialIds.contains(step2.id))
        assertTrue(initialIds.contains(step3.id))

        // Move step 1 down
        viewModel.selectStepById(step1.id)
        viewModel.moveStepDown(0)
        val afterMoveDown = viewModel.uiState.value.task!!.workflow.steps
        assertEquals(step1.id, afterMoveDown[1].id)

        // Move step 1 back up
        viewModel.moveStepUp(1)
        val afterMoveUp = viewModel.uiState.value.task!!.workflow.steps
        assertEquals(step1.id, afterMoveUp[0].id)

        // Insert step after index 0
        viewModel.insertStepAfter(0, ActionType.BACK)
        val afterInsert = viewModel.uiState.value.task!!.workflow.steps
        assertEquals(ActionType.BACK, afterInsert[1].actionType)

        // Select and duplicate step at index 1
        viewModel.selectStep(1)
        viewModel.duplicateSelectedStep()
        val afterDuplicate = viewModel.uiState.value.task!!.workflow.steps
        assertEquals(ActionType.BACK, afterDuplicate[2].actionType)
        assertTrue(afterDuplicate[2].label.contains("Copy"))

        // Delete duplicated step
        val sizeBeforeDelete = afterDuplicate.size
        viewModel.selectStep(2)
        viewModel.deleteSelectedStep()
        val afterDelete = viewModel.uiState.value.task!!.workflow.steps
        assertEquals(sizeBeforeDelete - 1, afterDelete.size)
    }

    @Test
    fun testValidation_reportsMissingNameAndCoordinates() = runTest {
        viewModel.loadTask(null)
        testScheduler.advanceUntilIdle()

        viewModel.updateTaskDetails("   ", "Description")

        viewModel.selectStep(0)
        viewModel.updateSelectedStep(
            label = "Invalid Tap",
            actionType = ActionType.TAP,
            coordinate = CoordinateAction(-1, -1)
        )

        var errorMessageReceived: String? = null
        viewModel.saveTaskWithFeedback(onError = { errorMessageReceived = it })

        val state = viewModel.uiState.value
        assertNotNull("Should set save error message", state.saveErrorMessage)
        assertTrue(state.validationErrors.isNotEmpty())
        assertNotNull(errorMessageReceived)
    }

    @Test
    fun testSaveTaskExplicitly_persistsToStorageAndClearsDirtyState() = runTest {
        viewModel.loadTask(null)
        testScheduler.advanceUntilIdle()

        viewModel.updateTaskDetails("Validated Workflow", "Valid Desc")

        viewModel.selectStep(0)
        viewModel.updateSelectedStep(
            label = "Valid Tap Step",
            actionType = ActionType.TAP,
            coordinate = CoordinateAction(450, 900),
            targetDescription = "Center button"
        )

        var saveSuccess = false
        val latch = java.util.concurrent.CountDownLatch(1)
        viewModel.saveTaskExplicitly {
            saveSuccess = true
            latch.countDown()
        }
        latch.await(3, java.util.concurrent.TimeUnit.SECONDS)

        val state = viewModel.uiState.value
        assertTrue("onSuccess callback should be called", saveSuccess)
        assertFalse("isDirty should be false after save", state.isDirty)
        assertFalse("isSaving should be false after save finishes", state.isSaving)
        assertNotNull("Should have save success message", state.saveSuccessMessage)
        assertNull("Save error message should be null", state.saveErrorMessage)

        val savedTaskId = state.task!!.id
        // Verify task exists in repository
        val loadedTask = taskRepository.getTaskById(savedTaskId)
        assertNotNull("Saved task must be readable from repository", loadedTask)
        assertEquals("Validated Workflow", loadedTask!!.title)
        assertEquals(1, loadedTask.workflow.steps.size)
        assertEquals(450, loadedTask.workflow.steps[0].coordinate!!.x)
        assertEquals(900, loadedTask.workflow.steps[0].coordinate!!.y)
    }
}
