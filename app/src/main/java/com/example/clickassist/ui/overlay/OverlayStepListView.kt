package com.example.clickassist.ui.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.example.clickassist.R
import com.example.clickassist.domain.model.ActionType
import com.example.clickassist.service.overlay.OverlayPanelSpec
import kotlin.math.roundToInt

class OverlayStepListView(
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

    fun bind(model: OverlayPanelSpec.StepList) {
        contentContainer.removeAllViews()
        if (model.items.isEmpty()) {
            contentContainer.addView(emptyText())
            return
        }

        model.items.forEach { item ->
            contentContainer.addView(
                LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(12), dp(10), dp(12), dp(10))
                    setBackgroundColor(if (item.isSelected) Color.parseColor("#DBEAFE") else Color.parseColor("#F8FAFC"))

                    addView(
                        TextView(context).apply {
                            setTextColor(Color.parseColor("#0F172A"))
                            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                            setTypeface(typeface, Typeface.BOLD)
                            text = context.getString(
                                R.string.overlay_panel_step_item_title,
                                item.orderIndex + 1,
                                context.getString(actionTypeLabel(item.actionType)),
                            )
                        },
                    )

                    addView(
                        TextView(context).apply {
                            setTextColor(Color.parseColor("#475569"))
                            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                            text = context.getString(
                                if (item.enabled) {
                                    R.string.common_status_enabled
                                } else {
                                    R.string.common_status_disabled
                                },
                            )
                        },
                        LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                        ).apply {
                            topMargin = dp(4)
                        },
                    )

                    addView(
                        LinearLayout(context).apply {
                            orientation = LinearLayout.HORIZONTAL
                            gravity = Gravity.START
                            addView(button(R.string.overlay_panel_step_select) {
                                model.onStepSelected(item.stepId)
                            })
                            addView(button(R.string.common_delete) {
                                model.onDeleteStep(item.stepId)
                            }, buttonParams())
                            addView(
                                button(R.string.common_move_up) {
                                    model.onMoveUp(item.stepId)
                                }.apply {
                                    isEnabled = item.canMoveUp
                                    alpha = if (item.canMoveUp) 1f else 0.45f
                                },
                                buttonParams(),
                            )
                            addView(
                                button(R.string.common_move_down) {
                                    model.onMoveDown(item.stepId)
                                }.apply {
                                    isEnabled = item.canMoveDown
                                    alpha = if (item.canMoveDown) 1f else 0.45f
                                },
                                buttonParams(),
                            )
                        },
                        LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                        ).apply {
                            topMargin = dp(10)
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

    private fun emptyText(): TextView {
        return TextView(context).apply {
            setTextColor(Color.parseColor("#475569"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            text = context.getString(R.string.overlay_panel_no_steps)
        }
    }

    private fun button(
        stringRes: Int,
        onClick: () -> Unit,
    ): Button {
        return Button(context).apply {
            text = context.getString(stringRes)
            isAllCaps = false
            setOnClickListener { onClick() }
        }
    }

    private fun buttonParams(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply {
            marginStart = dp(6)
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

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).roundToInt()
    }
}
