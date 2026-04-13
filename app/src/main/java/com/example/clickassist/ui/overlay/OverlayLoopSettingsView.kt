package com.example.clickassist.ui.overlay

import android.content.Context
import android.graphics.Color
import android.text.InputType
import android.util.TypedValue
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.example.clickassist.R
import com.example.clickassist.service.overlay.OverlayPanelSpec
import kotlin.math.roundToInt

class OverlayLoopSettingsView(
    context: Context,
) : ScrollView(context) {
    private val contentContainer = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(4), dp(4), dp(4), dp(4))
    }
    private val totalRoundsInput = EditText(context).apply {
        setTextColor(Color.parseColor("#0F172A"))
        setHintTextColor(Color.parseColor("#94A3B8"))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        setBackgroundColor(Color.parseColor("#FFFFFF"))
        setPadding(dp(12), dp(10), dp(12), dp(10))
        inputType = InputType.TYPE_CLASS_NUMBER
    }
    private val infiniteRoundsCheckBox = CheckBox(context).apply {
        text = context.getString(R.string.task_edit_label_infinite_rounds)
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

    fun bind(model: OverlayPanelSpec.LoopSettings) {
        contentContainer.removeAllViews()
        totalRoundsInput.setText(model.totalRounds)
        infiniteRoundsCheckBox.isChecked = model.infiniteRounds

        contentContainer.addView(label(R.string.task_edit_label_total_rounds))
        contentContainer.addView(totalRoundsInput, matchWidthParams())
        contentContainer.addView(infiniteRoundsCheckBox, topMarginParams())
        contentContainer.addView(
            Button(context).apply {
                text = context.getString(R.string.overlay_panel_apply_and_save)
                isAllCaps = false
                setOnClickListener {
                    model.onSave(
                        totalRoundsInput.text?.toString().orEmpty(),
                        infiniteRoundsCheckBox.isChecked,
                    )
                }
            },
            topMarginParams(),
        )
    }

    private fun label(labelRes: Int): TextView {
        return TextView(context).apply {
            setTextColor(Color.parseColor("#334155"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            text = context.getString(labelRes)
        }
    }

    private fun matchWidthParams(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply {
            topMargin = dp(8)
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
}
