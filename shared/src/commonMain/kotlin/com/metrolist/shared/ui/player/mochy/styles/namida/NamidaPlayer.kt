// Ported from Sora (github.com/shizenworks/sora), its namida player style after namida (github.com/namidaco/namida). Licensed under GPL-3.0.

// namida player style — the player itself, ported 1:1 from namida's
// packages/miniplayer_base.dart: the same colour-tinted panel card, the same top
// row (collapse arrow, index/total over the album name, the more chip), the
// artwork riding namida's geometry with swipe parallax — centred the way
// namida's Align+padding centres it, painted 1.13x past its box like namida's
// thumbnail scale — the waveform seek bar with drag-up-to-cancel, the controls
// (prev, the gradient play/pause circle, next) with the position and duration
// at the row's two ends, the bottom row (repeat, sound, lyrics, queue) at the
// right like namida's, and the track info with its like button — all placed by
// NamidaGeometry, namida's own math, through NamidaScale's device ramp.

package com.metrolist.shared.ui.player.mochy.styles.namida

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.metrolist.shared.generated.resources.Res
import com.metrolist.shared.generated.resources.namida_arrow_down_2
import com.metrolist.shared.generated.resources.namida_document
import com.metrolist.shared.generated.resources.namida_heart
import com.metrolist.shared.generated.resources.namida_heart_filled
import com.metrolist.shared.generated.resources.namida_more
import com.metrolist.shared.generated.resources.namida_next
import com.metrolist.shared.generated.resources.namida_pause
import com.metrolist.shared.generated.resources.namida_play
import com.metrolist.shared.generated.resources.namida_previous
import com.metrolist.shared.generated.resources.namida_repeat
import com.metrolist.shared.generated.resources.namida_repeate_music
import com.metrolist.shared.generated.resources.namida_repeate_one
import com.metrolist.shared.generated.resources.namida_row_vertical
import com.metrolist.shared.generated.resources.namida_sound
import com.metrolist.shared.ui.player.mochy.styles.LocalStyleSeedColor
import com.metrolist.shared.ui.player.mochy.styles.LocalStyleTrack
import com.metrolist.shared.ui.player.mochy.styles.StyleActions
import com.metrolist.shared.ui.player.mochy.styles.StyleFrame
import com.metrolist.shared.ui.player.mochy.styles.StyleTones
import com.metrolist.shared.ui.player.mochy.styles.rememberDurationMs
import com.metrolist.shared.ui.player.mochy.styles.rememberLivePosition
import com.metrolist.shared.ui.player.mochy.styles.styleInsets
import org.jetbrains.compose.resources.painterResource
import kotlin.math.abs
import kotlin.math.roundToInt

/** namida's _kTopBottomMargin. */
private val TopBottomMargin = 8.dp

/** namida's kSiParallax: how far the incoming artwork follows before it lands. */
private const val IMAGE_PARALLAX = 1.15f

/** namida's MiniplayerThumbnailScale._base: the picture paints this far past its box. */
private const val THUMBNAIL_PAINT_SCALE = 1.13f

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NamidaPlayerStyle(
    actions: StyleActions,
    modifier: Modifier,
    preview: Boolean,
    onCollapse: () -> Unit = {},
) {
    val connection = rememberNamidaConnection()
    val seed = LocalStyleSeedColor.current
    val palette = rememberNamidaPalette(seed)
    val track = LocalStyleTrack.current
    val position = rememberLivePosition(animated = !preview)
    val durationMs = rememberDurationMs()
    val transport = connection?.isPlaying?.collectAsState()?.value
    val playing = transport == true
    val lyrics = rememberNamidaLyrics()
    val queueItems = connection?.queueItems()
    val windowIndex = connection?.currentWindowIndex() ?: -1
    val liked = connection?.liked() == true
    val repeatMode = connection?.repeatMode()
    val muted = connection?.muted() == true

    // namida's expansion parameter: 1 = the player, 2 = the queue page sliding over it
    var pageTarget by remember { mutableFloatStateOf(1f) }
    val p by animateFloatAsState(
        targetValue = pageTarget,
        animationSpec = spring(dampingRatio = 0.9f, stiffness = 380f),
        label = "namidaP",
    )
    var lyricsOpen by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var draggingWave by remember { mutableStateOf(false) }
    var dragUpCancel by remember { mutableFloatStateOf(0f) }
    var seekPreviewMs by remember { mutableStateOf<Long?>(null) }

    CompositionLocalProvider(LocalNamidaPalette provides palette) {
        StyleFrame(
            preview,
            modifier,
            StyleTones(
                played = palette.currentColor,
                remaining = palette.waveBehind,
                ink = palette.textLarge,
                inkSoft = palette.textMedium,
                chip = palette.secondaryContainer,
            ),
            background = {
                // the cover's tone fades down into the panel's own first colour, so the screen
                // above the card is the song's colour rather than black
                val spec =
                    tween<Color>(
                        300,
                    )
                val top by animateColorAsState(palette.backdropTop, spec, label = "namidaBackdropTop")
                val bottom by animateColorAsState(palette.panelGradientTop, spec, label = "namidaBackdropBottom")
                Box(
                    Modifier.fillMaxSize().drawBehind {
                        drawRect(Brush.verticalGradient(0f to top, 1f to bottom))
                    },
                )
            },
        ) { landscape, _, _ ->
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val insets = styleInsets(landscape)
                // namida reads viewPadding, which keeps the bars' heights while they are
                // hidden; the stable ones are that, so the geometry never shifts under a hide
                val stableBottom =
                    WindowInsets.navigationBars
                        .union(WindowInsets.displayCutout)
                        .asPaddingValues()
                        .calculateBottomPadding()
                        .value
                        .toDouble()
                val scale =
                    remember(maxWidth, maxHeight) {
                        NamidaScale(screenWidthDp = maxWidth.value.toDouble(), screenHeightDp = maxHeight.value.toDouble())
                    }
                val geometry = remember { NamidaLayout() }
                geometry.update(
                    p.toDouble(),
                    NamidaLayout.Inputs(
                        screenWidth = scale.virtualWidth,
                        // namida's update() derives maxOffset as the screen height minus the
                        // nav bar; the panel adds the nav bar back inside
                        maxOffset = scale.virtualHeight - scale.virtual(stableBottom),
                        topInset = scale.virtual(insets.top.value.toDouble()),
                        bottomInset = scale.virtual(stableBottom),
                        navBarHeight = scale.virtual(stableBottom),
                        imageAspectRatio = 1.0,
                    ),
                )

                // ── the panel card: namida's rounded, colour-tinted panel ──
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(scale.dp(geometry.panelHeight))
                        .graphicsLayer {
                            shape =
                                RoundedCornerShape(
                                    topStart = scale.dp(geometry.panelTopRadius),
                                    topEnd = scale.dp(geometry.panelTopRadius),
                                )
                            clip = true
                        }.drawBehind {
                            drawRect(palette.panelBackground)
                            drawRect(
                                Brush.verticalGradient(
                                    0f to palette.panelGradientTop,
                                    1f to palette.panelGradientBottom,
                                ),
                            )
                        },
                )

                // ── the top row: collapse, index/total + album, more ──
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                        Modifier
                            .align(Alignment.TopStart)
                            .fillMaxWidth()
                            .padding(top = insets.top + TopBottomMargin)
                            .padding(horizontal = 6.dp),
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.namida_arrow_down_2),
                        contentDescription = null,
                        tint = palette.onSecondaryContainer,
                        modifier =
                            Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .clickable(enabled = !preview) {
                                    pageTarget = 1f
                                    onCollapse()
                                }.padding(8.dp),
                    )
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier =
                            Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp),
                    ) {
                        Text(
                            text = "${windowIndex + 1}/${queueItems?.size ?: 0}",
                            color = palette.onSecondaryContainer.copy(alpha = 0.8f),
                            fontSize = scale.sp(12.0),
                            fontWeight = FontWeight.W500,
                            fontFamily = NamidaFontFamily,
                        )
                        Text(
                            text = track.album.ifBlank { track.title },
                            textAlign = TextAlign.Center,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            color = palette.onSecondaryContainer.copy(alpha = 0.9f),
                            fontSize = scale.sp(16.0),
                            fontWeight = FontWeight.W600,
                            fontFamily = NamidaFontFamily,
                        )
                    }
                    Icon(
                        painter = painterResource(Res.drawable.namida_more),
                        contentDescription = null,
                        tint = palette.onSecondaryContainer,
                        modifier =
                            Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(palette.secondaryContainer.copy(alpha = 0.2f))
                                .clickable(enabled = !preview) { menuOpen = true }
                                .padding(8.dp),
                    )
                }

                // ── the artwork: namida's image slot, centred like its Align+padding
                //    centres it, painted 1.13x past its box like its thumbnail scale ──
                val slot = geometry.imageSlot(1.0)
                var swipeX by remember { mutableFloatStateOf(0f) }
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .offset { IntOffset(scale.dp(slot.leftOffset).roundToPx(), scale.dp(slot.vOffset).roundToPx()) }
                        .size(scale.dp(slot.boxWidth), scale.dp(slot.boxHeight))
                        .pointerInput(preview, track.title) {
                            if (preview) return@pointerInput
                            detectTapGestures(
                                onLongPress = { if (lyrics.lines.isNotEmpty()) lyricsOpen = true },
                            )
                        }.pointerInput(preview, track.title) {
                            if (preview) return@pointerInput
                            detectDragGestures(
                                onDragEnd = {
                                    if (abs(swipeX) > size.width / 3) {
                                        if (swipeX < 0) connection?.seekToNext() else connection?.skipToPreviousSong()
                                    }
                                    swipeX = 0f
                                },
                            ) { change, dragAmount ->
                                change.consume()
                                swipeX = (swipeX + dragAmount.x).coerceIn(-size.width * IMAGE_PARALLAX, size.width * IMAGE_PARALLAX)
                            }
                        },
                ) {
                    // namida's ArtworkWidget radius: 6.0 + 8.0 * (width * 0.004)
                    val corner = scale.scaled((6.0 + 8.0 * (slot.boxWidth * 0.004)).dp)
                    val paint = THUMBNAIL_PAINT_SCALE
                    AsyncImage(
                        model = track.artwork,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(corner))
                                .graphicsLayer {
                                    scaleX = paint
                                    scaleY = paint
                                },
                    )
                    if (swipeX != 0f) {
                        AsyncImage(
                            model = track.artwork,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(corner))
                                    .graphicsLayer {
                                        scaleX = paint
                                        scaleY = paint
                                        translationX =
                                            if (swipeX < 0) {
                                                size.width * IMAGE_PARALLAX + swipeX
                                            } else {
                                                -size.width * IMAGE_PARALLAX + swipeX
                                            }
                                        alpha = abs(swipeX) / (size.width * IMAGE_PARALLAX)
                                    },
                        )
                    }
                }

                // ── the waveform seek bar, with namida's drag-up-to-cancel ──
                val waveformHeight = scale.dp(64.0 * geometry.waveformYScale)
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .offset { IntOffset(0, scale.dp(geometry.vOffsetWaveform).roundToPx()) }
                        .fillMaxWidth()
                        .height(waveformHeight)
                        .padding(horizontal = 16.dp)
                        .graphicsLayer { alpha = 1f - (p - 1f).coerceIn(0f, 1f) * 0.85f },
                ) {
                    NamidaWaveform(
                        positionMs = seekPreviewMs ?: position.positionMs(),
                        durationMs = durationMs,
                        dragging = draggingWave,
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .pointerInput(preview, durationMs) {
                                    if (preview || durationMs <= 0) return@pointerInput
                                    detectDragGestures(
                                        onDragStart = {
                                            draggingWave = true
                                            dragUpCancel = 0f
                                        },
                                        onDragEnd = {
                                            draggingWave = false
                                            if (dragUpCancel < 5f) {
                                                seekPreviewMs?.let { connection?.seekTo(it) }
                                            }
                                            seekPreviewMs = null
                                            dragUpCancel = 0f
                                        },
                                        onDragCancel = {
                                            draggingWave = false
                                            seekPreviewMs = null
                                        },
                                    ) { change, dragAmount ->
                                        change.consume()
                                        // namida: five notches of upward drag cancels the seek
                                        dragUpCancel -= dragAmount.y * 0.1f
                                        if (dragUpCancel > 5f) {
                                            seekPreviewMs = null
                                            return@detectDragGestures
                                        }
                                        val fraction = (change.position.x / size.width).coerceIn(0f, 1f)
                                        seekPreviewMs = (fraction * durationMs).toLong()
                                    }
                                },
                    )
                }

                // ── the controls row: the times at both ends, prev / play / next centred
                //    over them, exactly where namida's controls Stack puts them ──
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .offset { IntOffset(0, scale.dp(geometry.vOffsetControls).roundToPx()) }
                        .fillMaxWidth()
                        .graphicsLayer { alpha = 1f - (p - 1f).coerceIn(0f, 1f) * 0.85f },
                ) {
                    // namida's positionDurationRowChild: spaceBetween, the row padded 24
                    // (positionRowPadding) and each text a further 12
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = scale.scaled(24.dp) + scale.scaled(12.dp)),
                    ) {
                        val posText =
                            seekPreviewMs?.let { previewMs ->
                                val diff = previewMs - position.positionMs()
                                val sign = if (diff < 0) "" else "+"
                                sign + namidaFormatMillis(abs(diff))
                            } ?: namidaFormatMillis(position.positionMs())
                        Text(
                            posText,
                            color = palette.textSmall,
                            fontSize = scale.sp(13.0),
                            fontWeight = FontWeight.W400,
                            fontFamily = NamidaFontFamily,
                        )
                        Text(
                            namidaFormatMillis(durationMs),
                            color = palette.textSmall,
                            fontSize = scale.sp(13.0),
                            fontWeight = FontWeight.W400,
                            fontFamily = NamidaFontFamily,
                        )
                    }
                    // namida's buttonsRow, pushed left from the right edge by buttonsRightPadding
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier =
                            Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = scale.dp(geometry.buttonsRightPadding)),
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.namida_previous),
                            contentDescription = null,
                            tint = palette.secondary,
                            modifier =
                                Modifier
                                    .size(scale.dp(geometry.nextPrevIconSize + geometry.nextPrevIconPadding * 2))
                                    .clickable(enabled = !preview) { connection?.skipToPreviousSong() }
                                    .padding(scale.dp(geometry.nextPrevIconPadding)),
                        )
                        val iconSize = NamidaPlayPauseMetrics.iconSize(p.toDouble())
                        val boxSize = NamidaPlayPauseMetrics.boxSize(iconSize)
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier =
                                Modifier
                                    .size(scale.dp(boxSize))
                                    .shadow(
                                        8.dp,
                                        CircleShape,
                                        ambientColor = palette.playShadow,
                                        spotColor = palette.playShadow,
                                    ).clip(CircleShape)
                                    .background(
                                        // namida's gradient: the current colour into its grey blend, 0 -> 0.7 stops
                                        Brush.linearGradient(
                                            colors = palette.playGradient,
                                            start = Offset.Zero,
                                            end =
                                                Offset(1000f, 1000f),
                                        ),
                                    ).clickable(enabled = !preview) {
                                        connection?.togglePlayPause()
                                    },
                        ) {
                            Icon(
                                painter = painterResource(if (playing) Res.drawable.namida_pause else Res.drawable.namida_play),
                                contentDescription = null,
                                tint = palette.playIcon,
                                modifier = Modifier.size(scale.dp(iconSize)),
                            )
                        }
                        Icon(
                            painter = painterResource(Res.drawable.namida_next),
                            contentDescription = null,
                            tint = palette.secondary,
                            modifier =
                                Modifier
                                    .size(scale.dp(geometry.nextPrevIconSize + geometry.nextPrevIconPadding * 2))
                                    .clickable(enabled = !preview) { connection?.seekToNext() }
                                    .padding(scale.dp(geometry.nextPrevIconPadding)),
                        )
                    }
                }

                // ── the bottom row: namida's repeat, sound, lyrics and queue at the right ──
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                        Modifier
                            .align(Alignment.BottomStart)
                            .padding(
                                start = 6.dp,
                                end = 6.dp,
                                // namida pads the row by viewPadding.bottom, which stays the nav
                                // bar's height even while the bar itself is hidden
                                bottom = stableBottom.dp + TopBottomMargin,
                            ).graphicsLayer { alpha = 1f - (p - 1f).coerceIn(0f, 1f) * 0.85f },
                ) {
                    Spacer(Modifier.weight(1f))
                    val repeatIcon =
                        when (repeatMode) {
                            1 -> Res.drawable.namida_repeate_one
                            2 -> Res.drawable.namida_repeat
                            else -> Res.drawable.namida_repeate_music
                        }
                    Icon(
                        painter = painterResource(repeatIcon),
                        contentDescription = null,
                        tint = palette.onSecondaryContainer,
                        modifier =
                            Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .clickable(enabled = !preview) {
                                    connection?.cycleRepeat()
                                }.padding(8.dp),
                    )
                    Icon(
                        painter = painterResource(Res.drawable.namida_sound),
                        contentDescription = null,
                        tint =
                            if (muted) {
                                palette.onSecondaryContainer.copy(alpha = 0.4f)
                            } else {
                                palette.onSecondaryContainer
                            },
                        modifier =
                            Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .clickable(enabled = !preview) { connection?.toggleMute() }
                                .padding(8.dp),
                    )
                    Icon(
                        painter = painterResource(Res.drawable.namida_document),
                        contentDescription = null,
                        tint = palette.onSecondaryContainer,
                        modifier =
                            Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .clickable(enabled = !preview && lyrics.lines.isNotEmpty()) { lyricsOpen = true }
                                .padding(8.dp),
                    )
                    Icon(
                        painter = painterResource(Res.drawable.namida_row_vertical),
                        contentDescription = null,
                        tint = palette.onSecondaryContainer,
                        modifier =
                            Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .clickable(enabled = !preview) { pageTarget = if (pageTarget > 1.5f) 1f else 2f }
                                .padding(8.dp),
                    )
                }

                // ── the track info: title, second line, the like button ──
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                        Modifier
                            .align(Alignment.BottomStart)
                            .offset { IntOffset(0, scale.dp(geometry.vOffsetTrackInfo).roundToPx()) }
                            .fillMaxWidth()
                            .height(scale.dp(geometry.trackInfoBoxHeight))
                            .padding(horizontal = scale.scaled(24.dp))
                            .graphicsLayer { alpha = 1f - (p - 1f).coerceIn(0f, 1f) * 0.85f },
                ) {
                    Column(
                        verticalArrangement = Arrangement.Center,
                        modifier =
                            Modifier
                                .weight(1f)
                                .padding(end = scale.scaled(32.dp)),
                    ) {
                        Text(
                            track.title,
                            maxLines = if (track.artist.isNotBlank()) 1 else 2,
                            overflow = TextOverflow.Ellipsis,
                            color = palette.textMedium,
                            fontSize = scale.sp(20.0),
                            fontWeight = FontWeight.W600,
                            fontFamily = NamidaFontFamily,
                        )
                        if (track.artist.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                track.artist,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = palette.textMedium,
                                fontSize = scale.sp(15.0),
                                fontWeight = FontWeight.W600,
                                fontFamily = NamidaFontFamily,
                            )
                        }
                    }
                    Icon(
                        painter = painterResource(if (liked) Res.drawable.namida_heart_filled else Res.drawable.namida_heart),
                        contentDescription = null,
                        tint = if (liked) palette.currentColor.copy(alpha = 0.75f) else palette.secondary.copy(alpha = 0.75f),
                        modifier =
                            Modifier
                                .size(scale.scaled(38.dp))
                                .clip(CircleShape)
                                .clickable(enabled = !preview) { connection?.toggleLike() }
                                .padding(3.dp),
                    )
                }

                // ── the queue page, sliding up over the player (p > 1) ──
                if (p > 1.02f) {
                    NamidaQueue(
                        visibleFraction = (p - 1f).coerceIn(0f, 1f),
                        actions = actions,
                        preview = preview,
                        onCollapse = { pageTarget = 1f },
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                // ── the lyrics page, over everything ──
                if (lyricsOpen) {
                    NamidaLyrics(
                        fullscreen = true,
                        onDismiss = { lyricsOpen = false },
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                // ── namida's track dialog, from the more chip ──
                if (menuOpen) {
                    NamidaTrackMenu(
                        actions = actions,
                        onDismiss = { menuOpen = false },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

/** namida's milliSecondsLabel: h:mm:ss or m:ss. */
internal fun namidaFormatMillis(ms: Long): String {
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60

    fun two(n: Long) = n.toString().padStart(2, '0')
    return if (h > 0) "$h:${two(m)}:${two(s)}" else "$m:${two(s)}"
}
