package com.taskscheduling.app.accessibility

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.taskscheduling.app.assistant.ActivityDotState
import com.taskscheduling.app.assistant.ActivityDotView
import com.taskscheduling.app.assistant.FloatingOrbView
import com.taskscheduling.app.assistant.AssistantChatMessage
import com.taskscheduling.app.assistant.AssistantUiState
import com.taskscheduling.app.assistant.OfflineAssistantController
import com.taskscheduling.app.domain.model.ActionType
import com.taskscheduling.app.domain.model.AssistantIconStyle
import com.taskscheduling.app.domain.model.BubbleColorPreset
import com.taskscheduling.app.domain.model.ExpandedWindowSize
import com.taskscheduling.app.domain.model.WindowTheme
import com.taskscheduling.app.recording.RecordingManager
import com.taskscheduling.app.voice.SpeechEngineError
import com.taskscheduling.app.voice.VoiceInputManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Manages visual overlay elements using AccessibilityService overlay capabilities (TYPE_ACCESSIBILITY_OVERLAY).
 * All visual indicators are non-touchable and strictly pass touches through to underlying apps.
 */
class AccessibilityOverlayManager(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private var activeRecordingToolbar: View? = null
    private var activeTargetOverlay: View? = null
    private var activeSwipeOverlay: View? = null
    private var currentTargetCoordinate: Pair<Int, Int>? = null
    private var onTargetCoordinateChanged: ((Int, Int) -> Unit)? = null
    private var onTargetDismissed: (() -> Unit)? = null
    private val activeTouchDots = mutableListOf<View>()

    private var activeFloatingRecorderPanel: View? = null
    private var activeFloatingRecorderHandle: View? = null
    private var activeTextInputOverlay: View? = null
    private var floatingRecorderX: Int = -1
    private var floatingRecorderY: Int = -1
    private var floatingRecorderJob: Job? = null

    private var activeFloatingShortcut: View? = null
    val isFloatingShortcutVisible: Boolean get() = activeFloatingShortcut != null
    private var activeAssistantPanel: View? = null
    val isAssistantPanelVisible: Boolean get() = activeAssistantPanel != null
    private var assistantController: OfflineAssistantController? = null
    val voiceInputManager by lazy { VoiceInputManager(context) }
    val speechOutputManager by lazy { com.taskscheduling.app.voice.AndroidSpeechOutputManager(context) }
    var continuousVoiceController: com.taskscheduling.app.voice.ContinuousVoiceController? = null
        private set
    private var assistantCollectorJob: Job? = null
    private var shortcutCollectorJob: Job? = null
    private var thinkingJob: Job? = null
    val botSettingsRepository by lazy { com.taskscheduling.app.data.repository.BotSettingsRepository(context) }

    private fun startThinkingAnimation(statusView: TextView) {
        if (thinkingJob != null) return
        thinkingJob = CoroutineScope(Dispatchers.Main).launch {
            var dotCount = 1
            while (isActive) {
                val dots = ".".repeat(dotCount)
                statusView.text = "Thinking$dots"
                dotCount = if (dotCount >= 3) 1 else dotCount + 1
                kotlinx.coroutines.delay(350L)
            }
        }
    }

    private fun stopThinkingAnimation() {
        thinkingJob?.cancel()
        thinkingJob = null
    }

    private fun dpToPx(dp: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            context.resources.displayMetrics
        ).toInt()
    }

    /**
     * Shows a real-device visual touch dot at physical screen coordinate (x, y).
     * The overlay is FLAG_NOT_TOUCHABLE and does not block touches.
     * Automatically disappears after ~1.2 seconds.
     */
    fun showTouchDot(x: Int, y: Int, label: String = "X: $x, Y: $y") {
        mainHandler.post {
            try {
                val dotView = VisualDotView(context, x, y, label)

                // Dot view measures 220px wide by 160px high, with center at (110, 60).
                // Positioning at (x - 110, y - 60) aligns the visual center with (x, y) to the exact pixel.
                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.TOP or Gravity.START
                    this.x = x - 110
                    this.y = y - 60
                }

                windowManager.addView(dotView, params)
                activeTouchDots.add(dotView)

                // Animate and auto-remove
                dotView.startAnimation {
                    try {
                        windowManager.removeView(dotView)
                        activeTouchDots.remove(dotView)
                    } catch (_: Exception) {}
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Shows a persistent, reusable floating recording panel or delegates to collapsed orb handle.
     */
    @SuppressLint("ClickableViewAccessibility", "SetTextI18n")
    fun showFloatingRecorder(
        onFinish: () -> Unit,
        onCancel: () -> Unit
    ) {
        mainHandler.post {
            // If already visible, do not duplicate
            if (activeFloatingRecorderPanel != null) return@post
            hideFloatingRecorderHandle()

            val screenMetrics = context.resources.displayMetrics
            val isDark = true

            val panel = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dpToPx(12f), dpToPx(8f), dpToPx(12f), dpToPx(10f))
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(Color.parseColor("#F20F172A")) // Modern dark glass
                    cornerRadius = dpToPx(16f).toFloat()
                    setStroke(dpToPx(1.5f), Color.parseColor("#38BDF8"))
                }
                elevation = 22f
            }

            // Top Status Bar: Mini Orb + Status + Step + Time + Collapse + Cancel
            val topBar = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }

            // Mini living light orb
            val miniOrb = FloatingOrbView(context).apply {
                state = if (RecordingManager.sessionState.value == com.taskscheduling.app.domain.model.SessionState.RECORDING) {
                    ActivityDotState.LISTENING
                } else {
                    ActivityDotState.IDLE
                }
                layoutParams = LinearLayout.LayoutParams(dpToPx(18f), dpToPx(18f)).apply {
                    setMargins(0, 0, dpToPx(6f), 0)
                }
            }
            topBar.addView(miniOrb)

            val statusBadge = TextView(context).apply {
                val isRec = RecordingManager.sessionState.value == com.taskscheduling.app.domain.model.SessionState.RECORDING
                text = if (isRec) "● REC" else "⏸ PAUSED"
                setTextColor(if (isRec) Color.parseColor("#EF4444") else Color.parseColor("#F59E0B"))
                textSize = 11.5f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
            topBar.addView(statusBadge)

            val stepCountText = TextView(context).apply {
                val count = RecordingManager.recordedSteps.value.size
                text = " • $count step" + (if (count != 1) "s" else "")
                setTextColor(Color.parseColor("#38BDF8"))
                textSize = 11.5f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
            topBar.addView(stepCountText)

            val timerText = TextView(context).apply {
                val totalSec = RecordingManager.elapsedSeconds.value
                val mins = totalSec / 60
                val secs = totalSec % 60
                text = String.format(" • %02d:%02d", mins, secs)
                setTextColor(Color.parseColor("#94A3B8"))
                textSize = 11f
                typeface = android.graphics.Typeface.MONOSPACE
            }
            topBar.addView(timerText)

            // Flexible spacer
            val spacer = View(context).apply {
                layoutParams = LinearLayout.LayoutParams(0, 0, 1f)
            }
            topBar.addView(spacer)

            // Minimize / Collapse button (collapses to luminous orb handle)
            val btnMinimize = TextView(context).apply {
                text = " – "
                setTextColor(Color.parseColor("#94A3B8"))
                textSize = 14f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                setPadding(dpToPx(6f), dpToPx(2f), dpToPx(6f), dpToPx(2f))
                setOnClickListener {
                    hideFloatingRecorderPanel()
                    showFloatingRecorderCollapsed(onFinish, onCancel)
                }
            }
            topBar.addView(btnMinimize)

            // Cancel button
            val btnClose = TextView(context).apply {
                text = "✕"
                setTextColor(Color.parseColor("#EF4444"))
                textSize = 12f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                setPadding(dpToPx(6f), dpToPx(2f), dpToPx(4f), dpToPx(2f))
                setOnClickListener {
                    hideFloatingRecorder()
                    RecordingManager.cancelRecording()
                    onCancel()
                }
            }
            topBar.addView(btnClose)
            panel.addView(topBar)

            // Guide Hint Text
            val guideText = TextView(context).apply {
                text = "Drag 🎯 Target over UI, or tap actions below"
                setTextColor(Color.parseColor("#94A3B8"))
                textSize = 9.5f
                setPadding(0, dpToPx(3f), 0, dpToPx(4f))
            }
            panel.addView(guideText)

            // Pill button generator helper
            fun createPillButton(text: String, bgColor: Int, textColor: Int, onClick: () -> Unit): TextView {
                return TextView(context).apply {
                    this.text = text
                    this.setTextColor(textColor)
                    textSize = 10.5f
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                    setPadding(dpToPx(8f), dpToPx(4f), dpToPx(8f), dpToPx(4f))
                    background = android.graphics.drawable.GradientDrawable().apply {
                        setColor(bgColor)
                        cornerRadius = dpToPx(10f).toFloat()
                    }
                    val params = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { setMargins(dpToPx(2.5f), dpToPx(2f), dpToPx(2.5f), dpToPx(2f)) }
                    layoutParams = params
                    setOnClickListener { onClick() }
                }
            }

            // Action Tools Scrollable Row (Phase 4 multi-action support)
            val actionScroll = HorizontalScrollView(context).apply {
                isHorizontalScrollBarEnabled = false
            }
            val actionRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            var btnRecordTap: TextView? = null
            var btnHold: TextView? = null

            // 🎯 Target Crosshair
            val btnTarget = createPillButton("🎯 Target", Color.parseColor("#1E293B"), Color.parseColor("#38BDF8")) {
                if (activeTargetOverlay != null) {
                    hideTargetCrosshair()
                    btnRecordTap?.visibility = View.GONE
                    btnHold?.visibility = View.GONE
                    guideText.text = "Target crosshair hidden"
                } else {
                    showTargetCrosshair(
                        onMoved = { tx, ty ->
                            btnRecordTap?.text = "⏺ Tap ($tx, $ty)"
                        },
                        onDismissed = {
                            btnRecordTap?.visibility = View.GONE
                            btnHold?.visibility = View.GONE
                            guideText.text = "Drag 🎯 Target over UI, or tap actions below"
                        }
                    )
                    btnRecordTap?.visibility = View.VISIBLE
                    btnHold?.visibility = View.VISIBLE
                    val (ix, iy) = calculateCurrentTargetCoordinate()
                    btnRecordTap?.text = "⏺ Tap ($ix, $iy)"
                    guideText.text = "Move 🎯 crosshair, then tap '⏺ Tap'"
                }
            }
            actionRow.addView(btnTarget)

            // ⏺ Record Tap (linked to precision target)
            btnRecordTap = createPillButton("⏺ Tap", Color.parseColor("#059669"), Color.WHITE) {
                val (tx, ty) = calculateCurrentTargetCoordinate()
                RecordingManager.recordTap(tx, ty)
                showTouchDot(tx, ty, "TAP ($tx, $ty)")
                val c = RecordingManager.recordedSteps.value.size
                stepCountText.text = " • $c step" + (if (c != 1) "s" else "")
                guideText.text = "Recorded Tap at ($tx, $ty)"
            }.apply { visibility = View.GONE }
            actionRow.addView(btnRecordTap)

            // ⏱ Hold (Long Press)
            btnHold = createPillButton("⏱ Hold", Color.parseColor("#D97706"), Color.WHITE) {
                val (tx, ty) = calculateCurrentTargetCoordinate()
                RecordingManager.recordLongPress(tx, ty, 900L)
                showTouchDot(tx, ty, "HOLD ($tx, $ty)")
                val c = RecordingManager.recordedSteps.value.size
                stepCountText.text = " • $c step" + (if (c != 1) "s" else "")
                guideText.text = "Recorded Long Press at ($tx, $ty)"
            }.apply { visibility = View.GONE }
            actionRow.addView(btnHold)

            // ↔ Swipe
            val btnSwipe = createPillButton("↔ Swipe", Color.parseColor("#1E293B"), Color.parseColor("#38BDF8")) {
                guideText.text = "Swipe mode: Drag finger start to end"
                showSwipeCaptureOverlay(
                    onSwipeCaptured = { sx, sy, ex, ey, dur ->
                        RecordingManager.recordSwipe(sx, sy, ex, ey, dur)
                        showTouchDot(sx, sy, "START ($sx, $sy)")
                        showTouchDot(ex, ey, "END ($ex, $ey)")
                        val c = RecordingManager.recordedSteps.value.size
                        stepCountText.text = " • $c step" + (if (c != 1) "s" else "")
                        guideText.text = "Recorded Swipe ($sx, $sy) → ($ex, $ey)"
                    },
                    onCancelled = {
                        guideText.text = "Swipe recording cancelled"
                    }
                )
            }
            actionRow.addView(btnSwipe)

            // 📝 Text Entry
            val btnText = createPillButton("📝 Text", Color.parseColor("#1E293B"), Color.parseColor("#818CF8")) {
                showTextInputOverlay(
                    onTextSubmitted = { text ->
                        RecordingManager.recordTextInput(text)
                        val c = RecordingManager.recordedSteps.value.size
                        stepCountText.text = " • $c step" + (if (c != 1) "s" else "")
                        guideText.text = "Recorded text entry: \"$text\""
                    },
                    onCancel = {
                        guideText.text = "Text input cancelled"
                    }
                )
            }
            actionRow.addView(btnText)

            // ⏳ Wait
            val btnWait = createPillButton("⏳ Wait", Color.parseColor("#1E293B"), Color.parseColor("#F59E0B")) {
                RecordingManager.recordWait(1000L)
                val c = RecordingManager.recordedSteps.value.size
                stepCountText.text = " • $c step" + (if (c != 1) "s" else "")
                guideText.text = "Recorded Wait 1000ms"
            }
            actionRow.addView(btnWait)

            // ◀ Back
            val btnBack = createPillButton("◀ Back", Color.parseColor("#1E293B"), Color.parseColor("#A78BFA")) {
                RecordingManager.recordBack()
                val c = RecordingManager.recordedSteps.value.size
                stepCountText.text = " • $c step" + (if (c != 1) "s" else "")
                guideText.text = "Recorded Back navigation"
            }
            actionRow.addView(btnBack)

            // ⌂ Home
            val btnHome = createPillButton("⌂ Home", Color.parseColor("#1E293B"), Color.parseColor("#A78BFA")) {
                RecordingManager.recordHome()
                val c = RecordingManager.recordedSteps.value.size
                stepCountText.text = " • $c step" + (if (c != 1) "s" else "")
                guideText.text = "Recorded Home navigation"
            }
            actionRow.addView(btnHome)

            // 🗂 Recents
            val btnRecents = createPillButton("🗂 Recents", Color.parseColor("#1E293B"), Color.parseColor("#A78BFA")) {
                RecordingManager.recordRecents()
                val c = RecordingManager.recordedSteps.value.size
                stepCountText.text = " • $c step" + (if (c != 1) "s" else "")
                guideText.text = "Recorded Recents navigation"
            }
            actionRow.addView(btnRecents)

            // ↓ Scroll
            val btnScrollDown = createPillButton("↓ Scroll", Color.parseColor("#1E293B"), Color.parseColor("#38BDF8")) {
                RecordingManager.recordScroll(isUp = true)
                val c = RecordingManager.recordedSteps.value.size
                stepCountText.text = " • $c step" + (if (c != 1) "s" else "")
                guideText.text = "Recorded Scroll Down"
            }
            actionRow.addView(btnScrollDown)

            // ↑ Scroll Up
            val btnScrollUp = createPillButton("↑ Scroll", Color.parseColor("#1E293B"), Color.parseColor("#38BDF8")) {
                RecordingManager.recordScroll(isUp = false)
                val c = RecordingManager.recordedSteps.value.size
                stepCountText.text = " • $c step" + (if (c != 1) "s" else "")
                guideText.text = "Recorded Scroll Up"
            }
            actionRow.addView(btnScrollUp)

            // ✋ Drag
            val btnDrag = createPillButton("✋ Drag", Color.parseColor("#1E293B"), Color.parseColor("#F472B6")) {
                guideText.text = "Drag mode: Drag from source to target"
                showSwipeCaptureOverlay(
                    onSwipeCaptured = { sx, sy, ex, ey, dur ->
                        RecordingManager.recordDrag(sx, sy, ex, ey, dur.coerceAtLeast(600L))
                        showTouchDot(sx, sy, "DRAG START ($sx, $sy)")
                        showTouchDot(ex, ey, "DROP ($ex, $ey)")
                        val c = RecordingManager.recordedSteps.value.size
                        stepCountText.text = " • $c step" + (if (c != 1) "s" else "")
                        guideText.text = "Recorded Drag ($sx, $sy) → ($ex, $ey)"
                    },
                    onCancelled = {
                        guideText.text = "Drag recording cancelled"
                    }
                )
            }
            actionRow.addView(btnDrag)

            actionScroll.addView(actionRow)
            panel.addView(actionScroll)

            // Controls Row: Pause/Resume + Undo + Finish
            val controlRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dpToPx(4f), 0, 0)
            }

            var btnPauseResume: TextView? = null
            btnPauseResume = createPillButton(
                if (RecordingManager.sessionState.value == com.taskscheduling.app.domain.model.SessionState.RECORDING) "⏸ Pause" else "▶ Resume",
                Color.parseColor("#334155"),
                Color.WHITE
            ) {
                if (RecordingManager.sessionState.value == com.taskscheduling.app.domain.model.SessionState.RECORDING) {
                    RecordingManager.pauseRecording()
                    btnPauseResume?.text = "▶ Resume"
                    statusBadge.text = "⏸ PAUSED"
                    statusBadge.setTextColor(Color.parseColor("#F59E0B"))
                    miniOrb.state = ActivityDotState.IDLE
                    guideText.text = "Recording paused — actions not captured"
                } else if (RecordingManager.sessionState.value == com.taskscheduling.app.domain.model.SessionState.PAUSED) {
                    RecordingManager.resumeActiveRecording()
                    btnPauseResume?.text = "⏸ Pause"
                    statusBadge.text = "● REC"
                    statusBadge.setTextColor(Color.parseColor("#EF4444"))
                    miniOrb.state = ActivityDotState.LISTENING
                    guideText.text = "Recording resumed"
                }
            }

            // Undo Last Step
            val btnUndo = createPillButton("↶ Undo", Color.parseColor("#334155"), Color.parseColor("#E2E8F0")) {
                val undone = RecordingManager.undoLastStep()
                val c = RecordingManager.recordedSteps.value.size
                stepCountText.text = " • $c step" + (if (c != 1) "s" else "")
                if (undone != null) {
                    guideText.text = "Undid: ${undone.label.ifBlank { undone.actionType.label }}"
                } else {
                    guideText.text = "No steps to undo"
                }
            }

            // Finish Recording
            val btnDone = createPillButton("✓ Finish", Color.parseColor("#059669"), Color.WHITE) {
                hideFloatingRecorder()
                val steps = RecordingManager.finishRecording()
                onFinish()
            }

            controlRow.addView(btnPauseResume)
            controlRow.addView(btnUndo)
            controlRow.addView(btnDone)
            panel.addView(controlRow)

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = if (floatingRecorderX >= 0) floatingRecorderX else (screenMetrics.widthPixels / 2) - dpToPx(140f)
                y = if (floatingRecorderY >= 0) floatingRecorderY else dpToPx(36f)
            }

            // Smooth drag repositioning
            var initialX = 0
            var initialY = 0
            var initialTouchX = 0f
            var initialTouchY = 0f

            panel.setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - initialTouchX).toInt()
                        val dy = (event.rawY - initialTouchY).toInt()
                        if (Math.abs(dx) > 6 || Math.abs(dy) > 6) {
                            params.x = (initialX + dx).coerceIn(0, (screenMetrics.widthPixels - dpToPx(240f)).coerceAtLeast(0))
                            params.y = (initialY + dy).coerceIn(dpToPx(20f), (screenMetrics.heightPixels - dpToPx(120f)).coerceAtLeast(dpToPx(20f)))
                            floatingRecorderX = params.x
                            floatingRecorderY = params.y
                            try {
                                windowManager.updateViewLayout(panel, params)
                            } catch (_: Exception) {}
                            true
                        } else {
                            false
                        }
                    }
                    else -> false
                }
            }

            try {
                windowManager.addView(panel, params)
                activeFloatingRecorderPanel = panel
                activeRecordingToolbar = panel

                // Start periodic timer updater
                floatingRecorderJob?.cancel()
                floatingRecorderJob = CoroutineScope(Dispatchers.Main).launch {
                    while (isActive) {
                        val count = RecordingManager.recordedSteps.value.size
                        stepCountText.text = " • $count step" + (if (count != 1) "s" else "")
                        val totalSec = RecordingManager.getElapsedRecordingTimeMs() / 1000L
                        val mins = totalSec / 60
                        val secs = totalSec % 60
                        timerText.text = String.format(" • %02d:%02d", mins, secs)
                        kotlinx.coroutines.delay(1000L)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Shows the compact, collapsed floating orb handle.
     * Uses abstract animated luminous pulse with zero cloud/robot pictograms.
     * Tapping expands into the full floating recording panel.
     */
    @SuppressLint("ClickableViewAccessibility")
    fun showFloatingRecorderCollapsed(
        onFinish: () -> Unit,
        onCancel: () -> Unit
    ) {
        mainHandler.post {
            if (activeFloatingRecorderHandle != null) return@post
            hideFloatingRecorderPanel()

            val screenMetrics = context.resources.displayMetrics
            val orbSizePx = dpToPx(52f)

            val params = WindowManager.LayoutParams(
                orbSizePx,
                orbSizePx,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = if (floatingRecorderX >= 0) floatingRecorderX else screenMetrics.widthPixels - orbSizePx - dpToPx(16f)
                y = if (floatingRecorderY >= 0) floatingRecorderY else dpToPx(60f)
            }

            val handleFrame = FrameLayout(context).apply {
                elevation = 20f
                val orb = FloatingOrbView(context).apply {
                    state = if (RecordingManager.sessionState.value == com.taskscheduling.app.domain.model.SessionState.RECORDING) {
                        ActivityDotState.LISTENING
                    } else {
                        ActivityDotState.IDLE
                    }
                }
                addView(orb, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            }

            var initialX = 0
            var initialY = 0
            var touchStartX = 0f
            var touchStartY = 0f
            var isDrag = false

            handleFrame.setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        touchStartX = event.rawX
                        touchStartY = event.rawY
                        isDrag = false
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - touchStartX).toInt()
                        val dy = (event.rawY - touchStartY).toInt()
                        if (Math.abs(dx) > 8 || Math.abs(dy) > 8) {
                            isDrag = true
                            params.x = (initialX + dx).coerceIn(0, (screenMetrics.widthPixels - orbSizePx).coerceAtLeast(0))
                            params.y = (initialY + dy).coerceIn(dpToPx(24f), (screenMetrics.heightPixels - orbSizePx - dpToPx(36f)).coerceAtLeast(dpToPx(24f)))
                            floatingRecorderX = params.x
                            floatingRecorderY = params.y
                            try {
                                windowManager.updateViewLayout(handleFrame, params)
                            } catch (_: Exception) {}
                        }
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!isDrag) {
                            // Tap: expand back into full panel
                            hideFloatingRecorderHandle()
                            showFloatingRecorder(onFinish, onCancel)
                        }
                        true
                    }
                    else -> false
                }
            }

            try {
                windowManager.addView(handleFrame, params)
                activeFloatingRecorderHandle = handleFrame
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Backward-compatible alias for showFloatingRecorder.
     */
    fun showRecordingToolbar(
        onFinishRecording: () -> Unit,
        onCancelRecording: () -> Unit
    ) {
        showFloatingRecorder(onFinishRecording, onCancelRecording)
    }

    fun hideFloatingRecorderPanel() {
        mainHandler.post {
            floatingRecorderJob?.cancel()
            floatingRecorderJob = null
            activeFloatingRecorderPanel?.let {
                try { windowManager.removeView(it) } catch (_: Exception) {}
                activeFloatingRecorderPanel = null
                activeRecordingToolbar = null
            }
        }
    }

    fun hideFloatingRecorderHandle() {
        mainHandler.post {
            activeFloatingRecorderHandle?.let {
                try { windowManager.removeView(it) } catch (_: Exception) {}
                activeFloatingRecorderHandle = null
            }
        }
    }

    fun hideFloatingRecorder() {
        mainHandler.post {
            hideFloatingRecorderPanel()
            hideFloatingRecorderHandle()
            hideTargetCrosshair()
            hideSwipeCaptureOverlay()
            hideTextInputOverlay()
        }
    }

    /**
     * Compact overlay modal for text entry step recording.
     */
    @SuppressLint("ClickableViewAccessibility")
    fun showTextInputOverlay(
        onTextSubmitted: (String) -> Unit,
        onCancel: () -> Unit
    ) {
        mainHandler.post {
            hideTextInputOverlay()

            val layout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dpToPx(16f), dpToPx(12f), dpToPx(16f), dpToPx(12f))
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(Color.parseColor("#EE0F172A"))
                    cornerRadius = dpToPx(16f).toFloat()
                    setStroke(dpToPx(1.5f), Color.parseColor("#818CF8"))
                }
                elevation = 24f
            }

            val title = TextView(context).apply {
                text = "📝 Record Text Input"
                setTextColor(Color.WHITE)
                textSize = 13f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                setPadding(0, 0, 0, dpToPx(8f))
            }
            layout.addView(title)

            val inputField = EditText(context).apply {
                hint = "Enter text to type..."
                setHintTextColor(Color.parseColor("#64748B"))
                setTextColor(Color.WHITE)
                textSize = 13f
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(Color.parseColor("#1E293B"))
                    cornerRadius = dpToPx(10f).toFloat()
                    setStroke(dpToPx(1f), Color.parseColor("#475569"))
                }
                setPadding(dpToPx(12f), dpToPx(8f), dpToPx(12f), dpToPx(8f))
            }
            layout.addView(inputField)

            val btnRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.END
                setPadding(0, dpToPx(10f), 0, 0)
            }

            val btnCancel = Button(context).apply {
                text = "Cancel"
                setTextColor(Color.parseColor("#94A3B8"))
                setBackgroundColor(Color.TRANSPARENT)
                textSize = 12f
                setOnClickListener {
                    hideTextInputOverlay()
                    onCancel()
                }
            }

            val btnSubmit = Button(context).apply {
                text = "Add Step"
                setTextColor(Color.WHITE)
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(Color.parseColor("#4F46E5"))
                    cornerRadius = dpToPx(8f).toFloat()
                }
                textSize = 12f
                setOnClickListener {
                    val txt = inputField.text.toString().trim()
                    if (txt.isNotEmpty()) {
                        hideTextInputOverlay()
                        onTextSubmitted(txt)
                    }
                }
            }

            btnRow.addView(btnCancel)
            btnRow.addView(btnSubmit)
            layout.addView(btnRow)

            val params = WindowManager.LayoutParams(
                dpToPx(280f),
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.CENTER
            }

            try {
                windowManager.addView(layout, params)
                activeTextInputOverlay = layout
                inputField.requestFocus()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun hideTextInputOverlay() {
        mainHandler.post {
            activeTextInputOverlay?.let {
                try { windowManager.removeView(it) } catch (_: Exception) {}
                activeTextInputOverlay = null
            }
        }
    }

    /**
     * Compact Floating Precision Target Reticle.
     * Visible size is 32 dp diameter with hollow transparent center.
     * Touch area is 48 dp. Does NOT block underlying UI or display large opaque panels.
     * Moving the target is strictly for positioning; coordinate is only recorded on explicit button click.
     */
    @SuppressLint("ClickableViewAccessibility")
    fun showTargetCrosshair(
        onMoved: (Int, Int) -> Unit = { _, _ -> },
        onDismissed: () -> Unit = {}
    ) {
        mainHandler.post {
            if (activeTargetOverlay != null) return@post

            onTargetCoordinateChanged = onMoved
            onTargetDismissed = onDismissed

            val touchSizePx = dpToPx(48f) // 48 dp touch interaction area
            val visibleRadiusPx = dpToPx(16f).toFloat() // 32 dp visible diameter

            val screenMetrics = context.resources.displayMetrics
            val initX = (screenMetrics.widthPixels / 2) - (touchSizePx / 2)
            val initY = (screenMetrics.heightPixels / 2) - (touchSizePx / 2)

            val params = WindowManager.LayoutParams(
                touchSizePx,
                touchSizePx,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = initX
                y = initY
            }

            val reticleView = PrecisionReticleView(context, visibleRadiusPx)

            var initialX = 0
            var initialY = 0
            var initialTouchX = 0f
            var initialTouchY = 0f

            reticleView.setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - initialTouchX).toInt()
                        val dy = (event.rawY - initialTouchY).toInt()
                        params.x = initialX + dx
                        params.y = initialY + dy
                        try {
                            windowManager.updateViewLayout(reticleView, params)
                            val (tx, ty) = calculateCurrentTargetCoordinate()
                            currentTargetCoordinate = Pair(tx, ty)
                            onTargetCoordinateChanged?.invoke(tx, ty)
                        } catch (_: Exception) {}
                        true
                    }
                    else -> true
                }
            }

            try {
                windowManager.addView(reticleView, params)
                activeTargetOverlay = reticleView
                val (tx, ty) = calculateCurrentTargetCoordinate()
                currentTargetCoordinate = Pair(tx, ty)
                onTargetCoordinateChanged?.invoke(tx, ty)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Calculates the exact physical screen pixel coordinate at the center of the precision reticle.
     */
    fun calculateCurrentTargetCoordinate(): Pair<Int, Int> {
        val overlay = activeTargetOverlay ?: return currentTargetCoordinate ?: Pair(0, 0)
        val loc = IntArray(2)
        overlay.getLocationOnScreen(loc)
        val tx = loc[0] + (overlay.width / 2)
        val ty = loc[1] + (overlay.height / 2)
        return Pair(tx, ty)
    }

    fun toggleTargetCrosshair(stepCountLabel: TextView? = null) {
        if (activeTargetOverlay != null) {
            hideTargetCrosshair()
        } else {
            showTargetCrosshair()
        }
    }

    fun hideTargetCrosshair() {
        mainHandler.post {
            activeTargetOverlay?.let {
                try {
                    windowManager.removeView(it)
                } catch (_: Exception) {}
                activeTargetOverlay = null
                currentTargetCoordinate = null
                onTargetDismissed?.invoke()
            }
        }
    }

    fun hideRecordingToolbar() {
        mainHandler.post {
            activeRecordingToolbar?.let {
                try {
                    windowManager.removeView(it)
                } catch (_: Exception) {}
                activeRecordingToolbar = null
            }
        }
    }

    /**
     * Shows a translucent full-screen gesture recording surface for capturing a SWIPE start -> end coordinate.
     * Captures user's finger drag data ONLY. Does NOT execute gestures or dispatch events.
     */
    @SuppressLint("ClickableViewAccessibility")
    fun showSwipeCaptureOverlay(
        onSwipeCaptured: (startX: Int, startY: Int, endX: Int, endY: Int, durationMs: Long) -> Unit,
        onCancelled: () -> Unit
    ) {
        mainHandler.post {
            if (activeSwipeOverlay != null) return@post

            val swipeView = SwipeCaptureView(context).apply {
                onCaptured = { sx, sy, ex, ey, dur ->
                    hideSwipeCaptureOverlay()
                    onSwipeCaptured(sx, sy, ex, ey, dur)
                }
                onCancel = {
                    hideSwipeCaptureOverlay()
                    onCancelled()
                }
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
            }

            try {
                windowManager.addView(swipeView, params)
                activeSwipeOverlay = swipeView
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun hideSwipeCaptureOverlay() {
        mainHandler.post {
            activeSwipeOverlay?.let {
                try {
                    windowManager.removeView(it)
                } catch (_: Exception) {}
                activeSwipeOverlay = null
            }
        }
    }

    private var activeActionCalibrationToolbar: View? = null

    /**
     * Records or re-records coordinates for a single action on the real Android screen.
     * Works with any coordinate-based action: TAP, LONG_PRESS, DOUBLE_TAP, SWIPE, DRAG.
     */
    fun recordSingleActionCoordinates(
        actionType: ActionType,
        actionLabel: String,
        onCoordinatesCaptured: (startX: Int, startY: Int, endX: Int?, endY: Int?, durationMs: Long) -> Unit,
        onCancel: () -> Unit = {}
    ) {
        mainHandler.post {
            hideTargetCrosshair()
            hideSwipeCaptureOverlay()
            hideActionCalibrationToolbar()

            if (actionType == ActionType.SWIPE || actionType == ActionType.DRAG) {
                showSwipeCaptureOverlay(
                    onSwipeCaptured = { sx, sy, ex, ey, dur ->
                        showTouchDot(sx, sy, "START ($sx, $sy)")
                        showTouchDot(ex, ey, "END ($ex, $ey)")
                        onCoordinatesCaptured(sx, sy, ex, ey, dur)
                    },
                    onCancelled = onCancel
                )
            } else {
                val toolbar = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dpToPx(14f), dpToPx(8f), dpToPx(14f), dpToPx(8f))
                    background = android.graphics.drawable.GradientDrawable().apply {
                        setColor(Color.parseColor("#EE111827"))
                        cornerRadius = dpToPx(16f).toFloat()
                        setStroke(dpToPx(1.5f), Color.parseColor("#38BDF8"))
                    }
                    elevation = 20f
                }

                val titleText = TextView(context).apply {
                    text = "🎯 Calibrating: $actionLabel"
                    setTextColor(Color.WHITE)
                    textSize = 12f
                    setPadding(0, 0, dpToPx(10f), 0)
                }

                val btnSave = TextView(context).apply {
                    text = "✓ Save"
                    setTextColor(Color.WHITE)
                    textSize = 11.5f
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                    setPadding(dpToPx(12f), dpToPx(6f), dpToPx(12f), dpToPx(6f))
                    background = android.graphics.drawable.GradientDrawable().apply {
                        setColor(Color.parseColor("#059669"))
                        cornerRadius = dpToPx(10f).toFloat()
                    }
                    setOnClickListener {
                        val (tx, ty) = calculateCurrentTargetCoordinate()
                        hideTargetCrosshair()
                        hideActionCalibrationToolbar()
                        showTouchDot(tx, ty, "SAVED ($tx, $ty)")
                        val dur = if (actionType == ActionType.LONG_PRESS) 900L else 100L
                        onCoordinatesCaptured(tx, ty, null, null, dur)
                    }
                }

                val btnCancel = TextView(context).apply {
                    text = "✕"
                    setTextColor(Color.parseColor("#94A3B8"))
                    textSize = 13f
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                    setPadding(dpToPx(10f), dpToPx(6f), dpToPx(10f), dpToPx(6f))
                    setOnClickListener {
                        hideTargetCrosshair()
                        hideActionCalibrationToolbar()
                        onCancel()
                    }
                }

                toolbar.addView(titleText)
                toolbar.addView(btnSave)
                toolbar.addView(btnCancel)

                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                    y = dpToPx(36f)
                }

                try {
                    windowManager.addView(toolbar, params)
                    activeActionCalibrationToolbar = toolbar
                    showTargetCrosshair(
                        onMoved = { tx, ty ->
                            titleText.text = "🎯 Target: ($tx, $ty)"
                        },
                        onDismissed = {
                            hideActionCalibrationToolbar()
                        }
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun hideActionCalibrationToolbar() {
        mainHandler.post {
            activeActionCalibrationToolbar?.let {
                try { windowManager.removeView(it) } catch (_: Exception) {}
                activeActionCalibrationToolbar = null
            }
        }
    }

    fun setAssistantController(controller: OfflineAssistantController) {
        this.assistantController = controller
        this.continuousVoiceController = com.taskscheduling.app.voice.ContinuousVoiceController(
            voiceInputManager = voiceInputManager,
            assistantController = controller,
            speechOutputManager = speechOutputManager
        )
    }

    /**
     * Shows a small, draggable, unobtrusive floating assistant bubble above other apps.
     * Tapping it opens the compact floating chat window.
     */
    @SuppressLint("ClickableViewAccessibility")
    fun showFloatingAssistantShortcut() {
        mainHandler.post {
            if (activeFloatingShortcut != null) return@post

            val prefs = botSettingsRepository.getPreferences()
            val buttonSizePx = dpToPx(prefs.botSize.sizeDp)
            val screenMetrics = context.resources.displayMetrics
            val initX = if (prefs.savedPositionX != null && prefs.savedPositionX >= 0) {
                prefs.savedPositionX.coerceIn(0, (screenMetrics.widthPixels - buttonSizePx).coerceAtLeast(0))
            } else {
                screenMetrics.widthPixels - buttonSizePx - dpToPx(16f)
            }
            val initY = if (prefs.savedPositionY != null && prefs.savedPositionY >= 0) {
                prefs.savedPositionY.coerceIn(dpToPx(24f), (screenMetrics.heightPixels - buttonSizePx - dpToPx(48f)).coerceAtLeast(dpToPx(24f)))
            } else {
                screenMetrics.heightPixels / 3
            }

            val params = WindowManager.LayoutParams(
                buttonSizePx,
                buttonSizePx,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = initX
                y = initY
                alpha = prefs.windowOpacity
            }

            val shortcutView = FrameLayout(context).apply {
                elevation = dpToPx(8f).toFloat()

                val orbView = com.taskscheduling.app.assistant.FloatingOrbView(context).apply {
                    this.enableAnimations = prefs.enableAnimations
                    this.state = ActivityDotState.IDLE
                }
                addView(
                    orbView,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        Gravity.CENTER
                    )
                )
            }

            val floatingOrb = shortcutView.getChildAt(0) as? com.taskscheduling.app.assistant.FloatingOrbView

            shortcutCollectorJob?.cancel()
            shortcutCollectorJob = CoroutineScope(Dispatchers.Main).launch {
                continuousVoiceController?.continuousState?.collect { state ->
                    floatingOrb?.state = when (state) {
                        com.taskscheduling.app.voice.ContinuousListeningState.IDLE -> ActivityDotState.IDLE
                        com.taskscheduling.app.voice.ContinuousListeningState.MUTED -> ActivityDotState.DISABLED
                        com.taskscheduling.app.voice.ContinuousListeningState.LISTENING,
                        com.taskscheduling.app.voice.ContinuousListeningState.LISTENING_AGAIN,
                        com.taskscheduling.app.voice.ContinuousListeningState.SPEECH_DETECTED,
                        com.taskscheduling.app.voice.ContinuousListeningState.UTTERANCE_FINALIZING -> ActivityDotState.LISTENING
                        com.taskscheduling.app.voice.ContinuousListeningState.TRANSCRIBING,
                        com.taskscheduling.app.voice.ContinuousListeningState.COMMAND_PROCESSING -> ActivityDotState.PROCESSING
                        com.taskscheduling.app.voice.ContinuousListeningState.EXECUTING -> ActivityDotState.SPEAKING
                        com.taskscheduling.app.voice.ContinuousListeningState.ERROR -> ActivityDotState.ERROR
                        com.taskscheduling.app.voice.ContinuousListeningState.STOPPED -> ActivityDotState.DISABLED
                    }
                }
            }

            var initialX = 0
            var initialY = 0
            var touchStartX = 0f
            var touchStartY = 0f
            var isDragging = false

            shortcutView.setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        touchStartX = event.rawX
                        touchStartY = event.rawY
                        isDragging = false
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - touchStartX).toInt()
                        val dy = (event.rawY - touchStartY).toInt()
                        if (Math.abs(dx) > 8 || Math.abs(dy) > 8) {
                            isDragging = true
                            params.x = (initialX + dx).coerceIn(0, (screenMetrics.widthPixels - buttonSizePx).coerceAtLeast(0))
                            params.y = (initialY + dy).coerceIn(dpToPx(24f), (screenMetrics.heightPixels - buttonSizePx - dpToPx(48f)).coerceAtLeast(dpToPx(24f)))
                            try {
                                windowManager.updateViewLayout(shortcutView, params)
                            } catch (_: Exception) {}
                        }
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (isDragging) {
                            val currentPrefs = botSettingsRepository.getPreferences()
                            if (currentPrefs.edgeSnapping) {
                                val midX = screenMetrics.widthPixels / 2
                                val targetX = if (params.x < midX) dpToPx(8f) else screenMetrics.widthPixels - buttonSizePx - dpToPx(8f)
                                params.x = targetX
                                try {
                                    windowManager.updateViewLayout(shortcutView, params)
                                } catch (_: Exception) {}
                            }
                            botSettingsRepository.savePosition(params.x, params.y)
                        } else {
                            toggleFloatingAssistantPanel()
                        }
                        true
                    }
                    else -> false
                }
            }

            try {
                windowManager.addView(shortcutView, params)
                activeFloatingShortcut = shortcutView
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun hideFloatingAssistantShortcut() {
        mainHandler.post {
            shortcutCollectorJob?.cancel()
            shortcutCollectorJob = null
            activeFloatingShortcut?.let {
                try {
                    windowManager.removeView(it)
                } catch (_: Exception) {}
                activeFloatingShortcut = null
            }
        }
    }

    fun toggleFloatingAssistantPanel() {
        if (activeAssistantPanel != null) {
            hideFloatingAssistantPanel()
        } else {
            showFloatingAssistantPanel()
        }
    }

    /**
     * Compact, elegant Gemini/ChatGPT-style floating AI assistant chat window.
     * Contains clean conversation bubbles, push-to-talk mic, action confirmation cards,
     * and an inline customization modal. Strictly excludes developer diagnostic telemetry.
     */
    @SuppressLint("ClickableViewAccessibility", "SetTextI18n")
    fun showFloatingAssistantPanel() {
        mainHandler.post {
            if (activeAssistantPanel != null) return@post

            val controller = assistantController ?: return@post
            voiceInputManager.prepareEngineAsync()

            val prefs = botSettingsRepository.getPreferences()
            val screenMetrics = context.resources.displayMetrics
            val panelWidth = dpToPx(prefs.expandedWindowSize.widthDp).coerceAtMost(screenMetrics.widthPixels - dpToPx(24f))
            val padH = if (prefs.isCompactMode) dpToPx(12f) else dpToPx(16f)
            val padV = if (prefs.isCompactMode) dpToPx(10f) else dpToPx(14f)

            val params = WindowManager.LayoutParams(
                panelWidth,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                y = dpToPx(70f)
                alpha = prefs.windowOpacity
                softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            }

            val panelLayout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(padH, padV, padH, padV)
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(Color.parseColor(prefs.bubbleColor.hexColor))
                    cornerRadius = dpToPx(prefs.cornerRadiusDp).toFloat()
                    setStroke(dpToPx(1.5f), Color.parseColor(prefs.bubbleColor.strokeColor))
                }
                elevation = dpToPx(16f).toFloat()
            }

            // 1. Header Row (Draggable)
            val headerRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 0, 0, dpToPx(8f))
            }

            val assistantIcon = FloatingOrbView(context).apply {
                layoutParams = LinearLayout.LayoutParams(dpToPx(24f), dpToPx(24f)).apply {
                    setMargins(0, 0, dpToPx(8f), 0)
                }
                state = ActivityDotState.IDLE
                enableAnimations = prefs.enableAnimations
                visibility = if (prefs.showIcon) View.VISIBLE else View.GONE
            }

            val titleCol = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }

            val titleText = TextView(context).apply {
                text = "Task Assistant"
                setTextColor(Color.WHITE)
                textSize = 14f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                visibility = if (prefs.showAssistantName) View.VISIBLE else View.GONE
            }

            val statusRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                visibility = if (prefs.showStatusIndicator) View.VISIBLE else View.GONE
            }

            val headerActivityDot = ActivityDotView(context).apply {
                this.enableAnimations = prefs.enableAnimations
                this.state = ActivityDotState.IDLE
            }
            val dotPx = dpToPx(8f)
            statusRow.addView(headerActivityDot, LinearLayout.LayoutParams(dotPx, dotPx).apply {
                setMargins(0, 0, dpToPx(4f), 0)
            })

            val statusText = TextView(context).apply {
                text = "Ready"
                setTextColor(Color.parseColor("#94A3B8"))
                textSize = 10f
                typeface = android.graphics.Typeface.MONOSPACE
            }
            statusRow.addView(statusText)

            titleCol.addView(titleText)
            titleCol.addView(statusRow)

            // Settings button
            val btnSettings = TextView(context).apply {
                text = "⚙"
                setTextColor(Color.parseColor("#94A3B8"))
                textSize = 15f
                setPadding(dpToPx(6f), dpToPx(4f), dpToPx(6f), dpToPx(4f))
            }

            // Minimize button
            val btnMinimize = TextView(context).apply {
                text = "—"
                setTextColor(Color.parseColor("#94A3B8"))
                textSize = 16f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                setPadding(dpToPx(6f), dpToPx(4f), dpToPx(6f), dpToPx(4f))
                setOnClickListener {
                    hideFloatingAssistantPanel()
                }
            }

            // Close button
            val btnClose = TextView(context).apply {
                text = "✕"
                setTextColor(Color.parseColor("#94A3B8"))
                textSize = 15f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                setPadding(dpToPx(6f), dpToPx(4f), dpToPx(6f), dpToPx(4f))
                setOnClickListener {
                    hideFloatingAssistantPanel()
                }
            }

            headerRow.addView(assistantIcon)
            headerRow.addView(titleCol)
            headerRow.addView(btnSettings)
            headerRow.addView(btnMinimize)
            headerRow.addView(btnClose)
            panelLayout.addView(headerRow)

            // Make Header draggable
            var headInitX = 0
            var headInitY = 0
            var headTouchX = 0f
            var headTouchY = 0f
            headerRow.setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        headInitX = params.x
                        headInitY = params.y
                        headTouchX = event.rawX
                        headTouchY = event.rawY
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - headTouchX).toInt()
                        val dy = (event.rawY - headTouchY).toInt()
                        params.x = headInitX + dx
                        params.y = headInitY + dy
                        try {
                            windowManager.updateViewLayout(panelLayout, params)
                        } catch (_: Exception) {}
                        true
                    }
                    else -> false
                }
            }

            // 2. Main Content Container
            val contentContainer = FrameLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
            panelLayout.addView(contentContainer)

            // A. CHAT VIEW
            val chatView = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
            contentContainer.addView(chatView)

            // Conversation Messages ScrollView
            val messagesContainer = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, dpToPx(4f), 0, dpToPx(8f))
            }

            val messagesScrollView = ScrollView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dpToPx(if (prefs.isCompactMode) 140f else 200f)
                )
                addView(messagesContainer)
            }
            chatView.addView(messagesScrollView)

            // Helper to render chat bubbles
            fun renderChatMessages(messages: List<AssistantChatMessage>) {
                messagesContainer.removeAllViews()
                for (msg in messages) {
                    val bubbleLayout = LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = if (msg.isUser) Gravity.END else Gravity.START
                        setPadding(0, dpToPx(3f), 0, dpToPx(3f))
                    }

                    if (!msg.isUser) {
                        val botIcon = FloatingOrbView(context).apply {
                            layoutParams = LinearLayout.LayoutParams(dpToPx(16f), dpToPx(16f)).apply {
                                setMargins(0, 0, dpToPx(6f), 0)
                            }
                            state = ActivityDotState.IDLE
                            enableAnimations = prefs.enableAnimations
                        }
                        bubbleLayout.addView(botIcon)
                    }

                    val msgText = TextView(context).apply {
                        text = msg.text
                        textSize = 12.5f
                        if (msg.isUser) {
                            setTextColor(Color.parseColor("#38BDF8"))
                            background = android.graphics.drawable.GradientDrawable().apply {
                                setColor(Color.parseColor("#1E293B"))
                                cornerRadius = dpToPx(12f).toFloat()
                            }
                        } else {
                            setTextColor(Color.parseColor("#E2E8F0"))
                            background = android.graphics.drawable.GradientDrawable().apply {
                                setColor(Color.parseColor("#0F172A"))
                                cornerRadius = dpToPx(12f).toFloat()
                                setStroke(dpToPx(1f), Color.parseColor("#334155"))
                            }
                        }
                        setPadding(dpToPx(10f), dpToPx(6f), dpToPx(10f), dpToPx(6f))
                    }

                    bubbleLayout.addView(msgText)
                    messagesContainer.addView(bubbleLayout)
                }

                messagesScrollView.post {
                    messagesScrollView.fullScroll(View.FOCUS_DOWN)
                }
            }

            // Context Action Cards
            // Confirmation Card
            val confirmationCard = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dpToPx(12f), dpToPx(10f), dpToPx(12f), dpToPx(10f))
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(Color.parseColor("#1E293B"))
                    cornerRadius = dpToPx(12f).toFloat()
                    setStroke(dpToPx(1f), Color.parseColor("#38BDF8"))
                }
                visibility = View.GONE
            }
            val confirmTitle = TextView(context).apply {
                setTextColor(Color.WHITE)
                textSize = 13f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
            val confirmQuestion = TextView(context).apply {
                text = "Do you want me to run it?"
                setTextColor(Color.parseColor("#CBD5E1"))
                textSize = 12f
                setPadding(0, dpToPx(2f), 0, dpToPx(8f))
            }
            val confirmBtnRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.END
            }
            val btnCancelConfirm = TextView(context).apply {
                text = "CANCEL"
                setTextColor(Color.parseColor("#9CA3AF"))
                textSize = 11.5f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                setPadding(dpToPx(12f), dpToPx(6f), dpToPx(12f), dpToPx(6f))
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(Color.parseColor("#374151"))
                    cornerRadius = dpToPx(6f).toFloat()
                }
                setOnClickListener { controller.cancelConfirmation() }
            }
            val btnRunConfirm = TextView(context).apply {
                text = "YES, RUN"
                setTextColor(Color.WHITE)
                textSize = 11.5f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                setPadding(dpToPx(14f), dpToPx(6f), dpToPx(14f), dpToPx(6f))
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(Color.parseColor("#059669"))
                    cornerRadius = dpToPx(6f).toFloat()
                }
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(dpToPx(8f), 0, 0, 0) }
                setOnClickListener { controller.confirmRun() }
            }
            confirmBtnRow.addView(btnCancelConfirm)
            confirmBtnRow.addView(btnRunConfirm)
            confirmationCard.addView(confirmTitle)
            confirmationCard.addView(confirmQuestion)
            confirmationCard.addView(confirmBtnRow)
            chatView.addView(confirmationCard)

            // Running Card
            val runningCard = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dpToPx(12f), dpToPx(10f), dpToPx(12f), dpToPx(10f))
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(Color.parseColor("#1E293B"))
                    cornerRadius = dpToPx(12f).toFloat()
                    setStroke(dpToPx(1f), Color.parseColor("#10B981"))
                }
                visibility = View.GONE
            }
            val runTitle = TextView(context).apply {
                setTextColor(Color.parseColor("#38BDF8"))
                textSize = 13f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
            val runStepInfo = TextView(context).apply {
                setTextColor(Color.WHITE)
                textSize = 11.5f
                setPadding(0, dpToPx(2f), 0, dpToPx(6f))
            }
            val btnStopExecution = TextView(context).apply {
                text = "STOP"
                setTextColor(Color.WHITE)
                textSize = 11f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                setPadding(dpToPx(14f), dpToPx(6f), dpToPx(14f), dpToPx(6f))
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(Color.parseColor("#EF4444"))
                    cornerRadius = dpToPx(6f).toFloat()
                }
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { gravity = Gravity.END }
                setOnClickListener { controller.stopExecution() }
            }
            runningCard.addView(runTitle)
            runningCard.addView(runStepInfo)
            runningCard.addView(btnStopExecution)
            chatView.addView(runningCard)

            // Completed Card
            val completedCard = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dpToPx(12f), dpToPx(8f), dpToPx(12f), dpToPx(8f))
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(Color.parseColor("#064E3B"))
                    cornerRadius = dpToPx(12f).toFloat()
                    setStroke(dpToPx(1f), Color.parseColor("#10B981"))
                }
                visibility = View.GONE
            }
            val compMessage = TextView(context).apply {
                text = "Workflow completed successfully."
                setTextColor(Color.parseColor("#D1FAE5"))
                textSize = 12f
            }
            completedCard.addView(compMessage)
            chatView.addView(completedCard)

            // Error Card
            val errorCard = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dpToPx(12f), dpToPx(8f), dpToPx(12f), dpToPx(8f))
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(Color.parseColor("#2D1515"))
                    cornerRadius = dpToPx(12f).toFloat()
                    setStroke(dpToPx(1f), Color.parseColor("#EF4444"))
                }
                visibility = View.GONE
            }
            val errorText = TextView(context).apply {
                setTextColor(Color.parseColor("#FCA5A5"))
                textSize = 12f
            }
            errorCard.addView(errorText)
            chatView.addView(errorCard)

            // Listening Card (Active Mic Banner)
            val listeningCard = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dpToPx(10f), dpToPx(6f), dpToPx(10f), dpToPx(6f))
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(Color.parseColor("#1E293B"))
                    cornerRadius = dpToPx(10f).toFloat()
                    setStroke(dpToPx(1f), Color.parseColor("#EF4444"))
                }
                visibility = View.GONE
            }
            val listeningDot = TextView(context).apply {
                text = "● "
                setTextColor(Color.parseColor("#EF4444"))
                textSize = 13f
            }
            val listeningLabel = TextView(context).apply {
                text = "Listening... Speak your command"
                setTextColor(Color.WHITE)
                textSize = 12f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            val btnStopListening = TextView(context).apply {
                text = "STOP"
                setTextColor(Color.WHITE)
                textSize = 11f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                setPadding(dpToPx(10f), dpToPx(4f), dpToPx(10f), dpToPx(4f))
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(Color.parseColor("#EF4444"))
                    cornerRadius = dpToPx(6f).toFloat()
                }
                setOnClickListener {
                    continuousVoiceController?.stopContinuousListening()
                    voiceInputManager.stopListening()
                }
            }
            listeningCard.addView(listeningDot)
            listeningCard.addView(listeningLabel)
            listeningCard.addView(btnStopListening)
            chatView.addView(listeningCard)

            // Input Row at bottom
            val inputRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dpToPx(8f), 0, 0)
            }

            val inputEditText = EditText(context).apply {
                hint = "Ask or type a command..."
                setHintTextColor(Color.parseColor("#64748B"))
                setTextColor(Color.WHITE)
                textSize = 13f
                isSingleLine = true
                imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEND
                setPadding(dpToPx(12f), dpToPx(8f), dpToPx(12f), dpToPx(8f))
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(Color.parseColor("#1E293B"))
                    cornerRadius = dpToPx(12f).toFloat()
                    setStroke(dpToPx(1f), Color.parseColor("#334155"))
                }
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }

            fun sendTypedCommand() {
                val txt = inputEditText.text?.toString()?.trim() ?: ""
                if (txt.isNotBlank()) {
                    inputEditText.setText("")
                    controller.processCommand(txt)
                }
            }

            inputEditText.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND ||
                    actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE
                ) {
                    sendTypedCommand()
                    true
                } else {
                    false
                }
            }

            val btnSend = TextView(context).apply {
                text = "➤"
                setTextColor(Color.parseColor("#0B0F19"))
                textSize = 14f
                gravity = Gravity.CENTER
                setPadding(dpToPx(12f), dpToPx(8f), dpToPx(12f), dpToPx(8f))
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(Color.parseColor("#06B6D4"))
                    cornerRadius = dpToPx(12f).toFloat()
                }
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(dpToPx(6f), 0, 0, 0) }
                setOnClickListener { sendTypedCommand() }
            }

            val btnMute = ImageView(context).apply {
                val isCurrentlyMuted = continuousVoiceController?.isMuted?.value == true
                setImageResource(if (isCurrentlyMuted) com.taskscheduling.app.R.drawable.ic_mic_off else com.taskscheduling.app.R.drawable.ic_mic)
                setColorFilter(Color.parseColor(if (isCurrentlyMuted) "#EF4444" else "#94A3B8"))
                contentDescription = if (isCurrentlyMuted) "Unmute microphone" else "Mute microphone"
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                setPadding(dpToPx(7f), dpToPx(7f), dpToPx(7f), dpToPx(7f))
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(Color.parseColor(if (isCurrentlyMuted) "#2A1616" else "#1E293B"))
                    cornerRadius = dpToPx(12f).toFloat()
                    setStroke(dpToPx(1.5f), Color.parseColor(if (isCurrentlyMuted) "#EF4444" else "#334155"))
                }
                layoutParams = LinearLayout.LayoutParams(
                    dpToPx(38f),
                    dpToPx(38f)
                ).apply { setMargins(dpToPx(6f), 0, 0, 0) }
                setOnClickListener {
                    val cvc = continuousVoiceController ?: return@setOnClickListener
                    cvc.toggleMute()
                }
            }

            val btnVoice = ImageView(context).apply {
                val isListening = continuousVoiceController?.isContinuousListening?.value == true
                setImageResource(if (isListening) com.taskscheduling.app.R.drawable.ic_listening_active else com.taskscheduling.app.R.drawable.ic_listening_paused)
                setColorFilter(Color.parseColor(if (isListening) "#06B6D4" else "#94A3B8"))
                contentDescription = if (isListening) "Pause listening" else "Start listening"
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                setPadding(dpToPx(7f), dpToPx(7f), dpToPx(7f), dpToPx(7f))
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(Color.parseColor(if (isListening) "#16283B" else "#1E293B"))
                    cornerRadius = dpToPx(12f).toFloat()
                    setStroke(dpToPx(1.5f), Color.parseColor(if (isListening) "#06B6D4" else "#334155"))
                }
                layoutParams = LinearLayout.LayoutParams(
                    dpToPx(38f),
                    dpToPx(38f)
                ).apply { setMargins(dpToPx(6f), 0, 0, 0) }
                setOnClickListener {
                    val cvc = continuousVoiceController ?: return@setOnClickListener
                    if (cvc.isContinuousListening.value) {
                        cvc.stopContinuousListening()
                    } else {
                        cvc.startContinuousListening()
                    }
                }
            }

            inputRow.addView(inputEditText)
            inputRow.addView(btnSend)
            inputRow.addView(btnMute)
            inputRow.addView(btnVoice)
            chatView.addView(inputRow)

            // B. INLINE SETTINGS OVERLAY VIEW
            val settingsView = ScrollView(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dpToPx(if (prefs.isCompactMode) 220f else 280f)
                )
                visibility = View.GONE
            }

            val settingsContent = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dpToPx(6f), dpToPx(4f), dpToPx(6f), dpToPx(6f))
            }
            settingsView.addView(settingsContent)
            contentContainer.addView(settingsView)

            // Populate Settings Content
            val settingsTitle = TextView(context).apply {
                text = "⚙ Assistant Customization"
                setTextColor(Color.WHITE)
                textSize = 14f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                setPadding(0, 0, 0, dpToPx(8f))
            }
            settingsContent.addView(settingsTitle)

            fun createChipRow(title: String, items: List<Pair<String, () -> Unit>>, isSelected: (String) -> Boolean): LinearLayout {
                val container = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(0, dpToPx(4f), 0, dpToPx(6f))
                }
                val label = TextView(context).apply {
                    text = title
                    setTextColor(Color.parseColor("#94A3B8"))
                    textSize = 11f
                    setPadding(0, 0, 0, dpToPx(3f))
                }
                container.addView(label)

                val scroll = HorizontalScrollView(context).apply {
                    isHorizontalScrollBarEnabled = false
                }
                val row = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                }
                for ((name, onClick) in items) {
                    val sel = isSelected(name)
                    val chip = TextView(context).apply {
                        text = name
                        textSize = 11.5f
                        setTextColor(if (sel) Color.WHITE else Color.parseColor("#CBD5E1"))
                        setPadding(dpToPx(10f), dpToPx(4f), dpToPx(10f), dpToPx(4f))
                        background = android.graphics.drawable.GradientDrawable().apply {
                            setColor(Color.parseColor(if (sel) "#0284C7" else "#1E293B"))
                            cornerRadius = dpToPx(8f).toFloat()
                            if (sel) setStroke(dpToPx(1.5f), Color.parseColor("#38BDF8"))
                        }
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        ).apply { setMargins(0, 0, dpToPx(6f), 0) }
                        setOnClickListener {
                            onClick()
                            hideFloatingAssistantPanel()
                            showFloatingAssistantPanel()
                        }
                    }
                    row.addView(chip)
                }
                scroll.addView(row)
                container.addView(scroll)
                return container
            }

            // 1. Icon style chips
            val iconChips = AssistantIconStyle.values().map { s ->
                "${s.emoji} ${s.displayName}" to {
                    val p = botSettingsRepository.getPreferences().copy(iconStyle = s)
                    botSettingsRepository.updatePreferences(p)
                }
            }
            settingsContent.addView(createChipRow("Assistant Icon", iconChips) { name ->
                name.contains(prefs.iconStyle.displayName)
            })

            // 2. Bubble size chips
            val sizeChips = com.taskscheduling.app.domain.model.BotSize.values().map { s ->
                s.displayName to {
                    val p = botSettingsRepository.getPreferences().copy(botSize = s)
                    botSettingsRepository.updatePreferences(p)
                }
            }
            settingsContent.addView(createChipRow("Bubble Size", sizeChips) { it == prefs.botSize.displayName })

            // 3. Window size chips
            val winSizeChips = ExpandedWindowSize.values().map { s ->
                s.displayName to {
                    val p = botSettingsRepository.getPreferences().copy(expandedWindowSize = s)
                    botSettingsRepository.updatePreferences(p)
                }
            }
            settingsContent.addView(createChipRow("Window Width", winSizeChips) { it == prefs.expandedWindowSize.displayName })

            // 4. Color presets
            val colorChips = BubbleColorPreset.values().map { c ->
                c.displayName to {
                    val p = botSettingsRepository.getPreferences().copy(bubbleColor = c)
                    botSettingsRepository.updatePreferences(p)
                }
            }
            settingsContent.addView(createChipRow("Color Accent", colorChips) { it == prefs.bubbleColor.displayName })

            // 5. Language mode
            val langChips = com.taskscheduling.app.voice.LanguageMode.values().map { l ->
                l.displayName to {
                    val p = botSettingsRepository.getPreferences().copy(languageMode = l)
                    botSettingsRepository.updatePreferences(p)
                    com.taskscheduling.app.voice.WhisperCppSpeechRecognitionEngine.getInstance(context).languageMode = l
                }
            }
            settingsContent.addView(createChipRow("Speech Recognition Language", langChips) { it == prefs.languageMode.displayName })

            // Action Buttons in Settings
            val actionsTitle = TextView(context).apply {
                text = "Assistant Actions"
                setTextColor(Color.parseColor("#94A3B8"))
                textSize = 11f
                setPadding(0, dpToPx(6f), 0, dpToPx(4f))
            }
            settingsContent.addView(actionsTitle)

            val actionsRow1 = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 0, 0, dpToPx(4f))
            }

            val btnClearChat = TextView(context).apply {
                text = "🧹 Clear Chat"
                textSize = 11f
                setTextColor(Color.WHITE)
                setPadding(dpToPx(10f), dpToPx(6f), dpToPx(10f), dpToPx(6f))
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(Color.parseColor("#1E293B"))
                    cornerRadius = dpToPx(6f).toFloat()
                }
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(0, 0, dpToPx(4f), 0)
                }
                setOnClickListener {
                    controller.clearChatMessages()
                    settingsView.visibility = View.GONE
                    chatView.visibility = View.VISIBLE
                }
            }

            val btnResetPos = TextView(context).apply {
                text = "📍 Reset Pos"
                textSize = 11f
                setTextColor(Color.WHITE)
                setPadding(dpToPx(10f), dpToPx(6f), dpToPx(10f), dpToPx(6f))
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(Color.parseColor("#1E293B"))
                    cornerRadius = dpToPx(6f).toFloat()
                }
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(dpToPx(4f), 0, 0, 0)
                }
                setOnClickListener {
                    botSettingsRepository.resetPosition()
                    hideFloatingAssistantShortcut()
                    showFloatingAssistantShortcut()
                }
            }

            actionsRow1.addView(btnClearChat)
            actionsRow1.addView(btnResetPos)
            settingsContent.addView(actionsRow1)

            val actionsRow2 = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 0, 0, dpToPx(6f))
            }

            val btnOpenDiagnostics = TextView(context).apply {
                text = "🔬 Diagnostic Center"
                textSize = 11f
                setTextColor(Color.parseColor("#38BDF8"))
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                setPadding(dpToPx(10f), dpToPx(6f), dpToPx(10f), dpToPx(6f))
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(Color.parseColor("#0F2942"))
                    cornerRadius = dpToPx(6f).toFloat()
                    setStroke(dpToPx(1f), Color.parseColor("#0284C7"))
                }
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(0, 0, dpToPx(4f), 0)
                }
                setOnClickListener {
                    try {
                        val intent = android.content.Intent(context, com.taskscheduling.app.MainActivity::class.java).apply {
                            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
                            putExtra("navigate_to", "diagnostics")
                        }
                        context.startActivity(intent)
                        hideFloatingAssistantPanel()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            val btnOpenApp = TextView(context).apply {
                text = "📱 Open App"
                textSize = 11f
                setTextColor(Color.WHITE)
                setPadding(dpToPx(10f), dpToPx(6f), dpToPx(10f), dpToPx(6f))
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(Color.parseColor("#1E293B"))
                    cornerRadius = dpToPx(6f).toFloat()
                }
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(dpToPx(4f), 0, 0, 0)
                }
                setOnClickListener {
                    try {
                        val intent = android.content.Intent(context, com.taskscheduling.app.MainActivity::class.java).apply {
                            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
                        }
                        context.startActivity(intent)
                        hideFloatingAssistantPanel()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            actionsRow2.addView(btnOpenDiagnostics)
            actionsRow2.addView(btnOpenApp)
            settingsContent.addView(actionsRow2)

            val btnExitAssistant = TextView(context).apply {
                text = "❌ Exit Floating Assistant"
                textSize = 11.5f
                setTextColor(Color.parseColor("#FCA5A5"))
                gravity = Gravity.CENTER
                setPadding(dpToPx(12f), dpToPx(7f), dpToPx(12f), dpToPx(7f))
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(Color.parseColor("#371B1B"))
                    cornerRadius = dpToPx(8f).toFloat()
                    setStroke(dpToPx(1f), Color.parseColor("#EF4444"))
                }
                setOnClickListener {
                    val p = botSettingsRepository.getPreferences().copy(persistentFloatingMode = false)
                    botSettingsRepository.updatePreferences(p)
                    hideFloatingAssistantPanel()
                    hideFloatingAssistantShortcut()
                }
            }
            settingsContent.addView(btnExitAssistant)

            val btnBackToChat = TextView(context).apply {
                text = "← Back to Chat"
                textSize = 12f
                setTextColor(Color.parseColor("#38BDF8"))
                gravity = Gravity.CENTER
                setPadding(0, dpToPx(8f), 0, dpToPx(4f))
                setOnClickListener {
                    settingsView.visibility = View.GONE
                    chatView.visibility = View.VISIBLE
                }
            }
            settingsContent.addView(btnBackToChat)

            btnSettings.setOnClickListener {
                if (settingsView.visibility == View.VISIBLE) {
                    settingsView.visibility = View.GONE
                    chatView.visibility = View.VISIBLE
                } else {
                    chatView.visibility = View.GONE
                    settingsView.visibility = View.VISIBLE
                }
            }

            // 3. State & Message Collection
            assistantCollectorJob?.cancel()
            assistantCollectorJob = CoroutineScope(Dispatchers.Main).launch {
                launch {
                    controller.chatMessages.collect { messages ->
                        renderChatMessages(messages)
                    }
                }
                launch {
                    continuousVoiceController?.continuousState?.collect { state ->
                        when (state) {
                            com.taskscheduling.app.voice.ContinuousListeningState.LISTENING,
                            com.taskscheduling.app.voice.ContinuousListeningState.LISTENING_AGAIN -> {
                                stopThinkingAnimation()
                                btnVoice.setImageResource(com.taskscheduling.app.R.drawable.ic_listening_active)
                                btnVoice.setColorFilter(Color.parseColor("#06B6D4"))
                                btnVoice.contentDescription = "Listening active — Tap to pause listening"
                                btnVoice.background = android.graphics.drawable.GradientDrawable().apply {
                                    setColor(Color.parseColor("#16283B"))
                                    cornerRadius = dpToPx(12f).toFloat()
                                    setStroke(dpToPx(1.5f), Color.parseColor("#06B6D4"))
                                }
                                listeningCard.visibility = View.VISIBLE
                                listeningLabel.text = "Listening… Speak your command"
                                statusText.text = "Listening…"
                                headerActivityDot.state = ActivityDotState.LISTENING
                            }
                            com.taskscheduling.app.voice.ContinuousListeningState.SPEECH_DETECTED -> {
                                stopThinkingAnimation()
                                listeningCard.visibility = View.VISIBLE
                                listeningLabel.text = "Listening… (pause to finish)"
                                statusText.text = "Listening…"
                                headerActivityDot.state = ActivityDotState.LISTENING
                            }
                            com.taskscheduling.app.voice.ContinuousListeningState.UTTERANCE_FINALIZING -> {
                                stopThinkingAnimation()
                                listeningCard.visibility = View.VISIBLE
                                listeningLabel.text = "Processing speech…"
                                statusText.text = "Processing speech…"
                                headerActivityDot.state = ActivityDotState.PROCESSING
                            }
                            com.taskscheduling.app.voice.ContinuousListeningState.TRANSCRIBING,
                            com.taskscheduling.app.voice.ContinuousListeningState.COMMAND_PROCESSING -> {
                                listeningCard.visibility = View.VISIBLE
                                listeningLabel.text = "Thinking…"
                                startThinkingAnimation(statusText)
                                headerActivityDot.state = ActivityDotState.PROCESSING
                            }
                            com.taskscheduling.app.voice.ContinuousListeningState.EXECUTING -> {
                                stopThinkingAnimation()
                                listeningCard.visibility = View.GONE
                                statusText.text = "Running action…"
                                headerActivityDot.state = ActivityDotState.SPEAKING
                            }
                            com.taskscheduling.app.voice.ContinuousListeningState.MUTED -> {
                                stopThinkingAnimation()
                                listeningCard.visibility = View.VISIBLE
                                listeningLabel.text = "Microphone is muted — Tap mic to unmute"
                                statusText.text = "Microphone muted"
                                headerActivityDot.state = ActivityDotState.IDLE
                            }
                            com.taskscheduling.app.voice.ContinuousListeningState.ERROR -> {
                                stopThinkingAnimation()
                                statusText.text = "Error"
                                headerActivityDot.state = ActivityDotState.ERROR
                            }
                            com.taskscheduling.app.voice.ContinuousListeningState.STOPPED -> {
                                stopThinkingAnimation()
                                btnVoice.setImageResource(com.taskscheduling.app.R.drawable.ic_listening_paused)
                                btnVoice.setColorFilter(Color.parseColor("#94A3B8"))
                                btnVoice.contentDescription = "Listening paused — Tap to start listening"
                                btnVoice.background = android.graphics.drawable.GradientDrawable().apply {
                                    setColor(Color.parseColor("#1E293B"))
                                    cornerRadius = dpToPx(12f).toFloat()
                                    setStroke(dpToPx(1.5f), Color.parseColor("#334155"))
                                }
                                listeningCard.visibility = View.GONE
                                statusText.text = "Listening paused"
                                headerActivityDot.state = ActivityDotState.IDLE
                            }
                            com.taskscheduling.app.voice.ContinuousListeningState.IDLE -> {
                                stopThinkingAnimation()
                                btnVoice.setImageResource(com.taskscheduling.app.R.drawable.ic_listening_paused)
                                btnVoice.setColorFilter(Color.parseColor("#94A3B8"))
                                btnVoice.contentDescription = "Tap to start listening"
                                btnVoice.background = android.graphics.drawable.GradientDrawable().apply {
                                    setColor(Color.parseColor("#1E293B"))
                                    cornerRadius = dpToPx(12f).toFloat()
                                    setStroke(dpToPx(1.5f), Color.parseColor("#334155"))
                                }
                                listeningCard.visibility = View.GONE
                                statusText.text = "Ready"
                                headerActivityDot.state = ActivityDotState.IDLE
                            }
                        }
                    }
                }
                launch {
                    continuousVoiceController?.isMuted?.collect { isMuted ->
                        btnMute.setImageResource(if (isMuted) com.taskscheduling.app.R.drawable.ic_mic_off else com.taskscheduling.app.R.drawable.ic_mic)
                        btnMute.setColorFilter(Color.parseColor(if (isMuted) "#EF4444" else "#94A3B8"))
                        btnMute.contentDescription = if (isMuted) "Unmute microphone" else "Mute microphone"
                        btnMute.background = android.graphics.drawable.GradientDrawable().apply {
                            setColor(Color.parseColor(if (isMuted) "#2A1616" else "#1E293B"))
                            cornerRadius = dpToPx(12f).toFloat()
                            setStroke(dpToPx(1.5f), Color.parseColor(if (isMuted) "#EF4444" else "#334155"))
                        }
                    }
                }
                launch {
                    controller.uiState.collect { state ->
                        confirmationCard.visibility = View.GONE
                        runningCard.visibility = View.GONE
                        completedCard.visibility = View.GONE
                        errorCard.visibility = View.GONE

                        when (state) {
                            is AssistantUiState.Idle -> {
                                stopThinkingAnimation()
                                statusText.text = if (state.message.isNotBlank()) state.message else "Ready"
                                if (continuousVoiceController?.isContinuousListening?.value != true) {
                                    headerActivityDot.state = ActivityDotState.IDLE
                                }
                            }
                            is AssistantUiState.Listening -> {
                                stopThinkingAnimation()
                                statusText.text = "Listening…"
                                headerActivityDot.state = ActivityDotState.LISTENING
                            }
                            is AssistantUiState.Processing -> {
                                startThinkingAnimation(statusText)
                                headerActivityDot.state = ActivityDotState.PROCESSING
                            }
                            is AssistantUiState.ConfirmationRequired -> {
                                stopThinkingAnimation()
                                confirmationCard.visibility = View.VISIBLE
                                confirmTitle.text = when {
                                    state.workflow != null -> "Workflow: ${state.workflow.name}"
                                    state.taskDraft != null -> "New Task: ${state.taskDraft.title.ifBlank { "Untitled" }}"
                                    state.task != null -> "Task: ${state.task.title}"
                                    else -> "Action: ${state.intent?.displayName ?: state.command}"
                                }
                                confirmQuestion.text = state.message
                                btnRunConfirm.text = if (state.taskDraft != null) "SAVE" else "YES, RUN"
                                statusText.text = "Confirmation"
                                headerActivityDot.state = ActivityDotState.IDLE
                            }
                            is AssistantUiState.Running -> {
                                stopThinkingAnimation()
                                runningCard.visibility = View.VISIBLE
                                runTitle.text = "Running: ${state.workflowName}"
                                runStepInfo.text = "Step ${state.currentStepIndex} of ${state.totalSteps}"
                                statusText.text = "Running action…"
                                headerActivityDot.state = ActivityDotState.PROCESSING
                            }
                            is AssistantUiState.Completed -> {
                                stopThinkingAnimation()
                                completedCard.visibility = View.VISIBLE
                                compMessage.text = "${state.workflowName} completed."
                                statusText.text = "${state.workflowName} completed."
                                headerActivityDot.state = ActivityDotState.SPEAKING
                            }
                            is AssistantUiState.Error -> {
                                stopThinkingAnimation()
                                errorCard.visibility = View.VISIBLE
                                errorText.text = state.message
                                statusText.text = state.message
                                headerActivityDot.state = ActivityDotState.ERROR
                            }
                            is AssistantUiState.Speaking -> {
                                stopThinkingAnimation()
                                statusText.text = "Speaking…"
                                headerActivityDot.state = ActivityDotState.SPEAKING
                            }
                            else -> {}
                        }
                    }
                }
            }

            try {
                windowManager.addView(panelLayout, params)
                activeAssistantPanel = panelLayout
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun hideFloatingAssistantPanel() {
        mainHandler.post {
            stopThinkingAnimation()
            assistantCollectorJob?.cancel()
            assistantCollectorJob = null
            if (continuousVoiceController?.isContinuousListening?.value != true) {
                try {
                    voiceInputManager.stopListening()
                } catch (_: Exception) {}
            }
            activeAssistantPanel?.let {
                try {
                    windowManager.removeView(it)
                } catch (_: Exception) {}
                activeAssistantPanel = null
            }
        }
    }

    fun clearAll() {
        mainHandler.post {
            hideFloatingRecorder()
            hideRecordingToolbar()
            hideTargetCrosshair()
            hideSwipeCaptureOverlay()
            hideTextInputOverlay()
            hideFloatingAssistantPanel()
            hideFloatingAssistantShortcut()
            try {
                continuousVoiceController?.stopContinuousListening()
                speechOutputManager.stop()
            } catch (_: Exception) {}
            for (dot in activeTouchDots) {
                try {
                    windowManager.removeView(dot)
                } catch (_: Exception) {}
            }
            activeTouchDots.clear()
        }
    }

    /**
     * Custom View for the visual coordinate dot.
     * Dimensions: 220px wide by 160px high.
     * Center is at (cx = 110f, cy = 60f).
     */
    private class VisualDotView(
        context: Context,
        val targetX: Int,
        val targetY: Int,
        val labelText: String
    ) : View(context) {

        private var radius = 24f
        private var pulseRadius = 38f
        private var alphaVal = 1f

        private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#06B6D4")
            style = Paint.Style.STROKE
            strokeWidth = 6f
        }

        private val centerDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#38BDF8")
            style = Paint.Style.FILL
        }

        private val pulsePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#3306B6D4")
            style = Paint.Style.FILL
        }

        private val textBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#DD0F172A")
            style = Paint.Style.FILL
        }

        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 24f
            typeface = android.graphics.Typeface.MONOSPACE
            textAlign = Paint.Align.CENTER
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            setMeasuredDimension(220, 160)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val cx = 110f
            val cy = 60f

            // Pulse outer circle
            pulsePaint.alpha = (alphaVal * 90).toInt()
            canvas.drawCircle(cx, cy, pulseRadius, pulsePaint)

            // Inner solid dot
            centerDotPaint.alpha = (alphaVal * 255).toInt()
            canvas.drawCircle(cx, cy, radius, centerDotPaint)

            // Ring stroke
            ringPaint.alpha = (alphaVal * 255).toInt()
            canvas.drawCircle(cx, cy, radius + 8f, ringPaint)

            // Badge text with coordinate
            textBgPaint.alpha = (alphaVal * 230).toInt()
            val textWidth = textPaint.measureText(labelText)
            val rectLeft = cx - (textWidth / 2f) - 12f
            val rectTop = cy + 30f
            val rectRight = cx + (textWidth / 2f) + 12f
            val rectBottom = rectTop + 34f
            canvas.drawRoundRect(rectLeft, rectTop, rectRight, rectBottom, 12f, 12f, textBgPaint)

            textPaint.alpha = (alphaVal * 255).toInt()
            canvas.drawText(labelText, cx, rectTop + 24f, textPaint)
        }

        fun startAnimation(onComplete: () -> Unit) {
            val animator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 1200L
                addUpdateListener { va ->
                    val fraction = va.animatedFraction
                    pulseRadius = 38f + (fraction * 24f)
                    if (fraction > 0.6f) {
                        alphaVal = 1f - ((fraction - 0.6f) / 0.4f)
                    }
                    invalidate()
                }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        onComplete()
                    }
                })
            }
            animator.start()
        }
    }

    /**
     * Minimal, highly transparent precision crosshair reticle (32 dp visible diameter).
     * Touch interaction area is 48 dp.
     * Hollow center ensures underlying buttons, icons, and text remain 100% visible and unobstructed.
     *
     *       ──┼──
     *         ○
     *       ──┼──
     */
    private class PrecisionReticleView(
        context: Context,
        private val visibleRadiusPx: Float // 16 dp
    ) : View(context) {

        init {
            setBackgroundColor(Color.TRANSPARENT)
        }

        // Shadow backing for contrast against bright white backgrounds
        private val shadowLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#4D000000") // 30% black
            strokeWidth = 3.5f
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }

        // Crisp thin crosshair line paint (cyan)
        private val crosshairPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E606B6D4") // 90% neon cyan
            strokeWidth = 2f
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }

        // Shadow backing for circle
        private val shadowRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#4D000000")
            strokeWidth = 3.5f
            style = Paint.Style.STROKE
        }

        // Thin translucent circular ring (approx 21 dp diameter)
        private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#CC38BDF8") // 80% sky cyan
            strokeWidth = 2f
            style = Paint.Style.STROKE
        }

        // Tiny 2px precision center point
        private val centerDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FFEE4444") // bright red precision center dot
            style = Paint.Style.FILL
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val cx = width / 2f
            val cy = height / 2f

            // Visual diameter is 32 dp (radius = visibleRadiusPx)
            val ringRadius = visibleRadiusPx * 0.65f // ~10.4 dp (~21 dp diameter ring)
            val innerGap = ringRadius * 0.55f        // crosshairs leave center open
            val outerLen = visibleRadiusPx            // 16 dp max extent

            // 1. Shadow backing for high contrast against light mode / white apps
            canvas.drawLine(cx - outerLen, cy, cx - innerGap, cy, shadowLinePaint)
            canvas.drawLine(cx + innerGap, cy, cx + outerLen, cy, shadowLinePaint)
            canvas.drawLine(cx, cy - outerLen, cx, cy - innerGap, shadowLinePaint)
            canvas.drawLine(cx, cy + innerGap, cx, cy + outerLen, shadowLinePaint)
            canvas.drawCircle(cx, cy, ringRadius, shadowRingPaint)

            // 2. Minimal crosshair lines:
            //       ──┼──
            //         ○
            //       ──┼──
            canvas.drawLine(cx - outerLen, cy, cx - innerGap, cy, crosshairPaint)
            canvas.drawLine(cx + innerGap, cy, cx + outerLen, cy, crosshairPaint)
            canvas.drawLine(cx, cy - outerLen, cx, cy - innerGap, crosshairPaint)
            canvas.drawLine(cx, cy + innerGap, cx, cy + outerLen, crosshairPaint)

            // 3. Thin translucent ring (hollow center, text/icons underneath stay 100% visible!)
            canvas.drawCircle(cx, cy, ringRadius, ringPaint)

            // 4. Tiny 2px center dot for single-pixel alignment
            canvas.drawCircle(cx, cy, 2.5f, centerDotPaint)
        }
    }

    /**
     * Translucent full-screen overlay to record a swipe gesture from start touch to release touch.
     * Records coordinates only. NEVER calls dispatchGesture or executes actions.
     */
    @SuppressLint("ClickableViewAccessibility")
    private class SwipeCaptureView(context: Context) : View(context) {
        var onCaptured: ((startX: Int, startY: Int, endX: Int, endY: Int, durationMs: Long) -> Unit)? = null
        var onCancel: (() -> Unit)? = null

        private var startX = 0f
        private var startY = 0f
        private var currentX = 0f
        private var currentY = 0f
        private var isDragging = false
        private var touchStartTime = 0L

        private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E606B6D4") // neon cyan
            strokeWidth = 6f
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }

        private val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FFEE4444")
            style = Paint.Style.FILL
        }

        private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 32f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }

        private val bgHintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#B30F172A") // 70% dark slate
            style = Paint.Style.FILL
        }

        init {
            setBackgroundColor(Color.parseColor("#33000000")) // subtle 20% dark tint so user knows they are in swipe capture mode
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            // Draw top instruction banner
            val bannerRect = android.graphics.RectF(40f, 100f, width - 40f, 220f)
            canvas.drawRoundRect(bannerRect, 20f, 20f, bgHintPaint)
            val hint = if (!isDragging) "SWIPE MODE: Drag finger to record swipe (Tap top right to cancel)" else "Recording Swipe..."
            canvas.drawText(hint, width / 2f, 170f, hintPaint)

            if (isDragging) {
                // Draw start dot
                canvas.drawCircle(startX, startY, 16f, pointPaint)
                // Draw swipe trajectory line
                canvas.drawLine(startX, startY, currentX, currentY, linePaint)
                // Draw arrow/current dot
                canvas.drawCircle(currentX, currentY, 20f, linePaint)
            }
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // Check if tap in top cancel region
                    if (event.y < 90f && event.x > width - 180f) {
                        onCancel?.invoke()
                        return true
                    }
                    startX = event.rawX
                    startY = event.rawY
                    currentX = startX
                    currentY = startY
                    isDragging = true
                    touchStartTime = System.currentTimeMillis()
                    invalidate()
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    currentX = event.rawX
                    currentY = event.rawY
                    invalidate()
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    val endX = event.rawX
                    val endY = event.rawY
                    val duration = (System.currentTimeMillis() - touchStartTime).coerceIn(100L, 2000L)
                    isDragging = false
                    invalidate()

                    // Check minimum swipe distance (at least 20px) to distinguish from accidental tap
                    val dx = Math.abs(endX - startX)
                    val dy = Math.abs(endY - startY)
                    if (dx > 20 || dy > 20) {
                        onCaptured?.invoke(startX.toInt(), startY.toInt(), endX.toInt(), endY.toInt(), duration)
                    } else {
                        onCancel?.invoke()
                    }
                    return true
                }
                MotionEvent.ACTION_CANCEL -> {
                    isDragging = false
                    onCancel?.invoke()
                    return true
                }
            }
            return super.onTouchEvent(event)
        }
    }
}
