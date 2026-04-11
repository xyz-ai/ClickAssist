package com.example.clickassist.service.runner

import androidx.annotation.StringRes
import com.example.clickassist.R

object RunnerErrorMessageMapper {
    @StringRes
    fun map(error: RunnerError?): Int? {
        return when (error) {
            null -> null
            RunnerError.NoTaskSelected -> R.string.error_no_active_task
            RunnerError.OverlayPermissionDenied -> R.string.error_overlay_permission_denied
            RunnerError.AccessibilityDisabled -> R.string.error_accessibility_disabled
            RunnerError.AccessibilityServiceUnavailable -> R.string.error_accessibility_service_unavailable
            RunnerError.TapPointNotSet -> R.string.error_tap_point_not_set
            is RunnerError.TapPointOutOfBounds -> R.string.error_tap_point_out_of_bounds
            RunnerError.NoExecutableSteps -> R.string.error_no_executable_steps
            is RunnerError.InvalidRepeatCount -> R.string.error_invalid_repeat_count
            is RunnerError.InvalidIntervalMs -> R.string.error_invalid_interval_ms
            RunnerError.InvalidTotalRounds -> R.string.error_invalid_total_rounds
            RunnerError.GestureDispatchFailed -> R.string.error_gesture_dispatch_failed
            is RunnerError.TaskNotFound -> R.string.error_task_not_found
            is RunnerError.Unknown -> R.string.error_unknown
        }
    }
}
