package com.example.clickassist.ui.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.example.clickassist.R
import com.example.clickassist.service.overlay.OverlayPanelSpec
import kotlin.math.roundToInt

class OverlaySchemePanelView(
    context: Context,
) : ScrollView(context) {
    private val contentContainer = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(4), dp(4), dp(4), dp(4))
    }
    private val currentNameInput = createInput()
    private val saveAsInput = createInput()
    private val tasksContainer = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
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

    fun bind(model: OverlayPanelSpec.Scheme) {
        contentContainer.removeAllViews()

        currentNameInput.setText(model.currentTaskName)
        saveAsInput.setText(model.saveAsDefaultName)

        contentContainer.addView(sectionTitle(R.string.overlay_panel_current_scheme))
        contentContainer.addView(currentNameInput, matchWidthParams())
        contentContainer.addView(
            actionButton(R.string.overlay_panel_save_scheme) {
                model.onSaveCurrent(currentNameInput.text?.toString().orEmpty())
            },
            topMarginParams(),
        )

        contentContainer.addView(sectionTitle(R.string.overlay_panel_save_as), topMarginParams())
        contentContainer.addView(saveAsInput, matchWidthParams())
        contentContainer.addView(
            actionButton(R.string.overlay_panel_save_as) {
                model.onSaveAs(saveAsInput.text?.toString().orEmpty())
            },
            topMarginParams(),
        )

        contentContainer.addView(sectionTitle(R.string.overlay_panel_scheme_list), topMarginParams())
        contentContainer.addView(tasksContainer, matchWidthParams())
        renderTaskList(model)
    }

    private fun renderTaskList(model: OverlayPanelSpec.Scheme) {
        tasksContainer.removeAllViews()
        if (model.tasks.isEmpty()) {
            tasksContainer.addView(bodyText(R.string.overlay_panel_no_tasks))
            return
        }

        model.tasks.forEach { item ->
            tasksContainer.addView(
                LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(12), dp(10), dp(12), dp(10))
                    setBackgroundColor(if (item.isActive) Color.parseColor("#E0F2FE") else Color.parseColor("#F8FAFC"))

                    addView(
                        TextView(context).apply {
                            setTextColor(Color.parseColor("#0F172A"))
                            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                            setTypeface(typeface, Typeface.BOLD)
                            text = item.name
                        },
                    )
                    addView(
                        TextView(context).apply {
                            setTextColor(Color.parseColor("#475569"))
                            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                            text = context.getString(R.string.overlay_panel_scheme_steps, item.stepCount)
                        },
                        LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                        ).apply {
                            topMargin = dp(4)
                        },
                    )
                    addView(
                        actionButton(
                            if (item.isActive) R.string.overlay_panel_scheme_current else R.string.overlay_panel_scheme_switch,
                        ) {
                            if (!item.isActive) {
                                model.onTaskSelected(item.taskId)
                            }
                        }.apply {
                            isEnabled = !item.isActive
                            alpha = if (item.isActive) 0.5f else 1f
                        },
                        LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                        ).apply {
                            topMargin = dp(8)
                        },
                    )
                },
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply {
                    topMargin = dp(8)
                },
            )
        }
    }

    private fun sectionTitle(stringRes: Int): TextView {
        return TextView(context).apply {
            setTextColor(Color.parseColor("#0F172A"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTypeface(typeface, Typeface.BOLD)
            text = context.getString(stringRes)
        }
    }

    private fun bodyText(stringRes: Int): TextView {
        return TextView(context).apply {
            setTextColor(Color.parseColor("#475569"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            text = context.getString(stringRes)
        }
    }

    private fun createInput(): EditText {
        return EditText(context).apply {
            setTextColor(Color.parseColor("#0F172A"))
            setHintTextColor(Color.parseColor("#94A3B8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setBackgroundColor(Color.parseColor("#FFFFFF"))
            setPadding(dp(12), dp(10), dp(12), dp(10))
            inputType = InputType.TYPE_CLASS_TEXT
        }
    }

    private fun actionButton(
        stringRes: Int,
        onClick: () -> Unit,
    ): Button {
        return Button(context).apply {
            text = context.getString(stringRes)
            isAllCaps = false
            gravity = Gravity.CENTER
            setOnClickListener { onClick() }
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
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply {
            topMargin = dp(8)
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).roundToInt()
    }
}
