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
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.taskscheduling.app.domain.model.ActionType
import com.taskscheduling.app.recording.RecordingManager

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
     * Shows a persistent floating recording toolbar with step count and recording controls.
     */
    @SuppressLint("ClickableViewAccessibility", "SetTextI18n")
    fun showRecordingToolbar(
        onFinishRecording: () -> Unit,
        onCancelRecording: () -> Unit
    ) {
        mainHandler.post {
            if (activeRecordingToolbar != null) return@post

            val layout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dpToPx(16f), dpToPx(10f), dpToPx(16f), dpToPx(10f))
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(Color.parseColor("#EE111827"))
                    cornerRadius = dpToPx(18f).toFloat()
                    setStroke(dpToPx(1.5f), Color.parseColor("#3306B6D4"))
                }
                elevation = 16f
            }

            // Top status row with red pulsing record dot
            val topRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            val recordDot = TextView(context).apply {
                text = "●"
                setTextColor(Color.parseColor("#EF4444"))
                textSize = 14f
            }

            val statusText = TextView(context).apply {
                text = " RECORDING"
                setTextColor(Color.WHITE)
                textSize = 12f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }

            val stepCountText = TextView(context).apply {
                text = "  |  0 steps"
                setTextColor(Color.parseColor("#06B6D4"))
                textSize = 12f
            }

            topRow.addView(recordDot)
            topRow.addView(statusText)
            topRow.addView(stepCountText)
            layout.addView(topRow)

            val guideText = TextView(context).apply {
                text = "Tap 🎯 Target to calibrate exact coordinates"
                setTextColor(Color.parseColor("#9CA3AF"))
                textSize = 10f
                setPadding(0, dpToPx(4f), 0, dpToPx(4f))
            }
            layout.addView(guideText)

            // Buttons row
            val btnRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            fun createPillButton(text: String, bgColor: Int, textColor: Int, onClick: () -> Unit): TextView {
                return TextView(context).apply {
                    this.text = text
                    this.setTextColor(textColor)
                    textSize = 11f
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                    setPadding(dpToPx(12f), dpToPx(6f), dpToPx(12f), dpToPx(6f))
                    background = android.graphics.drawable.GradientDrawable().apply {
                        setColor(bgColor)
                        cornerRadius = dpToPx(12f).toFloat()
                    }
                    val params = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { setMargins(dpToPx(4f), dpToPx(2f), dpToPx(4f), dpToPx(2f)) }
                    layoutParams = params
                    setOnClickListener { onClick() }
                }
            }

            // Dynamic action buttons
            var btnRecordTap: TextView? = null
            var btnHold: TextView? = null
            var btnSwipe: TextView? = null

            // + Tap Target tool (toggles compact 32dp precision crosshair)
            val btnTarget = createPillButton("🎯 Target", Color.parseColor("#1E293B"), Color.parseColor("#38BDF8")) {
                if (activeTargetOverlay != null) {
                    hideTargetCrosshair()
                } else {
                    showTargetCrosshair(
                        onMoved = { tx, ty ->
                            btnRecordTap?.text = "⏺ Record Tap ($tx, $ty)"
                        },
                        onDismissed = {
                            guideText.text = "Tap 🎯 Target to calibrate exact coordinates"
                        }
                    )
                    btnRecordTap?.visibility = View.VISIBLE
                    btnHold?.visibility = View.VISIBLE
                    guideText.text = "Drag 🎯 crosshair over element, then tap Record Tap"
                    val (ix, iy) = calculateCurrentTargetCoordinate()
                    btnRecordTap?.text = "⏺ Record Tap ($ix, $iy)"
                }
            }

            // Explicit Record Tap button on toolbar (preserves target positioning vs execution separation)
            btnRecordTap = createPillButton("⏺ Record Tap", Color.parseColor("#059669"), Color.WHITE) {
                val (tx, ty) = calculateCurrentTargetCoordinate()
                RecordingManager.recordTap(tx, ty)
                showTouchDot(tx, ty, "TAP X: $tx, Y: $ty")
                updateStepCount(stepCountText)
            }.apply { visibility = View.GONE }

            // Explicit Hold (Long Press) button on toolbar
            btnHold = createPillButton("⏱ Hold", Color.parseColor("#D97706"), Color.WHITE) {
                val (tx, ty) = calculateCurrentTargetCoordinate()
                RecordingManager.recordLongPress(tx, ty, 900L)
                showTouchDot(tx, ty, "HOLD X: $tx, Y: $ty")
                updateStepCount(stepCountText)
            }.apply { visibility = View.GONE }

            // Swipe Recording Tool
            btnSwipe = createPillButton("↔ Swipe", Color.parseColor("#1E293B"), Color.parseColor("#38BDF8")) {
                guideText.text = "Swipe Recording: Drag finger on screen from start to end"
                showSwipeCaptureOverlay(
                    onSwipeCaptured = { sx, sy, ex, ey, dur ->
                        RecordingManager.recordSwipe(sx, sy, ex, ey, dur)
                        showTouchDot(sx, sy, "START ($sx, $sy)")
                        showTouchDot(ex, ey, "END ($ex, $ey)")
                        updateStepCount(stepCountText)
                        guideText.text = "Swipe recorded: ($sx, $sy) → ($ex, $ey)"
                    },
                    onCancelled = {
                        guideText.text = "Swipe recording cancelled"
                    }
                )
            }

            // + Wait
            val btnWait = createPillButton("+ Wait", Color.parseColor("#1E293B"), Color.parseColor("#F59E0B")) {
                RecordingManager.recordWait(1000L)
                updateStepCount(stepCountText)
                guideText.text = "Recorded Wait 1000ms"
            }

            // + Back
            val btnBack = createPillButton("+ Back", Color.parseColor("#1E293B"), Color.parseColor("#A78BFA")) {
                RecordingManager.recordBack()
                updateStepCount(stepCountText)
                guideText.text = "Recorded Back action"
            }

            // Done & Review
            val btnDone = createPillButton("✓ Done", Color.parseColor("#059669"), Color.WHITE) {
                hideRecordingToolbar()
                hideTargetCrosshair()
                hideSwipeCaptureOverlay()
                onFinishRecording()
            }

            // Cancel
            val btnCancel = createPillButton("✕", Color.parseColor("#374151"), Color.parseColor("#9CA3AF")) {
                hideRecordingToolbar()
                hideTargetCrosshair()
                hideSwipeCaptureOverlay()
                onCancelRecording()
            }

            btnRow.addView(btnTarget)
            btnRow.addView(btnRecordTap)
            btnRow.addView(btnHold)
            btnRow.addView(btnSwipe)
            btnRow.addView(btnWait)
            btnRow.addView(btnBack)
            btnRow.addView(btnDone)
            btnRow.addView(btnCancel)
            layout.addView(btnRow)

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                y = dpToPx(30f)
            }

            var initialX = 0
            var initialY = 0
            var initialTouchX = 0f
            var initialTouchY = 0f

            layout.setOnTouchListener { _, event ->
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
                            params.x = initialX + dx
                            params.y = initialY + dy
                            try {
                                windowManager.updateViewLayout(layout, params)
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
                windowManager.addView(layout, params)
                activeRecordingToolbar = layout
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun updateStepCount(view: TextView) {
        val count = RecordingManager.recordedSteps.value.size
        view.text = "  |  $count step" + if (count != 1) "s" else ""
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

    fun clearAll() {
        mainHandler.post {
            hideRecordingToolbar()
            hideTargetCrosshair()
            hideSwipeCaptureOverlay()
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
