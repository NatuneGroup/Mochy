// Ported and adapted from Mochy Music by realidkroo. Licensed under GPL-3.0.

package com.metrolist.shared.ui.player.mochy.turntable

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.metrolist.shared.ui.player.artworkSwipe

@Composable
internal fun TurntableStage(
    artworkUrl: String?,
    nextArtworkUrl: String?,
    crossfadeProgressProvider: () -> Float,
    contentDescription: String?,
    positionProvider: () -> Long,
    duration: Long,
    songId: String?,
    isPlaying: Boolean,
    spinActive: Boolean,
    canSeek: Boolean,
    swipeEnabled: Boolean,
    swipeSensitivity: Float,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    topInsetDp: Float = 0f,
    artworkOverlay: (@Composable (Modifier) -> Unit)? = null,
) {
    BoxWithConstraints(
        modifier =
            modifier.artworkSwipe(
                enabled = swipeEnabled,
                sensitivity = swipeSensitivity,
                onPrevious = onPrevious,
                onNext = onNext,
            ),
    ) {
        val wide = maxWidth >= 500.dp
        val stageWidth =
            if (wide) {
                minOf(maxWidth.value * 0.58f, maxHeight.value * 0.72f)
            } else {
                minOf(maxWidth.value, maxHeight.value * 0.78f)
            }
        val leftOffset =
            if (wide) {
                (maxWidth.value / 2f) - (TURNTABLE_DISC_CENTER_X * stageWidth)
            } else {
                (maxWidth.value - stageWidth) / 2f
            }
        val topPadding = if (wide) maxOf(topInsetDp + 12f, 24f) else maxOf(topInsetDp, 8f)

        Box(
            modifier =
                Modifier
                    .offset(x = leftOffset.dp)
                    .padding(top = topPadding.dp)
                    .width(stageWidth.dp)
                    .fillMaxSize(),
        ) {
            TurntableBase(Modifier.fillMaxSize())
            VinylDisc(
                artworkUrl = artworkUrl,
                nextArtworkUrl = nextArtworkUrl,
                crossfadeProgressProvider = crossfadeProgressProvider,
                isPlaying = isPlaying,
                spinActive = spinActive,
                contentDescription = contentDescription,
                artworkOverlay = artworkOverlay,
                modifier = Modifier.fillMaxSize(),
            )
            TurntableArm(
                positionProvider = positionProvider,
                duration = duration,
                isPlaying = isPlaying,
                songId = songId,
                canSeek = canSeek,
                active = spinActive,
                interactive = canSeek,
                onSeek = onSeek,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
