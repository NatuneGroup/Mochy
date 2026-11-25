// Ported and adapted from Mochy Music by realidkroo. Licensed under GPL-3.0.

package com.metrolist.shared.audiolab

/** 8D pan: how fast the sound swings around the head (Hz, 0.05..0.5) and how far (0..1). Off by default. */
data class EightDSettings(
    val enabled: Boolean = false,
    val rotationSpeedHz: Float = 0.15f,
    val depth: Float = 0.85f,
)
