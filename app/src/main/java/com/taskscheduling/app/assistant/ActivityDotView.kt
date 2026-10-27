package com.taskscheduling.app.assistant

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.cos
import kotlin.math.sin

/**
 * States for the floating assistant's activity dot indicator.
 */
enum class ActivityDotState {
    IDLE,
    LISTENING,
    PROCESSING,
    SPEAKING,
    ERROR,
    DISABLED
}

/**
 * A subtle, battery-efficient, state-driven animated activity dot for the floating assistant.
 * Communicates idle, listening, processing, speaking, and error states.
 * Respects animation toggle preferences and reduced-motion settings.
 */
class ActivityDotView @JvmOverloads constructor(
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

    private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val pulsePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    private val orbitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private var animator: ValueAnimator? = null
    private var animFraction = 0f
    private val arcBounds = RectF()

    init {
        updateAnimatorForState()
    }

    private fun updateAnimatorForState() {
        stopAnimation()
        if (!enableAnimations || state == ActivityDotState.IDLE || state == ActivityDotState.DISABLED) {
            invalidate()
            return
        }

        val duration = when (state) {
            ActivityDotState.LISTENING -> 1200L
            ActivityDotState.PROCESSING -> 1000L
            ActivityDotState.SPEAKING -> 800L
            ActivityDotState.ERROR -> 1500L
            else -> 1000L
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

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility == VISIBLE && enableAnimations) {
            updateAnimatorForState()
        } else {
            stopAnimation()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val cx = width / 2f
        val cy = height / 2f
        val radius = (Math.min(width, height) / 2f) * 0.7f

        when (state) {
            ActivityDotState.IDLE -> {
                basePaint.color = Color.parseColor("#06B6D4") // Cyan
                canvas.drawCircle(cx, cy, radius * 0.75f, basePaint)
            }
            ActivityDotState.LISTENING -> {
                basePaint.color = Color.parseColor("#EF4444") // Red active recording
                canvas.drawCircle(cx, cy, radius * 0.8f, basePaint)

                if (enableAnimations) {
                    val pulseRadius = radius * (0.8f + 0.6f * animFraction)
                    val alpha = ((1f - animFraction) * 200).toInt().coerceIn(0, 255)
                    pulsePaint.color = Color.parseColor("#EF4444")
                    pulsePaint.alpha = alpha
                    pulsePaint.strokeWidth = radius * 0.25f
                    canvas.drawCircle(cx, cy, pulseRadius, pulsePaint)
                }
            }
            ActivityDotState.PROCESSING -> {
                basePaint.color = Color.parseColor("#38BDF8") // Sky Blue
                canvas.drawCircle(cx, cy, radius * 0.5f, basePaint)

                if (enableAnimations) {
                    val angle = animFraction * 2.0 * Math.PI
                    val orbitDistance = radius * 0.85f
                    val ox = cx + (orbitDistance * cos(angle)).toFloat()
                    val oy = cy + (orbitDistance * sin(angle)).toFloat()

                    orbitPaint.color = Color.parseColor("#818CF8") // Indigo
                    canvas.drawCircle(ox, oy, radius * 0.35f, orbitPaint)
                }
            }
            ActivityDotState.SPEAKING -> {
                basePaint.color = Color.parseColor("#10B981") // Emerald Green
                val scale = if (enableAnimations) {
                    0.7f + 0.3f * Math.abs(sin(animFraction * Math.PI * 2)).toFloat()
                } else 0.85f
                canvas.drawCircle(cx, cy, radius * scale, basePaint)

                if (enableAnimations) {
                    val outerRadius = radius * (scale + 0.3f)
                    pulsePaint.color = Color.parseColor("#34D399")
                    pulsePaint.alpha = 100
                    pulsePaint.strokeWidth = radius * 0.2f
                    canvas.drawCircle(cx, cy, outerRadius, pulsePaint)
                }
            }
            ActivityDotState.ERROR -> {
                basePaint.color = Color.parseColor("#F43F5E") // Rose Red
                canvas.drawCircle(cx, cy, radius * 0.85f, basePaint)
            }
            ActivityDotState.DISABLED -> {
                basePaint.color = Color.parseColor("#64748B") // Slate Grey
                canvas.drawCircle(cx, cy, radius * 0.6f, basePaint)
            }
        }
    }
}
