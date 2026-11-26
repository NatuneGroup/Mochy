// Ported from Mochy Music by realidkroo (github.com/realidkroo/mochy-music). Licensed under GPL-3.0.

package com.metrolist.shared.ui.player.mochy

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.metrolist.shared.settings.PlayerCoverStyle
import com.metrolist.shared.ui.components.CrossfadeArtwork
import com.metrolist.shared.ui.player.mochy.turntable.TURNTABLE_ART_RATIO
import com.metrolist.shared.ui.player.mochy.turntable.VinylDisc
import com.metrolist.shared.ui.theme.mochyInterFontFamily
import kotlin.math.PI

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
    discTitle: String = "",
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
        if (style != PlayerCoverStyle.DISC) {
            DiscTitleRing(title = discTitle, variant = variant, modifier = Modifier.fillMaxSize())
        }
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

// the printed name round the platter: the track title, repeated to fill the ring between the label and the rim
// (the reference's disc name plate, in its font, size and radius)
@Composable
private fun DiscTitleRing(
    title: String,
    variant: DiscVariant,
    modifier: Modifier,
) {
    if (title.isBlank()) return
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier = modifier) {
        val sideDp = maxWidth.value * variant.diameter
        val bandDp = (1f - variant.labelRatio) / 2f * sideDp
        val style =
            TextStyle(
                fontFamily = mochyInterFontFamily(),
                fontWeight = FontWeight.SemiBold,
                fontSize = (bandDp * 0.30f).coerceIn(8f, 15f).sp,
                letterSpacing = 0.22.em,
                color = Color.White,
            )
        val unit = title.trim().uppercase() + "  ·  "
        Canvas(modifier = Modifier.fillMaxSize()) {
            val side = size.width * variant.diameter
            val center = Offset(size.width * variant.centerX, size.height * variant.centerY)
            // the middle of the band between the label's edge and the rim, a little toward the rim
            val ringR = side / 2f * (variant.labelRatio + (1f - variant.labelRatio) * 0.58f)
            val glyphs = unit.map { measurer.measure(it.toString(), style) }
            val unitWidth = glyphs.sumOf { it.size.width.toDouble() }.toFloat().coerceAtLeast(1f)
            val circumference = (2f * PI.toFloat() * ringR)
            val repeats = (circumference / unitWidth).toInt().coerceAtLeast(1)
            // spread the repeats evenly so the end meets the start with no gap
            val stretch = circumference / (unitWidth * repeats)
            var travelled = 0f
            repeat(repeats) {
                for (glyph in glyphs) {
                    val w = glyph.size.width * stretch
                    val angle = (travelled + w / 2f) / ringR
                    rotate(angle * 180f / PI.toFloat(), center) {
                        val at = Offset(center.x - glyph.size.width / 2f, center.y - ringR - glyph.size.height / 2f)
                        drawText(glyph, color = Color.White.copy(alpha = 0.18f), topLeft = at)
                        drawText(glyph, color = Color.White.copy(alpha = 0.7f), topLeft = at, blendMode = BlendMode.Overlay)
                    }
                    travelled += w
                }
            }
        }
    }
}
