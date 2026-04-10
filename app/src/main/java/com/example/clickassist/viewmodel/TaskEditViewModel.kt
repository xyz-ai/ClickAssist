package com.example.clickassist.viewmodel

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.clickassist.R
import com.example.clickassist.app.AppContainer
import com.example.clickassist.data.local.entity.ActionStepEntity
import com.example.clickassist.data.local.entity.TaskEntity
import com.example.clickassist.domain.model.ScreenPoint
import com.example.clickassist.domain.repository.SettingsRepository
import com.example.clickassist.domain.repository.TaskRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class TaskEditUiState(
    val taskId: Long = 0L,
    val createdAt: Long = 0L,
    val name: String = "",
    val enabled: Boolean = true,
    val totalRounds: String = "1",
    val infiniteRounds: Boolean = false,
    val x: String = "",
    val y: String = "",
    val intervalMs: String = "300",
    val repeatCount: String = "1",
    val preDelayMs: String = "0",
    val postDelayMs: String = "0",
    val isCoordinatePickerVisible: Boolean = false,
    val isAdvancedSettingsExpanded: Boolean = false,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    @StringRes
    val validationMessageRes: Int? = null,
) {
    val tapPosition: ScreenPoint?
        get() {
            val tapX = x.toIntOrNull()
            val tapY = y.toIntOrNull()
            return if (tapX != null && tapY != null) {
                ScreenPoint(x = tapX, y = tapY)
            } else {
                null
            }
        }

    val isTapPositionSet: Boolean
        get() = tapPosition != null
}

class TaskEditViewModel(
    private val taskId: Long,
    private val taskRepository: TaskRepository,
    private val settingsRepository: SettingsRepository,
    private val defaultTaskName: String,
) : ViewModel() {
    private val internalState = MutableStateFlow(
        TaskEditUiState(isLoading = taskId != 0L),
    )
    val uiState: StateFlow<TaskEditUiState> = internalState.asStateFlow()

    private val _savedTaskIds = MutableSharedFlow<Long>(extraBufferCapacity = 1)
    val savedTaskIds = _savedTaskIds.asSharedFlow()

    init {
        if (taskId != 0L) {
            loadTask()
        }
    }

    fun updateName(value: String) = updateState { copy(name = value, validationMessageRes = null) }

    fun updateEnabled(value: Boolean) = updateState { copy(enabled = value) }

    fun updateTotalRounds(value: String) = updateState { copy(totalRounds = value, validationMessageRes = null) }

    fun updateInfiniteRounds(value: Boolean) = updateState { copy(infiniteRounds = value) }

    fun updateX(value: String) = updateState { copy(x = value, validationMessageRes = null) }

    fun updateY(value: String) = updateState { copy(y = value, validationMessageRes = null) }

    fun updateIntervalMs(value: String) = updateState { copy(intervalMs = value, validationMessageRes = null) }

    fun updateRepeatCount(value: String) = updateState { copy(repeatCount = value, validationMessageRes = null) }

    fun updatePreDelayMs(value: String) = updateState { copy(preDelayMs = value, validationMessageRes = null) }

    fun updatePostDelayMs(value: String) = updateState { copy(postDelayMs = value, validationMessageRes = null) }

    fun openCoordinatePicker() = updateState {
        copy(
            isCoordinatePickerVisible = true,
            validationMessageRes = null,
        )
    }

    fun dismissCoordinatePicker() = updateState {
        copy(isCoordinatePickerVisible = false)
    }

    fun applyCoordinateSelection(point: ScreenPoint) = updateState {
        copy(
            x = point.x.toString(),
            y = point.y.toString(),
            isCoordinatePickerVisible = false,
            validationMessageRes = null,
        )
    }

    fun toggleAdvancedSettings() = updateState {
        copy(isAdvancedSettingsExpanded = !isAdvancedSettingsExpanded)
    }

    fun saveTask() {
        val snapshot = internalState.value
        if (snapshot.isSaving) return

        val xValue = snapshot.x.trim()
        val yValue = snapshot.y.trim()
        val tapX = xValue.toIntOrNull()
        val tapY = yValue.toIntOrNull()
        val hasManualTapInput = xValue.isNotEmpty() || yValue.isNotEmpty()

        if (hasManualTapInput && (tapX == null || tapY == null)) {
            updateState { copy(validationMessageRes = R.string.validation_manual_coordinate_invalid) }
            return
        }

        val repeatCount = snapshot.repeatCount.toIntOrNull()?.coerceAtLeast(1) ?: 1
        val totalRounds = snapshot.totalRounds.toIntOrNull()?.coerceAtLeast(1) ?: 1
        val intervalMs = snapshot.intervalMs.toLongOrNull()?.coerceAtLeast(0L) ?: 300L
        val preDelayMs = snapshot.preDelayMs.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
        val postDelayMs = snapshot.postDelayMs.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
        val now = System.currentTimeMillis()

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

                val step = ActionStepEntity(
                    taskId = snapshot.taskId,
                    orderIndex = 0,
                    actionType = ActionStepEntity.ACTION_TAP,
                    x = tapX,
                    y = tapY,
                    intervalMs = intervalMs,
                    durationMs = 80L,
                    repeatCount = repeatCount,
                    preDelayMs = preDelayMs,
                    postDelayMs = postDelayMs,
                    enabled = true,
                )

                val savedTaskId = taskRepository.saveTask(task, listOf(step))
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

    private fun loadTask() {
        viewModelScope.launch {
            val taskWithSteps = taskRepository.getTask(taskId)
            val firstStep = taskWithSteps?.steps?.firstOrNull()

            if (taskWithSteps == null || firstStep == null) {
                updateState {
                    copy(
                        isLoading = false,
                        validationMessageRes = R.string.validation_task_not_found,
                    )
                }
                return@launch
            }

            internalState.value = TaskEditUiState(
                taskId = taskWithSteps.task.id,
                createdAt = taskWithSteps.task.createdAt,
                name = taskWithSteps.task.name,
                enabled = taskWithSteps.task.enabled,
                totalRounds = taskWithSteps.task.totalRounds.toString(),
                infiniteRounds = taskWithSteps.task.infiniteRounds,
                x = firstStep.x?.toString().orEmpty(),
                y = firstStep.y?.toString().orEmpty(),
                intervalMs = firstStep.intervalMs.toString(),
                repeatCount = firstStep.repeatCount.toString(),
                preDelayMs = firstStep.preDelayMs.toString(),
                postDelayMs = firstStep.postDelayMs.toString(),
                isLoading = false,
            )
        }
    }

    private fun updateState(transform: TaskEditUiState.() -> TaskEditUiState) {
        internalState.value = internalState.value.transform()
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
