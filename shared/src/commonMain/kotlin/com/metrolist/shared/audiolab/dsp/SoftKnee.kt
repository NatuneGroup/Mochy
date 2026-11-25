// Ported and adapted from Mochy Music by realidkroo. Licensed under GPL-3.0.

package com.metrolist.shared.audiolab.dsp

import kotlin.math.abs
import kotlin.math.tanh

private const val KNEE = 0.89f

/**
 * A soft knee above -1 dB instead of a hard clip: a full room adds level on loud songs.
 * Applied where processed float samples leave the effect chain.
 */
fun audioLabSoftKnee(sample: Float): Float {
    val a = abs(sample)
    if (a <= KNEE) return sample
    val over = (a - KNEE) / (1f - KNEE)
    return (KNEE + (1f - KNEE) * tanh(over)) * if (sample < 0) -1f else 1f
}
