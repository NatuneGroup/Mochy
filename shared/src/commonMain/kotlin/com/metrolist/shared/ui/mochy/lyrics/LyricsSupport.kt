// Ported from Mochy Music by realidkroo (github.com/realidkroo/mochy-music). Licensed under GPL-3.0.

// The small pieces Mochy's lyrics lean on from elsewhere in the reference app: the motion styles, the
// element blur switch, the change-only animations, the line timing rules and the lyrics header's metrics.

package com.metrolist.shared.ui.mochy.lyrics

import androidx.compose.animation.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.metrolist.shared.model.LyricsEntry
import kotlinx.coroutines.launch
import androidx.compose.animation.core.Animatable as FloatAnimatable

// risesWords: words rise and lines cascade (style 1's motion), shaded: the agsl glow and wave run on top
enum class LyricsMotionStyle(
    val risesWords: Boolean,
    val shaded: Boolean,
) {
    /** Style 1: words rise into place as they're sung, held notes swell and glow, lines cascade. */
    MOTION(risesWords = true, shaded = true),

    /** Style 2: the original - a wave across the sung letters, the list scrolls as one piece. */
    LEGACY(risesWords = false, shaded = true),

    // style 1 without the shader, for phones that struggle with it
    BORING(risesWords = true, shaded = false),

    // style 2 without the shader, just the colour sweeping along
    PLAIN(risesWords = false, shaded = false),
}

/** The app-wide motion switches the lyrics read while drawing. */
object LyricsMotion {
    // the lines' blur by their distance from the sung one; an element blur, so the glass blur switch leaves it alone
    var elementBlur by mutableStateOf(true)
}

/**
 * One reading positioned over the characters it belongs to. [start] inclusive, [end] exclusive, both indices
 * into the line's own text.
 */
data class RubyToken(
    val start: Int,
    val end: Int,
    val text: String,
)

/** eases to [target] the way animateFloatAsState does, but starts nothing until the target first changes, so a list row mounts without a coroutine per animation */
@Composable
internal fun animateFloatOnChange(
    target: Float,
    spec: AnimationSpec<Float>,
): State<Float> {
    val animatable = remember { FloatAnimatable(target) }
    val scope = rememberCoroutineScope()
    val lastTarget = remember { mutableFloatStateOf(target) }
    SideEffect {
        if (lastTarget.floatValue != target) {
            lastTarget.floatValue = target
            scope.launch { animatable.animateTo(target, spec) }
        }
    }
    return animatable.asState()
}

/** the same for a colour, eased the way animateColorAsState eases it */
@Composable
internal fun animateColorOnChange(
    target: Color,
    spec: AnimationSpec<Color>,
): State<Color> {
    val animatable = remember { Animatable(target) }
    val scope = rememberCoroutineScope()
    val lastTarget = remember { mutableStateOf(target) }
    SideEffect {
        if (lastTarget.value != target) {
            lastTarget.value = target
            scope.launch { animatable.animateTo(target, spec) }
        }
    }
    return animatable.asState()
}

// the lyrics header: the cover sits 36dp in and 28dp under the status bar, 60dp square
internal val LyricsHeaderArtSize = 60.dp
internal val LyricsHeaderArtX = 36.dp
internal val LyricsHeaderArtTopFromStatusBar = 28.dp

/** Mochy's line timing rules, the ones its lyrics page, card and gaps all agree on. */
internal object LyricsTiming {
    /** A line lights this long before its timestamp, so it is lit by the time its first word is sung. */
    const val LINE_LEAD_IN_MS = 300L

    /** Fallback duration for a line with no word timings and no successor. */
    private const val UNTIMED_LINE_DURATION_MS = 5_000L

    /**
     * Index of the line that should read as ACTIVE at [position], or -1 before the first line. Returns
     * [lines].size once playback has run past the final line's own end, so the last line doesn't stay lit
     * through the outro.
     */
    fun findCurrentLineIndex(
        lines: List<LyricsEntry>,
        position: Long,
    ): Int {
        if (lines.isEmpty()) return -1
        if (position >= lyricsEndMs(lines)) return lines.size
        for (index in lines.indices) {
            if (lines[index].time >= position + LINE_LEAD_IN_MS) {
                return index - 1
            }
        }
        return lines.lastIndex
    }

    /** When a line stops being sung: the end of its last word, else the next line's start, else a guess. */
    fun lineEndMs(
        lines: List<LyricsEntry>,
        index: Int,
    ): Long {
        val entry = lines.getOrNull(index) ?: return 0L
        entry.words?.lastOrNull()?.let { return (it.endTime * 1000).toLong() }
        lines.getOrNull(index + 1)?.let { return it.time }
        return entry.time + UNTIMED_LINE_DURATION_MS
    }

    fun lyricsEndMs(lines: List<LyricsEntry>): Long {
        if (lines.isEmpty()) return 0L
        return lineEndMs(lines, lines.lastIndex)
    }

    /** Several lines, no word timings, every line on the same timestamp: lyrics with no usable timing. */
    fun isUnsynced(lines: List<LyricsEntry>): Boolean {
        if (lines.size < 2) return false
        if (lines.any { !it.words.isNullOrEmpty() }) return false
        val first = lines.first().time
        return lines.all { it.time == first }
    }
}

/**
 * The lead and backing parts of a line. The app's lyrics keep backing vocals as their own flagged words and
 * text; Mochy finds them in brackets inside the line. Flagged ones are split here, the rest go through the
 * reference's bracket split.
 */
internal fun splitLyricLine(entry: LyricsEntry): LyricLineParts {
    val words = entry.words
    val backingWords = words?.filter { it.isBackground }.orEmpty()
    val leadWords = words?.filterNot { it.isBackground }
    val backingText =
        entry.backgroundText?.takeIf { it.isNotBlank() }
            ?: backingWords
                .joinToString("") { if (it.hasTrailingSpace) it.text + " " else it.text }
                .trim()
                .takeIf { it.isNotEmpty() }
    if (backingText == null || entry.isBackground || entry.text.isBlank()) {
        return splitBackingVocal(entry.text, leadWords?.ifEmpty { null } ?: words)
    }
    return LyricLineParts(
        lead = entry.text,
        backing = backingText,
        leadWords = leadWords?.ifEmpty { null },
        backingWords = backingWords.ifEmpty { null },
    )
}
