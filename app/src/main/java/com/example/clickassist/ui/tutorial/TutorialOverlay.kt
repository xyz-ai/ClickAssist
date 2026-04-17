package com.example.clickassist.ui.tutorial

import android.graphics.RectF
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.clickassist.R
import kotlin.math.max
import kotlin.math.roundToInt

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
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent),
    ) {
        val density = LocalDensity.current
        val closeButtonSize = 44.dp
        val closeButtonPadding = 16.dp
        val cardWidthDp = minOf(280.dp, maxWidth - 32.dp)
        var measuredCardHeightPx by remember { mutableIntStateOf(0) }
        var measuredActionBarHeightPx by remember { mutableIntStateOf(0) }
        val screenWidthPx = with(density) { maxWidth.toPx() }
        val screenHeightPx = with(density) { maxHeight.toPx() }
        val cardWidthPx = with(density) { cardWidthDp.toPx() }
        val horizontalPaddingPx = with(density) { 16.dp.toPx() }
        val topPaddingPx = with(density) { 20.dp.toPx() }
        val topControlsReservedPx = with(density) { (closeButtonSize + closeButtonPadding * 2).toPx() }
        val defaultCardHeightPx = with(density) { 172.dp.toPx() }
        val cardHeightPx = if (measuredCardHeightPx > 0) {
            measuredCardHeightPx.toFloat()
        } else {
            defaultCardHeightPx
        }
        val bottomControlsReservedPx = max(
            with(density) { 124.dp.toPx() }.roundToInt(),
            measuredActionBarHeightPx + with(density) { 28.dp.toPx() }.roundToInt(),
        ).toFloat()
        val closeDescription = stringResource(R.string.tutorial_close)
        val outlineColor = MaterialTheme.colorScheme.primary
        val cardOffset = rememberTutorialCardOffset(
            targetRect = targetRect,
            placement = step.placement,
            screenWidthPx = screenWidthPx,
            screenHeightPx = screenHeightPx,
            cardWidthPx = cardWidthPx,
            cardHeightPx = cardHeightPx,
            horizontalPaddingPx = horizontalPaddingPx,
            topPaddingPx = topPaddingPx,
            topControlsReservedPx = topControlsReservedPx,
            bottomControlsReservedPx = bottomControlsReservedPx,
        )

        Box(
            modifier = Modifier
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

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(0f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    ),
            )

            TextButton(
                onClick = onClose,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .zIndex(2f)
                    .padding(top = closeButtonPadding, end = closeButtonPadding)
                    .height(closeButtonSize)
                    .semantics {
                        contentDescription = closeDescription
                    },
            ) {
                Text(
                    text = stringResource(R.string.tutorial_close_symbol),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            Card(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .width(cardWidthDp)
                    .zIndex(1f)
                    .onSizeChanged { measuredCardHeightPx = it.height }
                    .offset {
                        IntOffset(
                            x = cardOffset.first.roundToInt(),
                            y = cardOffset.second.roundToInt(),
                        )
                    },
                shape = RoundedCornerShape(24.dp),
            ) {
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
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
                    Text(
                        text = stringResource(
                            R.string.tutorial_progress,
                            stepIndex + 1,
                            totalSteps,
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .zIndex(3f)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 20.dp),
                shape = RoundedCornerShape(24.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onSizeChanged { measuredActionBarHeightPx = it.height }
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val isFirstStep = stepIndex == 0
                    val isFinalStep = stepIndex == totalSteps - 1
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
        }
    }
}

@Composable
private fun rememberTutorialCardOffset(
    targetRect: RectF?,
    placement: TutorialPlacement,
    screenWidthPx: Float,
    screenHeightPx: Float,
    cardWidthPx: Float,
    cardHeightPx: Float,
    horizontalPaddingPx: Float,
    topPaddingPx: Float,
    topControlsReservedPx: Float,
    bottomControlsReservedPx: Float,
): Pair<Float, Float> {
    if (targetRect == null) {
        return Pair(
            clampToBounds(
                value = (screenWidthPx - cardWidthPx) / 2f,
                min = horizontalPaddingPx,
                max = screenWidthPx - cardWidthPx - horizontalPaddingPx,
            ),
            clampToBounds(
                value = screenHeightPx * 0.2f,
                min = topControlsReservedPx.coerceAtLeast(topPaddingPx),
                max = screenHeightPx - cardHeightPx - bottomControlsReservedPx,
            ),
        )
    }

    val x = when (placement) {
        TutorialPlacement.LEFT -> {
            targetRect.left - cardWidthPx - horizontalPaddingPx
        }

        TutorialPlacement.RIGHT -> {
            targetRect.right + horizontalPaddingPx
        }

        TutorialPlacement.ABOVE,
        TutorialPlacement.BELOW,
        -> {
            targetRect.centerX() - (cardWidthPx / 2f)
        }
    }

    val y = when (placement) {
        TutorialPlacement.ABOVE -> {
            targetRect.top - cardHeightPx - horizontalPaddingPx
        }

        TutorialPlacement.BELOW -> {
            targetRect.bottom + horizontalPaddingPx
        }

        TutorialPlacement.LEFT,
        TutorialPlacement.RIGHT,
        -> {
            targetRect.centerY() - (cardHeightPx / 2f)
        }
    }

    return Pair(
        clampToBounds(
            value = x,
            min = horizontalPaddingPx,
            max = screenWidthPx - cardWidthPx - horizontalPaddingPx,
        ),
        clampToBounds(
            value = y,
            min = topControlsReservedPx.coerceAtLeast(topPaddingPx),
            max = screenHeightPx - cardHeightPx - bottomControlsReservedPx,
        ),
    )
}

private fun clampToBounds(
    value: Float,
    min: Float,
    max: Float,
): Float {
    val resolvedMax = max.coerceAtLeast(min)
    return value.coerceIn(min, resolvedMax)
}
