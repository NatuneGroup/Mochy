// Ported and adapted from Mochy Music by realidkroo. Licensed under GPL-3.0.

package com.metrolist.shared.ui.audiolab

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.metrolist.shared.audiolab.ReverbPresets
import com.metrolist.shared.audiolab.ReverbSettings
import com.metrolist.shared.audiolab.reverbSettings
import com.metrolist.shared.audiolab.withPreset
import com.metrolist.shared.generated.resources.Res
import com.metrolist.shared.generated.resources.audio_lab_damping
import com.metrolist.shared.generated.resources.audio_lab_dry
import com.metrolist.shared.generated.resources.audio_lab_low_cut
import com.metrolist.shared.generated.resources.audio_lab_mix
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
import com.metrolist.shared.generated.resources.audio_lab_wet
import com.metrolist.shared.generated.resources.audio_lab_width
import com.metrolist.shared.settings.Settings
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

@Composable
internal fun ReverbCard() {
    val reverb = Settings.shared.reverbSettings()
    val presetId = Settings.shared.reverbPresetId
    EffectCard(
        picture = { ReverbPicture(reverb) },
        header = {
            LabToggleRow(
                title = stringResource(Res.string.audio_lab_reverb),
                subtitle = stringResource(Res.string.audio_lab_reverb_description),
                checked = reverb.enabled,
                onCheckedChange = { on -> saveReverb(reverb.copy(enabled = on), presetId) },
            )
        },
        expanded = reverb.enabled,
        options = {
            LabChips(
                chips = ReverbPresets.All.map { LabChip(it.id, stringResource(reverbPresetName(it.id))) },
                selectedKey = presetId,
                onSelect = { id ->
                    ReverbPresets.All.firstOrNull { it.id == id }?.let { preset ->
                        saveReverb(reverb.withPreset(preset), preset.id)
                    }
                },
            )
            Spacer(Modifier.height(10.dp))
            LabPanel {
                LabSlider(stringResource(Res.string.audio_lab_mix), percent(reverb.mix), reverb.mix, 0f..1f) {
                    saveReverb(reverb.copy(mix = it))
                }
                LabSlider(stringResource(Res.string.audio_lab_room_size), percent(reverb.roomSize), reverb.roomSize, 0f..1f) {
                    saveReverb(reverb.copy(roomSize = it))
                }
                LabSlider(stringResource(Res.string.audio_lab_damping), percent(reverb.damping), reverb.damping, 0f..1f) {
                    saveReverb(reverb.copy(damping = it))
                }
                LabSlider(stringResource(Res.string.audio_lab_wet), percent(reverb.wet), reverb.wet, 0f..1f) {
                    saveReverb(reverb.copy(wet = it))
                }
                LabSlider(stringResource(Res.string.audio_lab_dry), percent(reverb.dry), reverb.dry, 0f..1f) {
                    saveReverb(reverb.copy(dry = it))
                }
                LabSlider(stringResource(Res.string.audio_lab_width), percent(reverb.width), reverb.width, 0f..1f) {
                    saveReverb(reverb.copy(width = it))
                }
                LabSlider(
                    stringResource(Res.string.audio_lab_pre_delay),
                    milliseconds(reverb.preDelayMs),
                    reverb.preDelayMs,
                    0f..150f,
                ) {
                    saveReverb(reverb.copy(preDelayMs = it.roundToInt().toFloat()))
                }
                // low cut is heard on a log scale, so the slider moves in octaves
                LabSlider(
                    stringResource(Res.string.audio_lab_low_cut),
                    hertz(reverb.lowCutHz),
                    log10(reverb.lowCutHz),
                    log10(20f)..log10(1000f),
                ) {
                    saveReverb(reverb.copy(lowCutHz = 10f.pow(it).roundToInt().toFloat()))
                }
            }
        },
    )
}

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

// ---- the picture: waves leave the speaker, bounce between the walls and die away -----------

private class RoomWaves {
    val born = FloatArray(14) { -100f }
    val strength = FloatArray(14)
    var next = 0
    var nextEmit = 0f

    fun emit(
        t: Float,
        s: Float,
    ) {
        born[next] = t
        strength[next] = s
        next = (next + 1) % born.size
    }
}

@Composable
private fun ReverbPicture(settings: ReverbSettings) {
    val room by animateFloatAsState(settings.roomSize, spring(dampingRatio = 0.8f, stiffness = 120f), label = "room")
    val on by animateFloatAsState(if (settings.enabled) 1f else 0f, tween(400), label = "on")
    val waves = remember { RoomWaves() }
    PictureCanvas { t, _ ->
        val w = size.width
        val h = size.height
        val cy = h / 2f
        val speakerX = w * 0.15f
        val backX = w * 0.05f
        val wallX = w * (0.52f + 0.4f * room)
        val speed = w * 0.42f

        // with no music tap, a calm pulse every 1.3 seconds
        if (t >= waves.nextEmit) {
            waves.emit(t, 0.75f)
            waves.nextEmit = t + 1.3f
        }

        val reflectivity = (0.35f + 0.55f * settings.roomSize) * (1f - 0.55f * settings.damping)
        val echoAlpha = on * (0.35f + 0.65f * settings.wet) * (0.4f + 0.6f * settings.mix)
        var flash = 0f
        val span = wallX - backX
        for (i in waves.born.indices) {
            val age = t - waves.born[i]
            if (age < 0f || age > 8f) continue
            val d = age * speed
            val first = wallX - speakerX
            if (d <= first || on < 0.01f) {
                // the direct sound; with the reverb off it passes straight through the wall
                val x = speakerX + d
                if (x > w + 20f) continue
                val a = waves.strength[i] * exp(-d / (w * 1.6f))
                drawWave(x, cy, h, d, a * (if (d > first) 1f - on else 1f), rightward = true, softness = 0f)
                continue
            }
            // after the first hit it folds back and forth between the walls, losing energy each time
            val past = d - first
            val bounces = 1 + (past / span).toInt()
            val inLeg = past % span
            val rightward = bounces % 2 == 0
            val x = if (rightward) backX + inLeg else wallX - inLeg
            val a = waves.strength[i] * reflectivity.pow(bounces) * exp(-d / (w * 2.2f)) * echoAlpha
            if (a < 0.02f) continue
            if (inLeg < span * 0.08f && !rightward) flash = max(flash, a * (1f - inLeg / (span * 0.08f)))
            drawWave(x, cy, h, d, a, rightward, softness = settings.damping)
        }

        // the wall, lit for a moment by each wave that reaches it
        val wallH = h * 0.62f
        drawRoundRect(
            Color.White.copy(alpha = (0.18f + 0.55f * on + 0.6f * flash).coerceAtMost(1f)),
            Offset(wallX, cy - wallH / 2),
            Size(5.dp.toPx(), wallH),
            CornerRadius(3.dp.toPx()),
        )
        drawSpeaker(Offset(speakerX, cy), 1f)
    }
}

private fun DrawScope.drawWave(
    x: Float,
    cy: Float,
    h: Float,
    travelled: Float,
    alpha: Float,
    rightward: Boolean,
    softness: Float,
) {
    if (alpha <= 0.005f) return
    val reach = min(h * 0.42f, 14.dp.toPx() + travelled * 0.18f)
    val bulge = reach * 0.42f * if (rightward) 1f else -1f
    val path =
        Path().apply {
            moveTo(x - bulge * 0.2f, cy - reach)
            quadraticTo(x + bulge, cy, x - bulge * 0.2f, cy + reach)
        }
    if (softness > 0.15f) {
        drawPath(path, Color.White.copy(alpha = alpha * 0.25f * softness), style = Stroke(7.dp.toPx() * softness, cap = StrokeCap.Round))
    }
    drawPath(path, Color.White.copy(alpha = alpha.coerceAtMost(1f)), style = Stroke((2.2f - softness).dp.toPx(), cap = StrokeCap.Round))
}

private fun DrawScope.drawSpeaker(
    center: Offset,
    scale: Float,
) {
    val s = 9.dp.toPx() * scale
    val body =
        Path().apply {
            moveTo(center.x - s * 1.1f, center.y - s * 0.45f)
            lineTo(center.x - s * 0.4f, center.y - s * 0.45f)
            lineTo(center.x + s * 0.5f, center.y - s * 1.1f)
            lineTo(center.x + s * 0.5f, center.y + s * 1.1f)
            lineTo(center.x - s * 0.4f, center.y + s * 0.45f)
            lineTo(center.x - s * 1.1f, center.y + s * 0.45f)
            close()
        }
    drawPath(body, Color.White.copy(alpha = 0.92f), style = Stroke(1.6.dp.toPx()))
}
