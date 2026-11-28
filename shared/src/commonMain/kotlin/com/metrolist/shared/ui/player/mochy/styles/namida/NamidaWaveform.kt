// Ported from Sora (github.com/shizenworks/sora), its namida player style after namida (github.com/namidaco/namida). Licensed under GPL-3.0.

// namida player style — the waveform seek bar, ported 1:1 from namida's
// ui/widgets/waveform.dart: the same bar geometry (width fraction 0.54, heights
// 3..64, corners 5), the same two-layer paint (behind onSurface@0.16, front
// onSurface@0.43) and the same played gradient (the current colour blended onto
// onSurface at 0.71 and 0.55, both at 0.43 opacity). The bars themselves ride a
// deterministic amplitude series per song, the way namida's extracted
// waveforms ride their decoded samples.

package com.metrolist.shared.ui.player.mochy.styles.namida

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** WaveformComponent's bar metrics. */
private const val BAR_WIDTH_FRACTION = 0.54f
private const val BAR_MIN_HEIGHT = 3f
private const val BAR_MAX_HEIGHT = 64f
private const val BAR_RADIUS = 5f

/**
 * The amplitude series a song paints, in [0, 1]. namida decodes this from the
 * audio; the port keeps a deterministic series from the song's id so the same
 * song always paints the same shape.
 */
private class BarsValue(
    val list: List<Float>,
) {
    val value: List<Float> get() = list
}

@Composable
private fun rememberWaveformBars(
    songId: String?,
    barCount: Int,
): BarsValue {
    val bars =
        remember(songId, barCount) {
            val seed = (songId ?: "namida").fold(1125899906842597L) { acc, c -> 31 * acc + c.code }

            fun noise(i: Int) = sin(i * 12.9898 + seed * 0.0001) * 43758.5453
            val out =
                FloatArray(barCount) { i ->
                    val raw = abs(noise(i) % 1.0).toFloat()
                    val envelope = sin(PI * (i + 0.5) / barCount).toFloat().coerceIn(0.25f, 1f)
                    (0.22f + raw * 0.78f) * envelope
                }
            BarsValue(out.toList())
        }
    return bars
}

@Composable
fun NamidaWaveform(
    positionMs: Long,
    durationMs: Long,
    dragging: Boolean,
    modifier: Modifier = Modifier,
    songId: String? = null,
) {
    val palette = LocalNamidaPalette.current
    val density = LocalDensity.current

    // the bars are computed in composition (the draw scope is not composable), from
    // the canvas width once it is known
    var canvasWidthPx by remember { mutableFloatStateOf(0f) }
    val step = density.density
    val barCount = max(1, (canvasWidthPx / step).toInt())
    val bars = rememberWaveformBars(songId, barCount)

    Canvas(
        modifier.onSizeChanged { canvasWidthPx = it.width.toFloat() },
    ) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas

        val barSlot = size.width / barCount
        val barWidth = barSlot * BAR_WIDTH_FRACTION
        val corner = CornerRadius(BAR_RADIUS * density.density / 4, BAR_RADIUS * density.density / 4)
        val maxHeightPx = min(BAR_MAX_HEIGHT * density.density, size.height)
        val minHeightPx = BAR_MIN_HEIGHT * density.density

        fun barHeight(v: Float): Float = max(minHeightPx, v * maxHeightPx).coerceAtMost(size.height)

        // behind: the whole song at onSurface@0.16
        for (i in 0 until barCount) {
            val h = barHeight(bars.value[i])
            val left = i * barSlot + (barSlot - barWidth) / 2
            drawRoundRect(
                color = palette.waveBehind,
                topLeft = Offset(left, (size.height - h) / 2),
                size = Size(barWidth, h),
                cornerRadius = corner,
            )
        }

        // front: onSurface@0.43 up to the playhead, the played gradient past it
        val fraction = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
        val playedIndex = (fraction * barCount).toInt()
        val playedBrush = Brush.horizontalGradient(colors = palette.wavePlayed, endX = size.width)
        for (i in 0 until barCount) {
            val h = barHeight(bars.value[i])
            val left = i * barSlot + (barSlot - barWidth) / 2
            if (i < playedIndex) {
                drawRoundRect(
                    brush = playedBrush,
                    topLeft = Offset(left, (size.height - h) / 2),
                    size = Size(barWidth, h),
                    cornerRadius = corner,
                )
            } else {
                drawRoundRect(
                    color = palette.waveFront,
                    topLeft = Offset(left, (size.height - h) / 2),
                    size = Size(barWidth, h),
                    cornerRadius = corner,
                )
            }
        }

        // the playhead line while dragging (namida's SeekMagnet glow's quiet cousin)
        if (dragging && durationMs > 0) {
            val x = fraction * size.width
            drawLine(
                color = palette.currentColor.copy(alpha = 0.8f),
                start = Offset(x, size.height / 2 - maxHeightPx / 2 - 4 * density.density),
                end = Offset(x, size.height / 2 + maxHeightPx / 2 + 4 * density.density),
                strokeWidth = 1.5f * density.density,
                cap = StrokeCap.Round,
            )
        }
    }
}
