package com.taskscheduling.app.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import com.taskscheduling.app.MainActivity
import android.view.accessibility.AccessibilityEvent
import com.taskscheduling.app.assistant.TaskNotificationScheduler
import com.taskscheduling.app.data.repository.TaskPersistenceRepository
import com.taskscheduling.app.data.repository.TaskRepositoryImpl
import com.taskscheduling.app.recording.RecordingManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class TaskSchedulingAccessibilityService : AccessibilityService() {

    companion object {
        private val _isServiceConnected = MutableStateFlow(false)
        val isServiceConnected: StateFlow<Boolean> = _isServiceConnected.asStateFlow()

        var instance: TaskSchedulingAccessibilityService? = null
            private set
    }

    lateinit var overlayManager: AccessibilityOverlayManager
        private set
    lateinit var persistenceRepository: com.taskscheduling.app.data.repository.WorkflowPersistenceRepository
        private set
    lateinit var taskPersistenceRepository: TaskPersistenceRepository
        private set
    lateinit var taskRepository: TaskRepositoryImpl
        private set
    lateinit var notificationScheduler: TaskNotificationScheduler
        private set
    lateinit var automationEngine: com.taskscheduling.app.automation.AndroidAccessibilityAutomationEngine
        private set
    lateinit var actionExecutor: com.taskscheduling.app.assistant.AssistantActionExecutor
        private set
    lateinit var assistantController: com.taskscheduling.app.assistant.OfflineAssistantController
        private set

    override fun onCreate() {
        super.onCreate()
        overlayManager = AccessibilityOverlayManager(this)
        persistenceRepository = com.taskscheduling.app.data.repository.WorkflowPersistenceRepository(this)
        taskPersistenceRepository = TaskPersistenceRepository(this, persistenceRepository)
        taskRepository = TaskRepositoryImpl(persistenceRepository, taskPersistenceRepository)
        notificationScheduler = TaskNotificationScheduler(this)
        automationEngine = com.taskscheduling.app.automation.AndroidAccessibilityAutomationEngine(this)
        actionExecutor = com.taskscheduling.app.assistant.AndroidAssistantActionExecutor(
            context = this,
            serviceProvider = { this }
        )
        val commandResolver = com.taskscheduling.app.assistant.LocalCommandResolver()
        val workflowResolver = com.taskscheduling.app.assistant.LocalWorkflowResolver(persistenceRepository)
        assistantController = com.taskscheduling.app.assistant.OfflineAssistantController(
            commandResolver = commandResolver,
            workflowResolver = workflowResolver,
            automationEngine = automationEngine,
            actionExecutor = actionExecutor,
            persistenceRepository = persistenceRepository,
            taskRepository = taskRepository,
            taskNotificationScheduler = notificationScheduler,
            deviceProfileProvider = { AccessibilityUtils.getDeviceProfile(this) }
        ).apply {
            onOpenAddTaskRequested = { draft ->
                try {
                    val intent = Intent(this@TaskSchedulingAccessibilityService, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        putExtra("navigate_to", "add_edit_task")
                        putExtra("draft_title", draft.title)
                        putExtra("draft_due_date", draft.dueDate)
                        putExtra("draft_due_time", draft.dueTime)
                        putExtra("draft_has_due_time", draft.hasDueTime)
                        putExtra("draft_priority", draft.priority.name)
                        putExtra("draft_category", draft.category.name)
                        putExtra("draft_recurrence", draft.recurrence.name)
                        draft.reminderOffsetMinutes?.let { putExtra("draft_reminder_offset", it) }
                        putExtra("draft_has_reminder", draft.hasReminder)
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            onOpenTasksRequested = { tab ->
                try {
                    val intent = Intent(this@TaskSchedulingAccessibilityService, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        putExtra("navigate_to", "tasks")
                        putExtra("tasks_tab", tab)
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            onOpenEditTaskRequested = { taskId ->
                try {
                    val intent = Intent(this@TaskSchedulingAccessibilityService, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        putExtra("navigate_to", "edit_task")
                        putExtra("task_id", taskId)
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        overlayManager.setAssistantController(assistantController)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _isServiceConnected.value = true
        overlayManager.showFloatingAssistantShortcut()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        _isServiceConnected.value = false
        overlayManager.clearAll()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        _isServiceConnected.value = false
        overlayManager.clearAll()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        if (RecordingManager.isRecording.value && RecordingManager.enableNodeClickAutoDetect) {
            // Ignore events originating from our own app overlays to avoid recording toolbar clicks
            if (event.packageName == packageName) return

            when (event.eventType) {
                AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                    try {
                        val node = event.source ?: return
                        val rect = Rect()
                        node.getBoundsInScreen(rect)
                        if (!rect.isEmpty && rect.width() > 0 && rect.height() > 0) {
                            val cx = rect.centerX()
                            val cy = rect.centerY()
                            val label = (node.text ?: node.contentDescription ?: "").toString().trim()
                            RecordingManager.recordTap(cx, cy, label)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                AccessibilityEvent.TYPE_VIEW_LONG_CLICKED -> {
                    try {
                        val node = event.source ?: return
                        val rect = Rect()
                        node.getBoundsInScreen(rect)
                        if (!rect.isEmpty && rect.width() > 0 && rect.height() > 0) {
                            val cx = rect.centerX()
                            val cy = rect.centerY()
                            val label = (node.text ?: node.contentDescription ?: "").toString().trim()
                            RecordingManager.recordLongPress(cx, cy, 900L, label)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    override fun onInterrupt() {
        // Do NOT destroy overlays on accessibility feedback interruption (e.g. app switching or user touch).
        // Only stop active text-to-speech audio feedback.
        try {
            overlayManager.speechOutputManager.stop()
        } catch (_: Exception) {}
    }

    fun showVisualTouchDot(x: Int, y: Int, label: String = "X: $x, Y: $y") {
        overlayManager.showTouchDot(x, y, label)
    }

    suspend fun dispatchGestureSuspend(gesture: GestureDescription, timeoutMs: Long = 5000L): Boolean {
        val deferred = CompletableDeferred<Boolean>()
        val handler = Handler(Looper.getMainLooper())

        val timeoutRunnable = Runnable {
            if (deferred.isActive) {
                deferred.complete(false)
            }
        }
        handler.postDelayed(timeoutRunnable, timeoutMs)

        val callback = object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                handler.removeCallbacks(timeoutRunnable)
                if (deferred.isActive) deferred.complete(true)
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                handler.removeCallbacks(timeoutRunnable)
                if (deferred.isActive) deferred.complete(false)
            }
        }

        try {
            val dispatched = dispatchGesture(gesture, callback, null)
            if (!dispatched) {
                handler.removeCallbacks(timeoutRunnable)
                return false
            }
        } catch (e: Exception) {
            handler.removeCallbacks(timeoutRunnable)
            e.printStackTrace()
            return false
        }

        return deferred.await()
    }

    suspend fun performTap(x: Int, y: Int, durationMs: Long = 100L): Boolean {
        val path = Path().apply {
            moveTo(x.toFloat(), y.toFloat())
        }
        val stroke = GestureDescription.StrokeDescription(path, 0L, durationMs.coerceAtLeast(1L))
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGestureSuspend(gesture)
    }

    suspend fun performSwipe(
        startX: Int,
        startY: Int,
        endX: Int,
        endY: Int,
        durationMs: Long = 300L
    ): Boolean {
        val path = Path().apply {
            moveTo(startX.toFloat(), startY.toFloat())
            lineTo(endX.toFloat(), endY.toFloat())
        }
        val stroke = GestureDescription.StrokeDescription(path, 0L, durationMs.coerceAtLeast(10L))
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGestureSuspend(gesture)
    }

    suspend fun performLongPress(x: Int, y: Int, durationMs: Long = 900L): Boolean {
        return performTap(x, y, durationMs)
    }

    fun performBack(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_BACK)
    }

    fun performHome(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_HOME)
    }
}
