// Ported and adapted from Mochy Music by realidkroo. Licensed under GPL-3.0.

package com.metrolist.shared.ui.player.mochy.ipod

import androidx.compose.runtime.Stable
import androidx.compose.runtime.snapshotFlow
import com.metrolist.shared.model.SongItem
import com.metrolist.shared.playback.PlayerSession
import com.metrolist.shared.ui.playback.PlaybackController
import com.metrolist.shared.ui.player.mochy.FlagshipVolume
import com.metrolist.shared.utils.MetrolistClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

@Stable
internal class IpodController(
    val nav: IpodNavigator,
    val nowPlaying: IpodNowPlayingState,
    val coverFlow: IpodCoverFlowState,
    private val scope: CoroutineScope,
    private val session: PlayerSession?,
    private val playbackController: PlaybackController,
    private val volumeControl: FlagshipVolume?,
    private val library: IpodLibrarySource?,
    private val haptics: IpodHaptics,
    private val shuffleQueueTitle: String,
) : IpodWheelActions {
    private var seekJob: Job? = null
    private var opening = false

    private val page: IpodPage get() = nav.top.page

    override val accelerates: Boolean get() = page != IpodPage.NowPlaying

    override fun rotate(steps: Int): Boolean =
        when {
            page == IpodPage.CoverFlow -> coverFlow.move(steps)
            page != IpodPage.NowPlaying -> nav.top.move(steps)
            nowPlaying.bar == IpodBar.SCRUB -> scrub(steps)
            else -> adjustVolume(steps)
        }

    override fun menu() {
        if (page == IpodPage.NowPlaying && nowPlaying.bar != IpodBar.PROGRESS) {
            nowPlaying.bar = IpodBar.PROGRESS
            return
        }
        if (page == IpodPage.CoverFlow && coverFlow.turnBack()) return
        nav.back()
    }

    override fun menuHeld() {
        coverFlow.turnBack()
        nav.backToMain()
    }

    override fun center() {
        when (page) {
            IpodPage.NowPlaying -> {
                nowPlaying.bar = if (nowPlaying.bar == IpodBar.SCRUB) IpodBar.PROGRESS else IpodBar.SCRUB
                touch()
            }
            IpodPage.CoverFlow -> if (coverFlow.flipped) playTrack(coverFlow.back.selected) else turnOver()
            else -> pick(nav.top, nav.top.selected)
        }
    }

    override fun next() {
        session?.skipToNext() ?: playbackController.skipToNext()
    }

    override fun previous() {
        session?.skipToPrevious() ?: playbackController.skipToPrevious()
    }

    override fun playPause() {
        val activeSession = session
        if (activeSession != null) {
            val dur = activeSession.duration.value
            val pos = activeSession.currentPositionMs()
            if (!activeSession.isPlaying.value && dur > 0L && pos >= dur) {
                activeSession.seekTo(0L)
            }
            activeSession.togglePlayPause()
        } else {
            playbackController.togglePlayPause()
        }
    }

    override fun scan(forward: Boolean) {
        val activeSession = session
        val end = activeSession?.duration?.value?.takeIf { it > 0L } ?: playbackController.state.value.durationMs.takeIf { it > 0L }
            ?: Long.MAX_VALUE
        val currentMs = activeSession?.currentPositionMs() ?: playbackController.currentPositionMs()
        val target = (currentMs + if (forward) SCAN_STEP_MS else -SCAN_STEP_MS).coerceIn(0L, end)
        if (activeSession != null) activeSession.seekTo(target) else playbackController.seekTo(target)
    }

    fun tapRow(
        entry: IpodEntry,
        index: Int,
    ) {
        if (entry !== nav.top) return
        entry.selected = index
        entry.clamp()
        haptics.click()
        pick(entry, index)
    }

    fun tapCoverFlow(direction: Int) {
        haptics.click()
        if (direction == 0) turnOver() else coverFlow.move(direction)
    }

    fun tapTrack(index: Int) {
        coverFlow.back.selected = index
        haptics.click()
        playTrack(index)
    }

    private fun pick(
        entry: IpodEntry,
        index: Int,
    ) {
        when (val pick = entry.list.rows.getOrNull(index)?.pick) {
            is IpodPick.Open -> open(pick.page)
            is IpodPick.Play ->
                scope.launch {
                    play(entry.list.songs, pick.index, entry.page.resolveTitle())
                }
            IpodPick.Shuffle ->
                scope.launch {
                    val items = library?.shuffledLibrary().orEmpty()
                    if (items.isEmpty()) return@launch
                    playbackController.shuffleQueue(items, queueTitle = shuffleQueueTitle)
                    nav.showNowPlaying()
                }
            null -> Unit
        }
    }

    private fun open(target: IpodPage) {
        val source = library
        if (opening) return
        if (source == null || target == IpodPage.NowPlaying || target.isSidebar) {
            nav.push(target)
            return
        }
        if (target == IpodPage.CoverFlow) {
            val ready = source.cachedCoverFlow
            if (ready != null) {
                coverFlow.show(ready)
                nav.push(target)
                scope.launch { coverFlow.show(source.coverFlow()) }
                return
            }
        } else {
            val ready = source.cached(target)
            if (ready != null) {
                nav.push(target, ready)
                source.refreshAsync(target)
                return
            }
        }
        opening = true
        scope.launch {
            try {
                if (target == IpodPage.CoverFlow) {
                    val albums = scope.async { source.coverFlow() }
                    withTimeoutOrNull(PREPARE_MS) { albums.await() }?.let(coverFlow::show)
                    nav.push(target)
                    opening = false
                    coverFlow.show(albums.await())
                } else {
                    val list = scope.async { source.load(target) }
                    val entry = nav.push(target, withTimeoutOrNull(PREPARE_MS) { list.await() } ?: IpodLoadingList)
                    opening = false
                    if (entry.list === IpodLoadingList) {
                        val loaded = list.await()
                        snapshotFlow { nav.leaving == null }.first { it }
                        entry.list = loaded
                        entry.clamp()
                    }
                }
            } finally {
                opening = false
            }
        }
    }

    private fun turnOver() {
        val album = coverFlow.current ?: return
        val source = library ?: return
        if (coverFlow.flipped) return
        val target = IpodPage.Album(album.id, album.title)
        val ready = source.cached(target)
        if (ready != null) {
            coverFlow.turnOver(ready)
            return
        }
        scope.launch {
            val tracks = scope.async { source.load(target) }
            coverFlow.turnOver(withTimeoutOrNull(PREPARE_MS) { tracks.await() } ?: IpodLoadingList)
            if (coverFlow.back.list === IpodLoadingList) {
                val loaded = tracks.await()
                if (coverFlow.flipped && coverFlow.current?.id == album.id) {
                    coverFlow.back.list = loaded
                    coverFlow.back.clamp()
                }
            }
        }
    }

    private fun playTrack(index: Int) {
        val album = coverFlow.current ?: return
        val songs = coverFlow.back.list.songs
        if (songs.isEmpty()) return
        play(songs, index, album.title)
    }

    private fun play(
        songs: List<SongItem>,
        index: Int,
        title: String,
    ) {
        nav.showNowPlaying()
        scope.launch {
            snapshotFlow { nav.leaving == null }.first { it }
            playbackController.playQueue(songs, startIndex = index, queueTitle = title)
        }
    }

    private fun adjustVolume(steps: Int): Boolean {
        nowPlaying.bar = IpodBar.VOLUME
        touch()
        val vol = volumeControl
        if (vol != null) {
            val before = vol.shown.coerceIn(0f, 1f)
            val target = (before + steps * VOLUME_STEP).coerceIn(0f, 1f)
            if (target != before) {
                vol.drag(target)
                vol.release()
            }
            nowPlaying.volume = target
            return target != before
        }
        val activeSession = session
        if (activeSession != null) {
            val before = activeSession.volume.value.coerceIn(0f, 1f)
            val target = (before + steps * VOLUME_STEP).coerceIn(0f, 1f)
            if (target != before) activeSession.setVolume(target)
            nowPlaying.volume = target
            return target != before
        }
        return false
    }

    private fun scrub(steps: Int): Boolean {
        val activeSession = session
        val duration =
            activeSession?.duration?.value?.takeIf { it > 0L }
                ?: playbackController.state.value.durationMs.takeIf { it > 0L }
                ?: return false
        val from =
            if (nowPlaying.scrubMs >= 0L) {
                nowPlaying.scrubMs
            } else {
                activeSession?.currentPositionMs() ?: playbackController.currentPositionMs()
            }
        val step = (duration / 100L).coerceIn(1_000L, 10_000L)
        val target = (from + steps * step).coerceIn(0L, duration)
        touch()
        if (target == from) return false
        nowPlaying.scrubMs = target
        seekJob?.cancel()
        seekJob =
            scope.launch {
                delay(SCRUB_SETTLE_MS)
                if (activeSession != null) activeSession.seekTo(target) else playbackController.seekTo(target)
                nowPlaying.scrubMs = -1L
            }
        return true
    }

    private fun touch() {
        nowPlaying.touchedAt = MetrolistClock.nowMs()
    }
}

private const val VOLUME_STEP = 1f / 16f
private const val SCRUB_SETTLE_MS = 300L
private const val SCAN_STEP_MS = 4_000L
private const val PREPARE_MS = 70L
