package com.taskscheduling.app.feature.builder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.taskscheduling.app.data.repository.TaskRepository
import com.taskscheduling.app.domain.model.ActionType
import com.taskscheduling.app.domain.model.CoordinateAction
import com.taskscheduling.app.domain.model.Task
import com.taskscheduling.app.domain.model.Workflow
import com.taskscheduling.app.domain.model.WorkflowStep
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class BuilderUiState(
    val task: Task? = null,
    val selectedStepIndex: Int = 0,
    val selectedScreen: String = "Home",
    val showRadarGrid: Boolean = false,
    val isInspectorOpen: Boolean = false,
    val isDirty: Boolean = false
)

class BuilderViewModel(
    private val taskRepository: TaskRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BuilderUiState())
    val uiState: StateFlow<BuilderUiState> = _uiState.asStateFlow()

    fun loadTask(taskId: String?) {
        viewModelScope.launch {
            val task = if (taskId != null) {
                taskRepository.getTaskById(taskId)
            } else {
                taskRepository.getTaskById("task_wallpaper")
            } ?: taskRepository.getTaskById("task_wallpaper")

            _uiState.value = BuilderUiState(
                task = task,
                selectedStepIndex = 0,
                selectedScreen = task?.workflow?.steps?.firstOrNull()?.targetScreen ?: "Home"
            )
        }
    }

    fun selectStep(index: Int) {
        val steps = _uiState.value.task?.workflow?.steps ?: return
        if (index in steps.indices) {
            val step = steps[index]
            _uiState.value = _uiState.value.copy(
                selectedStepIndex = index,
                selectedScreen = step.targetScreen ?: _uiState.value.selectedScreen,
                isInspectorOpen = true
            )
        }
    }

    fun setScreen(screenName: String) {
        _uiState.value = _uiState.value.copy(selectedScreen = screenName)
    }

    fun toggleRadarGrid() {
        _uiState.value = _uiState.value.copy(showRadarGrid = !_uiState.value.showRadarGrid)
    }

    fun onPhoneScreenTapped(coord: CoordinateAction) {
        val currentTask = _uiState.value.task ?: return
        val steps = currentTask.workflow.steps.toMutableList()
        val currentIndex = _uiState.value.selectedStepIndex

        if (currentIndex in steps.indices) {
            // Update current step coordinate
            val oldStep = steps[currentIndex]
            val updated = oldStep.copy(coordinate = coord, targetScreen = _uiState.value.selectedScreen)
            steps[currentIndex] = updated
            updateWorkflowSteps(steps)
        } else {
            // Add new step at coordinate
            addNewStepAtCoord(coord)
        }
    }

    fun addNewStepAtCoord(coord: CoordinateAction) {
        val currentTask = _uiState.value.task ?: return
        val steps = currentTask.workflow.steps.toMutableList()
        val newStepNum = steps.size + 1
        val newStep = WorkflowStep(
            id = "step_${System.currentTimeMillis()}",
            stepNumber = newStepNum,
            label = "Tap at (${coord.x}, ${coord.y})",
            actionType = ActionType.TAP,
            coordinate = coord,
            durationMs = 400L,
            explanation = "Tap coordinate",
            targetScreen = _uiState.value.selectedScreen
        )
        steps.add(newStep)
        updateWorkflowSteps(steps)
        _uiState.value = _uiState.value.copy(
            selectedStepIndex = steps.size - 1,
            isInspectorOpen = true
        )
    }

    fun updateSelectedStep(
        label: String,
        actionType: ActionType,
        x: Int,
        y: Int,
        endX: Int?,
        endY: Int?,
        durationMs: Long
    ) {
        val currentTask = _uiState.value.task ?: return
        val steps = currentTask.workflow.steps.toMutableList()
        val index = _uiState.value.selectedStepIndex

        if (index in steps.indices) {
            val coord = CoordinateAction(x = x, y = y, endX = endX, endY = endY)
            val updated = steps[index].copy(
                label = label,
                actionType = actionType,
                coordinate = coord,
                durationMs = durationMs
            )
            steps[index] = updated
            updateWorkflowSteps(steps)
        }
    }

    fun deleteSelectedStep() {
        val currentTask = _uiState.value.task ?: return
        val steps = currentTask.workflow.steps.toMutableList()
        val index = _uiState.value.selectedStepIndex

        if (index in steps.indices && steps.size > 1) {
            steps.removeAt(index)
            // Re-index steps
            val reindexed = steps.mapIndexed { idx, s -> s.copy(stepNumber = idx + 1) }
            updateWorkflowSteps(reindexed)
            _uiState.value = _uiState.value.copy(
                selectedStepIndex = (index - 1).coerceAtLeast(0)
            )
        }
    }

    fun duplicateSelectedStep() {
        val currentTask = _uiState.value.task ?: return
        val steps = currentTask.workflow.steps.toMutableList()
        val index = _uiState.value.selectedStepIndex

        if (index in steps.indices) {
            val original = steps[index]
            val duplicate = original.copy(
                id = "step_${System.currentTimeMillis()}",
                label = "${original.label} (Copy)"
            )
            steps.add(index + 1, duplicate)
            val reindexed = steps.mapIndexed { idx, s -> s.copy(stepNumber = idx + 1) }
            updateWorkflowSteps(reindexed)
            _uiState.value = _uiState.value.copy(selectedStepIndex = index + 1)
        }
    }

    fun moveStepUp(index: Int) {
        val currentTask = _uiState.value.task ?: return
        val steps = currentTask.workflow.steps.toMutableList()
        if (index > 0 && index in steps.indices) {
            val temp = steps[index]
            steps[index] = steps[index - 1]
            steps[index - 1] = temp
            val reindexed = steps.mapIndexed { idx, s -> s.copy(stepNumber = idx + 1) }
            updateWorkflowSteps(reindexed)
            _uiState.value = _uiState.value.copy(selectedStepIndex = index - 1)
        }
    }

    fun moveStepDown(index: Int) {
        val currentTask = _uiState.value.task ?: return
        val steps = currentTask.workflow.steps.toMutableList()
        if (index < steps.size - 1 && index in steps.indices) {
            val temp = steps[index]
            steps[index] = steps[index + 1]
            steps[index + 1] = temp
            val reindexed = steps.mapIndexed { idx, s -> s.copy(stepNumber = idx + 1) }
            updateWorkflowSteps(reindexed)
            _uiState.value = _uiState.value.copy(selectedStepIndex = index + 1)
        }
    }

    fun closeInspector() {
        _uiState.value = _uiState.value.copy(isInspectorOpen = false)
    }

    private fun updateWorkflowSteps(steps: List<WorkflowStep>) {
        val currentTask = _uiState.value.task ?: return
        val updatedTask = currentTask.copy(
            workflow = currentTask.workflow.copy(steps = steps)
        )
        _uiState.value = _uiState.value.copy(task = updatedTask, isDirty = true)
        viewModelScope.launch {
            taskRepository.saveTask(updatedTask)
        }
    }
}
