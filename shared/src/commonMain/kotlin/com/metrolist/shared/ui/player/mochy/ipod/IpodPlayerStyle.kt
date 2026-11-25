// Ported and adapted from Mochy Music by realidkroo. Licensed under GPL-3.0.

package com.metrolist.shared.ui.player.mochy.ipod

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.metrolist.shared.generated.resources.Res
import com.metrolist.shared.generated.resources.ipod_shuffle_songs
import com.metrolist.shared.model.previewThumbnailUrl
import com.metrolist.shared.playback.LocalPlayerSession
import com.metrolist.shared.ui.playback.LocalPlaybackController
import com.metrolist.shared.ui.player.PlayerArtworkCrossfade
import com.metrolist.shared.ui.player.mochy.FlagshipVolume
import com.metrolist.shared.ui.theme.mochyInterFontFamily
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

internal fun isIpodWholeDevice(
    maxWidth: Dp,
    maxHeight: Dp,
): Boolean = minOf(maxWidth, maxHeight) >= 600.dp || maxWidth > maxHeight * 0.62f

@Composable
internal fun IpodPlayerStyle(
    active: Boolean,
    volumeControl: FlagshipVolume?,
    artworkCrossfade: PlayerArtworkCrossfade,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier,
    preview: Boolean = false,
) {
    val density = LocalDensity.current
    val bars = WindowInsets.safeDrawing
    val topInset = bars.getTop(density)
    val bottomInset = bars.getBottom(density)
    val look = rememberIpodLook()
    val inter = mochyInterFontFamily()

    BoxWithConstraints(
        modifier =
            modifier
                .fillMaxSize()
                .then(if (preview) SwallowTouches else Modifier),
    ) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val wholeDevice = isIpodWholeDevice(maxWidth, maxHeight)
        val geometry =
            remember(w, h, topInset, bottomInset, wholeDevice, look.tall) {
                if (wholeDevice) {
                    deviceGeometry(w, h, topInset.toFloat(), bottomInset.toFloat(), look.tall)
                } else {
                    phoneGeometry(w, h, topInset.toFloat(), bottomInset.toFloat(), look.tall)
                }
            }
        Box(
            Modifier
                .fillMaxSize()
                .drawWithCache {
                    val paint = IpodBodyPaint(geometry, look)
                    onDrawBehind { with(paint) { draw(onBlack = wholeDevice) } }
                },
        )
        CompositionLocalProvider(
            LocalIpodLook provides look,
            LocalTextStyle provides TextStyle(fontFamily = inter),
        ) {
            IpodFace(
                geometry = geometry,
                preview = preview,
                active = active,
                volumeControl = volumeControl,
                artworkCrossfade = artworkCrossfade,
                onCollapse = onCollapse,
                density = density,
            )
        }
    }
}

@Composable
private fun IpodFace(
    geometry: IpodGeometry,
    preview: Boolean,
    active: Boolean,
    volumeControl: FlagshipVolume?,
    artworkCrossfade: PlayerArtworkCrossfade,
    onCollapse: () -> Unit,
    density: Density,
) {
    val session = LocalPlayerSession.current
    val playbackController = LocalPlaybackController.current
    val scope = rememberCoroutineScope()
    val nav = remember(scope) { IpodNavigator(scope) }
    val nowPlaying = remember { IpodNowPlayingState() }
    val coverFlow = remember(scope) { IpodCoverFlowState(scope) }
    val haptics = rememberIpodHaptics()
    val library = remember(preview, scope) { if (preview) null else IpodLibrarySource(scope) }
    val shuffleTitle = stringResource(Res.string.ipod_shuffle_songs)
    val controller =
        remember(nav, nowPlaying, coverFlow, scope, session, playbackController, volumeControl, library, haptics, shuffleTitle) {
            IpodController(
                nav = nav,
                nowPlaying = nowPlaying,
                coverFlow = coverFlow,
                scope = scope,
                session = session,
                playbackController = playbackController,
                volumeControl = volumeControl,
                library = library,
                haptics = haptics,
                shuffleQueueTitle = shuffleTitle,
            )
        }
    val marquee = active && !preview

    BarTimeout(nowPlaying)
    IpodChevron(geometry, preview, onCollapse, density)

    val bezel = with(density) { geometry.bezel.toDp() }
    val outerCorner = with(density) { geometry.screenCorner.toDp() }
    val innerW = with(density) { (geometry.screen.width - geometry.bezel * 2f).toDp() }
    val innerH = with(density) { (geometry.screen.height - geometry.bezel * 2f).toDp() }
    val scale = remember(innerW, innerH, density.fontScale) { IpodScale(innerW, innerH, density.fontScale) }
    val covers by produceState(library?.cachedCovers.orEmpty(), library) {
        library?.let { value = it.covers() }
    }
    val fallback = rememberArtwork()
    val slideshowCovers = remember(covers, fallback) { covers.ifEmpty { listOfNotNull(fallback) } }

    Box(
        modifier =
            Modifier
                .placeIpodRect(geometry.screen, density)
                .clip(RoundedCornerShape(outerCorner))
                .background(LocalIpodLook.current.body.bezel),
    ) {
        Box(
            modifier =
                Modifier
                    .padding(bezel)
                    .fillMaxSize()
                    .clip(RoundedCornerShape((outerCorner - bezel).coerceAtLeast(0.dp)))
                    .background(LocalIpodLook.current.screen.background),
        ) {
            Column(Modifier.fillMaxSize()) {
                TitleBar(nav, coverFlow, scale)
                IpodPages(nav, Modifier.fillMaxWidth().weight(1f)) { entry, slideX, sidebarOnly, curtainOpen, showCoverCycle ->
                    IpodPageContent(
                        entry = entry,
                        slideX = slideX,
                        sidebarOnly = sidebarOnly,
                        curtainOpen = curtainOpen,
                        showCoverCycle = showCoverCycle,
                        covers = slideshowCovers,
                        controller = controller,
                        scale = scale,
                        marquee = marquee,
                        interactive = !preview,
                        showing = { active },
                        artworkCrossfade = artworkCrossfade,
                    )
                }
            }
        }
    }

    IpodClickWheel(
        actions = controller,
        haptics = haptics,
        enabled = !preview,
        modifier = Modifier.placeIpodRect(geometry.wheel, density),
    )
}

@Composable
private fun BarTimeout(nowPlaying: IpodNowPlayingState) {
    LaunchedEffect(nowPlaying.bar, nowPlaying.touchedAt) {
        if (nowPlaying.bar == IpodBar.PROGRESS) return@LaunchedEffect
        delay(if (nowPlaying.bar == IpodBar.VOLUME) VOLUME_SHOWN_MS else SCRUB_SHOWN_MS)
        nowPlaying.bar = IpodBar.PROGRESS
    }
}

@Composable
private fun TitleBar(
    nav: IpodNavigator,
    coverFlow: IpodCoverFlowState,
    scale: IpodScale,
) {
    val page = nav.top.page
    val title =
        if (page == IpodPage.CoverFlow && coverFlow.flipped) {
            coverFlow.current?.title ?: page.displayTitle()
        } else {
            page.displayTitle()
        }
    IpodHeader(title, scale)
}

@Composable
private fun IpodPageContent(
    entry: IpodEntry,
    slideX: () -> Float,
    sidebarOnly: Boolean,
    curtainOpen: (() -> Float)?,
    showCoverCycle: Boolean,
    covers: List<String>,
    controller: IpodController,
    scale: IpodScale,
    marquee: Boolean,
    interactive: Boolean,
    showing: () -> Boolean,
    artworkCrossfade: PlayerArtworkCrossfade,
) {
    val background = LocalIpodLook.current.screen.background
    when {
        entry.page == IpodPage.NowPlaying -> {
            IpodNowPlaying(
                state = controller.nowPlaying,
                scale = scale,
                marquee = marquee,
                artworkCrossfade = artworkCrossfade,
            )
        }

        entry.page == IpodPage.CoverFlow -> {
            IpodCoverFlow(
                state = controller.coverFlow,
                scale = scale,
                interactive = interactive,
                onTapSide = controller::tapCoverFlow,
                onTapTrack = controller::tapTrack,
            )
        }

        entry.page.isSidebar -> {
            Box(Modifier.fillMaxSize()) {
                if (showCoverCycle) {
                    IpodCoverCycle(
                        covers = covers,
                        running = showing,
                        modifier =
                            Modifier
                                .align(Alignment.CenterEnd)
                                .fillMaxWidth(0.5f)
                                .fillMaxHeight()
                                .graphicsLayer {
                                    if (curtainOpen != null) translationX = size.width * curtainOpen()
                                },
                    )
                }
                val halfWidthPx = with(LocalDensity.current) { (scale.width * 0.5f).toPx() }
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth(0.5f)
                            .fillMaxHeight()
                            .then(if (sidebarOnly) Modifier.clipToBounds() else Modifier),
                ) {
                    IpodMenuList(
                        entry = entry,
                        scale = scale,
                        marquee = marquee,
                        interactive = interactive,
                        onTapRow = { controller.tapRow(entry, it) },
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    translationX =
                                        when {
                                            curtainOpen != null -> -size.width * curtainOpen()
                                            sidebarOnly -> size.width * slideX()
                                            else -> 0f
                                        }
                                }.background(background),
                    )
                }
                if (showCoverCycle) {
                    IpodSidebarShadow(
                        scale = scale,
                        modifier =
                            Modifier
                                .offset(x = scale.width * 0.5f)
                                .graphicsLayer {
                                    if (curtainOpen != null) translationX = -halfWidthPx * curtainOpen()
                                },
                    )
                }
            }
        }

        else -> {
            IpodMenuList(
                entry = entry,
                scale = scale,
                marquee = marquee,
                interactive = interactive,
                onTapRow = { controller.tapRow(entry, it) },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun rememberArtwork(): String? {
    val session = LocalPlayerSession.current
    val fallbackState by LocalPlaybackController.current.state.collectAsState()
    val song = session?.currentSong?.collectAsState()?.value ?: fallbackState.currentSong
    return song?.previewThumbnailUrl ?: song?.thumbnail
}

@Composable
private fun IpodChevron(
    geometry: IpodGeometry,
    preview: Boolean,
    onCollapse: () -> Unit,
    density: Density,
) {
    val ink = LocalIpodLook.current.body.chevron
    val chevron = geometry.chevron ?: return
    val target = with(density) { Rect(Offset(chevron.x - 32.dp.toPx(), chevron.y - 24.dp.toPx()), Size(64.dp.toPx(), 48.dp.toPx())) }
    Box(
        modifier =
            Modifier
                .placeIpodRect(target, density)
                .then(
                    if (preview) {
                        Modifier
                    } else {
                        Modifier.pointerInput(onCollapse) {
                            detectTapGestures { onCollapse() }
                        }
                    },
                ).drawWithCache {
                    val half = geometry.unit * 0.0367f
                    val depth = geometry.unit * 0.026f
                    val path =
                        Path().apply {
                            moveTo(size.width / 2f - half, size.height / 2f - depth / 2f)
                            lineTo(size.width / 2f, size.height / 2f + depth / 2f)
                            lineTo(size.width / 2f + half, size.height / 2f - depth / 2f)
                        }
                    val stroke = Stroke(width = geometry.unit * 0.0075f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    onDrawBehind { drawPath(path, ink, style = stroke) }
                },
    )
}

private val SwallowTouches =
    Modifier.pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
        }
    }

private const val VOLUME_SHOWN_MS = 1_600L
private const val SCRUB_SHOWN_MS = 4_000L
