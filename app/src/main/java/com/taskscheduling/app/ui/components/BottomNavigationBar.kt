package com.taskscheduling.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.sp
import com.taskscheduling.app.ui.navigation.Screen
import com.taskscheduling.app.ui.theme.BgCardDark
import com.taskscheduling.app.ui.theme.NeonCyan
import com.taskscheduling.app.ui.theme.TextPrimaryDark
import com.taskscheduling.app.ui.theme.TextSecondaryDark

sealed class NavItem(val route: String, val title: String, val icon: ImageVector) {
    object Home : NavItem(Screen.Home.route, "Home", Icons.Default.Home)
    object Tasks : NavItem(Screen.Tasks.route, "Tasks", Icons.Default.List)
    object Builder : NavItem(Screen.Builder.route, "Builder", Icons.Default.Build)
    object Activity : NavItem(Screen.Activity.route, "Activity", Icons.Default.History)
    object Settings : NavItem(Screen.Settings.route, "Settings", Icons.Default.Settings)
}

@Composable
fun AppBottomNavigationBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val items = listOf(
        NavItem.Home,
        NavItem.Tasks,
        NavItem.Builder,
        NavItem.Activity,
        NavItem.Settings
    )

    NavigationBar(
        modifier = modifier,
        containerColor = BgCardDark
    ) {
        items.forEach { item ->
            val selected = currentRoute == item.route
            NavigationBarItem(
                selected = selected,
                onClick = { onNavigate(item.route) },
                icon = {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.title
                    )
                },
                label = {
                    Text(
                        text = item.title,
                        fontSize = 11.sp
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = NeonCyan,
                    selectedTextColor = NeonCyan,
                    indicatorColor = NeonCyan.copy(alpha = 0.15f),
                    unselectedIconColor = TextSecondaryDark,
                    unselectedTextColor = TextSecondaryDark
                )
            )
        }
    }
}
