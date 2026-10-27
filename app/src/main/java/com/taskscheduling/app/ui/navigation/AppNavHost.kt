package com.taskscheduling.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.taskscheduling.app.assistant.TaskNotificationScheduler
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
import com.taskscheduling.app.feature.tasks.AddEditTaskScreen
import com.taskscheduling.app.feature.tasks.CreateTaskFlowScreen
import com.taskscheduling.app.feature.tasks.CreateWorkflowScreen
import com.taskscheduling.app.feature.tasks.TasksScreen
import com.taskscheduling.app.feature.tasks.TasksViewModel
import com.taskscheduling.app.feature.usecases.UseCasesScreen
import com.taskscheduling.app.feature.voice.VoiceScreen
import com.taskscheduling.app.feature.voice.VoiceViewModel
import com.taskscheduling.app.automation.AndroidAccessibilityAutomationEngine
import com.taskscheduling.app.data.repository.WorkflowPersistenceRepository
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
    navigationTargetState: androidx.compose.runtime.MutableState<NavigationTarget?>? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val notificationScheduler = remember { TaskNotificationScheduler(context) }
    val activeTaskDraft = remember { androidx.compose.runtime.mutableStateOf<com.taskscheduling.app.domain.model.TaskDraft?>(null) }

    androidx.compose.runtime.LaunchedEffect(navigationTargetState?.value) {
        when (val target = navigationTargetState?.value) {
            is NavigationTarget.AddTask -> {
                activeTaskDraft.value = target.draft
                navController.navigate(Screen.AddEditTask.createRoute(null))
                navigationTargetState.value = null
            }
            is NavigationTarget.Tasks -> {
                navController.navigate(Screen.Tasks.route)
                navigationTargetState.value = null
            }
            is NavigationTarget.EditTask -> {
                navController.navigate(Screen.AddEditTask.createRoute(target.taskId))
                navigationTargetState.value = null
            }
            is NavigationTarget.Diagnostics -> {
                navController.navigate(Screen.VoiceBotDiagnostics.route)
                navigationTargetState.value = null
            }
            is NavigationTarget.BotSettings -> {
                navController.navigate(Screen.BotSettings.route)
                navigationTargetState.value = null
            }
            null -> Unit
        }
    }

    // Shared ViewModels
    val homeViewModel = remember { HomeViewModel(taskRepository, activityRepository, context = context) }
    val tasksViewModel = remember { TasksViewModel(taskRepository, notificationScheduler) }
    val builderViewModel = remember { BuilderViewModel(taskRepository) }
    val executionViewModel = remember { ExecutionViewModel(realAutomationEngine, taskRepository, activityRepository) }
    val voiceViewModel = remember {
        val voiceEngine = MockVoiceEngine { taskRepository.findTaskByVoiceIntent(it) }
        VoiceViewModel(voiceEngine, taskRepository, context)
    }
    val assistantViewModel = remember {
        AssistantViewModel(
            taskRepository = taskRepository,
            persistenceRepository = persistenceRepository,
            automationEngine = realAutomationEngine,
            context = context
        )
    }
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
                },
                onNavigateToTasks = {
                    navController.navigate(Screen.Tasks.route)
                },
                onNavigateToProfile = {
                    navController.navigate(Screen.Profile.route)
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
                onNavigateToCreateTask = {
                    navController.navigate(Screen.CreateWorkflow.route)
                },
                onNavigateToEditTask = { taskId ->
                    navController.navigate(Screen.AddEditTask.createRoute(taskId))
                },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.CreateWorkflow.route) {
            CreateWorkflowScreen(
                workflowPersistenceRepo = persistenceRepository,
                taskRepository = taskRepository,
                onNavigateBack = { navController.popBackStack() },
                onWorkflowSaved = { _ ->
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.CreateTask.route) {
            CreateWorkflowScreen(
                workflowPersistenceRepo = persistenceRepository,
                taskRepository = taskRepository,
                onNavigateBack = { navController.popBackStack() },
                onWorkflowSaved = { _ ->
                    navController.popBackStack()
                }
            )
        }

        composable(
            route = Screen.AddEditTask.route,
            arguments = listOf(navArgument("taskId") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            })
        ) { backStackEntry ->
            val taskId = backStackEntry.arguments?.getString("taskId")
            val allTasks by taskRepository.getTasks().collectAsState(initial = emptyList())
            val existing = allTasks.find { it.id == taskId }

            AddEditTaskScreen(
                initialDraft = if (existing == null) activeTaskDraft.value else null,
                existingTask = existing,
                onSaveTask = { task ->
                    CoroutineScope(Dispatchers.Main).launch {
                        taskRepository.saveTask(task)
                        if (task.hasReminder && task.notificationEnabled) {
                            notificationScheduler.scheduleReminder(task)
                        }
                        activeTaskDraft.value = null
                        navController.popBackStack()
                    }
                },
                onNavigateBack = {
                    activeTaskDraft.value = null
                    navController.popBackStack()
                },
                onNavigateToBuilder = { tid ->
                    navController.navigate(Screen.Builder.createRoute(tid))
                }
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
            val taskId = backStackEntry.arguments?.getString("taskId") ?: ""
            ExecutionScreen(
                viewModel = executionViewModel,
                taskId = taskId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToBuilder = { tid ->
                    navController.navigate(Screen.Builder.createRoute(tid))
                }
            )
        }

        composable(Screen.Voice.route) {
            VoiceScreen(
                viewModel = voiceViewModel,
                onNavigateToExecution = { taskId ->
                    navController.navigate(Screen.Execution.createRoute(taskId))
                },
                onNavigateBack = { navController.popBackStack() },
                onNavigateToDiagnosticCenter = {
                    navController.navigate(Screen.VoiceBotDiagnostics.route)
                }
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
                onNavigateBack = { navController.popBackStack() },
                onNavigateToSettings = { navController.navigate(Screen.BotSettings.route) }
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
                },
                onNavigateToDiagnosticCenter = {
                    navController.navigate(Screen.VoiceBotDiagnostics.route)
                },
                onNavigateToBotSettings = {
                    navController.navigate(Screen.BotSettings.route)
                },
                onNavigateToProfile = {
                    navController.navigate(Screen.Profile.route)
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

        composable(Screen.VoiceBotDiagnostics.route) {
            val diagnosticViewModel = remember {
                com.taskscheduling.app.feature.diagnostics.VoiceBotDiagnosticViewModel(context)
            }
            com.taskscheduling.app.feature.diagnostics.VoiceBotDiagnosticScreen(
                viewModel = diagnosticViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.BotSettings.route) {
            val botSettingsViewModel = remember {
                com.taskscheduling.app.feature.settings.BotSettingsViewModel(context)
            }
            com.taskscheduling.app.feature.settings.BotSettingsScreen(
                viewModel = botSettingsViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Profile.route) {
            val profileViewModel = remember {
                com.taskscheduling.app.feature.profile.ProfileViewModel(
                    settingsRepository = settingsRepository,
                    taskRepository = taskRepository,
                    activityRepository = activityRepository
                )
            }
            com.taskscheduling.app.feature.profile.ProfileScreen(
                viewModel = profileViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}

