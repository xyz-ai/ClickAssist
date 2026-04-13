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
import com.example.clickassist.service.overlay.OverlayPanelSpec
import com.example.clickassist.service.overlay.OverlayPanelType
import com.example.clickassist.service.overlay.OverlaySchemeItem
import com.example.clickassist.service.overlay.OverlayStepEditorDraft
import com.example.clickassist.service.overlay.OverlayMarkerRole
import com.example.clickassist.service.overlay.OverlayWaitStepItem
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
import kotlinx.coroutines.flow.first
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
    private var activePanelType: OverlayPanelType? = null
    private var activePanelMessageRes: Int? = null
    private var placementMode: OverlayPlacementMode = OverlayPlacementMode.NONE
    private var pendingSwipeStartPoint: ScreenPoint? = null
    private val runtimeStepGeometryMap = ConcurrentHashMap<Long, RuntimeStepGeometry>()

    init {
        overlayController.bindToolbarCallbacks(
            OverlayToolbarCallbacks(
                onStartRequested = ::handleToolbarStartRequested,
                onPauseRequested = ::pause,
                onStopRequested = ::stop,
                onAddNodeRequested = ::handleToolbarAddNodeRequested,
                onDeleteSelectedRequested = ::deleteSelectedNode,
                onSettingsRequested = ::handleToolbarSettingsRequested,
                onTargetToggleRequested = ::toggleTargetVisibility,
            ),
        )
        overlayController.bindHandleExpandCallback(::restoreToolbarFromHandle)
    }

    fun enterFloatingMode(taskId: Long) {
        engineScope.launch { enterFloatingModeInternal(taskId) }
    }

    fun testCurrentStep() {
        engineScope.launch { testCurrentStepInternal() }
    }

    fun toggleTargetVisibility() {
        Log.i(ACTION_TAG, "toggle target requested source=publicApi")
        engineScope.launch { toggleTargetVisibilityInternal() }
    }

    fun deleteSelectedNode() {
        Log.i(ACTION_TAG, "delete selected node requested source=publicApi")
        engineScope.launch { deleteSelectedNodeInternal() }
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
            engineScope.launch {
                clearPlacementState(syncOverlay = true)
                Log.i(TAG, "pause requested taskId=$activeTaskId")
                publishState(RunnerState.PAUSED)
            }
        }
    }

    fun resume() {
        if (_runnerState.value == RunnerState.PAUSED) {
            Log.i(TAG, "resume requested")
            publishError(null)
            if (runnerJob == null) {
                engineScope.launch { startActiveTaskInternal() }
            } else {
                publishStatusMessage(null)
                publishState(RunnerState.RUNNING)
            }
        }
    }

    fun stop() {
        engineScope.launch {
            clearPlacementState(syncOverlay = true)
            Log.i(TAG, "stop requested taskId=$activeTaskId runnerState=${_runnerState.value}")
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

    fun closeActivePanel() {
        engineScope.launch { closeActivePanelInternal() }
    }

    fun release() {
        runnerJob?.cancel()
        clearActiveSessionLocal()
        engineScope.cancel()
        overlayController.release()
    }

    fun requestCoordinateRecorder() = overlayController.requestCoordinateRecorder()

    fun requestJsonExport() = overlayController.requestJsonExport()

    fun requestTaskTemplateClone() { /* TODO reserve task template clone entry */
    }

    private fun handleToolbarStartRequested() {
        Log.i(
            ACTION_TAG,
            "toolbar start clicked state=${_runnerState.value} floatingMode=${_overlaySessionState.value.isFloatingModeEnabled}",
        )
        startActiveTask()
    }

    private fun handleToolbarAddNodeRequested() {
        Log.i(
            ACTION_TAG,
            "toolbar add node clicked taskId=$activeTaskId hidden=${_overlaySessionState.value.isToolbarHidden}",
        )
        togglePanel(OverlayPanelType.ADD_NODE)
    }

    private fun handleToolbarSettingsRequested() {
        Log.i(
            ACTION_TAG,
            "toolbar settings clicked taskId=$activeTaskId hidden=${_overlaySessionState.value.isToolbarHidden}",
        )
        togglePanel(OverlayPanelType.SETTINGS)
    }

    private fun handleBackgroundTap(
        point: ScreenPoint,
    ) {
        engineScope.launch {
            handleBackgroundTapInternal(point)
        }
    }

    private suspend fun enterFloatingModeInternal(taskId: Long) {
        Log.i(TAG, "enterFloatingMode taskId=$taskId")
        if (!overlayController.hasPermission()) {
            publishError(RunnerError.OverlayPermissionDenied, true)
            return
        }

        stopRunnerJobAndResetState(clearError = true)
        overlayController.hideFloatingMode(clearTargetPoint = true)
        clearActiveSessionLocal()
        activePanelType = null
        activePanelMessageRes = null
        placementMode = OverlayPlacementMode.NONE
        pendingSwipeStartPoint = null

        val loaded = loadTaskIntoFloatingMode(
            taskId = taskId,
            preferredSelectionHint = null,
            statusMessageRes = R.string.overlay_status_ready,
            initialTargetVisible = true,
        )
        if (loaded == null) {
            clearActiveSessionLocal()
            publishOverlaySessionState(OverlaySessionState())
            return
        }
        persistVisibleStepGeometriesAsync(loaded)
        publishError(null)
    }

    private suspend fun startActiveTaskInternal() {
        Log.i(TAG, "startActiveTaskInternal")
        clearPlacementState(syncOverlay = true)
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

    private suspend fun startPlacementMode(
        actionType: ActionType,
    ) {
        if (!_overlaySessionState.value.isFloatingModeEnabled || activeTaskId == null) {
            publishError(RunnerError.NoTaskSelected, true)
            return
        }

        activePanelType = null
        activePanelMessageRes = null
        overlayController.hidePanel()
        pendingSwipeStartPoint = null
        placementMode = when (actionType) {
            ActionType.TAP -> OverlayPlacementMode.PLACE_TAP
            ActionType.LONG_PRESS -> OverlayPlacementMode.PLACE_LONG_PRESS
            ActionType.SWIPE -> OverlayPlacementMode.PLACE_SWIPE_START
            ActionType.WAIT -> OverlayPlacementMode.NONE
        }

        val taskId = activeTaskId ?: return
        val task = taskRepository.getTask(taskId)?.let(::normalizeTaskForRuntime) ?: return
        publishOverlaySessionState(
            createOverlaySessionState(
                normalizedTask = task,
                isTargetVisible = true,
                statusMessageRes = null,
            ),
        )
        syncOverlayTargets(task)
    }

    private suspend fun handleBackgroundTapInternal(
        point: ScreenPoint,
    ) {
        when (placementMode) {
            OverlayPlacementMode.NONE -> Unit
            OverlayPlacementMode.PLACE_TAP -> {
                clearPlacementState(syncOverlay = false)
                addPlacedPointStep(ActionType.TAP, point)
            }

            OverlayPlacementMode.PLACE_LONG_PRESS -> {
                clearPlacementState(syncOverlay = false)
                addPlacedPointStep(ActionType.LONG_PRESS, point)
            }

            OverlayPlacementMode.PLACE_SWIPE_START -> {
                pendingSwipeStartPoint = point
                placementMode = OverlayPlacementMode.PLACE_SWIPE_END
                syncOverlaySessionSelection()
            }

            OverlayPlacementMode.PLACE_SWIPE_END -> {
                val start = pendingSwipeStartPoint ?: point
                clearPlacementState(syncOverlay = false)
                addPlacedSwipeStep(start = start, end = point)
            }
        }
    }

    private suspend fun addPlacedPointStep(
        actionType: ActionType,
        point: ScreenPoint,
    ) {
        mutateCurrentTaskStructure(
            successMessageRes = R.string.overlay_panel_save_success,
            selectionHintProvider = { steps ->
                steps.firstOrNull { step ->
                    step.actionTypeEnum() == actionType &&
                            step.x == point.x &&
                            step.y == point.y
                }?.selectionHint()
            },
        ) { current ->
            val ordered = current.steps.sortedBy { it.orderIndex }.toMutableList()
            val insertIndex = currentInsertionIndex(ordered)
            ordered.add(
                insertIndex,
                ActionStepEntity(
                    taskId = current.task.id,
                    orderIndex = insertIndex,
                    actionType = actionType.storageValue,
                    x = point.x,
                    y = point.y,
                    durationMs = defaultDurationFor(actionType),
                ),
            )
            ordered
        }
    }

    private suspend fun addPlacedSwipeStep(
        start: ScreenPoint,
        end: ScreenPoint,
    ) {
        mutateCurrentTaskStructure(
            successMessageRes = R.string.overlay_panel_save_success,
            selectionHintProvider = { steps ->
                steps.firstOrNull { step ->
                    step.actionTypeEnum() == ActionType.SWIPE &&
                            step.x == start.x &&
                            step.y == start.y &&
                            step.endX == end.x &&
                            step.endY == end.y
                }?.selectionHint()
            },
        ) { current ->
            val ordered = current.steps.sortedBy { it.orderIndex }.toMutableList()
            val insertIndex = currentInsertionIndex(ordered)
            ordered.add(
                insertIndex,
                ActionStepEntity(
                    taskId = current.task.id,
                    orderIndex = insertIndex,
                    actionType = ActionType.SWIPE.storageValue,
                    x = start.x,
                    y = start.y,
                    endX = end.x,
                    endY = end.y,
                    durationMs = defaultDurationFor(ActionType.SWIPE),
                ),
            )
            ordered
        }
    }

    private suspend fun clearPlacementState(
        syncOverlay: Boolean,
    ) {
        if (placementMode == OverlayPlacementMode.NONE && pendingSwipeStartPoint == null) {
            return
        }
        placementMode = OverlayPlacementMode.NONE
        pendingSwipeStartPoint = null
        if (syncOverlay) {
            syncOverlaySessionSelection()
        }
    }

    private suspend fun deleteSelectedNodeInternal() {
        clearPlacementState(syncOverlay = true)
        val taskId = activeTaskId ?: return
        val task = taskRepository.getTask(taskId) ?: return
        val selected = task.steps.firstOrNull { it.id == selectedStepId } ?: return
        if (selected.actionTypeEnum() == ActionType.WAIT) {
            return
        }
        deleteStep(selected.id)
    }

    private fun togglePanel(
        panelType: OverlayPanelType,
    ) {
        engineScope.launch { togglePanelInternal(panelType) }
    }

    private suspend fun togglePanelInternal(
        panelType: OverlayPanelType,
    ) {
        if (!_overlaySessionState.value.isFloatingModeEnabled || activeTaskId == null) {
            publishError(RunnerError.NoTaskSelected, true)
            return
        }
        clearPlacementState(syncOverlay = true)
        if (activePanelType == panelType) {
            closeActivePanelInternal()
            return
        }
        activePanelType = panelType
        activePanelMessageRes = null
        syncActivePanel()
    }

    private suspend fun closeActivePanelInternal() {
        activePanelType = null
        activePanelMessageRes = null
        overlayController.hidePanel()
        if (!_overlaySessionState.value.isFloatingModeEnabled) {
            return
        }
        val taskId = activeTaskId ?: run {
            publishOverlaySessionState(_overlaySessionState.value.copy(activePanelType = null))
            return
        }
        val task = taskRepository.getTask(taskId)?.let(::normalizeTaskForRuntime)
        if (task != null) {
            publishOverlaySessionState(
                createOverlaySessionState(
                    normalizedTask = task,
                    isTargetVisible = _overlaySessionState.value.isTargetVisible,
                    statusMessageRes = _overlaySessionState.value.statusMessageRes,
                ),
            )
        } else {
            publishOverlaySessionState(_overlaySessionState.value.copy(activePanelType = null))
        }
    }

    private fun hideToolbarToHandle() {
        Log.i(
            ACTION_TAG,
            "hide toolbar requested taskId=$activeTaskId panel=$activePanelType",
        )
        engineScope.launch { hideToolbarToHandleInternal() }
    }

    private suspend fun hideToolbarToHandleInternal() {
        if (!_overlaySessionState.value.isFloatingModeEnabled) {
            publishError(RunnerError.NoTaskSelected, true)
            return
        }
        clearPlacementState(syncOverlay = true)
        activePanelType = null
        activePanelMessageRes = null
        overlayController.hidePanel()
        val hidden = overlayController.hideToolbarToHandle()
        Log.i(ACTION_TAG, "hide toolbar result success=$hidden taskId=$activeTaskId")
        if (!hidden) {
            publishError(
                if (!overlayController.hasPermission()) RunnerError.OverlayPermissionDenied else RunnerError.Unknown(
                    "Unable to hide toolbar"
                ),
                true,
            )
            return
        }
        val taskId = activeTaskId ?: run {
            publishOverlaySessionState(
                _overlaySessionState.value.copy(
                    isToolbarHidden = true,
                    activePanelType = null
                )
            )
            return
        }
        val task = taskRepository.getTask(taskId)?.let(::normalizeTaskForRuntime)
        if (task != null) {
            publishOverlaySessionState(
                createOverlaySessionState(
                    normalizedTask = task,
                    isTargetVisible = _overlaySessionState.value.isTargetVisible,
                    statusMessageRes = R.string.overlay_status_toolbar_hidden,
                    isToolbarHidden = true,
                ),
            )
        } else {
            publishOverlaySessionState(
                _overlaySessionState.value.copy(
                    isToolbarHidden = true,
                    activePanelType = null,
                    statusMessageRes = R.string.overlay_status_toolbar_hidden,
                ),
            )
        }
    }

    private fun restoreToolbarFromHandle() {
        Log.i(
            ACTION_TAG,
            "restore toolbar requested taskId=$activeTaskId hidden=${_overlaySessionState.value.isToolbarHidden}",
        )
        engineScope.launch { restoreToolbarFromHandleInternal() }
    }

    private suspend fun restoreToolbarFromHandleInternal() {
        if (!_overlaySessionState.value.isFloatingModeEnabled) {
            publishError(RunnerError.NoTaskSelected, true)
            return
        }
        val shown = overlayController.showToolbarFromHandle()
        Log.i(ACTION_TAG, "restore toolbar result success=$shown taskId=$activeTaskId")
        if (!shown) {
            publishError(
                if (!overlayController.hasPermission()) RunnerError.OverlayPermissionDenied else RunnerError.Unknown(
                    "Unable to show toolbar"
                ),
                true,
            )
            return
        }
        val taskId = activeTaskId ?: run {
            publishOverlaySessionState(_overlaySessionState.value.copy(isToolbarHidden = false))
            return
        }
        val task = taskRepository.getTask(taskId)?.let(::normalizeTaskForRuntime)
        if (task != null) {
            publishOverlaySessionState(
                createOverlaySessionState(
                    normalizedTask = task,
                    isTargetVisible = _overlaySessionState.value.isTargetVisible,
                    statusMessageRes = R.string.overlay_status_toolbar_expanded,
                    isToolbarHidden = false,
                ),
            )
        } else {
            publishOverlaySessionState(
                _overlaySessionState.value.copy(
                    isToolbarHidden = false,
                    statusMessageRes = R.string.overlay_status_toolbar_expanded,
                ),
            )
        }
    }

    private fun launchRunner(taskWithSteps: TaskWithSteps) {
        Log.i(
            TAG,
            "launchRunner taskId=${taskWithSteps.task.id} stepCount=${taskWithSteps.steps.size}"
        )
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
                Log.i(
                    TAG,
                    "dispatchStep WAIT taskId=$taskId stepIndex=${stepIndex + 1} stepId=${runtimeStep.id} durationMs=$duration"
                )
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
        val targetVisible =
            _overlaySessionState.value.isTargetVisible && overlayController.isTargetVisible()
        if (targetVisible) {
            Log.i(
                TAG,
                "$source disabling target touch result=${
                    overlayController.setTargetTouchEnabled(false)
                }"
            )
        }
        return try {
            val dispatched = when (step.actionTypeEnum()) {
                ActionType.TAP -> {
                    val point = requireStepPoint(step, isLongPress = false) ?: return false
                    Log.i(
                        TAG,
                        "$source dispatch TAP taskId=$taskId stepId=${step.id} x=${point.x} y=${point.y} durationMs=${step.durationMs}"
                    )
                    service.dispatchTap(point.x, point.y, step.durationMs)
                }

                ActionType.LONG_PRESS -> {
                    val point = requireStepPoint(step, isLongPress = true) ?: return false
                    Log.i(
                        TAG,
                        "$source dispatch LONG_PRESS taskId=$taskId stepId=${step.id} x=${point.x} y=${point.y} durationMs=${step.durationMs}"
                    )
                    service.dispatchLongPress(point.x, point.y, step.durationMs)
                }

                ActionType.SWIPE -> {
                    val swipe = requireSwipePoints(step) ?: return false
                    Log.i(
                        TAG,
                        "$source dispatch SWIPE taskId=$taskId stepId=${step.id} start=${swipe.first} end=${swipe.second} durationMs=${step.durationMs}"
                    )
                    service.dispatchSwipe(
                        swipe.first.x,
                        swipe.first.y,
                        swipe.second.x,
                        swipe.second.y,
                        step.durationMs
                    )
                }

                ActionType.WAIT -> true
            }

            val lastStatus = MyAccessibilityService.lastDispatchStatus()
            Log.i(
                TAG,
                "$source dispatch result taskId=$taskId stepId=${step.id} actionType=${step.actionType} result=$dispatched lastStatus=$lastStatus"
            )
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
                Log.i(
                    TAG,
                    "$source restoring target touch result=${
                        overlayController.setTargetTouchEnabled(true)
                    }"
                )
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
        Log.i(
            TAG,
            "$source loaded taskId=$taskId stepCount=${normalizedTask.steps.size} selectedStepId=$selectedStepId"
        )

        val enabled = MyAccessibilityService.isEnabled(appContext)
        val service = MyAccessibilityService.current()
        Log.i(
            TAG,
            "$source accessibilityEnabled=$enabled accessibilityInstanceReady=${service != null}"
        )
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
        clearPlacementState(syncOverlay = false)
        val session = _overlaySessionState.value
        Log.i(
            TAG,
            "toggleTargetVisibility floatingMode=${session.isFloatingModeEnabled} visible=${session.isTargetVisible}"
        )
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
        val updated = overlayController.updateTargetLayer(
            markers = markers,
            isVisible = visible,
            placementMode = placementMode,
        )
        if (!updated) {
            publishError(
                if (!overlayController.hasPermission()) RunnerError.OverlayPermissionDenied else RunnerError.Unknown(
                    "Unable to update target visibility"
                ),
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
            val normalized = normalizeTaskForRuntime(task)
            if (_overlaySessionState.value.isTargetVisible) {
                syncOverlayTargets(normalized)
            }
            syncActivePanel(normalized)
        }
    }

    private fun handleMarkerSelected(
        markerId: String,
    ) {
        val markerMeta = parseMarkerId(markerId) ?: return
        selectedStepId = markerMeta.stepId
        placementMode = OverlayPlacementMode.NONE
        pendingSwipeStartPoint = null
        activePanelType = OverlayPanelType.STEP_EDITOR
        activePanelMessageRes = null
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
            syncActivePanel(normalized)
        }
    }

    private suspend fun syncOverlayTargets(
        taskWithSteps: TaskWithSteps,
    ) {
        val shouldShowMarkers = _overlaySessionState.value.isTargetVisible ||
                placementMode != OverlayPlacementMode.NONE
        overlayController.updateTargetLayer(
            markers = buildMarkerModels(taskWithSteps),
            isVisible = shouldShowMarkers,
            placementMode = placementMode,
        )
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
            if (session.isTargetVisible || placementMode != OverlayPlacementMode.NONE) {
                syncOverlayTargets(normalized)
            }
            syncActivePanel(normalized)
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
                    targetVisible = session.isTargetVisible,
                    selectedStepOrder = session.selectedStepOrder,
                    selectedStepActionType = session.selectedStepActionType,
                    canDeleteSelected = session.canDeleteSelected,
                    placementMode = session.placementMode,
                    statusMessageRes = session.statusMessageRes,
                ),
            )
        }
    }

    private fun createToolbarUiState(
        normalizedTask: TaskWithSteps? = null,
        targetVisible: Boolean = _overlaySessionState.value.isTargetVisible,
        selectedStepOrder: Int? = normalizedTask?.let { resolveSelectedStepOrder(it) }
            ?: _overlaySessionState.value.selectedStepOrder,
        selectedStepActionType: String? = normalizedTask?.let { resolveSelectedStepActionType(it) }
            ?: _overlaySessionState.value.selectedStepActionType,
        canDeleteSelected: Boolean = normalizedTask?.let { canDeleteSelected(it) }
            ?: _overlaySessionState.value.canDeleteSelected,
        placementMode: OverlayPlacementMode = this.placementMode,
        @StringRes statusMessageRes: Int? = _overlaySessionState.value.statusMessageRes,
    ): OverlayToolbarUiState {
        return OverlayToolbarUiState(
            runnerState = _runnerState.value,
            isTargetVisible = targetVisible,
            selectedStepOrder = selectedStepOrder,
            selectedStepActionType = selectedStepActionType,
            canDeleteSelected = canDeleteSelected,
            placementMode = placementMode,
            statusMessageRes = statusMessageRes,
        )
    }

    private fun createOverlaySessionState(
        normalizedTask: TaskWithSteps,
        isTargetVisible: Boolean,
        @StringRes statusMessageRes: Int?,
        isToolbarHidden: Boolean = _overlaySessionState.value.isToolbarHidden,
    ): OverlaySessionState {
        return OverlaySessionState(
            isFloatingModeEnabled = true,
            activeTaskId = normalizedTask.task.id,
            activeTaskName = normalizedTask.task.name,
            isTargetVisible = isTargetVisible,
            isMultiPointMode = determineIsMultiPointMode(normalizedTask),
            stepCount = normalizedTask.steps.count { it.enabled },
            selectedStepOrder = resolveSelectedStepOrder(normalizedTask),
            selectedStepActionType = resolveSelectedStepActionType(normalizedTask),
            selectedStepIsNode = isSelectedStepNode(normalizedTask),
            canDeleteSelected = canDeleteSelected(normalizedTask),
            hasWaitSteps = normalizedTask.steps.any { it.actionTypeEnum() == ActionType.WAIT },
            placementMode = placementMode,
            activePanelType = activePanelType,
            statusMessageRes = statusMessageRes,
            isToolbarHidden = isToolbarHidden,
        )
    }

    private fun determineIsMultiPointMode(
        taskWithSteps: TaskWithSteps,
    ): Boolean {
        val visibleSteps =
            taskWithSteps.steps.filter { it.enabled && it.actionTypeEnum() != ActionType.WAIT }
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

    private fun isSelectedStepNode(
        taskWithSteps: TaskWithSteps,
    ): Boolean {
        val selected = taskWithSteps.steps.firstOrNull { it.id == selectedStepId } ?: return false
        return selected.actionTypeEnum() != ActionType.WAIT
    }

    private fun canDeleteSelected(
        taskWithSteps: TaskWithSteps,
    ): Boolean {
        return isSelectedStepNode(taskWithSteps)
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
            selectedStepId =
                resolvePreferredSelectedStep(taskWithSteps.copy(steps = normalizedSteps))?.id
        }

        return taskWithSteps.copy(steps = normalizedSteps)
    }

    private fun buildMarkerModels(
        taskWithSteps: TaskWithSteps,
    ): List<OverlayMarkerModel> {
        val markers = taskWithSteps.steps
            .filter { it.enabled }
            .sortedBy { it.orderIndex }
            .flatMapIndexed { index, step ->
                val displayIndex = index + 1
                when (step.actionTypeEnum()) {
                    ActionType.TAP,
                    ActionType.LONG_PRESS,
                        -> {
                        val point = step.x?.let { x -> step.y?.let { y -> ScreenPoint(x, y) } }
                            ?: return@flatMapIndexed emptyList()
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
                                label = appContext.getString(
                                    R.string.overlay_marker_label_start,
                                    displayIndex
                                ),
                                actionType = ActionType.SWIPE,
                                point = start,
                                role = OverlayMarkerRole.START,
                                isSelected = step.id == selectedStepId,
                                connectedMarkerId = markerId(step.id, OverlayMarkerRole.END),
                            ),
                            OverlayMarkerModel(
                                markerId = markerId(step.id, OverlayMarkerRole.END),
                                stepId = step.id,
                                orderIndex = displayIndex,
                                label = appContext.getString(
                                    R.string.overlay_marker_label_end,
                                    displayIndex
                                ),
                                actionType = ActionType.SWIPE,
                                point = end,
                                role = OverlayMarkerRole.END,
                                isSelected = step.id == selectedStepId,
                                connectedMarkerId = markerId(step.id, OverlayMarkerRole.START),
                            ),
                        )
                    }

                    ActionType.WAIT -> emptyList()
                }
            }
        if (placementMode == OverlayPlacementMode.PLACE_SWIPE_END && pendingSwipeStartPoint != null) {
            return markers + OverlayMarkerModel(
                markerId = PREVIEW_SWIPE_START_MARKER_ID,
                stepId = PREVIEW_STEP_ID,
                orderIndex = markers.size + 1,
                label = appContext.getString(R.string.overlay_marker_preview_start),
                actionType = ActionType.SWIPE,
                point = pendingSwipeStartPoint!!,
                role = OverlayMarkerRole.START,
                isSelected = false,
            )
        }
        return markers
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
            publishError(
                if (isLongPress) RunnerError.LongPressPointNotSet else RunnerError.TapPointNotSet,
                true
            )
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
                            taskRepository.updateTapStepPosition(
                                taskWithSteps.task.id,
                                step.id,
                                x,
                                y
                            )
                        }.onFailure { throwable ->
                            Log.e(
                                TAG,
                                "persistVisibleStepGeometries point failed stepId=${step.id}",
                                throwable
                            )
                        }
                    }

                    ActionType.SWIPE -> {
                        val startX = step.x ?: return@forEach
                        val startY = step.y ?: return@forEach
                        val endX = step.endX ?: return@forEach
                        val endY = step.endY ?: return@forEach
                        runCatching {
                            taskRepository.updateSwipeStepPosition(
                                taskWithSteps.task.id,
                                step.id,
                                startX,
                                startY,
                                endX,
                                endY
                            )
                        }.onFailure { throwable ->
                            Log.e(
                                TAG,
                                "persistVisibleStepGeometries swipe failed stepId=${step.id}",
                                throwable
                            )
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
                    taskRepository.updateSwipeStepPosition(
                        taskId,
                        stepId,
                        start.x,
                        start.y,
                        end.x,
                        end.y
                    )
                }.onFailure { throwable ->
                    Log.e(TAG, "persistStepGeometry swipe failed stepId=$stepId", throwable)
                }
            }

            ActionType.WAIT -> Unit
        }
    }

    private suspend fun loadTaskIntoFloatingMode(
        taskId: Long,
        preferredSelectionHint: StepSelectionHint?,
        @StringRes statusMessageRes: Int?,
        initialTargetVisible: Boolean?,
    ): TaskWithSteps? {
        val taskWithSteps = taskRepository.getTask(taskId) ?: run {
            publishError(RunnerError.TaskNotFound(taskId), true)
            return null
        }
        val normalizedTask = normalizeTaskForRuntime(taskWithSteps)
        selectedStepId = resolveSelectedStepId(normalizedTask, preferredSelectionHint)
        activeTaskId = normalizedTask.task.id

        val markers = buildMarkerModels(normalizedTask)
        val targetVisible = when (initialTargetVisible) {
            null -> _overlaySessionState.value.isTargetVisible && markers.isNotEmpty()
            else -> initialTargetVisible && markers.isNotEmpty()
        }

        val shown = if (!_overlaySessionState.value.isFloatingModeEnabled) {
            overlayController.showFloatingMode(
                initialMarkers = markers,
                targetVisible = targetVisible,
                placementMode = placementMode,
                toolbarUiState = createToolbarUiState(
                    normalizedTask = normalizedTask,
                    targetVisible = targetVisible,
                    statusMessageRes = statusMessageRes,
                ),
                onBackgroundTap = ::handleBackgroundTap,
                onMarkerChanged = ::handleMarkerChanged,
                onMarkerDragEnd = ::handleMarkerDragEnd,
                onMarkerSelected = ::handleMarkerSelected,
            )
        } else {
            overlayController.updateToolbarState(
                createToolbarUiState(
                    normalizedTask = normalizedTask,
                    targetVisible = targetVisible,
                    statusMessageRes = statusMessageRes,
                ),
            )
            overlayController.updateTargetLayer(
                markers = markers,
                isVisible = targetVisible,
                placementMode = placementMode,
            )
        }

        if (!shown) {
            publishError(
                if (!overlayController.hasPermission()) {
                    RunnerError.OverlayPermissionDenied
                } else {
                    RunnerError.Unknown("Unable to show floating overlay")
                },
                true,
            )
            return null
        }

        publishOverlaySessionState(
            createOverlaySessionState(
                normalizedTask = normalizedTask,
                isTargetVisible = targetVisible,
                statusMessageRes = statusMessageRes,
            ),
        )
        syncActivePanel(normalizedTask)
        return normalizedTask
    }

    private suspend fun syncActivePanel(
        taskWithSteps: TaskWithSteps? = null,
    ) {
        val panelType = activePanelType
        if (!_overlaySessionState.value.isFloatingModeEnabled || panelType == null) {
            overlayController.hidePanel()
            return
        }

        val taskId = activeTaskId ?: run {
            overlayController.hidePanel()
            return
        }
        val normalizedTask =
            taskWithSteps ?: taskRepository.getTask(taskId)?.let(::normalizeTaskForRuntime) ?: run {
                overlayController.hidePanel()
                return
            }
        val spec = buildPanelSpec(normalizedTask) ?: run {
            overlayController.hidePanel()
            return
        }
        val shown = overlayController.showPanel(
            spec = spec,
            onCloseRequested = ::closeActivePanel,
        )
        if (!shown) {
            publishError(RunnerError.OverlayPermissionDenied, true)
        }
    }

    private suspend fun buildPanelSpec(
        taskWithSteps: TaskWithSteps,
    ): OverlayPanelSpec? {
        return when (activePanelType) {
            OverlayPanelType.SETTINGS -> {
                val schemes = taskRepository.observeTasks().first()
                    .map { task ->
                        OverlaySchemeItem(
                            taskId = task.task.id,
                            name = task.task.name,
                            stepCount = task.steps.size,
                            isCurrent = task.task.id == taskWithSteps.task.id,
                        )
                    }
                OverlayPanelSpec.Settings(
                    currentSchemeId = taskWithSteps.task.id,
                    currentTaskName = taskWithSteps.task.name,
                    saveAsDefaultName = appContext.getString(
                        R.string.overlay_panel_save_as_default_name,
                        taskWithSteps.task.name,
                    ),
                    totalRounds = taskWithSteps.task.totalRounds.toString(),
                    infiniteRounds = taskWithSteps.task.infiniteRounds,
                    schemes = schemes,
                    waitSteps = taskWithSteps.steps
                        .sortedBy { it.orderIndex }
                        .filter { it.actionTypeEnum() == ActionType.WAIT }
                        .map { step ->
                            OverlayWaitStepItem(
                                stepId = step.id,
                                orderIndex = step.orderIndex,
                                enabled = step.enabled,
                                isSelected = step.id == selectedStepId,
                            )
                        },
                    messageRes = activePanelMessageRes,
                    onSaveCurrent = { name, totalRounds, infiniteRounds ->
                        engineScope.launch { saveSettings(name, totalRounds, infiniteRounds) }
                    },
                    onSaveAs = { name, totalRounds, infiniteRounds ->
                        engineScope.launch {
                            saveAsNewScheme(
                                taskWithSteps,
                                name,
                                totalRounds,
                                infiniteRounds
                            )
                        }
                    },
                    onSchemeSelected = { taskId ->
                        engineScope.launch { switchTaskScheme(taskId) }
                    },
                    onWaitStepSelected = { stepId ->
                        engineScope.launch { openStepEditorForStep(stepId) }
                    },
                    onDeleteWaitStep = { stepId ->
                        engineScope.launch { deleteStep(stepId) }
                    },
                    onHideToolbar = ::hideToolbarToHandle,
                    onCloseFloating = ::exitFloatingMode,
                )
            }

            OverlayPanelType.ADD_NODE -> {
                OverlayPanelSpec.AddNode(
                    messageRes = activePanelMessageRes,
                    onAddStep = { actionType ->
                        engineScope.launch { startPlacementMode(actionType) }
                    },
                )
            }

            OverlayPanelType.STEP_EDITOR -> {
                val selectedStep = taskWithSteps.steps.firstOrNull { it.id == selectedStepId }
                OverlayPanelSpec.StepEditor(
                    draft = selectedStep?.toEditorDraft(),
                    messageRes = activePanelMessageRes,
                    onSave = { draft -> engineScope.launch { saveCurrentStep(draft) } },
                    onDeleteStep = { stepId -> engineScope.launch { deleteStep(stepId) } },
                )
            }

            null -> null
        }
    }

    private suspend fun openStepEditorForStep(
        stepId: Long,
    ) {
        val taskId = activeTaskId ?: return
        val task = taskRepository.getTask(taskId)?.let(::normalizeTaskForRuntime) ?: return
        selectedStepId = stepId
        activePanelType = OverlayPanelType.STEP_EDITOR
        activePanelMessageRes = null
        publishOverlaySessionState(
            createOverlaySessionState(
                normalizedTask = task,
                isTargetVisible = _overlaySessionState.value.isTargetVisible,
                statusMessageRes = _overlaySessionState.value.statusMessageRes,
            ),
        )
        if (_overlaySessionState.value.isTargetVisible || placementMode != OverlayPlacementMode.NONE) {
            syncOverlayTargets(task)
        }
        syncActivePanel(task)
    }

    private suspend fun deleteStep(
        stepId: Long,
    ) {
        mutateCurrentTaskStructure(
            successMessageRes = R.string.overlay_panel_save_success,
            selectionHintProvider = { currentSteps ->
                val ordered = currentSteps.sortedBy { it.orderIndex }
                val removedIndex = ordered.indexOfFirst { it.id == stepId }.coerceAtLeast(0)
                val remaining = ordered.filterNot { it.id == stepId }
                remaining.getOrNull(removedIndex.coerceAtMost(remaining.lastIndex))?.selectionHint()
            },
        ) { current ->
            current.steps.filterNot { it.id == stepId }
        }
    }

    private suspend fun moveStep(
        stepId: Long,
        direction: Int,
    ) {
        mutateCurrentTaskStructure(
            successMessageRes = R.string.overlay_panel_save_success,
            selectionHintProvider = { currentSteps ->
                currentSteps.firstOrNull { it.id == stepId }?.selectionHint()
            },
        ) { current ->
            val mutable = current.steps.sortedBy { it.orderIndex }.toMutableList()
            val currentIndex = mutable.indexOfFirst { it.id == stepId }
            if (currentIndex < 0) return@mutateCurrentTaskStructure mutable
            val targetIndex = (currentIndex + direction).coerceIn(0, mutable.lastIndex)
            if (currentIndex == targetIndex) return@mutateCurrentTaskStructure mutable
            val step = mutable.removeAt(currentIndex)
            mutable.add(targetIndex, step)
            mutable
        }
    }

    private suspend fun addStep(
        actionType: ActionType,
    ) {
        mutateCurrentTaskStructure(
            successMessageRes = R.string.overlay_panel_save_success,
            selectionHintProvider = {
                StepSelectionHint(
                    orderIndex = currentInsertionIndex(it),
                    actionType = actionType,
                )
            },
        ) { current ->
            val ordered = current.steps.sortedBy { it.orderIndex }.toMutableList()
            val insertIndex = currentInsertionIndex(ordered)
            ordered.add(
                insertIndex,
                ActionStepEntity(
                    taskId = current.task.id,
                    orderIndex = insertIndex,
                    actionType = actionType.storageValue,
                    durationMs = defaultDurationFor(actionType),
                ),
            )
            ordered
        }
    }

    private suspend fun saveLoopSettings(
        totalRoundsRaw: String,
        infiniteRounds: Boolean,
    ) {
        val taskId = activeTaskId ?: return
        val current = taskRepository.getTask(taskId) ?: run {
            setActivePanelMessage(R.string.error_task_not_found)
            return
        }
        val parsedRounds = if (infiniteRounds) {
            current.task.totalRounds.coerceAtLeast(1)
        } else {
            totalRoundsRaw.trim().toIntOrNull()?.takeIf { it > 0 } ?: run {
                setActivePanelMessage(R.string.error_invalid_total_rounds)
                return
            }
        }
        persistWorkbenchTask(
            originalTask = current,
            updatedTask = current.task.copy(
                totalRounds = parsedRounds,
                infiniteRounds = infiniteRounds,
                updatedAt = System.currentTimeMillis(),
            ),
            updatedSteps = current.steps,
            selectionHint = currentSelectionHint(current),
            successMessageRes = R.string.overlay_panel_save_success,
            autoRestartIfRunning = true,
        )
    }

    private suspend fun saveCurrentStep(
        draft: OverlayStepEditorDraft,
    ) {
        val taskId = activeTaskId ?: return
        val current = taskRepository.getTask(taskId) ?: run {
            setActivePanelMessage(R.string.error_task_not_found)
            return
        }
        val existingStep = current.steps.firstOrNull { it.id == draft.stepId } ?: run {
            setActivePanelMessage(R.string.overlay_panel_no_selected_step)
            return
        }
        val updatedStep = buildUpdatedStep(existingStep, draft) ?: run {
            publishStatusMessage(activePanelMessageRes)
            syncActivePanel()
            return
        }
        persistWorkbenchTask(
            originalTask = current,
            updatedTask = current.task.copy(updatedAt = System.currentTimeMillis()),
            updatedSteps = current.steps.map { step ->
                if (step.id == draft.stepId) updatedStep else step
            },
            selectionHint = StepSelectionHint(updatedStep.orderIndex, updatedStep.actionTypeEnum()),
            successMessageRes = R.string.overlay_panel_save_success,
            autoRestartIfRunning = true,
        )
    }

    private suspend fun saveCurrentScheme(
        rawName: String,
    ) {
        val taskId = activeTaskId ?: return
        val current = taskRepository.getTask(taskId) ?: run {
            setActivePanelMessage(R.string.error_task_not_found)
            return
        }
        val name = rawName.trim().ifEmpty {
            appContext.getString(R.string.default_task_name)
        }
        persistWorkbenchTask(
            originalTask = current,
            updatedTask = current.task.copy(
                name = name,
                updatedAt = System.currentTimeMillis(),
            ),
            updatedSteps = current.steps,
            selectionHint = currentSelectionHint(current),
            successMessageRes = R.string.overlay_panel_save_success,
            autoRestartIfRunning = true,
        )
    }

    private suspend fun saveSettings(
        rawName: String,
        totalRoundsRaw: String,
        infiniteRounds: Boolean,
    ) {
        val taskId = activeTaskId ?: return
        val current = taskRepository.getTask(taskId) ?: run {
            setActivePanelMessage(R.string.error_task_not_found)
            return
        }
        val name = rawName.trim().ifEmpty {
            appContext.getString(R.string.default_task_name)
        }
        val parsedRounds = if (infiniteRounds) {
            current.task.totalRounds.coerceAtLeast(1)
        } else {
            totalRoundsRaw.trim().toIntOrNull()?.takeIf { it > 0 } ?: run {
                setActivePanelMessage(R.string.error_invalid_total_rounds)
                return
            }
        }
        persistWorkbenchTask(
            originalTask = current,
            updatedTask = current.task.copy(
                name = name,
                totalRounds = parsedRounds,
                infiniteRounds = infiniteRounds,
                updatedAt = System.currentTimeMillis(),
            ),
            updatedSteps = current.steps,
            selectionHint = currentSelectionHint(current),
            successMessageRes = R.string.overlay_panel_save_success,
            autoRestartIfRunning = true,
        )
    }

    private suspend fun saveAsNewScheme(
        currentTask: TaskWithSteps,
        rawName: String,
        totalRoundsRaw: String,
        infiniteRounds: Boolean,
    ) {
        val name = rawName.trim().ifEmpty {
            appContext.getString(R.string.default_task_name)
        }
        val parsedRounds = if (infiniteRounds) {
            currentTask.task.totalRounds.coerceAtLeast(1)
        } else {
            totalRoundsRaw.trim().toIntOrNull()?.takeIf { it > 0 } ?: run {
                setActivePanelMessage(R.string.error_invalid_total_rounds)
                return
            }
        }
        val now = System.currentTimeMillis()
        try {
            val savedTaskId = taskRepository.saveTask(
                task = currentTask.task.copy(
                    id = 0L,
                    name = name,
                    totalRounds = parsedRounds,
                    infiniteRounds = infiniteRounds,
                    createdAt = now,
                    updatedAt = now,
                ),
                steps = currentTask.steps.map { it.copy(id = 0L, taskId = 0L) },
            )
            activePanelMessageRes = R.string.overlay_panel_save_as_success
            stopRunnerForWorkbenchChange()
            runtimeStepGeometryMap.clear()
            loadTaskIntoFloatingMode(
                taskId = savedTaskId,
                preferredSelectionHint = currentSelectionHint(currentTask),
                statusMessageRes = R.string.overlay_panel_save_as_success,
                initialTargetVisible = _overlaySessionState.value.isTargetVisible,
            )
            publishState(RunnerState.IDLE)
            publishStatusMessage(R.string.overlay_panel_save_as_success)
            publishError(null)
        } catch (throwable: Throwable) {
            Log.e(TAG, "saveAsNewScheme failed taskId=${currentTask.task.id}", throwable)
            activePanelMessageRes = R.string.overlay_panel_save_failed
            publishError(
                mapThrowableToRunnerError(throwable),
                true,
                R.string.overlay_panel_save_failed
            )
            syncActivePanel(currentTask)
        }
    }

    private suspend fun switchTaskScheme(
        taskId: Long,
    ) {
        if (taskId == activeTaskId) {
            setActivePanelMessage(R.string.overlay_panel_scheme_current)
            return
        }
        activePanelMessageRes = R.string.overlay_panel_scheme_switched
        stopRunnerForWorkbenchChange()
        runtimeStepGeometryMap.clear()
        loadTaskIntoFloatingMode(
            taskId = taskId,
            preferredSelectionHint = null,
            statusMessageRes = R.string.overlay_panel_scheme_switched,
            initialTargetVisible = _overlaySessionState.value.isTargetVisible,
        )
        publishState(RunnerState.IDLE)
        publishStatusMessage(R.string.overlay_panel_scheme_switched)
        publishError(null)
    }

    private suspend fun mutateCurrentTaskStructure(
        @StringRes successMessageRes: Int,
        selectionHintProvider: (List<ActionStepEntity>) -> StepSelectionHint?,
        transform: (TaskWithSteps) -> List<ActionStepEntity>,
    ) {
        val taskId = activeTaskId ?: return
        val current = taskRepository.getTask(taskId) ?: run {
            setActivePanelMessage(R.string.error_task_not_found)
            return
        }
        val updatedSteps = transform(current)
            .sortedBy { it.orderIndex }
            .mapIndexed { index, step -> step.copy(orderIndex = index) }
        persistWorkbenchTask(
            originalTask = current,
            updatedTask = current.task.copy(updatedAt = System.currentTimeMillis()),
            updatedSteps = updatedSteps,
            selectionHint = selectionHintProvider(updatedSteps),
            successMessageRes = successMessageRes,
            autoRestartIfRunning = true,
        )
    }

    private suspend fun persistWorkbenchTask(
        originalTask: TaskWithSteps,
        updatedTask: com.example.clickassist.data.local.entity.TaskEntity,
        updatedSteps: List<ActionStepEntity>,
        selectionHint: StepSelectionHint?,
        @StringRes successMessageRes: Int,
        autoRestartIfRunning: Boolean,
    ) {
        val runnerSnapshot = stopRunnerForWorkbenchChange()
        try {
            val savedTaskId = taskRepository.saveTask(updatedTask, updatedSteps)
            activePanelMessageRes = successMessageRes
            runtimeStepGeometryMap.clear()
            val loadedTask = loadTaskIntoFloatingMode(
                taskId = savedTaskId,
                preferredSelectionHint = selectionHint,
                statusMessageRes = successMessageRes,
                initialTargetVisible = _overlaySessionState.value.isTargetVisible,
            ) ?: return
            when {
                runnerSnapshot.wasRunning && autoRestartIfRunning -> {
                    publishStatusMessage(R.string.overlay_status_starting)
                    launchRunner(loadedTask)
                }

                runnerSnapshot.wasPaused -> {
                    publishState(RunnerState.PAUSED)
                    publishStatusMessage(R.string.overlay_panel_paused_updated)
                }

                else -> {
                    publishState(RunnerState.IDLE)
                    publishStatusMessage(successMessageRes)
                }
            }
            publishError(null)
        } catch (throwable: Throwable) {
            Log.e(TAG, "persistWorkbenchTask failed taskId=${originalTask.task.id}", throwable)
            activePanelMessageRes = R.string.overlay_panel_save_failed
            publishError(
                mapThrowableToRunnerError(throwable),
                true,
                R.string.overlay_panel_save_failed
            )
            syncActivePanel()
        }
    }

    private suspend fun stopRunnerForWorkbenchChange(): RunnerSnapshot {
        val snapshot = RunnerSnapshot(
            wasRunning = _runnerState.value == RunnerState.RUNNING,
            wasPaused = _runnerState.value == RunnerState.PAUSED,
        )
        runnerJob?.cancelAndJoin()
        runnerJob = null
        publishProgress(null)
        publishState(RunnerState.IDLE)
        return snapshot
    }

    private suspend fun setActivePanelMessage(
        @StringRes messageRes: Int?,
    ) {
        activePanelMessageRes = messageRes
        publishStatusMessage(messageRes)
        syncActivePanel()
    }

    private fun buildUpdatedStep(
        existingStep: ActionStepEntity,
        draft: OverlayStepEditorDraft,
    ): ActionStepEntity? {
        val primaryPoint = parseCoordinatePairForPanel(
            xValue = draft.x,
            yValue = draft.y,
            actionType = draft.actionType,
            isEnd = false,
        ) ?: return null
        val swipeEnd = if (draft.actionType == ActionType.SWIPE) {
            parseCoordinatePairForPanel(
                xValue = draft.endX,
                yValue = draft.endY,
                actionType = draft.actionType,
                isEnd = true,
            ) ?: return null
        } else {
            null
        }
        val intervalMs =
            parsePositiveLongForPanel(draft.intervalMs, R.string.error_invalid_interval_ms)
                ?: return null
        val repeatCount =
            parsePositiveIntForPanel(draft.repeatCount, R.string.error_invalid_repeat_count)
                ?: return null
        val durationMs = when (draft.actionType) {
            ActionType.TAP -> draft.durationMs.trim().toLongOrNull()?.coerceAtLeast(1L)
                ?: existingStep.durationMs.coerceAtLeast(1L)

            else -> parsePositiveLongForPanel(draft.durationMs, R.string.error_invalid_duration_ms)
                ?: return null
        }
        val preDelayMs =
            parseNonNegativeLongForPanel(draft.preDelayMs, R.string.validation_numeric_invalid)
                ?: return null
        val postDelayMs =
            parseNonNegativeLongForPanel(draft.postDelayMs, R.string.validation_numeric_invalid)
                ?: return null
        return existingStep.copy(
            actionType = draft.actionType.storageValue,
            enabled = draft.enabled,
            x = primaryPoint?.x,
            y = primaryPoint?.y,
            endX = swipeEnd?.x,
            endY = swipeEnd?.y,
            intervalMs = intervalMs,
            durationMs = durationMs,
            repeatCount = repeatCount,
            preDelayMs = preDelayMs,
            postDelayMs = postDelayMs,
        )
    }

    private fun parseCoordinatePairForPanel(
        xValue: String,
        yValue: String,
        actionType: ActionType,
        isEnd: Boolean,
    ): ScreenPoint? {
        val trimmedX = xValue.trim()
        val trimmedY = yValue.trim()
        if (trimmedX.isEmpty() && trimmedY.isEmpty()) {
            return null
        }
        val x = trimmedX.toIntOrNull()
        val y = trimmedY.toIntOrNull()
        if (x != null && y != null) {
            return ScreenPoint(x, y)
        }
        activePanelMessageRes = when {
            actionType == ActionType.SWIPE && isEnd -> R.string.validation_swipe_end_coordinate_invalid
            actionType == ActionType.SWIPE -> R.string.validation_swipe_start_coordinate_invalid
            else -> R.string.validation_manual_coordinate_invalid
        }
        return null
    }

    private fun parsePositiveLongForPanel(
        rawValue: String,
        @StringRes failureMessageRes: Int,
    ): Long? {
        val value = rawValue.trim().toLongOrNull()
        if (value != null && value > 0L) {
            return value
        }
        activePanelMessageRes = failureMessageRes
        return null
    }

    private fun parsePositiveIntForPanel(
        rawValue: String,
        @StringRes failureMessageRes: Int,
    ): Int? {
        val value = rawValue.trim().toIntOrNull()
        if (value != null && value > 0) {
            return value
        }
        activePanelMessageRes = failureMessageRes
        return null
    }

    private fun parseNonNegativeLongForPanel(
        rawValue: String,
        @StringRes failureMessageRes: Int,
    ): Long? {
        val value = rawValue.trim().toLongOrNull()
        if (value != null && value >= 0L) {
            return value
        }
        activePanelMessageRes = failureMessageRes
        return null
    }

    private fun ActionStepEntity.toEditorDraft(): OverlayStepEditorDraft {
        return OverlayStepEditorDraft(
            stepId = id,
            orderIndex = orderIndex,
            actionType = actionTypeEnum(),
            enabled = enabled,
            x = x?.toString().orEmpty(),
            y = y?.toString().orEmpty(),
            endX = endX?.toString().orEmpty(),
            endY = endY?.toString().orEmpty(),
            intervalMs = intervalMs.toString(),
            durationMs = durationMs.toString(),
            repeatCount = repeatCount.toString(),
            preDelayMs = preDelayMs.toString(),
            postDelayMs = postDelayMs.toString(),
        )
    }

    private fun ActionStepEntity.selectionHint(): StepSelectionHint {
        return StepSelectionHint(orderIndex = orderIndex, actionType = actionTypeEnum())
    }

    private fun currentSelectionHint(
        taskWithSteps: TaskWithSteps,
    ): StepSelectionHint? {
        return taskWithSteps.steps.firstOrNull { it.id == selectedStepId }?.selectionHint()
            ?: resolvePreferredSelectedStep(taskWithSteps)?.selectionHint()
    }

    private fun resolveSelectedStepId(
        taskWithSteps: TaskWithSteps,
        preferredSelectionHint: StepSelectionHint?,
    ): Long? {
        val sorted = taskWithSteps.steps.sortedBy { it.orderIndex }
        preferredSelectionHint?.let { hint ->
            sorted.firstOrNull { it.orderIndex == hint.orderIndex && it.actionTypeEnum() == hint.actionType }
                ?.let { return it.id }
            sorted.firstOrNull { it.orderIndex == hint.orderIndex }?.let { return it.id }
        }
        selectedStepId?.let { currentId ->
            sorted.firstOrNull { it.id == currentId }?.let { return currentId }
        }
        return resolvePreferredSelectedStep(taskWithSteps)?.id
    }

    private fun currentInsertionIndex(
        steps: List<ActionStepEntity>,
    ): Int {
        val ordered = steps.sortedBy { it.orderIndex }
        val selectedIndex = ordered.indexOfFirst { it.id == selectedStepId }
        return if (selectedIndex >= 0) {
            selectedIndex + 1
        } else {
            ordered.size
        }
    }

    private fun defaultDurationFor(
        actionType: ActionType,
    ): Long {
        return when (actionType) {
            ActionType.TAP -> 80L
            ActionType.LONG_PRESS -> 600L
            ActionType.SWIPE -> 300L
            ActionType.WAIT -> 1000L
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
        engineScope.launch {
            syncOverlayTargets(taskWithSteps)
            syncActivePanel(taskWithSteps)
        }
    }

    private fun ensureAccessibilityServiceReady(
        source: String,
    ): MyAccessibilityService? {
        val enabled = MyAccessibilityService.isEnabled(appContext)
        val service = MyAccessibilityService.current()
        Log.i(
            TAG,
            "$source accessibilityEnabled=$enabled accessibilityInstanceReady=${service != null}"
        )
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
        val role = runCatching { OverlayMarkerRole.valueOf(parts[2].uppercase()) }.getOrNull()
            ?: return null
        return MarkerMeta(stepId, role)
    }

    private fun clearActiveSessionLocal() {
        activeTaskId = null
        selectedStepId = null
        activePanelType = null
        activePanelMessageRes = null
        placementMode = OverlayPlacementMode.NONE
        pendingSwipeStartPoint = null
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

    private data class StepSelectionHint(
        val orderIndex: Int,
        val actionType: ActionType,
    )

    private data class RunnerSnapshot(
        val wasRunning: Boolean,
        val wasPaused: Boolean,
    )

    private data class MarkerMeta(
        val stepId: Long,
        val role: OverlayMarkerRole,
    )

    data class OverlaySessionState(
        val isFloatingModeEnabled: Boolean = false,
        val activeTaskId: Long? = null,
        val activeTaskName: String? = null,
        val isTargetVisible: Boolean = false,
        val isMultiPointMode: Boolean = false,
        val stepCount: Int = 0,
        val selectedStepOrder: Int? = null,
        val selectedStepActionType: String? = null,
        val selectedStepIsNode: Boolean = false,
        val canDeleteSelected: Boolean = false,
        val hasWaitSteps: Boolean = false,
        val placementMode: OverlayPlacementMode = OverlayPlacementMode.NONE,
        val activePanelType: OverlayPanelType? = null,
        val statusMessageRes: Int? = null,
        val isToolbarHidden: Boolean = false,
    )

    private companion object {
        const val CONTROL_POLL_INTERVAL_MS = 100L
        const val PREVIEW_STEP_ID = -1L
        const val PREVIEW_SWIPE_START_MARKER_ID = "preview:swipe:start"
        const val TAG = "TaskRunnerEngine"
        const val ACTION_TAG = "TaskRunnerAction"
    }
}
