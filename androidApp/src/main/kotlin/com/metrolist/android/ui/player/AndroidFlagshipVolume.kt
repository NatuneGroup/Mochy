// Ported from Mochy Music by realidkroo. Licensed under GPL-3.0.

package com.metrolist.android.ui.player

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.metrolist.shared.ui.player.mochy.FlagshipVolume
import kotlin.math.roundToInt

/**
 * The phone's music volume as 0..1. A drag shows exactly where the finger is (the phone only has
 * a few steps); letting go settles on the step the phone actually took.
 */
private class AndroidFlagshipVolume(
    private val audio: AudioManager,
) : FlagshipVolume {
    private val steps = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    private var level by mutableFloatStateOf(read())
    private var dragging by mutableStateOf<Float?>(null)

    // the step last handed to the phone during a drag, so it's only told when the finger crosses into a new one
    private var sentStep = -1

    override val shown: Float get() = dragging ?: level

    override val isDragging: Boolean get() = dragging != null

    fun read(): Float = audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / steps

    fun refresh() {
        level = read()
    }

    override fun drag(fraction: Float) {
        dragging = fraction
        // the call is slow on some phones, and made on every move of the finger it made the drag lag
        val step = (fraction * steps).roundToInt()
        if (step == sentStep) return
        sentStep = step
        // some phones refuse volume changes in Do Not Disturb; the line just stays where it is
        runCatching { audio.setStreamVolume(AudioManager.STREAM_MUSIC, step, 0) }
    }

    override fun release() {
        dragging = null
        sentStep = -1
        refresh()
    }
}

/** the music volume, following the volume keys while [listening] */
@Composable
internal fun rememberAndroidFlagshipVolume(listening: Boolean): FlagshipVolume {
    val context = LocalContext.current
    val volume =
        remember(context) {
            AndroidFlagshipVolume(context.getSystemService(Context.AUDIO_SERVICE) as AudioManager)
        }
    DisposableEffect(volume, listening) {
        if (!listening) return@DisposableEffect onDispose { }
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    context: Context,
                    intent: Intent,
                ) {
                    val stream = intent.getIntExtra("android.media.EXTRA_VOLUME_STREAM_TYPE", -1)
                    if (stream == AudioManager.STREAM_MUSIC) volume.refresh()
                }
            }
        // the volume keys may have moved it while the bar was away
        volume.refresh()
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter("android.media.VOLUME_CHANGED_ACTION"),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose { context.unregisterReceiver(receiver) }
    }
    return volume
}
