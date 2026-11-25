// Ported and adapted from Mochy Music by realidkroo. Licensed under GPL-3.0.

package com.metrolist.shared.ui.audiolab

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.metrolist.shared.audiolab.EightDSettings
import com.metrolist.shared.audiolab.ReverbPresets
import com.metrolist.shared.audiolab.ReverbSettings
import com.metrolist.shared.audiolab.eightDSettings
import com.metrolist.shared.audiolab.reverbSettings
import com.metrolist.shared.audiolab.withPreset
import com.metrolist.shared.generated.resources.Res
import com.metrolist.shared.generated.resources.audio_lab_android_only
import com.metrolist.shared.generated.resources.audio_lab_damping
import com.metrolist.shared.generated.resources.audio_lab_depth
import com.metrolist.shared.generated.resources.audio_lab_dry
import com.metrolist.shared.generated.resources.audio_lab_eightd
import com.metrolist.shared.generated.resources.audio_lab_eightd_description
import com.metrolist.shared.generated.resources.audio_lab_eightd_preset_balanced
import com.metrolist.shared.generated.resources.audio_lab_eightd_preset_chill
import com.metrolist.shared.generated.resources.audio_lab_eightd_preset_extreme
import com.metrolist.shared.generated.resources.audio_lab_hz
import com.metrolist.shared.generated.resources.audio_lab_hz_decimal
import com.metrolist.shared.generated.resources.audio_lab_low_cut
import com.metrolist.shared.generated.resources.audio_lab_mix
import com.metrolist.shared.generated.resources.audio_lab_ms
import com.metrolist.shared.generated.resources.audio_lab_percent
import com.metrolist.shared.generated.resources.audio_lab_pre_delay
import com.metrolist.shared.generated.resources.audio_lab_preset_bathroom
import com.metrolist.shared.generated.resources.audio_lab_preset_cathedral
import com.metrolist.shared.generated.resources.audio_lab_preset_dream
import com.metrolist.shared.generated.resources.audio_lab_preset_hall
import com.metrolist.shared.generated.resources.audio_lab_preset_plate
import com.metrolist.shared.generated.resources.audio_lab_preset_room
import com.metrolist.shared.generated.resources.audio_lab_preset_stadium
import com.metrolist.shared.generated.resources.audio_lab_preset_studio
import com.metrolist.shared.generated.resources.audio_lab_reverb
import com.metrolist.shared.generated.resources.audio_lab_reverb_description
import com.metrolist.shared.generated.resources.audio_lab_room_size
import com.metrolist.shared.generated.resources.audio_lab_rotation_speed
import com.metrolist.shared.generated.resources.audio_lab_wet
import com.metrolist.shared.generated.resources.audio_lab_width
import com.metrolist.shared.settings.Settings
import com.metrolist.shared.ui.settings.SettingsMainSwitch
import com.metrolist.shared.ui.settings.settingsRowContainerColor
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

/** Audio Lab settings: the reverb and 8D cards. The controls stay disabled where the effects are not wired yet. */
@Composable
fun AudioLabScreen(
    available: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (!available) {
            Text(
                text = stringResource(Res.string.audio_lab_android_only),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ReverbCard(available)
        EightDCard(available)
    }
}

@Composable
private fun ReverbCard(available: Boolean) {
    val reverb = Settings.shared.reverbSettings()
    val presetId = Settings.shared.reverbPresetId
    val active = available && reverb.enabled
    AudioLabCard {
        SettingsMainSwitch(
            title = stringResource(Res.string.audio_lab_reverb),
            checked = reverb.enabled,
            onCheckedChange = { saveReverb(reverb.copy(enabled = it), presetId) },
            enabled = available,
        )
        Text(
            text = stringResource(Res.string.audio_lab_reverb_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ReverbPresets.All.forEach { preset ->
                FilterChip(
                    selected = presetId == preset.id,
                    onClick = { saveReverb(reverb.withPreset(preset), preset.id) },
                    label = { Text(stringResource(reverbPresetName(preset.id))) },
                    enabled = active,
                )
            }
        }
        SliderRow(stringResource(Res.string.audio_lab_mix), percent(reverb.mix), reverb.mix, 0f..1f, active) {
            saveReverb(reverb.copy(mix = it))
        }
        SliderRow(stringResource(Res.string.audio_lab_room_size), percent(reverb.roomSize), reverb.roomSize, 0f..1f, active) {
            saveReverb(reverb.copy(roomSize = it))
        }
        SliderRow(stringResource(Res.string.audio_lab_damping), percent(reverb.damping), reverb.damping, 0f..1f, active) {
            saveReverb(reverb.copy(damping = it))
        }
        SliderRow(stringResource(Res.string.audio_lab_wet), percent(reverb.wet), reverb.wet, 0f..1f, active) {
            saveReverb(reverb.copy(wet = it))
        }
        SliderRow(stringResource(Res.string.audio_lab_dry), percent(reverb.dry), reverb.dry, 0f..1f, active) {
            saveReverb(reverb.copy(dry = it))
        }
        SliderRow(stringResource(Res.string.audio_lab_width), percent(reverb.width), reverb.width, 0f..1f, active) {
            saveReverb(reverb.copy(width = it))
        }
        SliderRow(
            stringResource(Res.string.audio_lab_pre_delay),
            milliseconds(reverb.preDelayMs),
            reverb.preDelayMs,
            0f..150f,
            active,
        ) {
            saveReverb(reverb.copy(preDelayMs = it.roundToInt().toFloat()))
        }
        // low cut is heard on a log scale, so the slider moves in octaves
        SliderRow(
            stringResource(Res.string.audio_lab_low_cut),
            hertz(reverb.lowCutHz),
            log10(reverb.lowCutHz),
            log10(20f)..log10(1000f),
            active,
        ) {
            saveReverb(reverb.copy(lowCutHz = 10f.pow(it).roundToInt().toFloat()))
        }
    }
}

@Composable
private fun EightDCard(available: Boolean) {
    val eightD = Settings.shared.eightDSettings()
    val active = available && eightD.enabled
    AudioLabCard {
        SettingsMainSwitch(
            title = stringResource(Res.string.audio_lab_eightd),
            checked = eightD.enabled,
            onCheckedChange = { saveEightD(eightD.copy(enabled = it)) },
            enabled = available,
        )
        Text(
            text = stringResource(Res.string.audio_lab_eightd_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EightDPresets.forEach { preset ->
                FilterChip(
                    selected = eightD.rotationSpeedHz == preset.rotationSpeedHz && eightD.depth == preset.depth,
                    onClick = { saveEightD(eightD.copy(rotationSpeedHz = preset.rotationSpeedHz, depth = preset.depth)) },
                    label = { Text(stringResource(preset.name)) },
                    enabled = active,
                )
            }
        }
        SliderRow(
            stringResource(Res.string.audio_lab_rotation_speed),
            stringResource(Res.string.audio_lab_hz_decimal, eightD.rotationSpeedHz),
            eightD.rotationSpeedHz,
            0.05f..0.5f,
            active,
        ) {
            saveEightD(eightD.copy(rotationSpeedHz = it))
        }
        SliderRow(stringResource(Res.string.audio_lab_depth), percent(eightD.depth), eightD.depth, 0f..1f, active) {
            saveEightD(eightD.copy(depth = it))
        }
    }
}

/** Writes every reverb control. Any change made by hand clears the preset id, so the chip stops being lit. */
private fun saveReverb(
    settings: ReverbSettings,
    presetId: String = "",
) {
    Settings.shared.reverbEnabled = settings.enabled
    Settings.shared.reverbMix = settings.mix
    Settings.shared.reverbRoomSize = settings.roomSize
    Settings.shared.reverbDamping = settings.damping
    Settings.shared.reverbWet = settings.wet
    Settings.shared.reverbDry = settings.dry
    Settings.shared.reverbWidth = settings.width
    Settings.shared.reverbPreDelayMs = settings.preDelayMs
    Settings.shared.reverbLowCutHz = settings.lowCutHz
    Settings.shared.reverbPresetId = presetId
}

private fun saveEightD(settings: EightDSettings) {
    Settings.shared.eightDEnabled = settings.enabled
    Settings.shared.eightDRotationSpeedHz = settings.rotationSpeedHz
    Settings.shared.eightDDepth = settings.depth
}

private data class EightDPreset(
    val name: StringResource,
    val rotationSpeedHz: Float,
    val depth: Float,
)

private val EightDPresets =
    listOf(
        EightDPreset(Res.string.audio_lab_eightd_preset_chill, 0.08f, 0.45f),
        EightDPreset(Res.string.audio_lab_eightd_preset_balanced, 0.15f, 0.85f),
        EightDPreset(Res.string.audio_lab_eightd_preset_extreme, 0.5f, 1f),
    )

private fun reverbPresetName(id: String): StringResource =
    when (id) {
        "room" -> Res.string.audio_lab_preset_room
        "studio" -> Res.string.audio_lab_preset_studio
        "bathroom" -> Res.string.audio_lab_preset_bathroom
        "plate" -> Res.string.audio_lab_preset_plate
        "hall" -> Res.string.audio_lab_preset_hall
        "cathedral" -> Res.string.audio_lab_preset_cathedral
        "stadium" -> Res.string.audio_lab_preset_stadium
        "dream" -> Res.string.audio_lab_preset_dream
        else -> error("Unknown reverb preset: $id")
    }

@Composable
private fun AudioLabCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.settingsRowContainerColor())
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
private fun SliderRow(
    title: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    onValueChange: (Float) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = valueText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            enabled = enabled,
        )
    }
}

@Composable
private fun percent(value: Float): String = stringResource(Res.string.audio_lab_percent, (value * 100f).roundToInt())

@Composable
private fun milliseconds(value: Float): String = stringResource(Res.string.audio_lab_ms, value.roundToInt())

@Composable
private fun hertz(value: Float): String = stringResource(Res.string.audio_lab_hz, value.roundToInt())
