package com.taskscheduling.app

import com.taskscheduling.app.assistant.CommandIntent
import com.taskscheduling.app.assistant.DefaultExecutionPolicyResolver
import com.taskscheduling.app.assistant.ExecutionPolicy
import com.taskscheduling.app.assistant.LocalCommandProcessor
import com.taskscheduling.app.assistant.LocalCommandResolver
import com.taskscheduling.app.assistant.LocalWorkflowResolver
import com.taskscheduling.app.assistant.TaskCommandParser
import com.taskscheduling.app.assistant.TaskParseResult
import com.taskscheduling.app.data.repository.TaskPersistenceRepository
import com.taskscheduling.app.data.repository.TaskRepositoryImpl
import com.taskscheduling.app.data.repository.WorkflowPersistenceRepository
import com.taskscheduling.app.domain.model.ChecklistItem
import com.taskscheduling.app.domain.model.Task
import com.taskscheduling.app.domain.model.TaskCategory
import com.taskscheduling.app.domain.model.TaskPriority
import com.taskscheduling.app.domain.model.TaskRecurrence
import com.taskscheduling.app.domain.model.TaskStatus
import com.taskscheduling.app.domain.model.Workflow
import com.taskscheduling.app.domain.model.TaskDraft
import com.taskscheduling.app.domain.model.TaskType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
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

class TaskManagementSystemTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var baseDir: File
    private lateinit var workflowRepo: WorkflowPersistenceRepository
    private lateinit var taskPersistenceRepo: TaskPersistenceRepository
    private lateinit var taskRepository: TaskRepositoryImpl
    private lateinit var commandResolver: LocalCommandResolver
    private lateinit var workflowResolver: LocalWorkflowResolver
    private lateinit var commandProcessor: LocalCommandProcessor

    @Before
    fun setup() {
        baseDir = tempFolder.newFolder("tasks_test_dir")
        workflowRepo = WorkflowPersistenceRepository(baseDir)
        taskPersistenceRepo = TaskPersistenceRepository(baseDir, workflowRepo)
        taskRepository = TaskRepositoryImpl(workflowRepo, taskPersistenceRepo)
        commandResolver = LocalCommandResolver()
        workflowResolver = LocalWorkflowResolver(workflowRepo)
        commandProcessor = LocalCommandProcessor(commandResolver, workflowResolver)
    }

    // ==========================================
    // 1. TASK MODEL TESTS
    // ==========================================

    @Test
    fun testTaskCreation_withDefaultValues() {
        val task = Task(
            title = "Study Machine Learning"
        )
        assertNotNull(task.id)
        assertEquals("Study Machine Learning", task.title)
        assertEquals("", task.description)
        assertEquals(TaskPriority.MEDIUM, task.priority)
        assertEquals(TaskStatus.TODO, task.status)
        assertFalse(task.isCompleted)
        assertEquals(TaskRecurrence.NONE, task.recurrence)
        assertTrue(task.checklist.isEmpty())
        assertTrue(task.notificationEnabled)
    }

    @Test
    fun testTaskUniqueId_neverSameForMultipleTasks() {
        val task1 = Task(title = "Study ML")
        val task2 = Task(title = "Study ML")
        assertNotEquals(task1.id, task2.id)
    }

    @Test
    fun testChecklistItem_independentCompletionState() {
        val item1 = ChecklistItem(text = "Review lecture slides", isCompleted = true)
        val item2 = ChecklistItem(text = "Solve problem set", isCompleted = false)
        val task = Task(
            title = "Prepare for exam",
            checklist = listOf(item1, item2)
        )
        assertEquals(2, task.checklist.size)
        assertTrue(task.checklist[0].isCompleted)
        assertFalse(task.checklist[1].isCompleted)
        assertFalse(task.isCompleted) // Completing checklist item does not complete entire task
    }

    // ==========================================
    // 2. VOICE TASK INTENT RESOLUTION TESTS
    // ==========================================

    @Test
    fun testVoiceIntent_addTask() {
        val result = TaskCommandParser.parse("Add task")
        assertTrue(result is TaskParseResult.AddTask)
    }

    @Test
    fun testVoiceIntent_addATask() {
        val result = TaskCommandParser.parse("Add a task")
        assertTrue(result is TaskParseResult.AddTask)
        val draft = (result as TaskParseResult.AddTask).draft
        assertEquals("", draft.title) // Does NOT invent title
    }

    @Test
    fun testVoiceIntent_createATask() {
        val result = TaskCommandParser.parse("Create a task")
        assertTrue(result is TaskParseResult.AddTask)
    }

    @Test
    fun testVoiceIntent_newTask() {
        val result = TaskCommandParser.parse("New task")
        assertTrue(result is TaskParseResult.AddTask)
    }

    @Test
    fun testVoiceIntent_newTaskPlease() {
        val result = TaskCommandParser.parse("New task please")
        assertTrue(result is TaskParseResult.AddTask)
    }

    @Test
    fun testVoiceIntent_addSomethingToMyTasks() {
        val result = TaskCommandParser.parse("Add something to my tasks")
        assertTrue(result is TaskParseResult.AddTask)
    }

    @Test
    fun testVoiceIntent_showMyTasks() {
        val result = TaskCommandParser.parse("Show my tasks")
        assertEquals(TaskParseResult.ShowTasks(), result)
    }

    @Test
    fun testVoiceIntent_showTodayTasks() {
        val result = TaskCommandParser.parse("Show today's tasks")
        assertEquals(TaskParseResult.ShowTodayTasks, result)
    }

    @Test
    fun testVoiceIntent_whatDoIHaveToday() {
        val result = TaskCommandParser.parse("What do I have today?")
        assertEquals(TaskParseResult.ShowTodayTasks, result)
    }

    @Test
    fun testVoiceIntent_showCompletedTasks() {
        val result = TaskCommandParser.parse("Show completed tasks")
        assertEquals(TaskParseResult.ShowCompletedTasks, result)
    }

    @Test
    fun testVoiceIntent_showOverdueTasks() {
        val result = TaskCommandParser.parse("Show overdue tasks")
        assertEquals(TaskParseResult.ShowOverdueTasks, result)
    }

    // ==========================================
    // 3. TASK EXTRACTION TESTS
    // ==========================================

    @Test
    fun testExtraction_taskWithTitle() {
        val result = TaskCommandParser.parse("Add a task called Study ML")
        assertTrue(result is TaskParseResult.AddTask)
        val draft = (result as TaskParseResult.AddTask).draft
        assertEquals("Study ML", draft.title)
    }

    @Test
    fun testExtraction_taskWithDate() {
        val result = TaskCommandParser.parse("Add a task to study ML tomorrow")
        assertTrue(result is TaskParseResult.AddTask)
        val draft = (result as TaskParseResult.AddTask).draft
        assertTrue(draft.title.contains("study ML", ignoreCase = true))
        assertEquals("Tomorrow", draft.dueDate)
    }

    @Test
    fun testExtraction_taskWithTime() {
        val result = TaskCommandParser.parse("Add a task to study ML tomorrow at 7 PM")
        assertTrue(result is TaskParseResult.AddTask)
        val draft = (result as TaskParseResult.AddTask).draft
        assertTrue(draft.title.contains("study ML", ignoreCase = true))
        assertEquals("Tomorrow", draft.dueDate)
        assertEquals("19:00", draft.dueTime)
    }

    @Test
    fun testExtraction_taskWithReminder() {
        val result = TaskCommandParser.parse("Remind me to submit my assignment tomorrow at 10 AM")
        assertTrue(result is TaskParseResult.AddTask)
        val draft = (result as TaskParseResult.AddTask).draft
        assertTrue(draft.title.contains("submit my assignment", ignoreCase = true))
        assertEquals("Tomorrow", draft.dueDate)
        assertEquals("10:00", draft.dueTime)
        assertTrue(draft.hasReminder)
    }

    @Test
    fun testExtraction_taskWithReminderOffset() {
        val result = TaskCommandParser.parse("Add a task to study ML tomorrow at 7 PM and remind me 30 minutes before")
        assertTrue(result is TaskParseResult.AddTask)
        val draft = (result as TaskParseResult.AddTask).draft
        assertTrue(draft.title.contains("study ML", ignoreCase = true))
        assertEquals("Tomorrow", draft.dueDate)
        assertEquals("19:00", draft.dueTime)
        assertTrue(draft.hasReminder)
        assertEquals(30, draft.reminderOffsetMinutes)
    }

    @Test
    fun testExtraction_taskWithPriority() {
        val result = TaskCommandParser.parse("Create a high priority task to submit my assignment tomorrow")
        assertTrue(result is TaskParseResult.AddTask)
        val draft = (result as TaskParseResult.AddTask).draft
        assertTrue(draft.title.contains("submit my assignment", ignoreCase = true))
        assertEquals(TaskPriority.HIGH, draft.priority)
        assertEquals("Tomorrow", draft.dueDate)
    }

    @Test
    fun testExtraction_urgentPriority() {
        val result = TaskCommandParser.parse("Add an urgent task to call the professor")
        assertTrue(result is TaskParseResult.AddTask)
        val draft = (result as TaskParseResult.AddTask).draft
        assertTrue(draft.title.contains("call the professor", ignoreCase = true))
        assertEquals(TaskPriority.URGENT, draft.priority)
    }

    @Test
    fun testExtraction_recurrence() {
        val result = TaskCommandParser.parse("Add a task to study every day at 7 PM")
        assertTrue(result is TaskParseResult.AddTask)
        val draft = (result as TaskParseResult.AddTask).draft
        assertTrue(draft.title.contains("study", ignoreCase = true))
        assertEquals(TaskRecurrence.DAILY, draft.recurrence)
        assertEquals("19:00", draft.dueTime)
    }

    @Test
    fun testExtraction_category() {
        val result = TaskCommandParser.parse("Add a study task to revise gradient descent")
        assertTrue(result is TaskParseResult.AddTask)
        val draft = (result as TaskParseResult.AddTask).draft
        assertTrue(draft.title.contains("revise gradient descent", ignoreCase = true))
        assertEquals(TaskCategory.STUDY, draft.category)
    }

    // ==========================================
    // 4. TASK SAFETY & AMBIGUITY TESTS
    // ==========================================

    @Test
    fun testSafety_ambiguousNonTaskSpeechDoesNotCreateTask() {
        val speech1 = TaskCommandParser.parse("Tomorrow is going to be busy")
        assertEquals(TaskParseResult.NotATaskCommand, speech1)

        val speech2 = TaskCommandParser.parse("I have to study tomorrow")
        assertEquals(TaskParseResult.NotATaskCommand, speech2)

        val speech3 = TaskCommandParser.parse("I should probably clean my room")
        assertEquals(TaskParseResult.NotATaskCommand, speech3)
    }

    @Test
    fun testSafety_ambiguousTaskQueryPromptsUser() {
        val speech = TaskCommandParser.parse("Do something with my tasks")
        assertEquals(TaskParseResult.UnknownTaskCommand, speech)
    }

    // ==========================================
    // 5. TASK PERSISTENCE TESTS
    // ==========================================

    @Test
    fun testPersistence_saveAndReloadTask() = runBlocking {
        val task = Task(
            id = "custom_test_task_1",
            title = "Prepare Presentation",
            description = "Draft slides and diagrams",
            dueDate = "2026-10-10",
            dueTime = "14:00",
            priority = TaskPriority.HIGH,
            category = TaskCategory.WORK,
            recurrence = TaskRecurrence.WEEKLY,
            checklist = listOf(
                ChecklistItem(id = "c1", text = "Create slides", isCompleted = true),
                ChecklistItem(id = "c2", text = "Add charts", isCompleted = false)
            )
        )
        taskPersistenceRepo.saveTask(task)

        // Reload fresh repository instance from disk
        val reloadedRepo = TaskPersistenceRepository(baseDir)
        val retrieved = reloadedRepo.getTaskById("custom_test_task_1")

        assertNotNull(retrieved)
        assertEquals("Prepare Presentation", retrieved!!.title)
        assertEquals("Draft slides and diagrams", retrieved.description)
        assertEquals("2026-10-10", retrieved.dueDate)
        assertEquals("14:00", retrieved.dueTime)
        assertEquals(TaskPriority.HIGH, retrieved.priority)
        assertEquals(TaskCategory.WORK, retrieved.category)
        assertEquals(TaskRecurrence.WEEKLY, retrieved.recurrence)
        assertEquals(2, retrieved.checklist.size)
        assertTrue(retrieved.checklist[0].isCompleted)
        assertFalse(retrieved.checklist[1].isCompleted)
    }

    @Test
    fun testPersistence_toggleCompleteAndHistory() = runBlocking {
        val task = Task(id = "task_toggle_test", title = "Solve Homework")
        taskPersistenceRepo.saveTask(task)

        val completed = taskPersistenceRepo.toggleCompleted("task_toggle_test")
        assertNotNull(completed)
        assertEquals(TaskStatus.COMPLETED, completed!!.status)
        assertTrue(completed.isCompleted)
        assertNotNull(completed.completedAt)

        val uncompleted = taskPersistenceRepo.toggleCompleted("task_toggle_test")
        assertNotNull(uncompleted)
        assertEquals(TaskStatus.TODO, uncompleted!!.status)
        assertFalse(uncompleted.isCompleted)
        assertNull(uncompleted.completedAt)
    }

    @Test
    fun testPersistence_deleteTask() = runBlocking {
        val task = Task(id = "task_to_delete", title = "Temporary Task")
        taskPersistenceRepo.saveTask(task)
        assertNotNull(taskPersistenceRepo.getTaskById("task_to_delete"))

        taskPersistenceRepo.deleteTask("task_to_delete")
        assertNull(taskPersistenceRepo.getTaskById("task_to_delete"))
    }

    @Test
    fun testPersistence_rescheduleTask() = runBlocking {
        val task = Task(id = "task_resched", title = "Meeting", dueDate = "Today", dueTime = "10:00")
        taskPersistenceRepo.saveTask(task)

        val updated = taskPersistenceRepo.rescheduleTask("task_resched", "Tomorrow", "15:00")
        assertNotNull(updated)
        assertEquals("Tomorrow", updated!!.dueDate)
        assertEquals("15:00", updated.dueTime)
    }

    // ==========================================
    // 6. EXECUTION POLICY & REGRESSION TESTS
    // ==========================================

    @Test
    fun testExecutionPolicy_taskIntents() {
        assertEquals(ExecutionPolicy.IMMEDIATE, DefaultExecutionPolicyResolver.getExecutionPolicy(CommandIntent.ADD_TASK))
        assertEquals(ExecutionPolicy.IMMEDIATE, DefaultExecutionPolicyResolver.getExecutionPolicy(CommandIntent.SHOW_TASKS))
        assertEquals(ExecutionPolicy.IMMEDIATE, DefaultExecutionPolicyResolver.getExecutionPolicy(CommandIntent.SHOW_TODAY_TASKS))
        assertEquals(ExecutionPolicy.IMMEDIATE, DefaultExecutionPolicyResolver.getExecutionPolicy(CommandIntent.SHOW_COMPLETED_TASKS))
        assertEquals(ExecutionPolicy.IMMEDIATE, DefaultExecutionPolicyResolver.getExecutionPolicy(CommandIntent.SHOW_OVERDUE_TASKS))
        assertEquals(ExecutionPolicy.IMMEDIATE, DefaultExecutionPolicyResolver.getExecutionPolicy(CommandIntent.EDIT_TASK))
        assertEquals(ExecutionPolicy.CONFIRMATION_REQUIRED, DefaultExecutionPolicyResolver.getExecutionPolicy(CommandIntent.COMPLETE_TASK))
        assertEquals(ExecutionPolicy.CONFIRMATION_REQUIRED, DefaultExecutionPolicyResolver.getExecutionPolicy(CommandIntent.DELETE_TASK))
        assertEquals(ExecutionPolicy.CONFIRMATION_REQUIRED, DefaultExecutionPolicyResolver.getExecutionPolicy(CommandIntent.RESCHEDULE_TASK))
    }

    // ==========================================
    // 7. TIME & DATE PARSING AUDIT TESTS
    // ==========================================

    @Test
    fun testExtraction_12AM_isMidnight() {
        val result = TaskCommandParser.parse("Add a task to backup data tomorrow at 12 AM")
        assertTrue(result is TaskParseResult.AddTask)
        val draft = (result as TaskParseResult.AddTask).draft
        assertEquals("00:00", draft.dueTime)
        assertEquals("Tomorrow", draft.dueDate)
    }

    @Test
    fun testExtraction_12PM_isNoon() {
        val result = TaskCommandParser.parse("Add a task to have lunch tomorrow at 12 PM")
        assertTrue(result is TaskParseResult.AddTask)
        val draft = (result as TaskParseResult.AddTask).draft
        assertEquals("12:00", draft.dueTime)
        assertEquals("Tomorrow", draft.dueDate)
    }

    @Test
    fun testExtraction_midnightAndNoonPhrases() {
        val resMidnight = TaskCommandParser.parse("Add a task to submit report at midnight")
        assertTrue(resMidnight is TaskParseResult.AddTask)
        val draftMidnight = (resMidnight as TaskParseResult.AddTask).draft
        assertEquals("00:00", draftMidnight.dueTime)
        assertTrue(draftMidnight.title.contains("submit report", ignoreCase = true))

        val resNoon = TaskCommandParser.parse("Add a task for team sync at noon")
        assertTrue(resNoon is TaskParseResult.AddTask)
        val draftNoon = (resNoon as TaskParseResult.AddTask).draft
        assertEquals("12:00", draftNoon.dueTime)
        assertTrue(draftNoon.title.contains("team sync", ignoreCase = true))
    }

    @Test
    fun testExtraction_6InTheEvening() {
        val result = TaskCommandParser.parse("Add a task to workout today at 6 in the evening")
        assertTrue(result is TaskParseResult.AddTask)
        val draft = (result as TaskParseResult.AddTask).draft
        assertEquals("18:00", draft.dueTime)
        assertEquals("Today", draft.dueDate)
        assertTrue(draft.title.contains("workout", ignoreCase = true))
    }

    @Test
    fun testExtraction_weekdayParsing() {
        val result = TaskCommandParser.parse("Create a task for Friday at 7 PM to review code")
        assertTrue(result is TaskParseResult.AddTask)
        val draft = (result as TaskParseResult.AddTask).draft
        assertEquals("Friday", draft.dueDate)
        assertEquals("19:00", draft.dueTime)
        assertTrue(draft.title.contains("review code", ignoreCase = true))
    }

    @Test
    fun testSafety_additionalConversationalPhrasesRejected() {
        assertEquals(TaskParseResult.NotATaskCommand, TaskCommandParser.parse("I need to study"))
        assertEquals(TaskParseResult.NotATaskCommand, TaskCommandParser.parse("I should study"))
        assertEquals(TaskParseResult.NotATaskCommand, TaskCommandParser.parse("I have an assignment"))
        assertEquals(TaskParseResult.NotATaskCommand, TaskCommandParser.parse("Study is difficult"))
        assertEquals(TaskParseResult.NotATaskCommand, TaskCommandParser.parse("Can you help me?"))
    }

    // ==========================================
    // 8. PERSISTENCE RESILIENCE & CONCURRENCY TESTS
    // ==========================================

    @Test
    fun testPersistence_corruptedJsonEntryPreservesValidTasks() = runBlocking {
        val validTask = Task(id = "valid_task_1", title = "Valid Task")
        taskPersistenceRepo.saveTask(validTask)

        val storageFile = File(baseDir, "tasks_storage.json")
        assertTrue(storageFile.exists())

        // Inject a corrupted element in the JSON array alongside the valid task
        val validJson = TaskPersistenceRepository.taskToJson(validTask).toString()
        val corruptedArray = "[$validJson, { \"corrupted\": true, \"missing_fields\": 123 }]"
        storageFile.writeText(corruptedArray)

        // Reload repo and verify valid task was recovered without crashing
        val reloadedRepo = TaskPersistenceRepository(baseDir)
        val loaded = reloadedRepo.getTaskById("valid_task_1")
        assertNotNull(loaded)
        assertEquals("Valid Task", loaded!!.title)
    }

    @Test
    fun testPersistence_concurrentSaves() = runBlocking {
        val jobs = (1..10).map { i ->
            async(Dispatchers.IO) {
                val t = Task(id = "concurrent_task_$i", title = "Task $i")
                taskPersistenceRepo.saveTask(t)
            }
        }
        jobs.forEach { it.await() }

        val allTasks = taskPersistenceRepo.tasks.value
        for (i in 1..10) {
            assertTrue("Task $i should exist", allTasks.any { it.id == "concurrent_task_$i" })
        }
    }

    @Test
    fun testModel_toTaskInvariants() {
        val draft = TaskDraft(
            title = "Test Invariants",
            dueTime = "10:00",
            hasDueTime = false,
            reminderOffsetMinutes = 15,
            hasReminder = false
        )
        val task = draft.toTask()
        assertEquals(TaskType.STANDARD_TASK, task.type)
        assertNull(task.dueTime)
        assertFalse(task.hasDueTime)
        assertNull(task.reminderOffsetMinutes)
        assertFalse(task.hasReminder)
        assertEquals(TaskStatus.TODO, task.status)
        assertNull(task.completedAt)
    }

    // ==========================================
    // 9. VOICE & WORKFLOW REGRESSION TESTS
    // ==========================================

    @Test
    fun testRegression_directDeviceActionsRemainImmediate() {
        assertEquals(ExecutionPolicy.IMMEDIATE, DefaultExecutionPolicyResolver.getExecutionPolicy(CommandIntent.SWIPE_UP))
        assertEquals(ExecutionPolicy.IMMEDIATE, DefaultExecutionPolicyResolver.getExecutionPolicy(CommandIntent.SWIPE_DOWN))
        assertEquals(ExecutionPolicy.IMMEDIATE, DefaultExecutionPolicyResolver.getExecutionPolicy(CommandIntent.SWIPE_LEFT))
        assertEquals(ExecutionPolicy.IMMEDIATE, DefaultExecutionPolicyResolver.getExecutionPolicy(CommandIntent.SWIPE_RIGHT))
        assertEquals(ExecutionPolicy.IMMEDIATE, DefaultExecutionPolicyResolver.getExecutionPolicy(CommandIntent.GO_BACK))
        assertEquals(ExecutionPolicy.IMMEDIATE, DefaultExecutionPolicyResolver.getExecutionPolicy(CommandIntent.GO_HOME))
        assertEquals(ExecutionPolicy.IMMEDIATE, DefaultExecutionPolicyResolver.getExecutionPolicy(CommandIntent.OPEN_SETTINGS))
        assertEquals(ExecutionPolicy.IMMEDIATE, DefaultExecutionPolicyResolver.getExecutionPolicy(CommandIntent.OPEN_YOUTUBE))
    }

    @Test
    fun testRegression_changeWallpaperRequiresConfirmation() {
        assertEquals(ExecutionPolicy.CONFIRMATION_REQUIRED, DefaultExecutionPolicyResolver.getExecutionPolicy(CommandIntent.CHANGE_WALLPAPER))
    }

    @Test
    fun testRegression_workflowRequiresConfirmation() {
        val workflow = Workflow(id = "wf_custom", name = "My Workflow")
        assertEquals(ExecutionPolicy.CONFIRMATION_REQUIRED, DefaultExecutionPolicyResolver.getWorkflowExecutionPolicy(workflow))
    }

    // ==========================================
    // 10. USER-REQUESTED EXACT PHRASES & FULL FLOWS
    // ==========================================

    @Test
    fun testExactPhrase_addTaskStudyMachineLearning() {
        val res = TaskCommandParser.parse("add task study machine learning")
        assertTrue(res is TaskParseResult.AddTask)
        val draft = (res as TaskParseResult.AddTask).draft
        assertEquals("Study machine learning", draft.title)
    }

    @Test
    fun testExactPhrase_addATaskStudyMachineLearning() {
        val res = TaskCommandParser.parse("add a task study machine learning")
        assertTrue(res is TaskParseResult.AddTask)
        val draft = (res as TaskParseResult.AddTask).draft
        assertEquals("Study machine learning", draft.title)
    }

    @Test
    fun testExactPhrase_createTaskFinishProject() {
        val res = TaskCommandParser.parse("create task finish project")
        assertTrue(res is TaskParseResult.AddTask)
        val draft = (res as TaskParseResult.AddTask).draft
        assertEquals("Finish project", draft.title)
    }

    @Test
    fun testExactPhrase_remindMeToStudy() {
        val res = TaskCommandParser.parse("remind me to study")
        assertTrue(res is TaskParseResult.AddTask)
        val draft = (res as TaskParseResult.AddTask).draft
        assertEquals("Study", draft.title)
        assertTrue(draft.hasReminder)
    }

    @Test
    fun testExactPhrase_remindMeToStudyTomorrow() {
        val res = TaskCommandParser.parse("remind me to study tomorrow")
        assertTrue(res is TaskParseResult.AddTask)
        val draft = (res as TaskParseResult.AddTask).draft
        assertEquals("Study", draft.title)
        assertEquals("Tomorrow", draft.dueDate)
        assertTrue(draft.hasReminder)
    }

    @Test
    fun testExactPhrase_addTaskStudyTomorrowAt7Pm() {
        val res = TaskCommandParser.parse("add task study tomorrow at 7 pm")
        assertTrue(res is TaskParseResult.AddTask)
        val draft = (res as TaskParseResult.AddTask).draft
        assertEquals("Study", draft.title)
        assertEquals("Tomorrow", draft.dueDate)
        assertEquals("19:00", draft.dueTime)
        assertTrue(draft.hasDueTime)
    }

    @Test
    fun testExactPhrase_addTaskStudyAt1900() {
        val res = TaskCommandParser.parse("add task study at 19:00")
        assertTrue(res is TaskParseResult.AddTask)
        val draft = (res as TaskParseResult.AddTask).draft
        assertEquals("Study", draft.title)
        assertEquals("19:00", draft.dueTime)
        assertTrue(draft.hasDueTime)
    }

    @Test
    fun testExactPhrase_addTaskHighPriorityStudyTomorrow() {
        val res = TaskCommandParser.parse("add task high priority study tomorrow")
        assertTrue(res is TaskParseResult.AddTask)
        val draft = (res as TaskParseResult.AddTask).draft
        assertEquals("Study", draft.title)
        assertEquals(TaskPriority.HIGH, draft.priority)
        assertEquals("Tomorrow", draft.dueDate)
    }

    @Test
    fun testExactPhrase_addTaskStudyEveryDay() {
        val res = TaskCommandParser.parse("add task study every day")
        assertTrue(res is TaskParseResult.AddTask)
        val draft = (res as TaskParseResult.AddTask).draft
        assertEquals("Study", draft.title)
        assertEquals(TaskRecurrence.DAILY, draft.recurrence)
    }

    @Test
    fun testExactPhrase_addTaskBuyGroceriesToday() {
        val res = TaskCommandParser.parse("add task buy groceries today")
        assertTrue(res is TaskParseResult.AddTask)
        val draft = (res as TaskParseResult.AddTask).draft
        assertEquals("Buy groceries", draft.title)
        assertEquals("Today", draft.dueDate)
    }

    @Test
    fun testExactPhrase_addTaskBuyGroceriesTomorrowAt6PM() {
        val res = TaskCommandParser.parse("add task buy groceries tomorrow at 6 PM")
        assertTrue(res is TaskParseResult.AddTask)
        val draft = (res as TaskParseResult.AddTask).draft
        assertEquals("Buy groceries", draft.title)
        assertEquals("Tomorrow", draft.dueDate)
        assertEquals("18:00", draft.dueTime)
        assertTrue(draft.hasDueTime)
    }

    @Test
    fun testExactPhrase_emptyAddTaskCommand() {
        val res = TaskCommandParser.parse("add task")
        assertTrue(res is TaskParseResult.AddTask)
        val draft = (res as TaskParseResult.AddTask).draft
        assertEquals("", draft.title)
        assertTrue(draft.title.isBlank())
    }

    @Test
    fun testExactPhrase_unrelatedNonTaskCommandsNotTreatedAsTasks() {
        assertEquals(TaskParseResult.NotATaskCommand, TaskCommandParser.parse("random unknown command"))
        assertEquals(TaskParseResult.NotATaskCommand, TaskCommandParser.parse("change my wallpaper"))
        assertEquals(TaskParseResult.NotATaskCommand, TaskCommandParser.parse("open settings"))
        assertEquals(TaskParseResult.NotATaskCommand, TaskCommandParser.parse("swipe up"))
        assertEquals(TaskParseResult.NotATaskCommand, TaskCommandParser.parse("what is machine learning?"))
    }

    @Test
    fun testConversationalFollowUp_addTaskThenProvideTitleThenConfirm() = runBlocking {
        val mockAutomationEngine = com.taskscheduling.app.automation.mock.MockAutomationEngine()
        val mockActionExecutor = object : com.taskscheduling.app.assistant.AssistantActionExecutor {
            override suspend fun executeAction(intent: CommandIntent, currentProfile: com.taskscheduling.app.domain.model.DeviceProfile): com.taskscheduling.app.assistant.ActionResult =
                com.taskscheduling.app.assistant.ActionResult.Success("Done")
        }
        val testScope = kotlinx.coroutines.CoroutineScope(Dispatchers.Unconfined)
        val controller = com.taskscheduling.app.assistant.OfflineAssistantController(
            commandResolver = commandResolver,
            workflowResolver = workflowResolver,
            automationEngine = mockAutomationEngine,
            actionExecutor = mockActionExecutor,
            persistenceRepository = workflowRepo,
            taskRepository = taskRepository,
            taskNotificationScheduler = null,
            deviceProfileProvider = { com.taskscheduling.app.domain.model.DeviceProfile(screenWidth = 1200, screenHeight = 2000) },
            coroutineScope = testScope
        )

        // Step 1: User says "add task"
        controller.processCommand("add task")
        assertTrue(controller.uiState.value is com.taskscheduling.app.assistant.AssistantUiState.TaskDraftReady)
        val draft = (controller.uiState.value as com.taskscheduling.app.assistant.AssistantUiState.TaskDraftReady).draft
        assertEquals("", draft.title)

        // Step 2: User says "study machine learning"
        controller.processCommand("study machine learning")
        assertTrue(controller.uiState.value is com.taskscheduling.app.assistant.AssistantUiState.ConfirmationRequired)
        val confState = controller.uiState.value as com.taskscheduling.app.assistant.AssistantUiState.ConfirmationRequired
        assertEquals("Study machine learning", confState.taskDraft?.title)

        // Step 3: User says "yes"
        controller.processCommand("yes")
        controller.lastActionJob?.join()
        assertTrue(controller.uiState.value is com.taskscheduling.app.assistant.AssistantUiState.TaskActionCompleted)

        // Verify task exists in repository
        val tasks = taskPersistenceRepo.tasks.value
        assertTrue("Task should be present in repository", tasks.any { it.title == "Study machine learning" })
    }

    @Test
    fun testDuplicatePrevention_duplicateVoskFinalResultsProcessedOnce() = runBlocking {
        val mockAutomationEngine = com.taskscheduling.app.automation.mock.MockAutomationEngine()
        val mockActionExecutor = object : com.taskscheduling.app.assistant.AssistantActionExecutor {
            override suspend fun executeAction(intent: CommandIntent, currentProfile: com.taskscheduling.app.domain.model.DeviceProfile): com.taskscheduling.app.assistant.ActionResult =
                com.taskscheduling.app.assistant.ActionResult.Success("Done")
        }
        val testScope = kotlinx.coroutines.CoroutineScope(Dispatchers.Unconfined)
        val controller = com.taskscheduling.app.assistant.OfflineAssistantController(
            commandResolver = commandResolver,
            workflowResolver = workflowResolver,
            automationEngine = mockAutomationEngine,
            actionExecutor = mockActionExecutor,
            persistenceRepository = workflowRepo,
            taskRepository = taskRepository,
            taskNotificationScheduler = null,
            deviceProfileProvider = { com.taskscheduling.app.domain.model.DeviceProfile(screenWidth = 1200, screenHeight = 2000) },
            coroutineScope = testScope
        )

        val initialCount = taskPersistenceRepo.tasks.value.count { it.title == "Buy milk" }

        // Process final command
        controller.processCommand("add task buy milk")
        assertTrue(controller.uiState.value is com.taskscheduling.app.assistant.AssistantUiState.ConfirmationRequired)
        val job1 = controller.confirmPendingAction()
        job1?.join()

        val afterFirstSaveCount = taskPersistenceRepo.tasks.value.count { it.title == "Buy milk" }
        assertEquals(initialCount + 1, afterFirstSaveCount)

        // Repeating confirmation or calling confirm when no pending action should not duplicate
        val job2 = controller.confirmPendingAction()
        job2?.join()
        val afterRepeatCount = taskPersistenceRepo.tasks.value.count { it.title == "Buy milk" }
        assertEquals(afterFirstSaveCount, afterRepeatCount)
    }
}
