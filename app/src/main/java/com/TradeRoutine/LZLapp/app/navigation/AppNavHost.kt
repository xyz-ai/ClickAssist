package com.TradeRoutine.LZLapp.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.TradeRoutine.LZLapp.app.AppContainer
import com.TradeRoutine.LZLapp.ui.onboarding.OnboardingScreen
import com.TradeRoutine.LZLapp.ui.permission.PermissionGuideScreen
import com.TradeRoutine.LZLapp.ui.settings.SettingsScreen
import com.TradeRoutine.LZLapp.ui.taskedit.TaskEditScreen
import com.TradeRoutine.LZLapp.ui.tasklist.TaskListScreen
import com.TradeRoutine.LZLapp.viewmodel.PermissionGuideViewModel
import com.TradeRoutine.LZLapp.viewmodel.SettingsViewModel
import com.TradeRoutine.LZLapp.viewmodel.TaskEditViewModel
import com.TradeRoutine.LZLapp.viewmodel.TaskListViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest

private const val ONBOARDING_ROUTE = "onboarding"
private const val PERMISSION_GUIDE_ROUTE = "permission_guide"
private const val TASK_LIST_ROUTE = "task_list"
private const val TASK_EDIT_ROUTE = "task_edit"
private const val SETTINGS_ROUTE = "settings"
private const val TASK_ID_ARG = "taskId"
private const val TASK_SAVE_RESULT_KEY = "task_save_result"

private fun taskEditRoute(taskId: Long): String = "$TASK_EDIT_ROUTE/$taskId"

@Composable
fun AppNavHost(
    appContainer: AppContainer,
    showOnboardingInitially: Boolean,
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()

    NavHost(
        navController = navController,
        startDestination = if (showOnboardingInitially) ONBOARDING_ROUTE else TASK_LIST_ROUTE,
        modifier = modifier,
    ) {
        composable(route = ONBOARDING_ROUTE) {
            OnboardingScreen(
                onFinish = {
                    scope.launch {
                        appContainer.settingsRepository.setHasSeenOnboarding(true)
                        navController.navigate(TASK_LIST_ROUTE) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                inclusive = true
                            }
                            launchSingleTop = true
                        }
                    }
                },
            )
        }

        composable(route = PERMISSION_GUIDE_ROUTE) {
            val viewModel: PermissionGuideViewModel = viewModel(
                factory = PermissionGuideViewModel.factory(appContainer),
            )

            PermissionGuideScreen(
                viewModel = viewModel,
                onContinueToTaskList = {
                    navController.navigate(TASK_LIST_ROUTE) {
                        launchSingleTop = true
                    }
                },
                onOpenSettings = {
                    navController.navigate(SETTINGS_ROUTE) {
                        launchSingleTop = true
                    }
                },
            )
        }

        composable(route = TASK_LIST_ROUTE) { backStackEntry ->
            val viewModel: TaskListViewModel = viewModel(
                factory = TaskListViewModel.factory(appContainer),
            )
            val saveResultFlow = backStackEntry.savedStateHandle.getStateFlow<Long?>(TASK_SAVE_RESULT_KEY, null)

            LaunchedEffect(backStackEntry, viewModel) {
                saveResultFlow.collectLatest { savedTaskId ->
                    if (savedTaskId == null) return@collectLatest
                    viewModel.onTaskSavedResult(savedTaskId)
                    backStackEntry.savedStateHandle.set<Long?>(TASK_SAVE_RESULT_KEY, null)
                }
            }

            TaskListScreen(
                viewModel = viewModel,
                onCreateTask = { navController.navigate(taskEditRoute(0L)) },
                onEditTask = { taskId -> navController.navigate(taskEditRoute(taskId)) },
                onOpenPermissions = { navController.navigate(PERMISSION_GUIDE_ROUTE) },
                onOpenSettings = { navController.navigate(SETTINGS_ROUTE) },
            )
        }

        composable(
            route = "$TASK_EDIT_ROUTE/{$TASK_ID_ARG}",
            arguments = listOf(
                navArgument(TASK_ID_ARG) { type = NavType.LongType },
            ),
        ) { backStackEntry ->
            val taskId = backStackEntry.arguments?.getLong(TASK_ID_ARG) ?: 0L
            val viewModel: TaskEditViewModel = viewModel(
                viewModelStoreOwner = backStackEntry,
                key = "task-edit-$taskId",
                factory = TaskEditViewModel.factory(
                    appContainer = appContainer,
                    taskId = taskId,
                ),
            )

            TaskEditScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.navigateUp() },
                onTaskSaved = { savedTaskId ->
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set(TASK_SAVE_RESULT_KEY, savedTaskId)
                    navController.navigateUp()
                },
            )
        }

        composable(route = SETTINGS_ROUTE) {
            val viewModel: SettingsViewModel = viewModel(
                factory = SettingsViewModel.factory(appContainer),
            )

            SettingsScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.navigateUp() },
                onOpenOnboarding = {
                    navController.navigate(ONBOARDING_ROUTE) {
                        launchSingleTop = true
                    }
                },
                onReopenFloatingTutorial = {
                    appContainer.taskRunnerEngine.reopenFloatingTutorialIfPossible()
                },
            )
        }
    }
}
