// Ported from Mochy Music by realidkroo (github.com/realidkroo/mochy-music). Licensed under GPL-3.0.

package com.metrolist.shared.ui.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.metrolist.shared.ui.haptics.HapticEvent
import com.metrolist.shared.ui.haptics.SliderDetents
import com.metrolist.shared.ui.haptics.rememberMetrolistHaptics
import kotlin.math.roundToInt

// Mochy's settings slider: a bar with no thumb that swells from 7dp to 14dp while it's held
@Composable
internal fun MochySettingsSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    enabled: Boolean,
    label: String,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberMetrolistHaptics()
    val detents = remember(valueRange, steps) { SliderDetents(valueRange, steps, value) }
    detents.reset(value)
    val start = valueRange.start
    val end = valueRange.endInclusive
    val onChange by rememberUpdatedState(onValueChange)
    var widthPx by remember { mutableFloatStateOf(0f) }
    var held by remember { mutableStateOf(false) }
    val open by animateFloatAsState(
        targetValue = if (held && enabled) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 520f),
        label = "mochySliderOpen",
    )
    val fraction = if (end > start) ((value - start) / (end - start)).coerceIn(0f, 1f) else 0f
    val activeColor = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    val inactiveColor =
        if (enabled) {
            MaterialTheme.colorScheme.onSurface.copy(
                alpha = 0.20f,
            )
        } else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
        }

    fun pick(x: Float) {
        val f = if (widthPx > 0f) (x / widthPx).coerceIn(0f, 1f) else 0f
        var picked = start + (end - start) * f
        if (steps > 0) {
            val step = (end - start) / (steps + 1)
            picked = start + ((picked - start) / step).roundToInt() * step
        }
        picked = picked.coerceIn(start, end)
        if (detents.moved(picked)) haptics.perform(HapticEvent.SEGMENT_TICK)
        onChange(picked)
    }

    Canvas(
        modifier =
            modifier
                .fillMaxWidth()
                .height(28.dp)
                .onSizeChanged { widthPx = it.width.toFloat() }
                .pointerInput(enabled, start, end, steps) {
                    if (!enabled) return@pointerInput
                    detectTapGestures(
                        onPress = { offset ->
                            held = true
                            pick(offset.x)
                            tryAwaitRelease()
                            held = false
                        },
                    )
                }.pointerInput(enabled, start, end, steps) {
                    if (!enabled) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            held = true
                            pick(offset.x)
                        },
                        onDragEnd = { held = false },
                        onDragCancel = { held = false },
                    ) { change, _ ->
                        change.consume()
                        pick(change.position.x)
                    }
                }.semantics { contentDescription = label },
    ) {
        val thickness = (7f + 7f * open).dp.toPx()
        val y = size.height / 2f
        drawLine(
            color = inactiveColor,
            start = Offset(thickness / 2f, y),
            end = Offset(size.width - thickness / 2f, y),
            strokeWidth = thickness,
            cap = StrokeCap.Round,
        )
        val endX = (thickness / 2f + (size.width - thickness) * fraction).coerceAtLeast(thickness / 2f)
        drawLine(
            color = activeColor,
            start = Offset(thickness / 2f, y),
            end = Offset(endX, y),
            strokeWidth = thickness,
            cap = StrokeCap.Round,
        )
    }
}
