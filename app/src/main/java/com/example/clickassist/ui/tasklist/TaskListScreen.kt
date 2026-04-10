package com.example.clickassist.ui.tasklist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.clickassist.R
import com.example.clickassist.data.local.entity.TaskWithSteps
import com.example.clickassist.service.runner.RunnerProgress
import com.example.clickassist.service.runner.RunnerState
import com.example.clickassist.viewmodel.TaskListViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskListScreen(
    viewModel: TaskListViewModel,
    onCreateTask: () -> Unit,
    onEditTask: (Long) -> Unit,
    onOpenPermissions: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.task_list_title)) },
                actions = {
                    TextButton(onClick = onOpenPermissions) {
                        Text(text = stringResource(R.string.task_list_action_permissions))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreateTask) {
                Text(text = "+")
            }
        },
    ) { innerPadding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                top = innerPadding.calculateTopPadding() + 16.dp,
                end = 16.dp,
                bottom = innerPadding.calculateBottomPadding() + 88.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                RunnerCard(
                    runnerState = uiState.runnerState,
                    runnerProgress = uiState.runnerProgress,
                    lastEditedTaskId = uiState.lastEditedTaskId,
                    runnerErrorMessageRes = uiState.runnerErrorMessageRes,
                    isFloatingModeEnabled = uiState.isFloatingModeEnabled,
                    activeFloatingTaskId = uiState.activeFloatingTaskId,
                    activeFloatingTaskName = uiState.activeFloatingTaskName,
                    isFloatingTargetVisible = uiState.isFloatingTargetVisible,
                )
            }

            if (uiState.tasks.isEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.task_list_empty_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = stringResource(R.string.task_list_empty_description),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            } else {
                items(
                    items = uiState.tasks,
                    key = { item -> item.task.id },
                ) { taskWithSteps ->
                    TaskCard(
                        taskWithSteps = taskWithSteps,
                        onEdit = { onEditTask(taskWithSteps.task.id) },
                        onDelete = { viewModel.deleteTask(taskWithSteps.task.id) },
                        onQuickStart = { viewModel.enterFloatingMode(taskWithSteps.task.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun RunnerCard(
    runnerState: RunnerState,
    runnerProgress: RunnerProgress?,
    lastEditedTaskId: Long?,
    runnerErrorMessageRes: Int?,
    isFloatingModeEnabled: Boolean,
    activeFloatingTaskId: Long?,
    activeFloatingTaskName: String?,
    isFloatingTargetVisible: Boolean,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.task_list_runner_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(
                    R.string.task_list_runner_state,
                    stringResource(runnerStateRes(runnerState)),
                ),
            )
            Text(
                text = stringResource(
                    R.string.task_list_floating_mode,
                    stringResource(
                        if (isFloatingModeEnabled) {
                            R.string.common_status_enabled
                        } else {
                            R.string.common_status_disabled
                        },
                    ),
                ),
            )
            if (activeFloatingTaskId != null) {
                Text(
                    text = stringResource(
                        R.string.task_list_floating_task,
                        activeFloatingTaskName ?: stringResource(R.string.task_list_floating_task_fallback),
                        activeFloatingTaskId,
                    ),
                )
            }
            Text(
                text = stringResource(
                    R.string.task_list_target_visibility,
                    stringResource(
                        if (isFloatingTargetVisible) {
                            R.string.task_list_target_visible
                        } else {
                            R.string.task_list_target_hidden
                        },
                    ),
                ),
            )
            runnerProgress?.let { progress ->
                Text(text = stringResource(R.string.task_list_runner_task, progress.taskId))
                Text(text = stringResource(R.string.task_list_runner_round, progress.currentRoundIndex + 1))
                Text(text = stringResource(R.string.task_list_runner_step, progress.currentStepIndex + 1))
                Text(text = stringResource(R.string.task_list_runner_repeat, progress.currentStepRepeatIndex + 1))
            }
            if (lastEditedTaskId != null) {
                Text(text = stringResource(R.string.task_list_last_edited_task, lastEditedTaskId))
            }
            runnerErrorMessageRes?.let { messageRes ->
                Text(
                    text = stringResource(messageRes),
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (isFloatingModeEnabled) {
                Text(
                    text = stringResource(R.string.task_list_overlay_control_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                Text(
                    text = stringResource(R.string.task_list_quick_start_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun TaskCard(
    taskWithSteps: TaskWithSteps,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onQuickStart: () -> Unit,
) {
    val task = taskWithSteps.task
    val firstStep = taskWithSteps.steps.firstOrNull()
    val pointX = firstStep?.x
    val pointY = firstStep?.y
    val hasTapPoint = pointX != null && pointY != null

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = task.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(
                    R.string.task_list_task_meta,
                    task.id,
                    stringResource(
                        if (task.enabled) {
                            R.string.task_list_task_enabled
                        } else {
                            R.string.task_list_task_disabled
                        },
                    ),
                ),
            )
            Text(
                text = stringResource(
                    if (hasTapPoint) {
                        R.string.task_list_position_set
                    } else {
                        R.string.task_list_position_not_set
                    },
                ),
            )
            Text(
                text = if (task.infiniteRounds) {
                    stringResource(R.string.task_list_rounds_infinite)
                } else {
                    stringResource(R.string.task_list_rounds_count, task.totalRounds)
                },
            )
            Text(
                text = stringResource(
                    R.string.task_list_repeat_count,
                    firstStep?.repeatCount ?: 0,
                ),
            )
            Text(
                text = stringResource(
                    R.string.task_list_interval_ms,
                    (firstStep?.intervalMs ?: 0L).toInt(),
                ),
            )
            if (hasTapPoint) {
                Text(
                    text = stringResource(
                        R.string.task_list_coordinate,
                        pointX!!,
                        pointY!!,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onQuickStart,
                    enabled = task.enabled,
                ) {
                    Text(text = stringResource(R.string.task_list_action_quick_start))
                }
                OutlinedButton(onClick = onEdit) {
                    Text(text = stringResource(R.string.task_list_action_edit))
                }
                TextButton(onClick = onDelete) {
                    Text(text = stringResource(R.string.task_list_action_delete))
                }
            }
        }
    }
}

private fun runnerStateRes(
    runnerState: RunnerState,
): Int {
    return when (runnerState) {
        RunnerState.IDLE -> R.string.runner_state_idle
        RunnerState.RUNNING -> R.string.runner_state_running
        RunnerState.PAUSED -> R.string.runner_state_paused
        RunnerState.STOPPING -> R.string.runner_state_stopping
        RunnerState.COMPLETED -> R.string.runner_state_completed
        RunnerState.ERROR -> R.string.runner_state_error
    }
}
