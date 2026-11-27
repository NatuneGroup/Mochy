// Ported from Sora (github.com/shizenworks/sora)'s WebDAV client, after Neiro's. Licensed under GPL-3.0.

package com.metrolist.shared.remote

import com.metrolist.shared.network.sharedOkHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.w3c.dom.Element
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit
import javax.xml.parsers.DocumentBuilderFactory

actual val remoteLibrariesSupported: Boolean = true

private const val PROPFIND_BODY =
    """<?xml version="1.0" encoding="utf-8"?>
<d:propfind xmlns:d="DAV:"><d:prop><d:displayname/><d:resourcetype/></d:prop></d:propfind>"""

// the shared client, so the Internet page's DNS over HTTPS and proxy apply here too
private val webDavClient by lazy {
    sharedOkHttpClient
        .newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
}

// one level at a time: many servers, Nextcloud among them, refuse Depth: infinity
actual suspend fun listRemoteFolder(
    server: RemoteServer,
    url: String,
): Result<List<RemoteEntry>> =
    withContext(Dispatchers.IO) {
        runCatching {
            val folder = (if (url.endsWith("/")) url else "$url/").toHttpUrlOrNull() ?: throw IOException("Not a web address: $url")
            val request =
                Request
                    .Builder()
                    .url(folder)
                    .method("PROPFIND", PROPFIND_BODY.toRequestBody("application/xml; charset=utf-8".toMediaType()))
                    .header("Depth", "1")
                    .apply { RemoteLibraries.authHeaders(server).forEach { (name, value) -> header(name, value) } }
                    .build()
            webDavClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                parseWebDavListing(folder, response.body.byteStream())
            }
        }
    }

// a PROPFIND Depth: 1 answer as the folder's subfolders and audio files, without the folder itself
internal fun parseWebDavListing(
    folder: HttpUrl,
    body: InputStream,
): List<RemoteEntry> {
    val document =
        DocumentBuilderFactory
            .newInstance()
            .apply {
                isNamespaceAware = true
                // a listing never needs a DTD, and refusing one keeps entity tricks out
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            }.newDocumentBuilder()
            .parse(body)
    val responses = document.getElementsByTagNameNS("DAV:", "response")
    return (0 until responses.length)
        .mapNotNull { index ->
            val element = responses.item(index) as? Element ?: return@mapNotNull null
            val href = element.getElementsByTagNameNS("DAV:", "href").item(0)?.textContent ?: return@mapNotNull null
            // the href is usually an absolute path, so it's resolved against the folder rather than appended to it
            val target = folder.resolve(href.trim()) ?: return@mapNotNull null
            if (target.encodedPath.trimEnd('/') == folder.encodedPath.trimEnd('/')) return@mapNotNull null
            val isFolder = element.getElementsByTagNameNS("DAV:", "collection").length > 0
            val fileName = target.pathSegments.lastOrNull { it.isNotEmpty() } ?: return@mapNotNull null
            if (!isFolder && !RemoteLibraries.isAudioFile(fileName)) return@mapNotNull null
            val name =
                element
                    .getElementsByTagNameNS("DAV:", "displayname")
                    .item(0)
                    ?.textContent
                    ?.takeIf { it.isNotBlank() } ?: fileName
            RemoteEntry(name = name, url = target.toString(), isFolder = isFolder)
        }.sortedWith(compareByDescending<RemoteEntry> { it.isFolder }.thenBy { it.name.lowercase() })
}
