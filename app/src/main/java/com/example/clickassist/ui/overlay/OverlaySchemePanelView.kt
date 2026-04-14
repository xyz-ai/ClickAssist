package com.example.clickassist.ui.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.InputType
import android.util.TypedValue
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.example.clickassist.R
import com.example.clickassist.domain.repository.AppSettings
import com.example.clickassist.service.overlay.OverlayAppearance
import com.example.clickassist.service.overlay.OverlaySchemeItem
import com.example.clickassist.service.overlay.OverlayPanelSpec
import com.example.clickassist.service.overlay.OverlayStylable
import com.example.clickassist.service.overlay.OverlayWaitStepItem
import kotlin.math.roundToInt

class OverlaySchemePanelView(
    context: Context,
) : ScrollView(context), OverlayStylable {
    private var appearance: OverlayAppearance =
        OverlayAppearance.fromSettings(context, AppSettings())
    private var lastModel: OverlayPanelSpec.Settings? = null
    private val contentContainer = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(4), dp(4), dp(4), dp(4))
    }
    private val currentNameInput = createTextInput()
    private val saveAsInput = createTextInput()
    private val totalRoundsInput = createNumberInput()
    private val infiniteRoundsCheckBox = CheckBox(context).apply {
        text = context.getString(R.string.task_edit_label_infinite_rounds)
    }
    private val schemesContainer = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
    }
    private val waitStepsContainer = LinearLayout(context).apply {
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

    fun bind(model: OverlayPanelSpec.Settings) {
        lastModel = model
        contentContainer.removeAllViews()
        setBackgroundColor(appearance.panelBackgroundColor)

        currentNameInput.setText(model.currentTaskName)
        saveAsInput.setText(model.saveAsDefaultName)
        totalRoundsInput.setText(model.totalRounds)
        infiniteRoundsCheckBox.isChecked = model.infiniteRounds

        contentContainer.addView(sectionTitle(R.string.overlay_panel_current_scheme))
        contentContainer.addView(currentNameInput, matchWidthParams())
        contentContainer.addView(label(R.string.task_edit_label_total_rounds), topMarginMatchParams())
        contentContainer.addView(totalRoundsInput, matchWidthParams())
        contentContainer.addView(infiniteRoundsCheckBox, topMarginMatchParams())
        contentContainer.addView(
            actionButton(R.string.overlay_panel_save_scheme) {
                Log.i(ACTION_TAG, "save current scheme clicked currentScheme=${model.currentTaskName}")
                model.onSaveCurrent(
                    currentNameInput.text?.toString().orEmpty(),
                    totalRoundsInput.text?.toString().orEmpty(),
                    infiniteRoundsCheckBox.isChecked,
                )
            },
            topMarginMatchParams(),
        )

        contentContainer.addView(sectionTitle(R.string.overlay_panel_scheme_list), topMarginMatchParams())
        contentContainer.addView(schemesContainer, matchWidthParams())
        renderSchemes(model.schemes, model)

        contentContainer.addView(sectionTitle(R.string.overlay_panel_save_as), topMarginMatchParams())
        contentContainer.addView(saveAsInput, matchWidthParams())
        contentContainer.addView(
            actionButton(R.string.overlay_panel_save_as) {
                Log.i(ACTION_TAG, "save as clicked currentScheme=${model.currentTaskName}")
                model.onSaveAs(
                    saveAsInput.text?.toString().orEmpty(),
                    totalRoundsInput.text?.toString().orEmpty(),
                    infiniteRoundsCheckBox.isChecked,
                )
            },
            topMarginMatchParams(),
        )

        contentContainer.addView(sectionTitle(R.string.overlay_panel_wait_steps_title), topMarginMatchParams())
        contentContainer.addView(waitStepsContainer, matchWidthParams())
        renderWaitSteps(model.waitSteps, model)

        contentContainer.addView(
            actionButton(R.string.overlay_action_hide_toolbar) {
                Log.i(ACTION_TAG, "hide toolbar clicked currentScheme=${model.currentTaskName}")
                model.onHideToolbar()
            },
            topMarginMatchParams(),
        ).also { params ->
            (contentContainer.getChildAt(contentContainer.childCount - 1) as? Button)?.isEnabled =
                model.canHideToolbar
        }

        contentContainer.addView(
            actionButton(R.string.overlay_action_close_floating) {
                Log.i(ACTION_TAG, "close floating clicked currentScheme=${model.currentTaskName}")
                model.onCloseFloating()
            },
            topMarginMatchParams(),
        )
    }

    private fun renderSchemes(
        schemes: List<OverlaySchemeItem>,
        model: OverlayPanelSpec.Settings,
    ) {
        schemesContainer.removeAllViews()
        if (schemes.isEmpty()) {
            schemesContainer.addView(bodyText(R.string.overlay_panel_no_tasks))
            return
        }

        schemes.forEach { item ->
            schemesContainer.addView(
                LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(12), dp(10), dp(12), dp(10))
                    setBackgroundColor(
                        if (item.isCurrent) {
                            Color.parseColor("#DBEAFE")
                        } else {
                            Color.parseColor("#F8FAFC")
                        },
                    )

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
                            if (item.isCurrent) {
                                R.string.overlay_panel_scheme_selected
                            } else {
                                R.string.overlay_panel_scheme_switch
                            },
                        ) {
                            Log.i(
                                SCHEME_TAG,
                                "scheme item clicked taskId=${item.taskId} current=${item.isCurrent} name=${item.name}",
                            )
                            model.onSchemeSelected(item.taskId)
                        }.apply {
                            isEnabled = !item.isCurrent
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

    private fun renderWaitSteps(
        waitSteps: List<OverlayWaitStepItem>,
        model: OverlayPanelSpec.Settings,
    ) {
        waitStepsContainer.removeAllViews()
        if (waitSteps.isEmpty()) {
            waitStepsContainer.addView(bodyText(R.string.overlay_panel_no_wait_steps))
            return
        }

        waitSteps.forEach { item ->
            waitStepsContainer.addView(
                LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(12), dp(10), dp(12), dp(10))
                    setBackgroundColor(
                        if (item.isSelected) {
                            Color.parseColor("#E0F2FE")
                        } else {
                            Color.parseColor("#F8FAFC")
                        },
                    )

                    addView(
                        TextView(context).apply {
                            setTextColor(Color.parseColor("#0F172A"))
                            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                            setTypeface(typeface, Typeface.BOLD)
                            text = context.getString(
                                R.string.overlay_panel_wait_step_item_title,
                                item.orderIndex + 1,
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
                            addView(
                                actionButton(R.string.overlay_panel_step_select) {
                                    Log.i(ACTION_TAG, "wait step select clicked stepId=${item.stepId}")
                                    model.onWaitStepSelected(item.stepId)
                                },
                            )
                            addView(
                                actionButton(R.string.common_delete) {
                                    Log.i(ACTION_TAG, "wait step delete clicked stepId=${item.stepId}")
                                    model.onDeleteWaitStep(item.stepId)
                                },
                                LinearLayout.LayoutParams(
                                    ViewGroup.LayoutParams.WRAP_CONTENT,
                                    ViewGroup.LayoutParams.WRAP_CONTENT,
                                ).apply {
                                    marginStart = dp(8)
                                },
                            )
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

    private fun label(labelRes: Int): TextView {
        return TextView(context).apply {
            setTextColor(Color.parseColor("#334155"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            text = context.getString(labelRes)
        }
    }

    private fun bodyText(stringRes: Int): TextView {
        return TextView(context).apply {
            setTextColor(Color.parseColor("#475569"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            text = context.getString(stringRes)
        }
    }

    private fun createTextInput(): EditText {
        return EditText(context).apply {
            setTextColor(Color.parseColor("#0F172A"))
            setHintTextColor(Color.parseColor("#94A3B8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setBackgroundColor(Color.parseColor("#FFFFFF"))
            setPadding(dp(12), dp(10), dp(12), dp(10))
            inputType = InputType.TYPE_CLASS_TEXT
        }
    }

    private fun createNumberInput(): EditText {
        return EditText(context).apply {
            setTextColor(Color.parseColor("#0F172A"))
            setHintTextColor(Color.parseColor("#94A3B8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setBackgroundColor(Color.parseColor("#FFFFFF"))
            setPadding(dp(12), dp(10), dp(12), dp(10))
            inputType = InputType.TYPE_CLASS_NUMBER
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

    private fun topMarginMatchParams(): LinearLayout.LayoutParams {
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

    override fun applyAppearance(appearance: OverlayAppearance) {
        this.appearance = appearance
        lastModel?.let(::bind)
    }

    private companion object {
        const val ACTION_TAG = "OverlayAction"
        const val SCHEME_TAG = "SchemeSelection"
    }
}
