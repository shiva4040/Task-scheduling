package com.taskscheduling.app.feature.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.taskscheduling.app.assistant.TaskNotificationScheduler
import com.taskscheduling.app.data.repository.TaskRepository
import com.taskscheduling.app.domain.model.Task
import com.taskscheduling.app.domain.model.TaskCategory
import com.taskscheduling.app.domain.model.TaskPriority
import com.taskscheduling.app.domain.model.TaskStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class TaskFilterTab(val displayName: String) {
    TODAY("Today"),
    UPCOMING("Upcoming"),
    OVERDUE("Overdue"),
    COMPLETED("Completed"),
    ALL("All")
}

data class TasksFilterState(
    val searchQuery: String = "",
    val selectedTab: TaskFilterTab = TaskFilterTab.TODAY,
    val selectedCategory: TaskCategory = TaskCategory.ALL,
    val selectedPriority: TaskPriority? = null,
    val showOnlyFavorites: Boolean = false
)

class TasksViewModel(
    private val taskRepository: TaskRepository,
    private val notificationScheduler: TaskNotificationScheduler? = null
) : ViewModel() {

    private val _filterState = MutableStateFlow(TasksFilterState())
    val filterState: StateFlow<TasksFilterState> = _filterState.asStateFlow()

    private val allTasksFlow = taskRepository.getTasks()

    val filteredTasks: StateFlow<List<Task>> = combine(allTasksFlow, _filterState) { list, filter ->
        val isSearching = filter.searchQuery.isNotBlank()
        val query = filter.searchQuery.trim()

        list.filter { task ->
            // Tab filter: When searching, search globally across all tasks so any registered action/task is discovered
            val matchesTab = if (isSearching) {
                true
            } else {
                when (filter.selectedTab) {
                    TaskFilterTab.TODAY -> !task.isCompleted && isTaskToday(task)
                    TaskFilterTab.UPCOMING -> !task.isCompleted && isTaskUpcoming(task)
                    TaskFilterTab.OVERDUE -> !task.isCompleted && isTaskOverdue(task)
                    TaskFilterTab.COMPLETED -> task.isCompleted
                    TaskFilterTab.ALL -> true
                }
            }

            // Category filter
            val matchesCategory = filter.selectedCategory == TaskCategory.ALL || task.category == filter.selectedCategory

            // Priority filter
            val matchesPriority = filter.selectedPriority == null || task.priority == filter.selectedPriority

            // Search query filter: Case-insensitive partial matching across title, description, notes, voice triggers, category, and ID
            val matchesSearch = !isSearching ||
                    task.title.contains(query, ignoreCase = true) ||
                    task.description.contains(query, ignoreCase = true) ||
                    task.notes.contains(query, ignoreCase = true) ||
                    task.voiceTriggers.any { it.contains(query, ignoreCase = true) } ||
                    task.category.displayName.contains(query, ignoreCase = true) ||
                    task.id.contains(query, ignoreCase = true)

            // Favorite filter
            val matchesFavorite = !filter.showOnlyFavorites || task.isFavorite

            matchesTab && matchesCategory && matchesPriority && matchesSearch && matchesFavorite
        }.sortedWith(
            compareBy<Task> { it.isCompleted }
                .thenByDescending { it.priority.level }
                .thenBy { it.dueDate ?: "9999" }
                .thenBy { it.dueTime ?: "99:99" }
                .thenByDescending { it.createdAt }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Metrics for counters
    val todayCount: StateFlow<Int> = allTasksFlow.combine(_filterState) { list, _ ->
        list.count { !it.isCompleted && isTaskToday(it) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val overdueCount: StateFlow<Int> = allTasksFlow.combine(_filterState) { list, _ ->
        list.count { !it.isCompleted && isTaskOverdue(it) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val upcomingCount: StateFlow<Int> = allTasksFlow.combine(_filterState) { list, _ ->
        list.count { !it.isCompleted && isTaskUpcoming(it) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val completedCount: StateFlow<Int> = allTasksFlow.combine(_filterState) { list, _ ->
        list.count { it.isCompleted }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun onTabSelect(tab: TaskFilterTab) {
        _filterState.value = _filterState.value.copy(selectedTab = tab)
    }

    fun onSearchChange(query: String) {
        _filterState.value = _filterState.value.copy(searchQuery = query)
    }

    fun onCategorySelect(category: TaskCategory) {
        _filterState.value = _filterState.value.copy(selectedCategory = category)
    }

    fun onPrioritySelect(priority: TaskPriority?) {
        _filterState.value = _filterState.value.copy(selectedPriority = priority)
    }

    fun toggleFavoritesOnly() {
        _filterState.value = _filterState.value.copy(showOnlyFavorites = !_filterState.value.showOnlyFavorites)
    }

    fun toggleFavorite(taskId: String) {
        viewModelScope.launch { taskRepository.toggleFavorite(taskId) }
    }

    fun toggleCompleted(taskId: String) {
        viewModelScope.launch {
            val updated = taskRepository.toggleCompleted(taskId)
            if (updated != null) {
                if (updated.isCompleted) {
                    notificationScheduler?.cancelReminder(taskId)
                } else if (updated.hasReminder && updated.notificationEnabled) {
                    notificationScheduler?.scheduleReminder(updated)
                }
            }
        }
    }

    fun rescheduleTask(taskId: String, newDueDate: String?, newDueTime: String?) {
        viewModelScope.launch {
            val updated = taskRepository.rescheduleTask(taskId, newDueDate, newDueTime)
            if (updated != null && updated.hasReminder) {
                notificationScheduler?.scheduleReminder(updated)
            }
        }
    }

    fun duplicateTask(taskId: String) {
        viewModelScope.launch { taskRepository.duplicateTask(taskId) }
    }

    fun deleteTask(taskId: String) {
        viewModelScope.launch {
            taskRepository.deleteTask(taskId)
            notificationScheduler?.cancelReminder(taskId)
        }
    }

    fun saveTask(task: Task) {
        viewModelScope.launch {
            taskRepository.saveTask(task)
            if (task.hasReminder && task.notificationEnabled && !task.isCompleted) {
                notificationScheduler?.scheduleReminder(task)
            } else {
                notificationScheduler?.cancelReminder(task.id)
            }
        }
    }

    companion object {
        fun isTaskToday(task: Task): Boolean {
            val dateStr = task.dueDate?.trim()?.lowercase(Locale.ROOT) ?: return true
            if (dateStr == "today" || dateStr == "tonight") return true
            val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date())
            return dateStr == todayStr
        }

        fun isTaskOverdue(task: Task): Boolean {
            val dateStr = task.dueDate?.trim()?.lowercase(Locale.ROOT) ?: return false
            if (dateStr == "yesterday") return true
            if (dateStr.matches(Regex("^[0-9]{4}-[0-9]{2}-[0-9]{2}$"))) {
                val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date())
                return dateStr < todayStr
            }
            return false
        }

        fun isTaskUpcoming(task: Task): Boolean {
            val dateStr = task.dueDate?.trim()?.lowercase(Locale.ROOT) ?: return false
            if (dateStr == "tomorrow" || dateStr == "next week") return true
            if (dateStr.matches(Regex("^[0-9]{4}-[0-9]{2}-[0-9]{2}$"))) {
                val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date())
                return dateStr > todayStr
            }
            return !isTaskToday(task) && !isTaskOverdue(task)
        }
    }
}
