package com.TradeRoutine.LZLapp.ui.tutorial

import android.graphics.RectF
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.TradeRoutine.LZLapp.R

@Composable
fun TutorialHighlightOverlay(
    targetRect: RectF?,
    modifier: Modifier = Modifier,
) {
    val outlineColor = MaterialTheme.colorScheme.primary

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer(alpha = 0.99f)
            .background(Color.Transparent),
    ) {
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(Color.Black.copy(alpha = 0.72f))
            targetRect?.let { rect ->
                drawRoundRect(
                    color = Color.Transparent,
                    topLeft = androidx.compose.ui.geometry.Offset(rect.left, rect.top),
                    size = androidx.compose.ui.geometry.Size(rect.width(), rect.height()),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(28f, 28f),
                    blendMode = BlendMode.Clear,
                )
                drawRoundRect(
                    color = outlineColor,
                    topLeft = androidx.compose.ui.geometry.Offset(rect.left, rect.top),
                    size = androidx.compose.ui.geometry.Size(rect.width(), rect.height()),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(28f, 28f),
                    style = Stroke(width = 6f, cap = StrokeCap.Round),
                )
            }
        }
    }
}

@Composable
fun TutorialControlsBlock(
    step: TutorialStep,
    stepIndex: Int,
    totalSteps: Int,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    onDone: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    onSizeChanged: (IntSize) -> Unit = {},
) {
    Card(
        modifier = modifier
            .widthIn(min = 280.dp, max = 340.dp)
            .onSizeChanged(onSizeChanged),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surface)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(step.titleRes),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(step.descriptionRes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                val closeDescription = stringResource(R.string.tutorial_close)
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.semantics {
                        contentDescription = closeDescription
                    },
                ) {
                    Text(
                        text = stringResource(R.string.tutorial_close_symbol),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            Text(
                text = stringResource(
                    R.string.tutorial_progress,
                    stepIndex + 1,
                    totalSteps,
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )

            TutorialActionRow(
                stepIndex = stepIndex,
                totalSteps = totalSteps,
                onBack = onBack,
                onNext = onNext,
                onSkip = onSkip,
                onDone = onDone,
            )
        }
    }
}

@Composable
fun TutorialOverlay(
    step: TutorialStep,
    targetRect: RectF?,
    stepIndex: Int,
    totalSteps: Int,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    onDone: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        TutorialHighlightOverlay(targetRect = targetRect)
        TutorialControlsBlock(
            step = step,
            stepIndex = stepIndex,
            totalSteps = totalSteps,
            onBack = onBack,
            onNext = onNext,
            onSkip = onSkip,
            onDone = onDone,
            onClose = onClose,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(16.dp),
        )
    }
}

@Composable
private fun TutorialActionRow(
    stepIndex: Int,
    totalSteps: Int,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    onDone: () -> Unit,
) {
    val isFirstStep = stepIndex == 0
    val isFinalStep = stepIndex == totalSteps - 1

    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!isFirstStep) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.weight(1f),
            ) {
                Text(text = stringResource(R.string.tutorial_back))
            }
        }
        if (!isFinalStep) {
            OutlinedButton(
                onClick = onSkip,
                modifier = Modifier.weight(1f),
            ) {
                Text(text = stringResource(R.string.tutorial_skip))
            }
        }
        if (isFinalStep) {
            Button(
                onClick = onDone,
                modifier = Modifier.weight(1f),
            ) {
                Text(text = stringResource(R.string.tutorial_done))
            }
        } else {
            Button(
                onClick = onNext,
                modifier = Modifier.weight(1f),
            ) {
                Text(text = stringResource(R.string.tutorial_next))
            }
        }
    }
}
