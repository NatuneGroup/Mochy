// Ported and adapted from Mochy Music by realidkroo. Licensed under GPL-3.0.

package com.metrolist.shared.ui.player.mochy.ipod

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.em
import com.metrolist.shared.ui.theme.mochyInterFontFamily
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.PI
import kotlin.math.atan2

@Stable
internal interface IpodWheelActions {
    fun rotate(steps: Int): Boolean

    val accelerates: Boolean

    fun menu()

    fun menuHeld()

    fun center()

    fun next()

    fun previous()

    fun playPause()

    fun scan(forward: Boolean)
}

@Composable
internal fun IpodClickWheel(
    actions: IpodWheelActions,
    haptics: IpodHaptics,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val current by rememberUpdatedState(actions)
    val colors = LocalIpodLook.current.wheel
    val scope = rememberCoroutineScope()
    val glow = remember { Animatable(0f) }
    var glowZone by remember { mutableStateOf<RingZone?>(null) }
    val measurer = rememberTextMeasurer()
    val inter = mochyInterFontFamily()
    val light: (RingZone?) -> Unit = { zone ->
        glowZone = zone
        scope.launch { glow.snapTo(1f) }
    }
    val dim: () -> Unit = { scope.launch { glow.animateTo(0f, GlowFade) } }

    Box(
        modifier =
            modifier
                .graphicsLayer()
                .pointerInput(enabled, haptics) {
                    if (!enabled) return@pointerInput
                    val spinner = Spinner()
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val middle = Offset(size.width / 2f, size.height / 2f)
                        val outer = minOf(size.width, size.height) / 2f
                        val from = down.position - middle
                        if (from.getDistance() > outer) return@awaitEachGesture
                        down.consume()

                        if (from.getDistance() < outer * CENTER_RATIO) {
                            light(null)
                            haptics.press()
                            val lifted = awaitLift(down.id)
                            dim()
                            if (lifted) {
                                haptics.release()
                                current.center()
                            }
                            return@awaitEachGesture
                        }

                        val zone = ringZone(from)
                        light(zone)
                        val slop = viewConfiguration.touchSlop
                        val notch: (Int, Long) -> Unit = { direction, at ->
                            val steps = spinner.steps(direction, at, current.accelerates)
                            if (current.rotate(steps)) haptics.tick()
                        }
                        val spinning: () -> Unit = { dim() }
                        try {
                            val start = withTimeoutOrNull(HOLD_MS) { awaitRingStart(down.id, down.position, slop) }
                            when {
                                start == RingStart.Tap -> tap(zone, haptics, current)
                                start == RingStart.Spin -> {
                                    spinning()
                                    spin(down.id, middle, outer, down.position, notch)
                                }
                                start == RingStart.Cancel -> Unit
                                zone == RingZone.NEXT || zone == RingZone.PREVIOUS -> {
                                    haptics.press()
                                    scanWhileHeld(down.id) {
                                        current.scan(forward = zone == RingZone.NEXT)
                                        haptics.tick()
                                    }
                                    haptics.release()
                                }
                                zone == RingZone.MENU ->
                                    when (withTimeoutOrNull(MENU_HOLD_MORE_MS) { awaitRingStart(down.id, down.position, slop) }) {
                                        RingStart.Tap -> tap(zone, haptics, current)
                                        RingStart.Spin -> {
                                            spinning()
                                            spin(down.id, middle, outer, down.position, notch)
                                        }
                                        RingStart.Cancel -> Unit
                                        null -> {
                                            haptics.press()
                                            current.menuHeld()
                                            awaitLift(down.id)
                                        }
                                    }
                                else ->
                                    when (awaitRingStart(down.id, down.position, slop)) {
                                        RingStart.Tap -> tap(zone, haptics, current)
                                        RingStart.Spin -> {
                                            spinning()
                                            spin(down.id, middle, outer, down.position, notch)
                                        }
                                        RingStart.Cancel -> Unit
                                    }
                            }
                        } finally {
                            dim()
                        }
                    }
                }.drawWithCache {
                    val radius = size.minDimension / 2f
                    val middle = Offset(size.width / 2f, size.height / 2f)
                    val label =
                        measurer.measure(
                            "MENU",
                            TextStyle(
                                color = colors.ink,
                                fontSize = (radius * 0.088f).toSp(),
                                fontWeight = FontWeight.Black,
                                fontFamily = inter,
                                letterSpacing = 0.04.em,
                            ),
                        )
                    val labelAt = Offset(middle.x - label.size.width / 2f, middle.y - radius * 0.82f - label.size.height / 2f)
                    val iconHeight = radius * 0.085f
                    val next = skipIcon(Offset(middle.x + radius * 0.80f, middle.y), iconHeight, forward = true)
                    val previous = skipIcon(Offset(middle.x - radius * 0.80f, middle.y), iconHeight, forward = false)
                    val play = playPauseIcon(Offset(middle.x, middle.y + radius * 0.80f), iconHeight * 1.05f)
                    val hairline = Stroke(width = 1.2f)
                    val sectors = RingZone.entries.associateWith { ringSector(it, middle, radius, radius * CENTER_RATIO) }
                    onDrawBehind {
                        drawCircle(colors.face, radius, middle)
                        drawCircle(colors.edge, radius - 0.6f, middle, style = hairline)
                        val lit = glow.value
                        val zone = glowZone
                        if (lit > 0.001f && zone != null) {
                            drawPath(sectors.getValue(zone), colors.pressGlow.copy(alpha = colors.pressGlow.alpha * lit))
                        }
                        drawText(label, topLeft = labelAt)
                        drawPath(next, colors.ink)
                        drawPath(previous, colors.ink)
                        drawPath(play, colors.ink)
                        val centerLit = if (zone == null) lit else 0f
                        drawCircle(lerp(colors.center, colors.centerPressed, centerLit), radius * CENTER_RATIO, middle)
                        drawCircle(colors.edge, radius * CENTER_RATIO - 0.6f, middle, style = hairline)
                    }
                },
    )
}

private fun skipIcon(
    center: Offset,
    height: Float,
    forward: Boolean,
): Path {
    val unit = height / 16f
    val width = 33f * unit
    val left = center.x - width / 2f
    val top = center.y - height / 2f

    fun x(v: Float) = if (forward) left + v * unit else left + width - v * unit

    fun y(v: Float) = top + v * unit

    return Path().apply {
        moveTo(x(4f), y(1.07f))
        lineTo(x(16f), y(8f))
        lineTo(x(4f), y(14.93f))
        close()
        moveTo(x(17f), y(1.07f))
        lineTo(x(29f), y(8f))
        lineTo(x(17f), y(14.93f))
        close()
        moveTo(x(29f), y(0.5f))
        lineTo(x(33f), y(0.5f))
        lineTo(x(33f), y(15.5f))
        lineTo(x(29f), y(15.5f))
        close()
    }
}

private fun playPauseIcon(
    center: Offset,
    height: Float,
): Path {
    val unit = height / 18f
    val left = center.x - 39f * unit / 2f
    val top = center.y - height / 2f

    fun x(v: Float) = left + v * unit

    fun y(v: Float) = top + v * unit

    return Path().apply {
        moveTo(x(4.5f), y(1.2f))
        lineTo(x(18f), y(9f))
        lineTo(x(4.5f), y(16.8f))
        close()
        moveTo(x(26f), y(1f))
        lineTo(x(30f), y(1f))
        lineTo(x(30f), y(17f))
        lineTo(x(26f), y(17f))
        close()
        moveTo(x(35f), y(1f))
        lineTo(x(39f), y(1f))
        lineTo(x(39f), y(17f))
        lineTo(x(35f), y(17f))
        close()
    }
}

private fun ringSector(
    zone: RingZone,
    middle: Offset,
    outer: Float,
    inner: Float,
): Path {
    val start =
        when (zone) {
            RingZone.MENU -> -135f
            RingZone.NEXT -> -45f
            RingZone.PLAY -> 45f
            RingZone.PREVIOUS -> 135f
        }
    val outerRect = Rect(middle, outer)
    val innerRect = Rect(middle, inner)
    return Path().apply {
        arcTo(outerRect, start, 90f, forceMoveTo = true)
        arcTo(innerRect, start + 90f, -90f, forceMoveTo = false)
        close()
    }
}

private enum class RingZone { MENU, NEXT, PLAY, PREVIOUS }

private enum class RingStart { Tap, Spin, Cancel }

private fun ringZone(from: Offset): RingZone {
    val degrees = ((atan2(from.y, from.x) * (180.0 / PI) + 90.0 + 360.0) % 360.0)
    return when {
        degrees < 45.0 || degrees >= 315.0 -> RingZone.MENU
        degrees < 135.0 -> RingZone.NEXT
        degrees < 225.0 -> RingZone.PLAY
        else -> RingZone.PREVIOUS
    }
}

private fun tap(
    zone: RingZone,
    haptics: IpodHaptics,
    actions: IpodWheelActions,
) {
    haptics.click()
    when (zone) {
        RingZone.MENU -> actions.menu()
        RingZone.NEXT -> actions.next()
        RingZone.PLAY -> actions.playPause()
        RingZone.PREVIOUS -> actions.previous()
    }
}

private suspend fun AwaitPointerEventScope.awaitRingStart(
    id: PointerId,
    from: Offset,
    slop: Float,
): RingStart {
    while (true) {
        val event = awaitPointerEvent()
        val change = event.changes.firstOrNull { it.id == id } ?: return RingStart.Cancel
        event.changes.forEach { it.consume() }
        if (!change.pressed) return RingStart.Tap
        if ((change.position - from).getDistance() > slop) return RingStart.Spin
    }
}

private suspend fun AwaitPointerEventScope.awaitLift(id: PointerId): Boolean {
    while (true) {
        val event = awaitPointerEvent()
        val change = event.changes.firstOrNull { it.id == id } ?: return false
        event.changes.forEach { it.consume() }
        if (!change.pressed) return true
    }
}

private suspend fun AwaitPointerEventScope.scanWhileHeld(
    id: PointerId,
    onStep: () -> Unit,
) {
    while (true) {
        onStep()
        if (withTimeoutOrNull(SCAN_REPEAT_MS) { awaitLift(id) } != null) return
    }
}

private suspend fun AwaitPointerEventScope.spin(
    id: PointerId,
    middle: Offset,
    outer: Float,
    start: Offset,
    onNotch: (direction: Int, at: Long) -> Unit,
) {
    var last: Float? = (start - middle).let { atan2(it.y, it.x) }
    var travelled = 0f
    while (true) {
        val event = awaitPointerEvent()
        val change = event.changes.firstOrNull { it.id == id } ?: return
        event.changes.forEach { it.consume() }
        if (!change.pressed) return
        val p = change.position - middle
        if (p.getDistance() < outer * DEAD_ZONE) {
            last = null
            continue
        }
        val angle = atan2(p.y, p.x)
        val before = last
        last = angle
        if (before == null) continue
        travelled += wrapAngle(angle - before)
        while (travelled >= NOTCH) {
            travelled -= NOTCH
            onNotch(1, change.uptimeMillis)
        }
        while (travelled <= -NOTCH) {
            travelled += NOTCH
            onNotch(-1, change.uptimeMillis)
        }
    }
}

private fun wrapAngle(radians: Float): Float {
    var a = radians
    while (a > PI) a -= (2 * PI).toFloat()
    while (a < -PI) a += (2 * PI).toFloat()
    return a
}

private class Spinner {
    private var lastAt = 0L
    private var lastDirection = 0
    private var streakStart = 0L

    fun steps(
        direction: Int,
        at: Long,
        accelerates: Boolean,
    ): Int {
        if (direction != lastDirection || at - lastAt > STREAK_GAP_MS) streakStart = at
        lastAt = at
        lastDirection = direction
        if (!accelerates) return direction
        val seconds = ((at - streakStart) / 1000L).toInt().coerceIn(0, 4)
        return direction * (1 shl seconds)
    }
}

internal const val CENTER_RATIO = 0.467f
private const val DEAD_ZONE = 0.22f
private const val NOTCH = (2 * PI / 24).toFloat()
private const val HOLD_MS = 450L
private const val MENU_HOLD_MORE_MS = 450L
private const val SCAN_REPEAT_MS = 180L
private const val STREAK_GAP_MS = 160L
private val GlowFade = tween<Float>(260)
