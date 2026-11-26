// Ported and adapted from Mochy Music by realidkroo. Licensed under GPL-3.0.

package com.metrolist.shared.ui.audiolab

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrolist.shared.ui.theme.mochyInterFontFamily
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

object LabColors {
    val Card = Color(0xFF1C1C1E)
    val Stage = Color(0xFF0E0E10)
    val StageLine = Color(0xFF2E2E32)
    val Row = Color(0xFF26262A)
    val RowSelected = Color(0xFF34343A)
    val Chip = Color(0xFF232327)
    val Primary = Color.White
    val Secondary = Color(0xFF9A9AA0)
    val Faint = Color(0xFF5C5C63)
    val Ink = Color(0xFF8A8A92)
}

@Composable
fun LabCard(
    modifier: Modifier = Modifier,
    padding: Dp = 14.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(LabColors.Card)
            .padding(padding),
        content = content,
    )
}

/** outlined dark box for a live picture */
@Composable
fun LabStage(
    modifier: Modifier = Modifier,
    height: Dp = 132.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(18.dp))
            .background(LabColors.Stage)
            .border(1.dp, LabColors.StageLine, RoundedCornerShape(18.dp)),
    ) {
        content()
    }
}

/**
 * The card's switch row. [highlightKey] is kept for the shared signature; KMP has no settings
 * jump-and-flash yet, so it does nothing here.
 */
@Composable
fun LabToggleRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingDot: Boolean = false,
    highlightKey: String? = null,
) {
    val font = mochyInterFontFamily()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(horizontal = 6.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingDot) {
            val dot by animateColorAsState(if (checked) Color.White else Color(0xFFD9D9DE), tween(200), label = "dot")
            Box(Modifier.size(20.dp).clip(CircleShape).background(dot))
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                fontFamily = font,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                color = if (enabled) LabColors.Primary else LabColors.Faint,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    fontFamily = font,
                    fontSize = 12.sp,
                    lineHeight = 15.sp,
                    color = if (enabled) LabColors.Secondary else LabColors.Faint,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        LabSwitch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

private val SwitchTrackWidth = 52.dp
private val SwitchTrackHeight = 32.dp
private val SwitchThumbSize = 24.dp
private val SwitchThumbInset = (SwitchTrackHeight - SwitchThumbSize) / 2
private val SwitchGrey = Color(0xFF8E8E93)

/** the pill switch the Mochy settings use: grey when off, a white thumb sliding across when on */
@Composable
internal fun LabSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val alpha = if (enabled) 1f else 0.38f
    val animSpec = tween<Color>(150)
    val trackColor by animateColorAsState(
        if (checked) SwitchGrey.copy(alpha = alpha) else Color.Transparent,
        animSpec,
        label = "trackColor",
    )
    val borderColor by animateColorAsState(
        if (checked) Color.Transparent else SwitchGrey.copy(alpha = 0.45f * alpha),
        animSpec,
        label = "borderColor",
    )
    val thumbColor by animateColorAsState(
        if (checked) Color.White.copy(alpha = alpha) else SwitchGrey.copy(alpha = alpha),
        animSpec,
        label = "thumbColor",
    )
    val thumbOffset by animateDpAsState(
        if (checked) SwitchTrackWidth - SwitchThumbSize - SwitchThumbInset else SwitchThumbInset,
        tween(150),
        label = "thumbOffset",
    )
    Box(
        modifier = modifier
            .size(SwitchTrackWidth, SwitchTrackHeight)
            .toggleable(
                value = checked,
                enabled = enabled && onCheckedChange != null,
                role = Role.Switch,
                onValueChange = { onCheckedChange?.invoke(it) },
            )
            .clip(RoundedCornerShape(50))
            .background(trackColor)
            .border(1.dp, borderColor, RoundedCornerShape(50)),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = thumbOffset)
                .size(SwitchThumbSize)
                .clip(CircleShape)
                .background(thumbColor),
        )
    }
}

/** a slider with its name on the left and its value on the right */
@Composable
fun LabSlider(
    label: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    steps: Int = 0,
    onValueChange: (Float) -> Unit,
) {
    val font = mochyInterFontFamily()
    val tint = if (enabled) LabColors.Primary else LabColors.Faint
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                fontFamily = font,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = tint,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = valueText,
                fontFamily = font,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = tint,
            )
        }
        Slider(
            value = value.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White,
                inactiveTrackColor = LabColors.RowSelected,
                disabledThumbColor = LabColors.Faint,
                disabledActiveTrackColor = LabColors.Faint,
                disabledInactiveTrackColor = LabColors.Row,
            ),
        )
    }
}

/** the inner darker panel the sliders sit in */
@Composable
fun LabPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(LabColors.Row)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        content = content,
    )
}

/** options that fold away while their effect is off */
@Composable
fun LabCollapsible(visible: Boolean, content: @Composable ColumnScope.() -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow)) + fadeIn(tween(220, 60)),
        exit = shrinkVertically(spring(dampingRatio = 1f, stiffness = Spring.StiffnessMedium)) + fadeOut(tween(140)),
    ) {
        Column(Modifier.fillMaxWidth()) { content() }
    }
}

/** a card's picture, switch, then its options, which fold away while the effect is off */
@Composable
internal fun EffectCard(
    picture: @Composable () -> Unit,
    header: @Composable () -> Unit,
    expanded: Boolean,
    options: @Composable ColumnScope.() -> Unit,
    footer: (@Composable ColumnScope.() -> Unit)? = null,
) {
    LabCard(padding = 14.dp) {
        LabStage(height = 132.dp) { picture() }
        Spacer(Modifier.height(10.dp))
        header()
        LabCollapsible(expanded) {
            Spacer(Modifier.height(10.dp))
            options()
        }
        footer?.invoke(this)
    }
}

data class LabChip(val key: String, val label: String, val dot: Color? = null)

/** a scrolling row of pills; the picked one is white */
@Composable
fun LabChips(
    chips: List<LabChip>,
    selectedKey: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    onLongPress: ((String) -> Unit)? = null,
    leading: @Composable RowScope.() -> Unit = {},
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        chips.forEach { chip ->
            // keyed, so taking a chip out of the middle doesn't hand its colour to the one after it
            key(chip.key) { LabChipPill(chip, chip.key == selectedKey, onSelect, onLongPress) }
        }
        trailing()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LabChipPill(chip: LabChip, selected: Boolean, onSelect: (String) -> Unit, onLongPress: ((String) -> Unit)?) {
    val bg by animateColorAsState(if (selected) Color.White else LabColors.Chip, tween(180), label = "chipBg")
    val fg by animateColorAsState(if (selected) Color.Black else Color.White, tween(180), label = "chipFg")
    val interaction = remember { MutableInteractionSource() }
    val longClick: () -> Unit = { onLongPress?.invoke(chip.key) }
    Row(
        modifier = Modifier
            .height(38.dp)
            .clip(RoundedCornerShape(19.dp))
            .background(bg)
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = { onSelect(chip.key) },
                onLongClick = if (onLongPress != null) longClick else null,
            )
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (chip.dot != null) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(chip.dot))
            Spacer(Modifier.width(8.dp))
        }
        Text(
            chip.label,
            fontFamily = mochyInterFontFamily(),
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            color = fg,
            maxLines = 1,
        )
    }
}

/** a text button in the section headers, such as Edit: dims while disabled */
@Composable
fun LabTextAction(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    val font = mochyInterFontFamily()
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            fontFamily = font,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            color = if (enabled) Color.White else LabColors.Faint,
        )
    }
}

/** a track with a white pill that slides to the picked option */
@Composable
fun LabSegmented(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 48.dp,
) {
    val font = mochyInterFontFamily()
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(LabColors.Card)
            .border(1.dp, LabColors.StageLine, RoundedCornerShape(height / 2))
            .padding(4.dp),
    ) {
        val segment = maxWidth / options.size
        val offset by animateDpAsState(segment * selectedIndex, spring(dampingRatio = 0.82f, stiffness = 520f), label = "segment")
        Box(
            Modifier
                .offset(x = offset)
                .width(segment)
                .fillMaxHeight()
                .clip(RoundedCornerShape(height / 2))
                .background(Color.White),
        )
        Row(Modifier.fillMaxWidth().fillMaxHeight()) {
            options.forEachIndexed { index, label ->
                val color by animateColorAsState(if (index == selectedIndex) Color.Black else Color.White, tween(160), label = "segText")
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(height / 2))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSelect(index) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        fontFamily = font,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = color,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** whether pictures should move: not while they are scrolled out of view */
@Composable
fun rememberPicturesHeld(onScreen: State<Boolean>): State<Boolean> =
    remember(onScreen) { derivedStateOf { !onScreen.value } }

/** seconds, advancing every frame while the picture is free to move, or checking back a few times a second when moving says no */
@Composable
fun rememberLabTime(held: State<Boolean>, moving: () -> Boolean = { true }): State<Float> {
    val time = remember { mutableFloatStateOf(0f) }
    val currentMoving by rememberUpdatedState(moving)
    LaunchedEffect(held) {
        var last = 0L
        while (true) {
            if (held.value) {
                snapshotFlow { held.value }.first { !it }
                last = 0L
            }
            if (!currentMoving()) {
                delay(250)
                last = 0L
                continue
            }
            withFrameNanos { now ->
                if (last != 0L) time.floatValue += ((now - last) / 1e9f).coerceAtMost(0.05f)
                last = now
            }
        }
    }
    return time
}

/** true while any part of this is inside the window; written only when it flips */
@Composable
fun Modifier.trackOnScreen(state: MutableState<Boolean>): Modifier {
    val windowHeight = LocalWindowInfo.current.containerSize.height
    return onGloballyPositioned { coords ->
        val bounds = coords.boundsInWindow()
        val visible = bounds.height > 0f && bounds.bottom > 0f && bounds.top < windowHeight
        if (visible != state.value) state.value = visible
    }
}

/**
 * Mochy's pictures listen to the playing audio here. KMP has no audio level tap yet, so this does nothing and
 * the pictures run on their idle motion.
 */
@Composable
fun ListenToAudio() = Unit

/**
 * Stands in for Mochy's music follower. KMP has no audio level tap, so [level] and [punch] stay at 0 and the
 * pictures run on their idle motion. Replace the body with a real follower once a tap exists.
 */
class LevelFollower {
    val level = 0f
    val punch = 0f

    /** call once per frame with the seconds since the last one */
    @Suppress("UNUSED_PARAMETER")
    fun update(dt: Float) = Unit
}

@Composable
fun rememberOnScreen(): MutableState<Boolean> = remember { mutableStateOf(true) }
