package com.taskscheduling.app.feature.builder

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
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
import com.taskscheduling.app.domain.model.ActionType
import com.taskscheduling.app.domain.model.CoordinateAction
import com.taskscheduling.app.domain.model.WorkflowStep
import com.taskscheduling.app.ui.components.PhonePreview
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
fun WorkflowBuilderScreen(
    viewModel: BuilderViewModel,
    taskId: String?,
    onNavigateToExecution: (String) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(taskId) {
        viewModel.loadTask(taskId)
    }

    val task = uiState.task
    val steps = task?.workflow?.steps ?: emptyList()
    val selectedStep = steps.getOrNull(uiState.selectedStepIndex)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BgDeepDark)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // 1. Header with Task Title & Run Button
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
                        text = task?.title ?: "Workflow Builder",
                        color = TextPrimaryDark,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Tap phone preview to set coordinates",
                        color = NeonCyan,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { viewModel.toggleRadarGrid() },
                    modifier = Modifier
                        .background(if (uiState.showRadarGrid) NeonCyan.copy(alpha = 0.2f) else BgCardDark, CircleShape)
                        .border(1.dp, if (uiState.showRadarGrid) NeonCyan else BorderSubtleDark, CircleShape)
                ) {
                    Icon(imageVector = Icons.Default.GridOn, contentDescription = "Grid", tint = if (uiState.showRadarGrid) NeonCyan else TextSecondaryDark)
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = { task?.let { onNavigateToExecution(it.id) } },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentEmerald),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Run", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 2. Simulated Screen Selector Row
        Text(
            text = "SIMULATED PHONE SCREEN:",
            color = Color(0xFF64748B),
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val screens = listOf("Home", "Settings", "Display", "Wallpaper", "Wifi", "Phone", "QuickSettings")
            screens.forEach { s ->
                val isSelected = uiState.selectedScreen == s
                Box(
                    modifier = Modifier
                        .background(if (isSelected) NeonCyan.copy(alpha = 0.2f) else BgCardDark, RoundedCornerShape(8.dp))
                        .border(1.dp, if (isSelected) NeonCyan else BorderSubtleDark, RoundedCornerShape(8.dp))
                        .clickable { viewModel.setScreen(s) }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(text = s, color = if (isSelected) NeonCyan else TextSecondaryDark, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 3. Central Phone Preview Component
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(380.dp),
            contentAlignment = Alignment.Center
        ) {
            PhonePreview(
                currentScreen = uiState.selectedScreen,
                activeCoordinate = selectedStep?.coordinate,
                activeActionType = selectedStep?.actionType,
                showRadarGrid = uiState.showRadarGrid,
                onCoordinateTapped = { coord ->
                    viewModel.onPhoneScreenTapped(coord)
                }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 4. Workflow Timeline
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "WORKFLOW TIMELINE (${steps.size} STEPS)",
                color = TextSecondaryDark,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )

            Button(
                onClick = {
                    viewModel.addNewStepAtCoord(CoordinateAction(x = 540, y = 1200))
                },
                colors = ButtonDefaults.buttonColors(containerColor = AccentIndigo),
                shape = RoundedCornerShape(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add Step", fontSize = 11.sp)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Timeline Step Items
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            steps.forEachIndexed { index, step ->
                val isSelected = uiState.selectedStepIndex == index
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (isSelected) AccentIndigo.copy(alpha = 0.2f) else BgCardDark, RoundedCornerShape(12.dp))
                        .border(1.dp, if (isSelected) NeonCyan else BorderSubtleDark, RoundedCornerShape(12.dp))
                        .clickable { viewModel.selectStep(index) }
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .background(if (isSelected) NeonCyan else Color(0x33FFFFFF), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${step.stepNumber}",
                                    color = if (isSelected) BgDeepDark else Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = step.label,
                                        color = TextPrimaryDark,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "[${step.actionType.label.uppercase()}]",
                                        color = NeonCyan,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                Text(
                                    text = if (step.coordinate != null) {
                                        if (step.coordinate.endX != null) "(${step.coordinate.x}, ${step.coordinate.y}) → (${step.coordinate.endX}, ${step.coordinate.endY})"
                                        else "(${step.coordinate.x}, ${step.coordinate.y}) • ${step.durationMs}ms"
                                    } else "System Key Action",
                                    color = TextSecondaryDark,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        // Reorder / actions
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (index > 0) {
                                IconButton(
                                    onClick = { viewModel.moveStepUp(index) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.ArrowUpward, contentDescription = "Up", tint = TextSecondaryDark, modifier = Modifier.size(16.dp))
                                }
                            }
                            if (index < steps.size - 1) {
                                IconButton(
                                    onClick = { viewModel.moveStepDown(index) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.ArrowDownward, contentDescription = "Down", tint = TextSecondaryDark, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // 5. Coordinate Inspector Panel
        selectedStep?.let { step ->
            CoordinateInspectorPanel(
                step = step,
                onSave = { label, actionType, x, y, endX, endY, dur ->
                    viewModel.updateSelectedStep(label, actionType, x, y, endX, endY, dur)
                },
                onDelete = { viewModel.deleteSelectedStep() },
                onDuplicate = { viewModel.duplicateSelectedStep() }
            )
        }
    }
}

@Composable
private fun CoordinateInspectorPanel(
    step: WorkflowStep,
    onSave: (String, ActionType, Int, Int, Int?, Int?, Long) -> Unit,
    onDelete: () -> Unit,
    onDuplicate: () -> Unit
) {
    var stepLabel by remember(step) { mutableStateOf(step.label) }
    var actionType by remember(step) { mutableStateOf(step.actionType) }
    var xStr by remember(step) { mutableStateOf(step.coordinate?.x?.toString() ?: "540") }
    var yStr by remember(step) { mutableStateOf(step.coordinate?.y?.toString() ?: "1200") }
    var endXStr by remember(step) { mutableStateOf(step.coordinate?.endX?.toString() ?: "540") }
    var endYStr by remember(step) { mutableStateOf(step.coordinate?.endY?.toString() ?: "500") }
    var durationStr by remember(step) { mutableStateOf(step.durationMs.toString()) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(BgCardDark, RoundedCornerShape(16.dp))
            .border(1.dp, NeonCyan.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "STEP ${step.stepNumber} INSPECTOR",
                    color = NeonCyan,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )

                Row {
                    IconButton(onClick = onDuplicate, modifier = Modifier.size(28.dp)) {
                        Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Duplicate", tint = TextSecondaryDark, modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = AccentRose, modifier = Modifier.size(16.dp))
                    }
                }
            }

            OutlinedTextField(
                value = stepLabel,
                onValueChange = {
                    stepLabel = it
                    onSave(stepLabel, actionType, xStr.toIntOrNull() ?: 0, yStr.toIntOrNull() ?: 0, endXStr.toIntOrNull(), endYStr.toIntOrNull(), durationStr.toLongOrNull() ?: 400L)
                },
                label = { Text("Step Label") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            )

            // Action Type Selector Chips
            Text("Action Type:", color = TextSecondaryDark, fontSize = 11.sp)
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
                            .background(if (isSel) NeonCyan.copy(alpha = 0.2f) else Color(0x22FFFFFF), RoundedCornerShape(6.dp))
                            .border(1.dp, if (isSel) NeonCyan else Color.Transparent, RoundedCornerShape(6.dp))
                            .clickable {
                                actionType = type
                                onSave(stepLabel, actionType, xStr.toIntOrNull() ?: 0, yStr.toIntOrNull() ?: 0, endXStr.toIntOrNull(), endYStr.toIntOrNull(), durationStr.toLongOrNull() ?: 400L)
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(text = type.label, color = if (isSel) NeonCyan else Color.White, fontSize = 11.sp)
                    }
                }
            }

            // Coordinates inputs
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = xStr,
                    onValueChange = {
                        xStr = it
                        onSave(stepLabel, actionType, xStr.toIntOrNull() ?: 0, yStr.toIntOrNull() ?: 0, endXStr.toIntOrNull(), endYStr.toIntOrNull(), durationStr.toLongOrNull() ?: 400L)
                    },
                    label = { Text("Touch X") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                )
                OutlinedTextField(
                    value = yStr,
                    onValueChange = {
                        yStr = it
                        onSave(stepLabel, actionType, xStr.toIntOrNull() ?: 0, yStr.toIntOrNull() ?: 0, endXStr.toIntOrNull(), endYStr.toIntOrNull(), durationStr.toLongOrNull() ?: 400L)
                    },
                    label = { Text("Touch Y") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                )
            }

            if (actionType == ActionType.SWIPE || actionType == ActionType.DRAG) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = endXStr,
                        onValueChange = {
                            endXStr = it
                            onSave(stepLabel, actionType, xStr.toIntOrNull() ?: 0, yStr.toIntOrNull() ?: 0, endXStr.toIntOrNull(), endYStr.toIntOrNull(), durationStr.toLongOrNull() ?: 400L)
                        },
                        label = { Text("End X") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    )
                    OutlinedTextField(
                        value = endYStr,
                        onValueChange = {
                            endYStr = it
                            onSave(stepLabel, actionType, xStr.toIntOrNull() ?: 0, yStr.toIntOrNull() ?: 0, endXStr.toIntOrNull(), endYStr.toIntOrNull(), durationStr.toLongOrNull() ?: 400L)
                        },
                        label = { Text("End Y") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            }

            OutlinedTextField(
                value = durationStr,
                onValueChange = {
                    durationStr = it
                    onSave(stepLabel, actionType, xStr.toIntOrNull() ?: 0, yStr.toIntOrNull() ?: 0, endXStr.toIntOrNull(), endYStr.toIntOrNull(), durationStr.toLongOrNull() ?: 400L)
                },
                label = { Text("Duration (ms)") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            )
        }
    }
}
