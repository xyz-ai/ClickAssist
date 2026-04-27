package com.TradeRoutine.LZLapp.ui.overlay

import android.content.Context
import android.graphics.Color
import android.text.InputType
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.TradeRoutine.LZLapp.R
import com.TradeRoutine.LZLapp.domain.model.ActionType
import com.TradeRoutine.LZLapp.domain.repository.AppSettings
import com.TradeRoutine.LZLapp.service.overlay.OverlayAppearance
import com.TradeRoutine.LZLapp.service.overlay.OverlayPanelSpec
import com.TradeRoutine.LZLapp.service.overlay.OverlayStepEditorDraft
import com.TradeRoutine.LZLapp.service.overlay.OverlayStylable
import kotlin.math.roundToInt

class OverlayStepEditorView(
    context: Context,
) : ScrollView(context), OverlayStylable {
    private var appearance: OverlayAppearance =
        OverlayAppearance.fromSettings(context, AppSettings())
    private var lastModel: OverlayPanelSpec.StepEditor? = null
    private val contentContainer = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(4), dp(4), dp(4), dp(4))
    }
    private val enabledCheckBox = CheckBox(context).apply {
        text = context.getString(R.string.task_edit_label_enabled)
    }
    private val xInput = createNumberInput(signed = true)
    private val yInput = createNumberInput(signed = true)
    private val endXInput = createNumberInput(signed = true)
    private val endYInput = createNumberInput(signed = true)
    private val intervalInput = createNumberInput()
    private val durationInput = createNumberInput()
    private val repeatInput = createNumberInput()
    private val preDelayInput = createNumberInput()
    private val postDelayInput = createNumberInput()
    private val primaryCoordinateContainer = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val swipeCoordinateContainer = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val durationContainer = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val waitHintView = TextView(context).apply {
        setTextColor(Color.parseColor("#475569"))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        visibility = View.GONE
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

    fun bind(model: OverlayPanelSpec.StepEditor) {
        lastModel = model
        contentContainer.removeAllViews()
        setBackgroundColor(appearance.panelBackgroundColor)
        val draft = model.draft
        if (draft == null) {
            contentContainer.addView(
                TextView(context).apply {
                    setTextColor(Color.parseColor("#475569"))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    text = context.getString(R.string.overlay_panel_no_selected_step)
                },
            )
            return
        }

        enabledCheckBox.isChecked = draft.enabled
        xInput.setText(draft.x)
        yInput.setText(draft.y)
        endXInput.setText(draft.endX)
        endYInput.setText(draft.endY)
        intervalInput.setText(draft.intervalMs)
        durationInput.setText(draft.durationMs)
        repeatInput.setText(draft.repeatCount)
        preDelayInput.setText(draft.preDelayMs)
        postDelayInput.setText(draft.postDelayMs)

        contentContainer.addView(
            TextView(context).apply {
                setTextColor(Color.parseColor("#0F172A"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                text = context.getString(
                    R.string.overlay_panel_step_item_title,
                    draft.orderIndex + 1,
                    context.getString(actionTypeLabel(draft.actionType)),
                )
            },
        )
        contentContainer.addView(enabledCheckBox, topMarginParams())

        primaryCoordinateContainer.removeAllViews()
        primaryCoordinateContainer.addView(labeledField(labelForPrimaryX(draft.actionType), xInput))
        primaryCoordinateContainer.addView(labeledField(labelForPrimaryY(draft.actionType), yInput), topMarginParams())
        primaryCoordinateContainer.visibility = if (draft.actionType == ActionType.WAIT) View.GONE else View.VISIBLE
        contentContainer.addView(primaryCoordinateContainer, topMarginParams())

        swipeCoordinateContainer.removeAllViews()
        swipeCoordinateContainer.addView(labeledField(R.string.overlay_panel_field_end_x, endXInput))
        swipeCoordinateContainer.addView(labeledField(R.string.overlay_panel_field_end_y, endYInput), topMarginParams())
        swipeCoordinateContainer.visibility = if (draft.actionType == ActionType.SWIPE) View.VISIBLE else View.GONE
        contentContainer.addView(swipeCoordinateContainer, topMarginParams())

        durationContainer.removeAllViews()
        durationContainer.addView(labeledField(durationLabel(draft.actionType), durationInput))
        durationContainer.visibility = if (draft.actionType == ActionType.TAP) View.GONE else View.VISIBLE
        contentContainer.addView(durationContainer, topMarginParams())

        waitHintView.text = context.getString(R.string.overlay_panel_wait_hint)
        waitHintView.visibility = if (draft.actionType == ActionType.WAIT) View.VISIBLE else View.GONE
        contentContainer.addView(waitHintView, topMarginParams())

        contentContainer.addView(labeledField(R.string.task_edit_label_repeat_count, repeatInput), topMarginParams())
        contentContainer.addView(labeledField(R.string.task_edit_label_interval, intervalInput), topMarginParams())
        contentContainer.addView(labeledField(R.string.task_edit_label_pre_delay, preDelayInput), topMarginParams())
        contentContainer.addView(labeledField(R.string.task_edit_label_post_delay, postDelayInput), topMarginParams())
        contentContainer.addView(
            Button(context).apply {
                text = context.getString(R.string.overlay_panel_apply_and_save)
                isAllCaps = false
                setOnClickListener {
                    model.onSave(
                        draft.copy(
                            enabled = enabledCheckBox.isChecked,
                            x = xInput.text?.toString().orEmpty(),
                            y = yInput.text?.toString().orEmpty(),
                            endX = endXInput.text?.toString().orEmpty(),
                            endY = endYInput.text?.toString().orEmpty(),
                            intervalMs = intervalInput.text?.toString().orEmpty(),
                            durationMs = durationInput.text?.toString().orEmpty(),
                            repeatCount = repeatInput.text?.toString().orEmpty(),
                            preDelayMs = preDelayInput.text?.toString().orEmpty(),
                            postDelayMs = postDelayInput.text?.toString().orEmpty(),
                        ),
                    )
                }
            },
            topMarginParams(),
        )
        model.onDeleteStep?.let { onDelete ->
            contentContainer.addView(
                Button(context).apply {
                    text = context.getString(R.string.common_delete)
                    isAllCaps = false
                    setOnClickListener {
                        onDelete(draft.stepId)
                    }
                },
                topMarginParams(),
            )
        }
    }

    override fun applyAppearance(appearance: OverlayAppearance) {
        this.appearance = appearance
        lastModel?.let(::bind)
    }

    private fun labeledField(
        labelRes: Int,
        input: EditText,
    ): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(
                TextView(context).apply {
                    setTextColor(Color.parseColor("#334155"))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    text = context.getString(labelRes)
                },
            )
            addView(
                input,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply {
                    topMargin = dp(6)
                },
            )
        }
    }

    private fun createNumberInput(
        signed: Boolean = false,
    ): EditText {
        val flags = if (signed) InputType.TYPE_NUMBER_FLAG_SIGNED else 0
        return EditText(context).apply {
            setTextColor(Color.parseColor("#0F172A"))
            setHintTextColor(Color.parseColor("#94A3B8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setBackgroundColor(Color.parseColor("#FFFFFF"))
            setPadding(dp(12), dp(10), dp(12), dp(10))
            inputType = InputType.TYPE_CLASS_NUMBER or flags
        }
    }

    private fun labelForPrimaryX(
        actionType: ActionType,
    ): Int {
        return when (actionType) {
            ActionType.SWIPE -> R.string.overlay_panel_field_start_x
            else -> R.string.overlay_panel_field_x
        }
    }

    private fun labelForPrimaryY(
        actionType: ActionType,
    ): Int {
        return when (actionType) {
            ActionType.SWIPE -> R.string.overlay_panel_field_start_y
            else -> R.string.overlay_panel_field_y
        }
    }

    private fun durationLabel(
        actionType: ActionType,
    ): Int {
        return when (actionType) {
            ActionType.LONG_PRESS -> R.string.task_edit_label_long_press_duration
            ActionType.WAIT -> R.string.task_edit_label_wait_duration
            else -> R.string.task_edit_label_duration
        }
    }

    private fun actionTypeLabel(
        actionType: ActionType,
    ): Int {
        return when (actionType) {
            ActionType.TAP -> R.string.action_type_tap
            ActionType.LONG_PRESS -> R.string.action_type_long_press
            ActionType.SWIPE -> R.string.action_type_swipe
            ActionType.WAIT -> R.string.action_type_wait
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
