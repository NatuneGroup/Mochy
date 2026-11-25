// Ported from Mochy Music by realidkroo. Licensed under GPL-3.0.

package com.metrolist.shared.ui.player.mochy

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrolist.shared.generated.resources.Res
import com.metrolist.shared.generated.resources.mochy_hold_fast
import com.metrolist.shared.generated.resources.mochy_hold_slow
import com.metrolist.shared.generated.resources.mochy_replay
import com.metrolist.shared.generated.resources.next
import com.metrolist.shared.generated.resources.pause
import com.metrolist.shared.generated.resources.play
import com.metrolist.shared.generated.resources.playback_speed_value
import com.metrolist.shared.generated.resources.previous
import com.metrolist.shared.playback.LocalPlayerSession
import com.metrolist.shared.ui.components.sliders.collectPlaybackDisplayState
import com.metrolist.shared.ui.haptics.HapticEvent
import com.metrolist.shared.ui.haptics.rememberMetrolistHaptics
import com.metrolist.shared.ui.player.LocalPlayerPositionActive
import com.metrolist.shared.utils.formatPlaybackSpeed
import com.metrolist.shared.utils.normalizePlaybackTempo
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

private const val DESIGN_WIDTH = 1172f
private val ButtonCentres = floatArrayOf(0.215f, 0.5f, 0.785f)

private data class ActiveSpeedHold(
    val heldSpeed: Float,
    val restore: () -> Unit,
)

@Composable
fun FlagshipTransport(
    playing: Boolean,
    loading: Boolean,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    contentColor: Color = Color.White,
) {
    val session = LocalPlayerSession.current
    val positionActive = LocalPlayerPositionActive.current
    val duration = session?.duration?.collectAsState()?.value ?: 0L
    val atEnd = remember(duration) { { position: Long -> duration > 0L && position >= duration } }
    val ended =
        !playing && !loading &&
            (session?.currentPosition?.collectPlaybackDisplayState(active = positionActive, transform = atEnd)?.value == true)
    val haptics = rememberMetrolistHaptics()
    var holdInProgress by remember(session) { mutableStateOf(false) }

    fun beginHold(multiplier: Float): ActiveSpeedHold? {
        if (holdInProgress) return null
        val activeSession = session ?: return null
        val previous = activeSession.playbackSpeedFlow.value
        val heldSpeed = normalizePlaybackTempo(previous * multiplier) ?: return null
        val restore = activeSession.beginTemporaryPlaybackSpeed(multiplier) ?: return null
        holdInProgress = true
        return ActiveSpeedHold(heldSpeed = heldSpeed) {
            if (holdInProgress) {
                holdInProgress = false
                restore()
            }
        }
    }

    val shapes = rememberTransportShapes()
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val unit = (maxWidth / DESIGN_WIDTH).coerceIn(if (compact) 0.14.dp else 0.18.dp, 0.42.dp)
        val touch = minOf(if (compact) 50.dp else 76.dp, maxWidth * 0.27f).coerceAtLeast(36.dp)
        Layout(
            modifier = Modifier.fillMaxWidth(),
            content = {
                FlagshipSkip(
                    shapes = shapes,
                    unit = unit,
                    touch = touch,
                    backwards = true,
                    description = stringResource(Res.string.previous),
                    contentColor = contentColor,
                    onSkip = {
                        haptics.perform(HapticEvent.PRESS)
                        onPrevious()
                    },
                    startHold = { beginHold(0.5f) },
                )
                val interaction = remember { MutableInteractionSource() }
                val scale = flagshipPressScale(interaction)
                val glyph = rememberPlayGlyph(playing)
                val description = stringResource(if (playing) Res.string.pause else Res.string.play)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier =
                        Modifier
                            .size(touch)
                            .graphicsLayer {
                                scaleX = scale()
                                scaleY = scaleX
                            }.clickable(
                                interactionSource = interaction,
                                indication = null,
                                role = Role.Button,
                                onClick = onPlayPause,
                            ).semantics { contentDescription = description },
                ) {
                    if (ended) {
                        Image(
                            painter = painterResource(Res.drawable.mochy_replay),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(contentColor),
                            modifier = Modifier.size(unit * shapes.playSize.height * 0.9f),
                        )
                    } else {
                        Spacer(
                            Modifier
                                .fillMaxSize()
                                .drawBehind { drawPlayGlyph(shapes, glyph, unit.toPx(), contentColor) },
                        )
                    }
                }
                FlagshipSkip(
                    shapes = shapes,
                    unit = unit,
                    touch = touch,
                    backwards = false,
                    description = stringResource(Res.string.next),
                    contentColor = contentColor,
                    onSkip = {
                        haptics.perform(HapticEvent.PRESS)
                        onNext()
                    },
                    startHold = { beginHold(2f) },
                )
            },
        ) { measurables, constraints ->
            val loose = constraints.copy(minWidth = 0, minHeight = 0)
            val placeables = measurables.map { it.measure(loose) }
            val height = placeables.maxOf { it.height }
            val width = constraints.maxWidth
            layout(width, height) {
                placeables.forEachIndexed { index, item ->
                    item.place((width * ButtonCentres[index] - item.width / 2f).roundToInt(), (height - item.height) / 2)
                }
            }
        }
    }
}

@Composable
private fun FlagshipSkip(
    shapes: TransportShapes,
    unit: Dp,
    touch: Dp,
    backwards: Boolean,
    description: String,
    contentColor: Color,
    onSkip: () -> Unit,
    startHold: () -> ActiveSpeedHold?,
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
    var activeHold by remember { mutableStateOf<ActiveSpeedHold?>(null) }
    var lastHeldSpeed by remember { mutableStateOf(if (backwards) 0.5f else 2f) }
    val holding = activeHold != null
    DisposableEffect(Unit) {
        onDispose { activeHold?.restore?.invoke() }
    }
    LaunchedEffect(holding) {
        while (holding) {
            launch { cycle.step() }
            delay(if (backwards) 900L else 420L)
        }
    }
    val badge by animateFloatAsState(
        targetValue = if (holding) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 520f),
        label = "flagshipHoldBadge",
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .size(touch)
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
                                val hold = activeHold
                                activeHold = null
                                hold?.restore?.invoke()
                            }
                        },
                        onLongPress = {
                            val hold = latestStart()
                            activeHold = hold
                            if (hold != null) {
                                lastHeldSpeed = hold.heldSpeed
                                haptics.perform(HapticEvent.PRESS)
                            }
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
                .drawBehind { drawSkipGlyph(shapes, cycle.phase(), unit.toPx(), contentColor, backwards) },
        )
        if (badge > 0.01f) {
            val formattedSpeed = formatPlaybackSpeed(lastHeldSpeed).trimEnd('0').trimEnd('.')
            val badgeText =
                when (lastHeldSpeed) {
                    0.5f -> stringResource(Res.string.mochy_hold_slow)
                    2f -> stringResource(Res.string.mochy_hold_fast)
                    else -> stringResource(Res.string.playback_speed_value, formattedSpeed)
                }
            val badgeTextColor = if (contentColor.luminance() >= 0.5f) Color.Black else Color.White
            Text(
                text = badgeText,
                color = badgeTextColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier =
                    Modifier
                        .align(Alignment.TopCenter)
                        .graphicsLayer {
                            alpha = badge.coerceIn(0f, 1f)
                            val scale = 0.6f + 0.4f * badge
                            scaleX = scale
                            scaleY = scale
                            translationY = -(4.dp.toPx() + 12.dp.toPx() * badge)
                        }.clip(RoundedCornerShape(50))
                        .background(contentColor.copy(alpha = 0.92f))
                        .padding(horizontal = 9.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
internal fun flagshipPressScale(source: MutableInteractionSource): () -> Float {
    val pressed by source.collectIsPressedAsState()
    val scale =
        animateFloatAsState(
            targetValue = if (pressed) 0.86f else 1f,
            animationSpec = spring(dampingRatio = 0.54f, stiffness = 720f),
            label = "flagshipPress",
        )
    return { scale.value }
}
