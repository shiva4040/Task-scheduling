package com.taskscheduling.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.taskscheduling.app.automation.mock.MockAutomationEngine
import com.taskscheduling.app.data.repository.ActivityRepositoryImpl
import com.taskscheduling.app.data.repository.FamilyRepositoryImpl
import com.taskscheduling.app.data.repository.SettingsRepositoryImpl
import com.taskscheduling.app.data.repository.TaskPersistenceRepository
import com.taskscheduling.app.data.repository.TaskRepositoryImpl
import com.taskscheduling.app.ui.components.AppBottomNavigationBar
import com.taskscheduling.app.ui.navigation.AppNavHost
import com.taskscheduling.app.ui.navigation.NavigationTarget
import com.taskscheduling.app.ui.navigation.Screen
import com.taskscheduling.app.ui.theme.BgDeepDark
import com.taskscheduling.app.ui.theme.TaskSchedulingTheme

@Composable
fun TaskSchedulingApp(
    navigationTargetState: MutableState<NavigationTarget?>? = null
) {
    // Top-Level Repositories & Engine instances
    val context = androidx.compose.ui.platform.LocalContext.current
    val persistenceRepository = remember { com.taskscheduling.app.data.repository.WorkflowPersistenceRepository(context) }
    val taskPersistenceRepository = remember { TaskPersistenceRepository(context, persistenceRepository) }
    val taskRepository = remember { TaskRepositoryImpl(persistenceRepository, taskPersistenceRepository) }
    val settingsRepository = remember { SettingsRepositoryImpl(context) }
    val activityRepository = remember { ActivityRepositoryImpl() }
    val familyRepository = remember { FamilyRepositoryImpl() }
    val realAutomationEngine = remember { com.taskscheduling.app.automation.AndroidAccessibilityAutomationEngine(context) }
    val automationEngine = remember { MockAutomationEngine() }

    val appSettings by settingsRepository.getAppSettings().collectAsState(initial = com.taskscheduling.app.domain.model.AppSettings())

    TaskSchedulingTheme(themeMode = appSettings.themeMode) {
        val navController = rememberNavController()
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route

        // Determine if bottom navigation should be visible
        val showBottomBar = currentRoute in listOf(
            Screen.Home.route,
            Screen.Tasks.route,
            Screen.Builder.route,
            Screen.Activity.route,
            Screen.Settings.route
        ) || currentRoute == Screen.Builder.createRoute(null) || currentRoute?.startsWith("builder") == true

        Scaffold(
            bottomBar = {
                if (showBottomBar) {
                    AppBottomNavigationBar(
                        currentRoute = currentRoute,
                        onNavigate = { route ->
                            if (currentRoute != route) {
                                navController.navigate(route) {
                                    popUpTo(Screen.Home.route) {
                                        saveState = (route != Screen.Home.route)
                                    }
                                    launchSingleTop = true
                                    restoreState = (route != Screen.Home.route)
                                }
                            }
                        }
                    )
                }
            },
            containerColor = androidx.compose.material3.MaterialTheme.colorScheme.background
        ) { innerPadding ->
            AppNavHost(
                navController = navController,
                taskRepository = taskRepository,
                settingsRepository = settingsRepository,
                activityRepository = activityRepository,
                familyRepository = familyRepository,
                automationEngine = automationEngine,
                realAutomationEngine = realAutomationEngine,
                persistenceRepository = persistenceRepository,
                navigationTargetState = navigationTargetState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
        }
    }
}
