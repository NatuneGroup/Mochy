// Ported from Sora (github.com/shizenworks/sora), its TikTok-style player. Licensed under GPL-3.0.

/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package com.metrolist.shared.ui.player.mochy.styles.tiktok

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.metrolist.shared.generated.resources.Res
import com.metrolist.shared.generated.resources.action_like
import com.metrolist.shared.generated.resources.lyrics
import com.metrolist.shared.generated.resources.more
import com.metrolist.shared.generated.resources.queue
import com.metrolist.shared.generated.resources.share
import com.metrolist.shared.generated.resources.tiktok_chat_round_linear
import com.metrolist.shared.generated.resources.tiktok_heart_bold
import com.metrolist.shared.generated.resources.tiktok_heart_linear
import com.metrolist.shared.generated.resources.tiktok_more_vert_linear
import com.metrolist.shared.generated.resources.tiktok_music_note_2_linear
import com.metrolist.shared.generated.resources.tiktok_share_linear
import com.metrolist.shared.model.SongItem
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

internal val TIKTOK_RED = Color(0xFFFE2C55)

private const val TIKTOK_RAIL_FADE_MS = 180

/**
 * The column down the right edge: the artist, like, lyrics, the queue, share, and the song's menu. While the lyrics
 * are open only their button stays.
 */
@Composable
internal fun TikTokRail(
    song: SongItem,
    liked: Boolean,
    lyricsActive: Boolean,
    onLike: () -> Unit,
    onToggleLyrics: () -> Unit,
    onQueue: () -> Unit,
    onShare: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.padding(end = 10.dp).padding(vertical = 6.dp),
    ) {
        RailItem(visible = !lyricsActive) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                TikTokArtistAvatar(song)
                Spacer(Modifier.height(14.dp))
            }
        }
        RailItem(visible = !lyricsActive) {
            TikTokLikeRailButton(
                liked = liked,
                onLike = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLike()
                },
            )
        }
        TikTokRailButton(
            icon = Res.drawable.tiktok_chat_round_linear,
            contentDescription = stringResource(Res.string.lyrics),
            tint = if (lyricsActive) TIKTOK_RED else Color.White,
        ) {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onToggleLyrics()
        }
        RailItem(visible = !lyricsActive) {
            TikTokRailButton(icon = Res.drawable.tiktok_music_note_2_linear, contentDescription = stringResource(Res.string.queue)) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onQueue()
            }
        }
        RailItem(visible = !lyricsActive) {
            TikTokRailButton(icon = Res.drawable.tiktok_share_linear, contentDescription = stringResource(Res.string.share)) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onShare()
            }
        }
        RailItem(visible = !lyricsActive) {
            TikTokRailButton(icon = Res.drawable.tiktok_more_vert_linear, contentDescription = stringResource(Res.string.more)) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onMore()
            }
        }
    }
}

@Composable
private fun RailItem(
    visible: Boolean,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(TIKTOK_RAIL_FADE_MS)),
        exit = fadeOut(tween(TIKTOK_RAIL_FADE_MS)),
    ) { content() }
}

// the song's first artist, in the circle the feed puts over the like button; their picture, else the cover
@Composable
private fun TikTokArtistAvatar(song: SongItem) {
    val artist = song.artists.firstOrNull()
    AsyncImage(
        model = artist?.thumbnail ?: song.thumbnail,
        contentDescription = artist?.name,
        contentScale = ContentScale.Crop,
        modifier =
            Modifier
                .size(40.dp)
                .shadow(elevation = 4.dp, shape = CircleShape, clip = false)
                .clip(CircleShape)
                .border(1.5.dp, Color.White.copy(alpha = 0.95f), CircleShape),
    )
}

@Composable
private fun TikTokLikeRailButton(
    liked: Boolean,
    onLike: () -> Unit,
) {
    val scale = remember { Animatable(1f) }
    var wasLiked by remember { mutableStateOf(liked) }
    LaunchedEffect(liked) {
        if (liked && !wasLiked) {
            scale.snapTo(0.2f)
            scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
        } else if (!liked && wasLiked) {
            scale.snapTo(0.85f)
            scale.animateTo(1f, spring(stiffness = Spring.StiffnessMediumLow))
        }
        wasLiked = liked
    }
    Box(modifier = Modifier.width(48.dp).height(48.dp).tiktokNoRippleClickable(onClick = onLike), contentAlignment = Alignment.Center) {
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier.size(34.dp).graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                },
        ) {
            TikTokRailGlyph(
                icon = if (liked) Res.drawable.tiktok_heart_bold else Res.drawable.tiktok_heart_linear,
                contentDescription = stringResource(Res.string.action_like),
                tint = if (liked) TIKTOK_RED else Color.White,
                iconSize = 34.dp,
            )
        }
    }
    Spacer(Modifier.height(2.dp))
}

@Composable
private fun TikTokRailButton(
    icon: DrawableResource,
    contentDescription: String,
    tint: Color = Color.White,
    onClick: () -> Unit,
) {
    Box(modifier = Modifier.size(48.dp).tiktokNoRippleClickable(onClick = onClick), contentAlignment = Alignment.Center) {
        TikTokRailGlyph(icon = icon, contentDescription = contentDescription, tint = tint)
    }
    Spacer(Modifier.height(2.dp))
}

// the icon over a soft drop shadow of itself, so it reads on any cover
@Composable
private fun TikTokRailGlyph(
    icon: DrawableResource,
    contentDescription: String,
    tint: Color,
    iconSize: Dp = 30.dp,
) {
    Box(modifier = Modifier.size(iconSize)) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = Color.Black.copy(alpha = 0.35f),
            modifier = Modifier.fillMaxSize().offset(y = 1.dp).blur(2.dp),
        )
        Icon(painter = painterResource(icon), contentDescription = contentDescription, tint = tint, modifier = Modifier.fillMaxSize())
    }
}

@Composable
internal fun Modifier.tiktokNoRippleClickable(
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    return then(
        if (enabled) Modifier.clickable(interactionSource = interactionSource, indication = null, onClick = onClick) else Modifier,
    )
}
