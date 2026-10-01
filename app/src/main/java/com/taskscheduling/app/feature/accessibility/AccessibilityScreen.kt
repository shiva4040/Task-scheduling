package com.taskscheduling.app.feature.accessibility

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.taskscheduling.app.domain.model.AccessibilitySettings
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

class AccessibilityViewModel(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val settings: StateFlow<AccessibilitySettings> = settingsRepository.getAccessibilitySettings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AccessibilitySettings())

    fun updateSettings(newSettings: AccessibilitySettings) {
        viewModelScope.launch {
            settingsRepository.updateAccessibilitySettings(newSettings)
        }
    }
}

@Composable
fun AccessibilityScreen(
    viewModel: AccessibilityViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val settings by viewModel.settings.collectAsState()

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
                Text(text = "Accessibility Center", color = TextPrimaryDark, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(text = "Phone simplification & touch indicators", color = NeonCyan, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Section 1: Phone Simplification
        Text("PHONE SIMPLIFICATION", color = NeonCyan, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SettingToggleItem(
                title = "Large Text Mode",
                description = "Increase font legibility across all phone previews and workflows",
                checked = settings.largeText,
                onCheckedChange = { viewModel.updateSettings(settings.copy(largeText = it)) }
            )
            SettingToggleItem(
                title = "Large Controls",
                description = "Enlarge buttons and touch targets for easy hand interaction",
                checked = settings.largeControls,
                onCheckedChange = { viewModel.updateSettings(settings.copy(largeControls = it)) }
            )
            SettingToggleItem(
                title = "High Contrast Mode",
                description = "Enhance borders and background contrast for visual impairment",
                checked = settings.highContrast,
                onCheckedChange = { viewModel.updateSettings(settings.copy(highContrast = it)) }
            )
            SettingToggleItem(
                title = "Reduce Motion",
                description = "Minimize animations, ripples, and visual motion effects",
                checked = settings.reduceMotion,
                onCheckedChange = { viewModel.updateSettings(settings.copy(reduceMotion = it)) }
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Section 2: Voice & Spoken Feedback
        Text("VOICE & FEEDBACK", color = NeonCyan, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SettingToggleItem(
                title = "Voice Commands",
                description = "Allow voice recognition to trigger predefined workflows",
                checked = settings.voiceFeedback,
                onCheckedChange = { viewModel.updateSettings(settings.copy(voiceFeedback = it)) }
            )
            SettingToggleItem(
                title = "Spoken Step Commentary",
                description = "Announce each automation step aloud as it executes",
                checked = settings.voiceConfirmation,
                onCheckedChange = { viewModel.updateSettings(settings.copy(voiceConfirmation = it)) }
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Section 3: Visual Automation Engine
        Text("AUTOMATION VISUALIZATION", color = NeonCyan, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SettingToggleItem(
                title = "Show Touches",
                description = "Draw radiating touch ripple at exact screen coordinates",
                checked = settings.showTouches,
                onCheckedChange = { viewModel.updateSettings(settings.copy(showTouches = it)) }
            )
            SettingToggleItem(
                title = "Show Coordinates",
                description = "Display exact (X, Y) pixel badges next to touch events",
                checked = settings.showCoordinates,
                onCheckedChange = { viewModel.updateSettings(settings.copy(showCoordinates = it)) }
            )
            SettingToggleItem(
                title = "Show Execution Vector Path",
                description = "Animate directional arrows for swipe and drag gestures",
                checked = settings.showExecutionPath,
                onCheckedChange = { viewModel.updateSettings(settings.copy(showExecutionPath = it)) }
            )
            SettingToggleItem(
                title = "Step-by-Step Debug Mode",
                description = "Pause execution at every step until user approves next action",
                checked = settings.stepByStepMode,
                onCheckedChange = { viewModel.updateSettings(settings.copy(stepByStepMode = it)) }
            )
        }
    }
}

@Composable
fun SettingToggleItem(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(BgCardDark, RoundedCornerShape(14.dp))
            .border(1.dp, BorderSubtleDark, RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(text = title, color = TextPrimaryDark, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(text = description, color = TextSecondaryDark, fontSize = 11.sp)
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = NeonCyan
                )
            )
        }
    }
}
