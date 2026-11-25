/*
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

// Ported and adapted from Mochy Music by realidkroo. Licensed under GPL-3.0.

package com.metrolist.shared.ui.share

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import com.metrolist.shared.ui.components.lyrics.drawCover

enum class SongShareCardStyle { COVER, WIDE }

// Card geometry in card units, scaled to the exported width; the same numbers as the Mochy cards.
private const val COVER_CARD_WIDTH = 172f
private const val COVER_PADDING = 14f
private const val COVER_GAP = 12f
private const val WIDE_CARD_WIDTH = 296f
private const val WIDE_PADDING = 18f
private const val WIDE_COVER_SIDE = 46f
private const val WIDE_COVER_GAP = 11f
private const val LINE_GAP = 4f
private const val TITLE_SIZE = 15f
private const val ARTIST_SIZE = 12f
private const val LYRIC_SIZE = 15f

private val FallbackPalette = listOf(Color(0xFF3A3A3C), Color(0xFF242426))

private fun textStyle(
    size: Float,
    weight: FontWeight,
    color: Color,
) = TextStyle(color = color, fontSize = size.sp, fontWeight = weight)

/**
 * Draws a song card at [widthPx] wide. The card is drawn directly into an ImageBitmap, the same
 * way the lyrics share image is, so it can be previewed and exported on every platform.
 */
fun renderSongShareCard(
    style: SongShareCardStyle,
    title: String,
    artist: String,
    cover: ImageBitmap?,
    palette: List<Color>,
    textMeasurer: TextMeasurer,
    layoutDirection: LayoutDirection,
    lyricLines: List<String> = emptyList(),
    widthPx: Int = 1080,
): ImageBitmap {
    val cardUnits = if (style == SongShareCardStyle.COVER) COVER_CARD_WIDTH else WIDE_CARD_WIDTH
    val scale = widthPx / cardUnits
    val density = Density(scale, fontScale = 1f)
    val colors = palette.ifEmpty { FallbackPalette }
    val ink = Color.White
    val inkSoft = Color.White.copy(alpha = 0.72f)

    fun measure(
        text: String,
        size: Float,
        weight: FontWeight,
        color: Color,
        maxWidthUnits: Float,
        maxLines: Int = Int.MAX_VALUE,
    ): TextLayoutResult =
        textMeasurer.measure(
            text,
            style = textStyle(size, weight, color),
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            constraints = Constraints(maxWidth = (maxWidthUnits * scale).toInt()),
            density = density,
            layoutDirection = layoutDirection,
        )

    // Lay the card out first so the bitmap is exactly as tall as its content.
    val pad = if (style == SongShareCardStyle.COVER) COVER_PADDING else WIDE_PADDING
    val contentWidth = cardUnits - 2 * pad
    val headerTextWidth = if (style == SongShareCardStyle.COVER) contentWidth else contentWidth - WIDE_COVER_SIDE - WIDE_COVER_GAP
    val titleLayout =
        measure(
            title,
            TITLE_SIZE,
            FontWeight.Bold,
            ink,
            headerTextWidth,
            maxLines =
                if (style ==
                    SongShareCardStyle.COVER
                ) {
                    2
                } else {
                    1
                },
        )
    val artistLayout = measure(artist, ARTIST_SIZE, FontWeight.SemiBold, inkSoft, headerTextWidth, maxLines = 1)
    val lyricLayouts = lyricLines.map { measure(it, LYRIC_SIZE, FontWeight.Bold, ink, contentWidth) }

    val headerHeight = titleLayout.size.height + artistLayout.size.height + 2f * scale
    val heightUnits =
        when (style) {
            SongShareCardStyle.COVER -> {
                pad + contentWidth + COVER_GAP + titleLayout.size.height / scale + 2f + artistLayout.size.height / scale + pad
            }

            SongShareCardStyle.WIDE -> {
                val lyricsHeight =
                    lyricLayouts.sumOf { it.size.height / scale.toDouble() }.toFloat() +
                        LINE_GAP * (lyricLayouts.size - 1).coerceAtLeast(0)
                val header = maxOf(WIDE_COVER_SIDE, headerHeight / scale)
                pad + header + (if (lyricLayouts.isEmpty()) 0f else 14f + lyricsHeight) + pad
            }
        }
    val heightPx = (heightUnits * scale).toInt()

    val bitmap = ImageBitmap(widthPx, heightPx)
    CanvasDrawScope().draw(density, layoutDirection, Canvas(bitmap), Size(widthPx.toFloat(), heightPx.toFloat())) {
        drawRect(Brush.linearGradient(colors, start = Offset.Zero, end = Offset(size.width, size.height)))
        val padPx = pad * scale
        when (style) {
            SongShareCardStyle.COVER -> {
                val side = contentWidth * scale
                val top = padPx
                if (cover != null) {
                    drawCover(cover, Offset(padPx, top), side)
                } else {
                    drawRect(Color.White.copy(alpha = 0.15f), Offset(padPx, top), Size(side, side))
                }
                var y = top + side + COVER_GAP * scale
                drawText(titleLayout, topLeft = Offset(padPx, y))
                y += titleLayout.size.height + 2f * scale
                drawText(artistLayout, topLeft = Offset(padPx, y))
            }

            SongShareCardStyle.WIDE -> {
                val side = WIDE_COVER_SIDE * scale
                val top = padPx
                if (cover != null) {
                    drawCover(cover, Offset(padPx, top), side)
                } else {
                    drawRect(Color.White.copy(alpha = 0.15f), Offset(padPx, top), Size(side, side))
                }
                val textX = padPx + (WIDE_COVER_SIDE + WIDE_COVER_GAP) * scale
                val textY = top + (side - headerHeight) / 2f
                drawText(titleLayout, topLeft = Offset(textX, textY))
                drawText(artistLayout, topLeft = Offset(textX, textY + titleLayout.size.height + 2f * scale))
                var y = top + maxOf(side, headerHeight) + 14f * scale
                lyricLayouts.forEach { layout ->
                    drawText(layout, topLeft = Offset(padPx, y))
                    y += layout.size.height + LINE_GAP * scale
                }
            }
        }
    }
    return bitmap
}
