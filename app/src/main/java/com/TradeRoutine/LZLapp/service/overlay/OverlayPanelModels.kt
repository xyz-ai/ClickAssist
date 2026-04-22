package com.TradeRoutine.LZLapp.service.overlay

import androidx.annotation.StringRes
import com.TradeRoutine.LZLapp.R
import com.TradeRoutine.LZLapp.domain.model.ActionType

enum class OverlayPanelType(
    @StringRes val titleRes: Int,
) {
    SETTINGS(R.string.overlay_panel_settings_title),
    ADD_NODE(R.string.overlay_panel_add_node_title),
    STEP_EDITOR(R.string.overlay_panel_current_step_title),
}

data class OverlayWaitStepItem(
    val stepId: Long,
    val orderIndex: Int,
    val enabled: Boolean,
    val isSelected: Boolean,
)

data class OverlaySchemeItem(
    val taskId: Long,
    val name: String,
    val stepCount: Int,
    val isCurrent: Boolean,
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

    data class Settings(
        val currentSchemeId: Long?,
        val currentTaskName: String,
        val saveAsDefaultName: String,
        val totalRounds: String,
        val infiniteRounds: Boolean,
        val canHideToolbar: Boolean,
        val schemes: List<OverlaySchemeItem>,
        val waitSteps: List<OverlayWaitStepItem>,
        override val messageRes: Int? = null,
        val onSaveCurrent: (String, String, Boolean) -> Unit,
        val onSaveAs: (String, String, Boolean) -> Unit,
        val onSchemeSelected: (Long) -> Unit,
        val onWaitStepSelected: (Long) -> Unit,
        val onDeleteWaitStep: (Long) -> Unit,
        val onHideToolbar: () -> Unit,
        val onCloseFloating: () -> Unit,
    ) : OverlayPanelSpec {
        override val type: OverlayPanelType = OverlayPanelType.SETTINGS
    }

    data class AddNode(
        override val messageRes: Int? = null,
        val onAddStep: (ActionType) -> Unit,
    ) : OverlayPanelSpec {
        override val type: OverlayPanelType = OverlayPanelType.ADD_NODE
    }

    data class StepEditor(
        val draft: OverlayStepEditorDraft?,
        override val messageRes: Int? = null,
        val onSave: (OverlayStepEditorDraft) -> Unit,
        val onDeleteStep: ((Long) -> Unit)? = null,
    ) : OverlayPanelSpec {
        override val type: OverlayPanelType = OverlayPanelType.STEP_EDITOR
    }
}
