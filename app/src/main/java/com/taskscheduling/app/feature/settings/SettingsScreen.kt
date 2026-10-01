package com.taskscheduling.app.feature.settings

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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
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
import com.taskscheduling.app.data.repository.SettingsRepository
import com.taskscheduling.app.domain.model.AppSettings
import com.taskscheduling.app.domain.model.ThemeMode
import com.taskscheduling.app.ui.theme.AccentEmerald
import com.taskscheduling.app.ui.theme.AccentIndigo
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

class SettingsViewModel(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val appSettings: StateFlow<AppSettings> = settingsRepository.getAppSettings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    fun updateSettings(newSettings: AppSettings) {
        viewModelScope.launch {
            settingsRepository.updateAppSettings(newSettings)
        }
    }
}

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateToOnboarding: () -> Unit,
    modifier: Modifier = Modifier
) {
    val settings by viewModel.appSettings.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BgDeepDark)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Bar
        Column {
            Text(text = "Settings", color = TextPrimaryDark, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(text = "App preferences & automation engine config", color = NeonCyan, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        }

        // Section: Account Profile
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(BgCardDark, RoundedCornerShape(16.dp))
                .border(1.dp, BorderSubtleDark, RoundedCornerShape(16.dp))
                .padding(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(AccentIndigo, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = Icons.Default.Person, contentDescription = null, tint = Color.White)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text("Shiva", color = TextPrimaryDark, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("shiva@taskscheduling.local • Premium", color = TextSecondaryDark, fontSize = 12.sp)
                }
            }
        }

        // Section: App Theme Mode
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("THEME MODE", color = NeonCyan, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    ThemeMode.DARK to "Dark Theme",
                    ThemeMode.LIGHT to "Light Theme",
                    ThemeMode.SYSTEM to "System Default"
                ).forEach { (mode, label) ->
                    val isSel = settings.themeMode == mode
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(if (isSel) NeonCyan.copy(alpha = 0.2f) else BgCardDark, RoundedCornerShape(10.dp))
                            .border(1.dp, if (isSel) NeonCyan else BorderSubtleDark, RoundedCornerShape(10.dp))
                            .clickable { viewModel.updateSettings(settings.copy(themeMode = mode)) }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = label, color = if (isSel) NeonCyan else TextSecondaryDark, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // Section: Automation Engine Parameters
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("AUTOMATION ENGINE CONTROLS", color = NeonCyan, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BgCardDark, RoundedCornerShape(16.dp))
                    .border(1.dp, BorderSubtleDark, RoundedCornerShape(16.dp))
                    .padding(14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Column {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Default Speed Multiplier", color = TextPrimaryDark, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text("${settings.executionSpeed}x", color = NeonCyan, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                        }
                        Slider(
                            value = settings.executionSpeed,
                            onValueChange = { viewModel.updateSettings(settings.copy(executionSpeed = it)) },
                            valueRange = 0.5f..2.5f,
                            steps = 3,
                            colors = SliderDefaults.colors(thumbColor = NeonCyan, activeTrackColor = NeonCyan)
                        )
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("Stop On First Error", color = TextPrimaryDark, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text("Aborts workflow if gesture fails", color = TextSecondaryDark, fontSize = 11.sp)
                        }
                        Switch(
                            checked = settings.stopOnError,
                            onCheckedChange = { viewModel.updateSettings(settings.copy(stopOnError = it)) },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = AccentEmerald)
                        )
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("Confirm Before Execution", color = TextPrimaryDark, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text("Prompts approval before running", color = TextSecondaryDark, fontSize = 11.sp)
                        }
                        Switch(
                            checked = settings.confirmBeforeExecution,
                            onCheckedChange = { viewModel.updateSettings(settings.copy(confirmBeforeExecution = it)) },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = AccentEmerald)
                        )
                    }
                }
            }
        }

        // Section: Onboarding & About
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("ABOUT & ONBOARDING", color = NeonCyan, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BgCardDark, RoundedCornerShape(16.dp))
                    .border(1.dp, BorderSubtleDark, RoundedCornerShape(16.dp))
                    .clickable { onNavigateToOnboarding() }
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Replay App Onboarding", color = TextPrimaryDark, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text("View the 5-step introduction to Task Scheduling", color = TextSecondaryDark, fontSize = 11.sp)
                    }
                    Text("VIEW", color = NeonCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BgCardDark, RoundedCornerShape(16.dp))
                    .border(1.dp, BorderSubtleDark, RoundedCornerShape(16.dp))
                    .padding(14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Task Scheduling V1.0 (Native Android)", color = TextPrimaryDark, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text("Kotlin + Jetpack Compose • Simulated Automation Engine", color = Color(0xFF64748B), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    Text("Designed for Accessibility and Hands-Free Interaction.", color = TextSecondaryDark, fontSize = 11.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(30.dp))
    }
}
