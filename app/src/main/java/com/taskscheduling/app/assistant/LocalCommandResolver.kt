package com.taskscheduling.app.assistant

/**
 * Concrete offline implementation of [CommandResolver].
 * Normalizes user input and matches it against structured local intent patterns without cloud AI.
 */
class LocalCommandResolver : CommandResolver {

    private data class IntentMatcher(
        val intent: CommandIntent,
        val patterns: List<Regex>
    )

    private val matchers = listOf(
        IntentMatcher(
            intent = CommandIntent.CHANGE_WALLPAPER,
            patterns = listOf(
                Regex("^(?:change|set|update|switch|modify|apply|replace)\\s+(?:(?:my|the|a|an|new|tablet|device|phone|current|screen|home)\\s+)*(?:wallpaper|background|wall paper)$"),
                Regex("^(?:change wallpaper|set wallpaper|update wallpaper|new wallpaper|set background|change background)$")
            )
        ),
        IntentMatcher(
            intent = CommandIntent.GO_BACK,
            patterns = listOf(
                Regex("^(?:go\\s+back|back|press\\s+back|go\\s+backwards|return\\s+back|take\\s+me\\s+back|go\\s+one\\s+step\\s+back)$")
            )
        ),
        IntentMatcher(
            intent = CommandIntent.GO_HOME,
            patterns = listOf(
                Regex("^(?:go\\s+home|home|press\\s+home|take\\s+me\\s+home|go\\s+to\\s+(?:the\\s+)?home(?:\\s+screen)?|home\\s+screen)$")
            )
        ),
        IntentMatcher(
            intent = CommandIntent.SWIPE_LEFT,
            patterns = listOf(
                Regex("^(?:swipe|move)\\s+(?:to\\s+(?:the\\s+)?|towards?\\s+(?:the\\s+)?)?left$")
            )
        ),
        IntentMatcher(
            intent = CommandIntent.SWIPE_RIGHT,
            patterns = listOf(
                Regex("^(?:swipe|move)\\s+(?:to\\s+(?:the\\s+)?|towards?\\s+(?:the\\s+)?)?right$")
            )
        ),
        IntentMatcher(
            intent = CommandIntent.SWIPE_UP,
            patterns = listOf(
                Regex("^(?:swipe|move)\\s+(?:to\\s+(?:the\\s+)?|towards?\\s+(?:the\\s+)?)?(?:up|upwards|top)$")
            )
        ),
        IntentMatcher(
            intent = CommandIntent.SWIPE_DOWN,
            patterns = listOf(
                Regex("^(?:swipe|move)\\s+(?:to\\s+(?:the\\s+)?|towards?\\s+(?:the\\s+)?)?(?:down|downwards|bottom)$")
            )
        ),
        IntentMatcher(
            intent = CommandIntent.SCROLL_UP,
            patterns = listOf(
                Regex("^scroll\\s+(?:up|upwards|to\\s+(?:the\\s+)?top|towards?\\s+(?:the\\s+)?top)$")
            )
        ),
        IntentMatcher(
            intent = CommandIntent.SCROLL_DOWN,
            patterns = listOf(
                Regex("^scroll\\s+(?:down|downwards|to\\s+(?:the\\s+)?bottom|towards?\\s+(?:the\\s+)?bottom)$")
            )
        ),
        IntentMatcher(
            intent = CommandIntent.OPEN_YOUTUBE,
            patterns = listOf(
                Regex("^(?:open|launch|start|go\\s+to)\\s+(?:the\\s+)?youtube(?:\\s+app)?$")
            )
        ),
        IntentMatcher(
            intent = CommandIntent.OPEN_SETTINGS,
            patterns = listOf(
                Regex("^(?:open|launch|start|go\\s+to)\\s+(?:the\\s+|android\\s+)?settings(?:\\s+app)?$")
            )
        )
    )

    override fun resolve(command: String): CommandIntent? {
        val normalized = normalize(command)
        if (normalized.isBlank()) return null

        val stripped = stripConversationalPrefix(normalized)
        val candidates = if (stripped.isNotBlank() && stripped != normalized) {
            listOf(stripped, normalized)
        } else {
            listOf(normalized)
        }

        // 1. Direct device control and app matchers
        for (candidate in candidates) {
            for (matcher in matchers) {
                for (pattern in matcher.patterns) {
                    if (pattern.matches(candidate)) {
                        return matcher.intent
                    }
                }
            }
        }

        // 2. Task Intent parsing
        for (candidate in candidates) {
            val taskResult = TaskCommandParser.parse(candidate)
            when (taskResult) {
                is TaskParseResult.AddTask -> return CommandIntent.ADD_TASK
                is TaskParseResult.ShowTodayTasks -> return CommandIntent.SHOW_TODAY_TASKS
                is TaskParseResult.ShowCompletedTasks -> return CommandIntent.SHOW_COMPLETED_TASKS
                is TaskParseResult.ShowOverdueTasks -> return CommandIntent.SHOW_OVERDUE_TASKS
                is TaskParseResult.ShowTasks -> return CommandIntent.SHOW_TASKS
                is TaskParseResult.CompleteTask -> return CommandIntent.COMPLETE_TASK
                is TaskParseResult.DeleteTask -> return CommandIntent.DELETE_TASK
                is TaskParseResult.EditTask -> return CommandIntent.EDIT_TASK
                is TaskParseResult.RescheduleTask -> return CommandIntent.RESCHEDULE_TASK
                is TaskParseResult.SearchTasks -> return CommandIntent.SEARCH_TASKS
                is TaskParseResult.UnknownTaskCommand,
                is TaskParseResult.NotATaskCommand -> { /* continue */ }
            }
        }

        return null
    }

    companion object {
        private val PREFIX_REGEX = Regex(
            "^(?:hey\\s+assistant\\s+|assistant\\s+|could\\s+you\\s+please\\s+|can\\s+you\\s+please\\s+|would\\s+you\\s+please\\s+|i\\s+want\\s+you\\s+to\\s+|i\\s+want\\s+to\\s+|could\\s+you\\s+|can\\s+you\\s+|would\\s+you\\s+|please\\s+|give\\s+me\\s+a\\s+|hello\\s+|hi\\s+|hey\\s+)+",
            RegexOption.IGNORE_CASE
        )

        /**
         * Safely strips common polite and conversational prefixes while preserving
         * the underlying command intent.
         */
        fun stripConversationalPrefix(text: String): String {
            return PREFIX_REGEX.replace(text.trim(), "").trim()
        }

        /**
         * Normalizes command string:
         * 1. Lowercases text
         * 2. Replaces punctuation with space (preserves ASCII letters, digits, and whitespace)
         * 3. Collapses repeated whitespace into single space
         * 4. Trims leading and trailing spaces
         */
        fun normalize(raw: String): String {
            if (raw.isBlank()) return ""
            var result = raw.lowercase()
            result = result.replace(Regex("[^a-z0-9\\s]"), " ")
            result = result.replace(Regex("\\s+"), " ")
            return result.trim()
        }

        private val GREETING_REGEXES = listOf(
            Regex("^(?:hello|hi|hey|good\\s+(?:morning|afternoon|evening)|how\\s+are\\s+you)(?:\\s+(?:there|assistant|bot))?$")
        )

        fun isGreeting(text: String): Boolean {
            val normalized = normalize(text)
            return GREETING_REGEXES.any { it.matches(normalized) }
        }
    }
}
