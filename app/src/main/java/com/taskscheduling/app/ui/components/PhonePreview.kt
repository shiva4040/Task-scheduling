package com.taskscheduling.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DisplaySettings
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.NetworkWifi
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.taskscheduling.app.domain.model.ActionType
import com.taskscheduling.app.domain.model.CoordinateAction
import com.taskscheduling.app.ui.theme.AccentAmber
import com.taskscheduling.app.ui.theme.AccentEmerald
import com.taskscheduling.app.ui.theme.AccentIndigo
import com.taskscheduling.app.ui.theme.AccentRose
import com.taskscheduling.app.ui.theme.AccentViolet
import com.taskscheduling.app.ui.theme.BgCardDark
import com.taskscheduling.app.ui.theme.BorderSubtleDark
import com.taskscheduling.app.ui.theme.DeepCyan
import com.taskscheduling.app.ui.theme.NeonCyan

@Composable
fun PhonePreview(
    currentScreen: String = "Home",
    activeCoordinate: CoordinateAction? = null,
    activeActionType: ActionType? = null,
    showRadarGrid: Boolean = false,
    modifier: Modifier = Modifier,
    onCoordinateTapped: ((CoordinateAction) -> Unit)? = null
) {
    var previewSize by remember { mutableStateOf(IntSize.Zero) }

    // Outer Phone Hardware Chassis Frame
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(9f / 19.5f)
            .shadow(elevation = 16.dp, shape = RoundedCornerShape(38.dp), spotColor = NeonCyan)
            .background(Color(0xFF10141E), RoundedCornerShape(38.dp))
            .border(4.dp, Color(0xFF1E2638), RoundedCornerShape(38.dp))
            .padding(6.dp)
    ) {
        // Phone Screen Surface
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(32.dp))
                .background(Color(0xFF070A11))
                .onSizeChanged { previewSize = it }
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        if (previewSize.width > 0 && previewSize.height > 0) {
                            val virtualX = ((offset.x / previewSize.width) * 1080f).toInt().coerceIn(0, 1080)
                            val virtualY = ((offset.y / previewSize.height) * 2400f).toInt().coerceIn(0, 2400)
                            onCoordinateTapped?.invoke(CoordinateAction(x = virtualX, y = virtualY))
                        }
                    }
                }
        ) {
            // 1. Android Status Bar (Punchhole Camera, Clock, Icons)
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(28.dp)
                        .padding(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "10:45",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    // Punch-hole Camera
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(Color.Black, CircleShape)
                            .border(1.dp, Color(0xFF263045), CircleShape)
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.NetworkWifi,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                        Box(
                            modifier = Modifier
                                .width(16.dp)
                                .height(8.dp)
                                .border(1.dp, Color.White, RoundedCornerShape(2.dp))
                                .padding(1.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.8f)
                                    .fillMaxSize()
                                    .background(AccentEmerald, RoundedCornerShape(1.dp))
                            )
                        }
                    }
                }

                // 2. Active Screen Content
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    when (currentScreen) {
                        "Settings" -> SettingsScreenPreview()
                        "Display" -> DisplayScreenPreview()
                        "Wallpaper" -> WallpaperScreenPreview()
                        "Wifi" -> WifiScreenPreview()
                        "Phone" -> PhoneCallScreenPreview()
                        "QuickSettings" -> QuickSettingsDrawerPreview()
                        else -> HomeScreenPreview()
                    }
                }

                // 3. Android Navigation Bar (Back, Home, Recents)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(32.dp)
                        .background(Color(0xFF090D16)),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Back",
                        tint = Color(0xFF64748B),
                        modifier = Modifier
                            .size(16.dp)
                    )
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(Color(0xFF94A3B8), CircleShape)
                    )
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .border(1.5.dp, Color(0xFF64748B), RoundedCornerShape(2.dp))
                    )
                }
            }

            // 4. Coordinate Overlay & Touch Indicators Layer
            CoordinateOverlay(
                activeCoordinate = activeCoordinate,
                actionType = activeActionType,
                showRadarGrid = showRadarGrid
            )
        }
    }
}

// ---------------- SIMULATED SCREENS ---------------- //

@Composable
private fun HomeScreenPreview() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0B192E), Color(0xFF18153A), Color(0xFF280F33))
                )
            )
            .padding(12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Widget Area
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "10:45",
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Light,
                    color = Color.White
                )
                Text(
                    text = "Wednesday, Sep 30 • 74°F ☀️",
                    fontSize = 11.sp,
                    color = Color(0xFFCBD5E1)
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Search Bar Widget
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0x33FFFFFF), RoundedCornerShape(20.dp))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Search apps & web...",
                        color = Color(0x99FFFFFF),
                        fontSize = 11.sp
                    )
                }
            }

            // App Icons Grid
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    AppIcon("Phone", Icons.Default.Call, AccentEmerald)
                    AppIcon("Messages", Icons.Default.Message, NeonCyan)
                    AppIcon("Camera", Icons.Default.CameraAlt, AccentRose)
                    AppIcon("Settings", Icons.Default.Settings, Color(0xFF64748B))
                }

                // Quick Dock
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0x22FFFFFF), RoundedCornerShape(22.dp))
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    Text("📞", fontSize = 16.sp)
                    Text("💬", fontSize = 16.sp)
                    Text("🌐", fontSize = 16.sp)
                    Text("⚙️", fontSize = 16.sp)
                }
            }
        }
    }
}

@Composable
private fun AppIcon(name: String, icon: ImageVector, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .background(color, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = name,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.height(3.dp))
        Text(text = name, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SettingsScreenPreview() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF090D16))
            .padding(12.dp)
    ) {
        Text(
            text = "Settings",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        SettingsItemRow("Network & internet", "Wi-Fi (Home_5G)", Icons.Default.Wifi, DeepCyan)
        SettingsItemRow("Display", "Dark theme, Font: Normal", Icons.Default.DisplaySettings, AccentAmber)
        SettingsItemRow("Wallpaper & style", "Sunset Gold preset", Icons.Default.Wallpaper, AccentViolet)
        SettingsItemRow("Sound & vibration", "Volume, Haptics", Icons.Default.VolumeUp, AccentRose)
        SettingsItemRow("Accessibility", "Touchless, Large controls", Icons.Default.FormatSize, AccentEmerald)
    }
}

@Composable
private fun SettingsItemRow(title: String, subtitle: String, icon: ImageVector, tint: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(tint, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(text = subtitle, color = Color(0xFF94A3B8), fontSize = 10.sp)
        }
        Icon(imageVector = Icons.Default.ChevronRight, contentDescription = null, tint = Color(0xFF475569), modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun DisplayScreenPreview() {
    var fontSizeSlider by remember { mutableFloatStateOf(0.7f) }
    var darkThemeChecked by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF090D16))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Display & Text", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)

        // Live Preview Box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(BgCardDark, RoundedCornerShape(12.dp))
                .padding(14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Make the text on my phone bigger for easier reading.",
                color = Color.White,
                fontSize = (12 + (fontSizeSlider * 8)).sp,
                textAlign = TextAlign.Center
            )
        }

        // Font Slider Control
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(BgCardDark, RoundedCornerShape(12.dp))
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Font Size", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Text("Large (125%)", color = NeonCyan, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
            Slider(
                value = fontSizeSlider,
                onValueChange = { fontSizeSlider = it },
                colors = SliderDefaults.colors(thumbColor = NeonCyan, activeTrackColor = NeonCyan)
            )
        }

        // Dark Theme Switch
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(BgCardDark, RoundedCornerShape(12.dp))
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Dark Theme", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Text("Reduces eye strain", color = Color(0xFF94A3B8), fontSize = 10.sp)
            }
            Switch(
                checked = darkThemeChecked,
                onCheckedChange = { darkThemeChecked = it },
                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = NeonCyan)
            )
        }
    }
}

@Composable
private fun WallpaperScreenPreview() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF090D16))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Wallpaper & Style", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)

        // Mock Phone Screen Preview
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
                .background(
                    Brush.verticalGradient(listOf(Color(0xFF4C0519), Color(0xFF78350F))),
                    RoundedCornerShape(14.dp)
                )
                .padding(8.dp),
            contentAlignment = Alignment.BottomStart
        ) {
            Text(
                text = "Sunset Gold Applied",
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .background(Color(0x99000000), RoundedCornerShape(8.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }

        Text("Select Palette Preset", color = Color(0xFF94A3B8), fontSize = 11.sp)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(60.dp)
                    .background(Brush.linearGradient(listOf(Color(0xFF062B3A), Color(0xFF064E3B))), RoundedCornerShape(8.dp))
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(60.dp)
                    .border(2.dp, NeonCyan, RoundedCornerShape(8.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF4C0519), Color(0xFF78350F))), RoundedCornerShape(8.dp))
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(60.dp)
                    .background(Brush.linearGradient(listOf(Color(0xFF030712), Color(0xFF4C1D95))), RoundedCornerShape(8.dp))
            )
        }
    }
}

@Composable
private fun WifiScreenPreview() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF090D16))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Wi-Fi & Internet", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(BgCardDark, RoundedCornerShape(10.dp))
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = Icons.Default.Wifi, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Home_HighSpeed_5G", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Text("Connected • Excellent Signal", color = AccentEmerald, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun PhoneCallScreenPreview() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF090D16))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .background(AccentRose, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text("👩", fontSize = 28.sp)
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text("Calling Mom...", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text("00:06 • Cellular Connected", color = AccentEmerald, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun QuickSettingsDrawerPreview() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xF0090D16))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Quick Settings", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuickTile("Wi-Fi", "Home_5G", Icons.Default.Wifi, true, Modifier.weight(1f))
            QuickTile("Dark", "On", Icons.Default.DarkMode, true, Modifier.weight(1f))
            QuickTile("Flashlight", "Off", Icons.Default.FlashlightOn, false, Modifier.weight(1f))
        }
    }
}

@Composable
private fun QuickTile(name: String, status: String, icon: ImageVector, active: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(if (active) DeepCyan else BgCardDark, RoundedCornerShape(10.dp))
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = name, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
        Text(text = status, color = Color(0xCCFFFFFF), fontSize = 9.sp)
    }
}
