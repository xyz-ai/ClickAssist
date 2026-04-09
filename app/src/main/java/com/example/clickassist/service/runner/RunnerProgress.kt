package com.example.clickassist.service.runner

data class RunnerProgress(
    val taskId: Long,
    val currentRoundIndex: Int,
    val currentStepIndex: Int,
    val currentStepRepeatIndex: Int,
)
