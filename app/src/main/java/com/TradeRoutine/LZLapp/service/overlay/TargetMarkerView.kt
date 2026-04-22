package com.TradeRoutine.LZLapp.service.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import com.TradeRoutine.LZLapp.domain.model.ActionType
import com.TradeRoutine.LZLapp.domain.repository.AppSettings
import kotlin.math.min

class TargetMarkerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {
    private val outerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 4f * resources.displayMetrics.density
    }
    private val crosshairPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2f * resources.displayMetrics.density
    }
    private val centerOuterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val centerInnerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        setTextSize(
            TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP,
                14f,
                resources.displayMetrics,
            ),
        )
        isFakeBoldText = true
    }

    private var label: String = ""
    private var actionType: ActionType = ActionType.TAP
    private var role: OverlayMarkerRole = OverlayMarkerRole.PRIMARY
    private var isSelected: Boolean = false
    private var appearance: OverlayAppearance =
        OverlayAppearance.fromSettings(context, AppSettings())

    fun applyAppearance(appearance: OverlayAppearance) {
        this.appearance = appearance
        invalidate()
    }

    fun bind(
        label: String,
        actionType: ActionType,
        role: OverlayMarkerRole,
        isSelected: Boolean,
    ) {
        this.label = label
        this.actionType = actionType
        this.role = role
        this.isSelected = isSelected
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        outerPaint.color = resolveOuterColor(
            actionType = actionType,
            role = role,
            isSelected = isSelected,
        )
        ringPaint.strokeWidth = if (isSelected) {
            5f * resources.displayMetrics.density
        } else {
            4f * resources.displayMetrics.density
        }
        crosshairPaint.strokeWidth = if (isSelected) {
            2.5f * resources.displayMetrics.density
        } else {
            2f * resources.displayMetrics.density
        }
        centerOuterPaint.color = appearance.markerCenterOuterColor
        centerInnerPaint.color = appearance.markerCenterInnerColor

        val centerX = width / 2f
        val centerY = height / 2f
        val outerRadius = min(width, height) / 2f
        val innerRadius = outerRadius * 0.45f
        val crosshairRadius = outerRadius * 0.72f
        val centerOuterRadius = outerRadius * 0.13f
        val centerInnerRadius = outerRadius * 0.07f

        canvas.drawCircle(centerX, centerY, outerRadius, outerPaint)
        canvas.drawCircle(centerX, centerY, innerRadius, ringPaint)
        if (appearance.showMarkerCenterCross) {
            canvas.drawLine(centerX - crosshairRadius, centerY, centerX + crosshairRadius, centerY, crosshairPaint)
            canvas.drawLine(centerX, centerY - crosshairRadius, centerX, centerY + crosshairRadius, crosshairPaint)
            canvas.drawCircle(centerX, centerY, centerOuterRadius, centerOuterPaint)
            canvas.drawCircle(centerX, centerY, centerInnerRadius, centerInnerPaint)
        }

        if (appearance.showMarkerNumbers && label.isNotEmpty()) {
            val baseline = centerY + outerRadius * 0.46f - ((textPaint.descent() + textPaint.ascent()) / 2f)
            canvas.drawText(label, centerX, baseline, textPaint)
        }
    }

    private fun resolveOuterColor(
        actionType: ActionType,
        role: OverlayMarkerRole,
        isSelected: Boolean,
    ): Int {
        if (isSelected) {
            return appearance.markerSelectedColor
        }
        return when (actionType) {
            ActionType.TAP -> appearance.markerTapColor
            ActionType.LONG_PRESS -> appearance.markerLongPressColor
            ActionType.SWIPE -> {
                when (role) {
                    OverlayMarkerRole.START -> appearance.markerSwipeStartColor
                    OverlayMarkerRole.END -> appearance.markerSwipeEndColor
                    OverlayMarkerRole.PRIMARY -> appearance.markerSwipeStartColor
                }
            }
            ActionType.WAIT -> appearance.textSecondaryColor
        }
    }

    companion object {
        const val DEFAULT_SIZE_DP = 56
    }
}
