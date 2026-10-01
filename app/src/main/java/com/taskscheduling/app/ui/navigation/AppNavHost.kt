package com.taskscheduling.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.taskscheduling.app.automation.mock.MockAutomationEngine
import com.taskscheduling.app.automation.mock.MockVoiceEngine
import com.taskscheduling.app.data.repository.ActivityRepositoryImpl
import com.taskscheduling.app.data.repository.FamilyRepositoryImpl
import com.taskscheduling.app.data.repository.SettingsRepositoryImpl
import com.taskscheduling.app.data.repository.TaskRepositoryImpl
import com.taskscheduling.app.feature.accessibility.AccessibilityScreen
import com.taskscheduling.app.feature.accessibility.AccessibilityViewModel
import com.taskscheduling.app.feature.activity.ActivityScreen
import com.taskscheduling.app.feature.activity.ActivityViewModel
import com.taskscheduling.app.feature.aipreview.AiPreviewScreen
import com.taskscheduling.app.feature.assistant.AssistantScreen
import com.taskscheduling.app.feature.assistant.AssistantViewModel
import com.taskscheduling.app.feature.builder.BuilderViewModel
import com.taskscheduling.app.feature.builder.WorkflowBuilderScreen
import com.taskscheduling.app.feature.execution.ExecutionScreen
import com.taskscheduling.app.feature.execution.ExecutionViewModel
import com.taskscheduling.app.feature.family.FamilyScreen
import com.taskscheduling.app.feature.family.FamilyViewModel
import com.taskscheduling.app.feature.home.HomeScreen
import com.taskscheduling.app.feature.home.HomeViewModel
import com.taskscheduling.app.feature.onboarding.OnboardingScreen
import com.taskscheduling.app.feature.scheduler.SchedulerScreen
import com.taskscheduling.app.feature.scheduler.SchedulerViewModel
import com.taskscheduling.app.feature.settings.SettingsScreen
import com.taskscheduling.app.feature.settings.SettingsViewModel
import com.taskscheduling.app.feature.tasks.CreateTaskFlowScreen
import com.taskscheduling.app.feature.tasks.TasksScreen
import com.taskscheduling.app.feature.tasks.TasksViewModel
import com.taskscheduling.app.feature.usecases.UseCasesScreen
import com.taskscheduling.app.feature.voice.VoiceScreen
import com.taskscheduling.app.feature.voice.VoiceViewModel
import com.taskscheduling.app.automation.AndroidAccessibilityAutomationEngine
import com.taskscheduling.app.data.repository.WorkflowPersistenceRepository
import com.taskscheduling.app.feature.wallpaper.WallpaperWorkflowScreen
import com.taskscheduling.app.feature.wallpaper.WallpaperWorkflowViewModel
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun AppNavHost(
    navController: NavHostController,
    taskRepository: TaskRepositoryImpl,
    settingsRepository: SettingsRepositoryImpl,
    activityRepository: ActivityRepositoryImpl,
    familyRepository: FamilyRepositoryImpl,
    automationEngine: MockAutomationEngine,
    realAutomationEngine: AndroidAccessibilityAutomationEngine,
    persistenceRepository: WorkflowPersistenceRepository,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    // Shared ViewModels
    val homeViewModel = remember { HomeViewModel(taskRepository, activityRepository) }
    val tasksViewModel = remember { TasksViewModel(taskRepository) }
    val builderViewModel = remember { BuilderViewModel(taskRepository) }
    val executionViewModel = remember { ExecutionViewModel(automationEngine, taskRepository, activityRepository) }
    val voiceViewModel = remember {
        val voiceEngine = MockVoiceEngine { taskRepository.findTaskByVoiceIntent(it) }
        VoiceViewModel(voiceEngine, taskRepository)
    }
    val assistantViewModel = remember { AssistantViewModel(taskRepository) }
    val accessibilityViewModel = remember { AccessibilityViewModel(settingsRepository) }
    val familyViewModel = remember { FamilyViewModel(familyRepository) }
    val activityViewModel = remember { ActivityViewModel(activityRepository) }
    val schedulerViewModel = remember { SchedulerViewModel(familyRepository) }
    val settingsViewModel = remember { SettingsViewModel(settingsRepository) }

    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        modifier = modifier
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                viewModel = homeViewModel,
                onNavigateToExecution = { taskId ->
                    navController.navigate(Screen.Execution.createRoute(taskId))
                },
                onNavigateToBuilder = { taskId ->
                    navController.navigate(Screen.Builder.createRoute(taskId))
                },
                onNavigateToVoice = {
                    navController.navigate(Screen.Voice.route)
                },
                onNavigateToAssistant = {
                    navController.navigate(Screen.Assistant.route)
                },
                onNavigateToAiPreview = {
                    navController.navigate(Screen.AiPreview.route)
                }
            )
        }

        composable(Screen.Tasks.route) {
            TasksScreen(
                viewModel = tasksViewModel,
                onNavigateToExecution = { taskId ->
                    navController.navigate(Screen.Execution.createRoute(taskId))
                },
                onNavigateToBuilder = { taskId ->
                    navController.navigate(Screen.Builder.createRoute(taskId))
                },
                onNavigateToCreateFlow = {
                    navController.navigate(Screen.CreateTask.route)
                }
            )
        }

        composable(Screen.CreateTask.route) {
            CreateTaskFlowScreen(
                onSaveTask = { task ->
                    CoroutineScope(Dispatchers.Main).launch {
                        taskRepository.saveTask(task)
                        navController.navigate(Screen.Builder.createRoute(task.id)) {
                            popUpTo(Screen.Tasks.route)
                        }
                    }
                },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.Builder.route,
            arguments = listOf(navArgument("taskId") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            })
        ) { backStackEntry ->
            val taskId = backStackEntry.arguments?.getString("taskId")
            WorkflowBuilderScreen(
                viewModel = builderViewModel,
                taskId = taskId,
                onNavigateToExecution = { tid ->
                    navController.navigate(Screen.Execution.createRoute(tid))
                },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.Execution.route,
            arguments = listOf(navArgument("taskId") { type = NavType.StringType })
        ) { backStackEntry ->
            val taskId = backStackEntry.arguments?.getString("taskId") ?: "task_wallpaper"
            if (taskId == "task_wallpaper") {
                val wallpaperViewModel = remember {
                    WallpaperWorkflowViewModel(
                        taskRepository = taskRepository,
                        persistenceRepository = persistenceRepository,
                        automationEngine = realAutomationEngine,
                        context = context
                    )
                }
                WallpaperWorkflowScreen(
                    viewModel = wallpaperViewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            } else {
                ExecutionScreen(
                    viewModel = executionViewModel,
                    taskId = taskId,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }

        composable(Screen.WallpaperWorkflow.route) {
            val wallpaperViewModel = remember {
                WallpaperWorkflowViewModel(
                    taskRepository = taskRepository,
                    persistenceRepository = persistenceRepository,
                    automationEngine = realAutomationEngine,
                    context = context
                )
            }
            WallpaperWorkflowScreen(
                viewModel = wallpaperViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Voice.route) {
            VoiceScreen(
                viewModel = voiceViewModel,
                onNavigateToExecution = { taskId ->
                    navController.navigate(Screen.Execution.createRoute(taskId))
                },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Assistant.route) {
            AssistantScreen(
                viewModel = assistantViewModel,
                onNavigateToExecution = { taskId ->
                    navController.navigate(Screen.Execution.createRoute(taskId))
                },
                onNavigateToBuilder = { taskId ->
                    navController.navigate(Screen.Builder.createRoute(taskId))
                },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Accessibility.route) {
            AccessibilityScreen(
                viewModel = accessibilityViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Family.route) {
            FamilyScreen(
                viewModel = familyViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Activity.route) {
            ActivityScreen(
                viewModel = activityViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Scheduler.route) {
            SchedulerScreen(
                viewModel = schedulerViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                viewModel = settingsViewModel,
                onNavigateToOnboarding = {
                    navController.navigate(Screen.Onboarding.route)
                }
            )
        }

        composable(Screen.UseCases.route) {
            UseCasesScreen(
                onRunTask = { taskId ->
                    navController.navigate(Screen.Execution.createRoute(taskId))
                },
                onViewWorkflow = { taskId ->
                    navController.navigate(Screen.Builder.createRoute(taskId))
                },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                onFinishOnboarding = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.AiPreview.route) {
            AiPreviewScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
