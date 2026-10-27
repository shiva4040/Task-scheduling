package com.taskscheduling.app.ui.navigation

import com.taskscheduling.app.domain.model.TaskDraft

sealed class NavigationTarget {
    data class AddTask(val draft: TaskDraft) : NavigationTarget()
    data class Tasks(val tab: String = "ALL") : NavigationTarget()
    data class EditTask(val taskId: String) : NavigationTarget()
    object Diagnostics : NavigationTarget()
    object BotSettings : NavigationTarget()
}
