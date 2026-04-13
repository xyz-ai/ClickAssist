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
import android.view.ViewConfiguration
import android.view.WindowManager
import com.example.clickassist.R
import com.example.clickassist.domain.model.ScreenPoint
import com.example.clickassist.service.runner.OverlayPlacementMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

class OverlayTargetController(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val windowManager = requireNotNull(appContext.getSystemService(WindowManager::class.java))
    private val mainHandler = Handler(Looper.getMainLooper())
    private val markerSizePx = (TargetMarkerView.DEFAULT_SIZE_DP * appContext.resources.displayMetrics.density).roundToInt()
    private val markerHalfSizePx = markerSizePx / 2
    private val touchSlop = ViewConfiguration.get(appContext).scaledTouchSlop

    private val markerEntries = linkedMapOf<String, MarkerEntry>()
    private val currentPoints = linkedMapOf<String, ScreenPoint>()

    private var layerView: OverlayTargetLayerView? = null
    private var layerLayoutParams: WindowManager.LayoutParams? = null
    private var currentMarkers: List<OverlayMarkerModel> = emptyList()
    private var markersVisible: Boolean = false
    private var placementMode: OverlayPlacementMode = OverlayPlacementMode.NONE
    private var touchEnabled: Boolean = true
    private var touchExclusionRects: List<Rect> = emptyList()

    private var onBackgroundTapCallback: ((ScreenPoint) -> Unit)? = null
    private var onMarkerChangedCallback: ((String, ScreenPoint) -> Unit)? = null
    private var onMarkerDragEndCallback: ((String, ScreenPoint) -> Unit)? = null
    private var onMarkerSelectedCallback: ((String) -> Unit)? = null

    fun hasPermission(): Boolean = Settings.canDrawOverlays(appContext)

    fun isTargetVisible(): Boolean = markersVisible

    fun currentMarkerPoint(markerId: String): ScreenPoint? = currentPoints[markerId]

    fun currentMarkerPoints(): Map<String, ScreenPoint> = currentPoints.toMap()

    fun setTouchExclusionRects(rects: List<Rect>) {
        touchExclusionRects = rects.map(::Rect)
        runOnMain {
            layerView?.let(::bindLayer)
        }
    }

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

    suspend fun showLayer(
        markers: List<OverlayMarkerModel>,
        areMarkersVisible: Boolean,
        placementMode: OverlayPlacementMode,
        onBackgroundTap: (ScreenPoint) -> Unit,
        onMarkerChanged: (String, ScreenPoint) -> Unit,
        onMarkerDragEnd: (String, ScreenPoint) -> Unit,
        onMarkerSelected: (String) -> Unit,
    ): Boolean = withContext(Dispatchers.Main.immediate) {
        if (!hasPermission()) {
            return@withContext false
        }

        onBackgroundTapCallback = onBackgroundTap
        onMarkerChangedCallback = onMarkerChanged
        onMarkerDragEndCallback = onMarkerDragEnd
        onMarkerSelectedCallback = onMarkerSelected
        currentMarkers = markers
        markersVisible = areMarkersVisible
        this@OverlayTargetController.placementMode = placementMode
        currentPoints.clear()
        currentPoints.putAll(markers.associate { it.markerId to it.point })

        val layer = ensureLayerView()
        bindLayer(layer)
        val params = layerLayoutParams ?: createLayerLayoutParams().also { layerLayoutParams = it }
        params.flags = layerFlags()
        val layerShown = runCatching {
            if (layer.parent == null) {
                windowManager.addView(layer, params)
            } else {
                windowManager.updateViewLayout(layer, params)
            }
        }.onFailure { throwable ->
            Log.e(TAG, "showLayer failed", throwable)
        }.isSuccess
        if (layerShown) {
            syncMarkersInternal(markers)
        }
        layerShown
    }

    suspend fun updateLayer(
        markers: List<OverlayMarkerModel> = currentMarkers,
        areMarkersVisible: Boolean = markersVisible,
        placementMode: OverlayPlacementMode = this@OverlayTargetController.placementMode,
    ): Boolean = withContext(Dispatchers.Main.immediate) {
        if (!hasPermission()) {
            return@withContext false
        }

        currentMarkers = markers
        markersVisible = areMarkersVisible
        this@OverlayTargetController.placementMode = placementMode
        currentPoints.keys.retainAll(markers.map { it.markerId }.toSet())
        markers.forEach { currentPoints[it.markerId] = it.point }

        val layer = ensureLayerView()
        bindLayer(layer)
        val params = layerLayoutParams ?: createLayerLayoutParams().also { layerLayoutParams = it }
        params.flags = layerFlags()
        var layerReady = true
        if (layer.parent == null) {
            layerReady = runCatching {
                windowManager.addView(layer, params)
            }.onFailure { throwable ->
                Log.e(TAG, "updateLayer add layer failed", throwable)
            }.isSuccess
        }
        if (!layerReady) {
            return@withContext false
        }
        if (layer.parent != null) {
            runCatching {
                windowManager.updateViewLayout(layer, params)
            }.onFailure { throwable ->
                Log.e(TAG, "updateLayer failed", throwable)
            }
        }
        syncMarkersInternal(markers)
        true
    }

    suspend fun setMarkerVisibility(
        visible: Boolean,
    ): Boolean = withContext(Dispatchers.Main.immediate) {
        markersVisible = visible
        val layer = layerView ?: return@withContext false
        bindLayer(layer)
        syncMarkersInternal(currentMarkers)
        true
    }

    suspend fun setPlacementMode(
        placementMode: OverlayPlacementMode,
    ): Boolean = withContext(Dispatchers.Main.immediate) {
        this@OverlayTargetController.placementMode = placementMode
        val layer = layerView ?: return@withContext false
        bindLayer(layer)
        layerLayoutParams?.flags = layerFlags()
        if (layer.parent != null) {
            runCatching {
                windowManager.updateViewLayout(layer, layerLayoutParams ?: return@withContext false)
            }
        }
        syncMarkersInternal(currentMarkers)
        true
    }

    suspend fun setTouchEnabled(
        enabled: Boolean,
    ): Boolean = withContext(Dispatchers.Main.immediate) {
        touchEnabled = enabled
        val layer = layerView ?: return@withContext false
        bindLayer(layer)
        layerLayoutParams?.flags = layerFlags()
        if (layer.parent != null) {
            runCatching {
                windowManager.updateViewLayout(layer, layerLayoutParams ?: return@withContext false)
            }
        }
        syncMarkersInternal(currentMarkers)
        true
    }

    suspend fun hideLayer(
        clearPoints: Boolean = false,
    ) = withContext(Dispatchers.Main.immediate) {
        hideInternal(clearPoints)
    }

    fun release() {
        runOnMain {
            hideInternal(clearPoints = true)
            markerEntries.clear()
            currentPoints.clear()
            layerView = null
            layerLayoutParams = null
            currentMarkers = emptyList()
            markersVisible = false
            placementMode = OverlayPlacementMode.NONE
            touchEnabled = true
            onBackgroundTapCallback = null
            onMarkerChangedCallback = null
            onMarkerDragEndCallback = null
            onMarkerSelectedCallback = null
        }
    }

    private fun ensureLayerView(): OverlayTargetLayerView {
        layerView?.let { return it }
        return OverlayTargetLayerView(appContext).also {
            layerView = it
        }
    }

    private fun bindLayer(
        layer: OverlayTargetLayerView,
    ) {
        layer.bind(
            markers = currentMarkers.map { marker ->
                marker.copy(point = currentPoints[marker.markerId] ?: marker.point)
            },
            areMarkersVisible = markersVisible,
            placementMode = placementMode,
            touchEnabled = touchEnabled,
            touchExclusionRects = touchExclusionRects,
            onBackgroundTap = { point ->
                onBackgroundTapCallback?.invoke(point)
            },
        )
    }

    private fun syncMarkersInternal(
        markers: List<OverlayMarkerModel>,
    ) {
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

        if (!markersVisible) {
            markerEntries.values.forEach { entry ->
                if (entry.view.parent != null) {
                    runCatching { windowManager.removeView(entry.view) }
                }
            }
            return
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
            entry.layoutParams.flags = markerFlags()
            val point = clampPoint(currentPoints[model.markerId] ?: model.point, bounds)
            applyPointToLayoutParams(entry.layoutParams, point)
            currentPoints[model.markerId] = point
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

        bindLayer(layerView ?: return)
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
            layoutParams = createMarkerLayoutParams(),
        )
    }

    private fun handleMarkerTouch(
        markerId: String,
        event: MotionEvent,
    ): Boolean {
        if (!touchEnabled || placementMode != OverlayPlacementMode.NONE || !markersVisible) {
            return false
        }
        val entry = markerEntries[markerId] ?: return false
        return when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                entry.downRawX = event.rawX
                entry.downRawY = event.rawY
                entry.dragging = false
                true
            }

            MotionEvent.ACTION_MOVE -> {
                if (!entry.dragging) {
                    entry.dragging = abs(event.rawX - entry.downRawX) >= touchSlop ||
                        abs(event.rawY - entry.downRawY) >= touchSlop
                    if (!entry.dragging) {
                        return true
                    }
                }

                val bounds = getScreenBounds()
                val clampedLeft = (event.rawX - markerHalfSizePx).roundToInt()
                    .coerceIn(0, (bounds.width - markerSizePx).coerceAtLeast(0))
                val clampedTop = (event.rawY - markerHalfSizePx).roundToInt()
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
                bindLayer(layerView ?: return true)
                onMarkerChangedCallback?.invoke(markerId, point)
                true
            }

            MotionEvent.ACTION_UP -> {
                if (entry.dragging) {
                    currentPoints[markerId]?.let { point ->
                        onMarkerDragEndCallback?.invoke(markerId, point)
                    }
                } else {
                    onMarkerSelectedCallback?.invoke(markerId)
                    entry.view.performClick()
                }
                entry.dragging = false
                true
            }

            MotionEvent.ACTION_CANCEL -> {
                if (entry.dragging) {
                    currentPoints[markerId]?.let { point ->
                        onMarkerDragEndCallback?.invoke(markerId, point)
                    }
                }
                entry.dragging = false
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

    private fun hideInternal(
        clearPoints: Boolean,
    ) {
        markerEntries.values.forEach { entry ->
            if (entry.view.parent != null) {
                runCatching { windowManager.removeView(entry.view) }
            }
        }
        layerView?.let { layer ->
            if (layer.parent != null) {
                runCatching { windowManager.removeView(layer) }
            }
        }
        if (clearPoints) {
            currentPoints.clear()
            currentMarkers = emptyList()
        }
    }

    private fun createMarkerLayoutParams(): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            markerSizePx,
            markerSizePx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            markerFlags(),
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
        }
    }

    private fun createLayerLayoutParams(): WindowManager.LayoutParams {
        val bounds = getScreenBounds()
        return WindowManager.LayoutParams(
            bounds.width,
            bounds.height,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            layerFlags(),
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
        }
    }

    private fun layerFlags(): Int {
        var flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        if (placementMode == OverlayPlacementMode.NONE || !touchEnabled) {
            flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }
        return flags
    }

    private fun markerFlags(): Int {
        var flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        if (!touchEnabled || placementMode != OverlayPlacementMode.NONE) {
            flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }
        return flags
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

    private data class MarkerEntry(
        val view: TargetMarkerView,
        val layoutParams: WindowManager.LayoutParams,
        var downRawX: Float = 0f,
        var downRawY: Float = 0f,
        var dragging: Boolean = false,
    )

    private data class ScreenBounds(
        val width: Int,
        val height: Int,
    )

    private companion object {
        const val TAG = "ClickAssistTarget"
    }
}
