package com.taskscheduling.app.feature.profile

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.taskscheduling.app.data.repository.ActivityRepository
import com.taskscheduling.app.data.repository.SettingsRepository
import com.taskscheduling.app.data.repository.TaskRepository
import com.taskscheduling.app.domain.model.AppSettings
import com.taskscheduling.app.domain.model.ThemeMode
import com.taskscheduling.app.ui.theme.AccentEmerald
import com.taskscheduling.app.ui.theme.AccentIndigo
import com.taskscheduling.app.ui.theme.AppTheme
import com.taskscheduling.app.ui.theme.Glass3DButton
import com.taskscheduling.app.ui.theme.GlassCard
import com.taskscheduling.app.ui.theme.GlassCyan
import com.taskscheduling.app.ui.theme.GlassFilterChip
import com.taskscheduling.app.ui.theme.GlassIndigo
import com.taskscheduling.app.ui.theme.GlassSectionHeader
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProfileViewModel(
    private val settingsRepository: SettingsRepository,
    private val taskRepository: TaskRepository,
    private val activityRepository: ActivityRepository
) : ViewModel() {

    val appSettings: StateFlow<AppSettings> = settingsRepository.getAppSettings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    val totalWorkflowsCount: StateFlow<Int> = taskRepository.getTasks()
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val totalActivitiesCount: StateFlow<Int> = activityRepository.getActivities()
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun updateProfile(name: String, email: String) {
        val current = appSettings.value
        viewModelScope.launch {
            settingsRepository.updateAppSettings(
                current.copy(userName = name.trim(), userEmail = email.trim())
            )
        }
    }

    fun updateSettings(newSettings: AppSettings) {
        viewModelScope.launch {
            settingsRepository.updateAppSettings(newSettings)
        }
    }

    fun resetToDefaults() {
        viewModelScope.launch {
            settingsRepository.updateAppSettings(AppSettings())
        }
    }
}

@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = AppTheme.colors
    val settings by viewModel.appSettings.collectAsState()
    val workflowsCount by viewModel.totalWorkflowsCount.collectAsState()
    val activitiesCount by viewModel.totalActivitiesCount.collectAsState()

    var userNameInput by remember(settings.userName) { mutableStateOf(settings.userName) }
    var userEmailInput by remember(settings.userEmail) { mutableStateOf(settings.userEmail) }
    var saveSuccessMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
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
                    text = "Profile & Account",
                    color = colors.textPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Local user identity & automation preferences",
                    color = colors.textSecondary,
                    fontSize = 12.sp
                )
            }
        }

        // Save banner
        if (saveSuccessMessage != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(AccentEmerald.copy(alpha = 0.15f))
                    .border(1.dp, AccentEmerald.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                    .padding(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = AccentEmerald,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = saveSuccessMessage!!,
                        color = AccentEmerald,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // 2. Identity Card
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(GlassIndigo, GlassCyan))),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "User Avatar",
                            tint = Color.White,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = settings.userName,
                            color = colors.textPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = settings.userEmail,
                            color = colors.textSecondary,
                            fontSize = 12.5.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Local Device Administrator",
                            color = GlassIndigo,
                            fontSize = 10.5.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = userNameInput,
                    onValueChange = { userNameInput = it },
                    label = { Text("Display Name", fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = colors.surface,
                        unfocusedContainerColor = colors.surface,
                        focusedBorderColor = colors.primary,
                        unfocusedBorderColor = colors.borderSubtle,
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = userEmailInput,
                    onValueChange = { userEmailInput = it },
                    label = { Text("Local Account Email", fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = colors.surface,
                        unfocusedContainerColor = colors.surface,
                        focusedBorderColor = colors.primary,
                        unfocusedBorderColor = colors.borderSubtle,
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary
                    )
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Glass3DButton(
                        text = "Save Profile",
                        icon = Icons.Default.Check,
                        onClick = {
                            viewModel.updateProfile(userNameInput, userEmailInput)
                            saveSuccessMessage = "Profile updated successfully"
                        },
                        accentColor = AccentEmerald
                    )
                }
            }
        }

        // 3. Automation Usage Statistics
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth()) {
                GlassSectionHeader(
                    title = "Automation Metrics",
                    subtitle = "On-device workflow usage statistics"
                )

                Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.surfaceSecondary)
                        .border(1.dp, colors.borderSubtle, RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Column {
                        Text(
                            text = "$workflowsCount",
                            color = colors.textPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Configured Workflows",
                            color = colors.textSecondary,
                            fontSize = 11.sp
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.surfaceSecondary)
                        .border(1.dp, colors.borderSubtle, RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Column {
                        Text(
                            text = "$activitiesCount",
                            color = colors.textPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Sessions Logged",
                            color = colors.textSecondary,
                            fontSize = 11.sp
                        )
                    }
                }
            }
            }
        }

        // 4. Quick Preferences
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth()) {
                GlassSectionHeader(
                    title = "User Preferences",
                    subtitle = "App interaction and visual theme defaults"
                )

                Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "APPLICATION THEME",
                color = colors.textSecondary,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    ThemeMode.LIGHT to "Light",
                    ThemeMode.DARK to "Dark",
                    ThemeMode.SYSTEM to "System"
                ).forEach { (mode, label) ->
                    GlassFilterChip(
                        selected = settings.themeMode == mode,
                        onClick = { viewModel.updateSettings(settings.copy(themeMode = mode)) },
                        label = label,
                        accentColor = GlassIndigo
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Notifications Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = null,
                        tint = colors.textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text("Task Reminders", color = colors.textPrimary, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                        Text("Alert triggers for scheduled automations", color = colors.textSecondary, fontSize = 11.sp)
                    }
                }
                Switch(
                    checked = settings.notificationsEnabled,
                    onCheckedChange = { viewModel.updateSettings(settings.copy(notificationsEnabled = it)) },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = AccentIndigo)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Haptic Feedback Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Vibration,
                        contentDescription = null,
                        tint = colors.textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text("Haptic Feedback", color = colors.textPrimary, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                        Text("Vibration on button taps & execution", color = colors.textSecondary, fontSize = 11.sp)
                    }
                }
                Switch(
                    checked = settings.hapticFeedbackEnabled,
                    onCheckedChange = { viewModel.updateSettings(settings.copy(hapticFeedbackEnabled = it)) },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = AccentIndigo)
                )
            }
            }
        }

        // 5. Privacy & Storage Notice
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = AccentEmerald,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "100% Offline & Private",
                    color = AccentEmerald,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "All workflows, recordings, voice commands, and settings are processed entirely on-device and stored in local sandboxed storage. No credentials or audio samples leave your tablet.",
                color = colors.textSecondary,
                fontSize = 11.5.sp,
                lineHeight = 16.sp
            )
        }

        // 6. Reset Profile
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Glass3DButton(
                text = "Reset Profile to Defaults",
                icon = Icons.Default.Refresh,
                onClick = {
                    viewModel.resetToDefaults()
                    userNameInput = "Shiva"
                    userEmailInput = "shiva@taskscheduling.local"
                    saveSuccessMessage = "Reset to default profile"
                },
                accentColor = Color(0xFF64748B)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
