package com.example.clickassist.ui.taskedit

import androidx.annotation.StringRes
import androidx.compose.foundation.background
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
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.clickassist.R
import com.example.clickassist.domain.model.ScreenPoint
import com.example.clickassist.viewmodel.TaskEditViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskEditScreen(
    viewModel: TaskEditViewModel,
    onNavigateBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    var previewPoint by remember { mutableStateOf<ScreenPoint?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.savedTaskIds.collect {
            onNavigateBack()
        }
    }

    LaunchedEffect(previewPoint) {
        if (previewPoint != null) {
            delay(800)
            previewPoint = null
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = stringResource(
                                if (uiState.taskId == 0L) {
                                    R.string.task_edit_title_new
                                } else {
                                    R.string.task_edit_title_edit
                                },
                            ),
                        )
                    },
                    navigationIcon = {
                        TextButton(onClick = onNavigateBack) {
                            Text(text = stringResource(R.string.common_back))
                        }
                    },
                    actions = {
                        TextButton(
                            onClick = viewModel::saveTask,
                            enabled = !uiState.isLoading && !uiState.isSaving,
                        ) {
                            Text(text = stringResource(R.string.common_save))
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
                            text = stringResource(R.string.task_edit_section_task_settings),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        OutlinedTextField(
                            value = uiState.name,
                            onValueChange = viewModel::updateName,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(text = stringResource(R.string.task_edit_label_name)) },
                            singleLine = true,
                        )
                        NumberField(
                            value = uiState.totalRounds,
                            onValueChange = viewModel::updateTotalRounds,
                            labelRes = R.string.task_edit_label_total_rounds,
                        )
                        SwitchRow(
                            label = stringResource(R.string.task_edit_label_infinite_rounds),
                            checked = uiState.infiniteRounds,
                            onCheckedChange = viewModel::updateInfiniteRounds,
                        )
                        SwitchRow(
                            label = stringResource(R.string.task_edit_label_enabled),
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
                            text = stringResource(R.string.task_edit_section_tap),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = uiState.tapPosition?.let { point ->
                                stringResource(
                                    R.string.task_edit_position_selected,
                                    point.x,
                                    point.y,
                                )
                            } ?: stringResource(R.string.task_edit_position_not_selected),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Button(
                            onClick = viewModel::openCoordinatePicker,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = stringResource(
                                    if (uiState.isTapPositionSet) {
                                        R.string.task_edit_action_repick_position
                                    } else {
                                        R.string.task_edit_action_pick_position
                                    },
                                ),
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                previewPoint = uiState.tapPosition
                            },
                            enabled = uiState.tapPosition != null,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(text = stringResource(R.string.task_edit_action_preview_position))
                        }
                        NumberField(
                            value = uiState.intervalMs,
                            onValueChange = viewModel::updateIntervalMs,
                            labelRes = R.string.task_edit_label_interval,
                        )
                        NumberField(
                            value = uiState.repeatCount,
                            onValueChange = viewModel::updateRepeatCount,
                            labelRes = R.string.task_edit_label_repeat_count,
                        )
                        NumberField(
                            value = uiState.preDelayMs,
                            onValueChange = viewModel::updatePreDelayMs,
                            labelRes = R.string.task_edit_label_pre_delay,
                        )
                        NumberField(
                            value = uiState.postDelayMs,
                            onValueChange = viewModel::updatePostDelayMs,
                            labelRes = R.string.task_edit_label_post_delay,
                        )
                        OutlinedButton(
                            onClick = viewModel::toggleAdvancedSettings,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = stringResource(
                                    if (uiState.isAdvancedSettingsExpanded) {
                                        R.string.task_edit_action_collapse_advanced
                                    } else {
                                        R.string.task_edit_action_expand_advanced
                                    },
                                ),
                            )
                        }
                        if (uiState.isAdvancedSettingsExpanded) {
                            NumberField(
                                value = uiState.x,
                                onValueChange = viewModel::updateX,
                                labelRes = R.string.task_edit_label_advanced_x,
                            )
                            NumberField(
                                value = uiState.y,
                                onValueChange = viewModel::updateY,
                                labelRes = R.string.task_edit_label_advanced_y,
                            )
                            Text(
                                text = stringResource(R.string.task_edit_advanced_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }

                uiState.validationMessageRes?.let { messageRes ->
                    Text(
                        text = stringResource(messageRes),
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                Button(
                    onClick = viewModel::saveTask,
                    enabled = !uiState.isSaving,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(
                            if (uiState.isSaving) {
                                R.string.task_edit_action_saving
                            } else {
                                R.string.task_edit_action_save_task
                            },
                        ),
                    )
                }
            }
        }

        if (previewPoint != null && !uiState.isCoordinatePickerVisible) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Transparent),
            ) {
                ScreenPointMarker(
                    point = previewPoint!!,
                    modifier = Modifier.align(Alignment.TopStart),
                    outerColor = Color.Red,
                    innerColor = Color.White,
                )
            }
        }

        if (uiState.isCoordinatePickerVisible) {
            CoordinatePickerScreen(
                initialPoint = uiState.tapPosition,
                onCancel = viewModel::dismissCoordinatePicker,
                onConfirm = viewModel::applyCoordinateSelection,
            )
        }
    }
}

@Composable
private fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    @StringRes labelRes: Int,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(text = stringResource(labelRes)) },
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
