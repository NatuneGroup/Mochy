// Ported and adapted from Mochy Music by realidkroo. Licensed under GPL-3.0.

package com.metrolist.shared.audiolab.dsp

import com.metrolist.shared.audiolab.SpatialMode
import com.metrolist.shared.audiolab.SpatialSettings
import kotlin.concurrent.Volatile
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.pow

/**
 * Headphone spatial audio. Crossfeed blends a shadowed copy of each ear into the other;
 * speakers place both channels at a virtual angle each side. Stereo only; other layouts pass through.
 * The output is not limited; the caller applies the soft knee.
 */
class SpatialCore : AudioLabCore {
    @Volatile private var settings = SpatialSettings()

    private var sampleRate = 0
    private val crossfeed = Crossfeed()
    private val speakers = HeadRenderer()

    // the gains actually applied, gliding toward the settings
    private var fade = 0f
    private var amount = 1f
    private var stateEmpty = true

    // Read once per buffer; when off, audio passes through after a short fade, like the reverb.
    fun update(newSettings: SpatialSettings) {
        settings = newSettings
    }

    override fun configure(
        sampleRate: Int,
        channels: Int,
    ) {
        if (channels != 2 || sampleRate <= 0) {
            this.sampleRate = 0
            return
        }
        if (sampleRate != this.sampleRate) {
            this.sampleRate = sampleRate
            clearState()
        }
        snapGains()
    }

    override fun reset() {
        // a seek or a new track: the old filter tails belong to audio that isn't playing any more
        clearState()
        // start where the settings are rather than fading in
        snapGains()
    }

    override fun settleIfIdle(): Boolean {
        if (sampleRate != 0 && (settings.enabled || fade != 0f)) return false
        if (!stateEmpty) clearState()
        return true
    }

    override fun process(
        interleaved: FloatArray,
        frames: Int,
    ): Boolean {
        if (frames <= 0 || settleIfIdle()) return false
        val params = settings
        val target = if (params.enabled) 1f else 0f
        stateEmpty = false
        val mode = params.mode
        if (mode == SpatialMode.Crossfeed) {
            crossfeed.configure(sampleRate, params.cutoffHz, params.feedDb)
        } else {
            speakers.configure(sampleRate, params.speakerAngle.coerceIn(MIN_ANGLE, MAX_ANGLE))
        }
        val targetAmount = params.amount.coerceIn(0f, 1f)
        val step = 1f / (sampleRate * TOGGLE_SECONDS)

        for (f in 0 until frames) {
            val i = f * 2
            val inL = interleaved[i]
            val inR = interleaved[i + 1]
            fade = glide(fade, target, step)
            amount = glide(amount, targetAmount, step)
            val g = fade * amount
            if (mode == SpatialMode.Crossfeed) {
                crossfeed.process(inL, inR)
                interleaved[i] = inL + (crossfeed.left - inL) * g
                interleaved[i + 1] = inR + (crossfeed.right - inR) * g
            } else {
                speakers.render(inL, inR)
                interleaved[i] = inL + (speakers.left * SPEAKER_GAIN - inL) * g
                interleaved[i + 1] = inR + (speakers.right * SPEAKER_GAIN - inR) * g
            }
        }
        return true
    }

    private fun snapGains() {
        val params = settings
        fade = if (params.enabled) 1f else 0f
        amount = params.amount.coerceIn(0f, 1f)
    }

    private fun clearState() {
        crossfeed.clear()
        speakers.clear()
        stateEmpty = true
    }

    private fun glide(
        value: Float,
        target: Float,
        step: Float,
    ): Float = if (target > value) minOf(target, value + step) else maxOf(target, value - step)

    /** bs2b-style crossfeed. Its filter state carries over when the settings move, so the sound doesn't jump. */
    private class Crossfeed {
        private var rate = 0
        private var cutoff = Float.NaN
        private var feed = Float.NaN
        private var a0Lo = 0f
        private var b1Lo = 0f
        private var a0Hi = 0f
        private var a1Hi = 0f
        private var b1Hi = 0f
        private var gain = 1f
        private var loL = 0f
        private var loR = 0f
        private var hiL = 0f
        private var hiR = 0f
        private var prevL = 0f
        private var prevR = 0f

        var left = 0f
            private set
        var right = 0f
            private set

        fun configure(
            sampleRate: Int,
            cutoffHz: Float,
            feedDb: Float,
        ) {
            val fc = cutoffHz.coerceIn(MIN_CUTOFF_HZ, MAX_CUTOFF_HZ).toDouble()
            val fd = feedDb.coerceIn(MIN_FEED_DB, MAX_FEED_DB).toDouble()
            if (sampleRate == rate && fc == cutoff.toDouble() && fd == feed.toDouble()) return
            rate = sampleRate
            cutoff = fc.toFloat()
            feed = fd.toFloat()
            val gbLo = fd * -5.0 / 6.0 - 3.0
            val gbHi = fd / 6.0 - 3.0
            val gLo = 10.0.pow(gbLo / 20.0)
            val gHi = 1.0 - 10.0.pow(gbHi / 20.0)
            val fcHi = fc * 2.0.pow((gbLo - 20.0 * log10(gHi)) / 12.0)
            val xLo = exp(-2.0 * PI * fc / sampleRate)
            b1Lo = xLo.toFloat()
            a0Lo = (gLo * (1.0 - xLo)).toFloat()
            val xHi = exp(-2.0 * PI * fcHi / sampleRate)
            b1Hi = xHi.toFloat()
            a0Hi = (1.0 - gHi * (1.0 - xHi)).toFloat()
            a1Hi = (-xHi).toFloat()
            gain = (1.0 / (1.0 - gHi + gLo)).toFloat()
        }

        fun process(
            l: Float,
            r: Float,
        ) {
            loL = a0Lo * l + b1Lo * loL
            loR = a0Lo * r + b1Lo * loR
            hiL = a0Hi * l + a1Hi * prevL + b1Hi * hiL
            hiR = a0Hi * r + a1Hi * prevR + b1Hi * hiR
            prevL = l
            prevR = r
            left = (hiL + loR) * gain
            right = (hiR + loL) * gain
        }

        fun clear() {
            loL = 0f
            loR = 0f
            hiL = 0f
            hiR = 0f
            prevL = 0f
            prevR = 0f
            left = 0f
            right = 0f
        }
    }

    /**
     * Brown and Duda's spherical head for two virtual speakers, one at -angle and one at +angle:
     * per speaker and ear, a Woodworth time delay and a first-order head-shadow filter.
     */
    private class HeadRenderer {
        private val line = Array(2) { FloatArray(DELAY_SIZE) }
        private var write = 0
        private val delay = FloatArray(4)
        private val b0 = FloatArray(4)
        private val b1 = FloatArray(4)
        private val a1 = FloatArray(4)
        private val x1 = FloatArray(4)
        private val y1 = FloatArray(4)
        private val raw = DoubleArray(4)
        private var rate = 0
        private var angle = Float.NaN

        var left = 0f
            private set
        var right = 0f
            private set

        fun configure(
            sampleRate: Int,
            angleDeg: Float,
        ) {
            if (sampleRate == rate && angleDeg == angle) return
            rate = sampleRate
            angle = angleDeg
            val w0 = SPEED_OF_SOUND / HEAD_RADIUS
            val k = 2.0 * sampleRate
            val norm = 2 * w0 + k
            val spread = angleDeg.toDouble()
            for (speaker in 0..1) {
                val az = if (speaker == 0) -spread else spread
                for (ear in 0..1) {
                    val i = speaker * 2 + ear
                    val earAz = if (ear == 0) -90.0 else 90.0
                    val theta = abs(wrapDegrees(az - earAz)) * PI / 180.0
                    val alpha = (1 + ALPHA_MIN / 2) + (1 - ALPHA_MIN / 2) * cos(theta / THETA_MIN * PI)
                    b0[i] = ((2 * w0 + alpha * k) / norm).toFloat()
                    b1[i] = ((2 * w0 - alpha * k) / norm).toFloat()
                    a1[i] = ((2 * w0 - k) / norm).toFloat()
                    raw[i] =
                        if (theta < PI / 2) {
                            -HEAD_RADIUS / SPEED_OF_SOUND * cos(theta)
                        } else {
                            HEAD_RADIUS / SPEED_OF_SOUND * (theta - PI / 2)
                        }
                }
            }
            val earliest = minOf(minOf(raw[0], raw[1]), minOf(raw[2], raw[3]))
            for (i in 0 until 4) {
                delay[i] = ((raw[i] - earliest) * sampleRate).toFloat().coerceIn(0f, (DELAY_SIZE - 2).toFloat())
            }
        }

        fun render(
            l: Float,
            r: Float,
        ) {
            line[0][write] = l
            line[1][write] = r
            var outL = 0f
            var outR = 0f
            for (speaker in 0..1) {
                val buf = line[speaker]
                for (ear in 0..1) {
                    val i = speaker * 2 + ear
                    val d = delay[i]
                    val whole = d.toInt()
                    val frac = d - whole
                    var p0 = write - whole
                    if (p0 < 0) p0 += DELAY_SIZE
                    val p1 = if (p0 == 0) DELAY_SIZE - 1 else p0 - 1
                    val x = buf[p0] + (buf[p1] - buf[p0]) * frac
                    val y = b0[i] * x + b1[i] * x1[i] - a1[i] * y1[i]
                    x1[i] = x
                    y1[i] = y
                    if (ear == 0) outL += y else outR += y
                }
            }
            write = if (write + 1 == DELAY_SIZE) 0 else write + 1
            left = outL
            right = outR
        }

        fun clear() {
            line.forEach { it.fill(0f) }
            x1.fill(0f)
            y1.fill(0f)
            left = 0f
            right = 0f
        }
    }

    private companion object {
        const val TOGGLE_SECONDS = 0.15f
        const val MIN_CUTOFF_HZ = 300f
        const val MAX_CUTOFF_HZ = 2000f
        const val MIN_FEED_DB = 1f
        const val MAX_FEED_DB = 15f
        const val MIN_ANGLE = 5f
        const val MAX_ANGLE = 90f
        const val SPEAKER_GAIN = 0.6f
        const val HEAD_RADIUS = 0.0875
        const val SPEED_OF_SOUND = 343.0
        const val ALPHA_MIN = 0.1
        const val THETA_MIN = 150.0 * PI / 180.0
        const val DELAY_SIZE = 256

        fun wrapDegrees(deg: Double): Double {
            var d = deg % 360.0
            if (d > 180.0) d -= 360.0
            if (d < -180.0) d += 360.0
            return d
        }
    }
}
