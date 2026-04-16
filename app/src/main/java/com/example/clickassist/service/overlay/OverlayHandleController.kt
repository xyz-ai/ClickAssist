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
import android.widget.TextView
import com.example.clickassist.R
import com.example.clickassist.domain.repository.AppSettings
import com.example.clickassist.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

class OverlayHandleController(
    context: Context,
    private val settingsRepository: SettingsRepository,
) {
    private val appContext = context.applicationContext
    private val windowManager = requireNotNull(appContext.getSystemService(WindowManager::class.java))
    private val mainHandler = Handler(Looper.getMainLooper())
    private val controllerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var handleView: DragInterceptLayout? = null
    private var handleLayoutParams: WindowManager.LayoutParams? = null
    private var handleButton: TextView? = null
    private var boundsCache: Rect? = null
    private var onExpandRequested: (() -> Unit)? = null
    private var currentAppearance: OverlayAppearance =
        OverlayAppearance.fromSettings(appContext, AppSettings())

    var onBoundsChanged: ((Rect?) -> Unit)? = null

    fun hasPermission(): Boolean = Settings.canDrawOverlays(appContext)

    fun currentBounds(): Rect? = boundsCache?.let(::Rect)

    fun applySettings(settings: AppSettings) {
        currentAppearance = OverlayAppearance.fromSettings(appContext, settings)
        runOnMain {
            handleButton?.background = GradientDrawable().apply {
                setColor(currentAppearance.toolbarBackgroundColor)
                cornerRadius = dpFloat(18)
            }
            handleButton?.setTextColor(currentAppearance.textPrimaryColor)
            handleLayoutParams?.let { updateArrowDirection(it.x) }
        }
    }

    suspend fun show(
        preferredX: Int? = null,
        preferredY: Int? = null,
        onExpandRequested: () -> Unit,
    ): Boolean {
        if (!hasPermission()) {
            return false
        }

        val settings = withContext(Dispatchers.IO) {
            settingsRepository.settingsFlow.first()
        }

        return withContext(Dispatchers.Main.immediate) {
            this@OverlayHandleController.onExpandRequested = onExpandRequested
            val view = ensureHandleView()
            val layoutParams = (handleLayoutParams ?: createLayoutParams()).also {
                handleLayoutParams = it
            }
            applyPosition(
                layoutParams = layoutParams,
                view = view,
                desiredX = preferredX ?: settings.overlayToolbarX ?: defaultHandleX(),
                desiredY = preferredY ?: settings.overlayToolbarY ?: defaultHandleY(),
            )
            updateArrowDirection(layoutParams.x)

            runCatching {
                if (view.parent == null) {
                    windowManager.addView(view, layoutParams)
                } else {
                    windowManager.updateViewLayout(view, layoutParams)
                }
            }.onSuccess {
                updateBoundsCache()
                Log.i(TAG, "handle show success x=${layoutParams.x} y=${layoutParams.y}")
            }.onFailure { throwable ->
                Log.e(TAG, "handle show failed", throwable)
            }.isSuccess
        }
    }

    suspend fun hide() = withContext(Dispatchers.Main.immediate) {
        hideInternal()
    }

    fun release() {
        controllerScope.cancel()
        runOnMain {
            hideInternal()
            handleView = null
            handleLayoutParams = null
            handleButton = null
            boundsCache = null
            onExpandRequested = null
            onBoundsChanged?.invoke(null)
        }
    }

    private fun ensureHandleView(): DragInterceptLayout {
        handleView?.let { return it }

        val root = DragInterceptLayout(appContext).apply {
            clipChildren = false
            clipToPadding = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            onDragBy = { deltaX, deltaY ->
                moveHandleBy(deltaX, deltaY)
            }
            onDragEnd = {
                persistCurrentPosition()
            }
        }

        handleButton = TextView(appContext).apply {
            gravity = Gravity.CENTER
            minWidth = dp(34)
            minHeight = dp(64)
            setPadding(dp(10), dp(12), dp(10), dp(12))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            text = ">"
            contentDescription = appContext.getString(R.string.overlay_action_show_toolbar)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                Log.i(TAG, "handle clicked expandRequested=true")
                onExpandRequested?.invoke()
            }
        }
        handleButton?.background = GradientDrawable().apply {
            setColor(currentAppearance.toolbarBackgroundColor)
            cornerRadius = dpFloat(18)
        }
        handleButton?.setTextColor(currentAppearance.textPrimaryColor)

        root.addView(
            handleButton,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )

        handleView = root
        return root
    }

    private fun moveHandleBy(
        deltaX: Float,
        deltaY: Float,
    ) {
        val view = handleView ?: return
        val layoutParams = handleLayoutParams ?: return
        applyPosition(
            layoutParams = layoutParams,
            view = view,
            desiredX = layoutParams.x + deltaX.roundToInt(),
            desiredY = layoutParams.y + deltaY.roundToInt(),
        )
        updateArrowDirection(layoutParams.x)
        if (view.parent != null) {
            runCatching {
                windowManager.updateViewLayout(view, layoutParams)
            }.onFailure { throwable ->
                Log.e(TAG, "handle move failed", throwable)
            }
        }
        updateBoundsCache()
    }

    private fun persistCurrentPosition() {
        val layoutParams = handleLayoutParams ?: return
        controllerScope.launch {
            settingsRepository.setOverlayToolbarPosition(
                x = layoutParams.x,
                y = layoutParams.y,
            )
        }
        Log.i(TAG, "handle position persisted x=${layoutParams.x} y=${layoutParams.y}")
    }

    private fun updateArrowDirection(
        x: Int,
    ) {
        val centerThreshold = getScreenBounds().width / 2
        handleButton?.text = if (x <= centerThreshold) ">" else "<"
        handleButton?.contentDescription = appContext.getString(R.string.overlay_action_show_toolbar)
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
            x = defaultHandleX()
            y = defaultHandleY()
        }
    }

    private fun applyPosition(
        layoutParams: WindowManager.LayoutParams,
        view: View,
        desiredX: Int,
        desiredY: Int,
    ) {
        val bounds = getScreenBounds()
        val size = measureViewSize(view)
        val maxX = (bounds.width - size.width).coerceAtLeast(0)
        val maxY = (bounds.height - size.height).coerceAtLeast(0)
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
            width = view.measuredWidth.coerceAtLeast(dp(34)),
            height = view.measuredHeight.coerceAtLeast(dp(64)),
        )
    }

    private fun updateBoundsCache() {
        val view = handleView
        val layoutParams = handleLayoutParams
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

    private fun hideInternal() {
        val view = handleView ?: return
        if (view.parent != null) {
            runCatching {
                windowManager.removeView(view)
            }
        }
        boundsCache = null
        onBoundsChanged?.invoke(null)
    }

    private fun defaultHandleX(): Int = dp(8)

    private fun defaultHandleY(): Int = dp(112)

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
