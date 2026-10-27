package com.taskscheduling.app.feature.execution

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.taskscheduling.app.accessibility.AccessibilityUtils
import com.taskscheduling.app.accessibility.TaskSchedulingAccessibilityService
import com.taskscheduling.app.domain.model.ExecutionState
import com.taskscheduling.app.ui.theme.AccentAmber
import com.taskscheduling.app.ui.theme.AccentEmerald
import com.taskscheduling.app.ui.theme.AccentRose
import com.taskscheduling.app.ui.theme.AppTheme
import com.taskscheduling.app.ui.theme.Glass3DButton
import com.taskscheduling.app.ui.theme.GlassCard
import com.taskscheduling.app.ui.theme.GlassCyan
import com.taskscheduling.app.ui.theme.GlassFilterChip
import com.taskscheduling.app.ui.theme.GlassIndigo
import com.taskscheduling.app.ui.theme.GlassStatusIndicator
import com.taskscheduling.app.ui.theme.StatusType

@Composable
fun ExecutionScreen(
    viewModel: ExecutionViewModel,
    taskId: String,
    onNavigateBack: () -> Unit,
    onNavigateToBuilder: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val session by viewModel.executionSession.collectAsState()
    val speed by viewModel.speed.collectAsState()
    val context = LocalContext.current
    val colors = AppTheme.colors
    var isLogExpanded by remember { mutableStateOf(false) }

    val deviceProfile = remember { AccessibilityUtils.getDeviceProfile(context) }
    val isServiceConnected = TaskSchedulingAccessibilityService.instance != null

    LaunchedEffect(taskId) {
        viewModel.loadAndExecute(taskId)
    }

    LaunchedEffect(session.state) {
        if (session.state == ExecutionState.COMPLETED) {
            viewModel.recordCompletedActivity()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Header with Task Title and Status Indicator
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = colors.textPrimary
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Column {
                    Text(
                        text = session.taskTitle.ifBlank { "Task Execution" },
                        color = colors.textPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "REAL DEVICE AUTOMATION",
                        color = GlassIndigo,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Real Execution Status Indicator
            val (statusType, statusLabel) = when (session.state) {
                ExecutionState.RUNNING -> StatusType.RUNNING to "RUNNING"
                ExecutionState.PAUSED -> StatusType.PAUSED to "PAUSED"
                ExecutionState.COMPLETED -> StatusType.READY to "COMPLETED"
                ExecutionState.FAILED -> StatusType.ERROR to "FAILED"
                ExecutionState.STOPPED -> StatusType.ERROR to "STOPPED"
                else -> StatusType.IDLE to session.state.name
            }
            GlassStatusIndicator(label = statusLabel, status = statusType)
        }

        // 2. Accessibility Service and Device Hardware Status
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (isServiceConnected) AccentEmerald else AccentRose)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isServiceConnected) "Accessibility Service Active (Physical Gestures)" else "Accessibility Service Disconnected",
                            color = if (isServiceConnected) AccentEmerald else AccentRose,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${deviceProfile.toDisplayString()} • Native Coordinate Space",
                        color = colors.textSecondary,
                        fontSize = 10.5.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                if (!isServiceConnected) {
                    TextButton(
                        onClick = {
                            try {
                                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                context.startActivity(intent)
                            } catch (_: Exception) {}
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "Enable",
                            color = GlassCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // 3. Step Progress Header Card (Derives real count, never STEP 1 OF 0)
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            val total = session.totalSteps
            val currentNumber = if (total == 0) 0 else (session.currentStepIndex + 1).coerceAtMost(total)

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (total == 0) "NO STEPS CONFIGURED" else "STEP $currentNumber OF $total",
                        color = if (total == 0) AccentAmber else GlassIndigo,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (total == 0) "0%" else "${(session.progress * 100).toInt()}%",
                        color = colors.textPrimary,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }

                LinearProgressIndicator(
                    progress = { if (total == 0) 0f else session.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = if (total == 0) colors.borderSubtle else GlassIndigo,
                    trackColor = colors.surfaceSecondary
                )

                // Current Step Label or Empty State Banner
                if (total == 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.surfaceSecondary)
                            .border(1.dp, colors.borderSubtle, RoundedCornerShape(10.dp))
                            .padding(12.dp)
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = AccentAmber,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Workflow has no automation steps",
                                    color = colors.textPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "This workflow does not contain any physical touch or swipe steps. Add actions in the Workflow Builder before executing.",
                                color = colors.textSecondary,
                                fontSize = 11.5.sp,
                                lineHeight = 16.sp
                            )
                            if (onNavigateToBuilder != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Glass3DButton(
                                    text = "Edit Workflow in Builder",
                                    icon = Icons.Default.Build,
                                    onClick = { onNavigateToBuilder(taskId) },
                                    accentColor = GlassIndigo
                                )
                            }
                        }
                    }
                } else {
                    Text(
                        text = session.currentStep?.label ?: if (session.state == ExecutionState.COMPLETED) "All physical steps executed successfully" else "Preparing automation step...",
                        color = colors.textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    session.activeCoordinate?.let { coord ->
                        Text(
                            text = if (coord.endX != null) {
                                "SWIPE: (${coord.x}, ${coord.y}) → (${coord.endX}, ${coord.endY})"
                            } else {
                                "COORDINATE: (${coord.x}, ${coord.y}) • ${session.activeActionType?.label?.uppercase() ?: "TAP"}"
                            },
                            color = GlassCyan,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // 4. Live Physical Screen Gesture Target Card
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ACTIVE GESTURE TARGET",
                        color = GlassIndigo,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )

                    session.activeCoordinate?.let { coord ->
                        TextButton(
                            onClick = {
                                TaskSchedulingAccessibilityService.instance?.showVisualTouchDot(
                                    coord.x,
                                    coord.y,
                                    "TEST (${coord.x}, ${coord.y})"
                                )
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Visibility,
                                contentDescription = null,
                                tint = GlassCyan,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Flash Touch Dot", color = GlassCyan, fontSize = 11.sp)
                        }
                    }
                }

                // Coordinate Target Display
                session.activeCoordinate?.let { coord ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.surfaceSecondary)
                            .border(1.dp, colors.borderSubtle, RoundedCornerShape(12.dp))
                            .padding(14.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Action: ${session.activeActionType?.label ?: "Tap"}",
                                    color = colors.textPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = if (coord.endX != null) "Gesture: Swipe" else "Gesture: Direct Tap",
                                    color = GlassIndigo,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Text(
                                text = if (coord.endX != null) {
                                    "Physical Path: (${coord.x}px, ${coord.y}px) ➔ (${coord.endX}px, ${coord.endY}px)"
                                } else {
                                    "Screen Coordinates: X = ${coord.x} px,  Y = ${coord.y} px"
                                },
                                color = GlassCyan,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold
                            )

                            Text(
                                text = "Dispatches hardware-level touch via AccessibilityService.dispatchGesture()",
                                color = colors.textSecondary,
                                fontSize = 10.5.sp
                            )
                        }
                    }
                } ?: run {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.surfaceSecondary)
                            .padding(14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (session.state == ExecutionState.COMPLETED) {
                                "✓ Workflow Execution Complete"
                            } else if (session.totalSteps == 0) {
                                "No active touch target (0 steps)"
                            } else {
                                "Ready to dispatch physical gestures"
                            },
                            color = if (session.state == ExecutionState.COMPLETED) AccentEmerald else colors.textSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Error message if execution failed
                session.errorMessage?.let { err ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(AccentRose.copy(alpha = if (colors.isDark) 0.18f else 0.12f))
                            .border(1.dp, AccentRose.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                            .padding(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = AccentRose,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = err, color = AccentRose, fontSize = 11.5.sp)
                        }
                    }
                }
            }
        }

        // 5. Playback Controls Bar (Responsive multi-row layout, never crowded)
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            val total = session.totalSteps
            val isExecutable = total > 0

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Primary Controls Row (Execution & Next Step)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (session.state == ExecutionState.RUNNING) {
                        Glass3DButton(
                            text = "Pause Execution",
                            icon = Icons.Default.Pause,
                            onClick = { if (isExecutable) viewModel.pause() },
                            accentColor = AccentAmber,
                            modifier = Modifier.weight(1.3f)
                        )
                    } else {
                        Glass3DButton(
                            text = if (session.state == ExecutionState.COMPLETED) "Re-run Workflow" else "Resume Execution",
                            icon = Icons.Default.PlayArrow,
                            onClick = { if (isExecutable) viewModel.resume() },
                            accentColor = if (isExecutable) AccentEmerald else colors.textTertiary,
                            modifier = Modifier.weight(1.3f)
                        )
                    }

                    Glass3DButton(
                        text = "Next Step",
                        icon = Icons.Default.SkipNext,
                        onClick = { if (isExecutable) viewModel.stepNext() },
                        accentColor = if (isExecutable) GlassIndigo else colors.textTertiary,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Secondary Controls Row (Restart & Stop)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Glass3DButton(
                        text = "Restart",
                        icon = Icons.Default.Refresh,
                        onClick = { if (isExecutable) viewModel.restart() },
                        accentColor = if (isExecutable) colors.primary else colors.textTertiary,
                        modifier = Modifier.weight(1f)
                    )

                    Glass3DButton(
                        text = "Stop",
                        icon = Icons.Default.Stop,
                        onClick = { viewModel.stop() },
                        accentColor = AccentRose,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Speed Selector Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Playback Speed: ",
                        color = colors.textSecondary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    listOf(0.5f, 1.0f, 2.0f).forEach { s ->
                        GlassFilterChip(
                            selected = speed == s,
                            onClick = { viewModel.setSpeed(s) },
                            label = "${s}x",
                            accentColor = GlassIndigo
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                }
            }
        }

        // 6. Collapsible Real-Time Execution Log
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isLogExpanded = !isLogExpanded }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "EXECUTION LOG",
                        color = GlassIndigo,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "(${session.logs.size} events)",
                        color = colors.textSecondary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Icon(
                    imageVector = if (isLogExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isLogExpanded) "Collapse" else "Expand",
                    tint = colors.textSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }

            AnimatedVisibility(visible = isLogExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (session.logs.isNotEmpty()) {
                        session.logs.forEach { log ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(colors.surfaceSecondary)
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(18.dp)
                                            .clip(CircleShape)
                                            .background(colors.primary.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "${log.stepNumber}",
                                            color = colors.primary,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = log.details,
                                        color = colors.textPrimary,
                                        fontSize = 11.5.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                Text(
                                    text = log.timestamp,
                                    color = colors.textTertiary,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    } else {
                        Text(
                            text = "No log events recorded yet.",
                            color = colors.textSecondary,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
