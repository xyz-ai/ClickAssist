package com.example.clickassist.ui.overlay

import android.content.Context
import android.util.Log
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import com.example.clickassist.R
import com.example.clickassist.domain.model.ActionType
import com.example.clickassist.domain.repository.AppSettings
import com.example.clickassist.service.overlay.OverlayAppearance
import com.example.clickassist.service.overlay.OverlayPanelSpec
import com.example.clickassist.service.overlay.OverlayStylable
import kotlin.math.roundToInt

class OverlayAddStepPanelView(
    context: Context,
) : ScrollView(context), OverlayStylable {
    private var appearance: OverlayAppearance =
        OverlayAppearance.fromSettings(context, AppSettings())
    private var lastModel: OverlayPanelSpec.AddNode? = null
    private val contentContainer = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(4), dp(4), dp(4), dp(4))
    }

    init {
        isFillViewport = true
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
        contentContainer.addView(createButton(R.string.task_edit_add_tap_step) { model.onAddStep(ActionType.TAP) })
        contentContainer.addView(createButton(R.string.task_edit_add_long_press_step) { model.onAddStep(ActionType.LONG_PRESS) }, topMarginParams())
        contentContainer.addView(createButton(R.string.task_edit_add_swipe_step) { model.onAddStep(ActionType.SWIPE) }, topMarginParams())
    }

    override fun applyAppearance(appearance: OverlayAppearance) {
        this.appearance = appearance
        lastModel?.let(::bind)
    }

    private fun createButton(
        labelRes: Int,
        onClick: () -> Unit,
    ): Button {
        return Button(context).apply {
            text = context.getString(labelRes)
            isAllCaps = false
            setTextColor(appearance.toolbarButtonTextColor)
            setBackgroundColor(appearance.primaryActionColor)
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

    private companion object {
        const val ACTION_TAG = "OverlayAction"
    }
}
