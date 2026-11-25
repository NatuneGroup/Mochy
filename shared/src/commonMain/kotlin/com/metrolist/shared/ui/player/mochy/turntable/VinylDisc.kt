// Ported and adapted from Mochy Music by realidkroo. Licensed under GPL-3.0.

package com.metrolist.shared.ui.player.mochy.turntable

import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.metrolist.shared.ui.components.CrossfadeArtwork
import com.metrolist.shared.ui.theme.rememberReducedMotion
import kotlinx.coroutines.isActive

private const val SPIN_DEGREES_PER_SECOND = 26f
private val LabelFallback = Color(0xFF27303B)

@Composable
fun VinylDisc(
    artworkUrl: String?,
    nextArtworkUrl: String?,
    crossfadeProgressProvider: () -> Float,
    isPlaying: Boolean,
    spinActive: Boolean,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    artworkOverlay: (@Composable (Modifier) -> Unit)? = null,
    discDiameter: Float = TURNTABLE_DISC_DIAMETER,
    discCenterX: Float = TURNTABLE_DISC_CENTER_X,
    discCenterYByWidth: Float = TURNTABLE_DISC_CENTER_Y_BY_WIDTH,
) {
    val reducedMotion = rememberReducedMotion()
    val angle = remember { mutableFloatStateOf(0f) }
    val lastFrameNanos = remember { mutableLongStateOf(0L) }
    val spinning = !reducedMotion && isPlaying && spinActive

    LaunchedEffect(spinning) {
        if (!spinning) {
            lastFrameNanos.longValue = 0L
            return@LaunchedEffect
        }
        while (isActive) {
            withInfiniteAnimationFrameNanos { frameTimeNanos ->
                val previous = lastFrameNanos.longValue
                lastFrameNanos.longValue = frameTimeNanos
                if (previous != 0L) {
                    val deltaSeconds = (frameTimeNanos - previous) / 1_000_000_000f
                    angle.floatValue = (angle.floatValue + SPIN_DEGREES_PER_SECOND * deltaSeconds).mod(360f)
                }
            }
        }
    }

    val density = LocalDensity.current
    BoxWithConstraints(modifier = modifier) {
        val boxSide = maxWidth.value
        val discSide = boxSide * discDiameter
        val discLeft = maxWidth.value * discCenterX - discSide / 2f
        val discTop = maxWidth.value * discCenterYByWidth - discSide / 2f
        val discLeftPx = with(density) { discLeft.dp.toPx() }
        val discTopPx = with(density) { discTop.dp.toPx() }

        val platterModifier =
            Modifier
                .wrapContentSize(Alignment.TopStart, unbounded = true)
                .requiredSize(discSide.dp)
                .offset { IntOffset(discLeftPx.toInt(), discTopPx.toInt()) }

        Canvas(modifier = platterModifier) {
            val r = size.minDimension / 2f
            val center = Offset(size.width / 2f, size.height / 2f)
            drawCircle(
                brush =
                    Brush.radialGradient(
                        0.86f to Color.Black.copy(alpha = 0.55f),
                        1f to Color.Transparent,
                        center = center + Offset(0f, r * 0.035f),
                        radius = r * 1.09f,
                    ),
                radius = r * 1.09f,
                center = center + Offset(0f, r * 0.035f),
            )
        }

        Canvas(
            modifier = platterModifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
        ) {
            val r = size.minDimension / 2f
            val center = Offset(size.width / 2f, size.height / 2f)
            drawVinyl(center, r, TURNTABLE_ART_RATIO)
        }

        val labelSide = (discSide * TURNTABLE_ART_RATIO).dp
        Box(
            modifier =
                platterModifier.graphicsLayer {
                    rotationZ = angle.floatValue
                },
        ) {
            val labelColorFilter =
                remember(isPlaying) {
                    if (isPlaying) null else ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0.7f) })
                }
            Box(
                modifier =
                    Modifier
                        .align(Alignment.Center)
                        .size(labelSide)
                        .clip(CircleShape)
                        .background(LabelFallback)
                        .graphicsLayer {
                            colorFilter = labelColorFilter
                        },
            ) {
                CrossfadeArtwork(
                    animate = spinActive,
                    currentUrl = artworkUrl,
                    nextUrl = nextArtworkUrl,
                    progress = crossfadeProgressProvider,
                    contentDescription = contentDescription,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                artworkOverlay?.invoke(Modifier.fillMaxSize())
            }
        }
    }
}
