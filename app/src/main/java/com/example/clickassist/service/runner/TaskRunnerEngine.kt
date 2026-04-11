package com.example.clickassist.service.runner

import android.content.Context
import android.util.Log
import androidx.annotation.StringRes
import com.example.clickassist.R
import com.example.clickassist.data.local.entity.ActionStepEntity
import com.example.clickassist.data.local.entity.TaskWithSteps
import com.example.clickassist.domain.model.ActionType
import com.example.clickassist.domain.model.ScreenPoint
import com.example.clickassist.domain.repository.TaskRepository
import com.example.clickassist.service.accessibility.MyAccessibilityService
import com.example.clickassist.service.overlay.OverlayController
import com.example.clickassist.service.overlay.OverlayMarkerModel
import com.example.clickassist.service.overlay.OverlayMarkerRole
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
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.coroutineContext

class TaskRunnerEngine(
    appContext: Context,
    private val taskRepository: TaskRepository,
    private val overlayController: OverlayController,
    private val taskStartValidator: TaskStartValidator,
) {
    private val appContext = appContext.applicationContext
    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val markerSpacingPx = (96 * appContext.resources.displayMetrics.density).toInt()
    private val swipeOffsetPx = (160 * appContext.resources.displayMetrics.density).toInt()

    private val _runnerState = MutableStateFlow(RunnerState.IDLE)
    val runnerState: StateFlow<RunnerState> = _runnerState.asStateFlow()
    private val _runnerProgress = MutableStateFlow<RunnerProgress?>(null)
    val runnerProgress: StateFlow<RunnerProgress?> = _runnerProgress.asStateFlow()
    private val _runnerError = MutableStateFlow<RunnerError?>(null)
    val runnerError: StateFlow<RunnerError?> = _runnerError.asStateFlow()
    private val _overlaySessionState = MutableStateFlow(OverlaySessionState())
    val overlaySessionState: StateFlow<OverlaySessionState> = _overlaySessionState.asStateFlow()

    private var runnerJob: Job? = null
    private var activeTaskId: Long? = null
    private var selectedStepId: Long? = null
    private val runtimeStepGeometryMap = ConcurrentHashMap<Long, RuntimeStepGeometry>()

    init {
        overlayController.bindToolbarCallbacks(
            OverlayToolbarCallbacks(
                onStartRequested = ::handleToolbarStartRequested,
                onDebugTapRequested = ::testCurrentStep,
                onPauseRequested = ::pause,
                onStopRequested = ::stop,
                onTargetToggleRequested = ::toggleTargetVisibility,
                onCloseRequested = ::exitFloatingMode,
            ),
        )
    }

    fun enterFloatingMode(taskId: Long) {
        engineScope.launch { enterFloatingModeInternal(taskId) }
    }

    fun testCurrentStep() {
        engineScope.launch { testCurrentStepInternal() }
    }

    fun toggleTargetVisibility() {
        engineScope.launch { toggleTargetVisibilityInternal() }
    }

    fun startActiveTask() {
        when (_runnerState.value) {
            RunnerState.PAUSED -> resume()
            RunnerState.RUNNING, RunnerState.STOPPING -> Unit
            RunnerState.IDLE, RunnerState.COMPLETED, RunnerState.ERROR -> {
                engineScope.launch { startActiveTaskInternal() }
            }
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
                RunnerState.RUNNING, RunnerState.PAUSED, RunnerState.STOPPING -> {
                    stopRunnerJobAndResetState(clearError = true)
                }
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
        Log.i(
            TAG,
            "toolbar start clicked state=${_runnerState.value} floatingMode=${_overlaySessionState.value.isFloatingModeEnabled}",
        )
        startActiveTask()
    }

    private suspend fun enterFloatingModeInternal(taskId: Long) {
        Log.i(TAG, "enterFloatingMode taskId=$taskId")
        val taskWithSteps = taskRepository.getTask(taskId)
        if (taskWithSteps == null) {
            publishError(RunnerError.TaskNotFound(taskId), true)
            return
        }
        if (!overlayController.hasPermission()) {
            publishError(RunnerError.OverlayPermissionDenied, true)
            return
        }
        taskStartValidator.validateFloatingMode(taskWithSteps)?.let {
            publishError(it, true)
            return
        }

        stopRunnerJobAndResetState(clearError = true)
        overlayController.hideFloatingMode(clearTargetPoint = true)
        clearActiveSessionLocal()

        val normalizedTask = normalizeTaskForRuntime(taskWithSteps)
        val selectedStep = resolvePreferredSelectedStep(normalizedTask)

        activeTaskId = normalizedTask.task.id
        selectedStepId = selectedStep?.id

        val markers = buildMarkerModels(normalizedTask)
        val targetVisible = markers.isNotEmpty()
        val shown = overlayController.showFloatingMode(
            initialMarkers = markers,
            targetVisible = targetVisible,
            toolbarUiState = createToolbarUiState(
                normalizedTask = normalizedTask,
                targetVisible = targetVisible,
                statusMessageRes = R.string.overlay_status_ready,
            ),
            onMarkerChanged = ::handleMarkerChanged,
            onMarkerDragEnd = ::handleMarkerDragEnd,
            onMarkerSelected = ::handleMarkerSelected,
        )
        if (!shown) {
            clearActiveSessionLocal()
            publishError(
                if (!overlayController.hasPermission()) {
                    RunnerError.OverlayPermissionDenied
                } else {
                    RunnerError.Unknown("Unable to show floating overlay")
                },
                true,
            )
            return
        }

        persistVisibleStepGeometriesAsync(normalizedTask)
        publishError(null)
        publishOverlaySessionState(
            createOverlaySessionState(
                normalizedTask = normalizedTask,
                isTargetVisible = targetVisible,
                statusMessageRes = R.string.overlay_status_ready,
            ),
        )
    }

    private suspend fun startActiveTaskInternal() {
        Log.i(TAG, "startActiveTaskInternal")
        val taskContext = resolveActiveTaskContext(
            source = "startActiveTask",
            requireService = true,
            validateStart = true,
        ) ?: return

        publishError(null)
        publishStatusMessage(R.string.overlay_status_starting)
        persistVisibleStepGeometriesAsync(taskContext.taskWithSteps)
        runnerJob?.cancelAndJoin()
        launchRunner(taskContext.taskWithSteps)
    }

    private suspend fun testCurrentStepInternal() {
        Log.i(TAG, "testCurrentStepInternal")
        val taskContext = resolveActiveTaskContext(
            source = "testCurrentStep",
            requireService = true,
            validateStart = false,
        ) ?: return
        val service = taskContext.service ?: return
        val step = resolveCurrentTestableStep(taskContext.taskWithSteps)
        if (step == null) {
            publishError(RunnerError.NoExecutableSteps, true)
            return
        }
        publishError(null)
        publishStatusMessage(R.string.overlay_status_testing_current_step)
        persistVisibleStepGeometriesAsync(taskContext.taskWithSteps)
        dispatchGestureStep(
            source = "testCurrentStep",
            taskId = taskContext.taskWithSteps.task.id,
            step = step,
            service = service,
        )
    }

    private fun launchRunner(taskWithSteps: TaskWithSteps) {
        Log.i(TAG, "launchRunner taskId=${taskWithSteps.task.id} stepCount=${taskWithSteps.steps.size}")
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
                if (runnerJob === coroutineContext[Job]) {
                    runnerJob = null
                }
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
                    selectStep(taskWithSteps, step.id, statusMessageRes = null)
                    if (!awaitRunnableState()) return
                    val dispatched = dispatchStep(task.id, step, stepIndex)
                    if (!dispatched) {
                        if (_runnerState.value == RunnerState.STOPPING) return
                        if (_runnerError.value == null) {
                            publishError(RunnerError.GestureDispatchFailed, true)
                        }
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

    private suspend fun dispatchStep(
        taskId: Long,
        step: ActionStepEntity,
        stepIndex: Int,
    ): Boolean {
        val runtimeStep = resolveRuntimeStep(step)
        return when (runtimeStep.actionTypeEnum()) {
            ActionType.TAP,
            ActionType.LONG_PRESS,
            ActionType.SWIPE,
            -> {
                val service = ensureAccessibilityServiceReady("dispatchStep") ?: return false
                Log.i(
                    TAG,
                    "dispatchStep taskId=$taskId stepIndex=${stepIndex + 1} stepId=${runtimeStep.id} actionType=${runtimeStep.actionType} repeat=${runtimeStep.repeatCount} intervalMs=${runtimeStep.intervalMs} durationMs=${runtimeStep.durationMs} x=${runtimeStep.x} y=${runtimeStep.y} endX=${runtimeStep.endX} endY=${runtimeStep.endY}",
                )
                dispatchGestureStep("runnerDispatch", taskId, runtimeStep, service)
            }
            ActionType.WAIT -> {
                val duration = runtimeStep.durationMs.coerceAtLeast(1L)
                Log.i(TAG, "dispatchStep WAIT taskId=$taskId stepIndex=${stepIndex + 1} stepId=${runtimeStep.id} durationMs=$duration")
                waitWithControl(duration)
            }
        }
    }

    private suspend fun dispatchGestureStep(
        source: String,
        taskId: Long,
        step: ActionStepEntity,
        service: MyAccessibilityService,
    ): Boolean {
        val targetVisible = _overlaySessionState.value.isTargetVisible && overlayController.isTargetVisible()
        if (targetVisible) {
            Log.i(TAG, "$source disabling target touch result=${overlayController.setTargetTouchEnabled(false)}")
        }
        return try {
            val dispatched = when (step.actionTypeEnum()) {
                ActionType.TAP -> {
                    val point = requireStepPoint(step, isLongPress = false) ?: return false
                    Log.i(TAG, "$source dispatch TAP taskId=$taskId stepId=${step.id} x=${point.x} y=${point.y} durationMs=${step.durationMs}")
                    service.dispatchTap(point.x, point.y, step.durationMs)
                }
                ActionType.LONG_PRESS -> {
                    val point = requireStepPoint(step, isLongPress = true) ?: return false
                    Log.i(TAG, "$source dispatch LONG_PRESS taskId=$taskId stepId=${step.id} x=${point.x} y=${point.y} durationMs=${step.durationMs}")
                    service.dispatchLongPress(point.x, point.y, step.durationMs)
                }
                ActionType.SWIPE -> {
                    val swipe = requireSwipePoints(step) ?: return false
                    Log.i(TAG, "$source dispatch SWIPE taskId=$taskId stepId=${step.id} start=${swipe.first} end=${swipe.second} durationMs=${step.durationMs}")
                    service.dispatchSwipe(swipe.first.x, swipe.first.y, swipe.second.x, swipe.second.y, step.durationMs)
                }
                ActionType.WAIT -> true
            }

            val lastStatus = MyAccessibilityService.lastDispatchStatus()
            Log.i(TAG, "$source dispatch result taskId=$taskId stepId=${step.id} actionType=${step.actionType} result=$dispatched lastStatus=$lastStatus")
            if (dispatched) {
                publishStatusMessage(successStatusRes(step.actionTypeEnum()))
                true
            } else {
                publishError(
                    RunnerError.GestureDispatchFailed,
                    true,
                    when (lastStatus) {
                        MyAccessibilityService.GestureDispatchStatus.CANCELLED -> R.string.overlay_status_gesture_cancelled
                        MyAccessibilityService.GestureDispatchStatus.REJECTED -> R.string.overlay_status_dispatch_rejected
                        else -> R.string.error_gesture_dispatch_failed
                    },
                )
                false
            }
        } catch (throwable: Throwable) {
            Log.e(TAG, "$source dispatch exception taskId=$taskId stepId=${step.id}", throwable)
            publishError(mapThrowableToRunnerError(throwable), true)
            false
        } finally {
            if (targetVisible) {
                Log.i(TAG, "$source restoring target touch result=${overlayController.setTargetTouchEnabled(true)}")
            }
        }
    }

    private suspend fun resolveActiveTaskContext(
        source: String,
        requireService: Boolean,
        validateStart: Boolean,
    ): ActiveTaskContext? {
        val taskId = activeTaskId
        if (taskId == null || !_overlaySessionState.value.isFloatingModeEnabled) {
            publishError(RunnerError.NoTaskSelected, true)
            return null
        }

        val loadedTask = taskRepository.getTask(taskId) ?: run {
            publishError(RunnerError.TaskNotFound(taskId), true)
            return null
        }
        val normalizedTask = normalizeTaskForRuntime(loadedTask)
        Log.i(TAG, "$source loaded taskId=$taskId stepCount=${normalizedTask.steps.size} selectedStepId=$selectedStepId")

        val enabled = MyAccessibilityService.isEnabled(appContext)
        val service = MyAccessibilityService.current()
        Log.i(TAG, "$source accessibilityEnabled=$enabled accessibilityInstanceReady=${service != null}")
        if (validateStart) {
            taskStartValidator.validateStart(normalizedTask, enabled)?.let {
                publishError(it, true)
                return null
            }
        }
        if (requireService) {
            if (!enabled) {
                publishError(RunnerError.AccessibilityDisabled, true)
                return null
            }
            if (service == null) {
                publishError(RunnerError.AccessibilityServiceUnavailable, true)
                return null
            }
        }

        publishOverlaySessionState(
            createOverlaySessionState(
                normalizedTask = normalizedTask,
                isTargetVisible = _overlaySessionState.value.isTargetVisible,
                statusMessageRes = _overlaySessionState.value.statusMessageRes,
            ),
        )
        if (_overlaySessionState.value.isTargetVisible) {
            syncOverlayTargets(normalizedTask)
        }
        return ActiveTaskContext(
            taskWithSteps = normalizedTask,
            service = service,
        )
    }

    private suspend fun toggleTargetVisibilityInternal() {
        val session = _overlaySessionState.value
        Log.i(TAG, "toggleTargetVisibility floatingMode=${session.isFloatingModeEnabled} visible=${session.isTargetVisible}")
        if (!session.isFloatingModeEnabled) {
            publishError(RunnerError.NoTaskSelected, true)
            return
        }

        val taskId = activeTaskId ?: run {
            publishError(RunnerError.NoTaskSelected, true)
            return
        }
        val taskWithSteps = taskRepository.getTask(taskId) ?: run {
            publishError(RunnerError.TaskNotFound(taskId), true)
            return
        }
        val normalizedTask = normalizeTaskForRuntime(taskWithSteps)
        val visible = !session.isTargetVisible
        val markers = buildMarkerModels(normalizedTask)
        if (visible && markers.isEmpty()) {
            publishError(RunnerError.NoExecutableSteps, true)
            return
        }
        val updated = overlayController.setTargetVisibility(
            isVisible = visible,
            markers = markers,
        )
        if (!updated) {
            publishError(
                if (!overlayController.hasPermission()) RunnerError.OverlayPermissionDenied else RunnerError.Unknown("Unable to update target visibility"),
                true,
            )
            return
        }

        publishOverlaySessionState(
            createOverlaySessionState(
                normalizedTask = normalizedTask,
                isTargetVisible = visible,
                statusMessageRes = if (visible) R.string.overlay_status_target_visible else R.string.overlay_status_target_hidden,
            ),
        )
    }

    private fun handleMarkerChanged(
        markerId: String,
        point: ScreenPoint,
    ) {
        val markerMeta = parseMarkerId(markerId) ?: return
        val current = runtimeStepGeometryMap[markerMeta.stepId] ?: RuntimeStepGeometry()
        val updated = when (markerMeta.role) {
            OverlayMarkerRole.PRIMARY,
            OverlayMarkerRole.START,
            -> current.copy(start = point)

            OverlayMarkerRole.END -> current.copy(end = point)
        }
        runtimeStepGeometryMap[markerMeta.stepId] = updated
        selectedStepId = markerMeta.stepId
        Log.i(TAG, "marker moved markerId=$markerId stepId=${markerMeta.stepId} point=$point")
        syncOverlaySessionSelection()
    }

    private fun handleMarkerDragEnd(
        markerId: String,
        point: ScreenPoint,
    ) {
        val markerMeta = parseMarkerId(markerId) ?: return
        handleMarkerChanged(markerId, point)
        engineScope.launch {
            persistStepGeometry(markerMeta.stepId)
            val taskId = activeTaskId ?: return@launch
            val task = taskRepository.getTask(taskId) ?: return@launch
            if (_overlaySessionState.value.isTargetVisible) {
                syncOverlayTargets(normalizeTaskForRuntime(task))
            }
        }
    }

    private fun handleMarkerSelected(
        markerId: String,
    ) {
        val markerMeta = parseMarkerId(markerId) ?: return
        selectedStepId = markerMeta.stepId
        Log.i(TAG, "marker selected markerId=$markerId stepId=${markerMeta.stepId}")
        engineScope.launch {
            val taskId = activeTaskId ?: return@launch
            val task = taskRepository.getTask(taskId) ?: return@launch
            val normalized = normalizeTaskForRuntime(task)
            syncOverlayTargets(normalized)
            publishOverlaySessionState(
                createOverlaySessionState(
                    normalizedTask = normalized,
                    isTargetVisible = _overlaySessionState.value.isTargetVisible,
                    statusMessageRes = _overlaySessionState.value.statusMessageRes,
                ),
            )
        }
    }

    private suspend fun syncOverlayTargets(
        taskWithSteps: TaskWithSteps,
    ) {
        if (!_overlaySessionState.value.isTargetVisible) return
        overlayController.updateTargets(buildMarkerModels(taskWithSteps))
    }

    private fun syncOverlaySessionSelection() {
        val session = _overlaySessionState.value
        if (!session.isFloatingModeEnabled) return
        engineScope.launch {
            val taskId = activeTaskId ?: return@launch
            val task = taskRepository.getTask(taskId) ?: return@launch
            val normalized = normalizeTaskForRuntime(task)
            publishOverlaySessionState(
                createOverlaySessionState(
                    normalizedTask = normalized,
                    isTargetVisible = session.isTargetVisible,
                    statusMessageRes = session.statusMessageRes,
                ),
            )
            if (session.isTargetVisible) {
                syncOverlayTargets(normalized)
            }
        }
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

    private suspend fun waitWithControl(
        durationMs: Long,
    ): Boolean {
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
        runnerJob?.let {
            publishState(RunnerState.STOPPING)
            it.cancelAndJoin()
            runnerJob = null
        }
        resetRunnerToIdle(clearError)
    }

    private fun resetRunnerToIdle(
        clearError: Boolean,
    ) {
        publishProgress(null)
        publishState(RunnerState.IDLE)
        if (clearError) {
            publishError(null)
        }
        if (_overlaySessionState.value.isFloatingModeEnabled) {
            publishStatusMessage(R.string.overlay_status_ready)
        }
    }

    private fun publishState(
        state: RunnerState,
    ) {
        _runnerState.value = state
        when (state) {
            RunnerState.IDLE -> if (_overlaySessionState.value.isFloatingModeEnabled) {
                publishStatusMessage(R.string.overlay_status_ready)
            }
            RunnerState.PAUSED -> publishStatusMessage(R.string.runner_state_paused)
            RunnerState.STOPPING -> publishStatusMessage(R.string.runner_state_stopping)
            RunnerState.COMPLETED -> publishStatusMessage(R.string.runner_state_completed)
            RunnerState.RUNNING, RunnerState.ERROR -> Unit
        }
        syncToolbarStateAsync()
    }

    private fun publishProgress(progress: RunnerProgress?) {
        _runnerProgress.value = progress
    }

    private fun publishError(
        error: RunnerError?,
        showStatusMessage: Boolean = false,
        @StringRes statusMessageOverrideRes: Int? = null,
    ) {
        _runnerError.value = error
        if (showStatusMessage) {
            publishStatusMessage(statusMessageOverrideRes ?: RunnerErrorMessageMapper.map(error))
        }
    }

    private fun publishStatusMessage(
        @StringRes messageRes: Int?,
    ) {
        val session = _overlaySessionState.value
        if (!session.isFloatingModeEnabled) return
        publishOverlaySessionState(session.copy(statusMessageRes = messageRes))
    }

    private fun publishOverlaySessionState(
        state: OverlaySessionState,
    ) {
        _overlaySessionState.value = state
        syncToolbarStateAsync()
    }

    private fun syncToolbarStateAsync() {
        val session = _overlaySessionState.value
        if (!session.isFloatingModeEnabled) return
        engineScope.launch {
            overlayController.updateToolbarState(
                createToolbarUiState(
                    isMultiPointMode = session.isMultiPointMode,
                    targetVisible = session.isTargetVisible,
                    stepCount = session.stepCount,
                    selectedStepOrder = session.selectedStepOrder,
                    selectedStepActionType = session.selectedStepActionType,
                    statusMessageRes = session.statusMessageRes,
                ),
            )
        }
    }

    private fun createToolbarUiState(
        normalizedTask: TaskWithSteps? = null,
        targetVisible: Boolean = _overlaySessionState.value.isTargetVisible,
        isMultiPointMode: Boolean = normalizedTask?.let { determineIsMultiPointMode(it) } ?: _overlaySessionState.value.isMultiPointMode,
        stepCount: Int = normalizedTask?.steps?.count { it.enabled } ?: _overlaySessionState.value.stepCount,
        selectedStepOrder: Int? = normalizedTask?.let { resolveSelectedStepOrder(it) } ?: _overlaySessionState.value.selectedStepOrder,
        selectedStepActionType: String? = normalizedTask?.let { resolveSelectedStepActionType(it) } ?: _overlaySessionState.value.selectedStepActionType,
        @StringRes statusMessageRes: Int? = _overlaySessionState.value.statusMessageRes,
    ): OverlayToolbarUiState {
        return OverlayToolbarUiState(
            runnerState = _runnerState.value,
            isTargetVisible = targetVisible,
            isMultiPointMode = isMultiPointMode,
            stepCount = stepCount,
            selectedStepOrder = selectedStepOrder,
            selectedStepActionType = selectedStepActionType,
            statusMessageRes = statusMessageRes,
        )
    }

    private fun createOverlaySessionState(
        normalizedTask: TaskWithSteps,
        isTargetVisible: Boolean,
        @StringRes statusMessageRes: Int?,
    ): OverlaySessionState {
        return OverlaySessionState(
            isFloatingModeEnabled = true,
            activeTaskId = normalizedTask.task.id,
            isTargetVisible = isTargetVisible,
            isMultiPointMode = determineIsMultiPointMode(normalizedTask),
            stepCount = normalizedTask.steps.count { it.enabled },
            selectedStepOrder = resolveSelectedStepOrder(normalizedTask),
            selectedStepActionType = resolveSelectedStepActionType(normalizedTask),
            statusMessageRes = statusMessageRes,
        )
    }

    private fun determineIsMultiPointMode(
        taskWithSteps: TaskWithSteps,
    ): Boolean {
        val visibleSteps = taskWithSteps.steps.filter { it.enabled && it.actionTypeEnum() != ActionType.WAIT }
        if (visibleSteps.size != 1) {
            return true
        }
        return visibleSteps.first().actionTypeEnum() == ActionType.SWIPE
    }

    private fun resolveSelectedStepOrder(
        taskWithSteps: TaskWithSteps,
    ): Int? {
        val steps = taskWithSteps.steps.filter { it.enabled }.sortedBy { it.orderIndex }
        val targetId = selectedStepId ?: steps.firstOrNull()?.id ?: return null
        return steps.indexOfFirst { it.id == targetId }.takeIf { it >= 0 }?.plus(1)
    }

    private fun resolveSelectedStepActionType(
        taskWithSteps: TaskWithSteps,
    ): String? {
        val steps = taskWithSteps.steps.filter { it.enabled }.sortedBy { it.orderIndex }
        val targetId = selectedStepId ?: steps.firstOrNull()?.id ?: return null
        return steps.firstOrNull { it.id == targetId }?.actionType
    }

    private fun normalizeTaskForRuntime(
        taskWithSteps: TaskWithSteps,
    ): TaskWithSteps {
        val baseCenter = overlayController.resolveInitialPoint(null, null)
        var visibleIndex = 0

        val normalizedSteps = taskWithSteps.steps.sortedBy { it.orderIndex }.map { step ->
            if (!step.enabled) return@map step

            when (step.actionTypeEnum()) {
                ActionType.TAP,
                ActionType.LONG_PRESS,
                -> {
                    val currentGeometry = runtimeStepGeometryMap[step.id]
                    val fallback = defaultVisiblePoint(baseCenter, visibleIndex)
                    val point = overlayController.resolveInitialPoint(
                        preferredX = currentGeometry?.start?.x ?: step.x ?: fallback.x,
                        preferredY = currentGeometry?.start?.y ?: step.y ?: fallback.y,
                    )
                    runtimeStepGeometryMap[step.id] = RuntimeStepGeometry(start = point)
                    visibleIndex += 1
                    step.copy(x = point.x, y = point.y)
                }

                ActionType.SWIPE -> {
                    val currentGeometry = runtimeStepGeometryMap[step.id]
                    val defaultStart = defaultVisiblePoint(baseCenter, visibleIndex)
                    val defaultEnd = overlayController.resolveInitialPoint(
                        preferredX = defaultStart.x + swipeOffsetPx,
                        preferredY = defaultStart.y,
                    )
                    val start = overlayController.resolveInitialPoint(
                        preferredX = currentGeometry?.start?.x ?: step.x ?: defaultStart.x,
                        preferredY = currentGeometry?.start?.y ?: step.y ?: defaultStart.y,
                    )
                    val end = overlayController.resolveInitialPoint(
                        preferredX = currentGeometry?.end?.x ?: step.endX ?: defaultEnd.x,
                        preferredY = currentGeometry?.end?.y ?: step.endY ?: defaultEnd.y,
                    )
                    runtimeStepGeometryMap[step.id] = RuntimeStepGeometry(start = start, end = end)
                    visibleIndex += 2
                    step.copy(
                        x = start.x,
                        y = start.y,
                        endX = end.x,
                        endY = end.y,
                        durationMs = step.durationMs.coerceAtLeast(200L),
                    )
                }

                ActionType.WAIT -> step.copy(durationMs = step.durationMs.coerceAtLeast(1L))
            }
        }

        if (selectedStepId == null) {
            selectedStepId = resolvePreferredSelectedStep(taskWithSteps.copy(steps = normalizedSteps))?.id
        }

        return taskWithSteps.copy(steps = normalizedSteps)
    }

    private fun buildMarkerModels(
        taskWithSteps: TaskWithSteps,
    ): List<OverlayMarkerModel> {
        return taskWithSteps.steps
            .filter { it.enabled }
            .sortedBy { it.orderIndex }
            .flatMapIndexed { index, step ->
                val displayIndex = index + 1
                when (step.actionTypeEnum()) {
                    ActionType.TAP,
                    ActionType.LONG_PRESS,
                    -> {
                        val point = step.x?.let { x -> step.y?.let { y -> ScreenPoint(x, y) } } ?: return@flatMapIndexed emptyList()
                        listOf(
                            OverlayMarkerModel(
                                markerId = markerId(step.id, OverlayMarkerRole.PRIMARY),
                                stepId = step.id,
                                orderIndex = displayIndex,
                                label = displayIndex.toString(),
                                actionType = step.actionTypeEnum(),
                                point = point,
                                role = OverlayMarkerRole.PRIMARY,
                                isSelected = step.id == selectedStepId,
                            ),
                        )
                    }

                    ActionType.SWIPE -> {
                        val start = step.x?.let { x -> step.y?.let { y -> ScreenPoint(x, y) } }
                        val end = step.endX?.let { x -> step.endY?.let { y -> ScreenPoint(x, y) } }
                        if (start == null || end == null) return@flatMapIndexed emptyList()
                        listOf(
                            OverlayMarkerModel(
                                markerId = markerId(step.id, OverlayMarkerRole.START),
                                stepId = step.id,
                                orderIndex = displayIndex,
                                label = appContext.getString(R.string.overlay_marker_label_start, displayIndex),
                                actionType = ActionType.SWIPE,
                                point = start,
                                role = OverlayMarkerRole.START,
                                isSelected = step.id == selectedStepId,
                            ),
                            OverlayMarkerModel(
                                markerId = markerId(step.id, OverlayMarkerRole.END),
                                stepId = step.id,
                                orderIndex = displayIndex,
                                label = appContext.getString(R.string.overlay_marker_label_end, displayIndex),
                                actionType = ActionType.SWIPE,
                                point = end,
                                role = OverlayMarkerRole.END,
                                isSelected = step.id == selectedStepId,
                            ),
                        )
                    }

                    ActionType.WAIT -> emptyList()
                }
            }
    }

    private fun resolvePreferredSelectedStep(
        taskWithSteps: TaskWithSteps,
    ): ActionStepEntity? {
        val steps = taskWithSteps.steps.filter { it.enabled }.sortedBy { it.orderIndex }
        val currentSelected = steps.firstOrNull { it.id == selectedStepId }
        if (currentSelected != null) return currentSelected
        return steps.firstOrNull { it.actionTypeEnum() != ActionType.WAIT } ?: steps.firstOrNull()
    }

    private fun resolveCurrentTestableStep(
        taskWithSteps: TaskWithSteps,
    ): ActionStepEntity? {
        val steps = taskWithSteps.steps.filter { it.enabled }.sortedBy { it.orderIndex }
        return steps.firstOrNull { it.id == selectedStepId && it.actionTypeEnum() != ActionType.WAIT }
            ?: steps.firstOrNull { it.actionTypeEnum() != ActionType.WAIT }
    }

    private fun resolveRuntimeStep(
        step: ActionStepEntity,
    ): ActionStepEntity {
        val geometry = runtimeStepGeometryMap[step.id]
        return when (step.actionTypeEnum()) {
            ActionType.TAP,
            ActionType.LONG_PRESS,
            -> step.copy(
                x = geometry?.start?.x ?: step.x,
                y = geometry?.start?.y ?: step.y,
            )

            ActionType.SWIPE -> step.copy(
                x = geometry?.start?.x ?: step.x,
                y = geometry?.start?.y ?: step.y,
                endX = geometry?.end?.x ?: step.endX,
                endY = geometry?.end?.y ?: step.endY,
            )

            ActionType.WAIT -> step
        }
    }

    private fun defaultVisiblePoint(
        center: ScreenPoint,
        index: Int,
    ): ScreenPoint {
        val column = index % 3
        val row = index / 3
        val offsetX = (column - 1) * markerSpacingPx
        val offsetY = row * markerSpacingPx
        return overlayController.resolveInitialPoint(
            preferredX = center.x + offsetX,
            preferredY = center.y + offsetY,
        )
    }

    private fun requireStepPoint(
        step: ActionStepEntity,
        isLongPress: Boolean,
    ): ScreenPoint? {
        val x = step.x
        val y = step.y
        if (x == null || y == null) {
            publishError(if (isLongPress) RunnerError.LongPressPointNotSet else RunnerError.TapPointNotSet, true)
            return null
        }
        return ScreenPoint(x = x, y = y)
    }

    private fun requireSwipePoints(
        step: ActionStepEntity,
    ): Pair<ScreenPoint, ScreenPoint>? {
        val startX = step.x
        val startY = step.y
        val endX = step.endX
        val endY = step.endY
        if (startX == null || startY == null || endX == null || endY == null) {
            publishError(RunnerError.SwipePointNotSet, true)
            return null
        }
        return ScreenPoint(startX, startY) to ScreenPoint(endX, endY)
    }

    private fun persistVisibleStepGeometriesAsync(
        taskWithSteps: TaskWithSteps,
    ) {
        engineScope.launch {
            taskWithSteps.steps.filter { it.enabled }.forEach { step ->
                when (step.actionTypeEnum()) {
                    ActionType.TAP,
                    ActionType.LONG_PRESS,
                    -> {
                        val x = step.x ?: return@forEach
                        val y = step.y ?: return@forEach
                        runCatching {
                            taskRepository.updateTapStepPosition(taskWithSteps.task.id, step.id, x, y)
                        }.onFailure { throwable ->
                            Log.e(TAG, "persistVisibleStepGeometries point failed stepId=${step.id}", throwable)
                        }
                    }

                    ActionType.SWIPE -> {
                        val startX = step.x ?: return@forEach
                        val startY = step.y ?: return@forEach
                        val endX = step.endX ?: return@forEach
                        val endY = step.endY ?: return@forEach
                        runCatching {
                            taskRepository.updateSwipeStepPosition(taskWithSteps.task.id, step.id, startX, startY, endX, endY)
                        }.onFailure { throwable ->
                            Log.e(TAG, "persistVisibleStepGeometries swipe failed stepId=${step.id}", throwable)
                        }
                    }

                    ActionType.WAIT -> Unit
                }
            }
        }
    }

    private suspend fun persistStepGeometry(
        stepId: Long,
    ) {
        val taskId = activeTaskId ?: return
        val taskWithSteps = taskRepository.getTask(taskId) ?: return
        val step = taskWithSteps.steps.firstOrNull { it.id == stepId } ?: return
        val geometry = runtimeStepGeometryMap[stepId] ?: return
        when (step.actionTypeEnum()) {
            ActionType.TAP,
            ActionType.LONG_PRESS,
            -> {
                val point = geometry.start ?: return
                runCatching {
                    taskRepository.updateTapStepPosition(taskId, stepId, point.x, point.y)
                }.onFailure { throwable ->
                    Log.e(TAG, "persistStepGeometry point failed stepId=$stepId", throwable)
                }
            }

            ActionType.SWIPE -> {
                val start = geometry.start ?: return
                val end = geometry.end ?: return
                runCatching {
                    taskRepository.updateSwipeStepPosition(taskId, stepId, start.x, start.y, end.x, end.y)
                }.onFailure { throwable ->
                    Log.e(TAG, "persistStepGeometry swipe failed stepId=$stepId", throwable)
                }
            }

            ActionType.WAIT -> Unit
        }
    }

    private fun selectStep(
        taskWithSteps: TaskWithSteps,
        stepId: Long,
        @StringRes statusMessageRes: Int?,
    ) {
        selectedStepId = stepId
        publishOverlaySessionState(
            createOverlaySessionState(
                normalizedTask = taskWithSteps,
                isTargetVisible = _overlaySessionState.value.isTargetVisible,
                statusMessageRes = statusMessageRes ?: _overlaySessionState.value.statusMessageRes,
            ),
        )
        engineScope.launch { syncOverlayTargets(taskWithSteps) }
    }

    private fun ensureAccessibilityServiceReady(
        source: String,
    ): MyAccessibilityService? {
        val enabled = MyAccessibilityService.isEnabled(appContext)
        val service = MyAccessibilityService.current()
        Log.i(TAG, "$source accessibilityEnabled=$enabled accessibilityInstanceReady=${service != null}")
        return when {
            !enabled -> {
                publishError(RunnerError.AccessibilityDisabled, true)
                null
            }
            service == null -> {
                publishError(RunnerError.AccessibilityServiceUnavailable, true)
                null
            }
            else -> service
        }
    }

    @StringRes
    private fun successStatusRes(
        actionType: ActionType,
    ): Int {
        return when (actionType) {
            ActionType.TAP -> R.string.overlay_status_tap_completed
            ActionType.LONG_PRESS -> R.string.overlay_status_long_press_completed
            ActionType.SWIPE -> R.string.overlay_status_swipe_completed
            ActionType.WAIT -> R.string.overlay_status_ready
        }
    }

    private fun markerId(
        stepId: Long,
        role: OverlayMarkerRole,
    ): String = "step:$stepId:${role.name.lowercase()}"

    private fun parseMarkerId(
        markerId: String,
    ): MarkerMeta? {
        val parts = markerId.split(':')
        if (parts.size != 3) return null
        val stepId = parts[1].toLongOrNull() ?: return null
        val role = runCatching { OverlayMarkerRole.valueOf(parts[2].uppercase()) }.getOrNull() ?: return null
        return MarkerMeta(stepId, role)
    }

    private fun clearActiveSessionLocal() {
        activeTaskId = null
        selectedStepId = null
        runtimeStepGeometryMap.clear()
    }

    private fun mapThrowableToRunnerError(
        throwable: Throwable,
    ): RunnerError {
        return when (throwable) {
            is CancellationException -> RunnerError.Unknown(throwable.message)
            else -> RunnerError.Unknown(throwable.message)
        }
    }

    private data class ActiveTaskContext(
        val taskWithSteps: TaskWithSteps,
        val service: MyAccessibilityService?,
    )

    private data class RuntimeStepGeometry(
        val start: ScreenPoint? = null,
        val end: ScreenPoint? = null,
    )

    private data class MarkerMeta(
        val stepId: Long,
        val role: OverlayMarkerRole,
    )

    private companion object {
        const val CONTROL_POLL_INTERVAL_MS = 100L
        const val TAG = "ClickAssistRunner"
    }
}
