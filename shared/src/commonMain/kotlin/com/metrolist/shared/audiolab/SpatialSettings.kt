// Ported and adapted from Mochy Music by realidkroo. Licensed under GPL-3.0.

package com.metrolist.shared.audiolab

import kotlinx.serialization.Serializable

/**
 * Headphone spatial audio. Crossfeed blends the other ear into each side, or virtual speakers place both channels at [speakerAngle] degrees.
 * Off by default.
 */
@Serializable
data class SpatialSettings(
    val enabled: Boolean = false,
    val mode: SpatialMode = SpatialMode.Crossfeed,
    /** Crossfeed: frequency (Hz) above which the other ear's sound is shadowed by the head. */
    val cutoffHz: Float = 700f,
    /** Crossfeed: how much quieter (dB) the other ear's sound arrives. */
    val feedDb: Float = 4.5f,
    /** Speakers: how far (degrees) each virtual speaker sits from straight ahead. */
    val speakerAngle: Float = 30f,
    /** 0..1: how strongly the effect is mixed in. */
    val amount: Float = 1f,
)

enum class SpatialMode { Crossfeed, Speakers }
