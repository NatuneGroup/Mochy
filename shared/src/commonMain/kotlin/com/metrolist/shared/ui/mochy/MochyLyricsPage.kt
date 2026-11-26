// Ported from Mochy Music by realidkroo. Licensed under GPL-3.0.

package com.metrolist.shared.ui.mochy

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateTopPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.metrolist.shared.ui.components.images.AsyncImage
import kotlinx.coroutines.delay

// the reference's header: the cover tile sits 36dp in, 28dp under the status bar
private val MochyLyricsArtSize = 60.dp
private val MochyLyricsArtInset = 36.dp
private val MochyLyricsArtTop = 28.dp
private val MochyLyricsArtGap = 10.dp
private val MochyLyricsArtShape = RoundedCornerShape(10.dp)

// the lyrics take the whole page after this much quiet; any touch brings the chrome back
private const val MOCHY_LYRICS_IMMERSIVE_DELAY_MS = 3_500L
private const val MOCHY_LYRICS_IMMERSION_MS = 420
private val MochyLyricsImmersionEase = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)

// the lyrics melt into the page at the top and the bottom
private const val MOCHY_LYRICS_TOP_FADE = 0.10f
private const val MOCHY_LYRICS_BOTTOM_FADE = 0.62f

/**
 * Full-screen Mochy lyrics page: the cover tile in the header (a tap closes the page), the lyrics
 * under it, faded at both edges, and the chrome that steps away while the lyrics are read.
 * [lyrics] is the existing lyrics view, so providers and sync are unchanged.
 */
@Composable
fun MochyLyricsPage(
    artworkUrl: String?,
    onClose: () -> Unit,
    contentBottomInset: Dp,
    modifier: Modifier = Modifier,
    onImmersiveChange: (Boolean) -> Unit = {},
    lyrics: @Composable (Modifier) -> Unit,
) {
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    var immersive by remember { mutableStateOf(false) }
    var presses by remember { mutableIntStateOf(0) }
    // every press restarts the quiet countdown, so the chrome comes back and then steps away again
    LaunchedEffect(presses) {
        immersive = false
        delay(MOCHY_LYRICS_IMMERSIVE_DELAY_MS)
        immersive = true
    }
    LaunchedEffect(immersive) { onImmersiveChange(immersive) }
    val immersion by animateFloatAsState(
        targetValue = if (immersive) 1f else 0f,
        animationSpec = tween(durationMillis = MOCHY_LYRICS_IMMERSION_MS, easing = MochyLyricsImmersionEase),
        label = "mochyLyricsImmersion",
    )

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.changes.any { it.changedToDown() }) presses++
                        }
                    }
                },
    ) {
        Box(
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .padding(start = MochyLyricsArtInset, top = statusTop + MochyLyricsArtTop)
                    .size(MochyLyricsArtSize)
                    .clip(MochyLyricsArtShape)
                    .clickable { if (!immersive) onClose() },
        ) {
            AsyncImage(url = artworkUrl, modifier = Modifier.fillMaxSize(), contentDescription = null)
        }

        CompositionLocalProvider(LocalMochyLyricsLook provides true) {
        lyrics(
            Modifier
                .fillMaxSize()
                .padding(top = statusTop + MochyLyricsArtTop + MochyLyricsArtSize + MochyLyricsArtGap)
                .padding(bottom = contentBottomInset * (1f - immersion))
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithCache {
                    val fade =
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            MOCHY_LYRICS_TOP_FADE to Color.Black,
                            MOCHY_LYRICS_BOTTOM_FADE to Color.Black,
                            1f to Color.Transparent,
                        )
                    onDrawWithContent {
                        drawContent()
                        drawRect(brush = fade, blendMode = BlendMode.DstIn)
                    }
                },
        )
        }
    }
}
