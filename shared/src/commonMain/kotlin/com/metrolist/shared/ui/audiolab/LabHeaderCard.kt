// Ported and adapted from Mochy Music by realidkroo. Licensed under GPL-3.0.

package com.metrolist.shared.ui.audiolab

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.metrolist.shared.ui.theme.mochyInterFontFamily

/** where a card stands in the header picture: low in its corner, its bottom well under the sheet */
internal val LabCardStart: Dp = 22.dp
internal val LabCardBottom: Dp = 18.dp

internal val LabCardWidth: Dp = 128.dp
internal val LabCardHeight: Dp = 186.dp
internal val LabCardBars = Color(0xFF5A5A60)
internal val LabCardInk = Color(0xFF7E7E84)

private val CardShape = RoundedCornerShape(26.dp)
private val CardColor = Color(0xFF1C1C1F)
private val CardEdge = Color(0xFF36363C)
private val PanelColor = Color(0xFF141416)

/** the card itself, before what's on it */
@Composable
internal fun LabHeaderCardFrame(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier =
            modifier
                .size(LabCardWidth, LabCardHeight)
                .clip(CardShape)
                .background(CardColor)
                .border(1.5.dp, CardEdge, CardShape),
        content = content,
    )
}

/**
 * What's on a card: topStart and topEnd in small type, label under them, and the panel that fills the rest.
 * [bars] draws into that panel; the Audio Lab passes nothing because Mochy's bars are a live audio visualizer.
 */
@Composable
internal fun LabCardFace(
    modifier: Modifier = Modifier,
    topStart: String? = null,
    topEnd: String? = null,
    label: String? = null,
    bars: @Composable (Modifier) -> Unit,
) {
    val font = mochyInterFontFamily()
    Column(modifier.fillMaxSize().padding(12.dp)) {
        if (topStart != null || topEnd != null) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                topStart?.let { Text(it, fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = LabCardInk, maxLines = 1) }
                Spacer(Modifier.weight(1f))
                topEnd?.let { Text(it, fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = LabCardInk, maxLines = 1) }
            }
        }
        if (label != null) {
            Text(
                label.uppercase(),
                fontFamily = font,
                fontWeight = FontWeight.Medium,
                // a long name gets smaller rather than cut
                fontSize = (20f * (7f / label.length).coerceAtMost(1f)).coerceAtLeast(13f).sp,
                letterSpacing = (-0.01).em,
                color = Color(0xFFA2A2A8),
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            Spacer(Modifier.height(8.dp))
        }
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(18.dp))
                .background(PanelColor),
        ) {
            bars(Modifier.fillMaxSize().padding(start = 10.dp, end = 10.dp, top = 14.dp))
        }
    }
}
