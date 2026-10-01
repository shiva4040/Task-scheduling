package com.taskscheduling.app.domain.model

import java.util.UUID

enum class WorkflowStatus(val displayName: String) {
    NOT_CALIBRATED("NOT CALIBRATED"),
    CALIBRATED("CALIBRATED")
}

enum class ActionType(val label: String) {
    TAP("Tap"),
    LONG_PRESS("Long Press"),
    SWIPE("Swipe"),
    WAIT("Wait"),
    BACK("Back"),
    DOUBLE_TAP("Double Tap"),
    DRAG("Drag"),
    HOME("Home"),
    RECENTS("Recents"),
    CUSTOM_GESTURE("Custom Gesture")
}

data class CoordinateAction(
    val x: Int = 0,
    val y: Int = 0,
    val endX: Int? = null,
    val endY: Int? = null
)

data class DeviceProfile(
    val manufacturer: String = "",
    val model: String = "",
    val androidVersion: String = "",
    val apiLevel: Int = 0,
    val screenWidth: Int = 0,
    val screenHeight: Int = 0,
    val density: Float = 0f,
    val orientation: String = "PORTRAIT" // "PORTRAIT" or "LANDSCAPE"
) {
    fun toDisplayString(): String {
        val brand = if (manufacturer.isNotBlank() && !model.startsWith(manufacturer, ignoreCase = true)) {
            "$manufacturer $model"
        } else {
            model.ifBlank { "Android Device" }
        }
        return "$brand (${screenWidth}x${screenHeight})"
    }
}

data class WorkflowStep(
    val id: String = UUID.randomUUID().toString(),
    val order: Int = 1,
    val type: ActionType = ActionType.TAP,
    val startX: Int = 0,
    val startY: Int = 0,
    val endX: Int? = null,
    val endY: Int? = null,
    val duration: Long = 100L,
    val waitAfter: Long = 500L,
    val description: String = "",
    // Backwards compatibility properties:
    val stepNumber: Int = order,
    val label: String = description,
    val actionType: ActionType = type,
    val coordinate: CoordinateAction? = CoordinateAction(startX, startY, endX, endY),
    val durationMs: Long = duration,
    val explanation: String = description,
    val targetScreen: String? = null
) {
    // Secondary constructor for existing codebase usages
    constructor(
        id: String,
        stepNumber: Int,
        label: String,
        actionType: ActionType,
        coordinate: CoordinateAction? = null,
        durationMs: Long = 400L,
        explanation: String = "",
        targetScreen: String? = null
    ) : this(
        id = id,
        order = stepNumber,
        type = actionType,
        startX = coordinate?.x ?: 0,
        startY = coordinate?.y ?: 0,
        endX = coordinate?.endX,
        endY = coordinate?.endY,
        duration = durationMs,
        waitAfter = 500L,
        description = explanation.ifEmpty { label },
        stepNumber = stepNumber,
        label = label,
        actionType = actionType,
        coordinate = coordinate ?: CoordinateAction(0, 0),
        durationMs = durationMs,
        explanation = explanation,
        targetScreen = targetScreen
    )
}

enum class SessionState {
    IDLE,
    RECORDING,
    PAUSED,
    REVIEW,
    SAVED
}

data class Workflow(
    val id: String,
    val name: String,
    val description: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val status: WorkflowStatus = WorkflowStatus.NOT_CALIBRATED,
    val deviceProfile: DeviceProfile? = null,
    val steps: List<WorkflowStep> = emptyList(),
    val schemaVersion: Int = 1
)
