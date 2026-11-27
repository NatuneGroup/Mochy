// Ported from Natune (github.com/NatuneGroup/Natune), ArchiveTune by Rukamori. Licensed under GPL-3.0.

// Natune's lyrics sheet, shared by every platform: the cover's colours drifting blurred behind everything, the
// song in a small header, the Apple Music style karaoke lyrics in the middle, transport controls that tuck
// away while the lyrics play, and the source, more and close buttons at the foot.

package com.metrolist.shared.ui.lyrics.natune

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextMotion
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrolist.shared.generated.resources.Res
import com.metrolist.shared.generated.resources.close
import com.metrolist.shared.generated.resources.lyrics_not_found
import com.metrolist.shared.generated.resources.mochy_set_close
import com.metrolist.shared.generated.resources.mochy_set_more_horiz
import com.metrolist.shared.generated.resources.more_options
import com.metrolist.shared.generated.resources.natune_lyrics_from_source
import com.metrolist.shared.model.LyricsEntry
import com.metrolist.shared.ui.components.images.AsyncImage
import com.metrolist.shared.ui.utils.nativeBlurCompat
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs
import kotlin.math.roundToInt

// Natune's lyrics sheet measures, from its LyricsScreen and LyricsEnhanced
private val SheetSidePadding = 16.dp
private const val CONTROLS_AUTO_HIDE_MS = 3_000L

// the drifting backdrop: the cover blown up past every corner, blurred hard, turning slowly
private const val BACKDROP_SCALE = 2.4f
private val BackdropBlur = 64.dp
private const val BACKDROP_TURN_MS = 90_000

@Composable
fun NatuneLyricsOverlay(
    lines: List<LyricsEntry>,
    isLoading: Boolean,
    title: String,
    artist: String,
    artworkUrl: String?,
    providerName: String?,
    // the frame's playback position with the lyrics offset applied
    positionProvider: () -> Long,
    onSeekToLine: (Long) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    onMore: (() -> Unit)? = null,
    // the player's own seek bar and transport, shown under the lyrics until they're left alone
    controls: (@Composable (Modifier) -> Unit)? = null,
    // the moving cover backdrop, off where blurring a full-screen layer every frame is too heavy
    movingBackdrop: Boolean = true,
) {
    val foreground = Color.White
    var controlsVisible by remember { mutableStateOf(true) }
    var interactions by remember { mutableIntStateOf(0) }
    LaunchedEffect(interactions) {
        controlsVisible = true
        delay(CONTROLS_AUTO_HIDE_MS)
        controlsVisible = false
    }
    val currentOnClose by rememberUpdatedState(onClose)

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(Color.Black)
            // any touch brings the tucked away controls back, and a pull down from the top closes the sheet
            .pointerInput(Unit) {
                val startRegion = 96.dp.toPx()
                val dismissAt = 120.dp.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    interactions++
                    if (down.position.y > startRegion) return@awaitEachGesture
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        val dx = change.position.x - down.position.x
                        val dy = change.position.y - down.position.y
                        if (dy < 0f || abs(dx) > abs(dy)) break
                        if (dy > 0f) change.consume()
                        if (dy > dismissAt) {
                            currentOnClose()
                            break
                        }
                    }
                }
            },
    ) {
        NatuneLyricsBackdrop(artworkUrl, moving = movingBackdrop)
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
            Grabber(onClick = onClose)
            TrackHeader(title, artist, artworkUrl, foreground, Modifier.fillMaxWidth().padding(horizontal = SheetSidePadding))
            NatuneLyricsPane(
                lines = lines,
                isLoading = isLoading,
                positionProvider = positionProvider,
                onSeekToLine = onSeekToLine,
                textColor = foreground,
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = SheetSidePadding),
            )
            if (controls != null) {
                AnimatedVisibility(
                    visible = controlsVisible,
                    enter = fadeIn(tween(120)) + slideInVertically(tween(180)) { it / 6 },
                    exit = fadeOut(tween(90)) + slideOutVertically(tween(140)) { it / 8 },
                    label = "natuneLyricsControls",
                ) {
                    controls(Modifier.fillMaxWidth().padding(horizontal = SheetSidePadding))
                }
            }
            Footer(
                providerName = providerName,
                foreground = foreground,
                onMore = onMore,
                onClose = onClose,
                modifier = Modifier.fillMaxWidth().padding(horizontal = SheetSidePadding, vertical = 2.dp),
            )
        }
    }
}

/** The cover blown up, blurred and turning slowly, so its colours drift behind the lyrics. */
@Composable
fun NatuneLyricsBackdrop(
    artworkUrl: String?,
    moving: Boolean = true,
) {
    val turn =
        if (moving) {
            rememberInfiniteTransition(label = "natuneLyricsBackdrop").animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(tween(BACKDROP_TURN_MS, easing = LinearEasing), RepeatMode.Restart),
                label = "natuneLyricsBackdropTurn",
            )
        } else {
            null
        }
    Box(Modifier.fillMaxSize()) {
        if (artworkUrl != null) {
            val blurPx = with(LocalDensity.current) { BackdropBlur.toPx() }
            AsyncImage(
                url = artworkUrl,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = BACKDROP_SCALE
                            scaleY = BACKDROP_SCALE
                            rotationZ = turn?.value ?: 0f
                        }.nativeBlurCompat(blurPx),
            )
        }
        // a touch of black, so white lyrics read on a pale cover
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.22f)))
    }
}

@Composable
private fun Grabber(onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(20.dp).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Box(Modifier.size(width = 36.dp, height = 5.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.35f)))
    }
}

@Composable
private fun TrackHeader(
    title: String,
    artist: String,
    artworkUrl: String?,
    foreground: Color,
    modifier: Modifier,
) {
    Row(modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.1f))) {
            if (artworkUrl != null) AsyncImage(url = artworkUrl, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                color = foreground,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = artist,
                color = foreground.copy(alpha = 0.72f),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun Footer(
    providerName: String?,
    foreground: Color,
    onMore: (() -> Unit)?,
    onClose: () -> Unit,
    modifier: Modifier,
) {
    Row(modifier.height(56.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        Spacer(Modifier.weight(1f))
        if (providerName != null) {
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.14f))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(
                    text = stringResource(Res.string.natune_lyrics_from_source, providerName),
                    color = foreground.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (onMore != null) {
            Spacer(Modifier.width(12.dp))
            FooterButton(Res.drawable.mochy_set_more_horiz, stringResource(Res.string.more_options), foreground, onMore)
        }
        Spacer(Modifier.width(12.dp))
        FooterButton(Res.drawable.mochy_set_close, stringResource(Res.string.close), foreground, onClose, ringed = true)
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun FooterButton(
    icon: org.jetbrains.compose.resources.DrawableResource,
    description: String,
    foreground: Color,
    onClick: () -> Unit,
    ringed: Boolean = false,
) {
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (ringed) {
            Box(Modifier.size(24.dp).clip(CircleShape).background(foreground.copy(alpha = 0.85f)))
            Icon(painterResource(icon), description, tint = Color.Black.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
        } else {
            Icon(painterResource(icon), description, tint = foreground.copy(alpha = 0.85f), modifier = Modifier.size(24.dp))
        }
    }
}

/**
 * Natune's lyrics pane, its LyricsEnhanced: the karaoke lyrics view on Android and desktop, word-synced lines
 * swept word by word, the sung line held near the top and the rest dimmed and blurred. iOS, which the view
 * isn't built for, draws Mochy's own lyrics list in its place.
 */
@Composable
expect fun NatuneLyricsPane(
    lines: List<LyricsEntry>,
    isLoading: Boolean,
    positionProvider: () -> Long,
    onSeekToLine: (Long) -> Unit,
    textColor: Color,
    modifier: Modifier = Modifier,
)
