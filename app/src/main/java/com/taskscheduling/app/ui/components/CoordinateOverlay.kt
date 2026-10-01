package com.taskscheduling.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.taskscheduling.app.domain.model.ActionType
import com.taskscheduling.app.domain.model.CoordinateAction
import com.taskscheduling.app.ui.theme.AccentAmber
import com.taskscheduling.app.ui.theme.AccentViolet
import com.taskscheduling.app.ui.theme.NeonCyan

@Composable
fun CoordinateOverlay(
    activeCoordinate: CoordinateAction?,
    actionType: ActionType?,
    modifier: Modifier = Modifier,
    virtualWidth: Float = 1080f,
    virtualHeight: Float = 2400f,
    showRadarGrid: Boolean = false
) {
    val rippleScale = remember { Animatable(0.2f) }
    val rippleAlpha = remember { Animatable(1.0f) }

    LaunchedEffect(activeCoordinate, actionType) {
        if (activeCoordinate != null) {
            rippleScale.snapTo(0.2f)
            rippleAlpha.snapTo(1.0f)
            rippleScale.animateTo(
                targetValue = 1.0f,
                animationSpec = tween(durationMillis = 600, easing = LinearEasing)
            )
            rippleAlpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 600, easing = LinearEasing)
            )
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val scaleX = size.width / virtualWidth
            val scaleY = size.height / virtualHeight

            // 1. Grid Radar Overlay if enabled
            if (showRadarGrid) {
                val gridLinesX = 5
                val gridLinesY = 10
                for (i in 1 until gridLinesX) {
                    val x = size.width * (i.toFloat() / gridLinesX)
                    drawLine(
                        color = NeonCyan.copy(alpha = 0.15f),
                        start = Offset(x, 0f),
                        end = Offset(x, size.height),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                    )
                }
                for (j in 1 until gridLinesY) {
                    val y = size.height * (j.toFloat() / gridLinesY)
                    drawLine(
                        color = NeonCyan.copy(alpha = 0.15f),
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                    )
                }
            }

            // 2. Active Touch / Gesture Action Rendering
            activeCoordinate?.let { coord ->
                val startX = coord.x * scaleX
                val startY = coord.y * scaleY

                if (coord.endX != null && coord.endY != null && (actionType == ActionType.SWIPE || actionType == ActionType.DRAG)) {
                    val endX = coord.endX * scaleX
                    val endY = coord.endY * scaleY

                    // Draw swipe path dashed trajectory
                    drawLine(
                        color = AccentViolet.copy(alpha = 0.6f),
                        start = Offset(startX, startY),
                        end = Offset(endX, endY),
                        strokeWidth = 4.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f),
                        cap = StrokeCap.Round
                    )

                    // Start marker
                    drawCircle(
                        color = NeonCyan,
                        radius = 8.dp.toPx(),
                        center = Offset(startX, startY)
                    )

                    // End marker
                    drawCircle(
                        color = AccentViolet,
                        radius = 10.dp.toPx(),
                        center = Offset(endX, endY)
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 4.dp.toPx(),
                        center = Offset(endX, endY)
                    )
                } else {
                    // Tap / Long Press Ripple
                    val maxRadius = if (actionType == ActionType.LONG_PRESS) 36.dp.toPx() else 28.dp.toPx()
                    val currentRadius = maxRadius * rippleScale.value

                    // Expanding outer ripple
                    drawCircle(
                        color = if (actionType == ActionType.LONG_PRESS) AccentAmber.copy(alpha = rippleAlpha.value) else NeonCyan.copy(alpha = rippleAlpha.value),
                        radius = currentRadius,
                        center = Offset(startX, startY),
                        style = Stroke(width = 3.dp.toPx())
                    )

                    // Glowing center dot
                    drawCircle(
                        color = if (actionType == ActionType.LONG_PRESS) AccentAmber else NeonCyan,
                        radius = 7.dp.toPx(),
                        center = Offset(startX, startY)
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 3.dp.toPx(),
                        center = Offset(startX, startY)
                    )
                }
            }
        }

        // Active coordinate label pill
        activeCoordinate?.let { coord ->
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp)
                    .background(Color(0xE6090D16), RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                val text = if (coord.endX != null) {
                    "${actionType?.label ?: "SWIPE"}: (${coord.x}, ${coord.y}) → (${coord.endX}, ${coord.endY})"
                } else {
                    "${actionType?.label ?: "TOUCH"}: X: ${coord.x}  Y: ${coord.y}"
                }
                Text(
                    text = text,
                    color = NeonCyan,
                    fontSize = 11.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
            }
        }
    }
}
