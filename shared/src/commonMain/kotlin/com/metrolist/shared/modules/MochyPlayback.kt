// Ported from Mochy Music by realidkroo. Licensed under GPL-3.0.

package com.metrolist.shared.modules

import com.metrolist.shared.model.SongItem
import com.metrolist.shared.model.isLive
import com.metrolist.shared.model.isPodcast
import com.metrolist.shared.model.isVideoSong
import com.metrolist.shared.youtube.playback.PlaybackNetworkStateStore
import com.metrolist.shared.youtube.playback.PlaybackResource
import io.ktor.http.Url

private val HEADER_NAME = Regex("[!#\$%&'*+.^_`|~0-9A-Za-z-]+")

internal val SongItem.canUseMochyModules: Boolean
    get() = !isLocal && !id.startsWith("local_") && !isUploaded && !isLive && !isPodcast && !isVideoSong

internal fun SongItem.toMochyTrackQuery(): TrackQuery =
    TrackQuery(
        title = canonicalTitle ?: title,
        artists = (canonicalArtists ?: artists).map { it.name },
        album = album?.name,
        durationMs = duration?.takeIf { it > 0 }?.times(1000),
    )

internal fun String.isMochyHttpUrl(): Boolean =
    (startsWith("https://", ignoreCase = true) || startsWith("http://", ignoreCase = true)) &&
        runCatching { Url(this).host.isNotBlank() }.getOrDefault(false)

suspend fun resolveMochyAudio(
    song: SongItem,
    supportsDash: Boolean = true,
): PlaybackResource? {
    if (!song.canUseMochyModules || !PlaybackNetworkStateStore.current.connected) return null
    val stream =
        MochyModuleRegistry.shared.resolveAudioStream(song.toMochyTrackQuery()) { candidate ->
            candidate.url.isMochyHttpUrl() &&
                (supportsDash || candidate.format != StreamFormat.Dash) &&
                (candidate.inlineManifest == null || candidate.format == StreamFormat.Dash) &&
                candidate.headers.all { (name, value) -> HEADER_NAME.matches(name) && '\r' !in value && '\n' !in value }
        } ?: return null
    return PlaybackResource(
        videoId = song.id,
        url = stream.url,
        headers = stream.headers,
        loudnessDb = null,
        expiresAt = null,
        mimeType =
            when (stream.format) {
                StreamFormat.Progressive -> null
                StreamFormat.Hls -> "application/x-mpegURL"
                StreamFormat.Dash -> "application/dash+xml"
            },
        sampleRate = stream.sampleRateHz,
        durationSeconds =
            stream.matched.durationMs
                ?.takeIf { it > 0L }
                ?.div(1000L) ?: song.duration,
        clientName = stream.sourceLabel,
        profileId = "mochy-module",
        requireBoundedRange = false,
        moduleStream = stream,
    )
}
