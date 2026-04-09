package com.example.clickassist.ui.taskedit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.clickassist.viewmodel.TaskEditViewModel
import kotlinx.coroutines.flow.collect

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskEditScreen(
    viewModel: TaskEditViewModel,
    onNavigateBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(viewModel) {
        viewModel.savedTaskIds.collect {
            onNavigateBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (uiState.taskId == 0L) "New Task" else "Edit Task",
                    )
                },
                navigationIcon = {
                    TextButton(onClick = onNavigateBack) {
                        Text(text = "Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick = viewModel::saveTask,
                        enabled = !uiState.isLoading && !uiState.isSaving,
                    ) {
                        Text(text = "Save")
                    }
                },
            )
        },
    ) { innerPadding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "Task Settings",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    OutlinedTextField(
                        value = uiState.name,
                        onValueChange = viewModel::updateName,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(text = "Task Name") },
                        singleLine = true,
                    )
                    NumberField(
                        value = uiState.totalRounds,
                        onValueChange = viewModel::updateTotalRounds,
                        label = "Total Rounds",
                    )
                    SwitchRow(
                        label = "Infinite Rounds",
                        checked = uiState.infiniteRounds,
                        onCheckedChange = viewModel::updateInfiniteRounds,
                    )
                    SwitchRow(
                        label = "Task Enabled",
                        checked = uiState.enabled,
                        onCheckedChange = viewModel::updateEnabled,
                    )
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "Default TAP Step",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    NumberField(
                        value = uiState.x,
                        onValueChange = viewModel::updateX,
                        label = "x",
                    )
                    NumberField(
                        value = uiState.y,
                        onValueChange = viewModel::updateY,
                        label = "y",
                    )
                    NumberField(
                        value = uiState.intervalMs,
                        onValueChange = viewModel::updateIntervalMs,
                        label = "intervalMs",
                    )
                    NumberField(
                        value = uiState.repeatCount,
                        onValueChange = viewModel::updateRepeatCount,
                        label = "repeatCount",
                    )
                    NumberField(
                        value = uiState.preDelayMs,
                        onValueChange = viewModel::updatePreDelayMs,
                        label = "preDelayMs",
                    )
                    NumberField(
                        value = uiState.postDelayMs,
                        onValueChange = viewModel::updatePostDelayMs,
                        label = "postDelayMs",
                    )
                    Text(
                        text = "Hooks are reserved for SWIPE, WAIT, long press, double tap, coordinate recording, and JSON import/export. This round only exposes one TAP editor.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            if (!uiState.validationMessage.isNullOrBlank()) {
                Text(
                    text = uiState.validationMessage.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Button(
                onClick = viewModel::saveTask,
                enabled = !uiState.isSaving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = if (uiState.isSaving) "Saving..." else "Save Task")
            }
        }
    }
}

@Composable
private fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(text = label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
    )
}

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}
