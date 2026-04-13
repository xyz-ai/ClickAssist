package com.example.clickassist.service.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.view.MotionEvent
import android.view.View
import com.example.clickassist.domain.model.ScreenPoint
import com.example.clickassist.service.runner.OverlayPlacementMode
import kotlin.math.abs
import kotlin.math.roundToInt

class OverlayTargetLayerView(
    context: Context,
) : View(context) {
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#60A5FA")
        style = Paint.Style.STROKE
        strokeWidth = 6f * resources.displayMetrics.density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val selectedLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F59E0B")
        style = Paint.Style.STROKE
        strokeWidth = 8f * resources.displayMetrics.density
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
    private var downX = 0f
    private var downY = 0f
    private var dragging = false

    init {
        setWillNotDraw(false)
    }

    fun bind(
        markers: List<OverlayMarkerModel>,
        areMarkersVisible: Boolean,
        placementMode: OverlayPlacementMode,
        touchEnabled: Boolean,
        touchExclusionRects: List<Rect>,
        onBackgroundTap: (ScreenPoint) -> Unit,
    ) {
        this.markers = markers
        markersVisible = areMarkersVisible
        this.placementMode = placementMode
        this.touchEnabled = touchEnabled
        this.touchExclusionRects = touchExclusionRects.map(::Rect)
        backgroundTapCallback = onBackgroundTap
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!markersVisible) {
            return
        }

        markers
            .filter { it.connectedMarkerId != null }
            .sortedBy { it.markerId }
            .forEach { model ->
                val otherId = model.connectedMarkerId ?: return@forEach
                if (model.markerId > otherId) {
                    return@forEach
                }
                val other = markers.firstOrNull { it.markerId == otherId } ?: return@forEach
                val paint = if (model.isSelected || other.isSelected) {
                    selectedLinePaint
                } else {
                    linePaint
                }
                canvas.drawLine(
                    model.point.x.toFloat(),
                    model.point.y.toFloat(),
                    other.point.x.toFloat(),
                    other.point.y.toFloat(),
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
        if (touchExclusionRects.any { it.contains(touchX, touchY) }) {
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
                            x = event.x.roundToInt(),
                            y = event.y.roundToInt(),
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
