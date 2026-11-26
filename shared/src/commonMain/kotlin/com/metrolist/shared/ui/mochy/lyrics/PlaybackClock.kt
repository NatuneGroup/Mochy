// Ported from Mochy Music by realidkroo (github.com/realidkroo/mochy-music). Licensed under GPL-3.0.

package com.metrolist.shared.ui.mochy.lyrics

import androidx.compose.runtime.Stable
import kotlin.math.abs
import kotlin.math.exp
import kotlin.time.TimeSource

// further apart than this (ms) and the clock jumps straight to the player, because that was a seek
private const val SNAP_MS = 250.0

// how fast a small gap to the player's own position is eased away
private const val PULL_TIME_MS = 60.0

// a longer break between reads than this (ms) means nothing was watching, so start again from the player
private const val STALE_MS = 200L

// a report is carried on at most this far (ms); the playback loop wakes at least every second, so past this the song really has stalled
private const val CARRY_MAX_MS = 2000L

private val ClockStart = TimeSource.Monotonic.markNow()

// the playback position for the frame on screen, so lyrics glide every frame instead of stepping with the position poller
@Stable
class PlaybackClock(
    private val readPositionMs: () -> Long,
    private val readIsPlaying: () -> Boolean,
    private val readSpeed: () -> Float = { 1f },
    private val offsetMs: () -> Long = { 0L },
) : () -> Long {
    private var frameMs = Long.MIN_VALUE
    private var smoothed = 0.0

    // the last position the player reported, and about when that was true
    private var reportedMs = Long.MIN_VALUE
    private var reportedAtMs = 0L

    override fun invoke(): Long {
        // every reader within the same millisecond sees the same time
        val now = ClockStart.elapsedNow().inWholeMilliseconds
        if (now != frameMs) advance(now)
        return smoothed.toLong() + offsetMs()
    }

    private fun advance(now: Long) {
        val playing = readIsPlaying()
        val speed = readSpeed()
        val fresh = frameMs == Long.MIN_VALUE || now - frameMs !in 1..STALE_MS
        val elapsed = (now - frameMs).toDouble()
        frameMs = now
        val heard = carried(readPositionMs().coerceAtLeast(0L), now, elapsed, fresh, playing, speed)
        if (fresh) {
            smoothed = heard
            return
        }
        // move on by the frame's own time, then ease out the little jitter in what the player reports
        val predicted = if (playing) smoothed + elapsed * speed else smoothed
        val gap = heard - predicted
        smoothed = if (abs(gap) > SNAP_MS) heard else predicted + gap * (1.0 - exp(-elapsed / PULL_TIME_MS))
    }

    // the player's position only moves when its playback loop wakes, so it's carried on by the time since
    private fun carried(
        reported: Long,
        now: Long,
        elapsed: Double,
        fresh: Boolean,
        playing: Boolean,
        speed: Float,
    ): Double {
        if (fresh || reported != reportedMs) {
            reportedMs = reported
            // it changed somewhere between the last frame and this one, so call it halfway
            reportedAtMs = if (fresh) now else now - (elapsed / 2.0).toLong()
        }
        if (!playing) {
            // paused, it holds still, and the carry starts over the moment it plays again
            reportedAtMs = now
            return reported.toDouble()
        }
        return reported + (now - reportedAtMs).coerceAtMost(CARRY_MAX_MS) * speed.toDouble()
    }
}
