// Ported from Mochy Music by realidkroo. Licensed under GPL-3.0.

package com.metrolist.shared.ui.mochy

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.metrolist.shared.generated.resources.Res
import com.metrolist.shared.generated.resources.mochy_heart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sin
import kotlin.random.Random

/** Which like button a heart flies home to: the one on the page that is showing. */
enum class LikeSlot {
    PLAYER,
    LYRICS,
    QUEUE,
}

private val LikeRed = Color(0xFFFF2D55)
private val LikePink = Color(0xFFFF8FAB)

private val HeartWidth = 104.dp
private const val HeartAspect = 76f / 88f
private const val HeartDrawOversize = 1.4f
private val ButtonGlyphWidth = 18.dp
private val ParticleWidth = 14.dp
private const val ParticleCount = 8

private const val TravelMs = 460
private const val FloatMs = 640
private const val HomeMs = 540

private val TravelEasing = CubicBezierEasing(0.3f, 1.22f, 0.5f, 1f)
private val PopEasing = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)
private val HomeEasing = CubicBezierEasing(0.5f, -0.18f, 0.7f, 1f)

private const val MaxFlights = 4

/**
 * Drives Mochy's like burst animation: a heart leaves the like button (or a double-tap point),
 * arcs up to the center of the player, thumps red with an 8-particle burst, floats back to white,
 * and dives home into the active like button, which bounces as it lands.
 */
@Stable
class LikeBurstState {
    internal class Flight(
        val id: Long,
        val origin: Offset,
        val fromButton: Boolean,
        val seed: Int,
    )

    internal val flights = mutableStateListOf<Flight>()
    private var nextId = 0L

    internal val buttonBounds = HashMap<LikeSlot, Rect>()

    var slotProvider: () -> LikeSlot = { LikeSlot.PLAYER }

    internal val landing = Animatable(1f)

    var likeCount by mutableIntStateOf(0)
        private set

    internal fun homeTarget(): Offset? = buttonBounds[slotProvider()]?.center

    /** Liking sends a heart arcing up out of the button; unliking is quiet. */
    fun likeFromButton(
        slot: LikeSlot,
        liked: Boolean,
        toggleLike: () -> Unit,
    ) {
        if (!liked) {
            buttonBounds[slot]?.let { launch(it.center, fromButton = true) }
            likeCount++
        }
        toggleLike()
    }

    /** Double tap always launches a heart; toggles only when not yet liked. */
    fun likeFromDoubleTap(
        rootPosition: Offset,
        liked: Boolean,
        toggleLike: () -> Unit,
    ) {
        launch(rootPosition, fromButton = false)
        if (!liked) {
            likeCount++
            toggleLike()
        }
    }

    fun clear() {
        flights.clear()
    }

    private fun launch(
        origin: Offset,
        fromButton: Boolean,
    ) {
        if (flights.size >= MaxFlights) flights.removeAt(0)
        flights += Flight(nextId++, origin, fromButton, Random.nextInt())
    }
}

/**
 * Marks a like button as the home of its [slot]: hearts launch from and land on it, and it
 * bounces when one lands.
 */
fun Modifier.likeButtonTarget(
    state: LikeBurstState?,
    slot: LikeSlot,
): Modifier =
    if (state == null) {
        this
    } else {
        this
            .onGloballyPositioned { state.buttonBounds[slot] = it.boundsInRoot() }
            .graphicsLayer {
                if (state.slotProvider() == slot) {
                    val s = state.landing.value
                    scaleX = s
                    scaleY = s
                }
            }
    }

/** Draws the hearts in flight full-screen above the player without consuming touch events. */
@Composable
fun LikeBurstOverlay(
    state: LikeBurstState,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var rootOffset by remember { mutableStateOf(Offset.Zero) }
    if (state.flights.isEmpty()) return

    val heart = painterResource(Res.drawable.mochy_heart)
    val particle = painterResource(Res.drawable.mochy_heart)

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .onGloballyPositioned { rootOffset = it.positionInRoot() },
    ) {
        for (flight in state.flights) {
            key(flight.id) {
                HeartFlight(
                    state = state,
                    flight = flight,
                    heart = heart,
                    particle = particle,
                    rootOffset = { rootOffset },
                    bounceScope = scope,
                    onDone = { state.flights.remove(flight) },
                )
            }
        }
    }
}

private class LikeParticle(
    val dx: Float,
    val dy: Float,
    val reach: Dp,
    val scale: Float,
    val spin: Float,
    val color: Color,
)

@Composable
private fun HeartFlight(
    state: LikeBurstState,
    flight: LikeBurstState.Flight,
    heart: Painter,
    particle: Painter,
    rootOffset: () -> Offset,
    bounceScope: CoroutineScope,
    onDone: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val glyphScale = ButtonGlyphWidth / HeartWidth

    val travel = remember { Animatable(0f) }
    val scale = remember { Animatable(if (flight.fromButton) glyphScale else 0f) }
    val redness = remember { Animatable(0f) }
    val burst = remember { Animatable(0f) }
    val floatProgress = remember { Animatable(0f) }
    val home = remember { Animatable(0f) }

    val random = remember { Random(flight.seed) }
    val side = remember { if (random.nextBoolean()) 1f else -1f }
    val particles =
        remember {
            val palette = listOf(LikeRed, LikePink, Color.White)
            List(ParticleCount) { i ->
                val angle = i * 2f * PI.toFloat() / ParticleCount + (random.nextFloat() - 0.5f) * 0.5f
                LikeParticle(
                    dx = cos(angle),
                    dy = sin(angle),
                    reach = (44 + random.nextInt(36)).dp,
                    scale = 0.6f + random.nextFloat() * 0.5f,
                    spin = (random.nextFloat() - 0.5f) * 70f,
                    color = palette[i % palette.size],
                )
            }
        }

    LaunchedEffect(Unit) {
        coroutineScope {
            if (!flight.fromButton) {
                haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
            }

            val pop = launch { scale.animateTo(1f, tween(TravelMs, easing = PopEasing)) }
            travel.animateTo(1f, tween(TravelMs, easing = TravelEasing))
            pop.join()

            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
            launch { redness.animateTo(1f, tween(110)) }
            launch { burst.animateTo(1f, tween(620, easing = LinearOutSlowInEasing)) }
            scale.animateTo(1.32f, tween(110, easing = LinearOutSlowInEasing))
            launch { scale.animateTo(1f, spring(dampingRatio = 0.3f, stiffness = 360f)) }

            launch {
                delay(160)
                redness.animateTo(0f, tween(520))
            }
            floatProgress.animateTo(1f, tween(FloatMs, easing = LinearEasing))

            home.animateTo(1f, tween(HomeMs, easing = HomeEasing))
            haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
            bounceScope.launch {
                state.landing.snapTo(1f)
                state.landing.animateTo(1.3f, tween(90, easing = LinearOutSlowInEasing))
                state.landing.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = 520f))
            }
        }
        onDone()
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val offset = rootOffset()
        val origin = flight.origin - offset
        val center = Offset(size.width / 2f, size.height * 0.44f)
        val heartW = HeartWidth.toPx()

        val travelControl =
            Offset(
                (origin.x + center.x) / 2f + side * 48.dp.toPx(),
                min(origin.y, center.y) - 110.dp.toPx(),
            )
        val t = travel.value
        val inPos = quadBezier(origin, travelControl, center, t)

        val f = floatProgress.value

        fun bobAt(v: Float) =
            Offset(
                sin(v * 2f * PI.toFloat() * 1.25f) * 7.dp.toPx() * side,
                -sin(v * 2f * PI.toFloat()) * 9.dp.toPx() - v * 16.dp.toPx(),
            )
        val floatPos = center + bobAt(f)
        val floatEnd = center + bobAt(1f)

        val target = state.homeTarget()?.minus(offset) ?: origin
        val h = home.value
        val homeControl = Offset(lerp(floatEnd.x, target.x, 0.25f), floatEnd.y - 90.dp.toPx())
        val pos =
            when {
                h != 0f -> quadBezier(floatEnd, homeControl, target, h)
                f > 0f -> floatPos
                else -> inPos
            }

        val wobble = sin(f * 2f * PI.toFloat() * 1.5f) * 8f
        val rotation =
            when {
                h != 0f -> lerp(wobble, 0f, h) + sin(h * PI.toFloat()) * 16f * sign(target.x - floatEnd.x)
                f > 0f -> wobble
                else -> lerp(-22f * side, 0f, t.coerceIn(0f, 1f))
            }

        val homeShrink = lerp(1f, glyphScale, h * h)
        val s = scale.value * homeShrink
        val squash = (scale.value - 1f).coerceAtLeast(0f)
        val sx = s * (1f + squash * 0.28f)
        val sy = s * (1f - squash * 0.14f)
        val alpha = 1f - ((h - 0.84f) / 0.16f).coerceIn(0f, 1f)
        val red = redness.value
        val color = lerp(Color.White, LikeRed, red)

        drawLikeBurstRing(center, heartW, particles, particle, burst.value)

        if (alpha > 0f && s > 0f) {
            drawCircle(
                brush =
                    Brush.radialGradient(
                        listOf(Color.Black.copy(alpha = 0.22f * alpha), Color.Transparent),
                        center = pos + Offset(0f, 8.dp.toPx() * s),
                        radius = heartW * 0.75f * s,
                    ),
                radius = heartW * 0.75f * s,
                center = pos + Offset(0f, 8.dp.toPx() * s),
            )
            if (red > 0.01f) {
                drawCircle(
                    brush =
                        Brush.radialGradient(
                            listOf(LikeRed.copy(alpha = 0.45f * red * alpha), Color.Transparent),
                            center = pos,
                            radius = heartW * 0.95f * s,
                        ),
                    radius = heartW * 0.95f * s,
                    center = pos,
                )
            }

            val drawW = heartW * HeartDrawOversize
            val drawH = drawW * HeartAspect
            withTransform({
                translate(pos.x, pos.y)
                rotate(rotation, pivot = Offset.Zero)
                scale(sx / HeartDrawOversize, sy / HeartDrawOversize, pivot = Offset.Zero)
            }) {
                translate(-drawW / 2f, -drawH / 2f) {
                    with(heart) { draw(Size(drawW, drawH), alpha = alpha, colorFilter = ColorFilter.tint(color)) }
                }
            }
        }
    }
}

private fun DrawScope.drawLikeBurstRing(
    center: Offset,
    heartW: Float,
    particles: List<LikeParticle>,
    particle: Painter,
    progress: Float,
) {
    if (progress <= 0f || progress >= 1f) return
    val out = 1f - (1f - progress) * (1f - progress) * (1f - progress)
    val fade = 1f - progress

    drawCircle(
        color = lerp(LikeRed, Color.White, progress * 0.6f).copy(alpha = 0.7f * fade),
        radius = heartW * 0.5f * lerp(0.7f, 1.9f, out),
        center = center,
        style = Stroke(width = lerp(5.dp.toPx(), 0.5.dp.toPx(), progress)),
    )

    val w = ParticleWidth.toPx()
    val h = w * HeartAspect
    val start = heartW * 0.32f
    for (p in particles) {
        val distance = start + p.reach.toPx() * out
        val at = center + Offset(p.dx * distance, p.dy * distance)
        val s = p.scale * (1f - progress * 0.5f)
        withTransform({
            translate(at.x, at.y)
            rotate(p.spin * progress, pivot = Offset.Zero)
            scale(s, s, pivot = Offset.Zero)
        }) {
            translate(-w / 2f, -h / 2f) {
                with(particle) { draw(Size(w, h), alpha = fade, colorFilter = ColorFilter.tint(p.color)) }
            }
        }
    }
}

private fun quadBezier(
    a: Offset,
    control: Offset,
    b: Offset,
    t: Float,
): Offset {
    val u = 1f - t
    return Offset(
        u * u * a.x + 2f * u * t * control.x + t * t * b.x,
        u * u * a.y + 2f * u * t * control.y + t * t * b.y,
    )
}
