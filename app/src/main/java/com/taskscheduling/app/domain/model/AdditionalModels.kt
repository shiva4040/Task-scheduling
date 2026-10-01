package com.taskscheduling.app.domain.model

enum class ActivityStatus(val displayName: String) {
    COMPLETED("Completed"),
    FAILED("Failed"),
    STOPPED("Stopped"),
    PAUSED("Paused")
}

data class ActivityRecord(
    val id: String,
    val taskTitle: String,
    val timestamp: String,
    val durationText: String,
    val status: ActivityStatus,
    val stepCount: Int,
    val logs: List<ExecutionLogEntry> = emptyList()
)

data class ScheduleWindow(
    val name: String,
    val startTime: String,
    val endTime: String
)

data class ChildProfile(
    val id: String,
    val name: String,
    val age: Int,
    val avatarEmoji: String = "👦",
    val screenTimeLimitMinutes: Int = 120,
    val gamingTimeLimitMinutes: Int = 60,
    val studyModeActive: Boolean = false,
    val schedules: List<ScheduleWindow> = emptyList(),
    val allowedApps: List<String> = emptyList(),
    val blockedApps: List<String> = emptyList()
)

enum class RepeatOption(val displayName: String) {
    ONCE("Once"),
    DAILY("Every Day"),
    WEEKDAYS("Weekdays"),
    WEEKENDS("Weekends"),
    CUSTOM("Custom")
}

data class ScheduleItem(
    val id: String,
    val taskTitle: String,
    val taskId: String,
    val time: String, // e.g. "10:00 PM"
    val repeatOption: RepeatOption,
    val isEnabled: Boolean = true
)

data class AccessibilitySettings(
    val largeText: Boolean = false,
    val largeControls: Boolean = false,
    val highContrast: Boolean = false,
    val reduceMotion: Boolean = false,
    val voiceFeedback: Boolean = true,
    val voiceConfirmation: Boolean = true,
    val showTouches: Boolean = true,
    val showCoordinates: Boolean = true,
    val showExecutionPath: Boolean = true,
    val stepByStepMode: Boolean = false
)

enum class ThemeMode {
    SYSTEM, LIGHT, DARK
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val executionSpeed: Float = 1.0f,
    val confirmBeforeExecution: Boolean = false,
    val stopOnError: Boolean = true,
    val notificationsEnabled: Boolean = true,
    val language: String = "English (US)"
)

data class UseCaseItem(
    val id: String,
    val title: String,
    val description: String,
    val category: String,
    val iconName: String,
    val associatedTaskId: String
)
