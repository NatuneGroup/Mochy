// Ported from Sora (github.com/shizenworks/sora), its Apple Music-style player. Licensed under GPL-3.0.

/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 *
 * Player design ported from Flamingo (yos.music.player) ui/pages/NowPlaying.kt — GPLv3,
 * https://github.com/shouryadixitisverycool/Flamingo
 */

package com.metrolist.shared.ui.player.mochy.styles.flamingo

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.EaseOutQuart
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import com.metrolist.shared.generated.resources.Res
import com.metrolist.shared.generated.resources.flamingo_np_airplay
import com.metrolist.shared.generated.resources.flamingo_np_favorite
import com.metrolist.shared.generated.resources.flamingo_np_favorited
import com.metrolist.shared.generated.resources.flamingo_np_fforward
import com.metrolist.shared.generated.resources.flamingo_np_lyrics
import com.metrolist.shared.generated.resources.flamingo_np_lyricson
import com.metrolist.shared.generated.resources.flamingo_np_more
import com.metrolist.shared.generated.resources.flamingo_np_pause
import com.metrolist.shared.generated.resources.flamingo_np_play
import com.metrolist.shared.generated.resources.flamingo_np_queue
import com.metrolist.shared.generated.resources.flamingo_np_queueon
import com.metrolist.shared.generated.resources.flamingo_np_repeat
import com.metrolist.shared.generated.resources.flamingo_np_repeatone
import com.metrolist.shared.generated.resources.flamingo_np_rewind
import com.metrolist.shared.generated.resources.flamingo_np_shuffle
import com.metrolist.shared.generated.resources.flamingo_np_volume
import com.metrolist.shared.generated.resources.flamingo_np_volume_full
import com.metrolist.shared.generated.resources.flamingo_queue_count
import com.metrolist.shared.generated.resources.flamingo_queue_title
import com.metrolist.shared.model.LyricsEntry
import com.metrolist.shared.model.SongItem
import com.metrolist.shared.playback.LocalPlayerSession
import com.metrolist.shared.playback.PlayerSession
import com.metrolist.shared.state.SongLikeState
import com.metrolist.shared.ui.player.mochy.styles.ExpressiveLyrics
import com.metrolist.shared.ui.player.mochy.styles.LivePosition
import com.metrolist.shared.ui.player.mochy.styles.LocalStyleLyricLines
import com.metrolist.shared.ui.player.mochy.styles.rememberDurationMs
import com.metrolist.shared.ui.player.mochy.styles.rememberLivePosition
import com.metrolist.shared.ui.player.mochy.styles.withLyricsOffset
import com.metrolist.shared.utils.formatDuration
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs

private enum class FlamingoPage { Album, Lyric, PlayingList }

private const val ANIM_MS = 600
private const val CONTROLS_AUTO_HIDE_MS = 2500L
private val FlamingoTrackBacking = Color(0x4D000000)
private val FlamingoAlbumShape = RoundedCornerShape(10.dp)

/**
 * The Apple Music player as Flamingo draws it: the cover over its own blurred colours with the title and the
 * favourite and more buttons under it; the lyrics, where the cover shrinks into a bar at the top; and the queue
 * under the same bar. Below them all, the thin seek track, the big transport, the volume and the lyrics, output and
 * queue buttons. Its ties to the app come in as callbacks: closing the player, the song's menu, and the cast picker
 * (null where there is none).
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun FlamingoPlayerStyle(
    onCollapse: () -> Unit,
    onMore: () -> Unit,
    onOutput: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val session = LocalPlayerSession.current ?: return
    val song by session.currentSong.collectAsState()
    val current = song ?: return
    val isPlaying by session.isPlaying.collectAsState()
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    var page by remember { mutableStateOf(FlamingoPage.Album) }
    // the lyrics page hides its controls after a while; a tap brings them back
    var controlsShown by remember { mutableStateOf(true) }
    var controlsTouch by remember { mutableStateOf(0) }
    LaunchedEffect(page, controlsTouch) {
        controlsShown = true
        if (page == FlamingoPage.Lyric) {
            delay(CONTROLS_AUTO_HIDE_MS)
            controlsShown = false
        }
    }
    val liked = SongLikeState.isLiked(current.id)
    val livePosition = rememberLivePosition()
    val lyricLines = LocalStyleLyricLines.current
    val lyricsPosition = livePosition.withLyricsOffset()

    CompositionLocalProvider(LocalContentColor provides Color.White) {
        Box(modifier.fillMaxSize().background(Color.Black)) {
            FlamingoFloatingLight(
                artworkUrl = current.thumbnail,
                dimmed = page != FlamingoPage.Lyric,
                modifier = Modifier.fillMaxSize(),
            )

            Column(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    SharedTransitionLayout {
                        AnimatedContent(
                            targetState = page,
                            transitionSpec = {
                                fadeIn(tween(ANIM_MS, easing = FastOutSlowInEasing)) togetherWith
                                    fadeOut(tween(ANIM_MS, easing = FastOutSlowInEasing))
                            },
                            modifier = Modifier.fillMaxSize(),
                            label = "flamingoPage",
                        ) { shown ->
                            // the cover and the bar's thumbnail are one element, so it grows and shrinks between them
                            val shared =
                                Modifier.sharedBounds(
                                    sharedContentState = rememberSharedContentState(key = "flamingoAlbum"),
                                    animatedVisibilityScope = this@AnimatedContent,
                                    boundsTransform =
                                        BoundsTransform { _, _ ->
                                            spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
                                        },
                                )
                            when (shown) {
                                FlamingoPage.Album -> {
                                    Column(Modifier.fillMaxSize().padding(top = topInset + 22.dp)) {
                                        FlamingoAlbum(
                                            artworkUrl = current.thumbnail,
                                            isPlaying = isPlaying,
                                            modifier = shared,
                                        )
                                        AnimatedContent(
                                            targetState = current,
                                            transitionSpec = { fadeIn() togetherWith fadeOut() },
                                            modifier = Modifier.padding(horizontal = 32.dp),
                                            label = "flamingoTitle",
                                        ) { s ->
                                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                                Column(Modifier.weight(1f).padding(end = 15.dp)) {
                                                    Text(
                                                        text = s.title,
                                                        fontSize = 19.5.sp,
                                                        maxLines = 1,
                                                        fontWeight = FontWeight.Medium,
                                                        modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                                                    )
                                                    Text(
                                                        text = s.artists.joinToString { it.name },
                                                        fontSize = 18.5.sp,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        color = Color.White.copy(alpha = 0.35f),
                                                        modifier = Modifier.overlayEffect(),
                                                    )
                                                }
                                                FlamingoActionButtons(
                                                    liked = liked,
                                                    onLike = { SongLikeState.toggleLike(s) },
                                                    onMore = onMore,
                                                )
                                            }
                                        }
                                    }
                                }

                                FlamingoPage.Lyric, FlamingoPage.PlayingList -> {
                                    Column(Modifier.fillMaxSize().padding(top = topInset + 22.dp)) {
                                        FlamingoPlayingBar(
                                            song = current,
                                            liked = liked,
                                            onAlbum = { page = FlamingoPage.Album },
                                            onLike = { SongLikeState.toggleLike(current) },
                                            onMore = onMore,
                                            thumbnailModifier = shared,
                                        )
                                        if (shown == FlamingoPage.Lyric) {
                                            FlamingoLyricsPane(
                                                lines = lyricLines,
                                                position = lyricsPosition,
                                                onTap = { controlsTouch++ },
                                                modifier = Modifier.fillMaxWidth().weight(1f),
                                            )
                                        } else {
                                            FlamingoPlayingList(session = session, modifier = Modifier.fillMaxWidth().weight(1f))
                                        }
                                    }
                                }
                            }
                        }
                    }
                    // the grabber at the top, a tap closes the player as a drag down does
                    Box(
                        Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = topInset + 8.dp)
                            .size(width = 64.dp, height = 24.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onCollapse,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier
                                .overlayEffect()
                                .size(width = 32.dp, height = 4.5.dp)
                                .background(Color(0x4DFFFFFF), RoundedCornerShape(2.25.dp)),
                        )
                    }
                }
                AnimatedVisibility(
                    visible = controlsShown || page != FlamingoPage.Lyric,
                    enter = fadeIn(tween(300)),
                    exit = fadeOut(tween(300)),
                ) {
                    FlamingoPlayerControl(
                        session = session,
                        isPlaying = isPlaying,
                        position = livePosition,
                        page = page,
                        onLyrics = { page = if (page == FlamingoPage.Lyric) FlamingoPage.Album else FlamingoPage.Lyric },
                        onPlaylist = { page = if (page == FlamingoPage.PlayingList) FlamingoPage.Album else FlamingoPage.PlayingList },
                        onOutput = onOutput,
                        onInteract = { controlsTouch++ },
                    )
                }
            }
        }
    }
}

// the cover, pulled in from the edges while paused and let out again when the song plays
@Composable
private fun ColumnScope.FlamingoAlbum(
    artworkUrl: String?,
    isPlaying: Boolean,
    modifier: Modifier,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.80f)
            .padding(top = 20.dp, start = 15.dp, end = 15.dp, bottom = 33.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        val inset by animateFloatAsState(
            targetValue = if (isPlaying) 0f else 1f,
            animationSpec =
                if (isPlaying) {
                    spring(stiffness = 300f, dampingRatio = 1f, visibilityThreshold = 0.001f)
                } else {
                    tween(350, easing = EaseOutQuart)
                },
            label = "flamingoAlbumInset",
        )
        val edge = (7 + 27 * inset).dp
        AsyncImage(
            model = artworkUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier =
                Modifier
                    .padding(start = edge, end = edge, bottom = edge)
                    .aspectRatio(1f)
                    .then(modifier)
                    .shadow(elevation = 24.dp, shape = FlamingoAlbumShape, clip = true),
        )
    }
}

// the compact bar the cover shrinks into on the lyrics and queue pages
@Composable
private fun FlamingoPlayingBar(
    song: SongItem,
    liked: Boolean,
    onAlbum: () -> Unit,
    onLike: () -> Unit,
    onMore: () -> Unit,
    thumbnailModifier: Modifier,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 22.dp)
            .height(70.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = song.thumbnail,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier =
                Modifier
                    .size(69.dp)
                    .then(thumbnailModifier)
                    .shadow(elevation = 12.dp, shape = RoundedCornerShape(5.dp), clip = true)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onAlbum),
        )
        Column(Modifier.weight(1f).padding(start = 12.dp, end = 15.dp)) {
            Text(
                text = song.title,
                fontSize = 16.5.sp,
                lineHeight = 16.5.sp,
                maxLines = 1,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
            )
            Text(
                text = song.artists.joinToString { it.name },
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = Color.White.copy(alpha = 0.35f),
                modifier = Modifier.overlayEffect(),
            )
        }
        FlamingoActionButtons(liked = liked, onLike = onLike, onMore = onMore)
    }
}

@Composable
private fun FlamingoActionButtons(
    liked: Boolean,
    onLike: () -> Unit,
    onMore: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    Row(Modifier.overlayEffect(), verticalAlignment = Alignment.CenterVertically) {
        FlamingoIconButton(if (liked) Res.drawable.flamingo_np_favorited else Res.drawable.flamingo_np_favorite, 28.dp) {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onLike()
        }
        Spacer(Modifier.width(2.dp))
        FlamingoIconButton(Res.drawable.flamingo_np_more, 28.dp, onClick = onMore)
    }
}

@Composable
private fun FlamingoIconButton(
    icon: DrawableResource,
    size: Dp,
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .size(48.dp)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(targetState = icon, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "flamingoIcon") { shown ->
            Icon(painterResource(shown), contentDescription = null, tint = tint, modifier = Modifier.size(size))
        }
    }
}

@Composable
private fun FlamingoLyricsPane(
    lines: List<LyricsEntry>,
    position: LivePosition,
    onTap: () -> Unit,
    modifier: Modifier,
) {
    Box(
        modifier
            .padding(horizontal = 16.dp)
            .pointerInput(Unit) { detectTapGestures { onTap() } },
    ) {
        if (lines.isNotEmpty()) {
            ExpressiveLyrics(lines = lines, position = position, color = Color.White, baseSize = 30.sp, modifier = Modifier.fillMaxSize())
        }
    }
}

// the queue page: what plays next, with shuffle and repeat as Flamingo's round toggles
@Composable
private fun FlamingoPlayingList(
    session: PlayerSession,
    modifier: Modifier,
) {
    val queue by session.queueState.collectAsState()
    val shuffle by session.shuffleModeEnabled.collectAsState()
    val repeat by session.repeatMode.collectAsState()
    val upNext = queue.entries.drop(queue.currentIndex + 1)
    Column(modifier) {
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 30.dp)
                .padding(top = 10.dp)
                .height(65.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(Res.string.flamingo_queue_title), fontSize = 16.5.sp, fontWeight = FontWeight.Medium)
                Text(
                    pluralStringResource(Res.plurals.flamingo_queue_count, upNext.size, upNext.size),
                    fontSize = 15.sp,
                    color = Color.White.copy(alpha = 0.35f),
                    modifier = Modifier.padding(top = 2.dp).overlayEffect(),
                )
            }
            FlamingoToggle(Res.drawable.flamingo_np_shuffle, on = shuffle, onClick = session::toggleShuffle)
            Spacer(Modifier.width(10.dp))
            // Media3's repeat constants: 0 off, 1 one, 2 all
            FlamingoToggle(
                if (repeat == 1) Res.drawable.flamingo_np_repeatone else Res.drawable.flamingo_np_repeat,
                on = repeat != 0,
                onClick = session::cycleRepeatMode,
            )
        }
        LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
            itemsIndexed(upNext, key = { _, entry -> entry.key }) { _, entry ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .clickable { session.skipToQueueEntry(entry.key) }
                        .padding(horizontal = 30.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AsyncImage(
                        model = entry.song.thumbnail,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(48.dp).clip(RoundedCornerShape(4.dp)),
                    )
                    Column(Modifier.weight(1f).padding(start = 14.dp, end = 12.dp)) {
                        Text(
                            entry.song.title,
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(bottom = 1.dp),
                        )
                        Text(
                            entry.song.artists.joinToString { it.name },
                            fontSize = 11.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = Color.White.copy(alpha = 0.35f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FlamingoToggle(
    icon: DrawableResource,
    on: Boolean,
    onClick: () -> Unit,
) {
    val fill by animateFloatAsState(if (on) 0.9f else 0f, label = "flamingoToggle")
    Box(
        Modifier
            .size(36.dp)
            .background(Color.White.copy(alpha = fill), RoundedCornerShape(8.dp))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = if (on) Color.Black else Color.White, modifier = Modifier.size(20.dp))
    }
}

// the seek track, the times, the transport, the volume and the bottom row of buttons
@Composable
private fun FlamingoPlayerControl(
    session: PlayerSession,
    isPlaying: Boolean,
    position: LivePosition,
    page: FlamingoPage,
    onLyrics: () -> Unit,
    onPlaylist: () -> Unit,
    onOutput: (() -> Unit)?,
    onInteract: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val durationMs = rememberDurationMs()
    val volume by session.volume.collectAsState()
    Column(
        Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.437f)
            .padding(horizontal = 25.dp)
            .padding(bottom = 15.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        FlamingoSeek(position = position, durationMs = durationMs, onSeek = session::seekTo, onInteract = onInteract)
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                FlamingoTransportButton(Res.drawable.flamingo_np_rewind, 61.dp) {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    session.skipToPrevious()
                    onInteract()
                }
                Spacer(Modifier.width(43.dp))
                Box(
                    Modifier
                        .size(58.5.dp)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            session.togglePlayPause()
                            onInteract()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    AnimatedContent(
                        targetState = isPlaying,
                        transitionSpec = {
                            (scaleIn(initialScale = 0.3f) + fadeIn()) togetherWith (scaleOut(targetScale = 0.3f) + fadeOut())
                        },
                        label = "flamingoPlayPause",
                    ) { playing ->
                        Icon(
                            painterResource(if (playing) Res.drawable.flamingo_np_pause else Res.drawable.flamingo_np_play),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize().padding(if (playing) 10.dp else 9.dp),
                        )
                    }
                }
                Spacer(Modifier.width(43.dp))
                FlamingoTransportButton(Res.drawable.flamingo_np_fforward, 61.dp) {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    session.skipToNext()
                    onInteract()
                }
            }
        }
        FlamingoVolumeSlider(volume = volume, onVolumeChange = session::setVolume, onInteract = onInteract)
        Row(Modifier.overlayEffect().fillMaxWidth().graphicsLayer { alpha = 0.4f }, horizontalArrangement = Arrangement.Center) {
            FlamingoBottomButton(
                if (page ==
                    FlamingoPage.Lyric
                ) {
                    Res.drawable.flamingo_np_lyricson
                } else {
                    Res.drawable.flamingo_np_lyrics
                },
                onLyrics,
            )
            Spacer(Modifier.weight(0.1f))
            if (onOutput != null) {
                FlamingoBottomButton(Res.drawable.flamingo_np_airplay, onOutput)
                Spacer(Modifier.weight(0.1f))
            }
            FlamingoBottomButton(
                if (page ==
                    FlamingoPage.PlayingList
                ) {
                    Res.drawable.flamingo_np_queueon
                } else {
                    Res.drawable.flamingo_np_queue
                },
                onPlaylist,
            )
        }
    }
}

@Composable
private fun RowScope.FlamingoBottomButton(
    icon: DrawableResource,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .height(36.dp)
            .weight(1f)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(targetState = icon, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "flamingoBottom") { shown ->
            Icon(painterResource(shown), contentDescription = null, modifier = Modifier.size(32.dp))
        }
    }
}

@Composable
private fun FlamingoTransportButton(
    icon: DrawableResource,
    size: Dp,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(size)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = null, modifier = Modifier.fillMaxSize().padding(10.dp))
    }
}

// the seek track: 7dp, thickening to 11dp under the finger, with the time played and the time left under it
@Composable
private fun FlamingoSeek(
    position: LivePosition,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    onInteract: () -> Unit,
) {
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    val engage by animateFloatAsState(
        targetValue = if (dragging) 1f else 0f,
        animationSpec = tween(180, easing = FastOutSlowInEasing),
        label = "flamingoSeekEngage",
    )
    val playedMs = if (dragging) (dragFraction * durationMs).toLong() else position.positionMs()
    val fraction = if (durationMs > 0L) (playedMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    Box(
        Modifier
            .fillMaxWidth()
            .height(18.dp)
            .overlayEffect()
            .graphicsLayer { alpha = 0.85f }
            .pointerInput(durationMs) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        dragging = true
                        dragFraction = (offset.x / size.width).coerceIn(0f, 1f)
                        onInteract()
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        dragFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                        onInteract()
                    },
                    onDragEnd = {
                        onSeek((dragFraction * durationMs).toLong())
                        dragging = false
                    },
                    onDragCancel = { dragging = false },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        FlamingoTrack(activeFraction = fraction, height = 7.dp + 4.dp * engage)
    }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp, horizontal = 7.dp).height(22.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        val leftMs = (durationMs - playedMs).coerceAtLeast(0L)
        Text(
            formatDuration(playedMs),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.3.sp,
            color = Color.White.copy(alpha = 0.3f),
            modifier = Modifier.overlayEffect(),
        )
        Text(
            "-" + formatDuration(leftMs),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.3.sp,
            color = Color.White.copy(alpha = 0.3f),
            modifier = Modifier.overlayEffect(),
        )
    }
}

@Composable
private fun FlamingoVolumeSlider(
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    onInteract: () -> Unit,
) {
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(volume) }
    val engage by animateFloatAsState(if (dragging) 1f else 0f, tween(180, easing = FastOutSlowInEasing), label = "flamingoVolumeEngage")
    val shown by animateFloatAsState(if (dragging) dragValue else volume, label = "flamingoVolume")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .padding(horizontal = 8.dp)
                .padding(top = 4.dp, bottom = 2.5.dp)
                .overlayEffect()
                .graphicsLayer { alpha = 0.45f },
    ) {
        Icon(painterResource(Res.drawable.flamingo_np_volume), contentDescription = null, modifier = Modifier.size(20.dp))
        Box(
            Modifier
                .weight(1f)
                .height(24.dp)
                .padding(start = 1.5.dp, end = 5.dp)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            dragging = true
                            dragValue = (offset.x / size.width).coerceIn(0f, 1f)
                            onVolumeChange(dragValue)
                            onInteract()
                        },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            dragValue = (change.position.x / size.width).coerceIn(0f, 1f)
                            onVolumeChange(dragValue)
                        },
                        onDragEnd = { dragging = false },
                        onDragCancel = { dragging = false },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            FlamingoTrack(activeFraction = shown.coerceIn(0f, 1f), height = 7.dp + 4.dp * engage)
        }
        Icon(painterResource(Res.drawable.flamingo_np_volume_full), contentDescription = null, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun FlamingoTrack(
    activeFraction: Float,
    height: Dp,
) {
    Canvas(Modifier.fillMaxWidth().height(height)) {
        val stroke = size.height
        val corner = CornerRadius(stroke / 2f)
        drawRoundRect(color = FlamingoTrackBacking, size = Size(size.width, stroke), cornerRadius = corner)
        drawRoundRect(color = Color.White.copy(alpha = 0.35f), size = Size(size.width, stroke), cornerRadius = corner)
        val width = abs(size.width * activeFraction)
        if (width > 0f) drawRoundRect(color = Color.White, size = Size(width, stroke), cornerRadius = corner)
    }
}

/**
 * Flamingo's floating light: the cover shrunk to a few pixels, its colour pushed up, then blurred and stretched over
 * the whole screen; a darker layer over it except on the lyrics page.
 */
@Composable
private fun FlamingoFloatingLight(
    artworkUrl: String?,
    dimmed: Boolean,
    modifier: Modifier,
) {
    val context = LocalPlatformContext.current
    val request =
        remember(artworkUrl) {
            ImageRequest
                .Builder(context)
                .data(artworkUrl)
                .size(32)
                .build()
        }
    val saturated = remember { ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(3f) }) }
    val dim by animateFloatAsState(if (dimmed) 0.618f else 0f, tween(300, easing = FastOutSlowInEasing), label = "flamingoDim")
    Box(modifier.clipToBounds().background(Color.Black)) {
        AsyncImage(
            model = request,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            colorFilter = saturated,
            modifier =
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = 1.2f
                        scaleY = 1.2f
                    }.blur(48.dp),
        )
        // the overlay tint and the darkening the resolver painted onto the shrunk cover
        Box(Modifier.fillMaxSize().background(Color(0x40000000)))
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = dim }.background(Color(0x33000000)))
    }
}

/** Flamingo's text and icon treatment: drawn in a layer added onto what's under it, so they glow over the cover. */
internal fun Modifier.overlayEffect(): Modifier =
    drawWithCache {
        val paint = Paint().apply { blendMode = BlendMode.Plus }
        val rect = Rect(0f, 0f, size.width, size.height)
        onDrawWithContent {
            val canvas = drawContext.canvas
            canvas.saveLayer(rect, paint)
            drawContent()
            canvas.restore()
        }
    }
