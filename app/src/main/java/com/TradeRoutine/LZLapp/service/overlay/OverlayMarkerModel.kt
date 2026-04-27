package com.TradeRoutine.LZLapp.service.overlay

import com.TradeRoutine.LZLapp.domain.model.ActionType
import com.TradeRoutine.LZLapp.domain.model.ScreenPoint

data class OverlayMarkerModel(
    val markerId: String,
    val stepId: Long,
    val orderIndex: Int,
    val label: String,
    val actionType: ActionType,
    val point: ScreenPoint,
    val role: OverlayMarkerRole,
    val isSelected: Boolean = false,
    val connectedMarkerId: String? = null,
)

enum class OverlayMarkerRole {
    PRIMARY,
    START,
    END,
}
