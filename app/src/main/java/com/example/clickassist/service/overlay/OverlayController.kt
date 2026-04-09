package com.example.clickassist.service.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.example.clickassist.service.runner.RunnerProgress
import com.example.clickassist.service.runner.RunnerState

class OverlayController(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val windowManager = appContext.getSystemService(WindowManager::class.java)

    private var rootView: View? = null
    private var stateTextView: TextView? = null
    private var progressTextView: TextView? = null

    private var coordinateRecorderCallback: (() -> Unit)? = null
    private var jsonExportCallback: (() -> Unit)? = null
    private var promoteOtherAppsCallback: (() -> Unit)? = null
    private var adSlotEntryCallback: (() -> Unit)? = null

    fun hasPermission(): Boolean = Settings.canDrawOverlays(appContext)

    fun show(
        state: RunnerState,
        progress: RunnerProgress?,
    ) {
        if (!hasPermission()) return

        ensureOverlayView()
        update(state, progress)

        val view = rootView ?: return
        if (view.parent == null) {
            windowManager.addView(view, createLayoutParams())
        }
    }

    fun update(
        state: RunnerState,
        progress: RunnerProgress?,
    ) {
        if (!hasPermission()) return

        ensureOverlayView()
        stateTextView?.text = "State: $state"
        progressTextView?.text = buildString {
            append("Progress: ")
            if (progress == null) {
                append("idle")
            } else {
                append("task#${progress.taskId} ")
                append("round ${progress.currentRoundIndex + 1} ")
                append("step ${progress.currentStepIndex + 1} ")
                append("repeat ${progress.currentStepRepeatIndex + 1}")
            }
        }
    }

    fun hide() {
        val view = rootView ?: return
        if (view.parent != null) {
            windowManager.removeView(view)
        }
    }

    fun release() {
        hide()
        rootView = null
        stateTextView = null
        progressTextView = null
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

    private fun ensureOverlayView() {
        if (rootView != null) return

        val container = LinearLayout(appContext).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#CC101418"))
            setPadding(24, 20, 24, 20)
        }

        stateTextView = TextView(appContext).apply {
            setTextColor(Color.WHITE)
            textSize = 14f
        }
        progressTextView = TextView(appContext).apply {
            setTextColor(Color.parseColor("#FFD7E3EC"))
            textSize = 12f
        }

        container.addView(stateTextView)
        container.addView(progressTextView)
        rootView = container
    }

    private fun createLayoutParams(): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 24
            y = 160
        }
    }
}
