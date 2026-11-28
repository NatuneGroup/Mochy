// Ported from Sora (github.com/shizenworks/sora), its TikTok-style player. Licensed under GPL-3.0.

/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package com.metrolist.shared.ui.player.mochy.styles.tiktok

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrolist.shared.generated.resources.Res
import com.metrolist.shared.generated.resources.home
import com.metrolist.shared.generated.resources.library
import com.metrolist.shared.generated.resources.search
import com.metrolist.shared.generated.resources.tiktok_feed_exit_fullscreen
import com.metrolist.shared.generated.resources.tiktok_feed_fullscreen
import com.metrolist.shared.generated.resources.tiktok_fullscreen_exit_linear
import com.metrolist.shared.generated.resources.tiktok_fullscreen_linear
import com.metrolist.shared.generated.resources.tiktok_magnifer_linear
import com.metrolist.shared.model.SongItem
import com.metrolist.shared.playback.LocalPlayerSession
import com.metrolist.shared.state.SongLikeState
import com.metrolist.shared.ui.navigation.Screen
import com.metrolist.shared.ui.player.mochy.styles.LocalStyleLyricLines
import com.metrolist.shared.ui.player.mochy.styles.rememberDurationMs
import com.metrolist.shared.ui.player.mochy.styles.rememberLivePosition
import com.metrolist.shared.ui.player.mochy.styles.withLyricsOffset
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs

internal val TIKTOK_TOP_NAV_HEIGHT = 44.dp

/**
 * The player as a feed: every song of the queue is a page, swiped up and down to move through it, the playing one
 * with its cover, lyrics and rail. Its few ties to the app come in as callbacks: closing the player, sharing a
 * song, opening the queue and the song's menu, a song's cover colours, and going to Home, Library or Search
 * ([onNavigate], null where the player can't, which then only closes it).
 */
@Composable
fun TikTokPlayerStyle(
    onCollapse: () -> Unit,
    onShare: (SongItem) -> Unit,
    onQueue: () -> Unit,
    onMore: () -> Unit,
    extractColors: suspend (SongItem) -> List<Color>?,
    onNavigate: ((Screen) -> Unit)?,
    modifier: Modifier = Modifier,
    reduceAnimation: Boolean = false,
) {
    val session = LocalPlayerSession.current ?: return
    val queue by session.queueState.collectAsState()
    val isPlaying by session.isPlaying.collectAsState()
    val isLoading by session.isPlaybackLoading.collectAsState()
    val queueTitle by session.queueTitle.collectAsState()
    val entries = queue.entries
    val currentIndex = queue.currentIndex
    val lyricLines = LocalStyleLyricLines.current
    val livePosition = rememberLivePosition()
    val lyricsPosition = livePosition.withLyricsOffset()
    val durationMs = rememberDurationMs()

    var immersive by remember { mutableStateOf(false) }
    var lyricsOpen by remember { mutableStateOf(false) }
    // a drag on the bar shows where it will land until it's let go
    var seekPreviewMs by remember { mutableStateOf<Long?>(null) }

    val pagerState = rememberPagerState(initialPage = currentIndex.coerceAtLeast(0)) { entries.size }
    var pendingTarget by remember { mutableStateOf<Int?>(null) }
    var autoAdvancing by remember { mutableStateOf(false) }

    // settling on another page plays it
    val liveIndex by rememberUpdatedState(currentIndex)
    val liveEntries by rememberUpdatedState(entries)
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { settled ->
                if (liveEntries.isNotEmpty() && settled != liveIndex && settled in liveEntries.indices) {
                    pendingTarget = settled
                    session.skipToQueueEntry(liveEntries[settled].key)
                    lyricsOpen = false
                }
            }
    }
    // the player moving on by itself turns the page with it
    LaunchedEffect(currentIndex, entries.size) {
        if (entries.isEmpty() || currentIndex !in entries.indices) return@LaunchedEffect
        pendingTarget?.let { pending ->
            if (currentIndex == pending) pendingTarget = null
            return@LaunchedEffect
        }
        if (pagerState.currentPage == currentIndex) return@LaunchedEffect
        if (pagerState.isScrollInProgress) {
            snapshotFlow { pagerState.isScrollInProgress }.first { !it }
            if (pagerState.currentPage == currentIndex) return@LaunchedEffect
        }
        if (abs(currentIndex - pagerState.currentPage) == 1) {
            autoAdvancing = true
            try {
                pagerState.animateScrollToPage(currentIndex)
            } finally {
                autoAdvancing = false
            }
        } else {
            pagerState.scrollToPage(currentIndex)
        }
    }

    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val topChromeHeight = if (immersive) 0.dp else topInset + TIKTOK_TOP_NAV_HEIGHT
    val bottomChromeHeight = if (immersive) 0.dp else bottomInset + TIKTOK_PROGRESS_ROW_HEIGHT

    Box(modifier = modifier.fillMaxSize().background(TIKTOK_EMPTY_BACKDROP)) {
        VerticalPager(
            state = pagerState,
            beyondViewportPageCount = 1,
            key = { index -> entries.getOrNull(index)?.key ?: index },
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val entry = entries.getOrNull(page) ?: return@VerticalPager
            val song = entry.song
            val isCurrentPage = page == currentIndex
            val liked = SongLikeState.isLiked(song.id)
            TikTokSongPage(
                song = song,
                isCurrentPage = isCurrentPage,
                isPlaying = isPlaying,
                suppressPauseOverlay = isLoading || autoAdvancing,
                queueTitle = queueTitle,
                immersive = immersive,
                lyricsOpen = lyricsOpen,
                lyricLines = if (isCurrentPage) lyricLines else emptyList(),
                position = lyricsPosition,
                liked = liked,
                reduceAnimation = reduceAnimation,
                extractColors = { extractColors(song) },
                topChromeHeight = topChromeHeight,
                bottomChromeHeight = bottomChromeHeight,
                // a double tap only ever likes; the heart on the rail likes and unlikes
                onLike = { onlyLike -> if (!onlyLike || !SongLikeState.isLiked(song.id)) SongLikeState.toggleLike(song) },
                onTogglePlayPause = session::togglePlayPause,
                onToggleLyrics = { lyricsOpen = !lyricsOpen },
                onQueueClick = {
                    lyricsOpen = false
                    onQueue()
                },
                onShare = { onShare(song) },
                onMore = onMore,
            )
        }

        AnimatedVisibility(visible = !immersive, enter = fadeIn(tween(200)), exit = fadeOut(tween(200))) {
            TikTokTopNavigation(
                topInset = topInset,
                isLoading = isLoading,
                onFullscreen = {
                    lyricsOpen = false
                    immersive = true
                },
                onTab = { screen ->
                    onCollapse()
                    onNavigate?.invoke(screen)
                },
            )
        }
        AnimatedVisibility(
            visible = !immersive,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(200)),
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0f), Color.Black.copy(alpha = 0.45f), Color.Black))),
        ) {
            TikTokBottomChrome(
                displayPositionMs = seekPreviewMs ?: livePosition.positionMs(),
                durationMs = durationMs,
                onSeek = { seekPreviewMs = it },
                onSeekFinished = {
                    seekPreviewMs?.let(session::seekTo)
                    seekPreviewMs = null
                },
            )
        }
        if (immersive) {
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(top = topInset + 2.dp, start = 6.dp)
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.28f))
                        .tiktokNoRippleClickable { immersive = false },
            ) {
                Icon(
                    painter = painterResource(Res.drawable.tiktok_fullscreen_exit_linear),
                    contentDescription = stringResource(Res.string.tiktok_feed_exit_fullscreen),
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

// the feed's top bar: full screen at one end, search at the other, Home and Library between them
@Composable
private fun TikTokTopNavigation(
    topInset: Dp,
    isLoading: Boolean,
    onFullscreen: () -> Unit,
    onTab: (Screen) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = topInset)
                .height(TIKTOK_TOP_NAV_HEIGHT)
                .padding(horizontal = 6.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(40.dp).clip(CircleShape).tiktokNoRippleClickable(onClick = onFullscreen),
        ) {
            Icon(
                painter = painterResource(Res.drawable.tiktok_fullscreen_linear),
                contentDescription = stringResource(Res.string.tiktok_feed_fullscreen),
                tint = Color.White,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            // the player is over whichever tab it was opened from; it reads as the feed's own "For you"
            TikTokTab(Res.string.home, selected = true) { onTab(Screen.Home) }
            TikTokTab(Res.string.library, selected = false) { onTab(Screen.Library) }
        }
        Spacer(Modifier.weight(1f))
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(40.dp).clip(CircleShape).tiktokNoRippleClickable { onTab(Screen.Search) },
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 2.2.dp,
                    modifier = Modifier.size(20.dp),
                )
            } else {
                Icon(
                    painter = painterResource(Res.drawable.tiktok_magnifer_linear),
                    contentDescription = stringResource(Res.string.search),
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

@Composable
private fun TikTokTab(
    label: StringResource,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier =
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .tiktokNoRippleClickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Text(
            text = stringResource(label),
            color = if (selected) Color.White else TIKTOK_INACTIVE_GRAY,
            fontSize = 16.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
        )
        Spacer(Modifier.height(2.dp))
        Box(
            modifier =
                Modifier
                    .width(22.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(if (selected) Color.White else Color.Transparent),
        )
    }
}
