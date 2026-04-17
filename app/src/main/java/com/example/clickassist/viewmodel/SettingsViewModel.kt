package com.example.clickassist.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.clickassist.app.AppContainer
import com.example.clickassist.common.i18n.AppLanguage
import com.example.clickassist.domain.repository.AppSettings
import com.example.clickassist.domain.repository.SettingsRepository
import com.example.clickassist.ui.theme.AppThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
)

sealed interface LanguageChangeDecision {
    data object SkipSameLanguage : LanguageChangeDecision

    data class ApplyLanguage(
        val target: AppLanguage,
    ) : LanguageChangeDecision
}

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    val uiState: StateFlow<SettingsUiState> = settingsRepository.settingsFlow
        .map { SettingsUiState(settings = it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SettingsUiState(),
        )

    suspend fun requestLanguageChange(languageMode: AppLanguage): LanguageChangeDecision {
        val currentLanguage = settingsRepository.settingsFlow.first().languageMode
        Log.i(TAG, "language option clicked current=$currentLanguage target=$languageMode")
        if (currentLanguage == languageMode) {
            Log.i(TAG, "skip same language current=$currentLanguage target=$languageMode")
            return LanguageChangeDecision.SkipSameLanguage
        }

        settingsRepository.setLanguageMode(languageMode)
        Log.i(TAG, "persist new language target=$languageMode")
        return LanguageChangeDecision.ApplyLanguage(target = languageMode)
    }

    fun setThemeMode(themeMode: AppThemeMode) = launchUpdate {
        settingsRepository.setThemeMode(themeMode)
    }

    fun setToolbarDefaultExpanded(expanded: Boolean) = launchUpdate {
        settingsRepository.setToolbarDefaultExpanded(expanded)
    }

    fun setShowHandleWhenToolbarHidden(enabled: Boolean) = launchUpdate {
        settingsRepository.setShowHandleWhenToolbarHidden(enabled)
    }

    fun setMarkerSizeDp(sizeDp: Int) = launchUpdate {
        settingsRepository.setMarkerSizeDp(sizeDp)
    }

    fun setSwipeLineWidthDp(widthDp: Int) = launchUpdate {
        settingsRepository.setSwipeLineWidthDp(widthDp)
    }

    fun setShowMarkerNumbers(enabled: Boolean) = launchUpdate {
        settingsRepository.setShowMarkerNumbers(enabled)
    }

    fun setShowMarkerCenterCross(enabled: Boolean) = launchUpdate {
        settingsRepository.setShowMarkerCenterCross(enabled)
    }

    fun setDefaultTapDurationMs(durationMs: Long) = launchUpdate {
        settingsRepository.setDefaultTapDurationMs(durationMs)
    }

    fun setDefaultLongPressDurationMs(durationMs: Long) = launchUpdate {
        settingsRepository.setDefaultLongPressDurationMs(durationMs)
    }

    fun setDefaultSwipeDurationMs(durationMs: Long) = launchUpdate {
        settingsRepository.setDefaultSwipeDurationMs(durationMs)
    }

    fun setDefaultStepIntervalMs(intervalMs: Long) = launchUpdate {
        settingsRepository.setDefaultStepIntervalMs(intervalMs)
    }

    fun setDefaultTotalRounds(totalRounds: Int) = launchUpdate {
        settingsRepository.setDefaultTotalRounds(totalRounds)
    }

    fun setDefaultNewStepRepeatCount(repeatCount: Int) = launchUpdate {
        settingsRepository.setDefaultNewStepRepeatCount(repeatCount)
    }

    suspend fun setHasSeenOnboarding(seen: Boolean) {
        settingsRepository.setHasSeenOnboarding(seen)
    }

    suspend fun setHasSeenFloatingTutorial(seen: Boolean) {
        settingsRepository.setHasSeenFloatingTutorial(seen)
    }

    private fun launchUpdate(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    companion object {
        private const val TAG = "SettingsLanguage"

        fun factory(appContainer: AppContainer): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return SettingsViewModel(
                        settingsRepository = appContainer.settingsRepository,
                    ) as T
                }
            }
        }
    }
}
