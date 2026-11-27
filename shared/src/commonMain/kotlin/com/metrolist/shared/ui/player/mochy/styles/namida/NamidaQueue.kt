// Ported from Sora (github.com/shizenworks/sora), its namida player style after namida (github.com/namidaco/namida). Licensed under GPL-3.0.

// namida player style — the queue page, ported from namida's player: the queue
// that slides up over the player (miniplayer_base's queue child +
// ui/pages/current_queue_page.dart): the 32-radius rounded sheet, its accent
// card gradient behind the chips header and search box, the 90-extent tiles
// (72 tall, 64 thumbnail), the floating jump button at 12/12 and the bottom
// utils row (48 tall, 12-radius top).

package com.metrolist.shared.ui.player.mochy.styles.namida

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.metrolist.shared.generated.resources.Res
import com.metrolist.shared.generated.resources.namida_arrow_down_2
import com.metrolist.shared.generated.resources.namida_arrow_up
import com.metrolist.shared.generated.resources.namida_close_circle
import com.metrolist.shared.generated.resources.namida_pause
import com.metrolist.shared.generated.resources.namida_play
import com.metrolist.shared.generated.resources.namida_queue
import com.metrolist.shared.generated.resources.namida_queue_items
import com.metrolist.shared.generated.resources.namida_search_normal
import com.metrolist.shared.generated.resources.search
import com.metrolist.shared.ui.player.mochy.styles.StyleActions
import com.metrolist.shared.ui.player.mochy.styles.styleInsets
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun NamidaQueue(
    visibleFraction: Float,
    actions: StyleActions,
    preview: Boolean,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalNamidaPalette.current
    val connection = rememberNamidaConnection()
    val queueItems = connection?.queueItems().orEmpty()
    val windowIndex = connection?.currentWindowIndex() ?: -1
    val playing = connection?.isPlaying?.collectAsState()?.value == true
    var searchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val jumpScope = androidx.compose.runtime.rememberCoroutineScope()

    // namida's queue follower: open the list at the playing song
    LaunchedEffect(queueItems.size, windowIndex) {
        if (windowIndex in queueItems.indices) {
            listState.scrollToItem(windowIndex.coerceAtMost(queueItems.size - 1))
        }
    }

    Box(
        modifier
            .graphicsLayer {
                translationY = (1f - visibleFraction) * size.height
                alpha = visibleFraction.coerceIn(0f, 1f)
            },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    shape = RoundedCornerShape(topStart = NamidaQueueTopRadius, topEnd = NamidaQueueTopRadius)
                    clip = true
                }.background(palette.scaffold),
        ) {
            // the accent card behind the header and search box
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(palette.accentCard),
            ) {
                // the chips header row: the queue chip, collapse arrow, search
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    QueueChip(text = stringResource(Res.string.namida_queue), active = true)
                    Spacer(Modifier.weight(1f))
                    Icon(
                        painter = painterResource(Res.drawable.namida_arrow_down_2),
                        contentDescription = null,
                        tint = palette.onSecondaryContainer,
                        modifier =
                            Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .clickable(enabled = !preview) { onCollapse() }
                                .padding(7.dp),
                    )
                    Icon(
                        painter = painterResource(Res.drawable.namida_search_normal),
                        contentDescription = null,
                        tint = palette.onSecondaryContainer,
                        modifier =
                            Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .clickable(enabled = !preview) {
                                    searchOpen = !searchOpen
                                    if (!searchOpen) searchQuery = ""
                                }.padding(7.dp),
                    )
                }
                // the search box (QueueSearchBox)
                if (searchOpen) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(palette.secondaryContainer.copy(alpha = 0.4f))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.namida_search_normal),
                            contentDescription = null,
                            tint = palette.textSmall,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Box(Modifier.weight(1f)) {
                            if (searchQuery.isEmpty()) {
                                Text(
                                    stringResource(Res.string.search),
                                    color = palette.textSmall,
                                    fontSize = 14.sp,
                                    fontFamily = NamidaFontFamily,
                                )
                            }
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                singleLine = true,
                                textStyle =
                                    androidx.compose.ui.text.TextStyle(
                                        color = palette.textLarge,
                                        fontSize = 14.sp,
                                        fontFamily = NamidaFontFamily,
                                    ),
                                cursorBrush = SolidColor(palette.currentColor),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        if (searchQuery.isNotEmpty()) {
                            Icon(
                                painter = painterResource(Res.drawable.namida_close_circle),
                                contentDescription = null,
                                tint = palette.textSmall,
                                modifier =
                                    Modifier
                                        .size(20.dp)
                                        .clickable { searchQuery = "" },
                            )
                        }
                    }
                }
            }

            // the tiles
            val shown =
                if (searchQuery.isBlank()) {
                    queueItems
                } else {
                    queueItems.filter {
                        it.title.contains(searchQuery, ignoreCase = true) || it.artist.contains(searchQuery, ignoreCase = true)
                    }
                }
            Box(Modifier.weight(1f)) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding =
                        androidx.compose.foundation.layout
                            .PaddingValues(top = 6.dp, bottom = 12.dp),
                ) {
                    items(shown.size, key = { i -> shown[i].uid }) { i ->
                        val item = shown[i]
                        val realIndex = queueItems.indexOf(item)
                        NamidaQueueTile(
                            title = item.title,
                            artist = item.artist,
                            artwork = item.artworkUri?.toString(),
                            duration = item.duration,
                            isCurrent = realIndex == windowIndex,
                            isPlaying = playing && realIndex == windowIndex,
                            onClick = {
                                if (!preview && realIndex >= 0) {
                                    connection?.playQueueItem(item)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                // the floating jump button (12/12), back to the playing song
                Icon(
                    painter = painterResource(Res.drawable.namida_arrow_up),
                    contentDescription = null,
                    tint = palette.onSecondaryContainer,
                    modifier =
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(12.dp)
                            .size(40.dp)
                            .shadow(6.dp, CircleShape, ambientColor = palette.playShadow, spotColor = palette.playShadow)
                            .clip(CircleShape)
                            .background(palette.scaffold)
                            .clickable(enabled = !preview) {
                                if (windowIndex in queueItems.indices) {
                                    jumpScope.launch { listState.animateScrollToItem(windowIndex) }
                                }
                            }.padding(10.dp),
                )
            }

            // the bottom utils row: count and length (QueueUtilsRow)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
                            clip = true
                        }.background(palette.scaffold)
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .height(NamidaQueueBottomRowHeight),
                horizontalArrangement = Arrangement.End,
            ) {
                val totalMs = shown.sumOf { it.duration }
                Text(
                    "${shown.size} ${stringResource(Res.string.namida_queue_items)} • ${makeTimeString(totalMs)}",
                    color = palette.textSmall,
                    fontSize = 12.sp,
                    fontFamily = NamidaFontFamily,
                )
                Spacer(Modifier.width(12.dp))
            }
        }
    }
}

@Composable
private fun QueueChip(
    text: String,
    active: Boolean,
) {
    val palette = LocalNamidaPalette.current
    Text(
        text,
        color = if (active) palette.scaffold else palette.onSecondaryContainer,
        fontSize = 13.sp,
        fontWeight = FontWeight.W600,
        fontFamily = NamidaFontFamily,
        modifier =
            Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(if (active) palette.currentColor else palette.secondaryContainer)
                .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

/** namida's queue tile: 72 tall, 64 rounded thumbnail, 15/13 text, 90 extent. */
@Composable
private fun NamidaQueueTile(
    title: String,
    artist: String,
    artwork: String?,
    duration: Long,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalNamidaPalette.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .height(NamidaQueueItemExtent)
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .size(NamidaQueueThumbnailSize)
                    .clip(RoundedCornerShape(8.dp)),
        ) {
            AsyncImage(
                model = artwork,
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (isCurrent) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(palette.scaffold.copy(alpha = 0.4f)),
                )
                Icon(
                    painter = painterResource(if (isPlaying) Res.drawable.namida_pause else Res.drawable.namida_play),
                    contentDescription = null,
                    tint = palette.currentColor,
                    modifier = Modifier.size(26.dp),
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (isCurrent) palette.currentColor else palette.textMedium,
                fontSize = 15.sp,
                fontWeight = FontWeight.W600,
                fontFamily = NamidaFontFamily,
            )
            Text(
                artist,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = palette.textSmall,
                fontSize = 13.sp,
                fontWeight = FontWeight.W400,
                fontFamily = NamidaFontFamily,
            )
        }
        Text(
            makeTimeString(duration),
            color = palette.textSmall,
            fontSize = 13.sp,
            fontFamily = NamidaFontFamily,
        )
    }
}
