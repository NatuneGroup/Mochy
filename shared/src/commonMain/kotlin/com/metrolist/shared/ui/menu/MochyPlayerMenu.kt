// Ported from Mochy Music by realidkroo (github.com/realidkroo/mochy-music). Licensed under GPL-3.0.

// Mochy's player menu: a black sheet with a grabber, the track's artwork beside its title, and each action as a dark row.

package com.metrolist.shared.ui.menu

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrolist.shared.generated.resources.Res
import com.metrolist.shared.generated.resources.action_like
import com.metrolist.shared.generated.resources.add_to_library
import com.metrolist.shared.generated.resources.add_to_playlist
import com.metrolist.shared.generated.resources.edit
import com.metrolist.shared.generated.resources.edit_lyrics
import com.metrolist.shared.generated.resources.equalizer
import com.metrolist.shared.generated.resources.lyrics
import com.metrolist.shared.generated.resources.mochy_menu_edit
import com.metrolist.shared.generated.resources.mochy_menu_equalizer
import com.metrolist.shared.generated.resources.mochy_menu_heart
import com.metrolist.shared.generated.resources.mochy_menu_heart_outline
import com.metrolist.shared.generated.resources.mochy_menu_library_add
import com.metrolist.shared.generated.resources.mochy_menu_library_add_check
import com.metrolist.shared.generated.resources.mochy_menu_lyrics
import com.metrolist.shared.generated.resources.mochy_menu_playlist_add
import com.metrolist.shared.generated.resources.mochy_menu_radio
import com.metrolist.shared.generated.resources.mochy_menu_share
import com.metrolist.shared.generated.resources.mochy_menu_sleep_timer
import com.metrolist.shared.generated.resources.mochy_menu_speed
import com.metrolist.shared.generated.resources.playback_speed
import com.metrolist.shared.generated.resources.remove_from_library
import com.metrolist.shared.generated.resources.share
import com.metrolist.shared.generated.resources.show_less
import com.metrolist.shared.generated.resources.show_more
import com.metrolist.shared.generated.resources.sleep_timer
import com.metrolist.shared.generated.resources.start_radio
import com.metrolist.shared.generated.resources.tempo_and_pitch
import com.metrolist.shared.generated.resources.unlike
import com.metrolist.shared.ui.components.AutoPlaylistThumbnail
import com.metrolist.shared.ui.components.ContainedLoadingIndicator
import com.metrolist.shared.ui.components.ExpandableText
import com.metrolist.shared.ui.components.PlayerMenuAction
import com.metrolist.shared.ui.components.images.AsyncImage
import com.metrolist.shared.ui.components.isAutoPlaylistIcon
import com.metrolist.shared.ui.theme.mochyInterFontFamily
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

// the reference's icon for each player action, keyed by the action's title; an action not listed keeps its own icon
internal val MochyMenuIcons: List<Pair<StringResource, DrawableResource>> =
    listOf(
        Res.string.start_radio to Res.drawable.mochy_menu_radio,
        Res.string.add_to_playlist to Res.drawable.mochy_menu_playlist_add,
        Res.string.share to Res.drawable.mochy_menu_share,
        Res.string.add_to_library to Res.drawable.mochy_menu_library_add,
        Res.string.remove_from_library to Res.drawable.mochy_menu_library_add_check,
        Res.string.action_like to Res.drawable.mochy_menu_heart_outline,
        Res.string.unlike to Res.drawable.mochy_menu_heart,
        Res.string.edit_lyrics to Res.drawable.mochy_menu_edit,
        Res.string.edit to Res.drawable.mochy_menu_edit,
        Res.string.sleep_timer to Res.drawable.mochy_menu_sleep_timer,
        Res.string.playback_speed to Res.drawable.mochy_menu_speed,
        Res.string.tempo_and_pitch to Res.drawable.mochy_menu_speed,
        Res.string.equalizer to Res.drawable.mochy_menu_equalizer,
        Res.string.lyrics to Res.drawable.mochy_menu_lyrics,
    )

internal object MochyMenuColors {
    val Surface = Color(0xFF0B0B0C)
    val RowSurface = Color(0xFF161619)
    val Secondary = Color(0xFFB3B3B3)
}

private val MochyMenuGutter = 20.dp
private val MochyMenuRowGap = 8.dp
private val MochyMenuSectionGap = 16.dp
private val MochyMenuArtwork = 52.dp

// the sheet's top is rounded 30dp, its grabber sits in a 38dp row with the bar 6dp low
internal val MochyMenuSheetCorner = 30.dp

// the reference's open and close: 440ms on its own ease curve
private val MochyMenuEasing = CubicBezierEasing(0.57f, 0.53f, 0f, 1f)
internal fun mochyMenuMotion(): AnimationSpec<Float> = tween(440, easing = MochyMenuEasing)
internal val MochyMenuHandleRow = 38.dp

@Composable
internal fun MochyMenuGrabber() {
    Box(Modifier.fillMaxWidth().height(MochyMenuHandleRow)) {
        Box(
            Modifier
                .align(Alignment.Center)
                .offset(y = 6.dp)
                .size(width = 120.dp, height = 5.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.7f)),
        )
    }
}

@Composable
internal fun MochyMenuHeader(
    title: String,
    subtitle: String?,
    subtitleLinks: AnnotatedString?,
    thumbnail: String?,
) {
    val font = mochyInterFontFamily()
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = MochyMenuGutter).padding(top = 16.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (thumbnail.isAutoPlaylistIcon()) {
            AutoPlaylistThumbnail(thumbnail, Modifier.size(MochyMenuArtwork), RoundedCornerShape(12.dp))
        } else {
            AsyncImage(
                url = thumbnail,
                contentDescription = null,
                modifier = Modifier.size(MochyMenuArtwork).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.08f)),
                contentScale = ContentScale.Crop,
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                fontFamily = font,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitleLinks != null) {
                Text(
                    text = subtitleLinks,
                    fontFamily = font,
                    fontSize = 12.sp,
                    color = MochyMenuColors.Secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    fontFamily = font,
                    fontSize = 12.sp,
                    color = MochyMenuColors.Secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
internal fun MochyMenuRows(
    actions: List<PlayerMenuAction>,
    modifier: Modifier = Modifier,
) {
    if (actions.isEmpty()) return
    // titles are resolved in the current language, so the lookup is by the resolved text
    val icons = MochyMenuIcons.associate { (title, icon) -> stringResource(title) to icon }
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = MochyMenuGutter),
        verticalArrangement = Arrangement.spacedBy(MochyMenuRowGap),
    ) {
        actions.forEach { action -> MochyMenuRow(action, icons[action.title]) }
    }
}

@Composable
private fun MochyMenuRow(
    action: PlayerMenuAction,
    reference: DrawableResource?,
) {
    val contentAlpha = if (action.enabled) 1f else 0.35f
    val font = mochyInterFontFamily()
    val iconTint =
        if (action.selected == true) {
            MaterialTheme.colorScheme.primary.copy(alpha = contentAlpha)
        } else {
            Color.White.copy(alpha = contentAlpha)
        }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MochyMenuColors.RowSurface)
                .clickable(enabled = action.enabled, onClick = action.onClick)
                .padding(horizontal = 14.dp, vertical = 14.dp),
    ) {
        if (action.isLoading) {
            ContainedLoadingIndicator(Modifier.size(20.dp), label = action.title)
        } else if (reference != null) {
            Icon(painterResource(reference), contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
        } else {
            Icon(action.icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = action.title,
                fontFamily = font,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = Color.White.copy(alpha = contentAlpha),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            action.description?.takeIf { it.isNotBlank() }?.let { description ->
                Text(
                    text = description,
                    fontFamily = font,
                    fontSize = 12.sp,
                    color = MochyMenuColors.Secondary.copy(alpha = contentAlpha),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// the same sections as the default menu, in the reference's order: header, then the header actions, primary actions and groups
@Composable
internal fun MochyMediaMenuSheetContent(
    title: String,
    subtitle: String?,
    subtitleLinks: AnnotatedString?,
    thumbnail: String?,
    description: String?,
    headerActions: List<PlayerMenuAction>,
    primaryActions: List<PlayerMenuAction>,
    actionGroups: List<List<PlayerMenuAction>>,
) {
    MochyMenuHeader(title = title, subtitle = subtitle, subtitleLinks = subtitleLinks, thumbnail = thumbnail)
    description?.takeIf(String::isNotBlank)?.let { text ->
        ExpandableText(
            text = text,
            showMoreText = stringResource(Res.string.show_more),
            showLessText = stringResource(Res.string.show_less),
            modifier = Modifier.padding(horizontal = MochyMenuGutter),
        )
    }
    MochyMenuRows(headerActions)
    if (headerActions.isNotEmpty()) Spacer(Modifier.height(MochyMenuSectionGap))
    MochyMenuRows(primaryActions)
    actionGroups.filter { it.isNotEmpty() }.forEach { actions ->
        Spacer(Modifier.height(MochyMenuSectionGap))
        MochyMenuRows(actions)
    }
    Spacer(Modifier.height(24.dp))
}
