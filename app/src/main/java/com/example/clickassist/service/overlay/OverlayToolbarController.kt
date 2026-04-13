package com.example.clickassist.service.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.StringRes
import com.example.clickassist.R
import com.example.clickassist.domain.model.ActionType
import com.example.clickassist.domain.repository.SettingsRepository
import com.example.clickassist.service.runner.OverlayPlacementMode
import com.example.clickassist.service.runner.RunnerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

data class OverlayToolbarUiState(
    val runnerState: RunnerState,
    val isTargetVisible: Boolean,
    val activeTaskId: Long? = null,
    val activeTaskName: String? = null,
    val selectedStepOrder: Int? = null,
    val selectedStepActionType: String? = null,
    val canDeleteSelected: Boolean = false,
    val placementMode: OverlayPlacementMode = OverlayPlacementMode.NONE,
    @StringRes
    val statusMessageRes: Int? = null,
)

data class OverlayToolbarCallbacks(
    val onStartRequested: () -> Unit = {},
    val onPauseRequested: () -> Unit = {},
    val onStopRequested: () -> Unit = {},
    val onAddNodeRequested: () -> Unit = {},
    val onDeleteSelectedRequested: () -> Unit = {},
    val onSettingsRequested: () -> Unit = {},
    val onTargetToggleRequested: () -> Unit = {},
)

class OverlayToolbarController(
    context: Context,
    private val settingsRepository: SettingsRepository,
) {
    private val appContext = context.applicationContext
    private val windowManager = requireNotNull(appContext.getSystemService(WindowManager::class.java))
    private val mainHandler = Handler(Looper.getMainLooper())
    private val controllerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var toolbarView: DragInterceptLayout? = null
    private var toolbarLayoutParams: WindowManager.LayoutParams? = null
    private var contentContainer: LinearLayout? = null
    private var startButton: TextView? = null
    private var pauseButton: TextView? = null
    private var stopButton: TextView? = null
    private var addNodeButton: TextView? = null
    private var deleteButton: TextView? = null
    private var settingsButton: TextView? = null
    private var toggleTargetButton: TextView? = null
    private var statusTextView: TextView? = null
    private var boundsCache: Rect? = null

    private var callbacks: OverlayToolbarCallbacks = OverlayToolbarCallbacks()
    private var callbacksBound: Boolean = false
    private var currentUiState = OverlayToolbarUiState(
        runnerState = RunnerState.IDLE,
        isTargetVisible = false,
    )
    var onBoundsChanged: ((Rect?) -> Unit)? = null

    fun hasPermission(): Boolean = Settings.canDrawOverlays(appContext)

    fun currentBounds(): Rect? = boundsCache?.let(::Rect)

    suspend fun show(
        uiState: OverlayToolbarUiState,
        callbacks: OverlayToolbarCallbacks,
    ): Boolean {
        if (!hasPermission()) {
            return false
        }

        val settings = withContext(Dispatchers.IO) {
            settingsRepository.settingsFlow.first()
        }

        return withContext(Dispatchers.Main.immediate) {
            this@OverlayToolbarController.callbacks = callbacks
            callbacksBound = true
            currentUiState = uiState

            val view = ensureToolbarView()
            applyUiState(uiState)
            val layoutParams = (toolbarLayoutParams ?: createLayoutParams()).also {
                toolbarLayoutParams = it
            }
            applyPosition(
                layoutParams = layoutParams,
                view = view,
                desiredX = settings.overlayToolbarX ?: defaultToolbarX(),
                desiredY = settings.overlayToolbarY ?: defaultToolbarY(),
            )

            runCatching {
                if (view.parent == null) {
                    windowManager.addView(view, layoutParams)
                } else {
                    windowManager.updateViewLayout(view, layoutParams)
                }
            }.onSuccess {
                updateBoundsCache()
                Log.i(
                    TAG,
                    "show success taskId=${uiState.activeTaskId} taskName=${uiState.activeTaskName} runnerState=${uiState.runnerState}",
                )
            }.onFailure { throwable ->
                Log.e(TAG, "show failed", throwable)
            }.isSuccess
        }
    }

    suspend fun updateState(
        uiState: OverlayToolbarUiState,
    ) = withContext(Dispatchers.Main.immediate) {
        currentUiState = uiState
        applyUiState(uiState)
        val view = toolbarView ?: return@withContext
        val layoutParams = toolbarLayoutParams ?: return@withContext
        applyPosition(
            layoutParams = layoutParams,
            view = view,
            desiredX = layoutParams.x,
            desiredY = layoutParams.y,
        )
        if (view.parent != null) {
            runCatching {
                windowManager.updateViewLayout(view, layoutParams)
            }.onFailure { throwable ->
                Log.e(TAG, "updateState failed", throwable)
            }
        }
        updateBoundsCache()
    }

    suspend fun hide() = withContext(Dispatchers.Main.immediate) {
        hideInternal()
    }

    fun showMessage(
        @StringRes messageRes: Int,
    ) {
        runOnMain {
            currentUiState = currentUiState.copy(statusMessageRes = messageRes)
            renderStatus(currentUiState)
        }
    }

    fun release() {
        controllerScope.cancel()
        runOnMain {
            hideInternal()
            toolbarView = null
            toolbarLayoutParams = null
            contentContainer = null
            startButton = null
            pauseButton = null
            stopButton = null
            addNodeButton = null
            deleteButton = null
            settingsButton = null
            toggleTargetButton = null
            statusTextView = null
            callbacksBound = false
            boundsCache = null
            onBoundsChanged?.invoke(null)
        }
    }

    private fun ensureToolbarView(): DragInterceptLayout {
        toolbarView?.let { return it }

        val root = DragInterceptLayout(appContext).apply {
            clipChildren = false
            clipToPadding = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            onDragBy = { deltaX, deltaY ->
                moveToolbarBy(deltaX, deltaY)
            }
            onDragEnd = {
                persistCurrentPosition()
            }
        }

        val container = LinearLayout(appContext).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#D90F172A"))
                cornerRadius = dpFloat(22)
            }
        }

        startButton = createActionButton(
            labelRes = R.string.overlay_action_start,
            backgroundColor = Color.parseColor("#047857"),
        ) {
            dispatchAction("start", isActionEnabled(startButton)) {
                callbacks.onStartRequested()
            }
        }.also { container.addToolbarButton(it) }

        pauseButton = createActionButton(
            labelRes = R.string.overlay_action_pause,
            backgroundColor = Color.parseColor("#B45309"),
        ) {
            dispatchAction("pause", isActionEnabled(pauseButton)) {
                callbacks.onPauseRequested()
            }
        }.also { container.addToolbarButton(it) }

        stopButton = createActionButton(
            labelRes = R.string.overlay_action_stop,
            backgroundColor = Color.parseColor("#B91C1C"),
        ) {
            dispatchAction("stop", isActionEnabled(stopButton)) {
                callbacks.onStopRequested()
            }
        }.also { container.addToolbarButton(it) }

        addNodeButton = createActionButton(
            labelRes = R.string.overlay_action_add_node,
            backgroundColor = Color.parseColor("#0F766E"),
        ) {
            dispatchAction("addNode", isActionEnabled(addNodeButton)) {
                callbacks.onAddNodeRequested()
            }
        }.also { container.addToolbarButton(it) }

        deleteButton = createActionButton(
            labelRes = R.string.overlay_action_delete_node,
            backgroundColor = Color.parseColor("#7C2D12"),
        ) {
            dispatchAction("deleteSelectedNode", isActionEnabled(deleteButton)) {
                callbacks.onDeleteSelectedRequested()
            }
        }.also { container.addToolbarButton(it) }

        settingsButton = createActionButton(
            labelRes = R.string.overlay_action_settings,
            backgroundColor = Color.parseColor("#1D4ED8"),
        ) {
            dispatchAction("settings", isActionEnabled(settingsButton)) {
                callbacks.onSettingsRequested()
            }
        }.also { container.addToolbarButton(it) }

        toggleTargetButton = createActionButton(
            labelRes = R.string.overlay_action_hide_target,
            backgroundColor = Color.parseColor("#4F46E5"),
        ) {
            dispatchAction(
                actionName = if (currentUiState.isTargetVisible) "hideTarget" else "showTarget",
                enabled = isActionEnabled(toggleTargetButton),
            ) {
                callbacks.onTargetToggleRequested()
            }
        }.also { container.addToolbarButton(it) }

        statusTextView = TextView(appContext).apply {
            setTextColor(Color.parseColor("#E2E8F0"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setPadding(dp(6), dp(10), dp(6), dp(2))
        }.also { view ->
            container.addView(
                view,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        }

        contentContainer = container
        root.addView(
            container,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        toolbarView = root
        return root
    }

    private fun applyUiState(
        uiState: OverlayToolbarUiState,
    ) {
        val startLabel = if (uiState.runnerState == RunnerState.PAUSED) {
            R.string.overlay_action_resume
        } else {
            R.string.overlay_action_start
        }
        startButton?.apply {
            updateLabel(startLabel)
            updateEnabledState(
                enabled = uiState.runnerState != RunnerState.RUNNING &&
                    uiState.runnerState != RunnerState.STOPPING,
            )
        }
        pauseButton?.updateEnabledState(enabled = uiState.runnerState == RunnerState.RUNNING)
        stopButton?.updateEnabledState(
            enabled = uiState.runnerState != RunnerState.IDLE &&
                uiState.runnerState != RunnerState.STOPPING,
        )
        addNodeButton?.updateEnabledState(
            enabled = uiState.runnerState != RunnerState.RUNNING &&
                uiState.runnerState != RunnerState.STOPPING,
        )
        deleteButton?.updateEnabledState(
            enabled = uiState.canDeleteSelected &&
                uiState.runnerState != RunnerState.STOPPING,
        )
        settingsButton?.updateEnabledState(enabled = uiState.runnerState != RunnerState.STOPPING)
        toggleTargetButton?.apply {
            updateLabel(
                if (uiState.isTargetVisible) {
                    R.string.overlay_action_hide_target
                } else {
                    R.string.overlay_action_show_target
                },
            )
            updateEnabledState(enabled = true)
        }
        renderStatus(uiState)
    }

    private fun renderStatus(
        uiState: OverlayToolbarUiState,
    ) {
        val message = when {
            uiState.statusMessageRes != null -> appContext.getString(uiState.statusMessageRes)
            uiState.placementMode != OverlayPlacementMode.NONE -> appContext.getString(uiState.placementMode.messageRes)
            uiState.selectedStepOrder != null && !uiState.selectedStepActionType.isNullOrBlank() -> appContext.getString(
                R.string.overlay_selected_step_label,
                uiState.selectedStepOrder,
                appContext.getString(actionTypeLabelRes(uiState.selectedStepActionType)),
            )
            !uiState.activeTaskName.isNullOrBlank() -> appContext.getString(
                R.string.overlay_current_task_label,
                uiState.activeTaskName,
            )
            else -> appContext.getString(R.string.overlay_selected_step_empty)
        }
        statusTextView?.text = message
    }

    private fun dispatchAction(
        actionName: String,
        enabled: Boolean,
        callback: () -> Unit,
    ) {
        Log.i(
            TAG,
            "button clicked action=$actionName enabled=$enabled callbacksBound=$callbacksBound taskId=${currentUiState.activeTaskId} taskName=${currentUiState.activeTaskName} runnerState=${currentUiState.runnerState}",
        )
        if (!enabled) {
            Log.w(ACTION_TAG, "button ignored action=$actionName reason=disabled")
            return
        }
        if (!callbacksBound) {
            Log.e(ACTION_TAG, "button ignored action=$actionName reason=callbacks_not_bound")
            showMessage(R.string.overlay_status_toolbar_action_unavailable)
            return
        }

        runCatching(callback)
            .onSuccess {
                Log.i(ACTION_TAG, "button forwarded action=$actionName")
            }
            .onFailure { throwable ->
                Log.e(ACTION_TAG, "button dispatch failed action=$actionName", throwable)
                showMessage(R.string.overlay_status_toolbar_action_unavailable)
            }
    }

    private fun isActionEnabled(
        button: TextView?,
    ): Boolean = button?.isEnabled == true

    private fun createActionButton(
        @StringRes labelRes: Int,
        backgroundColor: Int,
        onClick: () -> Unit,
    ): TextView {
        return TextView(appContext).apply {
            background = GradientDrawable().apply {
                setColor(backgroundColor)
                cornerRadius = dpFloat(18)
            }
            gravity = Gravity.CENTER
            minWidth = dp(92)
            minHeight = dp(40)
            setPadding(dp(14), dp(10), dp(14), dp(10))
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            text = appContext.getString(labelRes)
            contentDescription = text
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }
    }

    private fun LinearLayout.addToolbarButton(button: TextView) {
        addView(
            button,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                if (childCount > 0) {
                    topMargin = dp(6)
                }
            },
        )
    }

    private fun TextView.updateLabel(
        @StringRes labelRes: Int,
    ) {
        text = appContext.getString(labelRes)
        contentDescription = text
    }

    private fun TextView.updateEnabledState(
        enabled: Boolean,
    ) {
        isEnabled = enabled
        alpha = if (enabled) 1f else 0.45f
    }

    @StringRes
    private fun actionTypeLabelRes(
        actionTypeValue: String,
    ): Int {
        return when (ActionType.fromStorage(actionTypeValue)) {
            ActionType.TAP -> R.string.action_type_tap
            ActionType.LONG_PRESS -> R.string.action_type_long_press
            ActionType.SWIPE -> R.string.action_type_swipe
            ActionType.WAIT -> R.string.action_type_wait
        }
    }

    private fun moveToolbarBy(
        deltaX: Float,
        deltaY: Float,
    ) {
        val view = toolbarView ?: return
        val layoutParams = toolbarLayoutParams ?: return
        applyPosition(
            layoutParams = layoutParams,
            view = view,
            desiredX = layoutParams.x + deltaX.roundToInt(),
            desiredY = layoutParams.y + deltaY.roundToInt(),
        )
        if (view.parent != null) {
            runCatching {
                windowManager.updateViewLayout(view, layoutParams)
            }
        }
        updateBoundsCache()
    }

    private fun persistCurrentPosition() {
        val layoutParams = toolbarLayoutParams ?: return
        controllerScope.launch {
            settingsRepository.setOverlayToolbarPosition(
                x = layoutParams.x,
                y = layoutParams.y,
            )
        }
    }

    private fun applyPosition(
        layoutParams: WindowManager.LayoutParams,
        view: View,
        desiredX: Int,
        desiredY: Int,
    ) {
        val bounds = getScreenBounds()
        val viewSize = measureViewSize(view)
        val maxX = (bounds.width - viewSize.width).coerceAtLeast(0)
        val maxY = (bounds.height - viewSize.height).coerceAtLeast(0)
        layoutParams.x = desiredX.coerceIn(0, maxX)
        layoutParams.y = desiredY.coerceIn(0, maxY)
    }

    private fun measureViewSize(
        view: View,
    ): ViewSize {
        view.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        return ViewSize(
            width = view.measuredWidth.coerceAtLeast(dp(92)),
            height = view.measuredHeight.coerceAtLeast(dp(40)),
        )
    }

    private fun createLayoutParams(): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = defaultToolbarX()
            y = defaultToolbarY()
        }
    }

    private fun hideInternal() {
        val view = toolbarView ?: return
        if (view.parent != null) {
            runCatching {
                windowManager.removeView(view)
            }
        }
        boundsCache = null
        onBoundsChanged?.invoke(null)
    }

    private fun updateBoundsCache() {
        val view = toolbarView
        val layoutParams = toolbarLayoutParams
        if (view == null || layoutParams == null) {
            boundsCache = null
            onBoundsChanged?.invoke(null)
            return
        }
        val size = measureViewSize(view)
        boundsCache = Rect(
            layoutParams.x,
            layoutParams.y,
            layoutParams.x + size.width,
            layoutParams.y + size.height,
        )
        onBoundsChanged?.invoke(boundsCache?.let(::Rect))
    }

    private fun defaultToolbarX(): Int = dp(12)

    private fun defaultToolbarY(): Int = dp(96)

    private fun getScreenBounds(): ScreenBounds {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds: Rect = windowManager.currentWindowMetrics.bounds
            return ScreenBounds(
                width = bounds.width().coerceAtLeast(1),
                height = bounds.height().coerceAtLeast(1),
            )
        }

        val metrics = appContext.resources.displayMetrics
        return ScreenBounds(
            width = metrics.widthPixels.coerceAtLeast(1),
            height = metrics.heightPixels.coerceAtLeast(1),
        )
    }

    private fun dp(value: Int): Int {
        return (value * appContext.resources.displayMetrics.density).roundToInt()
    }

    private fun dpFloat(value: Int): Float {
        return value * appContext.resources.displayMetrics.density
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }

    private data class ScreenBounds(
        val width: Int,
        val height: Int,
    )

    private data class ViewSize(
        val width: Int,
        val height: Int,
    )

    private companion object {
        const val TAG = "OverlayToolbar"
        const val ACTION_TAG = "OverlayAction"
    }

    private class DragInterceptLayout(
        context: Context,
    ) : FrameLayout(context) {
        var onDragBy: ((Float, Float) -> Unit)? = null
        var onDragEnd: (() -> Unit)? = null

        private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        private var downRawX = 0f
        private var downRawY = 0f
        private var lastRawX = 0f
        private var lastRawY = 0f
        private var dragging = false

        override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = ev.rawX
                    downRawY = ev.rawY
                    lastRawX = ev.rawX
                    lastRawY = ev.rawY
                    dragging = false
                }

                MotionEvent.ACTION_MOVE -> {
                    if (!dragging && hasDraggedEnough(ev)) {
                        dragging = true
                        lastRawX = ev.rawX
                        lastRawY = ev.rawY
                        return true
                    }
                }

                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL,
                -> dragging = false
            }
            return super.onInterceptTouchEvent(ev)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    lastRawX = event.rawX
                    lastRawY = event.rawY
                    dragging = false
                    return true
                }

                MotionEvent.ACTION_MOVE -> {
                    if (!dragging && hasDraggedEnough(event)) {
                        dragging = true
                        lastRawX = event.rawX
                        lastRawY = event.rawY
                        return true
                    }
                    if (dragging) {
                        onDragBy?.invoke(
                            event.rawX - lastRawX,
                            event.rawY - lastRawY,
                        )
                        lastRawX = event.rawX
                        lastRawY = event.rawY
                        return true
                    }
                }

                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL,
                -> {
                    if (dragging) {
                        dragging = false
                        onDragEnd?.invoke()
                        return true
                    }
                    dragging = false
                }
            }
            return super.onTouchEvent(event)
        }

        private fun hasDraggedEnough(event: MotionEvent): Boolean {
            return abs(event.rawX - downRawX) >= touchSlop || abs(event.rawY - downRawY) >= touchSlop
        }
    }
}
