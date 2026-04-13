package com.example.clickassist.service.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import com.example.clickassist.domain.model.ActionType
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

        val centerX = width / 2f
        val centerY = height / 2f
        val outerRadius = min(width, height) / 2f
        val innerRadius = outerRadius * 0.45f
        val crosshairRadius = outerRadius * 0.72f

        canvas.drawCircle(centerX, centerY, outerRadius, outerPaint)
        canvas.drawCircle(centerX, centerY, innerRadius, ringPaint)
        canvas.drawLine(centerX - crosshairRadius, centerY, centerX + crosshairRadius, centerY, crosshairPaint)
        canvas.drawLine(centerX, centerY - crosshairRadius, centerX, centerY + crosshairRadius, crosshairPaint)

        if (label.isNotEmpty()) {
            val baseline = centerY - ((textPaint.descent() + textPaint.ascent()) / 2f)
            canvas.drawText(label, centerX, baseline, textPaint)
        }
    }

    private fun resolveOuterColor(
        actionType: ActionType,
        role: OverlayMarkerRole,
        isSelected: Boolean,
    ): Int {
        if (isSelected) {
            return Color.parseColor("#F59E0B")
        }
        return when (actionType) {
            ActionType.TAP -> Color.parseColor("#E53935")
            ActionType.LONG_PRESS -> Color.parseColor("#8E24AA")
            ActionType.SWIPE -> {
                when (role) {
                    OverlayMarkerRole.START -> Color.parseColor("#2563EB")
                    OverlayMarkerRole.END -> Color.parseColor("#0891B2")
                    OverlayMarkerRole.PRIMARY -> Color.parseColor("#2563EB")
                }
            }
            ActionType.WAIT -> Color.parseColor("#4B5563")
        }
    }

    companion object {
        const val DEFAULT_SIZE_DP = 56
    }
}
