package com.taskscheduling.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.taskscheduling.app.domain.model.Task
import com.taskscheduling.app.domain.model.TaskPriority
import com.taskscheduling.app.feature.tasks.TasksViewModel
import com.taskscheduling.app.ui.theme.AppTheme
import com.taskscheduling.app.ui.theme.Glass3DButton
import com.taskscheduling.app.ui.theme.GlassAmber
import com.taskscheduling.app.ui.theme.GlassCard
import com.taskscheduling.app.ui.theme.GlassCyan
import com.taskscheduling.app.ui.theme.GlassEmerald
import com.taskscheduling.app.ui.theme.GlassIndigo
import com.taskscheduling.app.ui.theme.GlassRimBorder
import com.taskscheduling.app.ui.theme.GlassRose
import com.taskscheduling.app.ui.theme.GlassTextPrimary
import com.taskscheduling.app.ui.theme.GlassTextSecondary
import com.taskscheduling.app.ui.theme.GlassTextTertiary
import com.taskscheduling.app.ui.theme.StatusError

@Composable
fun TaskCard(
    task: Task,
    onRunTask: () -> Unit,
    onEditTask: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDeleteTask: () -> Unit,
    onDuplicateTask: () -> Unit,
    onToggleCompleted: (() -> Unit)? = null,
    onRescheduleTask: ((String?, String?) -> Unit)? = null,
    onRenameTask: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val themeColors = AppTheme.colors
    var showMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var renameInput by remember { mutableStateOf(task.title) }

    val isOverdue = TasksViewModel.isTaskOverdue(task)
    val isCompleted = task.isCompleted

    // Delete confirmation dialog
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            containerColor = themeColors.cardBackground,
            title = {
                Text("Delete Workflow Task?", color = themeColors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            },
            text = {
                Text(
                    text = "Are you sure you want to permanently delete \"${task.title}\"? This action cannot be undone.",
                    color = themeColors.textSecondary,
                    fontSize = 13.5.sp,
                    lineHeight = 19.sp
                )
            },
            confirmButton = {
                Glass3DButton(
                    text = "Delete",
                    onClick = {
                        showDeleteConfirmDialog = false
                        onDeleteTask()
                    },
                    isDestructive = true,
                    minHeight = 40.dp
                )
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel", color = themeColors.textSecondary, fontWeight = FontWeight.SemiBold)
                }
            }
        )
    }

    // Rename Dialog
    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            containerColor = themeColors.cardBackground,
            title = {
                Text("Rename Task", color = themeColors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            },
            text = {
                Column {
                    Text("Enter new task title:", color = themeColors.textSecondary, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = renameInput,
                        onValueChange = { renameInput = it },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = themeColors.primary,
                            unfocusedBorderColor = themeColors.cardBorder,
                            focusedTextColor = themeColors.textPrimary,
                            unfocusedTextColor = themeColors.textPrimary,
                            focusedContainerColor = themeColors.inputBackground,
                            unfocusedContainerColor = themeColors.inputBackground
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                Glass3DButton(
                    text = "Save",
                    onClick = {
                        showRenameDialog = false
                        onRenameTask?.invoke(renameInput)
                    },
                    minHeight = 40.dp
                )
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Cancel", color = themeColors.textSecondary, fontWeight = FontWeight.SemiBold)
                }
            }
        )
    }

    val cardBorder = if (isOverdue && !isCompleted) {
        Brush.verticalGradient(listOf(StatusError.copy(alpha = 0.8f), StatusError.copy(alpha = 0.3f)))
    } else {
        themeColors.cardRimBrush
    }

    GlassCard(
        modifier = modifier.fillMaxWidth(),
        borderBrush = cardBorder,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp),
        onClick = onEditTask
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            // Task Completion Checkbox
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(
                        if (isCompleted) GlassEmerald else Color.Transparent
                    )
                    .border(
                        1.8.dp,
                        if (isCompleted) GlassEmerald else Color(0x6694A3B8),
                        CircleShape
                    )
                    .clickable { onToggleCompleted?.invoke() },
                contentAlignment = Alignment.Center
            ) {
                if (isCompleted) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Completed",
                        tint = Color.White,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                // Header Tags Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Category Chip
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0x266366F1))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = task.category.displayName.uppercase(),
                                color = GlassCyan,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Priority Chip
                        val priorityColor = when (task.priority) {
                            TaskPriority.URGENT -> GlassRose
                            TaskPriority.HIGH -> GlassAmber
                            TaskPriority.MEDIUM -> GlassCyan
                            TaskPriority.LOW -> GlassTextTertiary
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(priorityColor.copy(alpha = 0.15f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = task.priority.displayName.uppercase(),
                                color = priorityColor,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Reminder Icon
                        if (task.hasReminder) {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = "Reminder Active",
                                tint = GlassAmber,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }

                    // Action menu & favorite
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onToggleFavorite,
                            modifier = Modifier.size(26.dp)
                        ) {
                            Icon(
                                imageVector = if (task.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = if (task.isFavorite) "Un-favorite" else "Favorite",
                                tint = if (task.isFavorite) GlassRose else GlassTextTertiary,
                                modifier = Modifier.size(17.dp)
                            )
                        }

                        Box {
                            IconButton(
                                onClick = { showMenu = true },
                                modifier = Modifier.size(26.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "Options Menu",
                                    tint = GlassTextSecondary,
                                    modifier = Modifier.size(17.dp)
                                )
                            }

                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(if (isCompleted) "Mark as Active" else "Mark Complete") },
                                    onClick = {
                                        showMenu = false
                                        onToggleCompleted?.invoke()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Edit Workflow & Steps") },
                                    onClick = { showMenu = false; onEditTask() }
                                )
                                DropdownMenuItem(
                                    text = { Text("Reschedule: Tomorrow") },
                                    onClick = {
                                        showMenu = false
                                        onRescheduleTask?.invoke("Tomorrow", task.dueTime)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Reschedule: Next Week") },
                                    onClick = {
                                        showMenu = false
                                        onRescheduleTask?.invoke("Next Week", task.dueTime)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Rename") },
                                    onClick = {
                                        showMenu = false
                                        renameInput = task.title
                                        showRenameDialog = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Duplicate") },
                                    onClick = { showMenu = false; onDuplicateTask() }
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete", color = GlassRose) },
                                    onClick = {
                                        showMenu = false
                                        showDeleteConfirmDialog = true
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Title
                Text(
                    text = task.title,
                    color = if (isCompleted) themeColors.textTertiary else themeColors.textPrimary,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Bold,
                    textDecoration = if (isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (task.description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = task.description,
                        color = themeColors.textSecondary,
                        fontSize = 12.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Metadata Footer: Actions count, Due date, Run Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val stepsCount = task.workflow.steps.size
                        Text(
                            text = if (stepsCount == 1) "1 step" else "$stepsCount steps",
                            color = themeColors.textTertiary,
                            fontSize = 11.5.sp,
                            fontFamily = FontFamily.Monospace
                        )

                        if (!task.dueDate.isNullOrBlank()) {
                            Text(
                                text = "•  ${task.dueDate}${if (!task.dueTime.isNullOrBlank()) " ${task.dueTime}" else ""}",
                                color = if (isOverdue && !isCompleted) StatusError else themeColors.textSecondary,
                                fontSize = 11.5.sp,
                                fontWeight = if (isOverdue && !isCompleted) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }

                    // Tactile Run Button
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(themeColors.primaryButtonBrush)
                            .border(1.dp, themeColors.cardBorderSubtle, RoundedCornerShape(10.dp))
                            .clickable(onClick = onRunTask)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Run Workflow",
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Run",
                                color = Color.White,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
