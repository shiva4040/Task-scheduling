package com.taskscheduling.app.recording

import android.content.Context
import com.taskscheduling.app.accessibility.AccessibilityUtils
import com.taskscheduling.app.accessibility.TaskSchedulingAccessibilityService
import com.taskscheduling.app.data.repository.RecordingSession
import com.taskscheduling.app.data.repository.WorkflowPersistenceRepository
import com.taskscheduling.app.domain.model.ActionType
import com.taskscheduling.app.domain.model.CoordinateAction
import com.taskscheduling.app.domain.model.DeviceProfile
import com.taskscheduling.app.domain.model.WorkflowStep
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

object RecordingManager {

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _recordedSteps = MutableStateFlow<List<WorkflowStep>>(emptyList())
    val recordedSteps: StateFlow<List<WorkflowStep>> = _recordedSteps.asStateFlow()

    private val _sessionState = MutableStateFlow(com.taskscheduling.app.domain.model.SessionState.IDLE)
    val sessionState: StateFlow<com.taskscheduling.app.domain.model.SessionState> = _sessionState.asStateFlow()

    private var currentSessionId: String = UUID.randomUUID().toString()
    private var currentWorkflowId: String = UUID.randomUUID().toString()
    private var currentWorkflowName: String = "Change Wallpaper"
    private var sessionStartTime: Long = System.currentTimeMillis()

    var activeDeviceProfile: DeviceProfile? = null
        private set

    private var persistenceRepository: WorkflowPersistenceRepository? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    /**
     * Initializes repository reference for immediate action persistence.
     */
    fun initRepository(repo: WorkflowPersistenceRepository) {
        this.persistenceRepository = repo
    }

    /**
     * When false (default), coordinates are exclusively captured via the manual precision reticle target.
     * When true, accessibility view clicked events can provide element bounds center coordinates.
     */
    var enableNodeClickAutoDetect: Boolean = false

    private var onFinishCallback: (() -> Unit)? = null
    private var onCancelCallback: (() -> Unit)? = null

    /**
     * Starts a fresh coordinate recording session.
     * Records real physical screen pixels and device hardware profile.
     * Automation is strictly forbidden in recording mode.
     */
    fun startRecording(
        context: Context,
        workflowId: String,
        workflowName: String = "Change Wallpaper",
        onFinish: () -> Unit = {},
        onCancel: () -> Unit = {}
    ) {
        currentSessionId = UUID.randomUUID().toString()
        currentWorkflowId = workflowId
        currentWorkflowName = workflowName
        sessionStartTime = System.currentTimeMillis()
        _recordedSteps.value = emptyList()
        activeDeviceProfile = AccessibilityUtils.getDeviceProfile(context)
        _isRecording.value = true
        _sessionState.value = com.taskscheduling.app.domain.model.SessionState.RECORDING
        onFinishCallback = onFinish
        onCancelCallback = onCancel

        persistCurrentSession()

        TaskSchedulingAccessibilityService.instance?.let { service ->
            service.overlayManager.showRecordingToolbar(
                onFinishRecording = {
                    stopRecording()
                    onFinishCallback?.invoke()
                },
                onCancelRecording = {
                    cancelRecording()
                    onCancelCallback?.invoke()
                }
            )
        }
    }

    /**
     * Resumes an existing unfinished recording session loaded from storage.
     */
    fun resumeRecording(
        context: Context,
        session: RecordingSession,
        onFinish: () -> Unit = {},
        onCancel: () -> Unit = {}
    ) {
        currentSessionId = session.sessionId
        currentWorkflowId = session.workflowId
        currentWorkflowName = session.workflowName
        sessionStartTime = session.startedAt
        _recordedSteps.value = session.steps
        activeDeviceProfile = session.deviceProfile ?: AccessibilityUtils.getDeviceProfile(context)
        _isRecording.value = true
        _sessionState.value = com.taskscheduling.app.domain.model.SessionState.RECORDING
        onFinishCallback = onFinish
        onCancelCallback = onCancel

        persistCurrentSession()

        TaskSchedulingAccessibilityService.instance?.let { service ->
            service.overlayManager.showRecordingToolbar(
                onFinishRecording = {
                    stopRecording()
                    onFinishCallback?.invoke()
                },
                onCancelRecording = {
                    cancelRecording()
                    onCancelCallback?.invoke()
                }
            )
        }
    }

    /**
     * Immediately writes the current recording session state to persistent disk.
     */
    private fun persistCurrentSession() {
        val repo = persistenceRepository ?: return
        val now = System.currentTimeMillis()
        val currentSession = RecordingSession(
            sessionId = currentSessionId,
            workflowId = currentWorkflowId,
            workflowName = currentWorkflowName,
            startedAt = sessionStartTime,
            updatedAt = now,
            lastSavedAt = now,
            isRecordingActive = _isRecording.value,
            sessionState = _sessionState.value,
            deviceProfile = activeDeviceProfile,
            steps = _recordedSteps.value,
            schemaVersion = 1
        )
        scope.launch {
            repo.saveRecordingSession(currentSession)
        }
    }

    /**
     * Records a physical tap coordinate.
     */
    fun recordTap(x: Int, y: Int) {
        if (!_isRecording.value) return
        val profile = activeDeviceProfile

        // Validate coordinate bounds
        if (profile != null) {
            val check = CoordinateValidator.validateCoordinate(x, y, profile.screenWidth, profile.screenHeight)
            if (!check.isValid) return
        }

        val stepIndex = _recordedSteps.value.size + 1
        val newStep = WorkflowStep(
            id = UUID.randomUUID().toString(),
            order = stepIndex,
            type = ActionType.TAP,
            startX = x,
            startY = y,
            duration = 100L,
            waitAfter = 500L,
            description = "Tap at ($x, $y)",
            stepNumber = stepIndex,
            label = "Tap",
            actionType = ActionType.TAP,
            coordinate = CoordinateAction(x, y)
        )

        _recordedSteps.value = _recordedSteps.value + newStep
        persistCurrentSession()

        // Visual indicator flash
        TaskSchedulingAccessibilityService.instance?.showVisualTouchDot(x, y, "TAP X: $x, Y: $y")
    }

    /**
     * Records a long press action.
     */
    fun recordLongPress(x: Int, y: Int, durationMs: Long = 900L) {
        if (!_isRecording.value) return
        val profile = activeDeviceProfile

        if (profile != null) {
            val check = CoordinateValidator.validateCoordinate(x, y, profile.screenWidth, profile.screenHeight)
            if (!check.isValid) return
        }

        val stepIndex = _recordedSteps.value.size + 1
        val newStep = WorkflowStep(
            id = UUID.randomUUID().toString(),
            order = stepIndex,
            type = ActionType.LONG_PRESS,
            startX = x,
            startY = y,
            duration = durationMs,
            waitAfter = 500L,
            description = "Long press at ($x, $y) [${durationMs}ms]",
            stepNumber = stepIndex,
            label = "Long Press",
            actionType = ActionType.LONG_PRESS,
            coordinate = CoordinateAction(x, y)
        )

        _recordedSteps.value = _recordedSteps.value + newStep
        persistCurrentSession()
        TaskSchedulingAccessibilityService.instance?.showVisualTouchDot(x, y, "HOLD X: $x, Y: $y")
    }

    /**
     * Records a swipe gesture.
     */
    fun recordSwipe(startX: Int, startY: Int, endX: Int, endY: Int, durationMs: Long = 500L) {
        if (!_isRecording.value) return
        val profile = activeDeviceProfile

        if (profile != null) {
            val startCheck = CoordinateValidator.validateCoordinate(startX, startY, profile.screenWidth, profile.screenHeight)
            val endCheck = CoordinateValidator.validateCoordinate(endX, endY, profile.screenWidth, profile.screenHeight)
            if (!startCheck.isValid || !endCheck.isValid) return
        }

        val stepIndex = _recordedSteps.value.size + 1
        val newStep = WorkflowStep(
            id = UUID.randomUUID().toString(),
            order = stepIndex,
            type = ActionType.SWIPE,
            startX = startX,
            startY = startY,
            endX = endX,
            endY = endY,
            duration = durationMs,
            waitAfter = 500L,
            description = "Swipe ($startX, $startY) → ($endX, $endY)",
            stepNumber = stepIndex,
            label = "Swipe",
            actionType = ActionType.SWIPE,
            coordinate = CoordinateAction(startX, startY, endX, endY)
        )

        _recordedSteps.value = _recordedSteps.value + newStep
        persistCurrentSession()
        TaskSchedulingAccessibilityService.instance?.showVisualTouchDot(startX, startY, "SWIPE START")
    }

    /**
     * Records an explicit wait step.
     */
    fun recordWait(durationMs: Long = 1000L) {
        if (!_isRecording.value) return
        val stepIndex = _recordedSteps.value.size + 1
        val newStep = WorkflowStep(
            id = UUID.randomUUID().toString(),
            order = stepIndex,
            type = ActionType.WAIT,
            duration = durationMs,
            waitAfter = 0L,
            description = "Wait ${durationMs} ms",
            stepNumber = stepIndex,
            label = "Wait",
            actionType = ActionType.WAIT
        )

        _recordedSteps.value = _recordedSteps.value + newStep
        persistCurrentSession()
    }

    /**
     * Records a system back action.
     */
    fun recordBack() {
        if (!_isRecording.value) return
        val stepIndex = _recordedSteps.value.size + 1
        val newStep = WorkflowStep(
            id = UUID.randomUUID().toString(),
            order = stepIndex,
            type = ActionType.BACK,
            duration = 100L,
            waitAfter = 500L,
            description = "Back button",
            stepNumber = stepIndex,
            label = "Back",
            actionType = ActionType.BACK
        )

        _recordedSteps.value = _recordedSteps.value + newStep
        persistCurrentSession()
    }

    /**
     * Updates the full steps list during editing/reordering.
     */
    fun updateSteps(steps: List<WorkflowStep>) {
        _recordedSteps.value = steps.mapIndexed { idx, s ->
            s.copy(order = idx + 1, stepNumber = idx + 1)
        }
        persistCurrentSession()
    }

    /**
     * Stops recording and hides overlay. Transitions state to REVIEW.
     */
    fun stopRecording(): List<WorkflowStep> {
        _isRecording.value = false
        _sessionState.value = com.taskscheduling.app.domain.model.SessionState.REVIEW
        persistCurrentSession()
        TaskSchedulingAccessibilityService.instance?.overlayManager?.hideRecordingToolbar()
        TaskSchedulingAccessibilityService.instance?.overlayManager?.hideTargetCrosshair()
        return _recordedSteps.value
    }

    /**
     * Cancels recording and discards steps. Transitions state to IDLE.
     */
    fun cancelRecording() {
        _isRecording.value = false
        _sessionState.value = com.taskscheduling.app.domain.model.SessionState.IDLE
        _recordedSteps.value = emptyList()
        val repo = persistenceRepository
        if (repo != null) {
            scope.launch {
                repo.clearRecordingSession()
            }
        }
        TaskSchedulingAccessibilityService.instance?.overlayManager?.clearAll()
    }
}
