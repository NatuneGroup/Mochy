// Ported from Mochy Music by realidkroo (github.com/realidkroo/mochy-music). Licensed under GPL-3.0.

// Mochy's alternate player styles that are complete designs of their own: Poster, Timeline and Lyrics stage.

package com.metrolist.shared.ui.player.mochy.styles

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.metrolist.shared.generated.resources.Res
import com.metrolist.shared.generated.resources.ps_style_no_synced_lyrics
import com.metrolist.shared.model.LyricsEntry
import com.metrolist.shared.ui.components.images.AsyncImage
import com.metrolist.shared.ui.mochy.lyrics.LyricsTiming
import com.metrolist.shared.ui.player.PlayerBlurredArtworkBackground
import com.metrolist.shared.ui.theme.mochyInterFontFamily
import org.jetbrains.compose.resources.stringResource

/** The alternate styles this port draws, beside the flagship family and the iPod. */
enum class AlternateStyle { POSTER, TIMELINE, LYRICS }

/** the player's alternate styles, drawn as they look on the player or, [preview], on a selector card */
@Composable
fun AlternatePlayerStyle(
    style: AlternateStyle,
    actions: StyleActions,
    modifier: Modifier = Modifier,
    preview: Boolean = false,
) {
    when (style) {
        AlternateStyle.POSTER -> PosterPlayerStyle(actions, modifier, preview)
        AlternateStyle.TIMELINE -> TimelinePlayerStyle(actions, modifier, preview)
        AlternateStyle.LYRICS -> LyricsPlayerStyle(actions, modifier, preview)
    }
}

/** what every alternate style reads from the player */
@Immutable
class StyleTrack(
    val title: String,
    val artist: String,
    val album: String,
    val artwork: String?,
)

/** The playing song as the styles show it; the player provides it. */
val LocalStyleTrack = compositionLocalOf { StyleTrack("", "", "", null) }

/** The playing song's lyric lines as the player has them; the player provides them. */
val LocalStyleLyricLines = compositionLocalOf<List<LyricsEntry>> { emptyList() }

/**
 * The current song's synced lines, the background vocals left out. Lyrics without timings show nothing here:
 * a style performs lines in time.
 */
@Composable
internal fun rememberStyleLyrics(): List<LyricsEntry> {
    val all = LocalStyleLyricLines.current
    return remember(all) {
        all
            .takeIf { it.isNotEmpty() && !LyricsTiming.isUnsynced(it) }
            .orEmpty()
            .filter { !it.isBackground && it.text.isNotBlank() }
    }
}

// the frame every style is drawn in: tones, gestures, the window's shape, the chevron menu's state
@Composable
internal fun StyleFrame(
    preview: Boolean,
    modifier: Modifier,
    tones: StyleTones,
    // what lies under the style and holds still when it turns; the playhead split when null
    background: (@Composable BoxScope.() -> Unit)? = null,
    content: @Composable BoxScope.(landscape: Boolean, menuOpen: Boolean, setMenuOpen: (Boolean) -> Unit) -> Unit,
) {
    val playback = rememberStylePlayback()
    var menuOpen by remember { mutableStateOf(false) }
    StyleStage(
        tones = tones,
        background = background,
        modifier =
            modifier.then(
                if (preview) {
                    // a card shows the style, it doesn't play it
                    Modifier.pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                        }
                    }
                } else {
                    Modifier
                },
            ),
    ) { landscape ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .styleGestures(
                        enabled = !preview && playback != null,
                        interactive = LocalStyleInteractive.current,
                        onToggle = { playback?.togglePlayPause?.invoke() },
                        playback = { playback },
                    ),
        ) {
            // Inter and nothing else, in every style
            ProvideTextStyle(TextStyle(fontFamily = mochyInterFontFamily())) {
                content(landscape, menuOpen) { menuOpen = it }
            }
        }
    }
}

// where the system bars fall on a style; in landscape the status bar is hidden, so only the side bars and the cutout are left
internal class StyleInsets(
    val start: Dp,
    val top: Dp,
    val end: Dp,
    val bottom: Dp,
)

@Composable
internal fun styleInsets(landscape: Boolean): StyleInsets {
    val direction = LocalLayoutDirection.current
    val sides = WindowInsets.navigationBars.union(WindowInsets.displayCutout).asPaddingValues()
    val status = if (landscape) 0.dp else WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    return StyleInsets(
        start = sides.calculateStartPadding(direction),
        top = status,
        end = sides.calculateEndPadding(direction),
        bottom = sides.calculateBottomPadding(),
    )
}

// ---- Poster ------------------------------------------------------------------------------

/** how far the words move down while the cover's seek bar is out */
private val RingPush = 34.dp

/** the menu's chevron, the Turntable's size */
private val PosterChevron = 43.dp

/**
 * The cover cut into a great circle swung in from the top-left corner, over the flagship's own background, and
 * beneath it the song's words performed big with the artist and title set small under them. A song with no
 * synced lyrics shouts its title instead. Turned sideways, the circle swings in from the left.
 */
@Composable
private fun PosterPlayerStyle(
    actions: StyleActions,
    modifier: Modifier,
    preview: Boolean,
) {
    val tones = rememberStyleTones()
    val track = LocalStyleTrack.current
    val r = rememberResponsive()
    val position = rememberLivePosition(animated = !preview)
    val lyrics = rememberStyleLyrics()
    val ring = rememberCoverRingState()
    val live = !preview
    val density = LocalDensity.current
    StyleFrame(
        preview,
        modifier,
        tones,
        background = {
            // the flagship's frosted cover behind the poster
            track.artwork?.let { url -> PlayerBlurredArtworkBackground(thumbnailUrl = url, alpha = 1f, isLyricsVisible = false) }
        },
    ) { landscape, menuOpen, setMenuOpen ->
        val insets = styleInsets(landscape)
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val w = maxWidth
            val h = maxHeight
            // close to square (tablets, open foldables, desktop windows), the circle shrinks to leave the words room
            val tablet = minOf(w, h) >= 600.dp
            if (!landscape) {
                // circle from the top-left: through the right edge at 0.8 W, the left edge at 1.19 W
                val posterW = if (tablet) minOf(w, h * 0.48f) else w
                CircleCover(
                    url = track.artwork,
                    center = Offset(0.13f, 0.06f),
                    radius = 1.14f,
                    unit = posterW,
                    modifier = Modifier.fillMaxWidth().height(posterW * 1.22f),
                )
                val bottom = insets.bottom + 36.dp
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(
                                top = (posterW * 1.24f).coerceAtMost(h * 0.6f),
                                start = 28.dp,
                                end = 22.dp,
                                bottom = bottom,
                            ),
                ) {
                    Column(Modifier.fillMaxSize()) {
                        PosterText(
                            lyrics,
                            position,
                            track,
                            tones,
                            r,
                            baseSize = r.sp(0.12f, 34f, 56f),
                            gap = 12.dp,
                            // the seek bar coming out past the rim moves the words down with it
                            push = { Offset(0f, ring.expand.value * with(density) { RingPush.toPx() }) },
                        )
                    }
                }
                // the seek bar on the cover's rim
                val wPx = with(density) { posterW.toPx() }
                CoverRing(
                    state = ring,
                    circle = RingCircle(Offset(0.13f * wPx, 0.06f * wPx), 1.14f * wPx),
                    live = live,
                    position = position,
                    modifier = Modifier.fillMaxSize(),
                )
                // the menu at the top, opening down, clear of the transport
                StyleMenu(
                    open = menuOpen,
                    onOpenChange = setMenuOpen,
                    tones = tones,
                    actions = actions,
                    atBottom = false,
                    end = 22.dp,
                    edge = insets.top + 16.dp,
                    buttonSize = PosterChevron,
                    pill = true,
                    queue = true,
                )
            } else {
                // circle from the left edge, the words to its right
                val posterH = if (tablet) minOf(h, w * 0.45f) else h
                CircleCover(
                    url = track.artwork,
                    center = Offset(0.04f, 0.30f),
                    radius = 0.953f,
                    unit = posterH,
                    modifier = Modifier.fillMaxHeight().width(posterH * 1.0f),
                )
                Box(
                    modifier =
                        Modifier
                            .fillMaxHeight()
                            .padding(
                                start = maxOf(posterH * 1.08f, w * (if (tablet) 0.48f else 0.5f)),
                                end = 24.dp + insets.end,
                                top = 18.dp + PosterChevron + 18.dp,
                                bottom = 20.dp,
                            ),
                ) {
                    Column(Modifier.fillMaxHeight()) {
                        PosterText(
                            lyrics,
                            position,
                            track,
                            tones,
                            r,
                            baseSize = r.sp(0.095f, 28f, 46f),
                            gap = 8.dp,
                            // sideways the bar comes out to the right, and moves the words that way
                            push = { Offset(ring.expand.value * with(density) { RingPush.toPx() }, 0f) },
                        )
                    }
                }
                val hPx = with(density) { posterH.toPx() }
                CoverRing(
                    state = ring,
                    circle = RingCircle(Offset(0.04f * hPx, 0.30f * hPx), 0.953f * hPx),
                    live = live,
                    position = position,
                    side = RingSide.Right,
                    modifier = Modifier.fillMaxSize(),
                )
                StyleMenu(
                    open = menuOpen,
                    onOpenChange = setMenuOpen,
                    tones = tones,
                    actions = actions,
                    atBottom = false,
                    end = 20.dp + insets.end,
                    edge = 18.dp,
                    buttonSize = PosterChevron,
                    pill = true,
                    queue = true,
                )
            }
        }
    }
}

/**
 * The poster's words and its credits. With lyrics, the lyrics are performed big and the title sits small under
 * them with the artist; without, the title is the big words. When a song's lyrics are found, its big title slides
 * down and shrinks into its small place at the bottom while the lyrics take the space it leaves.
 */
@Composable
private fun ColumnScope.PosterText(
    lines: List<LyricsEntry>,
    position: LivePosition,
    track: StyleTrack,
    tones: StyleTones,
    r: Responsive,
    baseSize: TextUnit,
    gap: Dp,
    // how far the words are pushed aside (px), read as they're drawn
    push: () -> Offset = { Offset.Zero },
) {
    val hasLyrics = lines.isNotEmpty()
    val found = remember { Animatable(if (hasLyrics) 1f else 0f) }
    LaunchedEffect(hasLyrics) {
        found.animateTo(if (hasLyrics) 1f else 0f, tween(820, easing = CubicBezierEasing(0.3f, 0f, 0.1f, 1f)))
    }
    val titleShown by remember { derivedStateOf { found.value < 0.999f } }
    // the column, the big title and the small title: where the slide runs from and to
    val places = remember { arrayOfNulls<LayoutCoordinates>(3) }
    val smallTitle = r.sp(0.05f, 17f, 22f)
    val fontFamily = mochyInterFontFamily()

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .graphicsLayer {
                    val o = push()
                    translationX = o.x
                    translationY = o.y
                }.onPlaced { places[0] = it },
    ) {
        if (hasLyrics) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val f = found.value
                            if (f < 0.999f) alpha = ((f - 0.25f) / 0.75f).coerceIn(0f, 1f)
                        },
            ) {
                ExpressiveLyrics(
                    lines = lines,
                    position = position.withLyricsOffset(),
                    color = tones.ink,
                    baseSize = baseSize,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        if (titleShown) {
            Box(
                modifier =
                    Modifier
                        .onPlaced { places[1] = it }
                        .graphicsLayer {
                            val f = found.value
                            val column = places[0]
                            val from = places[1]
                            val to = places[2]
                            if (f > 0.0005f && column != null && from != null && to != null &&
                                column.isAttached && from.isAttached && to.isAttached
                            ) {
                                val a = column.localPositionOf(from, Offset.Zero)
                                val b = column.localPositionOf(to, Offset.Zero)
                                translationX = (b.x - a.x) * f
                                translationY = (b.y - a.y) * f
                            }
                            val s = 1f + (smallTitle.value / baseSize.value - 1f) * f
                            scaleX = s
                            scaleY = s
                            transformOrigin = TransformOrigin(0f, 0f)
                            // it becomes the small title in the last part of the slide
                            alpha = 1f - ((f - 0.6f) / 0.35f).coerceIn(0f, 1f)
                        },
            ) {
                ExpressiveTitle(text = track.title, baseSize = baseSize, color = tones.ink)
            }
        }
    }
    Spacer(Modifier.height(gap))
    if (track.artist.isNotBlank()) {
        CreditLine(track.artist, color = tones.inkSoft, style = posterType(r.sp(0.034f, 12f, 15f), FontWeight.SemiBold, fontFamily))
    }
    Spacer(Modifier.height(3.dp))
    CreditLine(
        track.title,
        color = tones.inkSoft,
        style = posterType(smallTitle, FontWeight.Bold, fontFamily),
        modifier =
            Modifier
                .onPlaced { places[2] = it }
                .graphicsLayer { alpha = ((found.value - 0.6f) / 0.4f).coerceIn(0f, 1f) },
    )
}

/**
 * One line of the poster's credits. Too long for the line, it scrolls by every few seconds and fades where it's
 * cut, so the whole name gets read. A right-to-left name starts at its right edge and scrolls the other way.
 */
@Composable
private fun CreditLine(
    text: String,
    color: Color,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    val rtl = remember(text) { isRtlText(text) }
    // the marquee lays the text out at its full width, so this says whether it's cut
    var fullWidth by remember { mutableIntStateOf(0) }
    CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
        Text(
            text = text,
            color = color,
            style = style,
            maxLines = 1,
            softWrap = false,
            onTextLayout = { fullWidth = it.size.width },
            modifier =
                modifier
                    .graphicsLayer {
                        // only a cut line needs its own layer for the fade
                        compositingStrategy =
                            if (fullWidth > size.width + 0.5f) CompositingStrategy.Offscreen else CompositingStrategy.Auto
                    }.drawWithCache {
                        val fade =
                            if (rtl) {
                                Brush.horizontalGradient(0f to Color.Transparent, 0.18f to Color.Black)
                            } else {
                                Brush.horizontalGradient(0.82f to Color.Black, 1f to Color.Transparent)
                            }
                        onDrawWithContent {
                            drawContent()
                            if (fullWidth > size.width + 0.5f) drawRect(brush = fade, blendMode = BlendMode.DstIn)
                        }
                    }.basicMarquee(
                        iterations = Int.MAX_VALUE,
                        initialDelayMillis = 2500,
                        repeatDelayMillis = 2500,
                        velocity = 26.dp,
                    ),
        )
    }
}

/**
 * [url] cropped into a circle of [radius] around [center], both in multiples of [unit], with a soft shadow on
 * the tone beneath and the rim shaded so the disc reads as lifted off the page.
 */
@Composable
private fun CircleCover(
    url: String?,
    center: Offset,
    radius: Float,
    unit: Dp,
    modifier: Modifier,
) {
    val density = LocalDensity.current
    val unitPx = with(density) { unit.toPx() }
    val c = Offset(center.x * unitPx, center.y * unitPx)
    val r = radius * unitPx
    val shape = remember(c, r) { GenericShape { _, _ -> addOval(Rect(c.x - r, c.y - r, c.x + r, c.y + r)) } }
    Box(
        modifier =
            modifier.drawBehind {
                drawCircle(
                    brush =
                        Brush.radialGradient(
                            0.9f to Color.Black.copy(alpha = 0.22f),
                            1f to Color.Transparent,
                            center = c + Offset(0f, 10.dp.toPx()),
                            radius = r * 1.06f,
                        ),
                    radius = r * 1.06f,
                    center = c + Offset(0f, 10.dp.toPx()),
                )
            },
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .clip(shape)
                    .background(Color.White.copy(alpha = 0.06f))
                    .drawWithContent {
                        drawContent()
                        drawCircle(
                            brush =
                                Brush.radialGradient(
                                    0.82f to Color.Transparent,
                                    1f to Color.Black.copy(alpha = 0.28f),
                                    center = c,
                                    radius = r,
                                ),
                            radius = r,
                            center = c,
                        )
                    },
        ) {
            if (url != null) {
                AsyncImage(
                    url = url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

// ---- Timeline ----------------------------------------------------------------------------

/**
 * The song as time: the screen split into what's played and what's left, the line between them the playhead,
 * with the elapsed and total times riding either side of it in huge type.
 */
@Composable
private fun TimelinePlayerStyle(
    actions: StyleActions,
    modifier: Modifier,
    preview: Boolean,
) {
    val tones = rememberStyleTones()
    val track = LocalStyleTrack.current
    val position = rememberLivePosition(animated = !preview)
    val r = rememberResponsive()
    val icon = r.dp(0.15f, 50.dp, 72.dp)
    val duration = rememberDurationMs()
    StyleFrame(preview, modifier, tones) { landscape, menuOpen, setMenuOpen ->
        val insets = styleInsets(landscape)
        RidingTimes(
            tones = tones,
            position = position,
            duration = duration,
            landscape = landscape,
            insets = insets,
            topReserve = if (landscape) 0.dp else 20.dp + icon + 14.dp,
        ) {
            // header: the cover as an icon, the title and artist beside it
            Row(
                modifier =
                    Modifier
                        .padding(start = 22.dp + insets.start, top = insets.top + 20.dp, end = 70.dp + insets.end)
                        .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AsyncImage(
                    url = track.artwork,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier =
                        Modifier
                            .size(if (landscape) icon * 0.86f else icon)
                            .clip(RoundedCornerShape(icon * 0.26f))
                            .background(Color.White.copy(alpha = 0.08f)),
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        track.title,
                        color = tones.ink,
                        fontSize = r.sp(0.04f, 14f, 18f),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        track.artist,
                        color = tones.inkSoft,
                        fontSize = r.sp(0.034f, 12f, 16f),
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            StyleControls(
                tones,
                landscape,
                insets,
                menuOpen,
                setMenuOpen,
                actions,
                menuTop = insets.top + 20.dp + (icon - r.dp(0.078f, 28.dp, 40.dp)) / 2,
            )
        }
    }
}

// ---- Lyrics ------------------------------------------------------------------------------

/**
 * The words, performed: the current line set huge and sung through letter by letter, over the same
 * played/remaining split and riding times as the timeline.
 */
@Composable
private fun LyricsPlayerStyle(
    actions: StyleActions,
    modifier: Modifier,
    preview: Boolean,
) {
    val tones = rememberStyleTones()
    val track = LocalStyleTrack.current
    val position = rememberLivePosition(animated = !preview)
    val r = rememberResponsive()
    val duration = rememberDurationMs()
    val lyrics = rememberStyleLyrics()
    StyleFrame(preview, modifier, tones) { landscape, menuOpen, setMenuOpen ->
        val insets = styleInsets(landscape)
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val lyricHeight = if (landscape) maxHeight * 0.52f else maxHeight * 0.3f
            RidingTimes(
                tones = tones,
                position = position,
                duration = duration,
                landscape = landscape,
                insets = insets,
                topReserve = if (landscape) 0.dp else 18.dp + lyricHeight + 8.dp,
            ) {
                Box(
                    modifier =
                        Modifier
                            .padding(start = 24.dp + insets.start, top = insets.top + 18.dp, end = 64.dp + insets.end)
                            .fillMaxWidth()
                            .height(lyricHeight),
                ) {
                    if (lyrics.isEmpty()) {
                        Column {
                            ExpressiveTitle(text = track.title, baseSize = r.sp(0.112f, 32f, 56f), color = tones.ink.copy(alpha = 0.55f))
                            Spacer(Modifier.height(10.dp))
                            Text(
                                stringResource(Res.string.ps_style_no_synced_lyrics),
                                color = tones.inkSoft,
                                fontSize = r.sp(0.034f, 12f, 15f),
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    } else {
                        ExpressiveLyrics(
                            lines = lyrics,
                            position = position.withLyricsOffset(),
                            color = tones.ink,
                            baseSize = if (landscape) r.sp(0.092f, 28f, 46f) else r.sp(0.118f, 34f, 58f),
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                StyleControls(tones, landscape, insets, menuOpen, setMenuOpen, actions, menuTop = insets.top + 20.dp)
            }
        }
    }
}

// ---- shared pieces -----------------------------------------------------------------------

/** the transport circles and the chevron menu where the timeline and lyrics styles keep them */
@Composable
private fun BoxScope.StyleControls(
    tones: StyleTones,
    landscape: Boolean,
    insets: StyleInsets,
    menuOpen: Boolean,
    setMenuOpen: (Boolean) -> Unit,
    actions: StyleActions,
    menuTop: Dp,
) {
    val circle = rememberResponsive().dp(0.078f, 28.dp, 40.dp)
    if (landscape) {
        Box(modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp + insets.end, bottom = 20.dp)) {
            TransportCircles(tones = tones, vertical = true, size = circle)
        }
    } else {
        Box(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = insets.bottom + 36.dp)) {
            TransportCircles(tones = tones, size = circle)
        }
    }
    StyleMenu(
        open = menuOpen,
        onOpenChange = setMenuOpen,
        tones = tones,
        actions = actions,
        atBottom = false,
        end = 22.dp + insets.end,
        edge = if (landscape) 18.dp else menuTop,
        buttonSize = circle,
    )
}

/**
 * The played/remaining split with the two times riding on the playhead: upright, the elapsed time sits just
 * above the line and the total just below it, the line running down the screen as the song plays; sideways, the
 * line runs across and the times sit either side of it along the bottom. [content] draws over the split.
 */
@Composable
private fun RidingTimes(
    tones: StyleTones,
    position: LivePosition,
    duration: Long,
    landscape: Boolean,
    insets: StyleInsets,
    topReserve: Dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val total = styleTimeString(duration)
    val r = rememberResponsive()
    val circle = r.dp(0.078f, 28.dp, 40.dp)
    val timeStyle = posterType(r.sp(0.185f, 56f, 96f), FontWeight.Bold, mochyInterFontFamily())
    val timeWidthPx =
        remember(total, timeStyle) {
            measurer
                .measure(total, timeStyle)
                .size.width
                .toFloat()
        }
    val timeHeightPx =
        remember(timeStyle) {
            measurer
                .measure("0:00", timeStyle)
                .size.height
                .toFloat()
        }
    val fraction = { (position.positionMs().toFloat() / duration).coerceIn(0f, 1f) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val wPx = constraints.maxWidth.toFloat()
        val hPx = constraints.maxHeight.toFloat()
        val (lo, hi) =
            with(density) {
                if (!landscape) {
                    val top = (insets.top + topReserve).toPx() + timeHeightPx
                    val bottom = hPx - (insets.bottom + 36.dp + circle + 18.dp).toPx() - timeHeightPx
                    top to bottom.coerceAtLeast(top)
                } else {
                    val left = (insets.start + 22.dp).toPx() + timeWidthPx + 10.dp.toPx()
                    val right = wPx - (insets.end + 20.dp + circle + 22.dp).toPx() - timeWidthPx - 10.dp.toPx()
                    left to right.coerceAtLeast(left)
                }
            }
        val linePx = { lo + (hi - lo) * fraction() }
        // the frame draws the split itself, under the turn, so it holds still when the style rotates
        ReportSplit(landscape) { linePx() / (if (landscape) wPx else hPx) }
        Box(modifier = Modifier.fillMaxSize()) {
            val gapPx = with(density) { 6.dp.toPx() }
            val startPx = with(density) { (insets.start + 24.dp).toPx() }
            val bottomPx = with(density) { 16.dp.toPx() }
            // the elapsed time: above the line, or left of it
            PlayedTime(
                position = position,
                color = tones.ink,
                style = timeStyle,
                modifier =
                    Modifier.layout { measurable, constraints ->
                        val p = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                        layout(constraints.maxWidth, constraints.maxHeight) {
                            if (!landscape) {
                                p.place(startPx.toInt(), (linePx() - p.height - gapPx * 0.3f).toInt())
                            } else {
                                p.place((linePx() - p.width - gapPx * 1.6f).toInt(), (hPx - bottomPx - p.height).toInt())
                            }
                        }
                    },
            )
            // the total: below the line, or right of it
            Text(
                text = total,
                color = tones.ink.copy(alpha = 0.78f),
                style = timeStyle,
                maxLines = 1,
                softWrap = false,
                modifier =
                    Modifier.layout { measurable, constraints ->
                        val p = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                        layout(constraints.maxWidth, constraints.maxHeight) {
                            if (!landscape) {
                                p.place(startPx.toInt(), (linePx() + gapPx * 0.6f).toInt())
                            } else {
                                p.place((linePx() + gapPx * 1.6f).toInt(), (hPx - bottomPx - p.height).toInt())
                            }
                        }
                    },
            )
            content()
        }
    }
}

/** the played time on its own, so the second ticking over recomposes this text and nothing around it */
@Composable
internal fun PlayedTime(
    position: LivePosition,
    color: Color,
    style: TextStyle,
    modifier: Modifier,
) {
    val seconds = rememberPlayedSeconds(position)
    Text(
        text = styleTimeString(seconds * 1_000L),
        color = color,
        style = style,
        maxLines = 1,
        softWrap = false,
        modifier = modifier,
    )
}
