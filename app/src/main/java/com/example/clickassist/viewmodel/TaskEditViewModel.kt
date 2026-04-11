package com.example.clickassist.viewmodel

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.clickassist.R
import com.example.clickassist.app.AppContainer
import com.example.clickassist.data.local.entity.ActionStepEntity
import com.example.clickassist.data.local.entity.TaskEntity
import com.example.clickassist.domain.model.ActionType
import com.example.clickassist.domain.model.ScreenPoint
import com.example.clickassist.domain.repository.SettingsRepository
import com.example.clickassist.domain.repository.TaskRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class EditableStepDraft(
    val draftKey: Long,
    val stepId: Long = 0L,
    val actionType: ActionType = ActionType.TAP,
    val enabled: Boolean = true,
    val x: String = "",
    val y: String = "",
    val endX: String = "",
    val endY: String = "",
    val intervalMs: String = "300",
    val durationMs: String = "80",
    val repeatCount: String = "1",
    val preDelayMs: String = "0",
    val postDelayMs: String = "0",
) {
    val point: ScreenPoint?
        get() = x.toIntOrNull()?.let { px -> y.toIntOrNull()?.let { py -> ScreenPoint(px, py) } }

    val endPoint: ScreenPoint?
        get() = endX.toIntOrNull()?.let { px -> endY.toIntOrNull()?.let { py -> ScreenPoint(px, py) } }
}

data class CoordinatePickerTarget(
    val draftKey: Long,
    val kind: CoordinatePickerKind,
)

enum class CoordinatePickerKind {
    PRIMARY,
    SWIPE_START,
    SWIPE_END,
}

data class TaskEditUiState(
    val taskId: Long = 0L,
    val createdAt: Long = 0L,
    val name: String = "",
    val enabled: Boolean = true,
    val totalRounds: String = "1",
    val infiniteRounds: Boolean = false,
    val steps: List<EditableStepDraft> = emptyList(),
    val editingStepKey: Long? = null,
    val coordinatePickerTarget: CoordinatePickerTarget? = null,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    @StringRes
    val validationMessageRes: Int? = null,
) {
    val editingStep: EditableStepDraft?
        get() = steps.firstOrNull { it.draftKey == editingStepKey }
}

class TaskEditViewModel(
    private val taskId: Long,
    private val taskRepository: TaskRepository,
    private val settingsRepository: SettingsRepository,
    private val defaultTaskName: String,
) : ViewModel() {
    private val internalState = MutableStateFlow(
        TaskEditUiState(
            isLoading = taskId != 0L,
            steps = listOf(defaultDraft(1L)),
            editingStepKey = 1L,
        ),
    )
    val uiState: StateFlow<TaskEditUiState> = internalState.asStateFlow()

    private val _savedTaskIds = MutableSharedFlow<Long>(extraBufferCapacity = 1)
    val savedTaskIds = _savedTaskIds.asSharedFlow()

    private var nextDraftKey = 2L

    init {
        if (taskId != 0L) {
            loadTask()
        }
    }

    fun updateName(value: String) = updateState { copy(name = value, validationMessageRes = null) }

    fun updateEnabled(value: Boolean) = updateState { copy(enabled = value) }

    fun updateTotalRounds(value: String) = updateState { copy(totalRounds = value, validationMessageRes = null) }

    fun updateInfiniteRounds(value: Boolean) = updateState { copy(infiniteRounds = value) }

    fun addStep(actionType: ActionType) {
        val key = nextDraftKey++
        updateState {
            copy(
                steps = steps + defaultDraft(key, actionType),
                editingStepKey = key,
                validationMessageRes = null,
            )
        }
    }

    fun selectStep(draftKey: Long) = updateState { copy(editingStepKey = draftKey) }

    fun deleteStep(draftKey: Long) {
        updateState {
            val updatedSteps = steps.filterNot { it.draftKey == draftKey }.ifEmpty {
                listOf(defaultDraft(nextDraftKey++))
            }
            copy(
                steps = updatedSteps,
                editingStepKey = updatedSteps.firstOrNull()?.draftKey,
                validationMessageRes = null,
            )
        }
    }

    fun moveStepUp(draftKey: Long) = moveStep(draftKey, -1)

    fun moveStepDown(draftKey: Long) = moveStep(draftKey, 1)

    fun updateStepActionType(draftKey: Long, actionType: ActionType) = updateDraft(draftKey) {
        val defaultDuration = when (actionType) {
            ActionType.TAP -> "80"
            ActionType.LONG_PRESS -> "600"
            ActionType.SWIPE -> "300"
            ActionType.WAIT -> "1000"
        }
        copy(actionType = actionType, durationMs = defaultDuration)
    }

    fun updateStepEnabled(draftKey: Long, enabled: Boolean) = updateDraft(draftKey) { copy(enabled = enabled) }

    fun updateStepIntervalMs(draftKey: Long, value: String) = updateDraft(draftKey) { copy(intervalMs = value) }

    fun updateStepDurationMs(draftKey: Long, value: String) = updateDraft(draftKey) { copy(durationMs = value) }

    fun updateStepRepeatCount(draftKey: Long, value: String) = updateDraft(draftKey) { copy(repeatCount = value) }

    fun updateStepPreDelayMs(draftKey: Long, value: String) = updateDraft(draftKey) { copy(preDelayMs = value) }

    fun updateStepPostDelayMs(draftKey: Long, value: String) = updateDraft(draftKey) { copy(postDelayMs = value) }

    fun openCoordinatePicker(draftKey: Long, kind: CoordinatePickerKind) = updateState {
        copy(
            coordinatePickerTarget = CoordinatePickerTarget(draftKey = draftKey, kind = kind),
            validationMessageRes = null,
        )
    }

    fun dismissCoordinatePicker() = updateState { copy(coordinatePickerTarget = null) }

    fun applyCoordinateSelection(point: ScreenPoint) {
        val target = internalState.value.coordinatePickerTarget ?: return
        updateDraft(target.draftKey) {
            when (target.kind) {
                CoordinatePickerKind.PRIMARY,
                CoordinatePickerKind.SWIPE_START,
                -> copy(x = point.x.toString(), y = point.y.toString())

                CoordinatePickerKind.SWIPE_END -> copy(endX = point.x.toString(), endY = point.y.toString())
            }
        }
        updateState { copy(coordinatePickerTarget = null) }
    }

    fun saveTask() {
        val snapshot = internalState.value
        if (snapshot.isSaving) return

        val totalRounds = snapshot.totalRounds.toIntOrNull()?.coerceAtLeast(1) ?: 1
        val now = System.currentTimeMillis()
        val steps = buildActionSteps(snapshot.steps) ?: return

        updateState { copy(isSaving = true, validationMessageRes = null) }
        viewModelScope.launch {
            try {
                val task = TaskEntity(
                    id = snapshot.taskId,
                    name = snapshot.name.trim().ifEmpty { defaultTaskName },
                    enabled = snapshot.enabled,
                    totalRounds = totalRounds,
                    infiniteRounds = snapshot.infiniteRounds,
                    createdAt = if (snapshot.taskId == 0L) now else snapshot.createdAt,
                    updatedAt = now,
                )
                val savedTaskId = taskRepository.saveTask(task, steps)
                settingsRepository.setLastEditedTaskId(savedTaskId)
                updateState {
                    copy(
                        taskId = savedTaskId,
                        createdAt = if (createdAt == 0L) now else createdAt,
                        isSaving = false,
                        validationMessageRes = null,
                    )
                }
                _savedTaskIds.tryEmit(savedTaskId)
            } catch (_: Throwable) {
                updateState {
                    copy(
                        isSaving = false,
                        validationMessageRes = R.string.validation_save_task_failed,
                    )
                }
            }
        }
    }

    private fun buildActionSteps(
        drafts: List<EditableStepDraft>,
    ): List<ActionStepEntity>? {
        return drafts.mapIndexed { index, draft ->
            val primaryPoint = parseCoordinatePair(draft.x, draft.y, draft.actionType)
                ?: return null
            val swipeEnd = if (draft.actionType == ActionType.SWIPE) {
                parseCoordinatePair(draft.endX, draft.endY, draft.actionType, isEnd = true) ?: return null
            } else {
                null
            }

            ActionStepEntity(
                id = draft.stepId,
                taskId = taskId,
                orderIndex = index,
                actionType = draft.actionType.storageValue,
                x = primaryPoint?.x,
                y = primaryPoint?.y,
                endX = swipeEnd?.x,
                endY = swipeEnd?.y,
                intervalMs = draft.intervalMs.toLongOrNull()?.coerceAtLeast(0L) ?: 300L,
                durationMs = draft.durationMs.toLongOrNull()?.coerceAtLeast(1L) ?: defaultDurationFor(draft.actionType),
                repeatCount = draft.repeatCount.toIntOrNull()?.coerceAtLeast(1) ?: 1,
                preDelayMs = draft.preDelayMs.toLongOrNull()?.coerceAtLeast(0L) ?: 0L,
                postDelayMs = draft.postDelayMs.toLongOrNull()?.coerceAtLeast(0L) ?: 0L,
                enabled = draft.enabled,
            )
        }
    }

    private fun parseCoordinatePair(
        xValue: String,
        yValue: String,
        actionType: ActionType,
        isEnd: Boolean = false,
    ): ScreenPoint? {
        val trimmedX = xValue.trim()
        val trimmedY = yValue.trim()
        if (trimmedX.isEmpty() && trimmedY.isEmpty()) {
            return null
        }
        val x = trimmedX.toIntOrNull()
        val y = trimmedY.toIntOrNull()
        if (x == null || y == null) {
            val messageRes = when {
                actionType == ActionType.SWIPE && isEnd -> R.string.validation_swipe_end_coordinate_invalid
                actionType == ActionType.SWIPE -> R.string.validation_swipe_start_coordinate_invalid
                else -> R.string.validation_manual_coordinate_invalid
            }
            updateState { copy(validationMessageRes = messageRes) }
            return null
        }
        return ScreenPoint(x, y)
    }

    private fun loadTask() {
        viewModelScope.launch {
            val taskWithSteps = taskRepository.getTask(taskId)
            if (taskWithSteps == null) {
                updateState {
                    copy(
                        isLoading = false,
                        validationMessageRes = R.string.validation_task_not_found,
                    )
                }
                return@launch
            }

            val drafts = taskWithSteps.steps
                .sortedBy { it.orderIndex }
                .map { step ->
                    val key = nextDraftKey++
                    EditableStepDraft(
                        draftKey = key,
                        stepId = step.id,
                        actionType = ActionType.fromStorage(step.actionType),
                        enabled = step.enabled,
                        x = step.x?.toString().orEmpty(),
                        y = step.y?.toString().orEmpty(),
                        endX = step.endX?.toString().orEmpty(),
                        endY = step.endY?.toString().orEmpty(),
                        intervalMs = step.intervalMs.toString(),
                        durationMs = step.durationMs.toString(),
                        repeatCount = step.repeatCount.toString(),
                        preDelayMs = step.preDelayMs.toString(),
                        postDelayMs = step.postDelayMs.toString(),
                    )
                }
                .ifEmpty {
                    listOf(defaultDraft(nextDraftKey++))
                }

            internalState.value = TaskEditUiState(
                taskId = taskWithSteps.task.id,
                createdAt = taskWithSteps.task.createdAt,
                name = taskWithSteps.task.name,
                enabled = taskWithSteps.task.enabled,
                totalRounds = taskWithSteps.task.totalRounds.toString(),
                infiniteRounds = taskWithSteps.task.infiniteRounds,
                steps = drafts,
                editingStepKey = drafts.firstOrNull()?.draftKey,
                isLoading = false,
            )
        }
    }

    private fun moveStep(
        draftKey: Long,
        direction: Int,
    ) {
        updateState {
            val currentIndex = steps.indexOfFirst { it.draftKey == draftKey }
            if (currentIndex < 0) return@updateState this
            val targetIndex = (currentIndex + direction).coerceIn(0, steps.lastIndex)
            if (targetIndex == currentIndex) return@updateState this
            val mutable = steps.toMutableList()
            val step = mutable.removeAt(currentIndex)
            mutable.add(targetIndex, step)
            copy(steps = mutable, validationMessageRes = null)
        }
    }

    private fun updateDraft(
        draftKey: Long,
        transform: EditableStepDraft.() -> EditableStepDraft,
    ) {
        updateState {
            copy(
                steps = steps.map { draft ->
                    if (draft.draftKey == draftKey) draft.transform() else draft
                },
                validationMessageRes = null,
            )
        }
    }

    private fun updateState(
        transform: TaskEditUiState.() -> TaskEditUiState,
    ) {
        internalState.value = internalState.value.transform()
    }

    private fun defaultDraft(
        draftKey: Long,
        actionType: ActionType = ActionType.TAP,
    ): EditableStepDraft {
        return EditableStepDraft(
            draftKey = draftKey,
            actionType = actionType,
            durationMs = defaultDurationFor(actionType).toString(),
        )
    }

    private fun defaultDurationFor(actionType: ActionType): Long {
        return when (actionType) {
            ActionType.TAP -> 80L
            ActionType.LONG_PRESS -> 600L
            ActionType.SWIPE -> 300L
            ActionType.WAIT -> 1000L
        }
    }

    companion object {
        fun factory(
            appContainer: AppContainer,
            taskId: Long,
        ): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return TaskEditViewModel(
                        taskId = taskId,
                        taskRepository = appContainer.taskRepository,
                        settingsRepository = appContainer.settingsRepository,
                        defaultTaskName = appContainer.appContext.getString(R.string.default_task_name),
                    ) as T
                }
            }
        }
    }
}
