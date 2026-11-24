// Ported from Mochy Music by realidkroo. Licensed under GPL-3.0.

package com.metrolist.shared.ui.player.mochy

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrolist.shared.cast.CastState
import com.metrolist.shared.generated.resources.Res
import com.metrolist.shared.generated.resources.album_art
import com.metrolist.shared.model.SongItem
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
import com.metrolist.shared.viewmodels.PlayerViewModel
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
    currentSong: SongItem?,
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
    onToggleFullscreen: () -> Unit,
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

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val squareSide = minOf(maxHeight * SQUARE_HEIGHT_SHARE, maxWidth * SQUARE_WIDTH_SHARE)
        val squareStart = startInset + (maxHeight - squareSide) / 2
        val coverWidth = squareStart + squareSide + PanelGap
        val compactHeight = maxHeight < 420.dp

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

        Box(
            modifier =
                Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = squareStart)
                    .size(squareSide)
                    .shadow(24.dp, SquareShape)
                    .clip(SquareShape)
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
                    .padding(bottom = bottomInset),
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
    }
}
