package com.example.clickassist.service.overlay

import androidx.annotation.StringRes
import com.example.clickassist.domain.model.ScreenPoint

class OverlayController(
    private val toolbarController: OverlayToolbarController,
    private val targetController: OverlayTargetController,
) {
    private var toolbarCallbacks: OverlayToolbarCallbacks = OverlayToolbarCallbacks()
    private var onPointChangedCallback: ((ScreenPoint) -> Unit)? = null
    private var onDragEndCallback: ((ScreenPoint) -> Unit)? = null

    private var coordinateRecorderCallback: (() -> Unit)? = null
    private var jsonExportCallback: (() -> Unit)? = null
    private var promoteOtherAppsCallback: (() -> Unit)? = null
    private var adSlotEntryCallback: (() -> Unit)? = null

    fun hasPermission(): Boolean = toolbarController.hasPermission() && targetController.hasPermission()

    fun isTargetVisible(): Boolean = targetController.isTargetVisible()

    fun currentTargetPoint(): ScreenPoint? = targetController.currentPoint()

    fun resolveInitialPoint(
        preferredX: Int?,
        preferredY: Int?,
    ): ScreenPoint {
        return targetController.resolveInitialPoint(
            preferredX = preferredX,
            preferredY = preferredY,
        )
    }

    fun bindToolbarCallbacks(
        callbacks: OverlayToolbarCallbacks,
    ) {
        toolbarCallbacks = callbacks
    }

    suspend fun showFloatingMode(
        initialPoint: ScreenPoint,
        targetVisible: Boolean,
        toolbarUiState: OverlayToolbarUiState,
        onPointChanged: (ScreenPoint) -> Unit,
        onDragEnd: (ScreenPoint) -> Unit,
    ): Boolean {
        if (!hasPermission()) {
            return false
        }

        onPointChangedCallback = onPointChanged
        onDragEndCallback = onDragEnd

        val toolbarShown = toolbarController.show(
            uiState = toolbarUiState,
            callbacks = toolbarCallbacks,
        )
        if (!toolbarShown) {
            return false
        }

        val targetUpdated = setTargetVisibility(
            isVisible = targetVisible,
            point = initialPoint,
        )
        if (!targetUpdated) {
            toolbarController.hide()
            return false
        }

        return true
    }

    suspend fun updateToolbarState(
        uiState: OverlayToolbarUiState,
    ) {
        toolbarController.updateState(uiState)
    }

    suspend fun setTargetVisibility(
        isVisible: Boolean,
        point: ScreenPoint? = targetController.currentPoint(),
    ): Boolean {
        if (!isVisible) {
            targetController.hideTarget(clearPoint = false)
            return true
        }

        val resolvedPoint = point ?: return false
        val onPointChanged = onPointChangedCallback ?: return false
        val onDragEnd = onDragEndCallback ?: return false
        return targetController.showTarget(
            initialPoint = resolvedPoint,
            onPointChanged = onPointChanged,
            onDragEnd = onDragEnd,
        )
    }

    suspend fun updateTarget(
        point: ScreenPoint,
    ): Boolean {
        return targetController.updateTarget(point)
    }

    suspend fun hideFloatingMode(
        clearTargetPoint: Boolean = true,
    ) {
        toolbarController.hide()
        targetController.hideTarget(clearPoint = clearTargetPoint)
    }

    fun showMessage(
        @StringRes messageRes: Int,
    ) {
        toolbarController.showMessage(messageRes)
    }

    fun release() {
        toolbarController.release()
        targetController.release()
        onPointChangedCallback = null
        onDragEndCallback = null
        coordinateRecorderCallback = null
        jsonExportCallback = null
        promoteOtherAppsCallback = null
        adSlotEntryCallback = null
    }

    fun bindFutureHooks(
        onCoordinateRecorderRequested: (() -> Unit)? = null,
        onJsonExportRequested: (() -> Unit)? = null,
        onPromoteOtherAppsRequested: (() -> Unit)? = null,
        onAdSlotRequested: (() -> Unit)? = null,
    ) {
        coordinateRecorderCallback = onCoordinateRecorderRequested
        jsonExportCallback = onJsonExportRequested
        promoteOtherAppsCallback = onPromoteOtherAppsRequested
        adSlotEntryCallback = onAdSlotRequested
    }

    fun requestCoordinateRecorder() {
        coordinateRecorderCallback?.invoke()
    }

    fun requestJsonExport() {
        jsonExportCallback?.invoke()
    }

    fun requestPromoteOtherApps() {
        promoteOtherAppsCallback?.invoke()
    }

    fun requestAdSlotEntry() {
        adSlotEntryCallback?.invoke()
    }
}
