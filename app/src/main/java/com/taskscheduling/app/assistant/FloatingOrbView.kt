package com.taskscheduling.app.assistant

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.cos
import kotlin.math.sin

/**
 * Premium glassmorphism circle containing an abstract animated luminous pulse.
 * Exactly implements the floating orb design from the concept board:
 * - NO cloud icon, NO robot, NO microphone pictogram, NO text or letters.
 * - Multi-state abstract living light:
 *   - IDLE: slow subtle luminous pulse & gentle breathing core.
 *   - LISTENING: responsive expanding luminous wave rings.
 *   - PROCESSING: rotating dual orbital light rings with glowing center.
 *   - SPEAKING: rhythmic undulating wave harmonic pulse.
 *   - ERROR / DISABLED: restrained warning status indication.
 * - Native Canvas rendering with zero lag, respects battery & reduced motion.
 */
class FloatingOrbView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var state: ActivityDotState = ActivityDotState.IDLE
        set(value) {
            if (field != value) {
                field = value
                updateAnimatorForState()
                invalidate()
            }
        }

    var enableAnimations: Boolean = true
        set(value) {
            field = value
            if (!value) {
                stopAnimation()
            } else {
                updateAnimatorForState()
            }
            invalidate()
        }

    // Paints
    private val sphereBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val orbitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private var animator: ValueAnimator? = null
    private var animFraction = 0f
    private val orbBounds = RectF()

    init {
        updateAnimatorForState()
    }

    private fun updateAnimatorForState() {
        stopAnimation()
        if (!enableAnimations || state == ActivityDotState.DISABLED) {
            invalidate()
            return
        }

        val duration = when (state) {
            ActivityDotState.IDLE -> 3000L
            ActivityDotState.LISTENING -> 1500L
            ActivityDotState.PROCESSING -> 1800L
            ActivityDotState.SPEAKING -> 1200L
            ActivityDotState.ERROR -> 2000L
            else -> 2500L
        }

        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            this.duration = duration
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { va ->
                animFraction = va.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun stopAnimation() {
        animator?.cancel()
        animator = null
        animFraction = 0f
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (visibility == VISIBLE && enableAnimations) {
            updateAnimatorForState()
        }
    }

    override fun onDetachedFromWindow() {
        stopAnimation()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val cx = w / 2f
        val cy = h / 2f
        val radius = (minOf(w, h) / 2f) - 3f

        orbBounds.set(cx - radius, cy - radius, cx + radius, cy + radius)

        // 1. Deep Glass Sphere Background
        sphereBgPaint.shader = RadialGradient(
            cx, cy - (radius * 0.25f), radius * 1.1f,
            intArrayOf(
                Color.argb(230, 24, 34, 60),
                Color.argb(245, 12, 16, 32),
                Color.argb(255, 6, 8, 18)
            ),
            floatArrayOf(0f, 0.65f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, radius, sphereBgPaint)

        // 2. State-Driven Animated Luminous Core & Waves
        when (state) {
            ActivityDotState.IDLE -> drawIdleState(canvas, cx, cy, radius)
            ActivityDotState.LISTENING -> drawListeningState(canvas, cx, cy, radius)
            ActivityDotState.PROCESSING -> drawProcessingState(canvas, cx, cy, radius)
            ActivityDotState.SPEAKING -> drawSpeakingState(canvas, cx, cy, radius)
            ActivityDotState.ERROR -> drawErrorState(canvas, cx, cy, radius)
            ActivityDotState.DISABLED -> drawDisabledState(canvas, cx, cy, radius)
        }

        // 3. Specular Glass Highlight Arc (Top-Left)
        highlightPaint.shader = LinearGradient(
            cx - radius, cy - radius, cx, cy,
            intArrayOf(
                Color.argb(120, 255, 255, 255),
                Color.argb(30, 160, 210, 255),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.45f, 1f),
            Shader.TileMode.CLAMP
        )
        val highlightRect = RectF(cx - (radius * 0.75f), cy - (radius * 0.85f), cx + (radius * 0.4f), cy - (radius * 0.1f))
        canvas.drawOval(highlightRect, highlightPaint)

        // 4. Subtle Outer Glass Rim
        rimPaint.strokeWidth = 2.5f
        rimPaint.shader = LinearGradient(
            cx - radius, cy - radius, cx + radius, cy + radius,
            intArrayOf(
                Color.argb(160, 99, 102, 241),
                Color.argb(100, 56, 189, 248),
                Color.argb(40, 255, 255, 255),
                Color.argb(80, 139, 92, 246)
            ),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, radius - 1.25f, rimPaint)
    }

    private fun drawIdleState(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        val breath = sin(animFraction * Math.PI.toFloat() * 2f) * 0.15f + 1f
        val coreRadius = radius * 0.38f * breath

        // Soft outer halo
        corePaint.shader = RadialGradient(
            cx, cy, coreRadius * 2.2f,
            intArrayOf(
                Color.argb(160, 56, 189, 248),
                Color.argb(70, 99, 102, 241),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, coreRadius * 2.2f, corePaint)

        // Glowing Core
        corePaint.shader = RadialGradient(
            cx, cy, coreRadius,
            intArrayOf(
                Color.argb(230, 224, 242, 254),
                Color.argb(200, 56, 189, 248),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, coreRadius, corePaint)

        // Subtle orbital ring
        orbitPaint.strokeWidth = 1.5f
        orbitPaint.color = Color.argb((60 * breath).toInt().coerceIn(0, 255), 147, 197, 253)
        canvas.save()
        canvas.rotate(animFraction * 360f, cx, cy)
        canvas.drawOval(cx - (radius * 0.65f), cy - (radius * 0.28f), cx + (radius * 0.65f), cy + (radius * 0.28f), orbitPaint)
        canvas.restore()
    }

    private fun drawListeningState(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        // Expanding concentric acoustic waves
        for (i in 0..2) {
            val wavePhase = (animFraction + (i * 0.33f)) % 1f
            val waveRadius = radius * (0.25f + (wavePhase * 0.65f))
            val waveAlpha = ((1f - wavePhase) * 190).toInt().coerceIn(0, 255)

            wavePaint.strokeWidth = 2.5f - (wavePhase * 1.5f)
            wavePaint.color = Color.argb(waveAlpha, 56, 189, 248)
            canvas.drawCircle(cx, cy, waveRadius, wavePaint)
        }

        // Vibrant expanding core
        val pulse = 1f + (sin(animFraction * Math.PI.toFloat() * 4f) * 0.18f)
        val coreRadius = radius * 0.34f * pulse
        corePaint.shader = RadialGradient(
            cx, cy, coreRadius * 1.5f,
            intArrayOf(
                Color.argb(255, 255, 255, 255),
                Color.argb(220, 56, 189, 248),
                Color.argb(100, 6, 182, 212),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.35f, 0.7f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, coreRadius * 1.5f, corePaint)
    }

    private fun drawProcessingState(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        // Rotating dual planetary light rings
        val angle = animFraction * 360f

        orbitPaint.strokeWidth = 2f
        orbitPaint.color = Color.argb(180, 56, 189, 248)

        canvas.save()
        canvas.rotate(angle, cx, cy)
        canvas.drawOval(cx - (radius * 0.72f), cy - (radius * 0.24f), cx + (radius * 0.72f), cy + (radius * 0.24f), orbitPaint)
        canvas.restore()

        canvas.save()
        canvas.rotate(-angle * 1.4f, cx, cy)
        orbitPaint.color = Color.argb(180, 139, 92, 246)
        canvas.drawOval(cx - (radius * 0.58f), cy - (radius * 0.24f), cx + (radius * 0.58f), cy + (radius * 0.24f), orbitPaint)
        canvas.restore()

        // Luminous nucleus
        val nucleusRadius = radius * 0.32f
        corePaint.shader = RadialGradient(
            cx, cy, nucleusRadius,
            intArrayOf(
                Color.argb(255, 245, 243, 255),
                Color.argb(220, 139, 92, 246),
                Color.argb(120, 99, 102, 241),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.4f, 0.75f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, nucleusRadius, corePaint)
    }

    private fun drawSpeakingState(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        // Rhythmic vertical wave pulse undulating smoothly
        val barCount = 7
        val barWidth = radius * 0.12f
        val maxBarHeight = radius * 1.1f
        val spacing = radius * 0.22f
        val startX = cx - ((barCount - 1) * spacing / 2f)

        for (i in 0 until barCount) {
            val x = startX + (i * spacing)
            val offset = i * 0.4f
            val waveHeight = (sin((animFraction * Math.PI.toFloat() * 3f) + offset) * 0.5f + 0.5f) * maxBarHeight * 0.65f + (radius * 0.25f)
            val alpha = (160 + (sin((animFraction * Math.PI.toFloat() * 3f) + offset) * 90f)).toInt().coerceIn(100, 255)

            corePaint.shader = LinearGradient(
                x, cy - (waveHeight / 2f), x, cy + (waveHeight / 2f),
                intArrayOf(
                    Color.argb(0, 56, 189, 248),
                    Color.argb(alpha, 139, 92, 246),
                    Color.argb(alpha, 255, 255, 255),
                    Color.argb(alpha, 56, 189, 248),
                    Color.argb(0, 139, 92, 246)
                ),
                null,
                Shader.TileMode.CLAMP
            )
            canvas.drawRoundRect(x - (barWidth / 2f), cy - (waveHeight / 2f), x + (barWidth / 2f), cy + (waveHeight / 2f), barWidth / 2f, barWidth / 2f, corePaint)
        }
    }

    private fun drawErrorState(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        val pulse = sin(animFraction * Math.PI.toFloat() * 2f) * 0.15f + 1f
        val coreRadius = radius * 0.35f * pulse

        corePaint.shader = RadialGradient(
            cx, cy, coreRadius * 1.8f,
            intArrayOf(
                Color.argb(220, 244, 63, 94),
                Color.argb(120, 225, 29, 72),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.6f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, coreRadius * 1.8f, corePaint)
    }

    private fun drawDisabledState(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        val coreRadius = radius * 0.25f
        corePaint.shader = RadialGradient(
            cx, cy, coreRadius,
            intArrayOf(
                Color.argb(100, 148, 163, 184),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, coreRadius, corePaint)
    }
}
