package com.example.clickassist.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.preferencesDataStore
import com.example.clickassist.domain.repository.AppSettings
import com.example.clickassist.domain.repository.SettingsRepository
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
                localOnlyNoticeAcknowledged = preferences[LOCAL_ONLY_NOTICE_KEY] ?: false,
                overlayGuideOpenCount = preferences[OVERLAY_GUIDE_COUNT_KEY] ?: 0,
                accessibilityGuideOpenCount = preferences[ACCESSIBILITY_GUIDE_COUNT_KEY] ?: 0,
                lastEditedTaskId = preferences[LAST_EDITED_TASK_ID_KEY],
            )
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

    private companion object {
        const val DATASTORE_FILE_NAME = "app_settings"

        val LOCAL_ONLY_NOTICE_KEY = booleanPreferencesKey("local_only_notice_acknowledged")
        val OVERLAY_GUIDE_COUNT_KEY = intPreferencesKey("overlay_guide_open_count")
        val ACCESSIBILITY_GUIDE_COUNT_KEY = intPreferencesKey("accessibility_guide_open_count")
        val LAST_EDITED_TASK_ID_KEY = longPreferencesKey("last_edited_task_id")
    }
}

private val Context.dataStore by preferencesDataStore(name = "app_settings")
