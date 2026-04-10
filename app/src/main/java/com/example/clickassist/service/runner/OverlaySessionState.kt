package com.example.clickassist.service.runner

data class OverlaySessionState(
    val isFloatingModeEnabled: Boolean = false,
    val activeTaskId: Long? = null,
    val isTargetVisible: Boolean = false,
)
