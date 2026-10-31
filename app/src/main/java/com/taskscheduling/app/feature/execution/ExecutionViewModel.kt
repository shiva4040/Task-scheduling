package com.taskscheduling.app.feature.execution

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.taskscheduling.app.automation.AutomationEngine
import com.taskscheduling.app.data.repository.ActivityRepository
import com.taskscheduling.app.data.repository.TaskRepository
import com.taskscheduling.app.domain.model.ActivityRecord
import com.taskscheduling.app.domain.model.ActivityStatus
import com.taskscheduling.app.domain.model.ExecutionSession
import com.taskscheduling.app.domain.model.ExecutionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import com.taskscheduling.app.data.repository.WorkflowPersistenceRepository
import com.taskscheduling.app.domain.model.Workflow

class ExecutionViewModel(
    private val automationEngine: AutomationEngine,
    private val taskRepository: TaskRepository,
    private val activityRepository: ActivityRepository,
    private val workflowPersistenceRepository: WorkflowPersistenceRepository? = null
) : ViewModel() {

    val executionSession: StateFlow<ExecutionSession> = automationEngine.executionSession

    private val _speed = MutableStateFlow(1.0f)
    val speed: StateFlow<Float> = _speed.asStateFlow()

    private var activeTaskId: String? = null

    fun loadTask(taskId: String) {
        activeTaskId = taskId
    }

    fun startExecution() {
        val tid = activeTaskId ?: return
        viewModelScope.launch {
            // 1. Try finding in taskRepository first
            val task = taskRepository.getTaskById(tid)
            var workflow = task?.workflow

            // 2. If not found or workflow has no steps, check workflowPersistenceRepository
            if ((workflow == null || workflow.steps.isEmpty()) && workflowPersistenceRepository != null) {
                val persisted = workflowPersistenceRepository.getWorkflow(tid)
                if (persisted != null) {
                    workflow = persisted
                }
            }

            if (workflow != null) {
                automationEngine.executeWorkflow(workflow, _speed.value)
            } else {
                // Also check if tid matched a task whose workflow.id matches
                val allWorkflows = workflowPersistenceRepository?.getAllWorkflows() ?: emptyList()
                val matched = allWorkflows.find { it.id == tid || it.name.equals(tid, ignoreCase = true) }
                if (matched != null) {
                    automationEngine.executeWorkflow(matched, _speed.value)
                }
            }
        }
    }

    fun loadAndExecute(taskId: String) {
        loadTask(taskId)
        startExecution()
    }

    fun pause() = automationEngine.pause()

    fun resume() {
        val currentState = executionSession.value.state
        if (currentState == ExecutionState.PAUSED) {
            automationEngine.resume()
        } else {
            // Re-run if finished, cancelled, or idle
            startExecution()
        }
    }

    fun stop() = automationEngine.stop()

    fun restart() {
        if (activeTaskId != null) {
            automationEngine.stop()
            startExecution()
        } else {
            automationEngine.restart()
        }
    }

    fun stepNext() {
        viewModelScope.launch { automationEngine.stepNext() }
    }

    fun setSpeed(newSpeed: Float) {
        _speed.value = newSpeed
    }

    fun recordCompletedActivity() {
        val session = executionSession.value
        if (session.state == ExecutionState.COMPLETED) {
            viewModelScope.launch {
                val record = ActivityRecord(
                    id = "act_${System.currentTimeMillis()}",
                    taskTitle = session.taskTitle,
                    timestamp = "Just now, " + SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date()),
                    durationText = "${(session.totalSteps * 0.5f)}s",
                    status = ActivityStatus.COMPLETED,
                    stepCount = session.totalSteps,
                    logs = session.logs
                )
                activityRepository.addActivity(record)
            }
        }
    }
}
