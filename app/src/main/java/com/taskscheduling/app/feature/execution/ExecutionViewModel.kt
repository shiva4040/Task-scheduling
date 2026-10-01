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

class ExecutionViewModel(
    private val automationEngine: AutomationEngine,
    private val taskRepository: TaskRepository,
    private val activityRepository: ActivityRepository
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
            val task = taskRepository.getTaskById(tid)
            if (task != null) {
                automationEngine.execute(task, _speed.value)
            }
        }
    }

    fun loadAndExecute(taskId: String) {
        loadTask(taskId)
    }

    fun pause() = automationEngine.pause()
    fun resume() = automationEngine.resume()
    fun stop() = automationEngine.stop()
    fun restart() = automationEngine.restart()
    fun stepNext() {
        viewModelScope.launch { automationEngine.stepNext() }
    }

    fun setSpeed(newSpeed: Float) {
        _speed.value = newSpeed
        activeTaskId?.let { loadAndExecute(it) }
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
