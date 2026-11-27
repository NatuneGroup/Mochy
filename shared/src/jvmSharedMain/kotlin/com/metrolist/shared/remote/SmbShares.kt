// Ported from Sora (github.com/shizenworks/sora)'s SMB libraries, after Neiro's. Licensed under GPL-3.0.

// A server whose address is smb://host[:port]/share/folder lists and plays through SMB2/3 with smbj. One session per
// server is kept and dropped on any failure, so a broken connection isn't reused on the next try.

package com.metrolist.shared.remote

import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import com.hierynomus.smbj.share.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/** An smb:// address taken apart: the host, its port, the share and the path inside it. */
internal data class SmbLocation(
    val host: String,
    val port: Int,
    val share: String,
    val path: String,
) {
    fun child(name: String): String =
        "smb://$host${if (port == DEFAULT_PORT) "" else ":$port"}/$share/${if (path.isEmpty()) name else "$path/$name"}"

    companion object {
        const val DEFAULT_PORT = 445

        fun parse(url: String): SmbLocation? {
            if (!url.startsWith("smb://", ignoreCase = true)) return null
            val rest = url.substring("smb://".length).trimEnd('/')
            val authority = rest.substringBefore('/')
            val segments = rest.substringAfter('/', "").split('/').filter { it.isNotEmpty() }
            val share = segments.firstOrNull() ?: return null
            val host = authority.substringBefore(':').takeIf { it.isNotBlank() } ?: return null
            val port = authority.substringAfter(':', "").toIntOrNull() ?: DEFAULT_PORT
            return SmbLocation(host, port, share, segments.drop(1).joinToString("/"))
        }
    }
}

internal object SmbShares {
    private val client by lazy { SMBClient() }
    private val sessions = ConcurrentHashMap<String, Session>()

    private fun key(
        server: RemoteServer,
        location: SmbLocation,
    ) = "${server.id}@${location.host}:${location.port}"

    private fun session(
        server: RemoteServer,
        location: SmbLocation,
    ): Session {
        sessions[key(server, location)]?.takeIf { it.connection.isConnected }?.let { return it }
        val connection = client.connect(location.host, location.port)
        val auth =
            if (server.username.isBlank() && server.password.isEmpty()) {
                AuthenticationContext.guest()
            } else {
                // DOMAIN\user signs in to that domain
                val domain = server.username.substringBefore('\\', "")
                val user = server.username.substringAfter('\\')
                AuthenticationContext(user, server.password.toCharArray(), domain)
            }
        return connection.authenticate(auth).also { sessions[key(server, location)] = it }
    }

    // runs [block] on the share, dropping the session if anything goes wrong
    private fun <T> withShare(
        server: RemoteServer,
        location: SmbLocation,
        block: (DiskShare) -> T,
    ): T =
        try {
            val share =
                session(server, location).connectShare(location.share) as? DiskShare
                    ?: throw IOException("${location.share} isn't a file share")
            block(share)
        } catch (error: Exception) {
            sessions.remove(key(server, location))?.let { runCatching { it.close() } }
            throw if (error is IOException) error else IOException(error.message ?: error.toString(), error)
        }

    fun list(
        server: RemoteServer,
        url: String,
    ): List<RemoteEntry> {
        val location = SmbLocation.parse(url) ?: throw IOException("Not an smb:// address: $url")
        return withShare(server, location) { share ->
            share
                .list(location.path)
                .filter { it.fileName != "." && it.fileName != ".." && !it.fileName.startsWith(".") }
                .mapNotNull { info ->
                    val isFolder = info.fileAttributes and FileAttributes.FILE_ATTRIBUTE_DIRECTORY.value != 0L
                    if (!isFolder && !RemoteLibraries.isAudioFile(info.fileName)) return@mapNotNull null
                    RemoteEntry(name = info.fileName, url = location.child(info.fileName) + if (isFolder) "/" else "", isFolder = isFolder)
                }.sortedWith(compareByDescending<RemoteEntry> { it.isFolder }.thenBy { it.name.lowercase() })
        }
    }

    /** An audio file on the share opened for reading, with its length. */
    fun open(
        server: RemoteServer,
        url: String,
    ): Pair<File, Long> {
        val location = SmbLocation.parse(url) ?: throw IOException("Not an smb:// address: $url")
        return withShare(server, location) { share ->
            val file =
                share.openFile(
                    location.path,
                    setOf(AccessMask.GENERIC_READ),
                    null,
                    setOf(SMB2ShareAccess.FILE_SHARE_READ),
                    SMB2CreateDisposition.FILE_OPEN,
                    null,
                )
            file to file.fileInformation.standardInformation.endOfFile
        }
    }
}
