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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.clickassist.data.local.entity.TaskWithSteps
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
                title = { Text(text = "Task List") },
                actions = {
                    TextButton(onClick = onOpenPermissions) {
                        Text(text = "Permissions")
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
                    runnerDescription = buildString {
                        append("Current state: ${uiState.runnerState}")
                        uiState.runnerProgress?.let { progress ->
                            append(" | task#${progress.taskId}")
                            append(" | round ${progress.currentRoundIndex + 1}")
                            append(" | step ${progress.currentStepIndex + 1}")
                            append(" | repeat ${progress.currentStepRepeatIndex + 1}")
                        }
                    },
                    lastEditedTaskId = uiState.lastEditedTaskId,
                    runnerErrorMessage = uiState.runnerErrorMessage,
                    onPause = viewModel::pauseTask,
                    onResume = viewModel::resumeTask,
                    onStop = viewModel::stopTask,
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
                                text = "No tasks yet",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "Create a local task with one TAP step and start it from here.",
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
                        onStart = { viewModel.startTask(taskWithSteps.task.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun RunnerCard(
    runnerState: RunnerState,
    runnerDescription: String,
    lastEditedTaskId: Long?,
    runnerErrorMessage: String?,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Runner Control",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(text = runnerDescription)
            if (lastEditedTaskId != null) {
                Text(text = "Last edited task: #$lastEditedTaskId")
            }
            if (!runnerErrorMessage.isNullOrBlank()) {
                Text(
                    text = runnerErrorMessage,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onPause,
                    enabled = runnerState == RunnerState.RUNNING,
                ) {
                    Text(text = "Pause")
                }
                Button(
                    onClick = onResume,
                    enabled = runnerState == RunnerState.PAUSED,
                ) {
                    Text(text = "Resume")
                }
                OutlinedButton(
                    onClick = onStop,
                    enabled = runnerState != RunnerState.IDLE,
                ) {
                    Text(text = "Stop")
                }
            }
        }
    }
}

@Composable
private fun TaskCard(
    taskWithSteps: TaskWithSteps,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onStart: () -> Unit,
) {
    val task = taskWithSteps.task
    val firstStep = taskWithSteps.steps.firstOrNull()

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
                text = buildString {
                    append("Task #${task.id} | ")
                    append(if (task.enabled) "Enabled" else "Disabled")
                    append(" | ")
                    append(
                        if (task.infiniteRounds) {
                            "Infinite rounds"
                        } else {
                            "Rounds ${task.totalRounds}"
                        },
                    )
                },
            )
            Text(
                text = if (firstStep == null) {
                    "No steps"
                } else {
                    "Default TAP: x=${firstStep.x ?: "-"} y=${firstStep.y ?: "-"} interval=${firstStep.intervalMs} repeat=${firstStep.repeatCount}"
                },
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onStart,
                    enabled = task.enabled,
                ) {
                    Text(text = "Start")
                }
                OutlinedButton(onClick = onEdit) {
                    Text(text = "Edit")
                }
                TextButton(onClick = onDelete) {
                    Text(text = "Delete")
                }
            }
        }
    }
}
