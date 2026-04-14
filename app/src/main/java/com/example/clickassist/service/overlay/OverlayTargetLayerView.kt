package com.example.clickassist.service.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.view.MotionEvent
import android.view.View
import com.example.clickassist.domain.model.ScreenPoint
import com.example.clickassist.domain.repository.AppSettings
import com.example.clickassist.service.runner.OverlayPlacementMode
import kotlin.math.abs
import kotlin.math.roundToInt

class OverlayTargetLayerView(
    context: Context,
) : View(context) {
    private val density = resources.displayMetrics.density
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val selectedLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val touchSlop = (8 * resources.displayMetrics.density).roundToInt()

    private var markers: List<OverlayMarkerModel> = emptyList()
    private var markersVisible: Boolean = false
    private var placementMode: OverlayPlacementMode = OverlayPlacementMode.NONE
    private var touchEnabled: Boolean = true
    private var touchExclusionRects: List<Rect> = emptyList()
    private var backgroundTapCallback: ((ScreenPoint) -> Unit)? = null
    private var originOnScreenX: Int = 0
    private var originOnScreenY: Int = 0
    private var downX = 0f
    private var downY = 0f
    private var dragging = false
    private var appearance: OverlayAppearance =
        OverlayAppearance.fromSettings(context, AppSettings())

    init {
        setWillNotDraw(false)
        applyAppearance(appearance)
    }

    fun applyAppearance(appearance: OverlayAppearance) {
        this.appearance = appearance
        linePaint.color = appearance.swipeLineColor
        linePaint.strokeWidth = appearance.swipeLineWidthPx
        selectedLinePaint.color = appearance.swipeSelectedLineColor
        selectedLinePaint.strokeWidth = appearance.swipeSelectedLineWidthPx
        invalidate()
    }

    fun bind(
        markers: List<OverlayMarkerModel>,
        areMarkersVisible: Boolean,
        placementMode: OverlayPlacementMode,
        touchEnabled: Boolean,
        touchExclusionRects: List<Rect>,
        originOnScreenX: Int,
        originOnScreenY: Int,
        onBackgroundTap: (ScreenPoint) -> Unit,
    ) {
        this.markers = markers
        markersVisible = areMarkersVisible
        this.placementMode = placementMode
        this.touchEnabled = touchEnabled
        this.touchExclusionRects = touchExclusionRects.map(::Rect)
        this.originOnScreenX = originOnScreenX
        this.originOnScreenY = originOnScreenY
        backgroundTapCallback = onBackgroundTap
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!markersVisible) {
            return
        }

        val markerMap = markers.associateBy { it.markerId }
        markers
            .filter { it.connectedMarkerId != null }
            .sortedBy { it.markerId }
            .forEach { model ->
                val otherId = model.connectedMarkerId ?: return@forEach
                if (model.markerId > otherId) {
                    return@forEach
                }
                val other = markerMap[otherId] ?: return@forEach
                val paint = if (model.isSelected || other.isSelected) {
                    selectedLinePaint
                } else {
                    linePaint
                }
                val startX = (model.point.x - originOnScreenX).toFloat()
                val startY = (model.point.y - originOnScreenY).toFloat()
                val endX = (other.point.x - originOnScreenX).toFloat()
                val endY = (other.point.y - originOnScreenY).toFloat()
                canvas.drawLine(
                    startX,
                    startY,
                    endX,
                    endY,
                    paint,
                )
            }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!touchEnabled || placementMode == OverlayPlacementMode.NONE) {
            return false
        }
        val touchX = event.x.roundToInt()
        val touchY = event.y.roundToInt()
        val absoluteTouchX = touchX + originOnScreenX
        val absoluteTouchY = touchY + originOnScreenY
        if (touchExclusionRects.any { it.contains(absoluteTouchX, absoluteTouchY) }) {
            return false
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                dragging = false
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (!dragging) {
                    dragging = abs(event.x - downX) >= touchSlop || abs(event.y - downY) >= touchSlop
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                if (!dragging) {
                    backgroundTapCallback?.invoke(
                        ScreenPoint(
                            x = event.x.roundToInt() + originOnScreenX,
                            y = event.y.roundToInt() + originOnScreenY,
                        ),
                    )
                }
                dragging = false
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                dragging = false
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
