package com.taskscheduling.app.assistant

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import com.taskscheduling.app.domain.model.Task
import java.util.Calendar

/**
 * Manages scheduling and cancelling local alarms for tasks using AlarmManager.
 */
class TaskNotificationScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    init {
        TaskReminderReceiver.createNotificationChannel(context)
    }

    /**
     * Schedules a local notification reminder for the given task.
     */
    fun scheduleReminder(task: Task) {
        if (!task.notificationEnabled || !task.hasReminder) return
        val targetEpochMs = calculateReminderEpoch(task) ?: return

        // Do not schedule reminders in the past
        if (targetEpochMs <= System.currentTimeMillis()) return

        val intent = Intent(context, TaskReminderReceiver::class.java).apply {
            putExtra(TaskReminderReceiver.EXTRA_TASK_ID, task.id)
            putExtra(TaskReminderReceiver.EXTRA_TASK_TITLE, task.title)
            putExtra(TaskReminderReceiver.EXTRA_TASK_DESC, task.description)
            putExtra(TaskReminderReceiver.EXTRA_TASK_PRIORITY, task.priority.name)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            task.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    targetEpochMs,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    targetEpochMs,
                    pendingIntent
                )
            }
        } catch (e: SecurityException) {
            // Android 12+ exact alarm permission or notification permission
            e.printStackTrace()
            alarmManager.set(
                AlarmManager.RTC_WAKEUP,
                targetEpochMs,
                pendingIntent
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Cancels pending alarm and dismisses active notification for the given task ID.
     */
    fun cancelReminder(taskId: String) {
        val intent = Intent(context, TaskReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            taskId.hashCode(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )

        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }

        val notificationManager = NotificationManagerCompat.from(context)
        notificationManager.cancel(taskId.hashCode())
    }

    /**
     * Reschedules reminders for all active tasks.
     */
    fun rescheduleAll(tasks: List<Task>) {
        for (task in tasks) {
            if (!task.isCompleted && task.hasReminder && task.notificationEnabled) {
                scheduleReminder(task)
            } else {
                cancelReminder(task.id)
            }
        }
    }

    /**
     * Calculates the target epoch timestamp in milliseconds for when the reminder should fire.
     */
    fun calculateReminderEpoch(task: Task): Long? {
        if (task.reminderTimestamp != null && task.reminderTimestamp > 0) {
            return task.reminderTimestamp
        }

        val calendar = Calendar.getInstance()
        val dateStr = task.dueDate?.trim()?.lowercase() ?: "today"

        when {
            dateStr.contains("tomorrow") -> calendar.add(Calendar.DAY_OF_YEAR, 1)
            dateStr.contains("monday") -> setDayOfWeek(calendar, Calendar.MONDAY)
            dateStr.contains("tuesday") -> setDayOfWeek(calendar, Calendar.TUESDAY)
            dateStr.contains("wednesday") -> setDayOfWeek(calendar, Calendar.WEDNESDAY)
            dateStr.contains("thursday") -> setDayOfWeek(calendar, Calendar.THURSDAY)
            dateStr.contains("friday") -> setDayOfWeek(calendar, Calendar.FRIDAY)
            dateStr.contains("saturday") -> setDayOfWeek(calendar, Calendar.SATURDAY)
            dateStr.contains("sunday") -> setDayOfWeek(calendar, Calendar.SUNDAY)
            dateStr.contains("next week") -> calendar.add(Calendar.WEEK_OF_YEAR, 1)
            dateStr.matches(Regex("^[0-9]{4}-[0-9]{2}-[0-9]{2}$")) -> {
                val parts = dateStr.split("-")
                calendar.set(Calendar.YEAR, parts[0].toInt())
                calendar.set(Calendar.MONTH, parts[1].toInt() - 1)
                calendar.set(Calendar.DAY_OF_MONTH, parts[2].toInt())
            }
            // "Today" or default
            else -> {}
        }

        val timeStr = task.dueTime?.trim()
        if (timeStr != null && timeStr.contains(":")) {
            val parts = timeStr.split(":")
            val hour = parts[0].trim().toIntOrNull() ?: 9
            val min = parts[1].trim().take(2).toIntOrNull() ?: 0
            calendar.set(Calendar.HOUR_OF_DAY, hour)
            calendar.set(Calendar.MINUTE, min)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
        } else {
            calendar.set(Calendar.HOUR_OF_DAY, 9)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
        }

        val offsetMinutes = task.reminderOffsetMinutes ?: 0
        if (offsetMinutes > 0) {
            calendar.add(Calendar.MINUTE, -offsetMinutes)
        }

        return calendar.timeInMillis
    }

    private fun setDayOfWeek(calendar: Calendar, targetDayOfWeek: Int) {
        val currentDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        var daysToAdd = targetDayOfWeek - currentDayOfWeek
        if (daysToAdd <= 0) daysToAdd += 7
        calendar.add(Calendar.DAY_OF_YEAR, daysToAdd)
    }
}
