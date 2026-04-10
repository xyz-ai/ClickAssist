package com.example.clickassist.domain.repository

import kotlinx.coroutines.flow.Flow

data class AppSettings(
    val localOnlyNoticeAcknowledged: Boolean = false,
    val overlayGuideOpenCount: Int = 0,
    val accessibilityGuideOpenCount: Int = 0,
    val lastEditedTaskId: Long? = null,
    val overlayToolbarX: Int? = null,
    val overlayToolbarY: Int? = null,
)

interface SettingsRepository {
    val settingsFlow: Flow<AppSettings>

    suspend fun setLocalOnlyNoticeAcknowledged(acknowledged: Boolean)

    suspend fun markOverlayGuideOpened()

    suspend fun markAccessibilityGuideOpened()

    suspend fun setLastEditedTaskId(taskId: Long)

    suspend fun setOverlayToolbarPosition(
        x: Int,
        y: Int,
    )
}
