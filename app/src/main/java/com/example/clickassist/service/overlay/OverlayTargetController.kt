package com.example.clickassist.service.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import com.example.clickassist.R
import com.example.clickassist.domain.model.ScreenPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

class OverlayTargetController(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val windowManager = requireNotNull(appContext.getSystemService(WindowManager::class.java))
    private val mainHandler = Handler(Looper.getMainLooper())
    private val markerSizePx = (MARKER_SIZE_DP * appContext.resources.displayMetrics.density).roundToInt()
    private val markerHalfSizePx = markerSizePx / 2

    private var targetView: TargetMarkerView? = null
    private var targetLayoutParams: WindowManager.LayoutParams? = null
    private var currentPoint: ScreenPoint? = null
    private var dragTouchOffsetX = 0f
    private var dragTouchOffsetY = 0f
    private var onPointChangedCallback: ((ScreenPoint) -> Unit)? = null
    private var onDragEndCallback: ((ScreenPoint) -> Unit)? = null
    private var isTouchEnabled: Boolean = true

    fun hasPermission(): Boolean = Settings.canDrawOverlays(appContext)

    fun currentPoint(): ScreenPoint? = currentPoint

    fun isTargetVisible(): Boolean = targetView?.parent != null

    fun resolveInitialPoint(
        preferredX: Int?,
        preferredY: Int?,
    ): ScreenPoint {
        val bounds = getScreenBounds()
        val fallbackPoint = ScreenPoint(
            x = preferredX ?: bounds.width / 2,
            y = preferredY ?: bounds.height / 2,
        )
        return clampPoint(
            point = fallbackPoint,
            bounds = bounds,
        )
    }

    suspend fun showTarget(
        initialPoint: ScreenPoint,
        onPointChanged: (ScreenPoint) -> Unit,
        onDragEnd: (ScreenPoint) -> Unit,
    ): Boolean = withContext(Dispatchers.Main.immediate) {
        if (!hasPermission()) {
            return@withContext false
        }

        onPointChangedCallback = onPointChanged
        onDragEndCallback = onDragEnd

        val clampedPoint = resolveInitialPoint(
            preferredX = initialPoint.x,
            preferredY = initialPoint.y,
        )
        val view = ensureTargetViewInternal()
        val layoutParams = (targetLayoutParams ?: createLayoutParams()).also {
            targetLayoutParams = it
        }
        layoutParams.flags = targetFlags()

        applyPointToLayoutParams(
            layoutParams = layoutParams,
            point = clampedPoint,
        )
        currentPoint = clampedPoint
        Log.i(TAG, "showTarget point=$clampedPoint touchEnabled=$isTouchEnabled")

        runCatching {
            if (view.parent == null) {
                windowManager.addView(view, layoutParams)
            } else {
                windowManager.updateViewLayout(view, layoutParams)
            }
        }.isSuccess
    }

    suspend fun updateTarget(
        point: ScreenPoint,
    ): Boolean = withContext(Dispatchers.Main.immediate) {
        if (!hasPermission()) {
            return@withContext false
        }

        val clampedPoint = resolveInitialPoint(
            preferredX = point.x,
            preferredY = point.y,
        )
        val layoutParams = (targetLayoutParams ?: createLayoutParams()).also {
            targetLayoutParams = it
        }
        layoutParams.flags = targetFlags()
        applyPointToLayoutParams(
            layoutParams = layoutParams,
            point = clampedPoint,
        )
        currentPoint = clampedPoint
        Log.d(TAG, "updateTarget point=$clampedPoint touchEnabled=$isTouchEnabled")

        val view = targetView
        if (view?.parent != null) {
            runCatching {
                windowManager.updateViewLayout(view, layoutParams)
            }.isSuccess
        } else {
            true
        }
    }

    suspend fun hideTarget(
        clearPoint: Boolean = false,
    ) = withContext(Dispatchers.Main.immediate) {
        Log.i(TAG, "hideTarget clearPoint=$clearPoint")
        hideTargetInternal(clearPoint = clearPoint)
    }

    suspend fun setTouchEnabled(
        enabled: Boolean,
    ): Boolean = withContext(Dispatchers.Main.immediate) {
        isTouchEnabled = enabled
        val layoutParams = targetLayoutParams ?: return@withContext true
        layoutParams.flags = targetFlags()
        val view = targetView
        Log.i(TAG, "setTouchEnabled enabled=$enabled attached=${view?.parent != null}")

        if (view?.parent != null) {
            runCatching {
                windowManager.updateViewLayout(view, layoutParams)
            }.isSuccess
        } else {
            true
        }
    }

    fun release() {
        runOnMain {
            hideTargetInternal(clearPoint = true)
            targetView = null
            targetLayoutParams = null
            currentPoint = null
            onPointChangedCallback = null
            onDragEndCallback = null
        }
    }

    private fun createLayoutParams(): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            markerSizePx,
            markerSizePx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            targetFlags(),
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
        }
    }

    private fun ensureTargetViewInternal(): TargetMarkerView {
        targetView?.let { return it }

        return TargetMarkerView(appContext).apply {
            contentDescription = appContext.getString(R.string.overlay_target_description)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            isClickable = true
            setOnTouchListener { _, event ->
                handleTargetTouch(event)
            }
        }.also { view ->
            targetView = view
        }
    }

    private fun handleTargetTouch(event: MotionEvent): Boolean {
        val view = targetView ?: return false
        val layoutParams = targetLayoutParams ?: return false

        return when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragTouchOffsetX = event.x
                dragTouchOffsetY = event.y
                Log.d(TAG, "drag start rawX=${event.rawX} rawY=${event.rawY} currentPoint=$currentPoint")
                true
            }

            MotionEvent.ACTION_MOVE -> {
                val bounds = getScreenBounds()
                val clampedLeft = (event.rawX - dragTouchOffsetX).roundToInt()
                    .coerceIn(0, (bounds.width - markerSizePx).coerceAtLeast(0))
                val clampedTop = (event.rawY - dragTouchOffsetY).roundToInt()
                    .coerceIn(0, (bounds.height - markerSizePx).coerceAtLeast(0))
                val point = ScreenPoint(
                    x = clampedLeft + markerHalfSizePx,
                    y = clampedTop + markerHalfSizePx,
                )

                layoutParams.x = clampedLeft
                layoutParams.y = clampedTop
                currentPoint = point

                runCatching {
                    if (view.parent != null) {
                        windowManager.updateViewLayout(view, layoutParams)
                    }
                }
                onPointChangedCallback?.invoke(point)
                true
            }

            MotionEvent.ACTION_UP -> {
                view.performClick()
                currentPoint?.let { point ->
                    Log.i(TAG, "drag end point=$point")
                    onDragEndCallback?.invoke(point)
                }
                true
            }

            MotionEvent.ACTION_CANCEL -> {
                currentPoint?.let { point ->
                    Log.i(TAG, "drag cancelled point=$point")
                    onDragEndCallback?.invoke(point)
                }
                true
            }

            else -> false
        }
    }

    private fun applyPointToLayoutParams(
        layoutParams: WindowManager.LayoutParams,
        point: ScreenPoint,
    ) {
        val clampedPoint = clampPoint(
            point = point,
            bounds = getScreenBounds(),
        )
        currentPoint = clampedPoint
        layoutParams.x = (clampedPoint.x - markerHalfSizePx).coerceAtLeast(0)
        layoutParams.y = (clampedPoint.y - markerHalfSizePx).coerceAtLeast(0)
    }

    private fun clampPoint(
        point: ScreenPoint,
        bounds: ScreenBounds,
    ): ScreenPoint {
        val minX = markerHalfSizePx.coerceAtLeast(0)
        val minY = markerHalfSizePx.coerceAtLeast(0)
        val maxX = (bounds.width - markerHalfSizePx).coerceAtLeast(minX)
        val maxY = (bounds.height - markerHalfSizePx).coerceAtLeast(minY)

        return ScreenPoint(
            x = point.x.coerceIn(minX, maxX),
            y = point.y.coerceIn(minY, maxY),
        )
    }

    private fun hideTargetInternal(
        clearPoint: Boolean,
    ) {
        val view = targetView
        if (view?.parent != null) {
            runCatching {
                windowManager.removeView(view)
            }
        }
        if (clearPoint) {
            currentPoint = null
        }
    }

    private fun getScreenBounds(): ScreenBounds {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds: Rect = windowManager.currentWindowMetrics.bounds
            return ScreenBounds(
                width = bounds.width().coerceAtLeast(markerSizePx),
                height = bounds.height().coerceAtLeast(markerSizePx),
            )
        }

        val metrics = appContext.resources.displayMetrics
        return ScreenBounds(
            width = metrics.widthPixels.coerceAtLeast(markerSizePx),
            height = metrics.heightPixels.coerceAtLeast(markerSizePx),
        )
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }

    private fun targetFlags(): Int {
        var flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        if (!isTouchEnabled) {
            flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }
        return flags
    }

    private data class ScreenBounds(
        val width: Int,
        val height: Int,
    )

    private companion object {
        const val MARKER_SIZE_DP = 56
        const val TAG = "ClickAssistTarget"
    }
}
