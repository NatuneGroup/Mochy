// Ported from Mochy Music by realidkroo (github.com/realidkroo/mochy-music). Licensed under GPL-3.0.

// What every one of Mochy's alternate player styles is built from: two tones taken from the cover, the clock
// that moves the playhead every frame, round buttons, the chevron menu, and the stage they're drawn on.

package com.metrolist.shared.ui.player.mochy.styles

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.metrolist.shared.generated.resources.Res
import com.metrolist.shared.generated.resources.lyrics
import com.metrolist.shared.generated.resources.menu_edit_player
import com.metrolist.shared.generated.resources.mochy_expand_less
import com.metrolist.shared.generated.resources.mochy_set_lyrics
import com.metrolist.shared.generated.resources.mochy_set_more_horiz
import com.metrolist.shared.generated.resources.mochy_set_queue_music
import com.metrolist.shared.generated.resources.mochy_set_repeat
import com.metrolist.shared.generated.resources.mochy_set_repeat_one
import com.metrolist.shared.generated.resources.mochy_set_shuffle
import com.metrolist.shared.generated.resources.mochy_set_tune
import com.metrolist.shared.generated.resources.ps_action_other_menu
import com.metrolist.shared.generated.resources.ps_style_repeat_one
import com.metrolist.shared.generated.resources.queue
import com.metrolist.shared.generated.resources.repeat
import com.metrolist.shared.generated.resources.shuffle
import com.metrolist.shared.playback.LocalPlayerSession
import com.metrolist.shared.playback.PlayerSession
import com.metrolist.shared.ui.LocalIsTv
import com.metrolist.shared.ui.mochy.lyrics.PlaybackClock
import com.metrolist.shared.ui.player.LocalPlayerContentActive
import com.metrolist.shared.ui.player.mochy.AnimatedPlayGlyph
import com.metrolist.shared.ui.player.mochy.AnimatedSkipGlyph
import com.metrolist.shared.ui.player.mochy.SkipCycleState
import com.metrolist.shared.ui.theme.mochyInterFontFamily
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Whether a style takes touches: only on the open player. Collapsed into the mini pill the style is still
 * composed under it, and its gestures would swallow the swipe and tap that open the player. Asked when a touch
 * lands, never in composition.
 */
val LocalStyleInteractive = compositionLocalOf<() -> Boolean> { { true } }

/** The cover's colour the styles take their tones from; the player provides it from the artwork. */
val LocalStyleSeedColor = compositionLocalOf { Color(0xFF4A4A4A) }

/** The song's own lyrics offset (ms), so lyrics can run early or late without moving the rest. */
val LocalStyleLyricsOffset = compositionLocalOf<() -> Long> { { 0L } }

/**
 * Runs [detect] only while the style takes touches. The rest of the time nothing here listens, so a touch
 * goes on to the player. A flow starts and stops it, so the player opening or closing recomposes nothing.
 */
internal suspend fun PointerInputScope.whileStyleInteractive(
    interactive: () -> Boolean,
    detect: suspend PointerInputScope.() -> Unit,
): Unit =
    coroutineScope {
        var listening: Job? = null
        // started in place: a hop to the next dispatch let the touch that wakes this handler go by unheard
        snapshotFlow { interactive() }.collect { on ->
            listening?.cancelAndJoin()
            listening = if (on) launch(start = CoroutineStart.UNDISPATCHED) { this@whileStyleInteractive.detect() } else null
        }
    }

/**
 * The two tones every alternate style is built from, taken from the cover: [played] is the part of the song
 * that has played, [remaining] the darker part still to come, and the line between them is the playhead. Both
 * are the cover's hue held quiet, so big flat areas of it stay easy on the eye while still belonging to the song.
 */
@Immutable
data class StyleTones(
    val played: Color,
    val remaining: Color,
    val ink: Color,
    val inkSoft: Color,
    val chip: Color,
)

private val Ink = Color(0xFFF1ECEC)

@Composable
fun rememberStyleTones(): StyleTones {
    val seed = LocalStyleSeedColor.current
    return remember(seed) {
        val (hue, sat, _) = seed.toHsl()
        val saturation = (sat * 0.42f).coerceIn(0.06f, 0.24f)
        StyleTones(
            played = Color.hsl(hue, saturation, 0.45f),
            remaining = Color.hsl(hue, saturation, 0.30f),
            ink = Ink,
            inkSoft = Ink.copy(alpha = 0.62f),
            chip = Color.White.copy(alpha = 0.22f),
        )
    }
}

// hue in degrees, saturation and lightness 0..1, as Android's ColorUtils.colorToHSL gives them
private fun Color.toHsl(): Triple<Float, Float, Float> {
    val r = red
    val g = green
    val b = blue
    val hi = max(r, max(g, b))
    val lo = min(r, min(g, b))
    val delta = hi - lo
    val l = (hi + lo) / 2f
    if (delta == 0f) return Triple(0f, 0f, l)
    val s = delta / (1f - abs(2f * l - 1f))
    val h =
        when (hi) {
            r -> ((g - b) / delta).mod(6f)
            g -> (b - r) / delta + 2f
            else -> (r - g) / delta + 4f
        } * 60f
    return Triple(h.mod(360f), s.coerceIn(0f, 1f), l)
}

/**
 * Soft light over a flat tone: brighter toward [light], a little darker at the far edge, so the large colour
 * fields read as lit surfaces rather than paint.
 */
fun Modifier.shadedTone(
    color: Color,
    light: Offset = Offset(0.15f, 0.05f),
): Modifier =
    drawBehind {
        drawRect(color)
        drawRect(
            Brush.radialGradient(
                colors = listOf(Color.White.copy(alpha = 0.10f), Color.Transparent),
                center = Offset(size.width * light.x, size.height * light.y),
                radius = size.maxDimension * 0.9f,
            ),
        )
        drawRect(Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.16f)))
    }

/**
 * The playback position on every frame, for the parts of a style that move with it. Read it inside draw,
 * layout or graphicsLayer blocks only: those re-run each frame without recomposing anything.
 */
@Stable
class LivePosition internal constructor(
    private val read: () -> Long,
    private val tick: () -> Long,
) {
    private var readAt = Long.MIN_VALUE
    private var value = 0L

    /** ms into the song, read from the player once per tick, so every part drawn in one frame moves together */
    fun positionMs(): Long {
        val now = tick()
        if (now != readAt) {
            readAt = now
            value = read()
        }
        return value
    }

    /** The same clock moved by [shiftMs], so lyrics can run early or late without touching the rest. */
    fun shifted(shiftMs: () -> Long) = LivePosition(read = { read() + shiftMs() }, tick = tick)
}

/** The playback clock the lyrics follow: [this] plus the song's own lyrics offset. */
@Composable
fun LivePosition.withLyricsOffset(): LivePosition {
    val offset = LocalStyleLyricsOffset.current
    return remember(this, offset) { shifted(offset) }
}

/**
 * [animated] false (a selector card's preview): it follows the player's own updates, not every frame.
 * Collapsed into the mini player the style can't be seen, so the clock waits for it to open again.
 */
@Composable
fun rememberLivePosition(animated: Boolean = true): LivePosition {
    val session = LocalPlayerSession.current
    val tick = remember { mutableLongStateOf(0L) }
    val showing = rememberStyleShowing()
    LaunchedEffect(session, animated, showing) {
        if (session == null) return@LaunchedEffect
        combine(session.isPlaying, session.currentPosition, snapshotFlow { showing() }) { playing, _, shown -> playing to shown }
            .collectLatest { (playing, shown) ->
                if (!shown) return@collectLatest
                // a seek or a pause still moves the boundary once; playing, it moves every frame
                tick.longValue++
                while (playing && animated) withFrameMillis { tick.longValue = it }
            }
    }
    return remember(session) {
        // the player's own position only moves every so often, which judders at 120 hz; this one moves on the frame's time
        val clock =
            if (session == null) {
                { 0L }
            } else {
                PlaybackClock(
                    readPositionMs = session::currentPositionMs,
                    readIsPlaying = { session.isPlaying.value },
                    readSpeed = { session.playbackSpeedFlow.value },
                )
            }
        LivePosition(read = clock, tick = { tick.longValue })
    }
}

/** when a layout can be seen, for one that isn't the open player */
val LocalStyleShowing = staticCompositionLocalOf<(() -> Boolean)?> { null }

/** whether the style can be seen: it stays composed in the mini player with its clocks held still */
@Composable
internal fun rememberStyleShowing(): () -> Boolean {
    LocalStyleShowing.current?.let { return it }
    val active by rememberUpdatedState(LocalPlayerContentActive.current)
    return remember { { active } }
}

/** the song's length in ms, never 0 */
@Composable
fun rememberDurationMs(): Long {
    val session = LocalPlayerSession.current ?: return 1L
    val duration by session.duration.collectAsState()
    return duration.takeIf { it > 0L } ?: 1L
}

/** the whole seconds played, recomposing once a second, for the big timestamp */
@Composable
fun rememberPlayedSeconds(position: LivePosition): Long {
    val seconds by remember(position) { derivedStateOf { position.positionMs() / 1_000L } }
    return seconds
}

/** "m:ss", or "h:mm:ss" past an hour, as the player writes times */
internal fun styleTimeString(ms: Long): String {
    val total = (ms / 1000L).coerceAtLeast(0L)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    val ss = s.toString().padStart(2, '0')
    return if (h > 0) "$h:${m.toString().padStart(2, '0')}:$ss" else "$m:$ss"
}

/** a small round button: the alternate styles' only controls */
@Composable
fun StyleCircle(
    tones: StyleTones,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: DrawableResource? = null,
    size: Dp = 30.dp,
    enabled: Boolean = true,
    // dressed as the Turntable's pills: a faint white fill, a white icon
    pill: Boolean = false,
    // a toggle that's on (shuffle, repeat): a brighter fill
    active: Boolean = false,
    // drawn in place of the icon, in the icon's colour: the transport's animated glyphs
    glyph: (@Composable (Color) -> Unit)? = null,
) {
    val press = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    val interactive = LocalStyleInteractive.current
    val currentOnClick by rememberUpdatedState(onClick)
    Box(
        modifier =
            modifier
                .graphicsLayer {
                    scaleX = press.value
                    scaleY = press.value
                    alpha = if (enabled) 1f else 0.4f
                }.size(size)
                .clip(CircleShape)
                .background(
                    when {
                        active -> ActiveFill
                        pill -> PillFill
                        else -> tones.chip
                    },
                ).pointerInput(enabled, interactive) {
                    if (!enabled) return@pointerInput
                    whileStyleInteractive(interactive) {
                        detectTapGestures(
                            onPress = {
                                scope.launch { press.animateTo(0.86f, spring(dampingRatio = 0.6f, stiffness = 900f)) }
                                tryAwaitRelease()
                                scope.launch { press.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 500f)) }
                            },
                            onTap = { currentOnClick() },
                        )
                    }
                },
        contentAlignment = Alignment.Center,
    ) {
        val tint =
            when {
                active -> Color.White
                pill -> Color.White.copy(alpha = 0.9f)
                else -> tones.ink
            }
        if (glyph != null) {
            glyph(tint)
        } else if (icon != null) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(if (pill) 20.dp else size * 0.5f),
            )
        }
    }
}

// how dark the screen goes behind a chevron menu
internal const val CHEVRON_MENU_DIM = 0.72f

private val PillFill = Color.White.copy(alpha = 0.08f)
private val ActiveFill = Color.White.copy(alpha = 0.32f)

/** one button in a style's chevron menu, with its name beside it */
private class MenuItem(
    val label: String,
    val icon: DrawableResource,
    val active: Boolean = false,
    val onClick: () -> Unit,
)

// the glyphs' size in a circle: the play triangle about as tall as the old icon, the skip pair a little wider
private const val CIRCLE_PLAY_HEIGHT = 0.48f
private const val CIRCLE_SKIP_WIDTH = 0.46f

/** previous, play/pause and next as three [StyleCircle]s */
@Composable
fun TransportCircles(
    tones: StyleTones,
    size: Dp = 30.dp,
    vertical: Boolean = false,
    spacing: Dp = 8.dp,
) {
    // a tv's remote plays and skips, buttons on the screen are only more things for focus to land on
    if (LocalIsTv.current) return
    val session = LocalPlayerSession.current ?: return
    val playing by session.isPlaying.collectAsState()
    val scope = rememberCoroutineScope()
    val previous = remember { SkipCycleState() }
    val next = remember { SkipCycleState() }
    val content: @Composable () -> Unit = {
        StyleCircle(
            tones = tones,
            size = size,
            onClick = {
                scope.launch { previous.step() }
                session.skipToPrevious()
            },
            glyph = { tint -> AnimatedSkipGlyph(cycle = previous, backwards = true, color = tint, width = size * CIRCLE_SKIP_WIDTH) },
        )
        StyleCircle(
            tones = tones,
            size = size,
            glyph = { tint -> AnimatedPlayGlyph(playing = playing, color = tint, height = size * CIRCLE_PLAY_HEIGHT) },
            onClick = { session.togglePlayPause() },
        )
        StyleCircle(
            tones = tones,
            size = size,
            onClick = {
                scope.launch { next.step() }
                session.skipToNext()
            },
            glyph = { tint -> AnimatedSkipGlyph(cycle = next, backwards = false, color = tint, width = size * CIRCLE_SKIP_WIDTH) },
        )
    }
    if (vertical) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing)) { content() }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(spacing)) { content() }
    }
}

/** what a style's chevron menu can do, wired by the player */
@Immutable
class StyleActions(
    val onOtherMenu: () -> Unit,
    val onEditPlayer: () -> Unit,
    // opens the song's whole lyrics over the style; no item without it (the selector's cards)
    val onLyrics: (() -> Unit)? = null,
    // opens the queue over the style, for the styles whose menu offers it (the poster)
    val onQueue: (() -> Unit)? = null,
)

/**
 * Double tap plays or pauses; a sideways swipe goes on to the next song or back to the last when it's let go.
 * The alternate styles have no big transport buttons, so the whole screen is the transport.
 */
fun Modifier.styleGestures(
    enabled: Boolean,
    interactive: () -> Boolean,
    onToggle: () -> Unit,
    session: () -> PlayerSession?,
): Modifier =
    if (!enabled) {
        this
    } else {
        this
            .pointerInput(enabled, interactive) {
                whileStyleInteractive(interactive) { detectTapGestures(onDoubleTap = { onToggle() }) }
            }.styleSongSwipe(interactive, session)
    }

// the sideways swipe alone, for a style that wants its own taps
fun Modifier.styleSongSwipe(
    interactive: () -> Boolean,
    session: () -> PlayerSession?,
): Modifier =
    pointerInput(interactive) {
        val tracker = VelocityTracker()
        val flingPx = SwipeFling.toPx()
        var travelled = 0f
        whileStyleInteractive(interactive) {
            detectHorizontalDragGestures(
                onDragStart = {
                    travelled = 0f
                    tracker.resetTracking()
                },
                onDragEnd = {
                    val velocity = tracker.calculateVelocity().x
                    // a third of the way across, or a flick, changes the song
                    val far = abs(travelled) > size.width / 3f || abs(velocity) > flingPx
                    if (far) {
                        if (travelled < 0f) session()?.skipToNext() else session()?.skipToPrevious()
                    }
                },
                onHorizontalDrag = { change, amount ->
                    tracker.addPointerInputChange(change)
                    travelled += amount
                },
            )
        }
    }

/** A flick faster than this skips however short it was. */
private val SwipeFling = 500.dp

/** the words beside a chevron menu's round buttons, on every player style */
@Composable
internal fun chevronMenuLabelStyle(
    color: Color,
    fontSize: TextUnit,
): TextStyle {
    val density = LocalDensity.current
    val fontFamily = mochyInterFontFamily()
    return remember(color, fontSize, density, fontFamily) {
        // a very soft shade, dark under light words and light under dark ones, so it lifts them without a hard edge
        val shade = if (color.luminance() > 0.5f) Color.Black else Color.White
        TextStyle(
            color = color,
            fontFamily = fontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = fontSize,
            letterSpacing = (-0.035).em,
            shadow = with(density) { Shadow(shade.copy(alpha = 0.35f), Offset(0f, 1.dp.toPx()), 8.dp.toPx()) },
        )
    }
}

// the chevron and its little menu (lyrics, shuffle, repeat, other menu, edit player), opening away from the edge it sits on
@Composable
fun BoxScope.StyleMenu(
    open: Boolean,
    onOpenChange: (Boolean) -> Unit,
    tones: StyleTones,
    actions: StyleActions,
    atBottom: Boolean,
    end: Dp,
    edge: Dp,
    buttonSize: Dp = 30.dp,
    // the button and the menu's circles dressed as the Turntable's pills
    pill: Boolean = false,
    // a queue row under lyrics, opening the queue page over the style
    queue: Boolean = false,
) {
    val corner = if (atBottom) Alignment.BottomEnd else Alignment.TopEnd
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(open) {
        reveal.animateTo(if (open) 1f else 0f, spring(dampingRatio = 0.72f, stiffness = 420f))
    }
    val menuVisible by remember(reveal) { derivedStateOf { reveal.value > 0.001f } }
    if (menuVisible) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .drawBehind { drawRect(Color.Black.copy(alpha = CHEVRON_MENU_DIM * reveal.value.coerceIn(0f, 1f))) }
                    .pointerInput(Unit) { detectTapGestures { onOpenChange(false) } },
        )
        val session = LocalPlayerSession.current
        val shuffle = session?.shuffleModeEnabled?.collectAsState()?.value == true
        val repeatMode = session?.repeatMode?.collectAsState()?.value ?: REPEAT_OFF
        // the styles have no shuffle or repeat buttons of their own, so each is a row here, staying open when tapped
        val shuffleAndRepeat =
            if (session != null) {
                listOf(
                    MenuItem(stringResource(Res.string.shuffle), Res.drawable.mochy_set_shuffle, active = shuffle) {
                        session.toggleShuffle()
                    },
                    MenuItem(
                        label = stringResource(if (repeatMode == REPEAT_ONE) Res.string.ps_style_repeat_one else Res.string.repeat),
                        icon = if (repeatMode == REPEAT_ONE) Res.drawable.mochy_set_repeat_one else Res.drawable.mochy_set_repeat,
                        active = repeatMode != REPEAT_OFF,
                    ) { session.cycleRepeatMode() },
                )
            } else {
                emptyList()
            }
        val lyricsLabel = stringResource(Res.string.lyrics)
        val queueLabel = stringResource(Res.string.queue)
        val otherMenuLabel = stringResource(Res.string.ps_action_other_menu)
        val editPlayerLabel = stringResource(Res.string.menu_edit_player)
        val rows =
            listOfNotNull(
                actions.onLyrics?.let { openLyrics ->
                    MenuItem(lyricsLabel, Res.drawable.mochy_set_lyrics) {
                        onOpenChange(false)
                        openLyrics()
                    }
                },
                actions.onQueue?.takeIf { queue }?.let { openQueue ->
                    MenuItem(queueLabel, Res.drawable.mochy_set_queue_music) {
                        onOpenChange(false)
                        openQueue()
                    }
                },
            ) + shuffleAndRepeat +
                listOf(
                    MenuItem(otherMenuLabel, Res.drawable.mochy_set_more_horiz) {
                        onOpenChange(false)
                        actions.onOtherMenu()
                    },
                    MenuItem(editPlayerLabel, Res.drawable.mochy_set_tune) {
                        onOpenChange(false)
                        actions.onEditPlayer()
                    },
                )
        val itemGap = 10.dp
        val windowHeight =
            with(LocalDensity.current) {
                LocalWindowInfo.current.containerSize.height
                    .toDp()
            }
        val roomForRows = windowHeight - edge - buttonSize - itemGap
        Column(
            modifier =
                Modifier
                    .align(corner)
                    .padding(
                        end = end - 3.dp,
                        top = if (atBottom) 0.dp else edge + buttonSize + itemGap,
                        bottom = if (atBottom) edge + buttonSize + itemGap else 0.dp,
                    )
                    // only a list too tall for the screen scrolls, from the button's end
                    .then(
                        if ((buttonSize + 6.dp + itemGap) * rows.size > roomForRows) {
                            Modifier.verticalScroll(rememberScrollState(), reverseScrolling = atBottom)
                        } else {
                            Modifier
                        },
                    ),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(itemGap),
        ) {
            rows.forEachIndexed { index, item ->
                // nearest the button first
                val order = if (atBottom) rows.size - 1 - index else index
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier =
                        Modifier.graphicsLayer {
                            val delay = order * 0.12f
                            val p = ((reveal.value - delay) / (1f - delay)).coerceIn(0f, 1.2f)
                            alpha = p.coerceIn(0f, 1f)
                            val away = if (atBottom) 1f else -1f
                            translationY = (1f - p) * 28.dp.toPx() * away
                            val s = 0.7f + 0.3f * p
                            scaleX = s
                            scaleY = s
                            transformOrigin = TransformOrigin(1f, 0.5f)
                        },
                ) {
                    Text(text = item.label, style = chevronMenuLabelStyle(if (pill) Color.White else tones.ink, 13.sp))
                    StyleCircle(
                        icon = item.icon,
                        tones = tones,
                        size = buttonSize + 6.dp,
                        onClick = item.onClick,
                        pill = pill,
                        active = item.active,
                    )
                }
            }
        }
    }
    Box(
        modifier =
            Modifier
                .align(corner)
                .padding(end = end, top = if (atBottom) 0.dp else edge, bottom = if (atBottom) edge else 0.dp),
    ) {
        MenuChevron(open, tones, buttonSize, pill, opensDown = !atBottom, onOpenChange)
    }
}

private const val REPEAT_OFF = 0
private const val REPEAT_ONE = 1

/** The menu's button: a chevron pointing the way the menu opens, flipping round once it's open. */
@Composable
internal fun MenuChevron(
    open: Boolean,
    tones: StyleTones,
    size: Dp,
    pill: Boolean,
    opensDown: Boolean,
    onOpenChange: (Boolean) -> Unit,
) {
    val turn = remember { Animatable(if (open) 1f else 0f) }
    LaunchedEffect(open) { turn.animateTo(if (open) 1f else 0f, spring(dampingRatio = 0.55f, stiffness = 380f)) }
    StyleCircle(
        icon = Res.drawable.mochy_expand_less,
        tones = tones,
        size = size,
        pill = pill,
        onClick = { onOpenChange(!open) },
        // up-pointing art: turned to point down at rest when the menu drops below it
        modifier = Modifier.graphicsLayer { rotationZ = (if (opensDown) 180f else 0f) + turn.value * 180f },
    )
}

// where each orientation of a style puts its playhead split, as a share of the screen
@Stable
class StyleSplits {
    var upright by mutableStateOf<(() -> Float)?>(null)
    var sideways by mutableStateOf<(() -> Float)?>(null)

    operator fun get(landscape: Boolean) = if (landscape) sideways else upright

    operator fun set(
        landscape: Boolean,
        value: (() -> Float)?,
    ) {
        if (landscape) sideways = value else upright = value
    }
}

internal val LocalStyleSplits = compositionLocalOf<StyleSplits?> { null }

/** a layout reporting where its split is, for the frame to draw it */
@Composable
internal fun ReportSplit(
    landscape: Boolean,
    boundary: () -> Float,
) {
    val splits = LocalStyleSplits.current ?: return
    val current by rememberUpdatedState(boundary)
    DisposableEffect(splits, landscape) {
        val read = { current() }
        splits[landscape] = read
        onDispose { if (splits[landscape] === read) splits[landscape] = null }
    }
}

/** the played/remaining split in two given colours, top to bottom when [vertical], left to right otherwise */
internal fun DrawScope.drawSplit(
    played: Color,
    remaining: Color,
    vertical: Boolean,
    boundary: Float,
) {
    val b = boundary.coerceIn(0f, 1f)
    if (vertical) {
        val y = size.height * b
        drawRect(played, size = size.copy(height = y))
        drawRect(remaining, topLeft = Offset(0f, y), size = size.copy(height = size.height - y))
        // light falls on the played part from the top, the line itself catches a highlight
        drawRect(
            Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.08f), Color.Transparent), endY = y.coerceAtLeast(1f)),
            size = size.copy(height = y),
        )
        drawRect(
            Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.18f), Color.Transparent), startY = y, endY = y + size.height * 0.18f),
            topLeft = Offset(0f, y),
            size = size.copy(height = size.height * 0.18f),
        )
        drawRect(Color.White.copy(alpha = 0.10f), topLeft = Offset(0f, y - 1f), size = size.copy(height = 1.5f))
    } else {
        val x = size.width * b
        drawRect(played, size = size.copy(width = x))
        drawRect(remaining, topLeft = Offset(x, 0f), size = size.copy(width = size.width - x))
        drawRect(
            Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.07f), Color.Transparent), endX = x.coerceAtLeast(1f)),
            size = size.copy(width = x),
        )
        drawRect(
            Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.18f), Color.Transparent), startX = x, endX = x + size.width * 0.14f),
            topLeft = Offset(x, 0f),
            size = size.copy(width = size.width * 0.14f),
        )
        drawRect(Color.White.copy(alpha = 0.10f), topLeft = Offset(x - 1f, 0f), size = size.copy(width = 1.5f))
    }
}

/** how long a style's background takes to fade to a new song's colours */
private const val TONE_FADE_MS = 700

// the style's background (its own tone, or the playhead split its layout reports) under its layout
@Composable
fun StyleStage(
    tones: StyleTones,
    modifier: Modifier = Modifier,
    background: (@Composable BoxScope.() -> Unit)? = null,
    content: @Composable BoxScope.(landscape: Boolean) -> Unit,
) {
    val splits = remember { StyleSplits() }
    // a new song's tones fade in rather than snap, read only while drawing so the fade doesn't recompose the style
    val played = animateColorAsState(tones.played, tween(TONE_FADE_MS), label = "playedTone")
    val remaining = animateColorAsState(tones.remaining, tween(TONE_FADE_MS), label = "remainingTone")
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val landscape = maxWidth > maxHeight
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .then(
                        if (background != null) {
                            Modifier
                        } else {
                            Modifier.drawBehind {
                                val split = splits[landscape]
                                if (split == null) {
                                    drawRect(played.value)
                                } else {
                                    drawSplit(played.value, remaining.value, vertical = !landscape, boundary = split())
                                }
                            }
                        },
                    ),
        ) {
            if (background != null) Box(modifier = Modifier.fillMaxSize(), content = background)
            CompositionLocalProvider(LocalStyleSplits provides splits) {
                Box(modifier = Modifier.fillMaxSize()) { content(landscape) }
            }
        }
    }
}

/** Sizes that follow the screen: a share of its short side, held between sensible bounds. */
@Immutable
class Responsive(
    val shortSideDp: Float,
) {
    fun dp(
        fraction: Float,
        min: Dp,
        max: Dp,
    ): Dp = (shortSideDp * fraction).dp.coerceIn(min, max)

    fun sp(
        fraction: Float,
        min: Float,
        max: Float,
    ): TextUnit = (shortSideDp * fraction).coerceIn(min, max).sp
}

@Composable
fun rememberResponsive(): Responsive {
    val size = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current
    val short = with(density) { min(size.width, size.height).toDp().value }
    return remember(short) { Responsive(short) }
}

/** a colour between two tones, for the bits that cross the playhead */
fun toneBetween(
    a: Color,
    b: Color,
    t: Float,
): Color = lerp(a, b, t.coerceIn(0f, 1f))
