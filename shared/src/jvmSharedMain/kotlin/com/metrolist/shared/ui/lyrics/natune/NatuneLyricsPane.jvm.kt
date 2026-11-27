// Ported from Natune (github.com/NatuneGroup/Natune), ArchiveTune by Rukamori. Licensed under GPL-3.0.

package com.metrolist.shared.ui.lyrics.natune

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextMotion
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrolist.shared.generated.resources.Res
import com.metrolist.shared.generated.resources.lyrics_not_found
import com.metrolist.shared.model.LyricsEntry
import com.metrolist.shared.model.WordTimestamp
import com.mocharealm.accompanist.lyrics.core.model.ISyncedLine
import com.mocharealm.accompanist.lyrics.core.model.SyncedLyrics
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeAlignment
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeLine
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeSyllable
import com.mocharealm.accompanist.lyrics.core.model.synced.SyncedLine
import com.mocharealm.accompanist.lyrics.ui.composable.list.rememberLyricsLazyListState
import com.mocharealm.accompanist.lyrics.ui.composable.lyrics.KaraokeBreathingDotsDefaults
import com.mocharealm.accompanist.lyrics.ui.composable.lyrics.KaraokeLyricsView
import com.mocharealm.accompanist.lyrics.ui.composable.lyrics.LyricsAnchor
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

// Natune's LyricsEnhanced measures
private val FocusAnchor = 64.dp
private const val FOCUS_ANCHOR_MAX_FRACTION = 0.25f
private const val LRC_LEAD_MS = 300L
private const val MIN_SYLLABLE_MS = 1
private const val LYRICS_TEXT_SP = 26f
private val ScrollSpec = tween<Float>(durationMillis = 700, easing = CubicBezierEasing(0.2f, 0f, 0f, 1f))

/**
 * Natune's lyrics pane, its LyricsEnhanced: the karaoke lyrics view, word-synced lines swept word by word,
 * line-synced ones lit whole, the sung line held [FocusAnchor] from the top and the rest dimmed and blurred.
 */
@Composable
actual fun NatuneLyricsPane(
    lines: List<LyricsEntry>,
    isLoading: Boolean,
    positionProvider: () -> Long,
    onSeekToLine: (Long) -> Unit,
    textColor: Color,
    modifier: Modifier,
) {
    val textSizeSp = LYRICS_TEXT_SP
    val lineBlur = true
    val wordSynced = remember(lines) { lines.any { !it.words.isNullOrEmpty() } }
    val synced = remember(lines) { lines.size > 1 && lines.map { it.time }.distinct().size > 1 }
    val syncedLyrics = remember(lines, wordSynced) { buildSyncedLyrics(lines, wordSynced) }
    // line-synced lyrics are lit a beat early, as Natune does, so the line is up as it's sung
    val leadMs = if (wordSynced) 0L else LRC_LEAD_MS
    val currentPosition by rememberUpdatedState(positionProvider)
    val normalStyle =
        MaterialTheme.typography.headlineMedium.copy(
            fontSize = textSizeSp.sp,
            lineHeight = TextUnit.Unspecified,
            letterSpacing = 0.sp,
            fontWeight = FontWeight.Bold,
            textMotion = TextMotion.Animated,
        )
    Box(modifier.padding(bottom = 12.dp), contentAlignment = Alignment.TopCenter) {
        when {
            isLoading && lines.isEmpty() -> {
                Unit
            }

            !synced || syncedLyrics.lines.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (lines.isEmpty()) {
                        Text(
                            text = stringResource(Res.string.lyrics_not_found),
                            style = MaterialTheme.typography.bodyLarge,
                            color = textColor.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center,
                        )
                    } else {
                        // lyrics with no timing read as a still page
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            lines.filter { it.text.isNotBlank() }.forEach { line ->
                                Text(line.text, style = normalStyle, color = textColor)
                            }
                        }
                    }
                }
            }

            else -> {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val anchor = remember(maxHeight) { LyricsAnchor.Fixed(focusAnchor(maxHeight)) }
                    val listState = rememberLyricsLazyListState()
                    KaraokeLyricsView(
                        listState = listState,
                        lyrics = syncedLyrics,
                        currentPosition = {
                            (currentPosition() + leadMs).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
                        },
                        onLineClicked = { line -> if (line.start > 0) onSeekToLine((line.start.toLong() - leadMs).coerceAtLeast(0L)) },
                        onLinePressed = {},
                        textColor = textColor,
                        normalLineTextStyle = normalStyle,
                        accompanimentLineTextStyle = normalStyle.copy(fontSize = (textSizeSp * 0.6f).sp),
                        translationTextStyle = normalStyle.copy(fontSize = (textSizeSp * 0.55f).sp),
                        phoneticTextStyle =
                            normalStyle.copy(
                                fontSize = (textSizeSp * 0.55f).sp,
                                fontWeight = FontWeight.SemiBold,
                                textMotion = TextMotion.Static,
                            ),
                        breathingDotsDefaults = remember(textColor) { KaraokeBreathingDotsDefaults(breathingDotsColor = textColor) },
                        blendMode = BlendMode.Plus,
                        useBlurEffect = lineBlur,
                        showTranslation = true,
                        showPhonetic = true,
                        anchor = anchor,
                        keepAliveZone = 72.dp,
                        scrollAnimationSpec = ScrollSpec,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

private fun focusAnchor(viewportHeight: Dp): Dp = minOf(FocusAnchor, viewportHeight * FOCUS_ANCHOR_MAX_FRACTION)

/** Our lyrics as the karaoke view's lines: word-timed lines as karaoke lines, the rest as plain timed lines. */
private fun buildSyncedLyrics(
    entries: List<LyricsEntry>,
    wordSynced: Boolean,
): SyncedLyrics {
    if (entries.isEmpty()) return SyncedLyrics(emptyList())
    val lines = mutableListOf<ISyncedLine>()
    entries.forEachIndexed { index, entry ->
        if (entry.time < 0L || entry.isBackground) return@forEachIndexed
        if (entry.text.isBlank() && entry.words.isNullOrEmpty()) return@forEachIndexed
        val words = entry.words
        if (wordSynced && !words.isNullOrEmpty()) {
            val mainWords = words.filterNot { it.isBackground }.ifEmpty { words }
            val backgroundWords = words.filter { it.isBackground }
            val alignment = if (entry.agent?.lowercase() == "v2") KaraokeAlignment.End else KaraokeAlignment.Start
            val syllables = mainWords.toSyllables()
            val lineStart = entry.time.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
            val lineEnd =
                (
                    entry.endTime?.toInt() ?: syllables.maxOf(
                        KaraokeSyllable::end,
                    )
                ).coerceAtLeast(syllables.maxOf(KaraokeSyllable::end))
            if (lineEnd <= lineStart) return@forEachIndexed
            val accompaniment =
                if (mainWords !== words && backgroundWords.isNotEmpty()) {
                    val backing = backgroundWords.toSyllables()
                    val start = backing.minOf(KaraokeSyllable::start)
                    val end = backing.maxOf(KaraokeSyllable::end)
                    if (end > start) {
                        listOf(KaraokeLine.AccompanimentKaraokeLine(backing, entry.backgroundTranslatedText, alignment, start, end))
                    } else {
                        null
                    }
                } else {
                    null
                }
            lines +=
                KaraokeLine.MainKaraokeLine(
                    syllables = syllables,
                    translation = entry.translatedText,
                    alignment = alignment,
                    start = lineStart,
                    end = lineEnd,
                    phonetic = entry.romanizedText,
                    accompanimentLines = accompaniment,
                )
        } else {
            val next = entries.getOrNull(index + 1)
            val end =
                when {
                    entry.endTime != null && entry.endTime > entry.time -> {
                        entry.endTime
                    }

                    next != null && next.time > entry.time -> {
                        val gap = next.time - entry.time
                        if (gap > 3000L) minOf(next.time - 1L, entry.time + 4000L) else next.time - 1L
                    }

                    else -> {
                        entry.time + 4000L
                    }
                }.coerceAtLeast(entry.time + 1L)
            lines +=
                SyncedLine(
                    content = entry.text,
                    translation = entry.translatedText,
                    start = entry.time.toInt(),
                    end = end.toInt(),
                    phonetic = entry.romanizedText,
                )
        }
    }
    return SyncedLyrics(lines = lines)
}

private fun List<WordTimestamp>.toSyllables(): List<KaraokeSyllable> =
    map { word ->
        val start = (word.startTime * 1000.0).roundToInt().coerceAtLeast(0)
        val end = (word.endTime * 1000.0).roundToInt().coerceAtLeast(start + MIN_SYLLABLE_MS)
        KaraokeSyllable(content = if (word.hasTrailingSpace) word.text + " " else word.text, start = start, end = end)
    }
