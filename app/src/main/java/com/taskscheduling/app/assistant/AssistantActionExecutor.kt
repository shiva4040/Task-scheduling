package com.taskscheduling.app.assistant

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.taskscheduling.app.accessibility.TaskSchedulingAccessibilityService
import com.taskscheduling.app.domain.model.DeviceProfile

sealed class ActionResult {
    data class Success(val message: String = "Done.") : ActionResult()
    data class Failure(val errorMessage: String) : ActionResult()
}

/**
 * Configurable parameters for dynamic gesture and scroll calculation.
 * Ensures consistent, screen-edge safe execution across all devices and orientations.
 */
object GestureConfig {
    const val SWIPE_DURATION_MS = 350L
    const val SCROLL_DURATION_MS = 450L

    const val SWIPE_HORIZONTAL_START_PCT = 0.75f
    const val SWIPE_HORIZONTAL_END_PCT = 0.25f
    const val SWIPE_VERTICAL_START_PCT = 0.75f
    const val SWIPE_VERTICAL_END_PCT = 0.25f

    const val SCROLL_VERTICAL_START_PCT = 0.70f
    const val SCROLL_VERTICAL_END_PCT = 0.30f
}

data class CalculatedGesture(
    val startX: Int,
    val startY: Int,
    val endX: Int,
    val endY: Int,
    val durationMs: Long
)

object GestureCalculator {
    fun calculate(intent: CommandIntent, width: Int, height: Int): CalculatedGesture? {
        if (width <= 0 || height <= 0) return null
        val w = width
        val h = height

        return when (intent) {
            CommandIntent.SWIPE_LEFT -> CalculatedGesture(
                startX = (w * GestureConfig.SWIPE_HORIZONTAL_START_PCT).toInt(),
                startY = (h * 0.50f).toInt(),
                endX = (w * GestureConfig.SWIPE_HORIZONTAL_END_PCT).toInt(),
                endY = (h * 0.50f).toInt(),
                durationMs = GestureConfig.SWIPE_DURATION_MS
            )
            CommandIntent.SWIPE_RIGHT -> CalculatedGesture(
                startX = (w * GestureConfig.SWIPE_HORIZONTAL_END_PCT).toInt(),
                startY = (h * 0.50f).toInt(),
                endX = (w * GestureConfig.SWIPE_HORIZONTAL_START_PCT).toInt(),
                endY = (h * 0.50f).toInt(),
                durationMs = GestureConfig.SWIPE_DURATION_MS
            )
            CommandIntent.SWIPE_UP -> CalculatedGesture(
                startX = (w * 0.50f).toInt(),
                startY = (h * GestureConfig.SWIPE_VERTICAL_START_PCT).toInt(),
                endX = (w * 0.50f).toInt(),
                endY = (h * GestureConfig.SWIPE_VERTICAL_END_PCT).toInt(),
                durationMs = GestureConfig.SWIPE_DURATION_MS
            )
            CommandIntent.SWIPE_DOWN -> CalculatedGesture(
                startX = (w * 0.50f).toInt(),
                startY = (h * GestureConfig.SWIPE_VERTICAL_END_PCT).toInt(),
                endX = (w * 0.50f).toInt(),
                endY = (h * GestureConfig.SWIPE_VERTICAL_START_PCT).toInt(),
                durationMs = GestureConfig.SWIPE_DURATION_MS
            )
            CommandIntent.SCROLL_UP -> CalculatedGesture(
                startX = (w * 0.50f).toInt(),
                startY = (h * GestureConfig.SCROLL_VERTICAL_START_PCT).toInt(),
                endX = (w * 0.50f).toInt(),
                endY = (h * GestureConfig.SCROLL_VERTICAL_END_PCT).toInt(),
                durationMs = GestureConfig.SCROLL_DURATION_MS
            )
            CommandIntent.SCROLL_DOWN -> CalculatedGesture(
                startX = (w * 0.50f).toInt(),
                startY = (h * GestureConfig.SCROLL_VERTICAL_END_PCT).toInt(),
                endX = (w * 0.50f).toInt(),
                endY = (h * GestureConfig.SCROLL_VERTICAL_START_PCT).toInt(),
                durationMs = GestureConfig.SCROLL_DURATION_MS
            )
            else -> null
        }
    }
}

/**
 * Clean abstraction executing pre-authorized direct device and app actions.
 * Only receives actions after explicit confirmation is recorded.
 */
interface AssistantActionExecutor {
    suspend fun executeAction(intent: CommandIntent, currentProfile: DeviceProfile): ActionResult
}

/**
 * Concrete Android implementation using AccessibilityService global actions,
 * dynamic gestures, and native Android package / intent launchers.
 */
class AndroidAssistantActionExecutor(
    private val context: Context? = null,
    private val serviceProvider: () -> TaskSchedulingAccessibilityService? = { TaskSchedulingAccessibilityService.instance }
) : AssistantActionExecutor {

    override suspend fun executeAction(intent: CommandIntent, currentProfile: DeviceProfile): ActionResult {
        return when (intent) {
            CommandIntent.GO_BACK -> executeBack()
            CommandIntent.GO_HOME -> executeHome()
            CommandIntent.SWIPE_LEFT,
            CommandIntent.SWIPE_RIGHT,
            CommandIntent.SWIPE_UP,
            CommandIntent.SWIPE_DOWN,
            CommandIntent.SCROLL_UP,
            CommandIntent.SCROLL_DOWN -> executeDynamicGesture(intent, currentProfile)
            CommandIntent.OPEN_YOUTUBE -> executeOpenYouTube()
            CommandIntent.OPEN_SETTINGS -> executeOpenSettings()
            CommandIntent.GREETING -> ActionResult.Success("Hello! How can I help you?")
            CommandIntent.CHANGE_WALLPAPER -> {
                ActionResult.Failure("Change Wallpaper must be executed through the recorded workflow engine.")
            }
            CommandIntent.ADD_TASK,
            CommandIntent.SHOW_TASKS,
            CommandIntent.SHOW_TODAY_TASKS,
            CommandIntent.SHOW_COMPLETED_TASKS,
            CommandIntent.SHOW_OVERDUE_TASKS,
            CommandIntent.COMPLETE_TASK,
            CommandIntent.DELETE_TASK,
            CommandIntent.EDIT_TASK,
            CommandIntent.RESCHEDULE_TASK,
            CommandIntent.SEARCH_TASKS -> {
                ActionResult.Success("Task action executed.")
            }
        }
    }

    private fun executeBack(): ActionResult {
        val service = serviceProvider()
            ?: return ActionResult.Failure("Accessibility Service is not connected.")

        AssistantLogger.i("Assistant", "Executing GO_BACK via GLOBAL_ACTION_BACK")
        val success = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        return if (success) {
            ActionResult.Success("Done.")
        } else {
            ActionResult.Failure("I couldn't go back.")
        }
    }

    private fun executeHome(): ActionResult {
        val service = serviceProvider()
            ?: return ActionResult.Failure("Accessibility Service is not connected.")

        AssistantLogger.i("Assistant", "Executing GO_HOME via GLOBAL_ACTION_HOME")
        val success = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
        return if (success) {
            ActionResult.Success("Done.")
        } else {
            ActionResult.Failure("I couldn't go to the home screen.")
        }
    }

    private suspend fun executeDynamicGesture(intent: CommandIntent, currentProfile: DeviceProfile): ActionResult {
        val service = serviceProvider()
            ?: return ActionResult.Failure("Accessibility Service is not connected.")

        val gesture = GestureCalculator.calculate(intent, currentProfile.screenWidth, currentProfile.screenHeight)
            ?: return ActionResult.Failure("I couldn't calculate coordinates for gesture.")

        AssistantLogger.i(
            "Assistant",
            "GESTURE: direction=$intent start=(${gesture.startX}, ${gesture.startY}) end=(${gesture.endX}, ${gesture.endY}) duration=${gesture.durationMs}ms orientation=${currentProfile.orientation}"
        )

        val success = service.performSwipe(
            startX = gesture.startX,
            startY = gesture.startY,
            endX = gesture.endX,
            endY = gesture.endY,
            durationMs = gesture.durationMs
        )

        return if (success) {
            ActionResult.Success("Done.")
        } else {
            val errorMsg = if (intent == CommandIntent.SCROLL_UP || intent == CommandIntent.SCROLL_DOWN) {
                "I couldn't perform that scroll."
            } else {
                "I couldn't perform that swipe."
            }
            ActionResult.Failure(errorMsg)
        }
    }

    private fun executeOpenYouTube(): ActionResult {
        val ctx = context
            ?: return ActionResult.Failure("Application context is unavailable.")

        val packageManager = ctx.packageManager
        val candidatePackages = listOf(
            "com.google.android.youtube",
            "com.google.android.youtube.tv",
            "com.google.android.apps.youtube.mango"
        )

        var launchIntent: Intent? = null
        for (pkg in candidatePackages) {
            try {
                launchIntent = packageManager.getLaunchIntentForPackage(pkg)
                if (launchIntent != null) break
            } catch (_: Exception) {}
        }

        if (launchIntent == null) {
            AssistantLogger.w("Assistant", "YouTube package not found on device.")
            return ActionResult.Failure("I couldn't find YouTube on this device.")
        }

        return try {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(launchIntent)
            AssistantLogger.i("Assistant", "YouTube launched successfully.")
            ActionResult.Success("Opening YouTube.")
        } catch (e: Exception) {
            AssistantLogger.e("Assistant", "Failed to launch YouTube: ${e.message}")
            ActionResult.Failure("I couldn't find YouTube on this device.")
        }
    }

    private fun executeOpenSettings(): ActionResult {
        val ctx = context
            ?: return ActionResult.Failure("Application context is unavailable.")

        return try {
            val intent = Intent(Settings.ACTION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(intent)
            AssistantLogger.i("Assistant", "Settings launched successfully.")
            ActionResult.Success("Opening Settings.")
        } catch (e: Exception) {
            AssistantLogger.e("Assistant", "Failed to open Settings: ${e.message}")
            ActionResult.Failure("I couldn't open Settings.")
        }
    }
}
