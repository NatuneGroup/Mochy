// Ported from Mochy Music by realidkroo (github.com/realidkroo/mochy-music). Licensed under GPL-3.0.

// Mochy's "Recommended for you" card at the top of the settings list: two tips for things that aren't set up yet.
// Only tips whose setting exists in the shared settings are kept.

package com.metrolist.shared.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrolist.shared.generated.resources.Res
import com.metrolist.shared.generated.resources.mochy_settings_cast_connected
import com.metrolist.shared.generated.resources.mochy_settings_crumb
import com.metrolist.shared.generated.resources.mochy_settings_equalizer
import com.metrolist.shared.generated.resources.mochy_settings_rec_bluetooth
import com.metrolist.shared.generated.resources.mochy_settings_rec_canvas
import com.metrolist.shared.generated.resources.mochy_settings_rec_crossfade
import com.metrolist.shared.generated.resources.mochy_settings_rec_devices
import com.metrolist.shared.generated.resources.mochy_settings_rec_discord
import com.metrolist.shared.generated.resources.mochy_settings_rec_equalizer
import com.metrolist.shared.generated.resources.mochy_settings_rec_lastfm
import com.metrolist.shared.generated.resources.mochy_settings_rec_linear_scale
import com.metrolist.shared.generated.resources.mochy_settings_rec_music_history
import com.metrolist.shared.generated.resources.mochy_settings_rec_pause_muted
import com.metrolist.shared.generated.resources.mochy_settings_rec_sparks
import com.metrolist.shared.generated.resources.mochy_settings_rec_spatial
import com.metrolist.shared.generated.resources.mochy_settings_rec_spatial_tracking_apple
import com.metrolist.shared.generated.resources.mochy_settings_rec_volume_off_pause
import com.metrolist.shared.generated.resources.mochy_settings_recommended_item
import com.metrolist.shared.settings.Settings
import com.metrolist.shared.ui.theme.mochyInterFontFamily
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

internal class MochyRecommendation(
    val titleRes: StringResource,
    val pageId: SettingsPageId,
    // the reference's drawable for this tip
    val icon: DrawableResource,
    // the setting that flashes on the page it opens, the same id search results use
    val highlight: String? = null,
    // false once there's nothing left to suggest, like a switch that's already on
    val applies: () -> Boolean = { true },
)

private val MochyRecommendations =
    listOf(
        MochyRecommendation(Res.string.mochy_settings_rec_equalizer, SettingsPageId.EQUALIZER, Res.drawable.mochy_settings_equalizer) {
            !Settings.shared.equalizerEnabled
        },
        MochyRecommendation(
            Res.string.mochy_settings_rec_spatial,
            SettingsPageId.AUDIO_LAB,
            Res.drawable.mochy_settings_rec_spatial_tracking_apple,
        ) {
            !Settings.shared.spatialEnabled
        },
        MochyRecommendation(
            Res.string.mochy_settings_rec_crossfade,
            SettingsPageId.PLAYER,
            Res.drawable.mochy_settings_rec_linear_scale,
            "crossfadeEnabled",
        ) {
            !Settings.shared.crossfadeEnabled
        },
        MochyRecommendation(
            Res.string.mochy_settings_rec_bluetooth,
            SettingsPageId.PLAYER,
            Res.drawable.mochy_settings_rec_bluetooth,
            "resumeOnBluetoothConnect",
        ) {
            !Settings.shared.resumeOnBluetoothConnect
        },
        MochyRecommendation(
            Res.string.mochy_settings_rec_pause_muted,
            SettingsPageId.PLAYER,
            Res.drawable.mochy_settings_rec_volume_off_pause,
            "pauseOnMute",
        ) {
            !Settings.shared.pauseOnMute
        },
        MochyRecommendation(
            Res.string.mochy_settings_rec_canvas,
            SettingsPageId.PLAYER_ARTWORK,
            Res.drawable.mochy_settings_rec_sparks,
            "playerCanvasEnabled",
        ) {
            !Settings.shared.playerCanvasEnabled
        },
        MochyRecommendation(Res.string.mochy_settings_rec_lastfm, SettingsPageId.LAST_FM, Res.drawable.mochy_settings_rec_music_history) {
            Settings.shared.lastFmSession.isBlank()
        },
        MochyRecommendation(Res.string.mochy_settings_rec_discord, SettingsPageId.DISCORD, Res.drawable.mochy_settings_rec_discord) {
            !Settings.shared.enableDiscordIntegration
        },
        MochyRecommendation(
            Res.string.mochy_settings_rec_devices,
            SettingsPageId.DEVICE_CONNECT,
            Res.drawable.mochy_settings_cast_connected,
        ),
    )

// two tips at a time, never two for the same page so the card stays varied
internal fun pickMochyRecommendations(
    registry: SettingsUiRegistry,
    count: Int = 2,
): List<MochyRecommendation> =
    MochyRecommendations
        .filter { registry[it.pageId] != null && it.applies() }
        .shuffled()
        .distinctBy { it.pageId }
        .take(count)

@Composable
internal fun MochyRecommendedCard(
    recommendations: List<MochyRecommendation>,
    registry: SettingsUiRegistry,
    onOpen: (SettingsPageId, String?) -> Unit,
) {
    if (recommendations.isEmpty()) return
    val font = mochyInterFontFamily()
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(MochySettingsColors.Bubble)
                .padding(horizontal = 8.dp, vertical = 10.dp),
    ) {
        Text(
            text = stringResource(Res.string.mochy_settings_recommended_item),
            fontFamily = font,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
            color = Color.White.copy(alpha = 0.55f),
            modifier = Modifier.padding(start = 10.dp, top = 8.dp, bottom = 4.dp),
        )
        recommendations.forEachIndexed { index, recommendation ->
            val page = registry[recommendation.pageId] ?: return@forEachIndexed
            if (index > 0) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 66.dp, end = 10.dp)
                            .height(0.8.dp)
                            .background(Color.White.copy(alpha = 0.08f)),
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onOpen(recommendation.pageId, recommendation.highlight) }
                        .padding(10.dp),
            ) {
                Box(
                    modifier = Modifier.size(44.dp).clip(CircleShape).background(MochySettingsColors.IconBackground),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(recommendation.icon),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = stringResource(recommendation.titleRes),
                        fontFamily = font,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(Res.string.mochy_settings_crumb, page.title),
                        fontFamily = font,
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.55f),
                    )
                }
            }
        }
    }
}
