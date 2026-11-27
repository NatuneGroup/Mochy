// Ported from Mochy Music by realidkroo (github.com/realidkroo/mochy-music). Licensed under GPL-3.0.

// mochy → settings → integrations: a row per service, tapping one opens its own page

package com.metrolist.shared.ui.settings

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrolist.shared.generated.resources.Res
import com.metrolist.shared.generated.resources.integration_backup_desc
import com.metrolist.shared.generated.resources.integration_discord_desc
import com.metrolist.shared.generated.resources.integration_scrobbling_desc
import com.metrolist.shared.generated.resources.mochy_integration_discord
import com.metrolist.shared.generated.resources.mochy_integration_lastfm
import com.metrolist.shared.generated.resources.mochy_integration_youtube_music
import com.metrolist.shared.generated.resources.mochy_set_backup
import com.metrolist.shared.generated.resources.mochy_set_integration
import com.metrolist.shared.generated.resources.mochy_set_person
import com.metrolist.shared.generated.resources.module_youtube_desc
import com.metrolist.shared.ui.theme.mochyInterFontFamily
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private val CardColor = Color(0xFF232323)
private val CardShape = RoundedCornerShape(16.dp)
private val IconShape = RoundedCornerShape(8.dp)

// the youtube music logo is a red disc, this fills its corners so it sits as a tile like the others
private val YouTubeMusicRed = Color(0xFFFF0000)

// backup has no brand of its own, so its white cloud sits on a plain blue tile
private val BackupBlue = Color(0xFF2F7CF6)

// the services Mochy has no logo for get its plain glyph on a neutral tile
private val PlainTile = Color(0xFF3A3A3C)

@Composable
internal fun MochyIntegrationsPage(onNavigate: (SettingsPageId) -> Unit) {
    val context = LocalMochySettingsPageContext.current ?: return
    val registry = context.registry
    val youtubeAccount =
        registry[SettingsPageId.CONTENT]
            ?.groups
            .orEmpty()
            .flatMap { it.items }
            .firstOrNull { it.id == "switchYouTubeChannel" }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Spacer(Modifier.height(LocalSettingsPageTopPadding.current))
        registry[SettingsPageId.DISCORD]?.let { page ->
            IntegrationRow(
                name = page.title,
                description = stringResource(Res.string.integration_discord_desc),
                icon = Res.drawable.mochy_integration_discord,
                onClick = { onNavigate(page.id) },
            )
        }
        registry[SettingsPageId.LAST_FM]?.let { page ->
            IntegrationRow(
                name = page.title,
                description = stringResource(Res.string.integration_scrobbling_desc, page.title),
                icon = Res.drawable.mochy_integration_lastfm,
                onClick = { onNavigate(page.id) },
            )
        }
        registry[SettingsPageId.LISTEN_BRAINZ]?.let { page ->
            IntegrationRow(
                name = page.title,
                description = stringResource(Res.string.integration_scrobbling_desc, page.title),
                icon = Res.drawable.mochy_set_integration,
                iconBackground = PlainTile,
                iconPadding = 7.dp,
                tint = Color.White,
                onClick = { onNavigate(page.id) },
            )
        }
        registry[SettingsPageId.LISTEN_TOGETHER]?.let { page ->
            IntegrationRow(
                name = page.title,
                description = page.subtitle.orEmpty(),
                icon = Res.drawable.mochy_set_person,
                iconBackground = PlainTile,
                iconPadding = 7.dp,
                tint = Color.White,
                onClick = { onNavigate(page.id) },
            )
        }
        if (youtubeAccount != null) {
            IntegrationRow(
                name = youtubeAccount.title,
                description = stringResource(Res.string.module_youtube_desc),
                icon = Res.drawable.mochy_integration_youtube_music,
                iconBackground = YouTubeMusicRed,
                onClick = { youtubeAccount.onClick?.invoke() },
            )
        }
        registry[SettingsPageId.BACKUP_RESTORE]?.let { page ->
            IntegrationRow(
                name = page.title,
                description = stringResource(Res.string.integration_backup_desc),
                icon = Res.drawable.mochy_set_backup,
                iconBackground = BackupBlue,
                iconPadding = 7.dp,
                tint = Color.White,
                onClick = { onNavigate(page.id) },
            )
        }
        Spacer(Modifier.height(LocalSettingsPageBottomPadding.current + 140.dp))
    }
}

/** one service on the integrations page: its app icon, its name and one short line about it */
@Composable
private fun IntegrationRow(
    name: String,
    description: String,
    icon: DrawableResource,
    onClick: () -> Unit,
    // the colour behind the icon, for a round logo that should still read as a square tile
    iconBackground: Color = Color.Transparent,
    // room around a plain glyph, so it sits inside its tile instead of filling it like a logo does
    iconPadding: Dp = 0.dp,
    // a plain glyph is drawn in one colour; a logo keeps its own
    tint: Color? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .clip(CardShape)
                .background(CardColor)
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(34.dp)
                    .clip(IconShape)
                    .background(iconBackground)
                    .padding(iconPadding),
            contentAlignment = Alignment.Center,
        ) {
            if (tint != null) {
                Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.fillMaxSize())
            } else {
                Image(
                    painter = painterResource(icon),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Column(Modifier.padding(start = 12.dp)) {
            Text(
                text = name,
                color = Color.White,
                fontFamily = mochyInterFontFamily(),
                fontSize = 15.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (description.isNotEmpty()) {
                Text(
                    text = description,
                    color = Color.White.copy(alpha = 0.55f),
                    fontFamily = mochyInterFontFamily(),
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
