package com.metrolist.shared.audiolab

import androidx.compose.runtime.snapshotFlow
import com.metrolist.shared.settings.Settings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged

/** The saved spatial controls, as the Android player applies them. */
fun Settings.spatialSettings(): SpatialSettings =
    SpatialSettings(
        enabled = spatialEnabled,
        mode = spatialMode,
        cutoffHz = spatialCutoffHz,
        feedDb = spatialFeedDb,
        speakerAngle = spatialSpeakerAngle,
        amount = spatialAmount,
    )

/** The saved reverb controls, as the Android player applies them. */
fun Settings.reverbSettings(): ReverbSettings =
    ReverbSettings(
        enabled = reverbEnabled,
        mix = reverbMix,
        roomSize = reverbRoomSize,
        damping = reverbDamping,
        wet = reverbWet,
        dry = reverbDry,
        width = reverbWidth,
        preDelayMs = reverbPreDelayMs,
        lowCutHz = reverbLowCutHz,
    )

/** The saved 8D controls, as the Android player applies them. */
fun Settings.eightDSettings(): EightDSettings =
    EightDSettings(
        enabled = eightDEnabled,
        rotationSpeedHz = eightDRotationSpeedHz,
        depth = eightDDepth,
    )

/** The saved Stage controls, as the Android player applies them. */
fun Settings.stageSettings(): StageSettings =
    StageSettings(
        enabled = stageEnabled,
        presetId = stagePresetId,
        roomSize = stageRoomSize,
        walls = stageWalls,
        roomAmount = stageRoomAmount,
        levelDb = stageLevelDb,
    )

/** Emits the current spatial controls, then again after each change. */
fun Settings.spatialSettingsFlow(): Flow<SpatialSettings> = snapshotFlow { spatialSettings() }.distinctUntilChanged()

/** Emits the current reverb controls, then again after each change. */
fun Settings.reverbSettingsFlow(): Flow<ReverbSettings> = snapshotFlow { reverbSettings() }.distinctUntilChanged()

/** Emits the current 8D controls, then again after each change. */
fun Settings.eightDSettingsFlow(): Flow<EightDSettings> = snapshotFlow { eightDSettings() }.distinctUntilChanged()

/** Emits the current Stage controls, then again after each change. */
fun Settings.stageSettingsFlow(): Flow<StageSettings> = snapshotFlow { stageSettings() }.distinctUntilChanged()
