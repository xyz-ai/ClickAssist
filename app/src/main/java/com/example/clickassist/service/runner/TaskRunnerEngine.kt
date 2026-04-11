package com.example.clickassist.service.runner

import android.content.Context
import android.util.Log
import androidx.annotation.StringRes
import com.example.clickassist.R
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
                onDebugTapRequested = ::debugTapOnce,
                onPauseRequested = ::pause,
                onStopRequested = ::stop,
                onTargetToggleRequested = ::toggleTargetVisibility,
                onCloseRequested = ::exitFloatingMode,
            ),
        )
    }

    fun enterFloatingMode(taskId: Long) { engineScope.launch { enterFloatingModeInternal(taskId) } }
    fun debugTapOnce() { engineScope.launch { debugTapOnceInternal() } }
    fun toggleTargetVisibility() { engineScope.launch { toggleTargetVisibilityInternal() } }

    fun startActiveTask() {
        when (_runnerState.value) {
            RunnerState.PAUSED -> resume()
            RunnerState.RUNNING, RunnerState.STOPPING -> Unit
            RunnerState.IDLE, RunnerState.COMPLETED, RunnerState.ERROR -> engineScope.launch { startActiveTaskInternal() }
        }
    }

    fun pause() {
        if (_runnerState.value == RunnerState.RUNNING) {
            Log.i(TAG, "pause requested")
            publishState(RunnerState.PAUSED)
        }
    }

    fun resume() {
        if (_runnerState.value == RunnerState.PAUSED) {
            Log.i(TAG, "resume requested")
            publishError(null)
            publishStatusMessage(null)
            publishState(RunnerState.RUNNING)
        }
    }

    fun stop() {
        engineScope.launch {
            Log.i(TAG, "stop requested runnerState=${_runnerState.value}")
            when (_runnerState.value) {
                RunnerState.IDLE -> Unit
                RunnerState.RUNNING, RunnerState.PAUSED, RunnerState.STOPPING -> stopRunnerJobAndResetState(clearError = true)
                RunnerState.COMPLETED, RunnerState.ERROR -> resetRunnerToIdle(clearError = true)
            }
        }
    }

    fun exitFloatingMode() {
        engineScope.launch {
            Log.i(TAG, "exitFloatingMode requested")
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

    fun requestCoordinateRecorder() = overlayController.requestCoordinateRecorder()
    fun requestJsonExport() = overlayController.requestJsonExport()
    fun requestTaskTemplateClone() { /* TODO reserve task template clone entry */ }

    private fun handleToolbarStartRequested() {
        Log.i(TAG, "toolbar start clicked state=${_runnerState.value} floatingMode=${_overlaySessionState.value.isFloatingModeEnabled}")
        startActiveTask()
    }

    private suspend fun enterFloatingModeInternal(taskId: Long) {
        Log.i(TAG, "enterFloatingMode taskId=$taskId")
        val taskWithSteps = taskRepository.getTask(taskId)
        if (taskWithSteps == null) return publishError(RunnerError.TaskNotFound(taskId), true)
        if (!overlayController.hasPermission()) return publishError(RunnerError.OverlayPermissionDenied, true)
        taskStartValidator.validateFloatingMode(taskWithSteps)?.let { return publishError(it, true) }
        val tapStep = findActiveTapStep(taskWithSteps) ?: return publishError(RunnerError.NoExecutableSteps, true)
        val point = overlayController.resolveInitialPoint(tapStep.x, tapStep.y)
        logTapStep("enterFloatingMode", taskWithSteps, tapStep, point)

        stopRunnerJobAndResetState(clearError = true)
        overlayController.hideFloatingMode(clearTargetPoint = true)
        clearActiveSessionLocal()

        activeTapTaskId = taskWithSteps.task.id
        activeTapStepId = tapStep.id
        activeTapPoint.set(point)
        val shown = overlayController.showFloatingMode(
            initialPoint = point,
            targetVisible = true,
            toolbarUiState = createToolbarUiState(targetVisible = true, statusMessageRes = R.string.overlay_status_ready),
            onPointChanged = { activeTapPoint.set(it) },
            onDragEnd = {
                Log.i(TAG, "target drag end taskId=${taskWithSteps.task.id} point=$it")
                activeTapPoint.set(it)
                persistTapPointAsync(taskWithSteps.task.id, tapStep.id, it)
            },
        )
        if (!shown) {
            clearActiveSessionLocal()
            return publishError(
                if (!overlayController.hasPermission()) RunnerError.OverlayPermissionDenied else RunnerError.Unknown("Unable to show floating overlay"),
                true,
            )
        }

        persistTapPoint(taskWithSteps.task.id, tapStep.id, point)
        publishError(null)
        publishOverlaySessionState(
            OverlaySessionState(
                isFloatingModeEnabled = true,
                activeTaskId = taskWithSteps.task.id,
                isTargetVisible = true,
                statusMessageRes = R.string.overlay_status_ready,
            ),
        )
    }

    private suspend fun startActiveTaskInternal() {
        Log.i(TAG, "startActiveTaskInternal")
        val context = resolveTapContext(source = "startActiveTask", validateStart = true) ?: return
        publishError(null)
        publishStatusMessage(R.string.overlay_status_starting)
        persistTapPoint(context.taskWithSteps.task.id, context.step.id, context.point)
        runnerJob?.cancelAndJoin()
        launchRunner(context.taskWithSteps)
    }

    private suspend fun debugTapOnceInternal() {
        Log.i(TAG, "debugTapOnceInternal")
        val context = resolveTapContext(source = "debugTapOnce", validateStart = false) ?: return
        publishError(null)
        publishStatusMessage(R.string.overlay_status_debug_tap_sending)
        persistTapPoint(context.taskWithSteps.task.id, context.step.id, context.point)
        performTapDispatch("debugTapOnce", context.taskWithSteps.task.id, context.step, context.point, context.service)
    }

    private fun launchRunner(taskWithSteps: TaskWithSteps) {
        Log.i(TAG, "launchRunner taskId=${taskWithSteps.task.id}")
        runnerJob = engineScope.launch {
            publishProgress(RunnerProgress(taskWithSteps.task.id, 0, 0, 0))
            publishState(RunnerState.RUNNING)
            try {
                executeTask(taskWithSteps)
                if (_runnerState.value == RunnerState.RUNNING) {
                    publishProgress(null)
                    publishState(RunnerState.COMPLETED)
                }
            } catch (cancellationException: CancellationException) {
                Log.i(TAG, "runner cancelled taskId=${taskWithSteps.task.id}")
                throw cancellationException
            } catch (throwable: Throwable) {
                Log.e(TAG, "runner exception taskId=${taskWithSteps.task.id}", throwable)
                publishError(mapThrowableToRunnerError(throwable), true)
                publishProgress(null)
                publishState(RunnerState.ERROR)
            } finally {
                if (runnerJob === coroutineContext[Job]) runnerJob = null
            }
        }
    }

    private suspend fun executeTask(taskWithSteps: TaskWithSteps) {
        val task = taskWithSteps.task
        val steps = taskWithSteps.steps.filter { it.enabled }.sortedBy { it.orderIndex }
        var currentRound = 0
        while (task.infiniteRounds || currentRound < task.totalRounds.coerceAtLeast(1)) {
            for ((stepIndex, step) in steps.withIndex()) {
                if (!awaitRunnableState()) return
                if (!waitWithControl(step.preDelayMs)) return
                for (repeatIndex in 0 until step.repeatCount.coerceAtLeast(1)) {
                    publishProgress(RunnerProgress(task.id, currentRound, stepIndex, repeatIndex))
                    if (!awaitRunnableState()) return
                    val dispatched = dispatchStep(step)
                    if (!dispatched) {
                        if (_runnerState.value == RunnerState.STOPPING) return
                        if (_runnerError.value == null) publishError(RunnerError.GestureDispatchFailed, true)
                        publishProgress(null)
                        publishState(RunnerState.ERROR)
                        return
                    }
                    if (repeatIndex < step.repeatCount.coerceAtLeast(1) - 1 && !waitWithControl(step.intervalMs)) return
                }
                if (!waitWithControl(step.postDelayMs)) return
            }
            currentRound += 1
        }
    }

    private suspend fun dispatchStep(step: ActionStepEntity): Boolean {
        val runtimeStep = resolveRuntimeStep(step)
        return when (runtimeStep.actionType) {
            ActionStepEntity.ACTION_TAP -> {
                val x = runtimeStep.x ?: return false
                val y = runtimeStep.y ?: return false
                Log.i(TAG, "dispatchStep TAP stepId=${runtimeStep.id} x=$x y=$y repeat=${runtimeStep.repeatCount} intervalMs=${runtimeStep.intervalMs}")
                val service = ensureAccessibilityServiceReady("runnerDispatch") ?: return false
                performTapDispatch("runnerDispatch", activeTapTaskId ?: 0L, runtimeStep, ScreenPoint(x, y), service)
            }
            ActionStepEntity.ACTION_SWIPE -> {
                val sx = runtimeStep.x ?: return false
                val sy = runtimeStep.y ?: return false
                val ex = runtimeStep.endX ?: return false
                val ey = runtimeStep.endY ?: return false
                val service = ensureAccessibilityServiceReady("dispatchSwipe") ?: return false
                Log.i(TAG, "dispatchStep SWIPE stepId=${runtimeStep.id} start=($sx,$sy) end=($ex,$ey) durationMs=${runtimeStep.durationMs}")
                service.dispatchSwipe(sx, sy, ex, ey, runtimeStep.durationMs)
            }
            ActionStepEntity.ACTION_WAIT -> waitWithControl(runtimeStep.durationMs.coerceAtLeast(runtimeStep.intervalMs))
            else -> false
        }
    }

    private suspend fun performTapDispatch(source: String, taskId: Long, step: ActionStepEntity, point: ScreenPoint, service: MyAccessibilityService): Boolean {
        val targetVisible = _overlaySessionState.value.isTargetVisible && overlayController.isTargetVisible()
        if (targetVisible) Log.i(TAG, "$source disabling target touch point=$point result=${overlayController.setTargetTouchEnabled(false)}")
        return try {
            Log.i(TAG, "$source dispatchTap before taskId=$taskId stepId=${step.id} x=${point.x} y=${point.y} durationMs=${step.durationMs}")
            val dispatched = service.dispatchTap(point.x, point.y, step.durationMs)
            val lastStatus = MyAccessibilityService.lastDispatchStatus()
            Log.i(TAG, "$source dispatchTap after taskId=$taskId stepId=${step.id} result=$dispatched lastStatus=$lastStatus")
            if (dispatched) {
                publishStatusMessage(R.string.overlay_status_tap_completed)
                true
            } else {
                publishError(
                    RunnerError.GestureDispatchFailed,
                    true,
                    when (lastStatus) {
                        MyAccessibilityService.GestureDispatchStatus.CANCELLED -> R.string.overlay_status_tap_cancelled
                        MyAccessibilityService.GestureDispatchStatus.REJECTED -> R.string.overlay_status_dispatch_rejected
                        else -> R.string.error_gesture_dispatch_failed
                    },
                )
                false
            }
        } catch (throwable: Throwable) {
            Log.e(TAG, "$source dispatchTap exception taskId=$taskId stepId=${step.id}", throwable)
            publishError(mapThrowableToRunnerError(throwable), true)
            false
        } finally {
            if (targetVisible) Log.i(TAG, "$source restoring target touch result=${overlayController.setTargetTouchEnabled(true)}")
        }
    }

    private suspend fun resolveTapContext(source: String, validateStart: Boolean): TapContext? {
        val taskId = activeTapTaskId
        if (taskId == null || !_overlaySessionState.value.isFloatingModeEnabled) {
            publishError(RunnerError.NoTaskSelected, true)
            return null
        }
        val taskWithSteps = taskRepository.getTask(taskId) ?: run {
            publishError(RunnerError.TaskNotFound(taskId), true)
            return null
        }
        Log.i(TAG, "$source loaded taskId=$taskId stepCount=${taskWithSteps.steps.size}")
        val tapStep = resolveSessionTapStep(taskWithSteps) ?: run {
            publishError(RunnerError.NoExecutableSteps, true)
            return null
        }
        val runtimePoint = activeTapPoint.get() ?: tapStep.x?.let { x -> tapStep.y?.let { y -> ScreenPoint(x, y) } }
        if (runtimePoint == null) {
            publishError(RunnerError.TapPointNotSet, true)
            return null
        }
        val point = overlayController.resolveInitialPoint(runtimePoint.x, runtimePoint.y)
        val normalized = taskWithSteps.withNormalizedTapPoint(tapStep.id, point)
        logTapStep(source, normalized, tapStep, point)
        val enabled = MyAccessibilityService.isEnabled(appContext)
        val service = MyAccessibilityService.current()
        Log.i(TAG, "$source accessibilityEnabled=$enabled accessibilityInstanceReady=${service != null}")
        if (validateStart) taskStartValidator.validateStart(normalized, enabled)?.let { publishError(it, true); return null }
        if (!enabled) { publishError(RunnerError.AccessibilityDisabled, true); return null }
        if (service == null) { publishError(RunnerError.AccessibilityServiceUnavailable, true); return null }
        activeTapStepId = tapStep.id
        activeTapPoint.set(point)
        if (_overlaySessionState.value.isTargetVisible) overlayController.updateTarget(point)
        return TapContext(normalized, tapStep.copy(x = point.x, y = point.y), point, service)
    }

    private suspend fun toggleTargetVisibilityInternal() {
        val session = _overlaySessionState.value
        Log.i(TAG, "toggleTargetVisibility floatingMode=${session.isFloatingModeEnabled} visible=${session.isTargetVisible}")
        if (!session.isFloatingModeEnabled) return publishError(RunnerError.NoTaskSelected, true)
        val visible = !session.isTargetVisible
        val point = activeTapPoint.get() ?: overlayController.currentTargetPoint()
        if (visible && point == null) return publishError(RunnerError.TapPointNotSet, true)
        val updated = overlayController.setTargetVisibility(visible, point)
        if (!updated) return publishError(
            if (!overlayController.hasPermission()) RunnerError.OverlayPermissionDenied else RunnerError.Unknown("Unable to update target visibility"),
            true,
        )
        publishOverlaySessionState(session.copy(isTargetVisible = visible, statusMessageRes = if (visible) R.string.overlay_status_target_visible else R.string.overlay_status_target_hidden))
    }

    private suspend fun awaitRunnableState(): Boolean {
        while (true) {
            when (_runnerState.value) {
                RunnerState.RUNNING -> return true
                RunnerState.PAUSED -> delay(CONTROL_POLL_INTERVAL_MS)
                RunnerState.STOPPING -> return false
                RunnerState.IDLE, RunnerState.COMPLETED, RunnerState.ERROR -> return false
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

    private suspend fun stopRunnerJobAndResetState(clearError: Boolean) {
        runnerJob?.let {
            publishState(RunnerState.STOPPING)
            it.cancelAndJoin()
            runnerJob = null
        }
        resetRunnerToIdle(clearError)
    }

    private fun resetRunnerToIdle(clearError: Boolean) {
        publishProgress(null)
        publishState(RunnerState.IDLE)
        if (clearError) publishError(null)
        if (_overlaySessionState.value.isFloatingModeEnabled) publishStatusMessage(R.string.overlay_status_ready)
    }

    private fun publishState(state: RunnerState) {
        _runnerState.value = state
        when (state) {
            RunnerState.IDLE -> if (_overlaySessionState.value.isFloatingModeEnabled) publishStatusMessage(R.string.overlay_status_ready)
            RunnerState.PAUSED -> publishStatusMessage(R.string.runner_state_paused)
            RunnerState.STOPPING -> publishStatusMessage(R.string.runner_state_stopping)
            RunnerState.COMPLETED -> publishStatusMessage(R.string.runner_state_completed)
            RunnerState.RUNNING, RunnerState.ERROR -> Unit
        }
        syncToolbarStateAsync()
    }

    private fun publishProgress(progress: RunnerProgress?) { _runnerProgress.value = progress }

    private fun publishError(error: RunnerError?, showStatusMessage: Boolean = false, @StringRes statusMessageOverrideRes: Int? = null) {
        _runnerError.value = error
        if (showStatusMessage) publishStatusMessage(statusMessageOverrideRes ?: RunnerErrorMessageMapper.map(error))
    }

    private fun publishStatusMessage(@StringRes messageRes: Int?) {
        val session = _overlaySessionState.value
        if (!session.isFloatingModeEnabled) return
        publishOverlaySessionState(session.copy(statusMessageRes = messageRes))
    }

    private fun publishOverlaySessionState(state: OverlaySessionState) {
        _overlaySessionState.value = state
        syncToolbarStateAsync()
    }

    private fun syncToolbarStateAsync() {
        val session = _overlaySessionState.value
        if (!session.isFloatingModeEnabled) return
        engineScope.launch { overlayController.updateToolbarState(createToolbarUiState(session.isTargetVisible, session.statusMessageRes)) }
    }

    private fun createToolbarUiState(targetVisible: Boolean = _overlaySessionState.value.isTargetVisible, @StringRes statusMessageRes: Int? = _overlaySessionState.value.statusMessageRes) =
        OverlayToolbarUiState(_runnerState.value, targetVisible, statusMessageRes)

    private fun findActiveTapStep(taskWithSteps: TaskWithSteps) =
        taskWithSteps.steps.filter { it.enabled && it.actionType == ActionStepEntity.ACTION_TAP }.sortedBy { it.orderIndex }.firstOrNull()

    private fun resolveSessionTapStep(taskWithSteps: TaskWithSteps) =
        taskWithSteps.steps.firstOrNull { it.id == activeTapStepId && it.enabled && it.actionType == ActionStepEntity.ACTION_TAP } ?: findActiveTapStep(taskWithSteps)

    private fun resolveRuntimeStep(step: ActionStepEntity): ActionStepEntity {
        val point = activeTapPoint.get()
        return if (step.actionType == ActionStepEntity.ACTION_TAP && step.id == activeTapStepId && point != null && activeTapTaskId != null) step.copy(x = point.x, y = point.y) else step
    }

    private fun clearActiveSessionLocal() {
        activeTapTaskId = null
        activeTapStepId = null
        activeTapPoint.set(null)
    }

    private suspend fun persistTapPoint(taskId: Long, stepId: Long, point: ScreenPoint) {
        runCatching { taskRepository.updateTapStepPosition(taskId, stepId, point.x, point.y) }
            .onFailure { Log.e(TAG, "persistTapPoint failed taskId=$taskId stepId=$stepId point=$point", it) }
    }

    private fun persistTapPointAsync(taskId: Long, stepId: Long, point: ScreenPoint) {
        engineScope.launch { persistTapPoint(taskId, stepId, point) }
    }

    private fun ensureAccessibilityServiceReady(source: String): MyAccessibilityService? {
        val enabled = MyAccessibilityService.isEnabled(appContext)
        val service = MyAccessibilityService.current()
        Log.i(TAG, "$source accessibilityEnabled=$enabled accessibilityInstanceReady=${service != null}")
        return when {
            !enabled -> { publishError(RunnerError.AccessibilityDisabled, true); null }
            service == null -> { publishError(RunnerError.AccessibilityServiceUnavailable, true); null }
            else -> service
        }
    }

    private fun logTapStep(source: String, taskWithSteps: TaskWithSteps, step: ActionStepEntity, point: ScreenPoint) {
        Log.i(TAG, "$source taskId=${taskWithSteps.task.id} stepCount=${taskWithSteps.steps.size} tapStepId=${step.id} x=${point.x} y=${point.y} repeat=${step.repeatCount} intervalMs=${step.intervalMs} durationMs=${step.durationMs}")
    }

    private fun mapThrowableToRunnerError(throwable: Throwable) =
        when (throwable) {
            is CancellationException -> RunnerError.Unknown(throwable.message)
            else -> RunnerError.Unknown(throwable.message)
        }

    private fun TaskWithSteps.withNormalizedTapPoint(stepId: Long, point: ScreenPoint) =
        copy(steps = steps.map { if (it.id == stepId) it.copy(x = point.x, y = point.y) else it })

    private data class TapContext(
        val taskWithSteps: TaskWithSteps,
        val step: ActionStepEntity,
        val point: ScreenPoint,
        val service: MyAccessibilityService,
    )

    private companion object {
        const val CONTROL_POLL_INTERVAL_MS = 100L
        const val TAG = "ClickAssistRunner"
    }
}
