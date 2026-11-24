// Ported and adapted from Mochy Music by realidkroo. Licensed under GPL-3.0.

package com.metrolist.shared.ui.player.mochy.ipod

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import com.metrolist.shared.ui.haptics.HapticEvent
import com.metrolist.shared.ui.haptics.MetrolistHaptics
import com.metrolist.shared.ui.haptics.rememberMetrolistHaptics

@Stable
internal class IpodHaptics(
    private val haptics: MetrolistHaptics,
) {
    fun tick() = haptics.perform(HapticEvent.SEGMENT_TICK)

    fun press() = haptics.perform(HapticEvent.PRESS)

    fun release() = haptics.perform(HapticEvent.SEGMENT_TICK)

    fun click() = haptics.perform(HapticEvent.CONFIRM)
}

@Composable
internal fun rememberIpodHaptics(): IpodHaptics {
    val haptics = rememberMetrolistHaptics()
    return remember(haptics) { IpodHaptics(haptics) }
}
