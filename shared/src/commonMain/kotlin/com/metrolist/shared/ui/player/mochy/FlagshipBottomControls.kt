// Ported from Mochy Music by realidkroo. Licensed under GPL-3.0.

package com.metrolist.shared.ui.player.mochy

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.metrolist.shared.generated.resources.Res
import com.metrolist.shared.generated.resources.lyrics
import com.metrolist.shared.generated.resources.mochy_flagship_lyrics
import com.metrolist.shared.generated.resources.mochy_flagship_output_phone
import com.metrolist.shared.generated.resources.mochy_flagship_queue
import com.metrolist.shared.generated.resources.mochy_output_change
import com.metrolist.shared.generated.resources.mochy_output_speaker
import com.metrolist.shared.generated.resources.queue
import com.metrolist.shared.generated.resources.volume
import com.metrolist.shared.ui.icons.MetrolistIcons
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs
import kotlin.math.sign

// the volume's row, the room under it and the pills' row (the pill is drawn smaller inside, the rest is touch room)
private val VolumeRowHeight = 28.dp
private val PillGap = 15.dp
private val PillTouchHeight = 41.dp

// the pills as the design has them: 294 by 67 of its 1172 units across

// the bar from the top of the volume line to the bottom of its pills' touch room
internal val FlagshipBarHeight = VolumeRowHeight + PillGap + PillTouchHeight

// the caller pads the bar 26dp in, this brings everything in to the design's edges
private val BarInset = 44.dp

// barely there, so the picture behind shows through

// the volume line at rest and while it's held, and how far past its end it can be stretched
private val TrackThin = 6.5.dp
private val TrackThick = 12.dp
private val StretchMax = 26.dp
private val SpeakerGap = 10.dp

// no bounce, about a third of a second, quick enough that a held volume key never lags behind
private val VolumeGlide = spring<Float>(dampingRatio = 1f, stiffness = 400f)

private val PillHeight = 23.dp
private val PillWidth = 99.dp
private val PillShape = RoundedCornerShape(50)
internal val FlagshipBarBottomGap = 8.dp

@Composable
internal fun FlagshipBottomControls(
    volume: FlagshipVolume?,
    lyricsVisible: Boolean,
    queueVisible: Boolean,
    outputName: String?,
    contentColor: Color,
    onOutput: () -> Unit,
    onLyrics: () -> Unit,
    onQueue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val outputLabel = stringResource(Res.string.mochy_output_change)
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = BarInset),
    ) {
        if (volume != null) {
            FlagshipVolumeLine(volume, stringResource(Res.string.volume), contentColor)
        } else {
            Spacer(Modifier.height(VolumeRowHeight))
        }
        Spacer(Modifier.height(PillGap))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth().height(PillTouchHeight),
        ) {
            Box(
                contentAlignment = Alignment.CenterStart,
                modifier =
                    Modifier
                        .weight(1f, fill = false)
                        .fillMaxHeight()
                        .clickable(role = Role.Button, onClick = onOutput)
                        .semantics {
                            contentDescription = outputLabel
                        },
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier =
                        Modifier
                            .widthIn(min = PillWidth, max = 200.dp)
                            .height(PillHeight)
                            .clip(PillShape)
                            .background(contentColor.copy(alpha = 0.1f))
                            .padding(horizontal = 11.dp),
                ) {
                    if (outputName == null) {
                        Icon(
                            painter = painterResource(Res.drawable.mochy_flagship_output_phone),
                            contentDescription = null,
                            tint = contentColor,
                            modifier = Modifier.size(11.5.dp),
                        )
                    } else {
                        Icon(MetrolistIcons.Filled.Cast, contentDescription = null, tint = contentColor, modifier = Modifier.size(11.5.dp))
                    }
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = outputName ?: stringResource(Res.string.mochy_output_speaker),
                        style =
                            TextStyle(
                                fontFamily = flagshipFontFamily(),
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.5.sp,
                                letterSpacing = (-0.03).em,
                                color = contentColor,
                            ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Row(Modifier.width(PillWidth).fillMaxHeight()) {
                FlagshipPageButton(
                    Res.drawable.mochy_flagship_lyrics,
                    stringResource(Res.string.lyrics),
                    lyricsVisible,
                    start = true,
                    contentColor = contentColor,
                    onClick = onLyrics,
                )
                FlagshipPageButton(
                    Res.drawable.mochy_flagship_queue,
                    stringResource(Res.string.queue),
                    queueVisible,
                    start = false,
                    contentColor = contentColor,
                    onClick = onQueue,
                )
            }
        }
    }
}

@Composable
private fun RowScope.FlagshipPageButton(
    icon: DrawableResource,
    label: String,
    active: Boolean,
    start: Boolean,
    contentColor: Color,
    onClick: () -> Unit,
) {
    val corner = PillHeight / 2
    val shape =
        if (start) {
            RoundedCornerShape(topStart = corner, bottomStart = corner)
        } else {
            RoundedCornerShape(topEnd = corner, bottomEnd = corner)
        }
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .clickable(role = Role.Button, onClick = onClick)
                .semantics {
                    contentDescription = label
                    selected = active
                },
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(PillHeight)
                    .background(contentColor.copy(alpha = if (active) 0.2f else 0.1f), shape),
        ) {
            Icon(
                painterResource(icon),
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(12.5.dp),
            )
        }
    }
}

@Composable
private fun FlagshipVolumeLine(
    volume: FlagshipVolume,
    description: String,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    // read while drawing only, so a drag redraws the line at the screen's own rate and recomposes nothing
    val held = remember { Animatable(0f) }
    val stretch = remember { mutableFloatStateOf(0f) }
    val settle = remember { arrayOfNulls<Job>(1) }
    // the volume the line shows, so the volume keys slide it along instead of jumping it a step at a time
    val line = remember(volume) { Animatable(volume.shown) }
    LaunchedEffect(volume) {
        snapshotFlow { volume.shown }.collect { target ->
            // under the finger the line is drawn straight from the drag, so nothing needs launching on every move
            if (volume.isDragging) return@collect
            // a fresh launch per change keeps the speed it had, so holding a key glides in one go
            launch { line.animateTo(target, VolumeGlide) }
        }
    }
    val speakers = rememberSpeakerMotion(volume)

    Spacer(
        modifier =
            modifier
                .fillMaxWidth()
                .height(VolumeRowHeight)
                .pointerInput(volume) {
                    val reach = StretchMax.toPx()
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var travelled = 0f
                        // up and down is left to the player sheet; only a sideways move takes the line
                        val first =
                            awaitHorizontalTouchSlopOrCancellation(down.id) { change, over ->
                                change.consume()
                                travelled = over
                            } ?: return@awaitEachGesture
                        settle[0]?.cancel()
                        scope.launch { held.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = 500f)) }
                        val track = (size.width - speakersWidth(SpeakerHeight.toPx()) - SpeakerGap.toPx() * 2f).coerceAtLeast(1f)
                        // grab the line where it's drawn, even if it's still gliding after a key press
                        val start = line.value

                        fun follow() {
                            val raw = start + travelled / track
                            volume.drag(raw.coerceIn(0f, 1f))
                            val over =
                                when {
                                    raw > 1f -> (raw - 1f) * track
                                    raw < 0f -> raw * track
                                    else -> 0f
                                }
                            stretch.floatValue = rubberBand(over, reach)
                        }
                        try {
                            follow()
                            horizontalDrag(first.id) { change ->
                                travelled += change.positionChange().x
                                change.consume()
                                follow()
                            }
                        } finally {
                            val lifted = volume.shown
                            scope.launch { line.snapTo(lifted) }
                            volume.release()
                        }
                        // a bouncy spring back, the way the iphone's slider snaps home
                        val from = stretch.floatValue
                        settle[0] =
                            scope.launch {
                                animate(from, 0f, animationSpec = spring(dampingRatio = 0.45f, stiffness = 420f)) { v, _ ->
                                    stretch.floatValue = v
                                }
                            }
                        scope.launch { held.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = 380f)) }
                    }
                }.semantics {
                    contentDescription = description
                    progressBarRangeInfo = ProgressBarRangeInfo(volume.shown, 0f..1f)
                    setProgress { target ->
                        volume.drag(target.coerceIn(0f, 1f))
                        volume.release()
                        true
                    }
                }.drawWithCache {
                    val iconPx = SpeakerHeight.toPx()
                    val quietPx = QUIET_SPEAKER_WIDTH * iconPx
                    val loudPx = LOUD_SPEAKER_WIDTH * iconPx
                    val gapPx = SpeakerGap.toPx()
                    val reach = StretchMax.toPx()
                    val body = speakerBody(iconPx)
                    val stroke = waveStroke(iconPx)
                    onDrawBehind {
                        val h = held.value.coerceIn(0f, 1.2f)
                        val s = stretch.floatValue
                        val pull = (abs(s) / reach).coerceIn(0f, 1f)
                        val cy = size.height / 2f
                        val left = quietPx + gapPx + minOf(s, 0f)
                        val right = size.width - loudPx - gapPx + maxOf(s, 0f)
                        // a little thinner the further it's stretched, like a band pulled tight
                        val thickness = (TrackThin.toPx() + (TrackThick.toPx() - TrackThin.toPx()) * h) * (1f - 0.18f * pull)
                        val radius = CornerRadius(thickness / 2f)
                        val top = cy - thickness / 2f
                        if (right <= left) return@onDrawBehind
                        drawRoundRect(
                            contentColor.copy(alpha = 0.22f + 0.08f * h),
                            Offset(left, top),
                            Size(right - left, thickness),
                            radius,
                        )
                        val level = if (volume.isDragging) volume.shown else line.value
                        val filled = (right - left) * level.coerceIn(0f, 1f)
                        if (filled > 0.5f) {
                            drawRoundRect(
                                contentColor,
                                Offset(left, top),
                                Size(filled.coerceIn(minOf(thickness, right - left), right - left), thickness),
                                radius,
                            )
                        }
                        val alpha = 0.6f + 0.4f * h.coerceAtMost(1f)
                        drawQuietSpeaker(
                            body,
                            x = minOf(s, 0f) * 0.55f,
                            cy,
                            iconPx,
                            grow = 1f + 0.1f * h + 0.12f * if (s < 0f) pull else 0f,
                            alpha = alpha,
                            color = contentColor,
                            mute = speakers.mute.value,
                        )
                        drawLoudSpeaker(
                            body,
                            stroke,
                            x = size.width - loudPx + maxOf(s, 0f) * 0.55f,
                            cy,
                            iconPx,
                            grow = 1f + 0.1f * h + 0.12f * if (s > 0f) pull else 0f,
                            alpha = alpha,
                            color = contentColor,
                            level = level,
                            echo = speakers.echo.value,
                        )
                    }
                },
    )
}

// both speakers side by side, so the line knows how much of the row is its own
private fun speakersWidth(heightPx: Float): Float = (QUIET_SPEAKER_WIDTH + LOUD_SPEAKER_WIDTH) * heightPx

private fun rubberBand(
    over: Float,
    reach: Float,
): Float = sign(over) * (1f - 1f / (abs(over) * 0.55f / reach + 1f)) * reach
