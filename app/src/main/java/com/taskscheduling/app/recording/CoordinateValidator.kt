package com.taskscheduling.app.recording

import com.taskscheduling.app.domain.model.ActionType
import com.taskscheduling.app.domain.model.DeviceProfile
import com.taskscheduling.app.domain.model.Workflow
import com.taskscheduling.app.domain.model.WorkflowStep

sealed class ValidationResult {
    object Valid : ValidationResult()
    data class Invalid(val reason: String) : ValidationResult()

    val isValid: Boolean get() = this is Valid
}

object CoordinateValidator {

    /**
     * Validates that coordinate (x, y) is strictly within [0, screenWidth) and [0, screenHeight).
     * Rejects invalid coordinates without silent clamping.
     */
    fun validateCoordinate(x: Int, y: Int, screenWidth: Int, screenHeight: Int): ValidationResult {
        if (screenWidth <= 0 || screenHeight <= 0) {
            return ValidationResult.Invalid("Screen dimensions are not available (width: $screenWidth, height: $screenHeight).")
        }
        if (x < 0 || x >= screenWidth) {
            return ValidationResult.Invalid("X coordinate ($x) is outside screen bounds [0, $screenWidth).")
        }
        if (y < 0 || y >= screenHeight) {
            return ValidationResult.Invalid("Y coordinate ($y) is outside screen bounds [0, $screenHeight).")
        }
        return ValidationResult.Valid
    }

    /**
     * Validates a single workflow step.
     */
    fun validateStep(step: WorkflowStep, screenWidth: Int, screenHeight: Int): ValidationResult {
        when (step.type) {
            ActionType.TAP, ActionType.DOUBLE_TAP, ActionType.LONG_PRESS -> {
                val pointCheck = validateCoordinate(step.startX, step.startY, screenWidth, screenHeight)
                if (!pointCheck.isValid) return pointCheck

                if (step.duration <= 0) {
                    return ValidationResult.Invalid("Step ${step.order}: duration must be greater than 0 ms (was ${step.duration} ms).")
                }
            }
            ActionType.SWIPE, ActionType.DRAG -> {
                val startCheck = validateCoordinate(step.startX, step.startY, screenWidth, screenHeight)
                if (!startCheck.isValid) return startCheck

                val endX = step.endX ?: return ValidationResult.Invalid("Step ${step.order}: swipe requires endX coordinate.")
                val endY = step.endY ?: return ValidationResult.Invalid("Step ${step.order}: swipe requires endY coordinate.")

                val endCheck = validateCoordinate(endX, endY, screenWidth, screenHeight)
                if (!endCheck.isValid) return endCheck

                if (step.duration < 50) {
                    return ValidationResult.Invalid("Step ${step.order}: swipe duration too short (${step.duration} ms, minimum 50 ms).")
                }
            }
            ActionType.WAIT -> {
                if (step.duration <= 0) {
                    return ValidationResult.Invalid("Step ${step.order}: wait duration must be greater than 0 ms (was ${step.duration} ms).")
                }
            }
            ActionType.BACK, ActionType.HOME, ActionType.RECENTS -> {
                // System actions don't require coordinates
            }
            ActionType.CUSTOM_GESTURE -> {
                if (step.duration <= 0) {
                    return ValidationResult.Invalid("Step ${step.order}: gesture duration must be greater than 0 ms.")
                }
            }
        }

        if (step.waitAfter < 0) {
            return ValidationResult.Invalid("Step ${step.order}: waitAfter cannot be negative.")
        }

        return ValidationResult.Valid
    }

    /**
     * Complete workflow validation for saving and execution.
     */
    fun validateWorkflow(workflow: Workflow, screenWidth: Int, screenHeight: Int): ValidationResult {
        if (workflow.steps.isEmpty()) {
            return ValidationResult.Invalid("Workflow must contain at least one step.")
        }

        val device = workflow.deviceProfile
        if (device == null || device.screenWidth <= 0 || device.screenHeight <= 0) {
            return ValidationResult.Invalid("Valid device profile with screen dimensions is required.")
        }

        val effectiveWidth = if (screenWidth > 0) screenWidth else device.screenWidth
        val effectiveHeight = if (screenHeight > 0) screenHeight else device.screenHeight

        for (step in workflow.steps) {
            val stepResult = validateStep(step, effectiveWidth, effectiveHeight)
            if (!stepResult.isValid) {
                return stepResult
            }
        }

        return ValidationResult.Valid
    }

    /**
     * Validates compatibility between recorded device profile and current runtime device.
     */
    fun validateDeviceCompatibility(recorded: DeviceProfile?, current: DeviceProfile): ValidationResult {
        if (recorded == null) {
            return ValidationResult.Invalid("Workflow has no recorded device profile.")
        }
        if (recorded.screenWidth != current.screenWidth || recorded.screenHeight != current.screenHeight) {
            return ValidationResult.Invalid(
                "Screen resolution mismatch: recorded on ${recorded.screenWidth}x${recorded.screenHeight}, " +
                "current device is ${current.screenWidth}x${current.screenHeight}."
            )
        }
        if (recorded.orientation != current.orientation) {
            return ValidationResult.Invalid(
                "Orientation mismatch: recorded in ${recorded.orientation}, current device is in ${current.orientation}."
            )
        }
        return ValidationResult.Valid
    }
}
