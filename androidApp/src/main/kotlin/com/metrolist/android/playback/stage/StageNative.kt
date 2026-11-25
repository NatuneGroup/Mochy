// Ported and adapted from Mochy Music by realidkroo. Licensed under GPL-3.0.

package com.metrolist.android.playback.stage

import android.content.Context
import java.nio.ByteBuffer

/**
 * JNI bridge to the mochystage library built by the stageEngine module. Handles are only passed back here, on the
 * playback thread. [ready] loads the library and the HRTF asset once; when either fails, Stage stays off.
 */
internal object StageNative {
    private const val HRTF_ASSET = "stage_hrtf.bin"

    private var loaded: Boolean? = null

    @Synchronized
    fun ready(context: Context): Boolean {
        loaded?.let { return it }
        val ok =
            runCatching {
                System.loadLibrary("mochystage")
                val bytes = context.applicationContext.assets.open(HRTF_ASSET).use { it.readBytes() }
                loadHrtf(bytes)
            }.getOrDefault(false)
        loaded = ok
        return ok
    }

    private external fun loadHrtf(data: ByteArray): Boolean

    external fun create(
        sampleRate: Int,
        channels: Int,
    ): Long

    external fun setParams(
        handle: Long,
        params: FloatArray,
    )

    external fun process(
        handle: Long,
        input: ByteBuffer,
        inputOffset: Int,
        output: ByteBuffer,
        frames: Int,
    ): Boolean

    external fun levels(
        handle: Long,
        out: FloatArray,
    )

    external fun clear(handle: Long)

    external fun destroy(handle: Long)
}
