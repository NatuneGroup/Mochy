// Ported and adapted from Mochy Music by realidkroo. Licensed under GPL-3.0.

package com.metrolist.shared.ui.audiolab

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.metrolist.shared.audiolab.SpatialMode
import com.metrolist.shared.audiolab.spatialSettings
import com.metrolist.shared.generated.resources.Res
import com.metrolist.shared.generated.resources.audio_lab_amount
import com.metrolist.shared.generated.resources.audio_lab_spatial
import com.metrolist.shared.generated.resources.audio_lab_spatial_angle
import com.metrolist.shared.generated.resources.audio_lab_spatial_cutoff
import com.metrolist.shared.generated.resources.audio_lab_spatial_description
import com.metrolist.shared.generated.resources.audio_lab_spatial_feed
import com.metrolist.shared.generated.resources.audio_lab_spatial_mode_crossfeed
import com.metrolist.shared.generated.resources.audio_lab_spatial_mode_speakers
import com.metrolist.shared.generated.resources.mochy_lab_xf_light
import com.metrolist.shared.generated.resources.mochy_lab_xf_natural
import com.metrolist.shared.generated.resources.mochy_lab_xf_strong
import com.metrolist.shared.settings.Settings
import org.jetbrains.compose.resources.StringResource
import kotlin.math.roundToInt

@Composable
internal fun SpatialCard() {
    val spatial = Settings.shared.spatialSettings()
    EffectCard(
        picture = { SpatialPicture(spatial) },
        header = {
            LabToggleRow(
                title = stringResource(Res.string.audio_lab_spatial),
                subtitle = stringResource(Res.string.audio_lab_spatial_description),
                checked = spatial.enabled,
                onCheckedChange = { on -> saveSpatial(spatial.copy(enabled = on)) },
            )
        },
        expanded = spatial.enabled,
        options = {
            LabSegmented(
                options = listOf(
                    stringResource(Res.string.audio_lab_spatial_mode_crossfeed),
                    stringResource(Res.string.audio_lab_spatial_mode_speakers),
                ),
                selectedIndex = if (spatial.mode == SpatialMode.Crossfeed) 0 else 1,
                onSelect = { i ->
                    saveSpatial(spatial.copy(mode = if (i == 0) SpatialMode.Crossfeed else SpatialMode.Speakers))
                },
                height = 42.dp,
            )
            Spacer(Modifier.height(10.dp))
            if (spatial.mode == SpatialMode.Crossfeed) {
                LabChips(
                    chips = CrossfeedLevels.map { LabChip(it.key, stringResource(it.label)) },
                    selectedKey = CrossfeedLevels.firstOrNull { it.cutoff == spatial.cutoffHz && it.feed == spatial.feedDb }?.key,
                    onSelect = { key ->
                        CrossfeedLevels.firstOrNull { it.key == key }?.let { level ->
                            saveSpatial(spatial.copy(cutoffHz = level.cutoff, feedDb = level.feed))
                        }
                    },
                )
                Spacer(Modifier.height(10.dp))
                LabPanel {
                    LabSlider(
                        label = stringResource(Res.string.audio_lab_spatial_cutoff),
                        valueText = hertz(spatial.cutoffHz),
                        value = spatial.cutoffHz,
                        valueRange = 300f..1500f,
                        onValueChange = { saveSpatial(spatial.copy(cutoffHz = (it / 10).roundToInt() * 10f)) },
                    )
                    // the slider runs from the quiet end to the strong end, the way the crossfeed level reads
                    LabSlider(
                        label = stringResource(Res.string.audio_lab_spatial_feed),
                        valueText = decibels(spatial.feedDb),
                        value = 16f - spatial.feedDb,
                        valueRange = 1f..15f,
                        onValueChange = { saveSpatial(spatial.copy(feedDb = ((16f - it) * 2).roundToInt() / 2f)) },
                    )
                    LabSlider(
                        label = stringResource(Res.string.audio_lab_amount),
                        valueText = percent(spatial.amount),
                        value = spatial.amount,
                        valueRange = 0f..1f,
                        onValueChange = { saveSpatial(spatial.copy(amount = it)) },
                    )
                }
            } else {
                LabPanel {
                    LabSlider(
                        label = stringResource(Res.string.audio_lab_spatial_angle),
                        valueText = degrees(spatial.speakerAngle),
                        value = spatial.speakerAngle,
                        valueRange = 10f..90f,
                        onValueChange = { saveSpatial(spatial.copy(speakerAngle = it.roundToInt().toFloat())) },
                    )
                    LabSlider(
                        label = stringResource(Res.string.audio_lab_amount),
                        valueText = percent(spatial.amount),
                        value = spatial.amount,
                        valueRange = 0f..1f,
                        onValueChange = { saveSpatial(spatial.copy(amount = it)) },
                    )
                }
            }
        },
    )
}

/** the crossfeed presets: how far the head shadow sits and how much quieter the other ear is */
private class CrossfeedLevel(val key: String, val label: StringResource, val cutoff: Float, val feed: Float)

private val CrossfeedLevels = listOf(
    CrossfeedLevel("light", Res.string.mochy_lab_xf_light, 700f, 4.5f),
    CrossfeedLevel("natural", Res.string.mochy_lab_xf_natural, 700f, 6f),
    CrossfeedLevel("strong", Res.string.mochy_lab_xf_strong, 650f, 9.5f),
)
