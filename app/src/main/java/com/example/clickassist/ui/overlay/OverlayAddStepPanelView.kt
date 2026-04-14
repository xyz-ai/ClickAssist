package com.example.clickassist.ui.overlay

import android.content.Context
import android.util.Log
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import com.example.clickassist.R
import com.example.clickassist.domain.model.ActionType
import com.example.clickassist.service.overlay.OverlayPanelSpec
import kotlin.math.roundToInt

class OverlayAddStepPanelView(
    context: Context,
) : ScrollView(context) {
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
        contentContainer.removeAllViews()
        contentContainer.addView(createButton(R.string.task_edit_add_tap_step) { model.onAddStep(ActionType.TAP) })
        contentContainer.addView(createButton(R.string.task_edit_add_long_press_step) { model.onAddStep(ActionType.LONG_PRESS) }, topMarginParams())
        contentContainer.addView(createButton(R.string.task_edit_add_swipe_step) { model.onAddStep(ActionType.SWIPE) }, topMarginParams())
    }

    private fun createButton(
        labelRes: Int,
        onClick: () -> Unit,
    ): Button {
        return Button(context).apply {
            text = context.getString(labelRes)
            isAllCaps = false
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
