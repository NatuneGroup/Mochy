// Ported from Mochy Music by realidkroo (github.com/realidkroo/mochy-music). Licensed under GPL-3.0.

package com.metrolist.shared.ui.player.mochy

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrolist.shared.ui.theme.mochyInterFontFamily

/** The format tag beside the flagship's title, "OPUS" or "AAC": an outlined tag in the design's proportions. */
@Composable
internal fun FlagshipFormatBadge(
    text: String,
    modifier: Modifier = Modifier,
    height: Dp = 20.dp,
    color: Color = Color.White.copy(alpha = 0.55f),
) {
    // the text is sized off the box, not the font scale, so the two never drift apart
    val fontSize = with(LocalDensity.current) { (height * 0.5f).toSp() }
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .height(height)
                .border(height * 0.0625f, color, RoundedCornerShape(height * 0.31f))
                .padding(horizontal = height * 0.29f),
    ) {
        Text(
            text = text,
            maxLines = 1,
            softWrap = false,
            style =
                TextStyle(
                    color = color,
                    fontFamily = mochyInterFontFamily(),
                    fontWeight = FontWeight.Medium,
                    fontSize = fontSize,
                    letterSpacing = 0.sp,
                ),
        )
    }
}

/** The badge's name for a stream's format, as Mochy names them; null until the codec is known. */
internal fun flagshipCodecLabel(
    mimeType: String?,
    codecs: String?,
): String? {
    val mime = mimeType?.lowercase().orEmpty()
    val codec = codecs?.lowercase().orEmpty()
    return when {
        mime == "audio/opus" || codec.startsWith("opus") -> "OPUS"

        mime == "audio/flac" || codec == "flac" -> "FLAC"

        mime == "audio/alac" || codec == "alac" -> "ALAC"

        codec == "ec+3" -> "DOLBY ATMOS"

        mime == "audio/eac3" || codec == "ec-3" -> "E-AC-3"

        mime == "audio/ac3" || codec == "ac-3" -> "AC-3"

        codec.startsWith("ac-4") -> "AC-4"

        // mp4a.40.5 and .29 are HE-AAC, the rest plain AAC
        codec.startsWith("mp4a.40.5") || codec.startsWith("mp4a.40.29") -> "HE-AAC"

        mime == "audio/mp4a-latm" || codec.startsWith("mp4a") -> "AAC"

        mime == "audio/vorbis" || codec == "vorbis" -> "VORBIS"

        mime == "audio/mpeg" || codec == "mp3" -> "MP3"

        // YouTube's container types, when its record has no codec
        mime.startsWith("audio/webm") -> "OPUS"

        mime.startsWith("audio/mp4") -> "AAC"

        else -> null
    }
}
