// Ported from Sora (github.com/shizenworks/sora)'s remote libraries, after Neiro's WebDAV stack. Licensed under GPL-3.0.

// Music on a server the listener runs themselves, browsed and played like local files. A song from one is addressed as
// remote|<server id>|<file URL>, so a queue keeps it across restarts while the password stays in the secure settings.

package com.metrolist.shared.remote

import com.metrolist.shared.model.ArtistTiny
import com.metrolist.shared.model.SongItem
import com.metrolist.shared.settings.Settings
import com.metrolist.shared.youtube.playback.PlaybackResource
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64

@Serializable
data class RemoteServer(
    val id: String,
    val name: String,
    // the WebDAV folder the library starts at, as a full http or https URL
    val address: String,
    val username: String = "",
    val password: String = "",
)

/** one entry of a server folder: a subfolder, or an audio file at [url] */
data class RemoteEntry(
    val name: String,
    val url: String,
    val isFolder: Boolean,
)

/** Whether this platform can browse remote libraries: Android and the desktop, not iOS. */
expect val remoteLibrariesSupported: Boolean

/** Whether this app may reach [host] over plain http; Android allows https only. */
expect fun remotePlainHttpAllowed(host: String): Boolean

/** The folder at [url] on [server], folders first, audio files only. */
expect suspend fun listRemoteFolder(
    server: RemoteServer,
    url: String,
): Result<List<RemoteEntry>>

object RemoteLibraries {
    private const val PREFIX = "remote|"
    private val json = Json { ignoreUnknownKeys = true }

    private val audioExtensions = setOf("mp3", "flac", "aac", "m4a", "ogg", "opus", "wav", "wma", "aiff", "alac", "webm")

    fun servers(): List<RemoteServer> =
        Settings.shared.remoteLibraries
            .takeIf { it.isNotBlank() }
            ?.let { runCatching { json.decodeFromString<List<RemoteServer>>(it) }.getOrNull() }
            .orEmpty()

    fun save(servers: List<RemoteServer>) {
        Settings.shared.remoteLibraries = if (servers.isEmpty()) "" else json.encodeToString(servers)
    }

    fun upsert(server: RemoteServer) = save(servers().filterNot { it.id == server.id } + server)

    fun remove(id: String) = save(servers().filterNot { it.id == id })

    fun isAudioFile(name: String): Boolean = name.substringAfterLast('.', "").lowercase() in audioExtensions

    fun isRemote(songId: String): Boolean = songId.startsWith(PREFIX)

    private fun songId(
        server: RemoteServer,
        url: String,
    ) = "$PREFIX${server.id}|$url"

    // the server and file a remote song id names
    private fun parse(songId: String): Pair<RemoteServer, String>? {
        if (!isRemote(songId)) return null
        val rest = songId.removePrefix(PREFIX)
        val serverId = rest.substringBefore('|')
        val url = rest.substringAfter('|', "").takeIf { it.isNotBlank() } ?: return null
        val server = servers().firstOrNull { it.id == serverId } ?: return null
        return server to url
    }

    /** A file as a song: its name for the title, the server's name in place of an artist. */
    fun song(
        server: RemoteServer,
        entry: RemoteEntry,
    ): SongItem =
        SongItem(
            id = songId(server, entry.url),
            title = entry.name.substringBeforeLast('.'),
            thumbnail = null,
            artists = listOf(ArtistTiny(null, server.name)),
            album = null,
            // played from its own address, never looked up on YouTube
            isLocal = true,
        )

    fun authHeaders(server: RemoteServer): Map<String, String> =
        if (server.username.isBlank() && server.password.isBlank()) {
            emptyMap()
        } else {
            val credential = Base64.encode("${server.username}:${server.password}".encodeToByteArray())
            mapOf("Authorization" to "Basic $credential")
        }

    /** What the player plays for a remote song: the file's address with the server's sign-in; null for any other song. */
    fun playbackResource(song: SongItem): PlaybackResource? {
        val (server, url) = parse(song.id) ?: return null
        return PlaybackResource(
            videoId = song.id,
            url = url,
            headers = authHeaders(server),
            loudnessDb = null,
            expiresAt = null,
            durationSeconds = song.duration,
            clientName = "remote",
            profileId = "remote-library",
            requireBoundedRange = false,
        )
    }
}
