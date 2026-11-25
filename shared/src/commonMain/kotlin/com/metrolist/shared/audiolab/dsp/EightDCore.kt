// Ported and adapted from Mochy Music by realidkroo. Licensed under GPL-3.0.

package com.metrolist.shared.audiolab.dsp

import com.metrolist.shared.audiolab.EightDSettings
import kotlin.concurrent.Volatile
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Pans the song slowly around the head with equal-power gains. Stereo only; other layouts pass through.
 * The output is not clamped; the caller limits it to the sample range.
 */
class EightDCore : AudioLabCore {
    @Volatile private var settings = EightDSettings()

    private var sampleRate = 0
    private var phase = 0.0
    private var smoothedEnabled = 0f
    private var smoothedDepth = 0f

    // Read once per buffer; when off, audio passes through after a short fade, like the equalizer.
    fun update(newSettings: EightDSettings) {
        settings = newSettings
    }

    override fun configure(
        sampleRate: Int,
        channels: Int,
    ) {
        this.sampleRate = if (channels == 2) sampleRate else 0
    }

    override fun reset() {
        phase = 0.0
        // fades in after a seek or a new track
        smoothedEnabled = 0f
        smoothedDepth = settings.depth.coerceIn(0f, 1f)
    }

    override fun process(
        interleaved: FloatArray,
        frames: Int,
    ): Boolean {
        val params = settings
        val targetEnabled = if (params.enabled) 1f else 0f
        // off and faded out: leave the song alone instead of a sin and cos per sample
        if (sampleRate <= 0 || frames <= 0 || (targetEnabled == 0f && smoothedEnabled == 0f)) return false

        val targetDepth = params.depth.coerceIn(0f, 1f)
        val speed = params.rotationSpeedHz.coerceIn(0.05f, 0.5f).toDouble()
        val smoothingStep = 1f / (sampleRate * SMOOTHING_SECONDS).coerceAtLeast(1f)
        val phaseStep = TWO_PI * speed / sampleRate

        for (f in 0 until frames) {
            val i = f * 2
            val left = interleaved[i]
            val right = interleaved[i + 1]
            smoothedEnabled = approach(smoothedEnabled, targetEnabled, smoothingStep)
            smoothedDepth = approach(smoothedDepth, targetDepth, smoothingStep)

            val pan = sin(phase) * smoothedDepth
            val theta = (pan + 1.0) * PI / 4.0
            val gainLeft = cos(theta)
            val gainRight = sin(theta)
            interleaved[i] = (left + (left * gainLeft - left) * smoothedEnabled).toFloat()
            interleaved[i + 1] = (right + (right * gainRight - right) * smoothedEnabled).toFloat()
            phase = (phase + phaseStep) % TWO_PI
        }
        return true
    }

    private fun approach(
        current: Float,
        target: Float,
        step: Float,
    ): Float =
        when {
            current < target -> min(target, current + step)
            current > target -> max(target, current - step)
            else -> current
        }

    private companion object {
        const val SMOOTHING_SECONDS = 0.01f
        val TWO_PI = 2.0 * PI
    }
}
