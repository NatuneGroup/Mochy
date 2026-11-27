// Ported from Mochy Music by realidkroo (github.com/realidkroo/mochy-music). Licensed under GPL-3.0.

// Mochy's phone mini player: a 64dp frosted pill with the cover at its start, the title and artist, and play and next at its end

package com.metrolist.shared.ui.mochy

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.metrolist.shared.model.SongItem
import com.metrolist.shared.model.previewThumbnailUrl
import com.metrolist.shared.ui.components.PlayerSheetSurface
import com.metrolist.shared.ui.components.images.AsyncImage
import com.metrolist.shared.ui.player.mochy.AnimatedPlayGlyph
import com.metrolist.shared.ui.player.mochy.AnimatedSkipGlyph
import com.metrolist.shared.ui.player.mochy.SkipCycleState
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

// the reference's metrics on a phone: the pill, the cover inset from its start, and the buttons at its end
private val MochyMiniHeight = 64.dp
private val MochyMiniArtSize = 48.dp
private val MochyMiniArtCorner = 4.dp
private val MochyMiniArtInset = 8.dp
private val MochyMiniTextGap = 12.dp
private val MochyMiniTextEnd = 10.dp
private val MochyMiniButtonSize = 36.dp
private val MochyMiniButtonGap = 8.dp
private val MochyMiniEndInset = 12.dp
private val MochyMiniGlyph = 19.dp

// the pill's ends are fully round: half its 64dp height
private val MochyMiniCorner = 32.dp

/**
 * The round highlight behind a pressed button: the nav bar's highlight colour, 4dp past the 36dp button, so the
 * skip button's sits 8dp in from the pill's end like the cover does.
 */
private val MochyMiniButtonHighlight =
    RoundedHighlight(cornerRadius = 22.dp, spillX = 4.dp, spillY = 4.dp, color = MochyChromeHighlightColor)

/**
 * The mini player for Mochy's look: the frosted chrome pill the nav bar uses. Inside the player sheet it hands
 * its bounds and colour to the sheet, so the open player grows out of the pill. Play, next, tapping the pill
 * and the horizontal swipe all do what the other mini players do.
 */
@Composable
fun MochyMiniPlayer(
    song: SongItem,
    isPlaying: Boolean,
    isLoading: Boolean,
    onTogglePlay: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onExpand: () -> Unit,
    canSkipNext: Boolean,
    canSkipPrevious: Boolean,
    playbackEnabled: Boolean,
    swipeThresholdPx: Float,
    modifier: Modifier = Modifier,
    // a caller that already drags the whole mini player turns this off so the swipe is not taken twice
    swipeEnabled: Boolean = true,
    // a d-pad or remote Enter on the focused pill expands it, as on the expressive mini
    onDpadExpand: () -> Unit = onExpand,
) {
    val layoutDirection = LocalLayoutDirection.current
    val scope = rememberCoroutineScope()
    var dragOffsetX by remember { mutableFloatStateOf(0f) }
    var returnAnimation by remember { mutableStateOf<Job?>(null) }
    val hairline = with(LocalDensity.current) { MOCHY_CHROME_OUTLINE_PX.toDp() }
    val artwork = song.previewThumbnailUrl
    val skipCycle = remember { SkipCycleState() }
    var isFocused by remember { mutableStateOf(false) }

    PlayerSheetSurface(
        color = MochyChromeColor,
        cornerRadius = MochyMiniCorner,
        elevation = 6.dp,
        border = BorderStroke(hairline, MochyChromeOutlineColor),
        modifier =
            modifier
                .fillMaxWidth()
                .height(MochyMiniHeight)
                .graphicsLayer { translationX = dragOffsetX }
                .onFocusChanged { isFocused = it.isFocused }
                .onPreviewKeyEvent { event ->
                    if (isFocused && event.type == KeyEventType.KeyUp && (event.key == Key.Enter || event.key == Key.DirectionCenter)) {
                        onDpadExpand()
                        true
                    } else {
                        false
                    }
                }.then(
                    if (swipeEnabled) {
                        Modifier.pointerInput(canSkipNext, canSkipPrevious, layoutDirection, swipeThresholdPx) {
                            detectHorizontalDragGestures(
                                onDragStart = {
                                    returnAnimation?.cancel()
                                    dragOffsetX = 0f
                                },
                                onDragEnd = {
                                    val logicalOffset = if (layoutDirection == LayoutDirection.Rtl) -dragOffsetX else dragOffsetX
                                    if (logicalOffset < -swipeThresholdPx && canSkipNext) {
                                        onSkipNext()
                                    } else if (logicalOffset > swipeThresholdPx && canSkipPrevious) {
                                        onSkipPrevious()
                                    }
                                    returnAnimation?.cancel()
                                    returnAnimation =
                                        scope.launch {
                                            Animatable(dragOffsetX).animateTo(
                                                targetValue = 0f,
                                                animationSpec =
                                                    spring(
                                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                                        stiffness = Spring.StiffnessLow,
                                                    ),
                                            ) {
                                                dragOffsetX = value
                                            }
                                        }
                                },
                                onDragCancel = {
                                    returnAnimation?.cancel()
                                    returnAnimation =
                                        scope.launch {
                                            Animatable(dragOffsetX).animateTo(0f) { dragOffsetX = value }
                                        }
                                },
                                onHorizontalDrag = { change, dragAmount ->
                                    dragOffsetX += dragAmount
                                    change.consume()
                                },
                            )
                        }
                    } else {
                        Modifier
                    },
                ).clickable(onClick = onExpand),
    ) {
        Box(Modifier.fillMaxSize()) {
            MochyChromeGlass(Modifier.matchParentSize(), shape = RoundedCornerShape(MochyMiniCorner))

            Row(
                modifier = Modifier.fillMaxSize().padding(start = MochyMiniArtInset, end = MochyMiniEndInset),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AsyncImage(
                    url = artwork,
                    contentDescription = null,
                    modifier = Modifier.size(MochyMiniArtSize).clip(RoundedCornerShape(MochyMiniArtCorner)),
                    contentScale = ContentScale.Crop,
                )
                Spacer(Modifier.width(MochyMiniTextGap))
                Column(
                    modifier = Modifier.weight(1f).mochyTextFade(),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        maxLines = 1,
                        softWrap = false,
                    )
                    val subtitle =
                        listOf(song.artists.joinToString { it.name }, song.album?.name.orEmpty())
                            .filter { it.isNotBlank() }
                            .joinToString(" — ")
                    if (subtitle.isNotBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
                Spacer(Modifier.width(MochyMiniTextEnd))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(MochyMiniButtonGap),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MochyMiniButton(enabled = playbackEnabled, onClick = onTogglePlay) {
                        AnimatedPlayGlyph(
                            playing = isPlaying,
                            color = Color.White,
                            height = MochyMiniGlyph,
                        )
                    }
                    MochyMiniButton(
                        enabled = canSkipNext,
                        onClick = {
                            scope.launch { skipCycle.step() }
                            onSkipNext()
                        },
                    ) {
                        AnimatedSkipGlyph(
                            cycle = skipCycle,
                            backwards = false,
                            color = Color.White,
                            width = MochyMiniGlyph,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MochyMiniButton(
    enabled: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier =
            Modifier
                .size(MochyMiniButtonSize)
                .pressBounce(interactionSource = interaction)
                .clickable(interactionSource = interaction, indication = MochyMiniButtonHighlight, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

// the title and artist fade out at their right end, so a long line ends softly instead of at the button
private fun Modifier.mochyTextFade(): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }.drawWithContent {
        drawContent()
        drawRect(
            brush = Brush.horizontalGradient(0f to Color.Black, 0.82f to Color.Black, 1f to Color.Transparent),
            blendMode = BlendMode.DstIn,
        )
    }
