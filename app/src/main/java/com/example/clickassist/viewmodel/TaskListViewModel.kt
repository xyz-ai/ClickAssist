package com.example.clickassist.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.clickassist.app.AppContainer
import com.example.clickassist.data.local.entity.TaskWithSteps
import com.example.clickassist.domain.repository.SettingsRepository
import com.example.clickassist.domain.repository.TaskRepository
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
    val runnerErrorMessage: String? = null,
    val lastEditedTaskId: Long? = null,
)

class TaskListViewModel(
    private val taskRepository: TaskRepository,
    private val settingsRepository: SettingsRepository,
    private val taskRunnerEngine: TaskRunnerEngine,
) : ViewModel() {
    val uiState: StateFlow<TaskListUiState> = combine(
        taskRepository.observeTasks(),
        settingsRepository.settingsFlow,
        taskRunnerEngine.runnerState,
        taskRunnerEngine.runnerProgress,
        taskRunnerEngine.errorMessage,
    ) { tasks, settings, runnerState, runnerProgress, runnerError ->
        TaskListUiState(
            tasks = tasks,
            runnerState = runnerState,
            runnerProgress = runnerProgress,
            runnerErrorMessage = runnerError,
            lastEditedTaskId = settings.lastEditedTaskId,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = TaskListUiState(),
    )

    fun startTask(taskId: Long) {
        taskRunnerEngine.start(taskId)
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
}
