package com.TradeRoutine.LZLapp.service.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowInsets as PlatformWindowInsets
import android.view.WindowManager
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.TradeRoutine.LZLapp.domain.repository.AppSettings
import com.TradeRoutine.LZLapp.ui.theme.ClickAssistTheme
import com.TradeRoutine.LZLapp.ui.tutorial.TutorialControlsBlock
import com.TradeRoutine.LZLapp.ui.tutorial.TutorialController
import com.TradeRoutine.LZLapp.ui.tutorial.TutorialHighlightOverlay
import com.TradeRoutine.LZLapp.ui.tutorial.TutorialStep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

class OverlayTutorialController(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val windowManager = requireNotNull(appContext.getSystemService(WindowManager::class.java))
    private val mainHandler = Handler(Looper.getMainLooper())

    private var currentSettings: AppSettings = AppSettings()
    private var highlightView: ComposeView? = null
    private var controlsView: ComposeView? = null
    private var highlightLayoutParams: WindowManager.LayoutParams? = null
    private var controlsLayoutParams: WindowManager.LayoutParams? = null
    private var controlsSize: IntSize = IntSize.Zero
    private var viewTreeOwner: OverlayViewTreeOwner? = null
    private var tutorialController: TutorialController? = null
    private var steps: List<TutorialStep> = emptyList()
    private var onStepChanged: ((Int, TutorialStep) -> Unit)? = null
    private var onSkip: (() -> Unit)? = null
    private var onDone: (() -> Unit)? = null
    private var onClose: (() -> Unit)? = null
    private val _stepIndex = MutableStateFlow(0)
    val stepIndex: StateFlow<Int> = _stepIndex.asStateFlow()

    fun hasPermission(): Boolean = Settings.canDrawOverlays(appContext)

    fun isVisible(): Boolean =
        highlightView?.parent != null || controlsView?.parent != null

    fun applySettings(settings: AppSettings) {
        currentSettings = settings
        runOnMain {
            if (highlightView != null || controlsView != null) {
                bindContent()
                updateControlsPosition()
            }
        }
    }

    fun setStepIndex(index: Int) {
        runOnMain {
            val lastIndex = steps.lastIndex
            if (lastIndex < 0) return@runOnMain
            _stepIndex.value = index.coerceIn(0, lastIndex)
            updateControlsPosition()
        }
    }

    suspend fun show(
        tutorialController: TutorialController,
        steps: List<TutorialStep>,
        initialStepIndex: Int = 0,
        onStepChanged: (Int, TutorialStep) -> Unit,
        onSkip: () -> Unit,
        onDone: () -> Unit,
        onClose: () -> Unit,
    ): Boolean = withContext(Dispatchers.Main.immediate) {
        if (!hasPermission() || steps.isEmpty()) {
            return@withContext false
        }
        this@OverlayTutorialController.tutorialController = tutorialController
        this@OverlayTutorialController.steps = steps
        this@OverlayTutorialController.onStepChanged = onStepChanged
        this@OverlayTutorialController.onSkip = onSkip
        this@OverlayTutorialController.onDone = onDone
        this@OverlayTutorialController.onClose = onClose
        _stepIndex.value = initialStepIndex.coerceIn(0, steps.lastIndex)

        val highlight = ensureHighlightView()
        val controls = ensureControlsView()
        bindContent()

        val highlightParams = createHighlightLayoutParams().also {
            highlightLayoutParams = it
        }
        val controlsParams = controlsLayoutParams ?: createControlsLayoutParams().also {
            controlsLayoutParams = it
        }
        updateControlsPosition(paramsOnly = true)

        val highlightShown = runCatching {
            if (highlight.parent == null) {
                windowManager.addView(highlight, highlightParams)
            } else {
                windowManager.updateViewLayout(highlight, highlightParams)
            }
        }.isSuccess
        val controlsShown = runCatching {
            if (controls.parent == null) {
                windowManager.addView(controls, controlsParams)
            } else {
                windowManager.updateViewLayout(controls, controlsParams)
            }
        }.isSuccess
        highlightShown && controlsShown
    }

    suspend fun hide() = withContext(Dispatchers.Main.immediate) {
        hideInternal()
    }

    fun release() {
        runOnMain {
            hideInternal()
            highlightView = null
            controlsView = null
            highlightLayoutParams = null
            controlsLayoutParams = null
            controlsSize = IntSize.Zero
            tutorialController = null
            steps = emptyList()
            onStepChanged = null
            onSkip = null
            onDone = null
            onClose = null
            viewTreeOwner?.destroy()
            viewTreeOwner = null
        }
    }

    private fun ensureHighlightView(): ComposeView {
        highlightView?.let { return it }
        val owner = ensureViewTreeOwner()
        return ComposeView(appContext).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setViewTreeLifecycleOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }.also { highlightView = it }
    }

    private fun ensureControlsView(): ComposeView {
        controlsView?.let { return it }
        val owner = ensureViewTreeOwner()
        return ComposeView(appContext).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setViewTreeLifecycleOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }.also { controlsView = it }
    }

    private fun ensureViewTreeOwner(): OverlayViewTreeOwner {
        return viewTreeOwner ?: OverlayViewTreeOwner().also { viewTreeOwner = it }
    }

    private fun bindContent() {
        val tutorialController = tutorialController ?: return
        val steps = steps
        if (steps.isEmpty()) return

        highlightView?.setContent {
            val stepIndex by this@OverlayTutorialController.stepIndex.collectAsState()
            val anchors by tutorialController.anchors.collectAsState()
            val currentStep = steps[stepIndex.coerceIn(0, steps.lastIndex)]
            ClickAssistTheme(themeMode = currentSettings.themeMode) {
                TutorialHighlightOverlay(targetRect = anchors[currentStep.key])
            }
        }

        controlsView?.setContent {
            val stepIndex by this@OverlayTutorialController.stepIndex.collectAsState()
            val anchors by tutorialController.anchors.collectAsState()
            val currentStep = steps[stepIndex.coerceIn(0, steps.lastIndex)]
            val targetRect = anchors[currentStep.key]

            LaunchedEffect(stepIndex, currentStep) {
                onStepChanged?.invoke(stepIndex, currentStep)
                updateControlsPosition()
            }
            LaunchedEffect(targetRect) {
                updateControlsPosition()
            }

            ClickAssistTheme(themeMode = currentSettings.themeMode) {
                TutorialControlsBlock(
                    step = currentStep,
                    stepIndex = stepIndex,
                    totalSteps = steps.size,
                    onBack = {
                        if (stepIndex > 0) {
                            setStepIndex(stepIndex - 1)
                        }
                    },
                    onNext = {
                        if (stepIndex < steps.lastIndex) {
                            setStepIndex(stepIndex + 1)
                        }
                    },
                    onSkip = { onSkip?.invoke() },
                    onDone = { onDone?.invoke() },
                    onClose = { onClose?.invoke() },
                    onSizeChanged = { size ->
                        if (size != controlsSize) {
                            controlsSize = size
                            updateControlsPosition()
                        }
                    },
                )
            }
        }
    }

    private fun createHighlightLayoutParams(): WindowManager.LayoutParams {
        val bounds = screenBounds()
        return WindowManager.LayoutParams(
            bounds.width(),
            bounds.height(),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
        }
    }

    private fun createControlsLayoutParams(): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
        }
    }

    private fun updateControlsPosition(paramsOnly: Boolean = false) {
        val params = controlsLayoutParams ?: return
        val position = calculateControlsPosition()
        params.x = position.x
        params.y = position.y
        val view = controlsView ?: return
        if (!paramsOnly && view.parent != null) {
            runCatching {
                windowManager.updateViewLayout(view, params)
            }
        }
    }

    private fun calculateControlsPosition(): Position {
        val safeBounds = safeScreenBounds()
        val margin = dp(16)
        val targetMargin = dp(16)
        val defaultWidth = dp(340)
        val defaultHeight = dp(260)
        val width = controlsSize.width.takeIf { it > 0 } ?: defaultWidth
        val height = controlsSize.height.takeIf { it > 0 } ?: defaultHeight
        val minX = safeBounds.left + margin
        val maxX = safeBounds.right - margin - width
        val minY = safeBounds.top + margin
        val maxY = safeBounds.bottom - margin - height
        val targetRect = currentTargetRect()

        if (targetRect == null) {
            return Position(
                x = clampToBounds(
                    value = safeBounds.centerX() - (width / 2),
                    min = minX,
                    max = maxX,
                ),
                y = clampToBounds(
                    value = safeBounds.top + ((safeBounds.height() * 0.2f).roundToInt()),
                    min = minY,
                    max = maxY,
                ),
            )
        }

        val x = targetRect.centerX().roundToInt() - (width / 2)
        val belowY = targetRect.bottom.roundToInt() + targetMargin
        val aboveY = targetRect.top.roundToInt() - height - targetMargin
        val spaceBelow = maxY - belowY
        val spaceAbove = aboveY - minY
        val y = when {
            belowY <= maxY -> belowY
            aboveY >= minY -> aboveY
            spaceBelow >= spaceAbove -> belowY
            else -> aboveY
        }

        return Position(
            x = clampToBounds(x, minX, maxX),
            y = clampToBounds(y, minY, maxY),
        )
    }

    private fun currentTargetRect(): RectF? {
        val controller = tutorialController ?: return null
        val step = steps.getOrNull(_stepIndex.value) ?: return null
        return controller.getAnchor(step.key)
    }

    private fun hideInternal() {
        controlsView?.let { view ->
            if (view.parent != null) {
                runCatching { windowManager.removeView(view) }
            }
        }
        highlightView?.let { view ->
            if (view.parent != null) {
                runCatching { windowManager.removeView(view) }
            }
        }
    }

    private fun safeScreenBounds(): Rect {
        val bounds = screenBounds()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = windowManager.currentWindowMetrics
            val insets = metrics.windowInsets.getInsetsIgnoringVisibility(
                PlatformWindowInsets.Type.systemBars() or PlatformWindowInsets.Type.displayCutout(),
            )
            Rect(
                bounds.left + insets.left,
                bounds.top + insets.top,
                bounds.right - insets.right,
                bounds.bottom - insets.bottom,
            )
        } else {
            val conservativeInset = dp(24)
            Rect(
                bounds.left,
                bounds.top + conservativeInset,
                bounds.right,
                bounds.bottom - conservativeInset,
            )
        }
    }

    private fun screenBounds(): Rect {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Rect(windowManager.currentWindowMetrics.bounds)
        } else {
            Rect(
                0,
                0,
                appContext.resources.displayMetrics.widthPixels,
                appContext.resources.displayMetrics.heightPixels,
            )
        }
    }

    private fun dp(value: Int): Int =
        (value * appContext.resources.displayMetrics.density).roundToInt()

    private fun clampToBounds(
        value: Int,
        min: Int,
        max: Int,
    ): Int {
        val resolvedMax = max.coerceAtLeast(min)
        return value.coerceIn(min, resolvedMax)
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }

    private data class Position(
        val x: Int,
        val y: Int,
    )

    private class OverlayViewTreeOwner :
        LifecycleOwner,
        SavedStateRegistryOwner,
        ViewModelStoreOwner {
        private val lifecycleRegistry = LifecycleRegistry(this)
        private val savedStateController = SavedStateRegistryController.create(this)
        private val internalViewModelStore = ViewModelStore()

        init {
            savedStateController.performAttach()
            savedStateController.performRestore(Bundle())
            lifecycleRegistry.currentState = Lifecycle.State.CREATED
            lifecycleRegistry.currentState = Lifecycle.State.STARTED
            lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        }

        override val lifecycle: Lifecycle
            get() = lifecycleRegistry

        override val savedStateRegistry: SavedStateRegistry
            get() = savedStateController.savedStateRegistry

        override val viewModelStore: ViewModelStore
            get() = internalViewModelStore

        fun destroy() {
            lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
            internalViewModelStore.clear()
        }
    }
}
