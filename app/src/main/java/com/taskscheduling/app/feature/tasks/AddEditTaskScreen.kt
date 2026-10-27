package com.taskscheduling.app.feature.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.taskscheduling.app.domain.model.ChecklistItem
import com.taskscheduling.app.domain.model.Task
import com.taskscheduling.app.domain.model.TaskCategory
import com.taskscheduling.app.domain.model.TaskDraft
import com.taskscheduling.app.domain.model.TaskPriority
import com.taskscheduling.app.domain.model.TaskRecurrence
import com.taskscheduling.app.domain.model.TaskStatus
import com.taskscheduling.app.domain.model.Workflow
import com.taskscheduling.app.ui.theme.AccentAmber
import com.taskscheduling.app.ui.theme.AccentEmerald
import com.taskscheduling.app.ui.theme.AccentIndigo
import com.taskscheduling.app.ui.theme.AccentRose
import com.taskscheduling.app.ui.theme.AppTheme
import com.taskscheduling.app.ui.theme.Glass3DButton
import com.taskscheduling.app.ui.theme.GlassCard
import com.taskscheduling.app.ui.theme.GlassCyan
import com.taskscheduling.app.ui.theme.GlassFilterChip
import com.taskscheduling.app.ui.theme.GlassIndigo
import com.taskscheduling.app.ui.theme.GlassSectionHeader
import com.taskscheduling.app.ui.theme.GlassStatusIndicator
import com.taskscheduling.app.ui.theme.StatusType
import java.util.UUID

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddEditTaskScreen(
    initialDraft: TaskDraft? = null,
    existingTask: Task? = null,
    onSaveTask: (Task) -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateToBuilder: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = AppTheme.colors

    var title by remember { mutableStateOf(existingTask?.title ?: initialDraft?.title ?: "") }
    var description by remember { mutableStateOf(existingTask?.description ?: initialDraft?.description ?: "") }
    var dueDate by remember { mutableStateOf(existingTask?.dueDate ?: initialDraft?.dueDate) }
    var dueTime by remember { mutableStateOf(existingTask?.dueTime ?: initialDraft?.dueTime) }
    var priority by remember { mutableStateOf(existingTask?.priority ?: initialDraft?.priority ?: TaskPriority.MEDIUM) }
    var category by remember { mutableStateOf(existingTask?.category ?: initialDraft?.category ?: TaskCategory.STUDY) }
    var recurrence by remember { mutableStateOf(existingTask?.recurrence ?: initialDraft?.recurrence ?: TaskRecurrence.NONE) }
    var reminderOffsetMinutes by remember { mutableStateOf(existingTask?.reminderOffsetMinutes ?: initialDraft?.reminderOffsetMinutes) }
    var notes by remember { mutableStateOf(existingTask?.notes ?: initialDraft?.notes ?: "") }

    val checklistItems = remember {
        mutableStateListOf<ChecklistItem>().apply {
            addAll(existingTask?.checklist ?: initialDraft?.checklist ?: emptyList())
        }
    }
    var newChecklistText by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(existingTask, initialDraft) {
        if (existingTask != null) {
            title = existingTask.title
            description = existingTask.description
            dueDate = existingTask.dueDate
            dueTime = existingTask.dueTime
            priority = existingTask.priority
            category = existingTask.category
            recurrence = existingTask.recurrence
            reminderOffsetMinutes = existingTask.reminderOffsetMinutes
            notes = existingTask.notes
            checklistItems.clear()
            checklistItems.addAll(existingTask.checklist)
        } else if (initialDraft != null) {
            title = initialDraft.title
            description = initialDraft.description
            dueDate = initialDraft.dueDate
            dueTime = initialDraft.dueTime
            priority = initialDraft.priority
            category = initialDraft.category
            recurrence = initialDraft.recurrence
            reminderOffsetMinutes = initialDraft.reminderOffsetMinutes
            notes = initialDraft.notes
            checklistItems.clear()
            checklistItems.addAll(initialDraft.checklist)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Screen Header and Back Navigation
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
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
                        text = if (existingTask != null) "Edit Task" else "Add New Task",
                        color = colors.textPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Workflow & schedule configuration",
                        color = colors.textSecondary,
                        fontSize = 11.5.sp
                    )
                }
            }

            if (initialDraft != null && initialDraft.title.isNotBlank()) {
                GlassStatusIndicator(
                    label = "VOICE DRAFT",
                    status = StatusType.RUNNING
                )
            }
        }

        // 2. Task Title & Main Details Card
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "TASK TITLE *",
                color = GlassIndigo,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = title,
                onValueChange = {
                    title = it
                    if (it.isNotBlank()) errorMessage = null
                },
                placeholder = {
                    Text(
                        text = "What needs to be done?",
                        fontSize = 15.sp,
                        color = colors.textTertiary
                    )
                },
                textStyle = TextStyle(
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary
                ),
                singleLine = false,
                maxLines = 3,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                isError = errorMessage != null && title.isBlank(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.primary,
                    unfocusedBorderColor = colors.borderSubtle,
                    focusedContainerColor = colors.surface,
                    unfocusedContainerColor = colors.surface,
                    errorBorderColor = AccentRose,
                    focusedTextColor = colors.textPrimary,
                    unfocusedTextColor = colors.textPrimary
                )
            )

            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = errorMessage!!,
                    color = AccentRose,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 3. Description or Instructions
            Text(
                text = "DESCRIPTION (OPTIONAL)",
                color = colors.textSecondary,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                placeholder = {
                    Text(
                        text = "Add details, instructions, or notes...",
                        fontSize = 13.sp,
                        color = colors.textTertiary
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4,
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

        // 4. Date and Time Configuration Card
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            GlassSectionHeader(
                title = "Due Date & Time",
                subtitle = "Set scheduled target date and time"
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "DUE DATE",
                color = colors.textSecondary,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val dateOptions = listOf(
                    null to "No Date",
                    "Today" to "Today",
                    "Tomorrow" to "Tomorrow",
                    "Next Week" to "Next Week",
                    "Monday" to "Monday",
                    "Friday" to "Friday"
                )
                dateOptions.forEach { (dValue, dLabel) ->
                    GlassFilterChip(
                        selected = dueDate == dValue,
                        onClick = { dueDate = dValue },
                        label = dLabel,
                        accentColor = GlassIndigo
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "DUE TIME",
                color = colors.textSecondary,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val timeOptions = listOf(
                    null to "No Time",
                    "09:00" to "09:00 AM",
                    "10:00" to "10:00 AM",
                    "14:00" to "02:00 PM",
                    "18:00" to "06:00 PM",
                    "19:00" to "07:00 PM",
                    "21:00" to "09:00 PM"
                )
                timeOptions.forEach { (tValue, tLabel) ->
                    GlassFilterChip(
                        selected = dueTime == tValue,
                        onClick = { dueTime = tValue },
                        label = tLabel,
                        accentColor = GlassCyan
                    )
                }
            }
        }

        // 5. Reminder Settings Card (Properly spaced, wrapping FlowRow)
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            GlassSectionHeader(
                title = "Reminder Settings",
                subtitle = "Notification trigger timing before due date"
            )

            Spacer(modifier = Modifier.height(10.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val reminderOptions = listOf(
                    null to "No Reminder",
                    0 to "At Due Time",
                    5 to "5m before",
                    10 to "10m before",
                    15 to "15m before",
                    30 to "30m before",
                    60 to "1 hour before"
                )
                reminderOptions.forEach { (remValue, remLabel) ->
                    GlassFilterChip(
                        selected = reminderOffsetMinutes == remValue,
                        onClick = { reminderOffsetMinutes = remValue },
                        label = remLabel,
                        accentColor = AccentAmber,
                        leadingIcon = if (remValue != null) Icons.Default.Notifications else null
                    )
                }
            }
        }

        // 6. Recurrence Settings Card (Distinct, separate section with wrapping)
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            GlassSectionHeader(
                title = "Recurrence & Repeat Cycle",
                subtitle = "Automatic routine repetition schedule"
            )

            Spacer(modifier = Modifier.height(10.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TaskRecurrence.entries.forEach { rec ->
                    GlassFilterChip(
                        selected = recurrence == rec,
                        onClick = { recurrence = rec },
                        label = rec.displayName,
                        accentColor = AccentEmerald,
                        leadingIcon = if (rec != TaskRecurrence.NONE) Icons.Default.Repeat else null
                    )
                }
            }
        }

        // 7. Priority & Category Card
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            GlassSectionHeader(
                title = "Priority & Category",
                subtitle = "Task categorization and urgency level"
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "PRIORITY",
                color = colors.textSecondary,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TaskPriority.entries.forEach { prio ->
                    val isSelected = priority == prio
                    val prioColor = when (prio) {
                        TaskPriority.LOW -> colors.textSecondary
                        TaskPriority.MEDIUM -> GlassCyan
                        TaskPriority.HIGH -> AccentAmber
                        TaskPriority.URGENT -> AccentRose
                    }
                    GlassFilterChip(
                        selected = isSelected,
                        onClick = { priority = prio },
                        label = prio.displayName,
                        accentColor = prioColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "CATEGORY",
                color = colors.textSecondary,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TaskCategory.entries.filterNot { it == TaskCategory.ALL }.forEach { cat ->
                    GlassFilterChip(
                        selected = category == cat,
                        onClick = { category = cat },
                        label = cat.displayName,
                        accentColor = GlassIndigo
                    )
                }
            }
        }

        // 8. Checklist & Subtasks Card
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            GlassSectionHeader(
                title = "Checklist & Subtasks",
                subtitle = "${checklistItems.count { it.isCompleted }}/${checklistItems.size} completed"
            )

            Spacer(modifier = Modifier.height(10.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                checklistItems.forEachIndexed { index, item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.surfaceSecondary)
                            .border(1.dp, colors.borderSubtle, RoundedCornerShape(10.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(if (item.isCompleted) AccentEmerald else Color.Transparent)
                                .border(
                                    1.5.dp,
                                    if (item.isCompleted) AccentEmerald else colors.textTertiary,
                                    CircleShape
                                )
                                .clickable {
                                    checklistItems[index] = item.copy(isCompleted = !item.isCompleted)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (item.isCompleted) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = item.text,
                            color = if (item.isCompleted) colors.textTertiary else colors.textPrimary,
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { checklistItems.removeAt(index) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Remove subtask",
                                tint = colors.textSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                // Add Subtask Row: input field and button aligned without squeezing
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = newChecklistText,
                        onValueChange = { newChecklistText = it },
                        placeholder = {
                            Text(
                                text = "Add subtask...",
                                fontSize = 12.5.sp,
                                color = colors.textTertiary
                            )
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.primary,
                            unfocusedBorderColor = colors.borderSubtle,
                            focusedTextColor = colors.textPrimary,
                            unfocusedTextColor = colors.textPrimary,
                            focusedContainerColor = colors.surface,
                            unfocusedContainerColor = colors.surface
                        )
                    )

                    Glass3DButton(
                        text = "Add",
                        icon = Icons.Default.Add,
                        onClick = {
                            if (newChecklistText.isNotBlank()) {
                                checklistItems.add(ChecklistItem(text = newChecklistText.trim()))
                                newChecklistText = ""
                            }
                        },
                        accentColor = GlassIndigo
                    )
                }
            }
        }

        // 9. Automated Workflow Attachment Card
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            val attachedWorkflow = existingTask?.workflow
            val stepCount = attachedWorkflow?.steps?.size ?: 0
            val workflowName = attachedWorkflow?.name?.ifBlank { null } ?: existingTask?.title?.ifBlank { null }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "AUTOMATED WORKFLOW",
                        color = GlassIndigo,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (stepCount > 0) {
                            "${workflowName ?: "Attached Workflow"} • $stepCount step" + (if (stepCount != 1) "s" else "")
                        } else {
                            "No physical screen actions attached"
                        },
                        color = if (stepCount > 0) AccentEmerald else colors.textSecondary,
                        fontSize = 12.5.sp,
                        fontWeight = if (stepCount > 0) FontWeight.SemiBold else FontWeight.Normal
                    )
                }

                Glass3DButton(
                    text = if (stepCount > 0) "Edit Actions" else "+ Add Actions",
                    icon = Icons.Default.PlayArrow,
                    onClick = {
                        val id = existingTask?.id ?: initialDraft?.id ?: UUID.randomUUID().toString()
                        onNavigateToBuilder?.invoke(id)
                    },
                    accentColor = AccentIndigo
                )
            }

            val steps = attachedWorkflow?.steps
            if (!steps.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                steps.take(4).forEach { step ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.surfaceSecondary)
                            .border(1.dp, colors.borderSubtle, RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "#${step.stepNumber} ${step.label.ifBlank { step.actionType.label }}",
                            color = colors.textPrimary,
                            fontSize = 12.sp
                        )
                        Text(
                            text = if (step.coordinate != null && (step.coordinate.x > 0 || step.coordinate.y > 0)) {
                                "(${step.coordinate.x}, ${step.coordinate.y})"
                            } else {
                                "[${step.actionType.label.uppercase()}]"
                            },
                            color = GlassCyan,
                            fontSize = 10.5.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        // 10. Save and Cancel Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Glass3DButton(
                text = "Cancel",
                onClick = onNavigateBack,
                accentColor = Color(0xFF64748B),
                modifier = Modifier.weight(1f)
            )

            Glass3DButton(
                text = "Save Task",
                icon = Icons.Default.Check,
                onClick = {
                    if (title.isBlank()) {
                        errorMessage = "Task title is required."
                        return@Glass3DButton
                    }
                    val finalTask = Task(
                        id = existingTask?.id ?: initialDraft?.id ?: UUID.randomUUID().toString(),
                        title = title.trim(),
                        description = description.trim(),
                        dueDate = dueDate,
                        dueTime = dueTime,
                        hasDueTime = dueTime != null,
                        reminderOffsetMinutes = reminderOffsetMinutes,
                        hasReminder = reminderOffsetMinutes != null,
                        reminderTimestamp = if (reminderOffsetMinutes != null) existingTask?.reminderTimestamp else null,
                        priority = priority,
                        status = existingTask?.status ?: TaskStatus.TODO,
                        category = category,
                        customCategoryName = existingTask?.customCategoryName ?: initialDraft?.customCategoryName,
                        type = existingTask?.type ?: com.taskscheduling.app.domain.model.TaskType.STANDARD_TASK,
                        iconName = existingTask?.iconName ?: "settings",
                        voiceTriggers = existingTask?.voiceTriggers ?: emptyList(),
                        isFavorite = existingTask?.isFavorite ?: false,
                        isEnabled = existingTask?.isEnabled ?: true,
                        recurrence = recurrence,
                        notes = notes.trim(),
                        checklist = checklistItems.toList(),
                        notificationEnabled = reminderOffsetMinutes != null,
                        workflow = existingTask?.workflow ?: Workflow(id = "wf_empty", name = title.trim()),
                        completedAt = existingTask?.completedAt,
                        createdAt = existingTask?.createdAt ?: System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis()
                    )
                    onSaveTask(finalTask)
                },
                accentColor = AccentEmerald,
                contentColor = Color.White,
                modifier = Modifier.weight(1.3f)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
