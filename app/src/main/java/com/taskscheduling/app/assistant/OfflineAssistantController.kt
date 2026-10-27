package com.taskscheduling.app.assistant

import com.taskscheduling.app.automation.AutomationEngine
import com.taskscheduling.app.data.repository.ExecutionHistoryItem
import com.taskscheduling.app.data.repository.TaskRepository
import com.taskscheduling.app.data.repository.WorkflowPersistenceRepository
import com.taskscheduling.app.domain.model.DeviceProfile
import com.taskscheduling.app.domain.model.ExecutionState
import com.taskscheduling.app.domain.model.Task
import com.taskscheduling.app.domain.model.TaskDraft
import com.taskscheduling.app.domain.model.Workflow
import com.taskscheduling.app.domain.model.WorkflowStatus
import com.taskscheduling.app.recording.CoordinateValidator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Represents an action awaiting explicit user confirmation.
 * Enforces that zero destructive or modifying actions can execute without passing through confirmation.
 */
sealed class PendingAction {
    data class WorkflowAction(val workflow: Workflow) : PendingAction()
    data class DirectDeviceAction(val intent: CommandIntent) : PendingAction()
    data class SaveTaskDraftAction(val draft: TaskDraft) : PendingAction()
    data class CompleteTaskAction(val task: Task) : PendingAction()
    data class DeleteTaskAction(val task: Task) : PendingAction()
    data class RescheduleTaskAction(val task: Task, val newDate: String?, val newTime: String?) : PendingAction()
}

/**
 * Structured debug info for offline voice recognition diagnostics.
 * Captures microphone parameters, audio levels (RMS/Peak), Sherpa/Vosk state, partial/final speech,
 * endpoint detection, pipeline health, and command execution telemetry.
 */
data class VoiceDebugInfo(
    val rawVoskResult: String = "",
    val partialText: String = "",
    val finalText: String = "",
    val normalizedText: String = "",
    val resolvedCommand: String = "",
    val executionPolicy: String = "",
    val engineType: String = "Whisper Small Q5_1",
    val audioSource: String = "DEFAULT",
    val sampleRate: Int = 16000,
    val channelCount: Int = 1,
    val pcmFormat: String = "16-bit PCM",
    val bufferSize: Int = 2048,
    val audioRecordState: String = "INITIALIZED",
    val isListening: Boolean = false,
    val voskState: String = "READY",
    val modelState: String = "READY",
    val recognizerState: String = "READY",
    val streamState: String = "IDLE",
    val rmsLevel: Float = 0f,
    val peakLevel: Float = 0f,
    val inputDetected: Boolean = false,
    val isEndpointDetected: Boolean = false,
    val micPipelineOk: Boolean = false,
    val pcmPipelineOk: Boolean = false,
    val enginePipelineOk: Boolean = false,
    val partialPipelineOk: Boolean = false,
    val textPipelineOk: Boolean = false,
    val commandPipelineOk: Boolean = false,
    val isRawSpeechTestMode: Boolean = false,
    val memoryStatus: String = "OK",
    val errorMessage: String = ""
)

/**
 * Explicit UI states for the offline assistant enforcing the strict confirmation state machine:
 * IDLE -> COMMAND_RECOGNIZED -> WORKFLOW_FOUND -> WAITING_FOR_CONFIRMATION -> USER_CONFIRMED -> RUNNING -> COMPLETED/FAILED/CANCELLED
 */
sealed class AssistantUiState {
    data class Idle(val message: String = "⚪ Not Listening") : AssistantUiState()
    data class Listening(val message: String = "MIC ACTIVE — NO SPEECH DETECTED") : AssistantUiState()
    data class Processing(val message: String = "● Processing...") : AssistantUiState()
    data class CommandRecognized(val command: String) : AssistantUiState()
    data class WorkflowFound(val workflow: Workflow) : AssistantUiState()
    data class ConfirmationRequired(
        val command: String,
        val workflow: Workflow? = null,
        val intent: CommandIntent? = null,
        val task: Task? = null,
        val taskDraft: TaskDraft? = null,
        val stepCount: Int = 0,
        val message: String = when (intent) {
            CommandIntent.CHANGE_WALLPAPER, null -> if (workflow != null) {
                "I found your ${workflow.name} workflow.\nDo you want me to run it?"
            } else {
                "Do you want me to run it?"
            }
            CommandIntent.GREETING -> "Do you want me to respond?"
            CommandIntent.GO_BACK -> "Do you want me to go back?"
            CommandIntent.GO_HOME -> "Do you want me to go to the home screen?"
            CommandIntent.SWIPE_LEFT -> "Do you want me to swipe left?"
            CommandIntent.SWIPE_RIGHT -> "Do you want me to swipe right?"
            CommandIntent.SWIPE_UP -> "Do you want me to swipe up?"
            CommandIntent.SWIPE_DOWN -> "Do you want me to swipe down?"
            CommandIntent.SCROLL_UP -> "Do you want me to scroll up?"
            CommandIntent.SCROLL_DOWN -> "Do you want me to scroll down?"
            CommandIntent.OPEN_YOUTUBE -> "Do you want me to open YouTube?"
            CommandIntent.OPEN_SETTINGS -> "Do you want me to open Settings?"
            CommandIntent.ADD_TASK -> "Do you want to save this task?"
            CommandIntent.COMPLETE_TASK -> "Do you want to mark ${task?.title ?: "this task"} as complete?"
            CommandIntent.DELETE_TASK -> "Do you want to delete ${task?.title ?: "this task"}?"
            CommandIntent.RESCHEDULE_TASK -> "Do you want to reschedule ${task?.title ?: "this task"}?"
            CommandIntent.SHOW_TASKS,
            CommandIntent.SHOW_TODAY_TASKS,
            CommandIntent.SHOW_COMPLETED_TASKS,
            CommandIntent.SHOW_OVERDUE_TASKS,
            CommandIntent.EDIT_TASK,
            CommandIntent.SEARCH_TASKS -> "Do you want to view tasks?"
        }
    ) : AssistantUiState()
    data class UserConfirmed(val workflow: Workflow? = null, val intent: CommandIntent? = null) : AssistantUiState()
    data class Running(
        val workflowName: String,
        val currentStepIndex: Int,
        val totalSteps: Int,
        val actionType: String,
        val coordinateInfo: String
    ) : AssistantUiState()
    data class AmbiguousWorkflows(
        val query: String,
        val candidates: List<Workflow>,
        val message: String
    ) : AssistantUiState()
    data class MultipleTasksFound(
        val candidates: List<Task>,
        val actionType: String,
        val message: String
    ) : AssistantUiState()
    data class TaskDraftReady(
        val draft: TaskDraft,
        val message: String = "Task draft created."
    ) : AssistantUiState()
    data class TaskActionCompleted(
        val message: String
    ) : AssistantUiState()
    data class Completed(
        val workflowName: String,
        val completedSteps: Int,
        val totalSteps: Int,
        val message: String = "Workflow completed."
    ) : AssistantUiState()
    data class Cancelled(val message: String = "Execution cancelled.") : AssistantUiState()
    data class Speaking(val message: String) : AssistantUiState()
    data class Error(val message: String) : AssistantUiState()
}

/**
 * Coordinates offline assistant workflows, task management, intent resolution, confirmation gating,
 * and execution feedback. Enforces zero automatic execution without explicit user confirmation.
 */
class OfflineAssistantController(
    val commandResolver: CommandResolver = LocalCommandResolver(),
    val workflowResolver: WorkflowResolver,
    val commandProcessor: CommandProcessor = LocalCommandProcessor(commandResolver, workflowResolver),
    val confirmationResolver: ConfirmationResolver = LocalConfirmationResolver(),
    val executionPolicyResolver: ExecutionPolicyResolver = DefaultExecutionPolicyResolver,
    private val automationEngine: AutomationEngine,
    val actionExecutor: AssistantActionExecutor = AndroidAssistantActionExecutor(),
    private val persistenceRepository: WorkflowPersistenceRepository? = null,
    val taskRepository: TaskRepository? = null,
    val taskNotificationScheduler: TaskNotificationScheduler? = null,
    private val deviceProfileProvider: () -> DeviceProfile,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Main)
) {
    private val _uiState = MutableStateFlow<AssistantUiState>(AssistantUiState.Idle())
    val uiState: StateFlow<AssistantUiState> = _uiState.asStateFlow()

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    private val _chatMessages = MutableStateFlow<List<AssistantChatMessage>>(
        listOf(AssistantChatMessage(isUser = false, text = "Hello! How can I help you today?"))
    )
    val chatMessages: StateFlow<List<AssistantChatMessage>> = _chatMessages.asStateFlow()

    fun addChatMessage(isUser: Boolean, text: String) {
        if (text.isBlank()) return
        _chatMessages.value = _chatMessages.value + AssistantChatMessage(isUser = isUser, text = text)
    }

    fun clearChatMessages() {
        _chatMessages.value = listOf(
            AssistantChatMessage(isUser = false, text = "Conversation cleared. How can I help you?")
        )
    }

    // Navigation callbacks for UI coordination
    var onOpenAddTaskRequested: ((TaskDraft) -> Unit)? = null
    var onOpenTasksRequested: ((filterTab: String) -> Unit)? = null
    var onOpenEditTaskRequested: ((taskId: String) -> Unit)? = null

    // Continuous listening lifecycle controls
    var isContinuousListening: Boolean = false
    var autoResumeDelayMs: Long = 1200L
    var onStopListeningRequested: (() -> Unit)? = null
    var onContinuousListeningResumed: (() -> Unit)? = null

    // Safety guards enforcing the hard execution boundary
    var pendingAction: PendingAction? = null
        private set

    val pendingWorkflow: Workflow?
        get() = (pendingAction as? PendingAction.WorkflowAction)?.workflow

    val pendingDirectAction: CommandIntent?
        get() = (pendingAction as? PendingAction.DirectDeviceAction)?.intent

    val pendingTask: Task?
        get() = when (val action = pendingAction) {
            is PendingAction.CompleteTaskAction -> action.task
            is PendingAction.DeleteTaskAction -> action.task
            is PendingAction.RescheduleTaskAction -> action.task
            else -> null
        }

    val pendingTaskDraft: TaskDraft?
        get() = (pendingAction as? PendingAction.SaveTaskDraftAction)?.draft

    var confirmationGranted: Boolean = false
        private set

    private var executionJob: Job? = null

    /**
     * Formats confirmation prompt question for the given intent or workflow.
     */
    fun formatConfirmationMessage(intent: CommandIntent?, workflow: Workflow? = null, task: Task? = null): String {
        if (workflow != null) {
            return "I found your ${workflow.name} workflow.\nDo you want me to run it?"
        }
        return when (intent) {
            CommandIntent.CHANGE_WALLPAPER -> "I found your ${workflow?.name ?: "Change Wallpaper"} workflow.\nDo you want me to run it?"
            CommandIntent.GREETING -> "Do you want me to respond?"
            CommandIntent.GO_BACK -> "Do you want me to go back?"
            CommandIntent.GO_HOME -> "Do you want me to go to the home screen?"
            CommandIntent.SWIPE_LEFT -> "Do you want me to swipe left?"
            CommandIntent.SWIPE_RIGHT -> "Do you want me to swipe right?"
            CommandIntent.SWIPE_UP -> "Do you want me to swipe up?"
            CommandIntent.SWIPE_DOWN -> "Do you want me to swipe down?"
            CommandIntent.SCROLL_UP -> "Do you want me to scroll up?"
            CommandIntent.SCROLL_DOWN -> "Do you want me to scroll down?"
            CommandIntent.OPEN_YOUTUBE -> "Do you want me to open YouTube?"
            CommandIntent.OPEN_SETTINGS -> "Do you want me to open Settings?"
            CommandIntent.ADD_TASK -> "Do you want to save this task?"
            CommandIntent.COMPLETE_TASK -> "Do you want to mark ${task?.title ?: "this task"} as complete?"
            CommandIntent.DELETE_TASK -> "Do you want to delete ${task?.title ?: "this task"}?"
            CommandIntent.RESCHEDULE_TASK -> "Do you want to reschedule ${task?.title ?: "this task"}?"
            CommandIntent.SHOW_TASKS,
            CommandIntent.SHOW_TODAY_TASKS,
            CommandIntent.SHOW_COMPLETED_TASKS,
            CommandIntent.SHOW_OVERDUE_TASKS,
            CommandIntent.EDIT_TASK,
            CommandIntent.SEARCH_TASKS -> "Do you want to view tasks?"
            null -> "Do you want me to run this workflow?"
        }
    }

    private val _voiceDebugInfo = MutableStateFlow(VoiceDebugInfo())
    val voiceDebugInfo: StateFlow<VoiceDebugInfo> = _voiceDebugInfo.asStateFlow()

    fun updateDebugPartial(partial: String) {
        val isNonBlank = partial.isNotBlank()
        _voiceDebugInfo.value = _voiceDebugInfo.value.copy(
            partialText = partial,
            partialPipelineOk = isNonBlank || _voiceDebugInfo.value.partialPipelineOk
        )
        AssistantLogger.d("Assistant", "[SHERPA_PARTIAL] \"$partial\"")
    }

    fun updateDebugFinal(finalText: String) {
        val isNonBlank = finalText.isNotBlank()
        _voiceDebugInfo.value = _voiceDebugInfo.value.copy(
            finalText = finalText,
            textPipelineOk = isNonBlank || _voiceDebugInfo.value.textPipelineOk
        )
        AssistantLogger.d("Assistant", "[SHERPA_FINAL] \"$finalText\"")
    }

    fun updateDebugInfo(
        rawVosk: String? = null,
        finalText: String,
        normalizedText: String,
        resolvedCommand: String,
        executionPolicy: String
    ) {
        val raw = rawVosk ?: "{\"text\":\"$finalText\"}"
        _voiceDebugInfo.value = _voiceDebugInfo.value.copy(
            rawVoskResult = raw,
            finalText = finalText,
            normalizedText = normalizedText,
            resolvedCommand = resolvedCommand,
            executionPolicy = executionPolicy,
            textPipelineOk = finalText.isNotBlank() || _voiceDebugInfo.value.textPipelineOk,
            commandPipelineOk = resolvedCommand.isNotBlank() || _voiceDebugInfo.value.commandPipelineOk
        )
        AssistantLogger.d("Assistant", """
            [DEBUG_INFO]
            RAW: $raw
            FINAL: $finalText
            NORMALIZED: $normalizedText
            COMMAND: $resolvedCommand
            POLICY: $executionPolicy
        """.trimIndent())
    }

    fun updateAudioLevel(rmsLevel: Float, peakLevel: Float, inputDetected: Boolean) {
        val hasSpeech = rmsLevel >= 0.005f || inputDetected
        _voiceDebugInfo.value = _voiceDebugInfo.value.copy(
            rmsLevel = rmsLevel,
            peakLevel = peakLevel,
            inputDetected = hasSpeech,
            pcmPipelineOk = hasSpeech || _voiceDebugInfo.value.pcmPipelineOk,
            micPipelineOk = hasSpeech || _voiceDebugInfo.value.micPipelineOk
        )
        if (_uiState.value is AssistantUiState.Listening) {
            val targetMsg = if (hasSpeech) "SPEECH DETECTED" else "MIC ACTIVE — NO SPEECH DETECTED"
            if ((_uiState.value as AssistantUiState.Listening).message != targetMsg) {
                _uiState.value = AssistantUiState.Listening(targetMsg)
            }
        }
    }

    fun updateEndpointStatus(isEndpoint: Boolean) {
        _voiceDebugInfo.value = _voiceDebugInfo.value.copy(isEndpointDetected = isEndpoint)
    }

    fun setRawSpeechTestMode(enabled: Boolean) {
        _voiceDebugInfo.value = _voiceDebugInfo.value.copy(isRawSpeechTestMode = enabled)
    }

    fun updateAudioDiagnostics(
        engineType: String = _voiceDebugInfo.value.engineType,
        audioSource: String = _voiceDebugInfo.value.audioSource,
        sampleRate: Int = _voiceDebugInfo.value.sampleRate,
        channelCount: Int = _voiceDebugInfo.value.channelCount,
        bufferSize: Int = _voiceDebugInfo.value.bufferSize,
        audioRecordState: String = _voiceDebugInfo.value.audioRecordState,
        isListening: Boolean = _voiceDebugInfo.value.isListening,
        voskState: String = _voiceDebugInfo.value.voskState,
        modelState: String = _voiceDebugInfo.value.modelState,
        recognizerState: String = _voiceDebugInfo.value.recognizerState,
        streamState: String = _voiceDebugInfo.value.streamState,
        micPipelineOk: Boolean = _voiceDebugInfo.value.micPipelineOk,
        pcmPipelineOk: Boolean = _voiceDebugInfo.value.pcmPipelineOk,
        enginePipelineOk: Boolean = _voiceDebugInfo.value.enginePipelineOk,
        partialPipelineOk: Boolean = _voiceDebugInfo.value.partialPipelineOk,
        textPipelineOk: Boolean = _voiceDebugInfo.value.textPipelineOk,
        memoryStatus: String = _voiceDebugInfo.value.memoryStatus,
        errorMessage: String = _voiceDebugInfo.value.errorMessage
    ) {
        _voiceDebugInfo.value = _voiceDebugInfo.value.copy(
            engineType = engineType,
            audioSource = audioSource,
            sampleRate = sampleRate,
            channelCount = channelCount,
            bufferSize = bufferSize,
            audioRecordState = audioRecordState,
            isListening = isListening,
            voskState = voskState,
            modelState = modelState,
            recognizerState = recognizerState,
            streamState = streamState,
            micPipelineOk = micPipelineOk,
            pcmPipelineOk = pcmPipelineOk,
            enginePipelineOk = enginePipelineOk,
            partialPipelineOk = partialPipelineOk,
            textPipelineOk = textPipelineOk,
            memoryStatus = memoryStatus,
            errorMessage = errorMessage
        )
    }

    fun setUiState(newState: AssistantUiState) {
        _uiState.value = newState
        AssistantLogger.i("Assistant", "[VOICE_STATE] $newState")
        when (newState) {
            is AssistantUiState.ConfirmationRequired -> addChatMessage(isUser = false, text = newState.message)
            is AssistantUiState.Completed -> addChatMessage(isUser = false, text = "${newState.workflowName}: ${newState.message}")
            is AssistantUiState.TaskActionCompleted -> addChatMessage(isUser = false, text = newState.message)
            is AssistantUiState.Cancelled -> {
                val lastMsg = _chatMessages.value.lastOrNull()
                if (lastMsg == null || lastMsg.text != newState.message) {
                    addChatMessage(isUser = false, text = newState.message)
                }
            }
            is AssistantUiState.Error -> addChatMessage(isUser = false, text = newState.message)
            is AssistantUiState.Speaking -> addChatMessage(isUser = false, text = newState.message)
            else -> {}
        }
    }

    /**
     * Processes a user command (from typed text or embedded offline voice recognition).
     * If currently waiting for confirmation, checks if the input is a confirmation response (yes/no).
     * Otherwise resolves intent, executes immediate actions or asks for confirmation.
     */
    fun processCommand(command: String, rawVoskJson: String? = null) {
        val trimmed = command.trim()
        if (trimmed.isBlank()) return

        val normalized = LocalCommandResolver.normalize(trimmed)
        _userMessage.value = trimmed
        addChatMessage(isUser = true, text = trimmed)
        AssistantLogger.i("Assistant", "[VOICE_TEXT] \"$trimmed\"")
        AssistantLogger.i("Assistant", "[COMMAND_RESOLVER] raw=\"$trimmed\", normalized=\"$normalized\"")

        // Reject non-English or unreliable transcripts
        if (com.taskscheduling.app.voice.EnglishSpeechValidator.isNonEnglishOrUnreliable(trimmed)) {
            AssistantLogger.w("Assistant", "[ENGLISH_ENFORCEMENT] Rejected non-English or unreliable transcript: \"$trimmed\"")
            val rejectionMsg = com.taskscheduling.app.voice.EnglishSpeechValidator.REJECTION_MESSAGE
            addChatMessage(isUser = false, text = rejectionMsg)
            setUiState(AssistantUiState.Error(rejectionMsg))
            return
        }

        // 1. If currently waiting for confirmation, evaluate confirmation or cancellation
        val isAwaitingConfirmation = pendingAction != null

        if (isAwaitingConfirmation) {
            // Explicit stop-listening phrase always cancels and terminates listening
            if (StopListeningResolver.isExplicitStopListening(trimmed)) {
                AssistantLogger.i("Assistant", "[COMMAND_INTENT] STOP_LISTENING_DURING_CONFIRMATION: \"$trimmed\"")
                updateDebugInfo(rawVoskJson, trimmed, normalized, "STOP_LISTENING", "TERMINATE")
                pendingAction = null
                confirmationGranted = false
                executionJob?.cancel()
                automationEngine.stop()
                isContinuousListening = false
                setUiState(AssistantUiState.Idle("⚪ Not Listening"))
                onStopListeningRequested?.invoke()
                return
            }

            val confirmIntent = confirmationResolver.resolve(trimmed)
            when (confirmIntent) {
                ConfirmationIntent.YES -> {
                    AssistantLogger.i("Assistant", "[COMMAND_INTENT] CONFIRMATION: YES")
                    updateDebugInfo(rawVoskJson, trimmed, normalized, "CONFIRM_YES", "EXECUTE")
                    confirmPendingAction()
                    return
                }
                ConfirmationIntent.NO -> {
                    AssistantLogger.i("Assistant", "[COMMAND_INTENT] CONFIRMATION: NO")
                    updateDebugInfo(rawVoskJson, trimmed, normalized, "CONFIRM_NO", "CANCEL")
                    cancelPendingAction()
                    return
                }
                ConfirmationIntent.UNKNOWN -> {
                    AssistantLogger.i("Assistant", "[COMMAND_INTENT] CONFIRMATION: UNKNOWN (\"$trimmed\")")
                    updateDebugInfo(rawVoskJson, trimmed, normalized, "CONFIRM_UNKNOWN", "PROMPT")
                    setUiState(AssistantUiState.ConfirmationRequired(
                        command = trimmed,
                        workflow = pendingWorkflow,
                        intent = pendingDirectAction,
                        task = pendingTask,
                        taskDraft = pendingTaskDraft,
                        stepCount = pendingWorkflow?.steps?.size ?: 0,
                        message = "I didn't understand. Please say yes or no."
                    ))
                    return
                }
            }
        }

        // 2. Global check for "Stop listening" when NOT in confirmation
        if (StopListeningResolver.isStopListening(trimmed)) {
            AssistantLogger.i("Assistant", "[COMMAND_INTENT] STOP_LISTENING_GLOBAL: \"$trimmed\"")
            updateDebugInfo(rawVoskJson, trimmed, normalized, "STOP_LISTENING", "TERMINATE")
            pendingAction = null
            confirmationGranted = false
            executionJob?.cancel()
            automationEngine.stop()
            isContinuousListening = false
            setUiState(AssistantUiState.Idle("⚪ Not Listening"))
            onStopListeningRequested?.invoke()
            return
        }

        // 2.5 Conversational follow-up when currently editing or awaiting task details
        if (_uiState.value is AssistantUiState.TaskDraftReady) {
            val currentDraft = (_uiState.value as AssistantUiState.TaskDraftReady).draft
            val fullParse = commandProcessor.process(trimmed)
            if (fullParse is CommandResolutionResult.TaskAction && fullParse.parseResult is TaskParseResult.AddTask) {
                handleTaskAction(fullParse.parseResult, trimmed, rawVoskJson, normalized)
                return
            }

            // Extract details from conversational speech
            val parsedDetails = TaskCommandParser.extractTaskDraft("add task $trimmed")
            if (parsedDetails.title.isNotBlank() || parsedDetails.dueDate != null || parsedDetails.dueTime != null) {
                val mergedTitle = if (parsedDetails.title.isNotBlank()) {
                    if (currentDraft.title.isBlank()) parsedDetails.title else parsedDetails.title
                } else currentDraft.title

                val mergedDate = parsedDetails.dueDate ?: currentDraft.dueDate
                val mergedTime = parsedDetails.dueTime ?: currentDraft.dueTime
                val mergedHasDueTime = parsedDetails.hasDueTime || currentDraft.hasDueTime
                val mergedPriority = if (parsedDetails.priority != com.taskscheduling.app.domain.model.TaskPriority.MEDIUM) parsedDetails.priority else currentDraft.priority
                val mergedCategory = if (parsedDetails.category != com.taskscheduling.app.domain.model.TaskCategory.CUSTOM) parsedDetails.category else currentDraft.category
                val mergedRecurrence = if (parsedDetails.recurrence != com.taskscheduling.app.domain.model.TaskRecurrence.NONE) parsedDetails.recurrence else currentDraft.recurrence
                val mergedOffset = parsedDetails.reminderOffsetMinutes ?: currentDraft.reminderOffsetMinutes
                val mergedHasReminder = parsedDetails.hasReminder || currentDraft.hasReminder

                val updatedDraft = currentDraft.copy(
                    title = mergedTitle,
                    dueDate = mergedDate,
                    dueTime = mergedTime,
                    hasDueTime = mergedHasDueTime,
                    priority = mergedPriority,
                    category = mergedCategory,
                    recurrence = mergedRecurrence,
                    reminderOffsetMinutes = mergedOffset,
                    hasReminder = mergedHasReminder
                )

                onOpenAddTaskRequested?.invoke(updatedDraft)

                if (updatedDraft.title.isNotBlank()) {
                    pendingAction = PendingAction.SaveTaskDraftAction(updatedDraft)
                    confirmationGranted = false
                    val schedStr = if (updatedDraft.dueDate != null) {
                        if (updatedDraft.dueTime != null) " for ${updatedDraft.dueDate} at ${updatedDraft.dueTime}" else " for ${updatedDraft.dueDate}"
                    } else ""
                    setUiState(AssistantUiState.ConfirmationRequired(
                        command = trimmed,
                        intent = CommandIntent.ADD_TASK,
                        taskDraft = updatedDraft,
                        message = "Add '${updatedDraft.title}'$schedStr?"
                    ))
                } else {
                    setUiState(AssistantUiState.TaskDraftReady(updatedDraft, "Sure. What task should I add?"))
                }
                return
            }
        }

        // 3. New command resolution path
        setUiState(AssistantUiState.CommandRecognized(trimmed))
        setUiState(AssistantUiState.Processing())

        val resolution = commandProcessor.process(trimmed)

        try {
            val service = com.taskscheduling.app.accessibility.TaskSchedulingAccessibilityService.instance
            if (service != null) {
                val tracker = com.taskscheduling.app.feature.diagnostics.VoiceBotDiagnosticTracker.getInstance(service)
                val resDesc = when (resolution) {
                    is CommandResolutionResult.TaskAction -> "TASK_ACTION (${resolution.parseResult::class.simpleName})"
                    is CommandResolutionResult.UnknownCommand -> "UNKNOWN_COMMAND"
                    is CommandResolutionResult.WorkflowNotFound -> "WORKFLOW_NOT_FOUND"
                    is CommandResolutionResult.DirectAction -> "DIRECT_ACTION (${resolution.intent})"
                    is CommandResolutionResult.Success -> "WORKFLOW_SUCCESS (${resolution.workflow.name})"
                    is CommandResolutionResult.AmbiguousWorkflows -> "AMBIGUOUS_WORKFLOWS"
                }
                tracker.recordCommandResolution(
                    rawText = trimmed,
                    normalizedText = normalized,
                    language = "DEFAULT",
                    resultIntent = resDesc,
                    isConfirmationRequired = resolution is CommandResolutionResult.Success
                )
                val isMatched = resolution !is CommandResolutionResult.UnknownCommand &&
                                resolution !is CommandResolutionResult.WorkflowNotFound
                tracker.recordBotPipelineStage("Command Matched", if (isMatched) "SUCCESS" else "FAILED", resDesc)
            }
        } catch (_: Throwable) {}

        when (resolution) {
            is CommandResolutionResult.TaskAction -> {
                handleTaskAction(resolution.parseResult, trimmed, rawVoskJson, normalized)
            }
            is CommandResolutionResult.UnknownCommand -> {
                AssistantLogger.w("Assistant", "[COMMAND_INTENT] UNKNOWN for \"$trimmed\"")
                updateDebugInfo(rawVoskJson, trimmed, normalized, "UNKNOWN", "NONE")
                pendingAction = null
                confirmationGranted = false
                val isGreeting = LocalCommandResolver.isGreeting(trimmed)
                val msg = if (isGreeting) {
                    "Hello! How can I help you today?"
                } else if (trimmed.contains("tasks", ignoreCase = true) || trimmed.contains("task", ignoreCase = true)) {
                    "What would you like me to do with your tasks?"
                } else {
                    "I don't know how to do that yet."
                }
                if (isGreeting) {
                    setUiState(AssistantUiState.Speaking(msg))
                } else {
                    setUiState(AssistantUiState.Error(msg))
                }
                if (isContinuousListening) {
                    coroutineScope.launch {
                        if (autoResumeDelayMs > 0L) kotlinx.coroutines.delay(autoResumeDelayMs)
                        if (isContinuousListening && (_uiState.value is AssistantUiState.Error || _uiState.value is AssistantUiState.Speaking)) {
                            setUiState(AssistantUiState.Listening("MIC ACTIVE — NO SPEECH DETECTED"))
                            onContinuousListeningResumed?.invoke()
                        }
                    }
                }
            }
            is CommandResolutionResult.WorkflowNotFound -> {
                val query = resolution.workflowName ?: trimmed
                AssistantLogger.w("Assistant", "[COMMAND_INTENT] WORKFLOW_NOT_FOUND: \"$query\" for \"$trimmed\"")
                updateDebugInfo(rawVoskJson, trimmed, normalized, "WORKFLOW_NOT_FOUND: $query", "NONE")
                pendingAction = null
                val errorMessage = if (resolution.intent != null) {
                    "I couldn't find a saved workflow for:\n\"$trimmed\""
                } else {
                    "I couldn't find a workflow called $query."
                }
                setUiState(AssistantUiState.Error(errorMessage))
                if (isContinuousListening) {
                    coroutineScope.launch {
                        if (autoResumeDelayMs > 0L) kotlinx.coroutines.delay(autoResumeDelayMs)
                        if (isContinuousListening && _uiState.value is AssistantUiState.Error) {
                            setUiState(AssistantUiState.Listening("MIC ACTIVE — NO SPEECH DETECTED"))
                            onContinuousListeningResumed?.invoke()
                        }
                    }
                }
            }
            is CommandResolutionResult.AmbiguousWorkflows -> {
                AssistantLogger.w("Assistant", "[COMMAND_INTENT] AMBIGUOUS_WORKFLOWS: \"${resolution.queryName}\" (${resolution.candidates.size} candidates)")
                updateDebugInfo(rawVoskJson, trimmed, normalized, "AMBIGUOUS: ${resolution.queryName}", "NONE")
                pendingAction = null
                confirmationGranted = false
                val candidateNames = resolution.candidates.joinToString("\n• ", prefix = "• ") { it.name }
                setUiState(AssistantUiState.AmbiguousWorkflows(
                    query = resolution.queryName,
                    candidates = resolution.candidates,
                    message = "I found multiple matching workflows. Which one do you mean?\n$candidateNames"
                ))
            }
            is CommandResolutionResult.DirectAction -> {
                val intent = resolution.intent
                val policy = executionPolicyResolver.getExecutionPolicy(intent)
                AssistantLogger.i("Assistant", "[COMMAND_INTENT] $intent")
                AssistantLogger.i("Assistant", "[EXECUTION_POLICY] $policy for $intent")
                updateDebugInfo(rawVoskJson, trimmed, normalized, intent.name, policy.name)

                if (intent == CommandIntent.GREETING) {
                    pendingAction = null
                    confirmationGranted = false
                    val greetingResponse = "Hello! How can I help you?"
                    addChatMessage(isUser = false, text = greetingResponse)
                    setUiState(AssistantUiState.Listening(greetingResponse))
                    return
                }

                if (policy == ExecutionPolicy.IMMEDIATE) {
                    pendingAction = null
                    confirmationGranted = false
                    executeDirectActionImmediately(intent)
                } else {
                    pendingAction = PendingAction.DirectDeviceAction(intent)
                    confirmationGranted = false
                    setUiState(AssistantUiState.ConfirmationRequired(
                        command = trimmed,
                        workflow = null,
                        intent = intent,
                        stepCount = 0,
                        message = formatConfirmationMessage(intent)
                    ))
                }
            }
            is CommandResolutionResult.Success -> {
                val workflow = resolution.workflow
                val intentDesc = resolution.intent?.displayName ?: "Custom Workflow"
                val policy = executionPolicyResolver.getWorkflowExecutionPolicy(workflow)
                AssistantLogger.i("Assistant", "[COMMAND_INTENT] $intentDesc -> ${workflow.name}")
                AssistantLogger.i("Assistant", "[EXECUTION_POLICY] $policy for \"${workflow.name}\"")
                updateDebugInfo(rawVoskJson, trimmed, normalized, "WORKFLOW: ${workflow.name}", policy.name)

                if (workflow.steps.isEmpty() || workflow.status != WorkflowStatus.CALIBRATED) {
                    pendingAction = null
                    confirmationGranted = false
                    val uncalibratedMsg = "The ${workflow.name} workflow is not calibrated yet. Please record or calibrate it before running."
                    setUiState(AssistantUiState.Error(uncalibratedMsg))
                    if (isContinuousListening) {
                        coroutineScope.launch {
                            if (autoResumeDelayMs > 0L) kotlinx.coroutines.delay(autoResumeDelayMs)
                            if (isContinuousListening && _uiState.value is AssistantUiState.Error) {
                                setUiState(AssistantUiState.Listening("MIC ACTIVE — NO SPEECH DETECTED"))
                                onContinuousListeningResumed?.invoke()
                            }
                        }
                    }
                    return
                }

                val currentDevice = deviceProfileProvider()
                val recordedDevice = workflow.deviceProfile
                if (recordedDevice != null) {
                    val compat = CoordinateValidator.validateDeviceCompatibility(recordedDevice, currentDevice)
                    if (!compat.isValid) {
                        pendingAction = null
                        confirmationGranted = false
                        setUiState(AssistantUiState.Error(
                            "This workflow was recorded for another device configuration.\nPlease recalibrate the workflow."
                        ))
                        return
                    }
                }

                if (policy == ExecutionPolicy.IMMEDIATE) {
                    pendingAction = null
                    confirmationGranted = false
                    setUiState(AssistantUiState.Running(
                        workflowName = workflow.name,
                        currentStepIndex = 1,
                        totalSteps = workflow.steps.size,
                        actionType = workflow.steps.firstOrNull()?.type?.label ?: "TAP",
                        coordinateInfo = ""
                    ))
                    executeWorkflowInternal(workflow)
                } else {
                    pendingAction = PendingAction.WorkflowAction(workflow)
                    confirmationGranted = false
                    setUiState(AssistantUiState.ConfirmationRequired(
                        command = trimmed,
                        workflow = workflow,
                        intent = resolution.intent,
                        stepCount = workflow.steps.size,
                        message = formatConfirmationMessage(resolution.intent, workflow)
                    ))
                }
            }
        }
    }

    private fun handleTaskAction(
        taskResult: TaskParseResult,
        rawCommand: String,
        rawVoskJson: String?,
        normalized: String
    ) {
        when (taskResult) {
            is TaskParseResult.AddTask -> {
                updateDebugInfo(rawVoskJson, rawCommand, normalized, "ADD_TASK", "IMMEDIATE_OR_DRAFT")
                onOpenAddTaskRequested?.invoke(taskResult.draft)
                if (taskResult.draft.title.isNotBlank()) {
                    pendingAction = PendingAction.SaveTaskDraftAction(taskResult.draft)
                    confirmationGranted = false
                    setUiState(AssistantUiState.ConfirmationRequired(
                        command = rawCommand,
                        intent = CommandIntent.ADD_TASK,
                        taskDraft = taskResult.draft,
                        message = "Task draft created: \"${taskResult.draft.title}\".\nDo you want to save it?"
                    ))
                } else {
                    pendingAction = null
                    confirmationGranted = false
                    setUiState(AssistantUiState.TaskDraftReady(taskResult.draft, "Sure. What task should I add?"))
                }
            }
            is TaskParseResult.ShowTasks -> {
                updateDebugInfo(rawVoskJson, rawCommand, normalized, "SHOW_TASKS", "IMMEDIATE")
                pendingAction = null
                onOpenTasksRequested?.invoke("ALL")
                setUiState(AssistantUiState.TaskActionCompleted("Opening tasks."))
            }
            is TaskParseResult.ShowTodayTasks -> {
                updateDebugInfo(rawVoskJson, rawCommand, normalized, "SHOW_TODAY_TASKS", "IMMEDIATE")
                pendingAction = null
                onOpenTasksRequested?.invoke("TODAY")
                setUiState(AssistantUiState.TaskActionCompleted("Opening today's tasks."))
            }
            is TaskParseResult.ShowCompletedTasks -> {
                updateDebugInfo(rawVoskJson, rawCommand, normalized, "SHOW_COMPLETED_TASKS", "IMMEDIATE")
                pendingAction = null
                onOpenTasksRequested?.invoke("COMPLETED")
                setUiState(AssistantUiState.TaskActionCompleted("Opening completed tasks."))
            }
            is TaskParseResult.ShowOverdueTasks -> {
                updateDebugInfo(rawVoskJson, rawCommand, normalized, "SHOW_OVERDUE_TASKS", "IMMEDIATE")
                pendingAction = null
                onOpenTasksRequested?.invoke("OVERDUE")
                setUiState(AssistantUiState.TaskActionCompleted("Opening overdue tasks."))
            }
            is TaskParseResult.CompleteTask -> {
                val matches = taskRepository?.findTasksByQuery(taskResult.targetQuery) ?: emptyList()
                when {
                    matches.isEmpty() -> {
                        updateDebugInfo(rawVoskJson, rawCommand, normalized, "COMPLETE_TASK_NOT_FOUND", "NONE")
                        pendingAction = null
                        setUiState(AssistantUiState.Error("I couldn't find a task called \"${taskResult.targetQuery}\"."))
                    }
                    matches.size == 1 -> {
                        val task = matches.first()
                        updateDebugInfo(rawVoskJson, rawCommand, normalized, "COMPLETE_TASK: ${task.title}", "CONFIRMATION_REQUIRED")
                        pendingAction = PendingAction.CompleteTaskAction(task)
                        confirmationGranted = false
                        setUiState(AssistantUiState.ConfirmationRequired(
                            command = rawCommand,
                            intent = CommandIntent.COMPLETE_TASK,
                            task = task,
                            message = "Do you want to mark \"${task.title}\" as complete?"
                        ))
                    }
                    else -> {
                        updateDebugInfo(rawVoskJson, rawCommand, normalized, "COMPLETE_TASK_MULTIPLE", "PROMPT")
                        pendingAction = null
                        val names = matches.joinToString("\n• ", prefix = "• ") { "${it.title} (${it.dueDate ?: "No date"})" }
                        setUiState(AssistantUiState.MultipleTasksFound(
                            candidates = matches,
                            actionType = "complete",
                            message = "I found multiple matching tasks. Which one do you want to complete?\n$names"
                        ))
                    }
                }
            }
            is TaskParseResult.DeleteTask -> {
                val matches = taskRepository?.findTasksByQuery(taskResult.targetQuery) ?: emptyList()
                when {
                    matches.isEmpty() -> {
                        updateDebugInfo(rawVoskJson, rawCommand, normalized, "DELETE_TASK_NOT_FOUND", "NONE")
                        pendingAction = null
                        setUiState(AssistantUiState.Error("I couldn't find a task called \"${taskResult.targetQuery}\"."))
                    }
                    matches.size == 1 -> {
                        val task = matches.first()
                        updateDebugInfo(rawVoskJson, rawCommand, normalized, "DELETE_TASK: ${task.title}", "CONFIRMATION_REQUIRED")
                        pendingAction = PendingAction.DeleteTaskAction(task)
                        confirmationGranted = false
                        setUiState(AssistantUiState.ConfirmationRequired(
                            command = rawCommand,
                            intent = CommandIntent.DELETE_TASK,
                            task = task,
                            message = "Delete \"${task.title}\"?"
                        ))
                    }
                    else -> {
                        updateDebugInfo(rawVoskJson, rawCommand, normalized, "DELETE_TASK_MULTIPLE", "PROMPT")
                        pendingAction = null
                        val names = matches.joinToString("\n• ", prefix = "• ") { "${it.title} (${it.dueDate ?: "No date"})" }
                        setUiState(AssistantUiState.MultipleTasksFound(
                            candidates = matches,
                            actionType = "delete",
                            message = "I found multiple matching tasks. Which one do you want to delete?\n$names"
                        ))
                    }
                }
            }
            is TaskParseResult.EditTask -> {
                val matches = taskRepository?.findTasksByQuery(taskResult.targetQuery) ?: emptyList()
                if (matches.isNotEmpty()) {
                    val task = matches.first()
                    updateDebugInfo(rawVoskJson, rawCommand, normalized, "EDIT_TASK: ${task.title}", "IMMEDIATE")
                    pendingAction = null
                    onOpenEditTaskRequested?.invoke(task.id)
                    setUiState(AssistantUiState.TaskActionCompleted("Opening Edit screen for \"${task.title}\"."))
                } else {
                    updateDebugInfo(rawVoskJson, rawCommand, normalized, "EDIT_TASK_NOT_FOUND", "NONE")
                    pendingAction = null
                    setUiState(AssistantUiState.Error("I couldn't find a task called \"${taskResult.targetQuery}\"."))
                }
            }
            is TaskParseResult.RescheduleTask -> {
                val matches = taskRepository?.findTasksByQuery(taskResult.targetQuery) ?: emptyList()
                if (matches.size == 1) {
                    val task = matches.first()
                    updateDebugInfo(rawVoskJson, rawCommand, normalized, "RESCHEDULE_TASK: ${task.title}", "CONFIRMATION_REQUIRED")
                    pendingAction = PendingAction.RescheduleTaskAction(task, taskResult.newDate, taskResult.newTime)
                    confirmationGranted = false
                    setUiState(AssistantUiState.ConfirmationRequired(
                        command = rawCommand,
                        intent = CommandIntent.RESCHEDULE_TASK,
                        task = task,
                        message = "Reschedule \"${task.title}\" to ${taskResult.newDate ?: "new date"}?"
                    ))
                } else if (matches.isEmpty()) {
                    setUiState(AssistantUiState.Error("I couldn't find a task called \"${taskResult.targetQuery}\"."))
                } else {
                    val names = matches.joinToString("\n• ", prefix = "• ") { "${it.title} (${it.dueDate ?: "No date"})" }
                    setUiState(AssistantUiState.MultipleTasksFound(
                        candidates = matches,
                        actionType = "reschedule",
                        message = "I found multiple matching tasks. Which one do you want to reschedule?\n$names"
                    ))
                }
            }
            is TaskParseResult.SearchTasks -> {
                updateDebugInfo(rawVoskJson, rawCommand, normalized, "SEARCH_TASKS: ${taskResult.searchQuery}", "IMMEDIATE")
                pendingAction = null
                onOpenTasksRequested?.invoke(taskResult.searchQuery)
                setUiState(AssistantUiState.TaskActionCompleted("Searching tasks for \"${taskResult.searchQuery}\"."))
            }
            is TaskParseResult.UnknownTaskCommand,
            is TaskParseResult.NotATaskCommand -> {
                setUiState(AssistantUiState.Error("What would you like me to do with your tasks?"))
            }
        }
    }

    /**
     * Confirms the active pending action.
     */
    fun confirmRun() = confirmPendingAction()

    fun confirmPendingWorkflow() = confirmPendingAction()

    var lastActionJob: Job? = null
        private set

    fun confirmPendingAction(): Job? {
        val action = pendingAction ?: return null
        confirmationGranted = true
        val job = when (action) {
            is PendingAction.WorkflowAction -> {
                setUiState(AssistantUiState.UserConfirmed(workflow = action.workflow))
                executeConfirmedWorkflow(action.workflow)
                null
            }
            is PendingAction.DirectDeviceAction -> {
                setUiState(AssistantUiState.UserConfirmed(intent = action.intent))
                executeConfirmedDirectAction(action.intent)
                null
            }
            is PendingAction.SaveTaskDraftAction -> {
                val finalTask = action.draft.toTask()
                pendingAction = null
                confirmationGranted = false
                coroutineScope.launch {
                    taskRepository?.saveTask(finalTask)
                    taskNotificationScheduler?.scheduleReminder(finalTask)
                    setUiState(AssistantUiState.TaskActionCompleted("Task \"${finalTask.title}\" saved."))
                }
            }
            is PendingAction.CompleteTaskAction -> {
                val targetTask = action.task
                pendingAction = null
                confirmationGranted = false
                coroutineScope.launch {
                    taskRepository?.toggleCompleted(targetTask.id)
                    taskNotificationScheduler?.cancelReminder(targetTask.id)
                    setUiState(AssistantUiState.TaskActionCompleted("Marked \"${targetTask.title}\" as complete."))
                }
            }
            is PendingAction.DeleteTaskAction -> {
                val targetTask = action.task
                pendingAction = null
                confirmationGranted = false
                coroutineScope.launch {
                    taskRepository?.deleteTask(targetTask.id)
                    taskNotificationScheduler?.cancelReminder(targetTask.id)
                    setUiState(AssistantUiState.TaskActionCompleted("Deleted \"${targetTask.title}\"."))
                }
            }
            is PendingAction.RescheduleTaskAction -> {
                val targetTask = action.task
                pendingAction = null
                confirmationGranted = false
                coroutineScope.launch {
                    val updated = taskRepository?.rescheduleTask(targetTask.id, action.newDate, action.newTime)
                    if (updated != null) {
                        taskNotificationScheduler?.scheduleReminder(updated)
                    }
                    setUiState(AssistantUiState.TaskActionCompleted("Rescheduled \"${targetTask.title}\" to ${action.newDate ?: "new date"}."))
                }
            }
        }
        lastActionJob = job
        return job
    }

    fun executeConfirmedWorkflow(workflow: Workflow) {
        if (!confirmationGranted || (pendingAction as? PendingAction.WorkflowAction)?.workflow != workflow) {
            _uiState.value = AssistantUiState.Error("Execution rejected: explicit confirmation is required before running automation.")
            return
        }

        val confirmedWorkflow = (pendingAction as PendingAction.WorkflowAction).workflow
        pendingAction = null
        confirmationGranted = false

        executeWorkflowInternal(confirmedWorkflow)
    }

    private fun executeWorkflowInternal(confirmedWorkflow: Workflow) {
        executionJob?.cancel()
        executionJob = coroutineScope.launch {
            AssistantLogger.i("Assistant", "EXECUTION_STARTED: ${confirmedWorkflow.name}")
            val totalSteps = confirmedWorkflow.steps.size
            _uiState.value = AssistantUiState.Running(
                workflowName = confirmedWorkflow.name,
                currentStepIndex = 1,
                totalSteps = totalSteps,
                actionType = confirmedWorkflow.steps.firstOrNull()?.type?.label ?: "TAP",
                coordinateInfo = "(${confirmedWorkflow.steps.firstOrNull()?.startX ?: 0}, ${confirmedWorkflow.steps.firstOrNull()?.startY ?: 0})"
            )

            val sessionCollectorJob = launch {
                automationEngine.executionSession.collect { session ->
                    if (session.state == ExecutionState.RUNNING) {
                        val stepIdx = (session.currentStepIndex + 1).coerceAtMost(totalSteps)
                        val step = session.currentStep ?: confirmedWorkflow.steps.getOrNull(session.currentStepIndex)
                        val actionLabel = step?.type?.label ?: session.activeActionType?.label ?: "TAP"
                        val coord = session.activeCoordinate ?: step?.let {
                            com.taskscheduling.app.domain.model.CoordinateAction(it.startX, it.startY, it.endX, it.endY)
                        }
                        val coordStr = if (coord != null) "(${coord.x}, ${coord.y})" else ""

                        _uiState.value = AssistantUiState.Running(
                            workflowName = confirmedWorkflow.name,
                            currentStepIndex = stepIdx,
                            totalSteps = totalSteps,
                            actionType = actionLabel,
                            coordinateInfo = coordStr
                        )
                    }
                }
            }

            val startTime = System.currentTimeMillis()
            try {
                automationEngine.executeWorkflow(confirmedWorkflow)
            } finally {
                sessionCollectorJob.cancel()
            }

            val finalSession = automationEngine.executionSession.value
            when (finalSession.state) {
                ExecutionState.COMPLETED -> {
                    AssistantLogger.i("Assistant", "EXECUTION_COMPLETED: ${confirmedWorkflow.name}")
                    _uiState.value = AssistantUiState.Completed(
                        workflowName = confirmedWorkflow.name,
                        completedSteps = totalSteps,
                        totalSteps = totalSteps,
                        message = "Workflow completed."
                    )
                    if (isContinuousListening) {
                        coroutineScope.launch {
                            if (autoResumeDelayMs > 0L) kotlinx.coroutines.delay(autoResumeDelayMs)
                            if (isContinuousListening) {
                                _uiState.value = AssistantUiState.Listening("MIC ACTIVE — NO SPEECH DETECTED")
                                onContinuousListeningResumed?.invoke()
                            }
                        }
                    }
                }
                ExecutionState.FAILED -> {
                    val failedStep = finalSession.currentStepIndex + 1
                    AssistantLogger.e("Assistant", "EXECUTION_FAILED: ${confirmedWorkflow.name} at step $failedStep")
                    _uiState.value = AssistantUiState.Error("Workflow stopped at step $failedStep.")
                }
                ExecutionState.STOPPED -> {
                    _uiState.value = AssistantUiState.Error("Workflow stopped by user.")
                }
                else -> {
                    _uiState.value = AssistantUiState.Error("Workflow stopped.")
                }
            }

            persistenceRepository?.let { repo ->
                val historyItem = ExecutionHistoryItem(
                    id = "run_${System.currentTimeMillis()}",
                    workflowId = confirmedWorkflow.id,
                    workflowName = confirmedWorkflow.name,
                    timestamp = startTime,
                    formattedTime = SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault()).format(Date(startTime)),
                    status = finalSession.state.name,
                    completedSteps = if (finalSession.state == ExecutionState.COMPLETED) totalSteps else finalSession.currentStepIndex,
                    totalSteps = totalSteps,
                    failedStep = if (finalSession.state == ExecutionState.FAILED) finalSession.currentStepIndex + 1 else null,
                    failureReason = finalSession.errorMessage
                )
                repo.saveHistoryRecord(historyItem)
            }
        }
    }

    fun executeDirectActionImmediately(intent: CommandIntent) {
        pendingAction = null
        confirmationGranted = false
        executeDirectActionInternal(intent)
    }

    fun executeConfirmedDirectAction(intent: CommandIntent) {
        if (!confirmationGranted || (pendingAction as? PendingAction.DirectDeviceAction)?.intent != intent) {
            _uiState.value = AssistantUiState.Error("Execution rejected: explicit confirmation is required before running actions.")
            return
        }

        val action = (pendingAction as PendingAction.DirectDeviceAction).intent
        pendingAction = null
        confirmationGranted = false

        executeDirectActionInternal(action)
    }

    private fun executeDirectActionInternal(action: CommandIntent) {
        executionJob?.cancel()
        executionJob = coroutineScope.launch {
            AssistantLogger.i("Assistant", "EXECUTION_STARTED: $action")
            _uiState.value = AssistantUiState.Running(
                workflowName = action.displayName,
                currentStepIndex = 1,
                totalSteps = 1,
                actionType = action.name,
                coordinateInfo = ""
            )

            val profile = deviceProfileProvider()
            val result = actionExecutor.executeAction(action, profile)

            when (result) {
                is ActionResult.Success -> {
                    AssistantLogger.i("Assistant", "EXECUTION_COMPLETED: $action")
                    _uiState.value = AssistantUiState.Completed(
                        workflowName = action.displayName,
                        completedSteps = 1,
                        totalSteps = 1,
                        message = result.message
                    )
                    if (isContinuousListening) {
                        coroutineScope.launch {
                            if (autoResumeDelayMs > 0L) kotlinx.coroutines.delay(autoResumeDelayMs)
                            if (isContinuousListening) {
                                _uiState.value = AssistantUiState.Listening("MIC ACTIVE — NO SPEECH DETECTED")
                                onContinuousListeningResumed?.invoke()
                            }
                        }
                    }
                }
                is ActionResult.Failure -> {
                    AssistantLogger.e("Assistant", "EXECUTION_FAILED: $action - ${result.errorMessage}")
                    _uiState.value = AssistantUiState.Error(result.errorMessage)
                    if (isContinuousListening) {
                        coroutineScope.launch {
                            if (autoResumeDelayMs > 0L) kotlinx.coroutines.delay(autoResumeDelayMs)
                            if (isContinuousListening && _uiState.value is AssistantUiState.Error) {
                                _uiState.value = AssistantUiState.Listening("MIC ACTIVE — NO SPEECH DETECTED")
                                onContinuousListeningResumed?.invoke()
                            }
                        }
                    }
                }
            }
        }
    }

    fun cancelConfirmation() = cancelPendingAction()

    fun cancelPendingWorkflow() = cancelPendingAction()

    fun cancelPendingAction() {
        val wasActive = pendingAction != null || executionJob?.isActive == true
        pendingAction = null
        confirmationGranted = false
        executionJob?.cancel()
        automationEngine.stop()
        if (wasActive) {
            val lastMsg = _chatMessages.value.lastOrNull()
            if (lastMsg == null || lastMsg.text != "Execution cancelled.") {
                addChatMessage(isUser = false, text = "Execution cancelled.")
            }
            _uiState.value = AssistantUiState.Cancelled("Execution cancelled.")
        }
        if (isContinuousListening) {
            coroutineScope.launch {
                if (autoResumeDelayMs > 0L) kotlinx.coroutines.delay(autoResumeDelayMs)
                if (isContinuousListening) {
                    _uiState.value = AssistantUiState.Listening("MIC ACTIVE — NO SPEECH DETECTED")
                    onContinuousListeningResumed?.invoke()
                }
            }
        } else {
            _uiState.value = AssistantUiState.Idle("⚪ Not Listening")
        }
    }

    fun stopExecution() {
        automationEngine.stop()
        executionJob?.cancel()
        pendingAction = null
        confirmationGranted = false
        _uiState.value = AssistantUiState.Error("Execution stopped by user.")
    }

    fun setListeningState() {
        _uiState.value = AssistantUiState.Listening("Listening…")
    }

    fun setMutedState(message: String = "Microphone muted") {
        val current = _uiState.value
        if (current !is AssistantUiState.Running && current !is AssistantUiState.Processing) {
            _uiState.value = AssistantUiState.Idle(message)
        }
    }

    fun setLoadingState(message: String = "Thinking…") {
        if (pendingAction == null && _uiState.value !is AssistantUiState.Running && _uiState.value !is AssistantUiState.ConfirmationRequired) {
            _uiState.value = AssistantUiState.Processing(message)
        }
    }

    fun stopListeningState() {
        isContinuousListening = false
        val current = _uiState.value
        if (current !is AssistantUiState.Running) {
            _uiState.value = AssistantUiState.Idle("Listening paused")
        }
    }

    fun setErrorMessage(msg: String) {
        pendingAction = null
        confirmationGranted = false
        _uiState.value = AssistantUiState.Error(msg)
    }

    fun reset() {
        executionJob?.cancel()
        pendingAction = null
        confirmationGranted = false
        _userMessage.value = null
        _uiState.value = AssistantUiState.Idle()
    }
}
