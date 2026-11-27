// Ported from Mochy Music by realidkroo (github.com/realidkroo/mochy-music). Licensed under GPL-3.0.

// The Library's landing page, Mochy's LibraryHomeScreen: the tab chips in their dark track, the import banner
// with the services' icons flowing past, the four category cards with two real covers tucked into a corner,
// then Recently added, Liked, Downloaded and Cached as wide cards.

package com.metrolist.shared.ui.mochy

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.metrolist.shared.generated.resources.Res
import com.metrolist.shared.generated.resources.close
import com.metrolist.shared.generated.resources.mochy_provider_apple_music
import com.metrolist.shared.generated.resources.mochy_provider_spotify
import com.metrolist.shared.generated.resources.mochy_provider_tidal
import com.metrolist.shared.generated.resources.mochy_provider_youtube_music
import com.metrolist.shared.generated.resources.mochy_set_close
import com.metrolist.shared.ui.components.images.AsyncImage
import com.metrolist.shared.ui.theme.mochyInterFontFamily
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.ceil

private val Tile = Color(0xFF121214)
private val TileHigh = Color(0xFF1C1C1F)
private val Primary = Color.White
private val Secondary = Color(0xFF9A9AA0)
private val ChipTrack = Color(0xFF161618)
private val Chip = Color(0xFF232326)
private val ChipSelected = Color(0xFF3D3D41)
private val SidePadding = 24.dp

/** One tab of the Library's chips. */
class MochyLibraryTab(
    val label: String,
    val onSelect: () -> Unit,
)

/** One half-width card: Songs, Playlists, Albums or Artists. */
class MochyLibraryCard(
    val title: String,
    val count: Int,
    val covers: List<String?>,
    val onClick: () -> Unit,
    val roundCovers: Boolean = false,
)

/** One full-width card: Recently added, Liked, Downloaded, Cached. */
class MochyLibraryWide(
    val title: String,
    val count: Int?,
    val covers: List<String?>,
    val onClick: () -> Unit,
)

/** The tabs as small pills in a dark track, the picked one a step lighter; ones that don't fit wrap to their own track. */
@Composable
fun MochyLibraryTabChips(
    tabs: List<MochyLibraryTab>,
    selectedIndex: Int,
    modifier: Modifier = Modifier,
) {
    Layout(
        modifier = modifier,
        content = {
            tabs.forEach { _ -> Box(Modifier.background(ChipTrack, RoundedCornerShape(percent = 50))) }
            tabs.forEachIndexed { index, tab -> TabChip(tab, index == selectedIndex) }
        },
    ) { measurables, constraints ->
        val tracks = measurables.take(tabs.size)
        val pad = 3.dp.roundToPx()
        val gap = 4.dp.roundToPx()
        val rowGap = 6.dp.roundToPx()
        val room = if (constraints.hasBoundedWidth) (constraints.maxWidth - pad * 2).coerceAtLeast(0) else Constraints.Infinity
        val chips = measurables.drop(tabs.size).map { it.measure(Constraints(maxWidth = room)) }
        val rows = mutableListOf(mutableListOf<Placeable>())
        var rowWidth = 0
        chips.forEach { chip ->
            val row = rows.last()
            val withChip = if (row.isEmpty()) chip.width else rowWidth + gap + chip.width
            if (row.isNotEmpty() && withChip > room) {
                rows += mutableListOf(chip)
                rowWidth = chip.width
            } else {
                row += chip
                rowWidth = withChip
            }
        }
        val rowHeights = rows.map { row -> row.maxOf { it.height } }
        val trackPlaceables =
            rows.mapIndexed { index, row ->
                val width = row.sumOf { it.width } + gap * (row.size - 1) + pad * 2
                tracks[index].measure(Constraints.fixed(width, rowHeights[index] + pad * 2))
            }
        val width = trackPlaceables.maxOf { it.width }.coerceIn(constraints.minWidth, constraints.maxWidth)
        val height = trackPlaceables.sumOf { it.height } + rowGap * (rows.size - 1)
        layout(width, height) {
            var y = 0
            rows.forEachIndexed { index, row ->
                trackPlaceables[index].placeRelative(0, y)
                var x = pad
                row.forEach { chip ->
                    chip.placeRelative(x, y + pad + (rowHeights[index] - chip.height) / 2)
                    x += chip.width + gap
                }
                y += trackPlaceables[index].height + rowGap
            }
        }
    }
}

@Composable
private fun TabChip(
    tab: MochyLibraryTab,
    selected: Boolean,
) {
    val background by animateColorAsState(if (selected) ChipSelected else Chip, tween(220), label = "mochyLibraryTabChip")
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .clip(RoundedCornerShape(percent = 50))
                .background(background)
                .clickable(role = Role.Tab, onClick = tab.onSelect)
                .heightIn(min = 28.dp)
                .padding(horizontal = 14.dp, vertical = 5.dp),
    ) {
        Text(
            text = tab.label,
            color = if (selected) Primary else Primary.copy(alpha = 0.72f),
            fontFamily = mochyInterFontFamily(),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** The banner at the top of All: the services' icons flowing past and "Import your music here!". */
@Composable
fun MochyLibraryImportBanner(
    title: String,
    onClick: () -> Unit,
    onDismiss: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Tile)
                .clickable(role = Role.Button, onClick = onClick),
    ) {
        Column(Modifier.fillMaxWidth().padding(vertical = 22.dp)) {
            ProviderMarquee()
            Spacer(Modifier.height(20.dp))
            Text(
                text = title,
                color = Primary,
                fontFamily = mochyInterFontFamily(),
                fontSize = 21.sp,
                lineHeight = 25.sp,
                letterSpacing = (-0.4).sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 22.dp),
            )
        }
        if (onDismiss != null) {
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f))
                        .clickable(role = Role.Button, onClick = onDismiss),
            ) {
                Icon(
                    painter = painterResource(Res.drawable.mochy_set_close),
                    contentDescription = stringResource(Res.string.close),
                    tint = Primary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

private class ProviderArt(
    val drawable: DrawableResource,
    val background: Brush,
    val circular: Boolean = false,
    val zoom: Float = 1f,
)

// the services an import can come from, in the banner's order, each on its own app-icon squircle
private val BannerServices =
    listOf(
        ProviderArt(Res.drawable.mochy_provider_spotify, Brush.linearGradient(listOf(Color(0xFF191414), Color(0xFF191414)))),
        ProviderArt(
            Res.drawable.mochy_provider_apple_music,
            Brush.verticalGradient(listOf(Color(0xFFFA5C70), Color(0xFFFA2D48))),
            zoom = 1.1f,
        ),
        ProviderArt(
            Res.drawable.mochy_provider_tidal,
            Brush.linearGradient(listOf(Color.Black, Color.Black)),
            circular = true,
            zoom = 1.06f,
        ),
        ProviderArt(
            Res.drawable.mochy_provider_youtube_music,
            Brush.linearGradient(listOf(Color(0xFFFF0000), Color(0xFFFF0000))),
            zoom = 1.06f,
        ),
    )

// how long the marquee takes to carry one icon past
private const val MARQUEE_MS_PER_ICON = 2600

@Composable
private fun ProviderMarquee() {
    val tile = 56.dp
    val gap = 14.dp
    val setWidth = (tile + gap) * BannerServices.size
    val setPx = with(LocalDensity.current) { setWidth.toPx() }
    val travel =
        rememberInfiniteTransition(label = "providerMarquee").animateFloat(
            initialValue = 0f,
            targetValue = setPx,
            animationSpec = infiniteRepeatable(tween(MARQUEE_MS_PER_ICON * BannerServices.size, easing = LinearEasing)),
            label = "providerMarqueeTravel",
        )
    BoxWithConstraints(Modifier.fillMaxWidth().height(tile).clipToBounds()) {
        val sets = ceil(maxWidth / setWidth).toInt() + 1
        Row(
            modifier = Modifier.wrapContentWidth(align = Alignment.Start, unbounded = true).graphicsLayer { translationX = -travel.value },
            horizontalArrangement = Arrangement.spacedBy(gap),
        ) {
            repeat(sets) { BannerServices.forEach { ProviderSquircle(it, tile) } }
        }
    }
}

@Composable
private fun ProviderSquircle(
    art: ProviderArt,
    size: Dp,
) {
    Box(
        modifier = Modifier.size(size).clip(RoundedCornerShape(size * 0.27f)).background(art.background),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(art.drawable),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier =
                Modifier
                    .size(size)
                    .graphicsLayer {
                        scaleX = art.zoom
                        scaleY = art.zoom
                    }.then(if (art.circular) Modifier.clip(CircleShape) else Modifier),
        )
    }
}

/** Cards two to a row, the last one keeping its half width when it's alone. */
@Composable
fun MochyLibraryCardGrid(
    cards: List<MochyLibraryCard>,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().padding(horizontal = SidePadding), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        cards.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEach { card -> CategoryCard(card, Modifier.weight(1f)) }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun CategoryCard(
    card: MochyLibraryCard,
    modifier: Modifier,
) {
    Box(
        modifier =
            modifier
                .height(78.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Tile)
                .clickable(onClick = card.onClick),
    ) {
        // the covers sit flush in the corner and run past the card's edge; the clip crops them, tucked in
        CoverStack(card.covers, round = card.roundCovers, modifier = Modifier.align(Alignment.BottomEnd).offset(x = 10.dp, y = 12.dp))
        Column(Modifier.align(Alignment.TopStart).padding(start = 14.dp, top = 11.dp, end = 12.dp)) {
            CardLabel(card.title)
            CardCount(card.count)
        }
    }
}

/** The full-width cards, label and count at the bottom left, the covers fanned at the right. */
@Composable
fun MochyLibraryWideCard(
    card: MochyLibraryWide,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = SidePadding)
                .height(104.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Tile)
                .clickable(onClick = card.onClick),
    ) {
        CoverStack(card.covers, tile = 60.dp, modifier = Modifier.align(Alignment.BottomEnd).offset(x = 10.dp, y = 14.dp))
        Column(Modifier.align(Alignment.BottomStart).padding(start = 14.dp, bottom = 14.dp, end = 110.dp)) {
            CardLabel(card.title)
            if (card.count != null) CardCount(card.count)
        }
    }
}

/** Two real covers fanned into a corner, the back one dimmed and turned the other way; nothing when there are none. */
@Composable
private fun CoverStack(
    covers: List<String?>,
    modifier: Modifier = Modifier,
    tile: Dp = 46.dp,
    round: Boolean = false,
) {
    val shown = remember(covers) { covers.filterNotNull().take(2) }
    if (shown.isEmpty()) return
    val corner = if (round) tile / 2 else tile * 0.22f
    Box(modifier.size(width = tile * 1.55f, height = tile)) {
        shown.getOrNull(1)?.let { back ->
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .size(tile)
                    .graphicsLayer {
                        alpha = 0.55f
                        rotationZ = if (round) 0f else -9f
                    },
            ) { Artwork(back, corner, round) }
        }
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .size(tile)
                .graphicsLayer { rotationZ = if (round) 0f else 7f },
        ) { Artwork(shown[0], corner, round) }
    }
}

@Composable
private fun Artwork(
    url: String,
    corner: Dp,
    round: Boolean,
) {
    Box(Modifier.fillMaxSize().clip(if (round) CircleShape else RoundedCornerShape(corner)).background(TileHigh)) {
        AsyncImage(url = url, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}

@Composable
private fun CardLabel(text: String) {
    Text(
        text = text,
        color = Primary,
        fontFamily = mochyInterFontFamily(),
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun CardCount(count: Int) {
    Text(
        text = count.toString(),
        color = Secondary,
        style =
            TextStyle(
                fontFamily = mochyInterFontFamily(),
                fontWeight = FontWeight.Bold,
                fontSize = 34.sp,
                lineHeight = 36.sp,
                letterSpacing = (-0.03).em,
            ),
        maxLines = 1,
    )
}

/** The one line a library with nothing in it shows. */
@Composable
fun MochyLibraryEmptyState(text: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 56.dp, horizontal = SidePadding), contentAlignment = Alignment.Center) {
        Text(text = text, color = Secondary, fontFamily = mochyInterFontFamily(), fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}
