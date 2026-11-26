// Ported from Mochy Music by realidkroo (LyricsGlowLine.kt). Licensed under GPL-3.0.

package com.metrolist.shared.ui.mochy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.metrolist.shared.model.LyricsEntry
import com.metrolist.shared.ui.theme.mochyInterFontFamily

/** True while the Mochy lyrics page is on screen; the lyrics view then draws its active line with [MochyGlowLine]. */
val LocalMochyLyricsLook = compositionLocalOf { false }

// the reference's resting sizes: the sung line is set extra bold, heavy like apple music's lyrics
private val MochyGlowFontSize = 32.sp
private val MochyGlowLineHeight = 40.sp
private val MochyGlowSungWeight = FontWeight.ExtraBold

// the line being sung is zoomed in a little, as the reference's ACTIVE scale is
internal const val MOCHY_GLOW_ACTIVE_SCALE = 1.06f

// words still to come are a faint copy of the line's colour, the way the reference's unsung alpha is
internal const val MOCHY_GLOW_UNSUNG_ALPHA = 0.58f

// the glow swells as a word starts and dies away as it ends, over these shares of the word
internal const val MOCHY_GLOW_ATTACK = 0.35f
internal const val MOCHY_GLOW_RELEASE = 0.3f

// the glow's strongest alpha, and how far the soft edge of the fill reaches past the sung point
internal const val MOCHY_GLOW_ALPHA = 0.16f
private const val MOCHY_GLOW_SOFT_EDGE = 0.05f

/** A word's place on the line, in milliseconds from the line's own start. */
internal data class MochyWordTiming(
    val text: String,
    val startMs: Long,
    val endMs: Long,
    val trailingSpace: Boolean,
)

internal fun mochySmoothstep(t: Float): Float {
    val x = t.coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}

/**
 * How far a word has been sung, 0 to 1, from the line's clock. A word with no length is sung
 * the moment the clock reaches it.
 */
internal fun mochyWordProgress(
    lineRelMs: Long,
    word: MochyWordTiming,
): Float {
    val length = (word.endMs - word.startMs).coerceAtLeast(1L)
    return when {
        lineRelMs >= word.endMs -> 1f
        lineRelMs < word.startMs -> 0f
        else -> (lineRelMs - word.startMs).toFloat() / length
    }
}

/** The glow for a word at progress [p]: it swells as the word starts and fades as it ends. */
internal fun mochyGlowStrength(p: Float): Float = mochySmoothstep(p / MOCHY_GLOW_ATTACK) * mochySmoothstep((1f - p) / MOCHY_GLOW_RELEASE)

/**
 * The words of a word-timed [entry], in order, each placed against the line's start. Empty when
 * the source gave no word timing, so the caller keeps its own renderer for that line.
 */
internal fun mochyWordTimings(entry: LyricsEntry): List<MochyWordTiming> {
    val words = entry.words ?: return emptyList()
    return words.map { word ->
        val startMs = ((word.startTime * 1000).toLong() - entry.time).coerceAtLeast(0L)
        val endMs = ((word.endTime * 1000).toLong() - entry.time).coerceAtLeast(startMs + 1L)
        MochyWordTiming(word.text, startMs, endMs, word.hasTrailingSpace)
    }
}

/**
 * The line being sung on the Mochy look: each word fills in as it is sung, with a soft glow that
 * swells on its way. The clock is read every frame, but only this line redraws with it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MochyGlowLine(
    entry: LyricsEntry,
    positionProvider: () -> Long,
    color: Color,
    textAlign: TextAlign,
    modifier: Modifier = Modifier,
) {
    val words = remember(entry.time, entry.words) { mochyWordTimings(entry) }
    val nowMs by produceState(initialValue = positionProvider()) {
        while (true) {
            withFrameMillis { }
            value = positionProvider()
        }
    }
    val lineRelMs = (nowMs - entry.time).coerceAtLeast(0L)
    val arrangement =
        when (textAlign) {
            TextAlign.Center -> Arrangement.Center
            TextAlign.End, TextAlign.Right -> Arrangement.End
            else -> Arrangement.Start
        }
    val fontFamily = mochyInterFontFamily()

    FlowRow(
        modifier =
            modifier.fillMaxWidth().graphicsLayer {
                scaleX = MOCHY_GLOW_ACTIVE_SCALE
                scaleY = MOCHY_GLOW_ACTIVE_SCALE
            },
        horizontalArrangement = arrangement,
    ) {
        words.forEach { word ->
            val progress = mochyWordProgress(lineRelMs, word)
            val glow = mochyGlowStrength(progress)
            val sungEdge = progress.coerceIn(0f, 1f)
            Text(
                text = if (word.trailingSpace) "${word.text} " else word.text,
                style =
                    TextStyle(
                        brush =
                            Brush.horizontalGradient(
                                0f to color,
                                (sungEdge - MOCHY_GLOW_SOFT_EDGE).coerceAtLeast(0f) to color,
                                (sungEdge + MOCHY_GLOW_SOFT_EDGE).coerceAtMost(1f) to color.copy(alpha = MOCHY_GLOW_UNSUNG_ALPHA),
                                1f to color.copy(alpha = MOCHY_GLOW_UNSUNG_ALPHA),
                            ),
                        fontFamily = fontFamily,
                        fontWeight = MochyGlowSungWeight,
                        fontSize = MochyGlowFontSize,
                        lineHeight = MochyGlowLineHeight,
                        shadow =
                            Shadow(
                                color = color.copy(alpha = MOCHY_GLOW_ALPHA * glow),
                                offset = Offset.Zero,
                                blurRadius = (12f * glow).coerceAtLeast(0.1f),
                            ),
                    ),
            )
        }
    }
}
