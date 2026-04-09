package com.example.clickassist.viewmodel

import android.content.Context
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.clickassist.app.AppContainer
import com.example.clickassist.domain.repository.SettingsRepository
import com.example.clickassist.service.accessibility.MyAccessibilityService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PermissionGuideUiState(
    val hasOverlayPermission: Boolean = false,
    val hasAccessibilityPermission: Boolean = false,
    val localOnlyNoticeAcknowledged: Boolean = false,
    val overlayGuideOpenCount: Int = 0,
    val accessibilityGuideOpenCount: Int = 0,
) {
    val allRequiredPermissionsGranted: Boolean
        get() = hasOverlayPermission && hasAccessibilityPermission
}

class PermissionGuideViewModel(
    private val appContext: Context,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    private val permissionSnapshot = MutableStateFlow(PermissionSnapshot())

    val uiState: StateFlow<PermissionGuideUiState> = combine(
        permissionSnapshot,
        settingsRepository.settingsFlow,
    ) { snapshot, settings ->
        PermissionGuideUiState(
            hasOverlayPermission = snapshot.hasOverlayPermission,
            hasAccessibilityPermission = snapshot.hasAccessibilityPermission,
            localOnlyNoticeAcknowledged = settings.localOnlyNoticeAcknowledged,
            overlayGuideOpenCount = settings.overlayGuideOpenCount,
            accessibilityGuideOpenCount = settings.accessibilityGuideOpenCount,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PermissionGuideUiState(),
    )

    init {
        refreshPermissionStatus()
    }

    fun refreshPermissionStatus() {
        permissionSnapshot.value = PermissionSnapshot(
            hasOverlayPermission = Settings.canDrawOverlays(appContext),
            hasAccessibilityPermission = MyAccessibilityService.isEnabled(appContext),
        )
    }

    fun markOverlaySettingsOpened() {
        viewModelScope.launch {
            settingsRepository.markOverlayGuideOpened()
            settingsRepository.setLocalOnlyNoticeAcknowledged(true)
        }
    }

    fun markAccessibilitySettingsOpened() {
        viewModelScope.launch {
            settingsRepository.markAccessibilityGuideOpened()
            settingsRepository.setLocalOnlyNoticeAcknowledged(true)
        }
    }

    fun acknowledgeLocalOnlyNotice() {
        viewModelScope.launch {
            settingsRepository.setLocalOnlyNoticeAcknowledged(true)
        }
    }

    companion object {
        fun factory(appContainer: AppContainer): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return PermissionGuideViewModel(
                        appContext = appContainer.appContext,
                        settingsRepository = appContainer.settingsRepository,
                    ) as T
                }
            }
        }
    }

    private data class PermissionSnapshot(
        val hasOverlayPermission: Boolean = false,
        val hasAccessibilityPermission: Boolean = false,
    )
}
