// Ported and adapted from Mochy Music by realidkroo. Licensed under GPL-3.0.

package com.metrolist.shared.ui.player.mochy.ipod

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.metrolist.shared.playback.LocalPlayerSession

@Composable
internal fun IpodHeader(
    title: String,
    scale: IpodScale,
    modifier: Modifier = Modifier,
) {
    val playing =
        LocalPlayerSession.current
            ?.isPlaying
            ?.collectAsState()
            ?.value ?: false
    val battery: State<Float> = rememberIpodBatteryLevel()
    val colors = LocalIpodLook.current.screen
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(scale.header)
                .drawBehind {
                    drawRect(colors.header)
                    drawRect(colors.headerLine, topLeft = Offset(0f, size.height - 1.dp.toPx()), size = Size(size.width, 1.dp.toPx()))
                },
    ) {
        Text(
            text = title,
            color = colors.ink,
            fontSize = scale.sp(18f),
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier =
                Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = scale.dp(9f), end = scale.dp(84f)),
        )
        Canvas(
            modifier =
                Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = scale.dp(10f))
                    .size(scale.dp(60f), scale.dp(16f)),
        ) {
            val unit = size.height / 16f
            drawPlayState(playing, unit)
            drawBattery(battery.value, unit)
        }
    }
}

private fun DrawScope.drawPlayState(
    playing: Boolean,
    unit: Float,
) {
    val h = 14f * unit
    val top = (size.height - h) / 2f
    if (playing) {
        val path =
            Path().apply {
                moveTo(0f, top)
                lineTo(13f * unit, top + h / 2f)
                lineTo(0f, top + h)
                close()
            }
        drawPath(path, PlayBrush)
    } else {
        drawRect(PlayBrush, topLeft = Offset(0f, top), size = Size(4.5f * unit, h))
        drawRect(PlayBrush, topLeft = Offset(7.5f * unit, top), size = Size(4.5f * unit, h))
    }
}

private fun DrawScope.drawBattery(
    level: Float,
    unit: Float,
) {
    val w = 36f * unit
    val h = 15f * unit
    val left = size.width - w
    val top = (size.height - h) / 2f
    val nub = 2.5f * unit
    val bodyW = w - nub
    drawRoundRect(BatteryShell, topLeft = Offset(left, top), size = Size(bodyW, h), cornerRadius = CornerRadius(2f * unit))
    drawRect(BatteryShell, topLeft = Offset(left + bodyW, top + h * 0.3f), size = Size(nub, h * 0.4f))
    val inset = 1.5f * unit
    val innerW = bodyW - inset * 2f
    drawRect(Color.White, topLeft = Offset(left + inset, top + inset), size = Size(innerW, h - inset * 2f))
    drawRect(
        brush = if (level < 0.2f) BatteryLow else BatteryFull,
        topLeft = Offset(left + inset, top + inset),
        size = Size(innerW * level.coerceIn(0.06f, 1f), h - inset * 2f),
    )
}

private val PlayBrush = Brush.verticalGradient(listOf(Color(0xFF9AD6FF), Color(0xFF2A8BDD)))
private val BatteryShell = Color(0xFF6E6E6E)
private val BatteryFull = Brush.verticalGradient(listOf(Color(0xFFD9F5B6), Color(0xFF9BD563), Color(0xFF6DAE34)))
private val BatteryLow = Brush.verticalGradient(listOf(Color(0xFFFFC2B8), Color(0xFFF0624D), Color(0xFFC9311F)))
