package com.taskscheduling.app.automation

import android.content.Context
import com.taskscheduling.app.accessibility.AccessibilityUtils
import com.taskscheduling.app.accessibility.TaskSchedulingAccessibilityService
import com.taskscheduling.app.domain.model.ActionType
import com.taskscheduling.app.domain.model.CoordinateAction
import com.taskscheduling.app.domain.model.ExecutionLogEntry
import com.taskscheduling.app.domain.model.ExecutionSession
import com.taskscheduling.app.domain.model.ExecutionState
import com.taskscheduling.app.domain.model.Task
import com.taskscheduling.app.domain.model.Workflow
import com.taskscheduling.app.domain.model.WorkflowStep
import com.taskscheduling.app.recording.CoordinateValidator
import com.taskscheduling.app.recording.ValidationResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AndroidAccessibilityAutomationEngine(
    private val context: Context
) : AutomationEngine {

    private val _executionSession = MutableStateFlow(
        ExecutionSession(
            taskId = "",
            taskTitle = "",
            state = ExecutionState.IDLE
        )
    )
    override val executionSession: StateFlow<ExecutionSession> = _executionSession.asStateFlow()

    private val engineScope = CoroutineScope(Dispatchers.Default)
    private var activeJob: Job? = null

    @Volatile
    private var isPaused = false

    @Volatile
    private var isStopped = false

    private fun getService(): TaskSchedulingAccessibilityService? {
        return TaskSchedulingAccessibilityService.instance
    }

    override suspend fun executeTap(x: Int, y: Int, durationMs: Long): StepExecutionResult {
        val service = getService()
            ?: return StepExecutionResult(
                status = StepExecutionStatus.FAILED,
                stepOrder = 0,
                actionType = ActionType.TAP,
                message = "Accessibility Service is not connected."
            )

        val startTime = System.currentTimeMillis()
        service.showVisualTouchDot(x, y, "TAP X: $x, Y: $y")
        val success = service.performTap(x, y, durationMs)
        val latency = System.currentTimeMillis() - startTime

        return StepExecutionResult(
            status = if (success) StepExecutionStatus.SUCCESS else StepExecutionStatus.FAILED,
            stepOrder = 0,
            actionType = ActionType.TAP,
            message = if (success) "Gesture dispatched successfully at ($x, $y)" else "Tap gesture failed or timed out",
            latencyMs = latency
        )
    }

    override suspend fun executeLongPress(x: Int, y: Int, durationMs: Long): StepExecutionResult {
        val service = getService()
            ?: return StepExecutionResult(
                status = StepExecutionStatus.FAILED,
                stepOrder = 0,
                actionType = ActionType.LONG_PRESS,
                message = "Accessibility Service is not connected."
            )

        val startTime = System.currentTimeMillis()
        service.showVisualTouchDot(x, y, "HOLD X: $x, Y: $y")
        val success = service.performLongPress(x, y, durationMs)
        val latency = System.currentTimeMillis() - startTime

        return StepExecutionResult(
            status = if (success) StepExecutionStatus.SUCCESS else StepExecutionStatus.FAILED,
            stepOrder = 0,
            actionType = ActionType.LONG_PRESS,
            message = if (success) "Gesture dispatched successfully at ($x, $y)" else "Long press gesture failed or timed out",
            latencyMs = latency
        )
    }

    override suspend fun executeSwipe(startX: Int, startY: Int, endX: Int, endY: Int, durationMs: Long): StepExecutionResult {
        val service = getService()
            ?: return StepExecutionResult(
                status = StepExecutionStatus.FAILED,
                stepOrder = 0,
                actionType = ActionType.SWIPE,
                message = "Accessibility Service is not connected."
            )

        val startTime = System.currentTimeMillis()
        service.showVisualTouchDot(startX, startY, "SWIPE")
        val success = service.performSwipe(startX, startY, endX, endY, durationMs)
        val latency = System.currentTimeMillis() - startTime

        return StepExecutionResult(
            status = if (success) StepExecutionStatus.SUCCESS else StepExecutionStatus.FAILED,
            stepOrder = 0,
            actionType = ActionType.SWIPE,
            message = if (success) "Gesture dispatched successfully ($startX, $startY) -> ($endX, $endY)" else "Swipe gesture failed or timed out",
            latencyMs = latency
        )
    }

    override suspend fun executeWait(durationMs: Long): StepExecutionResult {
        delay(durationMs)
        return StepExecutionResult(
            status = StepExecutionStatus.SUCCESS,
            stepOrder = 0,
            actionType = ActionType.WAIT,
            message = "Wait completed (${durationMs}ms)"
        )
    }

    override suspend fun executeBack(): StepExecutionResult {
        val service = getService()
            ?: return StepExecutionResult(
                status = StepExecutionStatus.FAILED,
                stepOrder = 0,
                actionType = ActionType.BACK,
                message = "Accessibility Service is not connected."
            )

        val success = service.performBack()
        return StepExecutionResult(
            status = if (success) StepExecutionStatus.SUCCESS else StepExecutionStatus.FAILED,
            stepOrder = 0,
            actionType = ActionType.BACK,
            message = if (success) "Back navigation performed" else "Back navigation failed"
        )
    }

    override suspend fun testStep(step: WorkflowStep): StepExecutionResult {
        val deviceProfile = AccessibilityUtils.getDeviceProfile(context)
        val validation = CoordinateValidator.validateStep(step, deviceProfile.screenWidth, deviceProfile.screenHeight)
        if (!validation.isValid) {
            val reason = (validation as ValidationResult.Invalid).reason
            return StepExecutionResult(
                status = StepExecutionStatus.FAILED,
                stepOrder = step.order,
                actionType = step.type,
                message = "Coordinate validation failed: $reason"
            )
        }

        return executeStep(step)
    }

    override suspend fun executeStep(step: WorkflowStep): StepExecutionResult {
        return when (step.type) {
            ActionType.TAP -> executeTap(step.startX, step.startY, step.duration)
            ActionType.LONG_PRESS -> executeLongPress(step.startX, step.startY, step.duration)
            ActionType.SWIPE -> {
                val endX = step.endX ?: step.startX
                val endY = step.endY ?: step.startY
                executeSwipe(step.startX, step.startY, endX, endY, step.duration)
            }
            ActionType.WAIT -> executeWait(step.duration)
            ActionType.BACK -> executeBack()
            ActionType.DOUBLE_TAP -> {
                val first = executeTap(step.startX, step.startY, 50L)
                delay(100L)
                val second = executeTap(step.startX, step.startY, 50L)
                if (first.status == StepExecutionStatus.SUCCESS && second.status == StepExecutionStatus.SUCCESS) {
                    first
                } else {
                    StepExecutionResult(
                        status = StepExecutionStatus.FAILED,
                        stepOrder = step.order,
                        actionType = ActionType.DOUBLE_TAP,
                        message = "Double tap failed"
                    )
                }
            }
            else -> executeTap(step.startX, step.startY, step.duration)
        }.copy(stepOrder = step.order)
    }

    override suspend fun executeWorkflow(workflow: Workflow, speedMultiplier: Float) {
        val service = getService()
        if (service == null) {
            _executionSession.value = _executionSession.value.copy(
                taskId = workflow.id,
                taskTitle = workflow.name,
                state = ExecutionState.FAILED,
                errorMessage = "TaskSchedulingAccessibilityService is not connected. Please enable accessibility access in Android Settings."
            )
            return
        }

        val deviceProfile = AccessibilityUtils.getDeviceProfile(context)
        val validation = CoordinateValidator.validateWorkflow(workflow, deviceProfile.screenWidth, deviceProfile.screenHeight)
        if (!validation.isValid) {
            val reason = (validation as ValidationResult.Invalid).reason
            _executionSession.value = _executionSession.value.copy(
                taskId = workflow.id,
                taskTitle = workflow.name,
                state = ExecutionState.FAILED,
                errorMessage = reason
            )
            return
        }

        if (workflow.steps.isEmpty()) {
            _executionSession.value = ExecutionSession(
                taskId = workflow.id,
                taskTitle = workflow.name,
                state = ExecutionState.IDLE,
                currentStepIndex = 0,
                totalSteps = 0,
                progress = 0f,
                errorMessage = "Workflow has no automation steps configured."
            )
            return
        }

        isStopped = false
        isPaused = false

        _executionSession.value = ExecutionSession(
            taskId = workflow.id,
            taskTitle = workflow.name,
            state = ExecutionState.RUNNING,
            currentStepIndex = 0,
            totalSteps = workflow.steps.size,
            progress = 0f,
            logs = emptyList()
        )

        val multiplier = speedMultiplier.coerceIn(0.5f, 3.0f)
        val steps = workflow.steps.sortedBy { it.order }

        for (i in steps.indices) {
            if (isStopped) {
                _executionSession.value = _executionSession.value.copy(
                    state = ExecutionState.STOPPED,
                    errorMessage = "Automation stopped by user."
                )
                return
            }

            // Pause handling: Wait before starting next step
            while (isPaused && !isStopped) {
                delay(200L)
            }
            if (isStopped) {
                _executionSession.value = _executionSession.value.copy(
                    state = ExecutionState.STOPPED,
                    errorMessage = "Automation stopped by user."
                )
                return
            }

            val step = steps[i]
            _executionSession.value = _executionSession.value.copy(
                currentStepIndex = i,
                currentStep = step,
                activeCoordinate = CoordinateAction(step.startX, step.startY, step.endX, step.endY),
                activeActionType = step.type,
                progress = (i.toFloat() / steps.size.toFloat())
            )

            val result = executeStep(step)
            val logEntry = ExecutionLogEntry(
                timestamp = SimpleDateFormat("hh:mm:ss a", Locale.getDefault()).format(Date()),
                stepNumber = step.order,
                actionType = step.type,
                details = result.message,
                status = if (result.status == StepExecutionStatus.SUCCESS) "SUCCESS" else "FAILED",
                latencyMs = result.latencyMs
            )

            _executionSession.value = _executionSession.value.copy(
                logs = _executionSession.value.logs + logEntry
            )

            if (result.status != StepExecutionStatus.SUCCESS) {
                _executionSession.value = _executionSession.value.copy(
                    state = ExecutionState.FAILED,
                    errorMessage = "Step ${step.order} failed: ${result.message}"
                )
                return
            }

            // Wait after step
            if (step.waitAfter > 0) {
                delay((step.waitAfter / multiplier).toLong())
            }
        }

        // All steps completed successfully
        _executionSession.value = _executionSession.value.copy(
            state = ExecutionState.COMPLETED,
            progress = 1.0f,
            currentStep = null,
            activeCoordinate = null,
            errorMessage = null
        )
    }

    override suspend fun execute(task: Task, speedMultiplier: Float) {
        executeWorkflow(task.workflow, speedMultiplier)
    }

    override fun pause() {
        isPaused = true
        _executionSession.value = _executionSession.value.copy(state = ExecutionState.PAUSED)
    }

    override fun resume() {
        isPaused = false
        _executionSession.value = _executionSession.value.copy(state = ExecutionState.RUNNING)
    }

    override fun stop() {
        isStopped = true
        isPaused = false
        activeJob?.cancel()
        _executionSession.value = _executionSession.value.copy(
            state = ExecutionState.STOPPED,
            errorMessage = "Automation cancelled."
        )
    }

    override fun restart() {
        stop()
    }

    override suspend fun stepNext() {
        // Step-by-step debug mode execution
    }
}
