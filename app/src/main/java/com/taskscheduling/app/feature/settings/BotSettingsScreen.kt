package com.taskscheduling.app.feature.settings

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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
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
import com.taskscheduling.app.domain.model.BotSize
import com.taskscheduling.app.ui.theme.AccentEmerald
import com.taskscheduling.app.ui.theme.AppTheme
import com.taskscheduling.app.ui.theme.Glass3DButton
import com.taskscheduling.app.ui.theme.GlassCard
import com.taskscheduling.app.ui.theme.GlassCyan
import com.taskscheduling.app.ui.theme.GlassFilterChip
import com.taskscheduling.app.ui.theme.GlassIndigo
import com.taskscheduling.app.ui.theme.GlassSectionHeader
import com.taskscheduling.app.ui.theme.GlassStatusIndicator
import com.taskscheduling.app.ui.theme.NeonCyan
import com.taskscheduling.app.ui.theme.StatusType
import com.taskscheduling.app.voice.LanguageMode

@Composable
fun BotSettingsScreen(
    viewModel: BotSettingsViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val prefs by viewModel.preferences.collectAsState()
    val sysOverview by viewModel.systemOverview.collectAsState()
    val speechModel by viewModel.speechModel.collectAsState()
    val colors = AppTheme.colors

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState())
    ) {
        // App Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = colors.textPrimary
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = "Floating Assistant Settings",
                    color = colors.textPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Appearance, position, voice mode & background persistence",
                    color = GlassIndigo,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Section 1: Appearance
            GlassCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    GlassSectionHeader(
                        title = "Appearance & Geometry",
                        subtitle = "Robot icon size, window opacity and animation smoothness"
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Robot Size
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Robot Icon Size", color = colors.textPrimary, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            BotSize.values().forEach { size ->
                                GlassFilterChip(
                                    selected = prefs.botSize == size,
                                    onClick = { viewModel.setBotSize(size) },
                                    label = "${size.displayName} (${size.sizeDp}dp)",
                                    accentColor = GlassIndigo
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Window Opacity
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Floating Window Opacity", color = colors.textPrimary, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                            Text("${(prefs.windowOpacity * 100).toInt()}%", color = GlassIndigo, fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Slider(
                            value = prefs.windowOpacity,
                            onValueChange = { viewModel.setWindowOpacity(it) },
                            valueRange = 0.3f..1.0f,
                            colors = SliderDefaults.colors(
                                thumbColor = GlassIndigo,
                                activeTrackColor = GlassIndigo,
                                inactiveTrackColor = colors.borderSubtle
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Compact Mode
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 16.dp)
                        ) {
                            Text("Compact Mode", color = colors.textPrimary, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("Reduces padding and card heights in the assistant window", color = colors.textSecondary, fontSize = 11.5.sp, lineHeight = 18.sp)
                        }
                        Switch(
                            checked = prefs.isCompactMode,
                            onCheckedChange = { viewModel.setCompactMode(it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = GlassIndigo, checkedTrackColor = GlassIndigo.copy(alpha = 0.3f))
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Show Robot Icon
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 16.dp)
                        ) {
                            Text("Show Robot Icon", color = colors.textPrimary, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("Displays the robot icon on the floating shortcut", color = colors.textSecondary, fontSize = 11.5.sp, lineHeight = 18.sp)
                        }
                        Switch(
                            checked = prefs.showIcon,
                            onCheckedChange = { viewModel.setShowIcon(it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = GlassIndigo, checkedTrackColor = GlassIndigo.copy(alpha = 0.3f))
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Show Label
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 16.dp)
                        ) {
                            Text("Show Assistant Label", color = colors.textPrimary, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("Displays 'Task Assistant' title inside the opened panel", color = colors.textSecondary, fontSize = 11.5.sp, lineHeight = 18.sp)
                        }
                        Switch(
                            checked = prefs.showLabel,
                            onCheckedChange = { viewModel.setShowLabel(it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = GlassIndigo, checkedTrackColor = GlassIndigo.copy(alpha = 0.3f))
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Smooth Animations
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 16.dp)
                        ) {
                            Text("Enable Animations", color = colors.textPrimary, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("Smooth expand/collapse transitions", color = colors.textSecondary, fontSize = 11.5.sp, lineHeight = 18.sp)
                        }
                        Switch(
                            checked = prefs.enableAnimations,
                            onCheckedChange = { viewModel.setEnableAnimations(it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = GlassIndigo, checkedTrackColor = GlassIndigo.copy(alpha = 0.3f))
                        )
                    }
                }
            }

            // Section 2: Position & Movement
            GlassCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    GlassSectionHeader(
                        title = "Position & Screen Bounds",
                        subtitle = "Clamping, edge snapping, and persistent coordinate memory"
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "You can drag the floating bot anywhere on your screen. The assistant automatically clamps within visible screen bounds, respects system status bars and display cutouts, and remembers its last position across app restarts.",
                        color = colors.textSecondary,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    val posX = prefs.savedPositionX
                    val posY = prefs.savedPositionY
                    val posText = if (posX != null && posY != null && posX >= 0 && posY >= 0) {
                        "Saved: (${posX}px, ${posY}px)"
                    } else {
                        "Default right edge"
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(posText, color = colors.textTertiary, fontSize = 12.sp, fontFamily = FontFamily.Monospace)

                        Glass3DButton(
                            text = "Reset Position",
                            icon = Icons.Default.Refresh,
                            onClick = { viewModel.resetPosition() },
                            accentColor = colors.surfaceSecondary,
                            contentColor = colors.textPrimary
                        )
                    }
                }
            }

            // Section 3: Voice & Interaction Behavior
            GlassCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    GlassSectionHeader(
                        title = "Voice Behavior & Interaction",
                        subtitle = "Recognition language, push-to-talk, and continuous listening"
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Language Mode
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Recognition Language Mode", color = colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GlassFilterChip(
                                selected = true,
                                onClick = { viewModel.setLanguageMode(LanguageMode.ENGLISH) },
                                label = "English (Strictly Enforced)",
                                accentColor = AccentEmerald
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Tap to listen
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 16.dp)
                        ) {
                            Text("Explicit Tap to Listen", color = colors.textPrimary, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("Start microphone listening only on explicit tap (recommended)", color = colors.textSecondary, fontSize = 11.5.sp, lineHeight = 18.sp)
                        }
                        Switch(
                            checked = prefs.tapToListen,
                            onCheckedChange = { viewModel.setTapToListen(it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = AccentEmerald, checkedTrackColor = AccentEmerald.copy(alpha = 0.3f))
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Continuous Listening
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 16.dp)
                        ) {
                            Text("Continuous Listening", color = colors.textPrimary, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("Automatically restarts listening across natural pauses", color = colors.textSecondary, fontSize = 11.5.sp, lineHeight = 18.sp)
                        }
                        Switch(
                            checked = prefs.continuousListening,
                            onCheckedChange = { viewModel.setContinuousListening(it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = GlassIndigo, checkedTrackColor = GlassIndigo.copy(alpha = 0.3f))
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Engine status indicator
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colors.surfaceSecondary, RoundedCornerShape(10.dp))
                            .border(1.dp, colors.borderSubtle, RoundedCornerShape(10.dp))
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Model Readiness: ${if (speechModel.modelFileExists) "READY (${speechModel.modelFilename})" else "MISSING"}",
                            color = colors.textPrimary,
                            fontSize = 11.5.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        GlassStatusIndicator(
                            label = if (sysOverview.micPermissionGranted) "MIC GRANTED" else "MIC DENIED",
                            status = if (sysOverview.micPermissionGranted) StatusType.READY else StatusType.ERROR
                        )
                    }
                }
            }

            // Section 4: Persistent Floating Mode
            GlassCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    GlassSectionHeader(
                        title = "Persistent Floating Assistant",
                        subtitle = "Always-available overlay over other applications"
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 16.dp)
                        ) {
                            Text("Keep Assistant Visible Across Apps", color = colors.textPrimary, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("The floating assistant orb remains visible over other applications when leaving Task Scheduling.", color = colors.textSecondary, fontSize = 11.5.sp, lineHeight = 18.sp)
                        }
                        Switch(
                            checked = prefs.persistentFloatingMode,
                            onCheckedChange = { viewModel.setPersistentFloatingMode(it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = GlassIndigo, checkedTrackColor = GlassIndigo.copy(alpha = 0.3f))
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "• Uses Android's supported Accessibility overlay capability (TYPE_ACCESSIBILITY_OVERLAY).\n• Minimizes into a draggable luminous glass orb that does not block typing or system touches.\n• Tap the orb at any time to open the assistant panel.\n• Closing the assistant safely releases the microphone.\n• Restored automatically when the accessibility service is active.",
                        color = colors.textTertiary,
                        fontSize = 11.sp,
                        lineHeight = 18.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
