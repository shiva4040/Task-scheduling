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

    private var lastWorkflowExecuted: Workflow? = null
    private var lastSpeedMultiplier: Float = 1.0f

    @Volatile
    private var isStepNextRequested = false

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

    suspend fun executeDrag(startX: Int, startY: Int, endX: Int, endY: Int, durationMs: Long): StepExecutionResult {
        val service = getService()
            ?: return StepExecutionResult(
                status = StepExecutionStatus.FAILED,
                stepOrder = 0,
                actionType = ActionType.DRAG,
                message = "Accessibility Service is not connected."
            )

        val startTime = System.currentTimeMillis()
        service.showVisualTouchDot(startX, startY, "DRAG START")
        val dur = durationMs.coerceAtLeast(600L)
        val success = service.performSwipe(startX, startY, endX, endY, dur)
        val latency = System.currentTimeMillis() - startTime

        return StepExecutionResult(
            status = if (success) StepExecutionStatus.SUCCESS else StepExecutionStatus.FAILED,
            stepOrder = 0,
            actionType = ActionType.DRAG,
            message = if (success) "Drag gesture dispatched ($startX, $startY) -> ($endX, $endY)" else "Drag gesture failed or timed out",
            latencyMs = latency
        )
    }

    suspend fun executeTextInput(text: String, x: Int = -1, y: Int = -1): StepExecutionResult {
        val service = getService()
            ?: return StepExecutionResult(
                status = StepExecutionStatus.FAILED,
                stepOrder = 0,
                actionType = ActionType.TEXT_INPUT,
                message = "Accessibility Service is not connected."
            )

        if (x > 0 && y > 0) {
            executeTap(x, y, 100L)
            delay(300L)
        }

        val rootNode = service.rootInActiveWindow
        val targetNode = rootNode?.findFocus(android.view.accessibility.AccessibilityNodeInfo.FOCUS_INPUT)
            ?: rootNode
        var success = false
        if (targetNode != null) {
            val arguments = android.os.Bundle().apply {
                putCharSequence(
                    android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    text
                )
            }
            success = targetNode.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
        }

        return StepExecutionResult(
            status = if (success) StepExecutionStatus.SUCCESS else StepExecutionStatus.FAILED,
            stepOrder = 0,
            actionType = ActionType.TEXT_INPUT,
            message = if (success) "Text entered: \"$text\"" else "Failed to set text: \"$text\" (No active input target)"
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

    suspend fun executeHome(): StepExecutionResult {
        val service = getService()
            ?: return StepExecutionResult(
                status = StepExecutionStatus.FAILED,
                stepOrder = 0,
                actionType = ActionType.HOME,
                message = "Accessibility Service is not connected."
            )

        val success = service.performHome()
        return StepExecutionResult(
            status = if (success) StepExecutionStatus.SUCCESS else StepExecutionStatus.FAILED,
            stepOrder = 0,
            actionType = ActionType.HOME,
            message = if (success) "Home navigation performed" else "Home navigation failed"
        )
    }

    suspend fun executeRecents(): StepExecutionResult {
        val service = getService()
            ?: return StepExecutionResult(
                status = StepExecutionStatus.FAILED,
                stepOrder = 0,
                actionType = ActionType.RECENTS,
                message = "Accessibility Service is not connected."
            )

        val success = service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_RECENTS)
        return StepExecutionResult(
            status = if (success) StepExecutionStatus.SUCCESS else StepExecutionStatus.FAILED,
            stepOrder = 0,
            actionType = ActionType.RECENTS,
            message = if (success) "Recents overview performed" else "Recents overview failed"
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
        val effectiveType = step.actionType
        val x = if (step.startX > 0) step.startX else step.coordinate?.x ?: 0
        val y = if (step.startY > 0) step.startY else step.coordinate?.y ?: 0
        val endX = step.endX ?: step.coordinate?.endX ?: x
        val endY = step.endY ?: step.coordinate?.endY ?: y
        val dur = if (step.duration > 0) step.duration else step.durationMs

        return when (effectiveType) {
            ActionType.TAP -> executeTap(x, y, dur)
            ActionType.LONG_PRESS -> executeLongPress(x, y, dur)
            ActionType.SWIPE -> executeSwipe(x, y, endX, endY, dur)
            ActionType.DRAG -> executeDrag(x, y, endX, endY, dur)
            ActionType.TEXT_INPUT -> executeTextInput(step.textInput ?: "", x, y)
            ActionType.WAIT -> executeWait(if (dur > 0) dur else step.waitAfter.coerceAtLeast(500L))
            ActionType.BACK -> executeBack()
            ActionType.HOME -> executeHome()
            ActionType.RECENTS -> executeRecents()
            ActionType.DOUBLE_TAP -> {
                val first = executeTap(x, y, 50L)
                delay(100L)
                val second = executeTap(x, y, 50L)
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
            ActionType.CUSTOM_GESTURE -> executeTap(x, y, dur)
        }.copy(stepOrder = step.order)
    }

    override suspend fun executeWorkflow(workflow: Workflow, speedMultiplier: Float) {
        lastWorkflowExecuted = workflow
        lastSpeedMultiplier = speedMultiplier

        if (workflow.steps.isEmpty()) {
            _executionSession.value = ExecutionSession(
                taskId = workflow.id,
                taskTitle = workflow.name,
                state = ExecutionState.FAILED,
                currentStepIndex = 0,
                totalSteps = 0,
                progress = 0f,
                errorMessage = "Workflow has no automation steps configured."
            )
            return
        }

        val totalStepsCount = workflow.steps.size

        val service = getService()
        if (service == null) {
            _executionSession.value = ExecutionSession(
                taskId = workflow.id,
                taskTitle = workflow.name,
                state = ExecutionState.FAILED,
                currentStepIndex = 0,
                totalSteps = totalStepsCount,
                progress = 0f,
                errorMessage = "TaskSchedulingAccessibilityService is not connected. Please enable accessibility access in Android Settings."
            )
            return
        }

        val deviceProfile = AccessibilityUtils.getDeviceProfile(context)
        val validation = CoordinateValidator.validateWorkflow(workflow, deviceProfile.screenWidth, deviceProfile.screenHeight)
        if (!validation.isValid) {
            val reason = (validation as ValidationResult.Invalid).reason
            _executionSession.value = ExecutionSession(
                taskId = workflow.id,
                taskTitle = workflow.name,
                state = ExecutionState.FAILED,
                currentStepIndex = 0,
                totalSteps = totalStepsCount,
                progress = 0f,
                errorMessage = reason
            )
            return
        }

        isStopped = false
        isPaused = false
        isStepNextRequested = false

        _executionSession.value = ExecutionSession(
            taskId = workflow.id,
            taskTitle = workflow.name,
            state = ExecutionState.RUNNING,
            currentStepIndex = 0,
            totalSteps = totalStepsCount,
            progress = 0f,
            logs = emptyList()
        )

        val multiplier = speedMultiplier.coerceIn(0.5f, 3.0f)
        val steps = workflow.steps.sortedBy { it.order }

        for (i in steps.indices) {
            if (isStopped) {
                _executionSession.value = _executionSession.value.copy(
                    state = ExecutionState.STOPPED,
                    errorMessage = "Automation cancelled."
                )
                return
            }

            // Pause & stepNext handling
            while (isPaused && !isStopped && !isStepNextRequested) {
                delay(100L)
            }
            if (isStopped) {
                _executionSession.value = _executionSession.value.copy(
                    state = ExecutionState.STOPPED,
                    errorMessage = "Automation cancelled."
                )
                return
            }
            val stepJustStepped = isStepNextRequested
            if (isStepNextRequested) {
                isStepNextRequested = false
            }

            val step = steps[i]
            val stepCoord = CoordinateAction(
                step.startX.let { if (it > 0) it else step.coordinate?.x ?: 0 },
                step.startY.let { if (it > 0) it else step.coordinate?.y ?: 0 },
                step.endX ?: step.coordinate?.endX,
                step.endY ?: step.coordinate?.endY
            )

            _executionSession.value = _executionSession.value.copy(
                currentStepIndex = i,
                currentStep = step,
                activeCoordinate = stepCoord,
                activeActionType = step.actionType,
                progress = (i.toFloat() / steps.size.toFloat())
            )

            val result = executeStep(step)
            val logEntry = ExecutionLogEntry(
                timestamp = SimpleDateFormat("hh:mm:ss a", Locale.getDefault()).format(Date()),
                stepNumber = step.order,
                actionType = step.actionType,
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

            // If this was a single step-next action while paused, pause again for subsequent step
            if (stepJustStepped) {
                isPaused = true
                _executionSession.value = _executionSession.value.copy(state = ExecutionState.PAUSED)
            }

            // Wait after step
            val waitMs = if (step.waitAfter > 0) step.waitAfter else step.waitDurationMs
            if (waitMs > 0) {
                delay((waitMs / multiplier).toLong())
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
        isStepNextRequested = false
        activeJob?.cancel()
        _executionSession.value = _executionSession.value.copy(
            state = ExecutionState.STOPPED,
            errorMessage = "Automation cancelled."
        )
    }

    override fun restart() {
        stop()
        val wf = lastWorkflowExecuted ?: return
        val speed = lastSpeedMultiplier
        engineScope.launch {
            delay(150L)
            executeWorkflow(wf, speed)
        }
    }

    override suspend fun stepNext() {
        if (isPaused) {
            isStepNextRequested = true
        }
    }
}
