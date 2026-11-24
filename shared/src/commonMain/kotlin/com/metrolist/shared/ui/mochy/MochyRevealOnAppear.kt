// Ported from Mochy Music by realidkroo. Licensed under GPL-3.0.

package com.metrolist.shared.ui.mochy

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.min
import kotlin.time.TimeMark
import kotlin.time.TimeSource

private val RevealEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
private const val BONES_FADE_OUT_MS = 280
private const val REVEAL_GAP_MS = 120
private const val REVEAL_FADE_IN_MS = 520
private val RevealSlideDistance = 20.dp
private const val BONES_WAVE_MS = 2000

private const val BURST_WINDOW_MS = 120L
private const val BURST_STEP_MS = 60
private const val BURST_MAX_STEPS = 6

/** Base color of a loading bone before the shimmer wave modulates it. */
val BoneColor = Color(0xFFD9D9D9).copy(alpha = 0.22f)

/**
 * Tracks which keys already played their reveal so returning to a screen or scrolling back
 * does not replay animations a second time.
 */
@Stable
class RevealSeenState(
    initial: Collection<String> = emptyList(),
) {
    private val seen = HashSet(initial)

    fun hasSeen(key: String): Boolean = key in seen

    fun markSeen(key: String) {
        seen.add(key)
    }

    private var burstStartedAt: TimeMark? = null
    private var burstCount = 0

    internal fun nextBurstDelay(): Int {
        val started = burstStartedAt
        if (started == null || started.elapsedNow().inWholeMilliseconds > BURST_WINDOW_MS) {
            burstStartedAt = TimeSource.Monotonic.markNow()
            burstCount = 0
        }
        return min(burstCount++, BURST_MAX_STEPS) * BURST_STEP_MS
    }

    companion object {
        val Saver: Saver<RevealSeenState, ArrayList<String>> =
            Saver(
                save = { ArrayList(it.seen) },
                restore = { RevealSeenState(it) },
            )
    }
}

@Composable
fun rememberRevealSeenState(): RevealSeenState = rememberSaveable(saver = RevealSeenState.Saver) { RevealSeenState() }

/**
 * Shows loading bones while [data] is null, then fades out the bones, pauses briefly, and
 * slides + fades the content into place. Subsequent data updates swap in place without flickering.
 */
@Composable
fun <T : Any> RevealWhenLoaded(
    data: T?,
    modifier: Modifier = Modifier,
    bones: @Composable () -> Unit,
    content: @Composable (T) -> Unit,
) {
    val slidePx = with(LocalDensity.current) { RevealSlideDistance.roundToPx() }

    AnimatedContent(
        targetState = data,
        contentKey = { it != null },
        transitionSpec = {
            if (targetState != null) {
                val delay = BONES_FADE_OUT_MS + REVEAL_GAP_MS
                (
                    fadeIn(tween(REVEAL_FADE_IN_MS, delayMillis = delay, easing = RevealEasing)) +
                        slideInVertically(tween(REVEAL_FADE_IN_MS, delayMillis = delay, easing = RevealEasing)) { slidePx }
                ).togetherWith(fadeOut(tween(BONES_FADE_OUT_MS)))
                    .using(SizeTransform(clip = false))
            } else {
                fadeIn(tween(BONES_FADE_OUT_MS))
                    .togetherWith(fadeOut(tween(BONES_FADE_OUT_MS)))
                    .using(SizeTransform(clip = false))
            }
        },
        label = "RevealWhenLoaded",
        modifier = modifier,
    ) { state ->
        if (state == null) bones() else content(state)
    }
}

/**
 * Fades and slides an item up the first time it appears for [key]. With [cascade], items that
 * land together stagger cleanly instead of animating simultaneously.
 */
@Composable
fun Modifier.revealOnAppear(
    key: String,
    seenState: RevealSeenState,
    enabled: Boolean = true,
    delayMillis: Int = 0,
    cascade: Boolean = false,
): Modifier {
    val alreadySeen = remember(key) { seenState.hasSeen(key) }
    if (alreadySeen) return this

    val progress = remember(key) { Animatable(0f) }
    LaunchedEffect(key, enabled) {
        if (!enabled) return@LaunchedEffect
        seenState.markSeen(key)
        val wait = delayMillis + if (cascade) seenState.nextBurstDelay() else 0
        progress.animateTo(1f, tween(REVEAL_FADE_IN_MS, delayMillis = wait, easing = RevealEasing))
    }

    val slidePx = with(LocalDensity.current) { RevealSlideDistance.toPx() }
    return graphicsLayer {
        val p = progress.value
        alpha = p
        translationY = (1f - p) * slidePx
        compositingStrategy = CompositingStrategy.ModulateAlpha
    }
}

/**
 * Hosts loading bones and runs a soft vertical opacity wave down through them in the draw pass.
 */
@Composable
fun BonesHost(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val wave = rememberInfiniteTransition(label = "bones")
    val phase =
        wave.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(BONES_WAVE_MS, easing = LinearEasing)),
            label = "bonesPhase",
        )

    Column(
        modifier =
            modifier
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    val h = size.height
                    val band = h * 0.8f + 1f
                    val center = -band + phase.value * (h + band * 2f)
                    drawRect(
                        brush =
                            Brush.verticalGradient(
                                0f to Color.Black.copy(alpha = 0.45f),
                                0.5f to Color.Black,
                                1f to Color.Black.copy(alpha = 0.45f),
                                startY = center - band,
                                endY = center + band,
                            ),
                        blendMode = BlendMode.DstIn,
                    )
                },
        content = content,
    )
}

@Composable
fun Bone(
    width: Dp,
    height: Dp,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
) {
    Box(
        modifier =
            modifier
                .size(width, height)
                .background(BoneColor, shape),
    )
}
