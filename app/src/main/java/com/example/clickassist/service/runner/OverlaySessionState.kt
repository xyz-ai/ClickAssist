package com.example.clickassist.service.runner

import androidx.annotation.StringRes
import com.example.clickassist.R
import com.example.clickassist.service.overlay.OverlayPanelType

enum class OverlayPlacementMode(
    @StringRes val messageRes: Int,
) {
    NONE(R.string.overlay_status_ready),
    PLACE_TAP(R.string.overlay_status_place_tap),
    PLACE_LONG_PRESS(R.string.overlay_status_place_long_press),
    PLACE_SWIPE_START(R.string.overlay_status_place_swipe_start),
    PLACE_SWIPE_END(R.string.overlay_status_place_swipe_end),
}

data class OverlaySessionState(
    val isFloatingModeEnabled: Boolean = false,
    val activeTaskId: Long? = null,
    val activeTaskName: String? = null,
    val isToolbarHidden: Boolean = false,
    val isTargetVisible: Boolean = false,
    val isMultiPointMode: Boolean = false,
    val stepCount: Int = 0,
    val selectedStepOrder: Int? = null,
    val selectedStepActionType: String? = null,
    val selectedStepIsNode: Boolean = false,
    val canDeleteSelected: Boolean = false,
    val hasWaitSteps: Boolean = false,
    val placementMode: OverlayPlacementMode = OverlayPlacementMode.NONE,
    val activePanelType: OverlayPanelType? = null,
    @StringRes
    val statusMessageRes: Int? = null,
)
