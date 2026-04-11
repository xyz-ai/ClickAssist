package com.example.clickassist.service.overlay

import com.example.clickassist.domain.model.ActionType
import com.example.clickassist.domain.model.ScreenPoint

data class OverlayMarkerModel(
    val markerId: String,
    val stepId: Long,
    val orderIndex: Int,
    val label: String,
    val actionType: ActionType,
    val point: ScreenPoint,
    val role: OverlayMarkerRole,
    val isSelected: Boolean = false,
)

enum class OverlayMarkerRole {
    PRIMARY,
    START,
    END,
}
