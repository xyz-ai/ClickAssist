package com.example.clickassist.domain.repository

import com.example.clickassist.common.i18n.AppLanguage
import com.example.clickassist.ui.theme.AppThemeMode
import kotlinx.coroutines.flow.Flow

data class AppSettings(
    val languageMode: AppLanguage = AppLanguage.ENGLISH,
    val themeMode: AppThemeMode = AppThemeMode.FOLLOW_SYSTEM,
    val toolbarDefaultExpanded: Boolean = true,
    val showHandleWhenToolbarHidden: Boolean = true,
    val markerSizeDp: Int = 56,
    val swipeLineWidthDp: Int = 10,
    val showMarkerNumbers: Boolean = true,
    val showMarkerCenterCross: Boolean = true,
    val defaultTapDurationMs: Long = 80L,
    val defaultLongPressDurationMs: Long = 600L,
    val defaultSwipeDurationMs: Long = 300L,
    val defaultStepIntervalMs: Long = 300L,
    val defaultTotalRounds: Int = 1,
    val defaultNewStepRepeatCount: Int = 1,
    val localOnlyNoticeAcknowledged: Boolean = false,
    val overlayGuideOpenCount: Int = 0,
    val accessibilityGuideOpenCount: Int = 0,
    val lastEditedTaskId: Long? = null,
    val overlayToolbarX: Int? = null,
    val overlayToolbarY: Int? = null,
)

interface SettingsRepository {
    val settingsFlow: Flow<AppSettings>

    suspend fun setLanguageMode(languageMode: AppLanguage)

    suspend fun setThemeMode(themeMode: AppThemeMode)

    suspend fun setToolbarDefaultExpanded(expanded: Boolean)

    suspend fun setShowHandleWhenToolbarHidden(enabled: Boolean)

    suspend fun setMarkerSizeDp(sizeDp: Int)

    suspend fun setSwipeLineWidthDp(widthDp: Int)

    suspend fun setShowMarkerNumbers(enabled: Boolean)

    suspend fun setShowMarkerCenterCross(enabled: Boolean)

    suspend fun setDefaultTapDurationMs(durationMs: Long)

    suspend fun setDefaultLongPressDurationMs(durationMs: Long)

    suspend fun setDefaultSwipeDurationMs(durationMs: Long)

    suspend fun setDefaultStepIntervalMs(intervalMs: Long)

    suspend fun setDefaultTotalRounds(totalRounds: Int)

    suspend fun setDefaultNewStepRepeatCount(repeatCount: Int)

    suspend fun setLocalOnlyNoticeAcknowledged(acknowledged: Boolean)

    suspend fun markOverlayGuideOpened()

    suspend fun markAccessibilityGuideOpened()

    suspend fun setLastEditedTaskId(taskId: Long)

    suspend fun setOverlayToolbarPosition(
        x: Int,
        y: Int,
    )
}
