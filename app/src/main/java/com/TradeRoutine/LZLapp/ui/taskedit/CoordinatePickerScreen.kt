package com.TradeRoutine.LZLapp.ui.taskedit

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.TradeRoutine.LZLapp.R
import com.TradeRoutine.LZLapp.domain.model.ScreenPoint
import kotlin.math.roundToInt

@Composable
fun CoordinatePickerScreen(
    initialPoint: ScreenPoint?,
    onCancel: () -> Unit,
    onConfirm: (ScreenPoint) -> Unit,
) {
    val markerSize = 56.dp
    val markerSizePx = with(LocalDensity.current) { markerSize.roundToPx() }
    val markerHalfSizePx = markerSizePx / 2

    var containerSizePx by remember { mutableStateOf(IntSize.Zero) }
    var currentPointPx by remember { mutableStateOf<ScreenPoint?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .onSizeChanged { newSize ->
                containerSizePx = newSize
                val centeredPoint = ScreenPoint(
                    x = newSize.width / 2,
                    y = newSize.height / 2,
                )
                currentPointPx = clampPointToBounds(
                    point = currentPointPx ?: initialPoint ?: centeredPoint,
                    containerSize = newSize,
                    markerHalfSizePx = markerHalfSizePx,
                )
            },
    ) {
        val resolvedPoint = currentPointPx ?: initialPoint ?: ScreenPoint(
            x = containerSizePx.width / 2,
            y = containerSizePx.height / 2,
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(containerSizePx, markerHalfSizePx) {
                    detectDragGestures(
                        onDragStart = { startOffset ->
                            currentPointPx = clampPointFromOffset(
                                offset = startOffset,
                                containerSize = containerSizePx,
                                markerHalfSizePx = markerHalfSizePx,
                            )
                        },
                        onDrag = { change, _ ->
                            currentPointPx = clampPointFromOffset(
                                offset = change.position,
                                containerSize = containerSizePx,
                                markerHalfSizePx = markerHalfSizePx,
                            )
                        },
                    )
                },
        )

        ScreenPointMarker(
            point = resolvedPoint,
            modifier = Modifier.align(Alignment.TopStart),
            markerSize = markerSize,
            outerColor = Color.White,
            innerColor = Color(0xFF1F2937),
        )

        Card(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.coordinate_picker_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(
                        R.string.coordinate_picker_position,
                        resolvedPoint.x,
                        resolvedPoint.y,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    OutlinedButton(onClick = onCancel) {
                        Text(text = stringResource(R.string.common_cancel))
                    }
                    Button(onClick = { onConfirm(resolvedPoint) }) {
                        Text(text = stringResource(R.string.common_confirm))
                    }
                }
            }
        }
    }
}

@Composable
fun ScreenPointMarker(
    point: ScreenPoint,
    modifier: Modifier = Modifier,
    markerSize: Dp = 40.dp,
    outerColor: Color = Color.Red,
    innerColor: Color = Color.White,
) {
    val markerSizePx = with(LocalDensity.current) { markerSize.roundToPx() }

    Box(
        modifier = modifier
            .offset {
                IntOffset(
                    x = point.x - markerSizePx / 2,
                    y = point.y - markerSizePx / 2,
                )
            }
            .size(markerSize),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val drawSize = size
            val center = Offset(drawSize.width / 2f, drawSize.height / 2f)
            drawCircle(
                color = outerColor,
                radius = drawSize.minDimension / 2f,
            )
            drawCircle(
                color = innerColor,
                radius = drawSize.minDimension / 2.7f,
            )
            drawLine(
                color = outerColor,
                start = Offset(center.x, 0f),
                end = Offset(center.x, drawSize.height),
                strokeWidth = 3f,
            )
            drawLine(
                color = outerColor,
                start = Offset(0f, center.y),
                end = Offset(drawSize.width, center.y),
                strokeWidth = 3f,
            )
        }
    }
}

private fun clampPointFromOffset(
    offset: Offset,
    containerSize: IntSize,
    markerHalfSizePx: Int,
): ScreenPoint {
    return clampPointToBounds(
        point = ScreenPoint(
            x = offset.x.roundToInt(),
            y = offset.y.roundToInt(),
        ),
        containerSize = containerSize,
        markerHalfSizePx = markerHalfSizePx,
    )
}

private fun clampPointToBounds(
    point: ScreenPoint,
    containerSize: IntSize,
    markerHalfSizePx: Int,
): ScreenPoint {
    val minX = markerHalfSizePx.coerceAtLeast(0)
    val minY = markerHalfSizePx.coerceAtLeast(0)
    val maxX = (containerSize.width - markerHalfSizePx).coerceAtLeast(minX)
    val maxY = (containerSize.height - markerHalfSizePx).coerceAtLeast(minY)
    return ScreenPoint(
        x = point.x.coerceIn(minX, maxX),
        y = point.y.coerceIn(minY, maxY),
    )
}
