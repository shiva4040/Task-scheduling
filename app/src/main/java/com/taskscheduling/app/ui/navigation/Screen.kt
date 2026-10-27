package com.taskscheduling.app.ui.navigation

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Tasks : Screen("tasks")
    object CreateTask : Screen("create_task")
    object CreateWorkflow : Screen("create_workflow")
    object AddEditTask : Screen("add_edit_task?taskId={taskId}") {
        fun createRoute(taskId: String? = null) = if (taskId != null) "add_edit_task?taskId=$taskId" else "add_edit_task"
    }
    object Builder : Screen("builder?taskId={taskId}") {
        fun createRoute(taskId: String? = null) = if (taskId != null) "builder?taskId=$taskId" else "builder"
    }
    object Execution : Screen("execution/{taskId}") {
        fun createRoute(taskId: String) = "execution/$taskId"
    }
    object Assistant : Screen("assistant")
    object Voice : Screen("voice")
    object Accessibility : Screen("accessibility")
    object Family : Screen("family")
    object Activity : Screen("activity")
    object Scheduler : Screen("scheduler")
    object Settings : Screen("settings")
    object UseCases : Screen("use_cases")
    object Onboarding : Screen("onboarding")
    object AiPreview : Screen("ai_preview")
    object VoiceBotDiagnostics : Screen("voice_bot_diagnostics")
    object BotSettings : Screen("bot_settings")
    object Profile : Screen("profile")
}

