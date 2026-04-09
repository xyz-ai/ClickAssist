package com.example.clickassist.service.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class MyAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        activeInstance = this
        _serviceConnected.value = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        clearInstance()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        clearInstance()
        super.onDestroy()
    }

    suspend fun dispatchTap(
        x: Int,
        y: Int,
        durationMs: Long,
    ): Boolean {
        val safeDuration = durationMs.coerceAtLeast(40L)
        val gesture = GestureDescription.Builder()
            .addStroke(
                GestureDescription.StrokeDescription(
                    Path().apply { moveTo(x.toFloat(), y.toFloat()) },
                    0L,
                    safeDuration,
                ),
            )
            .build()

        return dispatchGestureAwait(gesture)
    }

    suspend fun dispatchSwipe(
        startX: Int,
        startY: Int,
        endX: Int,
        endY: Int,
        durationMs: Long,
    ): Boolean {
        val safeDuration = durationMs.coerceAtLeast(120L)
        val gesture = GestureDescription.Builder()
            .addStroke(
                GestureDescription.StrokeDescription(
                    Path().apply {
                        moveTo(startX.toFloat(), startY.toFloat())
                        lineTo(endX.toFloat(), endY.toFloat())
                    },
                    0L,
                    safeDuration,
                ),
            )
            .build()

        return dispatchGestureAwait(gesture)
    }

    suspend fun dispatchLongPress(
        x: Int,
        y: Int,
        durationMs: Long,
    ): Boolean {
        return dispatchTap(
            x = x,
            y = y,
            durationMs = durationMs.coerceAtLeast(500L),
        )
    }

    suspend fun dispatchDoubleTap(
        x: Int,
        y: Int,
        intervalMs: Long = 120L,
    ): Boolean {
        val first = dispatchTap(x = x, y = y, durationMs = 60L)
        if (!first) return false
        return dispatchTap(x = x, y = y, durationMs = intervalMs.coerceAtLeast(60L))
    }

    private suspend fun dispatchGestureAwait(
        gesture: GestureDescription,
    ): Boolean = suspendCancellableCoroutine { continuation ->
        val dispatched = dispatchGesture(
            gesture,
            object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    if (continuation.isActive) {
                        continuation.resume(true)
                    }
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    if (continuation.isActive) {
                        continuation.resume(false)
                    }
                }
            },
            null,
        )

        if (!dispatched && continuation.isActive) {
            continuation.resume(false)
        }
    }

    private fun clearInstance() {
        if (activeInstance === this) {
            activeInstance = null
            _serviceConnected.value = false
        }
    }

    companion object {
        @Volatile
        private var activeInstance: MyAccessibilityService? = null

        private val _serviceConnected = MutableStateFlow(false)
        val serviceConnected: StateFlow<Boolean> = _serviceConnected.asStateFlow()

        fun current(): MyAccessibilityService? = activeInstance

        fun isEnabled(context: Context): Boolean {
            val serviceName = ComponentName(
                context,
                MyAccessibilityService::class.java,
            ).flattenToString()

            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ).orEmpty()

            return enabledServices
                .split(':')
                .any { enabledService ->
                    enabledService.equals(serviceName, ignoreCase = true)
                }
        }
    }
}
