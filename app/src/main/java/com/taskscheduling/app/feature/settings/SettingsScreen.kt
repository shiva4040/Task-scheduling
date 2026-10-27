package com.taskscheduling.app.feature.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.taskscheduling.app.accessibility.TaskSchedulingAccessibilityService
import com.taskscheduling.app.data.repository.SettingsRepository
import com.taskscheduling.app.domain.model.AppSettings
import com.taskscheduling.app.domain.model.ThemeMode
import com.taskscheduling.app.ui.theme.AccentEmerald
import com.taskscheduling.app.ui.theme.AppTheme
import com.taskscheduling.app.ui.theme.Glass3DButton
import com.taskscheduling.app.ui.theme.GlassCard
import com.taskscheduling.app.ui.theme.GlassCyan
import com.taskscheduling.app.ui.theme.GlassEmerald
import com.taskscheduling.app.ui.theme.GlassFilterChip
import com.taskscheduling.app.ui.theme.GlassIndigo
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
    onNavigateToOnboarding: () -> Unit = {},
    onNavigateToDiagnosticCenter: () -> Unit = {},
    onNavigateToBotSettings: () -> Unit = {},
    onNavigateToProfile: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val settings by viewModel.appSettings.collectAsState()
    val context = LocalContext.current
    val colors = AppTheme.colors
    val isAccessibilityActive = TaskSchedulingAccessibilityService.instance != null

    // Overflow menu & dialog states
    var showOptionsMenu by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }

    // Expanded section toggles
    var isAppearanceExpanded by remember { mutableStateOf(true) }
    var isGeneralExpanded by remember { mutableStateOf(false) }
    var isVoiceExpanded by remember { mutableStateOf(false) }
    var isAccessibilityExpanded by remember { mutableStateOf(false) }
    var isAssistantExpanded by remember { mutableStateOf(false) }
    var isStorageExpanded by remember { mutableStateOf(false) }
    var isAboutExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Header with working three-dot overflow menu
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Settings",
                    color = colors.textPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "App preferences & configuration",
                    color = colors.textSecondary,
                    fontSize = 12.5.sp
                )
            }

            Box {
                IconButton(
                    onClick = { showOptionsMenu = true },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Settings Menu Options",
                        tint = colors.textPrimary
                    )
                }

                DropdownMenu(
                    expanded = showOptionsMenu,
                    onDismissRequest = { showOptionsMenu = false },
                    modifier = Modifier.background(colors.surface)
                ) {
                    DropdownMenuItem(
                        text = { Text("Reset All Settings", color = colors.textPrimary) },
                        leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null, tint = colors.textSecondary) },
                        onClick = {
                            showOptionsMenu = false
                            showResetDialog = true
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Accessibility Setup", color = colors.textPrimary) },
                        leadingIcon = { Icon(Icons.Default.Accessibility, contentDescription = null, tint = colors.textSecondary) },
                        onClick = {
                            showOptionsMenu = false
                            try {
                                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                context.startActivity(intent)
                            } catch (_: Exception) {}
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Voice Diagnostics", color = colors.textPrimary) },
                        leadingIcon = { Icon(Icons.Default.Mic, contentDescription = null, tint = colors.textSecondary) },
                        onClick = {
                            showOptionsMenu = false
                            onNavigateToDiagnosticCenter()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("About Task Scheduling", color = colors.textPrimary) },
                        leadingIcon = { Icon(Icons.Default.Info, contentDescription = null, tint = colors.textSecondary) },
                        onClick = {
                            showOptionsMenu = false
                            showAboutDialog = true
                        }
                    )
                }
            }
        }

        // 2. User Profile Card -> opens Profile screen
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onNavigateToProfile)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(GlassIndigo, GlassCyan))),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column {
                        Text(
                            text = settings.userName.ifBlank { "Shiva" },
                            color = colors.textPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Tap to view profile & local account",
                            color = GlassIndigo,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                    contentDescription = null,
                    tint = colors.textTertiary,
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        // 3. Appearance & Theme (Deeply Configurable)
        SettingsCollapsibleSection(
            icon = Icons.Default.Palette,
            title = "Appearance & Theme",
            subtitle = "Theme (${settings.themeMode.name.lowercase().replaceFirstChar { it.uppercase() }}), accent & display",
            isExpanded = isAppearanceExpanded,
            onToggle = { isAppearanceExpanded = !isAppearanceExpanded }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // Theme selector
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Application Theme:",
                        color = colors.textSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            ThemeMode.LIGHT to "Light (White-First)",
                            ThemeMode.DARK to "Dark (Deep Charcoal)",
                            ThemeMode.SYSTEM to "System Default"
                        ).forEach { (mode, label) ->
                            GlassFilterChip(
                                selected = settings.themeMode == mode,
                                onClick = { viewModel.updateSettings(settings.copy(themeMode = mode)) },
                                label = label,
                                accentColor = GlassIndigo
                            )
                        }
                    }
                }

                // Accent Color Selector
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Accent Color:",
                        color = colors.textSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("Indigo", "Cyan", "Emerald", "Rose", "Amber").forEach { accent ->
                            GlassFilterChip(
                                selected = settings.accentColorName.equals(accent, ignoreCase = true),
                                onClick = { viewModel.updateSettings(settings.copy(accentColorName = accent)) },
                                label = accent,
                                accentColor = when (accent) {
                                    "Cyan" -> GlassCyan
                                    "Emerald" -> GlassEmerald
                                    "Rose" -> Color(0xFFF43F5E)
                                    "Amber" -> Color(0xFFF59E0B)
                                    else -> GlassIndigo
                                }
                            )
                        }
                    }
                }

                // Font Scale Selector
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Display Font Scale:",
                        color = colors.textSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            1.0f to "Standard (1.0x)",
                            1.15f to "Large (1.15x)"
                        ).forEach { (scale, label) ->
                            GlassFilterChip(
                                selected = kotlin.math.abs(settings.fontSizeScale - scale) < 0.05f,
                                onClick = { viewModel.updateSettings(settings.copy(fontSizeScale = scale)) },
                                label = label,
                                accentColor = GlassIndigo
                            )
                        }
                    }
                }

                // Reduced Motion Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            text = "Reduced Motion",
                            color = colors.textPrimary,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Minimizes animations across tabs, dialogs, and orb",
                            color = colors.textSecondary,
                            fontSize = 11.5.sp
                        )
                    }
                    Switch(
                        checked = settings.reducedMotion,
                        onCheckedChange = { viewModel.updateSettings(settings.copy(reducedMotion = it)) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = GlassEmerald
                        )
                    )
                }

                // Reset Appearance Button
                Glass3DButton(
                    text = "Reset Appearance Defaults",
                    onClick = {
                        viewModel.updateSettings(
                            settings.copy(
                                themeMode = ThemeMode.LIGHT,
                                accentColorName = "Indigo",
                                fontSizeScale = 1.0f,
                                reducedMotion = false
                            )
                        )
                    },
                    isSecondary = true,
                    modifier = Modifier.fillMaxWidth(),
                    minHeight = 36.dp
                )
            }
        }

        // 4. General App Preferences
        SettingsCollapsibleSection(
            icon = Icons.Default.Settings,
            title = "General",
            subtitle = "Speed (${settings.executionSpeed}x), confirmations & safety",
            isExpanded = isGeneralExpanded,
            onToggle = { isGeneralExpanded = !isGeneralExpanded }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // Execution speed multiplier
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Execution Speed Multiplier",
                            color = colors.textPrimary,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "${settings.executionSpeed}x",
                            color = GlassIndigo,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Slider(
                        value = settings.executionSpeed,
                        onValueChange = { viewModel.updateSettings(settings.copy(executionSpeed = it)) },
                        valueRange = 0.5f..2.5f,
                        steps = 3,
                        colors = SliderDefaults.colors(
                            thumbColor = GlassIndigo,
                            activeTrackColor = GlassIndigo
                        )
                    )
                }

                // Stop on first error
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            "Stop On First Error",
                            color = colors.textPrimary,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "Aborts workflow if a gesture fails or target is missing",
                            color = colors.textSecondary,
                            fontSize = 11.5.sp
                        )
                    }
                    Switch(
                        checked = settings.stopOnError,
                        onCheckedChange = { viewModel.updateSettings(settings.copy(stopOnError = it)) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = GlassEmerald
                        )
                    )
                }

                // Confirm before execution
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            "Confirm Before Execution",
                            color = colors.textPrimary,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "Prompts confirmation dialog before running gestures",
                            color = colors.textSecondary,
                            fontSize = 11.5.sp
                        )
                    }
                    Switch(
                        checked = settings.confirmBeforeExecution,
                        onCheckedChange = { viewModel.updateSettings(settings.copy(confirmBeforeExecution = it)) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = GlassEmerald
                        )
                    )
                }

                // Haptic feedback
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            "Haptic Touch Feedback",
                            color = colors.textPrimary,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "Subtle tactile response on taps and speech recognition triggers",
                            color = colors.textSecondary,
                            fontSize = 11.5.sp
                        )
                    }
                    Switch(
                        checked = settings.hapticFeedbackEnabled,
                        onCheckedChange = { viewModel.updateSettings(settings.copy(hapticFeedbackEnabled = it)) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = GlassEmerald
                        )
                    )
                }

                // Auto-clear completed tasks
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            "Auto-Archive Completed Tasks",
                            color = colors.textPrimary,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "Automatically moves finished tasks to history log",
                            color = colors.textSecondary,
                            fontSize = 11.5.sp
                        )
                    }
                    Switch(
                        checked = settings.autoClearCompletedTasks,
                        onCheckedChange = { viewModel.updateSettings(settings.copy(autoClearCompletedTasks = it)) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = GlassEmerald
                        )
                    )
                }
            }
        }

        // 5. Voice & Speech Recognition
        SettingsCollapsibleSection(
            icon = Icons.Default.Mic,
            title = "Voice & Speech",
            subtitle = "${settings.speechEngine.replace("_", " ")} (100% Offline)",
            isExpanded = isVoiceExpanded,
            onToggle = { isVoiceExpanded = !isVoiceExpanded }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Select Speech Recognition Engine:",
                    color = colors.textSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        "WHISPER_LARGE_V3_TURBO" to "Whisper.cpp (Optimal)",
                        "SHERPA_ONNX" to "Sherpa-ONNX (Fallback)",
                        "VOSK" to "Vosk (Legacy)"
                    ).forEach { (engineKey, label) ->
                        GlassFilterChip(
                            selected = settings.speechEngine == engineKey,
                            onClick = { viewModel.updateSettings(settings.copy(speechEngine = engineKey)) },
                            label = label,
                            accentColor = GlassIndigo
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(GlassEmerald.copy(alpha = 0.08f))
                        .border(1.dp, GlassEmerald.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    Text(
                        text = "✓ 100% On-Device Offline Processing\nZero audio frames sent to external cloud servers. Calibrated for Samsung Galaxy Tab A9 hardware acceleration.",
                        color = colors.textPrimary,
                        fontSize = 11.5.sp,
                        lineHeight = 16.sp
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Glass3DButton(
                        text = "Test Recognition",
                        onClick = onNavigateToDiagnosticCenter,
                        accentColor = GlassIndigo,
                        modifier = Modifier.weight(1f),
                        minHeight = 36.dp
                    )
                    Glass3DButton(
                        text = "Bot Settings",
                        onClick = onNavigateToBotSettings,
                        isSecondary = true,
                        modifier = Modifier.weight(1f),
                        minHeight = 36.dp
                    )
                }
            }
        }

        // 6. Automation & Accessibility
        SettingsCollapsibleSection(
            icon = Icons.Default.Accessibility,
            title = "Automation",
            subtitle = "Delay (${settings.stepDelayMs}ms), timeouts & service status",
            isExpanded = isAccessibilityExpanded,
            onToggle = { isAccessibilityExpanded = !isAccessibilityExpanded }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Step Delay
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Inter-Step Gesture Delay:",
                        color = colors.textSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(200L to "Fast (200ms)", 500L to "Normal (500ms)", 1000L to "Slow (1.0s)").forEach { (delay, label) ->
                            GlassFilterChip(
                                selected = settings.stepDelayMs == delay,
                                onClick = { viewModel.updateSettings(settings.copy(stepDelayMs = delay)) },
                                label = label,
                                accentColor = GlassIndigo
                            )
                        }
                    }
                }

                // Step Timeout
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Maximum Step Timeout:",
                        color = colors.textSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(10 to "10s", 15 to "15s (Default)", 30 to "30s").forEach { (timeout, label) ->
                            GlassFilterChip(
                                selected = settings.stepTimeoutSeconds == timeout,
                                onClick = { viewModel.updateSettings(settings.copy(stepTimeoutSeconds = timeout)) },
                                label = label,
                                accentColor = GlassIndigo
                            )
                        }
                    }
                }

                // Service Status
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Accessibility Service",
                            color = colors.textPrimary,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (isAccessibilityActive) "Active & dispatching touchless gestures" else "Service disabled. Tap Enable to open Android settings.",
                            color = if (isAccessibilityActive) GlassEmerald else colors.textSecondary,
                            fontSize = 11.5.sp
                        )
                    }

                    Glass3DButton(
                        text = if (isAccessibilityActive) "Active ✓" else "Enable",
                        onClick = {
                            try {
                                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                context.startActivity(intent)
                            } catch (_: Exception) {}
                        },
                        accentColor = if (isAccessibilityActive) GlassEmerald else GlassIndigo,
                        minHeight = 36.dp
                    )
                }
            }
        }

        // 7. Floating Assistant (Orb)
        SettingsCollapsibleSection(
            icon = Icons.Default.SmartToy,
            title = "Floating Assistant",
            subtitle = "Orb overlay (${if (settings.overlayOrbEnabled) "Enabled" else "Disabled"}), size & opacity",
            isExpanded = isAssistantExpanded,
            onToggle = { isAssistantExpanded = !isAssistantExpanded }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            "Enable Floating Orb Overlay",
                            color = colors.textPrimary,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "Renders draggable glass orb over all Android applications",
                            color = colors.textSecondary,
                            fontSize = 11.5.sp
                        )
                    }
                    Switch(
                        checked = settings.overlayOrbEnabled,
                        onCheckedChange = { viewModel.updateSettings(settings.copy(overlayOrbEnabled = it)) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = GlassEmerald
                        )
                    )
                }

                // Orb Size
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Floating Orb Size:",
                        color = colors.textSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(48 to "Compact (48dp)", 56 to "Standard (56dp)", 64 to "Large (64dp)").forEach { (size, label) ->
                            GlassFilterChip(
                                selected = settings.floatingOrbSizeDp == size,
                                onClick = { viewModel.updateSettings(settings.copy(floatingOrbSizeDp = size)) },
                                label = label,
                                accentColor = GlassIndigo
                            )
                        }
                    }
                }

                // Orb Opacity
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Orb Glass Opacity",
                            color = colors.textPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            "${(settings.floatingOrbOpacity * 100).toInt()}%",
                            color = GlassIndigo,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Slider(
                        value = settings.floatingOrbOpacity,
                        onValueChange = { viewModel.updateSettings(settings.copy(floatingOrbOpacity = it)) },
                        valueRange = 0.5f..1.0f,
                        steps = 4,
                        colors = SliderDefaults.colors(
                            thumbColor = GlassIndigo,
                            activeTrackColor = GlassIndigo
                        )
                    )
                }
            }
        }

        // 8. Storage & Persistence
        SettingsCollapsibleSection(
            icon = Icons.Default.Storage,
            title = "Workflow & Storage",
            subtitle = "Local SQLite & JSON repository persistence",
            isExpanded = isStorageExpanded,
            onToggle = { isStorageExpanded = !isStorageExpanded }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Local Storage Persistence",
                    color = colors.textPrimary,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "• Workflows saved to local JSON & room database\n• Instant recovery & persistent settings across device reboots\n• 100% on-device private data isolation without network dependencies",
                    color = colors.textSecondary,
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp
                )

                Glass3DButton(
                    text = "Reset All App Preferences",
                    onClick = { showResetDialog = true },
                    isSecondary = true,
                    modifier = Modifier.fillMaxWidth(),
                    minHeight = 36.dp
                )
            }
        }

        // 9. About & Diagnostics
        SettingsCollapsibleSection(
            icon = Icons.Default.Info,
            title = "Diagnostics & About",
            subtitle = "Task Scheduling 1.0 • Samsung Galaxy Tab A9 Native",
            isExpanded = isAboutExpanded,
            onToggle = { isAboutExpanded = !isAboutExpanded }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Task Scheduling 1.0 • Touchless Phone Automation",
                    color = colors.textPrimary,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Task Scheduling 1.0 (Official Release)\nNative Kotlin + Jetpack Compose + Whisper.cpp + AccessibilityService\nOffline-first touchless workflow execution",
                    color = colors.textSecondary,
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp
                )

                Glass3DButton(
                    text = "Open Diagnostics Center",
                    onClick = onNavigateToDiagnosticCenter,
                    accentColor = GlassIndigo,
                    modifier = Modifier.fillMaxWidth(),
                    minHeight = 36.dp
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }

    // Reset confirmation dialog
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset All Settings?", color = colors.textPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "This will restore all appearance, speed, and automation preferences to their factory defaults. Your saved workflows and tasks will not be deleted.",
                    color = colors.textSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.updateSettings(AppSettings())
                        showResetDialog = false
                    }
                ) {
                    Text("Reset", color = GlassIndigo, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Cancel", color = colors.textSecondary)
                }
            },
            containerColor = colors.surface
        )
    }

    // About Dialog
    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = { Text("About Task Scheduling", color = colors.textPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Task Scheduling 1.0", fontWeight = FontWeight.Bold, color = colors.textPrimary)
                    Text("Version: 1.0 (Official Release)", color = colors.textSecondary, fontSize = 12.sp)
                    Text("Architecture: 100% Offline Native Android", color = colors.textSecondary, fontSize = 12.sp)
                    Text("Speech Engine: Whisper.cpp / Sherpa-ONNX / Vosk", color = colors.textSecondary, fontSize = 12.sp)
                    Text("Automation: Android AccessibilityService & Gesture Dispatch", color = colors.textSecondary, fontSize = 12.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) {
                    Text("Close", color = GlassIndigo, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = colors.surface
        )
    }
}

@Composable
private fun SettingsCollapsibleSection(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    val colors = AppTheme.colors

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surface)
            .border(1.dp, colors.borderSubtle, RoundedCornerShape(16.dp))
            .clickable(onClick = onToggle)
            .padding(14.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(GlassIndigo.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = GlassIndigo,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = title,
                            color = colors.textPrimary,
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(1.dp))
                        Text(
                            text = subtitle,
                            color = colors.textSecondary,
                            fontSize = 11.5.sp
                        )
                    }
                }

                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.AutoMirrored.Filled.ArrowForwardIos,
                    contentDescription = null,
                    tint = colors.textTertiary,
                    modifier = Modifier.size(if (isExpanded) 20.dp else 14.dp)
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(colors.borderSubtle)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    content()
                }
            }
        }
    }
}
