// Ported and adapted from Mochy Music by realidkroo. Licensed under GPL-3.0.

package com.metrolist.android.playback

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import com.metrolist.android.playback.stage.StageNative
import com.metrolist.shared.audiolab.StageSettings
import com.metrolist.shared.audiolab.toStageParams
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Stage room for stereo PCM16, run by the native stageEngine. Other formats pass through, and so does everything
 * while Stage is off: the bytes are copied without calling into native code. If the native library or the HRTF
 * asset cannot load, the processor passes audio through and playback is unaffected.
 */
@OptIn(UnstableApi::class)
internal class StageAudioProcessor(
    private val context: Context,
) : BaseAudioProcessor() {
    private class Snapshot(
        val enabled: Boolean,
        val values: FloatArray,
    )

    @Volatile private var snapshot = Snapshot(false, FloatArray(0))

    private var sampleRate = 0
    private var channels = 0
    private var handle = 0L
    private var pushed: FloatArray? = null
    // true while the room only passes the song through, so it can be skipped without the native call
    private var idle = true
    private var directCopy: ByteBuffer = ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder())

    /** Read once per settings change; the playback thread picks the new room up on its next buffer. */
    fun update(newSettings: StageSettings) {
        snapshot = Snapshot(newSettings.enabled, newSettings.toStageParams())
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT || inputAudioFormat.channelCount != 2 ||
            !StageNative.ready(context)
        ) {
            channels = 0
            release()
            return AudioProcessor.AudioFormat.NOT_SET
        }
        if (inputAudioFormat.sampleRate != sampleRate || inputAudioFormat.channelCount != channels) release()
        sampleRate = inputAudioFormat.sampleRate
        channels = inputAudioFormat.channelCount
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val bytes = inputBuffer.remaining()
        if (bytes == 0) return
        val state = snapshot
        if (!state.enabled && idle) {
            release()
            passThrough(inputBuffer)
            return
        }
        if (handle == 0L) {
            handle = StageNative.create(sampleRate, channels)
            pushed = null
            if (handle == 0L) {
                passThrough(inputBuffer)
                return
            }
        }
        if (state.values !== pushed) {
            StageNative.setParams(handle, state.values)
            pushed = state.values
        }
        val frames = bytes / (2 * channels)
        val source = if (inputBuffer.isDirect) inputBuffer else copyToDirect(inputBuffer)
        val out = replaceOutputBuffer(frames * 4)
        idle = StageNative.process(handle, source, source.position(), out, frames)
        out.position(0)
        out.limit(frames * 4)
        inputBuffer.position(inputBuffer.limit())
        if (!state.enabled && idle) release()
    }

    private fun passThrough(inputBuffer: ByteBuffer) {
        val out = replaceOutputBuffer(inputBuffer.remaining())
        out.put(inputBuffer)
        out.flip()
    }

    private fun copyToDirect(input: ByteBuffer): ByteBuffer {
        val size = input.remaining()
        if (directCopy.capacity() < size) directCopy = ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder())
        directCopy.clear()
        directCopy.put(input.duplicate())
        directCopy.flip()
        return directCopy
    }

    override fun onFlush(streamMetadata: AudioProcessor.StreamMetadata) {
        // a seek or a new song drops the room's tail, but keeps the engine allocated
        if (handle != 0L) StageNative.clear(handle)
        idle = true
    }

    override fun onReset() {
        release()
        channels = 0
        sampleRate = 0
        idle = true
    }

    private fun release() {
        if (handle != 0L) StageNative.destroy(handle)
        handle = 0L
        pushed = null
    }
}
