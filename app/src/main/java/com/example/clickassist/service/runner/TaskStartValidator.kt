package com.example.clickassist.service.runner

import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager
import com.example.clickassist.data.local.entity.ActionStepEntity
import com.example.clickassist.data.local.entity.TaskWithSteps

class TaskStartValidator(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val windowManager = appContext.getSystemService(WindowManager::class.java)

    fun validateFloatingMode(
        taskWithSteps: TaskWithSteps,
    ): RunnerError? {
        val enabledTapStep = taskWithSteps.steps
            .filter { step ->
                step.enabled && step.actionType == ActionStepEntity.ACTION_TAP
            }
            .sortedBy { step -> step.orderIndex }
            .firstOrNull()
            ?: return RunnerError.NoExecutableSteps

        if (enabledTapStep.x == null || enabledTapStep.y == null) {
            return RunnerError.TapPointNotSet
        }

        return null
    }

    fun validateStart(
        taskWithSteps: TaskWithSteps,
        isAccessibilityEnabled: Boolean,
    ): RunnerError? {
        if (!isAccessibilityEnabled) {
            return RunnerError.AccessibilityDisabled
        }

        val task = taskWithSteps.task
        if (!task.infiniteRounds && task.totalRounds <= 0) {
            return RunnerError.InvalidTotalRounds
        }

        val enabledSteps = taskWithSteps.steps
            .filter { it.enabled }
            .sortedBy { it.orderIndex }

        if (enabledSteps.isEmpty()) {
            return RunnerError.NoExecutableSteps
        }

        val screenBounds = getScreenBounds()

        enabledSteps.forEachIndexed { index, step ->
            if (step.repeatCount <= 0) {
                return RunnerError.InvalidRepeatCount(stepIndex = index)
            }

            if (step.intervalMs < 1L) {
                return RunnerError.InvalidIntervalMs(stepIndex = index)
            }

            if (step.actionType == ActionStepEntity.ACTION_TAP) {
                val x = step.x
                val y = step.y
                if (x == null || y == null) {
                    return RunnerError.TapPointNotSet
                }
                if (x !in 0 until screenBounds.width || y !in 0 until screenBounds.height) {
                    return RunnerError.TapPointOutOfBounds(
                        x = x,
                        y = y,
                        screenWidth = screenBounds.width,
                        screenHeight = screenBounds.height,
                    )
                }
            }
        }

        return null
    }

    private fun getScreenBounds(): ScreenBounds {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds: Rect? = windowManager?.currentWindowMetrics?.bounds
            if (bounds != null) {
                return ScreenBounds(
                    width = bounds.width().coerceAtLeast(1),
                    height = bounds.height().coerceAtLeast(1),
                )
            }
        }

        @Suppress("DEPRECATION")
        val metrics = DisplayMetrics().apply {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                windowManager?.defaultDisplay?.getRealMetrics(this)
            }
        }

        val width = metrics.widthPixels
            .takeIf { it > 0 }
            ?: appContext.resources.displayMetrics.widthPixels
        val height = metrics.heightPixels
            .takeIf { it > 0 }
            ?: appContext.resources.displayMetrics.heightPixels

        return ScreenBounds(
            width = width.coerceAtLeast(1),
            height = height.coerceAtLeast(1),
        )
    }

    private data class ScreenBounds(
        val width: Int,
        val height: Int,
    )
}
