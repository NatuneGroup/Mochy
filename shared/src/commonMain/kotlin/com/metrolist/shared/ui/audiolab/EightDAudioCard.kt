// Ported and adapted from Mochy Music by realidkroo. Licensed under GPL-3.0.

package com.metrolist.shared.ui.audiolab

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrolist.shared.audiolab.EightDSettings
import com.metrolist.shared.audiolab.eightDSettings
import com.metrolist.shared.generated.resources.Res
import com.metrolist.shared.generated.resources.audio_lab_depth
import com.metrolist.shared.generated.resources.audio_lab_eightd
import com.metrolist.shared.generated.resources.audio_lab_eightd_description
import com.metrolist.shared.generated.resources.audio_lab_eightd_preset_balanced
import com.metrolist.shared.generated.resources.audio_lab_eightd_preset_chill
import com.metrolist.shared.generated.resources.audio_lab_eightd_preset_extreme
import com.metrolist.shared.generated.resources.audio_lab_hz_decimal
import com.metrolist.shared.generated.resources.audio_lab_rotation_speed
import com.metrolist.shared.settings.Settings
import com.metrolist.shared.ui.theme.mochyInterFontFamily
import kotlinx.coroutines.flow.first
import org.jetbrains.compose.resources.StringResource
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
internal fun EightDAudioCard() {
    val settings = Settings.shared.eightDSettings()
    val enabled = settings.enabled
    val font = mochyInterFontFamily()
    val onScreen = rememberOnScreen()
    val held = rememberPicturesHeld(onScreen)
    LabCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(Res.string.audio_lab_eightd),
                    fontFamily = font,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 17.sp,
                    color = LabColors.Primary,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    stringResource(Res.string.audio_lab_eightd_description),
                    fontFamily = font,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = LabColors.Secondary,
                )
            }
            LabSwitch(
                checked = enabled,
                onCheckedChange = { on -> saveEightD(settings.copy(enabled = on)) },
            )
        }

        Spacer(Modifier.height(12.dp))
        OrbitIndicator(settings, held, Modifier.trackOnScreen(onScreen))
        Spacer(Modifier.height(12.dp))
        LabChips(
            chips = EightDPresets.map { LabChip(it.key, stringResource(it.name)) },
            selectedKey = EightDPresets.firstOrNull { settings.matches(it) }?.key,
            onSelect = { key ->
                EightDPresets.firstOrNull { it.key == key }?.let { preset ->
                    saveEightD(settings.copy(rotationSpeedHz = preset.rotationSpeedHz, depth = preset.depth))
                }
            },
        )
        Spacer(Modifier.height(4.dp))
        LabPanel {
            LabSlider(
                label = stringResource(Res.string.audio_lab_rotation_speed),
                valueText = stringResource(Res.string.audio_lab_hz_decimal, roundedText(settings.rotationSpeedHz, 100f)),
                value = settings.rotationSpeedHz,
                valueRange = 0.05f..0.5f,
                enabled = enabled,
                steps = 8,
                onValueChange = { saveEightD(settings.copy(rotationSpeedHz = it)) },
            )
            LabSlider(
                label = stringResource(Res.string.audio_lab_depth),
                valueText = percent(settings.depth),
                value = settings.depth,
                valueRange = 0f..1f,
                enabled = enabled,
                onValueChange = { saveEightD(settings.copy(depth = it)) },
            )
        }
    }
}

/** the turning dot: it turns at the real rotation speed and holds still while the page is off screen */
@Composable
private fun OrbitIndicator(settings: EightDSettings, held: State<Boolean>, modifier: Modifier) {
    val angle = remember { mutableFloatStateOf(0f) }
    val currentSettings by rememberUpdatedState(settings)
    LaunchedEffect(settings.enabled, settings.rotationSpeedHz) {
        if (!settings.enabled) {
            angle.floatValue = 0f
            return@LaunchedEffect
        }
        var last = 0L
        while (true) {
            if (held.value) {
                snapshotFlow { held.value }.first { !it }
                last = 0L
            }
            withFrameNanos { now ->
                if (last != 0L) {
                    angle.floatValue = (angle.floatValue + (now - last) / 1e9f * 360f * currentSettings.rotationSpeedHz) % 360f
                }
                last = now
            }
        }
    }

    // the angle is only read in draw, so the turning dot repaints this box and never recomposes the card
    Canvas(modifier.fillMaxWidth().height(148.dp)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = minOf(size.width * 0.34f, size.height * 0.43f)
        val line = LabColors.StageLine
        repeat(3) { index ->
            drawCircle(
                color = line,
                radius = radius * (index + 1) / 3f,
                center = center,
                style = Stroke(1.dp.toPx()),
            )
        }
        drawLine(line, Offset(center.x - radius, center.y), Offset(center.x + radius, center.y), 1.dp.toPx())
        drawLine(line, Offset(center.x, center.y - radius), Offset(center.x, center.y + radius), 1.dp.toPx())

        val radians = (angle.floatValue - 90f) * PI.toFloat() / 180f
        val orbitRadius = radius * (0.25f + settings.depth * 0.75f)
        val source = Offset(
            center.x + cos(radians) * orbitRadius,
            center.y + sin(radians) * orbitRadius,
        )
        drawLine(
            color = Color.White.copy(alpha = if (settings.enabled) 0.32f else 0.12f),
            start = center,
            end = source,
            strokeWidth = 1.dp.toPx(),
        )
        drawCircle(
            color = Color.White.copy(alpha = if (settings.enabled) 1f else 0.35f),
            radius = 5.dp.toPx(),
            center = source,
        )

        val headsetRadius = radius * 0.22f
        drawArc(
            color = Color.White,
            startAngle = 200f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(center.x - headsetRadius, center.y - headsetRadius),
            size = Size(headsetRadius * 2f, headsetRadius * 2f),
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
        )
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(center.x - headsetRadius, center.y - 2.dp.toPx()),
            size = Size(5.dp.toPx(), 13.dp.toPx()),
        )
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(center.x + headsetRadius - 5.dp.toPx(), center.y - 2.dp.toPx()),
            size = Size(5.dp.toPx(), 13.dp.toPx()),
        )
    }
}

private fun EightDSettings.matches(preset: EightDPreset): Boolean =
    rotationSpeedHz == preset.rotationSpeedHz && depth == preset.depth

private class EightDPreset(
    val key: String,
    val name: StringResource,
    val rotationSpeedHz: Float,
    val depth: Float,
)

private val EightDPresets = listOf(
    EightDPreset("chill", Res.string.audio_lab_eightd_preset_chill, 0.08f, 0.45f),
    EightDPreset("balanced", Res.string.audio_lab_eightd_preset_balanced, 0.15f, 0.85f),
    EightDPreset("extreme", Res.string.audio_lab_eightd_preset_extreme, 0.5f, 1f),
)
