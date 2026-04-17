package com.example.clickassist.ui.tutorial

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

@Composable
fun OverlayTutorialHost(
    tutorialController: TutorialController,
    steps: List<TutorialStep>,
    stepIndex: Int,
    onStepIndexChange: (Int) -> Unit,
    onStepChanged: (Int, TutorialStep) -> Unit,
    onSkip: () -> Unit,
    onDone: () -> Unit,
    onClose: () -> Unit,
) {
    val anchors by tutorialController.anchors.collectAsState()
    val currentStep = steps[stepIndex]
    val targetRect = anchors[currentStep.key]

    LaunchedEffect(stepIndex, currentStep) {
        onStepChanged(stepIndex, currentStep)
    }

    TutorialOverlay(
        step = currentStep,
        targetRect = targetRect,
        stepIndex = stepIndex,
        totalSteps = steps.size,
        onBack = {
            if (stepIndex > 0) {
                onStepIndexChange(stepIndex - 1)
            }
        },
        onNext = {
            if (stepIndex < steps.lastIndex) {
                onStepIndexChange(stepIndex + 1)
            }
        },
        onSkip = onSkip,
        onDone = onDone,
        onClose = onClose,
    )
}
