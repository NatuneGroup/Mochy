/*
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

// Ported from Mochy Music by realidkroo (github.com/realidkroo/mochy-music). Licensed under GPL-3.0.

package com.metrolist.shared.ui.settings.transition

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.metrolist.shared.settings.CrossfadeStyle
import com.metrolist.shared.ui.audiolab.LevelFollower
import com.metrolist.shared.ui.audiolab.rememberLabTime
import com.metrolist.shared.ui.audiolab.rememberOnScreen
import com.metrolist.shared.ui.audiolab.rememberPicturesHeld
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** the old song's waveform narrows into the crossing while the next one opens out of it */
@Composable
internal fun TransitionPicture(
    style: CrossfadeStyle,
    overlap: Float,
    active: Boolean,
) {
    val span by animateFloatAsState(overlap, spring(dampingRatio = 0.85f, stiffness = 200f), label = "overlap")
    val on by animateFloatAsState(if (active) 1f else 0f, tween(500), label = "active")
    val onScreen = rememberOnScreen()
    val held = rememberPicturesHeld(onScreen)
    val time = rememberLabTime(held) { active }
    val level = remember { LevelFollower() }
    val tools = remember { PictureTools() }

    Canvas(Modifier.fillMaxSize().trackPictureOnScreen(onScreen)) {
        level.update(0.016f)
        tools.fitTo(density)
        val left = (size.width * 0.06f).coerceIn(10.dp.toPx(), 22.dp.toPx())
        val right = size.width - left
        val wide = right - left
        val shape =
            PictureShape(
                left = left,
                right = right,
                cy = size.height / 2f,
                half = size.height * 0.3f,
                fadeStart = left + wide * (1f - span) / 2f,
                fade = (wide * span).coerceAtLeast(1f),
                manual = style == CrossfadeStyle.MANUAL,
            )
        // a soft light drifts along the songs while this blend is the one picked
        val sweep = left + ((time.value * 0.16f) % 1f * 1.3f - 0.15f) * wide
        val breath = 0.94f + 0.1f * level.level * on

        drawWaveBars(shape, sweep, glow = on, breath = breath)
        drawCrossLines(tools, shape, alpha = 0.5f + 0.3f * on)
    }
}

// where the picture sits and how wide the blend is, so each layer works from the same numbers
private class PictureShape(
    val left: Float,
    val right: Float,
    val cy: Float,
    val half: Float,
    val fadeStart: Float,
    val fade: Float,
    val manual: Boolean,
) {
    val mid = left + (right - left) / 2f

    // 1 while only the old song plays, -1 once only the new one does, eased so the lines leave and land flat
    fun opening(x: Float): Float {
        val p = ((x - fadeStart) / fade).coerceIn(0f, 1f)
        val eased = if (manual) 0.35f * p + 0.65f * smoothStep(p) else smootherStep(p)
        return 1f - 2f * eased
    }
}

// paths and strokes kept between frames so a moving picture doesn't build them again every frame
private class PictureTools {
    val upper = Path()
    val lower = Path()
    var line = Stroke(1f)
        private set
    var haze = Stroke(1f)
        private set
    private var fittedDensity = 0f

    fun fitTo(density: Float) {
        if (density == fittedDensity) return
        fittedDensity = density
        line = Stroke(1.6f * density, cap = StrokeCap.Round)
        haze = Stroke(5f * density, cap = StrokeCap.Round)
    }
}

// the two songs as rounded bars filling the bow tie, dimmer and shorter as each one fades
private fun DrawScope.drawWaveBars(
    shape: PictureShape,
    sweep: Float,
    glow: Float,
    breath: Float,
) {
    val step = 4.5.dp.toPx()
    val barWidth = 1.8.dp.toPx()
    val wide = shape.right - shape.left
    val count = ((wide - step) / step).toInt()
    val start = shape.left + (wide - count * step) / 2f
    val sweepWidth = wide * 0.08f
    val tiny = 0.8.dp.toPx()
    for (i in 0..count) {
        val x = start + i * step
        val u = (x - shape.left) / wide
        val open = abs(shape.opening(x))
        val loud = barLevel(i, if (x < shape.mid) 0f else 2.9f)
        val near = if (glow > 0.01f) exp(-square((x - sweep) / sweepWidth)) * glow else 0f
        // the ends fade out instead of stopping on a hard bar
        val edge = smoothStep((u / 0.08f).coerceIn(0f, 1f)) * smoothStep(((1f - u) / 0.08f).coerceIn(0f, 1f))
        val reach = max(shape.half * open * loud * (breath + 0.14f * near), tiny)
        val alpha = ((0.22f + 0.5f * open) * edge + 0.35f * near).coerceAtMost(1f)
        drawLine(Color.White.copy(alpha = alpha), Offset(x, shape.cy - reach), Offset(x, shape.cy + reach), barWidth, StrokeCap.Round)
    }
}

// the volume as two smooth lines crossing in the middle, with a faint haze under each so they read soft
private fun DrawScope.drawCrossLines(
    tools: PictureTools,
    shape: PictureShape,
    alpha: Float,
) {
    drawCrossLine(tools, tools.upper, shape, sign = 1f, alpha = alpha)
    drawCrossLine(tools, tools.lower, shape, sign = -1f, alpha = alpha)
}

private fun DrawScope.drawCrossLine(
    tools: PictureTools,
    path: Path,
    shape: PictureShape,
    sign: Float,
    alpha: Float,
) {
    val step = 1.5.dp.toPx()
    path.reset()
    var x = shape.left
    path.moveTo(x, shape.cy - sign * shape.half * shape.opening(x))
    while (x < shape.right) {
        x = min(x + step, shape.right)
        path.lineTo(x, shape.cy - sign * shape.half * shape.opening(x))
    }
    drawPath(path, Color.White.copy(alpha = alpha * 0.1f), style = tools.haze)
    drawPath(path, Color.White.copy(alpha = alpha), style = tools.line)
}

// a made-up but steady song shape: a slow swell, a gentler ripple and a little grain so no two bars match
private fun barLevel(
    index: Int,
    seed: Float,
): Float {
    val swell = 0.5f + 0.5f * sin(index * 0.31f + seed)
    val ripple = 0.5f + 0.5f * sin(index * 0.83f + seed * 1.7f)
    return 0.38f + 0.36f * swell + 0.16f * ripple + 0.1f * grain(index + (seed * 100f).toInt())
}

private fun grain(index: Int): Float {
    var h = index * 374761393 + 668265263
    h = (h xor (h ushr 13)) * 1274126177
    return ((h xor (h ushr 16)) and 0xFFFF) / 65535f
}

private fun square(x: Float) = x * x

private fun smoothStep(x: Float) = x * x * (3f - 2f * x)

private fun smootherStep(x: Float) = x * x * x * (x * (x * 6f - 15f) + 10f)
