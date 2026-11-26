// Ported from Mochy Music by realidkroo. Licensed under GPL-3.0.

package com.metrolist.shared.ui.mochy

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sqrt

private const val MAX_FRAME_NANOS = 25_000_000L

private class CappedFrameClock(
    private val real: MonotonicFrameClock,
) : MonotonicFrameClock {
    private var lastReal = 0L
    private var shown = 0L

    override suspend fun <R> withFrameNanos(onFrame: (frameTimeNanos: Long) -> R): R =
        real.withFrameNanos { now ->
            shown = if (lastReal == 0L) now else shown + (now - lastReal).coerceIn(0L, MAX_FRAME_NANOS)
            lastReal = now
            onFrame(shown)
        }
}

/** Runs animations on frame steps capped at 25 ms so a slow layout frame glides instead of skipping. */
internal suspend fun <T> smoothFrames(block: suspend CoroutineScope.() -> T): T {
    val real = coroutineContext[MonotonicFrameClock] ?: return coroutineScope(block)
    return withContext(CappedFrameClock(real), block)
}

private const val DEFAULT_MAX_TILT = 7f
private const val DEFAULT_PRESSED_SCALE = 0.97f

private val TiltSpring =
    spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = 420f,
    )

fun Modifier.press3D(
    maxTilt: Float = DEFAULT_MAX_TILT,
    pressedScale: Float = DEFAULT_PRESSED_SCALE,
    enabled: Boolean = true,
    origin: TransformOrigin = TransformOrigin.Center,
): Modifier =
    composed {
        if (!enabled) return@composed this

        val scope = rememberCoroutineScope()
        val press = remember { Animatable(0f) }

        var normalized by remember { mutableStateOf(Offset.Zero) }
        var size by remember { mutableStateOf(IntSize.Zero) }

        this
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val down =
                            awaitPointerEvent(PointerEventPass.Initial)
                                .changes
                                .firstOrNull { it.pressed } ?: continue

                        val bounds = this.size
                        if (bounds.width > 0 && bounds.height > 0) {
                            val point = down.position
                            normalized =
                                Offset(
                                    x = ((point.x / bounds.width) * 2f - 1f).coerceIn(-1f, 1f),
                                    y = ((point.y / bounds.height) * 2f - 1f).coerceIn(-1f, 1f),
                                )
                            size = bounds
                        }
                        scope.launch { press.animateTo(1f, TiltSpring) }

                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.changes.none { it.pressed }) break
                        }
                        scope.launch { press.animateTo(0f, TiltSpring) }
                    }
                }
            }.graphicsLayer {
                val t = press.value
                if (t > 0f) {
                    val s = 1f - (1f - pressedScale) * t
                    scaleX = s
                    scaleY = s
                    rotationX = -normalized.y * maxTilt * t
                    rotationY = normalized.x * maxTilt * t
                    cameraDistance = (size.width.takeIf { it > 0 }?.toFloat() ?: 1000f) * 3f
                    transformOrigin = origin
                }
            }
    }

fun Modifier.hover3D(
    maxTilt: Float = DEFAULT_MAX_TILT,
    hoverScale: Float = 1.01f,
    origin: TransformOrigin = TransformOrigin.Center,
): Modifier =
    composed {
        val scope = rememberCoroutineScope()
        val hover = remember { Animatable(0f) }

        var normalized by remember { mutableStateOf(Offset.Zero) }
        var size by remember { mutableStateOf(IntSize.Zero) }

        this
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull() ?: continue
                        when (event.type) {
                            PointerEventType.Enter, PointerEventType.Move -> {
                                val bounds = this.size
                                if (bounds.width > 0 && bounds.height > 0) {
                                    val point = change.position
                                    normalized =
                                        Offset(
                                            x = ((point.x / bounds.width) * 2f - 1f).coerceIn(-1f, 1f),
                                            y = ((point.y / bounds.height) * 2f - 1f).coerceIn(-1f, 1f),
                                        )
                                    size = bounds
                                }
                                scope.launch { hover.animateTo(1f, TiltSpring) }
                            }

                            PointerEventType.Exit -> {
                                scope.launch { hover.animateTo(0f, TiltSpring) }
                            }
                        }
                    }
                }
            }.graphicsLayer {
                val t = hover.value
                if (t > 0f) {
                    val s = 1f + (hoverScale - 1f) * t
                    scaleX = s
                    scaleY = s
                    rotationX = -normalized.y * maxTilt * t
                    rotationY = normalized.x * maxTilt * t
                    cameraDistance = (size.width.takeIf { it > 0 }?.toFloat() ?: 1000f) * 3f
                    transformOrigin = origin
                }
            }
    }

@Composable
fun Modifier.pressBounce(
    interactionSource: InteractionSource,
    pressedScale: Float = 0.88f,
): Modifier {
    val scale = remember { Animatable(1f) }
    LaunchedEffect(interactionSource, pressedScale) {
        var pressed = 0
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> {
                    pressed++
                    launch { scale.animateTo(pressedScale, spring(dampingRatio = 1f, stiffness = 1600f)) }
                }

                is PressInteraction.Release, is PressInteraction.Cancel -> {
                    pressed = (pressed - 1).coerceAtLeast(0)
                    if (pressed == 0) {
                        launch {
                            if (scale.value > pressedScale + 0.03f) scale.animateTo(pressedScale, tween(70))
                            scale.animateTo(1f, spring(dampingRatio = 0.38f, stiffness = 520f))
                        }
                    }
                }
            }
        }
    }
    return graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    }
}

val MochyHighlightColor = Color.White.copy(alpha = 0.12f)

@Immutable
class RoundedHighlight(
    private val cornerRadius: Dp,
    private val spillX: Dp = 0.dp,
    private val spillY: Dp = 0.dp,
    private val color: Color = MochyHighlightColor,
) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode =
        RoundedHighlightNode(interactionSource, cornerRadius, spillX, spillY, color)

    override fun equals(other: Any?): Boolean =
        other is RoundedHighlight &&
            other.cornerRadius == cornerRadius &&
            other.spillX == spillX &&
            other.spillY == spillY &&
            other.color == color

    override fun hashCode(): Int {
        var result = cornerRadius.hashCode()
        result = 31 * result + spillX.hashCode()
        result = 31 * result + spillY.hashCode()
        result = 31 * result + color.hashCode()
        return result
    }
}

private class RoundedHighlightNode(
    private val interactionSource: InteractionSource,
    private val cornerRadius: Dp,
    private val spillX: Dp,
    private val spillY: Dp,
    private val color: Color,
) : Modifier.Node(),
    DrawModifierNode {
    private val strength = Animatable(0f)

    override fun onAttach() {
        coroutineScope.launch {
            val presses = ArrayList<PressInteraction.Press>()
            var focused = false
            var hovered = false
            interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> presses.add(interaction)
                    is PressInteraction.Release -> presses.remove(interaction.press)
                    is PressInteraction.Cancel -> presses.remove(interaction.press)
                    is FocusInteraction.Focus -> focused = true
                    is FocusInteraction.Unfocus -> focused = false
                    is HoverInteraction.Enter -> hovered = true
                    is HoverInteraction.Exit -> hovered = false
                    else -> return@collect
                }
                when {
                    presses.isNotEmpty() || focused -> {
                        launch { strength.animateTo(1f, tween(90)) }
                    }

                    hovered -> {
                        launch { strength.animateTo(0.55f, tween(120)) }
                    }

                    else -> {
                        launch {
                            if (strength.value < 1f && presses.isEmpty() && !hovered) {
                                strength.animateTo(0f, tween(260))
                            } else {
                                strength.animateTo(0f, tween(340))
                            }
                        }
                    }
                }
            }
        }
    }

    override fun ContentDrawScope.draw() {
        val s = strength.value
        if (s > 0f) {
            val dx = spillX.toPx()
            val dy = spillY.toPx()
            drawRoundRect(
                color = color.copy(alpha = color.alpha * s),
                topLeft = Offset(-dx, -dy),
                size = Size(size.width + dx * 2f, size.height + dy * 2f),
                cornerRadius = CornerRadius(cornerRadius.toPx()),
            )
        }
        drawContent()
    }
}

private const val PAGE_MEDIA_TRAVEL = 0.18f
private const val PAGE_TEXT_TRAVEL = 0.42f
private const val PAGE_WIPE_FEATHER = 0.55f
private const val PAGE_WIPE_TRAIL = 0.25f
private const val PAGE_WIPE_CURVE_REACH = 0.6f
private const val PAGE_WIPE_FLAT = 0.04f
private const val PAGE_WIPE_SETTLED = 0.001f
private const val PAGE_WIPE_HIDDEN = 0.999f
private const val PEEK_DIM = 0.5f
private const val PEEK_MEDIA_TRAVEL = 0.12f

private val FeatherEase = floatArrayOf(0f, 0.104f, 0.352f, 0.648f, 0.896f, 1f)

private class PageWipeEdge {
    var incoming = false
    var centerX = 0f
    var centerY = 0f
    var inner = 0f
    var outer = 0f

    fun update(
        d: Float,
        size: Size,
        curve: Float,
    ): Boolean {
        val a = abs(d)
        if (a < PAGE_WIPE_SETTLED || a >= PAGE_WIPE_HIDDEN) return false
        val w = size.width
        val feather = w * PAGE_WIPE_FEATHER
        val progress = if (d > 0f) 1f - d else -d
        val off = w * PAGE_WIPE_CURVE_REACH / curve.coerceIn(PAGE_WIPE_FLAT, 1f)
        centerX = w + off
        centerY = size.height / 2f
        val from = off - feather
        val to = hypot(centerX, centerY)
        val radius = from + (to - from) * progress
        incoming = d > 0f
        if (incoming) {
            inner = radius
            outer = radius + feather
        } else {
            inner = radius - feather * PAGE_WIPE_TRAIL
            outer = inner + feather
        }
        return if (incoming) inner < to else outer > off
    }
}

@Composable
internal fun MochyPageWipe(
    pageOffset: () -> Float,
    curve: () -> Float,
    modifier: Modifier = Modifier,
    peek: Boolean = false,
    // the page is blurred by this much at a full page away, so the switch reads as soft as the reference's
    blur: Dp = 0.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    if (peek) {
        Box(
            modifier =
                modifier
                    .fillMaxSize()
                    .clipToBounds()
                    .drawWithContent {
                        drawContent()
                        val d = abs(pageOffset()).coerceAtMost(1f)
                        if (d > PAGE_WIPE_SETTLED) {
                            drawRect(Color.Black.copy(alpha = PEEK_DIM * d))
                        }
                    },
            content = content,
        )
        return
    }

    val edge = remember { PageWipeEdge() }
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .graphicsLayer {
                    val d = pageOffset()
                    val a = abs(d)
                    translationX = -d * size.width
                    alpha = if (a >= PAGE_WIPE_HIDDEN) 0f else 1f
                    renderEffect =
                        if (blur > 0.dp && a in PAGE_WIPE_SETTLED..PAGE_WIPE_HIDDEN) {
                            val radius = a * blur.toPx()
                            BlurEffect(radius, radius, TileMode.Clamp)
                        } else {
                            null
                        }
                    compositingStrategy =
                        if (a in PAGE_WIPE_SETTLED..PAGE_WIPE_HIDDEN) {
                            CompositingStrategy.Offscreen
                        } else {
                            CompositingStrategy.Auto
                        }
                }.clipToBounds()
                .drawWithContent {
                    drawContent()
                    if (!edge.update(pageOffset(), size, curve())) return@drawWithContent
                    val w = size.width
                    val h = size.height
                    val halfH = h / 2f

                    fun fullyInside(radius: Float): Float =
                        if (radius > halfH) min(w, edge.centerX - sqrt(radius * radius - halfH * halfH)) else w

                    val clearLeft: Float
                    val clearRight: Float
                    val maskLeft: Float
                    val maskRight: Float
                    if (edge.incoming) {
                        clearLeft = 0f
                        clearRight = (edge.centerX - edge.outer).coerceIn(0f, w)
                        maskLeft = clearRight
                        maskRight = fullyInside(edge.inner).coerceIn(maskLeft, w)
                    } else {
                        clearLeft = fullyInside(edge.inner).coerceIn(0f, w)
                        clearRight = w
                        maskLeft = (edge.centerX - edge.outer).coerceIn(0f, clearLeft)
                        maskRight = clearLeft
                    }
                    if (clearRight > clearLeft) {
                        drawRect(
                            color = Color.Black,
                            topLeft = Offset(clearLeft, 0f),
                            size = Size(clearRight - clearLeft, h),
                            blendMode = BlendMode.DstOut,
                        )
                    }
                    if (maskRight > maskLeft && edge.outer > 0f) {
                        val center = Offset(edge.centerX, edge.centerY)
                        val stops =
                            if (edge.incoming) {
                                featherStops(edge.inner / edge.outer, inside = Color.Transparent, outside = Color.Black)
                            } else {
                                featherStops(edge.inner / edge.outer, inside = Color.Black, outside = Color.Transparent)
                            }
                        drawRect(
                            brush = Brush.radialGradient(*stops, center = center, radius = edge.outer),
                            topLeft = Offset(maskLeft, 0f),
                            size = Size(maskRight - maskLeft, h),
                            blendMode = BlendMode.DstOut,
                        )
                    }
                },
        content = content,
    )
}

private fun featherStops(
    start: Float,
    inside: Color,
    outside: Color,
): Array<Pair<Float, Color>> {
    val s = start.coerceIn(0f, 0.999f)
    return Array(FeatherEase.size) { index ->
        val t = index / (FeatherEase.size - 1f)
        (s + (1f - s) * t) to lerp(inside, outside, FeatherEase[index])
    }
}

internal fun Modifier.pageMediaDrift(
    pageOffset: () -> Float,
    peek: Boolean = false,
): Modifier =
    if (peek) {
        graphicsLayer {
            val d = pageOffset().coerceIn(-1f, 1f)
            val scale = 1f + 2f * PEEK_MEDIA_TRAVEL * abs(d)
            scaleX = scale
            scaleY = scale
            translationX = -d * size.width * PEEK_MEDIA_TRAVEL
        }
    } else {
        graphicsLayer {
            val d = pageOffset().coerceIn(-1f, 1f)
            val scale = 1f + 2f * PAGE_MEDIA_TRAVEL * abs(d)
            scaleX = scale
            scaleY = scale
            translationX = d * size.width * PAGE_MEDIA_TRAVEL
        }
    }

internal fun Modifier.pageTextDrift(
    pageOffset: () -> Float,
    peek: Boolean = false,
): Modifier =
    graphicsLayer {
        val d = pageOffset()
        if (peek) {
            alpha = (1f - abs(d) * 0.75f).coerceIn(0f, 1f)
            return@graphicsLayer
        }
        translationX = d * size.width * (PAGE_TEXT_TRAVEL + PAGE_MEDIA_TRAVEL)
        alpha = (1f - abs(d) * 1.9f).coerceIn(0f, 1f)
    }
