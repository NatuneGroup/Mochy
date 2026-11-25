// Ported and adapted from Mochy Music by realidkroo. Licensed under GPL-3.0.

package com.metrolist.android.playback

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import com.metrolist.shared.audiolab.EightDSettings
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Pans the song slowly around the head with equal-power gains. Stereo PCM16 only; other formats pass through. */
@OptIn(UnstableApi::class)
internal class EightDAudioProcessor : BaseAudioProcessor() {
    @Volatile private var settings = EightDSettings()

    private var sampleRate = 0
    private var phase = 0.0
    private var smoothedEnabled = 0f
    private var smoothedDepth = 0f

    // Read once per buffer; when off, audio passes through after a short fade, like the equalizer.
    fun update(newSettings: EightDSettings) {
        settings = newSettings
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT || inputAudioFormat.channelCount != 2) {
            sampleRate = 0
            return AudioProcessor.AudioFormat.NOT_SET
        }
        sampleRate = inputAudioFormat.sampleRate
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val byteCount = inputBuffer.remaining()
        if (byteCount == 0) return
        val source = inputBuffer.order(ByteOrder.nativeOrder())
        val output = replaceOutputBuffer(byteCount).order(ByteOrder.nativeOrder())
        val params = settings
        val targetEnabled = if (params.enabled) 1f else 0f
        // off and faded out: pass the song through instead of a sin and cos per sample
        if (sampleRate <= 0 || (targetEnabled == 0f && smoothedEnabled == 0f)) {
            output.put(source)
            output.flip()
            return
        }

        val targetDepth = params.depth.coerceIn(0f, 1f)
        val speed = params.rotationSpeedHz.coerceIn(0.05f, 0.5f).toDouble()
        val smoothingStep = 1f / (sampleRate * SMOOTHING_SECONDS).coerceAtLeast(1f)
        val frameCount = byteCount / STEREO_FRAME_BYTES
        val phaseStep = TWO_PI * speed / sampleRate

        repeat(frameCount) {
            val left = source.getShort().toInt()
            val right = source.getShort().toInt()
            smoothedEnabled = approach(smoothedEnabled, targetEnabled, smoothingStep)
            smoothedDepth = approach(smoothedDepth, targetDepth, smoothingStep)

            val pan = sin(phase) * smoothedDepth
            val theta = (pan + 1.0) * PI / 4.0
            val gainLeft = cos(theta)
            val gainRight = sin(theta)
            val processedLeft = left + (left * gainLeft - left) * smoothedEnabled
            val processedRight = right + (right * gainRight - right) * smoothedEnabled

            output.putShort(processedLeft.roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort())
            output.putShort(processedRight.roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort())
            phase = (phase + phaseStep) % TWO_PI
        }
        output.flip()
    }

    override fun onFlush(streamMetadata: AudioProcessor.StreamMetadata) {
        phase = 0.0
        // fades in after a seek or a new track
        smoothedEnabled = 0f
        smoothedDepth = settings.depth.coerceIn(0f, 1f)
    }

    override fun onReset() {
        sampleRate = 0
        phase = 0.0
        smoothedEnabled = 0f
        smoothedDepth = 0f
    }

    private fun approach(current: Float, target: Float, step: Float): Float = when {
        current < target -> min(target, current + step)
        current > target -> max(target, current - step)
        else -> current
    }

    private companion object {
        const val STEREO_FRAME_BYTES = 4
        const val SMOOTHING_SECONDS = 0.01f
        val TWO_PI = 2.0 * PI
    }
}
