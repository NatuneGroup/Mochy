// Ported and adapted from Mochy Music by realidkroo. Licensed under GPL-3.0.

package com.metrolist.shared.ui.player.mochy.ipod

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.delay
import platform.UIKit.UIDevice

@Composable
internal actual fun rememberIpodBatteryLevel(): State<Float> {
    val level = remember { mutableFloatStateOf(1f) }
    LaunchedEffect(Unit) {
        UIDevice.currentDevice.batteryMonitoringEnabled = true
        while (true) {
            val raw = UIDevice.currentDevice.batteryLevel
            if (raw >= 0f) level.floatValue = raw.coerceIn(0f, 1f)
            delay(30_000L)
        }
    }
    return level
}
