// Ported from Mochy Music by realidkroo (github.com/realidkroo/mochy-music). Licensed under GPL-3.0.

package com.metrolist.shared.ui.mochy.lyrics

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.metrolist.shared.model.LyricsEntry
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlin.math.abs
import kotlin.math.roundToInt

private const val ACTIVE_LINE_VIEWPORT_FRACTION = 0.055f

/**
 * Floor under [ACTIVE_LINE_VIEWPORT_FRACTION], and in practice the value that wins: the active line rests
 * exactly one lyric row from the top, leaving the just-finished line, and only that line, above it. One row
 * is a 40sp line plus 16dp of vertical padding on each side.
 */
private val MinPastLineVisibleHeight = 72.dp

/** The list's top and bottom padding, worked out from its height while it is measured. */
private class LyricsListPadding : PaddingValues {
    var heightPx = 0
    var density = 1f

    private fun listHeight() = (heightPx / density).dp

    override fun calculateTopPadding() = maxOf(listHeight() * ACTIVE_LINE_VIEWPORT_FRACTION, MinPastLineVisibleHeight)

    override fun calculateBottomPadding() = listHeight() * 0.55f

    override fun calculateLeftPadding(layoutDirection: LayoutDirection) = 0.dp

    override fun calculateRightPadding(layoutDirection: LayoutDirection) = 0.dp
}

private const val RECENTER_DURATION_MS = 950

/** Shared with the gap dots, whose collapse has to run in step with the recenter scroll. */
internal const val LYRICS_RECENTER_DURATION_MS = RECENTER_DURATION_MS

/** Style 2's recenter curve (the original). */
internal val LyricsRecenterEasing = CubicBezierEasing(0.5f, 0.45f, 0f, 1f)

/** Style 1's recenter curve: eases in instead of starting at speed, so the list never gets a kick at the start of a line change. */
internal val LyricsSmoothRecenterEasing = CubicBezierEasing(0.42f, 0f, 0.16f, 1f)

internal fun recenterEasingFor(style: LyricsMotionStyle) = if (style.risesWords) LyricsSmoothRecenterEasing else LyricsRecenterEasing

private const val IDLE_RECENTER_DELAY_MS = 3_500L

private const val TOP_FADE_FRACTION = 0.10f

private const val BOTTOM_FADE_START = 0.62f

// how often the line indices are worked out again; the sweep and the dots read the frame clock themselves
private const val LINE_POLL_MS = 66L

/**
 * Mochy's lyrics list: every line and every instrumental gap as its own row, the sung line resting one row
 * from the top, the list easing to each new line, rows below it trailing the scroll in style 1, lines blurred
 * by their distance from the sung one, and where the lyrics came from at the end.
 */
@Composable
fun MochyLyricsScreen(
    lines: List<LyricsEntry>,
    isLoading: Boolean,
    // the frame's playback position with the lyrics offset applied; read in draws and frame loops only
    positionProvider: () -> Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    providerName: String? = null,
    providerUrl: String? = null,
    curator: String? = null,
    curatorUrl: String? = null,
    onSwitchSource: (() -> Unit)? = null,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    motionStyle: LyricsMotionStyle = LyricsMotionStyle.MOTION,
    highBloom: Boolean = true,
    lineBlurAllowed: Boolean = true,
    // the page sits right on a cover-coloured background, so resting lines can soft-light onto it
    onCoverBackdrop: Boolean = false,
    softLightLyrics: Boolean = true,
    isSheetDragging: Boolean = false,
    noLyricsText: String,
    translationLoading: Boolean = false,
) {
    val density = LocalDensity.current
    val sheetDragging by rememberUpdatedState(isSheetDragging)
    val currentPositionProvider by rememberUpdatedState(positionProvider)

    // the line indices follow a 15 Hz position, as Mochy's do; the sweep and the gaps read the frame clock
    val position = remember { mutableLongStateOf(positionProvider()) }
    LaunchedEffect(Unit) {
        while (true) {
            position.longValue = currentPositionProvider()
            delay(LINE_POLL_MS)
        }
    }

    // lyrics with no timing are shown as a static sheet: every line readable, nothing blurred, no follow scrolling
    val unsynced = remember(lines) { LyricsTiming.isUnsynced(lines) || lines.all { it.time == 0L && it.words.isNullOrEmpty() } }

    val currentIndex by remember(lines, unsynced) {
        derivedStateOf { if (unsynced) -1 else LyricsTiming.findCurrentLineIndex(lines, position.longValue) }
    }
    val anchorIndex by remember(lines, unsynced) {
        derivedStateOf { if (unsynced) -1 else lyricAnchorIndex(lines, position.longValue) }
    }

    // pre-warm line wraps once the list has measured, so scrolling past the first screenful hits the cache
    LaunchedEffect(lines) {
        if (lines.isNotEmpty()) {
            delay(120)
            SteadyWraps.warmLines(lines)
        }
    }

    // every instrumental gap as its own list row, so the list never jumps when one opens
    val rows = remember(lines) { buildLyricRows(lines) }

    /** Lyric line index -> its row in [rows]. Gap rows sit between lines, so the two differ. */
    val rowOfLine =
        remember(rows, lines) {
            IntArray(lines.size).also { map ->
                rows.forEachIndexed { rowIndex, row -> if (row is LyricRow.Line) map[row.index] = rowIndex }
            }
        }

    val cascade = motionStyle.risesWords

    // opens already on the playing line; read once without subscribing
    val initialRow =
        Snapshot.withoutReadObservation {
            rowOfLine.getOrNull(anchorIndex.coerceIn(0, (lines.size - 1).coerceAtLeast(0))) ?: 0
        }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialRow)
    var followPlayback by remember { mutableStateOf(true) }

    val stagger = remember(rows) { LyricsStagger(rows.size + 1) }
    LaunchedEffect(stagger) { stagger.run() }

    // reference-counted, so cancelling an older auto-scroll never clears the flag for the newer one
    var autoScrollCount by remember { mutableIntStateOf(0) }
    val isDraggedState = listState.interactionSource.collectIsDraggedAsState()
    val userScrollingState =
        remember {
            derivedStateOf { isDraggedState.value || (listState.isScrollInProgress && autoScrollCount == 0) }
        }
    val suppressEffectsProvider = remember { { userScrollingState.value || sheetDragging } }

    LaunchedEffect(listState) {
        snapshotFlow { userScrollingState.value }.collect { scrolling ->
            if (scrolling) followPlayback = false
        }
    }

    LaunchedEffect(followPlayback, listState) {
        if (followPlayback) return@LaunchedEffect
        snapshotFlow { userScrollingState.value }.collectLatest { scrolling ->
            if (!scrolling) {
                delay(IDLE_RECENTER_DELAY_MS)
                followPlayback = true
            }
        }
    }

    // collected rather than keyed on the line, so a line change doesn't recompose the whole page
    LaunchedEffect(followPlayback, lines, motionStyle) {
        if (!followPlayback) return@LaunchedEffect
        snapshotFlow { anchorIndex }.collectLatest { target ->
            if (target !in lines.indices) return@collectLatest
            val targetRow = rowOfLine[target]

            val viewportHeight =
                snapshotFlow { listState.layoutInfo.viewportSize.height }
                    .first { it > 0 }

            val minOffsetPx = with(density) { MinPastLineVisibleHeight.toPx() }
            val restingOffset = maxOf(viewportHeight * ACTIVE_LINE_VIEWPORT_FRACTION, minOffsetPx).roundToInt()

            autoScrollCount++
            try {
                // the sheet itself on the move: put the line straight where it rests
                if (sheetDragging) {
                    listState.scrollToItem(targetRow, 0)
                    return@collectLatest
                }

                val layoutInfo = listState.layoutInfo
                val visible = layoutInfo.visibleItemsInfo.firstOrNull { it.index == targetRow }
                if (visible != null) {
                    val current = visible.offset - layoutInfo.viewportStartOffset
                    // a gap row above the target collapses during this same scroll and lifts the target by its height
                    val collapsing =
                        layoutInfo.visibleItemsInfo
                            .filter { it.index < targetRow && rows.getOrNull(it.index) is LyricRow.Interlude }
                            .sumOf { it.size }
                    val distance = (current - restingOffset - collapsing).toFloat()

                    stagger.anchorRow = targetRow
                    listState.scroll {
                        var previous = 0f
                        animate(
                            initialValue = 0f,
                            targetValue = distance,
                            animationSpec = tween(durationMillis = RECENTER_DURATION_MS, easing = recenterEasingFor(motionStyle)),
                        ) { value, _ ->
                            val consumed = scrollBy(value - previous)
                            previous += consumed
                            if (cascade && consumed != 0f) {
                                // rows below the active line hold back by what just scrolled, then catch up on their own
                                for (item in listState.layoutInfo.visibleItemsInfo) {
                                    if (item.index > targetRow) stagger.holdBack(item.index, consumed)
                                }
                            }
                        }
                    }
                } else {
                    // jump near the target first, then glide over the last few rows
                    val firstVisible = listState.firstVisibleItemIndex
                    if (abs(targetRow - firstVisible) > 8) {
                        val stagingRow =
                            (targetRow + if (targetRow > firstVisible) -4 else 4)
                                .coerceIn(0, (rows.size - 1).coerceAtLeast(0))
                        listState.scrollToItem(stagingRow, 0)
                    }
                    listState.animateScrollToItem(targetRow, 0)
                }
            } finally {
                autoScrollCount = (autoScrollCount - 1).coerceAtLeast(0)
            }
        }
    }

    /** Which gap, if any, is happening right now. [NO_INTERLUDE] when a line is being sung. */
    val activeInterlude by remember(lines, rows) {
        derivedStateOf { activeInterludeOf(rows, position.longValue) }
    }

    val useAgentAlignment = remember(lines) { usesAgentAlignment(lines) }
    val mirrored = rememberLyricsMirrored(lines)

    val lyricsWaveAnimation = motionStyle.shaded
    val blend =
        remember(onCoverBackdrop, softLightLyrics, listState, stagger) {
            if (onCoverBackdrop && softLightLyrics && SoftLightSupported) {
                LyricsBackdropBlend(TOP_FADE_FRACTION, BOTTOM_FADE_START) {
                    listState.layoutInfo
                    stagger.observe()
                }
            } else {
                null
            }
        }

    val listPadding = remember { LyricsListPadding() }
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .layout { measurable, constraints ->
                    listPadding.heightPx = constraints.maxHeight
                    listPadding.density = this.density
                    val placeable = measurable.measure(constraints)
                    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                },
    ) {
        if (lines.isEmpty()) {
            if (isLoading) {
                LyricsLoadingDots(modifier = Modifier.align(Alignment.Center))
            } else {
                Text(
                    text = noLyricsText,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center,
                    modifier =
                        Modifier
                            .align(Alignment.Center)
                            .padding(horizontal = LyricsHeaderArtX),
                )
            }
        } else {
            LazyColumn(
                state = listState,
                contentPadding = listPadding,
                userScrollEnabled = true,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .then(
                            if (blend != null) {
                                // no buffer of its own here, it would cut the soft-lit lines off from the background; the rows fade themselves
                                Modifier.onPlaced { blend.list = it }
                            } else {
                                Modifier
                                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                                    .drawWithCache {
                                        val fade =
                                            Brush.verticalGradient(
                                                0f to Color.Transparent,
                                                TOP_FADE_FRACTION to Color.Black,
                                                BOTTOM_FADE_START to Color.Black,
                                                1f to Color.Transparent,
                                            )
                                        onDrawWithContent {
                                            drawContent()
                                            drawRect(brush = fade, blendMode = BlendMode.DstIn)
                                        }
                                    }
                            },
                        ),
            ) {
                itemsIndexed(
                    items = rows,
                    key = { _, row -> row.key },
                    contentType = { _, row -> if (row is LyricRow.Interlude) "interlude" else "lyric" },
                ) { listIndex, row ->
                    when (row) {
                        is LyricRow.Interlude -> {
                            LyricsInterludeDots(
                                modifier =
                                    Modifier
                                        .graphicsLayer { translationY = stagger.lagOf(listIndex) }
                                        .lyricsEdgeFade(blend),
                                startMs = row.startMs,
                                endMs = row.endMs,
                                positionProvider = positionProvider,
                                bloom = lyricsWaveAnimation,
                                highBloom = highBloom,
                                visible = activeInterlude == row.afterIndex,
                                collapseEasing = recenterEasingFor(motionStyle),
                                // the gap belongs to whoever sings next, so it waits on that singer's side
                                alignment =
                                    lines
                                        .getOrNull(row.afterIndex + 1)
                                        ?.let { alignmentFor(it, useAgentAlignment, mirrored) }
                                        ?: LyricsAlignment.CENTER,
                            )
                        }

                        is LyricRow.Line -> {
                            val index = row.index
                            val entry = row.entry

                            // worked out per row, so a line change only recomposes the few rows whose look changes
                            val look by remember(index, lines, unsynced) {
                                derivedStateOf {
                                    lineLook(index, currentIndex, anchorIndex, unsynced, activeInterlude != NO_INTERLUDE)
                                }
                            }
                            val rowIndex = rowOfLine[index]
                            val translation = entry.translatedText?.takeIf { it.isNotBlank() }
                            LyricsGlowLine(
                                modifier =
                                    Modifier.graphicsLayer {
                                        translationY = stagger.lagOf(rowIndex)
                                        // a buffer here would cut the soft light off from the background while the row fades in
                                        if (blend != null) compositingStrategy = CompositingStrategy.ModulateAlpha
                                    },
                                entry = entry,
                                state = look.state,
                                blurStage = if (!lineBlurAllowed) 0 else look.blurStage,
                                suppressEffectsProvider = suppressEffectsProvider,
                                positionProvider = positionProvider,
                                accentColor = accentColor,
                                subLine = entry.romanizedText?.takeIf { it.isNotBlank() },
                                translationLoading = translationLoading && translation == null,
                                translation = translation,
                                alignment = alignmentFor(entry, useAgentAlignment, mirrored),
                                waveEnabled = lyricsWaveAnimation,
                                motionStyle = motionStyle,
                                highBloom = highBloom,
                                blend = blend,
                                onClick = {
                                    // untimed lines have nowhere to seek to
                                    if (!unsynced) {
                                        onSeek(entry.time)
                                        followPlayback = true
                                    }
                                },
                            )
                        }
                    }
                }

                // where the lyrics came from, after the last line
                if (providerName != null) {
                    item(key = "source", contentType = "source") {
                        LyricsSourceFooter(
                            providerName = providerName,
                            providerUrl = providerUrl,
                            curator = curator,
                            curatorUrl = curatorUrl,
                            onSwitchSource = onSwitchSource,
                            modifier =
                                Modifier
                                    .graphicsLayer { translationY = stagger.lagOf(rows.size) }
                                    .lyricsEdgeFade(blend)
                                    .padding(horizontal = LyricsHeaderArtX)
                                    .padding(top = 36.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Sentinel for "no instrumental gap is playing right now". */
internal const val NO_INTERLUDE = Int.MIN_VALUE

// the line the list rests on: the sung one, or an earlier one whose words are still running into it
internal fun lyricAnchorIndex(
    lines: List<LyricsEntry>,
    positionMs: Long,
): Int {
    var i = LyricsTiming.findCurrentLineIndex(lines, positionMs)
    // past the final line there is no active line to anchor
    if (i <= 0 || i >= lines.size) return i
    while (i > 0) {
        val previous = i - 1
        val endMs =
            lines[previous]
                .words
                ?.lastOrNull()
                ?.let { (it.endTime * 1000).toLong() }
                ?: return i
        if (endMs <= positionMs) return i
        i = previous
    }
    return i
}

// the gap playing right now, or NO_INTERLUDE; it ends with the same lead-in the next line starts with, so the dots collapse on the frame that line takes over
internal fun activeInterludeOf(
    rows: List<LyricRow>,
    positionMs: Long,
): Int =
    rows
        .firstOrNull {
            it is LyricRow.Interlude &&
                positionMs >= it.startMs &&
                positionMs + LyricsTiming.LINE_LEAD_IN_MS < it.endMs
        }?.let { (it as LyricRow.Interlude).afterIndex } ?: NO_INTERLUDE

// left and right only mean something when the song really has more than one voice; untagged lines count as the lead, background ones don't count
internal fun usesAgentAlignment(lines: List<LyricsEntry>): Boolean =
    lines
        .filter { !it.isBackground }
        .map { it.agent ?: "v1" }
        .distinct()
        .size > 1

// how a line looks right now: its state, and how blurred it is by its distance from the sung line
internal data class LineLook(
    val state: LyricsLineState,
    val blurStage: Int,
)

internal fun lineLook(
    index: Int,
    current: Int,
    anchor: Int,
    unsynced: Boolean,
    interlude: Boolean,
): LineLook {
    // no timing: every line is equally current, so all of them read at full strength
    if (unsynced) return LineLook(LyricsLineState.ACTIVE, 0)
    val distance =
        when {
            current < 0 -> index - current
            index < anchor -> index - anchor
            index > current -> index - current
            else -> 0
        }
    val state =
        when {
            current >= 0 && index in anchor..current -> if (interlude) LyricsLineState.PAST else LyricsLineState.ACTIVE
            index == current + 1 -> LyricsLineState.UPCOMING
            index < current -> LyricsLineState.PAST
            else -> LyricsLineState.DEFAULT
        }
    val blurStage =
        when (abs(distance)) {
            0 -> 0
            1 -> 1
            else -> 2
        }
    return LineLook(state, blurStage)
}

/** One row of the lyrics list: a line, or an instrumental gap in its own right. */
internal sealed interface LyricRow {
    /** Stable across the life of the song, so the list never re-creates a row. */
    val key: Long

    data class Line(
        val index: Int,
        val entry: LyricsEntry,
    ) : LyricRow {
        override val key get() = index.toLong()
    }

    /** [afterIndex] is the line this gap follows, or -1 for the intro before the first line. */
    data class Interlude(
        val afterIndex: Int,
        val startMs: Long,
        val endMs: Long,
    ) : LyricRow {
        // offset into a range no line index can reach, so lines and gaps never collide
        override val key get() = -1_000_000L - afterIndex
    }
}

internal fun buildLyricRows(lines: List<LyricsEntry>): List<LyricRow> {
    if (lines.isEmpty()) return emptyList()
    val rows = ArrayList<LyricRow>(lines.size + 4)

    // a long instrumental intro gets dots too
    if (lines.first().time >= MIN_INTERLUDE_GAP_MS) {
        rows.add(LyricRow.Interlude(afterIndex = -1, startMs = 0L, endMs = lines.first().time))
    }

    for (index in lines.indices) {
        rows.add(LyricRow.Line(index, lines[index]))
        val next = lines.getOrNull(index + 1) ?: continue
        val gapStart = LyricsTiming.lineEndMs(lines, index)
        if (next.time - gapStart >= MIN_INTERLUDE_GAP_MS) {
            rows.add(LyricRow.Interlude(afterIndex = index, startMs = gapStart, endMs = next.time))
        }
    }
    return rows
}

/**
 * Maps a line's agent tag onto a side of the screen: `v1` leads on the left, `v2` answers on the right,
 * `v1000` is a group line and sits centred; background vocals centre as well. [mirrored] swaps the sides.
 */
internal fun alignmentFor(
    entry: LyricsEntry,
    useAgentAlignment: Boolean,
    mirrored: Boolean = false,
): LyricsAlignment {
    val side =
        when {
            !useAgentAlignment -> LyricsAlignment.START
            entry.isBackground -> LyricsAlignment.CENTER
            entry.agent == "v2" -> LyricsAlignment.END
            entry.agent == "v1000" -> LyricsAlignment.CENTER
            else -> LyricsAlignment.START
        }
    if (!mirrored) return side
    return when (side) {
        LyricsAlignment.START -> LyricsAlignment.END
        LyricsAlignment.END -> LyricsAlignment.START
        LyricsAlignment.CENTER -> LyricsAlignment.CENTER
    }
}

// arabic, hebrew, persian... lyrics read from the right edge
@Composable
internal fun rememberLyricsMirrored(lines: List<LyricsEntry>): Boolean {
    val appRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val lyricsRtl = remember(lines) { lyricsReadRtl(lines) }
    return lyricsRtl != appRtl
}

// the whole song goes one way, so a latin line in an arabic song doesn't jump to the other side
internal fun lyricsReadRtl(lines: List<LyricsEntry>): Boolean {
    var rtl = 0
    var ltr = 0
    for (line in lines) {
        for (ch in line.text) {
            if (ch.isRightToLeft()) {
                rtl++
                break
            }
            if (ch.isLeftToRightLetter()) {
                ltr++
                break
            }
        }
    }
    return rtl > ltr
}

@Composable
internal fun LyricsLoadingDots(
    modifier: Modifier = Modifier,
    /** Start-aligned with a little air between the dots for the bottom card; centred on the page. */
    alignStart: Boolean = false,
) {
    val transition = rememberInfiniteTransition(label = "lyricsLoading")
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (alignStart) Arrangement.spacedBy(5.dp) else Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(3) { index ->
            val phase by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec =
                    infiniteRepeatable(
                        animation =
                            tween(
                                durationMillis = 900,
                                delayMillis = index * 150,
                                easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f),
                            ),
                        repeatMode = RepeatMode.Reverse,
                    ),
                label = "lyricsLoadingDot$index",
            )
            Box(
                modifier =
                    Modifier
                        .size(6.dp)
                        .graphicsLayer {
                            alpha = 0.3f + 0.7f * phase
                            val s = 0.75f + 0.25f * phase
                            scaleX = s
                            scaleY = s
                        }.clip(CircleShape)
                        .background(Color.White),
            )
        }
    }
}
