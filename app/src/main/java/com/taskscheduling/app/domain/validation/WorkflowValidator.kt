package com.taskscheduling.app.domain.validation

import com.taskscheduling.app.domain.model.ActionType
import com.taskscheduling.app.domain.model.Workflow
import com.taskscheduling.app.domain.model.WorkflowStep

sealed class WorkflowValidationResult {
    object Valid : WorkflowValidationResult()
    data class Invalid(
        val message: String,
        val stepIndex: Int? = null,
        val stepId: String? = null,
        val fieldName: String? = null
    ) : WorkflowValidationResult()

    val isValid: Boolean get() = this is Valid
}

object WorkflowValidator {

    fun validateWorkflow(
        workflow: Workflow,
        screenWidth: Int? = null,
        screenHeight: Int? = null
    ): WorkflowValidationResult {
        if (workflow.name.isBlank()) {
            return WorkflowValidationResult.Invalid(
                message = "Workflow name cannot be empty.",
                fieldName = "name"
            )
        }

        if (workflow.steps.isEmpty()) {
            return WorkflowValidationResult.Invalid(
                message = "Workflow must contain at least one step.",
                fieldName = "steps"
            )
        }

        workflow.steps.forEachIndexed { index, step ->
            val stepResult = validateStep(step, index, screenWidth, screenHeight)
            if (stepResult is WorkflowValidationResult.Invalid) {
                return stepResult
            }
        }

        return WorkflowValidationResult.Valid
    }

    fun validateStep(
        step: WorkflowStep,
        stepIndex: Int = 0,
        screenWidth: Int? = null,
        screenHeight: Int? = null
    ): WorkflowValidationResult {
        if (step.label.isBlank() && step.description.isBlank()) {
            return WorkflowValidationResult.Invalid(
                message = "Step ${stepIndex + 1} must have a label or description.",
                stepIndex = stepIndex,
                stepId = step.id,
                fieldName = "label"
            )
        }

        when (step.actionType) {
            ActionType.TAP, ActionType.LONG_PRESS, ActionType.DOUBLE_TAP -> {
                val x = step.coordinate?.x ?: step.startX
                val y = step.coordinate?.y ?: step.startY
                if (x < 0 || y < 0) {
                    return WorkflowValidationResult.Invalid(
                        message = "Step ${stepIndex + 1} (${step.actionType.label}) coordinates cannot be negative: ($x, $y).",
                        stepIndex = stepIndex,
                        stepId = step.id,
                        fieldName = "coordinate"
                    )
                }
                if (screenWidth != null && screenHeight != null && screenWidth > 0 && screenHeight > 0) {
                    if (x > screenWidth || y > screenHeight) {
                        return WorkflowValidationResult.Invalid(
                            message = "Step ${stepIndex + 1} target ($x, $y) exceeds screen dimensions (${screenWidth}x${screenHeight}).",
                            stepIndex = stepIndex,
                            stepId = step.id,
                            fieldName = "coordinate"
                        )
                    }
                }
            }
            ActionType.SWIPE, ActionType.DRAG -> {
                val sx = step.coordinate?.x ?: step.startX
                val sy = step.coordinate?.y ?: step.startY
                val ex = step.coordinate?.endX ?: step.endX
                val ey = step.coordinate?.endY ?: step.endY

                if (sx < 0 || sy < 0) {
                    return WorkflowValidationResult.Invalid(
                        message = "Step ${stepIndex + 1} swipe start coordinates cannot be negative.",
                        stepIndex = stepIndex,
                        stepId = step.id,
                        fieldName = "coordinate"
                    )
                }

                if (ex == null || ey == null || ex < 0 || ey < 0) {
                    return WorkflowValidationResult.Invalid(
                        message = "Step ${stepIndex + 1} (${step.actionType.label}) requires valid end coordinates.",
                        stepIndex = stepIndex,
                        stepId = step.id,
                        fieldName = "endCoordinate"
                    )
                }

                if (screenWidth != null && screenHeight != null && screenWidth > 0 && screenHeight > 0) {
                    if (sx > screenWidth || sy > screenHeight || ex > screenWidth || ey > screenHeight) {
                        return WorkflowValidationResult.Invalid(
                            message = "Step ${stepIndex + 1} swipe path exceeds screen dimensions (${screenWidth}x${screenHeight}).",
                            stepIndex = stepIndex,
                            stepId = step.id,
                            fieldName = "coordinate"
                        )
                    }
                }
            }
            ActionType.WAIT -> {
                val duration = if (step.durationMs > 0) step.durationMs else step.duration
                if (duration <= 0) {
                    return WorkflowValidationResult.Invalid(
                        message = "Step ${stepIndex + 1} wait duration must be greater than 0 ms.",
                        stepIndex = stepIndex,
                        stepId = step.id,
                        fieldName = "duration"
                    )
                }
            }
            ActionType.TEXT_INPUT -> {
                if (step.textInput.isNullOrBlank()) {
                    return WorkflowValidationResult.Invalid(
                        message = "Step ${stepIndex + 1} text input value cannot be empty.",
                        stepIndex = stepIndex,
                        stepId = step.id,
                        fieldName = "textInput"
                    )
                }
            }
            ActionType.BACK, ActionType.HOME, ActionType.RECENTS, ActionType.CUSTOM_GESTURE -> {
                // System actions do not require coordinate targets
            }
        }

        return WorkflowValidationResult.Valid
    }
}
