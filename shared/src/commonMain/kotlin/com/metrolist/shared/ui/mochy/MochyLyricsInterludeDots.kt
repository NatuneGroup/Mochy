// Ported from Mochy Music (LyricsInterludeDots.kt) by realidkroo. Licensed under GPL-3.0.

package com.metrolist.shared.ui.mochy

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.dp
import com.metrolist.shared.ui.components.lyrics.LYRICS_FORCED_SCROLL_MS
import com.metrolist.shared.ui.components.lyrics.LYRICS_RECENTER_EASING
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin

private const val DOT_COUNT = 3

private val DOTS_ROW_HEIGHT = 56.dp
private val DOT_SIZE = 12.dp
private val DOT_GAP = 7.dp
private val DOTS_INSET = 36.dp

// an unlit dot still shows a little, so the three of them read as a row before they light
private const val DOT_REST_ALPHA = 0.3f

// room round the dots for their glow, so it is never cut off by the row
private val DOTS_GLOW_ROOM = 8.dp

// once a dot is lit its glow lets go over about this long, a flare rather than a constant halo
private const val DOT_GLOW_FADE_MS = 900f

// idle bob: one slow breath up and down, each dot a little behind the one before it
private const val DOT_BOB_PERIOD_MS = 2_600f
private val DOT_BOB_HEIGHT = 1.8.dp
private const val DOT_BOB_PHASE_STEP = 0.9f

private const val DOT_POP_STAGGER_MS = 70L

// how long a dot takes to go from unlit to lit, as a share of its own slice of the gap
private const val DOT_FILL_FRACTION = 0.55f

// the last beat before the next line, where the glow spreads over all three dots so the gap ends on a cue
private const val DOT_FINALE_FRACTION = 0.88f

/**
 * The three dots that count down an instrumental gap on the Mochy look: each lights in turn as
 * the gap plays, then the row folds away with the recentre scroll. The row's height is real
 * layout, so [onLayoutShift] reports each change and the lines below move with it.
 */
@Composable
internal fun MochyLyricsInterludeDots(
    startMs: Long,
    endMs: Long,
    positionProvider: () -> Long,
    visible: Boolean,
    modifier: Modifier = Modifier,
    onLayoutShift: (Float) -> Unit = {},
) {
    val span = (endMs - startMs).coerceAtLeast(1L)

    // written in a frame callback and read in draws, so the clock never recomposes the row
    val fills = remember { Array(DOT_COUNT) { mutableFloatStateOf(0f) } }
    val glows = remember { Array(DOT_COUNT) { mutableFloatStateOf(0f) } }
    val finale = remember { mutableFloatStateOf(0f) }
    val clockMs = remember { mutableLongStateOf(0L) }

    val height = remember { Animatable(if (visible) 1f else 0f) }
    val pops = remember { Array(DOT_COUNT) { Animatable(if (visible) 1f else 0f) } }
    val shownHeight = remember { IntArray(1) }

    LaunchedEffect(visible) {
        if (visible) {
            launch {
                height.animateTo(1f, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow))
            }
            pops.forEachIndexed { index, pop ->
                launch {
                    delay(index * DOT_POP_STAGGER_MS)
                    // a touch of bounce, so the dots land instead of just appearing
                    pop.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 260f))
                }
            }
        } else {
            // the same length and curve as the recentre scroll, so the next line slides into the dots' place
            launch { height.animateTo(0f, tween(LYRICS_FORCED_SCROLL_MS, easing = LYRICS_RECENTER_EASING)) }
            pops.forEach { pop ->
                launch { pop.animateTo(0f, tween(LYRICS_FORCED_SCROLL_MS / 2, easing = LYRICS_RECENTER_EASING)) }
            }
        }
    }

    // ticks only while the row is grown; a hidden gap costs nothing per frame
    LaunchedEffect(startMs, endMs, positionProvider, visible) {
        if (!visible) return@LaunchedEffect
        var lastPos = Long.MIN_VALUE
        while (true) {
            var pos = 0L
            withFrameMillis { frameMs ->
                clockMs.longValue = frameMs
                pos = positionProvider()
                val progress = ((pos - startMs).toFloat() / span).coerceIn(0f, 1f)
                finale.floatValue = mochySmoothstep((progress - DOT_FINALE_FRACTION) / (1f - DOT_FINALE_FRACTION))
                for (index in 0 until DOT_COUNT) {
                    val sliceStart = index.toFloat() / DOT_COUNT
                    val fill = mochySmoothstep((progress - sliceStart) * DOT_COUNT / DOT_FILL_FRACTION)
                    fills[index].floatValue = fill
                    // the glow rides the fill up while the dot lights, then eases off once it's lit
                    val litAt = sliceStart + DOT_FILL_FRACTION / DOT_COUNT
                    glows[index].floatValue =
                        if (progress < litAt) fill else exp(-((progress - litAt) * span) / DOT_GLOW_FADE_MS)
                }
            }
            // paused during an interlude: check back gently instead of spinning every frame
            if (pos == lastPos) delay(180L)
            lastPos = pos
        }
    }

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .layout { measurable, constraints ->
                    val full = DOTS_ROW_HEIGHT.roundToPx()
                    val placeable = measurable.measure(constraints.copy(minHeight = full, maxHeight = full))
                    val shown = (full * height.value.coerceIn(0f, 1f)).roundToInt()
                    val delta = shown - shownHeight[0]
                    if (delta != 0) onLayoutShift(delta.toFloat())
                    shownHeight[0] = shown
                    layout(placeable.width, shown) { placeable.place(0, (shown - full) / 2) }
                }.height(DOTS_ROW_HEIGHT)
                .padding(horizontal = DOTS_INSET)
                .graphicsLayer { alpha = height.value.coerceIn(0f, 1f) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Row(
            modifier = Modifier.padding(DOTS_GLOW_ROOM),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(DOT_COUNT) { index ->
                Spacer(
                    modifier =
                        Modifier
                            .size(DOT_SIZE)
                            .graphicsLayer {
                                // the dot keeps its size as it lights; only the pop on arrival scales it
                                val pop = pops[index].value
                                scaleX = pop
                                scaleY = pop
                                alpha = (DOT_REST_ALPHA + (1f - DOT_REST_ALPHA) * fills[index].floatValue) * pop.coerceIn(0f, 1f)
                                val phase = clockMs.longValue / DOT_BOB_PERIOD_MS * 2f * PI.toFloat() - index * DOT_BOB_PHASE_STEP
                                translationY = sin(phase) * DOT_BOB_HEIGHT.toPx()
                            }
                            .drawBehind {
                                drawDotGlow(glows[index].floatValue)
                                drawCircle(Color.White.copy(alpha = 0.75f + 0.25f * fills[index].floatValue), size.minDimension / 2f)
                            },
                )
                if (index < DOT_COUNT - 1) Spacer(modifier = Modifier.size(DOT_GAP))
            }
        }
    }
}

// a soft white halo behind a lit dot, strongest as it lights and gone once its glow has faded
private fun DrawScope.drawDotGlow(glow: Float) {
    if (glow < 0.01f) return
    val radius = size.minDimension * 1.6f
    drawCircle(
        brush =
            Brush.radialGradient(
                colors = listOf(Color.White.copy(alpha = 0.16f * glow), Color.Transparent),
                center = Offset(size.width / 2f, size.height / 2f),
                radius = radius,
            ),
        radius = radius,
    )
}
