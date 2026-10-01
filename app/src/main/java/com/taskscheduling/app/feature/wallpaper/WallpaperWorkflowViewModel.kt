package com.taskscheduling.app.feature.wallpaper

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.taskscheduling.app.accessibility.AccessibilityUtils
import com.taskscheduling.app.accessibility.TaskSchedulingAccessibilityService
import com.taskscheduling.app.automation.AndroidAccessibilityAutomationEngine
import com.taskscheduling.app.automation.StepExecutionResult
import com.taskscheduling.app.automation.StepExecutionStatus
import com.taskscheduling.app.data.repository.ExecutionHistoryItem
import com.taskscheduling.app.data.repository.TaskRepository
import com.taskscheduling.app.data.repository.WorkflowPersistenceRepository
import com.taskscheduling.app.domain.model.ActionType
import com.taskscheduling.app.domain.model.CoordinateAction
import com.taskscheduling.app.domain.model.DeviceProfile
import com.taskscheduling.app.domain.model.ExecutionSession
import com.taskscheduling.app.domain.model.ExecutionState
import com.taskscheduling.app.domain.model.Task
import com.taskscheduling.app.domain.model.Workflow
import com.taskscheduling.app.domain.model.WorkflowStatus
import com.taskscheduling.app.domain.model.WorkflowStep
import com.taskscheduling.app.recording.CoordinateValidator
import com.taskscheduling.app.recording.RecordingManager
import com.taskscheduling.app.recording.ValidationResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class WallpaperWorkflowViewModel(
    private val taskRepository: TaskRepository,
    private val persistenceRepository: WorkflowPersistenceRepository,
    private val automationEngine: AndroidAccessibilityAutomationEngine,
    private val context: Context
) : ViewModel() {

    private val _task = MutableStateFlow<Task?>(null)
    val task: StateFlow<Task?> = _task.asStateFlow()

    private val _workflow = MutableStateFlow<Workflow?>(null)
    val workflow: StateFlow<Workflow?> = _workflow.asStateFlow()

    private val _currentDeviceProfile = MutableStateFlow(AccessibilityUtils.getDeviceProfile(context))
    val currentDeviceProfile: StateFlow<DeviceProfile> = _currentDeviceProfile.asStateFlow()

    private val _isAccessibilityConnected = MutableStateFlow(false)
    val isAccessibilityConnected: StateFlow<Boolean> = _isAccessibilityConnected.asStateFlow()

    val executionSession: StateFlow<ExecutionSession> = automationEngine.executionSession

    val isRecording: StateFlow<Boolean> = RecordingManager.isRecording
    val recordedSteps: StateFlow<List<WorkflowStep>> = RecordingManager.recordedSteps

    private val _history = MutableStateFlow<List<ExecutionHistoryItem>>(emptyList())
    val history: StateFlow<List<ExecutionHistoryItem>> = _history.asStateFlow()

    // Dialog & Flow States
    val showStartRecordingConfirmation = MutableStateFlow(false)
    val showRunConfirmation = MutableStateFlow(false)
    val showStopConfirmation = MutableStateFlow(false)
    val testStepConfirmation = MutableStateFlow<WorkflowStep?>(null)
    val testStepResult = MutableStateFlow<StepExecutionResult?>(null)
    val editingStep = MutableStateFlow<WorkflowStep?>(null)
    val validationError = MutableStateFlow<String?>(null)
    val deviceMismatchWarning = MutableStateFlow<String?>(null)
    val isReviewMode = MutableStateFlow(false)
    val unfinishedSessionPrompt = MutableStateFlow<com.taskscheduling.app.data.repository.RecordingSession?>(null)

    init {
        RecordingManager.initRepository(persistenceRepository)
        refreshServiceStatus()
        loadTask("task_wallpaper")
        checkForUnfinishedRecordingSession()
    }

    private fun checkForUnfinishedRecordingSession() {
        viewModelScope.launch {
            val session = persistenceRepository.loadRecordingSession()
            if (session != null && session.steps.isNotEmpty()) {
                unfinishedSessionPrompt.value = session
            }
        }
    }

    fun continueUnfinishedRecording() {
        val session = unfinishedSessionPrompt.value ?: return
        unfinishedSessionPrompt.value = null
        RecordingManager.resumeRecording(
            context = context,
            session = session,
            onFinish = {
                viewModelScope.launch {
                    isReviewMode.value = true
                }
            },
            onCancel = {
                viewModelScope.launch {
                    isReviewMode.value = false
                }
            }
        )
    }

    fun discardUnfinishedRecording() {
        unfinishedSessionPrompt.value = null
        viewModelScope.launch {
            persistenceRepository.clearRecordingSession()
            RecordingManager.cancelRecording()
        }
    }

    fun refreshServiceStatus() {
        _currentDeviceProfile.value = AccessibilityUtils.getDeviceProfile(context)
        val serviceConnected = TaskSchedulingAccessibilityService.isServiceConnected.value
        val settingsEnabled = AccessibilityUtils.isAccessibilityServiceEnabled(context)
        _isAccessibilityConnected.value = serviceConnected && settingsEnabled
    }

    fun openAccessibilitySettings() {
        AccessibilityUtils.openAccessibilitySettings(context)
    }

    fun loadTask(taskId: String) {
        viewModelScope.launch {
            refreshServiceStatus()
            val loadedTask = taskRepository.getTaskById(taskId)
            if (loadedTask != null) {
                _task.value = loadedTask
                val persistedWf = persistenceRepository.getWorkflow(loadedTask.workflow.id)
                val activeWf = persistedWf ?: loadedTask.workflow
                _workflow.value = activeWf
                loadHistory(activeWf.id)
            }
        }
    }

    private fun loadHistory(workflowId: String) {
        val allHistory = persistenceRepository.history.value
        _history.value = allHistory.filter { it.workflowId == workflowId }
    }

    // Recording Flow
    fun onRequestStartRecording() {
        showStartRecordingConfirmation.value = true
    }

    fun confirmStartRecording() {
        showStartRecordingConfirmation.value = false
        val currentWf = _workflow.value
        val wfId = if (currentWf != null && currentWf.id.isNotBlank()) currentWf.id else UUID.randomUUID().toString()
        val wfName = currentWf?.name ?: "Change Wallpaper"
        RecordingManager.startRecording(
            context = context,
            workflowId = wfId,
            workflowName = wfName,
            onFinish = {
                viewModelScope.launch {
                    isReviewMode.value = true
                }
            },
            onCancel = {
                viewModelScope.launch {
                    isReviewMode.value = false
                }
            }
        )
    }

    fun cancelStartRecording() {
        showStartRecordingConfirmation.value = false
    }

    fun finishRecordingAndReview() {
        val steps = RecordingManager.stopRecording()
        isReviewMode.value = true
    }

    fun cancelRecordingSession() {
        RecordingManager.cancelRecording()
        isReviewMode.value = false
    }

    // Step Manipulation in Review Mode
    fun deleteStep(stepId: String) {
        val current = RecordingManager.recordedSteps.value.filterNot { it.id == stepId }
        RecordingManager.updateSteps(current)
    }

    fun moveStepUp(index: Int) {
        val current = RecordingManager.recordedSteps.value.toMutableList()
        if (index > 0 && index < current.size) {
            val item = current.removeAt(index)
            current.add(index - 1, item)
            RecordingManager.updateSteps(current)
        }
    }

    fun moveStepDown(index: Int) {
        val current = RecordingManager.recordedSteps.value.toMutableList()
        if (index >= 0 && index < current.size - 1) {
            val item = current.removeAt(index)
            current.add(index + 1, item)
            RecordingManager.updateSteps(current)
        }
    }

    fun addWaitStep(durationMs: Long) {
        RecordingManager.recordWait(durationMs)
    }

    fun startEditingStep(step: WorkflowStep) {
        editingStep.value = step
    }

    fun saveEditedStep(updated: WorkflowStep) {
        val current = RecordingManager.recordedSteps.value.toMutableList()
        val idx = current.indexOfFirst { it.id == updated.id }
        if (idx >= 0) {
            current[idx] = updated
            RecordingManager.updateSteps(current)
        }
        editingStep.value = null
    }

    fun dismissEditingStep() {
        editingStep.value = null
    }

    // Test Step Execution
    fun requestTestStep(step: WorkflowStep) {
        testStepResult.value = null
        testStepConfirmation.value = step
    }

    fun confirmTestStep(step: WorkflowStep) {
        testStepConfirmation.value = null
        viewModelScope.launch {
            val result = automationEngine.testStep(step)
            testStepResult.value = result
        }
    }

    fun dismissTestStepConfirmation() {
        testStepConfirmation.value = null
    }

    fun dismissTestStepResult() {
        testStepResult.value = null
    }

    // Save Workflow
    fun saveWorkflow(): Boolean {
        refreshServiceStatus()
        val currentDevice = _currentDeviceProfile.value
        val steps = RecordingManager.recordedSteps.value

        val currentWf = _workflow.value
        val wfId = if (currentWf != null && currentWf.id.isNotBlank()) currentWf.id else UUID.randomUUID().toString()

        val targetWorkflow = Workflow(
            id = wfId,
            name = currentWf?.name ?: "Change Wallpaper",
            description = "Recorded on ${currentDevice.manufacturer} ${currentDevice.model}",
            createdAt = currentWf?.createdAt ?: System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            status = WorkflowStatus.CALIBRATED,
            deviceProfile = currentDevice,
            steps = steps,
            schemaVersion = 1
        )

        val validation = CoordinateValidator.validateWorkflow(targetWorkflow, currentDevice.screenWidth, currentDevice.screenHeight)
        if (!validation.isValid) {
            validationError.value = (validation as ValidationResult.Invalid).reason
            return false
        }

        viewModelScope.launch {
            persistenceRepository.saveWorkflow(targetWorkflow)
            _workflow.value = targetWorkflow
            _task.value?.let { currentTask ->
                val updatedTask = currentTask.copy(workflow = targetWorkflow)
                taskRepository.saveTask(updatedTask)
                _task.value = updatedTask
            }
            RecordingManager.cancelRecording()
            isReviewMode.value = false
            validationError.value = null
        }
        return true
    }

    fun dismissValidationError() {
        validationError.value = null
    }

    // Execution Controls
    fun onRequestRun() {
        refreshServiceStatus()
        val wf = _workflow.value
        if (wf == null || wf.status != WorkflowStatus.CALIBRATED || wf.steps.isEmpty()) {
            validationError.value = "Workflow is not calibrated. Record coordinates first before executing."
            return
        }

        val currentDevice = _currentDeviceProfile.value
        val recordedDevice = wf.deviceProfile

        if (recordedDevice != null) {
            val check = CoordinateValidator.validateDeviceCompatibility(recordedDevice, currentDevice)
            if (!check.isValid) {
                deviceMismatchWarning.value = (check as ValidationResult.Invalid).reason
                return
            }
        }

        showRunConfirmation.value = true
    }

    fun dismissDeviceMismatchWarning() {
        deviceMismatchWarning.value = null
    }

    fun recalibrateFromMismatch() {
        deviceMismatchWarning.value = null
        onRequestStartRecording()
    }

    fun confirmStartExecution() {
        showRunConfirmation.value = false
        val wf = _workflow.value ?: return
        if (wf.status != WorkflowStatus.CALIBRATED || wf.steps.isEmpty()) {
            validationError.value = "Workflow is not calibrated. Automation aborted."
            return
        }
        viewModelScope.launch {
            val startTime = System.currentTimeMillis()
            automationEngine.executeWorkflow(wf)

            val session = automationEngine.executionSession.value
            val historyRecord = ExecutionHistoryItem(
                id = "run_${System.currentTimeMillis()}",
                workflowId = wf.id,
                workflowName = wf.name,
                timestamp = startTime,
                formattedTime = SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault()).format(Date(startTime)),
                status = when (session.state) {
                    ExecutionState.COMPLETED -> "COMPLETED"
                    ExecutionState.FAILED -> "FAILED"
                    ExecutionState.STOPPED -> "STOPPED"
                    else -> "CANCELLED"
                },
                completedSteps = if (session.state == ExecutionState.COMPLETED) wf.steps.size else session.currentStepIndex,
                totalSteps = wf.steps.size,
                failedStep = if (session.state == ExecutionState.FAILED) session.currentStepIndex + 1 else null,
                failureReason = session.errorMessage
            )
            persistenceRepository.saveHistoryRecord(historyRecord)
            loadHistory(wf.id)
        }
    }

    fun cancelRunConfirmation() {
        showRunConfirmation.value = false
    }

    fun pauseExecution() = automationEngine.pause()
    fun resumeExecution() = automationEngine.resume()

    fun onRequestStop() {
        showStopConfirmation.value = true
    }

    fun confirmStopExecution() {
        showStopConfirmation.value = false
        automationEngine.stop()
    }

    fun cancelStopConfirmation() {
        showStopConfirmation.value = false
    }
}
