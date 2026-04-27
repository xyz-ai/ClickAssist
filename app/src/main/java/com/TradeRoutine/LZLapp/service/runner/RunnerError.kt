package com.TradeRoutine.LZLapp.service.runner

sealed interface RunnerError {
    data object NoTaskSelected : RunnerError

    data object OverlayPermissionDenied : RunnerError

    data object AccessibilityDisabled : RunnerError

    data object AccessibilityServiceUnavailable : RunnerError

    data object TapPointNotSet : RunnerError

    data object LongPressPointNotSet : RunnerError

    data object SwipePointNotSet : RunnerError

    data class TapPointOutOfBounds(
        val x: Int?,
        val y: Int?,
        val screenWidth: Int,
        val screenHeight: Int,
    ) : RunnerError

    data class SwipePointOutOfBounds(
        val screenWidth: Int,
        val screenHeight: Int,
    ) : RunnerError

    data object NoExecutableSteps : RunnerError

    data class InvalidRepeatCount(
        val stepIndex: Int,
    ) : RunnerError

    data class InvalidIntervalMs(
        val stepIndex: Int,
    ) : RunnerError

    data class InvalidDurationMs(
        val stepIndex: Int,
    ) : RunnerError

    data object InvalidTotalRounds : RunnerError

    data object GestureDispatchFailed : RunnerError

    data class TaskNotFound(
        val taskId: Long,
    ) : RunnerError

    data class Unknown(
        val rawMessage: String? = null,
    ) : RunnerError
}
