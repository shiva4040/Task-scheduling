package com.taskscheduling.app.automation.mock

import com.taskscheduling.app.automation.AutomationEngine
import com.taskscheduling.app.automation.StepExecutionResult
import com.taskscheduling.app.automation.StepExecutionStatus
import com.taskscheduling.app.domain.model.ActionType
import com.taskscheduling.app.domain.model.ExecutionLogEntry
import com.taskscheduling.app.domain.model.ExecutionSession
import com.taskscheduling.app.domain.model.ExecutionState
import com.taskscheduling.app.domain.model.Task
import com.taskscheduling.app.domain.model.Workflow
import com.taskscheduling.app.domain.model.WorkflowStep
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MockAutomationEngine(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) : AutomationEngine {

    private val _executionSession = MutableStateFlow(
        ExecutionSession(taskId = "", taskTitle = "Idle")
    )
    override val executionSession: StateFlow<ExecutionSession> = _executionSession.asStateFlow()

    private var executionJob: Job? = null
    private var activeTask: Task? = null
    private var currentSpeed: Float = 1.0f
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    override suspend fun execute(task: Task, speedMultiplier: Float) {
        stop()
        activeTask = task
        currentSpeed = speedMultiplier.coerceIn(0.2f, 3.0f)
        val steps = task.workflow.steps

        if (steps.isEmpty()) {
            _executionSession.value = ExecutionSession(
                taskId = task.id,
                taskTitle = task.title,
                state = ExecutionState.FAILED,
                errorMessage = "Workflow has no steps."
            )
            return
        }

        _executionSession.value = ExecutionSession(
            taskId = task.id,
            taskTitle = task.title,
            state = ExecutionState.RUNNING,
            currentStepIndex = 0,
            totalSteps = steps.size,
            currentStep = steps.firstOrNull(),
            progress = 0f,
            currentSimulatedScreen = steps.firstOrNull()?.targetScreen ?: "Home",
            activeCoordinate = steps.firstOrNull()?.coordinate,
            activeActionType = steps.firstOrNull()?.actionType,
            logs = listOf(
                ExecutionLogEntry(
                    timestamp = timeFormat.format(Date()),
                    stepNumber = 0,
                    actionType = ActionType.WAIT,
                    details = "Initialized execution for: ${task.title}",
                    status = "READY"
                )
            )
        )

        executionJob = scope.launch {
            runStepsLoop(startIndex = 0)
        }
    }

    private suspend fun runStepsLoop(startIndex: Int) {
        val task = activeTask ?: return
        val steps = task.workflow.steps

        for (i in startIndex until steps.size) {
            val step = steps[i]

            // Check if paused or stopped
            while (_executionSession.value.state == ExecutionState.PAUSED) {
                delay(150)
            }
            if (_executionSession.value.state == ExecutionState.STOPPED) {
                break
            }

            val stepStartTime = System.currentTimeMillis()
            val stepNumber = i + 1

            // Update state for current step active
            _executionSession.update { current ->
                current.copy(
                    state = ExecutionState.RUNNING,
                    currentStepIndex = i,
                    currentStep = step,
                    progress = (i.toFloat() / steps.size.toFloat()),
                    currentSimulatedScreen = step.targetScreen ?: current.currentSimulatedScreen,
                    activeCoordinate = step.coordinate,
                    activeActionType = step.actionType
                )
            }

            // Simulated step execution duration scaled by speed
            val stepDuration = (step.durationMs / currentSpeed).toLong().coerceAtLeast(350L)
            delay(stepDuration)

            val latency = System.currentTimeMillis() - stepStartTime
            val coordStr = if (step.coordinate != null) {
                if (step.coordinate.endX != null) {
                    "START: (${step.coordinate.x}, ${step.coordinate.y}) -> END: (${step.coordinate.endX}, ${step.coordinate.endY})"
                } else {
                    "X: ${step.coordinate.x}, Y: ${step.coordinate.y}"
                }
            } else {
                "System action"
            }

            val log = ExecutionLogEntry(
                timestamp = timeFormat.format(Date()),
                stepNumber = stepNumber,
                actionType = step.actionType,
                details = "${step.label} [$coordStr]",
                status = "SUCCESS",
                latencyMs = latency
            )

            _executionSession.update { current ->
                current.copy(
                    progress = ((i + 1).toFloat() / steps.size.toFloat()),
                    logs = current.logs + log
                )
            }

            // Brief pause between steps
            delay((250L / currentSpeed).toLong())
        }

        if (_executionSession.value.state == ExecutionState.RUNNING) {
            _executionSession.update { current ->
                current.copy(
                    state = ExecutionState.COMPLETED,
                    progress = 1.0f,
                    activeCoordinate = null,
                    activeActionType = null,
                    logs = current.logs + ExecutionLogEntry(
                        timestamp = timeFormat.format(Date()),
                        stepNumber = steps.size,
                        actionType = ActionType.WAIT,
                        details = "Task '${task.title}' completed successfully.",
                        status = "COMPLETED"
                    )
                )
            }
        }
    }

    override fun pause() {
        if (_executionSession.value.state == ExecutionState.RUNNING) {
            _executionSession.update { it.copy(state = ExecutionState.PAUSED) }
        }
    }

    override fun resume() {
        if (_executionSession.value.state == ExecutionState.PAUSED) {
            _executionSession.update { it.copy(state = ExecutionState.RUNNING) }
        }
    }

    override fun stop() {
        executionJob?.cancel(CancellationException("Stopped by user"))
        _executionSession.update { current ->
            current.copy(
                state = ExecutionState.STOPPED,
                activeCoordinate = null,
                activeActionType = null
            )
        }
    }

    override fun restart() {
        val task = activeTask ?: return
        scope.launch {
            execute(task, currentSpeed)
        }
    }

    override suspend fun stepNext() {
        val task = activeTask ?: return
        val current = _executionSession.value
        val nextIndex = (current.currentStepIndex + 1).coerceAtMost(task.workflow.steps.size - 1)
        val step = task.workflow.steps[nextIndex]

        _executionSession.update {
            it.copy(
                currentStepIndex = nextIndex,
                currentStep = step,
                currentSimulatedScreen = step.targetScreen ?: it.currentSimulatedScreen,
                activeCoordinate = step.coordinate,
                activeActionType = step.actionType,
                progress = ((nextIndex + 1).toFloat() / task.workflow.steps.size.toFloat())
            )
        }
    }

    override suspend fun executeTap(x: Int, y: Int, durationMs: Long): StepExecutionResult {
        delay(durationMs)
        return StepExecutionResult(StepExecutionStatus.SUCCESS, 0, ActionType.TAP, "Mock Tap at ($x, $y)")
    }

    override suspend fun executeLongPress(x: Int, y: Int, durationMs: Long): StepExecutionResult {
        delay(durationMs)
        return StepExecutionResult(StepExecutionStatus.SUCCESS, 0, ActionType.LONG_PRESS, "Mock Long Press at ($x, $y)")
    }

    override suspend fun executeSwipe(startX: Int, startY: Int, endX: Int, endY: Int, durationMs: Long): StepExecutionResult {
        delay(durationMs)
        return StepExecutionResult(StepExecutionStatus.SUCCESS, 0, ActionType.SWIPE, "Mock Swipe ($startX, $startY) -> ($endX, $endY)")
    }

    override suspend fun executeWait(durationMs: Long): StepExecutionResult {
        delay(durationMs)
        return StepExecutionResult(StepExecutionStatus.SUCCESS, 0, ActionType.WAIT, "Mock Wait ${durationMs}ms")
    }

    override suspend fun executeBack(): StepExecutionResult {
        return StepExecutionResult(StepExecutionStatus.SUCCESS, 0, ActionType.BACK, "Mock Back navigation")
    }

    override suspend fun executeStep(step: WorkflowStep): StepExecutionResult {
        return when (step.type) {
            ActionType.TAP -> executeTap(step.startX, step.startY, step.duration)
            ActionType.LONG_PRESS -> executeLongPress(step.startX, step.startY, step.duration)
            ActionType.SWIPE -> executeSwipe(step.startX, step.startY, step.endX ?: step.startX, step.endY ?: step.startY, step.duration)
            ActionType.WAIT -> executeWait(step.duration)
            ActionType.BACK -> executeBack()
            else -> executeTap(step.startX, step.startY, step.duration)
        }.copy(stepOrder = step.order)
    }

    override suspend fun executeWorkflow(workflow: Workflow, speedMultiplier: Float) {
        val dummyTask = Task(
            id = workflow.id,
            title = workflow.name,
            description = workflow.description,
            category = com.taskscheduling.app.domain.model.TaskCategory.CUSTOM,
            workflow = workflow
        )
        execute(dummyTask, speedMultiplier)
    }

    override suspend fun testStep(step: WorkflowStep): StepExecutionResult {
        return executeStep(step)
    }
}
