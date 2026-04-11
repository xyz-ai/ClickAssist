package com.example.clickassist.ui.taskedit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

@Composable
fun StepEditScreen(
    stepNumber: Int,
    draft: EditableStepDraft,
    onActionTypeChange: (ActionType) -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onPickCoordinate: (CoordinatePickerKind) -> Unit,
    onRepeatCountChange: (String) -> Unit,
    onIntervalMsChange: (String) -> Unit,
    onDurationMsChange: (String) -> Unit,
    onPreDelayMsChange: (String) -> Unit,
    onPostDelayMsChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.task_edit_step_editor_title, stepNumber),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )

            ActionTypeSelector(
                selected = draft.actionType,
                onSelected = onActionTypeChange,
            )

            SwitchRow(
                label = stringResource(R.string.task_edit_label_enabled),
                checked = draft.enabled,
                onCheckedChange = onEnabledChange,
            )

            when (draft.actionType) {
                ActionType.TAP -> {
                    PositionPickerBlock(
                        title = stringResource(R.string.task_edit_tap_position_title),
                        positionText = draft.point?.let { point ->
                            stringResource(R.string.task_edit_position_selected, point.x, point.y)
                        } ?: stringResource(R.string.task_edit_position_not_selected),
                        buttonText = stringResource(
                            if (draft.point == null) {
                                R.string.task_edit_action_pick_position
                            } else {
                                R.string.task_edit_action_repick_position
                            },
                        ),
                        onClick = { onPickCoordinate(CoordinatePickerKind.PRIMARY) },
                    )
                }

                ActionType.LONG_PRESS -> {
                    PositionPickerBlock(
                        title = stringResource(R.string.task_edit_long_press_position_title),
                        positionText = draft.point?.let { point ->
                            stringResource(R.string.task_edit_position_selected, point.x, point.y)
                        } ?: stringResource(R.string.task_edit_long_press_position_not_selected),
                        buttonText = stringResource(
                            if (draft.point == null) {
                                R.string.task_edit_action_pick_long_press_position
                            } else {
                                R.string.task_edit_action_repick_long_press_position
                            },
                        ),
                        onClick = { onPickCoordinate(CoordinatePickerKind.PRIMARY) },
                    )
                    NumberField(
                        value = draft.durationMs,
                        onValueChange = onDurationMsChange,
                        label = stringResource(R.string.task_edit_label_long_press_duration),
                    )
                }

                ActionType.SWIPE -> {
                    PositionPickerBlock(
                        title = stringResource(R.string.task_edit_swipe_start_title),
                        positionText = draft.point?.let { point ->
                            stringResource(R.string.task_edit_position_selected, point.x, point.y)
                        } ?: stringResource(R.string.task_edit_swipe_start_not_selected),
                        buttonText = stringResource(R.string.task_edit_action_pick_swipe_start),
                        onClick = { onPickCoordinate(CoordinatePickerKind.SWIPE_START) },
                    )
                    PositionPickerBlock(
                        title = stringResource(R.string.task_edit_swipe_end_title),
                        positionText = draft.endPoint?.let { point ->
                            stringResource(R.string.task_edit_position_selected, point.x, point.y)
                        } ?: stringResource(R.string.task_edit_swipe_end_not_selected),
                        buttonText = stringResource(R.string.task_edit_action_pick_swipe_end),
                        onClick = { onPickCoordinate(CoordinatePickerKind.SWIPE_END) },
                    )
                    NumberField(
                        value = draft.durationMs,
                        onValueChange = onDurationMsChange,
                        label = stringResource(R.string.task_edit_label_duration),
                    )
                }

                ActionType.WAIT -> {
                    NumberField(
                        value = draft.durationMs,
                        onValueChange = onDurationMsChange,
                        label = stringResource(R.string.task_edit_label_wait_duration),
                    )
                }
            }

            NumberField(
                value = draft.repeatCount,
                onValueChange = onRepeatCountChange,
                label = stringResource(R.string.task_edit_label_repeat_count),
            )
            NumberField(
                value = draft.intervalMs,
                onValueChange = onIntervalMsChange,
                label = stringResource(R.string.task_edit_label_interval),
            )
            NumberField(
                value = draft.preDelayMs,
                onValueChange = onPreDelayMsChange,
                label = stringResource(R.string.task_edit_label_pre_delay),
            )
            NumberField(
                value = draft.postDelayMs,
                onValueChange = onPostDelayMsChange,
                label = stringResource(R.string.task_edit_label_post_delay),
            )
        }
    }
}

@Composable
private fun ActionTypeSelector(
    selected: ActionType,
    onSelected: (ActionType) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.task_edit_action_type),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ActionType.entries.forEach { actionType ->
                val isSelected = actionType == selected
                val buttonModifier = Modifier.weight(1f)
                if (isSelected) {
                    Button(
                        onClick = { onSelected(actionType) },
                        modifier = buttonModifier,
                    ) {
                        Text(text = stringResource(actionTypeLabelRes(actionType)))
                    }
                } else {
                    OutlinedButton(
                        onClick = { onSelected(actionType) },
                        modifier = buttonModifier,
                    ) {
                        Text(text = stringResource(actionTypeLabelRes(actionType)))
                    }
                }
            }
        }
    }
}

@Composable
private fun PositionPickerBlock(
    title: String,
    positionText: String,
    buttonText: String,
    onClick: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
        Text(text = positionText)
        OutlinedButton(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = buttonText)
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
