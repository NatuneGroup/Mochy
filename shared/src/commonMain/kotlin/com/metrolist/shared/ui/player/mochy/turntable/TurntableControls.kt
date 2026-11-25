// Ported and adapted from Mochy Music by realidkroo. Licensed under GPL-3.0.

package com.metrolist.shared.ui.player.mochy.turntable

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.metrolist.shared.generated.resources.Res
import com.metrolist.shared.generated.resources.action_like
import com.metrolist.shared.generated.resources.mochy_expand_less
import com.metrolist.shared.generated.resources.mochy_heart
import com.metrolist.shared.generated.resources.mochy_heart_outline
import com.metrolist.shared.generated.resources.mochy_hold_fast
import com.metrolist.shared.generated.resources.mochy_hold_slow
import com.metrolist.shared.generated.resources.mochy_replay
import com.metrolist.shared.generated.resources.more_options
import com.metrolist.shared.generated.resources.next
import com.metrolist.shared.generated.resources.pause
import com.metrolist.shared.generated.resources.play
import com.metrolist.shared.generated.resources.previous
import com.metrolist.shared.generated.resources.unlike
import com.metrolist.shared.model.ArtistTiny
import com.metrolist.shared.playback.LocalPlayerSession
import com.metrolist.shared.ui.components.sliders.collectPlaybackDisplayState
import com.metrolist.shared.ui.haptics.HapticEvent
import com.metrolist.shared.ui.haptics.rememberMetrolistHaptics
import com.metrolist.shared.ui.mochy.LikeBurstState
import com.metrolist.shared.ui.mochy.LikeSlot
import com.metrolist.shared.ui.mochy.likeButtonTarget
import com.metrolist.shared.ui.navigation.navigationPopupSource
import com.metrolist.shared.ui.player.LocalPlayerContentActive
import com.metrolist.shared.ui.player.LocalPlayerPositionActive
import com.metrolist.shared.ui.player.PlayerArtistLine
import com.metrolist.shared.ui.player.mochy.SkipCycleState
import com.metrolist.shared.ui.player.mochy.drawPlayGlyph
import com.metrolist.shared.ui.player.mochy.drawSkipGlyph
import com.metrolist.shared.ui.player.mochy.flagshipPressScale
import com.metrolist.shared.ui.player.mochy.rememberPlayGlyph
import com.metrolist.shared.ui.player.mochy.rememberTransportShapes
import com.metrolist.shared.ui.theme.mochyInterFontFamily
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private val PillWidth = 59.dp
private val PillHeight = 42.dp
private val PillGap = 11.dp
private val ChevronSize = 43.dp
private val SideMargin = 30.dp
private val PillFill = Color.White.copy(alpha = 0.10f)
private val TitleGap = 4.dp

@Composable
internal fun TurntableControls(
    title: String,
    artists: List<ArtistTiny>,
    isPlaying: Boolean,
    isLoading: Boolean,
    isLiked: Boolean,
    menuOpen: Boolean,
    onToggleMenu: () -> Unit,
    onToggleLike: () -> Unit,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSongInfo: () -> Unit,
    onNavigateToArtist: (String) -> Unit,
    modifier: Modifier = Modifier,
    contentColor: Color = Color.White,
    likeBurstState: LikeBurstState? = null,
) {
    val chevron: @Composable () -> Unit = {
        TurntableChevronButton(
            open = menuOpen,
            contentColor = contentColor,
            onClick = onToggleMenu,
        )
    }
    val titleBlock: @Composable (Modifier) -> Unit = { weightModifier ->
        TurntableTitleLines(
            title = title,
            artists = artists,
            contentColor = contentColor,
            onSongInfo = onSongInfo,
            onNavigateToArtist = onNavigateToArtist,
            modifier = weightModifier,
        )
    }
    val pills: @Composable (Boolean, Modifier) -> Unit = { horizontal, pillModifier ->
        TurntablePills(
            isPlaying = isPlaying,
            isLoading = isLoading,
            isLiked = isLiked,
            contentColor = contentColor,
            onToggleLike = onToggleLike,
            onPlayPause = onPlayPause,
            onPrevious = onPrevious,
            onNext = onNext,
            likeBurstState = likeBurstState,
            horizontal = horizontal,
            modifier = pillModifier,
        )
    }

    BoxWithConstraints(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = SideMargin),
    ) {
        val wide = maxWidth >= 500.dp
        if (wide) {
            Row(
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    chevron()
                    Spacer(Modifier.width(14.dp))
                    titleBlock(Modifier.weight(1f))
                }
                Spacer(Modifier.width(14.dp))
                pills(false, Modifier)
            }
        } else {
            Column(
                verticalArrangement = Arrangement.spacedBy(PillGap),
                modifier = Modifier.fillMaxWidth(),
            ) {
                pills(false, Modifier.align(Alignment.End))
                Spacer(Modifier.height(TitleGap))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    chevron()
                    Spacer(Modifier.width(14.dp))
                    titleBlock(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
internal fun TurntablePills(
    isPlaying: Boolean,
    isLoading: Boolean,
    isLiked: Boolean,
    onToggleLike: () -> Unit,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    contentColor: Color = Color.White,
    likeBurstState: LikeBurstState? = null,
    horizontal: Boolean = false,
) {
    val session = LocalPlayerSession.current
    val positionActive = LocalPlayerPositionActive.current
    val duration = session?.duration?.collectAsState()?.value ?: 0L
    val atEnd = remember(duration) { { position: Long -> duration > 0L && position >= duration } }
    val ended =
        !isPlaying && !isLoading &&
            (session?.currentPosition?.collectPlaybackDisplayState(active = positionActive, transform = atEnd)?.value == true)
    val haptics = rememberMetrolistHaptics()
    var activeRestore by remember(session) { mutableStateOf<(() -> Unit)?>(null) }
    DisposableEffect(Unit) {
        onDispose {
            activeRestore?.invoke()
            activeRestore = null
        }
    }

    fun startHold(multiplier: Float): (() -> Unit)? {
        if (activeRestore != null) return null
        val restore = session?.beginTemporaryPlaybackSpeed(multiplier) ?: return null
        val cleanup = {
            if (activeRestore != null) {
                activeRestore = null
                restore()
            }
        }
        activeRestore = cleanup
        return cleanup
    }

    val shapes = rememberTransportShapes(small = true)
    val pills: @Composable () -> Unit = {
        Pill(contentColor = contentColor) {
            PillLikeButton(
                icon = if (isLiked) Res.drawable.mochy_heart else Res.drawable.mochy_heart_outline,
                description = stringResource(if (isLiked) Res.string.unlike else Res.string.action_like),
                tint = contentColor.copy(alpha = if (isLiked) 1f else 0.85f),
                onClick = onToggleLike,
                modifier = Modifier.likeButtonTarget(likeBurstState, LikeSlot.PLAYER),
            )
        }
        Pill(contentColor = contentColor) {
            TurntableSkipButton(
                shapes = shapes,
                backwards = true,
                description = stringResource(Res.string.previous),
                holdText = stringResource(Res.string.mochy_hold_slow),
                contentColor = contentColor,
                onSkip = {
                    haptics.perform(HapticEvent.PRESS)
                    onPrevious()
                },
                startHold = { startHold(0.5f) },
            )
        }
        Pill(contentColor = contentColor) {
            TurntablePlayPauseButton(
                shapes = shapes,
                playing = isPlaying,
                ended = ended,
                description = stringResource(if (isPlaying) Res.string.pause else Res.string.play),
                contentColor = contentColor,
                onClick = {
                    if (ended && session != null) {
                        session.seekTo(0L)
                        session.togglePlayPause()
                    } else {
                        onPlayPause()
                    }
                },
            )
        }
        Pill(contentColor = contentColor) {
            TurntableSkipButton(
                shapes = shapes,
                backwards = false,
                description = stringResource(Res.string.next),
                holdText = stringResource(Res.string.mochy_hold_fast),
                contentColor = contentColor,
                onSkip = {
                    haptics.perform(HapticEvent.PRESS)
                    onNext()
                },
                startHold = { startHold(2f) },
            )
        }
    }
    if (horizontal) {
        Row(horizontalArrangement = Arrangement.spacedBy(PillGap), modifier = modifier) { pills() }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(PillGap), modifier = modifier) { pills() }
    }
}

@Composable
private fun Pill(
    contentColor: Color,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val fill = if (contentColor == Color.White) PillFill else contentColor.copy(alpha = 0.12f)
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .size(PillWidth, PillHeight)
                .clip(RoundedCornerShape(50))
                .background(fill),
    ) { content() }
}

@Composable
private fun PillLikeButton(
    icon: DrawableResource,
    description: String,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val scale = flagshipPressScale(interaction)
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    role = Role.Button,
                    onClick = onClick,
                ),
    ) {
        Image(
            painter = painterResource(icon),
            contentDescription = description,
            colorFilter = ColorFilter.tint(tint),
            modifier =
                modifier
                    .size(20.dp)
                    .graphicsLayer {
                        scaleX = scale()
                        scaleY = scaleX
                    },
        )
    }
}

@Composable
private fun TurntablePlayPauseButton(
    shapes: com.metrolist.shared.ui.player.mochy.TransportShapes,
    playing: Boolean,
    ended: Boolean,
    description: String,
    contentColor: Color,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val scale = flagshipPressScale(interaction)
    val glyph = rememberPlayGlyph(playing)
    val iconPx = with(LocalDensity.current) { 21.dp.toPx() }
    val unit = (iconPx / shapes.playSize.height.coerceAtLeast(1f))
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale()
                    scaleY = scaleX
                }.clickable(
                    interactionSource = interaction,
                    indication = null,
                    role = Role.Button,
                    onClick = onClick,
                ).semantics { contentDescription = description },
    ) {
        if (ended) {
            Image(
                painter = painterResource(Res.drawable.mochy_replay),
                contentDescription = null,
                colorFilter = ColorFilter.tint(contentColor),
                modifier = Modifier.size(21.dp),
            )
        } else {
            Spacer(
                Modifier
                    .fillMaxSize()
                    .drawBehind { drawPlayGlyph(shapes, glyph, unit, contentColor) },
            )
        }
    }
}

@Composable
private fun TurntableSkipButton(
    shapes: com.metrolist.shared.ui.player.mochy.TransportShapes,
    backwards: Boolean,
    description: String,
    holdText: String,
    contentColor: Color,
    onSkip: () -> Unit,
    startHold: () -> (() -> Unit)?,
) {
    val haptics = rememberMetrolistHaptics()
    val scope = rememberCoroutineScope()
    val cycle = remember { SkipCycleState() }
    val interaction = remember { MutableInteractionSource() }
    val scale = flagshipPressScale(interaction)
    val latestStart by rememberUpdatedState(startHold)
    val tap by rememberUpdatedState {
        scope.launch { cycle.step() }
        onSkip()
    }
    var restoreHold by remember { mutableStateOf<(() -> Unit)?>(null) }
    val holding = restoreHold != null
    LaunchedEffect(holding) {
        while (holding) {
            launch { cycle.step() }
            delay(if (backwards) 900L else 420L)
        }
    }
    val iconPx = with(LocalDensity.current) { 22.dp.toPx() }
    val unit = iconPx / shapes.pairWidth.coerceAtLeast(1f)
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale()
                    scaleY = scaleX
                }.pointerInput(interaction) {
                    detectTapGestures(
                        onPress = { point ->
                            val press = PressInteraction.Press(point)
                            interaction.tryEmit(press)
                            var released = false
                            try {
                                released = tryAwaitRelease()
                            } finally {
                                interaction.tryEmit(if (released) PressInteraction.Release(press) else PressInteraction.Cancel(press))
                                val cleanup = restoreHold
                                restoreHold = null
                                cleanup?.invoke()
                            }
                        },
                        onLongPress = {
                            val cleanup = latestStart()
                            restoreHold = cleanup
                            if (cleanup != null) haptics.perform(HapticEvent.PRESS)
                        },
                        onTap = { tap() },
                    )
                }.semantics {
                    contentDescription = description
                    role = Role.Button
                    onClick {
                        tap()
                        true
                    }
                },
    ) {
        Spacer(
            Modifier
                .fillMaxSize()
                .drawBehind { drawSkipGlyph(shapes, cycle.phase(), unit, contentColor, backwards) },
        )
        if (holding) {
            Text(
                text = holdText,
                color = contentColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 2.dp),
            )
        }
    }
}

@Composable
private fun TurntableChevronButton(
    open: Boolean,
    contentColor: Color,
    onClick: () -> Unit,
) {
    val rotation by animateFloatAsState(
        if (open) 180f else 0f,
        spring(dampingRatio = 0.55f, stiffness = 380f),
        label = "ttChevron",
    )
    val surface by animateColorAsState(
        if (open) contentColor.copy(alpha = 0.28f) else contentColor.copy(alpha = 0.10f),
        tween(durationMillis = 280, easing = FastOutSlowInEasing),
        label = "ttChevronSurface",
    )
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.86f else 1f,
        spring(dampingRatio = 0.54f, stiffness = 720f),
        label = "ttChevronPress",
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .size(ChevronSize)
                .navigationPopupSource()
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    role = Role.Button,
                    onClick = onClick,
                ),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }.clip(CircleShape)
                    .background(surface),
        ) {
            Image(
                painter = painterResource(Res.drawable.mochy_expand_less),
                contentDescription = stringResource(Res.string.more_options),
                colorFilter = ColorFilter.tint(contentColor.copy(alpha = 0.90f)),
                modifier =
                    Modifier
                        .size(20.dp)
                        .graphicsLayer { rotationZ = rotation },
            )
        }
    }
}

@Composable
private fun TurntableTitleLines(
    title: String,
    artists: List<ArtistTiny>,
    contentColor: Color,
    onSongInfo: () -> Unit,
    onNavigateToArtist: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val inter = mochyInterFontFamily()
    Column(modifier = modifier) {
        Text(
            text = title,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            style =
                TextStyle(
                    fontFamily = inter,
                    fontWeight = FontWeight.Bold,
                    fontSize = 26.sp,
                    lineHeight = 30.sp,
                    letterSpacing = (-0.035).em,
                    color = contentColor,
                ),
            modifier =
                Modifier
                    .clickable(enabled = title.isNotBlank(), onClick = onSongInfo)
                    .basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 2500, velocity = 28.dp),
        )
        PlayerArtistLine(
            artists = artists,
            onNavigateToArtist = onNavigateToArtist,
            iterations = if (LocalPlayerContentActive.current) 1 else 0,
            style =
                TextStyle(
                    fontFamily = inter,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    lineHeight = 20.sp,
                    letterSpacing = (-0.02).em,
                ),
            color = contentColor.copy(alpha = 0.62f),
        )
    }
}
