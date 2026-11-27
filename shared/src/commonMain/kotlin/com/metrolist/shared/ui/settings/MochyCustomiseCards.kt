// Ported from Mochy Music by realidkroo (github.com/realidkroo/mochy-music). Licensed under GPL-3.0.

// The cards at the top of Mochy's Customisation page: the cover rounding card, its corner drawn at the size it
// rounds covers to with the slider card under it, and the player styles as a row of moving cards.

package com.metrolist.shared.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.metrolist.shared.generated.resources.Res
import com.metrolist.shared.generated.resources.mochy_customise_cover_rounding
import com.metrolist.shared.generated.resources.mochy_customise_customize_it
import com.metrolist.shared.generated.resources.mochy_customise_more_rounded
import com.metrolist.shared.generated.resources.mochy_customise_player_styles
import com.metrolist.shared.generated.resources.mochy_customise_reset
import com.metrolist.shared.generated.resources.mochy_customise_square
import com.metrolist.shared.settings.Settings
import com.metrolist.shared.ui.haptics.HapticEvent
import com.metrolist.shared.ui.haptics.rememberMetrolistHaptics
import com.metrolist.shared.ui.icons.MetrolistIcons
import com.metrolist.shared.ui.player.mochy.MochyStyleColumns
import com.metrolist.shared.ui.player.mochy.MochyStylePicker
import com.metrolist.shared.ui.player.mochy.MochyStylePreviewCard
import com.metrolist.shared.ui.theme.mochyInterFontFamily
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs
import kotlin.math.roundToInt

// Mochy's customisation measures
private const val MAX_COVER_RADIUS = 45f
private const val DEFAULT_COVER_RADIUS = 25f
private val CardColor = Color(0xFF1C1C1E)
private val CardShape = RoundedCornerShape(22.dp)
private val SliderCardShape = RoundedCornerShape(20.dp)
private val RoundingCardHeight = 186.dp
private val CardTextInset = 22.dp
private val SwatchWidth = 146.dp
private val SwatchHeight = 135.dp
private val SwatchColor = Color(0xFFD9D9D9)
private val ArcGap = 19.dp
private val ArcTail = 12.dp

@Composable
internal fun MochyCustomiseCards() {
    val font = mochyInterFontFamily()
    val haptics = rememberMetrolistHaptics()
    val radius = Settings.shared.mochyCoverRadius
    var lastTick by remember { mutableIntStateOf((radius / 5f).roundToInt()) }
    Column(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(RoundingCardHeight)
                .clip(CardShape)
                .background(CardColor),
        ) {
            RoundingSwatch(radius, Modifier.matchParentSize())
            CardTitle(stringResource(Res.string.mochy_customise_cover_rounding), abs(radius - DEFAULT_COVER_RADIUS) >= 0.5f) {
                Settings.shared.mochyCoverRadius = DEFAULT_COVER_RADIUS
            }
            Text(
                text = "${radius.roundToInt()}px",
                style =
                    TextStyle(
                        fontFamily = font,
                        fontWeight = FontWeight.Bold,
                        fontSize = 44.sp,
                        letterSpacing = (-0.03).em,
                        color = Color.White,
                    ),
                modifier = Modifier.align(Alignment.BottomStart).padding(start = CardTextInset, bottom = 24.dp),
            )
        }
        Spacer(Modifier.height(11.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .clip(SliderCardShape)
                .background(CardColor)
                .padding(start = CardTextInset, end = CardTextInset, top = 16.dp, bottom = 12.dp),
        ) {
            Text(
                stringResource(Res.string.mochy_customise_customize_it),
                style =
                    TextStyle(
                        fontFamily = font,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        letterSpacing = (-0.03).em,
                        color = Color.White,
                    ),
            )
            Spacer(Modifier.height(4.dp))
            BareSlider(radius / MAX_COVER_RADIUS) { f ->
                val next = f * MAX_COVER_RADIUS
                val tick = (next / 5f).roundToInt()
                // a tick every 5px, so the drag feels notched
                if (tick != lastTick) {
                    lastTick = tick
                    haptics.perform(HapticEvent.SEGMENT_TICK)
                }
                Settings.shared.mochyCoverRadius = next
            }
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                val small = TextStyle(fontFamily = font, fontSize = 12.sp, color = Color.White.copy(alpha = 0.5f))
                Text(stringResource(Res.string.mochy_customise_square), style = small)
                Text(stringResource(Res.string.mochy_customise_more_rounded), style = small)
            }
        }
        Spacer(Modifier.height(22.dp))
        Text(
            stringResource(Res.string.mochy_customise_player_styles),
            style = TextStyle(fontFamily = font, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White),
            modifier = Modifier.padding(start = 4.dp, bottom = 10.dp),
        )
        // the styles as a swipeable row of moving cards; a tap puts one to use, the outlined one is in use
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MochyStyleColumns.flatten().forEach { (type, label) ->
                val selected = Settings.shared.playerType == type
                Column(
                    Modifier
                        .width(130.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(CardColor)
                        .then(if (selected) Modifier.border(2.dp, Color.White, RoundedCornerShape(22.dp)) else Modifier)
                        .clickable { Settings.shared.playerType = type }
                        .padding(8.dp),
                ) {
                    MochyStylePreviewCard(type, Modifier.fillMaxWidth().height(170.dp).clip(RoundedCornerShape(16.dp)))
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(label),
                        fontFamily = font,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }
            // the full picker, with the styles stacked as the player shows them
            Box(
                Modifier
                    .width(130.dp)
                    .height(206.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(CardColor)
                    .clickable { MochyStylePicker.show() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(MetrolistIcons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Color.White)
            }
        }
        Spacer(Modifier.height(22.dp))
    }
}

// a big card's title in its top left with a small reset after it, faint while everything is at its default
@Composable
private fun CardTitle(
    title: String,
    canReset: Boolean,
    onReset: () -> Unit,
) {
    Row(Modifier.padding(start = CardTextInset, top = CardTextInset)) {
        Text(
            text = title,
            style = TextStyle(fontFamily = mochyInterFontFamily(), fontWeight = FontWeight.Bold, fontSize = 17.sp, lineHeight = 20.sp),
            color = Color.White.copy(alpha = 0.9f),
        )
        Icon(
            imageVector = MetrolistIcons.Filled.Refresh,
            contentDescription = stringResource(Res.string.mochy_customise_reset),
            tint = Color.White.copy(alpha = if (canReset) 0.8f else 0.25f),
            modifier =
                Modifier
                    .offset(x = 2.dp, y = (-9).dp)
                    .size(34.dp)
                    .clip(CircleShape)
                    .clickable(enabled = canReset, onClick = onReset)
                    .padding(8.dp),
        )
    }
}

// the big cover corner tucked into the card's top right, with the white arc hugging its bend
@Composable
private fun RoundingSwatch(
    radius: Float,
    modifier: Modifier,
) {
    Canvas(modifier) {
        val r = radius.coerceIn(0f, MAX_COVER_RADIUS).dp.toPx()
        val left = size.width - SwatchWidth.toPx()
        val bottom = SwatchHeight.toPx()
        drawRoundRect(SwatchColor, Offset(left, -r), Size(size.width - left + r, bottom + r), CornerRadius(r, r))
        val gap = ArcGap.toPx()
        val tail = ArcTail.toPx()
        val arcR = r + gap
        val x = left - gap
        val y = bottom + gap
        val path =
            Path().apply {
                moveTo(x, y - arcR - tail)
                lineTo(x, y - arcR)
                arcTo(Rect(x, y - arcR * 2f, x + arcR * 2f, y), 180f, -90f, false)
                lineTo(x + arcR + tail, y)
            }
        drawPath(path, Color.White, style = Stroke(width = 4.5.dp.toPx(), cap = StrokeCap.Round))
    }
}

// a bare line: the bright part is how far along it is, tap or drag anywhere on it
@Composable
private fun BareSlider(
    fraction: Float,
    onFractionChange: (Float) -> Unit,
) {
    var widthPx by remember { mutableFloatStateOf(1f) }
    val shown = fraction.coerceIn(0f, 1f)
    val currentShown by rememberUpdatedState(shown)
    val currentOnChange by rememberUpdatedState(onFractionChange)
    var dragX by remember { mutableFloatStateOf(0f) }
    val dragState =
        rememberDraggableState { delta ->
            dragX += delta
            currentOnChange((dragX / widthPx).coerceIn(0f, 1f))
        }
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(20.dp)
            .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(Unit) { detectTapGestures { currentOnChange((it.x / widthPx).coerceIn(0f, 1f)) } }
            .draggable(dragState, Orientation.Horizontal, onDragStarted = { dragX = currentShown * widthPx }),
    ) {
        val y = size.height / 2f
        val stroke = 3.dp.toPx()
        val start = stroke / 2f
        val end = size.width - stroke / 2f
        drawLine(Color.White.copy(alpha = 0.2f), Offset(start, y), Offset(end, y), stroke, StrokeCap.Round)
        drawLine(Color.White, Offset(start, y), Offset(start + (end - start) * shown, y), stroke, StrokeCap.Round)
    }
}
