// Ported and adapted from Mochy Music by realidkroo (github.com/realidkroo/mochy-music). Licensed under GPL-3.0.

// Mochy's player style picker: full screen black, the styles as cards in a row, the chosen card lifted and the others pulled back.
// The previews are drawn here and animate like the player they stand for; they are not screenshots of the real players.

package com.metrolist.shared.ui.player.mochy

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.metrolist.shared.generated.resources.Res
import com.metrolist.shared.generated.resources.mochy_menu_player_style
import com.metrolist.shared.generated.resources.mochy_menu_style_classic
import com.metrolist.shared.generated.resources.mochy_menu_style_flagship
import com.metrolist.shared.generated.resources.mochy_menu_style_ipod
import com.metrolist.shared.generated.resources.mochy_menu_style_modern
import com.metrolist.shared.generated.resources.mochy_menu_style_turntable
import com.metrolist.shared.generated.resources.mochy_menu_style_vintage
import com.metrolist.shared.generated.resources.player_type_lyrics_stage
import com.metrolist.shared.generated.resources.player_type_poster
import com.metrolist.shared.generated.resources.player_type_timeline
import com.metrolist.shared.playback.LocalPlayerSession
import com.metrolist.shared.settings.PlayerType
import com.metrolist.shared.settings.Settings
import com.metrolist.shared.ui.components.PopupVisibility
import com.metrolist.shared.ui.icons.MetrolistIcons
import com.metrolist.shared.ui.player.mochy.styles.AlternatePlayerStyle
import com.metrolist.shared.ui.player.mochy.styles.AlternateStyle
import com.metrolist.shared.ui.player.mochy.styles.LocalStyleTrack
import com.metrolist.shared.ui.player.mochy.styles.StyleActions
import com.metrolist.shared.ui.player.mochy.styles.StyleTrack
import com.metrolist.shared.ui.theme.mochyInterFontFamily
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

// the styles KMP has, stacked the way the reference's picker stacks its own: each column is one page, and the styles in it
// sit one behind another with the chosen one in front
internal val MochyStyleColumns: List<List<Pair<PlayerType, StringResource>>> =
    listOf(
        listOf(
            PlayerType.MOCHY to Res.string.mochy_menu_style_flagship,
            PlayerType.TURNTABLE to Res.string.mochy_menu_style_turntable,
        ),
        listOf(PlayerType.IPOD to Res.string.mochy_menu_style_ipod),
        listOf(PlayerType.POSTER to Res.string.player_type_poster),
        // the lyrics stage sits under the timeline
        listOf(
            PlayerType.TIMELINE to Res.string.player_type_timeline,
            PlayerType.LYRICS_STAGE to Res.string.player_type_lyrics_stage,
        ),
        listOf(
            PlayerType.MODERN to Res.string.mochy_menu_style_modern,
            PlayerType.VINTAGE to Res.string.mochy_menu_style_vintage,
            PlayerType.CLASSIC to Res.string.mochy_menu_style_classic,
        ),
    )

/** The styles only Mochy's look draws; a TV, where they don't fit, falls back to the plain player. */
internal val MochyOnlyPlayerTypes =
    setOf(
        PlayerType.MOCHY,
        PlayerType.TURNTABLE,
        PlayerType.IPOD,
        PlayerType.POSTER,
        PlayerType.TIMELINE,
        PlayerType.LYRICS_STAGE,
    )

private val CardWidth = 170.dp
private val CardCorner = 28.dp
private val PreviewCorner = 20.dp
private val CardFill = Color(0xFF1C1C1E)
private val StackStep = 46.dp
private val StackHeight = 440.dp

// the picker as a page: a black screen with its title, the columns side by side one page at a time
// and a tap on a card picks that style
@Composable
fun MochyStyleSelector(
    selected: PlayerType,
    onSelect: (PlayerType) -> Unit,
    onDismiss: () -> Unit,
) {
    val font = mochyInterFontFamily()
    val startColumn = MochyStyleColumns.indexOfFirst { column -> column.any { it.first == selected } }.coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = startColumn) { MochyStyleColumns.size }
    Column(
        modifier =
            Modifier.fillMaxSize().background(Color.Black).padding(
                top =
                    WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 18.dp,
            ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.mochy_menu_player_style),
                fontFamily = font,
                fontWeight = FontWeight.Bold,
                fontSize = 34.sp,
                lineHeight = 40.sp,
                color = Color.White,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier =
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(CardFill)
                        .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    MetrolistIcons.Default.Close,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth(),
            beyondViewportPageCount = 1,
        ) { page ->
            StyleColumn(
                styles = MochyStyleColumns[page],
                selected = selected,
                onClick = { style ->
                    onSelect(style)
                    onDismiss()
                },
            )
        }
    }
}

// one column of the grid: its styles stacked in the middle of the page, the chosen one in front
@Composable
private fun StyleColumn(
    styles: List<Pair<PlayerType, StringResource>>,
    selected: PlayerType,
    onClick: (PlayerType) -> Unit,
) {
    val front = styles.indexOfFirst { it.first == selected }.takeIf { it >= 0 } ?: 0
    Box(
        modifier = Modifier.fillMaxWidth().height(StackHeight),
        contentAlignment = Alignment.TopCenter,
    ) {
        // the one behind goes first, so the front card draws on top
        styles.indices.sortedBy { -abs(it - front) }.forEach { index ->
            val (style, title) = styles[index]
            StyleCard(
                style = style,
                title = stringResource(title),
                front = index == front,
                depth = index - front,
                onClick = { onClick(style) },
            )
        }
    }
}

// a card in its column: the front card stands full size, the cards behind it step down and sit back
@Composable
private fun StyleCard(
    style: PlayerType,
    title: String,
    front: Boolean,
    depth: Int,
    onClick: () -> Unit,
) {
    val step by animateDpAsState(
        targetValue = if (front) 0.dp else StackStep * depth,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 380f),
        label = "mochyStyleStack",
    )
    val cardScale by animateFloatAsState(
        targetValue = if (front) 1f else 0.9f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 380f),
        label = "mochyStyleScale",
    )
    val cardAlpha by animateFloatAsState(
        targetValue = if (front) 1f else 0.55f,
        animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
        label = "mochyStyleAlpha",
    )
    Column(
        modifier =
            Modifier
                .width(CardWidth)
                .zIndex(if (front) 1f else 0f)
                .offset(y = step)
                .graphicsLayer {
                    scaleX = cardScale
                    scaleY = cardScale
                    alpha = cardAlpha
                }.clip(RoundedCornerShape(CardCorner))
                .background(CardFill)
                .clickable(onClick = onClick)
                .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        StylePreview(style, Modifier.fillMaxWidth().aspectRatio(0.56f).clip(RoundedCornerShape(PreviewCorner)))
        Text(
            text = title,
            fontFamily = mochyInterFontFamily(),
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

// a looping drawing of each player: the disc turns, the wheel pulses, the bars move, so a card shows its style at rest
@Composable
private fun StylePreview(
    style: PlayerType,
    modifier: Modifier,
) {
    val alternate =
        when (style) {
            PlayerType.POSTER -> AlternateStyle.POSTER
            PlayerType.TIMELINE -> AlternateStyle.TIMELINE
            PlayerType.LYRICS_STAGE -> AlternateStyle.LYRICS
            else -> null
        }
    if (alternate != null) {
        // the style itself, as the player draws it, laid out at the window's size and shrunk onto the card
        LiveStylePreview(alternate, modifier)
        return
    }
    val loop = rememberInfiniteTransition(label = "mochyStylePreview")
    val phase by loop.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 6000, easing = LinearEasing), RepeatMode.Restart),
        label = "mochyStylePhase",
    )
    Canvas(modifier.background(Color(0xFF0E0E10))) {
        val w = size.width
        val h = size.height
        when (style) {
            PlayerType.TURNTABLE -> {
                // the record turns under its label, the arm rests on the edge
                val r = w * 0.36f
                val c = Offset(w / 2f, h * 0.42f)
                drawCircle(Color(0xFF151515), r, c)
                drawCircle(Color(0xFF2A2A2A), r * 0.7f, c)
                rotateGroove(c, r, phase)
                drawCircle(Color(0xFFE8A33D), r * 0.28f, c)
                drawLine(Color.White.copy(alpha = 0.7f), Offset(w * 0.82f, h * 0.08f), Offset(w * 0.6f, h * 0.36f), strokeWidth = 3f)
            }

            PlayerType.IPOD -> {
                // the screen on top, the click wheel below pulsing with the phase
                drawRoundRect(Color(0xFF2C2C30), Offset(w * 0.1f, h * 0.06f), Size(w * 0.8f, h * 0.42f), CornerRadius(8f))
                drawRoundRect(Color(0xFF9CC6A4), Offset(w * 0.16f, h * 0.12f), Size(w * 0.68f, h * 0.30f), CornerRadius(4f))
                val wheelCenter = Offset(w / 2f, h * 0.76f)
                drawCircle(Color(0xFFF2F2F2), w * 0.36f, wheelCenter)
                drawCircle(Color(0xFF222222), w * 0.14f, wheelCenter)
                drawCircle(Color(0xFF4FA3FF).copy(alpha = 0.25f + 0.2f * pulse(phase)), w * 0.33f, wheelCenter, style = Stroke(width = 4f))
            }

            PlayerType.VINTAGE -> {
                // warm tones with scan lines rolling down
                drawRect(Brush.verticalGradient(listOf(Color(0xFF6B3E1E), Color(0xFF2B1A10))))
                val gap = h / 14f
                val shift = (phase * gap)
                var y = -gap + shift
                while (y < h) {
                    drawRect(Color.Black.copy(alpha = 0.18f), Offset(0f, y), Size(w, gap * 0.4f))
                    y += gap
                }
                drawCircle(Color(0xFFE9C58C), w * 0.26f, Offset(w / 2f, h * 0.42f))
            }

            PlayerType.CLASSIC -> {
                // a square cover, title lines and a progress bar that runs across
                drawRect(Color(0xFF3A3F55), Offset(w * 0.12f, h * 0.12f), Size(w * 0.76f, w * 0.76f))
                drawRect(Color.White.copy(alpha = 0.8f), Offset(w * 0.12f, h * 0.62f), Size(w * 0.6f, 5f))
                drawRect(Color.White.copy(alpha = 0.35f), Offset(w * 0.12f, h * 0.67f), Size(w * 0.4f, 4f))
                drawRect(Color.White.copy(alpha = 0.25f), Offset(w * 0.12f, h * 0.8f), Size(w * 0.76f, 4f))
                drawRect(Color.White, Offset(w * 0.12f, h * 0.8f), Size(w * 0.76f * phase, 4f))
            }

            PlayerType.MODERN -> {
                // a cover that breathes, and a bar that fills and rewinds
                val breath = 0.94f + 0.06f * pulse(phase)
                val s = w * 0.7f * breath
                drawRect(Color(0xFF2E5E4E), Offset((w - s) / 2f, h * 0.14f), Size(s, s))
                drawRect(Color.White.copy(alpha = 0.25f), Offset(w * 0.15f, h * 0.8f), Size(w * 0.7f, 4f))
                drawRect(Color.White, Offset(w * 0.15f, h * 0.8f), Size(w * 0.7f * phase, 4f))
            }

            else -> {
                // Flagship and anything else: a frosted cover fade over the bottom bar
                drawRect(Brush.verticalGradient(listOf(Color(0xFF3C2A55), Color(0xFF0E0E10))))
                drawCircle(Color(0xFF6E58A0), w * 0.3f, Offset(w / 2f, h * 0.36f))
                drawRoundRect(Color.White.copy(alpha = 0.22f), Offset(w * 0.2f, h * 0.82f), Size(w * 0.6f, 12f), CornerRadius(12f))
                drawCircle(Color.White, 6f, Offset(w * 0.5f, h * 0.88f))
            }
        }
    }
}

private fun pulse(phase: Float): Float = 0.5f + 0.5f * sin(phase * 2f * PI.toFloat())

private fun DrawScope.rotateGroove(
    center: Offset,
    radius: Float,
    phase: Float,
) {
    val angle = phase * 360f
    for (k in 0 until 4) {
        val a = (angle + k * 90f) * PI.toFloat() / 180f
        val p = Offset(center.x + cos(a) * radius * 0.5f, center.y + sin(a) * radius * 0.5f)
        drawCircle(Color.White.copy(alpha = 0.08f), radius * 0.12f, p)
    }
}

// the picker's open state, so any screen can show it: the player menu, the artwork's long press and the settings row
object MochyStylePicker {
    private val opened = MutableStateFlow(false)
    val isOpen: StateFlow<Boolean> get() = opened

    fun show() {
        opened.value = true
    }

    fun hide() {
        opened.value = false
    }
}

// the picker over whatever is showing; it writes the chosen style to the player style setting
@Composable
fun MochyStylePickerHost() {
    val open by MochyStylePicker.isOpen.collectAsState()
    PopupVisibility(visible = open) {
        MochyStyleSelector(
            selected = Settings.shared.playerType,
            onSelect = { Settings.shared.playerType = it },
            onDismiss = { MochyStylePicker.hide() },
        )
    }
}

// what the cards' styles do when touched: nothing, a card shows its style rather than playing it
private val PreviewActions = StyleActions(onOtherMenu = {}, onEditPlayer = {})

@Composable
private fun LiveStylePreview(
    style: AlternateStyle,
    modifier: Modifier,
) {
    val session = LocalPlayerSession.current
    val song by (session?.currentSong ?: MutableStateFlow(null)).collectAsState()
    val track =
        remember(song) {
            StyleTrack(
                title = song?.title.orEmpty(),
                artist = song?.artists?.joinToString { it.name }.orEmpty(),
                album = song?.album?.name.orEmpty(),
                artwork = song?.thumbnail,
            )
        }
    val density = LocalDensity.current
    val window = LocalWindowInfo.current.containerSize
    val windowWidth = with(density) { window.width.toDp() }
    val windowHeight = with(density) { window.height.toDp() }
    BoxWithConstraints(modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        val scale = if (windowWidth > 0.dp) maxWidth / windowWidth else 1f
        CompositionLocalProvider(LocalStyleTrack provides track) {
            Box(
                Modifier
                    .requiredSize(windowWidth, windowHeight)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    },
            ) {
                AlternatePlayerStyle(style = style, actions = PreviewActions, preview = true, modifier = Modifier.fillMaxSize())
            }
        }
    }
}
