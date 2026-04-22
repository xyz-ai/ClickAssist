package com.TradeRoutine.LZLapp.service.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

class MyAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        activeInstance = this
        _serviceConnected.value = true
        Log.i(TAG, "Accessibility service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        Log.i(TAG, "Accessibility service unbound")
        clearInstance()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        Log.i(TAG, "Accessibility service destroyed")
        clearInstance()
        super.onDestroy()
    }

    suspend fun dispatchTap(
        x: Int,
        y: Int,
        durationMs: Long,
    ): Boolean {
        val safeDuration = durationMs.coerceAtLeast(40L)
        Log.i(TAG, "dispatchTap requested x=$x y=$y durationMs=$safeDuration")
        logDisplayGeometry("dispatchTap")

        return withContext(Dispatchers.Main.immediate) {
            val gesture = GestureDescription.Builder()
                .addStroke(
                    GestureDescription.StrokeDescription(
                        Path().apply {
                            moveTo(x.toFloat(), y.toFloat())
                        },
                        0L,
                        safeDuration,
                    ),
                )
                .build()

            dispatchGestureAwait(
                gesture = gesture,
                debugLabel = "tap(x=$x,y=$y,durationMs=$safeDuration)",
            )
        }
    }

    suspend fun dispatchSwipe(
        startX: Int,
        startY: Int,
        endX: Int,
        endY: Int,
        durationMs: Long,
    ): Boolean {
        val safeDuration = durationMs.coerceAtLeast(120L)
        Log.i(
            TAG,
            "dispatchSwipe requested start=($startX,$startY) end=($endX,$endY) durationMs=$safeDuration",
        )
        logDisplayGeometry("dispatchSwipe")

        return withContext(Dispatchers.Main.immediate) {
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

            dispatchGestureAwait(
                gesture = gesture,
                debugLabel = "swipe(start=($startX,$startY),end=($endX,$endY),durationMs=$safeDuration)",
            )
        }
    }

    suspend fun dispatchLongPress(
        x: Int,
        y: Int,
        durationMs: Long,
    ): Boolean {
        val safeDuration = durationMs.coerceAtLeast(500L)
        Log.i(TAG, "dispatchLongPress requested x=$x y=$y durationMs=$safeDuration")
        logDisplayGeometry("dispatchLongPress")
        return dispatchTap(
            x = x,
            y = y,
            durationMs = safeDuration,
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
        debugLabel: String,
    ): Boolean = suspendCancellableCoroutine { continuation ->
        lastGestureDispatchStatus = GestureDispatchStatus.STARTED
        try {
            val dispatched = dispatchGesture(
                gesture,
                object : GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        lastGestureDispatchStatus = GestureDispatchStatus.COMPLETED
                        Log.i(TAG, "dispatchGesture completed label=$debugLabel")
                        if (continuation.isActive) {
                            continuation.resume(true)
                        }
                    }

                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        lastGestureDispatchStatus = GestureDispatchStatus.CANCELLED
                        Log.w(TAG, "dispatchGesture cancelled label=$debugLabel")
                        if (continuation.isActive) {
                            continuation.resume(false)
                        }
                    }
                },
                null,
            )

            Log.i(TAG, "dispatchGesture returned=$dispatched label=$debugLabel")
            if (!dispatched) {
                lastGestureDispatchStatus = GestureDispatchStatus.REJECTED
                if (continuation.isActive) {
                    continuation.resume(false)
                }
            }
        } catch (throwable: Throwable) {
            lastGestureDispatchStatus = GestureDispatchStatus.ERROR
            Log.e(TAG, "dispatchGesture threw label=$debugLabel", throwable)
            if (continuation.isActive) {
                continuation.resume(false)
            }
        }
    }

    private fun clearInstance() {
        if (activeInstance === this) {
            activeInstance = null
            _serviceConnected.value = false
        }
    }

    private fun logDisplayGeometry(prefix: String) {
        val metrics = resources.displayMetrics
        Log.i(
            TAG,
            "$prefix displayMetrics width=${metrics.widthPixels} height=${metrics.heightPixels} densityDpi=${metrics.densityDpi}",
        )
    }

    companion object {
        @Volatile
        private var activeInstance: MyAccessibilityService? = null

        @Volatile
        private var lastGestureDispatchStatus: GestureDispatchStatus = GestureDispatchStatus.IDLE

        private val _serviceConnected = MutableStateFlow(false)
        val serviceConnected: StateFlow<Boolean> = _serviceConnected.asStateFlow()

        fun current(): MyAccessibilityService? = activeInstance

        fun lastDispatchStatus(): GestureDispatchStatus = lastGestureDispatchStatus

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

        private const val TAG = "ClickAssistA11y"
    }

    enum class GestureDispatchStatus {
        IDLE,
        STARTED,
        COMPLETED,
        CANCELLED,
        REJECTED,
        ERROR,
    }
}
