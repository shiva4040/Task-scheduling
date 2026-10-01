package com.taskscheduling.app.feature.activity

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.taskscheduling.app.data.repository.ActivityRepository
import com.taskscheduling.app.domain.model.ActivityRecord
import com.taskscheduling.app.domain.model.ActivityStatus
import com.taskscheduling.app.ui.theme.AccentAmber
import com.taskscheduling.app.ui.theme.AccentEmerald
import com.taskscheduling.app.ui.theme.AccentRose
import com.taskscheduling.app.ui.theme.BgCardDark
import com.taskscheduling.app.ui.theme.BgDeepDark
import com.taskscheduling.app.ui.theme.BorderSubtleDark
import com.taskscheduling.app.ui.theme.NeonCyan
import com.taskscheduling.app.ui.theme.TextPrimaryDark
import com.taskscheduling.app.ui.theme.TextSecondaryDark
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class ActivityViewModel(
    private val activityRepository: ActivityRepository
) : ViewModel() {

    val activities: StateFlow<List<ActivityRecord>> = activityRepository.getActivities()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

@Composable
fun ActivityScreen(
    viewModel: ActivityViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activities by viewModel.activities.collectAsState()
    var selectedRecord by remember { mutableStateOf<ActivityRecord?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BgDeepDark)
            .padding(16.dp)
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
                Text(text = "Execution History", color = TextPrimaryDark, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(text = "${activities.size} automation sessions logged", color = NeonCyan, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (activities.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No Execution History", color = TextSecondaryDark, fontSize = 14.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(activities, key = { it.id }) { record ->
                    ActivityItemCard(record = record, onClick = { selectedRecord = record })
                }
            }
        }

        // Detailed Execution History Dialog
        selectedRecord?.let { record ->
            AlertDialog(
                onDismissRequest = { selectedRecord = null },
                confirmButton = {
                    Button(
                        onClick = { selectedRecord = null },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = BgDeepDark)
                    ) {
                        Text("Close")
                    }
                },
                title = {
                    Text(text = record.taskTitle, color = TextPrimaryDark, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(text = "Time: ${record.timestamp}", color = TextSecondaryDark, fontSize = 12.sp)
                        Text(text = "Duration: ${record.durationText} • ${record.stepCount} steps", color = TextSecondaryDark, fontSize = 12.sp)
                        Text(text = "Status: ${record.status.displayName}", color = AccentEmerald, fontSize = 12.sp, fontWeight = FontWeight.Bold)

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = "STEP LOGS:", color = NeonCyan, fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)

                        if (record.logs.isNotEmpty()) {
                            record.logs.forEach { log ->
                                Text(
                                    text = "${log.timestamp} #${log.stepNumber}: ${log.details}",
                                    color = TextPrimaryDark,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        } else {
                            Text("No granular logs recorded for this session.", color = TextSecondaryDark, fontSize = 11.sp)
                        }
                    }
                },
                containerColor = BgCardDark,
                shape = RoundedCornerShape(18.dp)
            )
        }
    }
}

@Composable
private fun ActivityItemCard(record: ActivityRecord, onClick: () -> Unit) {
    val statusColor = when (record.status) {
        ActivityStatus.COMPLETED -> AccentEmerald
        ActivityStatus.FAILED -> AccentRose
        ActivityStatus.STOPPED -> AccentAmber
        ActivityStatus.PAUSED -> NeonCyan
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(BgCardDark, RoundedCornerShape(14.dp))
            .border(1.dp, BorderSubtleDark, RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(text = record.taskTitle, color = TextPrimaryDark, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${record.timestamp} • ${record.stepCount} Steps (${record.durationText})",
                    color = TextSecondaryDark,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Box(
                modifier = Modifier
                    .background(statusColor.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                    .border(1.dp, statusColor, RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(text = record.status.displayName, color = statusColor, fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            }
        }
    }
}
