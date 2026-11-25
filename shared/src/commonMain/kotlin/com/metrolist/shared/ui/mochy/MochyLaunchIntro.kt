// Ported from Mochy Music by realidkroo (github.com/realidkroo/mochy-music). Licensed under GPL-3.0.

// A cold start from the launcher, as in Mochy: once the splash steps aside the Mochy mark stands on black, then flies
// into Home's logo with a blur through the middle of the move while Home's pieces rise into place under it.

package com.metrolist.shared.ui.mochy

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.metrolist.shared.generated.resources.Res
import com.metrolist.shared.generated.resources.mochy_mark
import com.metrolist.shared.ui.utils.supportsNativeBlur
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.compose.resources.painterResource
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

// the flight: still, a gentle start, a long settle into the logo; scale leads the move a little
private const val FLIGHT_MILLIS = 660
private val MoveEasing = CubicBezierEasing(0.55f, 0f, 0.1f, 1f)
private val ScaleEasing = CubicBezierEasing(0.45f, 0f, 0.1f, 1f)

// Home's pieces rise one a little after the other
private const val RISE_DELAY_MILLIS = 80
private const val RISE_MILLIS = 520
private const val RISE_STAGGER_MILLIS = 50
private const val RISE_ORDERS = 4
private const val RISE_TOTAL_MILLIS = RISE_MILLIS + (RISE_ORDERS - 1) * RISE_STAGGER_MILLIS
private val RiseEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
private val RiseDistance = 56.dp

// the splash already waited for Home, so its logo is normally placed by the first frame; past this the mark just fades
private const val TARGET_TIMEOUT_MILLIS = 1_200L
private val StartMarkWidth = 96.dp
private const val MARK_ASPECT = 139f / 128f
private val BlurPeak = 8.dp

@Stable
class MochyLaunchIntroState internal constructor(
    val plays: Boolean,
) {
    internal val flight = Animatable(if (plays) 0f else 1f)
    internal val rise = Animatable(if (plays) 0f else 1f)

    /** Home's logo in root coordinates: where the mark lands */
    internal var markBounds by mutableStateOf<Rect?>(null)

    /** the mark has reached the logo; the real logo shows from this frame on */
    internal var landed by mutableStateOf(!plays)

    /** everything has settled; the overlay and every launch modifier step out of the way */
    var done by mutableStateOf(!plays)
        internal set

    internal fun riseAt(order: Int): Float {
        val millis = rise.value * RISE_TOTAL_MILLIS - order * RISE_STAGGER_MILLIS
        return RiseEasing.transform((millis / RISE_MILLIS).coerceIn(0f, 1f))
    }
}

val LocalMochyLaunchIntro = staticCompositionLocalOf<MochyLaunchIntroState?> { null }

@Composable
fun rememberMochyLaunchIntroState(plays: Boolean): MochyLaunchIntroState = remember { MochyLaunchIntroState(plays) }

/** Home's logo: reports where it is and stays hidden until the flying mark gets there, then takes over in that frame */
@Composable
fun Modifier.mochyLaunchMarkTarget(): Modifier {
    val state = LocalMochyLaunchIntro.current ?: return this
    if (state.done) return this
    return this
        .onGloballyPositioned { state.markBounds = it.boundsInRoot() }
        .graphicsLayer { alpha = if (state.landed) 1f else 0f }
}

/** a piece of Home that rises into place during the intro, [order] staggering it after the ones above; read in draw only */
@Composable
fun Modifier.mochyLaunchRise(
    order: Int,
    distance: Dp = RiseDistance,
): Modifier {
    val state = LocalMochyLaunchIntro.current ?: return this
    if (state.done) return this
    return graphicsLayer {
        val e = state.riseAt(order)
        alpha = e
        translationY = (1f - e) * distance.toPx()
        compositingStrategy = CompositingStrategy.ModulateAlpha
    }
}

/** Sits over the whole app for the intro: the splash's black and the mark flying into Home's logo. */
@Composable
fun MochyLaunchIntroOverlay(state: MochyLaunchIntroState) {
    if (state.done) return
    var hasTarget by remember { mutableStateOf(true) }
    LaunchedEffect(state) {
        hasTarget = withTimeoutOrNull(TARGET_TIMEOUT_MILLIS) { snapshotFlow { state.markBounds != null }.first { it } } != null
        // whatever just came up draws its first heavy frame while the mark still stands
        withFrameNanos { }
        withFrameNanos { }
        coroutineScope {
            launch { state.rise.animateTo(1f, tween(RISE_TOTAL_MILLIS, RISE_DELAY_MILLIS, LinearEasing)) }
            state.flight.animateTo(1f, tween(FLIGHT_MILLIS, easing = LinearEasing))
            state.landed = true
        }
        state.done = true
    }
    val blur = remember { supportsNativeBlur() }
    // where the standing mark's top-left sits, worked out in layout and read by the flight
    val start = remember { FloatArray(2) }

    Box(Modifier.fillMaxSize()) {
        // the black fades off Home, and keeps taps off it until the mark has landed
        Spacer(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                        }
                    }
                }.drawBehind {
                    val backdrop = 1f - smoothstep(0.18f, 0.7f, state.flight.value)
                    if (backdrop > 0f) drawRect(Color.Black.copy(alpha = backdrop))
                },
        )
        Icon(
            painter = painterResource(Res.drawable.mochy_mark),
            contentDescription = null,
            tint = Color.White,
            modifier =
                Modifier
                    .layout { measurable, constraints ->
                        val width = StartMarkWidth.roundToPx()
                        val height = (width * MARK_ASPECT).roundToInt()
                        val placeable = measurable.measure(Constraints.fixed(width, height))
                        val left = (constraints.maxWidth - width) / 2
                        val top = (constraints.maxHeight - height) / 2
                        start[0] = left.toFloat()
                        start[1] = top.toFloat()
                        layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(left, top) }
                    }.graphicsLayer {
                        val t = state.flight.value
                        val target = state.markBounds
                        transformOrigin = TransformOrigin(0f, 0f)
                        if (target != null && hasTarget) {
                            val move = MoveEasing.transform(t)
                            val grow = ScaleEasing.transform(t)
                            val scale = 1f + (target.width / size.width - 1f) * grow
                            // scaled from its top-left, which slides onto the logo's top-left
                            translationX = (target.left - start[0]) * move
                            translationY = (target.top - start[1]) * move
                            scaleX = scale
                            scaleY = scale
                        } else {
                            // no Home logo to land on (another start page): the mark just eases out where it stands
                            alpha = 1f - t
                            val s = 1f - 0.2f * t
                            scaleX = s
                            scaleY = s
                            transformOrigin = TransformOrigin.Center
                        }
                        val radius = BlurPeak.toPx() * sin(PI.toFloat() * t)
                        renderEffect = if (blur && radius > 0.5f) BlurEffect(radius, radius, TileMode.Decal) else null
                    },
        )
    }
}

private fun smoothstep(
    from: Float,
    to: Float,
    x: Float,
): Float {
    val t = ((x - from) / (to - from)).coerceIn(0f, 1f)
    return t * t * (3 - 2 * t)
}
