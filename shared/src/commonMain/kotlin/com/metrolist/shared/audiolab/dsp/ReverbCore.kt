// Ported and adapted from Mochy Music by realidkroo. Licensed under GPL-3.0.

package com.metrolist.shared.audiolab.dsp

import com.metrolist.shared.audiolab.ReverbSettings
import kotlin.concurrent.Volatile
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.roundToInt

/**
 * Freeverb-style room: eight damped combs into four allpasses per side, the right side detuned for width.
 * A mono pre-delay and low cut go in front so the bass stays dry. Gains glide across each buffer so dragging doesn't zipper.
 * Mono or stereo. The output is not limited; the caller applies the soft knee.
 */
class ReverbCore : AudioLabCore {
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

    override fun configure(
        sampleRate: Int,
        channels: Int,
    ) {
        if (sampleRate <= 0 || channels !in 1..2) {
            this.sampleRate = 0
            this.channels = 0
            return
        }
        if (sampleRate != this.sampleRate || channels != this.channels) {
            this.sampleRate = sampleRate
            this.channels = channels
            build(sampleRate)
        }
        snapGains()
    }

    override fun reset() {
        // a seek or a new track: the old room's tail belongs to audio that isn't playing any more
        clearRoom()
        // start where the settings are rather than fading in
        snapGains()
    }

    override fun process(
        interleaved: FloatArray,
        frames: Int,
    ): Boolean {
        if (frames <= 0) return false
        val params = settings
        val target = if (params.enabled) 1f else 0f
        if (channels == 0 || (target == 0f && amount == 0f)) {
            if (!roomEmpty) clearRoom()
            return false
        }
        roomEmpty = false

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

        for (f in 0 until frames) {
            val i = f * channels
            val inL = interleaved[i]
            val inR = if (channels == 2) interleaved[i + 1] else inL

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
                interleaved[i] = inL + (roomL - inL) * g
                interleaved[i + 1] = inR + (roomR - inR) * g
            } else {
                val room = accL * (wet1 + wet2) + inL * dryGain
                interleaved[i] = inL + (room - inL) * g
            }
        }
        wet1 = targetWet1
        wet2 = targetWet2
        dryGain = targetDry
        mix = targetMix
        return true
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
    }
}
