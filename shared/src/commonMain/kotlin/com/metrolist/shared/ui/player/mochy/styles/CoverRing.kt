// Ported from Mochy Music by realidkroo (github.com/realidkroo/mochy-music). Licensed under GPL-3.0.

// The seek bar that runs round a round cover's rim (the Poster's). Mochy also stands a spectrum visualizer on
// that rim; audio visualizers aren't part of this app, so only the bar is here.

package com.metrolist.shared.ui.player.mochy.styles

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrolist.shared.playback.LocalPlayerSession
import com.metrolist.shared.ui.theme.mochyInterFontFamily
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

/** a circle in the ring's own pixels: the cover's, which the ring runs round */
@Immutable
data class RingCircle(
    val center: Offset,
    val radius: Float,
)

/**
 * The ring round a round cover: what the seek bar on it is doing ([expand]: 0 a thin line on the cover's edge,
 * 1 the full bar just outside it with the times at its ends), and the time a held bar has been dragged to.
 * Shared with whatever the bar pushes aside (the Poster's lyrics).
 */
@Stable
class CoverRingState internal constructor(
    private val scope: CoroutineScope,
) {
    val expand = Animatable(0f)
    var scrubMs by mutableLongStateOf(-1L)
    internal var held = false
    private var job: Job? = null

    /** grows the bar, and lets it settle back after [forMs] unless it's held by then */
    fun peek(forMs: Long) {
        job?.cancel()
        job =
            scope.launch {
                expand.animateTo(1f, ExpandSpring)
                delay(forMs)
                if (!held) expand.animateTo(0f, CollapseSpring)
            }
    }

    internal fun grab() {
        job?.cancel()
        held = true
        job = scope.launch { expand.animateTo(1f, ExpandSpring) }
    }

    internal fun letGo(afterMs: Long) {
        held = false
        peek(afterMs)
    }
}

@Composable
fun rememberCoverRingState(): CoverRingState {
    val scope = rememberCoroutineScope()
    return remember(scope) { CoverRingState(scope) }
}

private val ExpandSpring = spring<Float>(dampingRatio = 0.7f, stiffness = 340f)
private val CollapseSpring = spring<Float>(dampingRatio = 0.9f, stiffness = 170f)

/** how long the bar stays out when playback starts */
private const val START_PEEK_MS = 3_000L

/** and after a touch lets go */
private const val RELEASE_PEEK_MS = 1_400L

/**
 * The seek bar round [circle], over the lower edge of a cover that runs off the screen's sides. The bar lies
 * thin on the cover's rim; touched, it moves out past the rim and thickens, the elapsed time and the song's
 * length riding its ends - drag along it to scrub, let go to play from there. It also comes out on its own for a
 * few seconds when the music starts.
 */
@Composable
fun CoverRing(
    state: CoverRingState,
    circle: RingCircle,
    // the live player (not a selector card): it seeks and peeks
    live: Boolean,
    position: LivePosition,
    modifier: Modifier = Modifier,
    side: RingSide = RingSide.Bottom,
) {
    val session = LocalPlayerSession.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val measurer = rememberTextMeasurer(cacheSize = 24)
    val fontFamily = mochyInterFontFamily()
    val labelStyle =
        remember(fontFamily) {
            TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Color.White)
        }
    val currentCircle by rememberUpdatedState(circle)
    val showing = rememberStyleShowing()
    val interactive = LocalStyleInteractive.current
    val duration = rememberDurationMs()

    // out for a few seconds whenever the music starts: play pressed, or the next song begun; not while collapsed
    LaunchedEffect(session, live) {
        if (!live || session == null) return@LaunchedEffect
        var wasPlaying = session.isPlaying.value
        var lastId = session.currentSong.value?.id
        combine(session.isPlaying, session.currentSong.map { it?.id }.distinctUntilChanged()) { playing, id -> playing to id }
            .collect { (playing, id) ->
                val started = playing && (!wasPlaying || id != lastId)
                wasPlaying = playing
                lastId = id
                if (started && !state.held && showing()) state.peek(START_PEEK_MS)
            }
    }

    Spacer(
        modifier =
            modifier
                .then(
                    if (!live || session == null) {
                        Modifier
                    } else {
                        Modifier.pointerInput(session, interactive) {
                            val slop = viewConfiguration.touchSlop
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = true, pass = PointerEventPass.Main)
                                // nothing taken from the mini player, or from the sheet on its way down
                                if (!interactive()) return@awaitEachGesture
                                val geometry = RingGeometry(currentCircle, Size(size.width.toFloat(), size.height.toFloat()), density, side)
                                if (!geometry.onBand(down.position)) return@awaitEachGesture
                                val length = session.duration.value.takeIf { it > 0 } ?: return@awaitEachGesture
                                state.grab()
                                // a swipe along the bar scrubs; one across it (down, to close the player) is left alone
                                var travel = Offset.Zero
                                var scrubbing = false
                                var lastTick = -1L
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Main)
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!change.pressed) break
                                    if (!scrubbing) {
                                        travel += change.positionChange()
                                        if (travel.getDistance() < slop) continue
                                        if (geometry.alongness(change.position, travel) < 0.55f) {
                                            // not ours: settle back and let the player have it
                                            state.letGo(250L)
                                            return@awaitEachGesture
                                        }
                                        scrubbing = true
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                    change.consume()
                                    val ms = (length * geometry.fractionAt(change.position)).toLong()
                                    state.scrubMs = ms
                                    val tick = ms / 10_000L
                                    if (tick != lastTick) {
                                        if (lastTick >= 0) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        lastTick = tick
                                    }
                                }
                                if (scrubbing && state.scrubMs >= 0L) session.seekTo(state.scrubMs.coerceIn(0L, length))
                                state.letGo(RELEASE_PEEK_MS)
                                scope.launch {
                                    // the bar keeps the dragged time until the player has caught up with it
                                    delay(400)
                                    if (!state.held) state.scrubMs = -1L
                                }
                            }
                        }
                    },
                ).drawBehind {
                    val geometry = RingGeometry(currentCircle, size, density, side)
                    val e = state.expand.value
                    // the frame's one position, shared with everything else the style draws
                    val at =
                        when {
                            state.scrubMs >= 0L -> state.scrubMs
                            live -> position.positionMs()
                            else -> (duration * 0.38f).toLong()
                        }
                    drawSeekArc(geometry, e, at.toFloat() / duration, at, duration, measurer, labelStyle)
                },
    )
}

/** which part of the cover's rim is on screen: its bottom (upright) or its right side (sideways) */
enum class RingSide { Bottom, Right }

private fun toDegrees(radians: Float): Float = radians * 180f / PI.toFloat()

/**
 * Where everything on the ring sits. The bar runs along the rim that's on screen, from the left (upright) or the
 * top (sideways), its ends kept a margin in from the screen's edges (wider when expanded, for the times beyond).
 */
private class RingGeometry(
    val circle: RingCircle,
    val size: Size,
    private val dp: Float,
    val side: RingSide,
) {
    val center get() = circle.center
    val radius get() = circle.radius

    /** the bar's radius: on the cover at rest, past the rim when out */
    fun barRadius(e: Float) = radius + lerp(-15f, 22f, e) * dp

    /** the angle (degrees, 0 right, 90 down) where a circle of [r] crosses x, on its lower side */
    fun angleAtX(
        x: Float,
        r: Float,
    ): Float {
        val dx = (x - center.x).coerceIn(-r * 0.999f, r * 0.999f)
        return toDegrees(atan2(sqrt(r * r - dx * dx), dx))
    }

    /** the same where it crosses y, on its right side */
    fun angleAtY(
        y: Float,
        r: Float,
    ): Float {
        val dy = (y - center.y).coerceIn(-r * 0.999f, r * 0.999f)
        return toDegrees(atan2(dy, sqrt(r * r - dy * dy)))
    }

    /** where a circle of [r] crosses into the screen [from] its first edge and [to] its last */
    fun span(
        r: Float,
        from: Float,
        to: Float,
    ): Pair<Float, Float> =
        when (side) {
            RingSide.Bottom -> angleAtX(from, r) to angleAtX(size.width - to, r)
            RingSide.Right -> angleAtY(from, r) to angleAtY(size.height - to, r)
        }

    /** the bar's ends: the song's start, then its end */
    fun barEnds(e: Float): Pair<Float, Float> = span(barRadius(e), lerp(26f, 64f, e) * dp, lerp(26f, 52f, e) * dp)

    /** a touch near enough the bar to take it */
    fun onBand(p: Offset): Boolean {
        val d = hypot(p.x - center.x, p.y - center.y)
        if (d < radius - 46f * dp || d > radius + 50f * dp) return false
        val (start, end) = barEnds(0f)
        val a = angleOf(p)
        return a in (minOf(start, end) - 4f)..(maxOf(start, end) + 4f)
    }

    fun angleOf(p: Offset) = toDegrees(atan2(p.y - center.y, p.x - center.x))

    /** how much of [move] runs along the circle at [p] rather than across it, 0..1 */
    fun alongness(
        p: Offset,
        move: Offset,
    ): Float {
        val rx = p.x - center.x
        val ry = p.y - center.y
        val rl = hypot(rx, ry).coerceAtLeast(1f)
        val ml = move.getDistance().coerceAtLeast(1f)
        val across = abs((move.x * rx + move.y * ry) / (rl * ml))
        return sqrt((1f - across * across).coerceAtLeast(0f))
    }

    /** how far along the bar [p] is, 0 at its start */
    fun fractionAt(p: Offset): Float {
        val (start, end) = barEnds(1f)
        return ((angleOf(p) - start) / (end - start)).coerceIn(0f, 1f)
    }
}

private fun DrawScope.drawSeekArc(
    g: RingGeometry,
    e: Float,
    fraction: Float,
    positionMs: Long,
    durationMs: Long,
    measurer: TextMeasurer,
    labelStyle: TextStyle,
) {
    val r = g.barRadius(e)
    val (start, end) = g.barEnds(e)
    val sweep = end - start
    val stroke = lerp(3f, 7f, e).dp.toPx()
    val topLeft = g.center - Offset(r, r)
    val arcSize = Size(r * 2f, r * 2f)
    // on the cover it's only a hint (a third as bright); out past the rim, the full bar
    val ink = lerp(0.3f, 1f, e)
    drawArc(
        color = Color.White.copy(alpha = 0.38f * ink),
        startAngle = start,
        sweepAngle = sweep,
        useCenter = false,
        topLeft = topLeft,
        size = arcSize,
        style = Stroke(stroke, cap = StrokeCap.Round),
    )
    val played = sweep * fraction.coerceIn(0f, 1f)
    drawArc(
        color = Color.White.copy(alpha = 0.96f * ink),
        startAngle = start,
        sweepAngle = if (abs(played) < 0.01f) 0.01f * sign(sweep) else played,
        useCenter = false,
        topLeft = topLeft,
        size = arcSize,
        style = Stroke(stroke, cap = StrokeCap.Round),
    )
    val shown = ((e - 0.3f) / 0.7f).coerceIn(0f, 1f)
    if (shown <= 0.001f || durationMs <= 1L) return
    val toward = sign(sweep)
    ringLabel(styleTimeString(positionMs), g, start, r, outward = -toward, shown, measurer, labelStyle)
    ringLabel(styleTimeString(durationMs), g, end, r, outward = toward, shown, measurer, labelStyle)
}

/** a time just past one end of the bar, turned along it, reading the right way up */
private fun DrawScope.ringLabel(
    text: String,
    g: RingGeometry,
    endDegrees: Float,
    r: Float,
    // which way round, in degrees, is away from the bar
    outward: Float,
    shown: Float,
    measurer: TextMeasurer,
    style: TextStyle,
) {
    // measured at full colour and faded as it's drawn, so the measurer's cache holds through the bar coming out
    val layout = measurer.measure(text, style)
    // clear of the round cap by a little, sliding in from further out as it appears
    val gapPx = (layout.size.width / 2f) + (8f + 10f * (1f - shown)).dp.toPx()
    val degrees = endDegrees + outward * toDegrees(gapPx / r)
    val rad = degrees * (PI.toFloat() / 180f)
    val at = g.center + Offset(cos(rad), sin(rad)) * r
    val s = 0.86f + 0.14f * shown
    // along the rim, turned whichever way keeps it the right way up
    var turn = degrees - 90f
    while (turn > 90f) turn -= 180f
    while (turn < -90f) turn += 180f
    rotate(turn, at) {
        scale(s, s, at) {
            drawText(layout, topLeft = Offset(at.x - layout.size.width / 2f, at.y - layout.size.height / 2f), alpha = shown)
        }
    }
}

private fun lerp(
    a: Float,
    b: Float,
    t: Float,
) = a + (b - a) * t
