// Ported and adapted from Mochy Music by realidkroo. Licensed under GPL-3.0.

package com.metrolist.shared.ui.player.mochy.ipod

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.metrolist.shared.generated.resources.Res
import com.metrolist.shared.generated.resources.ipod_no_albums
import com.metrolist.shared.ui.components.images.AsyncImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs
import kotlin.math.roundToInt

@Immutable
internal data class IpodAlbum(
    val id: String,
    val title: String,
    val artist: String,
    val cover: String?,
)

@Stable
internal class IpodCoverFlowState(
    private val scope: CoroutineScope,
) {
    var albums by mutableStateOf<List<IpodAlbum>>(emptyList())
        private set
    var index by mutableIntStateOf(0)
        private set
    val position = Animatable(0f)
    val flip = Animatable(0f)
    var flipped by mutableStateOf(false)
        private set

    val back = IpodEntry(IpodPage.CoverFlow, -1)

    val current: IpodAlbum? get() = albums.getOrNull(index)

    fun show(list: List<IpodAlbum>) {
        albums = list
        val clamped = index.coerceIn(0, (list.size - 1).coerceAtLeast(0))
        if (clamped != index) {
            index = clamped
            scope.launch { position.snapTo(clamped.toFloat()) }
        }
    }

    fun move(steps: Int): Boolean {
        if (flipped) return back.move(steps)
        val target = (index + steps).coerceIn(0, (albums.size - 1).coerceAtLeast(0))
        if (target == index) return false
        index = target
        scope.launch { position.animateTo(target.toFloat(), GlideSpring) }
        return true
    }

    fun turnOver(tracks: IpodList) {
        back.list = tracks
        back.selected = 0
        back.first = 0
        flipped = true
        scope.launch { flip.animateTo(1f, FlipSpring) }
    }

    fun turnBack(): Boolean {
        if (!flipped) return false
        flipped = false
        scope.launch { flip.animateTo(0f, FlipSpring) }
        return true
    }
}

@Composable
internal fun IpodCoverFlow(
    state: IpodCoverFlowState,
    scale: IpodScale,
    interactive: Boolean,
    onTapSide: (Int) -> Unit,
    onTapTrack: (Int) -> Unit,
) {
    val density = LocalDensity.current
    val look = LocalIpodLook.current.screen
    val side = minOf(scale.width * 0.50f, scale.content * 0.64f)
    val sidePx = with(density) { side.toPx() }
    val backWidth = scale.width * 0.84f
    val backHeight = minOf(scale.content * 0.86f, backWidth * 1.05f)
    val backWidthPx = with(density) { backWidth.toPx() }
    val backHeightPx = with(density) { backHeight.toPx() }
    val contentHeightPx = with(density) { scale.content.toPx() }
    val widthPx = with(density) { scale.width.toPx() }
    val albums = state.albums

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(look.background)
                .then(
                    if (!interactive) {
                        Modifier
                    } else {
                        Modifier.pointerInput(Unit) {
                            detectTapGestures { at ->
                                if (state.flipped) return@detectTapGestures
                                val third = size.width / 3f
                                onTapSide(
                                    if (at.x < third) {
                                        -1
                                    } else if (at.x > third * 2f) {
                                        1
                                    } else {
                                        0
                                    },
                                )
                            }
                        }
                    },
                ),
    ) {
        if (albums.isEmpty()) {
            Text(
                text = stringResource(Res.string.ipod_no_albums),
                color = look.quietInk,
                fontSize = scale.sp(18f),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Center),
            )
            return@Box
        }

        val center by remember(state) { derivedStateOf { state.position.value.roundToInt() } }
        val window = (center - WINDOW..center + WINDOW).filter { it in albums.indices }
        val pos = state.position.value
        val flip = state.flip.value.coerceIn(0f, 1f)
        val morph = smooth(flip)
        val coverCy = contentHeightPx * COVER_CENTER
        val menuCy = contentHeightPx - backHeightPx / 2f

        for (i in window.sortedByDescending { abs(it - pos) }) {
            key(albums[i].id) {
                val album = albums[i]
                val d = i - pos
                val front = i == state.index
                if (!(front && flip >= 0.5f)) {
                    val t = abs(d).coerceAtMost(1f)
                    val sign = if (d < 0f) -1f else 1f
                    val baseX =
                        if (abs(d) <= 1f) {
                            sign * t * FIRST_STEP * sidePx
                        } else {
                            sign * (FIRST_STEP + (abs(d) - 1f) * STEP) * sidePx
                        }
                    val spread = if (abs(d) > 0.01f) sign * morph * 0.42f * sidePx else 0f
                    val swing = smooth((t * 1.4f).coerceAtMost(1f))
                    val yaw = if (front && flip > 0f) flip * 180f else -sign * TURN_DEGREES * swing
                    val scaleXY =
                        when {
                            front && flip > 0f -> (sidePx + (backWidthPx - sidePx) * morph) / sidePx.coerceAtLeast(1f)
                            else -> 1f - 0.14f * swing
                        }
                    val cy = if (front && flip > 0f) coverCy + (menuCy - coverCy) * morph else coverCy
                    val reflectStrength =
                        if (front) {
                            REFLECTION * (1f - flip * 2f).coerceAtLeast(0f)
                        } else {
                            REFLECTION * (1f - flip * 1.25f).coerceAtLeast(0f)
                        }
                    val xOffsetPx = (widthPx - sidePx) / 2f + baseX + spread
                    val yOffsetPx = cy - sidePx / 2f

                    Column(
                        modifier =
                            Modifier
                                .zIndex(if (front) 100f else 50f - abs(d))
                                .offset { IntOffset(xOffsetPx.roundToInt(), yOffsetPx.roundToInt()) }
                                .size(side, side * 1.38f)
                                .graphicsLayer {
                                    rotationY = yaw
                                    cameraDistance = 12f * this.density
                                    scaleX = scaleXY
                                    scaleY = scaleXY
                                    transformOrigin = TransformOrigin(0.5f, 0.36f)
                                },
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .size(side)
                                    .background(look.artBlank),
                        ) {
                            AsyncImage(
                                url = album.cover,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        if (reflectStrength > 0.01f) {
                            Box(
                                modifier =
                                    Modifier
                                        .size(side, side * 0.38f),
                            ) {
                                AsyncImage(
                                    url = album.cover,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier =
                                        Modifier
                                            .size(side)
                                            .graphicsLayer { scaleY = -1f },
                                )
                                Canvas(Modifier.fillMaxSize()) {
                                    drawRect(
                                        brush =
                                            Brush.verticalGradient(
                                                0f to look.background.copy(alpha = 1f - reflectStrength),
                                                0.95f to look.background,
                                                1f to look.background,
                                            ),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        AlbumBack(
            state = state,
            scale = scale,
            width = backWidth,
            height = backHeight,
            contentHeightPx = contentHeightPx,
            coverPx = sidePx,
            interactive = interactive,
            onTapTrack = onTapTrack,
        )
        AlbumCaption(state, scale)
    }
}

@Composable
private fun AlbumCaption(
    state: IpodCoverFlowState,
    scale: IpodScale,
) {
    val album = state.current ?: return
    val look = LocalIpodLook.current.screen
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .offset(y = scale.content - scale.dp(54f))
                .padding(horizontal = scale.dp(16f))
                .graphicsLayer { alpha = (1f - state.flip.value * 1.4f).coerceIn(0f, 1f) },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = album.title,
            color = look.ink,
            fontSize = scale.sp(17f),
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        if (album.artist.isNotBlank()) {
            Text(
                text = album.artist,
                color = look.softInk,
                fontSize = scale.sp(15f),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun AlbumBack(
    state: IpodCoverFlowState,
    scale: IpodScale,
    width: Dp,
    height: Dp,
    contentHeightPx: Float,
    coverPx: Float,
    interactive: Boolean,
    onTapTrack: (Int) -> Unit,
) {
    val density = LocalDensity.current
    val backScale = remember(width, height, density.fontScale) { ipodListScale(width, height, density.fontScale) }
    val widthPx = with(density) { width.toPx() }
    val heightPx = with(density) { height.toPx() }
    val left = (scale.width - width) / 2f
    val top = scale.content - height
    val topPx = contentHeightPx - heightPx
    val coverCy = contentHeightPx * COVER_CENTER
    val menuCy = topPx + heightPx / 2f
    val look = LocalIpodLook.current.screen
    val flip = state.flip.value.coerceIn(0f, 1f)
    if (flip < 0.5f && !state.flipped) return

    val morph = smooth(flip)
    val curW = coverPx + (widthPx - coverPx) * morph
    val curH = coverPx + (heightPx - coverPx) * morph
    val curCy = coverCy + (menuCy - coverCy) * morph
    Box(
        modifier =
            Modifier
                .zIndex(200f)
                .offset(left, top)
                .size(width, height)
                .graphicsLayer {
                    val visible = flip >= 0.5f
                    alpha = if (visible) 1f else 0f
                    rotationY = flip * 180f - 180f
                    cameraDistance = 12f * this.density
                    scaleX = (curW / widthPx.coerceAtLeast(1f)).coerceAtLeast(0.01f)
                    scaleY = (curH / heightPx.coerceAtLeast(1f)).coerceAtLeast(0.01f)
                    translationY = curCy - menuCy
                }.background(look.background)
                .drawBehind {
                    drawRect(look.headerLine, size = Size(size.width, size.height), style = Stroke(1.dp.toPx()))
                },
    ) {
        IpodMenuList(
            entry = state.back,
            scale = backScale,
            marquee = false,
            interactive = interactive,
            onTapRow = onTapTrack,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

private fun smooth(t: Float): Float = t * t * (3f - 2f * t)

private const val WINDOW = 6
private const val COVER_CENTER = 0.41f
private const val FIRST_STEP = 0.68f
private const val STEP = 0.32f
private const val TURN_DEGREES = 66f
private const val REFLECTION = 0.34f
private val GlideSpring = spring<Float>(dampingRatio = 0.85f, stiffness = 280f)
private val FlipSpring = spring<Float>(dampingRatio = 0.92f, stiffness = 140f)
