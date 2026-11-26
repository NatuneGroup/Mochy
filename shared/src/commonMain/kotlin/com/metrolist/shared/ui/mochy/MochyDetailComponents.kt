// Ported and adapted from Mochy Music by realidkroo. Licensed under GPL-3.0.

package com.metrolist.shared.ui.mochy

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.metrolist.shared.model.previewThumbnailUrl
import com.metrolist.shared.generated.resources.Res
import com.metrolist.shared.generated.resources.more_options
import com.metrolist.shared.model.SongItem
import com.metrolist.shared.state.SongMetadataState
import com.metrolist.shared.ui.components.ArtistSubtitle
import com.metrolist.shared.ui.components.ItemStatusBadges
import com.metrolist.shared.ui.components.ListItemTitle
import com.metrolist.shared.ui.components.UnavailableSongTaps
import com.metrolist.shared.ui.components.images.AsyncImage
import com.metrolist.shared.ui.icons.MetrolistIcons
import com.metrolist.shared.ui.menu.onSecondaryClick
import com.metrolist.shared.ui.theme.mochyInterFontFamily
import com.metrolist.shared.ui.theme.rememberArtworkSeedColor
import com.metrolist.shared.ui.theme.rememberReducedMotion
import com.metrolist.shared.ui.theme.toHsv
import com.metrolist.shared.ui.utils.supportsNativeBlur
import com.metrolist.shared.utils.isDesktop
import org.jetbrains.compose.resources.stringResource

private const val DETAIL_GLOW_DURATION_MS = 550

@Composable
internal fun MochyDetailCoverBackdrop(
    thumbnailUrl: String?,
    modifier: Modifier = Modifier,
    topDepth: Dp = 360.dp,
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val seedColor = rememberArtworkSeedColor(thumbnailUrl)
    val targetColor = seedColor?.toDetailTone(isDark) ?: Color.Transparent
    val reducedMotion = rememberReducedMotion()
    val animatedColor by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(durationMillis = if (reducedMotion) 0 else DETAIL_GLOW_DURATION_MS),
        label = "mochyDetailBackdropColor",
    )
    val peakAlpha = if (isDark) 0.44f else 0.24f

    Spacer(
        modifier =
            modifier
                .fillMaxWidth()
                .height(topDepth)
                .drawWithCache {
                    if (animatedColor.alpha == 0f || size.width <= 0f || size.height <= 0f) {
                        return@drawWithCache onDrawBehind {}
                    }
                    val radial =
                        Brush.radialGradient(
                            0f to animatedColor.copy(alpha = peakAlpha),
                            0.55f to animatedColor.copy(alpha = peakAlpha * 0.48f),
                            1f to Color.Transparent,
                            center = Offset(size.width * 0.5f, size.height * 0.22f),
                            radius = maxOf(size.width, size.height) * 0.85f,
                        )
                    val verticalFade =
                        Brush.verticalGradient(
                            0f to animatedColor.copy(alpha = peakAlpha * 0.6f),
                            0.45f to animatedColor.copy(alpha = peakAlpha * 0.22f),
                            1f to Color.Transparent,
                        )
                    onDrawBehind {
                        drawRect(brush = verticalFade)
                        drawRect(brush = radial)
                    }
                },
    )
}

@Composable
internal fun MochyCollapsingTopBar(
    visible: Boolean,
    title: String,
    thumbnailUrl: String?,
    hasMoreOptions: Boolean,
    modifier: Modifier = Modifier,
    fallbackIcon: ImageVector? = null,
) {
    val reducedMotion = rememberReducedMotion()
    AnimatedVisibility(
        visible = visible,
        modifier = modifier.fillMaxSize(),
        // the reference's morph: folding in overshoots a little, leaving settles calmer
        enter = fadeIn(if (reducedMotion) snap() else spring(dampingRatio = 0.62f, stiffness = 360f)),
        exit = fadeOut(if (reducedMotion) snap() else spring(dampingRatio = 0.9f, stiffness = 360f)),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.94f)),
        ) {
            MochyCollapsedTopBarContent(
                title = title,
                thumbnailUrl = thumbnailUrl,
                hasMoreOptions = hasMoreOptions,
                fallbackIcon = fallbackIcon,
                modifier = Modifier.align(Alignment.CenterStart),
            )
            HorizontalDivider(
                modifier = Modifier.align(Alignment.BottomCenter),
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
            )
        }
    }
}

@Composable
internal fun MochyCollapsedTopBarContent(
    title: String,
    thumbnailUrl: String?,
    hasMoreOptions: Boolean,
    modifier: Modifier = Modifier,
    fallbackIcon: ImageVector? = null,
) {
    val borderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(start = 56.dp, end = if (hasMoreOptions) 56.dp else 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (!thumbnailUrl.isNullOrBlank() || fallbackIcon != null) {
            Box(
                modifier =
                    Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .border(0.75.dp, borderColor, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (!thumbnailUrl.isNullOrBlank()) {
                    AsyncImage(
                        url = thumbnailUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else if (fallbackIcon != null) {
                    Icon(
                        imageVector = fallbackIcon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Text(
            text = title,
            fontFamily = mochyInterFontFamily(),
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp,
            letterSpacing = (-0.02).em,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun Color.toDetailTone(isDark: Boolean): Color {
    val hsv = toHsv()
    val saturation = hsv[1].coerceAtMost(if (isDark) 0.58f else 0.45f)
    val value = if (isDark) hsv[2].coerceIn(0.22f, 0.42f) else hsv[2].coerceIn(0.60f, 0.85f)
    return Color.hsv(hsv[0], saturation, value)
}

// the reference's cover band: the photo fills 60% of the screen behind the list, and as the list scrolls it rides up,
// zooms, dims, dissolves from the bottom and fades, with a blurred copy showing through while it moves
private const val PHOTO_COVER_HEIGHT_SHARE = 0.6f
private const val PHOTO_FOLLOW = -0.8f
private const val PHOTO_ZOOM = 0.22f
private const val PHOTO_FADE_FROM = 0.2f
private const val PHOTO_FADE_TO = 0.85f
private const val PHOTO_DIM = 0.22f
private const val PHOTO_SOFT_BY = 0.55f
private const val PHOTO_SOFT_RADIUS = 30f

private fun photoSmoothStep(from: Float, to: Float, x: Float): Float {
    val t = ((x - from) / (to - from)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

@Composable
internal fun MochyDetailPhotoBackdrop(
    thumbnailUrl: String?,
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val coverPx = LocalWindowInfo.current.containerSize.height * PHOTO_COVER_HEIGHT_SHARE
    val coverHeight = with(density) { coverPx.toDp() }
    val background = MaterialTheme.colorScheme.background
    val blur = remember { supportsNativeBlur() }
    val progress: () -> Float = {
        if (listState.firstVisibleItemIndex > 0) {
            1f
        } else {
            (listState.firstVisibleItemScrollOffset / coverPx).coerceIn(0f, 1f)
        }
    }

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(coverHeight)
                .graphicsLayer {
                    val p = progress()
                    translationY = p * size.height * PHOTO_FOLLOW
                    val zoom = 1f + p * PHOTO_ZOOM
                    scaleX = zoom
                    scaleY = zoom
                    transformOrigin = TransformOrigin(0.5f, 0.35f)
                    alpha = 1f - photoSmoothStep(PHOTO_FADE_FROM, PHOTO_FADE_TO, p)
                    compositingStrategy = if (p > 0f) CompositingStrategy.Offscreen else CompositingStrategy.Auto
                }
                .drawWithContent {
                    drawContent()
                    val p = progress()
                    if (p > 0f) {
                        drawRect(Color.Black.copy(alpha = PHOTO_DIM * p))
                        // only the bottom band dissolves; above it the picture stays solid
                        val clear = 0.1f + 0.45f * photoSmoothStep(0f, 0.8f, p)
                        val top = size.height * (1f - clear)
                        drawRect(
                            brush =
                                Brush.verticalGradient(
                                    0f to Color.Black,
                                    1f to Color.Transparent,
                                    startY = top,
                                    endY = size.height,
                                ),
                            topLeft = Offset(0f, top),
                            size = Size(size.width, size.height - top),
                            blendMode = BlendMode.DstIn,
                        )
                    }
                },
    ) {
        if (!thumbnailUrl.isNullOrBlank()) {
            if (blur) {
                // the soft copy shows while the picture starts to move, then the sharp one fades over it
                AsyncImage(
                    url = thumbnailUrl,
                    contentDescription = null,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                alpha = if (progress() > 0f) 1f else 0f
                                renderEffect = BlurEffect(PHOTO_SOFT_RADIUS, PHOTO_SOFT_RADIUS, TileMode.Decal)
                            },
                    contentScale = ContentScale.Crop,
                )
            }
            AsyncImage(
                url = thumbnailUrl,
                contentDescription = null,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = if (blur) 1f - photoSmoothStep(0f, PHOTO_SOFT_BY, progress()) else 1f
                        },
                contentScale = ContentScale.Crop,
            )
        }
        Box(
            modifier =
                Modifier
                    .matchParentSize()
                    .drawWithCache {
                        val top =
                            Brush.verticalGradient(
                                0f to Color.Black.copy(alpha = 0.34f),
                                1f to Color.Transparent,
                                endY = size.height * 0.24f,
                            )
                        val bottom =
                            Brush.verticalGradient(
                                0.56f to Color.Transparent,
                                0.84f to background.copy(alpha = 0.78f),
                                1f to background,
                            )
                        onDrawBehind {
                            drawRect(top, size = Size(size.width, size.height * 0.24f))
                            drawRect(bottom)
                        }
                    },
        )
    }
}

// the detail pages' song row: a 39dp cover, the title and the artists with the duration, and the more button;
// the same taps, long-press, artist links and badges as the shared song row
@Composable
internal fun MochyDetailTrackRow(
    song: SongItem,
    isCurrent: Boolean,
    isPlaying: Boolean,
    isDownloaded: Boolean,
    onClick: () -> Unit,
    onShowMenu: () -> Unit,
    modifier: Modifier = Modifier,
    onArtistClick: ((String) -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    // a row picked while selecting sits on a soft band
    selected: Boolean = false,
) {
    val effectiveSong = SongMetadataState.apply(song)
    val enabled = !song.isUnavailable
    val titleColor = MaterialTheme.colorScheme.onSurface.copy(alpha = if (isCurrent) 1f else 0.94f)
    val subtitleColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
    val subtitleStyle = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.5.sp, lineHeight = 14.sp, letterSpacing = (-0.02).em)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .fillMaxWidth()
                .then(if (selected) Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f), RoundedCornerShape(9.dp)) else Modifier)
                .alpha(if (enabled) 1f else 0.38f)
                .combinedClickable(
                    onClick = {
                        if (enabled) {
                            onClick()
                        } else {
                            UnavailableSongTaps.tapped()
                        }
                    },
                    onLongClick = onLongClick ?: onShowMenu,
                )
                .onSecondaryClick(onShowMenu)
                .padding(start = 36.dp, end = 24.dp, top = 9.dp, bottom = 9.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .size(39.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(BoneColor),
        ) {
            AsyncImage(
                url = effectiveSong.previewThumbnailUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            if (isCurrent) {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (isPlaying) MetrolistIcons.Rounded.PlayArrow else MetrolistIcons.Default.Pause,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
        Column(modifier = Modifier.weight(1f).padding(horizontal = 11.dp)) {
            ListItemTitle(
                text = effectiveSong.title,
                isExplicit = isDesktop && effectiveSong.isExplicit,
                style = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 17.sp, letterSpacing = (-0.03).em),
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold,
                color = titleColor,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                ItemStatusBadges(effectiveSong, isDownloaded, showExplicit = !isDesktop)
                ArtistSubtitle(
                    artists = effectiveSong.artists,
                    onArtistClick = onArtistClick,
                    style = subtitleStyle,
                    modifier = Modifier.weight(1f, fill = false),
                    clickable = isDesktop,
                    color = subtitleColor,
                    linkColor = subtitleColor,
                )
                effectiveSong.duration?.let {
                    Text(
                        text = " \u2022 ${it / 60}:${(it % 60).toString().padStart(2, '0')}",
                        fontFamily = mochyInterFontFamily(),
                        fontWeight = FontWeight.Medium,
                        fontSize = 11.5.sp,
                        lineHeight = 14.sp,
                        color = subtitleColor,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (trailingContent != null) {
            trailingContent()
        } else {
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onShowMenu),
            ) {
                Icon(
                    imageVector = MetrolistIcons.Default.MoreVert,
                    contentDescription = stringResource(Res.string.more_options),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
