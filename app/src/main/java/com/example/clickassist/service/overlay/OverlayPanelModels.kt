package com.example.clickassist.service.overlay

import androidx.annotation.StringRes
import com.example.clickassist.R
import com.example.clickassist.domain.model.ActionType

enum class OverlayPanelType(
    @StringRes val titleRes: Int,
) {
    SCHEME(R.string.overlay_panel_scheme_title),
    STEP_LIST(R.string.overlay_panel_step_list_title),
    ADD_STEP(R.string.overlay_panel_add_step_title),
    LOOP_SETTINGS(R.string.overlay_panel_loop_settings_title),
    STEP_EDITOR(R.string.overlay_panel_current_step_title),
}

data class OverlaySchemeOption(
    val taskId: Long,
    val name: String,
    val stepCount: Int,
    val isActive: Boolean,
)

data class OverlayStepListItem(
    val stepId: Long,
    val orderIndex: Int,
    val actionType: ActionType,
    val enabled: Boolean,
    val isSelected: Boolean,
    val canMoveUp: Boolean,
    val canMoveDown: Boolean,
)

data class OverlayStepEditorDraft(
    val stepId: Long,
    val orderIndex: Int,
    val actionType: ActionType,
    val enabled: Boolean,
    val x: String,
    val y: String,
    val endX: String,
    val endY: String,
    val intervalMs: String,
    val durationMs: String,
    val repeatCount: String,
    val preDelayMs: String,
    val postDelayMs: String,
)

sealed interface OverlayPanelSpec {
    val type: OverlayPanelType

    @get:StringRes
    val messageRes: Int?

    data class Scheme(
        val currentTaskName: String,
        val saveAsDefaultName: String,
        val tasks: List<OverlaySchemeOption>,
        override val messageRes: Int? = null,
        val onTaskSelected: (Long) -> Unit,
        val onSaveCurrent: (String) -> Unit,
        val onSaveAs: (String) -> Unit,
    ) : OverlayPanelSpec {
        override val type: OverlayPanelType = OverlayPanelType.SCHEME
    }

    data class StepList(
        val items: List<OverlayStepListItem>,
        override val messageRes: Int? = null,
        val onStepSelected: (Long) -> Unit,
        val onDeleteStep: (Long) -> Unit,
        val onMoveUp: (Long) -> Unit,
        val onMoveDown: (Long) -> Unit,
    ) : OverlayPanelSpec {
        override val type: OverlayPanelType = OverlayPanelType.STEP_LIST
    }

    data class AddStep(
        override val messageRes: Int? = null,
        val onAddStep: (ActionType) -> Unit,
    ) : OverlayPanelSpec {
        override val type: OverlayPanelType = OverlayPanelType.ADD_STEP
    }

    data class LoopSettings(
        val totalRounds: String,
        val infiniteRounds: Boolean,
        override val messageRes: Int? = null,
        val onSave: (String, Boolean) -> Unit,
    ) : OverlayPanelSpec {
        override val type: OverlayPanelType = OverlayPanelType.LOOP_SETTINGS
    }

    data class StepEditor(
        val draft: OverlayStepEditorDraft?,
        override val messageRes: Int? = null,
        val onSave: (OverlayStepEditorDraft) -> Unit,
    ) : OverlayPanelSpec {
        override val type: OverlayPanelType = OverlayPanelType.STEP_EDITOR
    }
}
