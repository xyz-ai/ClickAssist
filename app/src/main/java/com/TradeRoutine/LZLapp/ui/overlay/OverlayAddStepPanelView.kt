package com.TradeRoutine.LZLapp.ui.overlay

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.TradeRoutine.LZLapp.R
import com.TradeRoutine.LZLapp.domain.model.ActionType
import com.TradeRoutine.LZLapp.domain.repository.AppSettings
import com.TradeRoutine.LZLapp.service.overlay.OverlayAppearance
import com.TradeRoutine.LZLapp.service.overlay.OverlayPanelSpec
import com.TradeRoutine.LZLapp.service.overlay.OverlayStylable
import kotlin.math.roundToInt

class OverlayAddStepPanelView(
    context: Context,
) : ScrollView(context), OverlayStylable {
    private var appearance: OverlayAppearance =
        OverlayAppearance.fromSettings(context, AppSettings())
    private var lastModel: OverlayPanelSpec.AddNode? = null
    private val contentContainer = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(2), dp(2), dp(2), dp(2))
    }

    init {
        isFillViewport = false
        overScrollMode = OVER_SCROLL_NEVER
        addView(
            contentContainer,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT,
            ),
        )
    }

    fun bind(model: OverlayPanelSpec.AddNode) {
        lastModel = model
        contentContainer.removeAllViews()
        setBackgroundColor(appearance.panelBackgroundColor)
        contentContainer.addView(createActionRow(R.string.task_edit_add_tap_step) { model.onAddStep(ActionType.TAP) })
        contentContainer.addView(
            createActionRow(R.string.task_edit_add_long_press_step) { model.onAddStep(ActionType.LONG_PRESS) },
            topMarginParams(),
        )
        contentContainer.addView(
            createActionRow(R.string.task_edit_add_swipe_step) { model.onAddStep(ActionType.SWIPE) },
            topMarginParams(),
        )
    }

    override fun applyAppearance(appearance: OverlayAppearance) {
        this.appearance = appearance
        lastModel?.let(::bind)
    }

    private fun createActionRow(
        labelRes: Int,
        onClick: () -> Unit,
    ): TextView {
        return TextView(context).apply {
            text = context.getString(labelRes)
            gravity = Gravity.CENTER_VERTICAL
            minHeight = dp(48)
            setPadding(dp(16), dp(10), dp(16), dp(10))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(appearance.textPrimaryColor)
            background = GradientDrawable().apply {
                setColor(appearance.surfaceVariantColor)
                cornerRadius = dpFloat(16)
                setStroke(dp(1), appearance.panelBorderColor)
            }
            isClickable = true
            isFocusable = true
            setOnClickListener {
                Log.i(ACTION_TAG, "add node option clicked label=$text")
                onClick()
            }
        }
    }

    private fun topMarginParams(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply {
            topMargin = dp(8)
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).roundToInt()
    }

    private fun dpFloat(value: Int): Float {
        return value * resources.displayMetrics.density
    }

    private companion object {
        const val ACTION_TAG = "OverlayAction"
    }
}
