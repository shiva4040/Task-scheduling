package com.taskscheduling.app.feature.family

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.taskscheduling.app.data.repository.FamilyRepository
import com.taskscheduling.app.domain.model.ChildProfile
import com.taskscheduling.app.ui.theme.AccentAmber
import com.taskscheduling.app.ui.theme.AccentEmerald
import com.taskscheduling.app.ui.theme.AccentIndigo
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
import kotlinx.coroutines.launch

class FamilyViewModel(
    private val familyRepository: FamilyRepository
) : ViewModel() {

    val childProfile: StateFlow<ChildProfile> = familyRepository.getChildProfile()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ChildProfile("", "Alex", 12))

    fun updateProfile(newProfile: ChildProfile) {
        viewModelScope.launch {
            familyRepository.updateChildProfile(newProfile)
        }
    }
}

@Composable
fun FamilyScreen(
    viewModel: FamilyViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val profile by viewModel.childProfile.collectAsState()

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
                Text(text = "Family & Child Controls", color = TextPrimaryDark, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(text = "Parental profiles & study schedules", color = NeonCyan, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Simulation Disclaimer Notice (Important technical constraint from Section 24)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(AccentAmber.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                .border(1.dp, AccentAmber.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                .padding(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Default.Info, contentDescription = null, tint = AccentAmber, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "V1 Frontend Simulation: Real Android system-level restrictions will use official Android parental APIs in future versions.",
                    color = AccentAmber,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Child Profile Header Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(BgCardDark, RoundedCornerShape(16.dp))
                .border(1.dp, BorderSubtleDark, RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .background(AccentIndigo.copy(alpha = 0.3f), CircleShape)
                        .border(2.dp, NeonCyan, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = profile.avatarEmoji, fontSize = 26.sp)
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column {
                    Text(text = "${profile.name} (Age ${profile.age})", color = TextPrimaryDark, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(text = "Active Family Profile • Standard Protection", color = AccentEmerald, fontSize = 11.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Screen Time Controls
        Text("TIME LIMITS & RESTRICTIONS", color = NeonCyan, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(BgCardDark, RoundedCornerShape(16.dp))
                .border(1.dp, BorderSubtleDark, RoundedCornerShape(16.dp))
                .padding(14.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // Screen time slider
                Column {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Daily Screen Time", color = TextPrimaryDark, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text("${profile.screenTimeLimitMinutes / 60}h ${profile.screenTimeLimitMinutes % 60}m", color = NeonCyan, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                    }
                    Slider(
                        value = profile.screenTimeLimitMinutes.toFloat(),
                        onValueChange = { viewModel.updateProfile(profile.copy(screenTimeLimitMinutes = it.toInt())) },
                        valueRange = 30f..360f,
                        colors = SliderDefaults.colors(thumbColor = NeonCyan, activeTrackColor = NeonCyan)
                    )
                }

                // Gaming time slider
                Column {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Gaming Time Limit", color = TextPrimaryDark, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text("${profile.gamingTimeLimitMinutes} mins", color = AccentIndigo, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                    }
                    Slider(
                        value = profile.gamingTimeLimitMinutes.toFloat(),
                        onValueChange = { viewModel.updateProfile(profile.copy(gamingTimeLimitMinutes = it.toInt())) },
                        valueRange = 0f..180f,
                        colors = SliderDefaults.colors(thumbColor = AccentIndigo, activeTrackColor = AccentIndigo)
                    )
                }

                // Study mode switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Study Mode Override", color = TextPrimaryDark, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text("Blocks all distracting apps immediately", color = TextSecondaryDark, fontSize = 11.sp)
                    }
                    Switch(
                        checked = profile.studyModeActive,
                        onCheckedChange = { viewModel.updateProfile(profile.copy(studyModeActive = it)) },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = AccentEmerald)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Schedule Windows
        Text("SCHEDULED PROFILE WINDOWS", color = NeonCyan, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            profile.schedules.forEach { sched ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(BgCardDark, RoundedCornerShape(12.dp))
                        .border(1.dp, BorderSubtleDark, RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Schedule, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(text = sched.name, color = TextPrimaryDark, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Text(text = "${sched.startTime} → ${sched.endTime}", color = Color(0xFF64748B), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Allowed vs Blocked Apps
        Text("APP RESTRICTIONS", color = NeonCyan, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // Allowed Apps Box
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(BgCardDark, RoundedCornerShape(14.dp))
                    .border(1.dp, AccentEmerald.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                    .padding(12.dp)
            ) {
                Column {
                    Text("Allowed Apps (${profile.allowedApps.size})", color = AccentEmerald, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    profile.allowedApps.forEach { app ->
                        Text("• $app", color = TextPrimaryDark, fontSize = 11.sp)
                    }
                }
            }

            // Blocked Apps Box
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(BgCardDark, RoundedCornerShape(14.dp))
                    .border(1.dp, AccentRose.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                    .padding(12.dp)
            ) {
                Column {
                    Text("Blocked Apps (${profile.blockedApps.size})", color = AccentRose, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    profile.blockedApps.forEach { app ->
                        Text("• $app", color = TextSecondaryDark, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}
