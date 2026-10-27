package com.taskscheduling.app.feature.builder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.taskscheduling.app.data.repository.TaskRepository
import com.taskscheduling.app.domain.model.ActionType
import com.taskscheduling.app.domain.model.CoordinateAction
import com.taskscheduling.app.domain.model.Task
import com.taskscheduling.app.domain.model.Workflow
import com.taskscheduling.app.domain.model.WorkflowStatus
import com.taskscheduling.app.domain.model.WorkflowStep
import com.taskscheduling.app.domain.validation.WorkflowValidationResult
import com.taskscheduling.app.domain.validation.WorkflowValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

data class BuilderUiState(
    val task: Task? = null,
    val selectedStepIndex: Int = 0,
    val selectedStepId: String? = null,
    val selectedScreen: String = "Home",
    val showRadarGrid: Boolean = false,
    val isInspectorOpen: Boolean = false,
    val isDirty: Boolean = false,
    val isSaving: Boolean = false,
    val saveSuccessMessage: String? = null,
    val saveErrorMessage: String? = null,
    val validationErrors: Map<String, String> = emptyMap()
)

class BuilderViewModel(
    private val taskRepository: TaskRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BuilderUiState())
    val uiState: StateFlow<BuilderUiState> = _uiState.asStateFlow()

    fun loadTask(taskId: String?) {
        viewModelScope.launch {
            val task = if (!taskId.isNullOrBlank()) {
                taskRepository.getTaskById(taskId) ?: createDefaultNewTask(taskId)
            } else {
                createDefaultNewTask(null)
            }

            val firstStep = task.workflow.steps.firstOrNull()

            _uiState.value = BuilderUiState(
                task = task,
                selectedStepIndex = 0,
                selectedStepId = firstStep?.id,
                selectedScreen = firstStep?.targetScreen ?: "Home",
                isDirty = false,
                isSaving = false,
                saveSuccessMessage = null,
                saveErrorMessage = null,
                validationErrors = emptyMap()
            )
        }
    }

    private fun createDefaultNewTask(preferredId: String?): Task {
        val uniqueId = preferredId?.ifBlank { null } ?: "workflow_${UUID.randomUUID()}"
        val initialStep = WorkflowStep(
            id = "step_${UUID.randomUUID()}",
            order = 1,
            type = ActionType.TAP,
            startX = 540,
            startY = 1200,
            duration = 100L,
            waitAfter = 500L,
            description = "Tap button",
            stepNumber = 1,
            label = "Tap Button",
            actionType = ActionType.TAP,
            coordinate = CoordinateAction(540, 1200)
        )

        val newWf = Workflow(
            id = uniqueId,
            name = "New Automation Workflow",
            description = "Custom automated workflow",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            status = WorkflowStatus.CALIBRATED,
            steps = listOf(initialStep),
            schemaVersion = 2
        )

        return Task(
            id = uniqueId,
            title = "New Automation Workflow",
            description = "Custom automated workflow",
            workflow = newWf,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
    }

    fun selectStep(index: Int) {
        val steps = _uiState.value.task?.workflow?.steps ?: return
        if (index in steps.indices) {
            val step = steps[index]
            _uiState.value = _uiState.value.copy(
                selectedStepIndex = index,
                selectedStepId = step.id,
                selectedScreen = step.targetScreen ?: _uiState.value.selectedScreen,
                isInspectorOpen = true
            )
        }
    }

    fun selectStepById(stepId: String) {
        val steps = _uiState.value.task?.workflow?.steps ?: return
        val index = steps.indexOfFirst { it.id == stepId }
        if (index >= 0) {
            selectStep(index)
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
            val oldStep = steps[currentIndex]
            val updated = oldStep.copyWithSync(
                coordinate = coord,
                targetScreen = _uiState.value.selectedScreen
            )
            steps[currentIndex] = updated
            updateWorkflowSteps(steps)
        } else {
            addNewStepAtCoord(coord)
        }
    }

    fun addNewStepAtCoord(coord: CoordinateAction) {
        val currentTask = _uiState.value.task ?: return
        val steps = currentTask.workflow.steps.toMutableList()
        val newStepNum = steps.size + 1
        val newStep = WorkflowStep(
            id = "step_${UUID.randomUUID()}",
            order = newStepNum,
            type = ActionType.TAP,
            startX = coord.x,
            startY = coord.y,
            endX = coord.endX,
            endY = coord.endY,
            duration = 100L,
            waitAfter = 500L,
            description = "Tap at (${coord.x}, ${coord.y})",
            stepNumber = newStepNum,
            label = "Tap at (${coord.x}, ${coord.y})",
            actionType = ActionType.TAP,
            coordinate = coord,
            durationMs = 100L,
            explanation = "Tap coordinate",
            targetScreen = _uiState.value.selectedScreen
        )
        steps.add(newStep)
        updateWorkflowSteps(steps)
        _uiState.value = _uiState.value.copy(
            selectedStepIndex = steps.size - 1,
            selectedStepId = newStep.id,
            isInspectorOpen = true
        )
    }

    fun addNewStepWithType(type: ActionType) {
        val currentTask = _uiState.value.task ?: return
        val steps = currentTask.workflow.steps.toMutableList()
        val newStepNum = steps.size + 1
        val defaultCoord = when (type) {
            ActionType.SWIPE, ActionType.DRAG -> CoordinateAction(x = 540, y = 400, endX = 540, endY = 1200)
            ActionType.WAIT, ActionType.BACK, ActionType.HOME, ActionType.RECENTS, ActionType.TEXT_INPUT -> CoordinateAction(x = 0, y = 0)
            else -> CoordinateAction(x = 540, y = 1200)
        }
        val defaultDuration = when (type) {
            ActionType.LONG_PRESS -> 900L
            ActionType.WAIT -> 1000L
            ActionType.SWIPE, ActionType.DRAG -> 400L
            else -> 100L
        }

        val newStep = WorkflowStep(
            id = "step_${UUID.randomUUID()}",
            order = newStepNum,
            type = type,
            startX = defaultCoord.x,
            startY = defaultCoord.y,
            endX = defaultCoord.endX,
            endY = defaultCoord.endY,
            duration = defaultDuration,
            waitAfter = 500L,
            description = "${type.label} Action",
            stepNumber = newStepNum,
            label = "${type.label} Action",
            actionType = type,
            coordinate = defaultCoord,
            durationMs = defaultDuration,
            explanation = "${type.label} action",
            targetScreen = _uiState.value.selectedScreen
        )
        steps.add(newStep)
        updateWorkflowSteps(steps)
        _uiState.value = _uiState.value.copy(
            selectedStepIndex = steps.size - 1,
            selectedStepId = newStep.id,
            isInspectorOpen = true
        )
    }

    fun addNewStepWithAction(actionItem: com.taskscheduling.app.domain.catalog.ActionCatalogItem) {
        val currentTask = _uiState.value.task ?: return
        val steps = currentTask.workflow.steps.toMutableList()
        val newStepNum = steps.size + 1
        val type = actionItem.actionType
        val defaultCoord = when (type) {
            ActionType.SWIPE, ActionType.DRAG -> CoordinateAction(x = 540, y = 400, endX = 540, endY = 1200)
            ActionType.WAIT, ActionType.BACK, ActionType.HOME, ActionType.RECENTS, ActionType.TEXT_INPUT -> CoordinateAction(x = 0, y = 0)
            else -> CoordinateAction(x = 540, y = 1200)
        }
        val defaultDuration = when (type) {
            ActionType.LONG_PRESS -> 900L
            ActionType.WAIT -> 1000L
            ActionType.SWIPE, ActionType.DRAG -> 400L
            else -> 100L
        }

        val newStep = WorkflowStep(
            id = "step_${UUID.randomUUID()}",
            order = newStepNum,
            type = type,
            startX = defaultCoord.x,
            startY = defaultCoord.y,
            endX = defaultCoord.endX,
            endY = defaultCoord.endY,
            duration = defaultDuration,
            waitAfter = 500L,
            description = actionItem.description,
            stepNumber = newStepNum,
            label = actionItem.title,
            actionType = type,
            coordinate = defaultCoord,
            durationMs = defaultDuration,
            explanation = actionItem.description,
            targetScreen = actionItem.executionTarget ?: _uiState.value.selectedScreen
        )
        steps.add(newStep)
        updateWorkflowSteps(steps)
        _uiState.value = _uiState.value.copy(
            selectedStepIndex = steps.size - 1,
            selectedStepId = newStep.id,
            isInspectorOpen = true
        )
    }

    fun insertStepBefore(index: Int, type: ActionType) {
        val currentTask = _uiState.value.task ?: return
        val steps = currentTask.workflow.steps.toMutableList()
        val insertIndex = index.coerceIn(0, steps.size)

        val defaultCoord = when (type) {
            ActionType.SWIPE, ActionType.DRAG -> CoordinateAction(x = 540, y = 400, endX = 540, endY = 1200)
            ActionType.WAIT, ActionType.BACK, ActionType.HOME, ActionType.RECENTS, ActionType.TEXT_INPUT -> CoordinateAction(x = 0, y = 0)
            else -> CoordinateAction(x = 540, y = 1200)
        }

        val newStep = WorkflowStep(
            id = "step_${UUID.randomUUID()}",
            order = insertIndex + 1,
            type = type,
            startX = defaultCoord.x,
            startY = defaultCoord.y,
            endX = defaultCoord.endX,
            endY = defaultCoord.endY,
            duration = 100L,
            waitAfter = 500L,
            description = "${type.label} Action",
            stepNumber = insertIndex + 1,
            label = "${type.label} Action",
            actionType = type,
            coordinate = defaultCoord,
            durationMs = 100L,
            targetScreen = _uiState.value.selectedScreen
        )

        steps.add(insertIndex, newStep)
        val reindexed = steps.mapIndexed { idx, s -> s.copyWithSync(stepNumber = idx + 1) }
        updateWorkflowSteps(reindexed)
        _uiState.value = _uiState.value.copy(
            selectedStepIndex = insertIndex,
            selectedStepId = newStep.id,
            isInspectorOpen = true
        )
    }

    fun insertStepAfter(index: Int, type: ActionType) {
        insertStepBefore(index + 1, type)
    }

    fun updateSelectedStep(
        label: String? = null,
        actionType: ActionType? = null,
        coordinate: CoordinateAction? = null,
        durationMs: Long? = null,
        targetScreen: String? = null,
        targetElement: String? = null,
        targetDescription: String? = null,
        textInput: String? = null,
        waitDurationMs: Long? = null,
        timeoutMs: Long? = null,
        expectedScreenState: String? = null,
        verificationRule: String? = null,
        retryLimit: Int? = null,
        recoveryBehavior: String? = null
    ) {
        val currentTask = _uiState.value.task ?: return
        val steps = currentTask.workflow.steps.toMutableList()
        val index = _uiState.value.selectedStepIndex

        if (index in steps.indices) {
            val oldStep = steps[index]
            val updated = oldStep.copyWithSync(
                label = label,
                actionType = actionType,
                coordinate = coordinate,
                durationMs = durationMs,
                targetScreen = targetScreen,
                targetElement = targetElement,
                targetDescription = targetDescription,
                textInput = textInput,
                waitDurationMs = waitDurationMs,
                timeoutMs = timeoutMs,
                expectedScreenState = expectedScreenState,
                verificationRule = verificationRule,
                retryLimit = retryLimit,
                recoveryBehavior = recoveryBehavior
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
            val reindexed = steps.mapIndexed { idx, s -> s.copyWithSync(stepNumber = idx + 1) }
            val nextIndex = (index - 1).coerceAtLeast(0)
            updateWorkflowSteps(reindexed)
            _uiState.value = _uiState.value.copy(
                selectedStepIndex = nextIndex,
                selectedStepId = reindexed.getOrNull(nextIndex)?.id
            )
        }
    }

    fun duplicateSelectedStep() {
        val currentTask = _uiState.value.task ?: return
        val steps = currentTask.workflow.steps.toMutableList()
        val index = _uiState.value.selectedStepIndex

        if (index in steps.indices) {
            val original = steps[index]
            val duplicate = original.copyWithSync(
                label = "${original.label} (Copy)"
            ).copy(id = "step_${UUID.randomUUID()}")

            steps.add(index + 1, duplicate)
            val reindexed = steps.mapIndexed { idx, s -> s.copyWithSync(stepNumber = idx + 1) }
            updateWorkflowSteps(reindexed)
            _uiState.value = _uiState.value.copy(
                selectedStepIndex = index + 1,
                selectedStepId = duplicate.id
            )
        }
    }

    fun moveStepUp(index: Int) {
        val currentTask = _uiState.value.task ?: return
        val steps = currentTask.workflow.steps.toMutableList()
        if (index > 0 && index in steps.indices) {
            val temp = steps[index]
            steps[index] = steps[index - 1]
            steps[index - 1] = temp
            val reindexed = steps.mapIndexed { idx, s -> s.copyWithSync(stepNumber = idx + 1) }
            updateWorkflowSteps(reindexed)
            _uiState.value = _uiState.value.copy(
                selectedStepIndex = index - 1,
                selectedStepId = temp.id
            )
        }
    }

    fun moveStepDown(index: Int) {
        val currentTask = _uiState.value.task ?: return
        val steps = currentTask.workflow.steps.toMutableList()
        if (index < steps.size - 1 && index in steps.indices) {
            val temp = steps[index]
            steps[index] = steps[index + 1]
            steps[index + 1] = temp
            val reindexed = steps.mapIndexed { idx, s -> s.copyWithSync(stepNumber = idx + 1) }
            updateWorkflowSteps(reindexed)
            _uiState.value = _uiState.value.copy(
                selectedStepIndex = index + 1,
                selectedStepId = temp.id
            )
        }
    }

    fun updateTaskDetails(title: String, description: String) {
        val currentTask = _uiState.value.task ?: return
        val trimmedTitle = title.trim()
        val trimmedDesc = description.trim()
        val updatedTask = currentTask.copy(
            title = trimmedTitle,
            description = trimmedDesc,
            workflow = currentTask.workflow.copy(name = trimmedTitle, description = trimmedDesc),
            updatedAt = System.currentTimeMillis()
        )
        _uiState.value = _uiState.value.copy(
            task = updatedTask,
            isDirty = true
        )
    }

    fun recalibrateStepCoordinates(
        index: Int,
        startX: Int,
        startY: Int,
        endX: Int?,
        endY: Int?,
        durationMs: Long
    ) {
        val currentTask = _uiState.value.task ?: return
        val steps = currentTask.workflow.steps.toMutableList()
        if (index in steps.indices) {
            val old = steps[index]
            val coord = CoordinateAction(x = startX, y = startY, endX = endX, endY = endY)
            val updated = old.copyWithSync(
                coordinate = coord,
                durationMs = durationMs,
                isVerified = true
            )
            steps[index] = updated
            updateWorkflowSteps(steps)
        }
    }

    fun validateWorkflow(): WorkflowValidationResult {
        val currentTask = _uiState.value.task
            ?: return WorkflowValidationResult.Invalid("No workflow is currently loaded.")
        return WorkflowValidator.validateWorkflow(currentTask.workflow)
    }

    fun saveTaskExplicitly(
        onSuccess: (() -> Unit)? = null
    ) {
        saveTaskWithFeedback(onSuccess = onSuccess, onError = null)
    }

    fun saveTaskWithFeedback(
        onSuccess: (() -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) {
        val currentTask = _uiState.value.task ?: run {
            onError?.invoke("No workflow loaded.")
            return
        }

        val validation = WorkflowValidator.validateWorkflow(currentTask.workflow)
        if (validation is WorkflowValidationResult.Invalid) {
            val errorMap = mutableMapOf<String, String>()
            if (validation.fieldName != null) {
                val key = if (validation.stepId != null) "${validation.stepId}_${validation.fieldName}" else validation.fieldName
                errorMap[key] = validation.message
            }
            _uiState.value = _uiState.value.copy(
                saveErrorMessage = validation.message,
                validationErrors = errorMap
            )
            onError?.invoke(validation.message)
            return
        }

        _uiState.value = _uiState.value.copy(
            isSaving = true,
            saveErrorMessage = null,
            validationErrors = emptyMap()
        )

        viewModelScope.launch(Dispatchers.IO) {
            try {
                taskRepository.saveTask(currentTask)
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    isDirty = false,
                    saveSuccessMessage = "Workflow saved successfully!"
                )
                onSuccess?.invoke()
            } catch (e: Throwable) {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    saveErrorMessage = "Failed to save workflow: ${e.message}"
                )
                onError?.invoke(e.message ?: "Save error")
            }
        }
    }

    fun dismissMessages() {
        _uiState.value = _uiState.value.copy(
            saveSuccessMessage = null,
            saveErrorMessage = null
        )
    }

    fun deleteTask(onSuccess: () -> Unit) {
        val currentTask = _uiState.value.task ?: return
        viewModelScope.launch {
            taskRepository.deleteTask(currentTask.id)
            onSuccess()
        }
    }

    fun closeInspector() {
        _uiState.value = _uiState.value.copy(isInspectorOpen = false)
    }

    private fun updateWorkflowSteps(steps: List<WorkflowStep>) {
        val currentTask = _uiState.value.task ?: return
        val hasAnyCoord = steps.any { it.requiresCoordinates && it.hasValidCoordinates }
        val updatedTask = currentTask.copy(
            workflow = currentTask.workflow.copy(
                steps = steps,
                status = if (hasAnyCoord) WorkflowStatus.CALIBRATED else WorkflowStatus.NOT_CALIBRATED,
                updatedAt = System.currentTimeMillis()
            ),
            updatedAt = System.currentTimeMillis()
        )
        _uiState.value = _uiState.value.copy(
            task = updatedTask,
            isDirty = true
        )
    }
}
