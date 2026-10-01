package com.taskscheduling.app.domain.model

enum class TaskCategory(val displayName: String) {
    ALL("All"),
    SETTINGS("Settings"),
    ACCESSIBILITY("Accessibility"),
    FAMILY("Family"),
    COMMUNICATION("Calling"),
    PRODUCTIVITY("Productivity"),
    CUSTOM("Custom")
}

enum class TaskType(val displayName: String) {
    COORDINATE_WORKFLOW("Coordinate Workflow"),
    SCHEDULED_TASK("Scheduled Task"),
    VOICE_COMMAND("Voice Command"),
    QUICK_ACTION("Quick Action")
}

data class Task(
    val id: String,
    val title: String,
    val description: String,
    val category: TaskCategory,
    val type: TaskType = TaskType.COORDINATE_WORKFLOW,
    val iconName: String = "settings",
    val voiceTriggers: List<String> = emptyList(),
    val isFavorite: Boolean = false,
    val isEnabled: Boolean = true,
    val workflow: Workflow
)
