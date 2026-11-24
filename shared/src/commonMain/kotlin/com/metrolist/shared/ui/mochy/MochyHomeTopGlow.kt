// Ported from Mochy Music by realidkroo. Licensed under GPL-3.0.

package com.metrolist.shared.ui.mochy

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import com.metrolist.shared.ui.theme.rememberArtworkSeedColor
import com.metrolist.shared.ui.theme.toHsv

private const val GLOW_REACH_FRACTION = 0.95f
private const val GLOW_STOP_COUNT = 12
private val GlowStopPositions = FloatArray(GLOW_STOP_COUNT + 1) { it / GLOW_STOP_COUNT.toFloat() }

@Composable
internal fun MochyHomeTopGlow(
    coverUrls: List<String>,
    depth: Dp,
    visibility: () -> Float,
    modifier: Modifier = Modifier,
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val leftSeed = rememberArtworkSeedColor(coverUrls.getOrNull(0))
    val rightSeed = rememberArtworkSeedColor(coverUrls.getOrNull(1))
    val leftTarget = (leftSeed ?: rightSeed)?.toGlowTone(isDark) ?: Color.Transparent
    val rightTarget = (rightSeed ?: leftSeed)?.toGlowTone(isDark) ?: Color.Transparent

    val left = animateColorAsState(leftTarget, tween(700), label = "mochyHomeGlowLeft")
    val right = animateColorAsState(rightTarget, tween(700), label = "mochyHomeGlowRight")
    val peakAlpha = if (isDark) 0.58f else 0.22f

    Spacer(
        modifier =
            modifier
                .graphicsLayer {
                    alpha = visibility().coerceIn(0f, 1f)
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                }.drawWithCache {
                    val leftColor = left.value
                    val rightColor = right.value
                    val width = size.width
                    val height = depth.toPx()
                    if (width <= 0f || height <= 0f || (leftColor.alpha == 0f && rightColor.alpha == 0f)) {
                        return@drawWithCache onDrawBehind {}
                    }
                    val leftCenterX = width * 0.08f
                    val rightCenterX = width * 0.92f
                    val leftBrush = cornerGlowBrush(leftColor, peakAlpha, leftCenterX, height)
                    val rightBrush = cornerGlowBrush(rightColor, peakAlpha, rightCenterX, height)
                    val horizontalScale = (width * GLOW_REACH_FRACTION / height).coerceAtLeast(1f)
                    onDrawBehind {
                        withTransform({ scale(scaleX = horizontalScale, scaleY = 1f, pivot = Offset(leftCenterX, 0f)) }) {
                            drawRect(brush = leftBrush)
                        }
                        withTransform({ scale(scaleX = horizontalScale, scaleY = 1f, pivot = Offset(rightCenterX, 0f)) }) {
                            drawRect(brush = rightBrush)
                        }
                    }
                },
    )
}

private fun Color.toGlowTone(isDark: Boolean): Color {
    val hsv = toHsv()
    val saturation = hsv[1].coerceAtMost(if (isDark) 0.62f else 0.48f)
    val value = if (isDark) hsv[2].coerceIn(0.24f, 0.42f) else hsv[2].coerceIn(0.55f, 0.82f)
    return Color.hsv(hsv[0], saturation, value)
}

private fun cornerGlowBrush(
    color: Color,
    peakAlpha: Float,
    centerX: Float,
    radius: Float,
): Brush {
    val stops =
        Array(GLOW_STOP_COUNT + 1) { index ->
            val pos = GlowStopPositions[index]
            val remaining = 1f - pos
            val falloff = remaining * remaining * (3f - 2f * remaining)
            pos to color.copy(alpha = (color.alpha * peakAlpha * falloff).coerceIn(0f, 1f))
        }
    return Brush.radialGradient(
        colorStops = stops,
        center = Offset(centerX, 0f),
        radius = radius,
    )
}
