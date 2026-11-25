package com.metrolist.shared.audiolab

import androidx.compose.runtime.snapshotFlow
import com.metrolist.shared.settings.Settings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged

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

/** Emits the current reverb controls, then again after each change. */
fun Settings.reverbSettingsFlow(): Flow<ReverbSettings> = snapshotFlow { reverbSettings() }.distinctUntilChanged()

/** Emits the current 8D controls, then again after each change. */
fun Settings.eightDSettingsFlow(): Flow<EightDSettings> = snapshotFlow { eightDSettings() }.distinctUntilChanged()
