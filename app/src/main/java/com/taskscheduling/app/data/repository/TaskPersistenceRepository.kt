package com.taskscheduling.app.data.repository

import android.content.Context
import com.taskscheduling.app.data.mock.MockDataProvider
import com.taskscheduling.app.domain.model.ChecklistItem
import com.taskscheduling.app.domain.model.Task
import com.taskscheduling.app.domain.model.TaskCategory
import com.taskscheduling.app.domain.model.TaskPriority
import com.taskscheduling.app.domain.model.TaskRecurrence
import com.taskscheduling.app.domain.model.TaskStatus
import com.taskscheduling.app.domain.model.TaskType
import com.taskscheduling.app.domain.model.Workflow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Robust JSON-based persistent repository for Tasks.
 * Implements atomic file writes, Mutex synchronization, and fault-tolerant JSON parsing.
 */
class TaskPersistenceRepository(
    private val baseDir: File,
    private val workflowPersistence: WorkflowPersistenceRepository? = null
) {
    constructor(context: Context, workflowPersistence: WorkflowPersistenceRepository? = null) :
            this(context.filesDir, workflowPersistence)

    private val tasksFile = File(baseDir, "tasks_storage.json")
    private val mutex = Mutex()

    private val _tasks = MutableStateFlow<List<Task>>(emptyList())
    val tasks: StateFlow<List<Task>> = _tasks.asStateFlow()

    init {
        loadAllTasks()
    }

    private fun loadAllTasks() {
        if (!tasksFile.exists()) {
            // First time initialization with mock tasks
            val initial = MockDataProvider.tasks.map { task ->
                val persistedWf = workflowPersistence?.getWorkflow(task.workflow.id)
                if (persistedWf != null) task.copy(workflow = persistedWf) else task
            }
            _tasks.value = initial
            persistTasks(initial)
            return
        }

        try {
            val jsonStr = tasksFile.readText()
            if (jsonStr.isBlank()) {
                _tasks.value = MockDataProvider.tasks
                return
            }
            val jsonArray = JSONArray(jsonStr)
            val list = mutableListOf<Task>()
            for (i in 0 until jsonArray.length()) {
                try {
                    val obj = jsonArray.getJSONObject(i)
                    val task = jsonToTask(obj)
                    val persistedWf = workflowPersistence?.getWorkflow(task.workflow.id)
                    list.add(if (persistedWf != null) task.copy(workflow = persistedWf) else task)
                } catch (e: Exception) {
                    // Corrupted individual task: skip and preserve remaining tasks
                    e.printStackTrace()
                }
            }
            _tasks.value = if (list.isNotEmpty()) list else MockDataProvider.tasks
        } catch (e: Exception) {
            e.printStackTrace()
            _tasks.value = MockDataProvider.tasks
        }
    }

    suspend fun saveTask(task: Task): Task = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (task.workflow.steps.isNotEmpty() && task.workflow.id.isNotBlank()) {
                workflowPersistence?.saveWorkflow(task.workflow)
            }
            val current = _tasks.value.toMutableList()
            val index = current.indexOfFirst { it.id == task.id }
            val updated = if (index >= 0) {
                current.apply { set(index, task.copy(updatedAt = System.currentTimeMillis())) }
            } else {
                listOf(task) + current
            }
            _tasks.value = updated
            persistTasks(updated)
            task
        }
    }

    suspend fun getTaskById(id: String): Task? = withContext(Dispatchers.IO) {
        val task = _tasks.value.find { it.id == id } ?: return@withContext null
        val persistedWf = workflowPersistence?.getWorkflow(task.workflow.id)
        if (persistedWf != null) task.copy(workflow = persistedWf) else task
    }

    suspend fun deleteTask(id: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val updated = _tasks.value.filterNot { it.id == id }
            _tasks.value = updated
            persistTasks(updated)
        }
    }

    suspend fun toggleFavorite(id: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val updated = _tasks.value.map {
                if (it.id == id) it.copy(isFavorite = !it.isFavorite, updatedAt = System.currentTimeMillis()) else it
            }
            _tasks.value = updated
            persistTasks(updated)
        }
    }

    suspend fun toggleEnabled(id: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val updated = _tasks.value.map {
                if (it.id == id) it.copy(isEnabled = !it.isEnabled, updatedAt = System.currentTimeMillis()) else it
            }
            _tasks.value = updated
            persistTasks(updated)
        }
    }

    suspend fun toggleCompleted(id: String): Task? = withContext(Dispatchers.IO) {
        mutex.withLock {
            var result: Task? = null
            val updated = _tasks.value.map {
                if (it.id == id) {
                    val newStatus = if (it.status == TaskStatus.COMPLETED) TaskStatus.TODO else TaskStatus.COMPLETED
                    val completedAt = if (newStatus == TaskStatus.COMPLETED) System.currentTimeMillis() else null
                    val updatedTask = it.copy(
                        status = newStatus,
                        completedAt = completedAt,
                        updatedAt = System.currentTimeMillis()
                    )
                    result = updatedTask
                    updatedTask
                } else {
                    it
                }
            }
            _tasks.value = updated
            persistTasks(updated)
            result
        }
    }

    suspend fun duplicateTask(id: String): Task? = withContext(Dispatchers.IO) {
        val original = _tasks.value.find { it.id == id } ?: return@withContext null
        val newId = UUID.randomUUID().toString()
        val duplicate = original.copy(
            id = newId,
            title = "${original.title} (Copy)",
            isFavorite = false,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            status = TaskStatus.TODO,
            completedAt = null
        )
        saveTask(duplicate)
    }

    suspend fun rescheduleTask(id: String, newDueDate: String?, newDueTime: String?): Task? = withContext(Dispatchers.IO) {
        mutex.withLock {
            var updatedTask: Task? = null
            val updated = _tasks.value.map {
                if (it.id == id) {
                    val t = it.copy(
                        dueDate = newDueDate,
                        dueTime = newDueTime,
                        hasDueTime = newDueTime != null,
                        updatedAt = System.currentTimeMillis()
                    )
                    updatedTask = t
                    t
                } else {
                    it
                }
            }
            _tasks.value = updated
            persistTasks(updated)
            updatedTask
        }
    }

    private fun persistTasks(list: List<Task>) {
        try {
            val jsonArray = JSONArray()
            for (t in list) {
                jsonArray.put(taskToJson(t))
            }
            atomicWriteFile(tasksFile, jsonArray.toString(2))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun atomicWriteFile(targetFile: File, content: String) {
        val parent = targetFile.parentFile ?: return
        if (!parent.exists()) parent.mkdirs()
        val tempFile = File(parent, "${targetFile.name}.tmp")
        tempFile.writeText(content)
        if (targetFile.exists()) {
            targetFile.delete()
        }
        tempFile.renameTo(targetFile)
    }

    companion object {
        fun taskToJson(task: Task): JSONObject {
            return JSONObject().apply {
                put("id", task.id)
                put("title", task.title)
                put("description", task.description)
                if (task.dueDate != null) put("dueDate", task.dueDate)
                if (task.dueTime != null) put("dueTime", task.dueTime)
                put("hasDueTime", task.hasDueTime)
                if (task.reminderTimestamp != null) put("reminderTimestamp", task.reminderTimestamp)
                put("hasReminder", task.hasReminder)
                if (task.reminderOffsetMinutes != null) put("reminderOffsetMinutes", task.reminderOffsetMinutes)
                put("priority", task.priority.name)
                put("status", task.status.name)
                put("category", task.category.name)
                if (task.customCategoryName != null) put("customCategoryName", task.customCategoryName)
                put("type", task.type.name)
                put("iconName", task.iconName)
                put("isFavorite", task.isFavorite)
                put("isEnabled", task.isEnabled)
                put("createdAt", task.createdAt)
                put("updatedAt", task.updatedAt)
                if (task.completedAt != null) put("completedAt", task.completedAt)
                put("recurrence", task.recurrence.name)
                put("notes", task.notes)
                put("notificationEnabled", task.notificationEnabled)

                val triggersArr = JSONArray()
                task.voiceTriggers.forEach { triggersArr.put(it) }
                put("voiceTriggers", triggersArr)

                val checklistArr = JSONArray()
                task.checklist.forEach { item ->
                    checklistArr.put(JSONObject().apply {
                        put("id", item.id)
                        put("text", item.text)
                        put("isCompleted", item.isCompleted)
                    })
                }
                put("checklist", checklistArr)

                put("workflow", WorkflowPersistenceRepository.workflowToJson(task.workflow))
            }
        }

        fun jsonToTask(obj: JSONObject): Task {
            val id = obj.optString("id", UUID.randomUUID().toString())
            val title = obj.optString("title", "Untitled Task")
            val description = obj.optString("description", "")
            val dueDate = if (obj.has("dueDate")) obj.getString("dueDate") else null
            val dueTime = if (obj.has("dueTime")) obj.getString("dueTime") else null
            val hasDueTime = obj.optBoolean("hasDueTime", dueTime != null)
            val reminderTimestamp = if (obj.has("reminderTimestamp")) obj.getLong("reminderTimestamp") else null
            val hasReminder = obj.optBoolean("hasReminder", reminderTimestamp != null)
            val reminderOffsetMinutes = if (obj.has("reminderOffsetMinutes")) obj.getInt("reminderOffsetMinutes") else null

            val priorityStr = obj.optString("priority", "MEDIUM")
            val priority = try { TaskPriority.valueOf(priorityStr) } catch (_: Exception) { TaskPriority.MEDIUM }

            val statusStr = obj.optString("status", "TODO")
            val status = try { TaskStatus.valueOf(statusStr) } catch (_: Exception) { TaskStatus.TODO }

            val catStr = obj.optString("category", "CUSTOM")
            val category = try { TaskCategory.valueOf(catStr) } catch (_: Exception) { TaskCategory.CUSTOM }
            val customCategoryName = if (obj.has("customCategoryName")) obj.getString("customCategoryName") else null

            val typeStr = obj.optString("type", "COORDINATE_WORKFLOW")
            val type = try { TaskType.valueOf(typeStr) } catch (_: Exception) { TaskType.COORDINATE_WORKFLOW }

            val iconName = obj.optString("iconName", "settings")
            val isFavorite = obj.optBoolean("isFavorite", false)
            val isEnabled = obj.optBoolean("isEnabled", true)
            val createdAt = obj.optLong("createdAt", System.currentTimeMillis())
            val updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
            val completedAt = if (obj.has("completedAt")) obj.getLong("completedAt") else null

            val recStr = obj.optString("recurrence", "NONE")
            val recurrence = try { TaskRecurrence.valueOf(recStr) } catch (_: Exception) { TaskRecurrence.NONE }

            val notes = obj.optString("notes", "")
            val notificationEnabled = obj.optBoolean("notificationEnabled", true)

            val triggersList = mutableListOf<String>()
            val triggersArr = obj.optJSONArray("voiceTriggers")
            if (triggersArr != null) {
                for (i in 0 until triggersArr.length()) {
                    triggersList.add(triggersArr.getString(i))
                }
            }

            val checklistList = mutableListOf<ChecklistItem>()
            val checklistArr = obj.optJSONArray("checklist")
            if (checklistArr != null) {
                for (i in 0 until checklistArr.length()) {
                    val cObj = checklistArr.getJSONObject(i)
                    checklistList.add(
                        ChecklistItem(
                            id = cObj.optString("id", UUID.randomUUID().toString()),
                            text = cObj.optString("text", ""),
                            isCompleted = cObj.optBoolean("isCompleted", false)
                        )
                    )
                }
            }

            val wfObj = obj.optJSONObject("workflow")
            val workflow = if (wfObj != null) {
                WorkflowPersistenceRepository.jsonToWorkflow(wfObj)
            } else {
                Workflow(id = "wf_empty", name = title)
            }

            return Task(
                id = id,
                title = title,
                description = description,
                dueDate = dueDate,
                dueTime = dueTime,
                hasDueTime = hasDueTime,
                reminderTimestamp = reminderTimestamp,
                hasReminder = hasReminder,
                reminderOffsetMinutes = reminderOffsetMinutes,
                priority = priority,
                status = status,
                category = category,
                customCategoryName = customCategoryName,
                type = type,
                iconName = iconName,
                voiceTriggers = triggersList,
                isFavorite = isFavorite,
                isEnabled = isEnabled,
                createdAt = createdAt,
                updatedAt = updatedAt,
                completedAt = completedAt,
                recurrence = recurrence,
                notes = notes,
                checklist = checklistList,
                notificationEnabled = notificationEnabled,
                workflow = workflow
            )
        }
    }
}
