package com.example.clickassist.service.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

class TargetMarkerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {
    private val outerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E53935")
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

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val centerX = width / 2f
        val centerY = height / 2f
        val outerRadius = min(width, height) / 2f
        val innerRadius = outerRadius * 0.45f
        val crosshairRadius = outerRadius * 0.72f

        canvas.drawCircle(centerX, centerY, outerRadius, outerPaint)
        canvas.drawCircle(centerX, centerY, innerRadius, ringPaint)
        canvas.drawLine(centerX - crosshairRadius, centerY, centerX + crosshairRadius, centerY, crosshairPaint)
        canvas.drawLine(centerX, centerY - crosshairRadius, centerX, centerY + crosshairRadius, crosshairPaint)
    }

    override fun performClick(): Boolean {
        return super.performClick()
    }
}
