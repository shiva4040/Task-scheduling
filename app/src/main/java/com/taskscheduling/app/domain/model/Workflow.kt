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
    TEXT_INPUT("Text Input"),
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
    // Backwards compatibility & expanded editor properties:
    val stepNumber: Int = order,
    val label: String = if (description.isNotBlank()) description else "Step $order",
    val actionType: ActionType = type,
    val coordinate: CoordinateAction? = CoordinateAction(startX, startY, endX, endY),
    val durationMs: Long = duration,
    val explanation: String = description,
    val targetScreen: String? = null,
    val targetElement: String? = null,
    val targetDescription: String? = null,
    val textInput: String? = null,
    val waitDurationMs: Long = waitAfter,
    val timeoutMs: Long = 5000L,
    val expectedScreenState: String? = null,
    val verificationRule: String = "NONE",
    val retryLimit: Int = 0,
    val recoveryBehavior: String = "STOP_ON_FAILURE",
    val isVerified: Boolean = false,
    val requiresPermission: Boolean = false
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

    /**
     * Checks if this step requires real physical screen coordinates.
     */
    val requiresCoordinates: Boolean
        get() = actionType in listOf(
            ActionType.TAP,
            ActionType.LONG_PRESS,
            ActionType.DOUBLE_TAP,
            ActionType.SWIPE,
            ActionType.DRAG
        )

    /**
     * Checks if coordinates have been configured and are non-zero.
     */
    val hasValidCoordinates: Boolean
        get() {
            if (!requiresCoordinates) return true
            val cx = coordinate?.x ?: startX
            val cy = coordinate?.y ?: startY
            if (cx <= 0 && cy <= 0) return false
            if (actionType == ActionType.SWIPE || actionType == ActionType.DRAG) {
                val ex = coordinate?.endX ?: endX ?: return false
                val ey = coordinate?.endY ?: endY ?: return false
                if (ex <= 0 && ey <= 0) return false
            }
            return true
        }

    /**
     * Helper to clone the step while ensuring all dual-represented properties stay in sync.
     */
    fun copyWithSync(
        label: String? = null,
        actionType: ActionType? = null,
        coordinate: CoordinateAction? = null,
        durationMs: Long? = null,
        targetScreen: String? = null,
        targetElement: String? = null,
        targetDescription: String? = null,
        textInput: String? = null,
        waitDurationMs: Long? = null,
        timeoutMs: Long? = null,
        expectedScreenState: String? = null,
        verificationRule: String? = null,
        retryLimit: Int? = null,
        recoveryBehavior: String? = null,
        isVerified: Boolean? = null,
        requiresPermission: Boolean? = null,
        stepNumber: Int? = null
    ): WorkflowStep {
        val newOrder = stepNumber ?: this.stepNumber
        val newActionType = actionType ?: this.actionType
        val newLabel = label ?: this.label
        val newCoord = coordinate ?: this.coordinate ?: CoordinateAction(this.startX, this.startY, this.endX, this.endY)
        val newDuration = durationMs ?: this.durationMs
        val newWait = waitDurationMs ?: this.waitDurationMs

        return this.copy(
            order = newOrder,
            type = newActionType,
            startX = newCoord.x,
            startY = newCoord.y,
            endX = newCoord.endX,
            endY = newCoord.endY,
            duration = newDuration,
            waitAfter = newWait,
            description = newLabel,
            stepNumber = newOrder,
            label = newLabel,
            actionType = newActionType,
            coordinate = newCoord,
            durationMs = newDuration,
            explanation = newLabel,
            targetScreen = targetScreen ?: this.targetScreen,
            targetElement = targetElement ?: this.targetElement,
            targetDescription = targetDescription ?: this.targetDescription,
            textInput = textInput ?: this.textInput,
            waitDurationMs = newWait,
            timeoutMs = timeoutMs ?: this.timeoutMs,
            expectedScreenState = expectedScreenState ?: this.expectedScreenState,
            verificationRule = verificationRule ?: this.verificationRule,
            retryLimit = retryLimit ?: this.retryLimit,
            recoveryBehavior = recoveryBehavior ?: this.recoveryBehavior,
            isVerified = isVerified ?: this.isVerified,
            requiresPermission = requiresPermission ?: this.requiresPermission
        )
    }
}

enum class SessionState {
    IDLE,
    RECORDING,
    PAUSED,
    FINISHING,
    REVIEW,
    SAVED,
    CANCELLED,
    ERROR
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
