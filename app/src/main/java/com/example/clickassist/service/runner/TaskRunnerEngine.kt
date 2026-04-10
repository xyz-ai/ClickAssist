package com.example.clickassist.service.runner

import android.content.Context
import com.example.clickassist.data.local.entity.ActionStepEntity
import com.example.clickassist.data.local.entity.TaskWithSteps
import com.example.clickassist.domain.model.ScreenPoint
import com.example.clickassist.domain.repository.TaskRepository
import com.example.clickassist.service.accessibility.MyAccessibilityService
import com.example.clickassist.service.overlay.OverlayController
import com.example.clickassist.service.overlay.OverlayToolbarCallbacks
import com.example.clickassist.service.overlay.OverlayToolbarUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.coroutineContext

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

    private val _overlaySessionState = MutableStateFlow(OverlaySessionState())
    val overlaySessionState: StateFlow<OverlaySessionState> = _overlaySessionState.asStateFlow()

    private var runnerJob: Job? = null
    private var activeTapTaskId: Long? = null
    private var activeTapStepId: Long? = null
    private val activeTapPoint = AtomicReference<ScreenPoint?>(null)

    init {
        overlayController.bindToolbarCallbacks(
            OverlayToolbarCallbacks(
                onStartRequested = ::handleToolbarStartRequested,
                onPauseRequested = ::pause,
                onStopRequested = ::stop,
                onTargetToggleRequested = ::toggleTargetVisibility,
                onCloseRequested = ::exitFloatingMode,
            ),
        )
    }

    fun enterFloatingMode(taskId: Long) {
        engineScope.launch {
            enterFloatingModeInternal(taskId)
        }
    }

    fun startActiveTask() {
        when (_runnerState.value) {
            RunnerState.PAUSED -> resume()
            RunnerState.RUNNING,
            RunnerState.STOPPING,
            -> Unit

            RunnerState.IDLE,
            RunnerState.COMPLETED,
            RunnerState.ERROR,
            -> {
                engineScope.launch {
                    startActiveTaskInternal()
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
        engineScope.launch {
            when (_runnerState.value) {
                RunnerState.IDLE -> Unit
                RunnerState.RUNNING,
                RunnerState.PAUSED,
                RunnerState.STOPPING,
                -> stopRunnerJobAndResetState(clearError = true)

                RunnerState.COMPLETED,
                RunnerState.ERROR,
                -> resetRunnerToIdle(clearError = true)
            }
        }
    }

    fun toggleTargetVisibility() {
        engineScope.launch {
            toggleTargetVisibilityInternal()
        }
    }

    fun exitFloatingMode() {
        engineScope.launch {
            stopRunnerJobAndResetState(clearError = true)
            overlayController.hideFloatingMode(clearTargetPoint = true)
            clearActiveSessionLocal()
            publishOverlaySessionState(OverlaySessionState())
        }
    }

    fun release() {
        runnerJob?.cancel()
        clearActiveSessionLocal()
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

    private fun handleToolbarStartRequested() {
        when (_runnerState.value) {
            RunnerState.PAUSED -> resume()
            RunnerState.RUNNING,
            RunnerState.STOPPING,
            -> Unit

            RunnerState.IDLE,
            RunnerState.COMPLETED,
            RunnerState.ERROR,
            -> startActiveTask()
        }
    }

    private suspend fun enterFloatingModeInternal(
        taskId: Long,
    ) {
        val taskWithSteps = taskRepository.getTask(taskId)
        if (taskWithSteps == null) {
            publishError(RunnerError.TaskNotFound(taskId), showToast = true)
            return
        }

        if (!overlayController.hasPermission()) {
            publishError(RunnerError.OverlayPermissionDenied, showToast = true)
            return
        }

        val floatingModeError = taskStartValidator.validateFloatingMode(taskWithSteps)
        if (floatingModeError != null) {
            publishError(floatingModeError, showToast = true)
            return
        }

        val activeTapStep = findActiveTapStep(taskWithSteps)
        if (activeTapStep == null) {
            publishError(RunnerError.NoExecutableSteps, showToast = true)
            return
        }

        stopRunnerJobAndResetState(clearError = true)
        overlayController.hideFloatingMode(clearTargetPoint = true)
        clearActiveSessionLocal()
        publishOverlaySessionState(OverlaySessionState())

        val initialPoint = overlayController.resolveInitialPoint(
            preferredX = activeTapStep.x,
            preferredY = activeTapStep.y,
        )
        val normalizedTaskWithSteps = taskWithSteps.withNormalizedTapPoint(
            stepId = activeTapStep.id,
            point = initialPoint,
        )

        activeTapTaskId = normalizedTaskWithSteps.task.id
        activeTapStepId = activeTapStep.id
        activeTapPoint.set(initialPoint)

        val overlayShown = overlayController.showFloatingMode(
            initialPoint = initialPoint,
            targetVisible = true,
            toolbarUiState = createToolbarUiState(targetVisible = true),
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
            clearActiveSessionLocal()
            publishError(
                error = if (!overlayController.hasPermission()) {
                    RunnerError.OverlayPermissionDenied
                } else {
                    RunnerError.Unknown("Unable to show floating overlay")
                },
                showToast = true,
            )
            return
        }

        persistTapPoint(
            taskId = normalizedTaskWithSteps.task.id,
            stepId = activeTapStep.id,
            point = initialPoint,
        )
        publishError(null)
        publishOverlaySessionState(
            OverlaySessionState(
                isFloatingModeEnabled = true,
                activeTaskId = normalizedTaskWithSteps.task.id,
                isTargetVisible = true,
            ),
        )
    }

    private suspend fun startActiveTaskInternal() {
        val sessionTaskId = activeTapTaskId
        if (sessionTaskId == null || !_overlaySessionState.value.isFloatingModeEnabled) {
            publishError(RunnerError.NoTaskSelected, showToast = true)
            return
        }

        val taskWithSteps = taskRepository.getTask(sessionTaskId)
        if (taskWithSteps == null) {
            publishError(RunnerError.TaskNotFound(sessionTaskId), showToast = true)
            return
        }

        val activeTapStep = resolveSessionTapStep(taskWithSteps)
        if (activeTapStep == null) {
            publishError(RunnerError.NoExecutableSteps, showToast = true)
            return
        }

        val runtimePoint = activeTapPoint.get()
            ?: activeTapStep.x?.let { x ->
                activeTapStep.y?.let { y ->
                    ScreenPoint(x = x, y = y)
                }
            }

        if (runtimePoint == null) {
            publishError(RunnerError.TapPointNotSet, showToast = true)
            return
        }

        val resolvedPoint = overlayController.resolveInitialPoint(
            preferredX = runtimePoint.x,
            preferredY = runtimePoint.y,
        )
        val normalizedTaskWithSteps = taskWithSteps.withNormalizedTapPoint(
            stepId = activeTapStep.id,
            point = resolvedPoint,
        )
        val validationError = taskStartValidator.validateStart(
            taskWithSteps = normalizedTaskWithSteps,
            isAccessibilityEnabled = MyAccessibilityService.isEnabled(appContext),
        )
        if (validationError != null) {
            publishError(validationError, showToast = true)
            return
        }

        activeTapStepId = activeTapStep.id
        activeTapPoint.set(resolvedPoint)
        if (_overlaySessionState.value.isTargetVisible) {
            overlayController.updateTarget(resolvedPoint)
        }
        persistTapPoint(
            taskId = normalizedTaskWithSteps.task.id,
            stepId = activeTapStep.id,
            point = resolvedPoint,
        )

        publishError(null)
        runnerJob?.cancelAndJoin()
        launchRunner(normalizedTaskWithSteps)
    }

    private fun launchRunner(
        taskWithSteps: TaskWithSteps,
    ) {
        runnerJob = engineScope.launch {
            publishProgress(
                RunnerProgress(
                    taskId = taskWithSteps.task.id,
                    currentRoundIndex = 0,
                    currentStepIndex = 0,
                    currentStepRepeatIndex = 0,
                ),
            )
            publishState(RunnerState.RUNNING)

            try {
                executeTask(taskWithSteps)
                if (_runnerState.value == RunnerState.RUNNING) {
                    publishProgress(null)
                    publishState(RunnerState.COMPLETED)
                }
            } catch (cancellationException: CancellationException) {
                throw cancellationException
            } catch (throwable: Throwable) {
                publishError(mapThrowableToRunnerError(throwable), showToast = true)
                publishProgress(null)
                publishState(RunnerState.ERROR)
            } finally {
                if (runnerJob === coroutineContext[Job]) {
                    runnerJob = null
                }
            }
        }
    }

    private suspend fun toggleTargetVisibilityInternal() {
        val sessionState = _overlaySessionState.value
        if (!sessionState.isFloatingModeEnabled) {
            publishError(RunnerError.NoTaskSelected, showToast = true)
            return
        }

        val targetVisible = !sessionState.isTargetVisible
        val point = activeTapPoint.get() ?: overlayController.currentTargetPoint()
        if (targetVisible && point == null) {
            publishError(RunnerError.TapPointNotSet, showToast = true)
            return
        }
        val updated = overlayController.setTargetVisibility(
            isVisible = targetVisible,
            point = point,
        )
        if (!updated) {
            publishError(
                error = if (!overlayController.hasPermission()) {
                    RunnerError.OverlayPermissionDenied
                } else {
                    RunnerError.Unknown("Unable to update target visibility")
                },
                showToast = true,
            )
            return
        }

        publishOverlaySessionState(
            sessionState.copy(isTargetVisible = targetVisible),
        )
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
                        publishError(RunnerError.GestureDispatchFailed, showToast = true)
                        publishProgress(null)
                        publishState(RunnerState.ERROR)
                        return
                    }

                    val hasNextRepeat = repeatIndex < repeatCount - 1
                    if (hasNextRepeat && !waitWithControl(step.intervalMs)) return
                }

                if (!waitWithControl(step.postDelayMs)) return
            }
            currentRound += 1
        }
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

    private suspend fun stopRunnerJobAndResetState(
        clearError: Boolean,
    ) {
        val job = runnerJob
        if (job != null) {
            publishState(RunnerState.STOPPING)
            job.cancelAndJoin()
            runnerJob = null
        }
        resetRunnerToIdle(clearError = clearError)
    }

    private fun resetRunnerToIdle(
        clearError: Boolean,
    ) {
        publishProgress(null)
        publishState(RunnerState.IDLE)
        if (clearError) {
            publishError(null)
        }
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
        syncToolbarStateAsync()
    }

    private fun publishProgress(progress: RunnerProgress?) {
        _runnerProgress.value = progress
    }

    private fun publishError(
        error: RunnerError?,
        showToast: Boolean = false,
    ) {
        _runnerError.value = error
        if (showToast) {
            RunnerErrorMessageMapper.map(error)?.let { messageRes ->
                overlayController.showMessage(messageRes)
            }
        }
    }

    private fun publishOverlaySessionState(
        state: OverlaySessionState,
    ) {
        _overlaySessionState.value = state
        syncToolbarStateAsync()
    }

    private fun syncToolbarStateAsync() {
        val sessionState = _overlaySessionState.value
        if (!sessionState.isFloatingModeEnabled) {
            return
        }

        engineScope.launch {
            overlayController.updateToolbarState(
                createToolbarUiState(targetVisible = sessionState.isTargetVisible),
            )
        }
    }

    private fun createToolbarUiState(
        targetVisible: Boolean = _overlaySessionState.value.isTargetVisible,
    ): OverlayToolbarUiState {
        return OverlayToolbarUiState(
            runnerState = _runnerState.value,
            isTargetVisible = targetVisible,
        )
    }

    private fun findActiveTapStep(
        taskWithSteps: TaskWithSteps,
    ): ActionStepEntity? {
        return taskWithSteps.steps
            .filter { step ->
                step.enabled && step.actionType == ActionStepEntity.ACTION_TAP
            }
            .sortedBy { step -> step.orderIndex }
            .firstOrNull()
    }

    private fun resolveSessionTapStep(
        taskWithSteps: TaskWithSteps,
    ): ActionStepEntity? {
        val sessionStepId = activeTapStepId
        return taskWithSteps.steps
            .firstOrNull { step ->
                step.id == sessionStepId &&
                    step.enabled &&
                    step.actionType == ActionStepEntity.ACTION_TAP
            }
            ?: findActiveTapStep(taskWithSteps)
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

    private fun clearActiveSessionLocal() {
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
