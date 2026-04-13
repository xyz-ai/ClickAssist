package com.example.clickassist.service.overlay

import androidx.annotation.StringRes
import com.example.clickassist.domain.model.ScreenPoint

class OverlayController(
    private val toolbarController: OverlayToolbarController,
    private val targetController: OverlayTargetController,
    private val panelController: OverlayPanelController,
) {
    private var toolbarCallbacks: OverlayToolbarCallbacks = OverlayToolbarCallbacks()
    private var onMarkerChangedCallback: ((String, ScreenPoint) -> Unit)? = null
    private var onMarkerDragEndCallback: ((String, ScreenPoint) -> Unit)? = null
    private var onMarkerSelectedCallback: ((String) -> Unit)? = null

    private var coordinateRecorderCallback: (() -> Unit)? = null
    private var jsonExportCallback: (() -> Unit)? = null
    private var promoteOtherAppsCallback: (() -> Unit)? = null
    private var adSlotEntryCallback: (() -> Unit)? = null

    fun hasPermission(): Boolean =
        toolbarController.hasPermission() &&
            targetController.hasPermission() &&
            panelController.hasPermission()

    fun isTargetVisible(): Boolean = targetController.isTargetVisible()

    fun currentMarkerPoints(): Map<String, ScreenPoint> = targetController.currentMarkerPoints()

    fun currentMarkerPoint(markerId: String): ScreenPoint? = targetController.currentMarkerPoint(markerId)

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
        initialMarkers: List<OverlayMarkerModel>,
        targetVisible: Boolean,
        toolbarUiState: OverlayToolbarUiState,
        onMarkerChanged: (String, ScreenPoint) -> Unit,
        onMarkerDragEnd: (String, ScreenPoint) -> Unit,
        onMarkerSelected: (String) -> Unit,
    ): Boolean {
        if (!hasPermission()) {
            return false
        }

        onMarkerChangedCallback = onMarkerChanged
        onMarkerDragEndCallback = onMarkerDragEnd
        onMarkerSelectedCallback = onMarkerSelected

        val toolbarShown = toolbarController.show(
            uiState = toolbarUiState,
            callbacks = toolbarCallbacks,
        )
        if (!toolbarShown) {
            return false
        }

        val targetUpdated = setTargetVisibility(
            isVisible = targetVisible,
            markers = initialMarkers,
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

    suspend fun showPanel(
        spec: OverlayPanelSpec,
        onCloseRequested: () -> Unit,
    ): Boolean {
        return panelController.showPanel(spec, onCloseRequested)
    }

    suspend fun hidePanel() {
        panelController.hidePanel()
    }

    suspend fun setTargetVisibility(
        isVisible: Boolean,
        markers: List<OverlayMarkerModel> = emptyList(),
    ): Boolean {
        if (!isVisible) {
            targetController.hideTargets(clearPoints = false)
            return true
        }

        val onMarkerChanged = onMarkerChangedCallback ?: return false
        val onMarkerDragEnd = onMarkerDragEndCallback ?: return false
        val onMarkerSelected = onMarkerSelectedCallback ?: return false
        return targetController.showMarkers(
            markers = markers,
            onMarkerChanged = onMarkerChanged,
            onMarkerDragEnd = onMarkerDragEnd,
            onMarkerSelected = onMarkerSelected,
        )
    }

    suspend fun updateTargets(
        markers: List<OverlayMarkerModel>,
    ): Boolean {
        return targetController.updateMarkers(markers)
    }

    suspend fun setTargetTouchEnabled(
        enabled: Boolean,
    ): Boolean {
        return targetController.setTouchEnabled(enabled)
    }

    suspend fun hideFloatingMode(
        clearTargetPoint: Boolean = true,
    ) {
        panelController.hidePanel()
        toolbarController.hide()
        targetController.hideTargets(clearPoints = clearTargetPoint)
    }

    fun showMessage(
        @StringRes messageRes: Int,
    ) {
        toolbarController.showMessage(messageRes)
    }

    fun release() {
        panelController.release()
        toolbarController.release()
        targetController.release()
        onMarkerChangedCallback = null
        onMarkerDragEndCallback = null
        onMarkerSelectedCallback = null
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
