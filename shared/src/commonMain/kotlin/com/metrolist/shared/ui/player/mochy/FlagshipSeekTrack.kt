// Ported from Mochy Music by realidkroo. Licensed under GPL-3.0.

package com.metrolist.shared.ui.player.mochy

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

private const val SWEEP_MILLIS = 1500L
private val SweepEasing = CubicBezierEasing(0.45f, 0f, 0.25f, 1f)

@Composable
fun FlagshipSeekTrack(
    fraction: () -> Float,
    interaction: MutableInteractionSource,
    loading: Boolean,
    active: Boolean,
    contentColor: Color = Color.White,
) {
    val pressed by interaction.collectIsPressedAsState()
    val dragged by interaction.collectIsDraggedAsState()
    val opening by animateFloatAsState(
        targetValue = if (pressed || dragged) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 520f),
        label = "flagshipSeekExpand",
    )
    val sweepAlpha by animateFloatAsState(
        targetValue = if (loading) 1f else 0f,
        animationSpec = tween(if (loading) 320 else 220),
        label = "flagshipSeekLoading",
    )
    val showSweep = loading || sweepAlpha > 0f
    var frameMillis by remember { mutableLongStateOf(0L) }
    LaunchedEffect(active, showSweep) {
        if (!active || !showSweep) return@LaunchedEffect
        while (true) withFrameMillis { frameMillis = it }
    }
    Canvas(Modifier.fillMaxWidth().height(16.dp)) {
        val stroke = (7f + 7f * opening).dp.toPx().coerceAtMost(size.minDimension)
        val inset = stroke / 2f
        val position = fraction().coerceIn(0f, 1f)
        scale(scaleX = if (layoutDirection == LayoutDirection.Rtl) -1f else 1f, scaleY = 1f) {
            drawLine(
                contentColor.copy(alpha = 0.24f),
                Offset(inset, center.y),
                Offset(size.width - inset, center.y),
                stroke,
                StrokeCap.Round,
            )
            drawLine(
                contentColor,
                Offset(inset, center.y),
                Offset(inset + (size.width - stroke) * position, center.y),
                stroke,
                StrokeCap.Round,
            )
            if (sweepAlpha > 0.005f) {
                val progress = SweepEasing.transform(frameMillis.mod(SWEEP_MILLIS) / SWEEP_MILLIS.toFloat())
                val length = size.width * 0.34f
                val from = (size.width + length) * progress - length
                val to = from + length
                val start = from.coerceAtLeast(inset)
                val end = to.coerceAtMost(size.width - inset)
                if (end > start) {
                    drawLine(
                        brush =
                            Brush.horizontalGradient(
                                0f to Color.Transparent,
                                0.5f to contentColor.copy(alpha = 0.7f * sweepAlpha),
                                1f to Color.Transparent,
                                startX = from,
                                endX = to,
                            ),
                        start = Offset(start, center.y),
                        end = Offset(end, center.y),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round,
                    )
                }
            }
        }
    }
}
