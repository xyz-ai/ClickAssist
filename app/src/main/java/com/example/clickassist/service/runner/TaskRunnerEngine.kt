package com.example.clickassist.service.runner

import com.example.clickassist.data.local.entity.ActionStepEntity
import com.example.clickassist.domain.repository.TaskRepository
import com.example.clickassist.service.accessibility.MyAccessibilityService
import com.example.clickassist.service.overlay.OverlayController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TaskRunnerEngine(
    private val taskRepository: TaskRepository,
    private val overlayController: OverlayController,
) {
    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _runnerState = MutableStateFlow(RunnerState.IDLE)
    val runnerState: StateFlow<RunnerState> = _runnerState.asStateFlow()

    private val _runnerProgress = MutableStateFlow<RunnerProgress?>(null)
    val runnerProgress: StateFlow<RunnerProgress?> = _runnerProgress.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var runnerJob: Job? = null

    fun start(taskId: Long) {
        val currentState = _runnerState.value
        if (currentState == RunnerState.RUNNING || currentState == RunnerState.PAUSED) return

        runnerJob?.cancel()
        _errorMessage.value = null
        publishProgress(
            RunnerProgress(
                taskId = taskId,
                currentRoundIndex = 0,
                currentStepIndex = 0,
                currentStepRepeatIndex = 0,
            ),
        )
        publishState(RunnerState.RUNNING)

        runnerJob = engineScope.launch {
            try {
                executeTask(taskId)
            } catch (_: CancellationException) {
                if (_runnerState.value == RunnerState.STOPPING) {
                    resetToIdle()
                }
            } catch (throwable: Throwable) {
                _errorMessage.value = throwable.message ?: "Task execution failed"
                publishState(RunnerState.ERROR)
            } finally {
                if (_runnerState.value != RunnerState.STOPPING) {
                    runnerJob = null
                }
            }
        }
    }

    fun pause() {
        if (_runnerState.value == RunnerState.RUNNING) {
            publishState(RunnerState.PAUSED)
        }
    }

    fun resume() {
        if (_runnerState.value == RunnerState.PAUSED) {
            publishState(RunnerState.RUNNING)
        }
    }

    fun stop() {
        when (_runnerState.value) {
            RunnerState.IDLE -> return
            RunnerState.RUNNING,
            RunnerState.PAUSED,
            RunnerState.STOPPING,
            RunnerState.COMPLETED,
            RunnerState.ERROR,
            -> {
                publishState(RunnerState.STOPPING)
                if (runnerJob == null || runnerJob?.isCompleted == true) {
                    resetToIdle()
                } else {
                    runnerJob?.cancel()
                }
            }
        }
    }

    fun release() {
        runnerJob?.cancel()
        engineScope.cancel()
        overlayController.release()
    }

    fun requestCoordinateRecorder() {
        overlayController.requestCoordinateRecorder()
    }

    fun requestJsonExport() {
        overlayController.requestJsonExport()
    }

    fun requestTaskTemplateClone() {
        // TODO: reserve task template clone entry
    }

    private suspend fun executeTask(taskId: Long) {
        val taskWithSteps = taskRepository.getTask(taskId)
            ?: error("Task not found")

        val task = taskWithSteps.task
        val enabledSteps = taskWithSteps.steps
            .filter { it.enabled }
            .sortedBy { it.orderIndex }

        if (!task.enabled) error("Task is disabled")
        if (enabledSteps.isEmpty()) error("At least one enabled step is required")

        var currentRound = 0
        while (task.infiniteRounds || currentRound < task.totalRounds.coerceAtLeast(1)) {
            for ((stepIndex, step) in enabledSteps.withIndex()) {
                if (!awaitRunnableState()) return

                if (!waitWithControl(step.preDelayMs)) return

                val repeatCount = step.repeatCount.coerceAtLeast(1)
                for (repeatIndex in 0 until repeatCount) {
                    publishProgress(
                        RunnerProgress(
                            taskId = taskId,
                            currentRoundIndex = currentRound,
                            currentStepIndex = stepIndex,
                            currentStepRepeatIndex = repeatIndex,
                        ),
                    )

                    if (!awaitRunnableState()) return

                    val dispatched = dispatchStep(step)
                    if (!dispatched) {
                        error("Gesture dispatch failed. Check accessibility access and coordinates.")
                    }

                    val hasNextRepeat = repeatIndex < repeatCount - 1
                    if (hasNextRepeat && !waitWithControl(step.intervalMs)) return
                }

                if (!waitWithControl(step.postDelayMs)) return
            }
            currentRound += 1
        }

        publishState(RunnerState.COMPLETED)
    }

    private suspend fun dispatchStep(
        step: ActionStepEntity,
    ): Boolean {
        return when (step.actionType) {
            ActionStepEntity.ACTION_TAP -> {
                val x = step.x ?: return false
                val y = step.y ?: return false
                val service = MyAccessibilityService.current() ?: return false
                service.dispatchTap(
                    x = x,
                    y = y,
                    durationMs = step.durationMs,
                )
            }

            ActionStepEntity.ACTION_SWIPE -> {
                val startX = step.x ?: return false
                val startY = step.y ?: return false
                val endX = step.endX ?: return false
                val endY = step.endY ?: return false
                val service = MyAccessibilityService.current() ?: return false
                service.dispatchSwipe(
                    startX = startX,
                    startY = startY,
                    endX = endX,
                    endY = endY,
                    durationMs = step.durationMs,
                )
            }

            ActionStepEntity.ACTION_WAIT -> waitWithControl(step.durationMs.coerceAtLeast(step.intervalMs))
            else -> false
        }
    }

    private suspend fun awaitRunnableState(): Boolean {
        while (true) {
            when (_runnerState.value) {
                RunnerState.RUNNING -> return true
                RunnerState.PAUSED -> delay(CONTROL_POLL_INTERVAL_MS)
                RunnerState.STOPPING -> return false
                RunnerState.IDLE,
                RunnerState.COMPLETED,
                RunnerState.ERROR,
                -> return false
            }
        }
    }

    private suspend fun waitWithControl(durationMs: Long): Boolean {
        var remaining = durationMs.coerceAtLeast(0L)
        while (remaining > 0L) {
            if (!awaitRunnableState()) return false
            val nextDelay = remaining.coerceAtMost(CONTROL_POLL_INTERVAL_MS)
            delay(nextDelay)
            remaining -= nextDelay
        }
        return true
    }

    private fun publishState(state: RunnerState) {
        _runnerState.value = state
        when (state) {
            RunnerState.IDLE -> overlayController.hide()
            else -> overlayController.show(state = state, progress = _runnerProgress.value)
        }
    }

    private fun publishProgress(progress: RunnerProgress?) {
        _runnerProgress.value = progress
        if (_runnerState.value != RunnerState.IDLE) {
            overlayController.show(state = _runnerState.value, progress = progress)
        }
    }

    private fun resetToIdle() {
        runnerJob = null
        _errorMessage.value = null
        publishProgress(null)
        publishState(RunnerState.IDLE)
    }

    private companion object {
        const val CONTROL_POLL_INTERVAL_MS = 100L
    }
}
