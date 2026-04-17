package com.example.clickassist.service.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
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
import com.example.clickassist.domain.repository.AppSettings
import com.example.clickassist.ui.theme.ClickAssistTheme
import com.example.clickassist.ui.tutorial.OverlayTutorialHost
import com.example.clickassist.ui.tutorial.TutorialController
import com.example.clickassist.ui.tutorial.TutorialStep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class OverlayTutorialController(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val windowManager = requireNotNull(appContext.getSystemService(WindowManager::class.java))
    private val mainHandler = Handler(Looper.getMainLooper())

    private var currentSettings: AppSettings = AppSettings()
    private var tutorialView: ComposeView? = null
    private var tutorialLayoutParams: WindowManager.LayoutParams? = null
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

    fun isVisible(): Boolean = tutorialView?.parent != null

    fun applySettings(settings: AppSettings) {
        currentSettings = settings
        runOnMain {
            if (tutorialView != null) {
                bindContent()
            }
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
        if (!hasPermission()) {
            return@withContext false
        }
        this@OverlayTutorialController.tutorialController = tutorialController
        this@OverlayTutorialController.steps = steps
        this@OverlayTutorialController.onStepChanged = onStepChanged
        this@OverlayTutorialController.onSkip = onSkip
        this@OverlayTutorialController.onDone = onDone
        this@OverlayTutorialController.onClose = onClose
        _stepIndex.value = initialStepIndex.coerceIn(0, steps.lastIndex.coerceAtLeast(0))

        val view = ensureTutorialView()
        bindContent()
        val layoutParams = tutorialLayoutParams ?: createLayoutParams().also {
            tutorialLayoutParams = it
        }
        runCatching {
            if (view.parent == null) {
                windowManager.addView(view, layoutParams)
            } else {
                windowManager.updateViewLayout(view, layoutParams)
            }
        }.isSuccess
    }

    suspend fun hide() = withContext(Dispatchers.Main.immediate) {
        hideInternal()
    }

    fun release() {
        runOnMain {
            hideInternal()
            tutorialView = null
            tutorialLayoutParams = null
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

    private fun ensureTutorialView(): ComposeView {
        tutorialView?.let { return it }
        val owner = OverlayViewTreeOwner()
        viewTreeOwner = owner
        return ComposeView(appContext).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setViewTreeLifecycleOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }.also { tutorialView = it }
    }

    private fun bindContent() {
        val composeView = tutorialView ?: return
        val tutorialController = tutorialController ?: return
        val steps = steps
        if (steps.isEmpty()) return
        composeView.setContent {
            val stepIndex by this@OverlayTutorialController.stepIndex.collectAsState()
            ClickAssistTheme(themeMode = currentSettings.themeMode) {
                OverlayTutorialHost(
                    tutorialController = tutorialController,
                    steps = steps,
                    stepIndex = stepIndex,
                    onStepIndexChange = { nextIndex ->
                        _stepIndex.value = nextIndex.coerceIn(0, steps.lastIndex)
                    },
                    onStepChanged = { index, step ->
                        onStepChanged?.invoke(index, step)
                    },
                    onSkip = { onSkip?.invoke() },
                    onDone = { onDone?.invoke() },
                    onClose = { onClose?.invoke() },
                )
            }
        }
    }

    private fun createLayoutParams(): WindowManager.LayoutParams {
        val bounds = screenBounds()
        return WindowManager.LayoutParams(
            bounds.width(),
            bounds.height(),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
        }
    }

    private fun hideInternal() {
        tutorialView?.let { view ->
            if (view.parent != null) {
                runCatching {
                    windowManager.removeView(view)
                }
            }
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

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }

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
