package com.example.clickassist.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.clickassist.app.AppContainer
import com.example.clickassist.data.local.entity.ActionStepEntity
import com.example.clickassist.data.local.entity.TaskEntity
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
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val validationMessage: String? = null,
)

class TaskEditViewModel(
    private val taskId: Long,
    private val taskRepository: TaskRepository,
    private val settingsRepository: SettingsRepository,
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

    fun updateName(value: String) = updateState { copy(name = value, validationMessage = null) }

    fun updateEnabled(value: Boolean) = updateState { copy(enabled = value) }

    fun updateTotalRounds(value: String) = updateState { copy(totalRounds = value, validationMessage = null) }

    fun updateInfiniteRounds(value: Boolean) = updateState { copy(infiniteRounds = value) }

    fun updateX(value: String) = updateState { copy(x = value, validationMessage = null) }

    fun updateY(value: String) = updateState { copy(y = value, validationMessage = null) }

    fun updateIntervalMs(value: String) = updateState { copy(intervalMs = value, validationMessage = null) }

    fun updateRepeatCount(value: String) = updateState { copy(repeatCount = value, validationMessage = null) }

    fun updatePreDelayMs(value: String) = updateState { copy(preDelayMs = value, validationMessage = null) }

    fun updatePostDelayMs(value: String) = updateState { copy(postDelayMs = value, validationMessage = null) }

    fun saveTask() {
        val snapshot = internalState.value
        if (snapshot.isSaving) return

        val tapX = snapshot.x.toIntOrNull()
        val tapY = snapshot.y.toIntOrNull()

        if (tapX == null || tapY == null) {
            updateState { copy(validationMessage = "TAP coordinates must be integers") }
            return
        }

        val repeatCount = snapshot.repeatCount.toIntOrNull()?.coerceAtLeast(1) ?: 1
        val totalRounds = snapshot.totalRounds.toIntOrNull()?.coerceAtLeast(1) ?: 1
        val intervalMs = snapshot.intervalMs.toLongOrNull()?.coerceAtLeast(0L) ?: 300L
        val preDelayMs = snapshot.preDelayMs.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
        val postDelayMs = snapshot.postDelayMs.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
        val now = System.currentTimeMillis()

        updateState { copy(isSaving = true, validationMessage = null) }

        viewModelScope.launch {
            try {
                val task = TaskEntity(
                    id = snapshot.taskId,
                    name = snapshot.name.trim().ifEmpty { "Untitled Task" },
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
                        validationMessage = null,
                    )
                }
                _savedTaskIds.tryEmit(savedTaskId)
            } catch (throwable: Throwable) {
                updateState {
                    copy(
                        isSaving = false,
                        validationMessage = throwable.message ?: "Failed to save task",
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
                        validationMessage = "Task not found or step data is empty",
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
                    ) as T
                }
            }
        }
    }
}
