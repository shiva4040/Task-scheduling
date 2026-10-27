package com.taskscheduling.app.domain.model

import java.util.UUID

enum class TaskCategory(val displayName: String) {
    ALL("All"),
    STUDY("Study"),
    WORK("Work"),
    PERSONAL("Personal"),
    SHOPPING("Shopping"),
    HEALTH("Health"),
    FAMILY("Family"),
    SETTINGS("Settings"),
    ACCESSIBILITY("Accessibility"),
    COMMUNICATION("Calling"),
    PRODUCTIVITY("Productivity"),
    CUSTOM("Custom")
}

enum class TaskType(val displayName: String) {
    STANDARD_TASK("Standard Task"),
    COORDINATE_WORKFLOW("Coordinate Workflow"),
    SCHEDULED_TASK("Scheduled Task"),
    VOICE_COMMAND("Voice Command"),
    QUICK_ACTION("Quick Action")
}

enum class TaskPriority(val displayName: String, val level: Int) {
    LOW("Low", 1),
    MEDIUM("Medium", 2),
    HIGH("High", 3),
    URGENT("Urgent", 4)
}

enum class TaskStatus(val displayName: String) {
    TODO("To Do"),
    IN_PROGRESS("In Progress"),
    COMPLETED("Completed")
}

enum class TaskRecurrence(val displayName: String) {
    NONE("None"),
    DAILY("Daily"),
    WEEKDAYS("Weekdays"),
    WEEKLY("Weekly"),
    MONTHLY("Monthly")
}

data class ChecklistItem(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val isCompleted: Boolean = false
)

data class Task(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val description: String = "",
    val dueDate: String? = null,             // Format: "YYYY-MM-DD" or text like "Today", "Tomorrow"
    val dueTime: String? = null,             // Format: "19:00" or "07:00 PM"
    val hasDueTime: Boolean = dueTime != null,
    val reminderTimestamp: Long? = null,     // Epoch ms for notification alarm
    val hasReminder: Boolean = reminderTimestamp != null,
    val reminderOffsetMinutes: Int? = null,  // 0 (at due time), 5, 10, 15, 30, 60, etc.
    val priority: TaskPriority = TaskPriority.MEDIUM,
    val status: TaskStatus = TaskStatus.TODO,
    val category: TaskCategory = TaskCategory.CUSTOM,
    val customCategoryName: String? = null,
    val type: TaskType = TaskType.COORDINATE_WORKFLOW,
    val iconName: String = "settings",
    val voiceTriggers: List<String> = emptyList(),
    val isFavorite: Boolean = false,
    val isEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val recurrence: TaskRecurrence = TaskRecurrence.NONE,
    val notes: String = "",
    val checklist: List<ChecklistItem> = emptyList(),
    val notificationEnabled: Boolean = true,
    val workflow: Workflow = Workflow(id = "wf_empty", name = "Default Workflow")
) {
    val isCompleted: Boolean
        get() = status == TaskStatus.COMPLETED
}

data class TaskDraft(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val description: String = "",
    val dueDate: String? = null,
    val dueTime: String? = null,
    val hasDueTime: Boolean = dueTime != null,
    val reminderOffsetMinutes: Int? = null,
    val reminderTimestamp: Long? = null,
    val hasReminder: Boolean = reminderOffsetMinutes != null || reminderTimestamp != null,
    val priority: TaskPriority = TaskPriority.MEDIUM,
    val category: TaskCategory = TaskCategory.CUSTOM,
    val customCategoryName: String? = null,
    val recurrence: TaskRecurrence = TaskRecurrence.NONE,
    val notes: String = "",
    val checklist: List<ChecklistItem> = emptyList()
) {
    fun toTask(): Task {
        val now = System.currentTimeMillis()
        return Task(
            id = id,
            title = title.trim(),
            description = description.trim(),
            dueDate = dueDate,
            dueTime = if (hasDueTime) dueTime else null,
            hasDueTime = hasDueTime && dueTime != null,
            reminderTimestamp = if (hasReminder) reminderTimestamp else null,
            hasReminder = hasReminder && (reminderOffsetMinutes != null || reminderTimestamp != null),
            reminderOffsetMinutes = if (hasReminder) reminderOffsetMinutes else null,
            priority = priority,
            status = TaskStatus.TODO,
            category = category,
            customCategoryName = customCategoryName,
            type = TaskType.STANDARD_TASK,
            recurrence = recurrence,
            notes = notes.trim(),
            checklist = checklist,
            notificationEnabled = hasReminder,
            createdAt = now,
            updatedAt = now,
            completedAt = null
        )
    }
}
