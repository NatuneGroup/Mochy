// Ported from Sora (github.com/shizenworks/sora), its TikTok-style player. Licensed under GPL-3.0.

/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package com.metrolist.shared.ui.player.mochy.styles.tiktok

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private val MeshFallbackColors =
    listOf(
        Color(0xFF3A1C71),
        Color(0xFFD76D77),
        Color(0xFF2B5876),
        Color(0xFFFFAF7B),
    )

@Immutable
internal data class TikTokMeshPalette(
    val colors: List<Color>,
)

private const val MESH_RENDER_SCALE = 0.25f

@Composable
internal fun TikTokMeshBackdrop(
    palette: TikTokMeshPalette,
    trackKey: Any? = null,
    reduceAnimation: Boolean = false,
    blurRadius: Dp = 64.dp,
    modifier: Modifier = Modifier,
) {
    val tuned =
        (palette.colors.ifEmpty { MeshFallbackColors } + MeshFallbackColors)
            .take(4)
            .map { it.tuned() }

    val colorSpec: AnimationSpec<Color> = if (reduceAnimation) snap() else tween(1400)
    val animatedColors =
        tuned.mapIndexed { index, color ->
            animateColorAsState(color, colorSpec, label = "tiktokMeshColor$index").value
        }
    val baseColor by animateColorAsState(tuned.first().dimmed(), colorSpec, label = "tiktokMeshBase")

    val phase = remember { Animatable(0f) }
    LaunchedEffect(trackKey, reduceAnimation) {
        when {
            reduceAnimation -> {
                phase.snapTo(0f)
            }

            else -> {
                phase.animateTo(
                    targetValue = phase.value + MESH_DRIFT_RADIANS,
                    animationSpec = tween(8_000, easing = FastOutSlowInEasing),
                )
            }
        }
    }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .clipToBounds(),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            modifier =
                Modifier
                    .fillMaxSize(MESH_RENDER_SCALE)
                    .graphicsLayer {
                        scaleX = 1.3f / MESH_RENDER_SCALE
                        scaleY = 1.3f / MESH_RENDER_SCALE
                    }.background(baseColor)
                    .blur(blurRadius * MESH_RENDER_SCALE),
        ) {
            val anchors =
                listOf(
                    Offset(0.20f, 0.25f),
                    Offset(0.80f, 0.20f),
                    Offset(0.75f, 0.80f),
                    Offset(0.25f, 0.75f),
                )
            val speeds = listOf(1f, -0.7f, 0.85f, -1.15f)
            val drift = phase.value

            animatedColors.forEachIndexed { index, color ->
                val anchor = anchors[index]
                val center =
                    Offset(
                        x = (anchor.x + 0.16f * cos(drift * speeds[index] + index * 1.7f)) * size.width,
                        y = (anchor.y + 0.16f * sin(drift * speeds[index] * 0.9f + index * 2.3f)) * size.height,
                    )
                val radius = size.maxDimension * 0.62f
                drawCircle(
                    brush =
                        Brush.radialGradient(
                            colors = listOf(color.copy(alpha = 0.92f), color.copy(alpha = 0f)),
                            center = center,
                            radius = radius,
                        ),
                    radius = radius,
                    center = center,
                )
            }

            drawRect(
                brush =
                    Brush.verticalGradient(
                        colors =
                            listOf(
                                Color.Black.copy(alpha = 0.05f),
                                Color.Black.copy(alpha = 0.20f),
                            ),
                    ),
            )
        }
    }
}

/**
 * The four mesh colours for a song: its cover's colours as the player extracts them, made distinct and filled out to
 * four, the fallbacks until they arrive.
 */
@Composable
internal fun rememberTikTokArtworkColors(
    songKey: Any?,
    extract: suspend () -> List<Color>?,
): TikTokMeshPalette {
    var palette by remember(songKey) { mutableStateOf(TikTokMeshPalette(MeshFallbackColors)) }
    LaunchedEffect(songKey) {
        val found = extract()?.distinctEnough().orEmpty()
        palette =
            TikTokMeshPalette(
                when {
                    found.isEmpty() -> MeshFallbackColors
                    found.size >= 4 -> found.take(4)
                    else -> found.expandedToFour()
                },
            )
    }
    return palette
}

private const val MESH_DRIFT_RADIANS = (PI * 0.45f).toFloat()

private fun List<Color>.distinctEnough(): List<Color> {
    val kept = mutableListOf<Color>()
    forEach { color -> if (kept.none { it.isCloseTo(color) }) kept += color }
    return kept
}

private fun Color.isCloseTo(other: Color): Boolean {
    val a = hsl()
    val b = other.hsl()
    val hueGap = abs(a[0] - b[0]).let { min(it, 360f - it) }
    return hueGap < 15f && abs(a[2] - b[2]) < 0.12f
}

private fun List<Color>.expandedToFour(): List<Color> {
    val out = toMutableList()
    var step = 1
    while (out.size < 4) {
        out += this[(out.size - size) % size].shifted(24f * step, 0.12f * step)
        step++
    }
    return out
}

private fun Color.shifted(
    hue: Float,
    lightness: Float,
): Color {
    val hsl = hsl()
    return Color.hsl((hsl[0] + hue) % 360f, hsl[1], (hsl[2] + lightness).coerceIn(0.2f, 0.7f))
}

private fun Color.tuned(): Color {
    val hsl = hsl()
    return Color.hsl(hsl[0], (hsl[1] * 1.4f).coerceAtMost(1f), hsl[2].coerceIn(0.22f, 0.66f))
}

private fun Color.dimmed(): Color {
    val hsl = hsl()
    return Color.hsl(hsl[0], hsl[1], 0.12f)
}

// hue in degrees, saturation and lightness from 0 to 1, as ColorUtils.colorToHSL gives them
private fun Color.hsl(): FloatArray {
    val max = maxOf(red, green, blue)
    val min = minOf(red, green, blue)
    val lightness = (max + min) / 2f
    val delta = max - min
    if (delta == 0f) return floatArrayOf(0f, 0f, lightness)
    val saturation = delta / (1f - abs(2f * lightness - 1f))
    val hue =
        when (max) {
            red -> 60f * (((green - blue) / delta) % 6f)
            green -> 60f * (((blue - red) / delta) + 2f)
            else -> 60f * (((red - green) / delta) + 4f)
        }.let { if (it < 0f) it + 360f else it }
    return floatArrayOf(hue, saturation.coerceIn(0f, 1f), lightness)
}
