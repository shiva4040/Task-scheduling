package com.taskscheduling.app.feature.tasks

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.EventNote
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.taskscheduling.app.domain.model.Task
import com.taskscheduling.app.domain.model.TaskCategory
import com.taskscheduling.app.domain.model.TaskPriority
import com.taskscheduling.app.ui.components.TaskCard
import com.taskscheduling.app.ui.theme.AppTheme
import com.taskscheduling.app.ui.theme.Glass3DButton
import com.taskscheduling.app.ui.theme.GlassAmber
import com.taskscheduling.app.ui.theme.GlassCyan
import com.taskscheduling.app.ui.theme.GlassEmerald
import com.taskscheduling.app.ui.theme.GlassEmptyState
import com.taskscheduling.app.ui.theme.GlassFilterChip
import com.taskscheduling.app.ui.theme.GlassIndigo
import com.taskscheduling.app.ui.theme.GlassRose

@Composable
fun TasksScreen(
    viewModel: TasksViewModel,
    onNavigateToExecution: (String) -> Unit,
    onNavigateToBuilder: (String?) -> Unit,
    onNavigateToCreateTask: () -> Unit,
    onNavigateToEditTask: (String) -> Unit = {},
    onNavigateBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val filterState by viewModel.filterState.collectAsState()
    val tasks by viewModel.filteredTasks.collectAsState()
    val todayCount by viewModel.todayCount.collectAsState()
    val upcomingCount by viewModel.upcomingCount.collectAsState()
    val overdueCount by viewModel.overdueCount.collectAsState()
    val completedCount by viewModel.completedCount.collectAsState()

    var taskPendingRun by remember { mutableStateOf<Task?>(null) }
    val colors = AppTheme.colors

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToCreateTask,
                containerColor = GlassIndigo,
                contentColor = Color.White,
                shape = CircleShape
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add New Workflow")
            }
        },
        containerColor = colors.background
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(14.dp))

            // Screen Header matching concept board: "Workflows" + action icons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (onNavigateBack != null) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = colors.textPrimary
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Column {
                        Text(
                            text = "Workflows",
                            color = colors.textPrimary,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.5).sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${tasks.size} automated routines available",
                            color = colors.textSecondary,
                            fontSize = 12.5.sp
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(
                        onClick = onNavigateToCreateTask,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(colors.surfaceSecondary)
                            .border(1.dp, colors.borderSubtle, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "New Workflow",
                            tint = GlassIndigo,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Search Bar: "Search workflows..."
            OutlinedTextField(
                value = filterState.searchQuery,
                onValueChange = { viewModel.onSearchChange(it) },
                placeholder = {
                    Text(
                        "Search workflows...",
                        fontSize = 13.sp,
                        color = colors.textTertiary
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = colors.textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                },
                trailingIcon = {
                    if (filterState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onSearchChange("") }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear search",
                                tint = colors.textSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = GlassIndigo,
                    unfocusedBorderColor = colors.borderSubtle,
                    focusedTextColor = colors.textPrimary,
                    unfocusedTextColor = colors.textPrimary,
                    focusedContainerColor = colors.surface,
                    unfocusedContainerColor = colors.surface
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Primary Tabs: All, Personal, System (Concept Board style) + TaskFilterTab
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Favorites Quick Toggle Chip
                GlassFilterChip(
                    selected = filterState.showOnlyFavorites,
                    label = "Favorites",
                    icon = Icons.Default.Favorite,
                    activeColor = GlassRose,
                    onClick = { viewModel.toggleFavoritesOnly() }
                )

                TaskFilterTab.values().forEach { tab ->
                    val isSelected = filterState.selectedTab == tab && !filterState.showOnlyFavorites
                    val badgeCount = when (tab) {
                        TaskFilterTab.TODAY -> todayCount
                        TaskFilterTab.UPCOMING -> upcomingCount
                        TaskFilterTab.OVERDUE -> overdueCount
                        TaskFilterTab.COMPLETED -> completedCount
                        TaskFilterTab.ALL -> null
                    }
                    val tabColor = when (tab) {
                        TaskFilterTab.OVERDUE -> if (overdueCount > 0) GlassRose else GlassCyan
                        TaskFilterTab.TODAY -> GlassCyan
                        TaskFilterTab.COMPLETED -> GlassEmerald
                        TaskFilterTab.UPCOMING -> GlassAmber
                        TaskFilterTab.ALL -> GlassIndigo
                    }

                    GlassFilterChip(
                        selected = isSelected,
                        label = tab.displayName,
                        badgeCount = badgeCount,
                        activeColor = tabColor,
                        onClick = {
                            if (filterState.showOnlyFavorites) viewModel.toggleFavoritesOnly()
                            viewModel.onTabSelect(tab)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Category & Priority Filters
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Priority filter toggle
                TaskPriority.values().forEach { prio ->
                    val isSelected = filterState.selectedPriority == prio
                    val prioColor = when (prio) {
                        TaskPriority.URGENT -> GlassRose
                        TaskPriority.HIGH -> GlassAmber
                        TaskPriority.MEDIUM -> GlassCyan
                        TaskPriority.LOW -> colors.textTertiary
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) prioColor.copy(alpha = 0.15f) else colors.surfaceSecondary)
                            .border(1.dp, if (isSelected) prioColor else colors.borderSubtle, RoundedCornerShape(8.dp))
                            .clickable {
                                viewModel.onPrioritySelect(if (isSelected) null else prio)
                            }
                            .padding(horizontal = 9.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = "${prio.displayName} Priority",
                            color = if (isSelected) prioColor else colors.textSecondary,
                            fontSize = 10.5.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }

                // Category chips
                TaskCategory.values().forEach { cat ->
                    val isSelected = filterState.selectedCategory == cat
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isSelected) GlassIndigo.copy(alpha = 0.15f) else colors.surfaceSecondary
                            )
                            .border(
                                1.dp,
                                if (isSelected) GlassIndigo else colors.borderSubtle,
                                RoundedCornerShape(8.dp)
                            )
                            .clickable { viewModel.onCategorySelect(cat) }
                            .padding(horizontal = 9.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = cat.displayName,
                            color = if (isSelected) GlassIndigo else colors.textSecondary,
                            fontSize = 10.5.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Active Search Header
            if (filterState.searchQuery.isNotBlank()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "FOUND ${tasks.size} MATCHING WORKFLOW" + if (tasks.size != 1) "S" else "",
                        color = GlassIndigo,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "CLEAR SEARCH",
                        color = colors.textSecondary,
                        fontSize = 10.5.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { viewModel.onSearchChange("") }
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            // Tasks List / Empty State
            if (tasks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 70.dp),
                    contentAlignment = Alignment.Center
                ) {
                    GlassEmptyState(
                        icon = Icons.AutoMirrored.Filled.EventNote,
                        title = when {
                            filterState.searchQuery.isNotBlank() -> "No Workflows Matched \"${filterState.searchQuery}\""
                            filterState.showOnlyFavorites -> "No Favorite Workflows"
                            filterState.selectedTab == TaskFilterTab.TODAY -> "No Workflows for Today"
                            filterState.selectedTab == TaskFilterTab.OVERDUE -> "No Overdue Workflows"
                            filterState.selectedTab == TaskFilterTab.UPCOMING -> "No Upcoming Workflows"
                            filterState.selectedTab == TaskFilterTab.COMPLETED -> "No Completed Workflows"
                            else -> "No Workflows Found"
                        },
                        description = if (filterState.searchQuery.isNotBlank()) {
                            "No workflows match this query. Clear search to view all scheduled and registered workflows."
                        } else {
                            "Tap + or use voice assistant to create your automation workflow."
                        },
                        actionText = if (filterState.searchQuery.isNotBlank()) "Clear Search" else "Create Workflow",
                        onActionClick = if (filterState.searchQuery.isNotBlank()) {
                            { viewModel.onSearchChange("") }
                        } else {
                            onNavigateToCreateTask
                        }
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(tasks, key = { it.id }) { task ->
                        TaskCard(
                            task = task,
                            onRunTask = { taskPendingRun = task },
                            onEditTask = { onNavigateToBuilder(task.id) },
                            onToggleFavorite = { viewModel.toggleFavorite(task.id) },
                            onToggleCompleted = { viewModel.toggleCompleted(task.id) },
                            onRescheduleTask = { d, t -> viewModel.rescheduleTask(task.id, d, t) },
                            onDeleteTask = { viewModel.deleteTask(task.id) },
                            onDuplicateTask = { viewModel.duplicateTask(task.id) },
                            onRenameTask = { newName -> viewModel.saveTask(task.copy(title = newName)) }
                        )
                    }
                }
            }

            // Run confirmation dialog
            if (taskPendingRun != null) {
                val pending = taskPendingRun!!
                AlertDialog(
                    onDismissRequest = { taskPendingRun = null },
                    containerColor = colors.surface,
                    title = {
                        Text(
                            "Run ${pending.title}?",
                            color = colors.textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                    },
                    text = {
                        Column {
                            val stepCount = pending.workflow.steps.size
                            Text(
                                text = if (stepCount == 1) "1 automated step" else "$stepCount automated steps",
                                color = GlassIndigo,
                                fontSize = 12.5.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "The saved coordinate actions will execute on this device.\n\n" +
                                        "Please verify the device is unlocked and displaying the expected screen.",
                                color = colors.textSecondary,
                                fontSize = 13.sp,
                                lineHeight = 18.sp
                            )
                        }
                    },
                    confirmButton = {
                        Glass3DButton(
                            text = "Execute",
                            icon = Icons.Default.PlayArrow,
                            onClick = {
                                val targetId = pending.id
                                taskPendingRun = null
                                onNavigateToExecution(targetId)
                            },
                            minHeight = 40.dp
                        )
                    },
                    dismissButton = {
                        TextButton(onClick = { taskPendingRun = null }) {
                            Text("Cancel", color = colors.textSecondary, fontWeight = FontWeight.SemiBold)
                        }
                    }
                )
            }
        }
    }
}
