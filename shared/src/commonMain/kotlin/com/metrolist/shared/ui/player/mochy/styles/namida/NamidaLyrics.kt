// Ported from Sora (github.com/shizenworks/sora), its namida player style after namida (github.com/namidaco/namida). Licensed under GPL-3.0.

// namida player style — the lyrics page, ported from namida's
// packages/lyrics_lrc_parsed_view.dart and ui/widgets/lyrics_karaoke_text.dart:
// the same type (NText medium — LexendDeca w600 — at 26 in the fullscreen page,
// 15 beside the player), the same focal effect (the active line grows, the rest
// sit smaller and dimmer), the same word-by-word karaoke sweep through the
// active line, the same interlude dots (radius fontSize × 0.16) and the same
// untimed lines at 0.8×.

package com.metrolist.shared.ui.player.mochy.styles.namida

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.metrolist.shared.generated.resources.Res
import com.metrolist.shared.generated.resources.namida_arrow_down_2
import com.metrolist.shared.model.LyricsEntry
import com.metrolist.shared.model.WordTimestamp
import com.metrolist.shared.ui.player.mochy.styles.LocalStyleTrack
import com.metrolist.shared.ui.player.mochy.styles.rememberLivePosition
import org.jetbrains.compose.resources.painterResource
import kotlin.math.roundToInt

/** the lyrics' base sizes (lyrics_lrc_parsed_view): 26 fullscreen, 15 beside the player */
private const val FULLSCREEN_LYRICS_SIZE = 26f
private const val PANEL_LYRICS_SIZE = 15f

/** the focal multipliers (the active line's growth against the rest) */
private const val ACTIVE_LINE_SCALE = 1.12f
private const val INACTIVE_LINE_ALPHA = 0.55f

@Composable
fun NamidaLyrics(
    fullscreen: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalNamidaPalette.current
    val connection = rememberNamidaConnection()
    val lyrics = rememberNamidaLyrics()
    val track = LocalStyleTrack.current
    val position = rememberLivePosition()
    val posMs = position.positionMs()

    val baseSize = (if (fullscreen) FULLSCREEN_LYRICS_SIZE else PANEL_LYRICS_SIZE).sp
    var activeIndex by remember { mutableIntStateOf(-1) }
    val listState = rememberLazyListState()

    // the active line follows the position
    LaunchedEffect(lyrics.mediaId) { activeIndex = -1 }
    val newActive =
        remember(lyrics.lines, posMs) {
            var found = -1
            for ((i, line) in lyrics.lines.withIndex()) {
                if (line.time <= posMs) found = i else break
            }
            found
        }
    LaunchedEffect(newActive) {
        if (newActive != activeIndex && newActive >= 0) {
            activeIndex = newActive
            // namida's follow: the active line rides the middle of the page
            listState.animateScrollToItem(
                index = (newActive - 2).coerceAtLeast(0),
                scrollOffset = 0,
            )
        }
    }

    Box(
        modifier
            .background(palette.scaffold),
    ) {
        // namida's lyrics backdrop: the artwork, blurred and dimmed
        if (fullscreen) {
            AsyncImage(
                model = track.artwork,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .blur(64.dp)
                        .alpha(0.35f),
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(palette.scaffold.copy(alpha = 0.45f)),
            )
        }

        Column(Modifier.fillMaxSize()) {
            if (fullscreen) {
                // the dismiss row
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.namida_arrow_down_2),
                        contentDescription = null,
                        tint = palette.onSecondaryContainer,
                        modifier =
                            Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .clickable { onDismiss() }
                                .padding(8.dp),
                    )
                }
            }

            LazyColumn(
                state = listState,
                horizontalAlignment = if (fullscreen) Alignment.CenterHorizontally else Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding =
                    androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 24.dp,
                        vertical = 64.dp,
                    ),
                modifier =
                    Modifier
                        .fillMaxSize()
                        .weight(1f)
                        .graphicsLayer { alpha = if (fullscreen) 1f else 1f },
            ) {
                items(lyrics.lines.size, key = { lyrics.lines[it].time * 1000 + it }) { i ->
                    val line = lyrics.lines[i]
                    val isActive = i == activeIndex
                    NamidaLyricsLine(
                        line = line,
                        positionMs = posMs,
                        isActive = isActive,
                        baseSize = baseSize,
                        alignCenter = fullscreen,
                        onClick = {
                            connection?.seekTo(line.time.toLong().coerceAtLeast(0))
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    Spacer(Modifier.height(96.dp))
                }
            }
        }
    }
}

@Composable
private fun NamidaLyricsLine(
    line: LyricsEntry,
    positionMs: Long,
    isActive: Boolean,
    baseSize: androidx.compose.ui.unit.TextUnit,
    alignCenter: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalNamidaPalette.current
    val scale by animateFloatAsState(
        targetValue = if (isActive) ACTIVE_LINE_SCALE else 1f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f),
        label = "namidaLyricsFocal",
    )
    val alpha by animateFloatAsState(
        targetValue = if (isActive) 1f else INACTIVE_LINE_ALPHA,
        animationSpec = tween(300),
        label = "namidaLyricsAlpha",
    )

    Column(
        horizontalAlignment = if (alignCenter) Alignment.CenterHorizontally else Alignment.Start,
        modifier =
            modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    this.alpha = alpha
                }.clickable { onClick() }
                .padding(vertical = 2.dp),
    ) {
        when {
            // the karaoke line: word by word through the active line
            line.words != null && line.words.isNotEmpty() -> {
                NamidaKaraokeText(
                    words = line.words!!,
                    positionMs = positionMs,
                    lineStartMs = line.time,
                    color = palette.textMedium,
                    fontSize = baseSize,
                    textAlign = if (alignCenter) TextAlign.Center else TextAlign.Start,
                )
            }

            // the instrumental gap: namida's interlude dots (radius = fontSize * 0.16)
            line.text.isBlank() -> {
                val dot = (baseSize.value * 0.16f * 2).dp
                Row(horizontalArrangement = Arrangement.spacedBy(dot / 2 + 4.dp)) {
                    repeat(3) { i ->
                        val phase = ((positionMs - line.time).coerceAtLeast(0L) % 1200L) / 1200f
                        val bounce = ((phase * 3 - i).rem(3f) + 3f).rem(3f).coerceIn(0f, 1f)
                        Box(
                            Modifier
                                .size(dot)
                                .graphicsLayer { translationY = -bounce * 6f }
                                .background(palette.textMedium.copy(alpha = 0.35f + 0.65f * (1f - bounce)), CircleShape),
                        )
                    }
                }
            }

            else -> {
                Text(
                    line.text,
                    color = palette.textMedium,
                    fontSize = baseSize,
                    fontWeight = FontWeight.W600,
                    fontFamily = NamidaFontFamily,
                    textAlign = if (alignCenter) TextAlign.Center else TextAlign.Start,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        // the translation under the line, at namida's secondary multiplier (0.75 fullscreen)
        line.translatedText?.let { translation ->
            if (translation.isNotBlank() && line.text.isNotBlank()) {
                Text(
                    translation,
                    color = palette.textSmall,
                    fontSize = (baseSize.value * if (alignCenter) 0.75f else 0.85f).sp,
                    fontWeight = FontWeight.W600,
                    fontFamily = NamidaFontFamily,
                    textAlign = if (alignCenter) TextAlign.Center else TextAlign.Start,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

/** namida's karaoke sweep: each word fills as it is sung (LyricsKaraokeText). */
@Composable
private fun NamidaKaraokeText(
    words: List<WordTimestamp>,
    positionMs: Long,
    lineStartMs: Long,
    color: Color,
    fontSize: androidx.compose.ui.unit.TextUnit,
    textAlign: TextAlign,
) {
    val density = LocalDensity.current
    val sungColor = color
    val unsungColor = color.copy(alpha = 0.4f)

    // build the line's spans: sung words full, the singing word split at its
    // own progress, the rest dim — the sweep namida's karaoke text paints
    val annotated =
        androidx.compose.runtime.remember(words, positionMs) {
            androidx.compose.ui.text.buildAnnotatedString {
                for ((i, word) in words.withIndex()) {
                    val start = word.startTime.toLong()
                    val end = word.endTime.toLong()
                    val text = word.text + if (i < words.size - 1) " " else ""
                    when {
                        positionMs >= end -> {
                            withStyle(
                                androidx.compose.ui.text
                                    .SpanStyle(color = sungColor),
                            ) { append(text) }
                        }

                        positionMs < start -> {
                            withStyle(
                                androidx.compose.ui.text
                                    .SpanStyle(color = unsungColor),
                            ) { append(text) }
                        }

                        else -> {
                            // the word mid-flight: split at its own fraction
                            val fraction = if (end > start) (positionMs - start).toFloat() / (end - start) else 1f
                            val cut = (text.length * fraction).roundToInt().coerceIn(0, text.length)
                            withStyle(
                                androidx.compose.ui.text
                                    .SpanStyle(color = sungColor),
                            ) { append(text.substring(0, cut)) }
                            withStyle(
                                androidx.compose.ui.text
                                    .SpanStyle(color = unsungColor),
                            ) { append(text.substring(cut)) }
                        }
                    }
                }
            }
        }
    Text(
        annotated,
        fontSize = fontSize,
        fontWeight = FontWeight.W600,
        fontFamily = NamidaFontFamily,
        textAlign = textAlign,
        modifier = Modifier.fillMaxWidth(),
    )
}
