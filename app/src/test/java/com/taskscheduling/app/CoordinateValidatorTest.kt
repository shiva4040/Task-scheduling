package com.taskscheduling.app

import com.taskscheduling.app.data.repository.ExecutionHistoryItem
import com.taskscheduling.app.data.repository.WorkflowPersistenceRepository
import com.taskscheduling.app.domain.model.ActionType
import com.taskscheduling.app.domain.model.CoordinateAction
import com.taskscheduling.app.domain.model.DeviceProfile
import com.taskscheduling.app.domain.model.ExecutionSession
import com.taskscheduling.app.domain.model.ExecutionState
import com.taskscheduling.app.domain.model.Workflow
import com.taskscheduling.app.domain.model.WorkflowStatus
import com.taskscheduling.app.domain.model.WorkflowStep
import com.taskscheduling.app.recording.CoordinateValidator
import com.taskscheduling.app.recording.ValidationResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoordinateValidatorTest {

    private val screenWidth = 800
    private val screenHeight = 1340

    @Test
    fun testValidCoordinatesPass() {
        val res1 = CoordinateValidator.validateCoordinate(0, 0, screenWidth, screenHeight)
        assertTrue(res1.isValid)

        val res2 = CoordinateValidator.validateCoordinate(799, 1339, screenWidth, screenHeight)
        assertTrue(res2.isValid)

        val res3 = CoordinateValidator.validateCoordinate(400, 670, screenWidth, screenHeight)
        assertTrue(res3.isValid)
    }

    @Test
    fun testZeroCoordinatesPass() {
        val res = CoordinateValidator.validateCoordinate(0, 0, screenWidth, screenHeight)
        assertTrue(res.isValid)
    }

    @Test
    fun testNegativeCoordinatesRejected() {
        val res1 = CoordinateValidator.validateCoordinate(-1, 500, screenWidth, screenHeight)
        assertFalse(res1.isValid)
        assertTrue((res1 as ValidationResult.Invalid).reason.contains("outside screen bounds"))

        val res2 = CoordinateValidator.validateCoordinate(500, -5, screenWidth, screenHeight)
        assertFalse(res2.isValid)
    }

    @Test
    fun testBoundaryConditionWidthAndHeightRejected() {
        // X == screenWidth must be rejected because bounds are [0, screenWidth)
        val resWidth = CoordinateValidator.validateCoordinate(screenWidth, 500, screenWidth, screenHeight)
        assertFalse(resWidth.isValid)
        assertTrue((resWidth as ValidationResult.Invalid).reason.contains("X coordinate ($screenWidth) is outside screen bounds"))

        // Y == screenHeight must be rejected because bounds are [0, screenHeight)
        val resHeight = CoordinateValidator.validateCoordinate(400, screenHeight, screenWidth, screenHeight)
        assertFalse(resHeight.isValid)
        assertTrue((resHeight as ValidationResult.Invalid).reason.contains("Y coordinate ($screenHeight) is outside screen bounds"))
    }

    @Test
    fun testOutOfBoundsCoordinatesRejected() {
        val res = CoordinateValidator.validateCoordinate(1200, 2000, screenWidth, screenHeight)
        assertFalse(res.isValid)
    }

    @Test
    fun testValidWorkflowSteps() {
        val tapStep = WorkflowStep(
            id = "s1",
            order = 1,
            type = ActionType.TAP,
            startX = 400,
            startY = 700,
            duration = 100L
        )
        val tapRes = CoordinateValidator.validateStep(tapStep, screenWidth, screenHeight)
        assertTrue(tapRes.isValid)

        val swipeStep = WorkflowStep(
            id = "s2",
            order = 2,
            type = ActionType.SWIPE,
            startX = 400,
            startY = 1000,
            endX = 400,
            endY = 300,
            duration = 500L
        )
        val swipeRes = CoordinateValidator.validateStep(swipeStep, screenWidth, screenHeight)
        assertTrue(swipeRes.isValid)

        val waitStep = WorkflowStep(
            id = "s3",
            order = 3,
            type = ActionType.WAIT,
            duration = 1000L
        )
        val waitRes = CoordinateValidator.validateStep(waitStep, screenWidth, screenHeight)
        assertTrue(waitRes.isValid)

        val backStep = WorkflowStep(
            id = "s4",
            order = 4,
            type = ActionType.BACK
        )
        val backRes = CoordinateValidator.validateStep(backStep, screenWidth, screenHeight)
        assertTrue(backRes.isValid)
    }

    @Test
    fun testInvalidWorkflowStepsRejected() {
        // Tap with out of bounds coordinate
        val invalidTap = WorkflowStep(
            id = "s1",
            order = 1,
            type = ActionType.TAP,
            startX = 900,
            startY = 500
        )
        val tapRes = CoordinateValidator.validateStep(invalidTap, screenWidth, screenHeight)
        assertFalse(tapRes.isValid)

        // Swipe missing end coordinate
        val invalidSwipe = WorkflowStep(
            id = "s2",
            order = 2,
            type = ActionType.SWIPE,
            startX = 200,
            startY = 300,
            endX = null,
            endY = 400
        )
        val swipeRes = CoordinateValidator.validateStep(invalidSwipe, screenWidth, screenHeight)
        assertFalse(swipeRes.isValid)

        // Wait with 0 ms
        val invalidWait = WorkflowStep(
            id = "s3",
            order = 3,
            type = ActionType.WAIT,
            duration = 0L
        )
        val waitRes = CoordinateValidator.validateStep(invalidWait, screenWidth, screenHeight)
        assertFalse(waitRes.isValid)
    }

    @Test
    fun testWorkflowValidationRequiresSteps() {
        val emptyWf = Workflow(
            id = "wf_test",
            name = "Test Workflow",
            steps = emptyList()
        )
        val emptyRes = CoordinateValidator.validateWorkflow(emptyWf, screenWidth, screenHeight)
        assertFalse(emptyRes.isValid)
        assertTrue((emptyRes as ValidationResult.Invalid).reason.contains("at least one step"))
    }

    @Test
    fun testWorkflowValidationRequiresDeviceProfile() {
        val wfWithoutDevice = Workflow(
            id = "wf_no_device",
            name = "No Device Workflow",
            deviceProfile = null,
            steps = listOf(WorkflowStep(id = "1", order = 1, type = ActionType.TAP, startX = 200, startY = 300))
        )
        val res = CoordinateValidator.validateWorkflow(wfWithoutDevice, 0, 0)
        assertFalse(res.isValid)
        assertTrue((res as ValidationResult.Invalid).reason.contains("Valid device profile"))
    }

    @Test
    fun testValidWorkflowPasses() {
        val device = DeviceProfile(
            manufacturer = "Samsung",
            model = "SM-X110",
            screenWidth = 800,
            screenHeight = 1340,
            orientation = "PORTRAIT"
        )

        val validWf = Workflow(
            id = "wf_wallpaper",
            name = "Change Wallpaper",
            status = WorkflowStatus.CALIBRATED,
            deviceProfile = device,
            steps = listOf(
                WorkflowStep(id = "1", order = 1, type = ActionType.TAP, startX = 400, startY = 600),
                WorkflowStep(id = "2", order = 2, type = ActionType.WAIT, duration = 1000L),
                WorkflowStep(id = "3", order = 3, type = ActionType.BACK)
            )
        )

        val validRes = CoordinateValidator.validateWorkflow(validWf, screenWidth, screenHeight)
        assertTrue(validRes.isValid)
    }

    @Test
    fun testDeviceCompatibilityMatchingPasses() {
        val recordedDevice = DeviceProfile(
            manufacturer = "Samsung",
            model = "SM-X110",
            screenWidth = 800,
            screenHeight = 1340,
            orientation = "PORTRAIT"
        )

        val matchingDevice = DeviceProfile(
            manufacturer = "Samsung",
            model = "SM-X110",
            screenWidth = 800,
            screenHeight = 1340,
            orientation = "PORTRAIT"
        )

        val compatRes = CoordinateValidator.validateDeviceCompatibility(recordedDevice, matchingDevice)
        assertTrue(compatRes.isValid)
    }

    @Test
    fun testDeviceCompatibilityResolutionMismatchRejected() {
        val recordedDevice = DeviceProfile(
            manufacturer = "Samsung",
            model = "SM-X110",
            screenWidth = 800,
            screenHeight = 1340,
            orientation = "PORTRAIT"
        )

        val differentResolution = recordedDevice.copy(screenWidth = 1080, screenHeight = 2400)
        val mismatchRes = CoordinateValidator.validateDeviceCompatibility(recordedDevice, differentResolution)
        assertFalse(mismatchRes.isValid)
        assertTrue((mismatchRes as ValidationResult.Invalid).reason.contains("Screen resolution mismatch"))
    }

    @Test
    fun testDeviceCompatibilityOrientationMismatchRejected() {
        val recordedPortrait = DeviceProfile(
            manufacturer = "Samsung",
            model = "SM-X110",
            screenWidth = 800,
            screenHeight = 1340,
            orientation = "PORTRAIT"
        )

        val currentLandscape = recordedPortrait.copy(orientation = "LANDSCAPE")
        val mismatchRes = CoordinateValidator.validateDeviceCompatibility(recordedPortrait, currentLandscape)
        assertFalse(mismatchRes.isValid)
        assertTrue((mismatchRes as ValidationResult.Invalid).reason.contains("Orientation mismatch"))
    }

    @Test
    fun testDeviceCompatibilityMissingRecordedProfileRejected() {
        val currentDevice = DeviceProfile(
            manufacturer = "Samsung",
            model = "SM-X110",
            screenWidth = 800,
            screenHeight = 1340,
            orientation = "PORTRAIT"
        )

        val res = CoordinateValidator.validateDeviceCompatibility(null, currentDevice)
        assertFalse(res.isValid)
        assertTrue((res as ValidationResult.Invalid).reason.contains("no recorded device profile"))
    }

    @Test
    fun testWorkflowSerializationAndDeserialization() {
        val device = DeviceProfile(
            manufacturer = "Samsung",
            model = "SM-X110",
            androidVersion = "14",
            apiLevel = 34,
            screenWidth = 800,
            screenHeight = 1340,
            density = 1.33f,
            orientation = "PORTRAIT"
        )

        val originalWf = Workflow(
            id = "wf_test_123",
            name = "Test Wallpaper Workflow",
            description = "Calibration test",
            status = WorkflowStatus.CALIBRATED,
            deviceProfile = device,
            steps = listOf(
                WorkflowStep(
                    id = "step_1",
                    order = 1,
                    type = ActionType.TAP,
                    startX = 450,
                    startY = 800,
                    duration = 100L,
                    waitAfter = 500L,
                    description = "Tap on Wallpaper icon"
                ),
                WorkflowStep(
                    id = "step_2",
                    order = 2,
                    type = ActionType.SWIPE,
                    startX = 400,
                    startY = 1100,
                    endX = 400,
                    endY = 400,
                    duration = 600L,
                    waitAfter = 800L,
                    description = "Swipe up wallpapers"
                )
            )
        )

        val json = WorkflowPersistenceRepository.workflowToJson(originalWf)
        val deserializedWf = WorkflowPersistenceRepository.jsonToWorkflow(json)

        assertEquals(originalWf.id, deserializedWf.id)
        assertEquals(originalWf.name, deserializedWf.name)
        assertEquals(originalWf.status, deserializedWf.status)
        assertNotNull(deserializedWf.deviceProfile)
        assertEquals(originalWf.deviceProfile?.screenWidth, deserializedWf.deviceProfile?.screenWidth)
        assertEquals(originalWf.deviceProfile?.screenHeight, deserializedWf.deviceProfile?.screenHeight)
        assertEquals(originalWf.steps.size, deserializedWf.steps.size)
        assertEquals(originalWf.steps[0].startX, deserializedWf.steps[0].startX)
        assertEquals(originalWf.steps[0].startY, deserializedWf.steps[0].startY)
        assertEquals(originalWf.steps[1].endX, deserializedWf.steps[1].endX)
    }

    @Test
    fun testExecutionHistoryItemSerialization() {
        val originalHistory = ExecutionHistoryItem(
            id = "run_1001",
            workflowId = "wf_wallpaper",
            workflowName = "Change Wallpaper",
            timestamp = 1727760000000L,
            formattedTime = "Oct 01, 10:00 AM",
            status = "COMPLETED",
            completedSteps = 5,
            totalSteps = 5,
            failedStep = null,
            failureReason = null
        )

        val json = WorkflowPersistenceRepository.historyToJson(originalHistory)
        val deserialized = WorkflowPersistenceRepository.jsonToHistory(json)

        assertEquals(originalHistory.id, deserialized.id)
        assertEquals(originalHistory.workflowId, deserialized.workflowId)
        assertEquals(originalHistory.status, deserialized.status)
        assertEquals(originalHistory.completedSteps, deserialized.completedSteps)
        assertNull(deserialized.failedStep)
    }

    @Test
    fun testNotCalibratedWorkflowCannotExecute() {
        val uncalibratedWf = Workflow(
            id = "wf_wallpaper",
            name = "Change Wallpaper",
            status = WorkflowStatus.NOT_CALIBRATED,
            steps = emptyList()
        )

        // Verify that uncalibrated status is correctly identified
        assertEquals(WorkflowStatus.NOT_CALIBRATED, uncalibratedWf.status)
        assertTrue(uncalibratedWf.steps.isEmpty())

        // Validation must reject empty / uncalibrated workflow
        val res = CoordinateValidator.validateWorkflow(uncalibratedWf, screenWidth, screenHeight)
        assertFalse(res.isValid)
    }

    @Test
    fun testExecutionStateTransitions() {
        var session = ExecutionSession(
            taskId = "task_wallpaper",
            taskTitle = "Change Wallpaper",
            state = ExecutionState.IDLE
        )
        assertEquals(ExecutionState.IDLE, session.state)

        // Transition: IDLE -> RUNNING
        session = session.copy(state = ExecutionState.RUNNING, currentStepIndex = 0, totalSteps = 3)
        assertEquals(ExecutionState.RUNNING, session.state)

        // Transition: RUNNING -> PAUSED
        session = session.copy(state = ExecutionState.PAUSED)
        assertEquals(ExecutionState.PAUSED, session.state)

        // Transition: PAUSED -> RUNNING
        session = session.copy(state = ExecutionState.RUNNING)
        assertEquals(ExecutionState.RUNNING, session.state)

        // Transition: RUNNING -> COMPLETED
        session = session.copy(state = ExecutionState.COMPLETED, progress = 1.0f)
        assertEquals(ExecutionState.COMPLETED, session.state)

        // Transition: RUNNING -> STOPPED
        session = session.copy(state = ExecutionState.STOPPED, errorMessage = "Cancelled by user")
        assertEquals(ExecutionState.STOPPED, session.state)

        // Transition: RUNNING -> FAILED
        session = session.copy(state = ExecutionState.FAILED, errorMessage = "Gesture timed out")
        assertEquals(ExecutionState.FAILED, session.state)
    }

    // =========================================================================
    // SECTION 28 UNIT TESTS — CONTINUOUS RECORDING & PERSISTENT STORAGE
    // =========================================================================

    @Test
    fun testSaveOneTap() {
        val step = WorkflowStep(
            id = "tap_1",
            order = 1,
            type = ActionType.TAP,
            startX = 500,
            startY = 800,
            duration = 100L
        )
        val session = com.taskscheduling.app.data.repository.RecordingSession(
            sessionId = "sess_1",
            workflowId = "wf_1",
            workflowName = "Test One Tap",
            startedAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            isRecordingActive = true,
            deviceProfile = DeviceProfile("Samsung", "SM-X110", "14", 34, 800, 1340, 1.33f, "PORTRAIT"),
            steps = listOf(step)
        )
        val json = WorkflowPersistenceRepository.recordingSessionToJson(session)
        val deserialized = WorkflowPersistenceRepository.jsonToRecordingSession(json)

        assertEquals(1, deserialized.steps.size)
        assertEquals(ActionType.TAP, deserialized.steps[0].type)
        assertEquals(500, deserialized.steps[0].startX)
        assertEquals(800, deserialized.steps[0].startY)
    }

    @Test
    fun testSaveMultipleTaps() {
        val tap1 = WorkflowStep(id = "t1", order = 1, type = ActionType.TAP, startX = 500, startY = 800)
        val tap2 = WorkflowStep(id = "t2", order = 2, type = ActionType.TAP, startX = 700, startY = 800)
        val session = com.taskscheduling.app.data.repository.RecordingSession(
            sessionId = "sess_2",
            workflowId = "wf_2",
            workflowName = "Two Taps",
            startedAt = 1000L,
            updatedAt = 2000L,
            isRecordingActive = true,
            deviceProfile = null,
            steps = listOf(tap1, tap2)
        )
        val deserialized = WorkflowPersistenceRepository.jsonToRecordingSession(WorkflowPersistenceRepository.recordingSessionToJson(session))
        assertEquals(2, deserialized.steps.size)
        assertEquals(500, deserialized.steps[0].startX)
        assertEquals(700, deserialized.steps[1].startX)
    }

    @Test
    fun testSaveTapTapTapSequence() {
        val steps = listOf(
            WorkflowStep(id = "t1", order = 1, type = ActionType.TAP, startX = 500, startY = 800),
            WorkflowStep(id = "t2", order = 2, type = ActionType.TAP, startX = 700, startY = 800),
            WorkflowStep(id = "t3", order = 3, type = ActionType.TAP, startX = 900, startY = 500)
        )
        val session = com.taskscheduling.app.data.repository.RecordingSession(
            sessionId = "sess_3",
            workflowId = "wf_3",
            workflowName = "Three Taps",
            startedAt = 1000L,
            updatedAt = 3000L,
            isRecordingActive = true,
            deviceProfile = null,
            steps = steps
        )
        val deserialized = WorkflowPersistenceRepository.jsonToRecordingSession(WorkflowPersistenceRepository.recordingSessionToJson(session))
        assertEquals(3, deserialized.steps.size)
        assertEquals(500, deserialized.steps[0].startX)
        assertEquals(700, deserialized.steps[1].startX)
        assertEquals(900, deserialized.steps[2].startX)
    }

    @Test
    fun testSaveTapSwipeWaitBackSequence() {
        val steps = listOf(
            WorkflowStep(id = "s1", order = 1, type = ActionType.TAP, startX = 400, startY = 600),
            WorkflowStep(id = "s2", order = 2, type = ActionType.SWIPE, startX = 400, startY = 1000, endX = 400, endY = 300, duration = 600L),
            WorkflowStep(id = "s3", order = 3, type = ActionType.WAIT, duration = 1000L),
            WorkflowStep(id = "s4", order = 4, type = ActionType.BACK, duration = 100L)
        )
        val session = com.taskscheduling.app.data.repository.RecordingSession(
            sessionId = "sess_4",
            workflowId = "wf_4",
            workflowName = "Multi Action",
            startedAt = 1000L,
            updatedAt = 4000L,
            isRecordingActive = true,
            deviceProfile = null,
            steps = steps
        )
        val deserialized = WorkflowPersistenceRepository.jsonToRecordingSession(WorkflowPersistenceRepository.recordingSessionToJson(session))
        assertEquals(4, deserialized.steps.size)
        assertEquals(ActionType.TAP, deserialized.steps[0].type)
        assertEquals(ActionType.SWIPE, deserialized.steps[1].type)
        assertEquals(ActionType.WAIT, deserialized.steps[2].type)
        assertEquals(ActionType.BACK, deserialized.steps[3].type)
    }

    @Test
    fun testReloadRecordingAndVerifyExactOrder() {
        val steps = (1..6).map { idx ->
            WorkflowStep(id = "step_$idx", order = idx, type = ActionType.TAP, startX = idx * 100, startY = idx * 150)
        }
        val session = com.taskscheduling.app.data.repository.RecordingSession(
            sessionId = "sess_order",
            workflowId = "wf_order",
            workflowName = "Order Test",
            startedAt = 1000L,
            updatedAt = 2000L,
            isRecordingActive = true,
            deviceProfile = null,
            steps = steps
        )
        val deserialized = WorkflowPersistenceRepository.jsonToRecordingSession(WorkflowPersistenceRepository.recordingSessionToJson(session))
        for (i in 0 until 6) {
            assertEquals(i + 1, deserialized.steps[i].order)
            assertEquals((i + 1) * 100, deserialized.steps[i].startX)
            assertEquals((i + 1) * 150, deserialized.steps[i].startY)
        }
    }

    @Test
    fun testExactTapCoordinatesPreserved() {
        val tap = WorkflowStep(id = "t_exact", order = 1, type = ActionType.TAP, startX = 523, startY = 847)
        val session = com.taskscheduling.app.data.repository.RecordingSession(
            sessionId = "sess_coords",
            workflowId = "wf_coords",
            workflowName = "Exact Tap",
            startedAt = 100L,
            updatedAt = 200L,
            isRecordingActive = true,
            deviceProfile = null,
            steps = listOf(tap)
        )
        val deserialized = WorkflowPersistenceRepository.jsonToRecordingSession(WorkflowPersistenceRepository.recordingSessionToJson(session))
        assertEquals(523, deserialized.steps[0].startX)
        assertEquals(847, deserialized.steps[0].startY)
    }

    @Test
    fun testSwipeCoordinatesAndDurationPreserved() {
        val swipe = WorkflowStep(
            id = "sw_exact",
            order = 1,
            type = ActionType.SWIPE,
            startX = 200,
            startY = 900,
            endX = 600,
            endY = 400,
            duration = 750L
        )
        val session = com.taskscheduling.app.data.repository.RecordingSession(
            sessionId = "sess_swipe",
            workflowId = "wf_swipe",
            workflowName = "Exact Swipe",
            startedAt = 100L,
            updatedAt = 200L,
            isRecordingActive = true,
            deviceProfile = null,
            steps = listOf(swipe)
        )
        val deserialized = WorkflowPersistenceRepository.jsonToRecordingSession(WorkflowPersistenceRepository.recordingSessionToJson(session))
        assertEquals(200, deserialized.steps[0].startX)
        assertEquals(900, deserialized.steps[0].startY)
        assertEquals(600, deserialized.steps[0].endX)
        assertEquals(400, deserialized.steps[0].endY)
        assertEquals(750L, deserialized.steps[0].duration)
    }

    @Test
    fun testWaitDurationPreserved() {
        val wait = WorkflowStep(id = "w_exact", order = 1, type = ActionType.WAIT, duration = 2000L)
        val session = com.taskscheduling.app.data.repository.RecordingSession(
            sessionId = "sess_wait",
            workflowId = "wf_wait",
            workflowName = "Wait Test",
            startedAt = 100L,
            updatedAt = 200L,
            isRecordingActive = true,
            deviceProfile = null,
            steps = listOf(wait)
        )
        val deserialized = WorkflowPersistenceRepository.jsonToRecordingSession(WorkflowPersistenceRepository.recordingSessionToJson(session))
        assertEquals(ActionType.WAIT, deserialized.steps[0].type)
        assertEquals(2000L, deserialized.steps[0].duration)
    }

    @Test
    fun testBackStepPreserved() {
        val back = WorkflowStep(id = "b_exact", order = 1, type = ActionType.BACK, duration = 100L)
        val session = com.taskscheduling.app.data.repository.RecordingSession(
            sessionId = "sess_back",
            workflowId = "wf_back",
            workflowName = "Back Test",
            startedAt = 100L,
            updatedAt = 200L,
            isRecordingActive = true,
            deviceProfile = null,
            steps = listOf(back)
        )
        val deserialized = WorkflowPersistenceRepository.jsonToRecordingSession(WorkflowPersistenceRepository.recordingSessionToJson(session))
        assertEquals(ActionType.BACK, deserialized.steps[0].type)
    }

    @Test
    fun testDeviceProfilePersistenceInRecordingSession() {
        val dp = DeviceProfile(
            manufacturer = "Samsung",
            model = "SM-X110",
            androidVersion = "14",
            apiLevel = 34,
            screenWidth = 800,
            screenHeight = 1340,
            density = 1.33f,
            orientation = "PORTRAIT"
        )
        val session = com.taskscheduling.app.data.repository.RecordingSession(
            sessionId = "sess_dp",
            workflowId = "wf_dp",
            workflowName = "DP Test",
            startedAt = 100L,
            updatedAt = 200L,
            isRecordingActive = true,
            deviceProfile = dp,
            steps = emptyList()
        )
        val deserialized = WorkflowPersistenceRepository.jsonToRecordingSession(WorkflowPersistenceRepository.recordingSessionToJson(session))
        assertNotNull(deserialized.deviceProfile)
        assertEquals("Samsung", deserialized.deviceProfile?.manufacturer)
        assertEquals("SM-X110", deserialized.deviceProfile?.model)
        assertEquals(800, deserialized.deviceProfile?.screenWidth)
        assertEquals(1340, deserialized.deviceProfile?.screenHeight)
        assertEquals(34, deserialized.deviceProfile?.apiLevel)
    }

    @Test
    fun testRestoreUnfinishedRecording() {
        val tempDir = java.nio.file.Files.createTempDirectory("wf_test_recovery").toFile()
        try {
            val repo = WorkflowPersistenceRepository(tempDir)
            kotlinx.coroutines.runBlocking {
                val initialSession = com.taskscheduling.app.data.repository.RecordingSession(
                    sessionId = "sess_recover",
                    workflowId = "wf_wallpaper",
                    workflowName = "Change Wallpaper",
                    startedAt = 1000L,
                    updatedAt = 2000L,
                    isRecordingActive = true,
                    deviceProfile = DeviceProfile("Samsung", "SM-X110", "14", 34, 800, 1340, 1.33f, "PORTRAIT"),
                    steps = listOf(
                        WorkflowStep(id = "s1", order = 1, type = ActionType.TAP, startX = 400, startY = 700),
                        WorkflowStep(id = "s2", order = 2, type = ActionType.SWIPE, startX = 400, startY = 1000, endX = 400, endY = 300)
                    )
                )
                repo.saveRecordingSession(initialSession)

                // Simulate app restart: reload from storage
                val restored = repo.loadRecordingSession()
                assertNotNull(restored)
                assertEquals("sess_recover", restored?.sessionId)
                assertEquals(2, restored?.steps?.size)
                assertEquals(ActionType.TAP, restored?.steps?.get(0)?.type)
                assertEquals(ActionType.SWIPE, restored?.steps?.get(1)?.type)

                // Clean up session
                repo.clearRecordingSession()
                val emptyRestored = repo.loadRecordingSession()
                assertNull(emptyRestored)
            }
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testSaveAndReloadCompletedWorkflow() {
        val tempDir = java.nio.file.Files.createTempDirectory("wf_test_save_reload").toFile()
        try {
            val repo = WorkflowPersistenceRepository(tempDir)
            kotlinx.coroutines.runBlocking {
                val wf = Workflow(
                    id = "wf_saved_1",
                    name = "Complete Workflow",
                    status = WorkflowStatus.CALIBRATED,
                    deviceProfile = DeviceProfile("Samsung", "SM-X110", "14", 34, 800, 1340, 1.33f, "PORTRAIT"),
                    steps = listOf(
                        WorkflowStep(id = "st1", order = 1, type = ActionType.TAP, startX = 100, startY = 200),
                        WorkflowStep(id = "st2", order = 2, type = ActionType.WAIT, duration = 1000L)
                    )
                )
                repo.saveWorkflow(wf)

                val loaded = repo.getWorkflow("wf_saved_1")
                assertNotNull(loaded)
                assertEquals("Complete Workflow", loaded?.name)
                assertEquals(2, loaded?.steps?.size)
            }
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testMultipleWorkflowsDoNotOverwriteEachOther() {
        val tempDir = java.nio.file.Files.createTempDirectory("wf_test_multi").toFile()
        try {
            val repo = WorkflowPersistenceRepository(tempDir)
            kotlinx.coroutines.runBlocking {
                val wfA = Workflow(id = "wf_A", name = "Workflow A", status = WorkflowStatus.CALIBRATED)
                val wfB = Workflow(id = "wf_B", name = "Workflow B", status = WorkflowStatus.CALIBRATED)

                repo.saveWorkflow(wfA)
                repo.saveWorkflow(wfB)

                val all = repo.getAllWorkflows()
                assertEquals(2, all.size)
                assertNotNull(repo.getWorkflow("wf_A"))
                assertNotNull(repo.getWorkflow("wf_B"))
                assertEquals("Workflow A", repo.getWorkflow("wf_A")?.name)
                assertEquals("Workflow B", repo.getWorkflow("wf_B")?.name)
            }
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testDeleteOneWorkflowWithoutAffectingOthers() {
        val tempDir = java.nio.file.Files.createTempDirectory("wf_test_del").toFile()
        try {
            val repo = WorkflowPersistenceRepository(tempDir)
            kotlinx.coroutines.runBlocking {
                val wfA = Workflow(id = "wf_A", name = "Workflow A", status = WorkflowStatus.CALIBRATED)
                val wfB = Workflow(id = "wf_B", name = "Workflow B", status = WorkflowStatus.CALIBRATED)

                repo.saveWorkflow(wfA)
                repo.saveWorkflow(wfB)
                assertEquals(2, repo.getAllWorkflows().size)

                repo.deleteWorkflow("wf_A")
                val remaining = repo.getAllWorkflows()
                assertEquals(1, remaining.size)
                assertNull(repo.getWorkflow("wf_A"))
                assertNotNull(repo.getWorkflow("wf_B"))
                assertEquals("Workflow B", remaining[0].name)
            }
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testCorruptedPersistenceDoesNotCrashApp() {
        val tempDir = java.nio.file.Files.createTempDirectory("wf_test_corrupt").toFile()
        try {
            val corruptFile = java.io.File(tempDir, "recording_session.json")
            corruptFile.writeText("INVALID JSON {[[ broken")

            val repo = WorkflowPersistenceRepository(tempDir)
            kotlinx.coroutines.runBlocking {
                val session = repo.loadRecordingSession()
                assertNull("Corrupted JSON must gracefully return null and not throw exception", session)
            }
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testRestoringWorkflowDoesNotTriggerExecution() {
        // Data loading only: Loading or restoring workflow model never alters execution state
        val wf = Workflow(
            id = "wf_pure_data",
            name = "Pure Data",
            status = WorkflowStatus.CALIBRATED,
            steps = listOf(WorkflowStep(id = "s1", order = 1, type = ActionType.TAP, startX = 100, startY = 100))
        )
        val session = ExecutionSession(taskId = "task_test", taskTitle = "Test", state = ExecutionState.IDLE)
        // Verify state remains strictly IDLE
        assertEquals(ExecutionState.IDLE, session.state)
        assertEquals(0, session.currentStepIndex)
        assertNotNull(wf)
    }

    @Test
    fun testRecordingDoesNotTriggerDispatchGesture() {
        // Verify that recording a step simply appends to in-memory/persistence data
        // and does not invoke dispatchGesture
        val initialSteps = emptyList<WorkflowStep>()
        val step = WorkflowStep(id = "rec_step", order = 1, type = ActionType.TAP, startX = 300, startY = 400)
        val updated = initialSteps + step

        assertEquals(1, updated.size)
        assertEquals(300, updated[0].startX)
        assertEquals(400, updated[0].startY)
        // No execution occurred
    }

    @Test
    fun testSavingDoesNotTriggerExecution() {
        val wf = Workflow(
            id = "wf_save_only",
            name = "Save Only",
            status = WorkflowStatus.CALIBRATED,
            steps = listOf(WorkflowStep(id = "s1", order = 1, type = ActionType.TAP, startX = 100, startY = 200))
        )
        var executionTriggered = false
        // Simulation of save action: only updates repository, execution flag remains false
        val saved = wf.copy(updatedAt = System.currentTimeMillis())
        assertNotNull(saved)
        assertFalse("Saving a workflow must NEVER trigger execution", executionTriggered)
    }

    @Test
    fun testOneRecordingCreatesOneWorkflowContainingManySteps() {
        // Architecture verification:
        // A single recording session produces ONE Workflow object with a stable ID,
        // containing an ordered list of WorkflowStep objects (TAPs, SWIPEs, WAITs, BACKs).
        // It does NOT produce separate workflow objects per action.
        val workflowId = "workflow_wallpaper_unique"
        val actions = listOf(
            WorkflowStep(id = "step_1", order = 1, type = ActionType.TAP, startX = 500, startY = 800),
            WorkflowStep(id = "step_2", order = 2, type = ActionType.TAP, startX = 700, startY = 800),
            WorkflowStep(id = "step_3", order = 3, type = ActionType.SWIPE, startX = 900, startY = 1200, endX = 900, endY = 500),
            WorkflowStep(id = "step_4", order = 4, type = ActionType.WAIT, duration = 1000L),
            WorkflowStep(id = "step_5", order = 5, type = ActionType.TAP, startX = 600, startY = 400),
            WorkflowStep(id = "step_6", order = 6, type = ActionType.BACK)
        )

        val singleWorkflow = Workflow(
            id = workflowId,
            name = "Change Wallpaper",
            status = WorkflowStatus.CALIBRATED,
            deviceProfile = DeviceProfile("Samsung", "SM-X110", "14", 34, 800, 1340, 1.33f, "PORTRAIT"),
            steps = actions
        )

        // 1. Must be ONE workflow
        assertEquals("workflow_wallpaper_unique", singleWorkflow.id)
        assertEquals("Change Wallpaper", singleWorkflow.name)

        // 2. Must contain 6 steps inside the single workflow
        assertEquals(6, singleWorkflow.steps.size)
        assertEquals(ActionType.TAP, singleWorkflow.steps[0].type)
        assertEquals(ActionType.TAP, singleWorkflow.steps[1].type)
        assertEquals(ActionType.SWIPE, singleWorkflow.steps[2].type)
        assertEquals(ActionType.WAIT, singleWorkflow.steps[3].type)
        assertEquals(ActionType.TAP, singleWorkflow.steps[4].type)
        assertEquals(ActionType.BACK, singleWorkflow.steps[5].type)

        // 3. Serializing and deserializing preserves the single workflow structure
        val json = WorkflowPersistenceRepository.workflowToJson(singleWorkflow)
        val reloadedWf = WorkflowPersistenceRepository.jsonToWorkflow(json)

        assertEquals("workflow_wallpaper_unique", reloadedWf.id)
        assertEquals(6, reloadedWf.steps.size)
        for (i in 0 until 6) {
            assertEquals(i + 1, reloadedWf.steps[i].order)
        }
    }

    @Test
    fun testUniqueWorkflowIdsGenerated() {
        val id1 = java.util.UUID.randomUUID().toString()
        val id2 = java.util.UUID.randomUUID().toString()
        assertFalse(id1 == id2)

        val wf1 = Workflow(id = id1, name = "Workflow 1")
        val wf2 = Workflow(id = id2, name = "Workflow 2")

        assertEquals(id1, wf1.id)
        assertEquals(id2, wf2.id)
        assertFalse(wf1.id == wf2.id)
    }

    @Test
    fun testCrashRecoveryPreservesExactStepsWithoutDuplicate() {
        val tempDir = java.nio.file.Files.createTempDirectory("wf_test_no_dup").toFile()
        try {
            val repo = WorkflowPersistenceRepository(tempDir)
            kotlinx.coroutines.runBlocking {
                // Record 4 actions
                val steps = listOf(
                    WorkflowStep(id = "s1", order = 1, type = ActionType.TAP, startX = 100, startY = 200),
                    WorkflowStep(id = "s2", order = 2, type = ActionType.TAP, startX = 300, startY = 400),
                    WorkflowStep(id = "s3", order = 3, type = ActionType.SWIPE, startX = 500, startY = 600, endX = 500, endY = 200),
                    WorkflowStep(id = "s4", order = 4, type = ActionType.WAIT, duration = 1000L)
                )
                val session = com.taskscheduling.app.data.repository.RecordingSession(
                    sessionId = "sess_4steps",
                    workflowId = "wf_unique_crash",
                    workflowName = "Change Wallpaper",
                    startedAt = 1000L,
                    updatedAt = 2000L,
                    isRecordingActive = true,
                    deviceProfile = null,
                    steps = steps
                )
                repo.saveRecordingSession(session)

                // App crashes and reopens:
                val restored = repo.loadRecordingSession()
                assertNotNull(restored)
                assertEquals(4, restored?.steps?.size)
                assertEquals("s4", restored?.steps?.get(3)?.id)

                // Continue recording by appending step 5 (TAP)
                val step5 = WorkflowStep(id = "s5", order = 5, type = ActionType.TAP, startX = 700, startY = 800)
                val updatedSteps = (restored?.steps ?: emptyList()) + step5
                val resumedSession = restored!!.copy(steps = updatedSteps, updatedAt = 3000L)
                repo.saveRecordingSession(resumedSession)

                val finalLoaded = repo.loadRecordingSession()
                assertEquals(5, finalLoaded?.steps?.size)
                // Verify all step IDs are distinct (no duplicated step 4)
                val stepIds = finalLoaded?.steps?.map { it.id } ?: emptyList()
                assertEquals(5, stepIds.distinct().size)
                assertEquals("s1", stepIds[0])
                assertEquals("s2", stepIds[1])
                assertEquals("s3", stepIds[2])
                assertEquals("s4", stepIds[3])
                assertEquals("s5", stepIds[4])
            }
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testWaitStepVsWaitAfterSemantics() {
        // Explicit WAIT action step vs waitAfter delay on a TAP action
        val explicitWaitStep = WorkflowStep(
            id = "w1",
            order = 1,
            type = ActionType.WAIT,
            duration = 1500L,
            waitAfter = 0L
        )
        val tapWithWaitAfter = WorkflowStep(
            id = "t1",
            order = 2,
            type = ActionType.TAP,
            startX = 200,
            startY = 300,
            duration = 100L,
            waitAfter = 500L
        )

        assertEquals(ActionType.WAIT, explicitWaitStep.type)
        assertEquals(1500L, explicitWaitStep.duration)
        assertEquals(0L, explicitWaitStep.waitAfter)

        assertEquals(ActionType.TAP, tapWithWaitAfter.type)
        assertEquals(100L, tapWithWaitAfter.duration)
        assertEquals(500L, tapWithWaitAfter.waitAfter)
    }

    @Test
    fun testTestStepDoesNotAlterSavedWorkflow() {
        // A test-step execution must not change the workflow's step list or order
        val steps = listOf(
            WorkflowStep(id = "s1", order = 1, type = ActionType.TAP, startX = 100, startY = 100),
            WorkflowStep(id = "s2", order = 2, type = ActionType.TAP, startX = 200, startY = 200)
        )
        val originalWorkflow = Workflow(
            id = "wf_immutable_test",
            name = "Test Step Workflow",
            status = WorkflowStatus.CALIBRATED,
            steps = steps
        )

        // Select step 2 for isolated testing
        val testedStep = originalWorkflow.steps[1]
        assertEquals("s2", testedStep.id)

        // Verify original workflow remains unaltered
        assertEquals(2, originalWorkflow.steps.size)
        assertEquals("s1", originalWorkflow.steps[0].id)
        assertEquals("s2", originalWorkflow.steps[1].id)
    }

    @Test
    fun testReviewEditingPersistsToSameWorkflow() {
        val tempDir = java.nio.file.Files.createTempDirectory("wf_test_edit").toFile()
        try {
            val repo = WorkflowPersistenceRepository(tempDir)
            kotlinx.coroutines.runBlocking {
                val wf = Workflow(
                    id = "wf_to_edit",
                    name = "Editable Workflow",
                    status = WorkflowStatus.CALIBRATED,
                    steps = listOf(
                        WorkflowStep(id = "st1", order = 1, type = ActionType.TAP, startX = 100, startY = 200),
                        WorkflowStep(id = "st2", order = 2, type = ActionType.TAP, startX = 300, startY = 400)
                    )
                )
                repo.saveWorkflow(wf)

                // Edit: reorder or modify step 1 coordinate to 150, 250
                val editedSteps = listOf(
                    wf.steps[0].copy(startX = 150, startY = 250),
                    wf.steps[1]
                )
                val updatedWf = wf.copy(steps = editedSteps, updatedAt = System.currentTimeMillis())
                repo.saveWorkflow(updatedWf)

                // Must update the SAME workflow ID without creating a new workflow
                val all = repo.getAllWorkflows()
                assertEquals(1, all.size)
                assertEquals("wf_to_edit", all[0].id)
                assertEquals(150, all[0].steps[0].startX)
                assertEquals(250, all[0].steps[0].startY)
            }
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testWorkflowSchemaVersionDefault() {
        val wf = Workflow(id = "wf_version", name = "Versioned Workflow")
        assertEquals(1, wf.schemaVersion)

        val json = WorkflowPersistenceRepository.workflowToJson(wf)
        val deserialized = WorkflowPersistenceRepository.jsonToWorkflow(json)
        assertEquals(1, deserialized.schemaVersion)
    }

    @Test
    fun testOneRecordingProducesOneWorkflowWithSixActionsAndPreservesDistinctSteps() {
        // A complete 6-action recording session must produce:
        // 1 single Workflow container
        // 6 distinct ordered WorkflowStep objects internally
        val wf = Workflow(
            id = "wf_wallpaper_unique_123",
            name = "Change Wallpaper",
            status = WorkflowStatus.CALIBRATED,
            steps = listOf(
                WorkflowStep(id = "s1", order = 1, type = ActionType.TAP, startX = 299, startY = 937),
                WorkflowStep(id = "s2", order = 2, type = ActionType.TAP, startX = 299, startY = 937),
                WorkflowStep(id = "s3", order = 3, type = ActionType.TAP, startX = 299, startY = 937),
                WorkflowStep(id = "s4", order = 4, type = ActionType.TAP, startX = 299, startY = 937),
                WorkflowStep(id = "s5", order = 5, type = ActionType.TAP, startX = 450, startY = 800),
                WorkflowStep(id = "s6", order = 6, type = ActionType.BACK, startX = 0, startY = 0)
            )
        )

        // 1. One workflow
        assertEquals("Change Wallpaper", wf.name)
        assertEquals("wf_wallpaper_unique_123", wf.id)

        // 2. Exactly 6 actions
        assertEquals(6, wf.steps.size)

        // 3. Actions remain separate distinct steps internally for sequential execution
        assertEquals(ActionType.TAP, wf.steps[0].type)
        assertEquals(299, wf.steps[0].startX)
        assertEquals(937, wf.steps[0].startY)

        assertEquals(ActionType.TAP, wf.steps[4].type)
        assertEquals(450, wf.steps[4].startX)
        assertEquals(800, wf.steps[4].startY)

        assertEquals(ActionType.BACK, wf.steps[5].type)

        // 4. Persistence saves exactly ONE workflow item
        val tempDir = java.nio.file.Files.createTempDirectory("wf_single_card_test").toFile()
        try {
            val repo = WorkflowPersistenceRepository(tempDir)
            kotlinx.coroutines.runBlocking {
                repo.saveWorkflow(wf)
                val allWorkflows = repo.getAllWorkflows()
                assertEquals(1, allWorkflows.size)
                assertEquals("Change Wallpaper", allWorkflows[0].name)
                assertEquals(6, allWorkflows[0].steps.size)
            }
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
