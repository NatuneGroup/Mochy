// Ported from Mochy Music by realidkroo (github.com/realidkroo/mochy-music). Licensed under GPL-3.0.

// a settings page under Mochy's header: a round back button, the page's big title below it that shrinks and blurs
// across into the bar beside the button once the page scrolls, and black building up behind it

package com.metrolist.shared.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.metrolist.shared.generated.resources.Res
import com.metrolist.shared.generated.resources.back
import com.metrolist.shared.generated.resources.close
import com.metrolist.shared.generated.resources.mochy_nav_search
import com.metrolist.shared.generated.resources.mochy_pages_arrow_back_ios
import com.metrolist.shared.generated.resources.pc_hide_search
import com.metrolist.shared.generated.resources.search
import com.metrolist.shared.ui.icons.MetrolistIcons
import com.metrolist.shared.ui.mochy.MochyBackdrop
import com.metrolist.shared.ui.mochy.MochyProgressiveGlass
import com.metrolist.shared.ui.mochy.mochyBackdropSource
import com.metrolist.shared.ui.theme.mochyInterFontFamily
import com.metrolist.shared.ui.utils.supportsNativeBlur
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.PI
import kotlin.math.sin
import androidx.compose.ui.unit.lerp as lerpDp

private val PagePadding = 20.dp
private val BackButtonSize = 40.dp
private val HeaderTopPadding = 16.dp
private val BackButtonCenterY = HeaderTopPadding + BackButtonSize / 2
private val ExpandedTitleTop = HeaderTopPadding + BackButtonSize + 12.dp
private val CollapsedTitleX = BackButtonSize + 14.dp
private val ExpandedHeaderHeight = 118.dp
private const val COLLAPSED_TITLE_SCALE = 0.62f
private const val TITLE_MORPH_MAX_BLUR_PX = 26f
private val CollapseEasing = CubicBezierEasing(0.57f, 0.53f, 0f, 1f)
private const val COLLAPSE_MILLIS = 900
private val BackButtonColor = Color(0xFF2C2C2E)
private val BackLabelStart = BackButtonSize + 10.dp
private val CollapsedStickyGap = 8.dp
private val SearchBarHeight = 52.dp
private val OverlayEasing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

/** The space a page leaves under its header for the search pill the scaffold draws there. */
internal val MochySettingsSearchSpace = SearchBarHeight + 16.dp

/**
 * Where a page hands the scaffold its search field. The scaffold draws it under the header and, as the title
 * folds into the bar, warps the same shape into the round search button at the bar's right end.
 */
internal val LocalMochySettingsSearchSlot = staticCompositionLocalOf<MutableState<(@Composable () -> Unit)?>?> { null }

/**
 * [listState] tells how far a list page has scrolled; pages that bring their own content are followed through
 * the scrolls they pass up instead. [content] gets the expanded header's height (content starts below it inside
 * its own scroll) and the bottom of the collapsed bar (where sticky rows pin).
 */
@Composable
internal fun MochySubSettingsScaffold(
    title: String,
    onBack: () -> Unit,
    listState: LazyListState?,
    modifier: Modifier = Modifier,
    backButtonModifier: Modifier = Modifier,
    // a close button at the header's right end, for a page shown beside the settings list
    onClose: (() -> Unit)? = null,
    // the page this one sits under, written beside the back button
    backLabel: String? = null,
    content: @Composable (headerHeight: Dp, collapsedBottom: Dp) -> Unit,
) {
    val density = LocalDensity.current
    // saved with the page's own scroll, so a page opened again part way down comes back with its title folded
    var nestedOffset by rememberSaveable { mutableFloatStateOf(0f) }
    val tracker =
        remember {
            object : NestedScrollConnection {
                override fun onPostScroll(
                    consumed: Offset,
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    nestedOffset = (nestedOffset - consumed.y).coerceAtLeast(0f)
                    // a fling or drag that hits the top again means the page is back at its start
                    if (available.y > 0f) nestedOffset = 0f
                    return Offset.Zero
                }
            }
        }
    val enterPx = with(density) { 24.dp.toPx() }
    val exitPx = with(density) { 6.dp.toPx() }
    val latch = remember { booleanArrayOf(false) }
    val collapsed by remember(listState, enterPx, exitPx) {
        derivedStateOf {
            val offset =
                if (listState != null) {
                    if (listState.firstVisibleItemIndex > 0) Float.MAX_VALUE else listState.firstVisibleItemScrollOffset.toFloat()
                } else {
                    nestedOffset
                }
            val next =
                when {
                    offset > enterPx -> true
                    offset < exitPx -> false
                    else -> latch[0]
                }
            latch[0] = next
            next
        }
    }
    // Mochy's latch-and-settle: once past the threshold the morph always finishes, even after the finger lifts
    val collapse by animateFloatAsState(
        targetValue = if (collapsed) 1f else 0f,
        animationSpec = tween(COLLAPSE_MILLIS, easing = CollapseEasing),
        label = "mochySubSettingsCollapse",
    )
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val headerHeight = statusBarTop + ExpandedHeaderHeight
    val collapsedBottom = statusBarTop + HeaderTopPadding + BackButtonSize + CollapsedStickyGap
    val blur = remember { supportsNativeBlur() }
    var titleHeightPx by remember { mutableIntStateOf(0) }
    val searchSlot = remember { mutableStateOf<(@Composable () -> Unit)?>(null) }

    val pageBackdrop = remember { MochyBackdrop() }
    Box(modifier.fillMaxSize().background(MochySettingsColors.Page)) {
        Box(Modifier.fillMaxSize().nestedScroll(tracker).mochyBackdropSource(pageBackdrop)) {
            CompositionLocalProvider(LocalMochySettingsSearchSlot provides searchSlot) {
                content(headerHeight, collapsedBottom)
            }
        }

        // the frosted page under the bar, then the black over it, only once the title has folded into it
        MochyProgressiveGlass(
            backdrop = pageBackdrop,
            maxBlurPx = { HEADER_GLASS_BLUR_PX * collapse },
            foundation = MochySettingsColors.Page.copy(alpha = 0.55f),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(headerHeight)
                    .graphicsLayer { alpha = collapse },
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(headerHeight)
                .graphicsLayer { alpha = collapse }
                .drawWithCache {
                    val scrim =
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.90f),
                            0.20f to Color.Black.copy(alpha = 0.50f),
                            0.45f to Color.Black.copy(alpha = 0.18f),
                            0.75f to Color.Transparent,
                        )
                    onDrawBehind { drawRect(scrim) }
                },
        )

        Box(
            Modifier
                .fillMaxWidth()
                .height(headerHeight)
                .padding(top = statusBarTop, start = PagePadding, end = PagePadding),
        ) {
            Box(
                modifier =
                    backButtonModifier
                        .padding(top = HeaderTopPadding)
                        .size(BackButtonSize)
                        .clip(CircleShape)
                        .background(BackButtonColor)
                        .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(Res.drawable.mochy_pages_arrow_back_ios),
                    contentDescription = stringResource(Res.string.back),
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }
            if (backLabel != null) {
                // gives way to the title, which slides into this spot as the page collapses
                Text(
                    text = backLabel,
                    fontFamily = mochyInterFontFamily(),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = Color.White,
                    maxLines = 1,
                    modifier =
                        Modifier
                            .padding(top = HeaderTopPadding, start = BackLabelStart)
                            .height(BackButtonSize)
                            .wrapContentHeight(Alignment.CenterVertically)
                            .graphicsLayer { alpha = (1f - collapse * 2.5f).coerceIn(0f, 1f) }
                            .clickable(onClick = onBack),
                )
            }
            if (onClose != null) {
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = HeaderTopPadding)
                            .size(BackButtonSize)
                            .clip(CircleShape)
                            .background(BackButtonColor)
                            .clickable(onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        MetrolistIcons.Default.Close,
                        contentDescription = stringResource(Res.string.close),
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Text(
                text = title,
                fontFamily = mochyInterFontFamily(),
                fontWeight = FontWeight.Bold,
                fontSize = 32.sp,
                lineHeight = 40.sp,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier =
                    Modifier
                        // keeps a long title clear of the close button it folds beside
                        .padding(end = if (onClose != null) BackButtonSize + 8.dp else 0.dp)
                        .onSizeChanged { titleHeightPx = it.height }
                        .graphicsLayer {
                            val p = collapse
                            transformOrigin = TransformOrigin(0f, 0f)
                            val scale = lerp(1f, COLLAPSED_TITLE_SCALE, p)
                            scaleX = scale
                            scaleY = scale
                            val collapsedTop = BackButtonCenterY.toPx() - titleHeightPx * COLLAPSED_TITLE_SCALE / 2f
                            translationX = lerp(0f, CollapsedTitleX.toPx(), p)
                            translationY = lerp(ExpandedTitleTop.toPx(), collapsedTop, p)
                            val radius = sin(p.coerceIn(0f, 1f) * PI.toFloat()) * TITLE_MORPH_MAX_BLUR_PX
                            renderEffect = if (blur && radius > 0.5f) BlurEffect(radius, radius, TileMode.Decal) else null
                        },
            )
        }

        searchSlot.value?.let { searchBar ->
            MochySearchPill(
                searchBar = searchBar,
                collapse = { collapse },
                expandedY = headerHeight,
                collapsedY = statusBarTop + HeaderTopPadding,
                // beside the close button when the page has one, else at the bar's right end
                endInset = if (onClose != null) BackButtonSize + 8.dp else 0.dp,
            )
        }
    }
}

// the search field resting under the header that resizes and slides into the round button in the bar: one shape
// warping, not one thing fading out while another appears; once it's a button, a tap drops the field down again
@Composable
private fun MochySearchPill(
    searchBar: @Composable () -> Unit,
    collapse: () -> Float,
    expandedY: Dp,
    collapsedY: Dp,
    endInset: Dp,
) {
    var overlayVisible by remember { mutableStateOf(false) }
    val iconTappable by remember { derivedStateOf { collapse() > 0.6f } }
    LaunchedEffect(Unit) {
        snapshotFlow { collapse() < 0.5f }.collect { opened -> if (opened) overlayVisible = false }
    }
    Box(
        modifier =
            Modifier
                .layout { measurable, constraints ->
                    // the page's own width, so beside the settings list the pill stays on the page
                    val pageWidth = constraints.maxWidth.toDp()
                    val p = collapse()
                    val width = lerpDp(pageWidth - PagePadding * 2, BackButtonSize, p).roundToPx()
                    val height = lerpDp(SearchBarHeight, BackButtonSize, p).roundToPx()
                    val placeable = measurable.measure(Constraints.fixed(width, height))
                    layout(width, height) {
                        placeable.place(
                            lerpDp(PagePadding, pageWidth - PagePadding - BackButtonSize - endInset, p).roundToPx(),
                            lerpDp(expandedY, collapsedY, p).roundToPx(),
                        )
                    }
                }.graphicsLayer {
                    shape = RoundedCornerShape(lerpDp(26.dp, BackButtonSize / 2, collapse()))
                    clip = true
                }.background(BackButtonColor),
    ) {
        if (!iconTappable && !overlayVisible) {
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = (1f - collapse() / 0.6f).coerceIn(0f, 1f) }) {
                searchBar()
            }
        }
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = ((collapse() - 0.45f) / 0.55f).coerceIn(0f, 1f) }
                    .clip(CircleShape)
                    // clickable only once it's a button, so the invisible circle doesn't take taps meant for the field
                    .then(if (iconTappable) Modifier.clickable { overlayVisible = !overlayVisible } else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            if (overlayVisible) {
                Icon(
                    MetrolistIcons.Default.Close,
                    contentDescription = stringResource(Res.string.pc_hide_search),
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
            } else {
                Icon(
                    painter = painterResource(Res.drawable.mochy_nav_search),
                    contentDescription = stringResource(Res.string.search),
                    tint = Color.White,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
    AnimatedVisibility(
        visible = overlayVisible,
        enter = fadeIn(tween(260, easing = OverlayEasing)) + slideInVertically(tween(260, easing = OverlayEasing)) { -it / 2 },
        exit = fadeOut(tween(180)) + slideOutVertically(tween(180)) { -it / 2 },
        modifier = Modifier.padding(top = collapsedY + BackButtonSize + 10.dp, start = PagePadding, end = PagePadding),
    ) {
        searchBar()
    }
}

// the reference's 40px at full collapse
private const val HEADER_GLASS_BLUR_PX = 40f
