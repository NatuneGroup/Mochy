// Ported from Mochy Music by realidkroo (github.com/realidkroo/mochy-music). Licensed under GPL-3.0.

package com.metrolist.shared.ui.player.mochy.styles

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import com.metrolist.shared.playback.LocalPlayerSession
import com.metrolist.shared.playback.PlayerSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map

/**
 * What the alternate styles read from the player and ask of it. Android and iOS build it from their player
 * session; the desktop app, whose player isn't a session, builds its own and provides it.
 */
@Stable
class StylePlayback(
    val isPlaying: StateFlow<Boolean>,
    // the player's reported position: it moves with seeks, so a paused style still moves its playhead once
    val reportedPosition: StateFlow<Long>,
    val duration: StateFlow<Long>,
    val songId: Flow<String?>,
    val positionMs: () -> Long,
    val speed: () -> Float,
    // read in composition: Media3's repeat constants, 0 off, 1 one, 2 all
    val shuffleOn: @Composable () -> Boolean,
    val repeatMode: @Composable () -> Int,
    val togglePlayPause: () -> Unit,
    val next: () -> Unit,
    val previous: () -> Unit,
    val seekTo: (Long) -> Unit,
    val toggleShuffle: () -> Unit,
    val cycleRepeat: () -> Unit,
)

/** The desktop app's own playback, for the styles it hosts; Android and iOS leave it unset. */
val LocalStylePlayback = staticCompositionLocalOf<StylePlayback?> { null }

/** The playback the styles drive: the one provided, else the player session's. */
@Composable
fun rememberStylePlayback(): StylePlayback? {
    LocalStylePlayback.current?.let { return it }
    val session = LocalPlayerSession.current ?: return null
    return remember(session) { session.asStylePlayback() }
}

private fun PlayerSession.asStylePlayback(): StylePlayback {
    val session = this
    return StylePlayback(
        isPlaying = isPlaying,
        reportedPosition = currentPosition,
        duration = duration,
        songId = currentSong.map { it?.id },
        positionMs = ::currentPositionMs,
        speed = { playbackSpeedFlow.value },
        shuffleOn = { session.shuffleModeEnabled.collectAsState().value },
        repeatMode = { session.repeatMode.collectAsState().value },
        togglePlayPause = ::togglePlayPause,
        next = ::skipToNext,
        previous = ::skipToPrevious,
        seekTo = { seekTo(it) },
        toggleShuffle = ::toggleShuffle,
        cycleRepeat = ::cycleRepeatMode,
    )
}
