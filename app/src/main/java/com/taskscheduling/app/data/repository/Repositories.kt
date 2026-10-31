package com.taskscheduling.app.data.repository

import com.taskscheduling.app.data.mock.MockDataProvider
import com.taskscheduling.app.domain.model.AccessibilitySettings
import com.taskscheduling.app.domain.model.ActivityRecord
import com.taskscheduling.app.domain.model.AppSettings
import com.taskscheduling.app.domain.model.ChildProfile
import com.taskscheduling.app.domain.model.ScheduleItem
import com.taskscheduling.app.domain.model.Task
import com.taskscheduling.app.domain.model.TaskCategory
import com.taskscheduling.app.domain.model.TaskStatus
import com.taskscheduling.app.domain.model.TaskType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.util.UUID

interface TaskRepository {
    fun getTasks(): Flow<List<Task>>
    suspend fun getTaskById(id: String): Task?
    suspend fun saveTask(task: Task)
    suspend fun deleteTask(id: String)
    suspend fun duplicateTask(id: String)
    suspend fun toggleFavorite(id: String)
    suspend fun toggleEnabled(id: String)
    suspend fun toggleCompleted(id: String): Task?
    suspend fun rescheduleTask(id: String, newDueDate: String?, newDueTime: String?): Task?
    fun findTaskByVoiceIntent(command: String): Task?
    fun findTasksByQuery(query: String): List<Task>
}

class TaskRepositoryImpl(
    private val workflowPersistence: WorkflowPersistenceRepository? = null,
    private val taskPersistence: TaskPersistenceRepository? = null
) : TaskRepository {
    private val _tasks = MutableStateFlow(
        taskPersistence?.tasks?.value ?: MockDataProvider.tasks.map { task ->
            val persisted = workflowPersistence?.getWorkflow(task.workflow.id)
            if (persisted != null) {
                task.copy(workflow = persisted)
            } else {
                task
            }
        }
    )

    override fun getTasks(): Flow<List<Task>> = taskPersistence?.tasks ?: _tasks.asStateFlow()

    override suspend fun getTaskById(id: String): Task? {
        if (taskPersistence != null) {
            val fromPersistence = taskPersistence.getTaskById(id)
            if (fromPersistence != null) return fromPersistence
        }
        val task = _tasks.value.find { it.id == id || (id != "wf_empty" && it.workflow.id == id) }
        if (task != null) {
            val persistedWf = workflowPersistence?.getWorkflow(task.workflow.id)
            return if (persistedWf != null) task.copy(workflow = persistedWf) else task
        }
        val wf = workflowPersistence?.getWorkflow(id)
        if (wf != null) {
            return Task(
                id = wf.id,
                title = wf.name,
                description = wf.description,
                category = TaskCategory.CUSTOM,
                type = TaskType.COORDINATE_WORKFLOW,
                workflow = wf
            )
        }
        return null
    }

    override suspend fun saveTask(task: Task) {
        if (taskPersistence != null) {
            taskPersistence.saveTask(task)
            _tasks.value = taskPersistence.tasks.value
            return
        }
        if (task.workflow.id.isNotBlank() && task.workflow.id != "wf_empty") {
            workflowPersistence?.saveWorkflow(task.workflow)
        }
        _tasks.update { list ->
            val idx = list.indexOfFirst { it.id == task.id }
            if (idx >= 0) {
                list.toMutableList().apply { set(idx, task.copy(updatedAt = System.currentTimeMillis())) }
            } else {
                listOf(task) + list
            }
        }
    }

    override suspend fun deleteTask(id: String) {
        if (taskPersistence != null) {
            taskPersistence.deleteTask(id)
            _tasks.value = taskPersistence.tasks.value
            return
        }
        _tasks.update { list -> list.filterNot { it.id == id } }
    }

    override suspend fun duplicateTask(id: String) {
        if (taskPersistence != null) {
            taskPersistence.duplicateTask(id)
            _tasks.value = taskPersistence.tasks.value
            return
        }
        val original = _tasks.value.find { it.id == id } ?: return
        val duplicate = original.copy(
            id = UUID.randomUUID().toString(),
            title = "${original.title} (Copy)",
            isFavorite = false,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        saveTask(duplicate)
    }

    override suspend fun toggleFavorite(id: String) {
        if (taskPersistence != null) {
            taskPersistence.toggleFavorite(id)
            _tasks.value = taskPersistence.tasks.value
            return
        }
        _tasks.update { list ->
            list.map { if (it.id == id) it.copy(isFavorite = !it.isFavorite) else it }
        }
    }

    override suspend fun toggleEnabled(id: String) {
        if (taskPersistence != null) {
            taskPersistence.toggleEnabled(id)
            _tasks.value = taskPersistence.tasks.value
            return
        }
        _tasks.update { list ->
            list.map { if (it.id == id) it.copy(isEnabled = !it.isEnabled) else it }
        }
    }

    override suspend fun toggleCompleted(id: String): Task? {
        if (taskPersistence != null) {
            val res = taskPersistence.toggleCompleted(id)
            _tasks.value = taskPersistence.tasks.value
            return res
        }
        var result: Task? = null
        _tasks.update { list ->
            list.map {
                if (it.id == id) {
                    val newStatus = if (it.status == TaskStatus.COMPLETED) TaskStatus.TODO else TaskStatus.COMPLETED
                    val comp = if (newStatus == TaskStatus.COMPLETED) System.currentTimeMillis() else null
                    val t = it.copy(status = newStatus, completedAt = comp, updatedAt = System.currentTimeMillis())
                    result = t
                    t
                } else {
                    it
                }
            }
        }
        return result
    }

    override suspend fun rescheduleTask(id: String, newDueDate: String?, newDueTime: String?): Task? {
        if (taskPersistence != null) {
            val res = taskPersistence.rescheduleTask(id, newDueDate, newDueTime)
            _tasks.value = taskPersistence.tasks.value
            return res
        }
        var result: Task? = null
        _tasks.update { list ->
            list.map {
                if (it.id == id) {
                    val t = it.copy(dueDate = newDueDate, dueTime = newDueTime, hasDueTime = newDueTime != null, updatedAt = System.currentTimeMillis())
                    result = t
                    t
                } else {
                    it
                }
            }
        }
        return result
    }

    override fun findTaskByVoiceIntent(command: String): Task? {
        val clean = command.lowercase().trim()
        val all = _tasks.value

        // Direct trigger match
        all.find { task -> task.voiceTriggers.any { clean.contains(it.lowercase()) } }?.let { return it }

        // Title match
        all.find { clean.contains(it.title.lowercase()) || it.title.lowercase().contains(clean) }?.let { return it }

        // Fuzzy keywords
        if (clean.contains("dark") || clean.contains("night")) return all.find { it.id == "task_dark_mode" }
        if (clean.contains("font") || clean.contains("text") || clean.contains("bigger")) return all.find { it.id == "task_text_size" }
        if (clean.contains("wifi") || clean.contains("internet")) return all.find { it.id == "task_wifi" }
        if (clean.contains("bluetooth")) return all.find { it.id == "task_bluetooth" }
        if (clean.contains("call") || clean.contains("mom")) return all.find { it.id == "task_call_mom" }
        if (clean.contains("study")) return all.find { it.id == "task_study" }

        return null
    }

    override fun findTasksByQuery(query: String): List<Task> {
        val clean = query.lowercase().trim()
        if (clean.isBlank()) return emptyList()
        return _tasks.value.filter { task ->
            task.title.lowercase().contains(clean) ||
                    task.description.lowercase().contains(clean) ||
                    task.notes.lowercase().contains(clean) ||
                    task.category.displayName.lowercase().contains(clean) ||
                    task.voiceTriggers.any { it.lowercase().contains(clean) } ||
                    task.id.lowercase().contains(clean)
        }
    }
}

interface SettingsRepository {
    fun getAppSettings(): Flow<AppSettings>
    suspend fun updateAppSettings(settings: AppSettings)
    fun getAccessibilitySettings(): Flow<AccessibilitySettings>
    suspend fun updateAccessibilitySettings(settings: AccessibilitySettings)
}

class SettingsRepositoryImpl(context: android.content.Context? = null) : SettingsRepository {
    private val prefs: android.content.SharedPreferences? = context?.applicationContext?.getSharedPreferences(
        "app_settings_prefs",
        android.content.Context.MODE_PRIVATE
    )

    private val _appSettings = MutableStateFlow(loadAppSettings())
    private val _accSettings = MutableStateFlow(MockDataProvider.defaultAccessibilitySettings)

    private fun loadAppSettings(): AppSettings {
        if (prefs == null) return MockDataProvider.defaultAppSettings
        val themeStr = prefs.getString("theme_mode", com.taskscheduling.app.domain.model.ThemeMode.LIGHT.name)
            ?: com.taskscheduling.app.domain.model.ThemeMode.LIGHT.name
        val theme = try {
            com.taskscheduling.app.domain.model.ThemeMode.valueOf(themeStr)
        } catch (_: Exception) {
            com.taskscheduling.app.domain.model.ThemeMode.LIGHT
        }
        val speed = prefs.getFloat("execution_speed", 1.0f)
        val confirm = prefs.getBoolean("confirm_before_execution", false)
        val stopOnError = prefs.getBoolean("stop_on_error", true)
        val notif = prefs.getBoolean("notifications_enabled", true)
        val lang = prefs.getString("language", "English (US)") ?: "English (US)"
        val engine = prefs.getString("speech_engine", "WHISPER_LARGE_V3_TURBO") ?: "WHISPER_LARGE_V3_TURBO"
        val accent = prefs.getString("accent_color", "Indigo") ?: "Indigo"
        val fontScale = prefs.getFloat("font_size_scale", 1.0f)
        val reducedMotion = prefs.getBoolean("reduced_motion", false)
        val overlayOrb = prefs.getBoolean("overlay_orb_enabled", true)
        val orbSize = prefs.getInt("floating_orb_size_dp", 56)
        val orbOpacity = prefs.getFloat("floating_orb_opacity", 0.95f)
        val haptic = prefs.getBoolean("haptic_feedback", true)
        val stepDelay = prefs.getLong("step_delay_ms", 500L)
        val stepTimeout = prefs.getInt("step_timeout_sec", 15)
        val autoClear = prefs.getBoolean("auto_clear_completed", false)
        val user = prefs.getString("user_name", "Shiva") ?: "Shiva"
        val email = prefs.getString("user_email", "shiva@taskscheduling.local") ?: "shiva@taskscheduling.local"

        return AppSettings(
            themeMode = theme,
            executionSpeed = speed,
            confirmBeforeExecution = confirm,
            stopOnError = stopOnError,
            notificationsEnabled = notif,
            language = lang,
            speechEngine = engine,
            accentColorName = accent,
            fontSizeScale = fontScale,
            reducedMotion = reducedMotion,
            overlayOrbEnabled = overlayOrb,
            floatingOrbSizeDp = orbSize,
            floatingOrbOpacity = orbOpacity,
            hapticFeedbackEnabled = haptic,
            stepDelayMs = stepDelay,
            stepTimeoutSeconds = stepTimeout,
            autoClearCompletedTasks = autoClear,
            userName = user,
            userEmail = email
        )
    }

    override fun getAppSettings(): Flow<AppSettings> = _appSettings.asStateFlow()

    override suspend fun updateAppSettings(settings: AppSettings) {
        _appSettings.value = settings
        prefs?.edit()?.apply {
            putString("theme_mode", settings.themeMode.name)
            putFloat("execution_speed", settings.executionSpeed)
            putBoolean("confirm_before_execution", settings.confirmBeforeExecution)
            putBoolean("stop_on_error", settings.stopOnError)
            putBoolean("notifications_enabled", settings.notificationsEnabled)
            putString("language", settings.language)
            putString("speech_engine", settings.speechEngine)
            putString("accent_color", settings.accentColorName)
            putFloat("font_size_scale", settings.fontSizeScale)
            putBoolean("reduced_motion", settings.reducedMotion)
            putBoolean("overlay_orb_enabled", settings.overlayOrbEnabled)
            putInt("floating_orb_size_dp", settings.floatingOrbSizeDp)
            putFloat("floating_orb_opacity", settings.floatingOrbOpacity)
            putBoolean("haptic_feedback", settings.hapticFeedbackEnabled)
            putLong("step_delay_ms", settings.stepDelayMs)
            putInt("step_timeout_sec", settings.stepTimeoutSeconds)
            putBoolean("auto_clear_completed", settings.autoClearCompletedTasks)
            putString("user_name", settings.userName)
            putString("user_email", settings.userEmail)
            apply()
        }
    }

    override fun getAccessibilitySettings(): Flow<AccessibilitySettings> = _accSettings.asStateFlow()
    override suspend fun updateAccessibilitySettings(settings: AccessibilitySettings) {
        _accSettings.value = settings
    }
}

interface ActivityRepository {
    fun getActivities(): Flow<List<ActivityRecord>>
    suspend fun addActivity(record: ActivityRecord)
    suspend fun clearActivities()
}

class ActivityRepositoryImpl : ActivityRepository {
    private val _activities = MutableStateFlow(MockDataProvider.activities)

    override fun getActivities(): Flow<List<ActivityRecord>> = _activities.asStateFlow()
    override suspend fun addActivity(record: ActivityRecord) {
        _activities.update { listOf(record) + it }
    }
    override suspend fun clearActivities() {
        _activities.value = emptyList()
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
