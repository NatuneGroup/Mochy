// Ported and adapted from Mochy Music by realidkroo. Licensed under GPL-3.0.

package com.metrolist.shared.ui.player.mochy.ipod

import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt

@Immutable
internal class IpodGeometry(
    val body: Rect,
    val corner: Float,
    val unit: Float,
    val chevron: Offset?,
    val screen: Rect,
    val wheel: Rect,
) {
    val bezel: Float get() = screen.width * BEZEL_SHARE
    val screenCorner: Float get() = screen.width * SCREEN_CORNER_SHARE
}

internal fun phoneGeometry(
    w: Float,
    h: Float,
    top: Float,
    bottom: Float,
    tall: Boolean,
): IpodGeometry {
    val chevron = Offset(w / 2f, top + w * 0.064f)
    val floor = h - bottom
    if (tall) tallPhone(w, h, top, floor)?.let { return it }
    val screenTop = chevron.y + w * 0.091f
    val screen = Rect(w * SCREEN_LEFT, screenTop, w * (SCREEN_LEFT + SCREEN_WIDTH), screenTop + w * SCREEN_WIDTH * SCREEN_ASPECT)
    val diameter = minOf(w * WHEEL_SHARE, (floor - screen.bottom) * 0.84f)
    val cy = (screen.bottom + (h - screen.bottom) * 0.548f).coerceAtMost(floor - diameter / 2f - w * 0.04f)
    return IpodGeometry(Rect(0f, 0f, w, h), 0f, w, chevron, screen, Rect(Offset(w / 2f, cy), diameter / 2f))
}

private fun tallPhone(
    w: Float,
    h: Float,
    top: Float,
    floor: Float,
): IpodGeometry? {
    val edge = w * TALL_EDGE
    val diameter = w * TALL_WHEEL
    val wheelBottom = floor - w * 0.08f
    val cy = wheelBottom - diameter / 2f
    val screenTop = top + w * 0.04f
    val screenBottom = cy - diameter / 2f - w * 0.09f
    if (screenBottom - screenTop < (w - edge * 2f) * 0.75f) return null
    val screen = Rect(edge, screenTop, w - edge, screenBottom)
    return IpodGeometry(Rect(0f, 0f, w, h), 0f, w, null, screen, Rect(Offset(w / 2f, cy), diameter / 2f))
}

internal fun deviceGeometry(
    w: Float,
    h: Float,
    top: Float,
    bottom: Float,
    tall: Boolean,
): IpodGeometry {
    val room = h - top - bottom
    val bodyW = minOf(w * 0.88f, room * 0.94f / BODY_ASPECT)
    val bodyH = bodyW * BODY_ASPECT
    val body = Rect(Offset((w - bodyW) / 2f, top + (room - bodyH) / 2f), Size(bodyW, bodyH))
    val screenTop = body.top + bodyW * 0.1976f
    val screenLeft = body.left + bodyW * SCREEN_LEFT
    val wheel = Rect(Offset(body.center.x, body.top + bodyW * 1.5752f), bodyW * WHEEL_SHARE / 2f)
    val screenBottom = if (tall) wheel.top - bodyW * 0.08f else screenTop + bodyW * SCREEN_WIDTH * SCREEN_ASPECT
    return IpodGeometry(
        body = body,
        corner = bodyW * 0.1224f,
        unit = bodyW,
        chevron = Offset(body.center.x, body.top + bodyW * 0.1066f),
        screen = Rect(screenLeft, screenTop, screenLeft + bodyW * SCREEN_WIDTH, screenBottom),
        wheel = wheel,
    )
}

internal class IpodBodyPaint(
    geometry: IpodGeometry,
    look: IpodLook,
) {
    private val body = geometry.body
    private val corner = CornerRadius(geometry.corner)
    private val fill = Brush.verticalGradient(listOf(look.body.top, look.body.bottom), startY = body.top, endY = body.bottom)
    private val shadeDown =
        if (look.shadow) {
            Brush.verticalGradient(
                0f to Color(0x0D000000),
                0.03f to Color.Transparent,
                0.86f to Color.Transparent,
                1f to Color(0x3A000000),
                startY = body.top,
                endY = body.bottom,
            )
        } else {
            null
        }
    private val shadeSides =
        if (look.shadow) {
            Brush.horizontalGradient(
                0f to Color(0x1C000000),
                0.045f to Color.Transparent,
                0.955f to Color.Transparent,
                1f to Color(0x1C000000),
                startX = body.left,
                endX = body.right,
            )
        } else {
            null
        }

    fun DrawScope.draw(onBlack: Boolean) {
        if (onBlack) drawRect(Color.Black)
        drawRoundRect(fill, body.topLeft, body.size, corner)
        shadeDown?.let { drawRoundRect(it, body.topLeft, body.size, corner) }
        shadeSides?.let { drawRoundRect(it, body.topLeft, body.size, corner) }
    }
}

internal fun Modifier.placeIpodRect(
    rect: Rect,
    density: Density,
): Modifier =
    with(density) {
        offset { IntOffset(rect.left.roundToInt(), rect.top.roundToInt()) }
            .size(rect.width.toDp(), rect.height.toDp())
    }

private const val SCREEN_LEFT = 43f / 572f
private const val SCREEN_WIDTH = 486f / 572f
private const val SCREEN_ASPECT = 367f / 486f
private const val WHEEL_SHARE = 375f / 572f
private const val BODY_ASPECT = 1248f / 572f
private const val BEZEL_SHARE = 6f / 486f
private const val TALL_EDGE = 0.049f
private const val TALL_WHEEL = 0.727f
private const val SCREEN_CORNER_SHARE = 0.045f
