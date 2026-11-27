// Ported from Mochy Music by realidkroo (github.com/realidkroo/mochy-music). Licensed under GPL-3.0.

// The phone's alternate player styles (Poster, Timeline, Lyrics stage) on the desktop's expanded player: the same
// composables, driven by the desktop's own player instead of a player session.

package com.metrolist.desktop.ui.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.metrolist.desktop.state.app.UiState
import com.metrolist.desktop.state.playback.LyricsState
import com.metrolist.desktop.state.playback.PlayerState
import com.metrolist.shared.model.RepeatMode
import com.metrolist.shared.model.SongItem
import com.metrolist.shared.settings.PlayerType
import com.metrolist.shared.ui.player.mochy.MochyStylePicker
import com.metrolist.shared.ui.player.mochy.styles.AlternatePlayerStyle
import com.metrolist.shared.ui.player.mochy.styles.AlternateStyle
import com.metrolist.shared.ui.player.mochy.styles.LocalStyleLyricLines
import com.metrolist.shared.ui.player.mochy.styles.LocalStylePlayback
import com.metrolist.shared.ui.player.mochy.styles.LocalStyleTrack
import com.metrolist.shared.ui.player.mochy.styles.StyleActions
import com.metrolist.shared.ui.player.mochy.styles.StylePlayback
import com.metrolist.shared.ui.player.mochy.styles.StyleTrack
import kotlinx.coroutines.flow.map

/** The alternate style [type] stands for, or null for a player the desktop draws itself. */
internal fun alternateStyleOf(type: PlayerType): AlternateStyle? =
    when (type) {
        PlayerType.POSTER -> AlternateStyle.POSTER
        PlayerType.TIMELINE -> AlternateStyle.TIMELINE
        PlayerType.LYRICS_STAGE -> AlternateStyle.LYRICS
        else -> null
    }

@Composable
internal fun DesktopAlternatePlayerStyle(
    style: AlternateStyle,
    currentTrack: SongItem?,
    modifier: Modifier = Modifier,
) {
    val playback = remember { desktopStylePlayback() }
    var menuOpen by remember { mutableStateOf(false) }
    val track =
        remember(currentTrack) {
            StyleTrack(
                title = currentTrack?.title.orEmpty(),
                artist = currentTrack?.artists?.joinToString { it.name }.orEmpty(),
                album = currentTrack?.album?.name.orEmpty(),
                artwork = currentTrack?.thumbnail,
            )
        }
    CompositionLocalProvider(
        LocalStylePlayback provides playback,
        LocalStyleTrack provides track,
        LocalStyleLyricLines provides LyricsState.currentLyricsEntries,
    ) {
        AlternatePlayerStyle(
            style = style,
            actions =
                StyleActions(
                    onOtherMenu = { menuOpen = true },
                    onEditPlayer = MochyStylePicker::show,
                    onLyrics = LyricsState::toggleLyricsDrawer,
                    onQueue = UiState::togglePersistentPlayerDrawer,
                ),
            modifier = modifier.fillMaxSize(),
        )
        // the player's own menu, the one the desktop's other players open from their more button
        PlayerMenuPanel(
            expanded = menuOpen,
            onDismiss = { menuOpen = false },
            onShowAddToPlaylistDialog = {
                PlayerState.currentTrack?.let { song -> PlayerState.showAddToPlaylistSong = song }
                menuOpen = false
            },
            onShowDetailsDialog = null,
            onShowSleepTimerDialog = null,
        )
    }
}

private fun desktopStylePlayback(): StylePlayback {
    val player = PlayerState.player
    return StylePlayback(
        isPlaying = player.isPlaying,
        reportedPosition = player.currentPosition,
        duration = player.duration,
        songId = player.currentSong.map { it?.id },
        positionMs = player::currentPlaybackPositionMs,
        speed = player::playbackSpeed,
        shuffleOn = { PlayerState.shuffleEnabled },
        repeatMode = {
            when (PlayerState.repeatMode) {
                RepeatMode.OFF -> 0
                RepeatMode.ONE -> 1
                RepeatMode.ALL -> 2
            }
        },
        togglePlayPause = PlayerState::togglePlay,
        next = { PlayerState.skipNext() },
        previous = { PlayerState.skipPrevious() },
        seekTo = { ms ->
            val length = player.duration.value
            if (length > 0L) PlayerState.seekTo(ms.toFloat() / length)
        },
        toggleShuffle = PlayerState::toggleShuffle,
        cycleRepeat = PlayerState::cycleRepeatMode,
    )
}
