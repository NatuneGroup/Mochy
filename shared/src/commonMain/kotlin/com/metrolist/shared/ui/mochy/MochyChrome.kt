// Ported from Mochy Music by realidkroo (github.com/realidkroo/mochy-music). Licensed under GPL-3.0.

// Mochy's floating chrome, the nav bar and the mini player: #131313 at 78% over the blurred app, a 2px white
// hairline at 5% just inside the edge, and a faint shadow under it. Both bars draw from here so they can't drift apart.

package com.metrolist.shared.ui.mochy

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.dp
import com.metrolist.shared.ui.utils.nativeBlurCompat
import com.metrolist.shared.ui.utils.supportsNativeBlur

/** The Figma's chrome shade: a fixed dark tone, never the Material You surface, so white icons always read on it. */
val MochyChromeColor = Color(0xFF131313)

/** How much of the chrome colour covers the blurred app. */
const val MOCHY_CHROME_TINT_ALPHA = 0.78f

/** The chrome's backdrop blur, heavier than the app's 24px default so it reads as properly frosted. */
const val MOCHY_CHROME_BLUR_PX = 48f

/** The lit tab's pill in the nav bar, and the round press behind the mini player's buttons. */
val MochyChromeHighlightColor = Color.White.copy(alpha = 0.1f)

/** Lifts the chrome off a light page without looking like a dark halo on a black one. */
val MochyChromeShadow = Shadow(radius = 6.3.dp, color = Color.Black.copy(alpha = 0.12f), spread = 2.dp)

// the hairline is 2 real pixels on every screen, not 2dp: any thicker and it reads as a border
const val MOCHY_CHROME_OUTLINE_PX = 2f
val MochyChromeOutlineColor = Color.White.copy(alpha = 0.05f)

/**
 * The app content the chrome floats over. The content records itself into [layer] as it draws, and each
 * piece of chrome draws that recording again, blurred, under its tint.
 */
@Stable
class MochyBackdrop {
    internal var layer by mutableStateOf<GraphicsLayer?>(null)
    internal var origin by mutableStateOf(Offset.Zero)

    // bumped each time the content re-records, so chrome on a platform that copies the recording redraws too
    internal var version by mutableIntStateOf(0)
}

val LocalMochyBackdrop = staticCompositionLocalOf<MochyBackdrop?> { null }

/**
 * Records this content for the chrome to blur. Put it on the page content only, never on a parent of the
 * chrome itself, or the chrome would draw its own recording.
 */
@Composable
fun Modifier.mochyBackdropSource(backdrop: MochyBackdrop?): Modifier {
    if (backdrop == null || !supportsNativeBlur()) return this
    val layer = rememberGraphicsLayer()
    DisposableEffect(backdrop, layer) {
        backdrop.layer = layer
        onDispose { if (backdrop.layer === layer) backdrop.layer = null }
    }
    return onGloballyPositioned { backdrop.origin = it.positionInWindow() }
        .drawWithContent {
            layer.record { this@drawWithContent.drawContent() }
            drawLayer(layer)
            backdrop.version++
        }
}

/**
 * The frosted fill of a piece of chrome: the solid foundation, the app behind it blurred where the device can
 * blur, and the 78% tint. With no blur the foundation and tint leave plain #131313, as Mochy does with blur off.
 * The caller clips it to the chrome's shape.
 */
@Composable
fun MochyChromeGlass(
    modifier: Modifier = Modifier,
    pureBlack: Boolean = false,
) {
    val backdrop = LocalMochyBackdrop.current
    val color = if (pureBlack) Color.Black else MochyChromeColor
    val blur = remember { supportsNativeBlur() }
    Box(modifier.drawBehind { drawRect(color) }) {
        if (blur && backdrop != null) {
            var position by remember { mutableStateOf(Offset.Zero) }
            Box(
                Modifier
                    .matchParentSize()
                    .onGloballyPositioned { position = it.positionInWindow() }
                    .nativeBlurCompat(MOCHY_CHROME_BLUR_PX)
                    .drawBehind {
                        // read so a re-recorded backdrop redraws the glass
                        backdrop.version
                        val layer = backdrop.layer ?: return@drawBehind
                        translate(backdrop.origin.x - position.x, backdrop.origin.y - position.y) { drawLayer(layer) }
                    },
            )
        }
        Box(Modifier.matchParentSize().drawBehind { drawRect(color.copy(alpha = MOCHY_CHROME_TINT_ALPHA)) })
    }
}

/** The chrome's hairline round a rounded rect, kept just inside it so the parent's clip can't shave half of it off. */
fun DrawScope.drawMochyChromeOutline(
    cornerRadius: Float = size.height / 2f,
    alpha: Float = 1f,
) {
    val half = MOCHY_CHROME_OUTLINE_PX / 2f
    drawRoundRect(
        color = MochyChromeOutlineColor,
        topLeft = Offset(half, half),
        size = Size((size.width - MOCHY_CHROME_OUTLINE_PX).coerceAtLeast(0f), (size.height - MOCHY_CHROME_OUTLINE_PX).coerceAtLeast(0f)),
        cornerRadius = CornerRadius((cornerRadius - half).coerceAtLeast(0f)),
        style = Stroke(MOCHY_CHROME_OUTLINE_PX),
        alpha = alpha,
    )
}

/** A floating piece of chrome in [shape]: shadow, frosted glass, hairline, then [content] on top. */
@Composable
fun MochyChromeSurface(
    shape: Shape,
    modifier: Modifier = Modifier,
    pureBlack: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier =
            modifier
                .dropShadow(shape, MochyChromeShadow)
                .clip(shape)
                .drawWithContent {
                    drawContent()
                    drawMochyChromeOutline()
                },
    ) {
        MochyChromeGlass(Modifier.matchParentSize(), pureBlack)
        content()
    }
}
