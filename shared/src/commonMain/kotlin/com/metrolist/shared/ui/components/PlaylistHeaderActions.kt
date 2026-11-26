@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.metrolist.shared.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.metrolist.shared.generated.resources.Res
import com.metrolist.shared.generated.resources.add_to_favorites
import com.metrolist.shared.generated.resources.remove_from_favorites
import com.metrolist.shared.ui.icons.MetrolistIcons
import com.metrolist.shared.ui.mochy.pressBounce
import com.metrolist.shared.ui.theme.mochyInterFontFamily
import com.metrolist.shared.ui.theme.rememberReducedMotion
import com.metrolist.shared.ui.tv.tvFocusHighlight
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

// the detail pages' button row, as the reference lays it out: a white play pill and round buttons beside it
@Composable
fun PlaylistHeaderActions(
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    playLabel: String,
    shuffleLabel: String,
    showPlayback: Boolean,
    isFavorite: Boolean,
    onFavorite: (() -> Unit)?,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showPlayback) {
            MochyPlayPill(label = playLabel, onClick = onPlayAll)
            Spacer(Modifier.width(9.dp))
            MochyCircleAction(
                icon = MetrolistIcons.Rounded.Shuffle,
                contentDescription = shuffleLabel,
                onClick = onShuffle,
                spinOnClick = true,
            )
        }
        if (onFavorite != null) {
            Spacer(Modifier.weight(1f))
            MochyCircleAction(
                icon = if (isFavorite) MetrolistIcons.Filled.Favorite else MetrolistIcons.Default.FavoriteBorder,
                contentDescription = stringResource(if (isFavorite) Res.string.remove_from_favorites else Res.string.add_to_favorites),
                onClick = onFavorite,
            )
        }
    }
}

private val MochyPillFill = Color.White.copy(alpha = 0.64f)
private val MochyPillInk = Color(0xFF2F2F2F)

// a white capsule with the label in dark ink; it squishes when pressed
@Composable
private fun MochyPlayPill(
    label: String,
    onClick: () -> Unit,
) {
    val reducedMotion = rememberReducedMotion()
    val interaction = remember { MutableInteractionSource() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .pressBounce(interaction, if (reducedMotion) 1f else 0.88f)
                .height(33.dp)
                .clip(RoundedCornerShape(50))
                .background(MochyPillFill)
                .clickable(interactionSource = interaction, indication = null, onClick = onClick)
                .tvFocusHighlight(RoundedCornerShape(50))
                .padding(start = 21.dp, end = 17.dp),
    ) {
        Icon(
            imageVector = MetrolistIcons.Default.PlayArrow,
            contentDescription = null,
            tint = MochyPillInk,
            modifier = Modifier.size(15.dp),
        )
        Spacer(Modifier.width(9.dp))
        Text(
            text = label,
            fontFamily = mochyInterFontFamily(),
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            letterSpacing = (-0.06).em,
            color = MochyPillInk,
            maxLines = 1,
            softWrap = false,
        )
    }
}

// a 33dp round button; the shuffle arrows give one turn when pressed
@Composable
private fun MochyCircleAction(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    spinOnClick: Boolean = false,
) {
    val reducedMotion = rememberReducedMotion()
    val interaction = remember { MutableInteractionSource() }
    val spin = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .pressBounce(interaction, if (reducedMotion) 1f else 0.88f)
                .size(33.dp)
                .clip(CircleShape)
                .background(MochyPillFill)
                .clickable(interactionSource = interaction, indication = null) {
                    if (spinOnClick && !reducedMotion) {
                        scope.launch {
                            spin.snapTo(0f)
                            spin.animateTo(1f, tween(520, easing = FastOutSlowInEasing))
                        }
                    }
                    onClick()
                }
                .tvFocusHighlight(CircleShape),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = MochyPillInk,
            modifier = Modifier.size(17.dp).graphicsLayer { rotationZ = spin.value * 360f },
        )
    }
}

@Composable
internal fun HeaderPlaybackButtons(
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    playLabel: String,
    shuffleLabel: String,
    buttonHeight: Dp,
    modifier: Modifier = Modifier,
    playFirst: Boolean = false,
    tonalShuffle: Boolean = false,
    enabled: Boolean = true,
) {
    val reducedMotion = rememberReducedMotion()
    val scope = rememberCoroutineScope()
    val shuffleSpin = remember { Animatable(0f) }
    val shuffleIndex = if (playFirst) 1 else 0
    val labels = if (playFirst) listOf(playLabel, shuffleLabel) else listOf(shuffleLabel, playLabel)
    val icons =
        if (playFirst) {
            listOf(
                MetrolistIcons.Default.PlayArrow,
                MetrolistIcons.Rounded.Shuffle,
            )
        } else {
            listOf(MetrolistIcons.Rounded.Shuffle, MetrolistIcons.Default.PlayArrow)
        }
    val onShuffleTapped = {
        if (!reducedMotion) {
            scope.launch {
                shuffleSpin.animateTo(
                    shuffleSpin.targetValue + 180f,
                    spring(dampingRatio = 0.55f, stiffness = 360f),
                )
            }
        }
        onShuffle()
    }
    val callbacks = if (playFirst) listOf(onPlayAll, onShuffleTapped) else listOf(onShuffleTapped, onPlayAll)
    ButtonGroup(
        overflowIndicator = { ButtonGroupDefaults.OverflowIndicator(it) },
        modifier = modifier,
        expandedRatio = 1f,
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        labels.forEachIndexed { index, label ->
            customItem(
                buttonGroupContent = {
                    val interactionSource = remember { MutableInteractionSource() }
                    val shapes =
                        if (index == 0) {
                            ButtonGroupDefaults.connectedLeadingButtonShapes()
                        } else {
                            ButtonGroupDefaults.connectedTrailingButtonShapes()
                        }.withStaticCorners()
                    val buttonModifier =
                        Modifier
                            .weight(1f)
                            .animateWidth(interactionSource, 12.dp)
                            .height(buttonHeight)
                            .pressBounce(interactionSource, if (reducedMotion) 1f else 0.94f)
                            .tvFocusHighlight(shapes.shape)
                    val buttonShapes = ButtonDefaults.shapes(shape = shapes.shape, pressedShape = shapes.pressedShape)
                    val content: @Composable RowScope.() -> Unit = {
                        Icon(
                            icons[index],
                            null,
                            Modifier.size(20.dp).then(
                                if (index == shuffleIndex) {
                                    Modifier.graphicsLayer { rotationZ = shuffleSpin.value }
                                } else {
                                    Modifier
                                },
                            ),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            label,
                            fontFamily = mochyInterFontFamily(),
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (tonalShuffle && index == shuffleIndex) {
                        FilledTonalButton(
                            onClick = callbacks[index],
                            enabled = enabled,
                            interactionSource = interactionSource,
                            modifier = buttonModifier,
                            shapes = buttonShapes,
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            content = content,
                        )
                    } else {
                        Button(
                            onClick = callbacks[index],
                            enabled = enabled,
                            interactionSource = interactionSource,
                            modifier = buttonModifier,
                            shapes = buttonShapes,
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            content = content,
                        )
                    }
                },
                menuContent = {
                    DropdownMenuItem(text = { Text(label) }, onClick = callbacks[index], enabled = enabled)
                },
            )
        }
    }
}
