// Ported and adapted from Mochy Music by realidkroo (github.com/realidkroo/mochy-music). Licensed under GPL-3.0.

// Mochy's player style picker: full screen black, the styles as cards in a row, the chosen card lifted and the others pulled back.
// The previews are drawn here and animate like the player they stand for; they are not screenshots of the real players.

package com.metrolist.shared.ui.player.mochy

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import com.metrolist.shared.generated.resources.Res
import com.metrolist.shared.generated.resources.mochy_menu_player_style
import com.metrolist.shared.generated.resources.mochy_menu_style_classic
import com.metrolist.shared.generated.resources.mochy_menu_style_flagship
import com.metrolist.shared.generated.resources.mochy_menu_style_ipod
import com.metrolist.shared.generated.resources.mochy_menu_style_modern
import com.metrolist.shared.generated.resources.mochy_menu_style_turntable
import com.metrolist.shared.generated.resources.mochy_menu_style_vintage
import com.metrolist.shared.settings.PlayerType
import com.metrolist.shared.ui.icons.MetrolistIcons
import com.metrolist.shared.ui.theme.mochyInterFontFamily
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// the styles KMP has, in the order the reference's picker shows its own
internal val MochyStyleOrder: List<Pair<PlayerType, StringResource>> =
    listOf(
        PlayerType.MOCHY to Res.string.mochy_menu_style_flagship,
        PlayerType.TURNTABLE to Res.string.mochy_menu_style_turntable,
        PlayerType.IPOD to Res.string.mochy_menu_style_ipod,
        PlayerType.MODERN to Res.string.mochy_menu_style_modern,
        PlayerType.VINTAGE to Res.string.mochy_menu_style_vintage,
        PlayerType.CLASSIC to Res.string.mochy_menu_style_classic,
    )

private val CardWidth = 170.dp
private val CardCorner = 28.dp
private val PreviewCorner = 20.dp
private val CardFill = Color(0xFF1C1C1E)

@Composable
fun MochyStyleSelector(
    selected: PlayerType,
    onSelect: (PlayerType) -> Unit,
    onDismiss: () -> Unit,
) {
    val font = mochyInterFontFamily()
    Column(
        modifier = Modifier.fillMaxSize().background(Color.Black).padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.mochy_menu_player_style),
                fontFamily = font,
                fontWeight = FontWeight.Bold,
                fontSize = 34.sp,
                lineHeight = 40.sp,
                color = Color.White,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier.size(36.dp).clip(CircleShape).background(CardFill).clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) {
                Icon(MetrolistIcons.Default.Close, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.height(24.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(MochyStyleOrder, key = { it.first.name }) { (style, title) ->
                StyleCard(
                    style = style,
                    title = stringResource(title),
                    selected = style == selected,
                    onClick = {
                        onSelect(style)
                        onDismiss()
                    },
                )
            }
        }
    }
}

// the card for one style: the chosen one stands full size, the others sit back a little and fade
@Composable
private fun StyleCard(
    style: PlayerType,
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val lift by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 420f),
        label = "mochyStyleLift",
    )
    Column(
        modifier =
            Modifier
                .width(CardWidth)
                .graphicsLayer {
                    val scale = 0.92f + 0.08f * lift
                    scaleX = scale
                    scaleY = scale
                    alpha = 0.6f + 0.4f * lift
                }.clip(RoundedCornerShape(CardCorner))
                .background(CardFill)
                .clickable(onClick = onClick)
                .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        StylePreview(style, Modifier.fillMaxWidth().aspectRatio(0.56f).clip(RoundedCornerShape(PreviewCorner)))
        Text(
            text = title,
            fontFamily = mochyInterFontFamily(),
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

// a looping drawing of each player: the disc turns, the wheel pulses, the bars move, so a card shows its style at rest
@Composable
private fun StylePreview(
    style: PlayerType,
    modifier: Modifier,
) {
    val loop = rememberInfiniteTransition(label = "mochyStylePreview")
    val phase by loop.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 6000, easing = LinearEasing), RepeatMode.Restart),
        label = "mochyStylePhase",
    )
    Canvas(modifier.background(Color(0xFF0E0E10))) {
        val w = size.width
        val h = size.height
        when (style) {
            PlayerType.TURNTABLE -> {
                // the record turns under its label, the arm rests on the edge
                val r = w * 0.36f
                val c = Offset(w / 2f, h * 0.42f)
                drawCircle(Color(0xFF151515), r, c)
                drawCircle(Color(0xFF2A2A2A), r * 0.7f, c)
                rotateGroove(c, r, phase)
                drawCircle(Color(0xFFE8A33D), r * 0.28f, c)
                drawLine(Color.White.copy(alpha = 0.7f), Offset(w * 0.82f, h * 0.08f), Offset(w * 0.6f, h * 0.36f), strokeWidth = 3f)
            }
            PlayerType.IPOD -> {
                // the screen on top, the click wheel below pulsing with the phase
                drawRoundRect(Color(0xFF2C2C30), Offset(w * 0.1f, h * 0.06f), Size(w * 0.8f, h * 0.42f), CornerRadius(8f))
                drawRoundRect(Color(0xFF9CC6A4), Offset(w * 0.16f, h * 0.12f), Size(w * 0.68f, h * 0.30f), CornerRadius(4f))
                val wheelCenter = Offset(w / 2f, h * 0.76f)
                drawCircle(Color(0xFFF2F2F2), w * 0.36f, wheelCenter)
                drawCircle(Color(0xFF222222), w * 0.14f, wheelCenter)
                drawCircle(Color(0xFF4FA3FF).copy(alpha = 0.25f + 0.2f * pulse(phase)), w * 0.33f, wheelCenter, style = Stroke(width = 4f))
            }
            PlayerType.VINTAGE -> {
                // warm tones with scan lines rolling down
                drawRect(Brush.verticalGradient(listOf(Color(0xFF6B3E1E), Color(0xFF2B1A10))))
                val gap = h / 14f
                val shift = (phase * gap)
                var y = -gap + shift
                while (y < h) {
                    drawRect(Color.Black.copy(alpha = 0.18f), Offset(0f, y), Size(w, gap * 0.4f))
                    y += gap
                }
                drawCircle(Color(0xFFE9C58C), w * 0.26f, Offset(w / 2f, h * 0.42f))
            }
            PlayerType.CLASSIC -> {
                // a square cover, title lines and a progress bar that runs across
                drawRect(Color(0xFF3A3F55), Offset(w * 0.12f, h * 0.12f), Size(w * 0.76f, w * 0.76f))
                drawRect(Color.White.copy(alpha = 0.8f), Offset(w * 0.12f, h * 0.62f), Size(w * 0.6f, 5f))
                drawRect(Color.White.copy(alpha = 0.35f), Offset(w * 0.12f, h * 0.67f), Size(w * 0.4f, 4f))
                drawRect(Color.White.copy(alpha = 0.25f), Offset(w * 0.12f, h * 0.8f), Size(w * 0.76f, 4f))
                drawRect(Color.White, Offset(w * 0.12f, h * 0.8f), Size(w * 0.76f * phase, 4f))
            }
            PlayerType.MODERN -> {
                // a cover that breathes, and a bar that fills and rewinds
                val breath = 0.94f + 0.06f * pulse(phase)
                val s = w * 0.7f * breath
                drawRect(Color(0xFF2E5E4E), Offset((w - s) / 2f, h * 0.14f), Size(s, s))
                drawRect(Color.White.copy(alpha = 0.25f), Offset(w * 0.15f, h * 0.8f), Size(w * 0.7f, 4f))
                drawRect(Color.White, Offset(w * 0.15f, h * 0.8f), Size(w * 0.7f * phase, 4f))
            }
            else -> {
                // Flagship and anything else: a frosted cover fade over the bottom bar
                drawRect(Brush.verticalGradient(listOf(Color(0xFF3C2A55), Color(0xFF0E0E10))))
                drawCircle(Color(0xFF6E58A0), w * 0.3f, Offset(w / 2f, h * 0.36f))
                drawRoundRect(Color.White.copy(alpha = 0.22f), Offset(w * 0.2f, h * 0.82f), Size(w * 0.6f, 12f), CornerRadius(12f))
                drawCircle(Color.White, 6f, Offset(w * 0.5f, h * 0.88f))
            }
        }
    }
}

private fun pulse(phase: Float): Float = 0.5f + 0.5f * sin(phase * 2f * PI.toFloat())

private fun DrawScope.rotateGroove(
    center: Offset,
    radius: Float,
    phase: Float,
) {
    val angle = phase * 360f
    for (k in 0 until 4) {
        val a = (angle + k * 90f) * PI.toFloat() / 180f
        val p = Offset(center.x + cos(a) * radius * 0.5f, center.y + sin(a) * radius * 0.5f)
        drawCircle(Color.White.copy(alpha = 0.08f), radius * 0.12f, p)
    }
}
