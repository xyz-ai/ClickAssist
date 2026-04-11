package com.example.clickassist.service.runner

import androidx.annotation.StringRes

data class OverlaySessionState(
    val isFloatingModeEnabled: Boolean = false,
    val activeTaskId: Long? = null,
    val isTargetVisible: Boolean = false,
    @StringRes
    val statusMessageRes: Int? = null,
)
