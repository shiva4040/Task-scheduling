package com.taskscheduling.app.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

// =========================================================================
// GLASSMORPHISM & DESIGN TOKENS (Theme-Aware)
// =========================================================================

// Backward-compatibility static colors (used when theme context is unavailable)
val GlassBgDeep = Color(0xFF090D18)
val GlassSurface = Color(0xFF111827)
val GlassCard = Color(0xFF1E293B)
val GlassCardElevated = Color(0xFF263348)
val GlassCardSubtle = Color(0xFF10192A)

val GlassFillNormal = Color(0xCC1E293B)
val GlassFillElevated = Color(0xE6263348)
val GlassFillHighlight = Color(0x1F6366F1)
val GlassBorderSubtle = Color(0x266366F1)

val GlassRimBorder = Brush.verticalGradient(
    colors = listOf(
        Color(0x666366F1),
        Color(0x2694A3B8),
        Color(0x101E293B)
    )
)

val GlassRimBorderSubtle = Brush.verticalGradient(
    colors = listOf(
        Color(0x3394A3B8),
        Color(0x1A475569)
    )
)

val Glass3DButtonBrush = Brush.verticalGradient(
    colors = listOf(
        Color(0xFF6366F1),
        Color(0xFF4F46E5)
    )
)

val Glass3DSecondaryButtonBrush = Brush.verticalGradient(
    colors = listOf(
        Color(0xFF263348),
        Color(0xFF1E293B)
    )
)

val Glass3DDestructiveButtonBrush = Brush.verticalGradient(
    colors = listOf(
        Color(0xFFF43F5E),
        Color(0xFFBE123C)
    )
)

val GlassCyan = Color(0xFF38BDF8)
val GlassIndigo = Color(0xFF6366F1)
val GlassEmerald = Color(0xFF10B981)
val GlassAmber = Color(0xFFF59E0B)
val GlassRose = Color(0xFFF43F5E)

val GlassTextPrimary = Color(0xFFF8FAFC)
val GlassTextSecondary = Color(0xFF94A3B8)
val GlassTextTertiary = Color(0xFF64748B)

// =========================================================================
// REUSABLE THEME-AWARE COMPONENTS
// =========================================================================

/**
 * Premium card container featuring clean geometry, responsive light/dark
 * surface colors, subtle specular borders, and soft elevation.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    backgroundColor: Color = Color.Unspecified,
    borderBrush: Brush? = null,
    borderWidth: Dp = 1.dp,
    elevation: Dp = 3.dp,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val themeColors = AppTheme.colors
    val effectiveBg = if (backgroundColor != Color.Unspecified) backgroundColor else themeColors.cardBackground
    val effectiveBorder = borderBrush ?: themeColors.cardRimBrush

    val clickModifier = if (onClick != null) {
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            role = Role.Button,
            onClick = onClick
        )
    } else Modifier

    Box(
        modifier = modifier
            .shadow(
                elevation = elevation,
                shape = shape,
                clip = false,
                ambientColor = themeColors.shadowColor,
                spotColor = themeColors.shadowColor
            )
            .clip(shape)
            .background(effectiveBg)
            .border(borderWidth, effectiveBorder, shape)
            .then(clickModifier)
            .padding(contentPadding)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            content()
        }
    }
}

/**
 * Tactile button with subtle gradient, pressed micro-interaction,
 * and accessible touch target (>= 48dp).
 */
@Composable
fun Glass3DButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    isSecondary: Boolean = false,
    isDestructive: Boolean = false,
    accentColor: Color? = null,
    contentColor: Color? = null,
    shape: Shape = RoundedCornerShape(14.dp),
    minHeight: Dp = 48.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 18.dp, vertical = 12.dp)
) {
    val themeColors = AppTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale = if (isPressed && enabled && !isLoading) 0.97f else 1.0f

    val backgroundBrush = when {
        !enabled -> Brush.verticalGradient(
            if (themeColors.isDark) listOf(Color(0xFF1E293B), Color(0xFF162031))
            else listOf(Color(0xFFE2E8F0), Color(0xFFCBD5E1))
        )
        accentColor != null -> Brush.verticalGradient(listOf(accentColor, accentColor.copy(alpha = 0.85f)))
        isDestructive -> Glass3DDestructiveButtonBrush
        isSecondary -> themeColors.secondaryButtonBrush
        else -> themeColors.primaryButtonBrush
    }

    val resolvedContentColor = when {
        contentColor != null -> contentColor
        !enabled -> if (themeColors.isDark) Color(0xFF64748B) else Color(0xFF94A3B8)
        isSecondary -> themeColors.textPrimary
        isDestructive -> Color.White
        else -> Color.White
    }

    val borderBrush = when {
        !enabled -> Brush.verticalGradient(listOf(Color(0x1A475569), Color(0x0D334155)))
        accentColor != null -> Brush.verticalGradient(listOf(accentColor.copy(alpha = 0.6f), Color(0x20000000)))
        isSecondary -> Brush.verticalGradient(
            if (themeColors.isDark) listOf(Color(0x3394A3B8), Color(0x1A475569))
            else listOf(Color(0xFFE2E8F0), Color(0xFFCBD5E1))
        )
        isDestructive -> Brush.verticalGradient(listOf(Color(0x80FDA4AF), Color(0x33E11D48)))
        else -> Brush.verticalGradient(listOf(Color(0x80A5B4FC), Color(0x336366F1)))
    }

    Box(
        modifier = modifier
            .scale(scale)
            .defaultMinSize(minHeight = minHeight)
            .shadow(if (enabled) 4.dp else 0.dp, shape, ambientColor = themeColors.shadowColor, spotColor = themeColors.shadowColor)
            .clip(shape)
            .background(backgroundBrush)
            .border(1.dp, borderBrush, shape)
            .clickable(
                enabled = enabled && !isLoading,
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick
            )
            .padding(contentPadding),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = resolvedContentColor,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Processing…",
                    color = resolvedContentColor,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = resolvedContentColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = text,
                    color = resolvedContentColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.2.sp
                )
            }
        }
    }
}

// =========================================================================
// ABSTRACT ANIMATED LUMINOUS VOICE ORB (Compose Component)
// =========================================================================

enum class VoiceAssistantStatus {
    IDLE,
    LISTENING,
    PROCESSING,
    SPEAKING,
    MUTED,
    STOPPED,
    ERROR
}

enum class StatusType {
    READY,
    RUNNING,
    PAUSED,
    IDLE,
    ERROR
}

/**
 * Premium glassmorphism circle containing an abstract animated luminous pulse.
 * - No cloud icon
 * - No robot
 * - No microphone pictogram
 * - No text or letters
 * - No static logo
 * Made with smooth fluid waves, luminous core, and specular glass refraction.
 */
@Composable
fun LuminousVoiceOrb(
    status: VoiceAssistantStatus,
    modifier: Modifier = Modifier,
    size: Dp = 100.dp,
    enableAnimations: Boolean = true,
    onClick: (() -> Unit)? = null
) {
    val themeColors = AppTheme.colors

    // Animators
    val pulseAnim = remember { Animatable(1f) }
    val rotateAnim = remember { Animatable(0f) }
    val waveAnim = remember { Animatable(0f) }

    val context = androidx.compose.ui.platform.LocalContext.current
    val isReducedMotion = remember(context) {
        try {
            android.provider.Settings.Global.getFloat(
                context.contentResolver,
                android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
                1.0f
            ) == 0f
        } catch (_: Throwable) {
            false
        }
    }
    val effectiveAnimations = enableAnimations && !isReducedMotion

    LaunchedEffect(status, effectiveAnimations) {
        if (!effectiveAnimations || status == VoiceAssistantStatus.STOPPED || status == VoiceAssistantStatus.MUTED) {
            pulseAnim.snapTo(1f)
            rotateAnim.snapTo(0f)
            waveAnim.snapTo(0f)
            return@LaunchedEffect
        }

        when (status) {
            VoiceAssistantStatus.IDLE -> {
                // Subtle slow calm breathing pulse
                pulseAnim.animateTo(
                    targetValue = 1.08f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(2200, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    )
                )
            }
            VoiceAssistantStatus.LISTENING -> {
                // Responsive wave / expansion
                pulseAnim.animateTo(
                    targetValue = 1.2f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(850, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    )
                )
            }
            VoiceAssistantStatus.PROCESSING -> {
                // Rotating flowing pattern
                rotateAnim.animateTo(
                    targetValue = 360f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1600, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    )
                )
            }
            VoiceAssistantStatus.SPEAKING -> {
                // Smooth rhythmic pulse
                waveAnim.animateTo(
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(700, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    )
                )
            }
            VoiceAssistantStatus.ERROR -> {
                // Restrained warning breathing pulse
                pulseAnim.animateTo(
                    targetValue = 1.05f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1400, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    )
                )
            }
            else -> Unit
        }
    }

    val clickModifier = if (onClick != null) {
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            role = Role.Button,
            onClick = onClick
        )
    } else Modifier

    Box(
        modifier = modifier
            .size(size)
            .shadow(
                elevation = 10.dp,
                shape = CircleShape,
                ambientColor = if (themeColors.isDark) Color(0x6638BDF8) else Color(0x266366F1),
                spotColor = if (themeColors.isDark) Color(0x666366F1) else Color(0x336366F1)
            )
            .clip(CircleShape)
            .then(clickModifier),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val centerOffset = Offset(size.toPx() / 2f, size.toPx() / 2f)
            val radius = size.toPx() / 2f

            // 1. Outer Glass Sphere Background
            val glassBackdrop = Brush.radialGradient(
                colors = if (themeColors.isDark) listOf(
                    Color(0xFF1A263D),
                    Color(0xFF101929),
                    Color(0xFF090D18)
                ) else listOf(
                    Color(0xFFF1F5F9),
                    Color(0xFFE2E8F0),
                    Color(0xFFCBD5E1)
                ),
                center = centerOffset,
                radius = radius
            )
            drawCircle(brush = glassBackdrop, radius = radius, center = centerOffset)

            // 2. Animated Luminous Pulse & Waves according to state
            when (status) {
                VoiceAssistantStatus.IDLE -> {
                    // Soft cyan/indigo ambient core
                    val coreRadius = radius * 0.52f * pulseAnim.value
                    val coreBrush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFBAE6FD).copy(alpha = 0.9f),
                            Color(0xFF38BDF8).copy(alpha = 0.6f),
                            Color(0xFF6366F1).copy(alpha = 0.25f),
                            Color.Transparent
                        ),
                        center = centerOffset,
                        radius = coreRadius
                    )
                    drawCircle(brush = coreBrush, radius = coreRadius, center = centerOffset)
                }

                VoiceAssistantStatus.LISTENING -> {
                    // Concentric expanding wave rings
                    val currentScale = pulseAnim.value
                    val outerWaveRadius = radius * 0.85f * (currentScale / 1.2f)
                    drawCircle(
                        color = Color(0xFF38BDF8).copy(alpha = (1.2f - currentScale).coerceIn(0.1f, 0.45f)),
                        radius = outerWaveRadius,
                        center = centerOffset,
                        style = Stroke(width = 3.dp.toPx())
                    )

                    val midWaveRadius = radius * 0.65f * currentScale
                    drawCircle(
                        color = Color(0xFF6366F1).copy(alpha = 0.4f),
                        radius = midWaveRadius,
                        center = centerOffset,
                        style = Stroke(width = 2.dp.toPx())
                    )

                    val coreBrush = Brush.radialGradient(
                        colors = listOf(
                            Color.White,
                            Color(0xFF38BDF8),
                            Color(0xFF6366F1).copy(alpha = 0.4f),
                            Color.Transparent
                        ),
                        center = centerOffset,
                        radius = radius * 0.45f
                    )
                    drawCircle(brush = coreBrush, radius = radius * 0.45f, center = centerOffset)
                }

                VoiceAssistantStatus.PROCESSING -> {
                    // Orbiting luminous pattern / ring
                    val angleRad = Math.toRadians(rotateAnim.value.toDouble())
                    val orbitDist = radius * 0.55f
                    val satelliteX = centerOffset.x + (orbitDist * cos(angleRad)).toFloat()
                    val satelliteY = centerOffset.y + (orbitDist * sin(angleRad)).toFloat()

                    val ringBrush = Brush.sweepGradient(
                        colors = listOf(
                            Color(0xFF38BDF8),
                            Color(0xFF8B5CF6),
                            Color(0xFF6366F1),
                            Color.Transparent,
                            Color(0xFF38BDF8)
                        ),
                        center = centerOffset
                    )
                    drawCircle(
                        brush = ringBrush,
                        radius = orbitDist,
                        center = centerOffset,
                        style = Stroke(width = 2.5.dp.toPx())
                    )

                    // Orbiting satellite dot
                    drawCircle(
                        color = Color.White,
                        radius = 4.dp.toPx(),
                        center = Offset(satelliteX, satelliteY)
                    )

                    // Luminous center core
                    val coreBrush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFDDD6FE),
                            Color(0xFF8B5CF6).copy(alpha = 0.7f),
                            Color.Transparent
                        ),
                        center = centerOffset,
                        radius = radius * 0.35f
                    )
                    drawCircle(brush = coreBrush, radius = radius * 0.35f, center = centerOffset)
                }

                VoiceAssistantStatus.SPEAKING -> {
                    // Rhythmic audio waves in magenta/violet/cyan
                    val waveFraction = waveAnim.value
                    val barWidth = 3.dp.toPx()
                    val barSpacing = 7.dp.toPx()
                    val heights = listOf(0.4f, 0.7f, 0.95f, 0.65f, 0.35f)

                    val startX = centerOffset.x - (2 * barSpacing)
                    heights.forEachIndexed { index, baseHeight ->
                        val dynamicH = radius * baseHeight * (0.6f + 0.4f * sin((waveFraction + index * 0.2) * Math.PI).toFloat())
                        val x = startX + index * barSpacing
                        drawLine(
                            color = Color(0xFFA855F7),
                            start = Offset(x, centerOffset.y - dynamicH / 2f),
                            end = Offset(x, centerOffset.y + dynamicH / 2f),
                            strokeWidth = barWidth
                        )
                    }

                    val coreBrush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFF472B6).copy(alpha = 0.6f),
                            Color(0xFF8B5CF6).copy(alpha = 0.3f),
                            Color.Transparent
                        ),
                        center = centerOffset,
                        radius = radius * 0.5f
                    )
                    drawCircle(brush = coreBrush, radius = radius * 0.5f, center = centerOffset)
                }

                VoiceAssistantStatus.MUTED, VoiceAssistantStatus.STOPPED -> {
                    // Calm slate muted core
                    drawCircle(
                        color = if (themeColors.isDark) Color(0xFF475569) else Color(0xFF94A3B8),
                        radius = radius * 0.38f,
                        center = centerOffset
                    )
                }

                VoiceAssistantStatus.ERROR -> {
                    // Restrained warning state - warm amber/rose breathing core without harsh flashing
                    val coreRadius = radius * 0.44f * pulseAnim.value
                    val warningBrush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFFCA5A5).copy(alpha = 0.85f),
                            Color(0xFFF43F5E).copy(alpha = 0.5f),
                            Color(0xFFF59E0B).copy(alpha = 0.2f),
                            Color.Transparent
                        ),
                        center = centerOffset,
                        radius = coreRadius
                    )
                    drawCircle(brush = warningBrush, radius = coreRadius, center = centerOffset)
                    drawCircle(
                        color = Color(0xFFF43F5E).copy(alpha = 0.35f),
                        radius = radius * 0.62f,
                        center = centerOffset,
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                }
            }

            // 3. Specular Glass Refraction Rim (Top light highlight)
            val glassRimBrush = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = if (themeColors.isDark) 0.55f else 0.8f),
                    Color.White.copy(alpha = 0.1f),
                    Color(0x1A6366F1)
                ),
                startY = 0f,
                endY = size.toPx()
            )
            drawCircle(
                brush = glassRimBrush,
                radius = radius - 1.dp.toPx(),
                center = centerOffset,
                style = Stroke(width = 1.5.dp.toPx())
            )
        }
    }
}

/**
 * Status indicator badge with micro-animation dot.
 */
@Composable
fun GlassStatusIndicator(
    status: VoiceAssistantStatus,
    modifier: Modifier = Modifier,
    customMessage: String? = null
) {
    val themeColors = AppTheme.colors
    val (dotColor, defaultText) = when (status) {
        VoiceAssistantStatus.LISTENING -> GlassCyan to "Listening…"
        VoiceAssistantStatus.MUTED -> GlassRose to "Microphone muted"
        VoiceAssistantStatus.STOPPED -> (if (themeColors.isDark) GlassTextTertiary else Color(0xFF64748B)) to "Listening paused"
        VoiceAssistantStatus.PROCESSING -> GlassIndigo to "Processing…"
        VoiceAssistantStatus.SPEAKING -> GlassEmerald to "Speaking…"
        VoiceAssistantStatus.ERROR -> GlassRose to "Attention required"
        VoiceAssistantStatus.IDLE -> GlassCyan to "Ready"
    }

    val displayText = customMessage ?: defaultText

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(dotColor.copy(alpha = if (themeColors.isDark) 0.15f else 0.1f))
            .border(1.dp, dotColor.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .background(dotColor, CircleShape)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = displayText,
            color = themeColors.textPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun GlassStatusIndicator(
    label: String,
    status: StatusType,
    modifier: Modifier = Modifier
) {
    val (dotColor, textColor) = when (status) {
        StatusType.READY -> GlassEmerald to GlassEmerald
        StatusType.RUNNING -> GlassCyan to GlassCyan
        StatusType.PAUSED -> GlassAmber to GlassAmber
        StatusType.IDLE -> GlassTextTertiary to GlassTextSecondary
        StatusType.ERROR -> GlassRose to GlassRose
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(dotColor.copy(alpha = 0.15f))
            .border(1.dp, dotColor.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .background(dotColor, CircleShape)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

/**
 * Filter chip with rounded pill geometry and theme-aware selection.
 */
@Composable
fun GlassFilterChip(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    leadingIcon: ImageVector? = null,
    badgeCount: Int? = null,
    activeColor: Color = Color.Unspecified,
    accentColor: Color = activeColor
) {
    val themeColors = AppTheme.colors
    val effectiveColor = when {
        accentColor != Color.Unspecified -> accentColor
        activeColor != Color.Unspecified -> activeColor
        else -> themeColors.primary
    }
    val effectiveIcon = leadingIcon ?: icon
    val bg = if (selected) effectiveColor.copy(alpha = if (themeColors.isDark) 0.22f else 0.12f)
    else if (themeColors.isDark) Color(0x991E293B) else Color(0xFFF1F5F9)

    val border = if (selected) effectiveColor
    else if (themeColors.isDark) Color(0x33475569) else Color(0xFFCBD5E1)

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (effectiveIcon != null) {
            Icon(
                imageVector = effectiveIcon,
                contentDescription = null,
                tint = if (selected) effectiveColor else themeColors.textSecondary,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(
            text = label,
            color = if (selected) effectiveColor else themeColors.textSecondary,
            fontSize = 12.5.sp,
            lineHeight = 16.sp,
            textAlign = TextAlign.Center,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
        if (badgeCount != null && badgeCount > 0) {
            Spacer(modifier = Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (selected) effectiveColor else if (themeColors.isDark) Color(0xFF334155) else Color(0xFFE2E8F0))
                    .padding(horizontal = 6.dp, vertical = 1.dp)
            ) {
                Text(
                    text = "$badgeCount",
                    color = if (selected) Color.White else themeColors.textPrimary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

/**
 * Section Header with title, subtitle, and optional action button.
 */
@Composable
fun GlassSectionHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null
) {
    val themeColors = AppTheme.colors
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f, fill = false)) {
            Text(
                text = title,
                color = themeColors.textPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = themeColors.textSecondary,
                    fontSize = 12.sp
                )
            }
        }
        if (actionText != null && onActionClick != null) {
            Text(
                text = actionText,
                color = themeColors.primary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clickable(onClick = onActionClick)
                    .padding(4.dp)
            )
        }
    }
}

/**
 * Consistent empty state with clean surface, icon, title, description, and primary CTA.
 */
@Composable
fun GlassEmptyState(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null
) {
    val themeColors = AppTheme.colors
    GlassCard(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(themeColors.primary.copy(alpha = 0.12f))
                    .border(1.dp, themeColors.primary.copy(alpha = 0.35f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = themeColors.primary,
                    modifier = Modifier.size(26.dp)
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = title,
                color = themeColors.textPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                color = themeColors.textSecondary,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )
            if (actionText != null && onActionClick != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Glass3DButton(
                    text = actionText,
                    onClick = onActionClick,
                    minHeight = 42.dp
                )
            }
        }
    }
}

/**
 * Modern theme-aware text field for search and form inputs.
 */
@Composable
fun GlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    singleLine: Boolean = true,
    enabled: Boolean = true
) {
    val themeColors = AppTheme.colors

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = {
            Text(
                text = placeholder,
                color = themeColors.textTertiary,
                fontSize = 13.5.sp
            )
        },
        leadingIcon = leadingIcon?.let {
            {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    tint = themeColors.primary,
                    modifier = Modifier.size(19.dp)
                )
            }
        },
        trailingIcon = trailingIcon,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        enabled = enabled,
        singleLine = singleLine,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = themeColors.primary,
            unfocusedBorderColor = themeColors.inputBorder,
            focusedTextColor = themeColors.textPrimary,
            unfocusedTextColor = themeColors.textPrimary,
            focusedContainerColor = themeColors.inputBackground,
            unfocusedContainerColor = themeColors.inputBackground
        )
    )
}
