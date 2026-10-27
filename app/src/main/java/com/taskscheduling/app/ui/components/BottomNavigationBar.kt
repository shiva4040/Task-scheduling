package com.taskscheduling.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.taskscheduling.app.ui.navigation.Screen
import com.taskscheduling.app.ui.theme.AppTheme

sealed class NavItem(val route: String, val title: String, val icon: ImageVector) {
    object Home : NavItem(Screen.Home.route, "Home", Icons.Default.Home)
    object Workflows : NavItem(Screen.Tasks.route, "Workflows", Icons.AutoMirrored.Filled.List)
    object Actions : NavItem(Screen.Builder.createRoute(null), "Actions", Icons.Default.Build)
    object Activity : NavItem(Screen.Activity.route, "Activity", Icons.Default.History)
    object Settings : NavItem(Screen.Settings.route, "Settings", Icons.Default.Settings)
}

@Composable
fun AppBottomNavigationBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val themeColors = AppTheme.colors

    val items = listOf(
        NavItem.Home,
        NavItem.Workflows,
        NavItem.Actions,
        NavItem.Activity,
        NavItem.Settings
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(themeColors.background)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = if (themeColors.isDark) 8.dp else 4.dp,
                    shape = RoundedCornerShape(22.dp),
                    ambientColor = themeColors.shadowColor,
                    spotColor = themeColors.shadowColor
                )
                .clip(RoundedCornerShape(22.dp))
                .background(themeColors.cardBackground)
                .border(1.dp, themeColors.cardBorder, RoundedCornerShape(22.dp))
                .padding(horizontal = 6.dp, vertical = 6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEach { item ->
                    val selected = currentRoute == item.route || (item is NavItem.Actions && (currentRoute == Screen.Builder.route || currentRoute?.startsWith("builder") == true))
                    val animatedIconTint by animateColorAsState(
                        targetValue = if (selected) themeColors.primary else themeColors.textSecondary,
                        animationSpec = tween(durationMillis = 200),
                        label = "NavIconTint"
                    )

                    val pillBg = if (selected) {
                        if (themeColors.isDark) Color(0x266366F1) else Color(0xFFEEF2FF)
                    } else {
                        Color.Transparent
                    }

                    val pillBorder = if (selected) {
                        if (themeColors.isDark) Color(0x666366F1) else Color(0xFFC7D2FE)
                    } else {
                        Color.Transparent
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(pillBg)
                            .border(1.dp, pillBorder, RoundedCornerShape(16.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { onNavigate(item.route) }
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.title,
                                tint = animatedIconTint,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = item.title,
                                color = if (selected) themeColors.primary else themeColors.textSecondary,
                                fontSize = 10.5.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}
