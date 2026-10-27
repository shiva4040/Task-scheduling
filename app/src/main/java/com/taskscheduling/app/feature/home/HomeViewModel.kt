package com.taskscheduling.app.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.taskscheduling.app.data.repository.ActivityRepository
import com.taskscheduling.app.data.repository.TaskRepository
import com.taskscheduling.app.domain.model.ActivityRecord
import com.taskscheduling.app.domain.model.Task
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val greeting: String = getDynamicGreeting(),
    val commandText: String = "",
    val suggestedCommands: List<String> = listOf(
        "Open YouTube",
        "Turn on dark mode",
        "Increase text size",
        "Open Wi-Fi",
        "Start study mode"
    ),
    val matchedTask: Task? = null,
    val isNetworkConnected: Boolean = true,
    val networkLabel: String = "Network Connected"
)

private fun getDynamicGreeting(userName: String = "Shiva"): String {
    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    val period = when {
        hour in 5..11 -> "Good morning"
        hour in 12..16 -> "Good afternoon"
        else -> "Good evening"
    }
    return "$period, $userName"
}

class HomeViewModel(
    private val taskRepository: TaskRepository,
    private val activityRepository: ActivityRepository,
    context: android.content.Context? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        context?.let { ctx ->
            viewModelScope.launch {
                com.taskscheduling.app.util.NetworkConnectivityMonitor.getInstance(ctx).networkStatus.collect { status ->
                    _uiState.value = _uiState.value.copy(
                        isNetworkConnected = status.isConnected,
                        networkLabel = status.displayLabel
                    )
                }
            }
        }
    }

    val tasks: StateFlow<List<Task>> = taskRepository.getTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activities: StateFlow<List<ActivityRecord>> = activityRepository.getActivities()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onCommandChange(newText: String) {
        _uiState.value = _uiState.value.copy(
            commandText = newText,
            matchedTask = if (newText.isNotBlank()) taskRepository.findTaskByVoiceIntent(newText) else null
        )
    }

    fun selectSuggestedCommand(cmd: String) {
        onCommandChange(cmd)
    }

    fun toggleFavorite(taskId: String) {
        viewModelScope.launch {
            taskRepository.toggleFavorite(taskId)
        }
    }
}
