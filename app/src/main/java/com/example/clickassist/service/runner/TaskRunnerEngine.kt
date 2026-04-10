package com.example.clickassist.service.runner

import android.content.Context
import com.example.clickassist.data.local.entity.ActionStepEntity
import com.example.clickassist.data.local.entity.TaskWithSteps
import com.example.clickassist.domain.model.ScreenPoint
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
import java.util.concurrent.atomic.AtomicReference

class TaskRunnerEngine(
    appContext: Context,
    private val taskRepository: TaskRepository,
    private val overlayController: OverlayController,
    private val taskStartValidator: TaskStartValidator,
) {
    private val appContext = appContext.applicationContext
    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _runnerState = MutableStateFlow(RunnerState.IDLE)
    val runnerState: StateFlow<RunnerState> = _runnerState.asStateFlow()

    private val _runnerProgress = MutableStateFlow<RunnerProgress?>(null)
    val runnerProgress: StateFlow<RunnerProgress?> = _runnerProgress.asStateFlow()

    private val _runnerError = MutableStateFlow<RunnerError?>(null)
    val runnerError: StateFlow<RunnerError?> = _runnerError.asStateFlow()

    private var runnerJob: Job? = null
    private var activeTapTaskId: Long? = null
    private var activeTapStepId: Long? = null
    private val activeTapPoint = AtomicReference<ScreenPoint?>(null)

    fun start(taskId: Long) {
        val currentState = _runnerState.value
        if (currentState == RunnerState.RUNNING || currentState == RunnerState.PAUSED) return

        runnerJob?.cancel()
        clearActiveTapSessionLocal()
        publishProgress(null)
        publishError(null)
        publishState(RunnerState.IDLE)

        runnerJob = engineScope.launch {
            overlayController.hideTarget()

            val taskWithSteps = taskRepository.getTask(taskId)
            if (taskWithSteps == null) {
                publishError(RunnerError.TaskNotFound(taskId))
                runnerJob = null
                return@launch
            }

            if (!overlayController.hasPermission()) {
                publishError(RunnerError.OverlayPermissionDenied)
                runnerJob = null
                return@launch
            }

            val activeTapStep = taskWithSteps.steps
                .filter { step ->
                    step.enabled && step.actionType == ActionStepEntity.ACTION_TAP
                }
                .sortedBy { step -> step.orderIndex }
                .firstOrNull()

            if (activeTapStep == null) {
                publishError(RunnerError.NoExecutableSteps)
                runnerJob = null
                return@launch
            }

            val initialPoint = overlayController.resolveInitialPoint(
                preferredX = activeTapStep.x,
                preferredY = activeTapStep.y,
            )
            val normalizedTaskWithSteps = taskWithSteps.withNormalizedTapPoint(
                stepId = activeTapStep.id,
                point = initialPoint,
            )

            val validationError = taskStartValidator.validate(
                taskWithSteps = normalizedTaskWithSteps,
                isAccessibilityEnabled = MyAccessibilityService.isEnabled(appContext),
            )
            if (validationError != null) {
                publishError(validationError)
                runnerJob = null
                return@launch
            }

            activeTapTaskId = normalizedTaskWithSteps.task.id
            activeTapStepId = activeTapStep.id
            activeTapPoint.set(initialPoint)

            val overlayShown = overlayController.showTarget(
                initialPoint = initialPoint,
                onPointChanged = { point ->
                    activeTapPoint.set(point)
                },
                onDragEnd = { point ->
                    activeTapPoint.set(point)
                    persistTapPointAsync(
                        taskId = normalizedTaskWithSteps.task.id,
                        stepId = activeTapStep.id,
                        point = point,
                    )
                },
            )
            if (!overlayShown) {
                clearActiveTapSessionLocal()
                publishError(
                    if (!overlayController.hasPermission()) {
                        RunnerError.OverlayPermissionDenied
                    } else {
                        RunnerError.Unknown("Unable to show overlay target")
                    },
                )
                runnerJob = null
                return@launch
            }

            persistTapPoint(
                taskId = normalizedTaskWithSteps.task.id,
                stepId = activeTapStep.id,
                point = initialPoint,
            )

            publishProgress(
                RunnerProgress(
                    taskId = normalizedTaskWithSteps.task.id,
                    currentRoundIndex = 0,
                    currentStepIndex = 0,
                    currentStepRepeatIndex = 0,
                ),
            )
            publishState(RunnerState.RUNNING)

            try {
                executeTask(normalizedTaskWithSteps)
            } catch (_: CancellationException) {
                if (_runnerState.value == RunnerState.STOPPING) {
                    resetToIdle()
                }
            } catch (throwable: Throwable) {
                publishError(mapThrowableToRunnerError(throwable))
                publishState(RunnerState.ERROR)
                clearActiveTapSession()
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
                    engineScope.launch {
                        resetToIdle()
                    }
                } else {
                    runnerJob?.cancel()
                }
            }
        }
    }

    fun release() {
        runnerJob?.cancel()
        clearActiveTapSessionLocal()
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

    private suspend fun executeTask(
        taskWithSteps: TaskWithSteps,
    ) {
        val task = taskWithSteps.task
        val enabledSteps = taskWithSteps.steps
            .filter { it.enabled }
            .sortedBy { it.orderIndex }

        var currentRound = 0
        while (task.infiniteRounds || currentRound < task.totalRounds.coerceAtLeast(1)) {
            for ((stepIndex, step) in enabledSteps.withIndex()) {
                if (!awaitRunnableState()) return

                if (!waitWithControl(step.preDelayMs)) return

                val repeatCount = step.repeatCount.coerceAtLeast(1)
                for (repeatIndex in 0 until repeatCount) {
                    publishProgress(
                        RunnerProgress(
                            taskId = task.id,
                            currentRoundIndex = currentRound,
                            currentStepIndex = stepIndex,
                            currentStepRepeatIndex = repeatIndex,
                        ),
                    )

                    if (!awaitRunnableState()) return

                    val dispatched = dispatchStep(step)
                    if (!dispatched) {
                        publishError(RunnerError.GestureDispatchFailed)
                        publishState(RunnerState.ERROR)
                        clearActiveTapSession()
                        return
                    }

                    val hasNextRepeat = repeatIndex < repeatCount - 1
                    if (hasNextRepeat && !waitWithControl(step.intervalMs)) return
                }

                if (!waitWithControl(step.postDelayMs)) return
            }
            currentRound += 1
        }

        publishState(RunnerState.COMPLETED)
        clearActiveTapSession()
    }

    private suspend fun dispatchStep(
        step: ActionStepEntity,
    ): Boolean {
        val runtimeStep = resolveRuntimeStep(step)

        return when (runtimeStep.actionType) {
            ActionStepEntity.ACTION_TAP -> {
                val x = runtimeStep.x ?: return false
                val y = runtimeStep.y ?: return false
                val service = MyAccessibilityService.current() ?: return false
                service.dispatchTap(
                    x = x,
                    y = y,
                    durationMs = runtimeStep.durationMs,
                )
            }

            ActionStepEntity.ACTION_SWIPE -> {
                val startX = runtimeStep.x ?: return false
                val startY = runtimeStep.y ?: return false
                val endX = runtimeStep.endX ?: return false
                val endY = runtimeStep.endY ?: return false
                val service = MyAccessibilityService.current() ?: return false
                service.dispatchSwipe(
                    startX = startX,
                    startY = startY,
                    endX = endX,
                    endY = endY,
                    durationMs = runtimeStep.durationMs,
                )
            }

            ActionStepEntity.ACTION_WAIT -> waitWithControl(
                runtimeStep.durationMs.coerceAtLeast(runtimeStep.intervalMs),
            )

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

    private fun mapThrowableToRunnerError(
        throwable: Throwable,
    ): RunnerError {
        return when (throwable) {
            is CancellationException -> RunnerError.Unknown(throwable.message)
            else -> RunnerError.Unknown(throwable.message)
        }
    }

    private fun publishState(state: RunnerState) {
        _runnerState.value = state
    }

    private fun publishProgress(progress: RunnerProgress?) {
        _runnerProgress.value = progress
    }

    private fun publishError(error: RunnerError?) {
        _runnerError.value = error
    }

    private fun resolveRuntimeStep(
        step: ActionStepEntity,
    ): ActionStepEntity {
        val runtimePoint = activeTapPoint.get()
        return if (
            step.actionType == ActionStepEntity.ACTION_TAP &&
            step.id != 0L &&
            step.id == activeTapStepId &&
            runtimePoint != null &&
            activeTapTaskId != null
        ) {
            step.copy(
                x = runtimePoint.x,
                y = runtimePoint.y,
            )
        } else {
            step
        }
    }

    private suspend fun clearActiveTapSession() {
        overlayController.hideTarget()
        clearActiveTapSessionLocal()
    }

    private fun clearActiveTapSessionLocal() {
        activeTapTaskId = null
        activeTapStepId = null
        activeTapPoint.set(null)
    }

    private suspend fun persistTapPoint(
        taskId: Long,
        stepId: Long,
        point: ScreenPoint,
    ) {
        runCatching {
            taskRepository.updateTapStepPosition(
                taskId = taskId,
                stepId = stepId,
                x = point.x,
                y = point.y,
            )
        }
    }

    private fun persistTapPointAsync(
        taskId: Long,
        stepId: Long,
        point: ScreenPoint,
    ) {
        engineScope.launch {
            persistTapPoint(
                taskId = taskId,
                stepId = stepId,
                point = point,
            )
        }
    }

    private suspend fun resetToIdle() {
        clearActiveTapSession()
        runnerJob = null
        publishProgress(null)
        publishState(RunnerState.IDLE)
    }

    private fun TaskWithSteps.withNormalizedTapPoint(
        stepId: Long,
        point: ScreenPoint,
    ): TaskWithSteps {
        return copy(
            steps = steps.map { step ->
                if (step.id == stepId) {
                    step.copy(
                        x = point.x,
                        y = point.y,
                    )
                } else {
                    step
                }
            },
        )
    }

    private companion object {
        const val CONTROL_POLL_INTERVAL_MS = 100L
    }
}
