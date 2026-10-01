package com.taskscheduling.app.feature.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.taskscheduling.app.data.repository.TaskRepository
import com.taskscheduling.app.domain.model.Task
import com.taskscheduling.app.domain.model.TaskCategory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TasksFilterState(
    val searchQuery: String = "",
    val selectedCategory: TaskCategory = TaskCategory.ALL,
    val showOnlyFavorites: Boolean = false
)

class TasksViewModel(
    private val taskRepository: TaskRepository
) : ViewModel() {

    private val _filterState = MutableStateFlow(TasksFilterState())
    val filterState: StateFlow<TasksFilterState> = _filterState.asStateFlow()

    private val allTasks = taskRepository.getTasks()

    val filteredTasks: StateFlow<List<Task>> = combine(allTasks, _filterState) { list, filter ->
        list.filter { task ->
            val matchesCategory = filter.selectedCategory == TaskCategory.ALL || task.category == filter.selectedCategory
            val matchesSearch = filter.searchQuery.isBlank() ||
                    task.title.contains(filter.searchQuery, ignoreCase = true) ||
                    task.description.contains(filter.searchQuery, ignoreCase = true)
            val matchesFavorite = !filter.showOnlyFavorites || task.isFavorite

            matchesCategory && matchesSearch && matchesFavorite
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onSearchChange(query: String) {
        _filterState.value = _filterState.value.copy(searchQuery = query)
    }

    fun onCategorySelect(category: TaskCategory) {
        _filterState.value = _filterState.value.copy(selectedCategory = category)
    }

    fun toggleFavoritesOnly() {
        _filterState.value = _filterState.value.copy(showOnlyFavorites = !_filterState.value.showOnlyFavorites)
    }

    fun toggleFavorite(taskId: String) {
        viewModelScope.launch { taskRepository.toggleFavorite(taskId) }
    }

    fun duplicateTask(taskId: String) {
        viewModelScope.launch { taskRepository.duplicateTask(taskId) }
    }

    fun deleteTask(taskId: String) {
        viewModelScope.launch { taskRepository.deleteTask(taskId) }
    }
}
