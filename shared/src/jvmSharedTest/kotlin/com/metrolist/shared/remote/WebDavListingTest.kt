package com.metrolist.shared.remote

import okhttp3.HttpUrl.Companion.toHttpUrl
import kotlin.test.Test
import kotlin.test.assertEquals

class WebDavListingTest {
    // a Nextcloud-shaped answer: absolute-path hrefs, the folder itself listed first, a percent-encoded name and a non-audio file
    private val nextcloudListing =
        """<?xml version="1.0"?>
<d:multistatus xmlns:d="DAV:" xmlns:s="http://sabredav.org/ns" xmlns:oc="http://owncloud.org/ns">
  <d:response><d:href>/remote.php/dav/files/me/Music/</d:href>
    <d:propstat><d:prop><d:resourcetype><d:collection/></d:resourcetype></d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>
  <d:response><d:href>/remote.php/dav/files/me/Music/Albums/</d:href>
    <d:propstat><d:prop><d:resourcetype><d:collection/></d:resourcetype></d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>
  <d:response><d:href>/remote.php/dav/files/me/Music/Blinding%20Lights.flac</d:href>
    <d:propstat><d:prop><d:resourcetype/></d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>
  <d:response><d:href>/remote.php/dav/files/me/Music/cover.jpg</d:href>
    <d:propstat><d:prop><d:resourcetype/></d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>
</d:multistatus>"""

    @Test
    fun absoluteHrefsResolveAgainstTheServerNotTheFolder() {
        val folder = "https://cloud.example.org/remote.php/dav/files/me/Music/".toHttpUrl()
        val entries = parseWebDavListing(folder, nextcloudListing.byteInputStream())
        assertEquals(
            listOf(
                RemoteEntry("Albums", "https://cloud.example.org/remote.php/dav/files/me/Music/Albums/", isFolder = true),
                RemoteEntry(
                    "Blinding Lights.flac",
                    "https://cloud.example.org/remote.php/dav/files/me/Music/Blinding%20Lights.flac",
                    isFolder = false,
                ),
            ),
            entries,
        )
    }
}
