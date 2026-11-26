// Ported from Mochy Music by realidkroo (github.com/realidkroo/mochy-music). Licensed under GPL-3.0.

// The script and grapheme questions the lyrics ask of their text. Mochy asks the JVM's Character and
// BreakIterator; shared code has neither, so these answer the same questions from the Unicode block ranges.

package com.metrolist.shared.ui.mochy.lyrics

/** The code point starting at [index], joining a surrogate pair. */
internal fun String.codePointAtIndex(index: Int): Int {
    val high = this[index]
    if (high.isHighSurrogate() && index + 1 < length) {
        val low = this[index + 1]
        if (low.isLowSurrogate()) return ((high.code - 0xD800) shl 10) + (low.code - 0xDC00) + 0x10000
    }
    return high.code
}

/** How many chars [codePoint] takes. */
internal fun charCountOf(codePoint: Int): Int = if (codePoint >= 0x10000) 2 else 1

internal fun isWhitespaceCodePoint(codePoint: Int): Boolean = codePoint < 0x10000 && codePoint.toChar().isWhitespace()

/** A right-to-left letter: Hebrew, Arabic, Syriac, Thaana, N'Ko and their presentation forms. */
internal fun Char.isRightToLeft(): Boolean {
    val c = code
    return c in 0x0590..0x08FF || c in 0xFB1D..0xFDFF || c in 0xFE70..0xFEFF
}

/** A left-to-right letter, the first strong character the reference reads a line's direction from. */
internal fun Char.isLeftToRightLetter(): Boolean = isLetter() && !isRightToLeft()

/** Chinese characters and Japanese kana: each one is its own sung word. */
internal fun isHanOrKana(codePoint: Int): Boolean =
    codePoint in 0x3040..0x30FF ||
        codePoint in 0x31F0..0x31FF ||
        codePoint in 0x3400..0x4DBF ||
        codePoint in 0x4E00..0x9FFF ||
        codePoint in 0xF900..0xFAFF ||
        codePoint in 0xFF66..0xFF9F ||
        codePoint in 0x20000..0x3FFFF

internal fun isHangul(codePoint: Int): Boolean =
    codePoint in 0x1100..0x11FF ||
        codePoint in 0x3130..0x318F ||
        codePoint in 0xA960..0xA97F ||
        codePoint in 0xAC00..0xD7AF ||
        codePoint in 0xD7B0..0xD7FF

/**
 * Whether [codePoint] belongs to a script whose letters are separate shapes (Latin, Greek, Cyrillic,
 * Armenian, Georgian, Hangul, and the shared digits, punctuation and marks). Joined and shaped scripts
 * (Arabic, Hebrew, the Indic scripts, Thai...) and Han and kana answer false.
 */
internal fun isSeparateLetterScript(codePoint: Int): Boolean {
    if (isHangul(codePoint)) return true
    if (isHanOrKana(codePoint)) return false
    return when (codePoint) {
        in 0x0590..0x08FF -> false // Hebrew, Arabic, Syriac, Thaana, N'Ko, Samaritan, Mandaic
        in 0x0900..0x0DFF -> false // Devanagari to Sinhala
        in 0x0E00..0x0FFF -> false // Thai, Lao, Tibetan
        in 0x1000..0x109F -> false // Myanmar
        in 0x1200..0x139F -> false // Ethiopic
        in 0x1780..0x18AF -> false // Khmer, Mongolian
        in 0x1A00..0x1AAF -> false // Buginese, Tai Tham
        in 0x1B00..0x1BFF -> false // Balinese, Sundanese, Batak
        in 0xA800..0xA8FF -> false // Syloti Nagri, Phags-pa, Saurashtra
        in 0xA980..0xAADF -> false // Javanese, Myanmar extended, Tai Viet
        in 0xFB1D..0xFDFF -> false // Hebrew and Arabic presentation forms
        in 0xFE70..0xFEFF -> false
        in 0x10800..0x10FFF -> false // historic right-to-left scripts
        in 0x11000..0x11FFF -> false // historic Indic scripts
        in 0x1E800..0x1EFFF -> false
        else -> true
    }
}

private fun isGraphemeExtender(codePoint: Int): Boolean =
    codePoint in 0x0300..0x036F ||
        codePoint in 0x1AB0..0x1AFF ||
        codePoint in 0x1DC0..0x1DFF ||
        codePoint in 0x20D0..0x20FF ||
        codePoint in 0xFE00..0xFE0F ||
        codePoint in 0xFE20..0xFE2F ||
        codePoint in 0x1F3FB..0x1F3FF ||
        codePoint in 0xE0020..0xE007F ||
        codePoint == 0x200C

private fun isRegionalIndicator(codePoint: Int): Boolean = codePoint in 0x1F1E6..0x1F1FF

/**
 * Where the user-perceived character starting at [start] ends: one code point plus the marks, variation
 * selectors, skin tones and joined emoji that draw with it, and both halves of a flag.
 */
internal fun String.nextGraphemeEnd(start: Int): Int {
    if (start >= length) return length
    val first = codePointAtIndex(start)
    var i = start + charCountOf(first)
    if (isRegionalIndicator(first) && i < length && isRegionalIndicator(codePointAtIndex(i))) {
        return i + charCountOf(codePointAtIndex(i))
    }
    while (i < length) {
        val cp = codePointAtIndex(i)
        when {
            isGraphemeExtender(cp) -> {
                i += charCountOf(cp)
            }

            cp == 0x200D && i + 1 < length -> {
                // a zero-width joiner holds the next emoji to this one
                i += 1
                i += charCountOf(codePointAtIndex(i))
            }

            else -> {
                return i
            }
        }
    }
    return i
}
