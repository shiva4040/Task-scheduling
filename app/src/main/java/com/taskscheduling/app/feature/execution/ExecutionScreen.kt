package com.taskscheduling.app.feature.execution

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.taskscheduling.app.domain.model.ExecutionState
import com.taskscheduling.app.ui.components.PhonePreview
import com.taskscheduling.app.ui.theme.AccentAmber
import com.taskscheduling.app.ui.theme.AccentEmerald
import com.taskscheduling.app.ui.theme.AccentIndigo
import com.taskscheduling.app.ui.theme.AccentRose
import com.taskscheduling.app.ui.theme.AccentViolet
import com.taskscheduling.app.ui.theme.BgCardDark
import com.taskscheduling.app.ui.theme.BgDeepDark
import com.taskscheduling.app.ui.theme.BorderSubtleDark
import com.taskscheduling.app.ui.theme.NeonCyan
import com.taskscheduling.app.ui.theme.TextPrimaryDark
import com.taskscheduling.app.ui.theme.TextSecondaryDark

@Composable
fun ExecutionScreen(
    viewModel: ExecutionViewModel,
    taskId: String,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val session by viewModel.executionSession.collectAsState()
    val speed by viewModel.speed.collectAsState()
    var isLogExpanded by remember { mutableStateOf(false) }

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
            .background(BgDeepDark)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // 1. Top Bar with Status Tag
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onNavigateBack) {
                    Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimaryDark)
                }
                Column {
                    Text(
                        text = session.taskTitle,
                        color = TextPrimaryDark,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "LIVE EXECUTION ENGINE",
                        color = NeonCyan,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Status Pill
            val statusColor = when (session.state) {
                ExecutionState.RUNNING -> AccentEmerald
                ExecutionState.PAUSED -> AccentAmber
                ExecutionState.COMPLETED -> NeonCyan
                ExecutionState.FAILED -> AccentRose
                else -> Color(0xFF64748B)
            }
            Box(
                modifier = Modifier
                    .background(statusColor.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                    .border(1.dp, statusColor, RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = session.state.name,
                    color = statusColor,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 2. Step Progress & Current Action Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(BgCardDark, RoundedCornerShape(16.dp))
                .border(1.dp, BorderSubtleDark, RoundedCornerShape(16.dp))
                .padding(14.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "STEP ${session.currentStepIndex + 1} OF ${session.totalSteps}",
                        color = NeonCyan,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${(session.progress * 100).toInt()}%",
                        color = TextPrimaryDark,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }

                LinearProgressIndicator(
                    progress = { session.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp),
                    color = NeonCyan,
                    trackColor = Color(0x33FFFFFF)
                )

                Text(
                    text = session.currentStep?.label ?: "Preparing automation steps...",
                    color = TextPrimaryDark,
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
                        color = Color(0xFF64748B),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 3. Central Simulated Phone Preview with Live Execution Overlay
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(390.dp),
            contentAlignment = Alignment.Center
        ) {
            PhonePreview(
                currentScreen = session.currentSimulatedScreen,
                activeCoordinate = session.activeCoordinate,
                activeActionType = session.activeActionType
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 4. Playback Controls Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(BgCardDark, RoundedCornerShape(16.dp))
                .border(1.dp, BorderSubtleDark, RoundedCornerShape(16.dp))
                .padding(10.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Pause / Resume Button
            if (session.state == ExecutionState.RUNNING) {
                IconButton(onClick = { viewModel.pause() }, modifier = Modifier.size(44.dp)) {
                    Icon(imageVector = Icons.Default.Pause, contentDescription = "Pause", tint = AccentAmber)
                }
            } else {
                IconButton(onClick = { viewModel.resume() }, modifier = Modifier.size(44.dp)) {
                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = "Resume", tint = AccentEmerald)
                }
            }

            // Step Next
            IconButton(onClick = { viewModel.stepNext() }, modifier = Modifier.size(44.dp)) {
                Icon(imageVector = Icons.Default.SkipNext, contentDescription = "Step Next", tint = NeonCyan)
            }

            // Restart
            IconButton(onClick = { viewModel.restart() }, modifier = Modifier.size(44.dp)) {
                Icon(imageVector = Icons.Default.Refresh, contentDescription = "Restart", tint = AccentIndigo)
            }

            // Stop
            IconButton(onClick = { viewModel.stop() }, modifier = Modifier.size(44.dp)) {
                Icon(imageVector = Icons.Default.Stop, contentDescription = "Stop", tint = AccentRose)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Speed Selector
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Speed: ", color = TextSecondaryDark, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            listOf(0.5f, 1.0f, 2.0f).forEach { s ->
                val isSel = speed == s
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .background(if (isSel) NeonCyan.copy(alpha = 0.2f) else BgCardDark, RoundedCornerShape(8.dp))
                        .border(1.dp, if (isSel) NeonCyan else BorderSubtleDark, RoundedCornerShape(8.dp))
                        .clickable { viewModel.setSpeed(s) }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(text = "${s}x", color = if (isSel) NeonCyan else TextSecondaryDark, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 5. Collapsible Real-Time Execution Log
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(BgCardDark, RoundedCornerShape(16.dp))
                .border(1.dp, BorderSubtleDark, RoundedCornerShape(16.dp))
                .padding(14.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isLogExpanded = !isLogExpanded },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "EXECUTION LOG (${session.logs.size} EVENTS)",
                        color = TextSecondaryDark,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    Icon(
                        imageVector = if (isLogExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = "Expand Log",
                        tint = TextSecondaryDark
                    )
                }

                AnimatedVisibility(visible = isLogExpanded || session.state == ExecutionState.COMPLETED) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        session.logs.takeLast(8).forEach { log ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${log.timestamp} #${log.stepNumber} [${log.actionType.label.uppercase()}]",
                                    color = NeonCyan,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = log.status,
                                    color = if (log.status == "SUCCESS" || log.status == "COMPLETED") AccentEmerald else AccentAmber,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = log.details,
                                color = TextPrimaryDark,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
