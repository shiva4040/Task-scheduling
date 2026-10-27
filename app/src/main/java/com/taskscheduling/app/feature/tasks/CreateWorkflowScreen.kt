package com.taskscheduling.app.feature.tasks

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.taskscheduling.app.accessibility.AccessibilityUtils
import com.taskscheduling.app.accessibility.TaskSchedulingAccessibilityService
import com.taskscheduling.app.data.repository.TaskRepository
import com.taskscheduling.app.data.repository.WorkflowPersistenceRepository
import com.taskscheduling.app.domain.model.ActionType
import com.taskscheduling.app.domain.model.CoordinateAction
import com.taskscheduling.app.domain.model.SessionState
import com.taskscheduling.app.domain.model.Workflow
import com.taskscheduling.app.domain.model.WorkflowStep
import com.taskscheduling.app.recording.RecordingManager
import com.taskscheduling.app.ui.theme.AccentAmber
import com.taskscheduling.app.ui.theme.AccentEmerald
import com.taskscheduling.app.ui.theme.AccentIndigo
import com.taskscheduling.app.ui.theme.AccentRose
import com.taskscheduling.app.ui.theme.AppTheme
import com.taskscheduling.app.ui.theme.Glass3DButton
import com.taskscheduling.app.ui.theme.GlassCard
import com.taskscheduling.app.ui.theme.GlassCyan
import com.taskscheduling.app.ui.theme.GlassIndigo
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreateWorkflowScreen(
    workflowPersistenceRepo: WorkflowPersistenceRepository,
    taskRepository: TaskRepository,
    onNavigateBack: () -> Unit,
    onWorkflowSaved: (Workflow) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val colors = AppTheme.colors
    val scope = rememberCoroutineScope()

    var workflowName by remember { mutableStateOf("") }
    var workflowDescription by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showDiscardDialog by remember { mutableStateOf(false) }

    // Step editing dialog state
    var stepBeingEdited by remember { mutableStateOf<WorkflowStep?>(null) }
    var showAddManualStepDialog by remember { mutableStateOf(false) }

    val recordedSteps by RecordingManager.recordedSteps.collectAsState()
    val sessionState by RecordingManager.sessionState.collectAsState()
    val elapsedSeconds by RecordingManager.elapsedSeconds.collectAsState()

    // Initialize repository reference on load
    LaunchedEffect(Unit) {
        RecordingManager.initRepository(workflowPersistenceRepo)
    }

    val isAccessibilityEnabled = AccessibilityUtils.isAccessibilityServiceEnabled(context)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Header (Back button, Title: "Create Workflow", Subtitle)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = {
                        if (recordedSteps.isNotEmpty()) {
                            showDiscardDialog = true
                        } else {
                            RecordingManager.cancelRecording()
                            onNavigateBack()
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = colors.textPrimary
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Column {
                    Text(
                        text = "Create Workflow",
                        color = colors.textPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.3).sp
                    )
                    Text(
                        text = "Record actions on your device",
                        color = colors.textSecondary,
                        fontSize = 12.sp
                    )
                }
            }

            // Status chip badge
            val badgeColor = when (sessionState) {
                SessionState.RECORDING -> AccentRose
                SessionState.PAUSED -> AccentAmber
                SessionState.REVIEW -> AccentEmerald
                SessionState.SAVED -> AccentEmerald
                else -> colors.textTertiary
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(badgeColor.copy(alpha = 0.15f))
                    .border(1.dp, badgeColor.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = when (sessionState) {
                        SessionState.RECORDING -> "● RECORDING"
                        SessionState.PAUSED -> "⏸ PAUSED"
                        SessionState.REVIEW -> "✓ REVIEW"
                        SessionState.SAVED -> "SAVED"
                        else -> "IDLE"
                    },
                    color = badgeColor,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Accessibility Service warning banner if not enabled
        if (!isAccessibilityEnabled) {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = AccentAmber.copy(alpha = 0.12f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Accessibility Service Required",
                            color = AccentAmber,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Enable Task Scheduling in Accessibility settings to record gestures across other apps.",
                            color = colors.textSecondary,
                            fontSize = 11.5.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            try {
                                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                context.startActivity(intent)
                            } catch (_: Exception) {}
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentAmber),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Enable", color = Color.Black, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 2. Workflow Details Card (Workflow Name required, Description optional)
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "WORKFLOW NAME *",
                color = GlassIndigo,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = workflowName,
                onValueChange = {
                    workflowName = it
                    if (it.isNotBlank()) errorMessage = null
                },
                placeholder = {
                    Text(
                        text = "e.g. Open Notes and Dictate",
                        fontSize = 14.5.sp,
                        color = colors.textTertiary
                    )
                },
                textStyle = TextStyle(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary
                ),
                singleLine = true,
                isError = errorMessage != null && workflowName.isBlank(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.primary,
                    unfocusedBorderColor = colors.borderSubtle,
                    focusedTextColor = colors.textPrimary,
                    unfocusedTextColor = colors.textPrimary,
                    focusedContainerColor = colors.surface,
                    unfocusedContainerColor = colors.surface,
                    errorBorderColor = AccentRose
                )
            )

            if (errorMessage != null && workflowName.isBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = errorMessage!!,
                    color = AccentRose,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "DESCRIPTION (OPTIONAL)",
                color = colors.textSecondary,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = workflowDescription,
                onValueChange = { workflowDescription = it },
                placeholder = {
                    Text(
                        text = "Explain the actions this routine performs...",
                        fontSize = 13.sp,
                        color = colors.textTertiary
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 3,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.primary,
                    unfocusedBorderColor = colors.borderSubtle,
                    focusedTextColor = colors.textPrimary,
                    unfocusedTextColor = colors.textPrimary,
                    focusedContainerColor = colors.surface,
                    unfocusedContainerColor = colors.surface
                )
            )
        }

        // 3. Compact Recording Panel
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "RECORDING PANEL",
                        color = GlassCyan,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    val mins = elapsedSeconds / 60
                    val secs = elapsedSeconds % 60
                    val timerStr = String.format("%02d:%02d", mins, secs)
                    Text(
                        text = if (recordedSteps.isEmpty()) "0 steps recorded" else "${recordedSteps.size} step${if (recordedSteps.size != 1) "s" else ""} recorded • $timerStr",
                        color = if (recordedSteps.isEmpty()) colors.textSecondary else AccentEmerald,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // If currently recording or paused, offer quick control
                if (sessionState == SessionState.RECORDING || sessionState == SessionState.PAUSED) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        IconButton(
                            onClick = {
                                if (sessionState == SessionState.RECORDING) {
                                    RecordingManager.pauseRecording()
                                } else {
                                    RecordingManager.resumeActiveRecording()
                                }
                            },
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(colors.surfaceSecondary)
                        ) {
                            Text(
                                text = if (sessionState == SessionState.RECORDING) "⏸" else "▶",
                                fontSize = 14.sp
                            )
                        }

                        IconButton(
                            onClick = {
                                TaskSchedulingAccessibilityService.instance?.let { service ->
                                    service.overlayManager.showFloatingRecorder(
                                        onFinish = { RecordingManager.finishRecording() },
                                        onCancel = { RecordingManager.cancelRecording() }
                                    )
                                }
                            },
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(colors.surfaceSecondary)
                        ) {
                            Text(text = "🪟", fontSize = 14.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Guidance explanation
            Text(
                text = when {
                    recordedSteps.isEmpty() && sessionState == SessionState.IDLE ->
                        "0 steps recorded. Tap Start Recording to launch the floating recorder and perform several actions across other apps."
                    sessionState == SessionState.RECORDING ->
                        "Floating recorder is active. You can switch apps and capture taps, long presses, swipes, text, or back buttons."
                    sessionState == SessionState.PAUSED ->
                        "Recording is paused. Actions will not be captured until you resume."
                    else ->
                        "You can perform several actions before finishing. Review and adjust each step below."
                },
                color = colors.textSecondary,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Recording control buttons
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (sessionState == SessionState.IDLE || sessionState == SessionState.SAVED || sessionState == SessionState.CANCELLED) {
                    Glass3DButton(
                        text = "Start Recording",
                        icon = Icons.Default.RadioButtonChecked,
                        onClick = {
                            val id = UUID.randomUUID().toString()
                            RecordingManager.startRecording(
                                context = context,
                                workflowId = id,
                                workflowName = workflowName.ifBlank { "New Workflow" },
                                onFinish = {},
                                onCancel = {}
                            )
                        },
                        accentColor = AccentRose,
                        contentColor = Color.White
                    )

                    Glass3DButton(
                        text = "+ Add Step Manually",
                        icon = Icons.Default.Add,
                        onClick = { showAddManualStepDialog = true },
                        accentColor = GlassIndigo
                    )
                } else if (sessionState == SessionState.RECORDING || sessionState == SessionState.PAUSED) {
                    Glass3DButton(
                        text = if (sessionState == SessionState.RECORDING) "Pause" else "Resume",
                        onClick = {
                            if (sessionState == SessionState.RECORDING) {
                                RecordingManager.pauseRecording()
                            } else {
                                RecordingManager.resumeActiveRecording()
                            }
                        },
                        accentColor = if (sessionState == SessionState.RECORDING) AccentAmber else AccentEmerald
                    )

                    if (recordedSteps.isNotEmpty()) {
                        Glass3DButton(
                            text = "Undo Last Step",
                            icon = Icons.Default.Undo,
                            onClick = { RecordingManager.undoLastStep() },
                            accentColor = Color(0xFF64748B)
                        )
                    }

                    Glass3DButton(
                        text = "Finish Recording",
                        icon = Icons.Default.Check,
                        onClick = { RecordingManager.finishRecording() },
                        accentColor = AccentEmerald,
                        contentColor = Color.White
                    )
                } else if (sessionState == SessionState.REVIEW) {
                    Glass3DButton(
                        text = "Continue Recording",
                        icon = Icons.Default.RadioButtonChecked,
                        onClick = {
                            RecordingManager.resumeActiveRecording()
                            TaskSchedulingAccessibilityService.instance?.let { service ->
                                service.overlayManager.showFloatingRecorder(
                                    onFinish = { RecordingManager.finishRecording() },
                                    onCancel = { RecordingManager.cancelRecording() }
                                )
                            }
                        },
                        accentColor = AccentRose
                    )

                    Glass3DButton(
                        text = "+ Add Step Manually",
                        icon = Icons.Default.Add,
                        onClick = { showAddManualStepDialog = true },
                        accentColor = GlassIndigo
                    )
                }
            }
        }

        // 4. Recorded Steps Review Section (Phase 7)
        if (recordedSteps.isNotEmpty()) {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "RECORDED ACTIONS (${recordedSteps.size})",
                        color = GlassIndigo,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )

                    TextButton(onClick = { RecordingManager.updateSteps(emptyList()) }) {
                        Text("Clear All", color = AccentRose, fontSize = 11.5.sp)
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    recordedSteps.forEachIndexed { index, step ->
                        StepItemCard(
                            step = step,
                            isFirst = index == 0,
                            isLast = index == recordedSteps.size - 1,
                            onMoveUp = { RecordingManager.moveStep(index, index - 1) },
                            onMoveDown = { RecordingManager.moveStep(index, index + 1) },
                            onEdit = { stepBeingEdited = step },
                            onDuplicate = { RecordingManager.duplicateStep(step.id) },
                            onDelete = { RecordingManager.deleteStep(step.id) }
                        )
                    }
                }
            }
        } else if (sessionState == SessionState.REVIEW) {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "0 steps recorded. Start recording or add steps manually to build your routine.",
                    color = colors.textSecondary,
                    fontSize = 13.sp
                )
            }
        }

        // 5. Footer: Save Workflow & Cancel
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Glass3DButton(
                text = "Cancel",
                onClick = {
                    if (recordedSteps.isNotEmpty()) {
                        showDiscardDialog = true
                    } else {
                        RecordingManager.cancelRecording()
                        onNavigateBack()
                    }
                },
                accentColor = Color(0xFF64748B),
                modifier = Modifier.weight(1f)
            )

            Glass3DButton(
                text = "Save Workflow",
                icon = Icons.Default.Check,
                onClick = {
                    val cleanName = workflowName.trim()
                    if (cleanName.isBlank()) {
                        errorMessage = "Workflow name is required."
                        return@Glass3DButton
                    }
                    if (recordedSteps.isEmpty()) {
                        errorMessage = "Cannot save empty workflow. Record at least 1 action."
                        return@Glass3DButton
                    }
                    errorMessage = null

                    scope.launch {
                        val saved = RecordingManager.saveWorkflow(
                            name = cleanName,
                            description = workflowDescription.trim(),
                            persistenceRepo = workflowPersistenceRepo,
                            taskRepo = taskRepository
                        )
                        if (saved != null) {
                            onWorkflowSaved(saved)
                        } else {
                            errorMessage = "Failed to save workflow. Please try again."
                        }
                    }
                },
                accentColor = AccentEmerald,
                contentColor = Color.White,
                modifier = Modifier.weight(1.3f)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }

    // Step Edit Dialog
    if (stepBeingEdited != null) {
        val currentStep = stepBeingEdited!!
        EditStepDialog(
            step = currentStep,
            onDismiss = { stepBeingEdited = null },
            onSave = { updated ->
                RecordingManager.updateStep(updated)
                stepBeingEdited = null
            }
        )
    }

    // Manual Step Add Dialog
    if (showAddManualStepDialog) {
        AddManualStepDialog(
            nextOrder = recordedSteps.size + 1,
            onDismiss = { showAddManualStepDialog = false },
            onAdd = { newStep ->
                RecordingManager.addManualStep(newStep)
                showAddManualStepDialog = false
            }
        )
    }

    // Confirm Discard Dialog
    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("Discard Recording?", color = colors.textPrimary) },
            text = {
                Text(
                    "You have ${recordedSteps.size} unsaved step(s). Discarding will permanently erase this session.",
                    color = colors.textSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardDialog = false
                        RecordingManager.cancelRecording()
                        onNavigateBack()
                    }
                ) {
                    Text("Discard", color = AccentRose, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text("Keep Editing", color = colors.textSecondary)
                }
            },
            containerColor = colors.surface
        )
    }
}

/**
 * Step Item Card with Action Badge, Reordering, Edit, and Delete controls.
 */
@Composable
private fun StepItemCard(
    step: WorkflowStep,
    isFirst: Boolean,
    isLast: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = AppTheme.colors

    val typeColor = when (step.actionType) {
        ActionType.TAP -> GlassCyan
        ActionType.LONG_PRESS -> AccentAmber
        ActionType.SWIPE -> GlassIndigo
        ActionType.TEXT_INPUT -> Color(0xFF818CF8)
        ActionType.WAIT -> Color(0xFFF59E0B)
        ActionType.BACK, ActionType.HOME -> Color(0xFFA78BFA)
        else -> colors.textSecondary
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(colors.surfaceSecondary)
            .border(1.dp, colors.borderSubtle, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Step number indicator
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(colors.surface)
                .border(1.dp, colors.borderSubtle, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "${step.order}",
                color = colors.textPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Center: Type badge + description + target summary
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(typeColor.copy(alpha = 0.15f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = step.actionType.label.uppercase(),
                        color = typeColor,
                        fontSize = 9.5.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = step.label.ifBlank { step.description.ifBlank { step.actionType.label } },
                    color = colors.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            val targetSummary = when {
                step.actionType == ActionType.TEXT_INPUT && !step.textInput.isNullOrBlank() ->
                    "Text: \"${step.textInput}\""
                step.actionType == ActionType.WAIT ->
                    "Wait: ${step.waitDurationMs}ms"
                step.coordinate != null && (step.coordinate.x > 0 || step.coordinate.y > 0) ->
                    if (step.coordinate.endX != null && step.coordinate.endY != null) {
                        "(${step.coordinate.x}, ${step.coordinate.y}) → (${step.coordinate.endX}, ${step.coordinate.endY})"
                    } else {
                        "Target: (${step.coordinate.x}, ${step.coordinate.y})"
                    }
                !step.targetDescription.isNullOrBlank() ->
                    "Target: ${step.targetDescription}"
                else ->
                    "Duration: ${step.durationMs}ms"
            }

            Text(
                text = targetSummary,
                color = colors.textSecondary,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        // Action controls: Move Up/Down, Edit, Duplicate, Delete
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onMoveUp,
                enabled = !isFirst,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowUp,
                    contentDescription = "Move Up",
                    tint = if (!isFirst) colors.textPrimary else colors.textTertiary,
                    modifier = Modifier.size(18.dp)
                )
            }

            IconButton(
                onClick = onMoveDown,
                enabled = !isLast,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Move Down",
                    tint = if (!isLast) colors.textPrimary else colors.textTertiary,
                    modifier = Modifier.size(18.dp)
                )
            }

            IconButton(
                onClick = onEdit,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit Step",
                    tint = GlassIndigo,
                    modifier = Modifier.size(16.dp)
                )
            }

            IconButton(
                onClick = onDuplicate,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Duplicate Step",
                    tint = colors.textSecondary,
                    modifier = Modifier.size(15.dp)
                )
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete Step",
                    tint = AccentRose,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/**
 * Dialog for editing an individual WorkflowStep's parameters.
 */
@Composable
private fun EditStepDialog(
    step: WorkflowStep,
    onDismiss: () -> Unit,
    onSave: (WorkflowStep) -> Unit
) {
    val colors = AppTheme.colors

    var label by remember { mutableStateOf(step.label.ifBlank { step.description }) }
    var actionType by remember { mutableStateOf(step.actionType) }
    var startX by remember { mutableStateOf((step.coordinate?.x ?: step.startX).toString()) }
    var startY by remember { mutableStateOf((step.coordinate?.y ?: step.startY).toString()) }
    var endX by remember { mutableStateOf((step.coordinate?.endX ?: step.endX ?: 0).toString()) }
    var endY by remember { mutableStateOf((step.coordinate?.endY ?: step.endY ?: 0).toString()) }
    var textInput by remember { mutableStateOf(step.textInput ?: "") }
    var waitMs by remember { mutableStateOf(step.waitDurationMs.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Edit Step #${step.order}", color = colors.textPrimary, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("DESCRIPTION / LABEL", fontSize = 10.sp, color = colors.textSecondary, fontFamily = FontFamily.Monospace)
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (actionType == ActionType.TEXT_INPUT) {
                    Text("INPUT TEXT", fontSize = 10.sp, color = colors.textSecondary, fontFamily = FontFamily.Monospace)
                    OutlinedTextField(
                        value = textInput,
                        onValueChange = { textInput = it },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (actionType in listOf(ActionType.TAP, ActionType.LONG_PRESS, ActionType.SWIPE)) {
                    Text("X, Y COORDINATES", fontSize = 10.sp, color = colors.textSecondary, fontFamily = FontFamily.Monospace)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = startX,
                            onValueChange = { startX = it },
                            label = { Text("X") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = startY,
                            onValueChange = { startY = it },
                            label = { Text("Y") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                if (actionType == ActionType.SWIPE) {
                    Text("END COORDINATES", fontSize = 10.sp, color = colors.textSecondary, fontFamily = FontFamily.Monospace)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = endX,
                            onValueChange = { endX = it },
                            label = { Text("End X") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = endY,
                            onValueChange = { endY = it },
                            label = { Text("End Y") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Text("WAIT AFTER (MS)", fontSize = 10.sp, color = colors.textSecondary, fontFamily = FontFamily.Monospace)
                OutlinedTextField(
                    value = waitMs,
                    onValueChange = { waitMs = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val sx = startX.toIntOrNull() ?: 0
                    val sy = startY.toIntOrNull() ?: 0
                    val ex = endX.toIntOrNull()
                    val ey = endY.toIntOrNull()
                    val w = waitMs.toLongOrNull() ?: 500L

                    val updated = step.copyWithSync(
                        label = label.trim(),
                        actionType = actionType,
                        coordinate = CoordinateAction(sx, sy, ex, ey),
                        textInput = if (actionType == ActionType.TEXT_INPUT) textInput.trim() else null,
                        waitDurationMs = w
                    )
                    onSave(updated)
                }
            ) {
                Text("Save", color = AccentEmerald, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = colors.textSecondary)
            }
        },
        containerColor = colors.surface
    )
}

/**
 * Dialog for adding a step manually without recording.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddManualStepDialog(
    nextOrder: Int,
    onDismiss: () -> Unit,
    onAdd: (WorkflowStep) -> Unit
) {
    val colors = AppTheme.colors

    var actionType by remember { mutableStateOf(ActionType.TAP) }
    var label by remember { mutableStateOf("") }
    var coordX by remember { mutableStateOf("540") }
    var coordY by remember { mutableStateOf("960") }
    var textInput by remember { mutableStateOf("") }
    var waitDuration by remember { mutableStateOf("1000") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Add Step Manually", color = colors.textPrimary, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("ACTION TYPE", fontSize = 10.sp, color = colors.textSecondary, fontFamily = FontFamily.Monospace)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val supported = listOf(
                        ActionType.TAP,
                        ActionType.LONG_PRESS,
                        ActionType.SWIPE,
                        ActionType.TEXT_INPUT,
                        ActionType.WAIT,
                        ActionType.BACK,
                        ActionType.HOME
                    )
                    supported.forEach { act ->
                        val isSelected = actionType == act
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSelected) GlassIndigo else colors.surfaceSecondary)
                                .clickable { actionType = act }
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = act.label,
                                color = if (isSelected) Color.White else colors.textPrimary,
                                fontSize = 11.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                Text("DESCRIPTION", fontSize = 10.sp, color = colors.textSecondary, fontFamily = FontFamily.Monospace)
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    placeholder = { Text("e.g. Tap confirm button") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (actionType in listOf(ActionType.TAP, ActionType.LONG_PRESS)) {
                    Text("COORDINATES (X, Y)", fontSize = 10.sp, color = colors.textSecondary, fontFamily = FontFamily.Monospace)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = coordX,
                            onValueChange = { coordX = it },
                            label = { Text("X") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = coordY,
                            onValueChange = { coordY = it },
                            label = { Text("Y") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                if (actionType == ActionType.TEXT_INPUT) {
                    Text("TEXT TO ENTER", fontSize = 10.sp, color = colors.textSecondary, fontFamily = FontFamily.Monospace)
                    OutlinedTextField(
                        value = textInput,
                        onValueChange = { textInput = it },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (actionType == ActionType.WAIT) {
                    Text("WAIT DURATION (MS)", fontSize = 10.sp, color = colors.textSecondary, fontFamily = FontFamily.Monospace)
                    OutlinedTextField(
                        value = waitDuration,
                        onValueChange = { waitDuration = it },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val x = coordX.toIntOrNull() ?: 0
                    val y = coordY.toIntOrNull() ?: 0
                    val w = waitDuration.toLongOrNull() ?: 1000L
                    val finalLabel = label.ifBlank { actionType.label }

                    val newStep = WorkflowStep(
                        id = UUID.randomUUID().toString(),
                        order = nextOrder,
                        type = actionType,
                        startX = x,
                        startY = y,
                        duration = if (actionType == ActionType.LONG_PRESS) 900L else 100L,
                        waitAfter = if (actionType == ActionType.WAIT) 0L else 500L,
                        description = finalLabel,
                        stepNumber = nextOrder,
                        label = finalLabel,
                        actionType = actionType,
                        coordinate = CoordinateAction(x, y),
                        textInput = if (actionType == ActionType.TEXT_INPUT) textInput.trim() else null,
                        waitDurationMs = if (actionType == ActionType.WAIT) w else 500L
                    )
                    onAdd(newStep)
                }
            ) {
                Text("Add Step", color = AccentEmerald, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = colors.textSecondary)
            }
        },
        containerColor = colors.surface
    )
}
