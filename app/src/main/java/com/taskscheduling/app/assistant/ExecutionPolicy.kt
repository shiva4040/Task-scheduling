package com.taskscheduling.app.assistant

import com.taskscheduling.app.domain.model.Workflow

/**
 * Execution policy categorizing commands into immediate execution or confirmation required.
 * Simple reversible direct device controls execute immediately without user confirmation prompts.
 * Custom / recorded multi-step workflows strictly require explicit confirmation before execution.
 */
enum class ExecutionPolicy {
    IMMEDIATE,
    CONFIRMATION_REQUIRED
}

/**
 * Centralized policy resolver determining the execution policy for each command intent and workflow.
 */
interface ExecutionPolicyResolver {
    fun getExecutionPolicy(intent: CommandIntent): ExecutionPolicy
    fun getWorkflowExecutionPolicy(workflow: Workflow? = null): ExecutionPolicy = ExecutionPolicy.CONFIRMATION_REQUIRED
}

/**
 * Default offline implementation of [ExecutionPolicyResolver].
 *
 * Category A — Safe Direct Actions (IMMEDIATE):
 * GO_BACK, GO_HOME, SWIPE_LEFT, SWIPE_RIGHT, SWIPE_UP, SWIPE_DOWN,
 * SCROLL_UP, SCROLL_DOWN, OPEN_YOUTUBE, OPEN_SETTINGS,
 * ADD_TASK, SHOW_TASKS, SHOW_TODAY_TASKS, SHOW_COMPLETED_TASKS, SHOW_OVERDUE_TASKS,
 * EDIT_TASK, SEARCH_TASKS
 *
 * Category B — Destructive / Modifying Actions & Workflows (CONFIRMATION_REQUIRED):
 * CHANGE_WALLPAPER, COMPLETE_TASK, DELETE_TASK, RESCHEDULE_TASK, and any multi-step automation workflow
 */
object DefaultExecutionPolicyResolver : ExecutionPolicyResolver {
    override fun getExecutionPolicy(intent: CommandIntent): ExecutionPolicy {
        return when (intent) {
            CommandIntent.CHANGE_WALLPAPER,
            CommandIntent.COMPLETE_TASK,
            CommandIntent.DELETE_TASK,
            CommandIntent.RESCHEDULE_TASK -> ExecutionPolicy.CONFIRMATION_REQUIRED

            CommandIntent.GO_BACK,
            CommandIntent.GO_HOME,
            CommandIntent.GREETING,
            CommandIntent.SWIPE_LEFT,
            CommandIntent.SWIPE_RIGHT,
            CommandIntent.SWIPE_UP,
            CommandIntent.SWIPE_DOWN,
            CommandIntent.SCROLL_UP,
            CommandIntent.SCROLL_DOWN,
            CommandIntent.OPEN_YOUTUBE,
            CommandIntent.OPEN_SETTINGS,
            CommandIntent.ADD_TASK,
            CommandIntent.SHOW_TASKS,
            CommandIntent.SHOW_TODAY_TASKS,
            CommandIntent.SHOW_COMPLETED_TASKS,
            CommandIntent.SHOW_OVERDUE_TASKS,
            CommandIntent.EDIT_TASK,
            CommandIntent.SEARCH_TASKS -> ExecutionPolicy.IMMEDIATE
        }
    }

    override fun getWorkflowExecutionPolicy(workflow: Workflow?): ExecutionPolicy {
        return ExecutionPolicy.CONFIRMATION_REQUIRED
    }
}
