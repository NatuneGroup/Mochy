// Ported from Mochy Music by realidkroo. Licensed under GPL-3.0.

package com.metrolist.shared.ui.player.mochy

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.metrolist.shared.playback.PlayerSession
import com.metrolist.shared.ui.player.collectWhilePlayerActive

@Stable
interface FlagshipVolume {
    val shown: Float
    val isDragging: Boolean

    fun drag(fraction: Float)

    fun release()
}

@Composable
internal fun rememberSessionFlagshipVolume(
    session: PlayerSession?,
    listening: Boolean,
): FlagshipVolume? {
    if (session == null) return null
    val level = session.volume.collectWhilePlayerActive(listening)
    return remember(session, level) {
        object : FlagshipVolume {
            private var dragging by mutableStateOf<Float?>(null)

            override val shown: Float get() = dragging ?: level.value
            override val isDragging: Boolean get() = dragging != null

            override fun drag(fraction: Float) {
                dragging = fraction
                session.setVolume(fraction)
            }

            override fun release() {
                dragging = null
            }
        }
    }
}
