// Ported and adapted from Mochy Music by realidkroo. Licensed under GPL-3.0.

package com.metrolist.shared.ui.audiolab

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.metrolist.shared.audiolab.SpatialMode
import com.metrolist.shared.audiolab.SpatialSettings
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** a frame clock and a music follower, shared by every picture */
internal class PictureClock {
    val level = LevelFollower()
    private var lastTime = -1f

    /** seconds between this frame and the one before */
    var dt = 0f
        private set

    /** once per drawn frame, works out dt and keeps the music level current */
    fun tick(t: Float) {
        if (t == lastTime) {
            dt = 0f
            return
        }
        dt = if (lastTime < 0f) 0f else (t - lastTime).coerceIn(0f, 0.05f)
        level.update(dt)
        lastTime = t
    }
}

@Composable
internal fun PictureCanvas(draw: DrawScope.(t: Float, clock: PictureClock) -> Unit) {
    val onScreen = rememberOnScreen()
    val held = rememberPicturesHeld(onScreen)
    val time = rememberLabTime(held)
    val clock = remember { PictureClock() }
    Canvas(Modifier.fillMaxSize().trackOnScreen(onScreen)) {
        val t = time.value
        clock.tick(t)
        draw(t, clock)
    }
}

// ---- spatial: a head from above, and where the sound reaches it from -------------------------

@Composable
fun SpatialPicture(settings: SpatialSettings) {
    val on by animateFloatAsState(if (settings.enabled) settings.amount else 0f, tween(400), label = "on")
    val speakers by animateFloatAsState(if (settings.mode == SpatialMode.Speakers) 1f else 0f, tween(500), label = "mode")
    val angle by animateFloatAsState(settings.speakerAngle, spring(dampingRatio = 0.8f, stiffness = 140f), label = "angle")
    val feed by animateFloatAsState(settings.feedDb, tween(300), label = "feed")
    PictureCanvas { t, clock ->
        val w = size.width
        val h = size.height
        val head = Offset(w / 2f, h * 0.66f)
        val r = 17.dp.toPx()
        val earL = Offset(head.x - r, head.y)
        val earR = Offset(head.x + r, head.y)
        val pulse = clock.level.punch

        // head, nose up, ears
        drawCircle(Color.White.copy(alpha = 0.9f), r, head, style = Stroke(1.6.dp.toPx()))
        drawPath(
            Path().apply {
                moveTo(head.x - 4.dp.toPx(), head.y - r + 1.dp.toPx())
                lineTo(head.x, head.y - r - 6.dp.toPx())
                lineTo(head.x + 4.dp.toPx(), head.y - r + 1.dp.toPx())
            },
            Color.White.copy(alpha = 0.9f),
            style = Stroke(1.6.dp.toPx()),
        )
        drawCircle(Color.White, 3.dp.toPx(), earL)
        drawCircle(Color.White, 3.dp.toPx(), earR)

        // stereo as headphones play it: each side only in its own ear, inside the head
        val inside = 1f - on
        if (inside > 0.01f) {
            drawCircle(Color(0xFF6BBAF2).copy(alpha = 0.7f * inside), 5.dp.toPx() + 2.dp.toPx() * pulse, Offset(head.x - r * 0.45f, head.y))
            drawCircle(Color(0xFFF28BA8).copy(alpha = 0.7f * inside), 5.dp.toPx() + 2.dp.toPx() * pulse, Offset(head.x + r * 0.45f, head.y))
        }

        // virtual speakers out in front, each heard by both ears (the far one later and darker)
        val ring = min(w * 0.36f, h * 0.55f)
        val crossfeedAmount = on * (1f - speakers)
        val speakerAmount = on * speakers
        val rad = degreesToRadians(angle.toDouble())
        val spL = Offset(head.x - (sin(rad) * ring).toFloat(), head.y - (cos(rad) * ring).toFloat())
        val spR = Offset(head.x + (sin(rad) * ring).toFloat(), head.y - (cos(rad) * ring).toFloat())
        if (speakerAmount > 0.01f) {
            listOf(spL to Color(0xFF6BBAF2), spR to Color(0xFFF28BA8)).forEachIndexed { side, (sp, color) ->
                val near = if (side == 0) earL else earR
                val far = if (side == 0) earR else earL
                flowLine(sp, near, color.copy(alpha = 0.8f * speakerAmount), t, 1f)
                flowLine(sp, far, color.copy(alpha = 0.32f * speakerAmount), t - 0.08f, 0.6f)
                drawRoundRect(
                    color.copy(alpha = speakerAmount),
                    Offset(sp.x - 6.dp.toPx(), sp.y - 8.dp.toPx()),
                    Size(12.dp.toPx(), 16.dp.toPx()),
                    CornerRadius(3.dp.toPx()),
                )
            }
        }
        // crossfeed: each side bleeds round the head into the other ear, softer the lower the feed
        if (crossfeedAmount > 0.01f) {
            val strength = (1f - (feed - 1f) / 14f).coerceIn(0.2f, 1f)
            val lift = r * 2.4f
            listOf(earL to earR to Color(0xFF6BBAF2), earR to earL to Color(0xFFF28BA8)).forEach { (pair, color) ->
                val (from, to) = pair
                val path = Path().apply {
                    moveTo(from.x, from.y - 4.dp.toPx())
                    cubicTo(from.x, from.y - lift, to.x, to.y - lift, to.x, to.y - 4.dp.toPx())
                }
                drawPath(
                    path,
                    color.copy(alpha = 0.55f * crossfeedAmount * strength),
                    style = Stroke(
                        1.8.dp.toPx(),
                        cap = StrokeCap.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 5.dp.toPx()), -t * 40.dp.toPx()),
                    ),
                )
            }
            drawCircle(Color(0xFF6BBAF2).copy(alpha = 0.8f * crossfeedAmount), 5.dp.toPx() + 2.dp.toPx() * pulse, Offset(earL.x - 10.dp.toPx(), earL.y))
            drawCircle(Color(0xFFF28BA8).copy(alpha = 0.8f * crossfeedAmount), 5.dp.toPx() + 2.dp.toPx() * pulse, Offset(earR.x + 10.dp.toPx(), earR.y))
        }
    }
}

/** a line with dots running along it, from one point to the other */
private fun DrawScope.flowLine(from: Offset, to: Offset, color: Color, t: Float, weight: Float) {
    drawLine(color.copy(alpha = color.alpha * 0.35f), from, to, 1.2.dp.toPx() * weight)
    for (k in 0 until 3) {
        val p = ((t * 0.9f + k / 3f) % 1f)
        drawCircle(color, 2.2.dp.toPx() * weight, Offset(from.x + (to.x - from.x) * p, from.y + (to.y - from.y) * p))
    }
}
