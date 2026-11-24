// Ported and adapted from Mochy Music by realidkroo. Licensed under GPL-3.0.

package com.metrolist.shared.ui.player.mochy.turntable

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

private val VinylTrackGaps = floatArrayOf(0.665f, 0.745f, 0.83f, 0.905f)
private val VinylBlack = Color(0xFF09090A)

internal const val ARM_LENGTH = 0.876f
internal const val ARM_HEAD_BEND = 27f
internal const val ARM_HEAD_LENGTH = 0.145f
internal const val ARM_STYLUS_ALONG = 0.115f

private const val BASE_CENTER_X = 0.885f
private const val BASE_HALF_WIDTH = 0.1f
private const val BASE_TOP = 0.02f
private const val BASE_BOTTOM = 0.4f

private val Aluminium =
    arrayOf(
        0.00f to Color(0xFF55585C),
        0.10f to Color(0xFF6F7277),
        0.30f to Color(0xFF8B8E93),
        0.52f to Color(0xFFA3A6AA),
        0.66f to Color(0xFFBDC0C3),
        0.76f to Color(0xFFD6D8DB),
        0.84f to Color(0xFFC3C5C8),
        0.93f to Color(0xFF919498),
        1.00f to Color(0xFF606368),
    )

private val Anodised =
    arrayOf(
        0.00f to Color(0xFF09090A),
        0.40f to Color(0xFF161618),
        0.68f to Color(0xFF26272A),
        0.80f to Color(0xFF3A3B3F),
        0.90f to Color(0xFF242528),
        1.00f to Color(0xFF0F0F11),
    )

private val FingerLift =
    arrayOf(
        0.00f to Color(0xFF3C3E42),
        0.55f to Color(0xFF7A7D82),
        0.75f to Color(0xFFA9ACB0),
        1.00f to Color(0xFF55585C),
    )

private class Finish(
    val faceLit: Color,
    val faceShade: Color,
    val side: Color,
)

private val Matte = Finish(Color(0xFF2B2C2F), Color(0xFF18191B), Color(0xFF0C0C0E))
private val CapFinish = Finish(Color(0xFF303135), Color(0xFF1B1C1E), Color(0xFF0D0D0F))
private val Shell = Finish(Color(0xFF34353A), Color(0xFF1F2023), Color(0xFF111113))

private fun hash(i: Int): Float {
    val s = sin(i * 12.9898f + 78.233f) * 43758.547f
    return s - floor(s)
}

private fun sheenBrush(
    center: Offset,
    peak: Float,
    power: Double,
    floorAlpha: Float = 0f,
): Brush {
    val steps = 180
    val stops =
        Array(steps + 1) { i ->
            val f = i / steps.toFloat()
            val c = abs(cos(f * 2.0 * PI)).pow(power).toFloat()
            f to Color.White.copy(alpha = floorAlpha + (peak - floorAlpha) * c)
        }
    return Brush.sweepGradient(*stops, center = center)
}

private fun grooveBands(
    center: Offset,
    radius: Float,
    from: Float,
    to: Float,
    bandPx: Float,
): Brush {
    val n = ((to - from) * radius / bandPx).toInt().coerceIn(12, 200)
    val stops = ArrayList<Pair<Float, Color>>(n + 3)
    stops += 0f to Color.Transparent
    for (i in 0..n) {
        val fine = hash(i)
        val slow = 0.5f + 0.5f * sin(i * 0.23f + 1.7f * sin(i * 0.043f))
        val dark = (0.04f + 0.18f * fine * fine + 0.14f * slow).coerceAtMost(0.4f)
        stops += (from + (to - from) * i / n) to Color.Black.copy(alpha = dark)
    }
    stops += 1f to Color.Transparent
    return Brush.radialGradient(*stops.toTypedArray(), center = center, radius = radius)
}

private fun fineGrooves(
    center: Offset,
    periodPx: Float,
): Brush =
    Brush.radialGradient(
        0f to Color.Black.copy(alpha = 0f),
        0.5f to Color.Black.copy(alpha = 0.14f),
        1f to Color.Black.copy(alpha = 0f),
        center = center,
        radius = periodPx,
        tileMode = TileMode.Repeated,
    )

internal fun DrawScope.drawVinyl(
    center: Offset,
    radius: Float,
    label: Float,
) {
    val runOut = (label + 0.055f).coerceAtMost(0.93f)
    val rimIn = 0.972f
    drawCircle(VinylBlack, radius, center)

    val bandRadius = (runOut + rimIn) / 2f * radius
    val band = Stroke(width = (rimIn - runOut) * radius)
    drawCircle(sheenBrush(center, peak = 0.2f, power = 5.0, floorAlpha = 0.012f), bandRadius, center, style = band)
    drawCircle(grooveBands(center, radius, runOut, rimIn, bandPx = 2.4.dp.toPx()), bandRadius, center, style = band)
    drawCircle(fineGrooves(center, max(3f, 1.2.dp.toPx())), bandRadius, center, style = band)

    val gap = max(1.5f, radius * 0.0045f)
    for (g in VinylTrackGaps) {
        if (g < runOut + 0.02f || g > rimIn - 0.01f) continue
        drawCircle(Color.Black.copy(alpha = 0.42f), g * radius, center, style = Stroke(gap))
        drawCircle(sheenBrush(center, peak = 0.16f, power = 140.0), g * radius, center, style = Stroke(gap * 0.55f))
    }

    drawCircle(
        sheenBrush(center, peak = 0.06f, power = 90.0),
        (label + runOut) / 2f * radius,
        center,
        style = Stroke((runOut - label) * radius),
    )
    drawCircle(Color.Black.copy(alpha = 0.45f), runOut * radius, center, style = Stroke(max(1f, radius * 0.003f)))

    drawCircle(sheenBrush(center, peak = 0.07f, power = 5.0), (rimIn + 1f) / 2f * radius, center, style = Stroke((1f - rimIn) * radius))
    drawCircle(
        sheenBrush(center, peak = 0.32f, power = 12.0, floorAlpha = 0.04f),
        radius * 0.991f,
        center,
        style = Stroke(max(1f, radius * 0.0035f)),
    )
    drawCircle(Color.Black.copy(alpha = 0.5f), radius - 0.75f, center, style = Stroke(1.5f))
}

internal fun DrawScope.drawArmBase(u: Float) {
    val left = (BASE_CENTER_X - BASE_HALF_WIDTH) * u
    val top = BASE_TOP * u
    val width = BASE_HALF_WIDTH * 2f * u
    val height = (BASE_BOTTOM - BASE_TOP) * u
    val round = BASE_HALF_WIDTH * u
    for (i in 1..5) {
        val grow = u * 0.007f * i
        drawRoundRect(
            color = Color.Black.copy(alpha = 0.075f),
            topLeft = Offset(left - grow + u * 0.008f, top - grow + u * 0.018f),
            size = Size(width + grow * 2f, height + grow * 2f),
            cornerRadius = CornerRadius(round + grow),
        )
    }
    drawRoundRect(
        brush =
            Brush.linearGradient(
                listOf(Color(0xFF1A1B1E), Color(0xFF0F1012), Color(0xFF0A0B0C)),
                start = Offset(left, top),
                end = Offset(left + width, top + height),
            ),
        topLeft = Offset(left, top),
        size = Size(width, height),
        cornerRadius = CornerRadius(round),
    )
    val hair = max(1f, u * 0.0016f)
    drawRoundRect(
        brush =
            Brush.linearGradient(
                listOf(Color.White.copy(alpha = 0.11f), Color.White.copy(alpha = 0.025f), Color.Transparent),
                start = Offset(left, top),
                end = Offset(left + width, top + height * 0.6f),
            ),
        topLeft = Offset(left + hair / 2f, top + hair / 2f),
        size = Size(width - hair, height - hair),
        cornerRadius = CornerRadius(round),
        style = Stroke(hair),
    )
}

internal fun DrawScope.drawTonearm(
    pivot: Offset,
    degrees: Float,
    u: Float,
    silhouette: Boolean,
) {
    val hair = max(1f, u * 0.0014f)
    withTransform({
        translate(pivot.x, pivot.y)
        rotate(degrees, Offset.Zero)
    }) {
        val tubeHalf = 0.0145f * u
        val tubeFrom = 0.09f * u
        val tubeTo = (ARM_LENGTH - 0.05f) * u
        cylinder(tubeFrom, tubeTo, tubeHalf, Aluminium, silhouette)
        if (!silhouette) {
            val ao = 0.022f * u
            drawRect(
                brush =
                    Brush.horizontalGradient(
                        0f to Color.Black.copy(alpha = 0.55f),
                        1f to Color.Transparent,
                        startX = 0.1f * u,
                        endX = 0.1f * u + ao,
                    ),
                topLeft = Offset(0.1f * u, -tubeHalf),
                size = Size(ao, tubeHalf * 2f),
            )
            drawRect(
                brush =
                    Brush.horizontalGradient(
                        0f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.5f),
                        startX = tubeTo - ao,
                        endX = tubeTo,
                    ),
                topLeft = Offset(tubeTo - ao, -tubeHalf),
                size = Size(ao, tubeHalf * 2f),
            )
        }

        cylinder((ARM_LENGTH - 0.055f) * u, (ARM_LENGTH + 0.004f) * u, 0.0175f * u, Anodised, silhouette, round = 0.003f * u)
        cylinder(0.034f * u, 0.1f * u, 0.0188f * u, Anodised, silhouette, round = 0.003f * u)
        if (!silhouette) {
            for (x in floatArrayOf((ARM_LENGTH - 0.055f) * u, 0.1f * u)) {
                drawLine(Color.White.copy(alpha = 0.16f), Offset(x, -0.016f * u), Offset(x, 0.017f * u), strokeWidth = hair)
            }
        }

        box(-0.052f * u, -0.058f * u, 0.042f * u, 0.088f * u, corner = 0.012f * u, depth = 0.011f * u, Matte, silhouette, hair)
        if (!silhouette) {
            drawLine(
                Color.Black.copy(alpha = 0.55f),
                Offset(-0.006f * u, -0.046f * u),
                Offset(-0.006f * u, 0.086f * u),
                strokeWidth = hair * 1.4f,
            )
            drawLine(
                Color.White.copy(alpha = 0.07f),
                Offset(-0.009f * u, -0.046f * u),
                Offset(-0.009f * u, 0.086f * u),
                strokeWidth = hair,
            )
        }
        box(-0.1f * u, -0.026f * u, -0.04f * u, 0.058f * u, corner = 0.01f * u, depth = 0.008f * u, CapFinish, silhouette, hair)
        if (!silhouette) {
            for (k in 1..3) {
                val x = (-0.092f + k * 0.012f) * u
                drawLine(Color.Black.copy(alpha = 0.35f), Offset(x, -0.016f * u), Offset(x, 0.054f * u), strokeWidth = hair)
            }
        }

        withTransform({
            translate(ARM_LENGTH * u, 0f)
            rotate(ARM_HEAD_BEND, Offset.Zero)
        }) {
            drawLine(
                brush =
                    if (silhouette) {
                        SolidColor(Color.Black)
                    } else {
                        Brush.linearGradient(*FingerLift, start = Offset(0.106f * u, 0f), end = Offset(0.132f * u, 0f))
                    },
                start = Offset(0.112f * u, -0.024f * u),
                end = Offset(0.127f * u, -0.067f * u),
                strokeWidth = 0.0105f * u,
                cap = StrokeCap.Round,
            )
            box(
                -0.014f * u,
                -0.032f * u,
                ARM_HEAD_LENGTH * u,
                0.032f * u,
                corner = 0.009f * u,
                depth = 0.008f * u,
                Shell,
                silhouette,
                hair,
            )
            if (!silhouette) {
                drawLine(Color.Black.copy(alpha = 0.5f), Offset(0.014f * u, -0.022f * u), Offset(0.014f * u, 0.03f * u), strokeWidth = hair)
                drawLine(
                    Color.Black.copy(alpha = 0.6f),
                    Offset(0.126f * u, 0.008f * u),
                    Offset(0.126f * u, 0.026f * u),
                    strokeWidth = hair * 1.6f,
                )
            }
        }
    }
}

private fun DrawScope.cylinder(
    from: Float,
    to: Float,
    half: Float,
    stops: Array<Pair<Float, Color>>,
    silhouette: Boolean,
    round: Float = 0f,
) {
    drawRoundRect(
        brush = if (silhouette) SolidColor(Color.Black) else Brush.verticalGradient(*stops, startY = -half, endY = half),
        topLeft = Offset(from, -half),
        size = Size(to - from, half * 2f),
        cornerRadius = CornerRadius(round),
    )
}

private fun DrawScope.box(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    corner: Float,
    depth: Float,
    finish: Finish,
    silhouette: Boolean,
    hair: Float,
) {
    val radius = CornerRadius(corner)
    if (silhouette) {
        drawRoundRect(Color.Black, Offset(left, top), Size(right - left, bottom - top), radius)
        return
    }
    drawRoundRect(finish.side, Offset(left, top), Size(right - left, bottom - top), radius)
    val faceTopLeft = Offset(left, top + depth)
    val faceSize = Size(right - left - depth, bottom - top - depth)
    drawRoundRect(
        brush = Brush.linearGradient(listOf(finish.faceLit, finish.faceShade), start = Offset(left, bottom), end = Offset(right, top)),
        topLeft = faceTopLeft,
        size = faceSize,
        cornerRadius = radius,
    )
    drawRoundRect(
        brush =
            Brush.linearGradient(
                listOf(Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0.03f), Color.Transparent),
                start = Offset(left, bottom),
                end = Offset(right - depth, top + depth),
            ),
        topLeft = faceTopLeft + Offset(hair / 2f, hair / 2f),
        size = Size(faceSize.width - hair, faceSize.height - hair),
        cornerRadius = radius,
        style = Stroke(hair),
    )
}
