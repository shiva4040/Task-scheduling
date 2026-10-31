package com.taskscheduling.app.recording

import android.content.Context
import com.taskscheduling.app.accessibility.AccessibilityUtils
import com.taskscheduling.app.accessibility.TaskSchedulingAccessibilityService
import com.taskscheduling.app.data.repository.RecordingSession
import com.taskscheduling.app.data.repository.TaskRepository
import com.taskscheduling.app.data.repository.WorkflowPersistenceRepository
import com.taskscheduling.app.domain.model.ActionType
import com.taskscheduling.app.domain.model.CoordinateAction
import com.taskscheduling.app.domain.model.DeviceProfile
import com.taskscheduling.app.domain.model.SessionState
import com.taskscheduling.app.domain.model.Task
import com.taskscheduling.app.domain.model.TaskCategory
import com.taskscheduling.app.domain.model.TaskPriority
import com.taskscheduling.app.domain.model.TaskStatus
import com.taskscheduling.app.domain.model.TaskType
import com.taskscheduling.app.domain.model.Workflow
import com.taskscheduling.app.domain.model.WorkflowStatus
import com.taskscheduling.app.domain.model.WorkflowStep
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Reusable, single canonical workflow recording manager.
 * Captures multiple actions in sequence into a unified WorkflowStep model.
 * Enforces strict lifecycle state transitions and prevents duplicate events.
 */
object RecordingManager {

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _recordedSteps = MutableStateFlow<List<WorkflowStep>>(emptyList())
    val recordedSteps: StateFlow<List<WorkflowStep>> = _recordedSteps.asStateFlow()

    private val _sessionState = MutableStateFlow(SessionState.IDLE)
    val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    private val _elapsedSeconds = MutableStateFlow(0L)
    val elapsedSeconds: StateFlow<Long> = _elapsedSeconds.asStateFlow()

    private var currentSessionId: String = UUID.randomUUID().toString()
    private var currentWorkflowId: String = UUID.randomUUID().toString()
    private var currentWorkflowName: String = "New Workflow"
    private var sessionStartTime: Long = System.currentTimeMillis()
    private var totalPausedDurationMs: Long = 0L
    private var pauseStartTime: Long = 0L

    var activeDeviceProfile: DeviceProfile? = null
        private set

    private var persistenceRepository: WorkflowPersistenceRepository? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    private var timerJob: Job? = null

    // Duplicate detection debounce tracking
    private var lastActionTimestamp: Long = 0L
    private var lastActionType: ActionType? = null
    private var lastActionCoordX: Int = -1
    private var lastActionCoordY: Int = -1

    /**
     * When false (default), coordinates are captured via the precision reticle target.
     * When true, accessibility view clicked events can provide element bounds center coordinates.
     */
    var enableNodeClickAutoDetect: Boolean = false

    private var onFinishCallback: (() -> Unit)? = null
    private var onCancelCallback: (() -> Unit)? = null

    /**
     * Initializes repository reference for immediate action persistence.
     */
    fun initRepository(repo: WorkflowPersistenceRepository) {
        this.persistenceRepository = repo
    }

    /**
     * Calculates the active elapsed recording time in milliseconds (excluding paused durations).
     */
    fun getElapsedRecordingTimeMs(): Long {
        if (_sessionState.value == SessionState.IDLE || _sessionState.value == SessionState.CANCELLED) {
            return 0L
        }
        val now = System.currentTimeMillis()
        val currentPause = if (_sessionState.value == SessionState.PAUSED && pauseStartTime > 0L) {
            (now - pauseStartTime).coerceAtLeast(0L)
        } else {
            0L
        }
        val elapsed = (now - sessionStartTime) - (totalPausedDurationMs + currentPause)
        return elapsed.coerceAtLeast(0L)
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = scope.launch {
            while (isActive) {
                if (_sessionState.value == SessionState.RECORDING) {
                    _elapsedSeconds.value = getElapsedRecordingTimeMs() / 1000L
                }
                delay(1000L)
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    /**
     * Starts a fresh coordinate recording session.
     * Rejects call if another session is already actively recording or paused.
     */
    fun startRecording(
        context: Context? = null,
        workflowId: String,
        workflowName: String = "New Workflow",
        deviceProfile: DeviceProfile? = null,
        onFinish: () -> Unit = {},
        onCancel: () -> Unit = {}
    ): Boolean {
        // Prevent simultaneous recording sessions
        if (_sessionState.value == SessionState.RECORDING || _sessionState.value == SessionState.PAUSED) {
            return false
        }

        currentSessionId = UUID.randomUUID().toString()
        currentWorkflowId = workflowId.ifBlank { UUID.randomUUID().toString() }
        currentWorkflowName = workflowName.ifBlank { "New Workflow" }
        sessionStartTime = System.currentTimeMillis()
        totalPausedDurationMs = 0L
        pauseStartTime = 0L
        _recordedSteps.value = emptyList()
        _elapsedSeconds.value = 0L
        activeDeviceProfile = deviceProfile ?: if (context != null) {
            AccessibilityUtils.getDeviceProfile(context)
        } else {
            DeviceProfile(screenWidth = 1080, screenHeight = 2400)
        }
        _isRecording.value = true
        _sessionState.value = SessionState.RECORDING
        onFinishCallback = onFinish
        onCancelCallback = onCancel

        startTimer()
        persistCurrentSession()

        TaskSchedulingAccessibilityService.instance?.let { service ->
            service.overlayManager.showFloatingRecorder(
                onFinish = {
                    finishRecording()
                    onFinishCallback?.invoke()
                },
                onCancel = {
                    cancelRecording()
                    onCancelCallback?.invoke()
                }
            )
        }
        return true
    }

    /**
     * Resumes an existing unfinished recording session loaded from storage.
     */
    fun resumeRecording(
        context: Context? = null,
        session: RecordingSession,
        deviceProfile: DeviceProfile? = null,
        onFinish: () -> Unit = {},
        onCancel: () -> Unit = {}
    ) {
        currentSessionId = session.sessionId
        currentWorkflowId = session.workflowId
        currentWorkflowName = session.workflowName
        sessionStartTime = session.startedAt
        totalPausedDurationMs = 0L
        pauseStartTime = 0L
        _recordedSteps.value = session.steps
        _elapsedSeconds.value = getElapsedRecordingTimeMs() / 1000L
        activeDeviceProfile = deviceProfile ?: session.deviceProfile ?: if (context != null) {
            AccessibilityUtils.getDeviceProfile(context)
        } else {
            DeviceProfile(screenWidth = 1080, screenHeight = 2400)
        }
        _isRecording.value = true
        _sessionState.value = SessionState.RECORDING
        onFinishCallback = onFinish
        onCancelCallback = onCancel

        startTimer()
        persistCurrentSession()

        TaskSchedulingAccessibilityService.instance?.let { service ->
            service.overlayManager.showFloatingRecorder(
                onFinish = {
                    finishRecording()
                    onFinishCallback?.invoke()
                },
                onCancel = {
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
     * Duplicate prevention guard: returns true if the incoming action is an accidental duplicate event.
     */
    private fun isDuplicateEvent(type: ActionType, x: Int = -1, y: Int = -1): Boolean {
        val now = System.currentTimeMillis()
        val isSameType = lastActionType == type
        val isSameLocation = (lastActionCoordX == x && lastActionCoordY == y) || (x == -1 && y == -1)
        val isRapid = (now - lastActionTimestamp) < 250L

        if (isSameType && isSameLocation && isRapid) {
            return true
        }

        lastActionTimestamp = now
        lastActionType = type
        lastActionCoordX = x
        lastActionCoordY = y
        return false
    }

    /**
     * Records a physical tap coordinate.
     */
    fun recordTap(x: Int, y: Int, label: String? = null) {
        if (_sessionState.value != SessionState.RECORDING) return
        if (isDuplicateEvent(ActionType.TAP, x, y)) return

        val profile = activeDeviceProfile
        if (profile != null) {
            val check = CoordinateValidator.validateCoordinate(x, y, profile.screenWidth, profile.screenHeight)
            if (!check.isValid) return
        }

        val stepIndex = _recordedSteps.value.size + 1
        val stepLabel = if (!label.isNullOrBlank()) label else "Tap"
        val newStep = WorkflowStep(
            id = UUID.randomUUID().toString(),
            order = stepIndex,
            type = ActionType.TAP,
            startX = x,
            startY = y,
            duration = 100L,
            waitAfter = 500L,
            description = if (!label.isNullOrBlank()) "Tap \"$label\" at ($x, $y)" else "Tap at ($x, $y)",
            stepNumber = stepIndex,
            label = stepLabel,
            actionType = ActionType.TAP,
            coordinate = CoordinateAction(x, y)
        )

        _recordedSteps.value = _recordedSteps.value + newStep
        persistCurrentSession()

        TaskSchedulingAccessibilityService.instance?.showVisualTouchDot(x, y, "TAP: $stepLabel ($x, $y)")
    }

    /**
     * Records a long press action.
     */
    fun recordLongPress(x: Int, y: Int, durationMs: Long = 900L, label: String? = null) {
        if (_sessionState.value != SessionState.RECORDING) return
        if (isDuplicateEvent(ActionType.LONG_PRESS, x, y)) return

        val profile = activeDeviceProfile
        if (profile != null) {
            val check = CoordinateValidator.validateCoordinate(x, y, profile.screenWidth, profile.screenHeight)
            if (!check.isValid) return
        }

        val stepIndex = _recordedSteps.value.size + 1
        val stepLabel = if (!label.isNullOrBlank()) label else "Long Press"
        val newStep = WorkflowStep(
            id = UUID.randomUUID().toString(),
            order = stepIndex,
            type = ActionType.LONG_PRESS,
            startX = x,
            startY = y,
            duration = durationMs,
            waitAfter = 500L,
            description = if (!label.isNullOrBlank()) "Long press \"$label\" at ($x, $y) [${durationMs}ms]" else "Long press at ($x, $y) [${durationMs}ms]",
            stepNumber = stepIndex,
            label = stepLabel,
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
        if (_sessionState.value != SessionState.RECORDING) return
        if (isDuplicateEvent(ActionType.SWIPE, startX, startY)) return

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
     * Records an explicit wait / delay step.
     */
    fun recordWait(durationMs: Long = 1000L) {
        if (_sessionState.value != SessionState.RECORDING) return
        if (isDuplicateEvent(ActionType.WAIT)) return

        val stepIndex = _recordedSteps.value.size + 1
        val newStep = WorkflowStep(
            id = UUID.randomUUID().toString(),
            order = stepIndex,
            type = ActionType.WAIT,
            duration = durationMs,
            waitAfter = 0L,
            description = "Wait ${durationMs}ms",
            stepNumber = stepIndex,
            label = "Wait",
            actionType = ActionType.WAIT,
            waitDurationMs = durationMs
        )

        _recordedSteps.value = _recordedSteps.value + newStep
        persistCurrentSession()
    }

    /**
     * Records a system back navigation action.
     */
    fun recordBack() {
        if (_sessionState.value != SessionState.RECORDING) return
        if (isDuplicateEvent(ActionType.BACK)) return

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
     * Records a system home navigation action.
     */
    fun recordHome() {
        if (_sessionState.value != SessionState.RECORDING) return
        if (isDuplicateEvent(ActionType.HOME)) return

        val stepIndex = _recordedSteps.value.size + 1
        val newStep = WorkflowStep(
            id = UUID.randomUUID().toString(),
            order = stepIndex,
            type = ActionType.HOME,
            duration = 100L,
            waitAfter = 500L,
            description = "Home button",
            stepNumber = stepIndex,
            label = "Home",
            actionType = ActionType.HOME
        )

        _recordedSteps.value = _recordedSteps.value + newStep
        persistCurrentSession()
    }

    /**
     * Records text input into an active text field.
     */
    fun recordTextInput(text: String) {
        if (_sessionState.value != SessionState.RECORDING) return
        if (text.isBlank()) return

        val stepIndex = _recordedSteps.value.size + 1
        val newStep = WorkflowStep(
            id = UUID.randomUUID().toString(),
            order = stepIndex,
            type = ActionType.TEXT_INPUT,
            duration = 200L,
            waitAfter = 500L,
            description = "Enter text: \"$text\"",
            stepNumber = stepIndex,
            label = "Text Input",
            actionType = ActionType.TEXT_INPUT,
            textInput = text
        )

        _recordedSteps.value = _recordedSteps.value + newStep
        persistCurrentSession()
    }

    /**
     * Records a scroll action (represented as vertical swipe gesture).
     */
    fun recordScroll(isUp: Boolean) {
        if (_sessionState.value != SessionState.RECORDING) return
        val profile = activeDeviceProfile
        val screenW = profile?.screenWidth ?: 1080
        val screenH = profile?.screenHeight ?: 1920

        val midX = screenW / 2
        val startY = if (isUp) (screenH * 0.75).toInt() else (screenH * 0.25).toInt()
        val endY = if (isUp) (screenH * 0.25).toInt() else (screenH * 0.75).toInt()

        val stepIndex = _recordedSteps.value.size + 1
        val newStep = WorkflowStep(
            id = UUID.randomUUID().toString(),
            order = stepIndex,
            type = ActionType.SWIPE,
            startX = midX,
            startY = startY,
            endX = midX,
            endY = endY,
            duration = 400L,
            waitAfter = 500L,
            description = if (isUp) "Scroll Down" else "Scroll Up",
            stepNumber = stepIndex,
            label = if (isUp) "Scroll Down" else "Scroll Up",
            actionType = ActionType.SWIPE,
            coordinate = CoordinateAction(midX, startY, midX, endY)
        )

        _recordedSteps.value = _recordedSteps.value + newStep
        persistCurrentSession()
    }

    /**
     * Records a drag and drop action.
     */
    fun recordDrag(startX: Int, startY: Int, endX: Int, endY: Int, durationMs: Long = 800L) {
        if (_sessionState.value != SessionState.RECORDING) return
        if (isDuplicateEvent(ActionType.DRAG, startX, startY)) return

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
            type = ActionType.DRAG,
            startX = startX,
            startY = startY,
            endX = endX,
            endY = endY,
            duration = durationMs,
            waitAfter = 500L,
            description = "Drag ($startX, $startY) → ($endX, $endY)",
            stepNumber = stepIndex,
            label = "Drag",
            actionType = ActionType.DRAG,
            coordinate = CoordinateAction(startX, startY, endX, endY)
        )

        _recordedSteps.value = _recordedSteps.value + newStep
        persistCurrentSession()
        TaskSchedulingAccessibilityService.instance?.showVisualTouchDot(startX, startY, "DRAG START")
    }

    /**
     * Records a double tap action.
     */
    fun recordDoubleTap(x: Int, y: Int) {
        if (_sessionState.value != SessionState.RECORDING) return
        if (isDuplicateEvent(ActionType.DOUBLE_TAP, x, y)) return

        val profile = activeDeviceProfile
        if (profile != null) {
            val check = CoordinateValidator.validateCoordinate(x, y, profile.screenWidth, profile.screenHeight)
            if (!check.isValid) return
        }

        val stepIndex = _recordedSteps.value.size + 1
        val newStep = WorkflowStep(
            id = UUID.randomUUID().toString(),
            order = stepIndex,
            type = ActionType.DOUBLE_TAP,
            startX = x,
            startY = y,
            duration = 100L,
            waitAfter = 500L,
            description = "Double tap at ($x, $y)",
            stepNumber = stepIndex,
            label = "Double Tap",
            actionType = ActionType.DOUBLE_TAP,
            coordinate = CoordinateAction(x, y)
        )

        _recordedSteps.value = _recordedSteps.value + newStep
        persistCurrentSession()
        TaskSchedulingAccessibilityService.instance?.showVisualTouchDot(x, y, "DOUBLE TAP X: $x, Y: $y")
    }

    /**
     * Records a recent apps overview navigation action.
     */
    fun recordRecents() {
        if (_sessionState.value != SessionState.RECORDING) return
        if (isDuplicateEvent(ActionType.RECENTS)) return

        val stepIndex = _recordedSteps.value.size + 1
        val newStep = WorkflowStep(
            id = UUID.randomUUID().toString(),
            order = stepIndex,
            type = ActionType.RECENTS,
            duration = 100L,
            waitAfter = 500L,
            description = "Recent Apps",
            stepNumber = stepIndex,
            label = "Recent Apps",
            actionType = ActionType.RECENTS
        )

        _recordedSteps.value = _recordedSteps.value + newStep
        persistCurrentSession()
    }

    /**
     * Records an accessibility node click with element identifier and text fallback.
     */
    fun recordNodeClick(nodeId: String?, nodeText: String?, x: Int, y: Int) {
        if (_sessionState.value != SessionState.RECORDING) return
        if (isDuplicateEvent(ActionType.TAP, x, y)) return

        val stepIndex = _recordedSteps.value.size + 1
        val desc = when {
            !nodeText.isNullOrBlank() -> "Click \"$nodeText\""
            !nodeId.isNullOrBlank() -> "Click element ($nodeId)"
            else -> "Tap at ($x, $y)"
        }

        val newStep = WorkflowStep(
            id = UUID.randomUUID().toString(),
            order = stepIndex,
            type = ActionType.TAP,
            startX = x,
            startY = y,
            duration = 100L,
            waitAfter = 500L,
            description = desc,
            stepNumber = stepIndex,
            label = desc,
            actionType = ActionType.TAP,
            coordinate = CoordinateAction(x, y),
            targetElement = nodeId,
            targetDescription = nodeText
        )

        _recordedSteps.value = _recordedSteps.value + newStep
        persistCurrentSession()
        TaskSchedulingAccessibilityService.instance?.showVisualTouchDot(x, y, desc)
    }

    /**
     * Undoes the last recorded step in the active session.
     */
    fun undoLastStep(): WorkflowStep? {
        val current = _recordedSteps.value
        if (current.isEmpty()) return null
        val lastStep = current.last()
        val remaining = current.dropLast(1).mapIndexed { idx, s ->
            s.copy(order = idx + 1, stepNumber = idx + 1)
        }
        _recordedSteps.value = remaining
        persistCurrentSession()
        return lastStep
    }

    /**
     * Pauses the active recording session without losing any recorded data.
     */
    fun pauseRecording(): Boolean {
        if (_sessionState.value != SessionState.RECORDING) return false
        _isRecording.value = false
        _sessionState.value = SessionState.PAUSED
        pauseStartTime = System.currentTimeMillis()
        persistCurrentSession()
        return true
    }

    /**
     * Resumes an existing paused recording session.
     */
    fun resumeActiveRecording(): Boolean {
        if (_sessionState.value != SessionState.PAUSED) return false
        val now = System.currentTimeMillis()
        if (pauseStartTime > 0L) {
            totalPausedDurationMs += (now - pauseStartTime)
            pauseStartTime = 0L
        }
        _isRecording.value = true
        _sessionState.value = SessionState.RECORDING
        persistCurrentSession()
        return true
    }

    /**
     * Deletes a recorded step by its unique stable ID.
     */
    fun deleteStep(stepId: String) {
        val current = _recordedSteps.value
        val updated = current.filterNot { it.id == stepId }
            .mapIndexed { idx, s -> s.copy(order = idx + 1, stepNumber = idx + 1) }
        _recordedSteps.value = updated
        persistCurrentSession()
    }

    /**
     * Renames a recorded step by its unique stable ID.
     */
    fun renameStep(stepId: String, newLabel: String) {
        val current = _recordedSteps.value
        val updated = current.map {
            if (it.id == stepId) it.copyWithSync(label = newLabel) else it
        }
        _recordedSteps.value = updated
        persistCurrentSession()
    }

    /**
     * Updates an entire step model by its unique ID.
     */
    fun updateStep(updatedStep: WorkflowStep) {
        val current = _recordedSteps.value
        val updated = current.map {
            if (it.id == updatedStep.id) updatedStep else it
        }
        _recordedSteps.value = updated
        persistCurrentSession()
    }

    /**
     * Duplicates a step directly after its position.
     */
    fun duplicateStep(stepId: String) {
        val current = _recordedSteps.value.toMutableList()
        val index = current.indexOfFirst { it.id == stepId }
        if (index == -1) return

        val source = current[index]
        val duplicated = source.copy(
            id = UUID.randomUUID().toString(),
            description = "${source.description} (Copy)",
            label = "${source.label} (Copy)"
        )
        current.add(index + 1, duplicated)
        val reordered = current.mapIndexed { idx, s -> s.copy(order = idx + 1, stepNumber = idx + 1) }
        _recordedSteps.value = reordered
        persistCurrentSession()
    }

    /**
     * Reorders a step from one index to another.
     */
    fun moveStep(fromIndex: Int, toIndex: Int) {
        val current = _recordedSteps.value.toMutableList()
        if (fromIndex !in current.indices || toIndex !in current.indices) return

        val item = current.removeAt(fromIndex)
        current.add(toIndex, item)
        val reordered = current.mapIndexed { idx, s -> s.copy(order = idx + 1, stepNumber = idx + 1) }
        _recordedSteps.value = reordered
        persistCurrentSession()
    }

    /**
     * Adds a supported step manually without physical recording.
     */
    fun addManualStep(step: WorkflowStep) {
        val stepIndex = _recordedSteps.value.size + 1
        val normalized = step.copy(
            id = if (step.id.isBlank()) UUID.randomUUID().toString() else step.id,
            order = stepIndex,
            stepNumber = stepIndex
        )
        _recordedSteps.value = _recordedSteps.value + normalized
        persistCurrentSession()
    }

    /**
     * Calibrate or re-record a single step's physical screen coordinates across any workflow action.
     */
    fun calibrateStepTarget(
        actionType: ActionType,
        stepLabel: String,
        onCaptured: (startX: Int, startY: Int, endX: Int?, endY: Int?, durationMs: Long) -> Unit,
        onCancel: () -> Unit = {}
    ) {
        val service = TaskSchedulingAccessibilityService.instance
        if (service != null) {
            service.overlayManager.recordSingleActionCoordinates(
                actionType = actionType,
                actionLabel = stepLabel,
                onCoordinatesCaptured = onCaptured,
                onCancel = onCancel
            )
        }
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
     * Finishes recording session and transitions state to FINISHING / REVIEW.
     * Overlays are cleanly dismissed.
     */
    fun finishRecording(): List<WorkflowStep> {
        _isRecording.value = false
        _sessionState.value = SessionState.REVIEW
        stopTimer()
        persistCurrentSession()
        TaskSchedulingAccessibilityService.instance?.overlayManager?.hideFloatingRecorder()
        return _recordedSteps.value
    }

    /**
     * Cancels recording and discards steps. Transitions state to CANCELLED then IDLE.
     */
    fun cancelRecording() {
        _isRecording.value = false
        _sessionState.value = SessionState.CANCELLED
        _recordedSteps.value = emptyList()
        _elapsedSeconds.value = 0L
        stopTimer()

        val repo = persistenceRepository
        if (repo != null) {
            scope.launch {
                repo.clearRecordingSession()
            }
        }
        TaskSchedulingAccessibilityService.instance?.overlayManager?.clearAll()
        _sessionState.value = SessionState.IDLE
    }

    /**
     * Saves the current recorded session into a permanent Workflow and Task.
     */
    suspend fun saveWorkflow(
        name: String,
        description: String = "",
        persistenceRepo: WorkflowPersistenceRepository? = persistenceRepository,
        taskRepo: TaskRepository? = null
    ): Workflow? {
        val cleanName = name.trim()
        if (cleanName.isBlank()) return null
        val steps = _recordedSteps.value
        if (steps.isEmpty()) return null

        val now = System.currentTimeMillis()
        val finalWorkflow = Workflow(
            id = currentWorkflowId.ifBlank { "wf_${UUID.randomUUID().toString().take(8)}" },
            name = cleanName,
            description = description.trim(),
            createdAt = sessionStartTime,
            updatedAt = now,
            status = WorkflowStatus.CALIBRATED,
            deviceProfile = activeDeviceProfile,
            steps = steps,
            schemaVersion = 1
        )

        val validation = com.taskscheduling.app.domain.validation.WorkflowValidator.validateWorkflow(finalWorkflow)
        if (!validation.isValid) {
            android.util.Log.e("RecordingManager", "Validation failed: ${(validation as? com.taskscheduling.app.domain.validation.WorkflowValidationResult.Invalid)?.message}")
            return null
        }

        persistenceRepo?.saveWorkflow(finalWorkflow)

        if (taskRepo != null) {
            val existingTask = taskRepo.getTaskById(finalWorkflow.id)
            val task = (existingTask ?: Task(
                id = finalWorkflow.id,
                title = cleanName,
                description = description.trim(),
                priority = TaskPriority.MEDIUM,
                status = TaskStatus.TODO,
                category = TaskCategory.CUSTOM,
                type = TaskType.COORDINATE_WORKFLOW,
                workflow = finalWorkflow,
                isEnabled = true,
                createdAt = sessionStartTime,
                updatedAt = now
            )).copy(
                title = cleanName,
                description = description.trim(),
                workflow = finalWorkflow,
                updatedAt = now
            )
            taskRepo.saveTask(task)
        }

        _sessionState.value = SessionState.SAVED
        persistenceRepo?.clearRecordingSession()
        return finalWorkflow
    }

    /**
     * Resets recording manager to a clean initial state.
     */
    fun reset() {
        stopTimer()
        _isRecording.value = false
        _sessionState.value = SessionState.IDLE
        _recordedSteps.value = emptyList()
        _elapsedSeconds.value = 0L
        currentSessionId = UUID.randomUUID().toString()
        currentWorkflowId = UUID.randomUUID().toString()
        currentWorkflowName = "New Workflow"
        totalPausedDurationMs = 0L
        pauseStartTime = 0L
    }
}
