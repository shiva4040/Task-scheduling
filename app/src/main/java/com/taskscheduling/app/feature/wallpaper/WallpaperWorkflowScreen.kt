package com.taskscheduling.app.feature.wallpaper

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.taskscheduling.app.domain.model.ActionType
import com.taskscheduling.app.domain.model.CoordinateAction
import com.taskscheduling.app.domain.model.ExecutionState
import com.taskscheduling.app.domain.model.WorkflowStatus
import com.taskscheduling.app.domain.model.WorkflowStep
import com.taskscheduling.app.recording.RecordingManager
import com.taskscheduling.app.ui.theme.AccentAmber
import com.taskscheduling.app.ui.theme.AccentEmerald
import com.taskscheduling.app.ui.theme.AccentIndigo
import com.taskscheduling.app.ui.theme.AccentRose
import com.taskscheduling.app.ui.theme.AccentViolet
import com.taskscheduling.app.ui.theme.BgCardDark
import com.taskscheduling.app.ui.theme.BgDeepDark
import com.taskscheduling.app.ui.theme.BorderHighlightDark
import com.taskscheduling.app.ui.theme.BorderSubtleDark
import com.taskscheduling.app.ui.theme.DeepCyan
import com.taskscheduling.app.ui.theme.NeonCyan
import com.taskscheduling.app.ui.theme.TextPrimaryDark
import com.taskscheduling.app.ui.theme.TextSecondaryDark

@Composable
fun WallpaperWorkflowScreen(
    viewModel: WallpaperWorkflowViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val task by viewModel.task.collectAsState()
    val workflow by viewModel.workflow.collectAsState()
    val currentDevice by viewModel.currentDeviceProfile.collectAsState()
    val isAccessibilityConnected by viewModel.isAccessibilityConnected.collectAsState()
    val executionSession by viewModel.executionSession.collectAsState()
    val isRecording by viewModel.isRecording.collectAsState()
    val recordedSteps by viewModel.recordedSteps.collectAsState()
    val history by viewModel.history.collectAsState()
    val isReviewMode by viewModel.isReviewMode.collectAsState()
    val unfinishedSessionPrompt by viewModel.unfinishedSessionPrompt.collectAsState()

    // Dialog state collectors
    val showStartRecordingConfirmation by viewModel.showStartRecordingConfirmation.collectAsState()
    val showRunConfirmation by viewModel.showRunConfirmation.collectAsState()
    val showStopConfirmation by viewModel.showStopConfirmation.collectAsState()
    val testStepConfirmation by viewModel.testStepConfirmation.collectAsState()
    val testStepResult by viewModel.testStepResult.collectAsState()
    val editingStep by viewModel.editingStep.collectAsState()
    val validationError by viewModel.validationError.collectAsState()
    val deviceMismatchWarning by viewModel.deviceMismatchWarning.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.refreshServiceStatus()
    }

    val isCalibrated = workflow?.status == WorkflowStatus.CALIBRATED && (workflow?.steps?.isNotEmpty() == true)
    val isExecuting = executionSession.state == ExecutionState.RUNNING || executionSession.state == ExecutionState.PAUSED

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(BgDeepDark)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Top Bar
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier
                            .size(38.dp)
                            .background(BgCardDark, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimaryDark
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "CHANGE WALLPAPER",
                            color = TextPrimaryDark,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "Physical Coordinate Automation",
                            color = TextSecondaryDark,
                            fontSize = 12.sp
                        )
                    }
                }

                // Calibration Status Badge
                val statusText = if (isCalibrated) "CALIBRATED" else "NOT CALIBRATED"
                val statusColor = if (isCalibrated) AccentEmerald else AccentAmber
                Box(
                    modifier = Modifier
                        .background(statusColor.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                        .border(1.dp, statusColor.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (isCalibrated) "✓ " else "○ ",
                            color = statusColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = statusText,
                            color = statusColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // 2. Accessibility Connection Status Banner
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = BgCardDark),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isAccessibilityConnected) AccentEmerald.copy(alpha = 0.4f) else AccentRose.copy(alpha = 0.4f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(
                                    if (isAccessibilityConnected) AccentEmerald else AccentRose,
                                    CircleShape
                                )
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = if (isAccessibilityConnected) "● Accessibility Connected" else "○ Accessibility Not Connected",
                                color = if (isAccessibilityConnected) AccentEmerald else AccentRose,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isAccessibilityConnected)
                                    "Ready to perform real coordinate gestures"
                                else
                                    "Required to execute real touch actions on device",
                                color = TextSecondaryDark,
                                fontSize = 11.sp
                            )
                        }
                    }

                    if (!isAccessibilityConnected) {
                        Button(
                            onClick = { viewModel.openAccessibilitySettings() },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentIndigo),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text("SETTINGS", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // 3. Hardware Device Profile Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = BgCardDark),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtleDark)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.PhoneAndroid,
                            contentDescription = "Device",
                            tint = NeonCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "TARGET DEVICE PROFILE",
                            color = TextSecondaryDark,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${currentDevice.manufacturer} ${currentDevice.model}".trim(),
                        color = TextPrimaryDark,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Resolution: ${currentDevice.screenWidth} × ${currentDevice.screenHeight} px",
                            color = NeonCyan,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "Android ${currentDevice.androidVersion} (API ${currentDevice.apiLevel})",
                            color = TextSecondaryDark,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        // 4. Execution State View (When Running, Paused, or Result)
        if (isExecuting || executionSession.state == ExecutionState.COMPLETED || executionSession.state == ExecutionState.FAILED) {
            item {
                ExecutionProgressCard(
                    session = executionSession,
                    onPause = { viewModel.pauseExecution() },
                    onResume = { viewModel.resumeExecution() },
                    onStop = { viewModel.onRequestStop() },
                    onRunAgain = { viewModel.onRequestRun() }
                )
            }
        }

        // 5. Recording Active Banner
        if (isRecording) {
            item {
                RecordingActiveCard(
                    stepCount = recordedSteps.size,
                    onFinish = { viewModel.finishRecordingAndReview() },
                    onCancel = { viewModel.cancelRecordingSession() }
                )
            }
        }

        // 6. Review Mode (After Recording or Editing) - ONE SINGLE WORKFLOW CONTAINER
        if (isReviewMode && !isRecording) {
            item {
                SingleWorkflowReviewContainer(
                    workflowName = workflow?.name ?: "Change Wallpaper",
                    steps = recordedSteps,
                    onSave = { viewModel.saveWorkflow() },
                    onRecordAgain = { viewModel.onRequestStartRecording() },
                    onAddWait = { viewModel.addWaitStep(1000L) },
                    onTestStep = { viewModel.requestTestStep(it) },
                    onEditStep = { viewModel.startEditingStep(it) },
                    onDeleteStep = { viewModel.deleteStep(it) },
                    onMoveStepUp = { viewModel.moveStepUp(it) },
                    onMoveStepDown = { viewModel.moveStepDown(it) }
                )
            }
        }

        // 7. Not Calibrated Setup Card (When not calibrated and not reviewing)
        if (!isCalibrated && !isReviewMode && !isRecording && !isExecuting) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = BgCardDark),
                    shape = RoundedCornerShape(20.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderHighlightDark.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = "First-time setup required",
                            color = AccentAmber,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "This task needs to be calibrated for your device.\n\n" +
                                    "You will perform the wallpaper-changing process once.\n" +
                                    "Task Scheduling will record the physical coordinates of your actions.\n\n" +
                                    "Nothing will be automated during recording.",
                            color = TextPrimaryDark,
                            fontSize = 13.sp,
                            lineHeight = 19.sp
                        )
                        Spacer(modifier = Modifier.height(20.dp))

                        Button(
                            onClick = { viewModel.onRequestStartRecording() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.TouchApp,
                                contentDescription = "Record",
                                tint = Color.Black,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "RECORD COORDINATES",
                                color = Color.Black,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }

        // 8. Calibrated Ready-To-Run View - ONE SINGLE WORKFLOW CONTAINER
        if (isCalibrated && !isReviewMode && !isRecording && !isExecuting) {
            item {
                SingleCalibratedWorkflowContainer(
                    workflow = workflow,
                    isAccessibilityConnected = isAccessibilityConnected,
                    onRun = { viewModel.onRequestRun() },
                    onEditWorkflow = {
                        workflow?.let { wf ->
                            RecordingManager.updateSteps(wf.steps)
                            viewModel.isReviewMode.value = true
                        }
                    },
                    onRecordAgain = { viewModel.onRequestStartRecording() },
                    onTestStep = { viewModel.requestTestStep(it) }
                )
            }

            // Execution History List
            if (history.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "EXECUTION HISTORY",
                        color = TextSecondaryDark,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                itemsIndexed(history.take(5)) { _, record ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = BgCardDark),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtleDark)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = record.formattedTime,
                                    color = TextPrimaryDark,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                val statusColor = when (record.status) {
                                    "COMPLETED" -> AccentEmerald
                                    "FAILED" -> AccentRose
                                    else -> AccentAmber
                                }
                                val icon = if (record.status == "COMPLETED") "✓ " else "✕ "
                                Text(
                                    text = "$icon${record.status}",
                                    color = statusColor,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = "${record.completedSteps} / ${record.totalSteps} steps",
                                color = TextSecondaryDark,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
    }

    // -------------------------------------------------------------
    // MODALS AND CONFIRMATION DIALOGS
    // -------------------------------------------------------------

    // 0. Unfinished Recording Detection Dialog
    unfinishedSessionPrompt?.let { session ->
        AlertDialog(
            onDismissRequest = { /* Require explicit choice */ },
            containerColor = BgCardDark,
            title = {
                Text(
                    text = "Unfinished Recording",
                    color = NeonCyan,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            },
            text = {
                Column {
                    Text(
                        text = "${session.steps.size} action${if (session.steps.size != 1) "s were" else " was"} recorded.",
                        color = TextPrimaryDark,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "An unfinished recording session was found from earlier. Would you like to restore all previous actions and continue recording, or discard it?",
                        color = TextSecondaryDark,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.continueUnfinishedRecording() },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentEmerald),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Continue Recording", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.discardUnfinishedRecording() }) {
                    Text("Discard Recording", color = AccentRose)
                }
            }
        )
    }

    // 1. Record Confirmation Dialog
    if (showStartRecordingConfirmation) {
        AlertDialog(
            onDismissRequest = { viewModel.cancelStartRecording() },
            containerColor = BgCardDark,
            title = {
                Text("RECORD CHANGE WALLPAPER", color = TextPrimaryDark, fontWeight = FontWeight.Bold)
            },
            text = {
                Column {
                    Text("Before recording:", color = TextSecondaryDark, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("• Make sure the device is unlocked.", color = TextPrimaryDark, fontSize = 13.sp)
                    Text("• Perform the wallpaper-changing process normally.", color = TextPrimaryDark, fontSize = 13.sp)
                    Text("• Task Scheduling will record your touch coordinates.", color = TextPrimaryDark, fontSize = 13.sp)
                    Text("• No automation will be executed during recording.", color = AccentAmber, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.confirmStartRecording() },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan)
                ) {
                    Text("START RECORDING", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelStartRecording() }) {
                    Text("CANCEL", color = TextSecondaryDark)
                }
            }
        )
    }

    // 2. Run Confirmation Dialog
    if (showRunConfirmation) {
        AlertDialog(
            onDismissRequest = { viewModel.cancelRunConfirmation() },
            containerColor = BgCardDark,
            title = {
                Text("READY TO RUN", color = TextPrimaryDark, fontWeight = FontWeight.Bold)
            },
            text = {
                Column {
                    Text("Task: Change Wallpaper", color = NeonCyan, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("Steps: ${workflow?.steps?.size ?: 0}", color = TextSecondaryDark, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        "The saved coordinate actions will be performed on this device.\n\n" +
                                "Make sure the device is:\n" +
                                "• unlocked\n" +
                                "• displaying the expected screen\n" +
                                "• ready for automation",
                        color = TextPrimaryDark,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.confirmStartExecution() },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentEmerald)
                ) {
                    Text("START", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelRunConfirmation() }) {
                    Text("CANCEL", color = TextSecondaryDark)
                }
            }
        )
    }

    // 3. Stop Confirmation Dialog
    if (showStopConfirmation) {
        AlertDialog(
            onDismissRequest = { viewModel.cancelStopConfirmation() },
            containerColor = BgCardDark,
            title = { Text("Stop automation?", color = TextPrimaryDark, fontWeight = FontWeight.Bold) },
            text = {
                Text("The remaining steps will not be executed.", color = TextSecondaryDark, fontSize = 14.sp)
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.confirmStopExecution() },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentRose)
                ) {
                    Text("STOP", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelStopConfirmation() }) {
                    Text("CANCEL", color = TextSecondaryDark)
                }
            }
        )
    }

    // 4. Test Step Confirmation Dialog
    testStepConfirmation?.let { step ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissTestStepConfirmation() },
            containerColor = BgCardDark,
            title = { Text("TEST STEP", color = TextPrimaryDark, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("This will perform a real action at:", color = TextSecondaryDark, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Action: ${step.type.label}", color = NeonCyan, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("X: ${step.startX}", color = TextPrimaryDark, fontFamily = FontFamily.Monospace, fontSize = 14.sp)
                    Text("Y: ${step.startY}", color = TextPrimaryDark, fontFamily = FontFamily.Monospace, fontSize = 14.sp)
                    if (step.type == ActionType.SWIPE) {
                        Text("End X: ${step.endX ?: 0}", color = TextPrimaryDark, fontFamily = FontFamily.Monospace, fontSize = 14.sp)
                        Text("End Y: ${step.endY ?: 0}", color = TextPrimaryDark, fontFamily = FontFamily.Monospace, fontSize = 14.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.confirmTestStep(step) },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan)
                ) {
                    Text("TEST", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissTestStepConfirmation() }) {
                    Text("CANCEL", color = TextSecondaryDark)
                }
            }
        )
    }

    // 5. Test Step Result Dialog
    testStepResult?.let { res ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissTestStepResult() },
            containerColor = BgCardDark,
            title = {
                Text(
                    text = if (res.status == com.taskscheduling.app.automation.StepExecutionStatus.SUCCESS) "✓ TEST SUCCESS" else "✕ TEST FAILED",
                    color = if (res.status == com.taskscheduling.app.automation.StepExecutionStatus.SUCCESS) AccentEmerald else AccentRose,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(res.message, color = TextPrimaryDark, fontSize = 14.sp)
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.dismissTestStepResult() },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentIndigo)
                ) {
                    Text("OK", color = Color.White)
                }
            }
        )
    }

    // 6. Validation Error Dialog
    validationError?.let { err ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissValidationError() },
            containerColor = BgCardDark,
            title = { Text("VALIDATION ERROR", color = AccentRose, fontWeight = FontWeight.Bold) },
            text = { Text(err, color = TextPrimaryDark, fontSize = 14.sp) },
            confirmButton = {
                Button(
                    onClick = { viewModel.dismissValidationError() },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentRose)
                ) {
                    Text("OK", color = Color.White)
                }
            }
        )
    }

    // 7. Device Mismatch Warning Dialog
    deviceMismatchWarning?.let { warning ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissDeviceMismatchWarning() },
            containerColor = BgCardDark,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Warning, contentDescription = "Warning", tint = AccentAmber)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("DEVICE MISMATCH", color = AccentAmber, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column {
                    Text(
                        text = "The recorded coordinates do not match your current device configuration:\n",
                        color = TextSecondaryDark,
                        fontSize = 13.sp
                    )
                    Text(
                        text = warning,
                        color = TextPrimaryDark,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Running on mismatched resolution or orientation could cause inaccurate touches. Would you like to re-calibrate now?",
                        color = TextSecondaryDark,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.recalibrateFromMismatch() },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan)
                ) {
                    Text("RE-CALIBRATE", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissDeviceMismatchWarning() }) {
                    Text("CANCEL", color = TextSecondaryDark)
                }
            }
        )
    }

    // 8. Step Editor Dialog
    editingStep?.let { step ->
        StepEditDialog(
            step = step,
            onSave = { updated -> viewModel.saveEditedStep(updated) },
            onDismiss = { viewModel.dismissEditingStep() }
        )
    }
}

@Composable
private fun RecordingActiveCard(
    stepCount: Int,
    onFinish: () -> Unit,
    onCancel: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = BgCardDark),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, AccentRose.copy(alpha = 0.6f))
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .alpha(alpha)
                            .background(AccentRose, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "RECORDING",
                        color = AccentRose,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Text(
                    text = "$stepCount steps recorded",
                    color = NeonCyan,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Perform the workflow normally.\n" +
                        "Touch coordinates are captured in physical screen pixels.\n" +
                        "Use the floating tool on screen to target or finish.",
                color = TextSecondaryDark,
                fontSize = 12.sp,
                lineHeight = 17.sp
            )
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onFinish,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentEmerald),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("FINISH & REVIEW", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(0.6f),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtleDark)
                ) {
                    Text("CANCEL", color = TextSecondaryDark, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun SingleWorkflowReviewContainer(
    workflowName: String,
    steps: List<WorkflowStep>,
    onSave: () -> Unit,
    onRecordAgain: () -> Unit,
    onAddWait: () -> Unit,
    onTestStep: (WorkflowStep) -> Unit,
    onEditStep: (WorkflowStep) -> Unit,
    onDeleteStep: (String) -> Unit,
    onMoveStepUp: (Int) -> Unit,
    onMoveStepDown: (Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = BgCardDark),
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderHighlightDark.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // 1. Workflow Header: Title & Action Count
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = workflowName,
                        color = TextPrimaryDark,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    val actionLabel = if (steps.size == 1) "1 action" else "${steps.size} actions"
                    Text(
                        text = actionLabel,
                        color = NeonCyan,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Box(
                    modifier = Modifier
                        .background(AccentAmber.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "REVIEW",
                        color = AccentAmber,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            // Divider
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(BorderSubtleDark)
            )
            Spacer(modifier = Modifier.height(10.dp))

            // 2. Compact Ordered Timeline / List INSIDE the single container
            if (steps.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No actions recorded yet", color = TextSecondaryDark, fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = onRecordAgain,
                            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan)
                        ) {
                            Text("START RECORDING", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    steps.forEachIndexed { index, step ->
                        CompactStepRow(
                            step = step,
                            index = index,
                            totalSteps = steps.size,
                            showActionsMenu = true,
                            onTest = { onTestStep(step) },
                            onEdit = { onEditStep(step) },
                            onDelete = { onDeleteStep(step.id) },
                            onMoveUp = { onMoveStepUp(index) },
                            onMoveDown = { onMoveStepDown(index) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            // Divider
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(BorderSubtleDark)
            )
            Spacer(modifier = Modifier.height(14.dp))

            // 3. Bottom controls: + WAIT and RECORD AGAIN
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onAddWait,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0x26F59E0B)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text("+ WAIT", color = AccentAmber, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onRecordAgain,
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtleDark),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("RECORD AGAIN", color = NeonCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 4. Prominent [SAVE WORKFLOW]
            Button(
                onClick = onSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentEmerald),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(imageVector = Icons.Default.Save, contentDescription = "Save", tint = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "SAVE WORKFLOW",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
private fun SingleCalibratedWorkflowContainer(
    workflow: com.taskscheduling.app.domain.model.Workflow?,
    isAccessibilityConnected: Boolean,
    onRun: () -> Unit,
    onEditWorkflow: () -> Unit,
    onRecordAgain: () -> Unit,
    onTestStep: (WorkflowStep) -> Unit
) {
    val steps = workflow?.steps ?: emptyList()
    val actionLabel = if (steps.size == 1) "1 action" else "${steps.size} actions"

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = BgCardDark),
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, AccentEmerald.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = workflow?.name ?: "Change Wallpaper",
                        color = TextPrimaryDark,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = actionLabel,
                        color = NeonCyan,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Box(
                    modifier = Modifier
                        .background(AccentEmerald.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "SAVED",
                        color = AccentEmerald,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Calibrated on ${workflow?.deviceProfile?.toDisplayString() ?: "Device"}",
                color = TextSecondaryDark,
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.height(14.dp))
            // Divider
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(BorderSubtleDark)
            )
            Spacer(modifier = Modifier.height(10.dp))

            // Compact list inside the card
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                steps.forEachIndexed { index, step ->
                    CompactStepRow(
                        step = step,
                        index = index,
                        totalSteps = steps.size,
                        showActionsMenu = true,
                        onTest = { onTestStep(step) },
                        onEdit = null,
                        onDelete = null,
                        onMoveUp = null,
                        onMoveDown = null
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            // Divider
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(BorderSubtleDark)
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Prominent RUN button
            Button(
                onClick = onRun,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isAccessibilityConnected) AccentEmerald else Color.DarkGray
                ),
                enabled = isAccessibilityConnected,
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(imageVector = Icons.Default.PlayArrow, contentDescription = "Run", tint = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "RUN",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onEditWorkflow,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtleDark)
                ) {
                    Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit", tint = TextPrimaryDark, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("EDIT WORKFLOW", color = TextPrimaryDark, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onRecordAgain,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtleDark)
                ) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = "Record Again", tint = NeonCyan, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("RECORD AGAIN", color = NeonCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun CompactStepRow(
    step: WorkflowStep,
    index: Int,
    totalSteps: Int,
    showActionsMenu: Boolean = true,
    onTest: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    val circledSymbols = listOf("①", "②", "③", "④", "⑤", "⑥", "⑦", "⑧", "⑨", "⑩", "⑪", "⑫", "⑬", "⑭", "⑮", "⑯", "⑰", "⑱", "⑲", "⑳")
    val indexLabel = if (index in circledSymbols.indices) circledSymbols[index] else "${index + 1}."

    val typeLabel = when (step.type) {
        ActionType.TAP -> "TAP"
        ActionType.LONG_PRESS -> "LONG PRESS"
        ActionType.SWIPE -> "SWIPE"
        ActionType.WAIT -> "WAIT"
        ActionType.BACK -> "BACK"
        else -> step.type.name
    }

    val coordText = when (step.type) {
        ActionType.TAP, ActionType.LONG_PRESS -> "${step.startX}, ${step.startY}"
        ActionType.SWIPE -> "(${step.startX}, ${step.startY}) → (${step.endX ?: 0}, ${step.endY ?: 0})"
        ActionType.WAIT -> "${step.duration} ms"
        ActionType.BACK -> ""
        else -> "${step.startX}, ${step.startY}"
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Step number e.g. ①
        Text(
            text = indexLabel,
            color = NeonCyan,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(26.dp)
        )

        // Action Type e.g. TAP
        Text(
            text = typeLabel,
            color = TextPrimaryDark,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(92.dp)
        )

        // Coordinates or details e.g. 299, 937
        Text(
            text = coordText,
            color = TextSecondaryDark,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f)
        )

        // Small overflow menu button
        if (showActionsMenu && (onTest != null || onEdit != null || onDelete != null)) {
            Box {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Options",
                        tint = TextSecondaryDark,
                        modifier = Modifier.size(16.dp)
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    modifier = Modifier.background(BgCardDark)
                ) {
                    onTest?.let {
                        DropdownMenuItem(
                            text = { Text("Test Step", color = NeonCyan, fontSize = 13.sp) },
                            onClick = {
                                showMenu = false
                                it()
                            }
                        )
                    }
                    onEdit?.let {
                        DropdownMenuItem(
                            text = { Text("Edit Step", color = TextPrimaryDark, fontSize = 13.sp) },
                            onClick = {
                                showMenu = false
                                it()
                            }
                        )
                    }
                    if (onMoveUp != null && index > 0) {
                        DropdownMenuItem(
                            text = { Text("Move Up", color = TextPrimaryDark, fontSize = 13.sp) },
                            onClick = {
                                showMenu = false
                                onMoveUp()
                            }
                        )
                    }
                    if (onMoveDown != null && index < totalSteps - 1) {
                        DropdownMenuItem(
                            text = { Text("Move Down", color = TextPrimaryDark, fontSize = 13.sp) },
                            onClick = {
                                showMenu = false
                                onMoveDown()
                            }
                        )
                    }
                    onDelete?.let {
                        DropdownMenuItem(
                            text = { Text("Delete Step", color = AccentRose, fontSize = 13.sp) },
                            onClick = {
                                showMenu = false
                                it()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExecutionProgressCard(
    session: com.taskscheduling.app.domain.model.ExecutionSession,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onRunAgain: () -> Unit
) {
    val state = session.state
    val isRunning = state == ExecutionState.RUNNING
    val isPaused = state == ExecutionState.PAUSED
    val isCompleted = state == ExecutionState.COMPLETED
    val isFailed = state == ExecutionState.FAILED

    val cardBorderColor = when {
        isCompleted -> AccentEmerald
        isFailed -> AccentRose
        else -> NeonCyan
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = BgCardDark),
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, cardBorderColor.copy(alpha = 0.6f))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = when {
                        isCompleted -> "✓ AUTOMATION COMPLETE"
                        isFailed -> "✕ AUTOMATION FAILED"
                        isPaused -> "PAUSED"
                        else -> "RUNNING"
                    },
                    color = cardBorderColor,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )

                Text(
                    text = "Step ${session.currentStepIndex + 1} of ${session.totalSteps}",
                    color = TextSecondaryDark,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            LinearProgressIndicator(
                progress = { session.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                color = cardBorderColor,
                trackColor = BorderSubtleDark
            )

            Spacer(modifier = Modifier.height(14.dp))

            if (isRunning || isPaused) {
                Text(
                    text = "Workflow: ${session.taskTitle.ifBlank { "Change Wallpaper" }}",
                    color = NeonCyan,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))

                session.currentStep?.let { step ->
                    Text(
                        text = "Step ${step.order} of ${session.totalSteps}: ${step.type.label}",
                        color = TextPrimaryDark,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    val detail = when (step.type) {
                        ActionType.TAP, ActionType.LONG_PRESS -> "X: ${step.startX}, Y: ${step.startY}"
                        ActionType.SWIPE -> "(${step.startX}, ${step.startY}) → (${step.endX ?: 0}, ${step.endY ?: 0})"
                        ActionType.WAIT -> "Wait ${step.duration} ms"
                        ActionType.BACK -> "System Back"
                        else -> "X: ${step.startX}, Y: ${step.startY}"
                    }
                    Text(text = detail, color = NeonCyan, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                    Text(text = "Status: ● EXECUTING", color = AccentAmber, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (isRunning) {
                        Button(
                            onClick = onPause,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentAmber)
                        ) {
                            Icon(imageVector = Icons.Default.Pause, contentDescription = "Pause")
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("PAUSE", fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Button(
                            onClick = onResume,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentEmerald)
                        ) {
                            Icon(imageVector = Icons.Default.PlayArrow, contentDescription = "Resume")
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("RESUME", fontWeight = FontWeight.Bold)
                        }
                    }

                    Button(
                        onClick = onStop,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentRose)
                    ) {
                        Icon(imageVector = Icons.Default.Stop, contentDescription = "Stop")
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("STOP", fontWeight = FontWeight.Bold)
                    }
                }
            } else if (isCompleted) {
                Text(
                    text = "Workflow: ${session.taskTitle.ifBlank { "Change Wallpaper" }}",
                    color = NeonCyan,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Workflow completed.\nAll ${session.totalSteps} recorded action${if (session.totalSteps != 1) "s" else ""} finished successfully.",
                    color = TextPrimaryDark,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onRunAgain,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentEmerald),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("RUN AGAIN", fontWeight = FontWeight.Bold)
                }
            } else if (isFailed) {
                Text(
                    text = "Result: ${session.errorMessage ?: "Gesture failed"}\nThe workflow has been stopped.",
                    color = AccentRose,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onRunAgain,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentIndigo),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("RETRY WORKFLOW", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun StepEditDialog(
    step: WorkflowStep,
    onSave: (WorkflowStep) -> Unit,
    onDismiss: () -> Unit
) {
    var startX by remember { mutableStateOf(step.startX.toString()) }
    var startY by remember { mutableStateOf(step.startY.toString()) }
    var duration by remember { mutableStateOf(step.duration.toString()) }
    var waitAfter by remember { mutableStateOf(step.waitAfter.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BgCardDark,
        title = { Text("Edit Step ${step.order}: ${step.type.label}", color = TextPrimaryDark, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (step.type == ActionType.TAP || step.type == ActionType.LONG_PRESS || step.type == ActionType.SWIPE) {
                    OutlinedTextField(
                        value = startX,
                        onValueChange = { startX = it },
                        label = { Text("X Coordinate (pixels)") },
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonCyan)
                    )
                    OutlinedTextField(
                        value = startY,
                        onValueChange = { startY = it },
                        label = { Text("Y Coordinate (pixels)") },
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonCyan)
                    )
                }
                OutlinedTextField(
                    value = duration,
                    onValueChange = { duration = it },
                    label = { Text("Duration (ms)") },
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonCyan)
                )
                OutlinedTextField(
                    value = waitAfter,
                    onValueChange = { waitAfter = it },
                    label = { Text("Wait After Step (ms)") },
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonCyan)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val x = startX.toIntOrNull() ?: step.startX
                    val y = startY.toIntOrNull() ?: step.startY
                    val d = duration.toLongOrNull() ?: step.duration
                    val w = waitAfter.toLongOrNull() ?: step.waitAfter
                    onSave(
                        step.copy(
                            startX = x,
                            startY = y,
                            duration = d,
                            waitAfter = w,
                            coordinate = CoordinateAction(x, y, step.endX, step.endY),
                            durationMs = d
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan)
            ) {
                Text("SAVE", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCEL", color = TextSecondaryDark)
            }
        }
    )
}
