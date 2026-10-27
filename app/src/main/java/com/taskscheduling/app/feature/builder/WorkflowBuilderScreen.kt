package com.taskscheduling.app.feature.builder

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.taskscheduling.app.accessibility.TaskSchedulingAccessibilityService
import com.taskscheduling.app.domain.model.ActionType
import com.taskscheduling.app.domain.model.CoordinateAction
import com.taskscheduling.app.domain.model.WorkflowStep
import com.taskscheduling.app.recording.RecordingManager
import com.taskscheduling.app.ui.theme.AccentEmerald
import com.taskscheduling.app.ui.theme.AccentRose
import com.taskscheduling.app.ui.theme.AppTheme
import com.taskscheduling.app.ui.theme.Glass3DButton
import com.taskscheduling.app.ui.theme.GlassAmber
import com.taskscheduling.app.ui.theme.GlassCard
import com.taskscheduling.app.ui.theme.GlassCyan
import com.taskscheduling.app.ui.theme.GlassEmerald
import com.taskscheduling.app.ui.theme.GlassIndigo
import com.taskscheduling.app.ui.theme.GlassRose
import com.taskscheduling.app.ui.theme.GlassSectionHeader

@Composable
fun WorkflowBuilderScreen(
    viewModel: BuilderViewModel,
    taskId: String?,
    onNavigateToExecution: (String) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val colors = AppTheme.colors

    LaunchedEffect(taskId) {
        viewModel.loadTask(taskId)
    }

    val task = uiState.task
    val steps = task?.workflow?.steps ?: emptyList()
    val selectedStep = steps.find { it.id == uiState.selectedStepId }
        ?: steps.getOrNull(uiState.selectedStepIndex)

    var taskTitle by remember(task?.title) { mutableStateOf(task?.title ?: "") }
    var taskDesc by remember(task?.description) { mutableStateOf(task?.description ?: "") }

    var showActionSearchDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showAccessibilityRequiredDialog by remember { mutableStateOf(false) }
    var showUnsavedChangesDialog by remember { mutableStateOf(false) }

    // Intercept back button when changes are unsaved
    BackHandler(enabled = uiState.isDirty) {
        showUnsavedChangesDialog = true
    }

    val handleSafeBack = {
        if (uiState.isDirty) {
            showUnsavedChangesDialog = true
        } else {
            onNavigateBack()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // 1. Header with Workflow Builder title & menu
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = handleSafeBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = colors.textPrimary
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Workflow Builder",
                            color = colors.textPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (uiState.isDirty) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(GlassAmber)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    "UNSAVED",
                                    color = Color(0xFF090D18),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                    Text(
                        text = "ID: ${task?.id?.take(18) ?: "NEW"} • Native Offline",
                        color = GlassIndigo,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            IconButton(onClick = { showDeleteConfirmDialog = true }) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Options",
                    tint = colors.textSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Save Success Banner
        uiState.saveSuccessMessage?.let { successMsg ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(GlassEmerald.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                    .border(1.dp, GlassEmerald, RoundedCornerShape(10.dp))
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = GlassEmerald, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = successMsg, color = GlassEmerald, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = { viewModel.dismissMessages() }, modifier = Modifier.size(24.dp)) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Dismiss", tint = GlassEmerald, modifier = Modifier.size(16.dp))
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        // Save Error Banner
        uiState.saveErrorMessage?.let { errorMsg ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(GlassRose.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                    .border(1.dp, GlassRose, RoundedCornerShape(10.dp))
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = GlassRose, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = errorMsg, color = GlassRose, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = { viewModel.dismissMessages() }, modifier = Modifier.size(24.dp)) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Dismiss", tint = GlassRose, modifier = Modifier.size(16.dp))
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        // 2. Task Details Editor Card
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = taskTitle,
                    onValueChange = {
                        taskTitle = it
                        viewModel.updateTaskDetails(taskTitle, taskDesc)
                    },
                    label = { Text("Workflow Name", color = colors.textSecondary) },
                    trailingIcon = {
                        Icon(imageVector = Icons.Default.Edit, contentDescription = null, tint = GlassIndigo, modifier = Modifier.size(18.dp))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GlassIndigo,
                        unfocusedBorderColor = colors.borderSubtle,
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary,
                        focusedContainerColor = colors.surfaceSecondary,
                        unfocusedContainerColor = colors.surfaceSecondary
                    ),
                    singleLine = true
                )

                OutlinedTextField(
                    value = taskDesc,
                    onValueChange = {
                        taskDesc = it
                        viewModel.updateTaskDetails(taskTitle, taskDesc)
                    },
                    label = { Text("Description & Purpose", color = colors.textSecondary) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GlassIndigo,
                        unfocusedBorderColor = colors.borderSubtle,
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary,
                        focusedContainerColor = colors.surfaceSecondary,
                        unfocusedContainerColor = colors.surfaceSecondary
                    ),
                    minLines = 2
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 3. Workflow Steps Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            GlassSectionHeader(
                title = "Workflow Steps (${steps.size})",
                subtitle = if (steps.any { it.requiresCoordinates && it.hasValidCoordinates }) "● Target calibrated" else "● Touchless automation steps"
            )

            // Search Action Catalog Button
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.surfaceSecondary)
                    .border(1.dp, GlassIndigo.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                    .clickable { showActionSearchDialog = true }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = GlassIndigo, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Search Actions", color = GlassIndigo, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Quick Add Action Buttons Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val quickTypes = listOf(
                ActionType.TAP to "+ Tap",
                ActionType.LONG_PRESS to "+ Long Press",
                ActionType.SWIPE to "+ Swipe",
                ActionType.WAIT to "+ Wait",
                ActionType.BACK to "+ Back",
                ActionType.TEXT_INPUT to "+ Type Text"
            )
            quickTypes.forEach { (type, label) ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.surfaceSecondary)
                        .border(1.dp, colors.borderSubtle, RoundedCornerShape(10.dp))
                        .clickable { viewModel.addNewStepWithType(type) }
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                ) {
                    Text(text = label, color = GlassIndigo, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Floating Target Recorder Quick Launch
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(GlassEmerald.copy(alpha = 0.12f))
                    .border(1.dp, GlassEmerald.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                    .clickable {
                        val service = TaskSchedulingAccessibilityService.instance
                        if (service != null && task != null) {
                            RecordingManager.startRecording(
                                context = context,
                                workflowId = task.id,
                                workflowName = task.title,
                                onFinish = {
                                    val recorded = RecordingManager.recordedSteps.value
                                    if (recorded.isNotEmpty()) {
                                        viewModel.loadTask(task.id)
                                    }
                                }
                            )
                        } else {
                            showAccessibilityRequiredDialog = true
                        }
                    }
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.FiberManualRecord, contentDescription = null, tint = AccentEmerald, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "Record with Floating Bot", color = AccentEmerald, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Steps List matching Concept Board Screen 3
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            steps.forEachIndexed { index, step ->
                val isSelected = selectedStep?.id == step.id
                val hasCoordinates = step.hasValidCoordinates
                val isCoordinateType = step.requiresCoordinates

                val stepIcon: ImageVector = when {
                    step.label.contains("Settings", ignoreCase = true) -> Icons.Default.Settings
                    step.label.contains("Image", ignoreCase = true) || step.label.contains("Gallery", ignoreCase = true) -> Icons.Default.Image
                    else -> Icons.Default.TouchApp
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isSelected) colors.surfaceSecondary else colors.surface)
                        .border(
                            1.dp,
                            if (isSelected) GlassIndigo else colors.borderSubtle,
                            RoundedCornerShape(14.dp)
                        )
                        .clickable { viewModel.selectStepById(step.id) }
                        .padding(12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                // Number Pill
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(if (isSelected) GlassIndigo else colors.surfaceSecondary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${step.stepNumber}",
                                        color = if (isSelected) Color.White else colors.textPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Spacer(modifier = Modifier.width(10.dp))

                                // Step Icon
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(GlassIndigo.copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = stepIcon,
                                        contentDescription = null,
                                        tint = GlassIndigo,
                                        modifier = Modifier.size(17.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(10.dp))

                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = step.label.ifBlank { "Step ${step.stepNumber}" },
                                            color = colors.textPrimary,
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "[${step.actionType.label.uppercase()}]",
                                            color = GlassIndigo,
                                            fontSize = 9.5.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Text(
                                        text = when {
                                            step.actionType in listOf(ActionType.SWIPE, ActionType.DRAG) && step.coordinate?.endX != null ->
                                                "(${step.coordinate.x}, ${step.coordinate.y}) → (${step.coordinate.endX}, ${step.coordinate.endY}) • ${step.durationMs}ms"
                                            isCoordinateType && hasCoordinates ->
                                                "Tap at (${step.coordinate?.x}, ${step.coordinate?.y}) • ${step.durationMs}ms"
                                            isCoordinateType ->
                                                "⚠️ Target coordinates uncalibrated"
                                            step.actionType == ActionType.TEXT_INPUT ->
                                                "Type: \"${step.textInput ?: "text"}\""
                                            else ->
                                                "Action • ${step.durationMs}ms"
                                        },
                                        color = if (isCoordinateType && !hasCoordinates) GlassAmber else colors.textSecondary,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }

                            // Reorder, insert and edit controls
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (index > 0) {
                                    IconButton(
                                        onClick = { viewModel.moveStepUp(index) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(imageVector = Icons.Default.ArrowUpward, contentDescription = "Move step up", tint = colors.textSecondary, modifier = Modifier.size(16.dp))
                                    }
                                }
                                if (index < steps.size - 1) {
                                    IconButton(
                                        onClick = { viewModel.moveStepDown(index) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(imageVector = Icons.Default.ArrowDownward, contentDescription = "Move step down", tint = colors.textSecondary, modifier = Modifier.size(16.dp))
                                    }
                                }
                                IconButton(
                                    onClick = { viewModel.selectStepById(step.id) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit Step", tint = GlassIndigo, modifier = Modifier.size(16.dp))
                                }
                            }
                        }

                        // Inline Step Actions Bar
                        if (isSelected) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(
                                    onClick = { viewModel.insertStepBefore(index, ActionType.TAP) },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text("+ Insert Before", color = GlassIndigo, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                                TextButton(
                                    onClick = { viewModel.insertStepAfter(index, ActionType.TAP) },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text("+ Insert After", color = GlassIndigo, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // "+ Add Step" Prominent Button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(colors.surfaceSecondary)
                .border(1.dp, GlassIndigo.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                .clickable { viewModel.addNewStepWithType(ActionType.TAP) }
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null, tint = GlassIndigo, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("+ Add Step", color = GlassIndigo, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // 4. Expanded Action Inspector & Calibration Panel
        selectedStep?.let { step ->
            ActionInspectorAndCalibrationPanel(
                step = step,
                onSave = { label, actionType, x, y, endX, endY, dur, waitAfter, targetScreen, targetElement, textInput, timeout, rule, retries, recovery ->
                    viewModel.updateSelectedStep(
                        label = label,
                        actionType = actionType,
                        coordinate = CoordinateAction(x, y, endX, endY),
                        durationMs = dur,
                        waitDurationMs = waitAfter,
                        targetScreen = targetScreen,
                        targetElement = targetElement,
                        textInput = textInput,
                        timeoutMs = timeout,
                        verificationRule = rule,
                        retryLimit = retries,
                        recoveryBehavior = recovery
                    )
                },
                onDelete = { viewModel.deleteSelectedStep() },
                onDuplicate = { viewModel.duplicateSelectedStep() },
                onRecordCoordinates = {
                    val service = TaskSchedulingAccessibilityService.instance
                    if (service != null) {
                        service.overlayManager.recordSingleActionCoordinates(
                            actionType = step.actionType,
                            actionLabel = step.label.ifBlank { "Step ${step.stepNumber}" },
                            onCoordinatesCaptured = { startX, startY, endX, endY, dur ->
                                viewModel.recalibrateStepCoordinates(
                                    index = uiState.selectedStepIndex,
                                    startX = startX,
                                    startY = startY,
                                    endX = endX,
                                    endY = endY,
                                    durationMs = dur
                                )
                            }
                        )
                    } else {
                        showAccessibilityRequiredDialog = true
                    }
                },
                onTestCoordinates = {
                    val service = TaskSchedulingAccessibilityService.instance
                    val coord = step.coordinate
                    if (service != null && coord != null) {
                        service.showVisualTouchDot(coord.x, coord.y, "TARGET (${coord.x}, ${coord.y})")
                        if (coord.endX != null && coord.endY != null) {
                            service.showVisualTouchDot(coord.endX, coord.endY, "SWIPE END (${coord.endX}, ${coord.endY})")
                        }
                    } else {
                        showAccessibilityRequiredDialog = true
                    }
                },
                onOpenActionSearch = { showActionSearchDialog = true }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 5. Bottom Test & Save Controls (Matching Concept Board)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Glass3DButton(
                text = "Test",
                icon = Icons.Default.PlayArrow,
                onClick = {
                    task?.let { onNavigateToExecution(it.id) }
                },
                isSecondary = true,
                modifier = Modifier.weight(1f)
            )

            Glass3DButton(
                text = if (uiState.isSaving) "Saving…" else "Save",
                icon = Icons.Default.Check,
                isLoading = uiState.isSaving,
                onClick = {
                    viewModel.saveTaskExplicitly(
                        onSuccess = {
                            // saved
                        }
                    )
                },
                modifier = Modifier.weight(1.3f)
            )
        }

        Spacer(modifier = Modifier.height(28.dp))
    }

    // Action Search Dialog
    if (showActionSearchDialog) {
        ActionSearchDialog(
            onDismissRequest = { showActionSearchDialog = false },
            onItemSelected = { selectedItem ->
                showActionSearchDialog = false
                if (selectedStep != null) {
                    viewModel.updateSelectedStep(actionType = selectedItem.actionType)
                } else {
                    viewModel.addNewStepWithAction(selectedItem)
                }
            },
            onActionSelected = { selectedType ->
                showActionSearchDialog = false
                if (selectedStep != null) {
                    viewModel.updateSelectedStep(actionType = selectedType)
                } else {
                    viewModel.addNewStepWithType(selectedType)
                }
            }
        )
    }

    // Unsaved Changes Confirmation Dialog
    if (showUnsavedChangesDialog) {
        AlertDialog(
            onDismissRequest = { showUnsavedChangesDialog = false },
            containerColor = colors.surface,
            title = {
                Text("Discard Unsaved Changes?", color = GlassAmber, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            },
            text = {
                Text(
                    "You have unsaved changes in this workflow. If you exit now, these modifications will be discarded.",
                    color = colors.textSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                Glass3DButton(
                    text = "Save & Exit",
                    onClick = {
                        showUnsavedChangesDialog = false
                        viewModel.saveTaskExplicitly(
                            onSuccess = { onNavigateBack() }
                        )
                    },
                    minHeight = 40.dp
                )
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showUnsavedChangesDialog = false
                        onNavigateBack()
                    }
                ) {
                    Text("Discard", color = GlassRose, fontWeight = FontWeight.SemiBold)
                }
            }
        )
    }

    // Accessibility Service Required Dialog
    if (showAccessibilityRequiredDialog) {
        AlertDialog(
            onDismissRequest = { showAccessibilityRequiredDialog = false },
            containerColor = colors.surface,
            title = {
                Text("Accessibility Service Required", color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            },
            text = {
                Text(
                    "Target coordinate recording and gesture playback require the Accessibility Service to be turned ON in Android Settings.",
                    color = colors.textSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                Glass3DButton(
                    text = "Open Settings",
                    onClick = {
                        showAccessibilityRequiredDialog = false
                        try {
                            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(intent)
                        } catch (_: Exception) {}
                    },
                    minHeight = 40.dp
                )
            },
            dismissButton = {
                TextButton(onClick = { showAccessibilityRequiredDialog = false }) {
                    Text("Cancel", color = colors.textSecondary, fontWeight = FontWeight.SemiBold)
                }
            }
        )
    }

    // Delete Task Confirmation Dialog
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            containerColor = colors.surface,
            title = {
                Text("Delete Workflow?", color = GlassRose, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            },
            text = {
                Text(
                    "Are you sure you want to delete \"${task?.title}\"? This action will remove the workflow permanently.",
                    color = colors.textSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                Glass3DButton(
                    text = "Delete",
                    onClick = {
                        showDeleteConfirmDialog = false
                        viewModel.deleteTask {
                            onNavigateBack()
                        }
                    },
                    isDestructive = true,
                    minHeight = 40.dp
                )
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel", color = colors.textSecondary, fontWeight = FontWeight.SemiBold)
                }
            }
        )
    }
}

@Composable
private fun ActionInspectorAndCalibrationPanel(
    step: WorkflowStep,
    onSave: (
        label: String,
        actionType: ActionType,
        x: Int,
        y: Int,
        endX: Int?,
        endY: Int?,
        dur: Long,
        waitAfter: Long,
        targetScreen: String?,
        targetElement: String?,
        textInput: String?,
        timeout: Long,
        rule: String,
        retries: Int,
        recovery: String
    ) -> Unit,
    onDelete: () -> Unit,
    onDuplicate: () -> Unit,
    onRecordCoordinates: () -> Unit,
    onTestCoordinates: () -> Unit,
    onOpenActionSearch: () -> Unit
) {
    val colors = AppTheme.colors

    var stepLabel by remember(step) { mutableStateOf(step.label) }
    var actionType by remember(step) { mutableStateOf(step.actionType) }
    var xStr by remember(step) { mutableStateOf((step.coordinate?.x ?: step.startX).toString()) }
    var yStr by remember(step) { mutableStateOf((step.coordinate?.y ?: step.startY).toString()) }
    var endXStr by remember(step) { mutableStateOf((step.coordinate?.endX ?: step.endX)?.toString() ?: "") }
    var endYStr by remember(step) { mutableStateOf((step.coordinate?.endY ?: step.endY)?.toString() ?: "") }
    var durationStr by remember(step) { mutableStateOf(step.durationMs.toString()) }
    var waitAfterStr by remember(step) { mutableStateOf(step.waitDurationMs.toString()) }
    var targetScreenStr by remember(step) { mutableStateOf(step.targetScreen ?: "") }
    var targetElementStr by remember(step) { mutableStateOf(step.targetElement ?: "") }
    var textInputStr by remember(step) { mutableStateOf(step.textInput ?: "") }
    var timeoutStr by remember(step) { mutableStateOf(step.timeoutMs.toString()) }
    var verificationRule by remember(step) { mutableStateOf(step.verificationRule) }
    var retryLimit by remember(step) { mutableStateOf(step.retryLimit) }
    var recoveryBehavior by remember(step) { mutableStateOf(step.recoveryBehavior) }

    val isCoordinateAction = actionType in listOf(
        ActionType.TAP, ActionType.LONG_PRESS, ActionType.DOUBLE_TAP, ActionType.SWIPE, ActionType.DRAG
    )
    val isSwipeAction = actionType in listOf(ActionType.SWIPE, ActionType.DRAG)
    val isTextInputAction = actionType == ActionType.TEXT_INPUT

    val notifyChanges = {
        onSave(
            stepLabel,
            actionType,
            xStr.toIntOrNull() ?: 0,
            yStr.toIntOrNull() ?: 0,
            endXStr.toIntOrNull(),
            endYStr.toIntOrNull(),
            durationStr.toLongOrNull() ?: 100L,
            waitAfterStr.toLongOrNull() ?: 500L,
            targetScreenStr.ifBlank { null },
            targetElementStr.ifBlank { null },
            textInputStr.ifBlank { null },
            timeoutStr.toLongOrNull() ?: 5000L,
            verificationRule,
            retryLimit,
            recoveryBehavior
        )
    }

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Inspector Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "STEP ${step.stepNumber} INSPECTOR",
                        color = GlassIndigo,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "• ID: ${step.id.take(8)}",
                        color = colors.textTertiary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Row {
                    IconButton(onClick = onDuplicate, modifier = Modifier.size(28.dp)) {
                        Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Duplicate Step", tint = colors.textSecondary, modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete Step", tint = GlassRose, modifier = Modifier.size(16.dp))
                    }
                }
            }

            // Step Label Input
            OutlinedTextField(
                value = stepLabel,
                onValueChange = {
                    stepLabel = it
                    notifyChanges()
                },
                label = { Text("Step Label / Description", color = colors.textSecondary) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = GlassIndigo,
                    unfocusedBorderColor = colors.borderSubtle,
                    focusedTextColor = colors.textPrimary,
                    unfocusedTextColor = colors.textPrimary,
                    focusedContainerColor = colors.surfaceSecondary,
                    unfocusedContainerColor = colors.surfaceSecondary
                )
            )

            // Action Type Selector Header + Search Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Action Type: ${actionType.label}", color = colors.textSecondary, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                TextButton(
                    onClick = onOpenActionSearch,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text("Change via Search…", color = GlassIndigo, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ActionType.values().forEach { type ->
                    val isSel = actionType == type
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSel) GlassIndigo.copy(alpha = 0.15f) else colors.surfaceSecondary)
                            .border(1.dp, if (isSel) GlassIndigo else colors.borderSubtle, RoundedCornerShape(8.dp))
                            .clickable {
                                actionType = type
                                notifyChanges()
                            }
                            .padding(horizontal = 9.dp, vertical = 6.dp)
                    ) {
                        Text(text = type.label, color = if (isSel) GlassIndigo else colors.textPrimary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }

            // Text Input Field (For TEXT_INPUT action)
            if (isTextInputAction) {
                OutlinedTextField(
                    value = textInputStr,
                    onValueChange = {
                        textInputStr = it
                        notifyChanges()
                    },
                    label = { Text("Text to Type Automatically", color = colors.textSecondary) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GlassIndigo,
                        unfocusedBorderColor = colors.borderSubtle,
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary,
                        focusedContainerColor = colors.surfaceSecondary,
                        unfocusedContainerColor = colors.surfaceSecondary
                    )
                )
            }

            // Coordinate Configuration (If applicable)
            if (isCoordinateAction) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Target Screen Coordinates (Physical Device Pixels):", color = colors.textSecondary, fontSize = 11.5.sp)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = xStr,
                            onValueChange = {
                                xStr = it
                                notifyChanges()
                            },
                            label = { Text("Start X", color = colors.textSecondary, fontSize = 11.sp) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GlassIndigo,
                                unfocusedBorderColor = colors.borderSubtle,
                                focusedTextColor = colors.textPrimary,
                                unfocusedTextColor = colors.textPrimary,
                                focusedContainerColor = colors.surfaceSecondary,
                                unfocusedContainerColor = colors.surfaceSecondary
                            ),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = yStr,
                            onValueChange = {
                                yStr = it
                                notifyChanges()
                            },
                            label = { Text("Start Y", color = colors.textSecondary, fontSize = 11.sp) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GlassIndigo,
                                unfocusedBorderColor = colors.borderSubtle,
                                focusedTextColor = colors.textPrimary,
                                unfocusedTextColor = colors.textPrimary,
                                focusedContainerColor = colors.surfaceSecondary,
                                unfocusedContainerColor = colors.surfaceSecondary
                            ),
                            singleLine = true
                        )
                    }

                    if (isSwipeAction) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                endXStr,
                                onValueChange = {
                                    endXStr = it
                                    notifyChanges()
                                },
                                label = { Text("End X", color = colors.textSecondary, fontSize = 11.sp) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = GlassIndigo,
                                    unfocusedBorderColor = colors.borderSubtle,
                                    focusedTextColor = colors.textPrimary,
                                    unfocusedTextColor = colors.textPrimary,
                                    focusedContainerColor = colors.surfaceSecondary,
                                    unfocusedContainerColor = colors.surfaceSecondary
                                ),
                                singleLine = true
                            )

                            OutlinedTextField(
                                endYStr,
                                onValueChange = {
                                    endYStr = it
                                    notifyChanges()
                                },
                                label = { Text("End Y", color = colors.textSecondary, fontSize = 11.sp) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = GlassIndigo,
                                    unfocusedBorderColor = colors.borderSubtle,
                                    focusedTextColor = colors.textPrimary,
                                    unfocusedTextColor = colors.textPrimary,
                                    focusedContainerColor = colors.surfaceSecondary,
                                    unfocusedContainerColor = colors.surfaceSecondary
                                ),
                                singleLine = true
                            )
                        }
                    }

                    // Calibration & Screen Test Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Glass3DButton(
                            text = "Record on Screen",
                            icon = Icons.Default.MyLocation,
                            onClick = onRecordCoordinates,
                            accentColor = Color(0xFF0369A1),
                            modifier = Modifier.weight(1f),
                            minHeight = 38.dp,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        )

                        Glass3DButton(
                            text = "Test Target Dot",
                            icon = Icons.Default.Visibility,
                            onClick = onTestCoordinates,
                            isSecondary = true,
                            modifier = Modifier.weight(1f),
                            minHeight = 38.dp,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            // Duration & Wait Times Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = durationStr,
                    onValueChange = {
                        durationStr = it
                        notifyChanges()
                    },
                    label = { Text("Duration (ms)", color = colors.textSecondary, fontSize = 11.sp) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GlassIndigo,
                        unfocusedBorderColor = colors.borderSubtle,
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary,
                        focusedContainerColor = colors.surfaceSecondary,
                        unfocusedContainerColor = colors.surfaceSecondary
                    ),
                    singleLine = true
                )

                OutlinedTextField(
                    value = waitAfterStr,
                    onValueChange = {
                        waitAfterStr = it
                        notifyChanges()
                    },
                    label = { Text("Wait After (ms)", color = colors.textSecondary, fontSize = 11.sp) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GlassIndigo,
                        unfocusedBorderColor = colors.borderSubtle,
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary,
                        focusedContainerColor = colors.surfaceSecondary,
                        unfocusedContainerColor = colors.surfaceSecondary
                    ),
                    singleLine = true
                )
            }

            // Target Element & Screen Context
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = targetElementStr,
                    onValueChange = {
                        targetElementStr = it
                        notifyChanges()
                    },
                    label = { Text("Element ID / Tag", color = colors.textSecondary, fontSize = 11.sp) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GlassIndigo,
                        unfocusedBorderColor = colors.borderSubtle,
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary,
                        focusedContainerColor = colors.surfaceSecondary,
                        unfocusedContainerColor = colors.surfaceSecondary
                    ),
                    singleLine = true
                )

                OutlinedTextField(
                    value = targetScreenStr,
                    onValueChange = {
                        targetScreenStr = it
                        notifyChanges()
                    },
                    label = { Text("Target Screen", color = colors.textSecondary, fontSize = 11.sp) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GlassIndigo,
                        unfocusedBorderColor = colors.borderSubtle,
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary,
                        focusedContainerColor = colors.surfaceSecondary,
                        unfocusedContainerColor = colors.surfaceSecondary
                    ),
                    singleLine = true
                )
            }
        }
    }
}
