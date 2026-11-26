/*
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

// Ported from Mochy Music by realidkroo (github.com/realidkroo/mochy-music). Licensed under GPL-3.0.

package com.metrolist.shared.ui.settings.transition

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.metrolist.shared.ui.audiolab.rememberLabTime
import com.metrolist.shared.ui.audiolab.rememberOnScreen
import com.metrolist.shared.ui.audiolab.rememberPicturesHeld

// the transport fade's limits, as the Playback slider has always used them
internal const val TransportFadeMinSeconds = 0.5f
internal const val TransportFadeMaxSeconds = 10f

/** the white line is the volume through a fade; off, the fade is a hard cut. A playhead rides the curve while it's enabled */
@Composable
internal fun TransportFadePicture(enabled: Boolean, seconds: Float) {
    val softness by animateFloatAsState(if (enabled) 1f else 0f, spring(dampingRatio = 0.85f, stiffness = 260f), label = "softness")
    val span by animateFloatAsState(
        0.12f + 0.5f * (seconds - TransportFadeMinSeconds) / (TransportFadeMaxSeconds - TransportFadeMinSeconds),
        spring(dampingRatio = 0.85f, stiffness = 220f),
        label = "span",
    )
    val onScreen = rememberOnScreen()
    val held = rememberPicturesHeld(onScreen)
    val time = rememberLabTime(held) { enabled }

    Canvas(Modifier.fillMaxSize().trackPictureOnScreen(onScreen)) {
        val left = 24.dp.toPx()
        val right = size.width - 24.dp.toPx()
        val top = size.height * 0.27f
        val bottom = size.height * 0.72f
        val width = right - left
        val fadeWidth = width * span
        val fadeStart = left + (width - fadeWidth) * 0.48f

        fun u(x: Float) = (x - fadeStart) / fadeWidth
        fun white(x: Float): Float {
            val t = u(x).coerceIn(0f, 1f)
            val step = if (u(x) >= 0.5f) 1f else 0f
            return softness * smooth(t) + (1f - softness) * step
        }
        fun yOf(level: Float) = top + (bottom - top) * level

        drawCurve(left, right, Color.White, 1.6.dp.toPx()) { yOf(white(it)) }

        if (enabled) {
            val loop = 1.4f + seconds.coerceAtMost(4f) * 0.35f
            val p = (time.value % loop) / loop
            val x = left + width * p
            val y = yOf(white(x))
            drawLine(Color.White.copy(alpha = 0.14f), Offset(x, top - 14.dp.toPx()), Offset(x, bottom + 14.dp.toPx()), 1.dp.toPx())
            drawCircle(Color.White, 3.dp.toPx(), Offset(x, y))
        }
    }
}

private fun DrawScope.drawCurve(left: Float, right: Float, color: Color, width: Float, y: (Float) -> Float) {
    val path = Path()
    var x = left
    path.moveTo(x, y(x))
    val step = 1.5.dp.toPx()
    while (x < right) {
        x = (x + step).coerceAtMost(right)
        path.lineTo(x, y(x))
    }
    drawPath(path, color, style = Stroke(width, cap = StrokeCap.Round))
}

private fun smooth(x: Float): Float = x * x * (3f - 2f * x)
