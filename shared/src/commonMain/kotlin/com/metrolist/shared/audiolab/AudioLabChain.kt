package com.metrolist.shared.audiolab

import com.metrolist.shared.audiolab.dsp.EightDCore
import com.metrolist.shared.audiolab.dsp.ReverbCore
import com.metrolist.shared.audiolab.dsp.SpatialCore
import com.metrolist.shared.audiolab.dsp.audioLabSoftKnee
import com.metrolist.shared.settings.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Spatial, reverb and 8D in the Android order, for the desktop and iOS players. Settings flow into the cores from
 * [scope]; [process] and [processPcm16] run on the audio thread and never touch Compose state.
 */
class AudioLabChain(
    scope: CoroutineScope,
) {
    private val spatial = SpatialCore()
    private val reverb = ReverbCore()
    private val eightD = EightDCore()
    private var channels = 0
    private var scratch = FloatArray(0)

    init {
        spatial.update(Settings.shared.spatialSettings())
        reverb.update(Settings.shared.reverbSettings())
        eightD.update(Settings.shared.eightDSettings())
    }

    private val jobs: List<Job> =
        listOf(
            scope.launch { Settings.shared.spatialSettingsFlow().collect { spatial.update(it) } },
            scope.launch { Settings.shared.reverbSettingsFlow().collect { reverb.update(it) } },
            scope.launch { Settings.shared.eightDSettingsFlow().collect { eightD.update(it) } },
        )

    fun configure(
        sampleRate: Int,
        channels: Int,
    ) {
        this.channels = if (channels in 1..2) channels else 0
        spatial.configure(sampleRate, channels)
        reverb.configure(sampleRate, channels)
        eightD.configure(sampleRate, channels)
    }

    fun reset() {
        spatial.reset()
        reverb.reset()
        eightD.reset()
    }

    fun close() {
        jobs.forEach { it.cancel() }
    }

    /** Runs the chain over [frames] interleaved float frames in -1..1, in place. */
    fun process(
        interleaved: FloatArray,
        frames: Int,
    ) {
        if (channels == 0 || frames <= 0) return
        val count = frames * channels
        // the Android processors limit each effect's output at the PCM16 edge; the same happens here
        if (spatial.process(interleaved, frames)) applySoftKnee(interleaved, count)
        if (reverb.process(interleaved, frames)) applySoftKnee(interleaved, count)
        if (eightD.process(interleaved, frames)) clip(interleaved, count)
    }

    /** Runs the chain over little-endian PCM16 bytes in place; only whole frames within [byteCount] are changed. */
    fun processPcm16(
        bytes: ByteArray,
        byteCount: Int,
    ) {
        val frames = if (channels == 0) 0 else byteCount / (2 * channels)
        if (frames <= 0) return
        // all three off and faded out: leave the bytes alone instead of converting every sample
        val spatialIdle = spatial.settleIfIdle()
        val reverbIdle = reverb.settleIfIdle()
        if (spatialIdle && reverbIdle && eightD.settleIfIdle()) return
        val count = frames * channels
        if (scratch.size < count) scratch = FloatArray(count)
        for (i in 0 until count) {
            val sample = (bytes[2 * i].toInt() and 0xff) or (bytes[2 * i + 1].toInt() shl 8)
            scratch[i] = sample / 32768f
        }
        process(scratch, frames)
        for (i in 0 until count) {
            val sample = (scratch[i] * 32768f).roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            bytes[2 * i] = sample.toByte()
            bytes[2 * i + 1] = (sample shr 8).toByte()
        }
    }

    private fun applySoftKnee(
        samples: FloatArray,
        count: Int,
    ) {
        for (i in 0 until count) samples[i] = audioLabSoftKnee(samples[i])
    }

    private fun clip(
        samples: FloatArray,
        count: Int,
    ) {
        for (i in 0 until count) samples[i] = samples[i].coerceIn(-1f, 1f)
    }
}
