package com.example.clickassist.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.clickassist.common.i18n.AppLanguage
import com.example.clickassist.domain.repository.AppSettings
import com.example.clickassist.domain.repository.SettingsRepository
import com.example.clickassist.ui.theme.AppThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

class DataStoreSettingsRepository(
    context: Context,
) : SettingsRepository {
    private val appContext = context.applicationContext

    private val dataStore = appContext.dataStore

    override val settingsFlow: Flow<AppSettings> = dataStore.data
        .catch { throwable ->
            if (throwable is IOException) {
                emit(preferencesOf())
            } else {
                throw throwable
            }
        }
        .map { preferences ->
            AppSettings(
                languageMode = AppLanguage.fromStorage(preferences[LANGUAGE_MODE_KEY]),
                themeMode = AppThemeMode.fromStorage(preferences[THEME_MODE_KEY]),
                hasSeenOnboarding = preferences[HAS_SEEN_ONBOARDING_KEY] ?: false,
                hasSeenFloatingTutorial = preferences[HAS_SEEN_FLOATING_TUTORIAL_KEY] ?: false,
                toolbarDefaultExpanded = preferences[TOOLBAR_DEFAULT_EXPANDED_KEY] ?: true,
                showHandleWhenToolbarHidden = preferences[SHOW_HANDLE_WHEN_HIDDEN_KEY] ?: true,
                markerSizeDp = preferences[MARKER_SIZE_DP_KEY] ?: 56,
                swipeLineWidthDp = preferences[SWIPE_LINE_WIDTH_DP_KEY] ?: 10,
                showMarkerNumbers = preferences[SHOW_MARKER_NUMBERS_KEY] ?: true,
                showMarkerCenterCross = preferences[SHOW_MARKER_CENTER_CROSS_KEY] ?: true,
                defaultTapDurationMs = preferences[DEFAULT_TAP_DURATION_MS_KEY] ?: 80L,
                defaultLongPressDurationMs = preferences[DEFAULT_LONG_PRESS_DURATION_MS_KEY] ?: 600L,
                defaultSwipeDurationMs = preferences[DEFAULT_SWIPE_DURATION_MS_KEY] ?: 300L,
                defaultStepIntervalMs = preferences[DEFAULT_STEP_INTERVAL_MS_KEY] ?: 300L,
                defaultTotalRounds = preferences[DEFAULT_TOTAL_ROUNDS_KEY] ?: 1,
                defaultNewStepRepeatCount = preferences[DEFAULT_NEW_STEP_REPEAT_COUNT_KEY] ?: 1,
                localOnlyNoticeAcknowledged = preferences[LOCAL_ONLY_NOTICE_KEY] ?: false,
                overlayGuideOpenCount = preferences[OVERLAY_GUIDE_COUNT_KEY] ?: 0,
                accessibilityGuideOpenCount = preferences[ACCESSIBILITY_GUIDE_COUNT_KEY] ?: 0,
                lastEditedTaskId = preferences[LAST_EDITED_TASK_ID_KEY],
                overlayToolbarX = preferences[OVERLAY_TOOLBAR_X_KEY],
                overlayToolbarY = preferences[OVERLAY_TOOLBAR_Y_KEY],
            )
        }

    override suspend fun setLanguageMode(languageMode: AppLanguage) {
        dataStore.edit { preferences ->
            preferences[LANGUAGE_MODE_KEY] = languageMode.storageValue
        }
    }

    override suspend fun setThemeMode(themeMode: AppThemeMode) {
        dataStore.edit { preferences ->
            preferences[THEME_MODE_KEY] = themeMode.storageValue
        }
    }

    override suspend fun setHasSeenOnboarding(seen: Boolean) {
        dataStore.edit { preferences ->
            preferences[HAS_SEEN_ONBOARDING_KEY] = seen
        }
    }

    override suspend fun setHasSeenFloatingTutorial(seen: Boolean) {
        dataStore.edit { preferences ->
            preferences[HAS_SEEN_FLOATING_TUTORIAL_KEY] = seen
        }
    }

    override suspend fun setToolbarDefaultExpanded(expanded: Boolean) {
        dataStore.edit { preferences ->
            preferences[TOOLBAR_DEFAULT_EXPANDED_KEY] = expanded
        }
    }

    override suspend fun setShowHandleWhenToolbarHidden(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[SHOW_HANDLE_WHEN_HIDDEN_KEY] = enabled
        }
    }

    override suspend fun setMarkerSizeDp(sizeDp: Int) {
        dataStore.edit { preferences ->
            preferences[MARKER_SIZE_DP_KEY] = sizeDp
        }
    }

    override suspend fun setSwipeLineWidthDp(widthDp: Int) {
        dataStore.edit { preferences ->
            preferences[SWIPE_LINE_WIDTH_DP_KEY] = widthDp
        }
    }

    override suspend fun setShowMarkerNumbers(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[SHOW_MARKER_NUMBERS_KEY] = enabled
        }
    }

    override suspend fun setShowMarkerCenterCross(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[SHOW_MARKER_CENTER_CROSS_KEY] = enabled
        }
    }

    override suspend fun setDefaultTapDurationMs(durationMs: Long) {
        dataStore.edit { preferences ->
            preferences[DEFAULT_TAP_DURATION_MS_KEY] = durationMs
        }
    }

    override suspend fun setDefaultLongPressDurationMs(durationMs: Long) {
        dataStore.edit { preferences ->
            preferences[DEFAULT_LONG_PRESS_DURATION_MS_KEY] = durationMs
        }
    }

    override suspend fun setDefaultSwipeDurationMs(durationMs: Long) {
        dataStore.edit { preferences ->
            preferences[DEFAULT_SWIPE_DURATION_MS_KEY] = durationMs
        }
    }

    override suspend fun setDefaultStepIntervalMs(intervalMs: Long) {
        dataStore.edit { preferences ->
            preferences[DEFAULT_STEP_INTERVAL_MS_KEY] = intervalMs
        }
    }

    override suspend fun setDefaultTotalRounds(totalRounds: Int) {
        dataStore.edit { preferences ->
            preferences[DEFAULT_TOTAL_ROUNDS_KEY] = totalRounds
        }
    }

    override suspend fun setDefaultNewStepRepeatCount(repeatCount: Int) {
        dataStore.edit { preferences ->
            preferences[DEFAULT_NEW_STEP_REPEAT_COUNT_KEY] = repeatCount
        }
    }

    override suspend fun setLocalOnlyNoticeAcknowledged(acknowledged: Boolean) {
        dataStore.edit { preferences ->
            preferences[LOCAL_ONLY_NOTICE_KEY] = acknowledged
        }
    }

    override suspend fun markOverlayGuideOpened() {
        dataStore.edit { preferences ->
            preferences[OVERLAY_GUIDE_COUNT_KEY] = (preferences[OVERLAY_GUIDE_COUNT_KEY] ?: 0) + 1
        }
    }

    override suspend fun markAccessibilityGuideOpened() {
        dataStore.edit { preferences ->
            preferences[ACCESSIBILITY_GUIDE_COUNT_KEY] =
                (preferences[ACCESSIBILITY_GUIDE_COUNT_KEY] ?: 0) + 1
        }
    }

    override suspend fun setLastEditedTaskId(taskId: Long) {
        dataStore.edit { preferences ->
            preferences[LAST_EDITED_TASK_ID_KEY] = taskId
        }
    }

    override suspend fun setOverlayToolbarPosition(
        x: Int,
        y: Int,
    ) {
        dataStore.edit { preferences ->
            preferences[OVERLAY_TOOLBAR_X_KEY] = x
            preferences[OVERLAY_TOOLBAR_Y_KEY] = y
        }
    }

    private companion object {
        val LANGUAGE_MODE_KEY = stringPreferencesKey("language_mode")
        val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
        val HAS_SEEN_ONBOARDING_KEY = booleanPreferencesKey("has_seen_onboarding")
        val HAS_SEEN_FLOATING_TUTORIAL_KEY = booleanPreferencesKey("has_seen_floating_tutorial")
        val TOOLBAR_DEFAULT_EXPANDED_KEY = booleanPreferencesKey("toolbar_default_expanded")
        val SHOW_HANDLE_WHEN_HIDDEN_KEY = booleanPreferencesKey("show_handle_when_hidden")
        val MARKER_SIZE_DP_KEY = intPreferencesKey("marker_size_dp")
        val SWIPE_LINE_WIDTH_DP_KEY = intPreferencesKey("swipe_line_width_dp")
        val SHOW_MARKER_NUMBERS_KEY = booleanPreferencesKey("show_marker_numbers")
        val SHOW_MARKER_CENTER_CROSS_KEY = booleanPreferencesKey("show_marker_center_cross")
        val DEFAULT_TAP_DURATION_MS_KEY = longPreferencesKey("default_tap_duration_ms")
        val DEFAULT_LONG_PRESS_DURATION_MS_KEY = longPreferencesKey("default_long_press_duration_ms")
        val DEFAULT_SWIPE_DURATION_MS_KEY = longPreferencesKey("default_swipe_duration_ms")
        val DEFAULT_STEP_INTERVAL_MS_KEY = longPreferencesKey("default_step_interval_ms")
        val DEFAULT_TOTAL_ROUNDS_KEY = intPreferencesKey("default_total_rounds")
        val DEFAULT_NEW_STEP_REPEAT_COUNT_KEY = intPreferencesKey("default_new_step_repeat_count")
        val LOCAL_ONLY_NOTICE_KEY = booleanPreferencesKey("local_only_notice_acknowledged")
        val OVERLAY_GUIDE_COUNT_KEY = intPreferencesKey("overlay_guide_open_count")
        val ACCESSIBILITY_GUIDE_COUNT_KEY = intPreferencesKey("accessibility_guide_open_count")
        val LAST_EDITED_TASK_ID_KEY = longPreferencesKey("last_edited_task_id")
        val OVERLAY_TOOLBAR_X_KEY = intPreferencesKey("overlay_toolbar_x")
        val OVERLAY_TOOLBAR_Y_KEY = intPreferencesKey("overlay_toolbar_y")
    }
}

private val Context.dataStore by preferencesDataStore(name = "app_settings")
