/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 *
 * Adapted from BitChord (GPL-3.0) https://github.com/kushagrasinghx/BitChord
 * (shared/src/jvmSharedMain/kotlin/com/music/bitchord/data/lyrics/BiniLyrics.kt) —
 * original re-implementation for ArchiveTune.
 */

// Ported to Mochy from Natune (NatuneGroup).

package com.metrolist.shared.api.lyrics

import io.ktor.client.HttpClient
import io.ktor.client.request.parameter
import io.ktor.client.request.prepareGet
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * BiniLyrics: a track, artist and album search returns the URL of a lyrics document. That document
 * is read as TTML, as a JSON envelope, as LRC or as plain text.
 */
class BiniLyricsProvider(
    private val httpClient: HttpClient,
) {
    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    suspend fun getLyrics(
        title: String,
        artist: String,
        album: String?,
        durationSec: Int,
    ): String? {
        if (title.isBlank() && artist.isBlank()) return null
        val documentUrl = findDocumentUrl(title, artist, album, durationSec) ?: return null
        val body = httpClient.prepareGet(documentUrl) { expectMissingLyrics() }.readLyricsText(MAX_DOCUMENT_BYTES) ?: return null
        return payloadToMlx(body, title, artist, unwrapped = false)
    }

    private suspend fun findDocumentUrl(
        title: String,
        artist: String,
        album: String?,
        durationSec: Int,
    ): String? {
        val body =
            httpClient
                .prepareGet(BASE_URL) {
                    expectMissingLyrics()
                    parameter("track", title.forLyricsSearch())
                    parameter("artist", artist.artistForLyricsSearch())
                    if (!album.isNullOrBlank()) parameter("album", album.trim())
                    if (durationSec > 0) parameter("duration", durationSec)
                }.readLyricsText() ?: return null
        val root = runCatching { json.parseToJsonElement(body) }.getOrNull() as? JsonObject ?: return null
        val hit = (root["results"] as? JsonArray)?.firstOrNull() as? JsonObject ?: return null
        return (hit["lyricsUrl"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.startsWith("https://") }
    }

    private fun payloadToMlx(
        raw: String,
        title: String,
        artist: String,
        unwrapped: Boolean,
    ): String? {
        val text = raw.trim().trimStart(BYTE_ORDER_MARK).trim()
        if (text.isEmpty()) return null

        if (text.startsWith("<")) {
            val isTtml =
                text.startsWith("<tt", ignoreCase = true) ||
                    text.contains("http://www.w3.org/ns/ttml", ignoreCase = true)
            if (!isTtml) return null
            return parseTtmlToMlx(text, title, artist, SOURCE).takeIf { it.isNotBlank() }
        }

        if (text.startsWith("{") && !unwrapped) {
            val envelope = runCatching { json.parseToJsonElement(text) }.getOrNull() as? JsonObject ?: return null
            val inner =
                ENVELOPE_KEYS.firstNotNullOfOrNull { key ->
                    (envelope[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
                } ?: return null
            return payloadToMlx(inner, title, artist, unwrapped = true)
        }

        val plain = decodeLyricsEntities(text)
        return if (LRC_TIMESTAMP.containsMatchIn(plain)) {
            parseLrcToMlx(plain, title, artist, source = SOURCE)
        } else {
            parsePlainToMlx(plain, title, artist, source = SOURCE)
        }
    }

    private companion object {
        const val SOURCE = "BiniLyrics"
        const val BASE_URL = "https://lyrics-api.binimum.org/"
        const val MAX_DOCUMENT_BYTES = 2 * 1024 * 1024
        val BYTE_ORDER_MARK = 0xFEFF.toChar()
        val ENVELOPE_KEYS = listOf("ttml", "lyrics")
        val LRC_TIMESTAMP = Regex("""\[\d{1,3}:\d{2}(?:[.:]\d{1,3})?]""")
    }
}
