package com.example.clickassist.viewmodel

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.clickassist.app.AppContainer
import com.example.clickassist.data.local.entity.TaskWithSteps
import com.example.clickassist.domain.repository.SettingsRepository
import com.example.clickassist.domain.repository.TaskRepository
import com.example.clickassist.service.runner.RunnerErrorMessageMapper
import com.example.clickassist.service.runner.RunnerProgress
import com.example.clickassist.service.runner.RunnerState
import com.example.clickassist.service.runner.TaskRunnerEngine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TaskListUiState(
    val tasks: List<TaskWithSteps> = emptyList(),
    val runnerState: RunnerState = RunnerState.IDLE,
    val runnerProgress: RunnerProgress? = null,
    @StringRes
    val runnerErrorMessageRes: Int? = null,
    val lastEditedTaskId: Long? = null,
    val isFloatingModeEnabled: Boolean = false,
    val activeFloatingTaskId: Long? = null,
    val activeFloatingTaskName: String? = null,
    val isFloatingTargetVisible: Boolean = false,
)

class TaskListViewModel(
    private val taskRepository: TaskRepository,
    private val settingsRepository: SettingsRepository,
    private val taskRunnerEngine: TaskRunnerEngine,
) : ViewModel() {
    private val baseUiState = combine(
        taskRepository.observeTasks(),
        settingsRepository.settingsFlow,
        taskRunnerEngine.runnerState,
        taskRunnerEngine.runnerProgress,
        taskRunnerEngine.runnerError,
    ) { tasks, settings, runnerState, runnerProgress, runnerError ->
        BaseTaskListState(
            tasks = tasks,
            runnerState = runnerState,
            runnerProgress = runnerProgress,
            runnerErrorMessageRes = RunnerErrorMessageMapper.map(runnerError),
            lastEditedTaskId = settings.lastEditedTaskId,
        )
    }

    val uiState: StateFlow<TaskListUiState> = combine(
        baseUiState,
        taskRunnerEngine.overlaySessionState,
    ) { baseState, overlaySession ->
        TaskListUiState(
            tasks = baseState.tasks,
            runnerState = baseState.runnerState,
            runnerProgress = baseState.runnerProgress,
            runnerErrorMessageRes = baseState.runnerErrorMessageRes,
            lastEditedTaskId = baseState.lastEditedTaskId,
            isFloatingModeEnabled = overlaySession.isFloatingModeEnabled,
            activeFloatingTaskId = overlaySession.activeTaskId,
            activeFloatingTaskName = baseState.tasks
                .firstOrNull { item -> item.task.id == overlaySession.activeTaskId }
                ?.task
                ?.name,
            isFloatingTargetVisible = overlaySession.isTargetVisible,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = TaskListUiState(),
    )

    fun enterFloatingMode(taskId: Long) {
        taskRunnerEngine.enterFloatingMode(taskId)
    }

    fun pauseTask() {
        taskRunnerEngine.pause()
    }

    fun resumeTask() {
        taskRunnerEngine.resume()
    }

    fun stopTask() {
        taskRunnerEngine.stop()
    }

    fun deleteTask(taskId: Long) {
        viewModelScope.launch {
            taskRepository.deleteTask(taskId)
        }
    }

    companion object {
        fun factory(appContainer: AppContainer): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return TaskListViewModel(
                        taskRepository = appContainer.taskRepository,
                        settingsRepository = appContainer.settingsRepository,
                        taskRunnerEngine = appContainer.taskRunnerEngine,
                    ) as T
                }
            }
        }
    }

    private data class BaseTaskListState(
        val tasks: List<TaskWithSteps> = emptyList(),
        val runnerState: RunnerState = RunnerState.IDLE,
        val runnerProgress: RunnerProgress? = null,
        @StringRes
        val runnerErrorMessageRes: Int? = null,
        val lastEditedTaskId: Long? = null,
    )
}
