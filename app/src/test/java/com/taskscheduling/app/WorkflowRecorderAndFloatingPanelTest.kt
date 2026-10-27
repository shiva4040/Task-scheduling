package com.taskscheduling.app

import com.taskscheduling.app.data.repository.TaskPersistenceRepository
import com.taskscheduling.app.data.repository.TaskRepositoryImpl
import com.taskscheduling.app.data.repository.WorkflowPersistenceRepository
import com.taskscheduling.app.domain.model.ActionType
import com.taskscheduling.app.domain.model.CoordinateAction
import com.taskscheduling.app.domain.model.DeviceProfile
import com.taskscheduling.app.domain.model.SessionState
import com.taskscheduling.app.domain.model.WorkflowStep
import com.taskscheduling.app.recording.RecordingManager
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
import java.nio.file.Files

class WorkflowRecorderAndFloatingPanelTest {

    private lateinit var tempDir: File
    private lateinit var persistenceRepo: WorkflowPersistenceRepository
    private lateinit var taskPersistenceRepo: TaskPersistenceRepository
    private lateinit var taskRepo: TaskRepositoryImpl
    private val testProfile = DeviceProfile(screenWidth = 1080, screenHeight = 2400)

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("workflow_recorder_test").toFile()
        persistenceRepo = WorkflowPersistenceRepository(tempDir)
        taskPersistenceRepo = TaskPersistenceRepository(tempDir, persistenceRepo)
        taskRepo = TaskRepositoryImpl(persistenceRepo, taskPersistenceRepo)
        RecordingManager.reset()
        RecordingManager.initRepository(persistenceRepo)
    }

    @After
    fun tearDown() {
        RecordingManager.reset()
        tempDir.deleteRecursively()
    }

    @Test
    fun testRecordMultipleActionsInSequence() {
        RecordingManager.startRecording(
            workflowId = "wf_multi_action",
            workflowName = "Multi Action Routine",
            deviceProfile = testProfile
        )

        // 1. Tap
        RecordingManager.recordTap(100, 200)
        // 2. Long Press (different coordinate so not debounced)
        RecordingManager.recordLongPress(300, 400, 900L)
        // 3. Swipe
        RecordingManager.recordSwipe(100, 800, 100, 200, 400L)
        // 4. Text Input
        RecordingManager.recordTextInput("Hello World")
        // 5. Wait
        RecordingManager.recordWait(1500L)
        // 6. Back
        RecordingManager.recordBack()
        // 7. Home
        RecordingManager.recordHome()

        val steps = RecordingManager.recordedSteps.value
        assertEquals(7, steps.size)

        // Verify sequence ordering and types
        assertEquals(ActionType.TAP, steps[0].actionType)
        assertEquals(1, steps[0].order)
        assertEquals(100, steps[0].coordinate?.x)
        assertEquals(200, steps[0].coordinate?.y)

        assertEquals(ActionType.LONG_PRESS, steps[1].actionType)
        assertEquals(2, steps[1].order)
        assertEquals(900L, steps[1].duration)

        assertEquals(ActionType.SWIPE, steps[2].actionType)
        assertEquals(3, steps[2].order)
        assertEquals(100, steps[2].coordinate?.x)
        assertEquals(800, steps[2].coordinate?.y)
        assertEquals(100, steps[2].coordinate?.endX)
        assertEquals(200, steps[2].coordinate?.endY)

        assertEquals(ActionType.TEXT_INPUT, steps[3].actionType)
        assertEquals(4, steps[3].order)
        assertEquals("Hello World", steps[3].textInput)

        assertEquals(ActionType.WAIT, steps[4].actionType)
        assertEquals(5, steps[4].order)
        assertEquals(1500L, steps[4].duration)

        assertEquals(ActionType.BACK, steps[5].actionType)
        assertEquals(6, steps[5].order)

        assertEquals(ActionType.HOME, steps[6].actionType)
        assertEquals(7, steps[6].order)

        // Verify all IDs are non-empty and unique
        val ids = steps.map { it.id }.toSet()
        assertEquals(7, ids.size)
    }

    @Test
    fun testPauseAndResumeBehavior() {
        RecordingManager.startRecording(
            workflowId = "wf_pause_test",
            workflowName = "Pause Test",
            deviceProfile = testProfile
        )

        RecordingManager.recordTap(100, 100)
        assertEquals(1, RecordingManager.recordedSteps.value.size)

        // Pause
        val paused = RecordingManager.pauseRecording()
        assertTrue(paused)
        assertEquals(SessionState.PAUSED, RecordingManager.sessionState.value)
        assertFalse(RecordingManager.isRecording.value)

        // Attempt action while paused — MUST NOT be captured!
        RecordingManager.recordTap(200, 200)
        assertEquals(1, RecordingManager.recordedSteps.value.size)

        // Resume
        val resumed = RecordingManager.resumeActiveRecording()
        assertTrue(resumed)
        assertEquals(SessionState.RECORDING, RecordingManager.sessionState.value)
        assertTrue(RecordingManager.isRecording.value)

        // Action after resume — MUST be captured
        RecordingManager.recordTap(300, 300)
        assertEquals(2, RecordingManager.recordedSteps.value.size)
    }

    @Test
    fun testUndoLastStep() {
        RecordingManager.startRecording(
            workflowId = "wf_undo_test",
            deviceProfile = testProfile
        )

        RecordingManager.recordTap(100, 100)
        RecordingManager.recordWait(1000L)
        RecordingManager.recordBack()
        assertEquals(3, RecordingManager.recordedSteps.value.size)

        val undone = RecordingManager.undoLastStep()
        assertNotNull(undone)
        assertEquals(ActionType.BACK, undone?.actionType)
        assertEquals(2, RecordingManager.recordedSteps.value.size)
        assertEquals(1, RecordingManager.recordedSteps.value[0].order)
        assertEquals(2, RecordingManager.recordedSteps.value[1].order)

        RecordingManager.undoLastStep()
        RecordingManager.undoLastStep()
        assertEquals(0, RecordingManager.recordedSteps.value.size)

        // Undoing on empty list safely returns null
        val nullUndone = RecordingManager.undoLastStep()
        assertNull(nullUndone)
    }

    @Test
    fun testFinishAndCancelBehavior() {
        RecordingManager.startRecording(
            workflowId = "wf_finish_cancel",
            deviceProfile = testProfile
        )

        RecordingManager.recordTap(50, 50)
        val finishedSteps = RecordingManager.finishRecording()
        assertEquals(1, finishedSteps.size)
        assertEquals(SessionState.REVIEW, RecordingManager.sessionState.value)
        assertFalse(RecordingManager.isRecording.value)

        // Cancel discards session
        RecordingManager.cancelRecording()
        assertEquals(SessionState.IDLE, RecordingManager.sessionState.value)
        assertEquals(0, RecordingManager.recordedSteps.value.size)
    }

    @Test
    fun testDuplicateEventPrevention() {
        RecordingManager.startRecording(
            workflowId = "wf_debounce",
            deviceProfile = testProfile
        )

        // Rapid duplicate taps at identical coordinate
        RecordingManager.recordTap(500, 600)
        RecordingManager.recordTap(500, 600) // Immediate duplicate, within 250ms

        assertEquals(1, RecordingManager.recordedSteps.value.size)
    }

    @Test
    fun testStepOrderingAndStableIds() {
        RecordingManager.startRecording(
            workflowId = "wf_ordering",
            deviceProfile = testProfile
        )

        RecordingManager.recordTap(10, 10)
        RecordingManager.recordWait(1000L)
        RecordingManager.recordBack()

        val id0 = RecordingManager.recordedSteps.value[0].id
        val id1 = RecordingManager.recordedSteps.value[1].id
        val id2 = RecordingManager.recordedSteps.value[2].id

        // Move first step (Tap) to the end (index 2)
        RecordingManager.moveStep(0, 2)

        val reordered = RecordingManager.recordedSteps.value
        assertEquals(3, reordered.size)
        assertEquals(id1, reordered[0].id)
        assertEquals(1, reordered[0].order)
        assertEquals(id2, reordered[1].id)
        assertEquals(2, reordered[1].order)
        assertEquals(id0, reordered[2].id)
        assertEquals(3, reordered[2].order)
    }

    @Test
    fun testEditingExactSelectedStep() {
        RecordingManager.startRecording(
            workflowId = "wf_editing",
            deviceProfile = testProfile
        )

        RecordingManager.recordTap(100, 100)
        RecordingManager.recordTap(200, 200)

        val firstStepId = RecordingManager.recordedSteps.value[0].id
        val secondStepId = RecordingManager.recordedSteps.value[1].id

        // Edit first step specifically
        val modifiedFirst = RecordingManager.recordedSteps.value[0].copyWithSync(
            label = "Edited Search Button",
            coordinate = CoordinateAction(150, 150)
        )
        RecordingManager.updateStep(modifiedFirst)

        val steps = RecordingManager.recordedSteps.value
        assertEquals("Edited Search Button", steps[0].label)
        assertEquals(150, steps[0].coordinate?.x)
        assertEquals(150, steps[0].coordinate?.y)
        assertEquals(firstStepId, steps[0].id)

        // Second step must remain completely untouched
        assertEquals(secondStepId, steps[1].id)
        assertEquals(200, steps[1].coordinate?.x)
    }

    @Test
    fun testWorkflowPersistenceAndReopening() = runBlocking {
        RecordingManager.startRecording(
            workflowId = "wf_persist_123",
            workflowName = "Daily Notes Workflow",
            deviceProfile = testProfile
        )

        RecordingManager.recordTap(400, 500)
        RecordingManager.recordTextInput("Daily journal entry")
        RecordingManager.recordWait(800L)

        // Save
        val saved = RecordingManager.saveWorkflow(
            name = "Daily Notes Workflow",
            description = "Opens notes and types journal",
            persistenceRepo = persistenceRepo,
            taskRepo = taskRepo
        )

        assertNotNull(saved)
        assertEquals("Daily Notes Workflow", saved?.name)
        assertEquals(3, saved?.steps?.size)
        assertEquals(SessionState.SAVED, RecordingManager.sessionState.value)

        // Reopen persistence repository fresh from disk
        val reloadedRepo = WorkflowPersistenceRepository(tempDir)
        val loaded = reloadedRepo.getWorkflow("wf_persist_123")
        assertNotNull(loaded)
        assertEquals("Daily Notes Workflow", loaded?.name)
        assertEquals(3, loaded?.steps?.size)
        assertEquals(ActionType.TAP, loaded?.steps?.get(0)?.actionType)
        assertEquals(ActionType.TEXT_INPUT, loaded?.steps?.get(1)?.actionType)
        assertEquals("Daily journal entry", loaded?.steps?.get(1)?.textInput)
        assertEquals(ActionType.WAIT, loaded?.steps?.get(2)?.actionType)

        // Confirm corresponding task was created in task repository
        val task = taskRepo.getTaskById("wf_persist_123")
        assertNotNull(task)
        assertEquals("Daily Notes Workflow", task?.title)
    }

    @Test
    fun testEmptyWorkflowValidation() = runBlocking {
        RecordingManager.startRecording(
            workflowId = "wf_empty",
            deviceProfile = testProfile
        )

        // Try to save with 0 steps
        val resultNoSteps = RecordingManager.saveWorkflow(
            name = "Empty Workflow",
            persistenceRepo = persistenceRepo,
            taskRepo = taskRepo
        )
        assertNull("Empty workflow must not be saved", resultNoSteps)

        // Add 1 step, but try to save with blank name
        RecordingManager.recordTap(10, 10)

        val resultBlankName = RecordingManager.saveWorkflow(
            name = "   ",
            persistenceRepo = persistenceRepo,
            taskRepo = taskRepo
        )
        assertNull("Workflow with blank name must not be saved", resultBlankName)
    }

    @Test
    fun testDuplicateSessionPrevention() {
        val startedFirst = RecordingManager.startRecording(
            workflowId = "wf_first",
            deviceProfile = testProfile
        )
        assertTrue(startedFirst)

        // Attempting to start another session while actively recording MUST be rejected
        val startedSecond = RecordingManager.startRecording(
            workflowId = "wf_second",
            deviceProfile = testProfile
        )
        assertFalse("Simultaneous recording session must be prevented", startedSecond)
    }

    @Test
    fun testStepDuplication() {
        RecordingManager.startRecording(
            workflowId = "wf_duplicate",
            deviceProfile = testProfile
        )

        RecordingManager.recordTap(100, 200)
        val origId = RecordingManager.recordedSteps.value[0].id

        RecordingManager.duplicateStep(origId)
        val steps = RecordingManager.recordedSteps.value
        assertEquals(2, steps.size)
        assertEquals(origId, steps[0].id)
        assertFalse(origId == steps[1].id)
        assertEquals(1, steps[0].order)
        assertEquals(2, steps[1].order)
        assertEquals(ActionType.TAP, steps[1].actionType)
        assertEquals(100, steps[1].coordinate?.x)
        assertEquals(200, steps[1].coordinate?.y)
    }
}
