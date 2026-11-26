// Ported and adapted from Mochy Music by realidkroo. Licensed under GPL-3.0.

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.metrolist.shared.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ShortNavigationBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.metrolist.shared.ui.components.focusOutline
import com.metrolist.shared.ui.mochy.press3D
import com.metrolist.shared.ui.mochy.pressBounce
import com.metrolist.shared.ui.mochy.smoothFrames
import com.metrolist.shared.ui.theme.rememberReducedMotion
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

val LocalFloatingNavBar = compositionLocalOf { false }

private const val TAP_SETTLE_MS = 450L
private const val PILL_SHOWN_FADE_MS = 200
private const val CHROME_OUTLINE_WIDTH_PX = 2f

private val FloatingBarHeight = 64.dp
private val FloatingItemHeight = 48.dp
private val FloatingBarShadow = 6.dp
private val NavHighlightInset = 8.dp
private val NavHighlightGap = 4.dp
private val NavRowPadding = NavHighlightInset - NavHighlightGap / 2

private val NavHighlightLead = spring<Float>(dampingRatio = 0.82f, stiffness = 700f)
private val NavHighlightTrail = spring<Float>(dampingRatio = 0.82f, stiffness = 320f)
private val PillRise = spring<Float>(dampingRatio = 0.55f, stiffness = 420f)
private val PillSettle = spring<Float>(dampingRatio = 0.7f, stiffness = 300f)

@Stable
private class NavPillState(
    initial: Int,
) {
    val start = Animatable(initial.coerceAtLeast(0).toFloat())
    val end = Animatable(initial.coerceAtLeast(0).toFloat())
    val shown = Animatable(if (initial >= 0) 1f else 0f)
    val lift = Animatable(0f)

    fun rect(
        rowSize: Size,
        tabCount: Int,
        tabBounds: List<Pair<Float, Float>?>,
        density: Density,
    ): Rect? {
        if (tabCount == 0 || shown.value <= 0f) return null
        val inset = with(density) { NavHighlightInset.toPx() }
        val (left, right) =
            if (tabBounds.size == tabCount && tabBounds.all { it != null }) {
                val measured = tabBounds.map { it!! }
                interpolateLeft(start.value, measured) to interpolateRight(end.value, measured)
            } else {
                val slot = rowSize.width / tabCount
                val halfGap = with(density) { NavHighlightGap.toPx() } / 2f
                (start.value * slot + halfGap) to ((end.value + 1f) * slot - halfGap)
            }
        return Rect(left, inset, right.coerceAtLeast(left), (rowSize.height - inset).coerceAtLeast(inset))
    }

    suspend fun moveTo(
        target: Float,
        reducedMotion: Boolean,
    ) {
        val clamped = target.coerceAtLeast(0f)
        if (reducedMotion) {
            start.snapTo(clamped)
            end.snapTo(clamped)
            return
        }
        smoothFrames {
            val movingRight = clamped > (start.value + end.value) / 2f
            launch { start.animateTo(clamped, if (movingRight) NavHighlightTrail else NavHighlightLead) }
            end.animateTo(clamped, if (movingRight) NavHighlightLead else NavHighlightTrail)
        }
    }

    private fun interpolateLeft(
        position: Float,
        bounds: List<Pair<Float, Float>>,
    ): Float {
        val clamped = position.coerceIn(0f, (bounds.size - 1).toFloat())
        val low = floor(clamped).toInt()
        val high = (low + 1).coerceAtMost(bounds.lastIndex)
        val fraction = clamped - low
        return lerp(bounds[low].first, bounds[high].first, fraction)
    }

    private fun interpolateRight(
        position: Float,
        bounds: List<Pair<Float, Float>>,
    ): Float {
        val clamped = position.coerceIn(0f, (bounds.size - 1).toFloat())
        val low = floor(clamped).toInt()
        val high = (low + 1).coerceAtMost(bounds.lastIndex)
        val fraction = clamped - low
        return lerp(bounds[low].second, bounds[high].second, fraction)
    }
}

@Composable
private fun rememberNavPill(
    selectedIndex: Int,
    reducedMotion: Boolean,
): NavPillState {
    val pill = remember { NavPillState(selectedIndex) }
    LaunchedEffect(selectedIndex, reducedMotion) {
        if (selectedIndex < 0) {
            if (reducedMotion) {
                pill.shown.snapTo(0f)
            } else {
                smoothFrames { pill.shown.animateTo(0f, tween(PILL_SHOWN_FADE_MS)) }
            }
            return@LaunchedEffect
        }
        val target = selectedIndex.toFloat()
        if (pill.shown.value == 0f || reducedMotion) {
            pill.start.snapTo(target)
            pill.end.snapTo(target)
        }
        if (reducedMotion) {
            pill.shown.snapTo(1f)
        } else {
            launch { smoothFrames { pill.shown.animateTo(1f, tween(PILL_SHOWN_FADE_MS)) } }
            pill.moveTo(target, reducedMotion = false)
        }
    }
    return pill
}

private fun Modifier.navHighlight(
    pill: NavPillState,
    tabCount: Int,
    tabBounds: List<Pair<Float, Float>?>,
    color: Color,
): Modifier =
    drawBehind {
        val rect = pill.rect(size, tabCount, tabBounds, this) ?: return@drawBehind
        drawMochyNavPill(rect = rect, color = color, alpha = pill.shown.value, lift = pill.lift.value)
    }

@Composable
private fun Modifier.navPillDrag(
    pill: NavPillState,
    tabCount: Int,
    tabBounds: List<Pair<Float, Float>?>,
    selectedIndex: Int,
    reducedMotion: Boolean,
    onPick: (Int) -> Unit,
): Modifier {
    val lit by rememberUpdatedState(selectedIndex)
    val pick by rememberUpdatedState(onPick)
    val reduce by rememberUpdatedState(reducedMotion)
    return pointerInput(pill, tabCount) {
        if (tabCount == 0) return@pointerInput
        coroutineScope {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)

                fun tabAt(x: Float): Float {
                    val measured = tabBounds.filterNotNull()
                    if (measured.size == tabCount) {
                        val centers = measured.map { (left, right) -> (left + right) / 2f }
                        if (centers.size == 1) return 0f
                        if (x <= centers.first()) return 0f
                        if (x >= centers.last()) return (centers.lastIndex).toFloat()
                        for (index in 0 until centers.lastIndex) {
                            val startCenter = centers[index]
                            val endCenter = centers[index + 1]
                            if (x <= endCenter) {
                                val span = (endCenter - startCenter).coerceAtLeast(1f)
                                return index + ((x - startCenter) / span).coerceIn(0f, 1f)
                            }
                        }
                    }
                    val slot = size.width.toFloat() / tabCount
                    return (x / slot - 0.5f).coerceIn(0f, tabCount - 1f)
                }

                if (!reduce) {
                    launch { smoothFrames { pill.lift.animateTo(1f, PillRise) } }
                }
                launch { pill.moveTo(tabAt(down.position.x), reduce) }
                var dragging = false
                var travel = 0f
                var x = down.position.x
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break
                    travel += change.positionChange().x
                    x = change.position.x
                    if (!dragging && abs(travel) > viewConfiguration.touchSlop) dragging = true
                    if (dragging) {
                        change.consume()
                        launch { pill.moveTo(tabAt(x), reduce) }
                    }
                }
                if (!reduce) {
                    launch { smoothFrames { pill.lift.animateTo(0f, PillSettle) } }
                }
                val target = tabAt(x).roundToInt().coerceIn(0, tabCount - 1)
                launch { pill.moveTo(target.toFloat(), reduce) }
                if (dragging) {
                    pick(target)
                } else {
                    launch {
                        delay(TAP_SETTLE_MS)
                        val current = lit
                        if (current in 0 until tabCount && current != target) {
                            pill.moveTo(current.toFloat(), reduce)
                        }
                    }
                }
            }
        }
    }
}

private fun Modifier.chromeOutline(
    color: Color,
    cornerRadius: Dp? = null,
): Modifier =
    drawWithContent {
        drawContent()
        val half = CHROME_OUTLINE_WIDTH_PX / 2f
        val radius = cornerRadius?.toPx() ?: (size.height / 2f)
        drawRoundRect(
            color = color,
            topLeft = Offset(half, half),
            size =
                Size(
                    (size.width - CHROME_OUTLINE_WIDTH_PX).coerceAtLeast(0f),
                    (size.height - CHROME_OUTLINE_WIDTH_PX).coerceAtLeast(0f),
                ),
            cornerRadius = CornerRadius((radius - half).coerceAtLeast(0f)),
            style = Stroke(CHROME_OUTLINE_WIDTH_PX),
        )
    }

@Composable
internal fun <T> FloatingNavigation(
    items: List<AdaptiveNavigationItem<T>>,
    selectedKey: T?,
    onSelect: (T) -> Unit,
    navigationVisible: Boolean,
    separateKey: T?,
    itemModifier: @Composable (T) -> Modifier,
    onBarHeightChanged: (Dp) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    AnimatedVisibility(
        visible = navigationVisible,
        modifier = modifier,
        enter =
            expandVertically(
                animationSpec = spring(dampingRatio = 0.9f, stiffness = 700f),
                expandFrom = Alignment.Bottom,
            ),
        exit =
            shrinkVertically(
                animationSpec = spring(dampingRatio = 0.9f, stiffness = 700f),
                shrinkTowards = Alignment.Bottom,
            ),
    ) {
        FloatingNavigationBar(
            items = items,
            selectedKey = selectedKey,
            onSelect = onSelect,
            separateKey = separateKey,
            itemModifier = itemModifier,
            modifier =
                Modifier.onSizeChanged { size ->
                    onBarHeightChanged(with(density) { size.height.toDp() })
                },
        )
    }
}

@Composable
private fun <T> FloatingNavigationBar(
    items: List<AdaptiveNavigationItem<T>>,
    selectedKey: T?,
    onSelect: (T) -> Unit,
    separateKey: T?,
    itemModifier: @Composable (T) -> Modifier,
    modifier: Modifier = Modifier,
) {
    val pillItems = items.filter { it.key != separateKey }
    val separateItem = items.firstOrNull { it.key == separateKey }
    val reducedMotion = rememberReducedMotion()
    val selectedPillIndex = pillItems.indexOfFirst { it.key == selectedKey }
    val pill = rememberNavPill(selectedPillIndex, reducedMotion)
    val tabBounds =
        remember(pillItems.size) {
            mutableStateListOf<Pair<Float, Float>?>().apply {
                repeat(pillItems.size) { add(null) }
            }
        }
    val outlineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .windowInsetsPadding(ShortNavigationBarDefaults.windowInsets)
                .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier =
                Modifier
                    .weight(1f)
                    .height(FloatingBarHeight)
                    .press3D(maxTilt = 4f, pressedScale = 0.985f, enabled = !reducedMotion),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shadowElevation = FloatingBarShadow,
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(FloatingBarHeight)
                        .chromeOutline(outlineColor)
                        .padding(horizontal = NavRowPadding)
                        .navHighlight(
                            pill = pill,
                            tabCount = pillItems.size,
                            tabBounds = tabBounds,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                        ).navPillDrag(
                            pill = pill,
                            tabCount = pillItems.size,
                            tabBounds = tabBounds,
                            selectedIndex = selectedPillIndex,
                            reducedMotion = reducedMotion,
                            onPick = { index ->
                                pillItems.getOrNull(index)?.let { onSelect(it.key) }
                            },
                        ),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                pillItems.forEachIndexed { index, item ->
                    FloatingNavigationItem(
                        item = item,
                        selected = item.key == selectedKey,
                        reducedMotion = reducedMotion,
                        onClick = { onSelect(item.key) },
                        modifier =
                            Modifier
                                .weight(1f)
                                .onGloballyPositioned { coordinates ->
                                    val x = coordinates.positionInParent().x
                                    val bounds = x to (x + coordinates.size.width)
                                    if (index < tabBounds.size && tabBounds[index] != bounds) {
                                        tabBounds[index] = bounds
                                    }
                                }.then(itemModifier(item.key)),
                    )
                }
            }
        }

        if (separateItem != null) {
            val selected = separateItem.key == selectedKey
            val container by animateColorAsState(
                if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                label = "floatingSearchContainer",
            )
            val content by animateColorAsState(
                if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                label = "floatingSearchContent",
            )
            Surface(
                onClick = { onSelect(separateItem.key) },
                modifier =
                    Modifier
                        .size(FloatingBarHeight)
                        .press3D(maxTilt = 6f, pressedScale = 0.94f, enabled = !reducedMotion)
                        .then(itemModifier(separateItem.key)),
                shape = CircleShape,
                color = container,
                contentColor = content,
                shadowElevation = FloatingBarShadow,
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize().chromeOutline(outlineColor),
                ) {
                    Icon(
                        imageVector = if (selected) separateItem.selectedIcon else separateItem.icon,
                        contentDescription = separateItem.label,
                    )
                }
            }
        }
    }
}

@Composable
private fun <T> FloatingNavigationItem(
    item: AdaptiveNavigationItem<T>,
    selected: Boolean,
    reducedMotion: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    // the outline and the filled icon cross-fade; the label is not drawn, the tab keeps it for screen readers
    val lit by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(200),
        label = "floatingItemLit",
    )
    val iconSize = if (item.key.toString() in SmallerTabKeys) 22.dp else 24.dp
    Box(
        modifier =
            modifier
                .fillMaxHeight()
                .pressBounce(interactionSource = interactionSource, pressedScale = if (reducedMotion) 1f else 0.85f)
                .clip(CircleShape)
                .focusOutline(CircleShape)
                .selectable(
                    selected = selected,
                    onClick = onClick,
                    role = Role.Tab,
                    interactionSource = interactionSource,
                    indication = null,
                ).semantics { contentDescription = item.label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = null,
            tint = Color.White,
            modifier =
                Modifier
                    .size(iconSize)
                    .graphicsLayer {
                        alpha = UnlitTabAlpha * (1f - lit)
                        compositingStrategy = CompositingStrategy.ModulateAlpha
                    },
        )
        Icon(
            imageVector = item.selectedIcon,
            contentDescription = null,
            tint = Color.White,
            modifier =
                Modifier
                    .size(iconSize)
                    .graphicsLayer {
                        alpha = lit
                        compositingStrategy = CompositingStrategy.ModulateAlpha
                    },
        )
    }
}

// the reference draws Home and Search a little smaller than the other tabs
private val SmallerTabKeys = setOf("Home", "Search")

// the tab that is not lit shows at 70%, as the reference's tabs do
private const val UnlitTabAlpha = 0.7f
