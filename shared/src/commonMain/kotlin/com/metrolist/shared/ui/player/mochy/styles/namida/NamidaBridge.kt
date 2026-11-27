// Ported from Sora (github.com/shizenworks/sora), its namida player style after namida (github.com/namidaco/namida). Licensed under GPL-3.0.

// What the namida style asks of the player, answered from this app's player session, so the ported files keep
// Sora's shape.

package com.metrolist.shared.ui.player.mochy.styles.namida

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import com.metrolist.shared.model.LyricsEntry
import com.metrolist.shared.model.SongItem
import com.metrolist.shared.playback.LocalPlayerSession
import com.metrolist.shared.playback.PlayerSession
import com.metrolist.shared.state.SongLikeState
import com.metrolist.shared.ui.player.mochy.styles.LocalStyleLyricLines
import com.metrolist.shared.utils.formatDuration

/** one song in the queue as namida's tiles show it; [duration] in milliseconds */
internal data class NamidaQueueItem(
    val uid: Long,
    val title: String,
    val artist: String,
    val artworkUri: String?,
    val duration: Long,
)

@Stable
internal class NamidaConnection(
    private val session: PlayerSession,
) {
    val isPlaying = session.isPlaying

    // the volume to go back to when the mute is taken off
    private var unmutedVolume = 1f

    @Composable
    fun queueItems(): List<NamidaQueueItem> {
        val state = session.queueState.collectAsState().value
        return remember(state.entries) {
            state.entries.map { entry ->
                NamidaQueueItem(
                    uid = entry.key,
                    title = entry.song.title,
                    artist = entry.song.artists.joinToString { it.name },
                    artworkUri = entry.song.thumbnail,
                    duration = (entry.song.duration ?: 0L) * 1000L,
                )
            }
        }
    }

    @Composable
    fun currentWindowIndex(): Int =
        session.queueState
            .collectAsState()
            .value.currentIndex

    // Media3's repeat constants: 0 off, 1 one, 2 all
    @Composable
    fun repeatMode(): Int = session.repeatMode.collectAsState().value

    @Composable
    fun liked(): Boolean {
        val song = session.currentSong.collectAsState().value
        return song != null && SongLikeState.isLiked(song.id)
    }

    @Composable
    fun muted(): Boolean = session.volume.collectAsState().value <= 0f

    @Composable
    fun currentSong(): SongItem? = session.currentSong.collectAsState().value

    fun togglePlayPause() = session.togglePlayPause()

    fun seekToNext() = session.skipToNext()

    fun skipToPreviousSong() = session.skipToPrevious()

    fun seekTo(positionMs: Long) = session.seekTo(positionMs)

    fun playQueueItem(item: NamidaQueueItem) = session.skipToQueueEntry(item.uid)

    fun cycleRepeat() = session.cycleRepeatMode()

    fun toggleMute() {
        val volume = session.volume.value
        if (volume > 0f) {
            unmutedVolume = volume
            session.setVolume(0f)
        } else {
            session.setVolume(unmutedVolume.takeIf { it > 0f } ?: 1f)
        }
    }

    fun toggleLike() {
        session.currentSong.value?.let { SongLikeState.toggleLike(it) }
    }

    fun playNext(song: SongItem) = session.addToQueue(song, playNext = true)

    fun addToQueue(song: SongItem) = session.addToQueue(song)
}

@Composable
internal fun rememberNamidaConnection(): NamidaConnection? {
    val session = LocalPlayerSession.current ?: return null
    return remember(session) { NamidaConnection(session) }
}

/** the playing song's lines, the background vocals left out, keyed by the song they belong to */
internal class NamidaLyricsState(
    val mediaId: String?,
    val lines: List<LyricsEntry>,
)

@Composable
internal fun rememberNamidaLyrics(): NamidaLyricsState {
    val all = LocalStyleLyricLines.current
    val songId =
        LocalPlayerSession.current
            ?.currentSong
            ?.collectAsState()
            ?.value
            ?.id
    return remember(all, songId) { NamidaLyricsState(songId, all.filter { !it.isBackground }) }
}

internal fun makeTimeString(durationMs: Long): String = formatDuration(durationMs)
