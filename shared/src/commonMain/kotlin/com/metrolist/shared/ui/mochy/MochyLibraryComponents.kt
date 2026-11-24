// Ported and adapted from Mochy Music by realidkroo. Licensed under GPL-3.0.

package com.metrolist.shared.ui.mochy

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrolist.shared.generated.resources.Res
import com.metrolist.shared.generated.resources.mochy_library_index_scrubber
import com.metrolist.shared.model.YTItem
import com.metrolist.shared.ui.haptics.HapticEvent
import com.metrolist.shared.ui.haptics.rememberMetrolistHaptics
import com.metrolist.shared.ui.theme.mochyInterFontFamily
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

private const val SYMBOL_BUCKET = "#"
private const val MAX_RAIL_LABELS = 22
private const val HANGUL_SYLLABLE_START = 0xAC00
private const val HANGUL_SYLLABLE_END = 0xD7A3
private const val HANGUL_MEDIAL_COUNT = 21
private const val HANGUL_FINAL_COUNT = 28

private val HangulInitialBuckets =
    arrayOf(
        "ㄱ", "ㄱ", "ㄴ", "ㄷ", "ㄷ",
        "ㄹ", "ㅁ", "ㅂ", "ㅂ", "ㅅ",
        "ㅅ", "ㅇ", "ㅈ", "ㅈ", "ㅊ",
        "ㅋ", "ㅌ", "ㅍ", "ㅎ",
    )

internal data class LibraryIndexEntry(
    val bucket: String,
    val itemIndex: Int,
)

internal fun libraryIndexBucket(title: String): String {
    val clean = title.trimStart { !it.isLetterOrDigit() }
    val first = clean.firstOrNull() ?: return SYMBOL_BUCKET
    val ascii = foldLatinAscii(first)
    if (ascii in 'A'..'Z') return ascii.toString()
    if (first.isDigit()) return SYMBOL_BUCKET
    val code = first.code
    if (code in HANGUL_SYLLABLE_START..HANGUL_SYLLABLE_END) {
        val initial = (code - HANGUL_SYLLABLE_START) / (HANGUL_MEDIAL_COUNT * HANGUL_FINAL_COUNT)
        return HangulInitialBuckets.getOrElse(initial) { SYMBOL_BUCKET }
    }
    return when (first) {
        in '\u3041'..'\u304A', in '\u30A1'..'\u30AA' -> "あ"
        in '\u304B'..'\u3054', in '\u30AB'..'\u30B4' -> "か"
        in '\u3055'..'\u305E', in '\u30B5'..'\u30BE' -> "さ"
        in '\u305F'..'\u3069', in '\u30BF'..'\u30C9' -> "た"
        in '\u306A'..'\u306E', in '\u30CA'..'\u30CE' -> "な"
        in '\u306F'..'\u307D', in '\u30CF'..'\u30DD' -> "は"
        in '\u307E'..'\u3082', in '\u30DE'..'\u30E2' -> "ま"
        in '\u3083'..'\u3088', in '\u30E3'..'\u30E8' -> "や"
        in '\u3089'..'\u308D', in '\u30E9'..'\u30ED' -> "ら"
        in '\u308E'..'\u3093', in '\u30EE'..'\u30F3' -> "わ"
        else -> SYMBOL_BUCKET
    }
}

private fun foldLatinAscii(ch: Char): Char =
    when (val upper = ch.uppercaseChar()) {
        in 'À'..'Å', 'Ā', 'Ă', 'Ą' -> 'A'
        'Ç', 'Ć', 'Ĉ', 'Ċ', 'Č' -> 'C'
        'Ð', 'Ď', 'Đ' -> 'D'
        in 'È'..'Ë', 'Ē', 'Ĕ', 'Ė', 'Ę', 'Ě' -> 'E'
        'Ĝ', 'Ğ', 'Ġ', 'Ģ' -> 'G'
        'Ĥ', 'Ħ' -> 'H'
        in 'Ì'..'Ï', 'Ĩ', 'Ī', 'Ĭ', 'Į', 'İ' -> 'I'
        'Ĵ' -> 'J'
        'Ķ' -> 'K'
        'Ĺ', 'Ļ', 'Ľ', 'Ŀ', 'Ł' -> 'L'
        'Ñ', 'Ń', 'Ņ', 'Ň' -> 'N'
        in 'Ò'..'Ö', 'Ø', 'Ō', 'Ŏ', 'Ő' -> 'O'
        'Ŕ', 'Ŗ', 'Ř' -> 'R'
        'Ś', 'Ŝ', 'Ş', 'Š', 'ẞ' -> 'S'
        'Ţ', 'Ť', 'Ŧ' -> 'T'
        in 'Ù'..'Ü', 'Ũ', 'Ū', 'Ŭ', 'Ů', 'Ű', 'Ų' -> 'U'
        'Ŵ' -> 'W'
        'Ý', 'Ÿ', 'Ŷ' -> 'Y'
        'Ź', 'Ż', 'Ž' -> 'Z'
        else -> upper
    }

internal fun buildLibraryIndex(
    items: List<YTItem>,
    titleOf: (YTItem) -> String,
): List<LibraryIndexEntry> {
    if (items.isEmpty()) return emptyList()
    val entries = ArrayList<LibraryIndexEntry>()
    val seen = HashSet<String>()
    items.forEachIndexed { index, item ->
        val bucket = libraryIndexBucket(titleOf(item))
        if (seen.add(bucket)) {
            entries.add(LibraryIndexEntry(bucket = bucket, itemIndex = index))
        }
    }
    return entries
}

@Composable
internal fun MochyLibrarySectionDivider(
    modifier: Modifier = Modifier,
) {
    HorizontalDivider(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
    )
}

@Composable
internal fun MochyLibraryIndexRail(
    entries: List<LibraryIndexEntry>,
    currentItemIndex: () -> Int,
    itemOffset: Int,
    onJumpToItem: (Int) -> Unit,
    modifier: Modifier = Modifier,
    topPadding: Dp = 0.dp,
    bottomPadding: Dp = 0.dp,
) {
    if (entries.size < 4) return
    val haptics = rememberMetrolistHaptics()
    val latestEntries by rememberUpdatedState(entries)
    val latestJump by rememberUpdatedState(onJumpToItem)
    val latestOffset by rememberUpdatedState(itemOffset)
    val scrubberLabel = stringResource(Res.string.mochy_library_index_scrubber)
    val density = LocalDensity.current

    var dragging by remember { mutableStateOf(false) }
    var activeBucketIndex by remember { mutableIntStateOf(-1) }
    var pointerY by remember { mutableFloatStateOf(0f) }
    var railHeightPx by remember { mutableIntStateOf(0) }

    val displayedBuckets =
        remember(entries) {
            if (entries.size <= MAX_RAIL_LABELS) {
                entries
            } else {
                List(MAX_RAIL_LABELS) { slot ->
                    val mapped = (slot * entries.lastIndex.toFloat() / (MAX_RAIL_LABELS - 1)).roundToInt()
                    entries[mapped]
                }.distinctBy { it.bucket }
            }
        }

    val activeRelativeIndex = (currentItemIndex() - itemOffset).coerceAtLeast(0)
    val highlightedBucket =
        if (dragging && activeBucketIndex in entries.indices) {
            entries[activeBucketIndex].bucket
        } else {
            entries.lastOrNull { it.itemIndex <= activeRelativeIndex }?.bucket ?: entries.first().bucket
        }

    Box(
        modifier =
            modifier
                .padding(top = topPadding + 48.dp, bottom = bottomPadding + 16.dp, end = 4.dp)
                .fillMaxHeight(),
        contentAlignment = Alignment.CenterEnd,
    ) {
        AnimatedVisibility(
            visible = dragging && activeBucketIndex in entries.indices,
            enter = fadeIn() + scaleIn(initialScale = 0.85f),
            exit = fadeOut() + scaleOut(targetScale = 0.85f),
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 34.dp)
                    .offset {
                        val halfBubble = with(density) { 24.dp.roundToPx() }
                        val maxY = (railHeightPx - halfBubble * 2).coerceAtLeast(0)
                        IntOffset(0, (pointerY.roundToInt() - halfBubble).coerceIn(0, maxY))
                    },
        ) {
            val activeLabel = entries.getOrNull(activeBucketIndex)?.bucket.orEmpty()
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shadowElevation = 4.dp,
            ) {
                Box(
                    modifier = Modifier.size(48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = activeLabel,
                        fontFamily = mochyInterFontFamily(),
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        Column(
            modifier =
                Modifier
                    .width(24.dp)
                    .clip(CircleShape)
                    .background(
                        MaterialTheme.colorScheme.surfaceContainerHigh.copy(
                            alpha = if (dragging) 0.92f else 0.65f,
                        ),
                    ).border(
                        0.75.dp,
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                        CircleShape,
                    ).onSizeChanged { railHeightPx = it.height }
                    .semantics { contentDescription = scrubberLabel }
                    .pointerInput(entries.size) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            val list = latestEntries
                            if (list.isEmpty() || size.height <= 0) return@awaitEachGesture

                            fun indexFor(y: Float): Int {
                                val fraction = (y / size.height.toFloat()).coerceIn(0f, 1f)
                                return (fraction * list.lastIndex).roundToInt().coerceIn(0, list.lastIndex)
                            }

                            var lastIdx = indexFor(down.position.y)
                            dragging = true
                            activeBucketIndex = lastIdx
                            pointerY = down.position.y
                            haptics.perform(HapticEvent.SEGMENT_TICK)
                            latestJump(latestOffset + list[lastIdx].itemIndex)
                            down.consume()

                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                pointerY = change.position.y
                                val nextIdx = indexFor(change.position.y)
                                if (nextIdx != lastIdx) {
                                    lastIdx = nextIdx
                                    activeBucketIndex = nextIdx
                                    haptics.perform(HapticEvent.SEGMENT_TICK)
                                    latestJump(latestOffset + list[nextIdx].itemIndex)
                                }
                                change.consume()
                            }
                            dragging = false
                        }
                    }.padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            displayedBuckets.forEach { entry ->
                val isLit = entry.bucket == highlightedBucket
                Text(
                    text = entry.bucket,
                    fontFamily = mochyInterFontFamily(),
                    fontWeight = if (isLit) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 10.sp,
                    lineHeight = 12.sp,
                    color =
                        if (isLit) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f)
                        },
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
