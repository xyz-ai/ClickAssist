package com.example.clickassist.service.overlay

import android.graphics.Rect
import android.util.Log
import androidx.annotation.StringRes
import com.example.clickassist.domain.model.ScreenPoint
import com.example.clickassist.service.runner.OverlayPlacementMode

class OverlayController(
    private val toolbarController: OverlayToolbarController,
    private val targetController: OverlayTargetController,
    private val panelController: OverlayPanelController,
    private val handleController: OverlayHandleController,
) {
    private var toolbarCallbacks: OverlayToolbarCallbacks = OverlayToolbarCallbacks()
    private var handleExpandCallback: (() -> Unit)? = null
    private var lastToolbarUiState: OverlayToolbarUiState = OverlayToolbarUiState(
        runnerState = com.example.clickassist.service.runner.RunnerState.IDLE,
        isTargetVisible = false,
    )
    private var onBackgroundTapCallback: ((ScreenPoint) -> Unit)? = null
    private var onMarkerChangedCallback: ((String, ScreenPoint) -> Unit)? = null
    private var onMarkerDragEndCallback: ((String, ScreenPoint) -> Unit)? = null
    private var onMarkerSelectedCallback: ((String) -> Unit)? = null

    private var coordinateRecorderCallback: (() -> Unit)? = null
    private var jsonExportCallback: (() -> Unit)? = null
    private var promoteOtherAppsCallback: (() -> Unit)? = null
    private var adSlotEntryCallback: (() -> Unit)? = null

    init {
        toolbarController.onBoundsChanged = { syncTouchExclusionRects() }
        panelController.onBoundsChanged = { syncTouchExclusionRects() }
        handleController.onBoundsChanged = { syncTouchExclusionRects() }
    }

    fun hasPermission(): Boolean =
        toolbarController.hasPermission() &&
            targetController.hasPermission() &&
            panelController.hasPermission() &&
            handleController.hasPermission()

    fun isTargetVisible(): Boolean = targetController.isTargetVisible()

    fun currentMarkerPoints(): Map<String, ScreenPoint> = targetController.currentMarkerPoints()

    fun currentMarkerPoint(markerId: String): ScreenPoint? = targetController.currentMarkerPoint(markerId)

    fun resolveInitialPoint(
        preferredX: Int?,
        preferredY: Int?,
    ): ScreenPoint {
        return targetController.resolveInitialPoint(preferredX, preferredY)
    }

    fun bindToolbarCallbacks(
        callbacks: OverlayToolbarCallbacks,
    ) {
        toolbarCallbacks = callbacks
    }

    fun bindHandleExpandCallback(
        callback: () -> Unit,
    ) {
        handleExpandCallback = callback
    }

    suspend fun showFloatingMode(
        initialMarkers: List<OverlayMarkerModel>,
        targetVisible: Boolean,
        placementMode: OverlayPlacementMode,
        toolbarUiState: OverlayToolbarUiState,
        onBackgroundTap: (ScreenPoint) -> Unit,
        onMarkerChanged: (String, ScreenPoint) -> Unit,
        onMarkerDragEnd: (String, ScreenPoint) -> Unit,
        onMarkerSelected: (String) -> Unit,
    ): Boolean {
        if (!hasPermission()) {
            return false
        }
        lastToolbarUiState = toolbarUiState

        onBackgroundTapCallback = onBackgroundTap
        onMarkerChangedCallback = onMarkerChanged
        onMarkerDragEndCallback = onMarkerDragEnd
        onMarkerSelectedCallback = onMarkerSelected

        val layerShown = targetController.showLayer(
            markers = initialMarkers,
            areMarkersVisible = targetVisible,
            placementMode = placementMode,
            onBackgroundTap = onBackgroundTap,
            onMarkerChanged = onMarkerChanged,
            onMarkerDragEnd = onMarkerDragEnd,
            onMarkerSelected = onMarkerSelected,
        )
        if (!layerShown) {
            return false
        }

        handleController.hide()
        val toolbarShown = toolbarController.show(
            uiState = toolbarUiState,
            callbacks = toolbarCallbacks,
        )
        if (!toolbarShown) {
            targetController.hideLayer(clearPoints = false)
            return false
        }

        syncTouchExclusionRects()

        return true
    }

    suspend fun updateToolbarState(
        uiState: OverlayToolbarUiState,
    ) {
        lastToolbarUiState = uiState
        toolbarController.updateState(uiState)
        syncTouchExclusionRects()
    }

    suspend fun showPanel(
        spec: OverlayPanelSpec,
        onCloseRequested: () -> Unit,
    ): Boolean {
        val shown = panelController.showPanel(spec, onCloseRequested)
        syncTouchExclusionRects()
        return shown
    }

    suspend fun hidePanel() {
        panelController.hidePanel()
        syncTouchExclusionRects()
    }

    suspend fun updateTargetLayer(
        markers: List<OverlayMarkerModel>,
        isVisible: Boolean,
        placementMode: OverlayPlacementMode,
    ): Boolean {
        return targetController.updateLayer(
            markers = markers,
            areMarkersVisible = isVisible,
            placementMode = placementMode,
        )
    }

    suspend fun setTargetVisibility(
        isVisible: Boolean,
    ): Boolean {
        return targetController.setMarkerVisibility(isVisible)
    }

    suspend fun setPlacementMode(
        placementMode: OverlayPlacementMode,
    ): Boolean {
        return targetController.setPlacementMode(placementMode)
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
        handleController.hide()
        targetController.hideLayer(clearPoints = clearTargetPoint)
        syncTouchExclusionRects()
    }

    suspend fun hideToolbarToHandle(): Boolean {
        Log.i(TAG, "hideToolbarToHandle requested")
        val anchor = toolbarController.currentBounds()
        val handleShown = handleController.show(
            preferredX = anchor?.left,
            preferredY = anchor?.top,
            onExpandRequested = {
                Log.i(TAG, "handle expand clicked")
                handleExpandCallback?.invoke()
            },
        )
        if (!handleShown) {
            Log.e(TAG, "hideToolbarToHandle failed reason=handle_not_shown")
            return false
        }
        toolbarController.hide()
        syncTouchExclusionRects()
        return true
    }

    suspend fun showToolbarFromHandle(): Boolean {
        Log.i(TAG, "showToolbarFromHandle requested")
        val shown = toolbarController.show(
            uiState = lastToolbarUiState,
            callbacks = toolbarCallbacks,
        )
        if (!shown) {
            Log.e(TAG, "showToolbarFromHandle failed reason=toolbar_not_shown")
            return false
        }
        handleController.hide()
        syncTouchExclusionRects()
        return true
    }

    fun showMessage(
        @StringRes messageRes: Int,
    ) {
        toolbarController.showMessage(messageRes)
    }

    fun release() {
        panelController.release()
        toolbarController.release()
        handleController.release()
        targetController.release()
        onBackgroundTapCallback = null
        onMarkerChangedCallback = null
        onMarkerDragEndCallback = null
        onMarkerSelectedCallback = null
        coordinateRecorderCallback = null
        jsonExportCallback = null
        promoteOtherAppsCallback = null
        adSlotEntryCallback = null
    }

    private fun syncTouchExclusionRects() {
        targetController.setTouchExclusionRects(
            listOfNotNull(
                toolbarController.currentBounds(),
                panelController.currentBounds(),
                handleController.currentBounds(),
            ).map(::Rect),
        )
    }

    private companion object {
        const val TAG = "OverlayAction"
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
