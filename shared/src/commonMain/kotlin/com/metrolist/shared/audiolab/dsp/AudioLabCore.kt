package com.metrolist.shared.audiolab.dsp

/**
 * One Audio Lab effect on interleaved float samples in -1..1. [process] works in place and returns false when it
 * left the samples alone (the effect is off and faded out), so the caller can pass the original bytes through.
 */
interface AudioLabCore {
    fun configure(
        sampleRate: Int,
        channels: Int,
    )

    fun reset()

    fun process(
        interleaved: FloatArray,
        frames: Int,
    ): Boolean
}
