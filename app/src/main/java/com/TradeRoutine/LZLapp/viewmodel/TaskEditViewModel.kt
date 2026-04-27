package com.TradeRoutine.LZLapp.viewmodel

import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.TradeRoutine.LZLapp.R
import com.TradeRoutine.LZLapp.app.AppContainer
import com.TradeRoutine.LZLapp.data.local.entity.ActionStepEntity
import com.TradeRoutine.LZLapp.data.local.entity.TaskEntity
import com.TradeRoutine.LZLapp.domain.model.ActionType
import com.TradeRoutine.LZLapp.domain.model.ScreenPoint
import com.TradeRoutine.LZLapp.domain.repository.AppSettings
import com.TradeRoutine.LZLapp.domain.repository.SettingsRepository
import com.TradeRoutine.LZLapp.domain.repository.TaskRepository
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

data class EditableStepDraft(
    val draftKey: Long,
    val stepId: Long = 0L,
    val actionType: ActionType = ActionType.TAP,
    val enabled: Boolean = true,
    val x: String = "",
    val y: String = "",
    val endX: String = "",
    val endY: String = "",
    val intervalMs: String = "300",
    val durationMs: String = "80",
    val repeatCount: String = "1",
    val preDelayMs: String = "0",
    val postDelayMs: String = "0",
) {
    val point: ScreenPoint?
        get() = x.toIntOrNull()?.let { px -> y.toIntOrNull()?.let { py -> ScreenPoint(px, py) } }

    val endPoint: ScreenPoint?
        get() = endX.toIntOrNull()?.let { px -> endY.toIntOrNull()?.let { py -> ScreenPoint(px, py) } }
}

data class CoordinatePickerTarget(
    val draftKey: Long,
    val kind: CoordinatePickerKind,
)

enum class CoordinatePickerKind {
    PRIMARY,
    SWIPE_START,
    SWIPE_END,
}

enum class SaveStatus {
    IDLE,
    SAVING,
    SUCCESS,
    ERROR,
}

data class TaskEditUiState(
    val taskId: Long = 0L,
    val createdAt: Long = 0L,
    val name: String = "",
    val enabled: Boolean = true,
    val totalRounds: String = "1",
    val infiniteRounds: Boolean = false,
    val steps: List<EditableStepDraft> = emptyList(),
    val editingStepKey: Long? = null,
    val coordinatePickerTarget: CoordinatePickerTarget? = null,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val saveStatus: SaveStatus = SaveStatus.IDLE,
    @StringRes
    val saveStatusMessageRes: Int? = null,
    val saveErrorDetail: String? = null,
    @StringRes
    val validationMessageRes: Int? = null,
) {
    val editingStep: EditableStepDraft?
        get() = steps.firstOrNull { it.draftKey == editingStepKey }
}

class TaskEditViewModel(
    private val taskId: Long,
    private val taskRepository: TaskRepository,
    private val settingsRepository: SettingsRepository,
    private val defaultTaskName: String,
    initialSettings: AppSettings = AppSettings(),
) : ViewModel() {
    private var nextDraftKey = 2L
    private var latestSettings = initialSettings
    private var hasAppliedNewTaskDefaults = false
    private val internalState = MutableStateFlow(createInitialState(initialSettings))
    val uiState: StateFlow<TaskEditUiState> = internalState.asStateFlow()

    private val _savedTaskIds = MutableSharedFlow<Long>(extraBufferCapacity = 1)
    val savedTaskIds = _savedTaskIds.asSharedFlow()

    init {
        Log.i(
            INIT_TAG,
            "init taskId=$taskId initialSettingsSource=factory_snapshot defaultStepIntervalMs=${initialSettings.defaultStepIntervalMs}",
        )
        viewModelScope.launch {
            settingsRepository.settingsFlow.collect { settings ->
                latestSettings = settings
                Log.i(
                    FACTORY_TAG,
                    "settingsFlow update taskId=$taskId defaultStepIntervalMs=${settings.defaultStepIntervalMs}",
                )
                if (taskId == 0L && !hasAppliedNewTaskDefaults) {
                    hasAppliedNewTaskDefaults = true
                    val current = internalState.value
                    if (current.steps.size == 1 && current.steps.first().stepId == 0L && current.name.isBlank()) {
                        internalState.value = current.copy(
                            totalRounds = settings.defaultTotalRounds.toString(),
                            steps = current.steps.map { draft ->
                                defaultDraft(
                                    draftKey = draft.draftKey,
                                    actionType = draft.actionType,
                                    settings = settings,
                                    settingsSource = SETTINGS_SOURCE_LATEST,
                                )
                            },
                            editingStepKey = current.editingStepKey ?: current.steps.first().draftKey,
                        )
                    }
                }
            }
        }
        if (taskId != 0L) {
            loadTask()
        }
    }

    fun updateName(value: String) = updateState { clearedFeedback().copy(name = value) }

    fun updateEnabled(value: Boolean) = updateState { clearedFeedback().copy(enabled = value) }

    fun updateTotalRounds(value: String) = updateState { clearedFeedback().copy(totalRounds = value) }

    fun updateInfiniteRounds(value: Boolean) = updateState { clearedFeedback().copy(infiniteRounds = value) }

    fun addStep(actionType: ActionType) {
        val key = nextDraftKey++
        updateState {
            clearedFeedback().copy(
                steps = steps + defaultDraft(
                    draftKey = key,
                    actionType = actionType,
                    settings = latestSettings,
                    settingsSource = SETTINGS_SOURCE_LATEST,
                ),
                editingStepKey = key,
            )
        }
    }

    fun selectStep(draftKey: Long) = updateState { clearedFeedback().copy(editingStepKey = draftKey) }

    fun deleteStep(draftKey: Long) {
        updateState {
            val updatedSteps = steps.filterNot { it.draftKey == draftKey }.ifEmpty {
                listOf(
                    defaultDraft(
                        draftKey = nextDraftKey++,
                        settings = latestSettings,
                        settingsSource = SETTINGS_SOURCE_LATEST,
                    ),
                )
            }
            clearedFeedback().copy(
                steps = updatedSteps,
                editingStepKey = updatedSteps.firstOrNull()?.draftKey,
            )
        }
    }

    fun moveStepUp(draftKey: Long) = moveStep(draftKey, -1)

    fun moveStepDown(draftKey: Long) = moveStep(draftKey, 1)

    fun updateStepActionType(draftKey: Long, actionType: ActionType) = updateDraft(draftKey) {
        val defaultDuration = defaultDurationFor(actionType).toString()
        copy(actionType = actionType, durationMs = defaultDuration)
    }

    fun updateStepEnabled(draftKey: Long, enabled: Boolean) = updateDraft(draftKey) { copy(enabled = enabled) }

    fun updateStepIntervalMs(draftKey: Long, value: String) = updateDraft(draftKey) { copy(intervalMs = value) }

    fun updateStepDurationMs(draftKey: Long, value: String) = updateDraft(draftKey) { copy(durationMs = value) }

    fun updateStepRepeatCount(draftKey: Long, value: String) = updateDraft(draftKey) { copy(repeatCount = value) }

    fun updateStepPreDelayMs(draftKey: Long, value: String) = updateDraft(draftKey) { copy(preDelayMs = value) }

    fun updateStepPostDelayMs(draftKey: Long, value: String) = updateDraft(draftKey) { copy(postDelayMs = value) }

    fun openCoordinatePicker(draftKey: Long, kind: CoordinatePickerKind) = updateState {
        clearedFeedback().copy(
            coordinatePickerTarget = CoordinatePickerTarget(draftKey = draftKey, kind = kind),
        )
    }

    fun dismissCoordinatePicker() = updateState { copy(coordinatePickerTarget = null) }

    fun applyCoordinateSelection(point: ScreenPoint) {
        val target = internalState.value.coordinatePickerTarget ?: return
        updateDraft(target.draftKey) {
            when (target.kind) {
                CoordinatePickerKind.PRIMARY,
                CoordinatePickerKind.SWIPE_START,
                -> copy(x = point.x.toString(), y = point.y.toString())

                CoordinatePickerKind.SWIPE_END -> copy(endX = point.x.toString(), endY = point.y.toString())
            }
        }
        updateState { copy(coordinatePickerTarget = null) }
    }

    fun saveTask() {
        val snapshot = internalState.value
        Log.i(
            TAG,
            "saveTask requested mode=${if (snapshot.taskId == 0L) "create" else "update"} taskId=${snapshot.taskId} stepCount=${snapshot.steps.size}",
        )
        if (snapshot.isSaving) {
            Log.i(TAG, "saveTask ignored because a save is already in progress")
            return
        }

        val totalRounds = parsePositiveInt(
            rawValue = snapshot.totalRounds,
            validationMessageRes = R.string.validation_numeric_invalid,
            fieldName = "totalRounds",
        ) ?: return
        val now = System.currentTimeMillis()
        val steps = buildActionSteps(
            drafts = snapshot.steps,
            currentTaskId = snapshot.taskId,
        ) ?: return

        updateState {
            clearedFeedback().copy(
                isSaving = true,
                saveStatus = SaveStatus.SAVING,
                saveStatusMessageRes = R.string.task_edit_save_status_saving,
            )
        }
        viewModelScope.launch {
            try {
                val task = TaskEntity(
                    id = snapshot.taskId,
                    name = snapshot.name.trim().ifEmpty { defaultTaskName },
                    enabled = snapshot.enabled,
                    totalRounds = totalRounds,
                    infiniteRounds = snapshot.infiniteRounds,
                    createdAt = if (snapshot.taskId == 0L) now else snapshot.createdAt,
                    updatedAt = now,
                )
                Log.i(
                    TAG,
                    "saveTask invoking repository mode=${if (snapshot.taskId == 0L) "create" else "update"} taskId=${task.id} stepCount=${steps.size}",
                )
                val savedTaskId = taskRepository.saveTask(task, steps)
                Log.i(TAG, "saveTask repository success savedTaskId=$savedTaskId stepCount=${steps.size}")
                runCatching {
                    settingsRepository.setLastEditedTaskId(savedTaskId)
                }.onFailure { throwable ->
                    Log.w(TAG, "saveTask saved task but failed to update lastEditedTaskId savedTaskId=$savedTaskId", throwable)
                }
                updateState {
                    copy(
                        taskId = savedTaskId,
                        createdAt = if (createdAt == 0L) now else createdAt,
                        isSaving = false,
                        saveStatus = SaveStatus.SUCCESS,
                        saveStatusMessageRes = R.string.task_edit_save_status_success,
                        saveErrorDetail = null,
                        validationMessageRes = null,
                    )
                }
                _savedTaskIds.tryEmit(savedTaskId)
            } catch (throwable: Throwable) {
                Log.e(TAG, "saveTask failed taskId=${snapshot.taskId} stepCount=${steps.size}", throwable)
                val errorDetail = throwable.message?.takeIf { it.isNotBlank() }
                updateState {
                    copy(
                        isSaving = false,
                        saveStatus = SaveStatus.ERROR,
                        saveStatusMessageRes = R.string.task_edit_save_status_failed,
                        saveErrorDetail = errorDetail,
                        validationMessageRes = R.string.validation_save_task_failed,
                    )
                }
            }
        }
    }

    private fun buildActionSteps(
        drafts: List<EditableStepDraft>,
        currentTaskId: Long,
    ): List<ActionStepEntity>? {
        if (drafts.isEmpty()) {
            Log.w(TAG, "buildActionSteps failed: no steps")
            updateState {
                validationFailed(R.string.validation_steps_required)
            }
            return null
        }

        return drafts.mapIndexed { index, draft ->
            val primaryPoint = parseCoordinatePair(draft.x, draft.y, draft.actionType)
            if (primaryPoint == null && hasCoordinateInput(draft.x, draft.y)) {
                return null
            }
            val swipeEnd = if (draft.actionType == ActionType.SWIPE) {
                parseCoordinatePair(draft.endX, draft.endY, draft.actionType, isEnd = true).also { endPoint ->
                    if (endPoint == null && hasCoordinateInput(draft.endX, draft.endY)) {
                        return null
                    }
                }
            } else {
                null
            }
            val intervalMs = parsePositiveLong(
                rawValue = draft.intervalMs,
                validationMessageRes = R.string.validation_numeric_invalid,
                fieldName = "intervalMs",
                draftKey = draft.draftKey,
            ) ?: return null
            val durationMs = parsePositiveLong(
                rawValue = draft.durationMs,
                validationMessageRes = R.string.validation_numeric_invalid,
                fieldName = "durationMs",
                draftKey = draft.draftKey,
            ) ?: return null
            val repeatCount = parsePositiveInt(
                rawValue = draft.repeatCount,
                validationMessageRes = R.string.validation_numeric_invalid,
                fieldName = "repeatCount",
                draftKey = draft.draftKey,
            ) ?: return null
            val preDelayMs = parseNonNegativeLong(
                rawValue = draft.preDelayMs,
                validationMessageRes = R.string.validation_numeric_invalid,
                fieldName = "preDelayMs",
                draftKey = draft.draftKey,
            ) ?: return null
            val postDelayMs = parseNonNegativeLong(
                rawValue = draft.postDelayMs,
                validationMessageRes = R.string.validation_numeric_invalid,
                fieldName = "postDelayMs",
                draftKey = draft.draftKey,
            ) ?: return null

            ActionStepEntity(
                id = draft.stepId,
                taskId = currentTaskId,
                orderIndex = index,
                actionType = draft.actionType.storageValue,
                x = primaryPoint?.x,
                y = primaryPoint?.y,
                endX = swipeEnd?.x,
                endY = swipeEnd?.y,
                intervalMs = intervalMs,
                durationMs = durationMs,
                repeatCount = repeatCount,
                preDelayMs = preDelayMs,
                postDelayMs = postDelayMs,
                enabled = draft.enabled,
            )
        }
    }

    private fun parseCoordinatePair(
        xValue: String,
        yValue: String,
        actionType: ActionType,
        isEnd: Boolean = false,
    ): ScreenPoint? {
        val trimmedX = xValue.trim()
        val trimmedY = yValue.trim()
        if (trimmedX.isEmpty() && trimmedY.isEmpty()) {
            return null
        }
        val x = trimmedX.toIntOrNull()
        val y = trimmedY.toIntOrNull()
        if (x == null || y == null) {
            val messageRes = when {
                actionType == ActionType.SWIPE && isEnd -> R.string.validation_swipe_end_coordinate_invalid
                actionType == ActionType.SWIPE -> R.string.validation_swipe_start_coordinate_invalid
                else -> R.string.validation_manual_coordinate_invalid
            }
            updateState { validationFailed(messageRes) }
            Log.w(
                TAG,
                "parseCoordinatePair failed actionType=$actionType isEnd=$isEnd xValue=$trimmedX yValue=$trimmedY",
            )
            return null
        }
        return ScreenPoint(x, y)
    }

    private fun loadTask() {
        viewModelScope.launch {
            try {
                Log.i(TAG, "loadTask start taskId=$taskId")
                val taskWithSteps = taskRepository.getTask(taskId)
                if (taskWithSteps == null) {
                    Log.w(TAG, "loadTask failed taskId=$taskId result=null")
                    updateState {
                        copy(
                            isLoading = false,
                            saveStatus = SaveStatus.ERROR,
                            saveStatusMessageRes = R.string.task_edit_load_failed,
                            saveErrorDetail = null,
                            validationMessageRes = R.string.validation_task_not_found,
                        )
                    }
                    return@launch
                }
                Log.i(
                    TAG,
                    "loadTask success taskId=${taskWithSteps.task.id} stepCount=${taskWithSteps.steps.size}",
                )

                val drafts = taskWithSteps.steps
                    .sortedBy { it.orderIndex }
                    .map { step ->
                        val key = nextDraftKey++
                        EditableStepDraft(
                            draftKey = key,
                            stepId = step.id,
                            actionType = ActionType.fromStorage(step.actionType),
                            enabled = step.enabled,
                            x = step.x?.toString().orEmpty(),
                            y = step.y?.toString().orEmpty(),
                            endX = step.endX?.toString().orEmpty(),
                            endY = step.endY?.toString().orEmpty(),
                            intervalMs = step.intervalMs.toString(),
                            durationMs = step.durationMs.toString(),
                            repeatCount = step.repeatCount.toString(),
                            preDelayMs = step.preDelayMs.toString(),
                            postDelayMs = step.postDelayMs.toString(),
                        )
                    }
                    .ifEmpty {
                        listOf(
                            defaultDraft(
                                draftKey = nextDraftKey++,
                                settings = latestSettings,
                                settingsSource = SETTINGS_SOURCE_LATEST,
                            ),
                        )
                    }

                internalState.value = TaskEditUiState(
                    taskId = taskWithSteps.task.id,
                    createdAt = taskWithSteps.task.createdAt,
                    name = taskWithSteps.task.name,
                    enabled = taskWithSteps.task.enabled,
                    totalRounds = taskWithSteps.task.totalRounds.toString(),
                    infiniteRounds = taskWithSteps.task.infiniteRounds,
                    steps = drafts,
                    editingStepKey = drafts.firstOrNull()?.draftKey,
                    isLoading = false,
                )
            } catch (throwable: Throwable) {
                Log.e(TAG, "loadTask exception taskId=$taskId", throwable)
                updateState {
                    copy(
                        isLoading = false,
                        saveStatus = SaveStatus.ERROR,
                        saveStatusMessageRes = R.string.task_edit_load_failed,
                        saveErrorDetail = throwable.message?.takeIf { it.isNotBlank() },
                        validationMessageRes = R.string.validation_task_not_found,
                    )
                }
            }
        }
    }

    private fun moveStep(
        draftKey: Long,
        direction: Int,
    ) {
        updateState {
            val currentIndex = steps.indexOfFirst { it.draftKey == draftKey }
            if (currentIndex < 0) return@updateState this
            val targetIndex = (currentIndex + direction).coerceIn(0, steps.lastIndex)
            if (targetIndex == currentIndex) return@updateState this
            val mutable = steps.toMutableList()
            val step = mutable.removeAt(currentIndex)
            mutable.add(targetIndex, step)
            clearedFeedback().copy(steps = mutable)
        }
    }

    private fun updateDraft(
        draftKey: Long,
        transform: EditableStepDraft.() -> EditableStepDraft,
    ) {
        updateState {
            clearedFeedback().copy(
                steps = steps.map { draft ->
                    if (draft.draftKey == draftKey) draft.transform() else draft
                },
            )
        }
    }

    private fun updateState(
        transform: TaskEditUiState.() -> TaskEditUiState,
    ) {
        internalState.value = internalState.value.transform()
    }

    private fun parsePositiveInt(
        rawValue: String,
        @StringRes validationMessageRes: Int,
        fieldName: String,
        draftKey: Long? = null,
    ): Int? {
        val value = rawValue.trim().toIntOrNull()
        if (value != null && value > 0) {
            return value
        }
        logNumericValidationFailure(fieldName, rawValue, draftKey)
        updateState { validationFailed(validationMessageRes) }
        return null
    }

    private fun parsePositiveLong(
        rawValue: String,
        @StringRes validationMessageRes: Int,
        fieldName: String,
        draftKey: Long? = null,
    ): Long? {
        val value = rawValue.trim().toLongOrNull()
        if (value != null && value > 0L) {
            return value
        }
        logNumericValidationFailure(fieldName, rawValue, draftKey)
        updateState { validationFailed(validationMessageRes) }
        return null
    }

    private fun parseNonNegativeLong(
        rawValue: String,
        @StringRes validationMessageRes: Int,
        fieldName: String,
        draftKey: Long? = null,
    ): Long? {
        val value = rawValue.trim().toLongOrNull()
        if (value != null && value >= 0L) {
            return value
        }
        logNumericValidationFailure(fieldName, rawValue, draftKey)
        updateState { validationFailed(validationMessageRes) }
        return null
    }

    private fun logNumericValidationFailure(
        fieldName: String,
        rawValue: String,
        draftKey: Long?,
    ) {
        Log.w(
            TAG,
            "numeric validation failed field=$fieldName rawValue=$rawValue draftKey=$draftKey",
        )
    }

    private fun hasCoordinateInput(
        xValue: String,
        yValue: String,
    ): Boolean {
        return xValue.trim().isNotEmpty() || yValue.trim().isNotEmpty()
    }

    private fun defaultDraft(
        draftKey: Long,
        actionType: ActionType = ActionType.TAP,
        settings: AppSettings? = latestSettings,
        settingsSource: String = SETTINGS_SOURCE_LATEST,
    ): EditableStepDraft {
        val resolvedSource = when {
            settings != null -> settingsSource
            else -> SETTINGS_SOURCE_FALLBACK
        }
        val safeSettings = settings ?: AppSettings()
        Log.i(
            DRAFT_TAG,
            "defaultDraft draftKey=$draftKey actionType=$actionType settingsSource=$resolvedSource defaultStepIntervalMs=${safeSettings.defaultStepIntervalMs}",
        )
        val draft = EditableStepDraft(
            draftKey = draftKey,
            actionType = actionType,
            intervalMs = safeSettings.defaultStepIntervalMs.toString(),
            durationMs = defaultDurationFor(actionType, safeSettings).toString(),
            repeatCount = safeSettings.defaultNewStepRepeatCount.toString(),
            preDelayMs = if (actionType == ActionType.TAP) "100" else "0",
            postDelayMs = if (actionType == ActionType.TAP) "200" else "0",
        )
        Log.i(
            DRAFT_TAG,
            "defaultDraft created draftKey=$draftKey actionType=$actionType repeatCount=${draft.repeatCount} intervalMs=${draft.intervalMs} durationMs=${draft.durationMs}",
        )
        return draft
    }

    private fun createInitialState(
        settings: AppSettings?,
    ): TaskEditUiState {
        val safeSettings = settings ?: AppSettings()
        val settingsSource = if (settings != null) SETTINGS_SOURCE_FACTORY else SETTINGS_SOURCE_FALLBACK
        val initialDraft = defaultDraft(
            draftKey = 1L,
            settings = safeSettings,
            settingsSource = settingsSource,
        )
        Log.i(
            INIT_TAG,
            "createInitialState taskId=$taskId isLoading=${taskId != 0L} settingsSource=$settingsSource",
        )
        return TaskEditUiState(
            isLoading = taskId != 0L,
            totalRounds = safeSettings.defaultTotalRounds.toString(),
            steps = listOf(initialDraft),
            editingStepKey = initialDraft.draftKey,
        )
    }

    private fun defaultDurationFor(
        actionType: ActionType,
        settings: AppSettings? = latestSettings,
    ): Long {
        val safeSettings = settings ?: AppSettings()
        return when (actionType) {
            ActionType.TAP -> safeSettings.defaultTapDurationMs
            ActionType.LONG_PRESS -> safeSettings.defaultLongPressDurationMs
            ActionType.SWIPE -> safeSettings.defaultSwipeDurationMs
            ActionType.WAIT -> 1000L
        }
    }

    companion object {
        private const val TAG = "TaskEditSave"
        private const val INIT_TAG = "TaskEditInit"
        private const val FACTORY_TAG = "TaskEditFactory"
        private const val DRAFT_TAG = "TaskEditDraft"
        private const val SETTINGS_SOURCE_FACTORY = "factory_snapshot"
        private const val SETTINGS_SOURCE_LATEST = "latest_settings"
        private const val SETTINGS_SOURCE_FALLBACK = "fallback_default"

        fun factory(
            appContainer: AppContainer,
            taskId: Long,
        ): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val repositorySettings = runBlocking {
                        runCatching {
                            appContainer.settingsRepository.settingsFlow.first()
                        }.getOrNull()
                    }
                    val settingsSource = if (repositorySettings != null) {
                        SETTINGS_SOURCE_FACTORY
                    } else {
                        SETTINGS_SOURCE_FALLBACK
                    }
                    val safeSettings = repositorySettings ?: AppSettings()
                    Log.i(
                        FACTORY_TAG,
                        "create taskId=$taskId settingsSource=$settingsSource repositoryNull=${repositorySettings == null} defaultStepIntervalMs=${safeSettings.defaultStepIntervalMs}",
                    )
                    return TaskEditViewModel(
                        taskId = taskId,
                        taskRepository = appContainer.taskRepository,
                        settingsRepository = appContainer.settingsRepository,
                        defaultTaskName = appContainer.appContext.getString(R.string.default_task_name),
                        initialSettings = safeSettings,
                    ) as T
                }
            }
        }
    }
}

private fun TaskEditUiState.clearedFeedback(): TaskEditUiState {
    return copy(
        saveStatus = SaveStatus.IDLE,
        saveStatusMessageRes = null,
        saveErrorDetail = null,
        validationMessageRes = null,
    )
}

private fun TaskEditUiState.validationFailed(
    @StringRes messageRes: Int,
): TaskEditUiState {
    return copy(
        saveStatus = SaveStatus.ERROR,
        saveStatusMessageRes = R.string.task_edit_save_status_failed,
        saveErrorDetail = null,
        validationMessageRes = messageRes,
        isSaving = false,
    )
}
