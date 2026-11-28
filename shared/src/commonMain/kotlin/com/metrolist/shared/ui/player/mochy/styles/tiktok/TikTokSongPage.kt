// Ported from Sora (github.com/shizenworks/sora), its TikTok-style player. Licensed under GPL-3.0.

/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package com.metrolist.shared.ui.player.mochy.styles.tiktok

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
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
import com.metrolist.shared.generated.resources.Res
import com.metrolist.shared.generated.resources.play
import com.metrolist.shared.generated.resources.tiktok_heart_bold
import com.metrolist.shared.generated.resources.tiktok_music_note_2_linear
import com.metrolist.shared.generated.resources.tiktok_play_linear
import com.metrolist.shared.model.LyricsEntry
import com.metrolist.shared.model.SongItem
import com.metrolist.shared.ui.player.mochy.styles.ExpressiveLyrics
import com.metrolist.shared.ui.player.mochy.styles.LivePosition
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

internal val TIKTOK_INACTIVE_GRAY = Color(0xFFA9A9B2)

internal val TIKTOK_CAPTION_ROW_HEIGHT = 40.dp

internal val TIKTOK_CAPTION_TEXT_CLEARANCE = 56.dp

/** One song of the feed: its cover's colours moving behind the cover or its lyrics, its name and the rail. */
@Composable
internal fun TikTokSongPage(
    song: SongItem,
    isCurrentPage: Boolean,
    isPlaying: Boolean,
    suppressPauseOverlay: Boolean,
    queueTitle: String?,
    immersive: Boolean,
    lyricsOpen: Boolean,
    lyricLines: List<LyricsEntry>,
    position: LivePosition,
    liked: Boolean,
    reduceAnimation: Boolean,
    extractColors: suspend () -> List<Color>?,
    topChromeHeight: Dp,
    bottomChromeHeight: Dp,
    onLike: (onlyLike: Boolean) -> Unit,
    onTogglePlayPause: () -> Unit,
    onToggleLyrics: () -> Unit,
    onQueueClick: () -> Unit,
    onShare: () -> Unit,
    onMore: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val heartBursts = remember { mutableStateListOf<TikTokHeartBurst>() }
    var nextHeartBurstId by remember { mutableLongStateOf(0L) }

    Box(modifier = Modifier.fillMaxSize().background(TIKTOK_EMPTY_BACKDROP)) {
        val meshColors = rememberTikTokArtworkColors(song.id, extractColors)
        Box(Modifier.fillMaxSize().clipToBounds()) {
            TikTokMeshBackdrop(palette = meshColors, trackKey = song.id, reduceAnimation = reduceAnimation)
        }
        Box(Modifier.fillMaxSize().tiktokScrim())

        Column(Modifier.fillMaxSize()) {
            Spacer(Modifier.height(topChromeHeight))
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                BoxWithConstraints(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    val artSize = minOf(maxWidth, maxHeight)
                    val cornerRadius = if (artSize < maxWidth) 16.dp else 10.dp
                    val artworkScale by animateFloatAsState(
                        targetValue = if (immersive) 1.04f else 1f,
                        animationSpec = tween(250),
                        label = "tiktokArtworkScale",
                    )
                    val showInlineLyrics = isCurrentPage && lyricsOpen && lyricLines.isNotEmpty()
                    AnimatedContent(
                        targetState = showInlineLyrics,
                        transitionSpec = { fadeIn(tween(240)) togetherWith fadeOut(tween(240)) },
                        contentAlignment = Alignment.Center,
                        label = "tiktokCoverOrLyrics",
                    ) { showLyrics ->
                        if (showLyrics) {
                            ExpressiveLyrics(
                                lines = lyricLines,
                                position = position,
                                color = Color.White,
                                baseSize = 26.sp,
                                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 4.dp),
                            )
                        } else {
                            Box(
                                modifier =
                                    Modifier
                                        .size(artSize)
                                        .graphicsLayer {
                                            scaleX = artworkScale
                                            scaleY = artworkScale
                                            compositingStrategy = CompositingStrategy.Offscreen
                                        }.drawWithContent {
                                            drawContent()
                                            drawRect(brush = TIKTOK_ART_EDGE_FADE, blendMode = BlendMode.DstIn)
                                        }.then(
                                            if (isCurrentPage) {
                                                Modifier.pointerInput(song.id) {
                                                    detectTapGestures(
                                                        onTap = {
                                                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                            onTogglePlayPause()
                                                        },
                                                        onDoubleTap = { tap ->
                                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                            onLike(true)
                                                            heartBursts +=
                                                                TikTokHeartBurst(
                                                                    id = nextHeartBurstId++,
                                                                    x = tap.x.toDp(),
                                                                    y = tap.y.toDp(),
                                                                )
                                                        },
                                                    )
                                                }
                                            } else {
                                                Modifier
                                            },
                                        ),
                            ) {
                                AsyncImage(
                                    model = song.thumbnail,
                                    contentDescription = song.title,
                                    contentScale = ContentScale.Crop,
                                    modifier =
                                        Modifier
                                            .fillMaxSize()
                                            .shadow(elevation = 18.dp, shape = RoundedCornerShape(cornerRadius), clip = true),
                                )
                                TikTokPausedOverlay(
                                    visible = isCurrentPage && !isPlaying && !suppressPauseOverlay && !lyricsOpen,
                                )
                                heartBursts.forEach { burst ->
                                    key(burst.id) {
                                        TikTokHeartBurstView(
                                            burst = burst,
                                            onFinished = { heartBursts.removeAll { it.id == burst.id } },
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (!immersive && !showInlineLyrics) {
                        Box(
                            modifier =
                                Modifier
                                    .align(Alignment.CenterEnd)
                                    .fillMaxHeight()
                                    .width(TIKTOK_RAIL_WASH_WIDTH)
                                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                                    .drawWithContent {
                                        drawRect(brush = TIKTOK_RAIL_WASH)
                                        drawRect(brush = TIKTOK_RAIL_WASH_VERTICAL_FADE, blendMode = BlendMode.DstIn)
                                    },
                        )
                    }
                }
            }
            AnimatedVisibility(visible = !immersive, enter = fadeIn(tween(200)), exit = fadeOut(tween(200))) {
                TikTokSongInfo(
                    song = song,
                    queueTitle = queueTitle,
                    onQueueClick = onQueueClick,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = TIKTOK_CAPTION_TEXT_CLEARANCE + 16.dp)
                            .padding(bottom = 4.dp),
                )
            }
            Spacer(Modifier.height(bottomChromeHeight))
        }

        AnimatedVisibility(
            visible = !immersive,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(200)),
            modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = bottomChromeHeight + 8.dp),
        ) {
            TikTokRail(
                song = song,
                liked = liked,
                lyricsActive = isCurrentPage && lyricsOpen,
                onLike = { onLike(false) },
                onToggleLyrics = onToggleLyrics,
                onQueue = onQueueClick,
                onShare = onShare,
                onMore = onMore,
            )
        }
    }
}

@Composable
private fun TikTokPausedOverlay(visible: Boolean) {
    AnimatedVisibility(
        visible = visible,
        enter =
            fadeIn(tween(150)) +
                scaleIn(
                    initialScale = 0.55f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                ),
        exit = fadeOut(tween(120)) + scaleOut(targetScale = 0.55f, animationSpec = tween(120)),
    ) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(Res.drawable.tiktok_play_linear),
                contentDescription = stringResource(Res.string.play),
                tint = Color.White.copy(alpha = 0.92f),
                modifier = Modifier.size(64.dp),
            )
        }
    }
}

@Composable
private fun TikTokSongInfo(
    song: SongItem,
    queueTitle: String?,
    onQueueClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        if (!queueTitle.isNullOrBlank()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(TIKTOK_CAPTION_ROW_HEIGHT)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                        Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White.copy(alpha = 0.14f))
                            .tiktokNoRippleClickable(onClick = onQueueClick)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.tiktok_music_note_2_linear),
                        contentDescription = null,
                        tint = TIKTOK_INACTIVE_GRAY,
                        modifier = Modifier.size(12.dp),
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = queueTitle,
                        color = TIKTOK_INACTIVE_GRAY,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 160.dp).basicMarquee(iterations = Int.MAX_VALUE),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        Text(
            text = song.title,
            color = Color.White,
            fontSize = 21.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(3.dp))
        val artistName = song.artists.joinToString(", ") { it.name }
        val album = song.album?.name
        val secondary =
            if (album.isNullOrBlank() || artistName.isBlank()) artistName.ifBlank { album.orEmpty() } else "$artistName • $album"
        if (secondary.isNotBlank()) {
            Text(text = secondary, color = TIKTOK_INACTIVE_GRAY, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private data class TikTokHeartBurst(
    val id: Long,
    val x: Dp,
    val y: Dp,
)

@Composable
private fun TikTokHeartBurstView(
    burst: TikTokHeartBurst,
    onFinished: () -> Unit,
) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(burst.id) {
        progress.animateTo(1f, animationSpec = tween(TIKTOK_HEART_BURST_MS, easing = LinearOutSlowInEasing))
        onFinished()
    }
    val p = progress.value
    val scale =
        when {
            p < 0.22f -> 0.2f + (p / 0.22f) * 1.15f
            p < 0.38f -> 1.35f - ((p - 0.22f) / 0.16f) * 0.35f
            else -> 1f
        }
    val alpha = if (p < 0.55f) 1f else 1f - ((p - 0.55f) / 0.45f)
    val rise = 160.dp * (p * p)
    val rotation = ((burst.id % 5) - 2) * 6f
    Icon(
        painter = painterResource(Res.drawable.tiktok_heart_bold),
        contentDescription = null,
        tint = TIKTOK_RED,
        modifier =
            Modifier
                .offset(x = burst.x - TIKTOK_HEART_BURST_SIZE / 2f, y = burst.y - TIKTOK_HEART_BURST_SIZE / 2f - rise)
                .size(TIKTOK_HEART_BURST_SIZE)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    this.alpha = alpha.coerceIn(0f, 1f)
                    rotationZ = rotation
                },
    )
}

private val TIKTOK_HEART_BURST_SIZE = 88.dp

private const val TIKTOK_HEART_BURST_MS = 650

private val TIKTOK_RAIL_WASH_WIDTH = 120.dp

private val TIKTOK_RAIL_WASH = Brush.horizontalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.35f)))

private val TIKTOK_RAIL_WASH_VERTICAL_FADE =
    Brush.verticalGradient(
        0.00f to Color.Transparent,
        0.28f to Color.Black,
        0.90f to Color.Black,
        1.00f to Color.Transparent,
    )

private val TIKTOK_ART_EDGE_FADE =
    Brush.verticalGradient(
        0.00f to Color.Transparent,
        0.14f to Color.Black,
        0.86f to Color.Black,
        1.00f to Color.Transparent,
    )

internal fun Modifier.tiktokScrim(): Modifier = drawBehind { drawRect(TIKTOK_SCRIM) }

internal val TIKTOK_EMPTY_BACKDROP = Color(0xFF0B0B0F)

private val TIKTOK_SCRIM =
    Brush.verticalGradient(
        colorStops =
            arrayOf(
                0.00f to Color.Black.copy(alpha = 0.32f),
                0.22f to Color.Black.copy(alpha = 0.06f),
                0.55f to Color.Black.copy(alpha = 0.08f),
                1.00f to Color.Black.copy(alpha = 0.52f),
            ),
    )
