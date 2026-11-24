package com.metrolist.shared.playback

import com.metrolist.shared.settings.Settings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.roundToLong
import kotlin.time.TimeSource

/**
 * Mochy-style smoothstep fade controller for play, pause, and resume transitions.
 *
 * Mirrors Mochy Music's TransportTransitions ergonomics across Android, Desktop, and iOS:
 * - Pausing fades gain down over a cubic Hermite smoothstep curve, pauses at silence, and rewinds
 *   to the exact position where pause was tapped so resuming picks up without losing audio.
 * - Calling pause a second time mid-fade stops immediately without rewinding.
 * - Calling resume mid-pause cancels the fade-out and swells smoothly back up from the current
 *   gain level over the remaining duration.
 */
class MochyTransportFader(
    private val scope: CoroutineScope,
    private val onGainChanged: (Float) -> Unit,
) {
    private enum class Phase { IDLE, PAUSING, RESUMING }

    private var phase = Phase.IDLE
    private var job: Job? = null
    private var level = 1f
    private var playIntentOverride: Boolean? = null

    val gain: Float
        get() = level

    val isPausing: Boolean
        get() = phase == Phase.PAUSING

    val isFading: Boolean
        get() = phase != Phase.IDLE

    fun effectivePlaying(enginePlaying: Boolean): Boolean = playIntentOverride ?: enginePlaying

    fun effectiveWantsPlayback(engineWantsPlayback: Boolean): Boolean = playIntentOverride ?: engineWantsPlayback

    fun cancel() {
        val hadActiveFade = job != null || phase != Phase.IDLE || level != 1f || playIntentOverride != null
        job?.cancel()
        job = null
        phase = Phase.IDLE
        playIntentOverride = null
        level = 1f
        if (hadActiveFade) {
            onGainChanged(1f)
        }
    }

    fun pause(
        songId: String?,
        currentSongId: () -> String?,
        isLive: Boolean,
        isPlaying: Boolean,
        canFade: Boolean,
        currentPositionMs: () -> Long,
        pauseImmediate: () -> Unit,
        rewindTo: (Long) -> Unit,
        onIntentChanged: ((Boolean?) -> Unit)? = null,
    ) {
        if (phase == Phase.PAUSING) {
            // Second pause press stops immediately without rewinding.
            stopJobAndReset(resetGain = false)
            pauseImmediate()
            setGain(1f)
            onIntentChanged?.invoke(null)
            return
        }
        if (!isPlaying) {
            cancel()
            onIntentChanged?.invoke(null)
            return
        }
        val lengthMs = configuredDurationMs()
        if (!canFade || lengthMs <= 0L || !scope.isActive) {
            cancel()
            pauseImmediate()
            onIntentChanged?.invoke(null)
            return
        }
        val from = level.coerceIn(0f, 1f)
        if (from <= 0.001f) {
            cancel()
            pauseImmediate()
            onIntentChanged?.invoke(null)
            return
        }
        stopJobAndReset(resetGain = false)
        val anchorMs = currentPositionMs().coerceAtLeast(0L)
        val fadeMs = max((lengthMs * from).roundToLong(), MIN_PARTIAL_FADE_MS)
        phase = Phase.PAUSING
        playIntentOverride = false
        onIntentChanged?.invoke(false)
        job =
            scope.launch {
                try {
                    val completed =
                        runCurve(fadeMs) { progress ->
                            if (songId != null && currentSongId() != songId) return@runCurve false
                            setGain(from * (1f - transportSmoothstep(progress)))
                            true
                        }
                    if (!completed) {
                        stopJobAndReset(resetGain = true)
                        onIntentChanged?.invoke(null)
                        return@launch
                    }
                    setGain(0f)
                    stopJobAndReset(resetGain = false)
                    pauseImmediate()
                    if (!isLive && anchorMs >= 0L) {
                        rewindTo(anchorMs)
                    }
                    setGain(1f)
                    onIntentChanged?.invoke(null)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Throwable) {
                    stopJobAndReset(resetGain = true)
                    pauseImmediate()
                    onIntentChanged?.invoke(null)
                }
            }
    }

    fun resume(
        songId: String?,
        currentSongId: () -> String?,
        isAlreadyPlaying: Boolean,
        canFade: Boolean,
        resumeImmediate: () -> Unit,
        onIntentChanged: ((Boolean?) -> Unit)? = null,
    ) {
        val resumingMidPause = phase == Phase.PAUSING
        val from = if (resumingMidPause) level.coerceIn(0f, 1f) else 0f
        if (!resumingMidPause && isAlreadyPlaying) {
            cancel()
            onIntentChanged?.invoke(null)
            return
        }
        val lengthMs = configuredDurationMs()
        if (!canFade || lengthMs <= 0L || !scope.isActive) {
            cancel()
            if (!resumingMidPause) resumeImmediate()
            onIntentChanged?.invoke(null)
            return
        }
        stopJobAndReset(resetGain = false)
        val fadeMs =
            if (resumingMidPause) {
                max((lengthMs * (1f - from)).roundToLong(), MIN_PARTIAL_FADE_MS)
            } else {
                lengthMs
            }
        setGain(from)
        phase = Phase.RESUMING
        playIntentOverride = true
        onIntentChanged?.invoke(true)
        if (!resumingMidPause) {
            resumeImmediate()
        }
        job =
            scope.launch {
                try {
                    val completed =
                        runCurve(fadeMs) { progress ->
                            if (songId != null && currentSongId() != songId) return@runCurve false
                            setGain(from + (1f - from) * transportSmoothstep(progress))
                            true
                        }
                    stopJobAndReset(resetGain = true)
                    onIntentChanged?.invoke(null)
                    if (!completed) return@launch
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Throwable) {
                    stopJobAndReset(resetGain = true)
                    onIntentChanged?.invoke(null)
                }
            }
    }

    private fun configuredDurationMs(): Long {
        val settings = Settings.shared
        if (!settings.transportFadeEnabled) return 0L
        return (settings.transportFadeSeconds.coerceIn(MIN_SECONDS, MAX_SECONDS) * 1000f).roundToLong()
    }

    private suspend fun runCurve(
        durationMs: Long,
        onStep: (Float) -> Boolean,
    ): Boolean {
        if (durationMs <= 0L) return onStep(1f)
        val start = TimeSource.Monotonic.markNow()
        while (true) {
            val elapsedMs = start.elapsedNow().inWholeMilliseconds
            val progress = (elapsedMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
            if (!onStep(progress)) return false
            if (progress >= 1f) return true
            delay(FRAME_MS)
        }
    }

    private fun stopJobAndReset(resetGain: Boolean) {
        job?.cancel()
        job = null
        phase = Phase.IDLE
        playIntentOverride = null
        if (resetGain) {
            setGain(1f)
        }
    }

    private fun setGain(value: Float) {
        val clamped = value.coerceIn(0f, 1f)
        level = clamped
        onGainChanged(clamped)
    }

    companion object {
        private const val FRAME_MS = 16L
        private const val MIN_PARTIAL_FADE_MS = 200L
        const val MIN_SECONDS = 0.5f
        const val MAX_SECONDS = 10f
    }
}

internal fun transportSmoothstep(t: Float): Float {
    val x = t.coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}
