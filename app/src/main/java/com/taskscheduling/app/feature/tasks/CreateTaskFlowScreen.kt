package com.taskscheduling.app.feature.tasks

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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.taskscheduling.app.domain.model.Task
import com.taskscheduling.app.domain.model.TaskCategory
import com.taskscheduling.app.domain.model.TaskType
import com.taskscheduling.app.domain.model.Workflow
import com.taskscheduling.app.domain.model.WorkflowStep
import com.taskscheduling.app.ui.theme.AccentEmerald
import com.taskscheduling.app.ui.theme.AccentIndigo
import com.taskscheduling.app.ui.theme.BgCardDark
import com.taskscheduling.app.ui.theme.BgDeepDark
import com.taskscheduling.app.ui.theme.BorderSubtleDark
import com.taskscheduling.app.ui.theme.NeonCyan
import com.taskscheduling.app.ui.theme.TextPrimaryDark
import com.taskscheduling.app.ui.theme.TextSecondaryDark

@Composable
fun CreateTaskFlowScreen(
    onSaveTask: (Task) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var currentStep by remember { mutableIntStateOf(1) }

    // Form State maintained across steps
    var taskName by remember { mutableStateOf("") }
    var taskDescription by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(TaskCategory.CUSTOM) }
    var selectedType by remember { mutableStateOf(TaskType.COORDINATE_WORKFLOW) }
    var voiceTrigger by remember { mutableStateOf("") }

    var initialStepLabel by remember { mutableStateOf("Open Settings") }
    var initialCoordX by remember { mutableStateOf("880") }
    var initialCoordY by remember { mutableStateOf("1600") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BgDeepDark)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Top Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimaryDark)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(text = "Create New Task", color = TextPrimaryDark, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(text = "Step $currentStep of 5", color = NeonCyan, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Step Progress Indicator
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            for (i in 1..5) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            if (i <= currentStep) NeonCyan else BgCardDark,
                            CircleShape
                        )
                        .border(
                            1.dp,
                            if (i == currentStep) NeonCyan else BorderSubtleDark,
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (i < currentStep) {
                        Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = BgDeepDark, modifier = Modifier.size(18.dp))
                    } else {
                        Text(
                            text = "$i",
                            color = if (i <= currentStep) BgDeepDark else TextSecondaryDark,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Multi-Step Content
        when (currentStep) {
            1 -> {
                // STEP 1: Task Information
                Text("STEP 1: TASK INFORMATION", color = NeonCyan, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = taskName,
                    onValueChange = { taskName = it },
                    label = { Text("Task Name") },
                    placeholder = { Text("e.g. Turn on Bluetooth") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = taskDescription,
                    onValueChange = { taskDescription = it },
                    label = { Text("Description") },
                    placeholder = { Text("e.g. Opens settings and toggles Bluetooth switch") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text("Select Category:", color = TextSecondaryDark, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TaskCategory.values().filterNot { it == TaskCategory.ALL }.forEach { cat ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (selectedCategory == cat) NeonCyan.copy(alpha = 0.15f) else BgCardDark, RoundedCornerShape(10.dp))
                                .border(1.dp, if (selectedCategory == cat) NeonCyan else BorderSubtleDark, RoundedCornerShape(10.dp))
                                .clickable { selectedCategory = cat }
                                .padding(12.dp)
                        ) {
                            Text(text = cat.displayName, color = if (selectedCategory == cat) NeonCyan else TextPrimaryDark, fontSize = 13.sp)
                        }
                    }
                }
            }

            2 -> {
                // STEP 2: Task Type
                Text("STEP 2: TASK TYPE", color = NeonCyan, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))

                TaskType.values().forEach { type ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .background(if (selectedType == type) AccentIndigo.copy(alpha = 0.2f) else BgCardDark, RoundedCornerShape(12.dp))
                            .border(1.dp, if (selectedType == type) NeonCyan else BorderSubtleDark, RoundedCornerShape(12.dp))
                            .clickable { selectedType = type }
                            .padding(14.dp)
                    ) {
                        Column {
                            Text(text = type.displayName, color = TextPrimaryDark, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text(
                                text = when (type) {
                                    TaskType.COORDINATE_WORKFLOW -> "Execute precise sequential screen touch coordinates."
                                    TaskType.SCHEDULED_TASK -> "Runs automatically at a specific time of day."
                                    TaskType.VOICE_COMMAND -> "Triggers instantly when speaking keyword."
                                    TaskType.QUICK_ACTION -> "One-tap shortcut from home screen."
                                },
                                color = TextSecondaryDark,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = voiceTrigger,
                    onValueChange = { voiceTrigger = it },
                    label = { Text("Voice / Chat Trigger Phrase") },
                    placeholder = { Text("e.g. bluetooth, enable bluetooth") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            3 -> {
                // STEP 3: Initial Workflow Step
                Text("STEP 3: WORKFLOW BUILDER INITIALIZATION", color = NeonCyan, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = initialStepLabel,
                    onValueChange = { initialStepLabel = it },
                    label = { Text("Initial Step 1 Label") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = initialCoordX,
                        onValueChange = { initialCoordX = it },
                        label = { Text("Touch X (0-1080)") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = initialCoordY,
                        onValueChange = { initialCoordY = it },
                        label = { Text("Touch Y (0-2400)") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "You can add more steps, drag coordinates, or test gestures in the visual Workflow Builder later.",
                    color = TextSecondaryDark,
                    fontSize = 11.sp
                )
            }

            4 -> {
                // STEP 4: Review
                Text("STEP 4: REVIEW TASK DETAILS", color = NeonCyan, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(BgCardDark, RoundedCornerShape(16.dp))
                        .border(1.dp, BorderSubtleDark, RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(text = taskName.ifBlank { "Untitled Task" }, color = TextPrimaryDark, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text(text = taskDescription.ifBlank { "No description provided." }, color = TextSecondaryDark, fontSize = 13.sp)

                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Column {
                                Text("CATEGORY", color = Color(0xFF64748B), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                Text(selectedCategory.displayName, color = NeonCyan, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                            Column {
                                Text("TYPE", color = Color(0xFF64748B), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                Text(selectedType.displayName, color = TextPrimaryDark, fontSize = 12.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text("WORKFLOW STEPS (1 INITIAL STEP):", color = Color(0xFF64748B), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        Text("1. $initialStepLabel at ($initialCoordX, $initialCoordY)", color = TextPrimaryDark, fontSize = 12.sp)
                    }
                }
            }

            5 -> {
                // STEP 5: Save & Confirm
                Text("STEP 5: READY TO SAVE", color = AccentEmerald, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0x2210B981), RoundedCornerShape(16.dp))
                        .border(1.dp, AccentEmerald, RoundedCornerShape(16.dp))
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("✔", fontSize = 36.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Task Configured Successfully!", color = TextPrimaryDark, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text("Click below to save and open in Workflow Builder.", color = TextSecondaryDark, fontSize = 12.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(30.dp))

        // Navigation Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (currentStep > 1) {
                OutlinedButton(
                    onClick = { currentStep-- },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Previous")
                }
            } else {
                Spacer(modifier = Modifier.width(1.dp))
            }

            if (currentStep < 5) {
                Button(
                    onClick = {
                        if (currentStep == 1 && taskName.isBlank()) taskName = "Custom Task ${System.currentTimeMillis() % 1000}"
                        currentStep++
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = BgDeepDark),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Next Step")
                }
            } else {
                Button(
                    onClick = {
                        val finalTask = Task(
                            id = "task_${System.currentTimeMillis()}",
                            title = taskName.ifBlank { "Custom Task" },
                            description = taskDescription.ifBlank { "Custom predefined coordinate task." },
                            category = selectedCategory,
                            type = selectedType,
                            voiceTriggers = if (voiceTrigger.isNotBlank()) listOf(voiceTrigger) else emptyList(),
                            workflow = Workflow(
                                id = "wf_${System.currentTimeMillis()}",
                                name = taskName.ifBlank { "Custom Task" },
                                steps = listOf(
                                    WorkflowStep(
                                        id = "step_1",
                                        stepNumber = 1,
                                        label = initialStepLabel,
                                        actionType = ActionType.TAP,
                                        coordinate = CoordinateAction(
                                            x = initialCoordX.toIntOrNull() ?: 540,
                                            y = initialCoordY.toIntOrNull() ?: 1200
                                        ),
                                        durationMs = 400L,
                                        explanation = initialStepLabel
                                    )
                                )
                            )
                        )
                        onSaveTask(finalTask)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentEmerald, contentColor = Color.White),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Save & Open Task")
                }
            }
        }
    }
}
