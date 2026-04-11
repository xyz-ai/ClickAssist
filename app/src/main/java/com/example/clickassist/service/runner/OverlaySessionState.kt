package com.example.clickassist.service.runner

import androidx.annotation.StringRes

data class OverlaySessionState(
    val isFloatingModeEnabled: Boolean = false,
    val activeTaskId: Long? = null,
    val isTargetVisible: Boolean = false,
    val isMultiPointMode: Boolean = false,
    val stepCount: Int = 0,
    val selectedStepOrder: Int? = null,
    val selectedStepActionType: String? = null,
    @StringRes
    val statusMessageRes: Int? = null,
)
