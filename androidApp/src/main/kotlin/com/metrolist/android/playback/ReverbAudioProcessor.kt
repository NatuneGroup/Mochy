// Ported and adapted from Mochy Music by realidkroo. Licensed under GPL-3.0.

package com.metrolist.android.playback

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import com.metrolist.shared.audiolab.ReverbSettings
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.tanh

/**
 * Freeverb-style room: eight damped combs into four allpasses per side, the right side detuned for width.
 * A mono pre-delay and low cut go in front so the bass stays dry. Gains glide across each buffer so dragging doesn't zipper.
 */
@OptIn(UnstableApi::class)
internal class ReverbAudioProcessor : BaseAudioProcessor() {
    @Volatile private var settings = ReverbSettings()

    private var sampleRate = 0
    private var channels = 0

    private var combsL: Array<Comb> = emptyArray()
    private var combsR: Array<Comb> = emptyArray()
    private var allpassL: Array<Allpass> = emptyArray()
    private var allpassR: Array<Allpass> = emptyArray()
    private var preDelay = FloatArray(1)
    private var preDelayIndex = 0
    private var lowCutState = 0f
    private var lowCutInput = 0f

    // the gains actually applied, gliding toward the settings
    private var amount = 0f
    private var wet1 = 0f
    private var wet2 = 0f
    private var dryGain = 1f
    private var mix = 0f
    private var roomEmpty = true

    // Read once per buffer; when off, audio passes through after a short fade, like the equalizer.
    fun update(newSettings: ReverbSettings) {
        settings = newSettings
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT || inputAudioFormat.channelCount !in 1..2) {
            channels = 0
            return AudioProcessor.AudioFormat.NOT_SET
        }
        if (inputAudioFormat.sampleRate != sampleRate || inputAudioFormat.channelCount != channels) {
            sampleRate = inputAudioFormat.sampleRate
            channels = inputAudioFormat.channelCount
            build(sampleRate)
        }
        snapGains()
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val bytes = inputBuffer.remaining()
        if (bytes == 0) return
        val source = inputBuffer.order(ByteOrder.nativeOrder())
        val output = replaceOutputBuffer(bytes).order(ByteOrder.nativeOrder())
        val params = settings
        val target = if (params.enabled) 1f else 0f
        if (channels == 0 || (target == 0f && amount == 0f)) {
            if (!roomEmpty) clearRoom()
            output.put(source)
            output.flip()
            return
        }
        roomEmpty = false
        val frames = bytes / (BYTES_PER_SAMPLE * channels)

        // room size and damping set the combs once per buffer, so they don't zipper
        val feedback = params.roomSize.coerceIn(0f, 1f) * SCALE_ROOM + OFFSET_ROOM
        val damp = params.damping.coerceIn(0f, 1f) * SCALE_DAMP
        for (c in combsL) c.set(feedback, damp)
        for (c in combsR) c.set(feedback, damp)
        val delayFrames =
            (params.preDelayMs.coerceIn(0f, MAX_PRE_DELAY_MS) / 1000f * sampleRate)
                .roundToInt()
                .coerceIn(0, preDelay.size - 1)
        val lowCut = exp(-2.0 * PI * params.lowCutHz.coerceIn(10f, 2000f) / sampleRate).toFloat()

        val width = params.width.coerceIn(0f, 1f)
        val wetLevel = params.wet.coerceIn(0f, 1f) * SCALE_WET
        val targetWet1 = wetLevel * (width / 2f + 0.5f)
        val targetWet2 = wetLevel * ((1f - width) / 2f)
        val targetDry = params.dry.coerceIn(0f, 1f)
        val targetMix = params.mix.coerceIn(0f, 1f)

        val step = 1f / frames
        val toggleStep = 1f / (sampleRate * TOGGLE_SECONDS)
        val dWet1 = (targetWet1 - wet1) * step
        val dWet2 = (targetWet2 - wet2) * step
        val dDry = (targetDry - dryGain) * step
        val dMix = (targetMix - mix) * step

        val scale = 1f / 32768f
        for (f in 0 until frames) {
            val inL = source.getShort() * scale
            val inR = if (channels == 2) source.getShort() * scale else inL

            amount = if (target > amount) minOf(target, amount + toggleStep) else maxOf(target, amount - toggleStep)
            wet1 += dWet1
            wet2 += dWet2
            dryGain += dDry
            mix += dMix

            // what goes into the room: mono, pre-delayed, low-cut
            val send = (inL + inR) * FIXED_GAIN
            preDelay[preDelayIndex] = send
            var read = preDelayIndex - delayFrames
            if (read < 0) read += preDelay.size
            preDelayIndex = if (preDelayIndex + 1 == preDelay.size) 0 else preDelayIndex + 1
            val delayed = preDelay[read]
            lowCutState = lowCut * (lowCutState + delayed - lowCutInput)
            lowCutInput = delayed
            val input = lowCutState + ANTI_DENORMAL

            var accL = 0f
            var accR = 0f
            for (c in combsL) accL += c.process(input)
            for (a in allpassL) accL = a.process(accL)
            if (channels == 2) {
                for (c in combsR) accR += c.process(input)
                for (a in allpassR) accR = a.process(accR)
            }

            val g = amount * mix
            if (channels == 2) {
                val roomL = accL * wet1 + accR * wet2 + inL * dryGain
                val roomR = accR * wet1 + accL * wet2 + inR * dryGain
                output.putShort(toPcm(inL + (roomL - inL) * g))
                output.putShort(toPcm(inR + (roomR - inR) * g))
            } else {
                val room = accL * (wet1 + wet2) + inL * dryGain
                output.putShort(toPcm(inL + (room - inL) * g))
            }
        }
        wet1 = targetWet1
        wet2 = targetWet2
        dryGain = targetDry
        mix = targetMix
        output.flip()
    }

    override fun onFlush(streamMetadata: AudioProcessor.StreamMetadata) {
        // a seek or a new track: the old room's tail belongs to audio that isn't playing any more
        clearRoom()
        // start where the settings are rather than fading in
        snapGains()
    }

    override fun onReset() {
        sampleRate = 0
        channels = 0
        combsL = emptyArray()
        combsR = emptyArray()
        allpassL = emptyArray()
        allpassR = emptyArray()
        preDelay = FloatArray(1)
        preDelayIndex = 0
        clearRoom()
    }

    private fun snapGains() {
        val params = settings
        amount = if (params.enabled) 1f else 0f
        val width = params.width.coerceIn(0f, 1f)
        val wetLevel = params.wet.coerceIn(0f, 1f) * SCALE_WET
        wet1 = wetLevel * (width / 2f + 0.5f)
        wet2 = wetLevel * ((1f - width) / 2f)
        dryGain = params.dry.coerceIn(0f, 1f)
        mix = params.mix.coerceIn(0f, 1f)
    }

    private fun clearRoom() {
        combsL.forEach { it.clear() }
        combsR.forEach { it.clear() }
        allpassL.forEach { it.clear() }
        allpassR.forEach { it.clear() }
        preDelay.fill(0f)
        lowCutState = 0f
        lowCutInput = 0f
        roomEmpty = true
    }

    private fun build(rate: Int) {
        val k = rate / 44_100f
        combsL = Array(COMB_TUNING.size) { Comb((COMB_TUNING[it] * k).roundToInt()) }
        combsR = Array(COMB_TUNING.size) { Comb(((COMB_TUNING[it] + STEREO_SPREAD) * k).roundToInt()) }
        allpassL = Array(ALLPASS_TUNING.size) { Allpass((ALLPASS_TUNING[it] * k).roundToInt()) }
        allpassR = Array(ALLPASS_TUNING.size) { Allpass(((ALLPASS_TUNING[it] + STEREO_SPREAD) * k).roundToInt()) }
        preDelay = FloatArray((rate * MAX_PRE_DELAY_MS / 1000f).roundToInt() + 2)
        preDelayIndex = 0
        clearRoom()
    }

    private class Comb(
        size: Int,
    ) {
        private val line = FloatArray(size.coerceAtLeast(1))
        private var index = 0
        private var store = 0f
        private var feedback = 0.84f
        private var damp1 = 0.2f
        private var damp2 = 0.8f

        fun set(
            feedback: Float,
            damp: Float,
        ) {
            this.feedback = feedback
            damp1 = damp
            damp2 = 1f - damp
        }

        fun process(input: Float): Float {
            val output = line[index]
            store = output * damp2 + store * damp1
            line[index] = input + store * feedback
            index = if (index + 1 == line.size) 0 else index + 1
            return output
        }

        fun clear() {
            line.fill(0f)
            store = 0f
        }
    }

    private class Allpass(
        size: Int,
    ) {
        private val line = FloatArray(size.coerceAtLeast(1))
        private var index = 0

        fun process(input: Float): Float {
            val buffered = line[index]
            line[index] = input + buffered * 0.5f
            index = if (index + 1 == line.size) 0 else index + 1
            return buffered - input
        }

        fun clear() = line.fill(0f)
    }

    private companion object {
        const val BYTES_PER_SAMPLE = 2

        // jezar's freeverb tunings, in samples at 44.1 kHz
        val COMB_TUNING = intArrayOf(1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617)
        val ALLPASS_TUNING = intArrayOf(556, 441, 341, 225)
        const val STEREO_SPREAD = 23
        const val FIXED_GAIN = 0.015f
        const val SCALE_WET = 3f
        const val SCALE_DAMP = 0.4f
        const val SCALE_ROOM = 0.28f
        const val OFFSET_ROOM = 0.7f
        const val MAX_PRE_DELAY_MS = 150f
        const val TOGGLE_SECONDS = 0.15f
        const val ANTI_DENORMAL = 1e-18f
        const val KNEE = 0.89f

        /** a soft knee above -1 dB instead of a hard clip: a full room adds level on loud songs */
        fun toPcm(sample: Float): Short {
            val a = abs(sample)
            val shaped =
                if (a <= KNEE) {
                    sample
                } else {
                    val over = (a - KNEE) / (1f - KNEE)
                    (KNEE + (1f - KNEE) * tanh(over)) * if (sample < 0) -1f else 1f
                }
            val s = (shaped * 32767f).roundToInt()
            return s.coerceIn(-32768, 32767).toShort()
        }
    }
}
