package com.taskscheduling.app.data.repository

import com.taskscheduling.app.data.mock.MockDataProvider
import com.taskscheduling.app.domain.model.AccessibilitySettings
import com.taskscheduling.app.domain.model.ActivityRecord
import com.taskscheduling.app.domain.model.AppSettings
import com.taskscheduling.app.domain.model.ChildProfile
import com.taskscheduling.app.domain.model.ScheduleItem
import com.taskscheduling.app.domain.model.Task
import com.taskscheduling.app.domain.model.TaskCategory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

interface TaskRepository {
    fun getTasks(): Flow<List<Task>>
    suspend fun getTaskById(id: String): Task?
    suspend fun saveTask(task: Task)
    suspend fun deleteTask(id: String)
    suspend fun duplicateTask(id: String)
    suspend fun toggleFavorite(id: String)
    suspend fun toggleEnabled(id: String)
    fun findTaskByVoiceIntent(command: String): Task?
}

class TaskRepositoryImpl(
    private val workflowPersistence: WorkflowPersistenceRepository? = null
) : TaskRepository {
    private val _tasks = MutableStateFlow(
        MockDataProvider.tasks.map { task ->
            val persisted = workflowPersistence?.getWorkflow(task.workflow.id)
            if (persisted != null) {
                task.copy(workflow = persisted)
            } else {
                task
            }
        }
    )

    override fun getTasks(): Flow<List<Task>> = _tasks.asStateFlow()

    override suspend fun getTaskById(id: String): Task? {
        val task = _tasks.value.find { it.id == id } ?: return null
        val persistedWf = workflowPersistence?.getWorkflow(task.workflow.id)
        return if (persistedWf != null) task.copy(workflow = persistedWf) else task
    }

    override suspend fun saveTask(task: Task) {
        workflowPersistence?.saveWorkflow(task.workflow)
        _tasks.update { list ->
            val idx = list.indexOfFirst { it.id == task.id }
            if (idx >= 0) {
                list.toMutableList().apply { set(idx, task) }
            } else {
                listOf(task) + list
            }
        }
    }

    override suspend fun deleteTask(id: String) {
        _tasks.update { list -> list.filterNot { it.id == id } }
    }

    override suspend fun duplicateTask(id: String) {
        val original = _tasks.value.find { it.id == id } ?: return
        val duplicate = original.copy(
            id = "task_${System.currentTimeMillis()}",
            title = "${original.title} (Copy)",
            isFavorite = false
        )
        saveTask(duplicate)
    }

    override suspend fun toggleFavorite(id: String) {
        _tasks.update { list ->
            list.map { if (it.id == id) it.copy(isFavorite = !it.isFavorite) else it }
        }
    }

    override suspend fun toggleEnabled(id: String) {
        _tasks.update { list ->
            list.map { if (it.id == id) it.copy(isEnabled = !it.isEnabled) else it }
        }
    }

    override fun findTaskByVoiceIntent(command: String): Task? {
        val clean = command.lowercase().trim()
        val all = _tasks.value

        // Direct trigger match
        all.find { task -> task.voiceTriggers.any { clean.contains(it.lowercase()) } }?.let { return it }

        // Title match
        all.find { clean.contains(it.title.lowercase()) || it.title.lowercase().contains(clean) }?.let { return it }

        // Fuzzy keywords
        if (clean.contains("wallpaper") || clean.contains("background")) return all.find { it.id == "task_wallpaper" }
        if (clean.contains("dark") || clean.contains("night")) return all.find { it.id == "task_dark_mode" }
        if (clean.contains("font") || clean.contains("text") || clean.contains("bigger")) return all.find { it.id == "task_text_size" }
        if (clean.contains("wifi") || clean.contains("internet")) return all.find { it.id == "task_wifi" }
        if (clean.contains("bluetooth")) return all.find { it.id == "task_bluetooth" }
        if (clean.contains("call") || clean.contains("mom")) return all.find { it.id == "task_call_mom" }
        if (clean.contains("study")) return all.find { it.id == "task_study" }

        return null
    }
}

interface SettingsRepository {
    fun getAppSettings(): Flow<AppSettings>
    suspend fun updateAppSettings(settings: AppSettings)
    fun getAccessibilitySettings(): Flow<AccessibilitySettings>
    suspend fun updateAccessibilitySettings(settings: AccessibilitySettings)
}

class SettingsRepositoryImpl : SettingsRepository {
    private val _appSettings = MutableStateFlow(MockDataProvider.defaultAppSettings)
    private val _accSettings = MutableStateFlow(MockDataProvider.defaultAccessibilitySettings)

    override fun getAppSettings(): Flow<AppSettings> = _appSettings.asStateFlow()
    override suspend fun updateAppSettings(settings: AppSettings) {
        _appSettings.value = settings
    }

    override fun getAccessibilitySettings(): Flow<AccessibilitySettings> = _accSettings.asStateFlow()
    override suspend fun updateAccessibilitySettings(settings: AccessibilitySettings) {
        _accSettings.value = settings
    }
}

interface ActivityRepository {
    fun getActivities(): Flow<List<ActivityRecord>>
    suspend fun addActivity(record: ActivityRecord)
}

class ActivityRepositoryImpl : ActivityRepository {
    private val _activities = MutableStateFlow(MockDataProvider.activities)

    override fun getActivities(): Flow<List<ActivityRecord>> = _activities.asStateFlow()
    override suspend fun addActivity(record: ActivityRecord) {
        _activities.update { listOf(record) + it }
    }
}

interface FamilyRepository {
    fun getChildProfile(): Flow<ChildProfile>
    suspend fun updateChildProfile(profile: ChildProfile)
    fun getSchedules(): Flow<List<ScheduleItem>>
    suspend fun toggleSchedule(id: String)
    suspend fun addSchedule(item: ScheduleItem)
}

class FamilyRepositoryImpl : FamilyRepository {
    private val _profile = MutableStateFlow(MockDataProvider.childProfile)
    private val _schedules = MutableStateFlow(MockDataProvider.schedules)

    override fun getChildProfile(): Flow<ChildProfile> = _profile.asStateFlow()
    override suspend fun updateChildProfile(profile: ChildProfile) {
        _profile.value = profile
    }

    override fun getSchedules(): Flow<List<ScheduleItem>> = _schedules.asStateFlow()
    override suspend fun toggleSchedule(id: String) {
        _schedules.update { list ->
            list.map { if (it.id == id) it.copy(isEnabled = !it.isEnabled) else it }
        }
    }

    override suspend fun addSchedule(item: ScheduleItem) {
        _schedules.update { listOf(item) + it }
    }
}
