// Ported and adapted from Mochy Music by realidkroo. Licensed under GPL-3.0.

package com.metrolist.shared.ui.mochy

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.metrolist.shared.generated.resources.Res
import com.metrolist.shared.generated.resources.mochy_hero_view
import com.metrolist.shared.generated.resources.next
import com.metrolist.shared.generated.resources.now_playing
import com.metrolist.shared.generated.resources.pause
import com.metrolist.shared.generated.resources.play
import com.metrolist.shared.generated.resources.previous
import com.metrolist.shared.model.SongItem
import com.metrolist.shared.model.YTItem
import com.metrolist.shared.settings.displayName
import com.metrolist.shared.ui.components.images.AsyncImage
import com.metrolist.shared.ui.icons.MetrolistIcons
import com.metrolist.shared.ui.player.mochy.AnimatedPlayGlyph
import com.metrolist.shared.ui.theme.mochyInterFontFamily
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

private const val HERO_ADVANCE_MILLIS = 5_500L
private const val HERO_SLIDE_MILLIS = 1_150
private const val HERO_DIM = 0.36f
private val HeroSlideEasing = CubicBezierEasing(0.32f, 0f, 0.15f, 1f)
private val HeroPillFill = Color.White.copy(alpha = 0.64f)
private val HeroPillContent = Color(0xFF2F2F2F)
private val HeroLabelColor = Color.White.copy(alpha = 0.69f)
private val PeekPageGap = 16.dp

private class HeroPeekPageSize(
    private val share: Float,
) : PageSize {
    override fun Density.calculateMainAxisPageSize(
        availableSpace: Int,
        pageSpacing: Int,
    ): Int = ((availableSpace - pageSpacing) * share).roundToInt().coerceAtLeast(1)
}

private val DesktopPeekPageSize = HeroPeekPageSize(0.76f)
private val TabletPeekPageSize = HeroPeekPageSize(0.8f)

@Composable
internal fun MochyHeroCarousel(
    items: List<MochyHeroItem>,
    currentSongId: State<String?>,
    isPlaying: State<Boolean>,
    isWide: Boolean,
    mobileHeroHeight: Dp?,
    fullBleed: Boolean,
    isScrolledAway: () -> Boolean,
    active: Boolean,
    onOpenItem: (YTItem) -> Unit,
    onTogglePlayPause: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return

    val isSingle = items.size == 1
    val loopCount = if (isSingle) 1 else Int.MAX_VALUE
    val initialPage = if (isSingle) 0 else (Int.MAX_VALUE / 2).let { it - it.mod(items.size) }
    val pagerState = rememberPagerState(initialPage = initialPage) { loopCount }
    val scope = rememberCoroutineScope()
    val hoverInteraction = remember { MutableInteractionSource() }
    val isHovered by hoverInteraction.collectIsHoveredAsState()
    val isDragged by pagerState.interactionSource.collectIsDraggedAsState()

    var userSwipe by remember { mutableStateOf(false) }
    LaunchedEffect(isDragged) {
        if (isDragged) userSwipe = true
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.isScrollInProgress }.collect { scrolling ->
            if (!scrolling) userSwipe = false
        }
    }
    val wipeCurve by animateFloatAsState(
        targetValue = if (userSwipe) 1f else 0f,
        animationSpec = tween(260),
        label = "mochyHeroWipeCurve",
    )

    if (!isSingle) {
        LaunchedEffect(pagerState, items.size, isDragged, isHovered, active) {
            if (!active || isDragged || isHovered) return@LaunchedEffect
            while (true) {
                delay(HERO_ADVANCE_MILLIS)
                if (isScrolledAway() || pagerState.isScrollInProgress) continue
                pagerState.animateScrollToPage(
                    page = pagerState.currentPage + 1,
                    animationSpec = tween(HERO_SLIDE_MILLIS, easing = HeroSlideEasing),
                )
            }
        }
    }

    val peek = isWide && !isSingle && !fullBleed
    val cardHeight = mobileHeroHeight ?: if (isWide) 268.dp else 284.dp
    val pageSize = if (mobileHeroHeight == null) DesktopPeekPageSize else TabletPeekPageSize
    val pageSpacing = if (mobileHeroHeight == null) PeekPageGap else 14.dp
    val horizontalInset =
        if (fullBleed) {
            0.dp
        } else if (isWide) {
            24.dp
        } else {
            16.dp
        }
    val surfaceFallback = MaterialTheme.colorScheme.surfaceContainerHigh

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = horizontalInset),
        contentAlignment = Alignment.Center,
    ) {
        BoxWithConstraints(
            modifier =
                Modifier
                    .widthIn(max = 1_400.dp)
                    .fillMaxWidth()
                    .height(cardHeight)
                    .hoverable(hoverInteraction),
        ) {
            val activeIndex = pagerState.currentPage.mod(items.size)
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = !isSingle,
                pageSize = if (peek) pageSize else PageSize.Fill,
                pageSpacing = if (peek) pageSpacing else 0.dp,
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                val itemIndex = page.mod(items.size)
                val card = items[itemIndex]
                val isCurrentSong = card.item is SongItem && currentSongId.value == card.item.id
                val isThisPlaying = isCurrentSong && isPlaying.value
                val pageShape = RoundedCornerShape(if (fullBleed) 0.dp else 24.dp)
                val isCurrentPage = pagerState.currentPage == page
                val open = {
                    if (isCurrentSong && onTogglePlayPause != null) {
                        onTogglePlayPause()
                    } else {
                        onOpenItem(card.item)
                    }
                }

                MochyHeroCard(
                    card = card,
                    isThisPlaying = isThisPlaying,
                    pageOffset = { pagerState.offsetOf(page) },
                    wipeCurve = { wipeCurve },
                    peek = peek,
                    compact = maxWidth < 480.dp,
                    fullBleed = fullBleed,
                    fullBleedLandscape = fullBleed && isWide,
                    surfaceFallback = surfaceFallback,
                    shape = pageShape,
                    onCardClick = {
                        if (peek && !isCurrentPage) {
                            scope.launch {
                                pagerState.animateScrollToPage(
                                    page = page,
                                    animationSpec = tween(HERO_SLIDE_MILLIS, easing = HeroSlideEasing),
                                )
                            }
                        } else {
                            open()
                        }
                    },
                    onPillClick = open,
                )
            }

            if (peek && mobileHeroHeight == null) {
                AnimatedVisibility(
                    visible = isHovered,
                    enter = fadeIn(tween(180)),
                    exit = fadeOut(tween(180)),
                    modifier = Modifier.align(Alignment.CenterStart).padding(start = 14.dp),
                ) {
                    HeroNavArrow(
                        isNext = false,
                        onClick = {
                            scope.launch {
                                pagerState.animateScrollToPage(
                                    page = (pagerState.currentPage - 1).coerceAtLeast(0),
                                    animationSpec = tween(520, easing = HeroSlideEasing),
                                )
                            }
                        },
                    )
                }
                AnimatedVisibility(
                    visible = isHovered,
                    enter = fadeIn(tween(180)),
                    exit = fadeOut(tween(180)),
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 14.dp),
                ) {
                    HeroNavArrow(
                        isNext = true,
                        onClick = {
                            scope.launch {
                                pagerState.animateScrollToPage(
                                    page = pagerState.currentPage + 1,
                                    animationSpec = tween(520, easing = HeroSlideEasing),
                                )
                            }
                        },
                    )
                }
            }

            if (!isSingle && mobileHeroHeight == null) {
                HeroPageIndicators(
                    count = items.size,
                    activeIndex = activeIndex,
                    onSelect = { targetIndex ->
                        val delta = targetIndex - activeIndex
                        if (delta != 0) {
                            scope.launch {
                                pagerState.animateScrollToPage(
                                    page = pagerState.currentPage + delta,
                                    animationSpec = tween(520, easing = HeroSlideEasing),
                                )
                            }
                        }
                    },
                    modifier =
                        Modifier
                            .align(if (isWide) Alignment.BottomEnd else Alignment.TopEnd)
                            .padding(
                                horizontal = if (isWide) 24.dp else 18.dp,
                                vertical = if (isWide) 20.dp else 16.dp,
                            ),
                )
            }
        }
    }
}

private fun PagerState.offsetOf(page: Int): Float = (page - currentPage) - currentPageOffsetFraction

@Composable
private fun MochyHeroCard(
    card: MochyHeroItem,
    isThisPlaying: Boolean,
    pageOffset: () -> Float,
    wipeCurve: () -> Float,
    peek: Boolean,
    compact: Boolean,
    fullBleed: Boolean,
    fullBleedLandscape: Boolean,
    surfaceFallback: Color,
    shape: Shape,
    onCardClick: () -> Unit,
    onPillClick: () -> Unit,
) {
    val item = card.item
    val artists = (item as? SongItem)?.artists?.joinToString { it.displayName }.orEmpty()
    val titleText = if (artists.isNotBlank()) "${item.title} - $artists" else item.title
    val reason = stringResource(if (isThisPlaying) Res.string.now_playing else card.reason)
    val artistSeed = card.artistSeed.takeUnless { isThisPlaying }
    val inter = mochyInterFontFamily()
    val pill =
        when {
            item !is SongItem -> HeroPillKind.VIEW
            isThisPlaying -> HeroPillKind.PAUSE
            else -> HeroPillKind.PLAY
        }

    val textInset =
        if (fullBleed) {
            36.dp
        } else if (compact) {
            20.dp
        } else {
            28.dp
        }
    val textBottom =
        when {
            fullBleedLandscape -> 46.dp
            fullBleed -> 64.dp
            compact -> 20.dp
            else -> 24.dp
        }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .then(
                    if (fullBleed) {
                        Modifier
                    } else {
                        Modifier.press3D(maxTilt = if (peek) 3.5f else 5f, pressedScale = 0.985f).clickable(onClick = onCardClick)
                    },
                ).clip(shape)
                .background(surfaceFallback),
    ) {
        MochyPageWipe(
            pageOffset = pageOffset,
            curve = wipeCurve,
            peek = peek,
        ) {
            AsyncImage(
                url = item.thumbnail,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().pageMediaDrift(pageOffset, peek),
            )
            HeroScrims(fullBleed)
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .pageTextDrift(pageOffset, peek),
            ) {
                Column(
                    modifier =
                        Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth(
                                if (fullBleed) {
                                    1f
                                } else if (compact) {
                                    0.92f
                                } else {
                                    0.72f
                                },
                            ).padding(start = textInset, end = textInset, bottom = textBottom),
                ) {
                    AnimatedContent(
                        targetState = reason to artistSeed,
                        transitionSpec = { heroLabelSwap() },
                        label = "mochyHeroLabel",
                    ) { (text, artist) ->
                        val labelStyle =
                            MaterialTheme.typography.titleSmall.copy(
                                fontFamily = inter,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                lineHeight = 19.sp,
                                letterSpacing = (-0.06).em,
                            )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text, style = labelStyle, color = HeroLabelColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (artist != null) {
                                artist.thumbnail?.let { cover ->
                                    Spacer(Modifier.width(6.dp))
                                    AsyncImage(
                                        url = cover,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.size(18.dp).clip(CircleShape),
                                    )
                                }
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = artist.title,
                                    style = labelStyle,
                                    color = Color.White.copy(alpha = 0.88f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                            }
                        }
                    }
                    AnimatedContent(
                        targetState = titleText,
                        transitionSpec = { heroLabelSwap() },
                        label = "mochyHeroTitle",
                    ) { text ->
                        Text(
                            text = text,
                            style =
                                MaterialTheme.typography.headlineSmall.copy(
                                    fontFamily = inter,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 24.sp,
                                    lineHeight = 28.sp,
                                    letterSpacing = (-0.06).em,
                                ),
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.height(11.dp))
                    MochyHeroPill(
                        kind = pill,
                        onClick = onPillClick,
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroScrims(fullBleed: Boolean) {
    val brush =
        if (fullBleed) {
            Brush.verticalGradient(
                0f to heroScrimBlack(0.86f),
                0.22f to heroScrimBlack(0.59f),
                0.463f to heroScrimBlack(0f),
                0.533f to heroScrimBlack(0f),
                0.742f to heroScrimBlack(0.59f),
                0.989f to Color.Black,
                1f to Color.Black,
            )
        } else {
            Brush.verticalGradient(
                0f to heroScrimBlack(0.10f),
                0.42f to heroScrimBlack(0.08f),
                0.72f to heroScrimBlack(0.56f),
                1f to heroScrimBlack(0.84f),
            )
        }
    val feedBackground = MaterialTheme.colorScheme.background
    Box(
        Modifier.fillMaxSize().background(brush).then(
            if (fullBleed) {
                Modifier.drawWithCache {
                    val edge =
                        Brush.verticalGradient(
                            0f to feedBackground.copy(alpha = 0f),
                            1f to feedBackground,
                            startY = (size.height - 32.dp.toPx()).coerceAtLeast(0f),
                            endY = size.height,
                        )
                    onDrawBehind { drawRect(edge) }
                }
            } else {
                Modifier
            },
        ),
    )
}

private fun heroScrimBlack(gradient: Float): Color = Color.Black.copy(alpha = 1f - (1f - HERO_DIM) * (1f - gradient))

private enum class HeroPillKind {
    PLAY,
    PAUSE,
    VIEW,
}

@Composable
private fun MochyHeroPill(
    kind: HeroPillKind,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val inter = mochyInterFontFamily()
    Box(
        modifier =
            Modifier
                .pressBounce(interaction)
                .height(33.dp)
                .clip(RoundedCornerShape(50))
                .background(HeroPillFill)
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    role = Role.Button,
                    onClick = onClick,
                ),
    ) {
        AnimatedContent(
            targetState = kind,
            transitionSpec = { heroGlyphSwap() },
            label = "mochyHeroPill",
        ) { action ->
            val opensPage = action == HeroPillKind.VIEW
            val label =
                when (action) {
                    HeroPillKind.PLAY -> Res.string.play
                    HeroPillKind.PAUSE -> Res.string.pause
                    HeroPillKind.VIEW -> Res.string.mochy_hero_view
                }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.height(33.dp).padding(start = if (opensPage) 17.dp else 21.dp, end = 17.dp),
            ) {
                if (!opensPage) {
                    AnimatedPlayGlyph(playing = action == HeroPillKind.PAUSE, color = HeroPillContent, height = 15.dp)
                    Spacer(Modifier.width(9.dp))
                }
                Text(
                    text = stringResource(label),
                    fontFamily = inter,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    lineHeight = 19.sp,
                    letterSpacing = (-0.06).em,
                    color = HeroPillContent,
                )
                if (opensPage) {
                    Spacer(Modifier.width(5.dp))
                    Icon(
                        MetrolistIcons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = HeroPillContent,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroNavArrow(
    isNext: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val description = stringResource(if (isNext) Res.string.next else Res.string.previous)
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .pressBounce(interaction, pressedScale = 0.86f)
                .size(38.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.56f))
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    role = Role.Button,
                    onClick = onClick,
                ).semantics { contentDescription = description },
    ) {
        Icon(
            imageVector = if (isNext) MetrolistIcons.Filled.ChevronRight else MetrolistIcons.Filled.ChevronLeft,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun HeroPageIndicators(
    count: Int,
    activeIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .clip(RoundedCornerShape(50))
                .background(Color.Black.copy(alpha = 0.34f))
                .padding(horizontal = 8.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { index ->
            val selected = index == activeIndex
            val dotWidth by animateDpAsState(
                targetValue = if (selected) 18.dp else 6.dp,
                animationSpec = spring(dampingRatio = 0.72f, stiffness = 500f),
                label = "mochyHeroDot",
            )
            Box(
                modifier =
                    Modifier
                        .height(6.dp)
                        .width(dotWidth)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = if (selected) 0.94f else 0.42f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onSelect(index) },
                        ),
            )
        }
    }
}

private fun <S> AnimatedContentTransitionScope<S>.heroGlyphSwap(): ContentTransform =
    (scaleIn(initialScale = 0.6f, animationSpec = spring(dampingRatio = 0.55f, stiffness = 650f)) + fadeIn(tween(130)))
        .togetherWith(scaleOut(targetScale = 0.6f, animationSpec = tween(110)) + fadeOut(tween(90)))
        .using(SizeTransform(clip = false) { _, _ -> spring(dampingRatio = 0.75f, stiffness = 520f) })

private fun <S> AnimatedContentTransitionScope<S>.heroLabelSwap(): ContentTransform =
    (slideInVertically(spring(dampingRatio = 0.8f, stiffness = 420f)) { it / 3 } + fadeIn(tween(220)))
        .togetherWith(slideOutVertically(tween(160)) { -it / 3 } + fadeOut(tween(140)))
        .using(SizeTransform(clip = false))
