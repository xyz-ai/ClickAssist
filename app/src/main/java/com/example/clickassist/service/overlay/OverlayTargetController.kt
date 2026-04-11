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

    private val markerEntries = linkedMapOf<String, MarkerEntry>()
    private val currentPoints = linkedMapOf<String, ScreenPoint>()

    private var onMarkerChangedCallback: ((String, ScreenPoint) -> Unit)? = null
    private var onMarkerDragEndCallback: ((String, ScreenPoint) -> Unit)? = null
    private var onMarkerSelectedCallback: ((String) -> Unit)? = null
    private var isTouchEnabled: Boolean = true

    fun hasPermission(): Boolean = Settings.canDrawOverlays(appContext)

    fun isTargetVisible(): Boolean = markerEntries.values.any { entry -> entry.view.parent != null }

    fun currentMarkerPoint(markerId: String): ScreenPoint? = currentPoints[markerId]

    fun currentMarkerPoints(): Map<String, ScreenPoint> = currentPoints.toMap()

    fun resolveInitialPoint(
        preferredX: Int?,
        preferredY: Int?,
    ): ScreenPoint {
        val bounds = getScreenBounds()
        return clampPoint(
            point = ScreenPoint(
                x = preferredX ?: bounds.width / 2,
                y = preferredY ?: bounds.height / 2,
            ),
            bounds = bounds,
        )
    }

    suspend fun showMarkers(
        markers: List<OverlayMarkerModel>,
        onMarkerChanged: (String, ScreenPoint) -> Unit,
        onMarkerDragEnd: (String, ScreenPoint) -> Unit,
        onMarkerSelected: (String) -> Unit,
    ): Boolean = withContext(Dispatchers.Main.immediate) {
        if (!hasPermission()) {
            return@withContext false
        }

        onMarkerChangedCallback = onMarkerChanged
        onMarkerDragEndCallback = onMarkerDragEnd
        onMarkerSelectedCallback = onMarkerSelected

        syncMarkersInternal(markers)
    }

    suspend fun updateMarkers(
        markers: List<OverlayMarkerModel>,
    ): Boolean = withContext(Dispatchers.Main.immediate) {
        if (!hasPermission()) {
            return@withContext false
        }

        syncMarkersInternal(markers)
    }

    suspend fun hideTargets(
        clearPoints: Boolean = false,
    ) = withContext(Dispatchers.Main.immediate) {
        Log.i(TAG, "hideTargets clearPoints=$clearPoints")
        hideTargetsInternal(clearPoints)
    }

    suspend fun setTouchEnabled(
        enabled: Boolean,
    ): Boolean = withContext(Dispatchers.Main.immediate) {
        isTouchEnabled = enabled
        Log.i(TAG, "setTouchEnabled enabled=$enabled markerCount=${markerEntries.size}")
        markerEntries.values.all { entry ->
            entry.layoutParams.flags = targetFlags()
            val attached = entry.view.parent != null
            if (!attached) {
                true
            } else {
                runCatching {
                    windowManager.updateViewLayout(entry.view, entry.layoutParams)
                }.isSuccess
            }
        }
    }

    fun release() {
        runOnMain {
            hideTargetsInternal(clearPoints = true)
            markerEntries.clear()
            currentPoints.clear()
            onMarkerChangedCallback = null
            onMarkerDragEndCallback = null
            onMarkerSelectedCallback = null
            isTouchEnabled = true
        }
    }

    private fun syncMarkersInternal(
        markers: List<OverlayMarkerModel>,
    ): Boolean {
        val nextIds = markers.map { it.markerId }.toSet()
        val obsoleteIds = markerEntries.keys.filterNot { it in nextIds }
        obsoleteIds.forEach { markerId ->
            markerEntries.remove(markerId)?.let { entry ->
                if (entry.view.parent != null) {
                    runCatching { windowManager.removeView(entry.view) }
                }
            }
            currentPoints.remove(markerId)
        }

        val bounds = getScreenBounds()
        markers.forEach { model ->
            val entry = markerEntries[model.markerId] ?: createMarkerEntry(model.markerId).also {
                markerEntries[model.markerId] = it
            }
            entry.view.bind(
                label = model.label,
                actionType = model.actionType,
                role = model.role,
                isSelected = model.isSelected,
            )
            entry.view.contentDescription = appContext.getString(
                R.string.overlay_target_description_with_label,
                model.label,
            )
            entry.layoutParams.flags = targetFlags()
            val point = clampPoint(model.point, bounds)
            applyPointToLayoutParams(entry.layoutParams, point)
            currentPoints[model.markerId] = point
            Log.d(
                TAG,
                "syncMarkers markerId=${model.markerId} stepId=${model.stepId} label=${model.label} point=$point selected=${model.isSelected}",
            )
            runCatching {
                if (entry.view.parent == null) {
                    windowManager.addView(entry.view, entry.layoutParams)
                } else {
                    windowManager.updateViewLayout(entry.view, entry.layoutParams)
                }
            }.onFailure { throwable ->
                Log.e(TAG, "syncMarkers failed markerId=${model.markerId}", throwable)
            }
        }

        return true
    }

    private fun createMarkerEntry(
        markerId: String,
    ): MarkerEntry {
        val view = TargetMarkerView(appContext).apply {
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            isClickable = true
            setOnTouchListener { _, event -> handleMarkerTouch(markerId, event) }
        }

        return MarkerEntry(
            view = view,
            layoutParams = createLayoutParams(),
        )
    }

    private fun handleMarkerTouch(
        markerId: String,
        event: MotionEvent,
    ): Boolean {
        val entry = markerEntries[markerId] ?: return false
        return when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                entry.dragTouchOffsetX = event.x
                entry.dragTouchOffsetY = event.y
                onMarkerSelectedCallback?.invoke(markerId)
                Log.d(TAG, "drag start markerId=$markerId rawX=${event.rawX} rawY=${event.rawY}")
                true
            }

            MotionEvent.ACTION_MOVE -> {
                val bounds = getScreenBounds()
                val clampedLeft = (event.rawX - entry.dragTouchOffsetX).roundToInt()
                    .coerceIn(0, (bounds.width - markerSizePx).coerceAtLeast(0))
                val clampedTop = (event.rawY - entry.dragTouchOffsetY).roundToInt()
                    .coerceIn(0, (bounds.height - markerSizePx).coerceAtLeast(0))
                val point = ScreenPoint(
                    x = clampedLeft + markerHalfSizePx,
                    y = clampedTop + markerHalfSizePx,
                )

                entry.layoutParams.x = clampedLeft
                entry.layoutParams.y = clampedTop
                currentPoints[markerId] = point

                runCatching {
                    if (entry.view.parent != null) {
                        windowManager.updateViewLayout(entry.view, entry.layoutParams)
                    }
                }
                onMarkerChangedCallback?.invoke(markerId, point)
                true
            }

            MotionEvent.ACTION_UP -> {
                entry.view.performClick()
                currentPoints[markerId]?.let { point ->
                    Log.i(TAG, "drag end markerId=$markerId point=$point")
                    onMarkerSelectedCallback?.invoke(markerId)
                    onMarkerDragEndCallback?.invoke(markerId, point)
                }
                true
            }

            MotionEvent.ACTION_CANCEL -> {
                currentPoints[markerId]?.let { point ->
                    Log.i(TAG, "drag cancel markerId=$markerId point=$point")
                    onMarkerDragEndCallback?.invoke(markerId, point)
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
        layoutParams.x = (point.x - markerHalfSizePx).coerceAtLeast(0)
        layoutParams.y = (point.y - markerHalfSizePx).coerceAtLeast(0)
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

    private fun hideTargetsInternal(
        clearPoints: Boolean,
    ) {
        markerEntries.values.forEach { entry ->
            if (entry.view.parent != null) {
                runCatching { windowManager.removeView(entry.view) }
            }
        }
        markerEntries.clear()
        if (clearPoints) {
            currentPoints.clear()
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

    private fun targetFlags(): Int {
        var flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        if (!isTouchEnabled) {
            flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }
        return flags
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }

    private data class MarkerEntry(
        val view: TargetMarkerView,
        val layoutParams: WindowManager.LayoutParams,
        var dragTouchOffsetX: Float = 0f,
        var dragTouchOffsetY: Float = 0f,
    )

    private data class ScreenBounds(
        val width: Int,
        val height: Int,
    )

    private companion object {
        const val MARKER_SIZE_DP = 56
        const val TAG = "ClickAssistTarget"
    }
}
