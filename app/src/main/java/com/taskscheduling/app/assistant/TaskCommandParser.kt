package com.taskscheduling.app.assistant

import com.taskscheduling.app.domain.model.TaskCategory
import com.taskscheduling.app.domain.model.TaskDraft
import com.taskscheduling.app.domain.model.TaskPriority
import com.taskscheduling.app.domain.model.TaskRecurrence
import java.util.Locale

/**
 * Result of local deterministic task command parsing.
 */
sealed class TaskParseResult {
    data class AddTask(val draft: TaskDraft, val rawQuery: String) : TaskParseResult()
    data class ShowTasks(val filter: String = "ALL") : TaskParseResult()
    object ShowTodayTasks : TaskParseResult()
    object ShowCompletedTasks : TaskParseResult()
    object ShowOverdueTasks : TaskParseResult()
    data class CompleteTask(val targetQuery: String) : TaskParseResult()
    data class DeleteTask(val targetQuery: String) : TaskParseResult()
    data class EditTask(val targetQuery: String) : TaskParseResult()
    data class RescheduleTask(val targetQuery: String, val newDate: String? = null, val newTime: String? = null) : TaskParseResult()
    data class SearchTasks(val searchQuery: String) : TaskParseResult()
    object UnknownTaskCommand : TaskParseResult()
    object NotATaskCommand : TaskParseResult()
}

/**
 * Offline deterministic parser for voice task management commands without cloud NLP or LLMs.
 */
object TaskCommandParser {

    /**
     * Parses a normalized or raw voice command into a structured [TaskParseResult].
     * Enforces safety: never guesses task intent on ambiguous phrases (e.g. "I should study tomorrow").
     */
    fun parse(rawCommand: String): TaskParseResult {
        val clean = LocalCommandResolver.stripConversationalPrefix(rawCommand).trim()
        if (clean.isBlank()) return TaskParseResult.NotATaskCommand

        val lower = clean.lowercase(Locale.ROOT)

        // 1. Specific Query / Display Intents
        if (isTodayTasksQuery(lower)) return TaskParseResult.ShowTodayTasks
        if (isCompletedTasksQuery(lower)) return TaskParseResult.ShowCompletedTasks
        if (isOverdueTasksQuery(lower)) return TaskParseResult.ShowOverdueTasks
        if (isShowTasksQuery(lower)) return TaskParseResult.ShowTasks()

        // 2. Complete Task Commands
        parseCompleteCommand(clean)?.let { return it }

        // 3. Delete Task Commands
        parseDeleteCommand(clean)?.let { return it }

        // 4. Reschedule Task Commands
        parseRescheduleCommand(clean)?.let { return it }

        // 5. Edit Task Commands
        parseEditCommand(clean)?.let { return it }

        // 6. Search Tasks Commands
        parseSearchCommand(clean)?.let { return it }

        // 7. Add / Create Task / Remind Intent
        if (isAddTaskTrigger(clean)) {
            val draft = extractTaskDraft(clean)
            return TaskParseResult.AddTask(draft = draft, rawQuery = rawCommand)
        }

        // 8. Ambiguous generic task phrase (e.g. "do something with my tasks")
        if (isAmbiguousTaskPhrase(clean)) {
            return TaskParseResult.UnknownTaskCommand
        }

        return TaskParseResult.NotATaskCommand
    }

    private fun isShowTasksQuery(lower: String): Boolean {
        val clean = lower.replace(Regex("[^a-z0-9\\s]"), " ").replace(Regex("\\s+"), " ").trim()
        return clean == "show tasks" ||
                clean == "show my tasks" ||
                clean == "show all tasks" ||
                clean == "view tasks" ||
                clean == "view my tasks" ||
                clean == "list tasks" ||
                clean == "list my tasks" ||
                clean == "what are my tasks" ||
                clean == "what is on my task list" ||
                clean == "whats on my task list" ||
                clean == "open tasks" ||
                clean == "open my tasks" ||
                clean == "task list" ||
                clean == "my tasks"
    }

    private fun isTodayTasksQuery(lower: String): Boolean {
        val clean = lower.replace(Regex("[^a-z0-9\\s]"), " ").replace(Regex("\\s+"), " ").trim()
        return clean == "show today tasks" ||
                clean == "show today s tasks" ||
                clean == "show todays tasks" ||
                clean == "show my tasks for today" ||
                clean == "show tasks for today" ||
                clean == "what do i have today" ||
                clean == "what tasks do i have today" ||
                clean == "what do i need to do today" ||
                clean == "what is on my schedule today" ||
                clean == "whats on my schedule today" ||
                clean == "today s tasks" ||
                clean == "todays tasks" ||
                clean == "tasks for today" ||
                clean == "today tasks"
    }

    private fun isCompletedTasksQuery(lower: String): Boolean {
        val clean = lower.replace(Regex("[^a-z0-9\\s]"), " ").replace(Regex("\\s+"), " ").trim()
        return clean == "show completed tasks" ||
                clean == "show my completed tasks" ||
                clean == "what have i completed" ||
                clean == "what did i complete" ||
                clean == "completed tasks" ||
                clean == "show done tasks" ||
                clean == "finished tasks"
    }

    private fun isOverdueTasksQuery(lower: String): Boolean {
        val clean = lower.replace(Regex("[^a-z0-9\\s]"), " ").replace(Regex("\\s+"), " ").trim()
        return clean == "show overdue tasks" ||
                clean == "show my overdue tasks" ||
                clean == "what am i late on" ||
                clean == "which tasks are overdue" ||
                clean == "what is overdue" ||
                clean == "whats overdue" ||
                clean == "overdue tasks" ||
                clean == "late tasks"
    }

    private fun parseCompleteCommand(text: String): TaskParseResult? {
        val lower = text.lowercase(Locale.ROOT)
        val patterns = listOf(
            Regex("^(?:complete|finish|mark\\s+as\\s+complete|mark\\s+as\\s+done|mark\\s+done|i\\s+finished|i\\s+completed)\\s+(?:my\\s+)?(?:task\\s+)?(?:called\\s+)?(.+?)(?:\\s+task)?$"),
            Regex("^(?:mark)\\s+(.+?)(?:\\s+as\\s+complete|\\s+as\\s+done)$")
        )
        for (pattern in patterns) {
            val match = pattern.matchEntire(lower)
            if (match != null) {
                var target = match.groupValues[1].trim()
                target = target.removePrefix("my ").removeSuffix(" task").trim()
                if (target.isNotBlank()) {
                    return TaskParseResult.CompleteTask(target)
                }
            }
        }
        return null
    }

    private fun parseDeleteCommand(text: String): TaskParseResult? {
        val lower = text.lowercase(Locale.ROOT)
        val patterns = listOf(
            Regex("^(?:delete|remove|cancel|discard)\\s+(?:my\\s+)?(?:task\\s+)?(?:called\\s+)?(.+?)(?:\\s+task)?$")
        )
        for (pattern in patterns) {
            val match = pattern.matchEntire(lower)
            if (match != null) {
                var target = match.groupValues[1].trim()
                target = target.removePrefix("my ").removeSuffix(" task").trim()
                if (target.isNotBlank()) {
                    return TaskParseResult.DeleteTask(target)
                }
            }
        }
        return null
    }

    private fun parseRescheduleCommand(text: String): TaskParseResult? {
        val lower = text.lowercase(Locale.ROOT)
        val patterns = listOf(
            Regex("^(?:reschedule|move|postpone)\\s+(?:my\\s+)?(?:task\\s+)?(?:called\\s+)?(.+?)(?:\\s+to\\s+(tomorrow|next\\s+week|monday|tuesday|wednesday|thursday|friday|saturday|sunday|today))?$"),
            Regex("^(?:change)\\s+(?:my\\s+)?(?:task\\s+)?(.+?)(?:\\s+task)?(?:\\s+to\\s+(tomorrow|next\\s+week|monday|tuesday|wednesday|thursday|friday|saturday|sunday|today|[0-9]+(?:\\s*(?:am|pm))?))$")
        )
        for (pattern in patterns) {
            val match = pattern.matchEntire(lower)
            if (match != null) {
                var target = match.groupValues[1].trim()
                val newDateOrTime = if (match.groupValues.size > 2) match.groupValues[2].trim() else null
                target = target.removePrefix("my ").removeSuffix(" task").trim()
                if (target.isNotBlank() && target != "wallpaper" && target != "background") {
                    return TaskParseResult.RescheduleTask(targetQuery = target, newDate = newDateOrTime)
                }
            }
        }
        return null
    }

    private fun parseEditCommand(text: String): TaskParseResult? {
        val lower = text.lowercase(Locale.ROOT)
        val patterns = listOf(
            Regex("^(?:edit|modify|update)\\s+(?:my\\s+)?(?:task\\s+)?(?:called\\s+)?(.+?)(?:\\s+task)?$"),
            Regex("^(?:change)\\s+(?:my\\s+)?(.+?)(?:\\s+task)$")
        )
        for (pattern in patterns) {
            val match = pattern.matchEntire(lower)
            if (match != null) {
                var target = match.groupValues[1].trim()
                target = target.removePrefix("my ").removeSuffix(" task").trim()
                if (target.isNotBlank() && target != "wallpaper" && target != "background") {
                    return TaskParseResult.EditTask(target)
                }
            }
        }
        return null
    }

    private fun parseSearchCommand(text: String): TaskParseResult? {
        val lower = text.lowercase(Locale.ROOT)
        val patterns = listOf(
            Regex("^(?:find|search|look\\s+for|show\\s+tasks\\s+about)\\s+(?:my\\s+)?(?:task\\s+|tasks\\s+)?(?:about\\s+|for\\s+)?(.+)$")
        )
        for (pattern in patterns) {
            val match = pattern.matchEntire(lower)
            if (match != null) {
                val query = match.groupValues[1].trim()
                if (query.isNotBlank() && query != "tasks" && query != "my tasks") {
                    return TaskParseResult.SearchTasks(query)
                }
            }
        }
        return null
    }

    private fun isAddTaskTrigger(text: String): Boolean {
        val lower = text.lowercase(Locale.ROOT).trim()
        val hinglishTaskTrigger = lower.contains(Regex("\\b(?:ek\\s+)?task\\s+bana\\s*(?:do|dena|de|dijiye|o)?\\b", RegexOption.IGNORE_CASE)) ||
                lower.contains(Regex("(?:एक\\s+)?टास्क\\s*बना(?:ओ|दो|दें|दीजिए)?", RegexOption.IGNORE_CASE))
        if (hinglishTaskTrigger) return true

        return lower == "add task" ||
                lower == "add a task" ||
                lower == "create a task" ||
                lower == "create task" ||
                lower == "new task" ||
                lower == "new task please" ||
                lower == "add something to my tasks" ||
                lower == "add this to my tasks" ||
                lower == "put this on my tasks" ||
                lower == "put this on my task list" ||
                lower.startsWith("add a task") ||
                lower.startsWith("add task ") ||
                lower.startsWith("add task:") ||
                lower.startsWith("create a task") ||
                lower.startsWith("create task ") ||
                lower.startsWith("new task ") ||
                lower.startsWith("make a task") ||
                lower.startsWith("make task ") ||
                lower.startsWith("schedule a task") ||
                lower.startsWith("schedule task ") ||
                lower.startsWith("set a reminder") ||
                lower.startsWith("remind me to ") ||
                lower.startsWith("remind me ") ||
                lower.startsWith("create a high priority task") ||
                lower.startsWith("create a low priority task") ||
                lower.startsWith("create a urgent task") ||
                lower.startsWith("create an urgent task") ||
                lower.startsWith("add a high priority task") ||
                lower.startsWith("add a low priority task") ||
                lower.startsWith("add an urgent task") ||
                lower.startsWith("add a study task") ||
                lower.startsWith("add a work task") ||
                lower.startsWith("add a personal task") ||
                lower.startsWith("add a shopping task") ||
                lower.startsWith("add a health task") ||
                lower.endsWith(" to my tasks") ||
                lower.endsWith(" on my task list") ||
                lower.endsWith(" to my task list")
    }

    private fun isAmbiguousTaskPhrase(text: String): Boolean {
        val lower = text.lowercase(Locale.ROOT)
        return lower.contains("tasks") || lower.contains("task list") || lower.contains("my task")
    }

    /**
     * Extracts structured fields for a new TaskDraft from natural voice text.
     */
    fun extractTaskDraft(rawText: String): TaskDraft {
        var working = LocalCommandResolver.stripConversationalPrefix(rawText).trim()

        var priority = TaskPriority.MEDIUM
        var category = TaskCategory.CUSTOM
        var recurrence = TaskRecurrence.NONE
        var reminderOffsetMinutes: Int? = null
        var isReminderEnabled = false
        var extractedDate: String? = null
        var extractedTime: String? = null

        val lower = working.lowercase(Locale.ROOT)

        // 1. Extract Priority
        if (working.contains(Regex("\\b(?:an\\s+)?urgent(?:\\s+priority)?\\b", RegexOption.IGNORE_CASE))) {
            priority = TaskPriority.URGENT
            working = working.replace(Regex("\\b(?:an\\s+)?urgent(?:\\s+priority)?(?:\\s+task)?\\b", RegexOption.IGNORE_CASE), " ")
        } else if (working.contains(Regex("\\b(?:make\\s+it\\s+|a\\s+)?high\\s+priority\\b", RegexOption.IGNORE_CASE)) || working.contains(Regex("\\bimportant\\b", RegexOption.IGNORE_CASE))) {
            priority = TaskPriority.HIGH
            working = working.replace(Regex("\\b(?:make\\s+it\\s+|a\\s+)?high\\s+priority(?:\\s+task)?\\b", RegexOption.IGNORE_CASE), " ")
                .replace(Regex("\\bimportant\\b", RegexOption.IGNORE_CASE), " ")
        } else if (working.contains(Regex("\\b(?:make\\s+it\\s+|a\\s+)?low\\s+priority\\b", RegexOption.IGNORE_CASE))) {
            priority = TaskPriority.LOW
            working = working.replace(Regex("\\b(?:make\\s+it\\s+|a\\s+)?low\\s+priority(?:\\s+task)?\\b", RegexOption.IGNORE_CASE), " ")
        } else if (working.contains(Regex("\\b(?:make\\s+it\\s+|a\\s+)?medium\\s+priority\\b", RegexOption.IGNORE_CASE))) {
            priority = TaskPriority.MEDIUM
            working = working.replace(Regex("\\b(?:make\\s+it\\s+|a\\s+)?medium\\s+priority(?:\\s+task)?\\b", RegexOption.IGNORE_CASE), " ")
        }

        // 2. Extract Category
        if (working.contains(Regex("\\bstudy\\s+task\\b", RegexOption.IGNORE_CASE)) || working.contains(Regex("^add\\s+a\\s+study\\s+task", RegexOption.IGNORE_CASE))) {
            category = TaskCategory.STUDY
            working = working.replace(Regex("\\bstudy\\s+task\\b", RegexOption.IGNORE_CASE), " ")
                .replace(Regex("^add\\s+a\\s+study\\s+task", RegexOption.IGNORE_CASE), "add a task")
        } else if (working.contains(Regex("\\bwork\\s+task\\b", RegexOption.IGNORE_CASE)) || working.contains(Regex("^add\\s+a\\s+work\\s+task", RegexOption.IGNORE_CASE))) {
            category = TaskCategory.WORK
            working = working.replace(Regex("\\bwork\\s+task\\b", RegexOption.IGNORE_CASE), " ")
                .replace(Regex("^add\\s+a\\s+work\\s+task", RegexOption.IGNORE_CASE), "add a task")
        } else if (working.contains(Regex("\\bpersonal\\s+task\\b", RegexOption.IGNORE_CASE)) || working.contains(Regex("^add\\s+a\\s+personal\\s+task", RegexOption.IGNORE_CASE))) {
            category = TaskCategory.PERSONAL
            working = working.replace(Regex("\\bpersonal\\s+task\\b", RegexOption.IGNORE_CASE), " ")
                .replace(Regex("^add\\s+a\\s+personal\\s+task", RegexOption.IGNORE_CASE), "add a task")
        } else if (working.contains(Regex("\\bshopping\\s+task\\b", RegexOption.IGNORE_CASE)) || working.contains(Regex("^add\\s+a\\s+shopping\\s+task", RegexOption.IGNORE_CASE))) {
            category = TaskCategory.SHOPPING
            working = working.replace(Regex("\\bshopping\\s+task\\b", RegexOption.IGNORE_CASE), " ")
                .replace(Regex("^add\\s+a\\s+shopping\\s+task", RegexOption.IGNORE_CASE), "add a task")
        } else if (working.contains(Regex("\\bhealth\\s+task\\b", RegexOption.IGNORE_CASE)) || working.contains(Regex("^add\\s+a\\s+health\\s+task", RegexOption.IGNORE_CASE))) {
            category = TaskCategory.HEALTH
            working = working.replace(Regex("\\bhealth\\s+task\\b", RegexOption.IGNORE_CASE), " ")
                .replace(Regex("^add\\s+a\\s+health\\s+task", RegexOption.IGNORE_CASE), "add a task")
        }

        // 3. Extract Recurrence
        if (working.contains(Regex("\\b(?:every\\s+day|daily)\\b", RegexOption.IGNORE_CASE))) {
            recurrence = TaskRecurrence.DAILY
            working = working.replace(Regex("\\b(?:every\\s+day|daily)\\b", RegexOption.IGNORE_CASE), " ")
        } else if (working.contains(Regex("\\b(?:every\\s+weekday|weekdays)\\b", RegexOption.IGNORE_CASE))) {
            recurrence = TaskRecurrence.WEEKDAYS
            working = working.replace(Regex("\\b(?:every\\s+weekday|weekdays)\\b", RegexOption.IGNORE_CASE), " ")
        } else if (working.contains(Regex("\\b(?:every\\s+week|weekly|every\\s+monday|every\\s+tuesday|every\\s+wednesday|every\\s+thursday|every\\s+friday|every\\s+saturday|every\\s+sunday)\\b", RegexOption.IGNORE_CASE))) {
            recurrence = TaskRecurrence.WEEKLY
            working = working.replace(Regex("\\b(?:every\\s+week|weekly|every\\s+monday|every\\s+tuesday|every\\s+wednesday|every\\s+thursday|every\\s+friday|every\\s+saturday|every\\s+sunday)\\b", RegexOption.IGNORE_CASE), " ")
        } else if (working.contains(Regex("\\b(?:every\\s+month|monthly)\\b", RegexOption.IGNORE_CASE))) {
            recurrence = TaskRecurrence.MONTHLY
            working = working.replace(Regex("\\b(?:every\\s+month|monthly)\\b", RegexOption.IGNORE_CASE), " ")
        }

        // 4. Extract Reminder Offset (e.g. "and remind me 30 minutes before", "remind me 1 hour before")
        val reminderOffsetRegex = Regex("(?:and\\s+)?remind\\s+me\\s+(?:(?:about\\s+)?([0-9]+)\\s+(minutes?|mins?|hours?|hrs?)\\s+before|at\\s+due\\s+time|before)", RegexOption.IGNORE_CASE)
        val offsetMatch = reminderOffsetRegex.find(working)
        if (offsetMatch != null) {
            isReminderEnabled = true
            val numStr = offsetMatch.groupValues[1]
            val unit = offsetMatch.groupValues[2].lowercase(Locale.ROOT)
            if (numStr.isNotBlank()) {
                val num = numStr.toIntOrNull() ?: 0
                reminderOffsetMinutes = if (unit.startsWith("hour") || unit.startsWith("hr")) num * 60 else num
            } else {
                reminderOffsetMinutes = 0
            }
            working = working.removeRange(offsetMatch.range).trim()
        } else if (lower.startsWith("remind me to ") || lower.startsWith("remind me ") || lower.contains("with a reminder") || lower.contains("and remind me")) {
            isReminderEnabled = true
            reminderOffsetMinutes = 0
            working = working.replace(Regex("\\b(?:with\\s+a\\s+reminder|and\\s+remind\\s+me)\\b", RegexOption.IGNORE_CASE), " ").trim()
        }

        // 5. Extract Time (e.g. "at 7 pm", "at 10 am", "at 7:30 pm", "at 19:00", "at 6 in the evening", "at midnight", "at noon")
        val midnightRegex = Regex("\\b(?:at\\s+)?midnight\\b", RegexOption.IGNORE_CASE)
        val noonRegex = Regex("\\b(?:at\\s+)?noon\\b", RegexOption.IGNORE_CASE)

        if (working.contains(midnightRegex)) {
            extractedTime = "00:00"
            working = working.replace(midnightRegex, " ").trim()
        } else if (working.contains(noonRegex)) {
            extractedTime = "12:00"
            working = working.replace(noonRegex, " ").trim()
        } else {
            val bajeRegex = Regex("\\b([0-1]?[0-9]|2[0-3])(?:\\s*:\\s*([0-5][0-9]))?\\s*baje\\b", RegexOption.IGNORE_CASE)
            val bajeMatch = bajeRegex.find(working)
            if (bajeMatch != null) {
                var hour = bajeMatch.groupValues[1].toIntOrNull() ?: 0
                val min = bajeMatch.groupValues[2].ifBlank { "00" }.toIntOrNull() ?: 0
                val isEveningOrNight = working.contains(Regex("\\b(?:shaam|sham|raat|evening|night|pm)\\b", RegexOption.IGNORE_CASE))
                if (isEveningOrNight && hour < 12) {
                    hour += 12
                }
                extractedTime = String.format(Locale.ROOT, "%02d:%02d", hour, min)
                working = working.removeRange(bajeMatch.range).trim()
            } else {
                val timeRegex = Regex("\\b(?:at\\s+)?([0-1]?[0-9]|2[0-3])(?:\\s*:\\s*([0-5][0-9])|\\s+([0-5][0-9]))?\\s*(am|pm|in\\s+the\\s+morning|in\\s+the\\s+evening|in\\s+the\\s+afternoon|at\\s+night)?\\b", RegexOption.IGNORE_CASE)
                val timeMatch = timeRegex.findAll(working).lastOrNull { m ->
                    val hasAmPm = m.groupValues[4].isNotBlank()
                    val hasColon = m.groupValues[2].isNotBlank() || m.groupValues[3].isNotBlank()
                    val hasAtPrefix = m.value.trim().lowercase(Locale.ROOT).startsWith("at")
                    hasAmPm || hasColon || hasAtPrefix
                }

                if (timeMatch != null) {
                    val hourStr = timeMatch.groupValues[1]
                    val minStr = timeMatch.groupValues[2].ifBlank { timeMatch.groupValues[3] }.ifBlank { "00" }
                    val period = timeMatch.groupValues[4].lowercase(Locale.ROOT)

                    var hour = hourStr.toIntOrNull() ?: 0
                    val min = minStr.toIntOrNull() ?: 0

                    if (period == "pm" || period.contains("evening") || period.contains("night") || period.contains("afternoon")) {
                        if (hour < 12) hour += 12
                    } else if (period == "am" || period.contains("morning")) {
                        if (hour == 12) hour = 0
                    }

                    extractedTime = String.format(Locale.ROOT, "%02d:%02d", hour, min)
                    working = working.removeRange(timeMatch.range).trim()
                }
            }
        }

        // 6. Extract Date (e.g. "day after tomorrow", "tomorrow", "today", "tonight", "next monday", "monday", "october 10")
        val dateKeywords = listOf(
            "day after tomorrow",
            "parso", "parson",
            "tomorrow",
            "kal",
            "today", "tonight",
            "aaj",
            "next week",
            "next monday", "next tuesday", "next wednesday", "next thursday", "next friday", "next saturday", "next sunday",
            "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday"
        )
        for (kw in dateKeywords) {
            val regex = Regex("\\b(?:on\\s+|for\\s+)?$kw\\b", RegexOption.IGNORE_CASE)
            if (working.contains(regex)) {
                extractedDate = when (kw) {
                    "day after tomorrow", "parso", "parson" -> "Day After Tomorrow"
                    "tomorrow", "kal" -> "Tomorrow"
                    "today", "tonight", "aaj" -> "Today"
                    "next week" -> "Next Week"
                    else -> kw.split(" ").joinToString(" ") { word ->
                        word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
                    }
                }
                working = working.replace(regex, " ").trim()
                break
            }
        }

        // Specific date like "October 10" or "Oct 10"
        val specificDateRegex = Regex("\\b(?:on\\s+|for\\s+)?(january|february|march|april|may|june|july|august|september|october|november|december|jan|feb|mar|apr|jun|jul|aug|sep|oct|nov|dec)\\s+([0-3]?[0-9])(?:st|nd|rd|th)?\\b", RegexOption.IGNORE_CASE)
        val specDateMatch = specificDateRegex.find(working)
        if (specDateMatch != null) {
            val month = specDateMatch.groupValues[1].replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
            val day = specDateMatch.groupValues[2]
            extractedDate = "$month $day"
            working = working.removeRange(specDateMatch.range).trim()
        }

        // 7. Clean up Title
        val prefixPatterns = listOf(
            Regex("^(?:add\\s+a\\s+task\\s+called|create\\s+a\\s+task\\s+called|make\\s+a\\s+task\\s+called|add\\s+a\\s+task\\s+to|create\\s+a\\s+task\\s+to|make\\s+a\\s+task\\s+to|add\\s+a\\s+task|create\\s+a\\s+task|make\\s+a\\s+task|schedule\\s+a\\s+task|new\\s+task\\s+please|new\\s+task|add\\s+task\\s+to|add\\s+task\\s+called|create\\s+task\\s+to|create\\s+task\\s+called|add\\s+task|create\\s+task|make\\s+task|schedule\\s+task|remind\\s+me\\s+to|remind\\s+me|set\\s+a\\s+reminder\\s+to|set\\s+a\\s+reminder)\\s*", RegexOption.IGNORE_CASE),
            Regex("^(?:add|create|put|make|schedule)\\s+", RegexOption.IGNORE_CASE),
            Regex("^(?:mujhe|hume|humko|kripya|please)\\s+", RegexOption.IGNORE_CASE),
            Regex("^(?:kal\\s+morning|kal\\s+subah|tomorrow\\s+morning|morning)\\s+", RegexOption.IGNORE_CASE)
        )
        for (pattern in prefixPatterns) {
            working = working.replace(pattern, "")
        }

        val suffixPatterns = listOf(
            Regex("\\s+(?:to\\s+my\\s+tasks|to\\s+my\\s+task\\s+list|on\\s+my\\s+task\\s+list|on\\s+my\\s+tasks|to\\s+tasks|to\\s+task\\s+list)$", RegexOption.IGNORE_CASE),
            Regex("[,\\s]+(?:(?:ek\\s+)?task\\s+bana\\s*(?:do|dena|de|dijiye|o)|(?:एक\\s+)?टास्क\\s*बना(?:ओ|दो|दें|दीजिए))$", RegexOption.IGNORE_CASE),
            Regex("\\s+(?:karni\\s+hai|karna\\s+hai|karo)$", RegexOption.IGNORE_CASE)
        )
        for (pattern in suffixPatterns) {
            working = working.replace(pattern, "")
        }

        var finalTitle = working.replace(Regex("[^\\p{L}\\p{M}\\p{N}\\s]"), " ").replace(Regex("\\s+"), " ").trim()
        finalTitle = finalTitle.replace(Regex("^(?:to|called|ek\\s+task)\\s+", RegexOption.IGNORE_CASE), "").trim()

        if (finalTitle.isNotEmpty()) {
            finalTitle = finalTitle.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
        }

        return TaskDraft(
            title = finalTitle,
            dueDate = extractedDate,
            dueTime = extractedTime,
            hasDueTime = extractedTime != null,
            priority = priority,
            category = category,
            recurrence = recurrence,
            reminderOffsetMinutes = reminderOffsetMinutes,
            hasReminder = isReminderEnabled || reminderOffsetMinutes != null
        )
    }
}
