// Ported from Mochy Music by realidkroo (github.com/realidkroo/mochy-music). Licensed under GPL-3.0.

package com.metrolist.shared.ui.player.mochy

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.metrolist.shared.settings.PlayerCoverStyle
import com.metrolist.shared.ui.components.CrossfadeArtwork
import com.metrolist.shared.ui.player.mochy.turntable.TURNTABLE_ART_RATIO
import com.metrolist.shared.ui.player.mochy.turntable.VinylDisc

// whether the cover is one of the records: the plain disc or one of the reference's five disc looks
internal val PlayerCoverStyle.isDiscLook: Boolean
    get() =
        this == PlayerCoverStyle.DISC ||
            this == PlayerCoverStyle.DISC_SMALL_FULL ||
            this == PlayerCoverStyle.DISC_SMALL_LABEL ||
            this == PlayerCoverStyle.DISC_BIG_FULL ||
            this == PlayerCoverStyle.DISC_BIG_LABEL ||
            this == PlayerCoverStyle.DISC_ALBUM

// the reference's geometry, as fractions of the frame's side: the record's diameter, its centre, the label's size
// relative to the record, and (for the album look) a square sleeve beside the record
private class DiscVariant(
    val diameter: Float,
    val centerX: Float,
    val centerY: Float,
    val labelRatio: Float,
    val albumCard: Boolean = false,
)

private fun discVariant(style: PlayerCoverStyle): DiscVariant =
    when (style) {
        PlayerCoverStyle.DISC_SMALL_FULL -> DiscVariant(1f, 0.5f, 0.5f, 0.88f)
        PlayerCoverStyle.DISC_SMALL_LABEL -> DiscVariant(1f, 0.5f, 0.5f, 0.44f)
        PlayerCoverStyle.DISC_BIG_FULL -> DiscVariant(1.55f, 0.24f, 0.20f, 0.95f)
        PlayerCoverStyle.DISC_BIG_LABEL -> DiscVariant(1.60f, 0.22f, 0.18f, 0.42f)
        PlayerCoverStyle.DISC_ALBUM -> DiscVariant(0.82f, 0.43f, 0.5f, 0.40f, albumCard = true)
        else -> DiscVariant(FLAGSHIP_DISC_DIAMETER, FLAGSHIP_DISC_CENTER, FLAGSHIP_DISC_CENTER, TURNTABLE_ART_RATIO)
    }

// the album sleeve's side and centre on the frame, for the album look
private const val ALBUM_CARD_SIDE = 0.64f
private const val ALBUM_CARD_CENTER_X = 0.665f
private val AlbumCardCorner = 12.dp

// one of the records with its label, sized and placed the way the reference lays out its disc looks
@Composable
internal fun FlagshipDiscCover(
    style: PlayerCoverStyle,
    artworkUrl: String?,
    nextArtworkUrl: String?,
    crossfadeProgressProvider: () -> Float,
    isPlaying: Boolean,
    spinActive: Boolean,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    artworkOverlay: (@Composable (Modifier) -> Unit)? = null,
) {
    val variant = discVariant(style)
    Box(modifier = modifier) {
        VinylDisc(
            artworkUrl = artworkUrl,
            nextArtworkUrl = nextArtworkUrl,
            crossfadeProgressProvider = crossfadeProgressProvider,
            isPlaying = isPlaying,
            spinActive = spinActive,
            contentDescription = contentDescription,
            artworkOverlay = artworkOverlay,
            discDiameter = variant.diameter,
            discCenterX = variant.centerX,
            discCenterYByWidth = variant.centerY,
            labelRatio = variant.labelRatio,
            modifier = Modifier.fillMaxSize(),
        )
        if (variant.albumCard) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val side = maxWidth * ALBUM_CARD_SIDE
                Box(
                    modifier =
                        Modifier
                            .offset(x = maxWidth * ALBUM_CARD_CENTER_X - side / 2, y = maxWidth * 0.5f - side / 2)
                            .size(side)
                            .clip(RoundedCornerShape(AlbumCardCorner)),
                ) {
                    CrossfadeArtwork(
                        currentUrl = artworkUrl,
                        nextUrl = null,
                        progress = { 0f },
                        contentDescription = contentDescription,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        animate = spinActive,
                    )
                }
            }
        }
    }
}
