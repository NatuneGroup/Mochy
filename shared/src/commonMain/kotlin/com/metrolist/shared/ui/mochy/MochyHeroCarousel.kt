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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.metrolist.shared.generated.resources.Res
import com.metrolist.shared.generated.resources.next
import com.metrolist.shared.generated.resources.now_playing
import com.metrolist.shared.generated.resources.pause
import com.metrolist.shared.generated.resources.play
import com.metrolist.shared.generated.resources.previous
import com.metrolist.shared.generated.resources.quick_picks
import com.metrolist.shared.model.SongItem
import com.metrolist.shared.settings.displayName
import com.metrolist.shared.ui.components.images.AsyncImage
import com.metrolist.shared.ui.icons.MetrolistIcons
import com.metrolist.shared.ui.player.mochy.AnimatedPlayGlyph
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

private const val HERO_ADVANCE_MILLIS = 6_500L
private const val HERO_SLIDE_MILLIS = 920
private const val HERO_DIM = 0.25f
private val HeroSlideEasing = CubicBezierEasing(0.32f, 0.0f, 0.22f, 1.0f)
private val HeroPillFill = Color.White.copy(alpha = 0.72f)
private val HeroPillContent = Color.Black.copy(alpha = 0.88f)
private val HeroLabelColor = Color.White.copy(alpha = 0.76f)
private val PeekPageGap = 16.dp

private object DesktopPeekPageSize : PageSize {
    override fun Density.calculateMainAxisPageSize(
        availableSpace: Int,
        pageSpacing: Int,
    ): Int = ((availableSpace - pageSpacing) * 0.76f).roundToInt().coerceAtLeast(1)
}

@Composable
internal fun MochyHeroCarousel(
    songs: List<SongItem>,
    currentSongId: State<String?>,
    isPlaying: State<Boolean>,
    isWide: Boolean,
    isScrolledAway: () -> Boolean,
    onPlaySong: (SongItem) -> Unit,
    onTogglePlayPause: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    if (songs.isEmpty()) return

    val items = remember(songs) { songs.take(8) }
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
        LaunchedEffect(pagerState, items.size, isDragged, isHovered) {
            if (isDragged || isHovered) return@LaunchedEffect
            while (true) {
                delay(HERO_ADVANCE_MILLIS)
                if (isScrolledAway()) continue
                pagerState.animateScrollToPage(
                    page = pagerState.currentPage + 1,
                    animationSpec = tween(HERO_SLIDE_MILLIS, easing = HeroSlideEasing),
                )
            }
        }
    }

    val peek = isWide && !isSingle
    val cardHeight = if (isWide) 268.dp else 284.dp
    val horizontalInset = if (isWide) 24.dp else 16.dp
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
                pageSize = if (peek) DesktopPeekPageSize else PageSize.Fill,
                pageSpacing = if (peek) PeekPageGap else 0.dp,
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                val itemIndex = page.mod(items.size)
                val song = items[itemIndex]
                val isThisPlaying = isPlaying.value && currentSongId.value == song.id
                val pageShape = RoundedCornerShape(24.dp)
                val isCurrentPage = pagerState.currentPage == page

                MochyHeroCard(
                    song = song,
                    isThisPlaying = isThisPlaying,
                    pageOffset = { pagerState.offsetOf(page) },
                    wipeCurve = { wipeCurve },
                    peek = peek,
                    compact = maxWidth < 480.dp,
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
                        } else if (isThisPlaying && onTogglePlayPause != null) {
                            onTogglePlayPause()
                        } else {
                            onPlaySong(song)
                        }
                    },
                    onPillClick = {
                        if (isThisPlaying && onTogglePlayPause != null) {
                            onTogglePlayPause()
                        } else {
                            onPlaySong(song)
                        }
                    },
                )
            }

            if (!isSingle && isWide) {
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

            if (!isSingle) {
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
    song: SongItem,
    isThisPlaying: Boolean,
    pageOffset: () -> Float,
    wipeCurve: () -> Float,
    peek: Boolean,
    compact: Boolean,
    surfaceFallback: Color,
    shape: RoundedCornerShape,
    onCardClick: () -> Unit,
    onPillClick: () -> Unit,
) {
    val artists = remember(song.artists) { song.artists.joinToString { it.displayName } }
    val titleText = if (artists.isNotBlank()) "${song.title} - $artists" else song.title
    val labelText = stringResource(if (isThisPlaying) Res.string.now_playing else Res.string.quick_picks)

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .press3D(maxTilt = if (peek) 3.5f else 5f, pressedScale = 0.985f)
                .clip(shape)
                .background(surfaceFallback)
                .clickable(onClick = onCardClick),
    ) {
        MochyPageWipe(
            pageOffset = pageOffset,
            curve = wipeCurve,
            peek = peek,
        ) {
            AsyncImage(
                url = song.thumbnail,
                contentDescription = song.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().pageMediaDrift(pageOffset, peek),
            )
            HeroScrims()
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
                            .fillMaxWidth(if (compact) 0.92f else 0.72f)
                            .padding(
                                start = if (compact) 20.dp else 28.dp,
                                end = if (compact) 20.dp else 28.dp,
                                bottom = if (compact) 20.dp else 24.dp,
                            ),
                ) {
                    AnimatedContent(
                        targetState = labelText,
                        transitionSpec = { heroLabelSwap() },
                        label = "mochyHeroLabel",
                    ) { label ->
                        Text(
                            text = label,
                            style =
                                MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    letterSpacing = (-0.04).em,
                                ),
                            color = HeroLabelColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    AnimatedContent(
                        targetState = titleText,
                        transitionSpec = { heroLabelSwap() },
                        label = "mochyHeroTitle",
                    ) { text ->
                        Text(
                            text = text,
                            style =
                                MaterialTheme.typography.headlineSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = if (compact) 22.sp else 26.sp,
                                    lineHeight = if (compact) 26.sp else 30.sp,
                                    letterSpacing = (-0.05).em,
                                ),
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    MochyHeroPill(
                        isPlaying = isThisPlaying,
                        onClick = onPillClick,
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroScrims() {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to heroScrimBlack(0.10f),
                        0.42f to heroScrimBlack(0.08f),
                        0.72f to heroScrimBlack(0.56f),
                        1f to heroScrimBlack(0.84f),
                    ),
                ),
    )
}

private fun heroScrimBlack(gradient: Float): Color = Color.Black.copy(alpha = 1f - (1f - HERO_DIM) * (1f - gradient))

@Composable
private fun MochyHeroPill(
    isPlaying: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier =
            Modifier
                .pressBounce(interaction)
                .height(34.dp)
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
            targetState = isPlaying,
            transitionSpec = { heroGlyphSwap() },
            label = "mochyHeroPill",
        ) { playing ->
            val actionLabel = stringResource(if (playing) Res.string.pause else Res.string.play)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .height(34.dp)
                        .padding(start = 18.dp, end = 17.dp),
            ) {
                AnimatedPlayGlyph(
                    playing = playing,
                    color = HeroPillContent,
                    height = 14.dp,
                )
                Spacer(Modifier.width(9.dp))
                Text(
                    text = actionLabel,
                    style =
                        MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            letterSpacing = (-0.04).em,
                        ),
                    color = HeroPillContent,
                )
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
