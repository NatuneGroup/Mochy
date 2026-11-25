package com.metrolist.android.playback

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import com.metrolist.shared.audiolab.dsp.AudioLabCore
import com.metrolist.shared.audiolab.dsp.audioLabSoftKnee
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

/**
 * Runs a shared Audio Lab core on PCM16 buffers from Media3. Samples are read into a reusable float buffer, the core
 * changes them in place, and they are written back at the PCM16 edge. When the core leaves a buffer alone, the input
 * bytes pass through unchanged.
 */
@OptIn(UnstableApi::class)
internal abstract class Pcm16CoreAudioProcessor<T : AudioLabCore>(
    protected val core: T,
    private val supportedChannels: IntRange,
    private val softKnee: Boolean,
) : BaseAudioProcessor() {
    private var channels = 0
    private var samples = FloatArray(0)

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT || inputAudioFormat.channelCount !in supportedChannels) {
            channels = 0
            core.configure(0, 0)
            return AudioProcessor.AudioFormat.NOT_SET
        }
        channels = inputAudioFormat.channelCount
        core.configure(inputAudioFormat.sampleRate, inputAudioFormat.channelCount)
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val bytes = inputBuffer.remaining()
        if (bytes == 0) return
        val start = inputBuffer.position()
        val source = inputBuffer.order(ByteOrder.nativeOrder())
        val output = replaceOutputBuffer(bytes).order(ByteOrder.nativeOrder())
        val count = bytes / BYTES_PER_SAMPLE
        val frames = if (channels == 0) 0 else count / channels
        if (samples.size < count) samples = FloatArray(count)
        // absolute reads, so the input position stays put until we know whether the core changed anything
        for (i in 0 until count) samples[i] = source.getShort(start + i * BYTES_PER_SAMPLE) / SAMPLE_SCALE
        if (core.process(samples, frames)) {
            for (i in 0 until count) output.putShort(toPcm16(samples[i]))
            source.position(source.limit())
        } else {
            output.put(source)
        }
        output.flip()
    }

    override fun onFlush(streamMetadata: AudioProcessor.StreamMetadata) {
        core.reset()
    }

    override fun onReset() {
        channels = 0
        core.configure(0, 0)
        core.reset()
    }

    private fun toPcm16(sample: Float): Short {
        // the knee keeps its original 32767 scale; the hard clip uses the exact power-of-two scale
        val scaled = if (softKnee) audioLabSoftKnee(sample) * KNEE_SCALE else sample * SAMPLE_SCALE
        return scaled.roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
    }

    private companion object {
        const val BYTES_PER_SAMPLE = 2
        const val SAMPLE_SCALE = 32768f
        const val KNEE_SCALE = 32767f
    }
}
