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
import com.taskscheduling.app.domain.validation.WorkflowValidationResult
import com.taskscheduling.app.domain.validation.WorkflowValidator
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class WorkflowPersistenceAndValidationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var baseDir: File
    private lateinit var workflowRepo: WorkflowPersistenceRepository
    private lateinit var taskPersistenceRepo: TaskPersistenceRepository
    private lateinit var taskRepository: TaskRepositoryImpl

    @Before
    fun setup() {
        baseDir = tempFolder.newFolder("workflow_test_dir")
        workflowRepo = WorkflowPersistenceRepository(baseDir)
        taskPersistenceRepo = TaskPersistenceRepository(baseDir, workflowRepo)
        taskRepository = TaskRepositoryImpl(workflowRepo, taskPersistenceRepo)
    }

    @Test
    fun testWorkflowValidator_validWorkflow_returnsValid() {
        val step1 = WorkflowStep(
            id = "step_1",
            label = "Tap Settings",
            actionType = ActionType.TAP,
            startX = 250,
            startY = 400,
            coordinate = CoordinateAction(250, 400),
            targetDescription = "Settings icon"
        )
        val step2 = WorkflowStep(
            id = "step_2",
            label = "Enter Text",
            actionType = ActionType.TEXT_INPUT,
            startX = 300,
            startY = 500,
            coordinate = CoordinateAction(300, 500),
            textInput = "Search query"
        )
        val workflow = Workflow(
            id = "wf_1",
            name = "Test Workflow",
            steps = listOf(step1, step2)
        )

        val result = WorkflowValidator.validateWorkflow(workflow)
        assertTrue(result.isValid)
        assertTrue(result is WorkflowValidationResult.Valid)
    }

    @Test
    fun testWorkflowValidator_blankTitle_returnsError() {
        val workflow = Workflow(
            id = "wf_2",
            name = "   ",
            steps = listOf(
                WorkflowStep(
                    id = "step_1",
                    label = "Tap",
                    actionType = ActionType.TAP,
                    startX = 100,
                    startY = 100,
                    coordinate = CoordinateAction(100, 100)
                )
            )
        )

        val result = WorkflowValidator.validateWorkflow(workflow)
        assertFalse(result.isValid)
        assertTrue(result is WorkflowValidationResult.Invalid)
        val invalid = result as WorkflowValidationResult.Invalid
        assertTrue(invalid.message.contains("name cannot be empty", ignoreCase = true))
    }

    @Test
    fun testWorkflowValidator_emptySteps_returnsError() {
        val workflow = Workflow(
            id = "wf_3",
            name = "Empty Workflow",
            steps = emptyList()
        )

        val result = WorkflowValidator.validateWorkflow(workflow)
        assertFalse(result.isValid)
        assertTrue(result is WorkflowValidationResult.Invalid)
        val invalid = result as WorkflowValidationResult.Invalid
        assertTrue(invalid.message.contains("at least one step", ignoreCase = true))
    }

    @Test
    fun testWorkflowValidator_missingCoordinatesForTap_returnsError() {
        val step = WorkflowStep(
            id = "step_tap_invalid",
            label = "Invalid Tap",
            actionType = ActionType.TAP,
            startX = -1,
            startY = -1,
            coordinate = CoordinateAction(-1, -1)
        )
        val workflow = Workflow(
            id = "wf_4",
            name = "Invalid Tap Workflow",
            steps = listOf(step)
        )

        val result = WorkflowValidator.validateWorkflow(workflow)
        assertFalse(result.isValid)
        assertTrue(result is WorkflowValidationResult.Invalid)
        val invalid = result as WorkflowValidationResult.Invalid
        assertEquals("step_tap_invalid", invalid.stepId)
        assertTrue(invalid.message.contains("negative", ignoreCase = true))
    }

    @Test
    fun testWorkflowValidator_missingSwipeEndCoordinates_returnsError() {
        val step = WorkflowStep(
            id = "step_swipe_invalid",
            label = "Invalid Swipe",
            actionType = ActionType.SWIPE,
            startX = 200,
            startY = 400,
            endX = -1,
            endY = -1,
            coordinate = CoordinateAction(200, 400, -1, -1)
        )
        val workflow = Workflow(
            id = "wf_5",
            name = "Invalid Swipe Workflow",
            steps = listOf(step)
        )

        val result = WorkflowValidator.validateWorkflow(workflow)
        assertFalse(result.isValid)
        assertTrue(result is WorkflowValidationResult.Invalid)
        val invalid = result as WorkflowValidationResult.Invalid
        assertEquals("step_swipe_invalid", invalid.stepId)
        assertTrue(invalid.message.contains("end coordinate", ignoreCase = true))
    }

    @Test
    fun testWorkflowValidator_emptyTextInputForAction_returnsError() {
        val step = WorkflowStep(
            id = "step_text_invalid",
            label = "Blank Text Input",
            actionType = ActionType.TEXT_INPUT,
            startX = 100,
            startY = 200,
            coordinate = CoordinateAction(100, 200),
            textInput = "   "
        )
        val workflow = Workflow(
            id = "wf_6",
            name = "Invalid Text Workflow",
            steps = listOf(step)
        )

        val result = WorkflowValidator.validateWorkflow(workflow)
        assertFalse(result.isValid)
        assertTrue(result is WorkflowValidationResult.Invalid)
        val invalid = result as WorkflowValidationResult.Invalid
        assertEquals("step_text_invalid", invalid.stepId)
        assertTrue(invalid.message.contains("text input", ignoreCase = true))
    }

    @Test
    fun testWorkflowStep_copyWithSync_keepsPrimaryAndSecondaryInSync() {
        val initialStep = WorkflowStep(
            id = "step_sync",
            type = ActionType.TAP,
            startX = 100,
            startY = 200,
            description = "Initial description",
            duration = 500L
        )

        val synced = initialStep.copyWithSync(
            label = "Updated Tap",
            actionType = ActionType.LONG_PRESS,
            coordinate = CoordinateAction(350, 750),
            targetDescription = "New Target",
            textInput = "New text",
            waitDurationMs = 1200L,
            timeoutMs = 8000L,
            retryLimit = 3,
            recoveryBehavior = "RETRY_WITH_BACKOFF",
            isVerified = true
        )

        assertEquals("Updated Tap", synced.label)
        assertEquals("Updated Tap", synced.description)
        assertEquals(ActionType.LONG_PRESS, synced.actionType)
        assertEquals(ActionType.LONG_PRESS, synced.type)
        assertEquals(350, synced.coordinate!!.x)
        assertEquals(350, synced.startX)
        assertEquals(750, synced.coordinate!!.y)
        assertEquals(750, synced.startY)
        assertEquals(1200L, synced.waitDurationMs)
        assertEquals(1200L, synced.waitAfter)
        assertEquals(500L, synced.duration)
        assertEquals(8000L, synced.timeoutMs)
        assertEquals(3, synced.retryLimit)
        assertEquals("RETRY_WITH_BACKOFF", synced.recoveryBehavior)
        assertTrue(synced.isVerified)
    }

    @Test
    fun testWorkflowPersistenceRepository_roundTripSerialization_preservesAllExtendedFields() = runBlocking {
        val step1 = WorkflowStep(
            id = "step_ext_1",
            label = "Type Username",
            actionType = ActionType.TEXT_INPUT,
            startX = 400,
            startY = 600,
            coordinate = CoordinateAction(400, 600),
            targetScreen = "LoginScreen",
            targetElement = "username_input",
            targetDescription = "Username text field",
            textInput = "admin_user",
            waitDurationMs = 1500L,
            timeoutMs = 7000L,
            expectedScreenState = "LOGIN_FORM_ACTIVE",
            verificationRule = "EXACT_TEXT_MATCH",
            retryLimit = 4,
            recoveryBehavior = "RESTART_ACTIVITY",
            isVerified = true,
            requiresPermission = false
        )

        val step2 = WorkflowStep(
            id = "step_ext_2",
            label = "Swipe Up Feed",
            actionType = ActionType.SWIPE,
            startX = 500,
            startY = 1000,
            endX = 500,
            endY = 200,
            coordinate = CoordinateAction(500, 1000, 500, 200),
            targetScreen = "MainFeed",
            waitDurationMs = 800L,
            timeoutMs = 5000L,
            expectedScreenState = "NEXT_PAGE_LOADED",
            verificationRule = "ELEMENT_PRESENT",
            retryLimit = 2,
            recoveryBehavior = "CONTINUE_ON_FAILURE",
            isVerified = false,
            requiresPermission = true
        )

        val originalWorkflow = Workflow(
            id = "wf_roundtrip_1",
            name = "Full Extended Workflow",
            steps = listOf(step1, step2)
        )

        // Test true JSON serialization & deserialization round-trip
        val json = WorkflowPersistenceRepository.workflowToJson(originalWorkflow)
        val loadedWorkflow = WorkflowPersistenceRepository.jsonToWorkflow(json)

        assertNotNull("Workflow must be loaded from deserialized json", loadedWorkflow)
        assertEquals(originalWorkflow.name, loadedWorkflow.name)
        assertEquals(2, loadedWorkflow.steps.size)

        val loadedStep1 = loadedWorkflow.steps[0]
        assertEquals("step_ext_1", loadedStep1.id)
        assertEquals("Type Username", loadedStep1.label)
        assertEquals("Type Username", loadedStep1.description)
        assertEquals(ActionType.TEXT_INPUT, loadedStep1.actionType)
        assertEquals(ActionType.TEXT_INPUT, loadedStep1.type)
        assertEquals(400, loadedStep1.coordinate!!.x)
        assertEquals(400, loadedStep1.startX)
        assertEquals(600, loadedStep1.coordinate!!.y)
        assertEquals(600, loadedStep1.startY)
        assertEquals("LoginScreen", loadedStep1.targetScreen)
        assertEquals("username_input", loadedStep1.targetElement)
        assertEquals("Username text field", loadedStep1.targetDescription)
        assertEquals("admin_user", loadedStep1.textInput)
        assertEquals(1500L, loadedStep1.waitDurationMs)
        assertEquals(100L, loadedStep1.duration)
        assertEquals(7000L, loadedStep1.timeoutMs)
        assertEquals("LOGIN_FORM_ACTIVE", loadedStep1.expectedScreenState)
        assertEquals("EXACT_TEXT_MATCH", loadedStep1.verificationRule)
        assertEquals(4, loadedStep1.retryLimit)
        assertEquals("RESTART_ACTIVITY", loadedStep1.recoveryBehavior)
        assertTrue(loadedStep1.isVerified)
        assertFalse(loadedStep1.requiresPermission)

        val loadedStep2 = loadedWorkflow.steps[1]
        assertEquals("step_ext_2", loadedStep2.id)
        assertEquals(ActionType.SWIPE, loadedStep2.actionType)
        assertEquals(ActionType.SWIPE, loadedStep2.type)
        assertEquals(500, loadedStep2.endX)
        assertEquals(200, loadedStep2.endY)
        assertEquals("MainFeed", loadedStep2.targetScreen)
        assertEquals(800L, loadedStep2.waitDurationMs)
        assertEquals(5000L, loadedStep2.timeoutMs)
        assertEquals("NEXT_PAGE_LOADED", loadedStep2.expectedScreenState)
        assertEquals("ELEMENT_PRESENT", loadedStep2.verificationRule)
        assertEquals(2, loadedStep2.retryLimit)
        assertEquals("CONTINUE_ON_FAILURE", loadedStep2.recoveryBehavior)
        assertFalse(loadedStep2.isVerified)
        assertTrue(loadedStep2.requiresPermission)
    }

    @Test
    fun testTaskRepository_saveWorkflowTask_persistsAcrossRestart() = runBlocking {
        val workflowId = "workflow_test_persist_${System.currentTimeMillis()}"
        val step = WorkflowStep(
            id = "step_p1",
            label = "Tap Home",
            actionType = ActionType.TAP,
            startX = 540,
            startY = 1920,
            coordinate = CoordinateAction(540, 1920)
        )
        val workflow = Workflow(
            id = workflowId,
            name = "Persistent Test Workflow",
            steps = listOf(step)
        )

        // Save via workflow repository
        workflowRepo.saveWorkflow(workflow)

        // Save task representation
        val task = Task(
            id = workflowId,
            title = "Persistent Test Workflow",
            description = "Auto-created workflow task",
            priority = TaskPriority.HIGH,
            category = TaskCategory.CUSTOM,
            status = TaskStatus.TODO,
            workflow = workflow
        )
        taskRepository.saveTask(task)

        // Verify loaded through TaskRepository
        val loadedTask = taskRepository.getTaskById(workflowId)
        assertNotNull("Task must exist in task repository", loadedTask)
        assertEquals("Persistent Test Workflow", loadedTask!!.title)
        assertNotNull("Task must contain workflow", loadedTask.workflow)
        assertEquals(1, loadedTask.workflow.steps.size)
        assertEquals("Tap Home", loadedTask.workflow.steps[0].label)
        assertEquals(540, loadedTask.workflow.steps[0].coordinate!!.x)

        // Simulate app restart by creating brand new repository instances pointing to the same baseDir
        val newWorkflowRepo = WorkflowPersistenceRepository(baseDir)
        val newTaskPersistenceRepo = TaskPersistenceRepository(baseDir, newWorkflowRepo)
        val newTaskRepo = TaskRepositoryImpl(newWorkflowRepo, newTaskPersistenceRepo)

        val reloadedWorkflow = newWorkflowRepo.getWorkflow(workflowId)
        assertNotNull("Workflow must persist after restart", reloadedWorkflow)
        assertEquals("Persistent Test Workflow", reloadedWorkflow!!.name)
        assertEquals(1, reloadedWorkflow.steps.size)
        assertEquals("Tap Home", reloadedWorkflow.steps[0].label)

        val reloadedTask = newTaskRepo.getTaskById(workflowId)
        assertNotNull("Task must persist after restart", reloadedTask)
        assertEquals("Persistent Test Workflow", reloadedTask!!.title)
    }
}
