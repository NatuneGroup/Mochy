package com.metrolist.shared.ui.onboarding.mochy

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

// no motion sensor read here yet: the card follows the finger only
@Composable
internal actual fun rememberDeviceTilt(maxDegrees: Float): DeviceTilt = remember { DeviceTilt() }
