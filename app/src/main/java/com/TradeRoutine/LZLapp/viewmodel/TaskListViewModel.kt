package com.TradeRoutine.LZLapp.viewmodel

import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.TradeRoutine.LZLapp.R
import com.TradeRoutine.LZLapp.app.AppContainer
import com.TradeRoutine.LZLapp.data.local.entity.TaskWithSteps
import com.TradeRoutine.LZLapp.domain.repository.SettingsRepository
import com.TradeRoutine.LZLapp.domain.repository.TaskRepository
import com.TradeRoutine.LZLapp.service.runner.RunnerErrorMessageMapper
import com.TradeRoutine.LZLapp.service.runner.RunnerProgress
import com.TradeRoutine.LZLapp.service.runner.RunnerState
import com.TradeRoutine.LZLapp.service.runner.TaskRunnerEngine
import kotlinx.coroutines.flow.MutableStateFlow
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
    val isFloatingMultiPointMode: Boolean = false,
    val floatingStepCount: Int = 0,
    val floatingSelectedStepOrder: Int? = null,
    val floatingSelectedStepActionType: String? = null,
    @StringRes
    val pendingMessageRes: Int? = null,
)

class TaskListViewModel(
    private val taskRepository: TaskRepository,
    private val settingsRepository: SettingsRepository,
    private val taskRunnerEngine: TaskRunnerEngine,
) : ViewModel() {
    private val pendingMessageRes = MutableStateFlow<Int?>(null)
    private val lastSavedTaskId = MutableStateFlow<Long?>(null)

    private val baseUiState = combine(
        taskRepository.observeTasks(),
        settingsRepository.settingsFlow,
        taskRunnerEngine.runnerState,
        taskRunnerEngine.runnerProgress,
        taskRunnerEngine.runnerError,
    ) { tasks, settings, runnerState, runnerProgress, runnerError ->
        val recentSavedTaskId = lastSavedTaskId.value
        Log.i(
            TAG,
            "list refreshed count=${tasks.size} lastEditedTaskId=${settings.lastEditedTaskId} recentSavedTaskId=$recentSavedTaskId",
        )
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
        pendingMessageRes,
    ) { baseState, overlaySession, messageRes ->
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
            isFloatingMultiPointMode = overlaySession.isMultiPointMode,
            floatingStepCount = overlaySession.stepCount,
            floatingSelectedStepOrder = overlaySession.selectedStepOrder,
            floatingSelectedStepActionType = overlaySession.selectedStepActionType,
            pendingMessageRes = messageRes,
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

    fun onTaskSavedResult(taskId: Long) {
        Log.i(TAG, "received task save result taskId=$taskId")
        lastSavedTaskId.value = taskId
        pendingMessageRes.value = R.string.task_list_save_result_refreshed
    }

    fun consumePendingMessage() {
        pendingMessageRes.value = null
    }

    private data class BaseTaskListState(
        val tasks: List<TaskWithSteps> = emptyList(),
        val runnerState: RunnerState = RunnerState.IDLE,
        val runnerProgress: RunnerProgress? = null,
        @StringRes
        val runnerErrorMessageRes: Int? = null,
        val lastEditedTaskId: Long? = null,
    )

    companion object {
        private const val TAG = "TaskListRefresh"

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
