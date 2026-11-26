// Ported from Mochy Music by realidkroo. Licensed under GPL-3.0.

package com.metrolist.shared.ui.player.mochy

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.Dp
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.runtime.mutableFloatStateOf
import com.metrolist.shared.cast.CastState
import com.metrolist.shared.generated.resources.Res
import com.metrolist.shared.generated.resources.album_art
import com.metrolist.shared.settings.PlayerCoverStyle
import com.metrolist.shared.ui.components.BlurredArtworkBackground
import com.metrolist.shared.ui.components.CrossfadeArtwork
import com.metrolist.shared.ui.components.lyrics.LyricsOffsetEditor
import com.metrolist.shared.ui.components.rememberTrackMarquee
import com.metrolist.shared.ui.haptics.HapticEvent
import com.metrolist.shared.ui.haptics.rememberMetrolistHaptics
import com.metrolist.shared.ui.mochy.LikeBurstState
import com.metrolist.shared.ui.mochy.LikeSlot
import com.metrolist.shared.ui.player.LocalPlayerContentActive
import com.metrolist.shared.ui.player.LocalPlayerPlatform
import com.metrolist.shared.ui.player.LyricsViewWithPosition
import com.metrolist.shared.ui.player.PlayerArtistLine
import com.metrolist.shared.ui.player.PlayerProgressSlider
import com.metrolist.shared.ui.player.QueueScreen
import com.metrolist.shared.ui.player.QueueSession
import com.metrolist.shared.ui.player.artworkSeekGestures
import com.metrolist.shared.ui.player.artworkSwipe
import com.metrolist.shared.ui.player.mochy.turntable.TurntableStage
import com.metrolist.shared.viewmodels.PlayerViewModel
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

// Exact Mochy FlagshipLandscape proportions and visual tokens.
private const val SQUARE_HEIGHT_SHARE = 0.74f
private const val SQUARE_WIDTH_SHARE = 0.42f
private val SquareShape = RoundedCornerShape(22.dp)
private val PanelGap = 40.dp
private val PanelGutter = 26.dp
private val ButtonSize = 34.dp
private val SideButtonsRoom = ButtonSize + 30.dp
private val EvenShade = Color.Black.copy(alpha = 0.38f)

// the lyrics page tucks the seek bar and transport away after this long without a touch
private const val LANDSCAPE_TRANSPORT_IDLE_MS = 3_500L
private const val RETRACT_MS = 420
// the title and artist rest just above the seek bar and glide this far down and to the left as the controls retract
private val LandscapeCoverGutter = 20.dp
private val RetractEasing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)

private enum class FlagshipLandscapePanel {
    INFO,
    LYRICS,
    QUEUE,
}

/**
 * Landscape layout for Mochy's Flagship player: rounded square cover over a full-bleed blurred
 * artwork backdrop with an even dark shade on the left, title/lyrics/queue swapping cleanly inside
 * the right panel above the anchored Flagship seek track, transport controls, and bottom bar, plus
 * stacked like and menu chevron buttons at the bottom-end corner.
 */
@Composable
internal fun FlagshipLandscapePlayer(
    viewModel: PlayerViewModel,
    queueSession: QueueSession,
    artworkOverlay: @Composable (Modifier) -> Unit,
    currentSong: PlayerViewModel.SongUiModel?,
    crossfadeNextArtworkUrl: String?,
    artworkCrossfadeProgress: () -> Float,
    isPlaying: Boolean,
    isPlaybackLoading: Boolean,
    duration: Long,
    isLiked: Boolean,
    uiState: PlayerViewModel.PlayerUiState,
    isQueueVisible: Boolean,
    isMenuOpen: Boolean,
    showLyricsOffsetEditor: Boolean,
    onLyricsOffsetChange: (Int) -> Unit,
    onDoneLyricsOffset: () -> Unit,
    likeBurstState: LikeBurstState,
    volume: FlagshipVolume?,
    castState: CastState,
    isListenTogetherGuest: Boolean,
    artworkSwipeEnabled: Boolean,
    artworkSwipeSensitivity: Float,
    showVideoSurface: Boolean,
    onToggleFullscreen: (() -> Unit)?,
    onArtworkDoubleTap: ((Offset) -> Unit)?,
    onSongInfo: () -> Unit,
    onToggleLike: () -> Unit,
    onShowPlayerMenu: () -> Unit,
    onToggleLyrics: () -> Unit,
    onToggleQueue: () -> Unit,
    onCloseQueue: () -> Unit,
    onOutput: () -> Unit,
    onShowSleepTimer: () -> Unit,
    onNavigateToArtist: (String) -> Unit,
    onNavigateToAlbum: (String) -> Unit,
    onNavigateToListenTogether: () -> Unit,
    modifier: Modifier = Modifier,
    turntableMode: Boolean = false,
    coverStyle: PlayerCoverStyle = PlayerCoverStyle.SQUARED,
    canSeek: Boolean = !isListenTogetherGuest,
    onSeek: (Long) -> Unit = {},
    positionProvider: () -> Long = { 0L },
) {
    val platform = LocalPlayerPlatform.current
    val haptics = rememberMetrolistHaptics()
    val flagshipFont = flagshipFontFamily()
    val direction = LocalLayoutDirection.current
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    val startInset = insets.calculateStartPadding(direction)
    val endInset = insets.calculateEndPadding(direction)
    val bottomInset = insets.calculateBottomPadding()
    val blurRadiusPx = with(LocalDensity.current) { 120.dp.toPx() }
    val contentColor = Color.White

    val activePanel =
        when {
            uiState.isLyricsVisible -> FlagshipLandscapePanel.LYRICS
            isQueueVisible -> FlagshipLandscapePanel.QUEUE
            else -> FlagshipLandscapePanel.INFO
        }

    var transportRevealed by remember { mutableStateOf(true) }
    var touchTick by remember { mutableLongStateOf(0L) }
    val lyricsShowing = uiState.isLyricsVisible
    LaunchedEffect(lyricsShowing, touchTick) {
        transportRevealed = true
        if (lyricsShowing) {
            delay(LANDSCAPE_TRANSPORT_IDLE_MS)
            transportRevealed = false
        }
    }
    val transportShown = transportRevealed || !lyricsShowing
    // 0 with the controls up, 1 with them tucked away on the lyrics page: the title, the frost and the band follow it
    // the seek bar and transport's measured height: the title rests just above them and drops by this as they go
    var controlsHeightPx by remember { mutableFloatStateOf(0f) }
    val controlsRetract by animateFloatAsState(
        targetValue = if (lyricsShowing && !transportShown) 1f else 0f,
        animationSpec = tween(RETRACT_MS, easing = RetractEasing),
        label = "flagshipLandscapeRetract",
    )

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val squareSide = minOf(maxHeight * SQUARE_HEIGHT_SHARE, maxWidth * SQUARE_WIDTH_SHARE)
        val squareStart = startInset + (maxHeight - squareSide) / 2
        val coverWidth = squareStart + squareSide + PanelGap
        val compactHeight = maxHeight < 420.dp
        val coverFrame =
            when (coverStyle) {
                PlayerCoverStyle.EDGE_TO_EDGE -> {
                    Modifier.size(width = coverWidth, height = maxHeight)
                }

                PlayerCoverStyle.SQUARED -> {
                    Modifier
                        .padding(start = squareStart)
                        .size(squareSide)
                        .shadow(24.dp, SquareShape)
                        .clip(SquareShape)
                }

                else -> {
                    Modifier.padding(start = squareStart).size(squareSide)
                }
            }

        currentSong?.displayThumbnail?.let { thumbUrl ->
            BlurredArtworkBackground(
                url = thumbUrl,
                modifier = Modifier.fillMaxSize(),
                blurRadiusPx = blurRadiusPx,
                pendingScrimAlpha = 0.55f,
            )
        }
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(EvenShade),
        )

        if (turntableMode && !showVideoSurface) {
            TurntableStage(
                artworkUrl = currentSong?.displayThumbnail,
                nextArtworkUrl = crossfadeNextArtworkUrl,
                crossfadeProgressProvider = artworkCrossfadeProgress,
                contentDescription = stringResource(Res.string.album_art),
                positionProvider = positionProvider,
                duration = duration,
                songId = currentSong?.id,
                isPlaying = isPlaying,
                spinActive = LocalPlayerContentActive.current,
                canSeek = canSeek,
                swipeEnabled = artworkSwipeEnabled,
                swipeSensitivity = artworkSwipeSensitivity,
                onPrevious = viewModel::previous,
                onNext = viewModel::next,
                onSeek = onSeek,
                artworkOverlay = artworkOverlay.takeUnless { uiState.isLyricsVisible },
                modifier =
                    Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = startInset)
                        .size(width = coverWidth - 16.dp, height = maxHeight)
                        .padding(bottom = bottomInset),
            )
        } else {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.CenterStart)
                        .then(coverFrame)
                        .artworkSwipe(
                            enabled = artworkSwipeEnabled,
                            sensitivity = artworkSwipeSensitivity,
                            onPrevious = viewModel::previous,
                            onNext = viewModel::next,
                        ).artworkSeekGestures(
                            seekEnabled = false,
                            stepMs = 0L,
                            onTap = null,
                            tapLabel = null,
                            onDoubleTap = onArtworkDoubleTap,
                            onSeek = {},
                        ),
            ) {
                if (showVideoSurface) {
                    platform.MusicVideoSurface(
                        cornerRadius = 22.dp,
                        fillContainer = true,
                        showControls = true,
                        useSurfaceView = false,
                        onFullscreenClick = onToggleFullscreen,
                        onPiPClick = {
                            platform.enterPictureInPicture(
                                isPlaying = isPlaying,
                                hasVideo = true,
                            )
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                } else if (coverStyle.isDiscLook) {
                    FlagshipDiscCover(
                        style = coverStyle,
                        artworkUrl = currentSong?.displayThumbnail,
                        nextArtworkUrl = crossfadeNextArtworkUrl,
                        crossfadeProgressProvider = artworkCrossfadeProgress,
                        isPlaying = isPlaying,
                        spinActive = LocalPlayerContentActive.current,
                        contentDescription = stringResource(Res.string.album_art),
                        artworkOverlay = artworkOverlay.takeUnless { uiState.isLyricsVisible },
                        discTitle = currentSong?.title.orEmpty(),
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    CrossfadeArtwork(
                        animate = LocalPlayerContentActive.current,
                        currentUrl = currentSong?.displayThumbnail,
                        nextUrl = crossfadeNextArtworkUrl,
                        progress = artworkCrossfadeProgress,
                        contentDescription = stringResource(Res.string.album_art),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                    if (!uiState.isLyricsVisible) {
                        artworkOverlay(Modifier.fillMaxSize())
                    }
                }
            }
        }

        val coverThumb = currentSong?.displayThumbnail
        if (coverThumb != null && !turntableMode && !showVideoSurface && coverStyle == PlayerCoverStyle.EDGE_TO_EDGE) {
            // the cover mirrored past its right seam, the frost on lyrics or queue, then the lyrics band
            LandscapeCoverReflection(url = coverThumb, coverWidth = coverWidth, modifier = Modifier.align(Alignment.TopStart))
            if (uiState.isLyricsVisible || isQueueVisible) {
                LandscapeCoverFrost(url = coverThumb, coverWidth = coverWidth, retract = { controlsRetract }, modifier = Modifier.align(Alignment.TopStart))
            }
            if (uiState.isLyricsVisible) {
                LandscapeLyricsBand(url = coverThumb, coverWidth = coverWidth, retract = { controlsRetract }, modifier = Modifier.align(Alignment.BottomStart))
            }
        }

        FlagshipCoverButtons(
            liked = isLiked,
            menuOpen = isMenuOpen,
            onLike = {
                haptics.toggle(!isLiked)
                likeBurstState.likeFromButton(
                    slot = LikeSlot.PLAYER,
                    liked = isLiked,
                    toggleLike = onToggleLike,
                )
            },
            onMenu = {
                haptics.perform(HapticEvent.PRESS)
                onShowPlayerMenu()
            },
            contentColor = contentColor,
            likeBurstState = likeBurstState,
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = endInset + 16.dp, bottom = bottomInset + 18.dp),
        )

        Column(
            modifier =
                Modifier
                    .fillMaxHeight()
                    .padding(start = coverWidth, end = endInset + SideButtonsRoom)
                    .padding(bottom = bottomInset)
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            touchTick++
                        }
                    },
        ) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                AnimatedContent(
                    targetState = activePanel,
                    transitionSpec = {
                        (fadeIn(tween(220)) + slideInVertically(tween(240)) { it / 12 }) togetherWith
                            (fadeOut(tween(160)) + slideOutVertically(tween(180)) { -it / 16 })
                    },
                    label = "flagshipLandscapePanel",
                    modifier = Modifier.fillMaxSize(),
                ) { panel ->
                    when (panel) {
                        FlagshipLandscapePanel.INFO -> {
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = PanelGutter)
                                        .padding(top = 18.dp),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text(
                                        text = currentSong?.title.orEmpty(),
                                        style =
                                            MaterialTheme.typography.titleLarge.copy(
                                                fontFamily = flagshipFont,
                                                fontSize = 21.sp,
                                                lineHeight = 26.sp,
                                            ),
                                        fontWeight = FontWeight.Bold,
                                        color = contentColor,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier =
                                            Modifier
                                                .clickable(enabled = currentSong != null, onClick = onSongInfo)
                                                .then(
                                                    currentSong?.let { song ->
                                                        rememberTrackMarquee(
                                                            trackId = song.id,
                                                            enabled = LocalPlayerContentActive.current,
                                                            initialDelayMillis = 3000L,
                                                        )
                                                    } ?: Modifier,
                                                ),
                                    )
                                    PlayerArtistLine(
                                        artists = currentSong?.artists,
                                        onNavigateToArtist = onNavigateToArtist,
                                        iterations = if (LocalPlayerContentActive.current) 1 else 0,
                                        style =
                                            MaterialTheme.typography.titleMedium.copy(
                                                fontFamily = flagshipFont,
                                                fontSize = 15.sp,
                                            ),
                                        color = contentColor.copy(alpha = 0.72f),
                                    )
                                }
                            }
                        }

                        FlagshipLandscapePanel.LYRICS -> {
                            Column(
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .padding(top = 20.dp, start = 8.dp, end = 8.dp),
                            ) {
                                LyricsViewWithPosition(
                                    viewModel = viewModel,
                                    lyrics = uiState.lyrics,
                                    songwriters = uiState.lyricsSongwriters,
                                    agents = uiState.lyricsAgents,
                                    provider = uiState.lyricsProvider,
                                    timingOffsetMs = uiState.lyricsOffsetMs,
                                    onSeek = viewModel::seekToPosition,
                                    isLoading = uiState.isLyricsLoading,
                                    textColor = contentColor,
                                    isCasting = castState.isConnected,
                                    isBuffering = castState.isBuffering,
                                    modifier = Modifier.weight(1f).fillMaxWidth(),
                                )
                                AnimatedVisibility(visible = showLyricsOffsetEditor) {
                                    LyricsOffsetEditor(
                                        offsetMs = uiState.lyricsOffsetMs,
                                        onOffsetChange = onLyricsOffsetChange,
                                        onDone = onDoneLyricsOffset,
                                        contentColor = contentColor,
                                    )
                                }
                            }
                        }

                        FlagshipLandscapePanel.QUEUE -> {
                            QueueScreen(
                                session = queueSession,
                                viewModel = viewModel,
                                onClose = onCloseQueue,
                                onShowSleepTimer = onShowSleepTimer,
                                onNavigateToAlbum = { albumId ->
                                    onCloseQueue()
                                    onNavigateToAlbum(albumId)
                                },
                                onNavigateToArtist = { artistId ->
                                    onCloseQueue()
                                    onNavigateToArtist(artistId)
                                },
                                onNavigateToListenTogether = {
                                    onCloseQueue()
                                    onNavigateToListenTogether()
                                },
                                modifier = Modifier.fillMaxSize().padding(top = 20.dp),
                                backHandlerEnabled = false,
                                handleDismissalBack = false,
                            )
                        }
                    }
                }
            }

            // the reference tucks the seek bar and transport down over 420 ms on an ease-in-out cubic
            AnimatedVisibility(
                visible = transportShown,
                enter =
                    expandVertically(tween(RETRACT_MS, easing = RetractEasing)) +
                        fadeIn(tween(RETRACT_MS, easing = RetractEasing)),
                exit =
                    shrinkVertically(tween(RETRACT_MS, easing = RetractEasing)) +
                        fadeOut(tween(RETRACT_MS, easing = RetractEasing)),
            ) {
                Column(modifier = Modifier.onSizeChanged { if (transportShown) controlsHeightPx = it.height.toFloat() }) {
                    Column(modifier = Modifier.padding(horizontal = PanelGutter - 12.dp)) {
                        PlayerProgressSlider(
                            viewModel = viewModel,
                            duration = duration,
                            isPlaying = isPlaying,
                            textBackgroundColor = contentColor,
                            canSeek = !isListenTogetherGuest,
                            isCasting = castState.isConnected,
                            songId = currentSong?.id,
                            isLive = uiState.isLive,
                            mochyTrack = true,
                            timerTextStyle = MaterialTheme.typography.labelMedium.copy(fontFamily = flagshipFont),
                        )
                    }

                    if (!isListenTogetherGuest) {
                        FlagshipTransport(
                            playing = isPlaying,
                            loading = isPlaybackLoading,
                            onPlayPause = viewModel::togglePlayPause,
                            onPrevious = viewModel::previous,
                            onNext = viewModel::next,
                            compact = compactHeight,
                            contentColor = contentColor,
                            modifier = Modifier.padding(vertical = 6.dp),
                        )
                    } else {
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }

            FlagshipBottomControls(
                volume = volume,
                lyricsVisible = uiState.isLyricsVisible,
                queueVisible = isQueueVisible,
                outputName = castState.device?.name?.takeIf { castState.isConnected },
                contentColor = contentColor,
                onOutput = onOutput,
                onLyrics = onToggleLyrics,
                onQueue = onToggleQueue,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }

        // on the lyrics page the title and artist sit above the seek bar; as the controls tuck away they glide down and left
        if (uiState.isLyricsVisible) {
            // the title's size eases from the panel's to the compact size the reference uses on the lyrics page
            val r = controlsRetract
            val titleSize = 22f - 5f * r
            val artistSize = 15f - 2f * r
            val controlsHeight = with(LocalDensity.current) { controlsHeightPx.toDp() }
            Column(
                modifier =
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = coverWidth + PanelGutter, bottom = bottomInset + 8.dp + controlsHeight)
                        .padding(end = endInset + SideButtonsRoom)
                        .fillMaxWidth()
                        .graphicsLayer {
                            val r = controlsRetract
                            // the reference's drop: the controls' height less 6dp, and the bottom-left inset it settles at
                            translationX = -(coverWidth + PanelGutter - startInset - LandscapeCoverGutter).toPx() * r
                            translationY = (controlsHeightPx - 6.dp.toPx()) * r
                        },
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = currentSong?.title.orEmpty(),
                    style =
                        MaterialTheme.typography.titleLarge.copy(
                            fontFamily = flagshipFont,
                            fontSize = titleSize.sp,
                            lineHeight = (titleSize + 4f).sp,
                        ),
                    fontWeight = FontWeight.Bold,
                    color = contentColor,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier =
                        Modifier
                            .clickable(enabled = currentSong != null, onClick = onSongInfo)
                            .then(
                                currentSong?.let { song ->
                                    rememberTrackMarquee(
                                        trackId = song.id,
                                        enabled = LocalPlayerContentActive.current,
                                        initialDelayMillis = 3000L,
                                    )
                                } ?: Modifier,
                            ),
                )
                PlayerArtistLine(
                    artists = currentSong?.artists,
                    onNavigateToArtist = onNavigateToArtist,
                    iterations = if (LocalPlayerContentActive.current) 1 else 0,
                    style =
                        MaterialTheme.typography.titleMedium.copy(
                            fontFamily = flagshipFont,
                            fontSize = artistSize.sp,
                        ),
                    color = contentColor.copy(alpha = 0.72f),
                )
            }
        }
    }
}

// the constants and masks below are the reference's landscape values: the cover's mirror, its frost and the lyrics band
private const val REFLECTION_STRENGTH = 0.9f
private const val REFLECTION_FADE_END = 0.85f
private val FROST_MASK =
    Brush.horizontalGradient(
        0f to Color.Transparent,
        0.06f to Color.Black,
        0.76f to Color.Black,
        0.84f to Color.Black.copy(alpha = 0.72f),
        0.92f to Color.Black.copy(alpha = 0.28f),
        1f to Color.Transparent,
    )

// a horizontal fade that holds full strength until [fadeStart] and eases to nothing at the right edge
private fun smoothFadeRight(fadeStart: Float): Brush =
    Brush.horizontalGradient(
        *Array(9) { i ->
            val t = i / 8f
            (fadeStart + (1f - fadeStart) * t) to Color.Black.copy(alpha = 1f - t * t * (3f - 2f * t))
        },
    )

// draws what's inside only where [mask] is opaque, on its own layer so the mask cuts the content, not what's behind
private fun Modifier.maskedBy(mask: Brush): Modifier =
    this
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            drawRect(mask, blendMode = BlendMode.DstIn)
        }

// the cover flipped across its right seam, fading into the backdrop past it
@Composable
private fun LandscapeCoverReflection(url: String, coverWidth: Dp, modifier: Modifier) {
    val blurPx = with(LocalDensity.current) { 40.dp.toPx() }
    Box(
        modifier =
            modifier
                .offset { IntOffset(coverWidth.roundToPx(), 0) }
                .width(coverWidth * REFLECTION_FADE_END)
                .fillMaxHeight()
                .clipToBounds()
                .maskedBy(
                    Brush.horizontalGradient(
                        *Array(9) { i ->
                            val t = i / 8f
                            (REFLECTION_FADE_END * t) to Color.Black.copy(alpha = REFLECTION_STRENGTH * (1f - t * t * (3f - 2f * t)))
                        },
                    ),
                ),
    ) {
        Box(
            modifier =
                Modifier
                    .width(coverWidth)
                    .fillMaxHeight()
                    .graphicsLayer { scaleX = -1f },
        ) {
            BlurredArtworkBackground(url = url, modifier = Modifier.fillMaxSize(), blurRadiusPx = blurPx, pendingScrimAlpha = 0f)
        }
    }
}

// a blurred frost over the cover's right edge while lyrics or the queue are open
@Composable
private fun LandscapeCoverFrost(url: String, coverWidth: Dp, retract: () -> Float, modifier: Modifier) {
    val blurPx = with(LocalDensity.current) { 40.dp.toPx() }
    Box(
        modifier =
            modifier
                .offset { IntOffset((coverWidth * 0.64f).roundToPx(), 0) }
                .width(coverWidth * 0.5f)
                .fillMaxHeight()
                .clipToBounds()
                .maskedBy(FROST_MASK)
                .graphicsLayer { alpha = 1f - retract() },
    ) {
        BlurredArtworkBackground(url = url, modifier = Modifier.fillMaxSize(), blurRadiusPx = blurPx, pendingScrimAlpha = 0f)
    }
}

// the lower-left band the lyrics sit on: a blurred strip under a dark shade that fades toward the cover's middle
@Composable
private fun LandscapeLyricsBand(url: String, coverWidth: Dp, retract: () -> Float, modifier: Modifier) {
    val blurPx = 30f
    Box(
        modifier =
            modifier
                .width(coverWidth * 1.10f)
                .fillMaxHeight(0.54f)
                .clipToBounds()
                .maskedBy(smoothFadeRight(0.58f))
                .graphicsLayer { alpha = 1f - retract() },
    ) {
        BlurredArtworkBackground(url = url, modifier = Modifier.fillMaxSize(), blurRadiusPx = blurPx, pendingScrimAlpha = 0f)
    }
    Box(
        modifier =
            modifier
                .width(coverWidth * 1.22f)
                .fillMaxHeight(0.64f)
                .background(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.62f), 1f to Color.Transparent))
                .maskedBy(smoothFadeRight(0.56f))
                .graphicsLayer { alpha = 1f - retract() },
    )
    // the lower shade the title and artist settle onto as the controls retract
    Box(
        modifier =
            modifier
                .width(coverWidth * 1.22f)
                .fillMaxHeight(0.64f)
                .background(Brush.verticalGradient(0.48f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.58f)))
                .maskedBy(smoothFadeRight(0.56f))
                .graphicsLayer { alpha = retract() },
    )
}
