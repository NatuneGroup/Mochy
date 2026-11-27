// Ported from Sora (github.com/shizenworks/sora), its namida player style after namida (github.com/namidaco/namida). Licensed under GPL-3.0.

// namida's track dialog, ported from its showGeneralPopupDialog (ui/dialogs/
// general_popup_dialog.dart): the same centred Material card (28dp corners, the
// inset margins), the same 0.92 scale-and-fade entrance, the same header (60dp
// artwork with 8dp corners, the title at 17 over the artist at 14 tinted with
// the current colour) and the same compact icon rows — opened by the miniplayer's
// more chip exactly where namida's onMenuOpen opens it. The rows are the ones
// namida's queue-sourced track dialog carries that sora has a backend for.

package com.metrolist.shared.ui.player.mochy.styles.namida

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.metrolist.shared.generated.resources.Res
import com.metrolist.shared.generated.resources.action_like
import com.metrolist.shared.generated.resources.add_to_queue
import com.metrolist.shared.generated.resources.more_options
import com.metrolist.shared.generated.resources.namida_close_circle
import com.metrolist.shared.generated.resources.namida_document
import com.metrolist.shared.generated.resources.namida_heart
import com.metrolist.shared.generated.resources.namida_heart_filled
import com.metrolist.shared.generated.resources.namida_more
import com.metrolist.shared.generated.resources.namida_play
import com.metrolist.shared.generated.resources.namida_remove_like
import com.metrolist.shared.generated.resources.namida_row_vertical
import com.metrolist.shared.generated.resources.namida_search_normal
import com.metrolist.shared.generated.resources.namida_sound
import com.metrolist.shared.generated.resources.play_next
import com.metrolist.shared.ui.player.mochy.styles.LocalStyleTrack
import com.metrolist.shared.ui.player.mochy.styles.StyleActions
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** the track dialog namida's more chip opens: its card, its header, its rows. */
@Composable
fun NamidaTrackMenu(
    actions: StyleActions,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val connection = rememberNamidaConnection() ?: return
    val palette = LocalNamidaPalette.current
    val track = LocalStyleTrack.current
    val metadata = connection.currentSong()
    val liked = connection.liked()

    // namida's dialog: the page behind dims, the card comes up from 0.92 scale
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        appear.animateTo(1f, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow))
    }
    var dismissed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun close() {
        if (dismissed) return
        dismissed = true
        scope.launch {
            appear.animateTo(0f, tween(220))
            onDismiss()
        }
    }

    Box(
        modifier
            .fillMaxSize()
            .graphicsLayer { alpha = appear.value }
            .background(Color.Black.copy(alpha = 0.5f * appear.value))
            .pointerInput(Unit) {
                detectTapGestures { close() }
            },
        contentAlignment = Alignment.Center,
    ) {
        // namida's Dialog inset: a horizontal margin around a bounded card
        Column(
            modifier =
                Modifier
                    .padding(horizontal = 34.dp, vertical = 24.dp)
                    .widthIn(max = 420.dp)
                    .graphicsLayer {
                        val s = 0.92f + 0.08f * appear.value
                        scaleX = s
                        scaleY = s
                        alpha = appear.value
                    }.clip(RoundedCornerShape(28.dp))
                    .background(lerp(palette.scaffold, palette.currentColor, 0.08f))
                    .clickable(enabled = false, onClick = {}),
        ) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                // ── the header: artwork, title, artist, third line ──
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 14.dp),
                ) {
                    Spacer(Modifier.width(16.dp))
                    if (track.artwork != null) {
                        AsyncImage(
                            model = track.artwork,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier =
                                Modifier
                                    .size(60.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                        )
                    } else {
                        Box(
                            Modifier
                                .size(60.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(palette.secondaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painter = painterResource(Res.drawable.namida_sound),
                                contentDescription = null,
                                tint = palette.onSecondaryContainer,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = track.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 17.sp,
                            lineHeight = 20.sp,
                            fontWeight = FontWeight.W600,
                            fontFamily = NamidaFontFamily,
                            color = palette.currentColor.copy(alpha = 0.16f).compositeOver(palette.textMedium),
                        )
                        if (track.artist.isNotBlank()) {
                            Text(
                                text = track.artist,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 14.sp,
                                lineHeight = 15.sp,
                                fontFamily = NamidaFontFamily,
                                color = palette.currentColor.copy(alpha = 0.31f).compositeOver(palette.textMedium),
                            )
                        }
                        if (track.album.isNotBlank()) {
                            Text(
                                text = track.album,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 12.5.sp,
                                lineHeight = 13.sp,
                                fontFamily = NamidaFontFamily,
                                color = palette.currentColor.copy(alpha = 0.16f).compositeOver(palette.textMedium),
                            )
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                }

                // ── the rows, namida's compact icon tiles ──
                val iconTint = palette.currentColor.copy(alpha = 0.47f).compositeOver(palette.textMedium)
                val titleTint = palette.currentColor.copy(alpha = 0.16f).compositeOver(palette.textMedium)

                @Composable fun row(
                    label: String,
                    icon: DrawableResource,
                    closeAfter: Boolean = true,
                    tint: Color = titleTint,
                    iconColor: Color = iconTint,
                    onClick: () -> Unit,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .clickable {
                                    onClick()
                                    if (closeAfter) close()
                                },
                    ) {
                        Spacer(Modifier.width(16.dp))
                        Icon(
                            painter = painterResource(icon),
                            contentDescription = null,
                            tint = iconColor,
                            modifier = Modifier.size(22.dp),
                        )
                        Spacer(Modifier.width(14.dp))
                        Text(
                            text = label,
                            fontSize = 14.5.sp,
                            fontFamily = NamidaFontFamily,
                            color = tint,
                        )
                    }
                }

                if (metadata != null) {
                    row(stringResource(Res.string.play_next), Res.drawable.namida_play) {
                        connection.playNext(metadata)
                    }
                    row(stringResource(Res.string.add_to_queue), Res.drawable.namida_row_vertical) {
                        connection.addToQueue(metadata)
                    }
                    row(
                        stringResource(if (liked) Res.string.namida_remove_like else Res.string.action_like),
                        if (liked) Res.drawable.namida_heart_filled else Res.drawable.namida_heart,
                        closeAfter = false,
                    ) {
                        connection.toggleLike()
                    }
                    // adding to a playlist, downloading, the details and the album and artist pages live in the app's
                    // own song menu, which this row opens
                    row(stringResource(Res.string.more_options), Res.drawable.namida_more) {
                        actions.onOtherMenu()
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}
