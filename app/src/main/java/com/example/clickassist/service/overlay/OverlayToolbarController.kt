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
import com.example.clickassist.domain.repository.SettingsRepository
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
    @StringRes
    val statusMessageRes: Int? = null,
)

data class OverlayToolbarCallbacks(
    val onStartRequested: () -> Unit = {},
    val onDebugTapRequested: () -> Unit = {},
    val onPauseRequested: () -> Unit = {},
    val onStopRequested: () -> Unit = {},
    val onTargetToggleRequested: () -> Unit = {},
    val onCloseRequested: () -> Unit = {},
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
    private var expandedContainer: LinearLayout? = null
    private var collapsedContainer: TextView? = null
    private var collapseButton: TextView? = null
    private var startButton: TextView? = null
    private var debugTapButton: TextView? = null
    private var pauseButton: TextView? = null
    private var stopButton: TextView? = null
    private var toggleTargetButton: TextView? = null
    private var closeButton: TextView? = null
    private var statusTextView: TextView? = null

    private var callbacks: OverlayToolbarCallbacks = OverlayToolbarCallbacks()
    private var currentUiState: OverlayToolbarUiState = OverlayToolbarUiState(
        runnerState = RunnerState.IDLE,
        isTargetVisible = false,
    )
    private var isExpanded: Boolean = true

    fun hasPermission(): Boolean = Settings.canDrawOverlays(appContext)

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
            isExpanded = true
            currentUiState = uiState

            val view = ensureToolbarViewInternal()
            applyUiState(uiState)
            updateExpandedState(refreshLayout = false)

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
            }
        }
    }

    suspend fun hide() = withContext(Dispatchers.Main.immediate) {
        hideInternal()
    }

    fun showMessage(
        @StringRes messageRes: Int,
    ) {
        runOnMain {
            currentUiState = currentUiState.copy(statusMessageRes = messageRes)
            renderStatusMessage(currentUiState)
            Log.i(
                TAG,
                "Status message updated: ${appContext.getString(messageRes)}",
            )
        }
    }

    fun release() {
        controllerScope.cancel()
        runOnMain {
            hideInternal()
            toolbarView = null
            toolbarLayoutParams = null
            expandedContainer = null
            collapsedContainer = null
            collapseButton = null
            startButton = null
            pauseButton = null
            stopButton = null
            toggleTargetButton = null
            closeButton = null
        }
    }

    private fun ensureToolbarViewInternal(): DragInterceptLayout {
        toolbarView?.let { return it }

        val root = DragInterceptLayout(appContext).apply {
            clipChildren = false
            clipToPadding = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            onDragBy = { deltaX, deltaY ->
                moveToolbarBy(deltaX = deltaX, deltaY = deltaY)
            }
            onDragEnd = {
                persistCurrentPosition()
            }
        }

        val expanded = createExpandedContent()
        val collapsed = createCollapsedContent()
        expandedContainer = expanded
        collapsedContainer = collapsed

        root.addView(
            expanded,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        root.addView(
            collapsed,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )

        toolbarView = root
        return root
    }

    private fun createExpandedContent(): LinearLayout {
        return LinearLayout(appContext).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#D90F172A"))
                cornerRadius = dpFloat(22)
            }

            collapseButton = createActionButton(
                labelRes = R.string.overlay_action_collapse,
                backgroundColor = Color.parseColor("#334155"),
            ) {
                Log.i(TAG, "Collapse requested from toolbar")
                isExpanded = false
                updateExpandedState(refreshLayout = true)
            }.also { addButton(it) }

            startButton = createActionButton(
                labelRes = R.string.overlay_action_start,
                backgroundColor = Color.parseColor("#047857"),
            ) {
                Log.i(
                    TAG,
                    "Start requested runnerState=${currentUiState.runnerState} targetVisible=${currentUiState.isTargetVisible}",
                )
                callbacks.onStartRequested()
            }.also { addButton(it) }

            // TODO: debug only
            debugTapButton = createActionButton(
                labelRes = R.string.overlay_action_debug_tap,
                backgroundColor = Color.parseColor("#0F766E"),
            ) {
                Log.i(
                    TAG,
                    "Debug tap requested runnerState=${currentUiState.runnerState} targetVisible=${currentUiState.isTargetVisible}",
                )
                callbacks.onDebugTapRequested()
            }.also { addButton(it) }

            pauseButton = createActionButton(
                labelRes = R.string.overlay_action_pause,
                backgroundColor = Color.parseColor("#B45309"),
            ) {
                Log.i(
                    TAG,
                    "Pause requested runnerState=${currentUiState.runnerState} targetVisible=${currentUiState.isTargetVisible}",
                )
                callbacks.onPauseRequested()
            }.also { addButton(it) }

            stopButton = createActionButton(
                labelRes = R.string.overlay_action_stop,
                backgroundColor = Color.parseColor("#B91C1C"),
            ) {
                Log.i(
                    TAG,
                    "Stop requested runnerState=${currentUiState.runnerState} targetVisible=${currentUiState.isTargetVisible}",
                )
                callbacks.onStopRequested()
            }.also { addButton(it) }

            toggleTargetButton = createActionButton(
                labelRes = R.string.overlay_action_hide_target,
                backgroundColor = Color.parseColor("#1D4ED8"),
            ) {
                Log.i(
                    TAG,
                    "Toggle target requested runnerState=${currentUiState.runnerState} targetVisible=${currentUiState.isTargetVisible}",
                )
                callbacks.onTargetToggleRequested()
            }.also { addButton(it) }

            closeButton = createActionButton(
                labelRes = R.string.overlay_action_close_floating,
                backgroundColor = Color.parseColor("#4B5563"),
            ) {
                Log.i(
                    TAG,
                    "Close floating requested runnerState=${currentUiState.runnerState} targetVisible=${currentUiState.isTargetVisible}",
                )
                callbacks.onCloseRequested()
            }.also { addButton(it) }

            statusTextView = TextView(appContext).apply {
                setTextColor(Color.parseColor("#E2E8F0"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setPadding(dp(4), dp(10), dp(4), dp(2))
                text = appContext.getString(R.string.overlay_status_ready)
            }.also { view ->
                addView(
                    view,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ),
                )
            }
        }
    }

    private fun createCollapsedContent(): TextView {
        return TextView(appContext).apply {
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#D90F172A"))
                cornerRadius = dpFloat(20)
            }
            gravity = Gravity.CENTER
            minWidth = dp(52)
            minHeight = dp(40)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            text = appContext.getString(R.string.overlay_action_expand)
            contentDescription = text
            isClickable = true
            isFocusable = true
            setOnClickListener {
                isExpanded = true
                updateExpandedState(refreshLayout = true)
            }
        }
    }

    private fun LinearLayout.addButton(button: TextView) {
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
            minWidth = dp(88)
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

    private fun applyUiState(
        uiState: OverlayToolbarUiState,
    ) {
        collapseButton?.updateLabel(R.string.overlay_action_collapse)
        collapsedContainer?.updateLabel(R.string.overlay_action_expand)

        val startLabelRes = if (uiState.runnerState == RunnerState.PAUSED) {
            R.string.overlay_action_resume
        } else {
            R.string.overlay_action_start
        }
        startButton?.apply {
            updateLabel(startLabelRes)
            updateEnabledState(
                enabled = uiState.runnerState != RunnerState.RUNNING &&
                    uiState.runnerState != RunnerState.STOPPING,
            )
        }

        debugTapButton?.updateEnabledState(
            enabled = uiState.runnerState != RunnerState.RUNNING &&
                uiState.runnerState != RunnerState.STOPPING,
        )
        pauseButton?.updateEnabledState(enabled = uiState.runnerState == RunnerState.RUNNING)
        stopButton?.updateEnabledState(
            enabled = uiState.runnerState != RunnerState.IDLE &&
                uiState.runnerState != RunnerState.STOPPING,
        )

        toggleTargetButton?.apply {
            val labelRes = if (uiState.isTargetVisible) {
                R.string.overlay_action_hide_target
            } else {
                R.string.overlay_action_show_target
            }
            updateLabel(labelRes)
            updateEnabledState(enabled = true)
        }

        closeButton?.updateEnabledState(enabled = true)
        renderStatusMessage(uiState)
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

    private fun updateExpandedState(
        refreshLayout: Boolean,
    ) {
        expandedContainer?.visibility = if (isExpanded) View.VISIBLE else View.GONE
        collapsedContainer?.visibility = if (isExpanded) View.GONE else View.VISIBLE

        if (!refreshLayout) {
            return
        }

        val view = toolbarView ?: return
        val layoutParams = toolbarLayoutParams ?: return
        applyPosition(
            layoutParams = layoutParams,
            view = view,
            desiredX = layoutParams.x,
            desiredY = layoutParams.y,
        )
        if (view.parent != null) {
            runCatching {
                windowManager.updateViewLayout(view, layoutParams)
            }
        }
        persistCurrentPosition()
    }

    private fun renderStatusMessage(
        uiState: OverlayToolbarUiState,
    ) {
        val statusRes = uiState.statusMessageRes ?: defaultStatusMessageRes(uiState.runnerState)
        statusTextView?.text = appContext.getString(statusRes)
    }

    @StringRes
    private fun defaultStatusMessageRes(
        runnerState: RunnerState,
    ): Int {
        return when (runnerState) {
            RunnerState.IDLE -> R.string.overlay_status_ready
            RunnerState.RUNNING -> R.string.runner_state_running
            RunnerState.PAUSED -> R.string.runner_state_paused
            RunnerState.STOPPING -> R.string.runner_state_stopping
            RunnerState.COMPLETED -> R.string.runner_state_completed
            RunnerState.ERROR -> R.string.runner_state_error
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
            width = view.measuredWidth.coerceAtLeast(dp(52)),
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
                -> {
                    dragging = false
                }
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

    private companion object {
        const val TAG = "ClickAssistToolbar"
    }
}
