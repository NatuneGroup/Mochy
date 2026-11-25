// Ported from Mochy Music by realidkroo (github.com/realidkroo/mochy-music). Licensed under GPL-3.0.

// a settings page under Mochy's header: a round back button, the page's big title below it that shrinks and blurs
// across into the bar beside the button once the page scrolls, and black building up behind it

package com.metrolist.shared.ui.settings

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.metrolist.shared.generated.resources.Res
import com.metrolist.shared.generated.resources.back
import com.metrolist.shared.ui.icons.MetrolistIcons
import com.metrolist.shared.ui.theme.mochyInterFontFamily
import com.metrolist.shared.ui.utils.supportsNativeBlur
import org.jetbrains.compose.resources.stringResource
import kotlin.math.PI
import kotlin.math.sin

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

/**
 * [listState] tells how far a list page has scrolled; pages that bring their own content are followed through
 * the scrolls they pass up instead. [content] gets the header's height to start below.
 */
@Composable
internal fun MochySubSettingsScaffold(
    title: String,
    onBack: () -> Unit,
    listState: LazyListState?,
    modifier: Modifier = Modifier,
    backButtonModifier: Modifier = Modifier,
    content: @Composable (topPadding: Dp) -> Unit,
) {
    val density = LocalDensity.current
    var nestedOffset by remember { mutableFloatStateOf(0f) }
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
    val blur = remember { supportsNativeBlur() }
    var titleHeightPx by remember { mutableIntStateOf(0) }

    Box(modifier.fillMaxSize().background(MochySettingsColors.Page)) {
        Box(Modifier.fillMaxSize().nestedScroll(tracker)) {
            content(headerHeight)
        }

        // the black under the bar, only once the title has folded into it
        Box(
            Modifier
                .fillMaxWidth()
                .height(headerHeight)
                .graphicsLayer { alpha = collapse }
                .drawWithCache {
                    val scrim =
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.95f),
                            0.45f to Color.Black.copy(alpha = 0.7f),
                            0.75f to Color.Black.copy(alpha = 0.3f),
                            1f to Color.Transparent,
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
                    MetrolistIcons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(Res.string.back),
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
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
    }
}
