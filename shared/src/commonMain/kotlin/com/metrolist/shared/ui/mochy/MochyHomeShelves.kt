// Ported from Mochy Music by realidkroo. Licensed under GPL-3.0.

package com.metrolist.shared.ui.mochy

import androidx.compose.foundation.lazy.rememberLazyListState
import com.metrolist.shared.ui.components.DesktopHorizontalScrollControls
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridPrefetchStrategy
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.metrolist.shared.generated.resources.Res
import com.metrolist.shared.generated.resources.more_options
import com.metrolist.shared.model.AlbumItem
import com.metrolist.shared.model.ArtistItem
import com.metrolist.shared.model.PlaylistItem
import com.metrolist.shared.model.SongItem
import com.metrolist.shared.model.YTItem
import com.metrolist.shared.model.canonicalHomeItemId
import com.metrolist.shared.settings.GridItemSize
import com.metrolist.shared.settings.Settings
import com.metrolist.shared.settings.displayName
import com.metrolist.shared.state.SongMetadataState
import com.metrolist.shared.ui.components.CollectionPreviews
import com.metrolist.shared.ui.components.ItemStatusBadges
import com.metrolist.shared.ui.components.PlaylistCoverImage
import com.metrolist.shared.ui.components.SongThumbnailPlayOverlay
import com.metrolist.shared.ui.components.UnavailableSongTaps
import com.metrolist.shared.ui.components.currentSongSemantics
import com.metrolist.shared.ui.components.focusOutline
import com.metrolist.shared.ui.components.images.AsyncImage
import com.metrolist.shared.ui.components.rememberItemPlayback
import com.metrolist.shared.ui.icons.MetrolistIcons
import com.metrolist.shared.ui.menu.artworkMenuActions
import com.metrolist.shared.ui.navigation.LocalContentObscured
import com.metrolist.shared.ui.navigation.LocalRootContentActive
import com.metrolist.shared.ui.navigation.navigationPageSource
import com.metrolist.shared.ui.navigation.rememberAlbumArtworkSource
import com.metrolist.shared.ui.theme.mochyInterFontFamily
import com.metrolist.shared.ui.theme.rememberReducedMotion
import org.jetbrains.compose.resources.stringResource

private val HomeInset = 36.dp
private val CoverRadius = 25.dp
private val TrackArtworkSize = 54.dp
private val TrackArtworkShape = RoundedCornerShape(14.dp)

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun MochyHomeTrackShelf(
    sectionKey: String,
    tracks: List<SongItem>,
    onItemClick: (SongItem) -> Unit,
    onItemMenu: ((SongItem) -> Unit)?,
    isItemEnabled: (SongItem) -> Boolean,
    currentSongId: State<String?>,
    isPlaying: State<Boolean>,
) {
    if (tracks.isEmpty()) return
    val rows = minOf(3, tracks.size)
    val gap = 6.dp
    val textHeight = with(LocalDensity.current) { 32.sp.toDp() }
    val rowHeight = maxOf(TrackArtworkSize, textHeight + 14.dp)
    val prefetch = remember { LazyGridPrefetchStrategy(nestedPrefetchItemCount = 6) }
    val state = rememberLazyGridState(prefetchStrategy = prefetch)
    val fling = rememberSnapFlingBehavior(state, SnapPosition.Start)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val twoColumnWidth = maxWidth * 0.475f
        val columnWidth = if (twoColumnWidth >= 320.dp) twoColumnWidth else maxWidth * 0.76f
        // a mouse wheel can't move a sideways row, so desktop gets the arrow buttons other shelves have
        DesktopHorizontalScrollControls(state) {
        LazyHorizontalGrid(
            rows = GridCells.Fixed(rows),
            state = state,
            flingBehavior = fling,
            contentPadding = PaddingValues(horizontal = HomeInset),
            horizontalArrangement = Arrangement.spacedBy(39.dp),
            verticalArrangement = Arrangement.spacedBy(gap),
            modifier = Modifier.fillMaxWidth().height(rowHeight * rows + gap * (rows - 1)),
        ) {
            itemsIndexed(
                items = tracks,
                key = { index, item -> "$sectionKey/${canonicalHomeItemId(item)}/$index" },
                contentType = { _, _ -> "mochy_home_track" },
            ) { _, song ->
                val playback by rememberItemPlayback(song.id, currentSongId, isPlaying)
                val displayed = SongMetadataState.apply(song)
                val menu = onItemMenu?.let { show -> { show(song) } }
                val enabled = isItemEnabled(song) && !song.isUnavailable
                val click = { onItemClick(song) }
                val onSurface = MaterialTheme.colorScheme.onSurface
                Row(
                    modifier =
                        Modifier
                            .width(columnWidth)
                            .heightIn(min = rowHeight)
                            .homeMediaClick(song, enabled, click, menu, cornerRadius = 22.dp, highlightSpill = 8.dp)
                            .currentSongSemantics(playback.isCurrent, playback.isPlaying),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MochyHomeArtwork(
                        item = displayed,
                        size = TrackArtworkSize,
                        shape = TrackArtworkShape,
                        isCurrent = playback.isCurrent,
                        isPlaying = playback.isPlaying,
                    )
                    Column(Modifier.weight(1f).padding(horizontal = 13.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ItemStatusBadges(displayed)
                            MochyHomeTitle(
                                title = displayed.title,
                                size = 15.sp,
                                modifier = Modifier.weight(1f, fill = false),
                                alpha = if (playback.isCurrent) 1f else 0.92f,
                            )
                        }
                        MochyHomeSubtitle(displayed.artists.joinToString { it.displayName })
                    }
                    if (menu != null) {
                        val menuLabel = stringResource(Res.string.more_options)
                        Box(
                            modifier = Modifier.size(38.dp).clip(CircleShape).clickable(role = Role.Button, onClick = menu),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                Modifier.size(24.dp).background(onSurface.copy(alpha = 0.14f), CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    MetrolistIcons.Default.MoreHoriz,
                                    contentDescription = menuLabel,
                                    tint = onSurface.copy(alpha = 0.85f),
                                    modifier = Modifier.size(17.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
        }
    }
}

@Composable
internal fun MochyHomeCoverShelf(
    sectionKey: String,
    items: List<YTItem>,
    onItemClick: (YTItem) -> Unit,
    onItemMenu: ((YTItem) -> Unit)?,
    isItemEnabled: (YTItem) -> Boolean,
    currentSongId: State<String?>,
    isPlaying: State<Boolean>,
) {
    val size = if (Settings.shared.gridItemSize == GridItemSize.BIG) 137.dp else 104.dp
    val seen = rememberRevealSeenState()
    val reveal = LocalRootContentActive.current && !LocalContentObscured.current && !rememberReducedMotion()
    val rowState = rememberLazyListState()
    DesktopHorizontalScrollControls(rowState) {
    LazyRow(
        state = rowState,
        contentPadding = PaddingValues(horizontal = HomeInset),
        horizontalArrangement = Arrangement.spacedBy(15.dp),
    ) {
        itemsIndexed(
            items,
            key = { index, item -> "$sectionKey/${canonicalHomeItemId(item)}/$index" },
            contentType = { _, _ -> "mochy_home_cover" },
        ) { index, item ->
            val playback by rememberItemPlayback(item.id, currentSongId, isPlaying)
            val displayed = (item as? SongItem)?.let(SongMetadataState::apply) ?: item
            val enabled = isItemEnabled(item) && !item.isUnavailable
            val menu = onItemMenu?.let { show -> { show(item) } }
            val click = { onItemClick(item) }
            val artwork = rememberAlbumArtworkSource(item, click.takeIf { enabled })
            val circular = item is ArtistItem
            val shape = if (circular) CircleShape else RoundedCornerShape(CoverRadius)
            val subtitle = displayed.homeSubtitle()
            Column(
                Modifier
                    .width(size)
                    .revealOnAppear(
                        key = "$sectionKey/${canonicalHomeItemId(item)}/$index",
                        seenState = seen,
                        enabled = reveal,
                        delayMillis = minOf(index, 4) * 60,
                    ).then(if (artwork != null) Modifier.navigationPageSource(artwork.token) else Modifier)
                    .homeMediaClick(item, enabled, click, menu, cornerRadius = CoverRadius + 6.dp, highlightSpill = 7.dp)
                    .currentSongSemantics(playback.isCurrent, playback.isPlaying),
            ) {
                MochyHomeArtwork(
                    item = displayed,
                    size = size,
                    shape = shape,
                    isCurrent = playback.isCurrent,
                    isPlaying = playback.isPlaying,
                    modifier = artwork?.modifier(CoverRadius) ?: Modifier,
                )
                Spacer(Modifier.height(8.dp))
                MochyHomeTitle(
                    title = displayed.title,
                    size = 14.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = if (circular) TextAlign.Center else TextAlign.Start,
                )
                if (subtitle.isNotBlank() || item is SongItem || item is AlbumItem) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ItemStatusBadges(displayed)
                        MochyHomeSubtitle(subtitle)
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun MochyHomeArtwork(
    item: YTItem,
    size: Dp,
    shape: Shape,
    isCurrent: Boolean,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(modifier.size(size).clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
        if (item is PlaylistItem) {
            PlaylistCoverImage(item, Modifier.fillMaxSize())
        } else {
            AsyncImage(item.thumbnail, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        if (item is SongItem && isCurrent) SongThumbnailPlayOverlay(isCurrent, isPlaying, shape)
    }
}

@Composable
private fun MochyHomeTitle(
    title: String,
    size: TextUnit,
    modifier: Modifier = Modifier,
    alpha: Float = 0.92f,
    textAlign: TextAlign = TextAlign.Start,
) {
    Text(
        text = title,
        modifier = modifier,
        fontFamily = mochyInterFontFamily(),
        fontWeight = FontWeight.SemiBold,
        fontSize = size,
        lineHeight = 17.sp,
        letterSpacing = (-0.03).em,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
        textAlign = textAlign,
    )
}

@Composable
private fun MochyHomeSubtitle(subtitle: String) {
    if (subtitle.isBlank()) return
    Text(
        text = subtitle,
        fontFamily = mochyInterFontFamily(),
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 15.sp,
        letterSpacing = (-0.02).em,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
    )
}

private fun YTItem.homeSubtitle(): String =
    when (this) {
        is SongItem -> artists.joinToString { it.displayName }
        is AlbumItem -> artists.joinToString { it.displayName }
        is ArtistItem -> subscribers.orEmpty()
        is PlaylistItem -> subtitle ?: author.orEmpty()
        else -> ""
    }

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Modifier.homeMediaClick(
    item: YTItem,
    enabled: Boolean,
    onClick: () -> Unit,
    menu: (() -> Unit)?,
    cornerRadius: Dp,
    highlightSpill: Dp,
): Modifier {
    val highlightColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val shape = remember(cornerRadius) { RoundedCornerShape(cornerRadius) }
    val highlight =
        remember(highlightColor, cornerRadius, highlightSpill) {
            RoundedHighlight(cornerRadius, spillX = highlightSpill, color = highlightColor)
        }
    val menuLabel = stringResource(Res.string.more_options)
    return alpha(if (enabled) 1f else 0.38f)
        .focusOutline(shape)
        .artworkMenuActions(menu, menuLabel)
        .combinedClickable(
            enabled = enabled || menu != null || item.isUnavailable,
            interactionSource = null,
            indication = highlight,
            role = Role.Button,
            onLongClick = menu,
            onLongClickLabel = menuLabel,
            onClick = {
                if (enabled) {
                    CollectionPreviews.record(item)
                    onClick()
                } else if (item.isUnavailable) {
                    UnavailableSongTaps.tapped()
                }
            },
        )
}
