package com.taskscheduling.app.feature.home

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.taskscheduling.app.domain.model.Task
import com.taskscheduling.app.ui.components.TaskCard
import com.taskscheduling.app.ui.theme.AccentEmerald
import com.taskscheduling.app.ui.theme.AccentIndigo
import com.taskscheduling.app.ui.theme.AccentViolet
import com.taskscheduling.app.ui.theme.BgCardDark
import com.taskscheduling.app.ui.theme.BorderHighlightDark
import com.taskscheduling.app.ui.theme.BorderSubtleDark
import com.taskscheduling.app.ui.theme.DeepCyan
import com.taskscheduling.app.ui.theme.NeonCyan
import com.taskscheduling.app.ui.theme.TextPrimaryDark
import com.taskscheduling.app.ui.theme.TextSecondaryDark

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToExecution: (String) -> Unit,
    onNavigateToBuilder: (String?) -> Unit,
    onNavigateToVoice: () -> Unit,
    onNavigateToAssistant: () -> Unit,
    onNavigateToAiPreview: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val tasks by viewModel.tasks.collectAsState()
    val activities by viewModel.activities.collectAsState()

    val favoriteTasks = tasks.filter { it.isFavorite }
    val recentTasks = tasks.take(4)

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // 1. Header Greeting
        item {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = uiState.greeting,
                            color = TextPrimaryDark,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Touchless Phone Automation",
                            color = NeonCyan,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    // AI Vision preview teaser button
                    IconButton(
                        onClick = onNavigateToAiPreview,
                        modifier = Modifier
                            .background(AccentIndigo.copy(alpha = 0.2f), CircleShape)
                            .border(1.dp, AccentIndigo.copy(alpha = 0.4f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "AI Vision",
                            tint = AccentViolet
                        )
                    }
                }
            }
        }

        // 2. Main Command Area
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BgCardDark, RoundedCornerShape(20.dp))
                    .border(1.dp, BorderHighlightDark.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                    .padding(16.dp)
            ) {
                Column {
                    Text(
                        text = "What do you want to do?",
                        color = TextPrimaryDark,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = uiState.commandText,
                            onValueChange = { viewModel.onCommandChange(it) },
                            placeholder = { Text("Type command (e.g. 'Change wallpaper')...", fontSize = 12.sp) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeonCyan,
                                unfocusedBorderColor = BorderSubtleDark,
                                focusedTextColor = TextPrimaryDark,
                                unfocusedTextColor = TextPrimaryDark,
                                focusedContainerColor = Color(0x33000000),
                                unfocusedContainerColor = Color(0x33000000)
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))

                        // Mic Button
                        IconButton(
                            onClick = onNavigateToVoice,
                            modifier = Modifier
                                .size(48.dp)
                                .background(DeepCyan, RoundedCornerShape(12.dp))
                        ) {
                            Icon(imageVector = Icons.Default.Mic, contentDescription = "Voice", tint = Color.White)
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        // Send Button
                        IconButton(
                            onClick = {
                                uiState.matchedTask?.let { onNavigateToExecution(it.id) }
                                    ?: onNavigateToAssistant()
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .background(AccentEmerald, RoundedCornerShape(12.dp))
                        ) {
                            Icon(imageVector = Icons.Default.Send, contentDescription = "Execute", tint = Color.White)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Suggested Commands Horizontal Row
                    Text(
                        text = "TRY SAYING:",
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
                        uiState.suggestedCommands.forEach { cmd ->
                            Box(
                                modifier = Modifier
                                    .background(Color(0x2638BDF8), RoundedCornerShape(20.dp))
                                    .border(1.dp, NeonCyan.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                                    .clickable { viewModel.selectSuggestedCommand(cmd) }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(text = "\"$cmd\"", color = NeonCyan, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        // Matched Task Card if found
        uiState.matchedTask?.let { matched ->
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0x3310B981), RoundedCornerShape(16.dp))
                        .border(1.dp, AccentEmerald, RoundedCornerShape(16.dp))
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "TASK MATCHED", color = AccentEmerald, fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                            val count = matched.workflow.steps.size
                            Text(text = if (count == 1) "1 action" else "$count actions", color = TextSecondaryDark, fontSize = 12.sp)
                        }
                        Button(
                            onClick = { onNavigateToExecution(matched.id) },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentEmerald),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Run Now", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // 3. Quick Actions Section
        item {
            Column {
                Text(
                    text = "QUICK ACTIONS",
                    color = TextSecondaryDark,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    QuickActionChip("Wallpaper", Icons.Default.Wallpaper, "task_wallpaper", onNavigateToExecution)
                    QuickActionChip("Dark Mode", Icons.Default.DarkMode, "task_dark_mode", onNavigateToExecution)
                    QuickActionChip("Text Size", Icons.Default.FormatSize, "task_text_size", onNavigateToExecution)
                    QuickActionChip("Wi-Fi", Icons.Default.Wifi, "task_wifi", onNavigateToExecution)
                    QuickActionChip("Bluetooth", Icons.Default.Bluetooth, "task_bluetooth", onNavigateToExecution)
                    QuickActionChip("Volume", Icons.Default.VolumeUp, "task_volume", onNavigateToExecution)
                    QuickActionChip("Camera", Icons.Default.CameraAlt, "task_camera", onNavigateToExecution)
                }
            }
        }

        // 4. Favorites Section
        if (favoriteTasks.isNotEmpty()) {
            item {
                Text(
                    text = "FAVORITE TASKS",
                    color = TextSecondaryDark,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }

            items(favoriteTasks) { task ->
                TaskCard(
                    task = task,
                    onRunTask = { onNavigateToExecution(task.id) },
                    onEditTask = { onNavigateToBuilder(task.id) },
                    onToggleFavorite = { viewModel.toggleFavorite(task.id) },
                    onDeleteTask = {},
                    onDuplicateTask = {}
                )
            }
        }

        // 5. Recent Tasks Section
        item {
            Text(
                text = "RECENT TASKS",
                color = TextSecondaryDark,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }

        items(recentTasks) { task ->
            TaskCard(
                task = task,
                onRunTask = { onNavigateToExecution(task.id) },
                onEditTask = { onNavigateToBuilder(task.id) },
                onToggleFavorite = { viewModel.toggleFavorite(task.id) },
                onDeleteTask = {},
                onDuplicateTask = {}
            )
        }
    }
}

@Composable
private fun QuickActionChip(
    title: String,
    icon: ImageVector,
    taskId: String,
    onRunTask: (String) -> Unit
) {
    Box(
        modifier = Modifier
            .background(BgCardDark, RoundedCornerShape(14.dp))
            .border(1.dp, BorderSubtleDark, RoundedCornerShape(14.dp))
            .clickable { onRunTask(taskId) }
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = icon, contentDescription = title, tint = NeonCyan, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = title, color = TextPrimaryDark, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
