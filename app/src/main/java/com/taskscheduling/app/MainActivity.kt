package com.taskscheduling.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import com.taskscheduling.app.domain.model.TaskCategory
import com.taskscheduling.app.domain.model.TaskDraft
import com.taskscheduling.app.domain.model.TaskPriority
import com.taskscheduling.app.domain.model.TaskRecurrence
import com.taskscheduling.app.ui.navigation.NavigationTarget

class MainActivity : ComponentActivity() {
    private val navigationTargetState = mutableStateOf<NavigationTarget?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        setContent {
            TaskSchedulingApp(
                navigationTargetState = navigationTargetState
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        val destination = intent.getStringExtra("navigate_to") ?: return
        when (destination) {
            "add_edit_task" -> {
                val title = intent.getStringExtra("draft_title") ?: ""
                val dueDate = intent.getStringExtra("draft_due_date")
                val dueTime = intent.getStringExtra("draft_due_time")
                val hasDueTime = intent.getBooleanExtra("draft_has_due_time", dueTime != null)
                val priorityStr = intent.getStringExtra("draft_priority")
                val categoryStr = intent.getStringExtra("draft_category")
                val recurrenceStr = intent.getStringExtra("draft_recurrence")
                val reminderOffset = if (intent.hasExtra("draft_reminder_offset")) intent.getIntExtra("draft_reminder_offset", 0) else null
                val hasReminder = intent.getBooleanExtra("draft_has_reminder", reminderOffset != null)

                val priority = try {
                    if (priorityStr != null) TaskPriority.valueOf(priorityStr) else TaskPriority.MEDIUM
                } catch (e: Exception) { TaskPriority.MEDIUM }

                val category = try {
                    if (categoryStr != null) TaskCategory.valueOf(categoryStr) else TaskCategory.CUSTOM
                } catch (e: Exception) { TaskCategory.CUSTOM }

                val recurrence = try {
                    if (recurrenceStr != null) TaskRecurrence.valueOf(recurrenceStr) else TaskRecurrence.NONE
                } catch (e: Exception) { TaskRecurrence.NONE }

                val draft = TaskDraft(
                    title = title,
                    dueDate = dueDate,
                    dueTime = dueTime,
                    hasDueTime = hasDueTime,
                    priority = priority,
                    category = category,
                    recurrence = recurrence,
                    reminderOffsetMinutes = reminderOffset,
                    hasReminder = hasReminder
                )
                navigationTargetState.value = NavigationTarget.AddTask(draft)
            }
            "tasks" -> {
                val tab = intent.getStringExtra("tasks_tab") ?: "ALL"
                navigationTargetState.value = NavigationTarget.Tasks(tab)
            }
            "edit_task" -> {
                val taskId = intent.getStringExtra("task_id")
                if (taskId != null) {
                    navigationTargetState.value = NavigationTarget.EditTask(taskId)
                }
            }
            "diagnostics" -> {
                navigationTargetState.value = NavigationTarget.Diagnostics
            }
            "bot_settings" -> {
                navigationTargetState.value = NavigationTarget.BotSettings
            }
        }
    }
}
