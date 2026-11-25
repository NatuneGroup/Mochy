// Ported from Mochy Music by realidkroo (github.com/realidkroo/mochy-music). Licensed under GPL-3.0.

package com.metrolist.shared.ui.onboarding.mochy

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue

/**
 * How far the phone has been tipped from the way it's being held, as a lean for a card to follow (degrees, for
 * graphicsLayer's rotationX / rotationY). Changes many times a second, so read it in a graphicsLayer or draw lambda only.
 */
@Stable
internal class DeviceTilt {
    /** the phone's top edge tipping towards or away from you */
    var rotationX by mutableFloatStateOf(0f)

    /** the phone rolling left or right */
    var rotationY by mutableFloatStateOf(0f)
}

/** listens to the motion sensors while the screen is resumed; where there are none the card just doesn't lean */
@Composable
internal expect fun rememberDeviceTilt(maxDegrees: Float = 14f): DeviceTilt
