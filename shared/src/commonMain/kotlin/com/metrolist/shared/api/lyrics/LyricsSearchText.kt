/*
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */
/*
 * Adapted from BitChord (GPL-3.0) https://github.com/kushagrasinghx/BitChord
 * (shared/src/jvmSharedMain/kotlin/com/music/bitchord/data/lyrics/LyricsHttp.kt) —
 * original re-implementation for ArchiveTune.
 */
/*
 * Adapted from BitChord (GPL-3.0) https://github.com/kushagrasinghx/BitChord
 * (shared/src/jvmSharedMain/kotlin/com/music/bitchord/data/lyrics/
 *  LyricsHttp.kt, ProviderLyrics.kt, EnhancedLrc.kt, LyricsQuery.kt) —
 * original re-implementation for ArchiveTune.
 */

// Ported and adapted from Mochy Music by realidkroo. Licensed under GPL-3.0.
// Ported to Mochy from Natune (NatuneGroup).

package com.metrolist.shared.api.lyrics

/** Normalises a track title for a provider search: strips feature and video credits, then collapses whitespace. */
internal fun String.forLyricsSearch(): String {
    var name = this
    LYRICS_SEARCH_CREDITS.forEach { pattern -> name = pattern.replace(name, " ") }
    return name
        .replace(LYRICS_SEARCH_WHITESPACE, " ")
        .trim()
        .trimEnd(',', '-', '–', '—')
        .trim()
        .ifBlank { trim() }
}

/** YouTube Music appends " - Topic" to auto-generated artist channels; providers search the bare name. */
internal fun String.artistForLyricsSearch(): String = removeSuffix(" - Topic").trim().ifBlank { trim() }

/** Decodes the HTML and XML entities that lyric pages and envelopes carry in their text. */
internal fun decodeLyricsEntities(text: String): String {
    if ('&' !in text) return text
    return text
        .replace(LYRICS_HEX_ENTITY) { match -> decodeScalarEntity(match.groupValues[1], 16) ?: match.value }
        .replace(LYRICS_DEC_ENTITY) { match -> decodeScalarEntity(match.groupValues[1], 10) ?: match.value }
        .replace("&apos;", "'")
        .replace("&quot;", "\"")
        .replace("&nbsp;", " ")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&amp;", "&")
}

private fun decodeScalarEntity(
    digits: String,
    radix: Int,
): String? {
    val codePoint = digits.toIntOrNull(radix) ?: return null
    if (codePoint !in 0..0x10FFFF || codePoint in 0xD800..0xDFFF) return null
    return codePointToString(codePoint)
}

private fun codePointToString(codePoint: Int): String {
    if (codePoint < 0x10000) return codePoint.toChar().toString()
    val offset = codePoint - 0x10000
    return "" + (0xD800 + (offset shr 10)).toChar() + (0xDC00 + (offset and 0x3FF)).toChar()
}

private val LYRICS_SEARCH_WHITESPACE = Regex("""\s+""")

private val LYRICS_SEARCH_CREDITS =
    listOf(
        Regex("""\s*[(\[]\s*(feat|ft|featuring|with)\b[^)\]]*[)\]]""", RegexOption.IGNORE_CASE),
        Regex("""\s+(feat|ft|featuring)\.?\s+.*$""", RegexOption.IGNORE_CASE),
        Regex(
            """\s*[(\[]\s*(official\s*)?(music\s*)?""" +
                """(video|audio|visuali[sz]er|lyrics?\s*video|lyrics?|m/?v|hd|hq|4k|full\s*song)""" +
                """\s*[)\]]""",
            RegexOption.IGNORE_CASE,
        ),
        Regex("""\s*[(\[]\s*official\s*[)\]]""", RegexOption.IGNORE_CASE),
    )

private val LYRICS_HEX_ENTITY = Regex("""&#x([0-9a-fA-F]+);""")
private val LYRICS_DEC_ENTITY = Regex("""&#([0-9]+);""")
