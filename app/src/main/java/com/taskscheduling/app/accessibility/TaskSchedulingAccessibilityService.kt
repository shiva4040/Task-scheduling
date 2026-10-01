package com.taskscheduling.app.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
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

    override fun onCreate() {
        super.onCreate()
        overlayManager = AccessibilityOverlayManager(this)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _isServiceConnected.value = true
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

        // When user is recording, only detect clicked UI bounds if auto-detect is explicitly enabled.
        // By default (V1), manual precision crosshair target is the reliable source of truth.
        if (RecordingManager.isRecording.value && RecordingManager.enableNodeClickAutoDetect) {
            when (event.eventType) {
                AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                    try {
                        val node = event.source ?: return
                        val rect = Rect()
                        node.getBoundsInScreen(rect)
                        if (!rect.isEmpty && rect.width() > 0 && rect.height() > 0) {
                            val cx = rect.centerX()
                            val cy = rect.centerY()
                            RecordingManager.recordTap(cx, cy)
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
                            RecordingManager.recordLongPress(cx, cy, 900L)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    override fun onInterrupt() {
        overlayManager.clearAll()
    }

    /**
     * Shows a real-device visual touch dot at physical screen coordinate (x, y).
     * The dot is a visual representation of the coordinate and does not block touches.
     */
    fun showVisualTouchDot(x: Int, y: Int, label: String = "X: $x, Y: $y") {
        overlayManager.showTouchDot(x, y, label)
    }

    /**
     * Dispatches a real Android gesture using AccessibilityService.dispatchGesture.
     * Suspends until the system gesture callback confirms completion or cancellation.
     * Enforces a 5000ms safety timeout so execution never hangs indefinitely.
     */
    suspend fun dispatchGestureSuspend(gesture: GestureDescription, timeoutMs: Long = 5000L): Boolean {
        val deferred = CompletableDeferred<Boolean>()
        val handler = Handler(Looper.getMainLooper())

        val callback = object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                super.onCompleted(gestureDescription)
                deferred.complete(true)
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                super.onCancelled(gestureDescription)
                deferred.complete(false)
            }
        }

        val dispatched = dispatchGesture(gesture, callback, handler)
        if (!dispatched) {
            return false
        }

        return try {
            kotlinx.coroutines.withTimeoutOrNull(timeoutMs) {
                deferred.await()
            } ?: false
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Executes a real physical tap at (x, y) via AccessibilityService.
     */
    suspend fun performTap(x: Int, y: Int, durationMs: Long = 100L): Boolean {
        val path = Path().apply {
            moveTo(x.toFloat(), y.toFloat())
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs.coerceAtLeast(50L))
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGestureSuspend(gesture)
    }

    /**
     * Executes a real physical long press at (x, y) via AccessibilityService.
     */
    suspend fun performLongPress(x: Int, y: Int, durationMs: Long = 900L): Boolean {
        val path = Path().apply {
            moveTo(x.toFloat(), y.toFloat())
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs.coerceAtLeast(500L))
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGestureSuspend(gesture)
    }

    /**
     * Executes a real physical swipe from (startX, startY) to (endX, endY).
     */
    suspend fun performSwipe(startX: Int, startY: Int, endX: Int, endY: Int, durationMs: Long = 500L): Boolean {
        val path = Path().apply {
            moveTo(startX.toFloat(), startY.toFloat())
            lineTo(endX.toFloat(), endY.toFloat())
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs.coerceAtLeast(100L))
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGestureSuspend(gesture)
    }

    /**
     * Executes a system back action via AccessibilityService.
     */
    fun performBack(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_BACK)
    }
}
