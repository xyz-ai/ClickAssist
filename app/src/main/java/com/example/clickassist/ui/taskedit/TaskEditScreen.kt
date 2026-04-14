package com.example.clickassist.ui.taskedit

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.clickassist.R
import com.example.clickassist.domain.model.ActionType
import com.example.clickassist.viewmodel.CoordinatePickerKind
import com.example.clickassist.viewmodel.EditableStepDraft
import com.example.clickassist.viewmodel.SaveStatus
import com.example.clickassist.viewmodel.TaskEditViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskEditScreen(
    viewModel: TaskEditViewModel,
    onNavigateBack: () -> Unit,
    onTaskSaved: (Long) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val onSaveClick = {
        Log.i(
            TAG,
            "save button clicked mode=${if (uiState.taskId == 0L) "create" else "update"} taskId=${uiState.taskId} stepCount=${uiState.steps.size}",
        )
        viewModel.saveTask()
    }

    LaunchedEffect(viewModel) {
        viewModel.savedTaskIds.collect { savedTaskId ->
            onTaskSaved(savedTaskId)
        }
    }

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
                        onClick = onSaveClick,
                        enabled = !uiState.isLoading && !uiState.isSaving,
                    ) {
                        Text(text = stringResource(R.string.common_save))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.addStep(ActionType.TAP) }) {
                Text(text = stringResource(R.string.common_add))
            }
        },
    ) { innerPadding ->
        if (uiState.isLoading) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
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
            TaskSaveStatusSection(
                saveStatus = uiState.saveStatus,
                saveStatusMessageRes = uiState.saveStatusMessageRes,
                saveErrorDetail = uiState.saveErrorDetail,
                validationMessageRes = uiState.validationMessageRes,
            )

            TaskSettingsSection(
                name = uiState.name,
                totalRounds = uiState.totalRounds,
                enabled = uiState.enabled,
                infiniteRounds = uiState.infiniteRounds,
                onNameChange = viewModel::updateName,
                onTotalRoundsChange = viewModel::updateTotalRounds,
                onEnabledChange = viewModel::updateEnabled,
                onInfiniteRoundsChange = viewModel::updateInfiniteRounds,
            )

            StepListSection(
                steps = uiState.steps,
                editingStepKey = uiState.editingStepKey,
                onSelect = viewModel::selectStep,
                onMoveUp = viewModel::moveStepUp,
                onMoveDown = viewModel::moveStepDown,
                onDelete = viewModel::deleteStep,
                onAddTap = { viewModel.addStep(ActionType.TAP) },
                onAddLongPress = { viewModel.addStep(ActionType.LONG_PRESS) },
                onAddSwipe = { viewModel.addStep(ActionType.SWIPE) },
                onAddWait = { viewModel.addStep(ActionType.WAIT) },
            )

            uiState.editingStep?.let { draft ->
                StepEditScreen(
                    stepNumber = uiState.steps.indexOfFirst { it.draftKey == draft.draftKey } + 1,
                    draft = draft,
                    onActionTypeChange = { viewModel.updateStepActionType(draft.draftKey, it) },
                    onEnabledChange = { viewModel.updateStepEnabled(draft.draftKey, it) },
                    onPickCoordinate = { viewModel.openCoordinatePicker(draft.draftKey, it) },
                    onRepeatCountChange = { viewModel.updateStepRepeatCount(draft.draftKey, it) },
                    onIntervalMsChange = { viewModel.updateStepIntervalMs(draft.draftKey, it) },
                    onDurationMsChange = { viewModel.updateStepDurationMs(draft.draftKey, it) },
                    onPreDelayMsChange = { viewModel.updateStepPreDelayMs(draft.draftKey, it) },
                    onPostDelayMsChange = { viewModel.updateStepPostDelayMs(draft.draftKey, it) },
                )
            }

            Button(
                onClick = onSaveClick,
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

        uiState.coordinatePickerTarget?.let { target ->
            val draft = uiState.steps.firstOrNull { it.draftKey == target.draftKey }
            CoordinatePickerScreen(
                initialPoint = when (target.kind) {
                    CoordinatePickerKind.PRIMARY,
                    CoordinatePickerKind.SWIPE_START,
                    -> draft?.point

                    CoordinatePickerKind.SWIPE_END -> draft?.endPoint
                },
                onCancel = viewModel::dismissCoordinatePicker,
                onConfirm = viewModel::applyCoordinateSelection,
            )
        }
    }
}

@Composable
private fun TaskSaveStatusSection(
    saveStatus: SaveStatus,
    saveStatusMessageRes: Int?,
    saveErrorDetail: String?,
    validationMessageRes: Int?,
) {
    if (saveStatusMessageRes == null && validationMessageRes == null) {
        return
    }

    val isError = saveStatus == SaveStatus.ERROR || validationMessageRes != null
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            saveStatusMessageRes?.let { messageRes ->
                val messageText = if (saveStatus == SaveStatus.ERROR && !saveErrorDetail.isNullOrBlank()) {
                    stringResource(
                        R.string.task_edit_save_status_failed_with_reason,
                        saveErrorDetail.orEmpty(),
                    )
                } else {
                    stringResource(messageRes)
                }
                Text(
                    text = messageText,
                    color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            validationMessageRes?.let { messageRes ->
                Text(
                    text = stringResource(messageRes),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun TaskSettingsSection(
    name: String,
    totalRounds: String,
    enabled: Boolean,
    infiniteRounds: Boolean,
    onNameChange: (String) -> Unit,
    onTotalRoundsChange: (String) -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onInfiniteRoundsChange: (Boolean) -> Unit,
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
                value = name,
                onValueChange = onNameChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(text = stringResource(R.string.task_edit_label_name)) },
                singleLine = true,
            )
            NumberField(
                value = totalRounds,
                onValueChange = onTotalRoundsChange,
                label = stringResource(R.string.task_edit_label_total_rounds),
            )
            SwitchRow(
                label = stringResource(R.string.task_edit_label_infinite_rounds),
                checked = infiniteRounds,
                onCheckedChange = onInfiniteRoundsChange,
            )
            SwitchRow(
                label = stringResource(R.string.task_edit_label_enabled),
                checked = enabled,
                onCheckedChange = onEnabledChange,
            )
        }
    }
}

@Composable
private fun StepListSection(
    steps: List<EditableStepDraft>,
    editingStepKey: Long?,
    onSelect: (Long) -> Unit,
    onMoveUp: (Long) -> Unit,
    onMoveDown: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onAddTap: () -> Unit,
    onAddLongPress: () -> Unit,
    onAddSwipe: () -> Unit,
    onAddWait: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.task_edit_section_steps),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(onClick = onAddTap, modifier = Modifier.weight(1f)) {
                    Text(text = stringResource(R.string.task_edit_add_tap_step))
                }
                OutlinedButton(onClick = onAddLongPress, modifier = Modifier.weight(1f)) {
                    Text(text = stringResource(R.string.task_edit_add_long_press_step))
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(onClick = onAddSwipe, modifier = Modifier.weight(1f)) {
                    Text(text = stringResource(R.string.task_edit_add_swipe_step))
                }
                OutlinedButton(onClick = onAddWait, modifier = Modifier.weight(1f)) {
                    Text(text = stringResource(R.string.task_edit_add_wait_step))
                }
            }

            steps.forEachIndexed { index, step ->
                StepListItem(
                    stepNumber = index + 1,
                    draft = step,
                    selected = step.draftKey == editingStepKey,
                    onSelect = { onSelect(step.draftKey) },
                    onMoveUp = { onMoveUp(step.draftKey) },
                    onMoveDown = { onMoveDown(step.draftKey) },
                    onDelete = { onDelete(step.draftKey) },
                )
            }
        }
    }
}

@Composable
private fun StepListItem(
    stepNumber: Int,
    draft: EditableStepDraft,
    selected: Boolean,
    onSelect: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(
                    R.string.task_edit_step_item_title,
                    stepNumber,
                    stringResource(actionTypeLabelRes(draft.actionType)),
                ),
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = buildStepSummaryText(draft),
                style = MaterialTheme.typography.bodySmall,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(onClick = onSelect, modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(
                            if (selected) R.string.task_edit_action_editing else R.string.task_edit_action_edit_step,
                        ),
                    )
                }
                OutlinedButton(onClick = onMoveUp, modifier = Modifier.weight(1f)) {
                    Text(text = stringResource(R.string.common_move_up))
                }
                OutlinedButton(onClick = onMoveDown, modifier = Modifier.weight(1f)) {
                    Text(text = stringResource(R.string.common_move_down))
                }
                TextButton(onClick = onDelete) {
                    Text(text = stringResource(R.string.common_delete))
                }
            }
        }
    }
}

@Composable
private fun buildStepSummaryText(
    draft: EditableStepDraft,
): String {
    val typeText = stringResource(actionTypeLabelRes(draft.actionType))
    val enabledText = stringResource(
        if (draft.enabled) R.string.common_status_enabled else R.string.common_status_disabled,
    )
    return when (draft.actionType) {
        ActionType.TAP,
        ActionType.LONG_PRESS,
        -> {
            val pointText = draft.point?.let { point ->
                stringResource(R.string.task_edit_position_selected, point.x, point.y)
            } ?: stringResource(R.string.task_edit_position_not_selected)
            stringResource(
                R.string.task_edit_step_summary_tap_like,
                typeText,
                enabledText,
                pointText,
            )
        }

        ActionType.SWIPE -> {
            val startText = draft.point?.let { point ->
                stringResource(R.string.task_edit_swipe_start_short, point.x, point.y)
            } ?: stringResource(R.string.task_edit_swipe_start_not_selected)
            val endText = draft.endPoint?.let { point ->
                stringResource(R.string.task_edit_swipe_end_short, point.x, point.y)
            } ?: stringResource(R.string.task_edit_swipe_end_not_selected)
            stringResource(
                R.string.task_edit_step_summary_swipe,
                typeText,
                enabledText,
                startText,
                endText,
            )
        }

        ActionType.WAIT -> stringResource(
            R.string.task_edit_step_summary_wait,
            typeText,
            enabledText,
            stringResource(R.string.task_edit_wait_summary, draft.durationMs),
        )
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

private fun actionTypeLabelRes(
    actionType: ActionType,
): Int {
    return when (actionType) {
        ActionType.TAP -> R.string.action_type_tap
        ActionType.LONG_PRESS -> R.string.action_type_long_press
        ActionType.SWIPE -> R.string.action_type_swipe
        ActionType.WAIT -> R.string.action_type_wait
    }
}

private const val TAG = "TaskEditSave"
