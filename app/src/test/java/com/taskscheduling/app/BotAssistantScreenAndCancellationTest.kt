package com.taskscheduling.app

import com.taskscheduling.app.assistant.LocalCommandResolver
import com.taskscheduling.app.assistant.LocalWorkflowResolver
import com.taskscheduling.app.automation.StepExecutionResult
import com.taskscheduling.app.data.repository.TaskPersistenceRepository
import com.taskscheduling.app.data.repository.TaskRepositoryImpl
import com.taskscheduling.app.data.repository.WorkflowPersistenceRepository
import com.taskscheduling.app.domain.model.ActionType
import com.taskscheduling.app.domain.model.ExecutionSession
import com.taskscheduling.app.domain.model.ExecutionState
import com.taskscheduling.app.domain.model.Task
import com.taskscheduling.app.domain.model.TaskCategory
import com.taskscheduling.app.domain.model.Workflow
import com.taskscheduling.app.domain.model.WorkflowStep
import com.taskscheduling.app.feature.assistant.AssistantViewModel
import com.taskscheduling.app.feature.assistant.ExecutionMessageStatus
import com.taskscheduling.app.ui.theme.VoiceAssistantStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
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

@OptIn(ExperimentalCoroutinesApi::class)
class BotAssistantScreenAndCancellationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var baseDir: File
    private lateinit var workflowRepo: WorkflowPersistenceRepository
    private lateinit var taskPersistenceRepo: TaskPersistenceRepository
    private lateinit var taskRepo: TaskRepositoryImpl
    private lateinit var commandResolver: LocalCommandResolver
    private lateinit var workflowResolver: LocalWorkflowResolver
    private lateinit var fakeAutomationEngine: FakeTestAutomationEngine

    private class FakeTestAutomationEngine : com.taskscheduling.app.automation.AutomationEngine {
        private val _session = MutableStateFlow(ExecutionSession(taskId = "", taskTitle = ""))
        override val executionSession: StateFlow<ExecutionSession> = _session.asStateFlow()

        var executedTask: Task? = null
        var stopCount = 0

        override suspend fun executeTap(x: Int, y: Int, durationMs: Long) = StepExecutionResult(com.taskscheduling.app.automation.StepExecutionStatus.SUCCESS, 1, ActionType.TAP)
        override suspend fun executeLongPress(x: Int, y: Int, durationMs: Long) = StepExecutionResult(com.taskscheduling.app.automation.StepExecutionStatus.SUCCESS, 1, ActionType.LONG_PRESS)
        override suspend fun executeSwipe(startX: Int, startY: Int, endX: Int, endY: Int, durationMs: Long) = StepExecutionResult(com.taskscheduling.app.automation.StepExecutionStatus.SUCCESS, 1, ActionType.SWIPE)
        override suspend fun executeWait(durationMs: Long) = StepExecutionResult(com.taskscheduling.app.automation.StepExecutionStatus.SUCCESS, 1, ActionType.WAIT)
        override suspend fun executeBack() = StepExecutionResult(com.taskscheduling.app.automation.StepExecutionStatus.SUCCESS, 1, ActionType.BACK)
        override suspend fun executeStep(step: WorkflowStep) = StepExecutionResult(com.taskscheduling.app.automation.StepExecutionStatus.SUCCESS, step.order, step.type)
        override suspend fun executeWorkflow(workflow: Workflow, speedMultiplier: Float) {}
        override suspend fun testStep(step: WorkflowStep) = StepExecutionResult(com.taskscheduling.app.automation.StepExecutionStatus.SUCCESS, step.order, step.type)

        override suspend fun execute(task: Task, speedMultiplier: Float) {
            executedTask = task
            _session.value = ExecutionSession(
                taskId = task.id,
                taskTitle = task.title,
                totalSteps = task.workflow.steps.size,
                currentStepIndex = 0,
                currentStep = task.workflow.steps.firstOrNull(),
                state = ExecutionState.RUNNING
            )
        }

        fun completeExecution() {
            _session.value = _session.value.copy(
                state = ExecutionState.COMPLETED,
                currentStepIndex = _session.value.totalSteps
            )
        }

        fun failExecution(error: String) {
            _session.value = _session.value.copy(
                state = ExecutionState.FAILED,
                errorMessage = error
            )
        }

        override fun pause() { _session.value = _session.value.copy(state = ExecutionState.PAUSED) }
        override fun resume() { _session.value = _session.value.copy(state = ExecutionState.RUNNING) }
        override fun stop() {
            stopCount++
            _session.value = _session.value.copy(state = ExecutionState.STOPPED)
        }
        override fun restart() {}
        override suspend fun stepNext() {}
    }

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        baseDir = tempFolder.newFolder("bot_assistant_test")
        workflowRepo = WorkflowPersistenceRepository(baseDir)
        taskPersistenceRepo = TaskPersistenceRepository(baseDir, workflowRepo)
        taskRepo = TaskRepositoryImpl(workflowRepo, taskPersistenceRepo)
        commandResolver = LocalCommandResolver()
        workflowResolver = LocalWorkflowResolver(workflowRepo)
        fakeAutomationEngine = FakeTestAutomationEngine()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): AssistantViewModel {
        return AssistantViewModel(
            taskRepository = taskRepo,
            persistenceRepository = workflowRepo,
            commandResolver = commandResolver,
            workflowResolver = workflowResolver,
            automationEngine = fakeAutomationEngine,
            context = null
        )
    }

    @Test
    fun testNoDuplicateCancellationMessagesForSameEvent() = runTest {
        val vm = createViewModel()
        val wf = Workflow(
            id = "wf_study",
            name = "Daily Study",
            description = "Open study app",
            steps = listOf(WorkflowStep(id = "s1", order = 1, type = ActionType.TAP, startX = 100, startY = 200, description = "Tap study icon"))
        )
        workflowRepo.saveWorkflow(wf)

        // Trigger confirmation for saved workflow
        vm.sendMessage("Run Daily Study")

        val confirmMsg = vm.messages.value.find { it.isConfirmation }
        assertNotNull("Confirmation message must be generated", confirmMsg)
        val initialCount = vm.messages.value.size

        // 1. First cancellation
        vm.cancelConfirmation(confirmMsg!!.id)

        val countAfterFirstCancel = vm.messages.value.size
        assertEquals(initialCount + 1, countAfterFirstCancel)
        val cancelMsg = vm.messages.value.last()
        assertEquals(ExecutionMessageStatus.CANCELLED, cancelMsg.executionStatus)
        assertEquals("Workflow execution was cancelled.", cancelMsg.text)

        val updatedConfirmMsg = vm.messages.value.find { it.id == confirmMsg.id }
        assertFalse(updatedConfirmMsg!!.isConfirmation)
        assertEquals(ExecutionMessageStatus.CANCELLED, updatedConfirmMsg.executionStatus)

        // 2. Second cancellation attempt on the SAME message (e.g. repeated tap)
        vm.cancelConfirmation(confirmMsg.id)

        // Must NOT append duplicate cancellation message
        assertEquals("Message count must not increase on duplicate cancel", countAfterFirstCancel, vm.messages.value.size)
        // Must not mangle original message text with duplicate tags
        assertFalse(updatedConfirmMsg.text.contains("[Cancelled]\n\n[Cancelled]"))
    }

    @Test
    fun testCancellationPhraseWhenNothingPendingDoesNotAppendCancellationMessage() = runTest {
        val vm = createViewModel()
        val initialCount = vm.messages.value.size

        // User says "cancel" when nothing is awaiting confirmation and nothing is running
        vm.sendMessage("cancel")

        val newMessages = vm.messages.value
        // User message was appended, plus an assistant explanation
        assertEquals(initialCount + 2, newMessages.size)
        val assistantResponse = newMessages.last()
        assertEquals("Nothing is currently running or awaiting confirmation.", assistantResponse.text)
        assertFalse("Must NOT mark as CANCELLED when nothing was cancelled", assistantResponse.executionStatus == ExecutionMessageStatus.CANCELLED)
    }

    @Test
    fun testCancellationPhraseWithPendingConfirmationCancelsCleanlyOnce() = runTest {
        val vm = createViewModel()
        val wf = Workflow(
            id = "wf_workout",
            name = "Morning Workout",
            description = "Start fitness timer",
            steps = listOf(WorkflowStep(id = "s1", order = 1, type = ActionType.TAP, startX = 300, startY = 400, description = "Tap timer"))
        )
        workflowRepo.saveWorkflow(wf)

        // Request workflow
        vm.sendMessage("Morning Workout")
        val confirmMsg = vm.messages.value.find { it.isConfirmation }
        assertNotNull(confirmMsg)

        // User types or speaks "stop"
        vm.sendMessage("stop")

        val cancelMsg = vm.messages.value.last()
        assertEquals(ExecutionMessageStatus.CANCELLED, cancelMsg.executionStatus)
        assertEquals("Workflow execution was cancelled.", cancelMsg.text)

        val cancelCount = vm.messages.value.count { it.text == "Workflow execution was cancelled." }
        assertEquals("Must only have exactly ONE cancellation message", 1, cancelCount)
    }

    @Test
    fun testWorkflowNotFoundExplainsClearlyAndProvidesCreateWorkflowAction() = runTest {
        val vm = createViewModel()

        // Query a nonexistent workflow
        vm.sendMessage("Clean my room")

        val response = vm.messages.value.last()
        assertTrue("Must explain clearly that workflow was not found", response.text.contains("couldn't find a saved workflow"))
        assertTrue("Must provide Create Workflow action", response.showCreateWorkflowAction)
        assertEquals("Clean My Room", response.queryForNewWorkflow)
        assertNull("Must not associate a fake workflow", response.associatedWorkflow)
    }

    @Test
    fun testClearHistoryResetsToWelcomePrompt() = runTest {
        val vm = createViewModel()
        vm.sendMessage("Change wallpaper")
        vm.sendMessage("Open YouTube")
        assertTrue(vm.messages.value.size > 2)

        vm.clearHistory()

        assertEquals("Must reset to single welcome message", 1, vm.messages.value.size)
        assertEquals(VoiceAssistantStatus.IDLE, vm.orbStatus.value)
        assertEquals("Ready for instructions", vm.statusLabel.value)
        assertNull(vm.errorMessage.value)
    }

    @Test
    fun testRunWorkflowExecutesViaAutomationEngine() = runTest {
        val vm = createViewModel()
        val wf = Workflow(
            id = "wf_yt",
            name = "Open YouTube",
            description = "Launch YouTube",
            steps = listOf(WorkflowStep(id = "s1", order = 1, type = ActionType.TAP, startX = 500, startY = 600, description = "Tap YouTube icon"))
        )
        val task = Task(id = wf.id, title = wf.name, description = wf.description, category = TaskCategory.CUSTOM, workflow = wf)
        workflowRepo.saveWorkflow(wf)

        vm.runWorkflow(task)

        assertNotNull("Automation engine must be invoked", fakeAutomationEngine.executedTask)
        assertEquals("Open YouTube", fakeAutomationEngine.executedTask?.workflow?.name)

        // Simulate successful completion on engine
        fakeAutomationEngine.completeExecution()

        val progressOrDone = vm.messages.value.find { it.executionStatus == ExecutionMessageStatus.COMPLETED }
        assertNotNull("Must record completed execution status", progressOrDone)
        assertTrue(progressOrDone!!.text.contains("completed successfully"))
    }

    @Test
    fun testStopRunningExecutionHaltsRunningTask() = runTest {
        val vm = createViewModel()
        val wf = Workflow(
            id = "wf_long",
            name = "Long Task",
            description = "Many steps",
            steps = listOf(WorkflowStep(id = "s1", order = 1, type = ActionType.TAP, startX = 100, startY = 100, description = "Step 1"))
        )
        val task = Task(id = wf.id, title = wf.name, description = wf.description, category = TaskCategory.CUSTOM, workflow = wf)
        workflowRepo.saveWorkflow(wf)

        vm.runWorkflow(task)
        assertTrue(vm.isExecuting.value)

        vm.stopRunningExecution()

        assertFalse("Execution state must be inactive after stop", vm.isExecuting.value)
        assertTrue("Automation engine stop must be called", fakeAutomationEngine.stopCount > 0)
        assertEquals(VoiceAssistantStatus.IDLE, vm.orbStatus.value)

        val stoppedMsg = vm.messages.value.last()
        assertEquals("Workflow execution was stopped.", stoppedMsg.text)
        assertEquals(ExecutionMessageStatus.CANCELLED, stoppedMsg.executionStatus)
    }
}
