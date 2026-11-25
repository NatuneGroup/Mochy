// Ported and adapted from Mochy Music by realidkroo. Licensed under GPL-3.0.

package com.metrolist.shared.ui.player.mochy.ipod

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.metrolist.shared.model.SongItem
import com.metrolist.shared.model.previewThumbnailUrl
import com.metrolist.shared.playback.LocalPlayerSession
import com.metrolist.shared.ui.components.CrossfadeArtwork
import com.metrolist.shared.ui.components.sliders.collectPlaybackDisplayState
import com.metrolist.shared.ui.playback.LocalPlaybackController
import com.metrolist.shared.ui.player.LocalPlayerPositionActive
import com.metrolist.shared.ui.player.PlayerArtworkCrossfade
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

internal enum class IpodBar { PROGRESS, VOLUME, SCRUB }

@Stable
internal class IpodNowPlayingState {
    var bar by mutableStateOf(IpodBar.PROGRESS)
    var volume by mutableFloatStateOf(0f)
    var scrubMs by mutableLongStateOf(-1L)
    var touchedAt by mutableLongStateOf(0L)
}

@Composable
internal fun IpodNowPlaying(
    state: IpodNowPlayingState,
    scale: IpodScale,
    marquee: Boolean,
    artworkCrossfade: PlayerArtworkCrossfade,
) {
    val session = LocalPlayerSession.current
    val playbackState by LocalPlaybackController.current.state.collectAsState()
    val song = session?.currentSong?.collectAsState()?.value ?: playbackState.currentSong
    val queueIndex = session?.currentMediaItemIndex?.collectAsState()?.value ?: playbackState.currentQueueIndex
    LaunchedEffect(song?.id, queueIndex) { state.scrubMs = -1L }
    val duration =
        session
            ?.duration
            ?.collectAsState()
            ?.value
            ?.takeIf { it > 0L } ?: playbackState.durationMs.coerceAtLeast(1L)
    val positionFlow = session?.currentPosition ?: remember { MutableStateFlow(0L) }

    Box(Modifier.fillMaxSize()) {
        ClassicLayout(
            song = song,
            scale = scale,
            marquee = marquee,
            artworkCrossfade = artworkCrossfade,
        )
        Crossfade(
            targetState = state.bar == IpodBar.VOLUME,
            animationSpec = tween(160),
            label = "ipodBar",
            modifier =
                Modifier
                    .offset(y = scale.content - scale.dp(45f))
                    .fillMaxWidth()
                    .height(scale.dp(30f)),
        ) { volume ->
            if (volume) {
                VolumeRow(state, scale)
            } else {
                TimeRow(state, positionFlow, duration, scale)
            }
        }
    }
}

@Composable
private fun ClassicLayout(
    song: SongItem?,
    scale: IpodScale,
    marquee: Boolean,
    artworkCrossfade: PlayerArtworkCrossfade,
) {
    val left = scale.dp(28f)
    val width = scale.dp(174f)
    val height = scale.dp(205f)
    val top = ((scale.content - scale.dp(45f) - height) / 2f).coerceAtLeast(scale.dp(20f))
    val colors = LocalIpodLook.current.screen
    val artworkUrl = song?.previewThumbnailUrl ?: song?.thumbnail

    Box(
        modifier =
            Modifier
                .offset(left, top)
                .size(width, height)
                .graphicsLayer {
                    rotationY = 22f
                    cameraDistance = 12f * density
                }.background(colors.artBlank),
    ) {
        CrossfadeArtwork(
            animate = marquee,
            currentUrl = artworkUrl,
            nextUrl = artworkCrossfade.nextArtworkUrl,
            progress = artworkCrossfade.progressProvider,
            contentDescription = song?.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }

    val reflectHeight = height * 0.28f
    Box(
        modifier =
            Modifier
                .offset(left, top + height)
                .size(width, reflectHeight)
                .clipToBounds()
                .graphicsLayer {
                    rotationY = 22f
                    cameraDistance = 12f * density
                },
    ) {
        CrossfadeArtwork(
            animate = marquee,
            currentUrl = artworkUrl,
            nextUrl = artworkCrossfade.nextArtworkUrl,
            progress = artworkCrossfade.progressProvider,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier =
                Modifier
                    .size(width, height)
                    .graphicsLayer { scaleY = -1f },
        )
        Canvas(Modifier.fillMaxSize()) {
            drawRect(
                Brush.verticalGradient(
                    0f to colors.background.copy(alpha = REFLECTION_STRENGTH),
                    0.85f to colors.background,
                    1f to colors.background,
                ),
            )
        }
    }

    Box(
        modifier =
            Modifier
                .offset(scale.dp(252f), top)
                .width(scale.dp(MOCK_SCREEN_WIDTH - 252f - 14f))
                .height(height),
        contentAlignment = Alignment.CenterStart,
    ) {
        SongText(song, scale, marquee)
    }
}

@Composable
private fun SongText(
    song: SongItem?,
    scale: IpodScale,
    marquee: Boolean,
) {
    val colors = LocalIpodLook.current.screen
    val title = song?.title.orEmpty()
    val artist = song?.artists?.joinToString { it.name }.orEmpty()
    val album = song?.album?.name.orEmpty()

    Column(verticalArrangement = Arrangement.spacedBy(scale.dp(1f))) {
        Text(
            text = title,
            color = colors.ink,
            fontSize = scale.sp(20f),
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false,
            overflow = if (marquee) TextOverflow.Clip else TextOverflow.Ellipsis,
            modifier =
                if (marquee) {
                    Modifier
                        .graphicsLayer()
                        .basicMarquee(
                            iterations = Int.MAX_VALUE,
                            initialDelayMillis = 2000,
                            repeatDelayMillis = 2000,
                            velocity = 24.dp,
                        )
                } else {
                    Modifier
                },
        )
        SongLine(artist, scale)
        SongLine(album, scale)
    }
}

@Composable
private fun SongLine(
    text: String,
    scale: IpodScale,
) {
    if (text.isBlank()) return
    Text(
        text = text,
        color = LocalIpodLook.current.screen.softInk,
        fontSize = scale.sp(16.5f),
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun TimeRow(
    state: IpodNowPlayingState,
    positionFlow: StateFlow<Long>,
    duration: Long,
    scale: IpodScale,
) {
    val positionActive = LocalPlayerPositionActive.current
    val positionMs by positionFlow.collectPlaybackDisplayState(active = positionActive) { it }
    val scrubbing = state.bar == IpodBar.SCRUB
    val colors = LocalIpodLook.current.screen
    val shownMs = if (state.scrubMs >= 0L) state.scrubMs else positionMs

    Box(Modifier.fillMaxSize()) {
        TimeText(clock(shownMs), scale, Modifier.align(Alignment.CenterStart).padding(start = scale.dp(30f)))
        Canvas(
            Modifier
                .align(Alignment.CenterStart)
                .padding(start = scale.dp(101f))
                .size(scale.dp(268f), scale.dp(24f))
                .graphicsLayer(),
        ) {
            val fraction = (shownMs.toFloat() / duration.coerceAtLeast(1L)).coerceIn(0f, 1f)
            drawTrack(colors)
            drawFill(fraction)
            if (scrubbing) drawDiamond(fraction)
        }
        TimeText(
            "-" + clock((duration - shownMs).coerceAtLeast(0L)),
            scale,
            Modifier.align(Alignment.CenterEnd).padding(end = scale.dp(30f)),
        )
    }
}

@Composable
private fun VolumeRow(
    state: IpodNowPlayingState,
    scale: IpodScale,
) {
    val colors = LocalIpodLook.current.screen
    Box(Modifier.fillMaxSize()) {
        Canvas(
            Modifier
                .align(Alignment.CenterStart)
                .padding(start = scale.dp(48f))
                .size(scale.dp(40f), scale.dp(24f)),
        ) { drawSpeaker(loud = false, ink = colors.ink) }
        Canvas(
            Modifier
                .align(Alignment.CenterStart)
                .padding(start = scale.dp(101f))
                .size(scale.dp(268f), scale.dp(24f)),
        ) {
            drawTrack(colors)
            drawFill(state.volume)
        }
        Canvas(
            Modifier
                .align(Alignment.CenterEnd)
                .padding(end = scale.dp(48f))
                .size(scale.dp(40f), scale.dp(24f)),
        ) { drawSpeaker(loud = true, ink = colors.ink) }
    }
}

@Composable
private fun TimeText(
    text: String,
    scale: IpodScale,
    modifier: Modifier,
) {
    Text(
        text = text,
        color = LocalIpodLook.current.screen.ink,
        fontSize = scale.sp(17f),
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        softWrap = false,
        modifier = modifier,
    )
}

private fun clock(ms: Long): String {
    val total = (ms / 1000L).coerceAtLeast(0L)
    val hours = total / 3600L
    val minutes = (total % 3600L) / 60L
    val seconds = (total % 60L).toString().padStart(2, '0')
    return if (hours > 0L) {
        "$hours:${minutes.toString().padStart(2, '0')}:$seconds"
    } else {
        "${minutes.toString().padStart(2, '0')}:$seconds"
    }
}

private fun DrawScope.drawTrack(colors: IpodScreenColors) {
    drawRect(colors.track)
    drawRect(colors.trackLine, style = Stroke(1f))
}

private fun DrawScope.drawFill(fraction: Float) {
    val w = size.width * fraction.coerceIn(0f, 1f)
    if (w <= 0f) return
    clipRect(right = w) { drawRect(FillBrush) }
}

private fun DrawScope.drawDiamond(fraction: Float) {
    val x = size.width * fraction.coerceIn(0f, 1f)
    val half = size.height * 0.5f
    val cy = size.height / 2f
    val path =
        Path().apply {
            moveTo(x, cy - half)
            lineTo(x + half * 0.75f, cy)
            lineTo(x, cy + half)
            lineTo(x - half * 0.75f, cy)
            close()
        }
    drawPath(path, DiamondBrush)
    drawPath(path, DiamondEdge, style = Stroke(1.2f))
}

private fun DrawScope.drawSpeaker(
    loud: Boolean,
    ink: Color,
) {
    val h = size.height
    val cy = h / 2f
    val box = h * 0.26f
    val left = size.width * 0.12f
    drawRect(ink, topLeft = Offset(left, cy - box / 2f), size = Size(box * 0.8f, box))
    val cone =
        Path().apply {
            moveTo(left + box * 0.8f, cy - box / 2f)
            lineTo(left + box * 1.9f, cy - h * 0.34f)
            lineTo(left + box * 1.9f, cy + h * 0.34f)
            lineTo(left + box * 0.8f, cy + box / 2f)
            close()
        }
    drawPath(cone, ink)
    val waves = if (loud) 3 else 1
    for (i in 1..waves) {
        val r = h * (0.16f + 0.12f * i)
        drawArc(
            color = ink,
            startAngle = -45f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(left + box * 1.6f - r, cy - r),
            size = Size(r * 2f, r * 2f),
            style = Stroke(h * 0.07f),
        )
    }
}

private const val REFLECTION_STRENGTH = 0.62f

private val FillBrush =
    Brush.verticalGradient(
        0f to Color(0xFF9FCBF4),
        0.48f to Color(0xFF5A9EE6),
        0.52f to Color(0xFF3E87DB),
        1f to Color(0xFF7DC0FA),
    )

private val DiamondBrush = Brush.verticalGradient(listOf(Color(0xFFF4F4F4), Color(0xFF9A9A9A)))
private val DiamondEdge = Color(0xFF4A4A4A)
