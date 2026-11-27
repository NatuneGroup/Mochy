// Ported from Mochy Music by realidkroo (github.com/realidkroo/mochy-music). Licensed under GPL-3.0.

// The flagship's bottom deck, Mochy's PlayerBottomCardStack and MiniLyricsTicker: the lyric card in front with the
// line being sung, the queue card peeking over it with the next song. A swipe up flips them, a tap opens the
// page of the card in front, and the front card runs on past the screen's bottom edge so the edge cuts it.

package com.metrolist.shared.ui.player.mochy

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrolist.shared.generated.resources.Res
import com.metrolist.shared.generated.resources.pc_end_of_queue
import com.metrolist.shared.generated.resources.pc_next_song
import com.metrolist.shared.generated.resources.video_mode_no_lyrics
import com.metrolist.shared.model.LyricsEntry
import com.metrolist.shared.ui.components.images.AsyncImage
import com.metrolist.shared.ui.mochy.lyrics.KaraokeSweepText
import com.metrolist.shared.ui.mochy.lyrics.LyricsAlignment
import com.metrolist.shared.ui.mochy.lyrics.LyricsInterludeDots
import com.metrolist.shared.ui.mochy.lyrics.LyricsLoadingDots
import com.metrolist.shared.ui.mochy.lyrics.LyricsMotionStyle
import com.metrolist.shared.ui.mochy.lyrics.LyricsTiming
import com.metrolist.shared.ui.mochy.lyrics.MIN_INTERLUDE_GAP_MS
import com.metrolist.shared.ui.mochy.lyrics.SweepProbe
import com.metrolist.shared.ui.mochy.lyrics.splitLyricLine
import com.metrolist.shared.ui.mochy.lyrics.withLetterMotionFeatures
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs
import kotlin.math.exp

/** The card's visible height. Its body carries on below the screen edge, see [BOTTOM_BLEED]. */
internal val FlagshipCardHeight = 46.dp
private val CardCorner = 24.dp

/** How far the card in front sits from the screen's bottom edge, back card included. */
private val PeekOffset = 7.dp

/** The whole deck's height on screen: the card plus the back card peeking over it. */
internal val FlagshipDeckHeight = FlagshipCardHeight + PeekOffset

// the card is longer than the screen shows, so the screen cuts it rather than the card ending in a line
private val BottomBleed = 100.dp
private val ArtworkSize = 28.dp
private val ArtworkShape = RoundedCornerShape(ArtworkSize * 0.3f)
private val PeekInset = 10.dp
private const val BACK_CARD_SCALE = 0.94f
private val FlipDistance = 70.dp

// the flagship's subtle low-opacity fill and outline, with the card behind even fainter
private const val FRONT_FILL = 0.025f
private const val FRONT_BORDER = 0.08f
private const val BACK_FILL = 0.012f
private const val BACK_BORDER = 0.04f
private val BorderWidth = 1.15.dp

private val LyricContentStart = 18.dp
private val QueueContentStart = 14.dp

/** Which card a tap opened. */
internal enum class FlagshipDeckPage { LYRICS, QUEUE }

@Stable
private class DeckState {
    // 0: the lyric card in front, 1: the queue card
    var frontIndex by mutableIntStateOf(0)
    val flip = Animatable(0f)
}

@Composable
internal fun FlagshipCardDeck(
    lines: List<LyricsEntry>,
    lyricsLoaded: Boolean,
    // the next song in play order, null at the end of the queue
    nextTitle: String?,
    nextArtist: String?,
    nextArtwork: String?,
    // the frame's playback position with the lyrics offset applied
    positionProvider: () -> Long,
    isPlaying: () -> Boolean,
    onOpen: (FlagshipDeckPage) -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
    // where the deck sits in the root, so the page a card opens can grow out of it
    onBounds: (Rect) -> Unit = {},
    motionStyle: LyricsMotionStyle = LyricsMotionStyle.MOTION,
    highBloom: Boolean = true,
) {
    val deck = remember { DeckState() }
    val scope = rememberCoroutineScope()
    val flipDistancePx = with(LocalDensity.current) { FlipDistance.toPx() }
    val currentOnOpen by rememberUpdatedState(onOpen)
    val currentOnLongPress by rememberUpdatedState(onLongPress)

    fun queueFrontness(): Float = deck.flip.value.coerceIn(0f, 1f)

    // only the order is decided in composition, once per flip; everything that moves is read in layers
    val queueOnTop by remember { derivedStateOf { queueFrontness() >= 0.5f } }
    val order =
        if (queueOnTop) {
            listOf(
                FlagshipDeckPage.LYRICS,
                FlagshipDeckPage.QUEUE,
            )
        } else {
            listOf(FlagshipDeckPage.QUEUE, FlagshipDeckPage.LYRICS)
        }

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(FlagshipDeckHeight)
                .onGloballyPositioned { onBounds(it.boundsInRoot()) }
                // directional on purpose: the flip only runs upward, so a downward drag is left for the sheet
                .pointerInput(Unit) {
                    val slop = viewConfiguration.touchSlop
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var claimed = false
                        var travelled = 0f
                        var dragAccum = 0f
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            val dy = change.positionChange().y
                            travelled += dy
                            if (!claimed) {
                                if (travelled < -slop) {
                                    claimed = true
                                    dragAccum = 0f
                                } else if (travelled > slop) {
                                    break
                                }
                            }
                            if (claimed) {
                                change.consume()
                                dragAccum += -dy
                                val frac = (abs(dragAccum) / flipDistancePx).coerceIn(0f, 1f)
                                val target = if (deck.frontIndex == 0) frac else 1f - frac
                                scope.launch { deck.flip.snapTo(target) }
                            }
                        }
                        if (claimed) {
                            if (abs(dragAccum) >= flipDistancePx * 0.4f) deck.frontIndex = 1 - deck.frontIndex
                            scope.launch {
                                deck.flip.animateTo(
                                    targetValue = deck.frontIndex.toFloat(),
                                    animationSpec =
                                        spring(
                                            dampingRatio = Spring.DampingRatioLowBouncy,
                                            stiffness = Spring.StiffnessMediumLow,
                                        ),
                                )
                            }
                        }
                    }
                }.pointerInput(Unit) {
                    detectTapGestures(
                        onLongPress = { currentOnLongPress() },
                        onTap = { currentOnOpen(if (queueFrontness() >= 0.5f) FlagshipDeckPage.QUEUE else FlagshipDeckPage.LYRICS) },
                    )
                },
    ) {
        // keyed and in one loop, so a flip moves the cards instead of rebuilding them
        for (page in order) {
            key(page) {
                DeckCard(
                    frontnessProvider = {
                        val q = queueFrontness()
                        if (page == FlagshipDeckPage.QUEUE) q else 1f - q
                    },
                ) {
                    if (page == FlagshipDeckPage.LYRICS) {
                        MiniLyricsTicker(
                            lines = lines,
                            loaded = lyricsLoaded,
                            positionProvider = positionProvider,
                            isPlaying = isPlaying,
                            motionStyle = motionStyle,
                            highBloom = highBloom,
                            modifier = Modifier.fillMaxSize().padding(horizontal = LyricContentStart),
                        )
                    } else {
                        NextSongRow(nextTitle, nextArtist, nextArtwork)
                    }
                }
            }
        }
    }
}

@Composable
private fun BoxScope.DeckCard(
    frontnessProvider: () -> Float,
    content: @Composable () -> Unit,
) {
    fun frontness(): Float = frontnessProvider().coerceIn(0f, 1f)
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(FlagshipCardHeight)
                .align(Alignment.BottomCenter)
                .graphicsLayer {
                    val f = frontness()
                    val scale = mix(BACK_CARD_SCALE, 1f, f)
                    scaleX = scale
                    scaleY = scale
                    translationY = -mix(PeekOffset.toPx(), 0f, f)
                    transformOrigin = TransformOrigin(0.5f, 1f)
                }.drawBehind {
                    val f = frontness()
                    val inset = mix(PeekInset.toPx(), 0f, f)
                    val corner = CardCorner.toPx()
                    // only the strip of a back card that shows above the front one is drawn
                    val scale = mix(BACK_CARD_SCALE, 1f, f)
                    val otherF = 1f - f
                    val otherScale = mix(BACK_CARD_SCALE, 1f, otherF)
                    val baseHeight = FlagshipCardHeight.toPx()
                    val thisTop = size.height * scale + mix(PeekOffset.toPx(), 0f, f)
                    val otherTop = baseHeight * otherScale + mix(PeekOffset.toPx(), 0f, otherF)
                    val exposed = (thisTop - otherTop) / scale
                    val bottom = size.height + BottomBleed.toPx()
                    val visibleBottom = if (exposed > 0f) minOf(exposed, bottom) else bottom
                    if (visibleBottom <= 0f) return@drawBehind
                    clipRect(bottom = visibleBottom) {
                        val topLeft = Offset(inset, 0f)
                        val cardSize = Size(size.width - inset * 2f, bottom)
                        drawRoundRect(
                            color = Color.White.copy(alpha = mix(BACK_FILL, FRONT_FILL, f)),
                            topLeft = topLeft,
                            size = cardSize,
                            cornerRadius = CornerRadius(corner, corner),
                        )
                        // the border runs down the sides and off the screen with the body, so no line along the bottom
                        drawRoundRect(
                            color = Color.White.copy(alpha = mix(BACK_BORDER, FRONT_BORDER, f)),
                            topLeft = topLeft,
                            size = cardSize,
                            cornerRadius = CornerRadius(corner, corner),
                            style = Stroke(width = BorderWidth.toPx()),
                        )
                    }
                },
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val f = frontness()
                        alpha = f * f * (3f - 2f * f)
                    },
        ) {
            content()
        }
    }
}

/** The queue card's own row: the next song's artwork, and its title and artist. */
@Composable
private fun NextSongRow(
    nextTitle: String?,
    nextArtist: String?,
    nextArtwork: String?,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .height(FlagshipCardHeight)
                .padding(horizontal = QueueContentStart),
    ) {
        if (nextArtwork != null) {
            AsyncImage(
                url = nextArtwork,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(ArtworkSize).clip(ArtworkShape),
            )
        } else {
            Box(Modifier.size(ArtworkSize).clip(ArtworkShape).background(Color.White.copy(alpha = 0.10f)))
        }
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f).fadingRightEdge()) {
            Text(
                text = stringResource(Res.string.pc_next_song),
                style = MaterialTheme.typography.labelSmall.copy(lineHeight = 11.sp),
                color = Color.White.copy(alpha = 0.5f),
            )
            Text(
                text =
                    if (nextTitle != null) {
                        listOfNotNull(nextTitle, nextArtist?.takeIf { it.isNotBlank() }).joinToString(" - ")
                    } else {
                        stringResource(Res.string.pc_end_of_queue)
                    },
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 15.sp),
                fontWeight = FontWeight.SemiBold,
                color = if (nextTitle != null) Color.White else Color.White.copy(alpha = 0.55f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                softWrap = false,
            )
        }
    }
}

private fun Modifier.fadingRightEdge(): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithCache {
            val fade = Brush.horizontalGradient(0f to Color.Black, 0.85f to Color.Black, 1f to Color.Transparent)
            onDrawWithContent {
                drawContent()
                drawRect(brush = fade, blendMode = BlendMode.DstIn)
            }
        }

private val TickerFontSize = 17.sp
private val TickerLineHeight = 22.sp

// the letter motion is sized for 32sp lines; scaled down to match
private const val TICKER_MOTION_SCALE = 17f / 32f

// soft edges where the line runs past the card
private val TickerEdgeFade = 18.dp

// how far into the card the sweep is kept while a long line slides: past the middle, so a little of what's coming shows
private const val SWEEP_KEEP_FRACTION = 0.62f

// critically damped follow time of the sideways slide: it trails the sweep, never jumps
private const val SLIDE_FOLLOW_SEC = 0.32f
private const val DOTS_SCALE = 0.5f
private const val UNSUNG_ALPHA = 0.45f

// the line index follows a 15 Hz position, as the lyrics page does; the sweep reads the frame clock
private const val SLOT_POLL_MS = 66L

private val LineChangeSpring =
    spring(dampingRatio = 0.86f, stiffness = Spring.StiffnessLow, visibilityThreshold = IntOffset.VisibilityThreshold)
private val LineFadeSpring = spring<Float>(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow)

private data class TickerSlot(
    val kind: Int,
    val index: Int,
) {
    companion object {
        const val EMPTY = 0
        const val LINE = 1
        const val GAP = 2
        const val END = 3
        const val LOADING = 4
    }
}

private fun tickerSlot(
    lines: List<LyricsEntry>,
    position: Long,
): TickerSlot {
    if (lines.isEmpty()) return TickerSlot(TickerSlot.EMPTY, -1)
    val index = LyricsTiming.findCurrentLineIndex(lines, position)
    if (index >= lines.size) return TickerSlot(TickerSlot.END, lines.lastIndex)
    if (index < 0) {
        return if (lines.first().time >= MIN_INTERLUDE_GAP_MS) TickerSlot(TickerSlot.GAP, -1) else TickerSlot(TickerSlot.LINE, 0)
    }
    // a long instrumental gap after this line reads as dots, like on the lyrics page
    val next = lines.getOrNull(index + 1)
    if (next != null) {
        val end = LyricsTiming.lineEndMs(lines, index)
        if (next.time - end >= MIN_INTERLUDE_GAP_MS && position > end) return TickerSlot(TickerSlot.GAP, index)
    }
    return TickerSlot(TickerSlot.LINE, index)
}

/**
 * The lyrics page, card-sized: the current line with the same karaoke sweep and letter motion. A line too long
 * for the card slides left as it's sung, keeping the sweep in view, and each new line rolls up from below as
 * the last one leaves.
 */
@Composable
private fun MiniLyricsTicker(
    lines: List<LyricsEntry>,
    loaded: Boolean,
    positionProvider: () -> Long,
    isPlaying: () -> Boolean,
    motionStyle: LyricsMotionStyle,
    highBloom: Boolean,
    modifier: Modifier = Modifier,
) {
    val rowPx = with(LocalDensity.current) { FlagshipCardHeight.roundToPx() }
    val currentPosition by rememberUpdatedState(positionProvider)
    val position = remember { mutableLongStateOf(positionProvider()) }
    LaunchedEffect(Unit) {
        while (true) {
            position.longValue = currentPosition()
            delay(SLOT_POLL_MS)
        }
    }
    val slot by remember(lines) { derivedStateOf { tickerSlot(lines, position.longValue) } }
    // still looking the lyrics up is its own state, so the dots hand over to the first line with the same roll
    val shown = if (!loaded && slot.kind == TickerSlot.EMPTY) TickerSlot(TickerSlot.LOADING, -1) else slot

    Column(modifier = modifier.clipToBounds()) {
        AnimatedContent(
            targetState = shown,
            transitionSpec = {
                // the old line rolls up and out, the new one up and in: the lyrics page's scroll, in a card
                (slideInVertically(LineChangeSpring) { minOf(it, rowPx) } + fadeIn(LineFadeSpring))
                    .togetherWith(slideOutVertically(LineChangeSpring) { -minOf(it, rowPx) } + fadeOut(LineFadeSpring))
                    .using(SizeTransform(clip = false))
            },
            contentAlignment = Alignment.CenterStart,
            label = "miniLyricsLine",
            modifier = Modifier.fillMaxWidth().height(FlagshipCardHeight),
        ) { current ->
            Box(contentAlignment = Alignment.CenterStart, modifier = Modifier.fillMaxSize()) {
                when (current.kind) {
                    TickerSlot.LINE, TickerSlot.END -> {
                        lines.getOrNull(current.index)?.let { entry ->
                            TickerLine(
                                entry = entry,
                                dim = current.kind == TickerSlot.END,
                                lineEndMs = LyricsTiming.lineEndMs(lines, current.index),
                                positionProvider = positionProvider,
                                isPlaying = isPlaying,
                                motionStyle = motionStyle,
                                highBloom = highBloom,
                            )
                        }
                    }

                    TickerSlot.GAP -> {
                        val startMs = if (current.index < 0) 0L else LyricsTiming.lineEndMs(lines, current.index)
                        val endMs = lines.getOrNull(current.index + 1)?.time ?: startMs
                        LyricsInterludeDots(
                            startMs = startMs,
                            endMs = endMs,
                            positionProvider = positionProvider,
                            bloom = motionStyle.shaded,
                            highBloom = highBloom,
                            alignment = LyricsAlignment.START,
                            modifier =
                                Modifier.graphicsLayer {
                                    scaleX = DOTS_SCALE
                                    scaleY = DOTS_SCALE
                                    transformOrigin = TransformOrigin(0f, 0.5f)
                                },
                        )
                    }

                    TickerSlot.LOADING -> {
                        LyricsLoadingDots(alignStart = true)
                    }

                    else -> {
                        Text(
                            text = stringResource(Res.string.video_mode_no_lyrics),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White.copy(alpha = 0.55f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TickerLine(
    entry: LyricsEntry,
    dim: Boolean,
    lineEndMs: Long,
    positionProvider: () -> Long,
    isPlaying: () -> Boolean,
    motionStyle: LyricsMotionStyle,
    highBloom: Boolean,
) {
    val parts = remember(entry) { splitLyricLine(entry) }
    val text = parts.lead.ifBlank { entry.text }
    val words = parts.leadWords
    val karaoke = !dim && !words.isNullOrEmpty() && text.isNotEmpty()

    val probe = remember(entry) { SweepProbe() }
    val viewport = remember { mutableFloatStateOf(0f) }
    val scroll = remember(entry) { mutableFloatStateOf(0f) }

    // the sideways slide: follows the sweep for a word-synced line, the clock for a line-synced one, critically
    // damped so a sweep that jumps (a seek) still glides
    LaunchedEffect(entry, karaoke) {
        var value = 0f
        var velocity = 0f
        var lastMs = -1L
        while (true) {
            var settled = false
            var hasOverflow = false
            withFrameMillis { frameMs ->
                val dt = if (lastMs < 0L) 0.016f else ((frameMs - lastMs) / 1000f).coerceIn(0f, 0.064f)
                lastMs = frameMs
                val overflow = (probe.textWidth - viewport.floatValue).coerceAtLeast(0f)
                hasOverflow = overflow > 0f
                val target =
                    when {
                        overflow <= 0f -> {
                            0f
                        }

                        karaoke -> {
                            (probe.headX - viewport.floatValue * SWEEP_KEEP_FRACTION).coerceIn(0f, overflow)
                        }

                        else -> {
                            val span = (lineEndMs - entry.time).coerceAtLeast(1L).toFloat()
                            val through = (positionProvider() - entry.time) / span
                            overflow * smoothstep((through - 0.1f) / 0.75f)
                        }
                    }
                val omega = 1f / SLIDE_FOLLOW_SEC
                val y = value - target
                val e = exp(-omega * dt)
                val tmp = (velocity + omega * y) * dt
                value = target + (y + tmp) * e
                velocity = (velocity - omega * tmp) * e
                if (abs(value - target) < 0.05f && abs(velocity) < 1f) {
                    value = target
                    velocity = 0f
                    settled = true
                }
                if (value != scroll.floatValue) scroll.floatValue = value
            }
            // rest when a short line doesn't overflow or playback is paused, instead of waking every frame
            if (settled && (!hasOverflow || !isPlaying())) {
                lastMs = -1L
                delay(180L)
            }
        }
    }

    Box(
        contentAlignment = Alignment.CenterStart,
        modifier =
            Modifier
                .fillMaxSize()
                .onSizeChanged { viewport.floatValue = it.width.toFloat() }
                .clipToBounds()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    // fade only the edge the line actually runs past, and only as far as it does
                    val overflow = (probe.textWidth - size.width).coerceAtLeast(0f)
                    if (overflow <= 0f) return@drawWithContent
                    val fade = TickerEdgeFade.toPx()
                    val left = (scroll.floatValue / fade).coerceIn(0f, 1f)
                    val right = ((overflow - scroll.floatValue) / fade).coerceIn(0f, 1f)
                    val edge = (fade / size.width).coerceIn(0f, 0.5f)
                    drawRect(
                        brush =
                            Brush.horizontalGradient(
                                0f to Color.Black.copy(alpha = 1f - left),
                                edge to Color.Black,
                                1f - edge to Color.Black,
                                1f to Color.Black.copy(alpha = 1f - right),
                            ),
                        blendMode = BlendMode.DstIn,
                    )
                },
    ) {
        Box(
            modifier =
                Modifier
                    .wrapContentWidth(align = Alignment.Start, unbounded = true)
                    .graphicsLayer { translationX = -scroll.floatValue },
        ) {
            if (karaoke) {
                KaraokeSweepText(
                    text = text,
                    words = words.orEmpty(),
                    positionProvider = positionProvider,
                    waveAmplitudeProvider = { if (motionStyle.shaded) 1f else 0f },
                    riseAmplitudeProvider = { 1f },
                    baseColor = { Color.White.copy(alpha = UNSUNG_ALPHA) },
                    highlightColor = Color.White,
                    fontSize = TickerFontSize,
                    lineHeight = TickerLineHeight,
                    highBloom = highBloom,
                    motionStyle = motionStyle,
                    singleLine = true,
                    motionScale = TICKER_MOTION_SCALE,
                    probe = probe,
                )
            } else {
                Text(
                    text = text,
                    // the same letter features as the sung line, so the words keep their width when the line goes dim
                    style =
                        MaterialTheme.typography.headlineSmall
                            .copy(fontSize = TickerFontSize, lineHeight = TickerLineHeight)
                            .withLetterMotionFeatures(motionStyle.risesWords),
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = if (dim) 0.55f else 1f),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                    onTextLayout = { probe.textWidth = it.size.width.toFloat() },
                )
            }
        }
    }
}

private fun mix(
    a: Float,
    b: Float,
    t: Float,
): Float = a + (b - a) * t

private fun smoothstep(t: Float): Float {
    val x = t.coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}
