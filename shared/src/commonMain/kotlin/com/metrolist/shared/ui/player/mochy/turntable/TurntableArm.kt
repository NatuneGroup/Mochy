// Ported and adapted from Mochy Music by realidkroo. Licensed under GPL-3.0.

package com.metrolist.shared.ui.player.mochy.turntable

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrolist.shared.ui.haptics.HapticEvent
import com.metrolist.shared.ui.haptics.rememberMetrolistHaptics
import com.metrolist.shared.ui.theme.mochyInterFontFamily
import com.metrolist.shared.ui.utils.supportsNativeBlur
import com.metrolist.shared.utils.formatDuration
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

internal const val TURNTABLE_DISC_DIAMETER = 1.406f
internal const val TURNTABLE_DISC_CENTER_X = 0.162f
internal const val TURNTABLE_DISC_CENTER_Y_BY_WIDTH = 0.703f
internal const val TURNTABLE_ART_RATIO = 0.526f

private const val PIVOT_X = 0.895f
private const val PIVOT_Y = 0.125f

private val RecordRadius = TURNTABLE_DISC_DIAMETER / 2f

private fun rad(degrees: Float): Float = (degrees * (PI / 180.0)).toFloat()

private fun deg(radians: Float): Float = (radians * (180.0 / PI)).toFloat()

private val StylusReach = hypot(ARM_LENGTH + ARM_STYLUS_ALONG * cos(rad(ARM_HEAD_BEND)), ARM_STYLUS_ALONG * sin(rad(ARM_HEAD_BEND)))
private val StylusDegrees = deg(atan2(ARM_STYLUS_ALONG * sin(rad(ARM_HEAD_BEND)), ARM_LENGTH + ARM_STYLUS_ALONG * cos(rad(ARM_HEAD_BEND))))

private fun tubeDegreesFor(fraction: Float): Float {
    val vx = TURNTABLE_DISC_CENTER_X - PIVOT_X
    val vy = TURNTABLE_DISC_CENTER_Y_BY_WIDTH - PIVOT_Y
    val v = hypot(vx, vy)
    val r = fraction * RecordRadius
    val turn = acos(((StylusReach * StylusReach + v * v - r * r) / (2f * StylusReach * v)).coerceIn(-1f, 1f))
    return deg(atan2(vy, vx)) - deg(turn) - StylusDegrees
}

private val StartDegrees = tubeDegreesFor(0.94f)
private val EndDegrees = tubeDegreesFor(TURNTABLE_ART_RATIO + 0.075f)

private const val ARC_RADIUS = 1.03f
private const val PEEK_MS = 3_000L
private const val LABEL_GAP_DEGREES = 3.5f

@Composable
internal fun TurntableBase(modifier: Modifier = Modifier) {
    Spacer(modifier.drawBehind { drawArmBase(size.width) })
}

@Composable
internal fun TurntableArm(
    positionProvider: () -> Long,
    duration: Long,
    isPlaying: Boolean,
    songId: String?,
    canSeek: Boolean,
    active: Boolean,
    interactive: Boolean,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    awayProvider: () -> Float = { 0f },
) {
    val haptics = rememberMetrolistHaptics()
    val scope = rememberCoroutineScope()

    val angle = remember { Animatable(StartDegrees) }
    val held = remember { Animatable(0f) }
    val dragging = remember { booleanArrayOf(false) }
    val scrubMs = remember { mutableLongStateOf(-1L) }
    val peek = remember { Animatable(0f) }
    val peekGrow = remember { Animatable(0f) }

    val latestPosition by rememberUpdatedState(positionProvider)
    val latestDuration by rememberUpdatedState(duration)
    val latestIsPlaying by rememberUpdatedState(isPlaying)
    val latestSongId by rememberUpdatedState(songId)
    val latestCanSeek by rememberUpdatedState(canSeek)
    val latestOnSeek by rememberUpdatedState(onSeek)

    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        var wasPlaying = false
        var lastId: String? = null
        var showing: Job? = null
        snapshotFlow { latestIsPlaying to latestSongId }
            .distinctUntilChanged()
            .collect { (playing, id) ->
                val started = playing && (!wasPlaying || id != lastId)
                wasPlaying = playing
                lastId = id
                if (!started) return@collect
                showing?.cancel()
                showing =
                    launch {
                        if (peek.value < 0.05f) peekGrow.snapTo(0f)
                        launch { peek.animateTo(1f, tween(240)) }
                        peekGrow.animateTo(1f, tween(760, easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)))
                        delay(PEEK_MS - 760L)
                        peek.animateTo(0f, tween(420))
                    }
            }
    }

    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        snapshotFlow { latestPosition() to latestDuration }.collect { (pos, dur) ->
            if (dragging[0]) return@collect
            val target = angleFor(pos, dur)
            if (abs(target - angle.value) > 1.5f) {
                launch { angle.animateTo(target, spring(dampingRatio = 0.9f, stiffness = 120f)) }
            } else if (!angle.isRunning) {
                angle.snapTo(target)
            }
        }
    }

    val measurer = rememberTextMeasurer(cacheSize = 16)
    val inter = mochyInterFontFamily()
    val labelStyle =
        remember(inter) {
            TextStyle(
                fontFamily = inter,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                color = Color.White,
            )
        }

    val softShadow = supportsNativeBlur()
    val density = LocalDensity.current
    val shadowBlur =
        remember(density, softShadow) {
            if (softShadow) with(density) { BlurEffect(5.dp.toPx(), 5.dp.toPx(), TileMode.Clamp) } else null
        }

    Box(
        modifier =
            modifier.then(
                if (!interactive) {
                    Modifier
                } else {
                    Modifier.pointerInput(Unit) {
                        val grabPx = 26.dp.toPx()
                        awaitEachGesture {
                            val w = size.width.toFloat()
                            val pivot = Offset(w * PIVOT_X, w * PIVOT_Y)
                            val down = awaitFirstDown(requireUnconsumed = false)
                            if (!onArm(down.position, pivot, angle.value, w, grabPx)) return@awaitEachGesture
                            val dur = latestDuration.takeIf { it > 0L } ?: return@awaitEachGesture
                            if (!latestCanSeek) return@awaitEachGesture
                            down.consume()
                            dragging[0] = true
                            haptics.perform(HapticEvent.LONG_PRESS)
                            scope.launch { angle.stop() }
                            scope.launch { held.animateTo(1f, tween(180)) }
                            val grabbed = angle.value - degreesOf(down.position, pivot)
                            var lastTick = -1L
                            scrubMs.longValue = msFor(angle.value, dur)

                            drag(down.id) { change ->
                                change.consume()
                                val next = (degreesOf(change.position, pivot) + grabbed).coerceIn(StartDegrees, EndDegrees)
                                scope.launch { angle.snapTo(next) }
                                val ms = msFor(next, dur)
                                scrubMs.longValue = ms
                                val tick = ms / 10_000L
                                if (tick != lastTick) {
                                    if (lastTick >= 0) haptics.perform(HapticEvent.SEGMENT_TICK)
                                    lastTick = tick
                                }
                            }

                            latestOnSeek(scrubMs.longValue.coerceIn(0L, dur))
                            dragging[0] = false
                            scope.launch {
                                delay(450)
                                held.animateTo(0f, tween(320))
                                if (!dragging[0]) scrubMs.longValue = -1L
                            }
                        }
                    }
                },
            ),
    ) {
        Spacer(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    turnAboutPivot(angle.value)
                    val lift = maxOf(held.value, (awayProvider() * 2.5f).coerceAtMost(0.6f))
                    val shift = size.width * (0.012f + 0.024f * lift)
                    translationX = shift * 0.55f
                    translationY = shift * 1.1f
                    alpha = (if (softShadow) 0.8f else 0.42f) - 0.2f * lift
                    renderEffect = shadowBlur
                }.drawBehind { drawTonearm(pivotIn(size), StartDegrees, size.width, silhouette = true) },
        )

        Spacer(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    turnAboutPivot(angle.value)
                    val s = 1f + 0.012f * maxOf(held.value, (awayProvider() * 2.5f).coerceAtMost(0.6f))
                    scaleX = s
                    scaleY = s
                }.drawBehind { drawTonearm(pivotIn(size), StartDegrees, size.width, silhouette = false) },
        )

        Spacer(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    val lift = maxOf(held.value, peek.value)
                    if (lift > 0.001f) {
                        drawTimeArc(
                            pivot = pivotIn(size),
                            degrees = angle.value,
                            w = size.width,
                            alpha = lift,
                            grow = if (held.value >= peek.value) 1f else peekGrow.value,
                            positionMs = scrubMs.longValue,
                            durationMs = latestDuration,
                            measurer = measurer,
                            labelStyle = labelStyle,
                        )
                    }
                },
        )
    }
}

private fun pivotIn(size: Size) = Offset(size.width * PIVOT_X, size.width * PIVOT_Y)

private fun GraphicsLayerScope.turnAboutPivot(degrees: Float) {
    transformOrigin = TransformOrigin(PIVOT_X, if (size.height > 0f) PIVOT_Y * size.width / size.height else 0f)
    rotationZ = degrees - StartDegrees
}

private fun angleFor(
    position: Long,
    duration: Long,
): Float {
    if (duration <= 0L) return StartDegrees
    val f = (position.toFloat() / duration).coerceIn(0f, 1f)
    return StartDegrees + (EndDegrees - StartDegrees) * f
}

private fun msFor(
    degrees: Float,
    duration: Long,
): Long {
    val f = ((degrees - StartDegrees) / (EndDegrees - StartDegrees)).coerceIn(0f, 1f)
    return (duration * f).toLong()
}

private fun unit(degrees: Float): Offset {
    val r = rad(degrees)
    return Offset(cos(r), sin(r))
}

private fun degreesOf(
    p: Offset,
    pivot: Offset,
): Float = deg(atan2(p.y - pivot.y, p.x - pivot.x))

private fun onArm(
    p: Offset,
    pivot: Offset,
    degrees: Float,
    w: Float,
    slop: Float,
): Boolean {
    val u = unit(degrees)
    val joint = pivot + u * (w * ARM_LENGTH)
    val head = joint + unit(degrees + ARM_HEAD_BEND) * (w * ARM_HEAD_LENGTH * 0.5f)
    if (hypot(p.x - head.x, p.y - head.y) < w * ARM_HEAD_LENGTH * 0.5f + slop * 0.5f) return true
    val rel = p - pivot
    val along = rel.x * u.x + rel.y * u.y
    if (along < w * 0.12f || along > w * ARM_LENGTH) return false
    return abs(rel.y * u.x - rel.x * u.y) < slop
}

private fun DrawScope.drawTimeArc(
    pivot: Offset,
    degrees: Float,
    w: Float,
    alpha: Float,
    grow: Float = 1f,
    positionMs: Long,
    durationMs: Long,
    measurer: TextMeasurer,
    labelStyle: TextStyle,
) {
    val radius = w * ARC_RADIUS
    val stroke = 5.dp.toPx()
    val topLeft = pivot - Offset(radius, radius)
    val arcSize = Size(radius * 2f, radius * 2f)
    val start = StartDegrees + StylusDegrees
    val reach = (EndDegrees - StartDegrees) * grow.coerceIn(0f, 1f)
    drawArc(
        color = Color.White.copy(alpha = 0.32f * alpha),
        startAngle = start,
        sweepAngle = reach.coerceAtLeast(0.01f),
        useCenter = false,
        topLeft = topLeft,
        size = arcSize,
        style = Stroke(stroke, cap = StrokeCap.Round),
    )
    drawArc(
        color = Color.White.copy(alpha = alpha),
        startAngle = start,
        sweepAngle = (degrees - StartDegrees).coerceIn(0.01f, reach.coerceAtLeast(0.01f)),
        useCenter = false,
        topLeft = topLeft,
        size = arcSize,
        style = Stroke(stroke, cap = StrokeCap.Round),
    )
    if (durationMs <= 0L) return
    val shown = if (positionMs >= 0L) positionMs else msFor(degrees, durationMs)
    timeLabel(
        text = formatDuration(shown, hideLeadingZeroUnits = true),
        pivot = pivot,
        degrees = start - LABEL_GAP_DEGREES,
        radius = radius,
        alpha = alpha,
        measurer = measurer,
        style = labelStyle,
    )
    val endAlpha = alpha * ((grow - 0.7f) / 0.3f).coerceIn(0f, 1f)
    if (endAlpha > 0.001f) {
        timeLabel(
            text = formatDuration(durationMs, hideLeadingZeroUnits = true),
            pivot = pivot,
            degrees = EndDegrees + StylusDegrees + LABEL_GAP_DEGREES,
            radius = radius,
            alpha = endAlpha,
            measurer = measurer,
            style = labelStyle,
        )
    }
}

private fun DrawScope.timeLabel(
    text: String,
    pivot: Offset,
    degrees: Float,
    radius: Float,
    alpha: Float,
    measurer: TextMeasurer,
    style: TextStyle,
) {
    val layout = measurer.measure(text, style.copy(color = style.color.copy(alpha = alpha)))
    val at = pivot + unit(degrees) * radius
    rotate(degrees - 90f, at) {
        drawText(layout, topLeft = Offset(at.x - layout.size.width / 2f, at.y - layout.size.height / 2f))
    }
}
