// Ported from Mochy Music by realidkroo (github.com/realidkroo/mochy-music). Licensed under GPL-3.0.

// Mochy's phone mini player: a 64dp frosted pill with the cover at its start, the title and artist, and play and next at its end

package com.metrolist.shared.ui.mochy

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.shadow
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.metrolist.shared.model.SongItem
import com.metrolist.shared.model.previewThumbnailUrl
import com.metrolist.shared.ui.components.images.AsyncImage
import com.metrolist.shared.ui.player.mochy.AnimatedPlayGlyph
import com.metrolist.shared.ui.player.mochy.AnimatedSkipGlyph
import com.metrolist.shared.ui.player.mochy.SkipCycleState
import com.metrolist.shared.ui.utils.nativeBlurCompat
import com.metrolist.shared.ui.utils.supportsNativeBlur
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

// the chrome's tint and hairline, the same values the nav bar's glass uses
private const val MOCHY_MINI_TINT_ALPHA = 0.78f
private const val MOCHY_MINI_BACKDROP_ALPHA = 0.6f
private const val MOCHY_MINI_BLUR_PX = 24f
private val MochyMiniHairline = Color.White.copy(alpha = 0.05f)

/**
 * The mini player for Mochy's look. The pill blurs its cover behind the tint where the device can blur, and
 * is a plain tinted pill where it can't. Play, next, tapping the pill and the horizontal swipe all do what
 * the other mini players do.
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
    val blur = remember { supportsNativeBlur() }
    val layoutDirection = LocalLayoutDirection.current
    val scope = rememberCoroutineScope()
    var dragOffsetX by remember { mutableFloatStateOf(0f) }
    var returnAnimation by remember { mutableStateOf<Job?>(null) }
    val shape = RoundedCornerShape(50)
    val artwork = song.previewThumbnailUrl
    val skipCycle = remember { SkipCycleState() }
    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(MochyMiniHeight)
                .graphicsLayer { translationX = dragOffsetX }
                .shadow(
                    elevation = 6.dp,
                    shape = shape,
                    ambientColor = Color.Black.copy(alpha = 0.12f),
                    spotColor = Color.Black.copy(alpha = 0.12f),
                ).clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = MOCHY_MINI_TINT_ALPHA))
                .border(1.dp, MochyMiniHairline, shape)
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
        // the cover, blurred and under the tint, is what makes the pill frosted; without blur the tint stands alone
        if (blur && artwork != null) {
            AsyncImage(
                url = artwork,
                contentDescription = null,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = MOCHY_MINI_BACKDROP_ALPHA }
                        .nativeBlurCompat(MOCHY_MINI_BLUR_PX, offscreen = true),
                contentScale = ContentScale.Crop,
            )
        }

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
                    color = MaterialTheme.colorScheme.onSurface,
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    } else {
                        AnimatedPlayGlyph(
                            playing = isPlaying,
                            color = MaterialTheme.colorScheme.onSurface,
                            height = MochyMiniGlyph,
                        )
                    }
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
                        color = MaterialTheme.colorScheme.onSurface,
                        width = MochyMiniGlyph,
                    )
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
                .clip(CircleShape)
                .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick),
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
