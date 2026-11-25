/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 * Portions © vossgraves — github.com/vossgraves
 */

// Ported to Mochy from Natune (NatuneGroup).

package com.metrolist.shared.api.lyrics

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.prepareGet
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * YouLyPlus: each path (TTML first, then line and syllable lyrics) walks the mirror list in order.
 * A mirror that fails or returns an undecodable body is skipped; cancellation still propagates.
 */
class YouLyPlusLyricsProvider(
    private val httpClient: HttpClient,
) {
    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        }

    suspend fun getLyrics(
        title: String,
        artist: String,
        album: String?,
        durationSec: Int,
    ): String? {
        val cleanTitle = title.trim()
        val cleanArtist = artist.trim()
        if (cleanTitle.isBlank() || cleanArtist.isBlank()) return null
        val cleanAlbum = album?.trim().orEmpty()
        return fetchTtml(cleanTitle, cleanArtist, cleanAlbum, durationSec)
            ?: fetchLyrics(cleanTitle, cleanArtist, cleanAlbum, durationSec)
    }

    private suspend fun fetchTtml(
        title: String,
        artist: String,
        album: String,
        durationSec: Int,
    ): String? =
        fetchFromMirrors(TTML_PATH, title, artist, album, durationSec) { body ->
            val trimmed = body.trim()
            // Only an XML document counts; a JSON envelope carrying plain text is not TTML.
            val ttml =
                if (trimmed.startsWith("<")) {
                    trimmed
                } else {
                    json.decodeFromString<YouLyPlusTtmlResponse>(body).ttml?.trim()
                }
            ttml
                ?.takeIf { it.startsWith("<") }
                ?.let { parseTtmlToMlx(it, title, artist, SOURCE) }
                ?.takeIf { it.isNotBlank() }
        }

    private suspend fun fetchLyrics(
        title: String,
        artist: String,
        album: String,
        durationSec: Int,
    ): String? =
        fetchFromMirrors(LYRICS_PATH, title, artist, album, durationSec) { body ->
            json.decodeFromString<YouLyPlusLyricsResponse>(body).toMlx(title, artist)
        }

    private suspend fun fetchFromMirrors(
        path: String,
        title: String,
        artist: String,
        album: String,
        durationSec: Int,
        decode: (String) -> String?,
    ): String? {
        for (baseUrl in MIRROR_BASE_URLS) {
            val lyrics =
                try {
                    httpClient
                        .prepareGet(baseUrl + path) {
                            expectMissingLyrics()
                            header(HttpHeaders.Accept, "application/json")
                            parameter("title", title)
                            parameter("artist", artist)
                            if (album.isNotBlank()) parameter("album", album)
                            if (durationSec > 0) parameter("duration", durationSec)
                        }.readLyricsText()
                        ?.let(decode)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    null
                }
            if (!lyrics.isNullOrBlank()) return lyrics
        }
        return null
    }

    /**
     * Timed lines become MLX: word-typed lines with syllable timing keep their syllables, other timed
     * lines keep their text. Without any timed line, the untimed text is kept as unsynced lines.
     */
    private fun YouLyPlusLyricsResponse.toMlx(
        title: String,
        artist: String,
    ): String? {
        val timed = lyrics.filter { it.time != null }
        val plain = lyrics.mapNotNull { line -> line.text?.trim()?.takeIf { it.isNotEmpty() } }
        if (timed.isEmpty() && plain.isEmpty()) return null

        val builder = MlxBuilder().meta(SOURCE).song(title = title, artist = artist)
        if (timed.isEmpty()) {
            plain.forEach { builder.unsyncedLine(it) }
            return builder.build()
        }

        timed.forEach { line ->
            val startMs = (line.time ?: 0L).coerceAtLeast(0L)
            val syllables = line.syllabus.orEmpty().filter { !it.text.isNullOrBlank() && it.time != null }
            if (type.equals("Word", ignoreCase = true) && syllables.isNotEmpty()) {
                val words =
                    syllables.mapIndexed { index, syllable ->
                        val wordStart = syllable.time ?: 0L
                        val wordEnd =
                            syllable.duration?.let { wordStart + it }
                                ?: syllables.getOrNull(index + 1)?.time
                                ?: wordStart
                        MlxWord(syllable.text.orEmpty(), wordStart, wordEnd, hasTrailingSpace = false)
                    }
                builder.line(startMs, words.last().endMs, words = words)
            } else {
                val text = line.text.orEmpty()
                if (text.isBlank()) return@forEach
                val endMs = line.duration?.let { startMs + it }
                if (endMs != null) {
                    builder.line(startMs, endMs, text)
                } else {
                    builder.line(startMs, text)
                }
            }
        }
        return builder.build()
    }

    private companion object {
        const val SOURCE = "YouLyPlus"
        const val TTML_PATH = "v1/ttml/get"
        const val LYRICS_PATH = "v2/lyrics/get"

        // Ordered by observed reliability; hosts that failed DNS or certificate checks are left out.
        val MIRROR_BASE_URLS =
            listOf(
                "https://lyricsplus.prjktla.my.id/",
                "https://lyricsplus.prjktla.workers.dev/",
                "https://lyricsplus-seven.vercel.app/",
            )
    }
}

@Serializable
private data class YouLyPlusTtmlResponse(
    val ttml: String? = null,
)

@Serializable
private data class YouLyPlusLyricsResponse(
    val type: String? = null,
    val lyrics: List<YouLyPlusLine> = emptyList(),
)

@Serializable
private data class YouLyPlusLine(
    val time: Long? = null,
    val duration: Long? = null,
    val text: String? = null,
    val syllabus: List<YouLyPlusSyllable>? = null,
)

@Serializable
private data class YouLyPlusSyllable(
    val time: Long? = null,
    val duration: Long? = null,
    val text: String? = null,
)
