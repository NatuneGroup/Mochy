// Ported and adapted from Mochy Music by realidkroo. Licensed under GPL-3.0.

package com.metrolist.shared.ui.audiolab

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrolist.shared.audiolab.EightDSettings
import com.metrolist.shared.audiolab.ReverbSettings
import com.metrolist.shared.audiolab.SpatialSettings
import com.metrolist.shared.audiolab.StageSettings
import com.metrolist.shared.audiolab.eightDSettings
import com.metrolist.shared.audiolab.reverbSettings
import com.metrolist.shared.audiolab.spatialSettings
import com.metrolist.shared.audiolab.stageSettings
import com.metrolist.shared.generated.resources.Res
import com.metrolist.shared.generated.resources.audio_lab_db
import com.metrolist.shared.generated.resources.audio_lab_degrees
import com.metrolist.shared.generated.resources.audio_lab_hls_note
import com.metrolist.shared.generated.resources.audio_lab_hz
import com.metrolist.shared.generated.resources.audio_lab_ms
import com.metrolist.shared.generated.resources.audio_lab_percent
import com.metrolist.shared.generated.resources.audio_lab_stage_kemar_credit
import com.metrolist.shared.generated.resources.percent_symbol
import com.metrolist.shared.settings.Settings
import com.metrolist.shared.ui.theme.mochyInterFontFamily
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/** Whether the Stage room can run on this host: Android only, and only once its native library loads. */
enum class AudioLabStageSupport { Available, AndroidOnly, EngineUnavailable }

/** the card stack's height: the cards stand in it and dip under the page's top edge */
private val HeaderHeight = 228.dp

/** above this width the cards sit in two columns, and the page stops widening past [ContentMaxWidth] */
private val TwoColumnWidth = 720.dp
private val ContentMaxWidth = 1040.dp

/**
 * Audio Lab settings: the animated effect stack, then the Stage, Reverb, 8D and Spatial cards. [showHlsNote] adds the
 * note that effects skip HLS streams. [stageSupport] decides whether the Stage switch is usable here.
 */
@Composable
fun AudioLabScreen(
    showHlsNote: Boolean,
    stageSupport: AudioLabStageSupport,
    modifier: Modifier = Modifier,
) {
    val scroll = rememberScrollState()
    val headerPx = with(LocalDensity.current) { HeaderHeight.toPx() }
    // the stack stops turning once the page has scrolled most of the header away
    val stackHidden = remember(headerPx) { derivedStateOf { scroll.value >= headerPx * 0.6f } }
    val usable = stageSupport == AudioLabStageSupport.Available
    val stageOn = usable && Settings.shared.stageSettings().enabled
    val reverb = Settings.shared.reverbSettings()
    val spatial = Settings.shared.spatialSettings()
    val eightD = Settings.shared.eightDSettings()
    val effects = activeEffects(reverb, spatial, eightD, stageOn)

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .verticalScroll(scroll)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        val wide = maxWidth >= TwoColumnWidth
        Column(
            modifier = Modifier.align(Alignment.TopCenter).widthIn(max = ContentMaxWidth).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(Modifier.fillMaxWidth().height(HeaderHeight)) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .fillMaxWidth(if (wide) 0.4f else 0.5f)
                        .fillMaxHeight(),
                ) {
                    FxCardStack(effects, stackHidden)
                }
            }
            if (showHlsNote) {
                Text(
                    text = stringResource(Res.string.audio_lab_hls_note),
                    fontFamily = mochyInterFontFamily(),
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = LabColors.Secondary,
                )
            }
            FxPresetsSection()
            if (wide) {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        StageGroup(stageSupport)
                        EightDAudioCard()
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        ReverbCard()
                        SpatialCard()
                    }
                }
            } else {
                StageGroup(stageSupport)
                ReverbCard()
                EightDAudioCard()
                SpatialCard()
            }
        }
    }
}

@Composable
private fun StageGroup(stageSupport: AudioLabStageSupport) {
    StageCard(stageSupport)
}

/** Writes every reverb control. Any change made by hand clears the preset id, so the chip stops being lit. */
internal fun saveReverb(
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

/** Writes every Stage control. The preset id is kept, so the chip stays lit after a slider change. */
internal fun saveStage(settings: StageSettings) {
    Settings.shared.stageEnabled = settings.enabled
    Settings.shared.stagePresetId = settings.presetId
    Settings.shared.stageRoomSize = settings.roomSize
    Settings.shared.stageWalls = settings.walls
    Settings.shared.stageRoomAmount = settings.roomAmount
    Settings.shared.stageLevelDb = settings.levelDb
}

internal fun saveSpatial(settings: SpatialSettings) {
    Settings.shared.spatialEnabled = settings.enabled
    Settings.shared.spatialMode = settings.mode
    Settings.shared.spatialCutoffHz = settings.cutoffHz
    Settings.shared.spatialFeedDb = settings.feedDb
    Settings.shared.spatialSpeakerAngle = settings.speakerAngle
    Settings.shared.spatialAmount = settings.amount
}

internal fun saveEightD(settings: EightDSettings) {
    Settings.shared.eightDEnabled = settings.enabled
    Settings.shared.eightDRotationSpeedHz = settings.rotationSpeedHz
    Settings.shared.eightDDepth = settings.depth
}

@Composable
internal fun percent(value: Float): String =
    stringResource(Res.string.audio_lab_percent, (value * 100f).roundToInt(), stringResource(Res.string.percent_symbol))

@Composable
internal fun milliseconds(value: Float): String = stringResource(Res.string.audio_lab_ms, value.roundToInt())

@Composable
internal fun hertz(value: Float): String = stringResource(Res.string.audio_lab_hz, value.roundToInt())

@Composable
internal fun decibels(value: Float): String = stringResource(Res.string.audio_lab_db, roundedText(value, 10f))

/** Rounds to the nearest 1/scale for display, without a printf format (Compose resources have none). */
internal fun roundedText(
    value: Float,
    scale: Float,
): String = ((value * scale).roundToInt() / scale).toString()

@Composable
internal fun degrees(value: Float): String = stringResource(Res.string.audio_lab_degrees, value.roundToInt())
