package com.taskscheduling.app.feature.assistant

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.taskscheduling.app.assistant.CommandIntent
import com.taskscheduling.app.assistant.LocalCommandResolver
import com.taskscheduling.app.automation.AutomationEngine
import com.taskscheduling.app.data.repository.TaskRepository
import com.taskscheduling.app.data.repository.WorkflowPersistenceRepository
import com.taskscheduling.app.domain.model.ExecutionState
import com.taskscheduling.app.domain.model.Task
import com.taskscheduling.app.domain.model.TaskCategory
import com.taskscheduling.app.domain.model.Workflow
import com.taskscheduling.app.ui.theme.AccentEmerald
import com.taskscheduling.app.ui.theme.AccentIndigo
import com.taskscheduling.app.ui.theme.AccentRose
import com.taskscheduling.app.ui.theme.AppTheme
import com.taskscheduling.app.ui.theme.Glass3DButton
import com.taskscheduling.app.ui.theme.GlassCyan
import com.taskscheduling.app.ui.theme.GlassEmerald
import com.taskscheduling.app.ui.theme.GlassIndigo
import com.taskscheduling.app.ui.theme.GlassRose
import com.taskscheduling.app.ui.theme.GlassStatusIndicator
import com.taskscheduling.app.ui.theme.LuminousVoiceOrb
import com.taskscheduling.app.ui.theme.StatusType
import com.taskscheduling.app.ui.theme.VoiceAssistantStatus
import com.taskscheduling.app.voice.VoiceInputCallback
import com.taskscheduling.app.voice.VoiceInputManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ChatSender { USER, ASSISTANT, SYSTEM }

enum class ExecutionMessageStatus {
    NONE,
    CONFIRMATION_REQUIRED,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED
}

data class ChatMessage(
    val id: String,
    val sender: ChatSender,
    val text: String,
    val timestamp: String,
    val associatedTask: Task? = null,
    val associatedWorkflow: Workflow? = null,
    val isConfirmation: Boolean = false,
    val executionStatus: ExecutionMessageStatus = ExecutionMessageStatus.NONE,
    val progressMessage: String? = null,
    val showCreateWorkflowAction: Boolean = false,
    val queryForNewWorkflow: String? = null
)

class AssistantViewModel(
    private val taskRepository: TaskRepository,
    private val persistenceRepository: WorkflowPersistenceRepository? = null,
    private val commandResolver: com.taskscheduling.app.assistant.CommandResolver = com.taskscheduling.app.assistant.LocalCommandResolver(),
    private val workflowResolver: com.taskscheduling.app.assistant.WorkflowResolver? = persistenceRepository?.let { com.taskscheduling.app.assistant.LocalWorkflowResolver(it) },
    private val automationEngine: AutomationEngine? = null,
    private val context: Context? = null
) : ViewModel() {

    private val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())

    private val _orbStatus = MutableStateFlow(VoiceAssistantStatus.IDLE)
    val orbStatus: StateFlow<VoiceAssistantStatus> = _orbStatus.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _statusLabel = MutableStateFlow("Ready for instructions")
    val statusLabel: StateFlow<String> = _statusLabel.asStateFlow()

    private val _partialTranscript = MutableStateFlow("")
    val partialTranscript: StateFlow<String> = _partialTranscript.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _isExecuting = MutableStateFlow(false)
    val isExecuting: StateFlow<Boolean> = _isExecuting.asStateFlow()

    private val _messages = MutableStateFlow(
        listOf(
            ChatMessage(
                id = "m0",
                sender = ChatSender.ASSISTANT,
                text = "Hello! Tell me what you'd like to do, e.g. \"Turn on dark mode\" or \"Open YouTube\".",
                timestamp = timeFormat.format(Date())
            )
        )
    )
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private var voiceInputManager: VoiceInputManager? = null
    private var activeExecutionJob: Job? = null

    init {
        context?.let { ctx ->
            try {
                voiceInputManager = VoiceInputManager(ctx)
                voiceInputManager?.prepareEngineAsync { success ->
                    if (success) {
                        _statusLabel.value = "Whisper offline voice ready"
                    } else {
                        _statusLabel.value = "Ready (voice engine fallback)"
                    }
                }
            } catch (_: Throwable) {
                // Ignore initialization failures in testing environments
            }
        }
    }

    fun toggleListening() {
        if (_isListening.value) {
            stopListening()
        } else {
            startListening()
        }
    }

    fun startListening() {
        _errorMessage.value = null
        val vim = voiceInputManager
        if (vim != null) {
            _isListening.value = true
            _orbStatus.value = VoiceAssistantStatus.LISTENING
            _statusLabel.value = "Listening for speech..."
            _partialTranscript.value = ""

            vim.startListening(object : VoiceInputCallback {
                override fun onListeningStarted() {
                    _isListening.value = true
                    _orbStatus.value = VoiceAssistantStatus.LISTENING
                    _statusLabel.value = "Listening for speech..."
                }

                override fun onPartialResult(hypothesis: String) {
                    _partialTranscript.value = hypothesis
                    if (hypothesis.isNotBlank()) {
                        _statusLabel.value = hypothesis
                    }
                }

                override fun onSpeechRecognized(text: String) {
                    _isListening.value = false
                    _partialTranscript.value = ""
                    if (text.isNotBlank()) {
                        sendMessage(text)
                    } else {
                        _orbStatus.value = VoiceAssistantStatus.IDLE
                        _statusLabel.value = "Ready for instructions"
                    }
                }

                override fun onError(message: String) {
                    _isListening.value = false
                    _orbStatus.value = VoiceAssistantStatus.ERROR
                    _errorMessage.value = message
                    _statusLabel.value = "Voice error: $message"
                }

                override fun onListeningStopped() {
                    _isListening.value = false
                    if (_orbStatus.value == VoiceAssistantStatus.LISTENING) {
                        _orbStatus.value = VoiceAssistantStatus.IDLE
                        _statusLabel.value = "Ready for instructions"
                    }
                }
            })
        } else {
            _isListening.value = true
            _orbStatus.value = VoiceAssistantStatus.LISTENING
            _statusLabel.value = "Listening... (Tap orb to finish)"
        }
    }

    fun stopListening() {
        _isListening.value = false
        val vim = voiceInputManager
        if (vim != null) {
            vim.stopListening()
        } else {
            _orbStatus.value = VoiceAssistantStatus.IDLE
            _statusLabel.value = "Ready for instructions"
        }
    }

    fun clearError() {
        _errorMessage.value = null
        if (_orbStatus.value == VoiceAssistantStatus.ERROR) {
            _orbStatus.value = VoiceAssistantStatus.IDLE
            _statusLabel.value = "Ready for instructions"
        }
    }

    fun clearHistory() {
        _messages.value = listOf(
            ChatMessage(
                id = "m_reset_${System.currentTimeMillis()}",
                sender = ChatSender.ASSISTANT,
                text = "Conversation cleared. Tell me what you'd like to do, e.g. \"Turn on dark mode\" or \"Run daily study\".",
                timestamp = timeFormat.format(Date())
            )
        )
        _statusLabel.value = "Ready for instructions"
        _orbStatus.value = VoiceAssistantStatus.IDLE
        _errorMessage.value = null
    }

    /**
     * Cancels an active workflow confirmation.
     * Guaranteed not to append duplicate cancellation messages for the same event.
     */
    fun cancelConfirmation(messageId: String) {
        val target = _messages.value.find { it.id == messageId }
        if (target == null || !target.isConfirmation) {
            return
        }

        // Update target confirmation status without mangling text with duplicate strings
        _messages.value = _messages.value.map {
            if (it.id == messageId) {
                it.copy(
                    isConfirmation = false,
                    executionStatus = ExecutionMessageStatus.CANCELLED
                )
            } else it
        }

        // Emit exactly ONE cancellation confirmation
        _messages.value = _messages.value + ChatMessage(
            id = "bot_cancel_${System.currentTimeMillis()}",
            sender = ChatSender.ASSISTANT,
            text = "Workflow execution was cancelled.",
            timestamp = timeFormat.format(Date()),
            executionStatus = ExecutionMessageStatus.CANCELLED
        )
        _orbStatus.value = VoiceAssistantStatus.IDLE
        _statusLabel.value = "Execution cancelled"
    }

    /**
     * Stops an actively running workflow execution.
     */
    fun stopRunningExecution() {
        if (activeExecutionJob?.isActive != true && !_isExecuting.value) {
            return
        }
        activeExecutionJob?.cancel()
        activeExecutionJob = null
        automationEngine?.stop()
        _isExecuting.value = false
        _orbStatus.value = VoiceAssistantStatus.IDLE
        _statusLabel.value = "Execution stopped"

        _messages.value = _messages.value + ChatMessage(
            id = "bot_stopped_${System.currentTimeMillis()}",
            sender = ChatSender.ASSISTANT,
            text = "Workflow execution was stopped.",
            timestamp = timeFormat.format(Date()),
            executionStatus = ExecutionMessageStatus.CANCELLED
        )
    }

    /**
     * Executes the confirmed task/workflow using the real automation engine, or invokes external execution.
     */
    fun runWorkflow(
        task: Task,
        messageId: String? = null,
        onNavigateToExecution: ((String) -> Unit)? = null
    ) {
        if (messageId != null) {
            _messages.value = _messages.value.map {
                if (it.id == messageId) {
                    it.copy(
                        isConfirmation = false,
                        executionStatus = ExecutionMessageStatus.RUNNING
                    )
                } else it
            }
        }

        val engine = automationEngine
        if (engine != null) {
            activeExecutionJob?.cancel()
            _isExecuting.value = true
            activeExecutionJob = viewModelScope.launch {
                try {
                    _orbStatus.value = VoiceAssistantStatus.PROCESSING
                    _statusLabel.value = "Starting ${task.workflow.name}..."

                    val progressMsgId = "exec_${System.currentTimeMillis()}"
                    val stepsTotal = task.workflow.steps.size

                    val progressMsg = ChatMessage(
                        id = progressMsgId,
                        sender = ChatSender.ASSISTANT,
                        text = "Running \"${task.workflow.name}\" ($stepsTotal steps)...",
                        timestamp = timeFormat.format(Date()),
                        associatedTask = task,
                        associatedWorkflow = task.workflow,
                        executionStatus = ExecutionMessageStatus.RUNNING,
                        progressMessage = "Preparing automation steps..."
                    )
                    _messages.value = _messages.value + progressMsg

                    // Launch workflow execution on engine
                    engine.execute(task, 1.0f)

                    var hasFinished = false
                    engine.executionSession.collect { session ->
                        if (session.state == ExecutionState.RUNNING) {
                            _orbStatus.value = VoiceAssistantStatus.PROCESSING
                            _statusLabel.value = "Step ${session.currentStepIndex + 1} of $stepsTotal"
                            _messages.value = _messages.value.map {
                                if (it.id == progressMsgId) {
                                    val desc = session.currentStep?.description ?: "Executing action"
                                    it.copy(
                                        progressMessage = "Step ${session.currentStepIndex + 1}/$stepsTotal: $desc"
                                    )
                                } else it
                            }
                        } else if (session.state == ExecutionState.COMPLETED && !hasFinished) {
                            hasFinished = true
                            _isExecuting.value = false
                            _orbStatus.value = VoiceAssistantStatus.SPEAKING
                            _statusLabel.value = "Workflow completed"
                            _messages.value = _messages.value.map {
                                if (it.id == progressMsgId) {
                                    it.copy(
                                        executionStatus = ExecutionMessageStatus.COMPLETED,
                                        progressMessage = null,
                                        text = "✓ Workflow \"${task.workflow.name}\" completed successfully ($stepsTotal steps)."
                                    )
                                } else it
                            }
                            delay(900)
                            _orbStatus.value = VoiceAssistantStatus.IDLE
                            _statusLabel.value = "Ready for instructions"
                        } else if (session.state == ExecutionState.FAILED && !hasFinished) {
                            hasFinished = true
                            _isExecuting.value = false
                            _orbStatus.value = VoiceAssistantStatus.ERROR
                            _statusLabel.value = "Execution failed"
                            _messages.value = _messages.value.map {
                                if (it.id == progressMsgId) {
                                    it.copy(
                                        executionStatus = ExecutionMessageStatus.FAILED,
                                        progressMessage = null,
                                        text = "Workflow \"${task.workflow.name}\" failed: ${session.errorMessage ?: "Unknown error"}"
                                    )
                                } else it
                            }
                        } else if (session.state == ExecutionState.STOPPED && !hasFinished) {
                            hasFinished = true
                            _isExecuting.value = false
                            _orbStatus.value = VoiceAssistantStatus.IDLE
                            _statusLabel.value = "Execution stopped"
                            _messages.value = _messages.value.map {
                                if (it.id == progressMsgId) {
                                    it.copy(
                                        executionStatus = ExecutionMessageStatus.CANCELLED,
                                        progressMessage = null,
                                        text = "Workflow \"${task.workflow.name}\" was cancelled."
                                    )
                                } else it
                            }
                        }
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    _isExecuting.value = false
                    _orbStatus.value = VoiceAssistantStatus.IDLE
                    _statusLabel.value = "Execution cancelled"
                } catch (e: Exception) {
                    _isExecuting.value = false
                    _orbStatus.value = VoiceAssistantStatus.ERROR
                    _statusLabel.value = "Execution error"
                    _errorMessage.value = e.localizedMessage ?: "Execution failed"
                }
            }
        } else if (onNavigateToExecution != null) {
            onNavigateToExecution(task.id)
        }
    }

    private fun isCancellationPhrase(text: String): Boolean {
        val norm = LocalCommandResolver.normalize(text)
        return norm == "cancel" || norm == "stop" || norm == "abort" || norm == "no" ||
                norm == "do not run" || norm == "dont run" || norm == "cancel workflow" ||
                norm == "stop execution"
    }

    fun sendMessage(content: String) {
        if (content.isBlank()) return
        val trimmed = content.trim()
        val userMsg = ChatMessage(
            id = "user_${System.currentTimeMillis()}",
            sender = ChatSender.USER,
            text = trimmed,
            timestamp = timeFormat.format(Date())
        )
        _messages.value = _messages.value + userMsg

        // Check if user is cancelling via voice or text input
        if (isCancellationPhrase(trimmed)) {
            val pendingConfirmation = _messages.value.lastOrNull { it.isConfirmation }
            if (pendingConfirmation != null) {
                cancelConfirmation(pendingConfirmation.id)
                return
            }
            if (_isExecuting.value || activeExecutionJob?.isActive == true) {
                stopRunningExecution()
                return
            }
            // Nothing was running or pending — do NOT append spurious cancellation messages!
            _messages.value = _messages.value + ChatMessage(
                id = "bot_${System.currentTimeMillis()}",
                sender = ChatSender.ASSISTANT,
                text = "Nothing is currently running or awaiting confirmation.",
                timestamp = timeFormat.format(Date())
            )
            _orbStatus.value = VoiceAssistantStatus.IDLE
            _statusLabel.value = "Ready for instructions"
            return
        }

        _orbStatus.value = VoiceAssistantStatus.PROCESSING
        _statusLabel.value = "Processing goal..."

        viewModelScope.launch {
            try {
                // 1. Check custom saved workflows by name via workflowResolver
                val customRes = workflowResolver?.resolveByName(trimmed)
                when (customRes) {
                    is com.taskscheduling.app.assistant.WorkflowResolution.Found -> {
                        val wf = customRes.workflow
                        val task = Task(
                            id = wf.id,
                            title = wf.name,
                            description = wf.description,
                            category = TaskCategory.CUSTOM,
                            workflow = wf
                        )
                        _messages.value = _messages.value + ChatMessage(
                            id = "bot_${System.currentTimeMillis()}",
                            sender = ChatSender.ASSISTANT,
                            text = "I found your \"${wf.name}\" workflow (${wf.steps.size} steps).\nDo you want me to run it?",
                            timestamp = timeFormat.format(Date()),
                            associatedTask = task,
                            associatedWorkflow = wf,
                            isConfirmation = true,
                            executionStatus = ExecutionMessageStatus.CONFIRMATION_REQUIRED
                        )
                        completeInteraction(true)
                        return@launch
                    }
                    is com.taskscheduling.app.assistant.WorkflowResolution.Ambiguous -> {
                        val candidateNames = customRes.candidates.joinToString("\n• ", prefix = "• ") { it.name }
                        _messages.value = _messages.value + ChatMessage(
                            id = "bot_${System.currentTimeMillis()}",
                            sender = ChatSender.ASSISTANT,
                            text = "I found multiple matching workflows. Which one do you mean?\n$candidateNames",
                            timestamp = timeFormat.format(Date())
                        )
                        completeInteraction(true)
                        return@launch
                    }
                    else -> Unit
                }

                // 2. Check predefined commands & wallpaper intents
                val intent = commandResolver.resolve(trimmed)
                if (intent != null) {
                    if (intent == CommandIntent.CHANGE_WALLPAPER) {
                        val wallpapers = workflowResolver?.getWallpaperWorkflows() ?: emptyList()
                        if (wallpapers.isEmpty()) {
                            _messages.value = _messages.value + ChatMessage(
                                id = "bot_${System.currentTimeMillis()}",
                                sender = ChatSender.ASSISTANT,
                                text = "No saved Change Wallpaper workflow was found.\nWould you like to create one now?",
                                timestamp = timeFormat.format(Date()),
                                showCreateWorkflowAction = true,
                                queryForNewWorkflow = "Change Wallpaper"
                            )
                            completeInteraction(false)
                            return@launch
                        } else if (wallpapers.size == 1) {
                            val wf = wallpapers.first()
                            val task = taskRepository.getTaskById(wf.id)?.copy(workflow = wf)
                                ?: Task(
                                    id = "task_${wf.id}",
                                    title = wf.name,
                                    description = wf.description,
                                    category = TaskCategory.CUSTOM,
                                    workflow = wf
                                )
                            _messages.value = _messages.value + ChatMessage(
                                id = "bot_${System.currentTimeMillis()}",
                                sender = ChatSender.ASSISTANT,
                                text = "I found your \"${wf.name}\" workflow (${wf.steps.size} steps).\nDo you want me to run it?",
                                timestamp = timeFormat.format(Date()),
                                associatedTask = task,
                                associatedWorkflow = wf,
                                isConfirmation = true,
                                executionStatus = ExecutionMessageStatus.CONFIRMATION_REQUIRED
                            )
                            completeInteraction(true)
                            return@launch
                        } else {
                            val candidateNames = wallpapers.joinToString("\n• ", prefix = "• ") { it.name }
                            _messages.value = _messages.value + ChatMessage(
                                id = "bot_${System.currentTimeMillis()}",
                                sender = ChatSender.ASSISTANT,
                                text = "I found multiple wallpaper workflows. Which one do you mean?\n$candidateNames",
                                timestamp = timeFormat.format(Date())
                            )
                            completeInteraction(true)
                            return@launch
                        }
                    }

                    val wf = workflowResolver?.resolveWorkflow(intent)
                    if (wf != null && wf.steps.isNotEmpty()) {
                        val task = taskRepository.getTaskById(wf.id)?.copy(workflow = wf)
                            ?: Task(
                                id = "task_${wf.id}",
                                title = wf.name,
                                description = wf.description,
                                category = TaskCategory.CUSTOM,
                                workflow = wf
                            )
                        _messages.value = _messages.value + ChatMessage(
                            id = "bot_${System.currentTimeMillis()}",
                            sender = ChatSender.ASSISTANT,
                            text = "I found your \"${wf.name}\" workflow (${wf.steps.size} steps).\nDo you want me to run it?",
                            timestamp = timeFormat.format(Date()),
                            associatedTask = task,
                            associatedWorkflow = wf,
                            isConfirmation = true,
                            executionStatus = ExecutionMessageStatus.CONFIRMATION_REQUIRED
                        )
                        completeInteraction(true)
                        return@launch
                    } else if (com.taskscheduling.app.assistant.DefaultExecutionPolicyResolver.getExecutionPolicy(intent) == com.taskscheduling.app.assistant.ExecutionPolicy.IMMEDIATE) {
                        _messages.value = _messages.value + ChatMessage(
                            id = "bot_${System.currentTimeMillis()}",
                            sender = ChatSender.ASSISTANT,
                            text = "Executing action: ${intent.displayName}",
                            timestamp = timeFormat.format(Date())
                        )
                        completeInteraction(true)
                        return@launch
                    }
                }

                // 3. Fallback search via task repository
                val matched = taskRepository.findTaskByVoiceIntent(trimmed)
                if (matched != null) {
                    _messages.value = _messages.value + ChatMessage(
                        id = "bot_${System.currentTimeMillis()}",
                        sender = ChatSender.ASSISTANT,
                        text = "I found your \"${matched.workflow.name}\" workflow.\nDo you want me to run it?",
                        timestamp = timeFormat.format(Date()),
                        associatedTask = matched,
                        associatedWorkflow = matched.workflow,
                        isConfirmation = true,
                        executionStatus = ExecutionMessageStatus.CONFIRMATION_REQUIRED
                    )
                    completeInteraction(true)
                } else {
                    // Truly not found — explain clearly and offer working Create Workflow action
                    val notFoundName = if (customRes is com.taskscheduling.app.assistant.WorkflowResolution.NotFound && customRes.queryName.isNotBlank()) {
                        customRes.queryName
                    } else {
                        trimmed
                    }
                    _messages.value = _messages.value + ChatMessage(
                        id = "bot_${System.currentTimeMillis()}",
                        sender = ChatSender.ASSISTANT,
                        text = "I couldn't find a saved workflow for \"$notFoundName\". Would you like to create one now?",
                        timestamp = timeFormat.format(Date()),
                        showCreateWorkflowAction = true,
                        queryForNewWorkflow = notFoundName
                    )
                    completeInteraction(false)
                }
            } catch (e: Exception) {
                _orbStatus.value = VoiceAssistantStatus.ERROR
                _errorMessage.value = e.localizedMessage ?: "Failed to process command"
                _statusLabel.value = "Error processing instruction"
            }
        }
    }

    private suspend fun completeInteraction(success: Boolean) {
        _orbStatus.value = VoiceAssistantStatus.SPEAKING
        _statusLabel.value = if (success) "Workflow matched" else "Response ready"
        delay(700)
        _orbStatus.value = VoiceAssistantStatus.IDLE
        _statusLabel.value = "Ready for instructions"
    }
}

@Composable
fun AssistantScreen(
    viewModel: AssistantViewModel,
    onNavigateToExecution: (String) -> Unit,
    onNavigateToBuilder: (String) -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateToSettings: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = AppTheme.colors
    val messages by viewModel.messages.collectAsState()
    val orbStatus by viewModel.orbStatus.collectAsState()
    val isListening by viewModel.isListening.collectAsState()
    val statusLabel by viewModel.statusLabel.collectAsState()
    val partialTranscript by viewModel.partialTranscript.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val isExecuting by viewModel.isExecuting.collectAsState()

    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Auto-scroll to bottom as new messages arrive
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    val suggestedPrompts = listOf(
        "Open YouTube",
        "Turn on dark mode",
        "Open Settings",
        "Create workflow",
        "Check battery status"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .imePadding()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        // 1. Compact Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = colors.textPrimary
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Column {
                    Text(
                        text = "Task Assistant",
                        color = colors.textPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Conversational phone automation",
                        color = GlassIndigo,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                GlassStatusIndicator(
                    label = when (orbStatus) {
                        VoiceAssistantStatus.LISTENING -> "LISTENING"
                        VoiceAssistantStatus.PROCESSING -> "PROCESSING"
                        VoiceAssistantStatus.SPEAKING -> "SPEAKING"
                        VoiceAssistantStatus.ERROR -> "ATTENTION"
                        VoiceAssistantStatus.MUTED,
                        VoiceAssistantStatus.STOPPED -> "OFFLINE"
                        else -> "READY"
                    },
                    status = when (orbStatus) {
                        VoiceAssistantStatus.LISTENING,
                        VoiceAssistantStatus.PROCESSING,
                        VoiceAssistantStatus.SPEAKING -> StatusType.RUNNING
                        VoiceAssistantStatus.ERROR,
                        VoiceAssistantStatus.MUTED,
                        VoiceAssistantStatus.STOPPED -> StatusType.ERROR
                        else -> StatusType.READY
                    }
                )

                Spacer(modifier = Modifier.width(4.dp))

                // Clear Conversation History
                IconButton(
                    onClick = { viewModel.clearHistory() }
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Clear Chat History",
                        tint = colors.textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                if (onNavigateToSettings != null) {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Assistant Settings",
                            tint = colors.textSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // 2. Hero Bot Section (Central Luminous Voice Orb)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            LuminousVoiceOrb(
                status = orbStatus,
                size = 92.dp,
                onClick = { viewModel.toggleListening() }
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = statusLabel,
                color = colors.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )

            if (partialTranscript.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "\"$partialTranscript\"",
                    color = GlassIndigo,
                    fontSize = 12.sp,
                    fontStyle = FontStyle.Italic
                )
            } else {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (isListening) "Tap orb to finish speaking" else "Tap orb or mic to speak offline",
                    color = colors.textTertiary,
                    fontSize = 11.sp
                )
            }
        }

        // Error message banner
        AnimatedVisibility(
            visible = errorMessage != null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            errorMessage?.let { err ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(AccentRose.copy(alpha = if (colors.isDark) 0.18f else 0.12f))
                        .border(1.dp, AccentRose.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = err,
                            color = AccentRose,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { viewModel.clearError() },
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss error",
                                tint = AccentRose,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }

        // Suggested Prompts Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            suggestedPrompts.forEach { prompt ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.surfaceSecondary)
                        .border(1.dp, colors.borderSubtle, RoundedCornerShape(12.dp))
                        .clickable { viewModel.sendMessage(prompt) }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = prompt,
                        color = colors.textSecondary,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // 3. Conversation Message Stream (Auto-scrolling, theme-consistent)
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 6.dp)
        ) {
            items(messages, key = { it.id }) { msg ->
                BotChatBubble(
                    message = msg,
                    onRunTask = { task ->
                        viewModel.runWorkflow(task, msg.id, onNavigateToExecution)
                    },
                    onCancelTask = { viewModel.cancelConfirmation(it) },
                    onStopExecution = { viewModel.stopRunningExecution() },
                    onCreateWorkflow = { onNavigateToBuilder(it) },
                    onOpenFullscreenExecution = { tid -> onNavigateToExecution(tid) }
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // 4. Command Input Bar (Floating above keyboard via imePadding)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(colors.surface)
                .border(1.dp, colors.borderSubtle, RoundedCornerShape(16.dp))
                .padding(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Voice Input Toggle Button
            IconButton(
                onClick = { viewModel.toggleListening() },
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (isListening) AccentIndigo.copy(alpha = 0.2f) else colors.surfaceSecondary
                    )
                    .border(
                        1.dp,
                        if (isListening) AccentIndigo else colors.borderSubtle,
                        CircleShape
                    )
            ) {
                Icon(
                    imageVector = if (isListening) Icons.Default.Stop else Icons.Default.Mic,
                    contentDescription = if (isListening) "Stop voice input" else "Start voice input",
                    tint = if (isListening) AccentIndigo else colors.textSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = {
                    Text(
                        text = "Type a command or ask to run a task...",
                        fontSize = 13.sp,
                        color = colors.textTertiary
                    )
                },
                modifier = Modifier.weight(1f),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedTextColor = colors.textPrimary,
                    unfocusedTextColor = colors.textPrimary,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent
                )
            )

            Spacer(modifier = Modifier.width(6.dp))

            Glass3DButton(
                text = "Send",
                icon = Icons.AutoMirrored.Filled.Send,
                onClick = {
                    if (input.isNotBlank()) {
                        val textToSend = input
                        input = ""
                        viewModel.sendMessage(textToSend)
                    }
                },
                accentColor = GlassIndigo
            )
        }
    }
}

@Composable
private fun BotChatBubble(
    message: ChatMessage,
    onRunTask: (Task) -> Unit,
    onCancelTask: (String) -> Unit,
    onStopExecution: () -> Unit,
    onCreateWorkflow: (String) -> Unit,
    onOpenFullscreenExecution: (String) -> Unit
) {
    val colors = AppTheme.colors
    val isUser = message.sender == ChatSender.USER

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isUser) 16.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 16.dp
                    )
                )
                .background(
                    if (isUser) {
                        Brush.linearGradient(listOf(AccentIndigo, Color(0xFF4338CA)))
                    } else {
                        Brush.linearGradient(
                            if (colors.isDark) {
                                listOf(Color(0xFF1E293B), Color(0xFF131D2E))
                            } else {
                                listOf(colors.surface, colors.surface)
                            }
                        )
                    }
                )
                .border(
                    1.dp,
                    if (isUser) AccentIndigo.copy(alpha = 0.5f) else colors.borderSubtle,
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isUser) 16.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 16.dp
                    )
                )
                .padding(14.dp)
        ) {
            Column {
                if (!isUser) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Luminous glass dot (strictly abstract, no robot, no cloud)
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when (message.executionStatus) {
                                            ExecutionMessageStatus.RUNNING -> GlassIndigo
                                            ExecutionMessageStatus.COMPLETED -> GlassEmerald
                                            ExecutionMessageStatus.FAILED,
                                            ExecutionMessageStatus.CANCELLED -> GlassRose
                                            else -> GlassCyan
                                        }
                                    )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "TASK ASSISTANT",
                                color = GlassIndigo,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Execution status tag
                        when (message.executionStatus) {
                            ExecutionMessageStatus.CANCELLED -> {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0x2664748B))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "CANCELLED",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            ExecutionMessageStatus.COMPLETED -> {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0x2610B981))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "COMPLETED",
                                        color = AccentEmerald,
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            ExecutionMessageStatus.FAILED -> {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0x26F43F5E))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "FAILED",
                                        color = AccentRose,
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            else -> Unit
                        }
                    }
                }

                Text(
                    text = message.text,
                    color = if (isUser) Color.White else colors.textPrimary,
                    fontSize = 13.5.sp,
                    lineHeight = 19.sp
                )

                // Live Execution Progress indicator
                if (message.executionStatus == ExecutionMessageStatus.RUNNING) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.surfaceSecondary)
                            .border(1.dp, GlassIndigo.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                            .padding(10.dp)
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = GlassIndigo
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = message.progressMessage ?: "Executing steps...",
                                    color = colors.textPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Glass3DButton(
                                    text = "Stop",
                                    icon = Icons.Default.Stop,
                                    onClick = onStopExecution,
                                    accentColor = AccentRose,
                                    modifier = Modifier.weight(1f)
                                )
                                message.associatedTask?.let { task ->
                                    Glass3DButton(
                                        text = "Fullscreen",
                                        icon = Icons.Default.Fullscreen,
                                        onClick = { onOpenFullscreenExecution(task.id) },
                                        accentColor = GlassIndigo,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }

                // Matched Workflow Confirmation Card
                message.associatedTask?.let { task ->
                    if (message.isConfirmation) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(colors.surfaceSecondary)
                                .border(1.dp, colors.borderSubtle, RoundedCornerShape(12.dp))
                                .padding(10.dp)
                        ) {
                            Column {
                                Text(
                                    text = task.title,
                                    color = colors.textPrimary,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${task.workflow.steps.size} automation steps • ${task.category.displayName}",
                                    color = colors.textSecondary,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )

                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Glass3DButton(
                                        text = "Cancel",
                                        onClick = { onCancelTask(message.id) },
                                        accentColor = Color(0xFF64748B),
                                        modifier = Modifier.weight(1f)
                                    )

                                    Glass3DButton(
                                        text = "Run Now",
                                        icon = Icons.Default.PlayArrow,
                                        onClick = { onRunTask(task) },
                                        accentColor = AccentEmerald,
                                        contentColor = Color.White,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }

                // Create Workflow Action when matching workflow does not exist
                if (message.showCreateWorkflowAction) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Glass3DButton(
                        text = "Create Workflow",
                        icon = Icons.Default.Add,
                        onClick = {
                            onCreateWorkflow(message.queryForNewWorkflow ?: "")
                        },
                        accentColor = GlassIndigo
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = message.timestamp,
                    color = if (isUser) Color(0xCCFFFFFF) else colors.textTertiary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}
