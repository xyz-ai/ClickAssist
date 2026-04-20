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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.clickassist.R
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
        val layoutDirection = LocalLayoutDirection.current
        val closeButtonSize = 44.dp
        val closeButtonPadding = 16.dp
        val blockWidthDp = minOf(320.dp, maxWidth - 32.dp)
        var measuredBlockHeightPx by remember { mutableIntStateOf(0) }
        val screenWidthPx = with(density) { maxWidth.toPx() }
        val screenHeightPx = with(density) { maxHeight.toPx() }
        val blockWidthPx = with(density) { blockWidthDp.toPx() }
        val horizontalPaddingPx = with(density) { 16.dp.toPx() }
        val targetMarginPx = with(density) { 16.dp.toPx() }
        val defaultBlockHeightPx = with(density) { 260.dp.toPx() }
        val blockHeightPx = if (measuredBlockHeightPx > 0) {
            measuredBlockHeightPx.toFloat()
        } else {
            defaultBlockHeightPx
        }
        val safeInsets = WindowInsets.safeDrawing
        val safeLeftPx = safeInsets.getLeft(density, layoutDirection).toFloat()
        val safeRightPx = safeInsets.getRight(density, layoutDirection).toFloat()
        val safeTopPx = safeInsets.getTop(density).toFloat()
        val safeBottomPx = safeInsets.getBottom(density).toFloat()
        val safeTopDp = with(density) { safeTopPx.toDp() }
        val safeRightDp = with(density) { safeRightPx.toDp() }
        val closeDescription = stringResource(R.string.tutorial_close)
        val outlineColor = MaterialTheme.colorScheme.primary
        val blockOffset = rememberTutorialBlockOffset(
            targetRect = targetRect,
            screenWidthPx = screenWidthPx,
            screenHeightPx = screenHeightPx,
            blockWidthPx = blockWidthPx,
            blockHeightPx = blockHeightPx,
            horizontalPaddingPx = horizontalPaddingPx,
            targetMarginPx = targetMarginPx,
            safeLeftPx = safeLeftPx,
            safeTopPx = safeTopPx,
            safeRightPx = safeRightPx,
            safeBottomPx = safeBottomPx,
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
                    .padding(
                        top = safeTopDp + closeButtonPadding,
                        end = safeRightDp + closeButtonPadding,
                    )
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
                    .width(blockWidthDp)
                    .zIndex(1f)
                    .onSizeChanged { measuredBlockHeightPx = it.height }
                    .offset {
                        IntOffset(
                            x = blockOffset.first.roundToInt(),
                            y = blockOffset.second.roundToInt(),
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
                    Row(
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
}

@Composable
private fun rememberTutorialBlockOffset(
    targetRect: RectF?,
    screenWidthPx: Float,
    screenHeightPx: Float,
    blockWidthPx: Float,
    blockHeightPx: Float,
    horizontalPaddingPx: Float,
    targetMarginPx: Float,
    safeLeftPx: Float,
    safeTopPx: Float,
    safeRightPx: Float,
    safeBottomPx: Float,
): Pair<Float, Float> {
    val minX = safeLeftPx + horizontalPaddingPx
    val maxX = screenWidthPx - safeRightPx - blockWidthPx - horizontalPaddingPx
    val minY = safeTopPx + horizontalPaddingPx
    val maxY = screenHeightPx - safeBottomPx - blockHeightPx - horizontalPaddingPx

    if (targetRect == null) {
        return Pair(
            clampToBounds(
                value = (screenWidthPx - blockWidthPx) / 2f,
                min = minX,
                max = maxX,
            ),
            clampToBounds(
                value = screenHeightPx * 0.2f,
                min = minY,
                max = maxY,
            ),
        )
    }

    val x = targetRect.centerX() - (blockWidthPx / 2f)
    val belowY = targetRect.bottom + targetMarginPx
    val aboveY = targetRect.top - blockHeightPx - targetMarginPx
    val spaceBelow = maxY - belowY
    val spaceAbove = aboveY - minY
    val y = when {
        belowY <= maxY -> belowY
        aboveY >= minY -> aboveY
        spaceBelow >= spaceAbove -> belowY
        else -> aboveY
    }

    return Pair(
        clampToBounds(
            value = x,
            min = minX,
            max = maxX,
        ),
        clampToBounds(
            value = y,
            min = minY,
            max = maxY,
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
