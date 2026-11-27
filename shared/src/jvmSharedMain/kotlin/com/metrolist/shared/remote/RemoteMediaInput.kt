// Ported from Sora (github.com/shizenworks/sora)'s SmbDataSource, after Neiro's. Licensed under GPL-3.0.

package com.metrolist.shared.remote

import com.metrolist.shared.playback.SeekableMediaSource
import com.metrolist.shared.playback.ffmpeg.PlaybackPositionInput
import com.metrolist.shared.playback.ffmpeg.SeekableMediaInput
import java.io.InputStream

/**
 * An audio file on an SMB share as the players read it: Android's ExoPlayer through its seekable source, the
 * desktop's decoder as a stream. The file opens on first use, on the player's loading thread rather than wherever
 * the source was built, and every read names its own offset, so seeking only moves the position.
 */
internal class SmbMediaInput(
    private val server: RemoteServer,
    private val url: String,
) : InputStream(),
    SeekableMediaInput,
    PlaybackPositionInput {
    private val opening = lazy { SmbShares.open(server, url) }
    private val opened by opening
    private val file get() = opened.first

    override val mediaSize: Long get() = opened.second

    override var mediaPosition: Long = 0L
        private set

    override val streamFailure: Throwable? = null

    override fun updatePlaybackPosition(positionMs: Long) = Unit

    @Synchronized
    override fun read(): Int {
        val one = ByteArray(1)
        return if (read(one, 0, 1) <= 0) -1 else one[0].toInt() and 0xFF
    }

    @Synchronized
    override fun read(
        buffer: ByteArray,
        offset: Int,
        length: Int,
    ): Int = readAtMediaPosition(mediaPosition, buffer, offset, length).also { if (it > 0) mediaPosition += it }

    @Synchronized
    override fun readAtMediaPosition(
        position: Long,
        buffer: ByteArray,
        offset: Int,
        length: Int,
    ): Int {
        if (length == 0) return 0
        if (position >= mediaSize) return -1
        val wanted = minOf(length.toLong(), mediaSize - position).toInt()
        return file.read(buffer, position, offset, wanted).takeIf { it > 0 } ?: -1
    }

    @Synchronized
    override fun seekToMediaPosition(position: Long): Long {
        mediaPosition = position.coerceIn(0L, mediaSize)
        return mediaPosition
    }

    override fun skip(n: Long): Long {
        val from = mediaPosition
        return seekToMediaPosition(from + n.coerceAtLeast(0L)) - from
    }

    override fun available(): Int = (mediaSize - mediaPosition).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()

    override fun close() {
        // never opened, nothing to close
        if (opening.isInitialized()) runCatching { file.close() }
    }
}

/**
 * The seekable input for a remote file the players can't open themselves: an smb:// address of one of the saved
 * servers. Null for any other address, which the players open as usual.
 */
fun openRemoteMediaSource(url: String): SeekableMediaSource? {
    if (!url.startsWith("smb://", ignoreCase = true)) return null
    val server = RemoteLibraries.serverFor(url) ?: return null
    return SmbMediaInput(server, url)
}
