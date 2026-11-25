// Ported and adapted from Mochy Music by realidkroo. Licensed under GPL-3.0.

package com.metrolist.desktop.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.metrolist.desktop.state.app.UiState
import com.metrolist.desktop.state.content.ArtistState
import com.metrolist.desktop.state.playback.ListenTogetherState
import com.metrolist.desktop.state.playback.LyricsState
import com.metrolist.desktop.state.playback.PlayerState
import com.metrolist.shared.generated.resources.Res
import com.metrolist.shared.generated.resources.album_art
import com.metrolist.shared.generated.resources.not_playing
import com.metrolist.shared.model.SongItem
import com.metrolist.shared.settings.PlayerCoverStyle
import com.metrolist.shared.settings.Settings
import com.metrolist.shared.ui.components.PlayerConnectedButtonGroup
import com.metrolist.shared.ui.player.PlayerArtistLine
import com.metrolist.shared.ui.player.mochy.FLAGSHIP_DISC_CENTER
import com.metrolist.shared.ui.player.mochy.FLAGSHIP_DISC_DIAMETER
import com.metrolist.shared.ui.player.mochy.FlagshipTransport
import com.metrolist.shared.ui.player.mochy.coverFadeToBottom
import com.metrolist.shared.ui.player.mochy.flagshipFontFamily
import com.metrolist.shared.ui.player.mochy.turntable.VinylDisc
import org.jetbrains.compose.resources.stringResource

// The desktop fullscreen player's wide layout when the Mochy Flagship player is selected:
// artwork on the left, title, artist, seek, transport and the lyrics/queue group on the right.
@Composable
internal fun FlagshipFullscreenContent(
    currentTrack: SongItem?,
    isPlaying: Boolean,
    scale: Float,
    toggleWindowFullscreen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isListenTogetherGuest = ListenTogetherState.isListenTogetherGuest()
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FlagshipCover(modifier = Modifier.weight(1f).fillMaxHeight())

        Column(
            modifier = Modifier.weight(1f).fillMaxHeight().padding(start = 40.dp * scale),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = currentTrack?.title ?: stringResource(Res.string.not_playing),
                style = MaterialTheme.typography.headlineLarge,
                fontFamily = flagshipFontFamily(),
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(4.dp))
            PlayerArtistLine(
                artists = currentTrack?.artists,
                onNavigateToArtist = { id -> ArtistState.fetchArtistData(id) },
                fallbackText = "",
                iterations = 1,
                initialDelayMillis = 2000,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(32.dp * scale))
            PlayerProgressSection(flagshipTrack = true)

            Spacer(Modifier.height(20.dp * scale))
            FlagshipTransport(
                playing = isPlaying,
                loading = PlayerState.isPlaybackLoading,
                onPlayPause = { PlayerState.togglePlay() },
                onPrevious = { if (PlayerState.canSkipPrevious) PlayerState.skipPrevious() },
                onNext = { PlayerState.skipNext() },
                modifier = Modifier.fillMaxWidth(),
                contentColor = Color.White,
            )

            if (!isListenTogetherGuest) {
                Spacer(Modifier.height(24.dp * scale))
                PlayerConnectedButtonGroup(
                    modifier = Modifier.fillMaxWidth(),
                    sectioned = true,
                    touchTarget = 40.dp,
                    onShuffleClick = { PlayerState.toggleShuffle() },
                    isShuffled = PlayerState.shuffleEnabled,
                    onRepeatClick = { PlayerState.cycleRepeatMode() },
                    repeatMode = PlayerState.repeatMode,
                    volume = PlayerState.volume,
                    onVolumeChange = { PlayerState.setVolumeLevel(it) },
                    onQueueClick = UiState::togglePersistentPlayerDrawer,
                    onLyricsClick = {
                        LyricsState.toggleLyricsDrawer()
                    },
                    isLyricsActive = UiState.showLyricsDrawer,
                    isQueueActive = UiState.showPersistentPlayerDrawer,
                    onVideoClick =
                        if (shouldShowVideoButton()) {
                            PlayerState::toggleFullscreenVideo
                        } else {
                            null
                        },
                    isVideoActive = false,
                    onSubtitleClick =
                        if (PlayerState.isVideoActive) {
                            {
                                PlayerState.isSubtitleActive = !PlayerState.isSubtitleActive
                            }
                        } else {
                            null
                        },
                    isSubtitleActive = PlayerState.isSubtitleActive,
                    onCastClick = null,
                    isCasting = false,
                    onFullscreenClick = toggleWindowFullscreen,
                    isFullscreen = PlayerState.isFullscreen || UiState.isNativeFullscreen,
                )
            }
        }
    }
}

// The cover follows Settings.playerCoverStyle, the same three looks as the mobile flagship.
@Composable
private fun FlagshipCover(modifier: Modifier) {
    val coverShape = RoundedCornerShape(22.dp)
    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        val side = minOf(maxWidth, maxHeight)
        when (Settings.shared.playerCoverStyle) {
            PlayerCoverStyle.SQUARED -> {
                AsyncImage(
                    url = PlayerState.currentArtworkUrl ?: "",
                    artworkPreview = PlayerState.artworkPreview,
                    modifier =
                        Modifier
                            .size(side)
                            .clip(coverShape)
                            .shadow(24.dp, coverShape),
                    contentScale = ContentScale.Crop,
                )
            }

            PlayerCoverStyle.EDGE_TO_EDGE -> {
                AsyncImage(
                    url = PlayerState.currentArtworkUrl ?: "",
                    artworkPreview = PlayerState.artworkPreview,
                    modifier = Modifier.fillMaxSize().coverFadeToBottom(),
                    contentScale = ContentScale.Crop,
                )
            }

            PlayerCoverStyle.DISC -> {
                VinylDisc(
                    artworkUrl = PlayerState.currentArtworkUrl,
                    nextArtworkUrl = null,
                    crossfadeProgressProvider = { 0f },
                    isPlaying = PlayerState.isPlaying,
                    spinActive = PlayerState.isPlaying,
                    contentDescription = stringResource(Res.string.album_art),
                    discDiameter = FLAGSHIP_DISC_DIAMETER,
                    discCenterX = FLAGSHIP_DISC_CENTER,
                    discCenterYByWidth = FLAGSHIP_DISC_CENTER,
                    modifier = Modifier.size(side),
                )
            }
        }
    }
}
