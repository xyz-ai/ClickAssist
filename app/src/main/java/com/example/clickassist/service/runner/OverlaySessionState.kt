package com.example.clickassist.service.runner

import androidx.annotation.StringRes
import com.example.clickassist.service.overlay.OverlayPanelType

data class OverlaySessionState(
    val isFloatingModeEnabled: Boolean = false,
    val activeTaskId: Long? = null,
    val activeTaskName: String? = null,
    val isTargetVisible: Boolean = false,
    val isMultiPointMode: Boolean = false,
    val stepCount: Int = 0,
    val selectedStepOrder: Int? = null,
    val selectedStepActionType: String? = null,
    val activePanelType: OverlayPanelType? = null,
    @StringRes
    val statusMessageRes: Int? = null,
)
