package com.taskscheduling.app.assistant

import com.taskscheduling.app.domain.model.Workflow

sealed class CommandResolutionResult {
    data class Success(val intent: CommandIntent?, val workflow: Workflow) : CommandResolutionResult()
    data class DirectAction(val intent: CommandIntent, val command: String) : CommandResolutionResult()
    data class TaskAction(val parseResult: TaskParseResult, val command: String) : CommandResolutionResult()
    data class UnknownCommand(val command: String) : CommandResolutionResult()
    data class WorkflowNotFound(val intent: CommandIntent?, val command: String, val workflowName: String? = null) : CommandResolutionResult()
    data class AmbiguousWorkflows(val command: String, val queryName: String, val candidates: List<Workflow>) : CommandResolutionResult()
}

/**
 * Interface processing raw text commands into matched workflows, direct device actions, or task management actions.
 */
interface CommandProcessor {
    fun process(command: String): CommandResolutionResult
}

/**
 * Concrete local processor combining [CommandResolver], [WorkflowResolver], and [TaskCommandParser].
 * Evaluates in strict priority:
 * 1. Safe Direct Device Actions & explicit System Workflows (Change Wallpaper, Go Back, Swipes, etc.)
 * 2. Task Management Commands (Add Task, Show Today, Complete Task, etc.)
 * 3. Custom Recorded Workflows from Persistence
 * 4. Unknown
 */
class LocalCommandProcessor(
    private val commandResolver: CommandResolver = LocalCommandResolver(),
    private val workflowResolver: WorkflowResolver
) : CommandProcessor {

    override fun process(command: String): CommandResolutionResult {
        val trimmed = command.trim()
        if (trimmed.isBlank()) return CommandResolutionResult.UnknownCommand(command)

        // 1. Direct device actions and explicit system workflows (e.g. Change Wallpaper, Swipes, Go Back)
        val directIntent = commandResolver.resolve(trimmed) ?: resolveDirectSystemIntent(trimmed)
        if (directIntent != null && !isTaskIntent(directIntent)) {
            if (directIntent == CommandIntent.CHANGE_WALLPAPER) {
                val wallpapers = workflowResolver.getWallpaperWorkflows()
                if (wallpapers.size > 1) {
                    return CommandResolutionResult.AmbiguousWorkflows(trimmed, "Change Wallpaper", wallpapers)
                }
                val workflow = workflowResolver.resolveWorkflow(directIntent)
                    ?: return CommandResolutionResult.WorkflowNotFound(directIntent, trimmed, "Change Wallpaper")
                return CommandResolutionResult.Success(directIntent, workflow)
            }
            return CommandResolutionResult.DirectAction(directIntent, trimmed)
        }

        // 2. Task-specific commands (Add Task, Show Today, Complete, Delete, Search)
        val taskResult = TaskCommandParser.parse(trimmed)
        if (taskResult !is TaskParseResult.NotATaskCommand && taskResult !is TaskParseResult.UnknownTaskCommand) {
            return CommandResolutionResult.TaskAction(taskResult, trimmed)
        }

        // 3. Custom recorded workflows resolved dynamically from Persistence
        val resolution = workflowResolver.resolveByName(trimmed)
        return when (resolution) {
            is WorkflowResolution.Found -> {
                CommandResolutionResult.Success(null, resolution.workflow)
            }
            is WorkflowResolution.NotFound -> {
                if (isWorkflowInvocationPhrase(trimmed)) {
                    CommandResolutionResult.WorkflowNotFound(null, trimmed, resolution.queryName)
                } else if (taskResult is TaskParseResult.UnknownTaskCommand) {
                    CommandResolutionResult.TaskAction(taskResult, trimmed)
                } else {
                    CommandResolutionResult.UnknownCommand(trimmed)
                }
            }
            is WorkflowResolution.Ambiguous -> {
                CommandResolutionResult.AmbiguousWorkflows(trimmed, resolution.queryName, resolution.candidates)
            }
        }
    }

    private fun resolveDirectSystemIntent(command: String): CommandIntent? {
        val normalized = LocalCommandResolver.normalize(command)
        val stripped = LocalCommandResolver.stripConversationalPrefix(normalized)
        val candidates = if (stripped.isNotBlank() && stripped != normalized) {
            listOf(stripped, normalized)
        } else {
            listOf(normalized)
        }

        val directIntents = listOf(
            CommandIntent.CHANGE_WALLPAPER,
            CommandIntent.GO_BACK,
            CommandIntent.GO_HOME,
            CommandIntent.SWIPE_LEFT,
            CommandIntent.SWIPE_RIGHT,
            CommandIntent.SWIPE_UP,
            CommandIntent.SWIPE_DOWN,
            CommandIntent.SCROLL_UP,
            CommandIntent.SCROLL_DOWN,
            CommandIntent.OPEN_YOUTUBE,
            CommandIntent.OPEN_SETTINGS
        )

        for (candidate in candidates) {
            for (intent in directIntents) {
                when (intent) {
                    CommandIntent.CHANGE_WALLPAPER -> {
                        if (candidate.matches(Regex("^(?:change|set|update|switch|modify|apply|replace)\\s+(?:(?:my|the|a|an|new|tablet|device|phone|current|screen|home)\\s+)*(?:wallpaper|background|wall paper)$")) ||
                            candidate.matches(Regex("^(?:change wallpaper|set wallpaper|update wallpaper|new wallpaper|set background|change background)$"))) {
                            return intent
                        }
                    }
                    CommandIntent.GO_BACK -> {
                        if (candidate.matches(Regex("^(?:go\\s+back|back|press\\s+back|go\\s+backwards|return\\s+back|take\\s+me\\s+back|go\\s+one\\s+step\\s+back)$"))) {
                            return intent
                        }
                    }
                    CommandIntent.GO_HOME -> {
                        if (candidate.matches(Regex("^(?:go\\s+home|home|press\\s+home|take\\s+me\\s+home|go\\s+to\\s+(?:the\\s+)?home(?:\\s+screen)?|home\\s+screen)$"))) {
                            return intent
                        }
                    }
                    CommandIntent.SWIPE_LEFT -> {
                        if (candidate.matches(Regex("^(?:swipe|move)\\s+(?:to\\s+(?:the\\s+)?|towards?\\s+(?:the\\s+)?)?left$"))) {
                            return intent
                        }
                    }
                    CommandIntent.SWIPE_RIGHT -> {
                        if (candidate.matches(Regex("^(?:swipe|move)\\s+(?:to\\s+(?:the\\s+)?|towards?\\s+(?:the\\s+)?)?right$"))) {
                            return intent
                        }
                    }
                    CommandIntent.SWIPE_UP -> {
                        if (candidate.matches(Regex("^(?:swipe|move)\\s+(?:to\\s+(?:the\\s+)?|towards?\\s+(?:the\\s+)?)?(?:up|upwards|top)$"))) {
                            return intent
                        }
                    }
                    CommandIntent.SWIPE_DOWN -> {
                        if (candidate.matches(Regex("^(?:swipe|move)\\s+(?:to\\s+(?:the\\s+)?|towards?\\s+(?:the\\s+)?)?(?:down|downwards|bottom)$"))) {
                            return intent
                        }
                    }
                    CommandIntent.SCROLL_UP -> {
                        if (candidate.matches(Regex("^scroll\\s+(?:up|upwards|to\\s+(?:the\\s+)?top|towards?\\s+(?:the\\s+)?top)$"))) {
                            return intent
                        }
                    }
                    CommandIntent.SCROLL_DOWN -> {
                        if (candidate.matches(Regex("^scroll\\s+(?:down|downwards|to\\s+(?:the\\s+)?bottom|towards?\\s+(?:the\\s+)?bottom)$"))) {
                            return intent
                        }
                    }
                    CommandIntent.OPEN_YOUTUBE -> {
                        if (candidate.matches(Regex("^(?:open|launch|start|go\\s+to)\\s+(?:the\\s+)?youtube(?:\\s+app)?$"))) {
                            return intent
                        }
                    }
                    CommandIntent.OPEN_SETTINGS -> {
                        if (candidate.matches(Regex("^(?:open|launch|start|go\\s+to)\\s+(?:the\\s+|android\\s+)?settings(?:\\s+app)?$"))) {
                            return intent
                        }
                    }
                    else -> {}
                }
            }
        }
        return null
    }

    private fun isWorkflowInvocationPhrase(text: String): Boolean {
        val stripped = LocalCommandResolver.stripConversationalPrefix(LocalCommandResolver.normalize(text))
        val lower = stripped.lowercase()
        return lower.startsWith("run ") ||
                lower.startsWith("start ") ||
                lower.startsWith("execute ") ||
                lower.startsWith("do ") ||
                lower.startsWith("launch ") ||
                lower.startsWith("play ") ||
                lower.endsWith(" workflow") ||
                lower.endsWith(" routine") ||
                lower.endsWith(" task")
    }

    private fun isTaskIntent(intent: CommandIntent): Boolean {
        return when (intent) {
            CommandIntent.ADD_TASK,
            CommandIntent.SHOW_TASKS,
            CommandIntent.SHOW_TODAY_TASKS,
            CommandIntent.SHOW_COMPLETED_TASKS,
            CommandIntent.SHOW_OVERDUE_TASKS,
            CommandIntent.COMPLETE_TASK,
            CommandIntent.DELETE_TASK,
            CommandIntent.EDIT_TASK,
            CommandIntent.RESCHEDULE_TASK,
            CommandIntent.SEARCH_TASKS -> true
            else -> false
        }
    }
}
