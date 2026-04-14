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

    fun currentMarkerGeometry(markerId: String): MarkerGeometrySnapshot? =
        targetController.currentMarkerGeometry(markerId)

    fun currentScreenGeometry(): ScreenGeometrySnapshot = targetController.currentScreenGeometry()

    fun resolveInitialPoint(
        preferredX: Int?,
        preferredY: Int?,
    ): ScreenPoint {
        return targetController.resolveInitialPoint(preferredX, preferredY)
    }

    fun bindToolbarCallbacks(
        callbacks: OverlayToolbarCallbacks,
    ) {
        Log.i(TAG, "bindToolbarCallbacks")
        toolbarCallbacks = callbacks
    }

    fun bindHandleExpandCallback(
        callback: () -> Unit,
    ) {
        Log.i(TAG, "bindHandleExpandCallback")
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
            Log.e(TAG, "showFloatingMode failed reason=overlay_permission_denied")
            return false
        }
        lastToolbarUiState = toolbarUiState
        Log.i(
            TAG,
            "showFloatingMode markers=${initialMarkers.size} targetVisible=$targetVisible placementMode=$placementMode taskId=${toolbarUiState.activeTaskId} taskName=${toolbarUiState.activeTaskName}",
        )

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
            Log.e(TAG, "showFloatingMode failed reason=target_layer_not_shown")
            return false
        }

        handleController.hide()
        val toolbarShown = toolbarController.show(
            uiState = toolbarUiState,
            callbacks = toolbarCallbacks,
        )
        if (!toolbarShown) {
            targetController.hideLayer(clearPoints = false)
            Log.e(TAG, "showFloatingMode failed reason=toolbar_not_shown")
            return false
        }

        syncTouchExclusionRects()
        Log.i(TAG, "showFloatingMode success")

        return true
    }

    suspend fun updateToolbarState(
        uiState: OverlayToolbarUiState,
    ) {
        lastToolbarUiState = uiState
        Log.i(
            TAG,
            "updateToolbarState taskId=${uiState.activeTaskId} taskName=${uiState.activeTaskName} runnerState=${uiState.runnerState} targetVisible=${uiState.isTargetVisible}",
        )
        toolbarController.updateState(uiState)
        syncTouchExclusionRects()
    }

    suspend fun showPanel(
        spec: OverlayPanelSpec,
        onCloseRequested: () -> Unit,
    ): Boolean {
        Log.i(TAG, "showPanel type=${spec.type}")
        val shown = panelController.showPanel(spec, onCloseRequested)
        Log.i(TAG, "showPanel result type=${spec.type} success=$shown")
        syncTouchExclusionRects()
        return shown
    }

    suspend fun hidePanel() {
        Log.i(TAG, "hidePanel")
        panelController.hidePanel()
        syncTouchExclusionRects()
    }

    suspend fun updateTargetLayer(
        markers: List<OverlayMarkerModel>,
        isVisible: Boolean,
        placementMode: OverlayPlacementMode,
    ): Boolean {
        Log.i(
            TAG,
            "updateTargetLayer markers=${markers.size} visible=$isVisible placementMode=$placementMode",
        )
        val updated = targetController.updateLayer(
            markers = markers,
            areMarkersVisible = isVisible,
            placementMode = placementMode,
        )
        Log.i(TAG, "updateTargetLayer result success=$updated")
        return updated
    }

    suspend fun setTargetVisibility(
        isVisible: Boolean,
    ): Boolean {
        Log.i(TAG, "setTargetVisibility visible=$isVisible")
        val updated = targetController.setMarkerVisibility(isVisible)
        Log.i(TAG, "setTargetVisibility result success=$updated")
        return updated
    }

    suspend fun setPlacementMode(
        placementMode: OverlayPlacementMode,
    ): Boolean {
        Log.i(TAG, "setPlacementMode placementMode=$placementMode")
        val updated = targetController.setPlacementMode(placementMode)
        Log.i(TAG, "setPlacementMode result success=$updated")
        return updated
    }

    suspend fun setTargetTouchEnabled(
        enabled: Boolean,
    ): Boolean {
        Log.i(TAG, "setTargetTouchEnabled enabled=$enabled")
        val updated = targetController.setTouchEnabled(enabled)
        Log.i(TAG, "setTargetTouchEnabled result success=$updated")
        return updated
    }

    suspend fun hideFloatingMode(
        clearTargetPoint: Boolean = true,
    ) {
        Log.i(TAG, "hideFloatingMode clearTargetPoint=$clearTargetPoint")
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
        Log.i(TAG, "hideToolbarToHandle success")
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
        Log.i(TAG, "showToolbarFromHandle success")
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
